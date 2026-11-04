package com.aitchn.prism.api.menu;

import com.aitchn.prism.api.PrismKey;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * One choice in a {@link MenuSpec}.
 *
 * @param id    returned to the handler when the player picks this button
 * @param label MiniMessage text
 * @param icon  an item (vanilla or custom) shown on Java; Bedrock may show it as the form button image
 */
public record MenuButton(@NotNull String id, @NotNull String label, @NotNull PrismKey icon) {
    public MenuButton {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(icon, "icon");
    }
}
