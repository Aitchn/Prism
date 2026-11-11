package com.aitchn.prism.api.text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Text that is translated for each player when it is shown: a translation key plus arguments. Nothing about the
 * player's language is stored, so a message can live on an item stack or in a menu and still read correctly for every
 * viewer.
 *
 * <p>The key is looked up in the mods' language files ({@code prism/lang/<locale>.json}). The value found is a
 * MiniMessage template whose {@code {0}}, {@code {1}}, ... are replaced by the arguments. A key that no language file
 * defines is used as the template itself, so {@code Message.of("<gray>Ready")} is simply literal MiniMessage text.
 *
 * <p>Arguments may be {@link String} (shown as plain text: MiniMessage tags in it are not interpreted), a
 * {@link Number} or {@link Boolean} (formatted), or another {@code Message} (translated for the same viewer, so an
 * item name can be part of a sentence).
 *
 * @param key  a translation key, or literal MiniMessage text
 * @param args the arguments, in placeholder order
 */
public record Message(@NotNull String key, @NotNull List<Object> args) {

    public Message {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(args, "args");
        List<Object> copy = new ArrayList<>(args.size());
        for (Object arg : args) {
            if (!(arg instanceof String || arg instanceof Message || arg instanceof Boolean
                    || arg instanceof Integer || arg instanceof Long || arg instanceof Short || arg instanceof Byte
                    || arg instanceof Double || arg instanceof Float)) {
                throw new IllegalArgumentException("a message argument is a String, a number, a Boolean or a Message, got "
                        + (arg == null ? "null" : arg.getClass().getName()));
            }
            copy.add(arg);
        }
        args = Collections.unmodifiableList(copy);
    }

    /** A message with {@code args} for the placeholders {@code {0}}, {@code {1}}, ... of {@code key}'s template. */
    public static @NotNull Message of(@NotNull String key, @NotNull Object... args) {
        return new Message(key, Arrays.asList(args));
    }
}
