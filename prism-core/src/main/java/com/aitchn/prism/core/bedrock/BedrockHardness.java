package com.aitchn.prism.core.bedrock;

import com.aitchn.prism.api.content.PrismComponents;
import org.jetbrains.annotations.NotNull;

/**
 * The hardness Geyser is told for a synthetic state, from {@code prism:mining}. Geyser computes the break time of a
 * Bedrock player like vanilla: {@code destroyTime * 1.5} seconds by hand when the block does not need a tool, then
 * divided by the tool speed of the held item for the blocks the item's tool rules name. A synthetic block is in no
 * tool tag, so tools do not speed it up for a Bedrock player (a documented difference from Java).
 */
public final class BedrockHardness {

    /** Seconds by hand per unit of vanilla hardness when no tool is needed. */
    private static final double SECONDS_PER_HARDNESS = 1.5;

    /**
     * @param destroyTime       vanilla hardness: {@code -1} cannot be broken, {@code 0} breaks instantly
     * @param canBreakWithHand  always true: a custom block never needs a tool to be broken
     */
    public record Hardness(float destroyTime, boolean canBreakWithHand) {}

    private BedrockHardness() {}

    public static @NotNull Hardness of(@NotNull PrismComponents.Mining mining) {
        if (mining.hardness() < 0) {
            return new Hardness(-1f, true);
        }
        return new Hardness((float) (mining.hardness() / SECONDS_PER_HARDNESS), true);
    }
}
