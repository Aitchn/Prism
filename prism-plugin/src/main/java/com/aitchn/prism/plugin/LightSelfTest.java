package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.core.block.Appearances;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Light;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;

/**
 * Optional startup check of block light ({@code prism:light}), enabled with
 * {@code -Dprism.selftest.light=<lit block>,<other block>}; the smoke test passes
 * {@code examplemod:ruby_counter,examplemod:ruby_block}. The lit block must be dark in its first variant and glow in
 * {@code active}. Places it in the air, switches it on, builds over its light, switches it off and on, then removes
 * it, checking the light block after each step.
 */
final class LightSelfTest implements Listener {

    private final PrismKey lit;
    private final PrismKey other;
    private final PrismBlockService blocks;
    private final PlatformScheduler scheduler;
    private final Logger log;

    LightSelfTest(String spec, PrismBlockService blocks, PlatformScheduler scheduler, Logger log) {
        String[] parts = spec.split(",");
        this.lit = PrismKey.parse(parts[0]);
        this.other = PrismKey.parse(parts.length > 1 ? parts[1] : parts[0]);
        this.blocks = blocks;
        this.scheduler = scheduler;
        this.log = log;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        if (world == null) {
            log.severe("PRISM_LIGHT_SELFTEST_FAIL no world");
            return;
        }
        Location spawn = world.getSpawnLocation();
        Location at = new Location(world, spawn.getBlockX() + 8, world.getMaxHeight() - 40, spawn.getBlockZ() + 8);
        scheduler.runAtLater(at, () -> run(at.getBlock()), 40);
    }

    private void run(Block block) {
        Block up = block.getRelative(BlockFace.UP);
        Block north = block.getRelative(BlockFace.NORTH);
        try {
            boolean placed = blocks.place(lit, block.getLocation());
            boolean darkFirst = !isLight(up);
            blocks.setVariant(block, "active");
            int level = level(up);
            boolean placedOver = blocks.place(other, up.getLocation());
            boolean moved = level(north) == level && level > 0;
            blocks.setVariant(block, blocks.get(block).map(c -> Appearances.variants(c.definition()).getFirst())
                    .orElse("idle"));
            boolean off = !isLight(north) && !isLight(up);
            blocks.setVariant(block, "active");
            boolean onAgain = isLight(north);
            blocks.remove(block, false);
            boolean gone = !isLight(north);
            blocks.remove(up, false);
            boolean ok = placed && darkFirst && level > 0 && placedOver && moved && off && onAgain && gone;
            log.info((ok ? "PRISM_LIGHT_SELFTEST_OK " : "PRISM_LIGHT_SELFTEST_FAIL ") + lit + " placed=" + placed
                    + " darkFirst=" + darkFirst + " level=" + level + " placedOver=" + placedOver + " moved=" + moved
                    + " off=" + off + " onAgain=" + onAgain + " gone=" + gone);
        } catch (RuntimeException e) {
            log.severe("PRISM_LIGHT_SELFTEST_FAIL " + e);
        }
    }

    private static boolean isLight(Block block) {
        return block.getType() == Material.LIGHT;
    }

    private static int level(Block block) {
        return block.getBlockData() instanceof Light light ? light.getLevel() : 0;
    }
}
