package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;

/**
 * Optional startup check of ore generation, enabled with {@code -Dprism.selftest.ore=<block key>}; the smoke test
 * passes {@code examplemod:ruby_ore}. Generates a chunk far from spawn and checks that it holds indexed, visible
 * blocks of that key. The block must have a {@code prism:ore} spec common enough to appear in every chunk.
 */
final class OreSelfTest implements Listener {

    private final PrismKey key;
    private final PrismBlockService blocks;
    private final PlatformScheduler scheduler;
    private final Logger log;

    OreSelfTest(PrismKey key, PrismBlockService blocks, PlatformScheduler scheduler, Logger log) {
        this.key = key;
        this.blocks = blocks;
        this.scheduler = scheduler;
        this.log = log;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        if (world == null) {
            log.severe("PRISM_ORE_SELFTEST_FAIL no world");
            return;
        }
        // Far from spawn, so the chunk is certainly generated now, with Prism running.
        int chunkX = (world.getSpawnLocation().getBlockX() >> 4) + 200;
        int chunkZ = world.getSpawnLocation().getBlockZ() >> 4;
        Location center = new Location(world, (chunkX << 4) + 8, 64, (chunkZ << 4) + 8);
        world.getChunkAtAsync(chunkX, chunkZ, true).whenComplete((chunk, error) -> {
            if (error != null || chunk == null) {
                log.severe("PRISM_ORE_SELFTEST_FAIL " + (error == null ? "no chunk" : error.toString()));
                return;
            }
            scheduler.runAt(center, () -> check(world, chunkX, chunkZ));
        });
    }

    private void check(World world, int chunkX, int chunkZ) {
        try {
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                log.severe("PRISM_ORE_SELFTEST_FAIL chunk unloaded before the check");
                return;
            }
            Chunk chunk = world.getChunkAt(chunkX, chunkZ);
            List<CustomBlock> ores = blocks.blocksIn(chunk).stream().filter(b -> b.key().equals(key)).toList();
            boolean visible = !ores.isEmpty() && ores.stream().allMatch(b -> b.block().getType() == Material.NOTE_BLOCK);
            int targets = (int) blocks.oreTargets().stream().filter(t -> t.block().equals(key)).count();
            boolean ok = targets > 0 && visible;
            // What the ore could replace, to tell "no stone here" from "generation did not run".
            String sample = chunk.getBlock(8, -40, 8).getType() + "," + chunk.getBlock(8, -20, 8).getType();
            log.info((ok ? "PRISM_ORE_SELFTEST_OK " : "PRISM_ORE_SELFTEST_FAIL ") + key + " targets=" + targets
                    + " blocks=" + ores.size() + " noteBlocks=" + visible + " chunk=" + chunkX + "," + chunkZ + " sample=" + sample + " " + blocks.oreStats());
        } catch (RuntimeException e) {
            log.severe("PRISM_ORE_SELFTEST_FAIL " + e);
        }
    }
}
