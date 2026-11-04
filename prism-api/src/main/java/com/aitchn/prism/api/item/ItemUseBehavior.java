package com.aitchn.prism.api.item;

import com.aitchn.prism.api.content.Behavior;
import org.jetbrains.annotations.NotNull;

/** Capability: runs when a player uses (right clicks with) the item. Called on the thread that owns the player. */
public interface ItemUseBehavior extends Behavior {

    void onUse(@NotNull ItemUseContext context);
}
