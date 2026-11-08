package com.aitchn.prism.api.item;

import java.util.List;
import java.util.function.Predicate;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Moves items in and out of something without caring about its slots: a chest, a furnace, a machine's buffer, a
 * storage network. Pipes, item ducts and storage buses talk to every block through this one shape. Ask for it with
 * {@code PrismCapabilities.ITEM_HANDLER}; Prism answers it for vanilla containers (respecting the furnace's sides, like
 * a hopper) and for any custom block that offers {@code PrismCapabilities.INVENTORY}. Call it on the thread that owns
 * the block.
 *
 * <p>With {@code simulate} true nothing changes; the result is what would happen. Stacks passed in are never modified.
 */
public interface ItemHandler {

    /** Puts as much of {@code stack} in as fits; returns what did not fit (an empty stack when everything did). */
    @NotNull ItemStack insert(@NotNull ItemStack stack, boolean simulate);

    /**
     * Takes up to {@code amount} items that {@code filter} accepts, all of one kind (the first accepted kind found);
     * returns what was taken (an empty stack when nothing was).
     */
    @NotNull ItemStack extract(@NotNull Predicate<ItemStack> filter, int amount, boolean simulate);

    /** Copies of the stacks held, for display, search and counting. */
    @NotNull List<ItemStack> contents();
}
