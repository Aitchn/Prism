package com.aitchn.prism.validation.ae2.runtime;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.validation.ae2.storage.ItemKey;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Turns item stacks into network keys and back. Prism gives custom items an identity but no way to ask "are these two
 * stacks identical including their data", so the network keys on the full serialized stack.
 */
public final class StackCodec {

    private StackCodec() {}

    public static @NotNull ItemKey key(@NotNull ItemStack stack) {
        return new ItemKey(Base64.getEncoder().withoutPadding().encodeToString(stack.asOne().serializeAsBytes()));
    }

    public static @NotNull Optional<ItemStack> stack(@NotNull ItemKey key, int amount) {
        try {
            ItemStack stack = ItemStack.deserializeBytes(Base64.getDecoder().decode(key.id()));
            stack.setAmount(amount);
            return Optional.of(stack);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** The key Prism's menus use for an icon: the custom identity when there is one, else the vanilla item. */
    public static @NotNull PrismKey icon(@NotNull ItemStack stack) {
        return Prism.items().identify(stack)
                .orElseGet(() -> new PrismKey("minecraft", stack.getType().getKey().getKey()));
    }

    /** A readable name: {@code iron_ingot} becomes {@code Iron Ingot}. */
    public static @NotNull String name(@NotNull ItemStack stack) {
        String raw = Prism.items().identify(stack).map(PrismKey::value)
                .orElseGet(() -> stack.getType().getKey().getKey());
        StringBuilder out = new StringBuilder();
        for (String word : raw.replace('/', ' ').split("[_ ]+")) {
            if (!word.isEmpty()) {
                out.append(out.isEmpty() ? "" : " ").append(word.substring(0, 1).toUpperCase(Locale.ROOT))
                        .append(word.substring(1));
            }
        }
        return out.toString();
    }
}
