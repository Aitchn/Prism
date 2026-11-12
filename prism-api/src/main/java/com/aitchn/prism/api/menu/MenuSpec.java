package com.aitchn.prism.api.menu;

import com.aitchn.prism.api.PrismKey;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A list of buttons the player picks one from. One description, rendered per client (see {@link MenuService}).
 * The title is a translation key or MiniMessage text, shown in the viewer's language.
 *
 * <p>{@code art} is an optional menu file key (see {@link ContainerMenu#art}): the menu then has the art's rows, its
 * background is drawn for Java players with the pack, and the buttons fill the slots the art uses, in slot order.
 * Widgets stay in their first state, since a button list has no live view.
 */
public record MenuSpec(@NotNull String title, @NotNull List<MenuButton> buttons, @Nullable PrismKey art) {

    /** Largest menu the chest renderer can show (6 rows of 9). */
    public static final int MAX_BUTTONS = 54;

    /** A button list without art. */
    public MenuSpec(@NotNull String title, @NotNull List<MenuButton> buttons) {
        this(title, buttons, null);
    }

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

    /** The same menu drawn with menu file {@code art}. */
    public @NotNull MenuSpec withArt(@Nullable PrismKey art) {
        return new MenuSpec(title, buttons, art);
    }

    /** Chest rows needed for these buttons (without art). */
    public int rows() {
        return (buttons.size() + 8) / 9;
    }
}
