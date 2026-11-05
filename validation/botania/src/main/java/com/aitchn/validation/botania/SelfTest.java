package com.aitchn.validation.botania;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.scheduler.PrismTask;
import com.aitchn.validation.botania.logic.Flower;
import com.aitchn.validation.botania.logic.Pool;
import com.aitchn.validation.botania.logic.Spreader;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * Real-server check of the whole mana chain at the world spawn: a Daybloom feeds a Spreader, the Spreader shoots a
 * burst along +x and a Mana Pool four blocks away must receive it. Logs BOTANIA_SELFTEST_OK or _FAIL.
 */
final class SelfTest {

    private static final int POLLS = 40;

    private final BotaniaRuntime runtime;

    SelfTest(BotaniaRuntime runtime) {
        this.runtime = runtime;
    }

    void start(World world) {
        Location spawn = world.getSpawnLocation();
        Prism.scheduler().runAt(spawn, () -> run(world, spawn));
    }

    private void run(World world, Location spawn) {
        try {
            int x = spawn.getBlockX();
            int y = spawn.getBlockY();
            int z = spawn.getBlockZ();
            Block spreaderBlock = world.getBlockAt(x, y, z);
            Block poolBlock = world.getBlockAt(x + 4, y, z);
            Block flowerBlock = world.getBlockAt(x, y, z + 2);
            // Facing WEST means the front is on the west side, so the spreader shoots EAST (+x).
            boolean placed = Prism.blocks().place(key("mana_spreader"), spreaderBlock.getLocation(), null, BlockFace.WEST)
                    && Prism.blocks().place(key("mana_pool"), poolBlock.getLocation())
                    && Prism.blocks().place(key("daybloom"), flowerBlock.getLocation());
            if (!placed) {
                fail("could not place the blocks");
                return;
            }
            // Prism runs the place behaviors, which create the entities; ensure() just finds them (or creates them).
            var spreader = (Spreader) runtime.ensure(spreaderBlock, "spreader");
            var pool = (Pool) runtime.ensure(poolBlock, "pool");
            var flower = (Flower) runtime.ensure(flowerBlock, "flower:daybloom");
            spreader.receive(Spreader.BURST_MANA - 40); // the Daybloom has to supply the rest
            if (!flower.bind(spreader.pos())) {
                fail("flower could not bind to the spreader");
                return;
            }
            int[] polls = {0};
            PrismTask[] handle = new PrismTask[1];
            handle[0] = Prism.scheduler().runAtTimer(spawn, () -> {
                polls[0]++;
                if (pool.mana() > 0) {
                    handle[0].cancel();
                    Bukkit.getLogger().info("BOTANIA_SELFTEST_OK poolMana=" + pool.mana() + " polls=" + polls[0]);
                    Prism.blocks().remove(spreaderBlock, false);
                    Prism.blocks().remove(poolBlock, false);
                    Prism.blocks().remove(flowerBlock, false);
                } else if (polls[0] >= POLLS) {
                    handle[0].cancel();
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
