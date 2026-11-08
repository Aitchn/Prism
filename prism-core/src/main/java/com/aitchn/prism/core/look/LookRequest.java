package com.aitchn.prism.core.look;

import com.aitchn.prism.api.content.PrismComponents;
import org.jetbrains.annotations.NotNull;

/**
 * One look that needs a client representation.
 *
 * @param id          {@code Appearances.Look#id()}
 * @param physics     the collision, occlusion and sound the look must have for a client
 * @param render      the block's {@code prism:render} preference
 * @param bedrockOnly Java players never see this look (the block is drawn by an entity for them); it only needs a note
 *                    block state for Bedrock players, so it never goes to the display layer
 */
public record LookRequest(@NotNull String id, @NotNull PhysicsClass physics, @NotNull PrismComponents.Render render,
        boolean bedrockOnly) {}
