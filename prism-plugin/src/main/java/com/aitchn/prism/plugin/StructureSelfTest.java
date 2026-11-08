package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;

/**
 * Optional startup check of multiblock structures, enabled with {@code -Dprism.selftest.structure=<core>,<floor>};
 * the smoke test passes {@code examplemod:ruby_core,examplemod:ruby_block}. {@code core} must form on top of the middle
 * of a 3x3 floor of {@code floor} and switch to its {@code formed} variant when it does (the example's behavior).
 * Places the core first and the floor after, then breaks and restores a floor block, then removes the core.
 */
final class StructureSelfTest implements Listener {

    private final PrismKey core;
    private final PrismKey floor;
    private final PrismBlockService blocks;
    private final PlatformScheduler scheduler;
    private final Logger log;

    StructureSelfTest(String spec, PrismBlockService blocks, PlatformScheduler scheduler, Logger log) {
        String[] parts = spec.split(",");
        this.core = PrismKey.parse(parts[0]);
        this.floor = PrismKey.parse(parts.length > 1 ? parts[1] : parts[0]);
        this.blocks = blocks;
        this.scheduler = scheduler;
        this.log = log;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        if (world == null) {
            log.severe("PRISM_STRUCTURE_SELFTEST_FAIL no world");
            return;
        }
        Location spawn = world.getSpawnLocation();
        Location at = new Location(world, spawn.getBlockX() + 6, world.getMaxHeight() - 40, spawn.getBlockZ() - 6);
        scheduler.runAtLater(at, () -> run(at.getBlock()), 40);
    }

    private void run(Block center) {
        Block top = center.getRelative(BlockFace.UP);
        List<Block> floorBlocks = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                floorBlocks.add(center.getRelative(dx, 0, dz));
            }
        }
        try {
            boolean placed = blocks.place(core, top.getLocation());
            for (Block block : floorBlocks.subList(0, 8)) {
                placed &= blocks.place(floor, block.getLocation());
            }
            boolean early = blocks.structure(top).isPresent();
            placed &= blocks.place(floor, floorBlocks.get(8).getLocation());
            int members = blocks.structure(top).map(s -> s.members().size()).orElse(0);
            boolean formedLook = variant(top).equals("formed");
            boolean memberLink = blocks.controllerOf(floorBlocks.get(0)).map(top::equals).orElse(false);
            blocks.remove(floorBlocks.get(4), false);
            boolean broken = blocks.structure(top).isEmpty() && variant(top).equals("idle");
            blocks.place(floor, floorBlocks.get(4).getLocation());
            boolean reformed = blocks.structure(top).isPresent();
            blocks.remove(top, false);
            boolean released = blocks.controllerOf(floorBlocks.get(0)).isEmpty();
            floorBlocks.forEach(block -> blocks.remove(block, false));
            boolean ok = placed && !early && members == 9 && formedLook && memberLink && broken && reformed && released;
            log.info((ok ? "PRISM_STRUCTURE_SELFTEST_OK " : "PRISM_STRUCTURE_SELFTEST_FAIL ") + core + " placed="
                    + placed + " earlyForm=" + early + " members=" + members + " formedLook=" + formedLook
                    + " memberLink=" + memberLink + " broken=" + broken + " reformed=" + reformed
                    + " released=" + released);
        } catch (RuntimeException e) {
            log.severe("PRISM_STRUCTURE_SELFTEST_FAIL " + e);
        }
    }

    private String variant(Block block) {
        return blocks.get(block).map(CustomBlock::variant).orElse("none");
    }
}
