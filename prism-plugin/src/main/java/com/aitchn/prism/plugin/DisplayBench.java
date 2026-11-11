package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.mod.ModContext;
import com.aitchn.prism.api.mod.PrismMod;
import com.aitchn.prism.core.content.MemoryContentSource;
import com.aitchn.prism.core.mod.ModCandidate;
import com.aitchn.prism.core.mod.ModManager;
import com.aitchn.prism.core.mod.ModReport;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /prism bench displays <count> [radius]} and {@code /prism bench clear}: a load test of the display layer
 * (ADR-0025). Places up to {@code count} blocks of a built-in test-only look ({@code prismbench:lattice}, registered on
 * first use) in a sparse lattice above the player, every second position on every axis, so no block hides another and
 * the cull removes nothing. Only air in loaded chunks is used. Reports how many display entities the player is sent and
 * the server's mean tick time before and about five seconds after. {@code clear} removes every block it placed.
 */
final class DisplayBench {

    private static final String NAMESPACE = "prismbench";
    private static final PrismKey BLOCK = new PrismKey(NAMESPACE, "lattice");
    private static final int DEFAULT_RADIUS = 48;
    private static final int MAX_COUNT = 1_000_000;
    private static final long SETTLE_TICKS = 100;

    private final Plugin plugin;
    private final ModManager mods;
    private final Runnable contentChanged;
    private final PrismBlockService blocks;
    private final DisplayLayer layer;
    private final PlatformScheduler scheduler;
    /** Every block the bench placed, by world; removed by {@code clear}. */
    private final Map<java.util.UUID, List<long[]>> placed = new ConcurrentHashMap<>();
    private volatile boolean registered;

    DisplayBench(Plugin plugin, ModManager mods, Runnable contentChanged, PrismBlockService blocks, DisplayLayer layer,
            PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.mods = mods;
        this.contentChanged = contentChanged;
        this.blocks = blocks;
        this.layer = layer;
        this.scheduler = scheduler;
    }

