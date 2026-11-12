package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.effect.ParticleSpec;
import com.aitchn.prism.api.effect.Travel;
import com.aitchn.prism.api.effect.TravelHandle;
import com.aitchn.prism.core.block.Appearances;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;

/**
 * Optional startup check of the world effects (ADR-0027), enabled with {@code -Dprism.selftest.effects=<block>}; the
 * smoke test passes {@code examplemod:ruby_lamp}. The block must declare {@code prism:particles} with more emitters in
 * {@code active} than in its first variant, and {@code prism:tint} for its variants. Steps, each a few clock cycles
 * apart on the block's thread:
 *
 * <ol>
 *   <li>Place the block: after a cycle its chunk has emitters; a simulated viewer next to it is scheduled emissions, one
 *       far away none, and with no player online the clock scheduled no player task at all.
 *   <li>Switch to {@code active}: the emitter set follows the variant.
 *   <li>Launch a travelling visual with a model and a trail (and one that is cancelled at once), plus a burst and a
 *       beam; the first travel's arrival callback must run on the destination's thread, on time by the effects clock,
 *       and the cancelled one's never.
 *   <li>The Java pack has a constant tint in the block's item definitions and the Bedrock pack a baked texture.
 *   <li>Remove the block: its emitters are gone after the next cycle.
 * </ol>
 *
 * Logs {@code PRISM_EFFECTS_SELFTEST_OK emitters=<n> launched=<n> completed=<n> tinted=<n>} or
 * {@code PRISM_EFFECTS_SELFTEST_FAIL <reason>}.
 */
final class EffectsSelfTest implements Listener {

    private static final int FLIGHT = 20;

    private final PrismKey key;
    private final PrismBlockService blocks;
    private final BlockEmitters emitters;
    private final WorldEffects effects;
    private final PackManager pack;
    private final PlatformScheduler scheduler;
    private final Logger log;

    EffectsSelfTest(PrismKey key, PrismBlockService blocks, BlockEmitters emitters, WorldEffects effects,
            PackManager pack, PlatformScheduler scheduler, Logger log) {
        this.key = key;
        this.blocks = blocks;
        this.emitters = emitters;
        this.effects = effects;
        this.pack = pack;
        this.scheduler = scheduler;
        this.log = log;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        if (world == null) {
            fail("no world");
            return;
        }
        Location spawn = world.getSpawnLocation();
        Location at = new Location(world, spawn.getBlockX() - 40, world.getMaxHeight() - 48, spawn.getBlockZ() + 40);
        scheduler.runAtLater(at, () -> place(at.getBlock()), 60);
    }

    private void fail(String reason) {
        log.severe("PRISM_EFFECTS_SELFTEST_FAIL " + reason);
        release();
    }

    /** The chunk under test stays loaded for the test: an unloaded chunk rightly has no emitters. */
    private volatile org.bukkit.Chunk held;

    private void hold(Block block) {
        held = block.getChunk();
        held.addPluginChunkTicket(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(EffectsSelfTest.class));
    }

    private void release() {
        org.bukkit.Chunk chunk = held;
        held = null;
        if (chunk != null) {
            scheduler.runAt(new Location(chunk.getWorld(), chunk.getX() << 4, 64, chunk.getZ() << 4),
                    () -> chunk.removePluginChunkTicket(
                    org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(EffectsSelfTest.class)));
        }
    }

    /** Waits a little more than two emitter cycles, so the chunk's emitters were rebuilt. */
    private void afterCycles(Block block, Runnable next) {
        scheduler.runAtLater(block.getLocation(), () -> {
            try {
                next.run();
            } catch (RuntimeException e) {
                fail(e.toString());
                blocks.remove(block, false);
            }
        }, emitters.interval() * 2L + 2);
    }

    private void place(Block block) {
        try {
            hold(block);
            if (!blocks.place(key, block.getLocation())) {
                fail("cannot place " + key);
                return;
            }
            afterCycles(block, () -> idle(block));
        } catch (RuntimeException e) {
            fail(e.toString());
        }
    }

    private void idle(Block block) {
        int idle = emitters.at(block).size();
        double x = block.getX() + 0.5;
        double y = block.getY() + 0.5;
        double z = block.getZ() + 0.5;
        int near = emitters.simulate(block, x + 2, y + 1, z, 100);
        int far = emitters.simulate(block, x + 500, y, z, 100);
        boolean noPlayers = Bukkit.getOnlinePlayers().isEmpty();
        long tasks = emitters.tasksScheduled();
        if (idle == 0 || near == 0 || far != 0 || noPlayers && tasks != 0 || !emitters.running()) {
            fail("idle emitters=" + idle + " near=" + near + " far=" + far + " tasks=" + tasks + " noPlayers="
                    + noPlayers + " running=" + emitters.running() + " " + emitters.describe(block));
            blocks.remove(block, false);
            return;
        }
        blocks.setVariant(block, "active");
        afterCycles(block, () -> active(block, idle));
    }

