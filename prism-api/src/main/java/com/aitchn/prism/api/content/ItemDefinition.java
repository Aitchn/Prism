package com.aitchn.prism.api.content;

import com.aitchn.prism.api.PrismKey;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * A custom item: identity, the vanilla item it is built on, data components and bound behaviors.
 *
 * @param owner the plugin that registered it; its behaviors stop running when the owner is disabled
 * @param base  the vanilla item (for example {@code minecraft:paper}) used as the server-side carrier
 */
public record ItemDefinition(
        @NotNull PrismKey key,
        @NotNull String owner,
        @NotNull PrismKey base,
        @NotNull ComponentMap components,
        @NotNull List<Behavior> behaviors) {

    public ItemDefinition {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(components, "components");
        behaviors = List.copyOf(behaviors);
    }

    /** Behaviors implementing {@code capability}, in declaration order. */
    public <B> @NotNull List<B> behaviors(@NotNull Class<B> capability) {
        return behaviors.stream().filter(capability::isInstance).map(capability::cast).toList();
    }
}
