package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.mod.ModContext;
import com.aitchn.prism.api.mod.PrismMod;
import com.aitchn.prism.core.block.Appearances;
import com.aitchn.prism.core.block.BlockLook;
import com.aitchn.prism.core.content.MemoryContentSource;
import com.aitchn.prism.core.look.LookTable;
import com.aitchn.prism.core.mod.ModCandidate;
import com.aitchn.prism.core.mod.ModManager;
import com.aitchn.prism.core.mod.ModReport;
import com.aitchn.prism.pack.JavaPackBuilder;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Optional startup check of the look pool at scale, enabled with {@code -Dprism.selftest.synthetic=<count>} (the smoke
 * test passes 10000). Registers that many synthetic block definitions (solid, passable, forced to the display layer,
 * with other surfaces; every one with its own texture, so the pack has a model per look), then checks that every look
 * got a client representation, that the pack builds, twice, to the same bytes and without a warning about these blocks,
 * that the canonicalization table never maps a borrowed look to itself, and that blocks of both tiers placed in the
 * world read back with exactly the look the table promises. Off by default.
 */
final class SyntheticSelfTest implements Listener {

    private static final String NAMESPACE = "synth";
    private static final int SAMPLE = 200;

    private final int count;
    private final ModManager mods;
    private final Runnable contentChanged;
    private final PackManager packs;
    private final PrismBlockService blocks;
    private final PlatformScheduler scheduler;
    private final Logger log;

