package com.aitchn.prism.validation.mekanism;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;

/**
 * Sounds and particles. They are plain world calls, so they must run on the thread that owns the location (machine
 * ticks and the scheduler's {@code runAt} do). Java and Bedrock (through Geyser) both play vanilla sounds and particles.
 */
final class Effects {

    private Effects() {}

    /** A working machine: a quiet hum every two seconds and a few sparks on top every half second. */
    static void working(Block block, long ticks) {
        Location top = block.getLocation().add(0.5, 1.05, 0.5);
        if (ticks % 40 == 0) {
            block.getWorld().playSound(top, Sound.BLOCK_BEACON_AMBIENT, SoundCategory.BLOCKS, 0.12f, 1.7f);
        }
        if (ticks % 10 == 0) {
            block.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, top, 2, 0.25, 0.0, 0.25, 0.02);
        }
    }

    /** Someone leaves or arrives. */
    static void teleport(Location at) {
        at.getWorld().spawnParticle(Particle.PORTAL, at.clone().add(0, 1.0, 0), 40, 0.4, 0.8, 0.4, 0.3);
        at.getWorld().playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.6f, 1.0f);
    }

    /** The miner took an ore. */
    static void mined(Location at) {
        at.getWorld().spawnParticle(Particle.END_ROD, at.clone().add(0.5, 0.5, 0.5), 6, 0.3, 0.3, 0.3, 0.02);
    }
}
