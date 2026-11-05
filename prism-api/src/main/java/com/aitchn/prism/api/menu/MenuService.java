package com.aitchn.prism.api.menu;

import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Opens menus. Java and Bedrock players both get a chest-style container (Geyser translates it); a native Bedrock
 * form renderer is planned and will use the same {@link MenuSpec}. Mods should check
 * {@code ClientCapability.CONTAINER_MENUS} / {@code FORM_MENUS} only when they need something beyond buttons.
 */
public interface MenuService {

    /**
     * Shows {@code spec} to {@code player}; {@code onSelect} receives the chosen button id on the player's thread.
     * Closing the menu without choosing does not call the handler.
     */
    void open(@NotNull Player player, @NotNull MenuSpec spec, @NotNull Consumer<String> onSelect);

    /**
     * Shows a {@link ContainerMenu} with real slots to {@code player}. Callable from any thread; the menu opens on the
     * player's thread.
     */
    void open(@NotNull Player player, @NotNull ContainerMenu menu);
}
