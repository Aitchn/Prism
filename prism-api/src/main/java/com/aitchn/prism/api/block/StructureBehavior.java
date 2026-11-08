package com.aitchn.prism.api.block;

import com.aitchn.prism.api.content.Behavior;
import org.jetbrains.annotations.NotNull;

/**
 * Capability of a structure controller ({@code prism:structure} or {@code prism:cuboid}): runs when its structure
 * forms and when it breaks. Called on the controller's thread. A structure forms when the last missing block is placed
 * (or the controller is placed into a finished frame) and breaks when a member, a cell that must stay air, or the
 * controller is changed or removed. A box that is rebuilt at another size breaks and forms again. When a chunk loads,
 * a structure that was formed before stays formed without a new {@link #onFormed} call, and one that no longer
 * stands gets {@link #onBroken}.
 */
public interface StructureBehavior extends Behavior {

    void onFormed(@NotNull BlockContext context, @NotNull FormedStructure structure);

    void onBroken(@NotNull BlockContext context);
}
