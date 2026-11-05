package com.aitchn.prism.core.block;

import com.aitchn.prism.api.content.PrismComponents.Rotation;

/** Which way a block faces when a player places it. Pure math; names match {@code org.bukkit.block.BlockFace}. */
public final class Facings {

    private Facings() {}

    /**
     * @param yaw   the placer's yaw in degrees (0 = looking south, 90 = west, like Minecraft)
     * @param pitch the placer's pitch in degrees (positive = looking down)
     * @return NORTH, EAST, SOUTH, WEST, UP or DOWN; the block faces the player, like a furnace or dispenser
     */
    public static String facing(Rotation mode, float yaw, float pitch) {
        if (mode == Rotation.NONE) {
            return "NORTH";
        }
        if (mode == Rotation.ALL) {
            if (pitch > 45) {
                return "UP";
            }
            if (pitch < -45) {
                return "DOWN";
            }
        }
        int quarter = Math.floorMod(Math.round(yaw / 90f), 4);
        // The player looks south (0), west (1), north (2), east (3); the block faces back at them.
        return switch (quarter) {
            case 0 -> "NORTH";
            case 1 -> "EAST";
            case 2 -> "SOUTH";
            default -> "WEST";
        };
    }

    /** Yaw in degrees for a display entity that should face {@code facing} (NORTH = 180, like an entity). */
    public static float yawOf(String facing) {
        return switch (facing) {
            case "SOUTH" -> 0f;
            case "WEST" -> 90f;
            case "EAST" -> -90f;
            default -> 180f;
        };
    }

    /** Pitch in degrees for a display entity that should face {@code facing}. */
    public static float pitchOf(String facing) {
        return switch (facing) {
            case "UP" -> -90f;
            case "DOWN" -> 90f;
            default -> 0f;
        };
    }
}
