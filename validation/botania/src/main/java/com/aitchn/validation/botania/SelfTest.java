package com.aitchn.validation.botania;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.PrismKey;
import com.aitchn.validation.botania.logic.Flower;
import com.aitchn.validation.botania.logic.Pool;
import com.aitchn.validation.botania.logic.Spreader;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

/**
 * Real-server check of the whole mana chain at the world spawn: a Daybloom feeds a Spreader, the Spreader shoots a
 * burst along +x and a Mana Pool four blocks away must receive it. Logs BOTANIA_SELFTEST_OK or _FAIL.
 */
final class SelfTest {

    private static final int POLLS = 40;

    private final Plugin plugin;
    private final BotaniaRuntime runtime;

    SelfTest(Plugin plugin, BotaniaRuntime runtime) {
        this.plugin = plugin;
        this.runtime = runtime;
    }

    void start(World world) {
        Location spawn = world.getSpawnLocation();
        Bukkit.getRegionScheduler().execute(plugin, spawn, () -> run(world, spawn));
    }

    private void run(World world, Location spawn) {
        try {
            int x = spawn.getBlockX();
            int y = spawn.getBlockY();
            int z = spawn.getBlockZ();
            Block spreaderBlock = world.getBlockAt(x, y, z);
            Block poolBlock = world.getBlockAt(x + 4, y, z);
            Block flowerBlock = world.getBlockAt(x, y, z + 2);
            boolean placed = Prism.blocks().place(key("mana_spreader"), spreaderBlock.getLocation())
                    && Prism.blocks().place(key("mana_pool"), poolBlock.getLocation())
                    && Prism.blocks().place(key("daybloom"), flowerBlock.getLocation());
            if (!placed) {
                fail("could not place the blocks");
                return;
            }
            // Placing through BlockService does not run place behaviors, so register the entities like the behavior would.
            var spreader = (Spreader) runtime.ensure(spreaderBlock, "spreader", null);
            var pool = (Pool) runtime.ensure(poolBlock, "pool", null);
            var flower = (Flower) runtime.ensure(flowerBlock, "flower:daybloom", null);
            spreader.aim(-90f, 0f); // yaw -90 looks towards +x
            spreader.receive(Spreader.BURST_MANA - 40); // the Daybloom has to supply the rest
            if (!flower.bind(spreader.pos())) {
                fail("flower could not bind to the spreader");
                return;
            }
            int[] polls = {0};
            Bukkit.getRegionScheduler().runAtFixedRate(plugin, spawn, task -> {
                polls[0]++;
                if (pool.mana() > 0) {
                    task.cancel();
                    Bukkit.getLogger().info("BOTANIA_SELFTEST_OK poolMana=" + pool.mana() + " polls=" + polls[0]);
                    runtime.discard(spreaderBlock);
                    runtime.discard(poolBlock);
                    runtime.discard(flowerBlock);
                    Prism.blocks().remove(spreaderBlock, false);
                    Prism.blocks().remove(poolBlock, false);
                    Prism.blocks().remove(flowerBlock, false);
                } else if (polls[0] >= POLLS) {
                    task.cancel();
                    fail("pool stayed empty: spreader=" + spreader.mana() + " flower=" + flower.buffer());
                }
            }, 20L, 20L);
        } catch (RuntimeException e) {
            fail(e.toString());
        }
    }

    private static PrismKey key(String value) {
        return new PrismKey("botania", value);
    }

    private static void fail(String reason) {
        Bukkit.getLogger().severe("BOTANIA_SELFTEST_FAIL " + reason);
    }
}
