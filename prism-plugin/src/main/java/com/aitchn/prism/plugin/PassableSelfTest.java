package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Optional startup check of passable blocks, enabled with {@code -Dprism.selftest.passable=<block key>}; the smoke
 * test passes {@code examplemod:ruby_flower}. Places two side by side on stone, puts stone next to one with physics
 * and drops an item onto the other, then checks a few ticks later that both still show their own tripwire state
 * (vanilla string would have connected or been pressed) and can be walked through.
 */
final class PassableSelfTest implements Listener {

    private final PrismKey key;
    private final PrismBlockService blocks;
    private final PlatformScheduler scheduler;
    private final Logger log;

    PassableSelfTest(PrismKey key, PrismBlockService blocks, PlatformScheduler scheduler, Logger log) {
        this.key = key;
        this.blocks = blocks;
        this.scheduler = scheduler;
        this.log = log;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        if (world == null) {
            log.severe("PRISM_PASSABLE_SELFTEST_FAIL no world");
            return;
        }
        Location spawn = world.getSpawnLocation();
        Location at = new Location(world, spawn.getBlockX() - 6, world.getMaxHeight() - 30, spawn.getBlockZ() - 6);
        scheduler.runAtLater(at, () -> run(at.getBlock()), 40);
    }

    private void run(Block a) {
        Block b = a.getRelative(BlockFace.EAST);
        try {
            a.getRelative(BlockFace.DOWN).setType(Material.STONE, false);
            b.getRelative(BlockFace.DOWN).setType(Material.STONE, false);
            boolean placed = blocks.place(key, a.getLocation()) && blocks.place(key, b.getLocation());
            BlockData before = a.getBlockData();
            boolean carrier = a.getType() == Material.TRIPWIRE && b.getType() == Material.TRIPWIRE;
            a.getRelative(BlockFace.WEST).setType(Material.STONE, true);
            blocks.notifyNeighbors(a.getRelative(BlockFace.WEST));
            Item dropped = b.getWorld().dropItem(b.getLocation().add(0.5, 0.1, 0.5), new ItemStack(Material.STICK));
            scheduler.runAtLater(a.getLocation(), () -> check(a, b, before, placed, carrier, dropped), 10);
        } catch (RuntimeException e) {
            log.severe("PRISM_PASSABLE_SELFTEST_FAIL " + e);
        }
    }

    private void check(Block a, Block b, BlockData before, boolean placed, boolean carrier, Item dropped) {
        try {
            boolean kept = a.getBlockData().equals(before) && b.getBlockData().equals(before);
            boolean passable = a.isPassable() && b.isPassable();
            boolean indexed = blocks.identify(a).isPresent() && blocks.identify(b).isPresent();
            String state = a.getBlockData().getAsString() + " / " + b.getBlockData().getAsString();
            dropped.remove();
            blocks.remove(a, false);
            blocks.remove(b, false);
            a.getRelative(BlockFace.WEST).setType(Material.AIR, false);
            boolean ok = placed && carrier && kept && passable && indexed;
            log.info((ok ? "PRISM_PASSABLE_SELFTEST_OK " : "PRISM_PASSABLE_SELFTEST_FAIL ") + key + " placed=" + placed
                    + " tripwire=" + carrier + " kept=" + kept + " passable=" + passable + " indexed=" + indexed
                    + " state=" + state);
        } catch (RuntimeException e) {
            log.severe("PRISM_PASSABLE_SELFTEST_FAIL " + e);
        }
    }
}
