package com.aitchn.prism.api.item;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ItemDefinition;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Creates and recognises custom item stacks. A stack carries only identity; appearance is decided per viewer. */
public interface ItemService {

    /** A new stack of {@code amount} items, or empty when no such item is registered. */
    @NotNull Optional<ItemStack> create(@NotNull PrismKey key, int amount);

    /** The custom item identity of {@code stack}, if it is one. */
    @NotNull Optional<PrismKey> identify(@NotNull ItemStack stack);

    /** The currently registered definition of {@code key}. Prefer this over a kept snapshot; it follows reloads. */
    @NotNull Optional<ItemDefinition> definition(@NotNull PrismKey key);

    /** The definition of the custom item {@code stack} is, if it is one. */
    default @NotNull Optional<ItemDefinition> definition(@NotNull ItemStack stack) {
        return identify(stack).flatMap(this::definition);
    }
}