    private void active(Block block, int idle) {
        int active = emitters.at(block).size();
        if (active <= idle) {
            fail("variant active did not switch the emitters: idle=" + idle + " active=" + active);
            blocks.remove(block, false);
            return;
        }
        Location from = block.getLocation().add(0.5, 1.5, 0.5);
        Location to = from.clone().add(10, 0, 0);
        AtomicInteger arrivedAt = new AtomicInteger(-1);
        AtomicBoolean ownedThread = new AtomicBoolean();
        AtomicBoolean cancelledRan = new AtomicBoolean();
        int launchedAt = effects.clockNow();
        TravelHandle flight = effects.launch(Travel.between(from, to, FLIGHT)
                .withModel(Appearances.itemModel(key, "active"))
                .withScale(0.5f)
                .withBillboard(Travel.Billboard.CENTER)
                .withTrail(ParticleSpec.dust(0x3ab5ff, 1f), 2)
                .onArrive(() -> {
                    arrivedAt.set(effects.clockNow());
                    ownedThread.set(scheduler.isOwnedByCurrentThread(to));
                }));
        TravelHandle cancelled = effects.launch(Travel.along(from, new org.bukkit.util.Vector(0, 0, 1), 6, FLIGHT)
                .withTrail(ParticleSpec.of("minecraft:flame"), 1)
                .onArrive(() -> cancelledRan.set(true)));
        boolean cancelOk = cancelled.cancel() && !cancelled.cancel() && cancelled.done();
        effects.burst(from, ParticleSpec.of("minecraft:flame").withCount(4).withSpread(0.2, 0.2, 0.2));
        effects.line(from, to, ParticleSpec.dust(0xff4040, 0.8f), 4);
        scheduler.runAtLater(block.getLocation(), () -> {
            try {
                arrived(block, active, flight, launchedAt, arrivedAt.get(), ownedThread.get(), cancelOk,
                        cancelledRan.get());
            } catch (RuntimeException e) {
                fail(e.toString());
                blocks.remove(block, false);
            }
        }, FLIGHT + 40);
    }

    private void arrived(Block block, int active, TravelHandle flight, int launchedAt, int arrivedAt,
            boolean ownedThread, boolean cancelOk, boolean cancelledRan) {
        int took = arrivedAt - launchedAt;
        boolean onTime = arrivedAt >= 0 && took >= FLIGHT - 1 && took <= FLIGHT + 2;
        int completed = arrivedAt >= 0 && ownedThread && onTime && flight.done() ? 1 : 0;
        if (completed != 1 || !cancelOk || cancelledRan || effects.activeCount() != 0) {
            fail("travel arrivedAt=" + arrivedAt + " took=" + took + " ownedThread=" + ownedThread + " done="
                    + flight.done() + " cancelOk=" + cancelOk + " cancelledRan=" + cancelledRan + " active="
                    + effects.activeCount());
            blocks.remove(block, false);
            return;
        }
        int tinted = javaTints();
        boolean bedrock = bedrockTint();
        if (tinted == 0 || !bedrock) {
            fail("tint java=" + tinted + " bedrock=" + bedrock);
            blocks.remove(block, false);
            return;
        }
        blocks.remove(block, false);
        afterCycles(block, () -> {
            if (!emitters.at(block).isEmpty()) {
                fail("emitters left after the block was removed: " + emitters.at(block).size());
                return;
            }
            log.info("PRISM_EFFECTS_SELFTEST_OK emitters=" + active + " launched=1 completed=" + completed
                    + " tinted=" + tinted);
            release();
        });
    }

    /** How many of the block's variants have an item definition with a constant tint in the Java pack. */
    private int javaTints() {
        var files = pack.resolveNow().files();
        int tinted = 0;
        List<String> variants = blocks.definition(key).map(Appearances::variants).orElse(List.of());
        for (String variant : variants) {
            PrismKey model = Appearances.itemModel(key, variant);
            byte[] json = files.get("assets/" + model.namespace() + "/items/" + model.value() + ".json");
            if (json != null && new String(json, StandardCharsets.UTF_8).contains("minecraft:constant")) {
                tinted++;
            }
        }
        return tinted;
    }

    /** Whether the Bedrock pack holds a block texture with a tint baked in. */
    private boolean bedrockTint() {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(pack.bedrockNow().mcpack()))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.getName().matches("textures/prism/blocks/.*_t[0-9a-f]{6}\\.png")) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }
}