    SyntheticSelfTest(int count, ModManager mods, Runnable contentChanged, PackManager packs,
            PrismBlockService blocks, PlatformScheduler scheduler, Logger log) {
        this.count = count;
        this.mods = mods;
        this.contentChanged = contentChanged;
        this.packs = packs;
        this.blocks = blocks;
        this.scheduler = scheduler;
        this.log = log;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        try {
            MemoryContentSource source = content();
            ModReport report = mods.activate(new ModCandidate("prism-synthetic", NAMESPACE, "prism", source,
                    new PrismMod() {
                        @Override
                        public void onInitialize(@NotNull ModContext context) {}
                    }, log));
            if (!report.active() || report.blockCount() != count) {
                fail("registered " + report.blockCount() + " of " + count + " blocks: " + report.problems());
                return;
            }
            contentChanged.run();
            List<BlockDefinition> definitions = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                mods.blocks().get(new PrismKey(NAMESPACE, "b" + i)).ifPresent(definitions::add);
            }
            String problem = checkTable(definitions);
            if (problem == null) {
                problem = checkPack();
            }
            if (problem != null) {
                fail(problem);
                return;
            }
            World world = Bukkit.getWorlds().get(0);
            Location at = world.getSpawnLocation();
            scheduler.runAt(at, () -> {
                String failure;
                try {
                    failure = checkWorld(world, at, definitions);
                } catch (RuntimeException e) {
                    failure = "exception " + e;
                }
                if (failure != null) {
                    fail(failure);
                    return;
                }
                LookTable table = packs.looks().table();
                log.info("PRISM_SYNTHETIC_SELFTEST_OK blocks=" + count + " looks=" + table.placements().size()
                        + " state=" + table.count(LookTable.Tier.STATE) + " display="
                        + table.count(LookTable.Tier.DISPLAY) + " unplaced=" + table.unplaced().size()
                        + " families=" + table.activeBlocks().size());
            });
        } catch (IOException | RuntimeException e) {
            fail("exception " + e);
        }
    }

    private void fail(String reason) {
        log.severe("PRISM_SYNTHETIC_SELFTEST_FAIL " + reason);
    }

    /** The kinds: solid, passable, forced display, glass-like (not occluding) and stone-like blocks. */
    private MemoryContentSource content() throws IOException {
        MemoryContentSource source = new MemoryContentSource();
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        for (int i = 0; i < count; i++) {
            StringBuilder yml = new StringBuilder("base: minecraft:barrier\n");
            switch (i % 10) {
                case 0 -> yml.append("components:\n  prism:passable: true\n");
                case 1, 2 -> yml.append("components:\n  prism:render: display\n");
                case 3 -> yml.append("components:\n  prism:surface:\n    sound: glass\n    occludes: false\n");
                case 4 -> yml.append("components:\n  prism:surface:\n    sound: stone\n");
                default -> { }
            }
            source.put("prism/blocks/b" + i + ".yml", yml.toString());
            source.put("assets/" + NAMESPACE + "/textures/block/b" + i + ".png", png.toByteArray());
        }
        return source;
    }

    private static Carrier carrierOf(BlockDefinition definition) {
        return Appearances.passable(definition) ? Carrier.PASSABLE : Carrier.SOLID;
    }

    /** Every look has a representation, and no borrowed look is its own canonical sibling. */
    private String checkTable(List<BlockDefinition> definitions) {
        ClientLooks looks = packs.looks();
        int total = 0;
        for (BlockDefinition definition : definitions) {
            for (Appearances.Look look : Appearances.looks(definition)) {
                total++;
                BlockLook shown = looks.lookFor(carrierOf(definition), look.id());
                if (shown == null) {
                    return "no client representation for " + look.id();
                }
                LookTable.Placement placement = looks.table().placement(look.id());
                if (placement != null && placement.tier() == LookTable.Tier.DISPLAY && !shown.displayed()) {
                    return "display look without a display plan: " + look.id();
                }
                if (placement != null && placement.tier() == LookTable.Tier.STATE) {
                    int canonical = looks.canon().of(shown.look());
                    if (canonical == shown.look() || looks.canon().of(canonical) != canonical) {
                        return "borrowed state of " + look.id() + " is not canonicalized to a stable sibling";
                    }
                }
            }
        }
        return looks.table().unplaced().isEmpty() && total == count ? null
                : "unplaced=" + looks.table().unplaced().size() + " looks=" + total;
    }

    /** The pack builds twice to the same bytes, and says nothing about the synthetic blocks. */
    private String checkPack() {
        JavaPackBuilder.Result first = packs.buildNow();
        JavaPackBuilder.Result second = packs.buildNow();
        if (!first.sha1().equals(second.sha1())) {
            return "the pack differs between two builds of the same content";
        }
        Optional<String> warning = first.warnings().stream().filter(w -> w.contains(NAMESPACE + ":")).findFirst();
        return warning.map(w -> "pack warning " + w).orElse(null);
    }

    /** Both tiers and both carriers placed, read back and removed in the arena chunk. */
    private String checkWorld(World world, Location spawn, List<BlockDefinition> definitions) {
        ClientLooks looks = packs.looks();
        List<BlockDefinition> picked = new ArrayList<>();
        int states = 0;
        int displays = 0;
        int passable = 0;
        for (BlockDefinition definition : definitions) {
            LookTable.Placement placement = looks.table().placement(
                    new Appearances.Look(definition.key(), "", "NORTH").id());
            if (placement == null) {
                continue;
            }
            boolean display = placement.tier() == LookTable.Tier.DISPLAY;
            if (display ? displays < SAMPLE : states < SAMPLE) {
                picked.add(definition);
                states += display ? 0 : 1;
                displays += display ? 1 : 0;
            } else if (Appearances.passable(definition) && passable < 20) {
                picked.add(definition);
                passable++;
            }
        }
        int baseX = (spawn.getBlockX() >> 4 << 4);
        int baseZ = (spawn.getBlockZ() >> 4 << 4);
        int y = world.getMaxHeight() - 60;
        int placed = 0;
        for (int i = 0; i < picked.size(); i++) {
            BlockDefinition definition = picked.get(i);
            Block block = world.getBlockAt(baseX + i % 16, y + i / 256, baseZ + i / 16 % 16);
            if (!blocks.place(definition.key(), block.getLocation())) {
                return "cannot place " + definition.key();
            }
            placed++;
            String id = new Appearances.Look(definition.key(), "", "NORTH").id();
            BlockLook expected = looks.lookFor(carrierOf(definition), id);
            BlockLook read = blocks.lookOf(block);
            if (!expected.equals(read)) {
                return "read back " + read + " for " + id + ", expected " + expected;
            }
            if (blocks.expectedLookId(block) != expected.look()) {
                return "index and table disagree about " + id;
            }
            if (!blocks.remove(block, false) || blocks.lookOf(block) != null) {
                return "cannot remove " + definition.key();
            }
        }
        return placed == picked.size() && placed > 0 ? null : "placed " + placed;
    }
}
