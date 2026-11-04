package com.aitchn.prism.api.item;

import com.aitchn.prism.api.PrismKey;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Creates and recognises custom item stacks. A stack carries only identity; appearance is decided per viewer. */
public interface ItemService {

    /** A new stack of {@code amount} items, or empty when no such item is registered. */
    @NotNull Optional<ItemStack> create(@NotNull PrismKey key, int amount);

    /** The custom item identity of {@code stack}, if it is one. */
    @NotNull Optional<PrismKey> identify(@NotNull ItemStack stack);
}
