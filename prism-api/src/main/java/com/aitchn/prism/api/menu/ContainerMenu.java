package com.aitchn.prism.api.menu;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * A container GUI with real slots: buttons, input slots players drag items into and output slots they take items
 * from. Shown as a chest container on Java and, through Geyser's container translation, on Bedrock. Every callback
 * runs on the player's thread; touch block state only through {@code Prism.scheduler().runAt}.
 *
 * <p>Prism enforces the slot roles for every click type (left, right, shift, number keys, drop, double-click, drag),
 * so a menu cannot lose or duplicate items by forgetting a case. Items in input and output slots belong to the menu
 * object: keep them in your own model (for example per-block data) through {@link #onSlotChanged}, and give items
 * back in {@link #onClose} if the menu is not backed by anything.
 */
public interface ContainerMenu {

    /** MiniMessage title. */
    @NotNull String title();

    /** Rows of 9 slots, 1 to 6. */
    int rows();

    /** The role of {@code slot}; {@link SlotRole#BUTTON} unless overridden. */
    default @NotNull SlotRole role(int slot) {
        return SlotRole.BUTTON;
    }

    /** Whether {@code stack} may be placed into input slot {@code slot}. */
    default boolean accepts(int slot, @NotNull ItemStack stack) {
        return true;
    }

    /** Fill the slots. Runs once when the menu opens. */
    void onOpen(@NotNull MenuView view);

    /** A {@link SlotRole#BUTTON} slot was clicked. */
    default void onButton(@NotNull MenuView view, int slot, @NotNull ClickKind click) {}

    /**
     * The item in an input or output slot changed because of the player; runs one tick after the click, or just
     * before {@link #onClose} if the menu closes first, so every change is reported.
     */
    default void onSlotChanged(@NotNull MenuView view, int slot) {}

    /** Ticks between {@link #onRefresh} calls while open; 0 (the default) means never. */
    default int refreshTicks() {
        return 0;
    }

    /** Update live values (progress, energy, counts) here. */
    default void onRefresh(@NotNull MenuView view) {}

    /** The menu closed (player closed it, logged out, or {@link MenuView#close()}). */
    default void onClose(@NotNull MenuView view) {}
}
