package com.aitchn.prism.api.item;

import com.aitchn.prism.api.content.Behavior;
import com.aitchn.prism.api.text.Message;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Capability: lore lines computed from one stack, for example a charge or a stored amount kept in the stack's
 * persistent data. Prism computes them when the stack is created and every time it changes through
 * {@link ItemService#editData}; call {@link ItemService#refresh} after changing the stack another way.
 *
 * <p>The lines are kept on the stack as translation keys and arguments, never as text: each player sees them in their
 * own language, and two stacks with the same data stay equal (and stack) whoever looked at them. Lines appear below
 * the item's static {@code prism:lore}, on Java and on Bedrock (through Geyser).
 *
 * <p>Implement {@link #loreMessages} for translated lines, or {@link #lore} for fixed MiniMessage lines.
 */
public interface ItemLoreBehavior extends Behavior {

    /**
     * Lines for {@code stack}, each a translation key with arguments (see {@link Message}). Must not change
     * {@code stack}. Defaults to {@link #lore}, each line taken as a key or as literal MiniMessage text.
     */
    default @NotNull List<Message> loreMessages(@NotNull ItemStack stack) {
        List<Message> lines = new ArrayList<>();
        for (String line : lore(stack)) {
            lines.add(Message.of(line));
        }
        return lines;
    }

    /**
     * MiniMessage lines (or translation keys without arguments) for {@code stack}. Must not change {@code stack}.
     * Only used when {@link #loreMessages} is not overridden.
     */
    default @NotNull List<String> lore(@NotNull ItemStack stack) {
        return List.of();
    }
}
