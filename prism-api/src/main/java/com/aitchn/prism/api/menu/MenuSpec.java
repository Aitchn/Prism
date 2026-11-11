package com.aitchn.prism.api.menu;

import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * A list of buttons the player picks one from. One description, rendered per client (see {@link MenuService}).
 * The title is a translation key or MiniMessage text, shown in the viewer's language.
 */
public record MenuSpec(@NotNull String title, @NotNull List<MenuButton> buttons) {

    /** Largest menu the chest renderer can show (6 rows of 9). */
    public static final int MAX_BUTTONS = 54;

    public MenuSpec {
        Objects.requireNonNull(title, "title");
        buttons = List.copyOf(buttons);
        if (buttons.isEmpty() || buttons.size() > MAX_BUTTONS) {
            throw new IllegalArgumentException("a menu needs 1 to " + MAX_BUTTONS + " buttons, got " + buttons.size());
        }
        long distinct = buttons.stream().map(MenuButton::id).distinct().count();
        if (distinct != buttons.size()) {
            throw new IllegalArgumentException("button ids must be unique");
        }
    }

    /** Chest rows needed for these buttons. */
    public int rows() {
        return (buttons.size() + 8) / 9;
    }
}
