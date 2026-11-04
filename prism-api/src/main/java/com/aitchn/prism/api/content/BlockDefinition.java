package com.aitchn.prism.api.content;

import com.aitchn.prism.api.PrismKey;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * A custom block: identity, the vanilla carrier block that exists in the world, data components and behaviors.
 * What players see is decided by the presentation backend (display entities in v1), not by the carrier.
 *
 * @param carrier the vanilla block placed in the world (for example {@code minecraft:barrier})
 * @param drop    the item given when the block is broken, if any
 */
public record BlockDefinition(
        @NotNull PrismKey key,
        @NotNull String owner,
        @NotNull PrismKey carrier,
        @NotNull Optional<PrismKey> drop,
        @NotNull ComponentMap components,
        @NotNull List<Behavior> behaviors) {

    public BlockDefinition {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(drop, "drop");
        Objects.requireNonNull(components, "components");
        behaviors = List.copyOf(behaviors);
    }

    public <B> @NotNull List<B> behaviors(@NotNull Class<B> capability) {
        return behaviors.stream().filter(capability::isInstance).map(capability::cast).toList();
    }
}
