package com.aitchn.prism.api.item;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ItemDefinition;
import java.util.Optional;
import java.util.function.Consumer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

/** Creates and recognises custom item stacks. A stack carries only identity; appearance is decided per viewer. */
public interface ItemService {

    /** A new stack of {@code amount} items, or empty when no such item is registered. */
    @NotNull Optional<ItemStack> create(@NotNull PrismKey key, int amount);

    /** The custom item identity of {@code stack}, if it is one. */
    @NotNull Optional<PrismKey> identify(@NotNull ItemStack stack);

    /** The currently registered definition of {@code key}. Prefer this over a kept snapshot; it follows reloads. */
    @NotNull Optional<ItemDefinition> definition(@NotNull PrismKey key);

    /**
     * Changes the per-stack data of {@code stack} in place and re-renders its lore. Per-stack data is the stack's own
     * {@link PersistentDataContainer}: it travels with the item (chests, drops, other players) and makes stacks with
     * different data not stack together. Read it with {@code stack.getPersistentDataContainer()}. Use your own
     * namespace for keys; Prism's are reserved.
     *
     * @return false if {@code stack} is not a custom item (nothing is changed)
     */
    boolean editData(@NotNull ItemStack stack, @NotNull Consumer<PersistentDataContainer> edit);

    /** Re-renders the name and lore of a custom item stack (static {@code prism:lore} then {@link ItemLoreBehavior}). */
    void refresh(@NotNull ItemStack stack);

    /** The definition of the custom item {@code stack} is, if it is one. */
    default @NotNull Optional<ItemDefinition> definition(@NotNull ItemStack stack) {
        return identify(stack).flatMap(this::definition);
    }
}