    void run(CommandSender sender, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "displays" -> displays(sender, args);
            case "clear" -> clear(sender);
            default -> sender.sendMessage(Component.text(
                    "/prism bench displays <count> [radius] | /prism bench clear", NamedTextColor.GRAY));
        }
    }

    private void displays(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can run the display bench.", NamedTextColor.RED));
            return;
        }
        int count;
        int radius;
        try {
            count = Integer.parseInt(args[2]);
            radius = args.length > 3 ? Integer.parseInt(args[3]) : DEFAULT_RADIUS;
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            sender.sendMessage(Component.text("/prism bench displays <count> [radius]", NamedTextColor.GRAY));
            return;
        }
        if (count < 1 || count > MAX_COUNT || radius < 1 || radius > 512) {
            sender.sendMessage(Component.text("count must be 1 to " + MAX_COUNT + ", radius 1 to 512",
                    NamedTextColor.RED));
            return;
        }
        scheduler.runGlobal(() -> {
            String problem = register();
            if (problem != null) {
                sender.sendMessage(Component.text("Cannot register the bench block: " + problem, NamedTextColor.RED));
                return;
            }
            scheduler.runOn(player, () -> start(player, count, radius), null);
        });
    }

    /** Registers the test-only block once: a solid block drawn by the display layer, with a checker texture. */
    private synchronized String register() {
        if (registered) {
            return null;
        }
        MemoryContentSource source = new MemoryContentSource();
        source.put("prism/blocks/lattice.yml", "base: minecraft:barrier\ncomponents:\n  prism:render: display\n");
        try {
            source.put("assets/" + NAMESPACE + "/textures/block/lattice.png", checker());
        } catch (IOException e) {
            return e.toString();
        }
        ModReport report = mods.activate(new ModCandidate("prism-bench", NAMESPACE, "prism", source, new PrismMod() {
            @Override
            public void onInitialize(@NotNull ModContext context) {}
        }, plugin.getLogger()));
        if (!report.active() || report.blockCount() != 1) {
            return "registered " + report.blockCount() + " blocks: " + report.problems();
        }
        contentChanged.run();
        registered = true;
        return null;
    }

    private static byte[] checker() throws IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                image.setRGB(x, y, ((x >> 2) + (y >> 2)) % 2 == 0 ? 0xFFE040FB : 0xFF202020);
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        return png.toByteArray();
    }

    /** On the player's thread: picks the lattice positions, then places them chunk by chunk on each chunk's thread. */
    private void start(Player player, int count, int radius) {
        World world = player.getWorld();
        Location at = player.getLocation();
        int entitiesBefore = layer.entityCount(player);
        String msptBefore = mspt();
        Map<Long, List<int[]>> byChunk = new LinkedHashMap<>();
        int chosen = 0;
        int top = world.getMaxHeight() - 1;
        for (int y = at.getBlockY() + 3; y <= top && chosen < count; y += 2) {
            for (int dx = -radius; dx <= radius && chosen < count; dx += 2) {
                for (int dz = -radius; dz <= radius && chosen < count; dz += 2) {
                    int x = at.getBlockX() + dx;
                    int z = at.getBlockZ() + dz;
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                        continue;
                    }
                    byChunk.computeIfAbsent(Chunk.getChunkKey(x >> 4, z >> 4), k -> new ArrayList<>())
                            .add(new int[] {x, y, z});
                    chosen++;
                }
            }
        }
        if (chosen == 0) {
            player.sendMessage(Component.text("No room above you in loaded chunks.", NamedTextColor.RED));
            return;
        }
        AtomicInteger done = new AtomicInteger();
        AtomicInteger made = new AtomicInteger();
        int groups = byChunk.size();
        List<long[]> mine = placed.computeIfAbsent(world.getUID(), id -> java.util.Collections.synchronizedList(
                new ArrayList<>()));
        for (List<int[]> group : byChunk.values()) {
            int[] first = group.getFirst();
            scheduler.runAt(new Location(world, first[0], first[1], first[2]), () -> {
                if (world.isChunkLoaded(first[0] >> 4, first[2] >> 4)) {
                    for (int[] p : group) {
                        Block block = world.getBlockAt(p[0], p[1], p[2]);
                        if (block.getType().isAir() && blocks.place(BLOCK, block.getLocation())) {
                            mine.add(new long[] {p[0], p[1], p[2]});
                            made.incrementAndGet();
                        }
                    }
                }
                if (done.incrementAndGet() == groups) {
                    scheduler.runOn(player, () -> report(player, made.get(), entitiesBefore, msptBefore), null);
                }
            });
        }
    }

    private void report(Player player, int made, int entitiesBefore, String msptBefore) {
        player.sendMessage(Component.text("Placed " + made + " bench blocks; measuring for " + SETTLE_TICKS / 20
                + " s.", NamedTextColor.GRAY));
        Location at = player.getLocation();
        scheduler.runAtLater(at, () -> scheduler.runOn(player, () -> {
            int entitiesAfter = layer.entityCount(player);
            String line = "PRISM_BENCH_DISPLAYS placed=" + made + " entities=" + entitiesBefore + "->" + entitiesAfter
                    + " budget=" + layer.budget() + " lighting=" + layer.lighting().id() + " mspt=" + msptBefore + "->"
                    + mspt();
            plugin.getLogger().info(line);
            player.sendMessage(Component.text(line, NamedTextColor.GREEN));
        }, null), SETTLE_TICKS);
    }

    private void clear(CommandSender sender) {
        int total = 0;
        for (Map.Entry<java.util.UUID, List<long[]>> entry : placed.entrySet()) {
            World world = Bukkit.getWorld(entry.getKey());
            List<long[]> positions;
            synchronized (entry.getValue()) {
                positions = new ArrayList<>(entry.getValue());
                entry.getValue().clear();
            }
            if (world == null) {
                continue;
            }
            total += positions.size();
            Map<Long, List<long[]>> byChunk = new LinkedHashMap<>();
            for (long[] p : positions) {
                byChunk.computeIfAbsent(Chunk.getChunkKey((int) p[0] >> 4, (int) p[2] >> 4), k -> new ArrayList<>())
                        .add(p);
            }
            for (List<long[]> group : byChunk.values()) {
                long[] first = group.getFirst();
                scheduler.runAt(new Location(world, first[0], first[1], first[2]), () -> {
                    for (long[] p : group) {
                        Block block = world.getBlockAt((int) p[0], (int) p[1], (int) p[2]);
                        if (blocks.identify(block).filter(BLOCK::equals).isPresent()) {
                            blocks.remove(block, false);
                        }
                    }
                });
            }
        }
        sender.sendMessage(Component.text("Removing " + total + " bench blocks.", NamedTextColor.GREEN));
    }

    /** The server's mean tick time in milliseconds, or n/a where the platform has none (Folia's regions). */
    private static String mspt() {
        try {
            return String.format(Locale.ROOT, "%.2f", Bukkit.getServer().getAverageTickTime());
        } catch (RuntimeException e) {
            return "n/a";
        }
    }
}
