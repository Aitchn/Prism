package com.aitchn.prism.core.block;

import com.aitchn.prism.api.content.PrismComponents.Mining;
import java.util.Locale;

/**
 * Break time of a custom block in survival, close to vanilla: {@code hardness} seconds by hand, divided by the tool
 * speed when the right tool type is used, plus efficiency, haste and mining fatigue, five times slower in the air or
 * under water.
 */
public final class DigMath {

    /** Never finishes. */
    public static final int UNBREAKABLE = -1;

    private DigMath() {}

    /**
     * @param heldMaterial the held item's material name, for example {@code DIAMOND_PICKAXE}; any name is accepted
     * @return ticks until the block breaks, 0 for instant, {@link #UNBREAKABLE} if it cannot be broken
     */
    public static int ticksToBreak(Mining mining, String heldMaterial, int efficiency, int haste, int fatigue,
            boolean onGround, boolean inWater) {
        if (mining.hardness() < 0) {
            return UNBREAKABLE;
        }
        if (mining.hardness() == 0) {
            return 0;
        }
        double speed = 1.0;
        boolean rightTool = !"none".equals(mining.tool()) && toolType(heldMaterial).equals(mining.tool());
        if (rightTool) {
            speed = tierSpeed(heldMaterial);
            if (efficiency > 0) {
                speed += efficiency * efficiency + 1;
            }
        }
        if (haste > 0) {
            speed *= 1 + 0.2 * haste;
        }
        if (fatigue > 0) {
            speed *= Math.pow(0.3, Math.min(fatigue, 4));
        }
        if (!onGround) {
            speed /= 5;
        }
        if (inWater) {
            speed /= 5;
        }
        return Math.max(1, (int) Math.ceil(mining.hardness() * 20 / speed));
    }

    /** {@code pickaxe}, {@code axe}, ... for a tool material name, or {@code none}. */
    public static String toolType(String material) {
        String name = material.toLowerCase(Locale.ROOT);
        for (String tool : new String[] {"pickaxe", "shovel", "sword", "hoe", "axe"}) {
            if (name.endsWith("_" + tool)) {
                return tool;
            }
        }
        return "none";
    }

    /** Vanilla tool tier speed by material prefix; 1 for anything unknown. */
    public static double tierSpeed(String material) {
        String name = material.toLowerCase(Locale.ROOT);
        if (name.startsWith("wooden_")) return 2;
        if (name.startsWith("stone_")) return 4;
        if (name.startsWith("copper_")) return 5;
        if (name.startsWith("iron_")) return 6;
        if (name.startsWith("diamond_")) return 8;
        if (name.startsWith("netherite_")) return 9;
        if (name.startsWith("golden_")) return 12;
        return 1;
    }

    /** Crack stage 0..9 shown for {@code progress} in [0, 1). */
    public static int stage(double progress) {
        return Math.max(0, Math.min(9, (int) (progress * 10)));
    }
}
