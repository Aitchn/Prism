package com.aitchn.prism.api.menu;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.text.Message;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An open {@link ContainerMenu} as one player sees it. Use it on the player's thread (every callback already runs
 * there). Changes show at once, without reopening, so live progress and counters do not flicker.
 */
public interface MenuView {

    @NotNull Player player();

    /** Number of slots: rows times 9. */
    int size();

    /** A copy of the item in {@code slot}, or null if it is empty. */
    @Nullable ItemStack item(int slot);

    void setItem(int slot, @Nullable ItemStack stack);

    /**
     * Shows {@code icon} (a custom or vanilla item key) with a name and lore in {@code slot}. A shortcut for buttons
     * and indicators such as progress bars; unknown keys show a barrier. Each string is a translation key or literal
     * MiniMessage text (see {@link Message}); every viewer reads it in their own language.
     */
    default void setIcon(int slot, @NotNull PrismKey icon, @NotNull String name, @NotNull List<String> lore) {
        setIcon(slot, icon, Message.of(name), messages(lore));
    }

    /**
     * Shows {@code icon} with a translated name and lore in {@code slot}. An empty name key keeps the item's own name
     * (a custom item's translated name, or the vanilla name).
     */
    void setIcon(int slot, @NotNull PrismKey icon, @NotNull Message name, @NotNull List<Message> lore);

    /**
     * Shows a gauge item ({@code prism:gauge}) at the step nearest {@code fraction} (0 empty, 1 full) in {@code slot},
     * with a MiniMessage name and lore: a fill bar for energy or a tank, an arrow for progress. Both editions see the
     * stepped icon. Call it again from {@link ContainerMenu#onRefresh} to move it. A key that is not a gauge shows as
     * {@link #setIcon} would.
     */
    default void setGauge(int slot, @NotNull PrismKey gauge, double fraction, @NotNull String name,
            @NotNull List<String> lore) {
        setGauge(slot, gauge, fraction, Message.of(name), messages(lore));
    }

    /** {@link #setGauge(int, PrismKey, double, String, List)} with a translated name and lore. */
    void setGauge(int slot, @NotNull PrismKey gauge, double fraction, @NotNull Message name,
            @NotNull List<Message> lore);

    /**
     * Whether the player changed {@code slot} and {@link ContainerMenu#onSlotChanged} has not reported it yet. While
     * true, the slot's content may still be moving; do not copy it into your model or overwrite it.
     */
    boolean isChangePending(int slot);

    /** Closes the menu (on the next tick). {@link ContainerMenu#onClose} still runs. */
    void close();

    boolean isOpen();

    private static List<Message> messages(List<String> lines) {
        List<Message> messages = new java.util.ArrayList<>(lines.size());
        lines.forEach(line -> messages.add(Message.of(line)));
        return messages;
    }
}
