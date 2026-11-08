package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.api.block.FormedStructure;
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
 * Optional startup check of box structures ({@code prism:cuboid}), enabled with
 * {@code -Dprism.selftest.cuboid=<controller>,<wall>}; the smoke test passes
 * {@code examplemod:ruby_tank,examplemod:ruby_block}. Builds a hollow 4x3x3 box of {@code wall} with the controller in
 * the middle of its roof, which must switch the controller to its {@code formed} variant (the example's behavior).
 * Then fills and clears the inside, and finally removes the controller.
 */
final class CuboidSelfTest implements Listener {

    private final PrismKey controller;
    private final PrismKey wall;
    private final PrismBlockService blocks;
    private final PlatformScheduler scheduler;
    private final Logger log;

    CuboidSelfTest(String spec, PrismBlockService blocks, PlatformScheduler scheduler, Logger log) {
        String[] parts = spec.split(",");
        this.controller = PrismKey.parse(parts[0]);
        this.wall = PrismKey.parse(parts.length > 1 ? parts[1] : parts[0]);
        this.blocks = blocks;
        this.scheduler = scheduler;
        this.log = log;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        if (world == null) {
            log.severe("PRISM_CUBOID_SELFTEST_FAIL no world");
            return;
        }
        Location spawn = world.getSpawnLocation();
        Location at = new Location(world, spawn.getBlockX() - 8, world.getMaxHeight() - 40, spawn.getBlockZ() + 8);
        scheduler.runAtLater(at, () -> run(at.getBlock()), 40);
    }

    /** {@code corner} is the low corner of the box: x 0..3, y 0..2, z 0..2. */
    private void run(Block corner) {
        Block top = corner.getRelative(1, 2, 1);
        Block inside = corner.getRelative(1, 1, 1); // right under the controller: no smaller box can form
        List<Block> shell = new ArrayList<>();
        for (int x = 0; x <= 3; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 0; z <= 2; z++) {
                    Block block = corner.getRelative(x, y, z);
                    boolean edge = x == 0 || x == 3 || y == 0 || y == 2 || z == 0 || z == 2;
                    if (edge && !block.equals(top)) {
                        shell.add(block);
                    }
                }
            }
        }
        try {
            boolean placed = blocks.place(controller, top.getLocation());
            for (Block block : shell.subList(0, shell.size() - 1)) {
                placed &= blocks.place(wall, block.getLocation());
            }
            boolean early = blocks.structure(top).isPresent();
            placed &= blocks.place(wall, shell.getLast().getLocation());
            FormedStructure formed = blocks.structure(top).orElse(null);
            int members = formed == null ? 0 : formed.members().size();
            boolean shape = formed != null && formed.facing() == BlockFace.DOWN && formed.min().equals(corner)
                    && formed.max().equals(corner.getRelative(3, 2, 2));
            boolean formedLook = variant(top).equals("formed");
            boolean memberLink = blocks.controllerOf(shell.getFirst()).map(top::equals).orElse(false);
            blocks.place(wall, inside.getLocation());
            boolean filled = blocks.structure(top).isEmpty() && variant(top).equals("idle");
            blocks.remove(inside, false);
            boolean reformed = blocks.structure(top).isPresent() && variant(top).equals("formed");
            blocks.remove(top, false);
            boolean released = blocks.controllerOf(shell.getFirst()).isEmpty();
            shell.forEach(block -> blocks.remove(block, false));
            boolean ok = placed && !early && members == shell.size() && shape && formedLook && memberLink && filled
                    && reformed && released;
            log.info((ok ? "PRISM_CUBOID_SELFTEST_OK " : "PRISM_CUBOID_SELFTEST_FAIL ") + controller + " placed="
                    + placed + " earlyForm=" + early + " members=" + members + "/" + shell.size() + " shape=" + shape
                    + " formedLook=" + formedLook + " memberLink=" + memberLink + " filled=" + filled
                    + " reformed=" + reformed + " released=" + released);
        } catch (RuntimeException e) {
            log.severe("PRISM_CUBOID_SELFTEST_FAIL " + e);
        }
    }

    private String variant(Block block) {
        return blocks.get(block).map(CustomBlock::variant).orElse("none");
    }
}
