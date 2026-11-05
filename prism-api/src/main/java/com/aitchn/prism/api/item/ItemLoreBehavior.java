package com.aitchn.prism.api.item;

import com.aitchn.prism.api.content.Behavior;
import java.util.List;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Capability: lore lines computed from one stack, for example a charge or a stored amount kept in the stack's
 * persistent data. Prism renders them below the item's static {@code prism:lore} when the stack is created and every
 * time it changes through {@link ItemService#editData}; call {@link ItemService#refresh} after changing the stack
 * another way. Lore is part of the stack, so Java and Bedrock (through Geyser) both show it.
 */
public interface ItemLoreBehavior extends Behavior {

    /** MiniMessage lines for {@code stack}. Must not change {@code stack}. */
    @NotNull List<String> lore(@NotNull ItemStack stack);
}
