package com.aitchn.prism.core.lang;

import com.aitchn.prism.api.text.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The text a stack carries in a language-neutral form: an optional name and lore lines, each a {@link Message}. It is
 * stored as one string in the stack's persistent data ({@code prism:text}) and turned into words only in the packets
 * a player receives.
 *
 * <p>Encoding (version 1): {@code P1} then the name ({@code -} for none, else {@code n} and a message), the line
 * count and the lines. A message is a string (its key), an argument count and the arguments; each argument is a type
 * letter ({@code s} text, {@code i} integer, {@code d} decimal, {@code b} boolean, {@code m} message) and its value.
 * Strings and counts are written as {@code <length>:<chars>} and {@code <count>;}. The encoding is deterministic, so
 * equal text gives equal stacks.
 *
 * @param name the name to show instead of the item's own, or null
 * @param lore lines shown below the item's static lore
 */
public record TextSpec(@Nullable Message name, @NotNull List<Message> lore) {

    private static final String VERSION = "P1";

    public TextSpec {
        lore = List.copyOf(lore);
    }

    /** Whether there is nothing to show. */
    public boolean isEmpty() {
        return name == null && lore.isEmpty();
    }

    /** The persistent form of this text. */
    public @NotNull String encode() {
        StringBuilder out = new StringBuilder(VERSION);
        if (name == null) {
            out.append('-');
        } else {
            out.append('n');
            message(out, name);
        }
        count(out, lore.size());
        for (Message line : lore) {
            message(out, line);
        }
        return out.toString();
    }

    /** The text stored as {@code encoded}; empty when it is not a valid encoding. */
    public static @NotNull Optional<TextSpec> decode(@Nullable String encoded) {
        if (encoded == null || !encoded.startsWith(VERSION)) {
            return Optional.empty();
        }
        try {
            Reader in = new Reader(encoded, VERSION.length());
            char kind = in.next();
            Message name = switch (kind) {
                case '-' -> null;
                case 'n' -> in.message(0);
                default -> throw new IllegalArgumentException("bad name marker");
            };
            int lines = in.count();
            List<Message> lore = new ArrayList<>(Math.min(lines, 64));
            for (int i = 0; i < lines; i++) {
                lore.add(in.message(0));
            }
            if (!in.done()) {
                throw new IllegalArgumentException("trailing data");
            }
            return Optional.of(new TextSpec(name, lore));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static void message(StringBuilder out, Message message) {
        string(out, message.key());
        count(out, message.args().size());
        for (Object arg : message.args()) {
            if (arg instanceof Message nested) {
                out.append('m');
                message(out, nested);
            } else if (arg instanceof String text) {
                out.append('s');
                string(out, text);
            } else if (arg instanceof Double || arg instanceof Float) {
                out.append('d');
                string(out, Double.toString(((Number) arg).doubleValue()));
            } else if (arg instanceof Number number) {
                out.append('i');
                string(out, Long.toString(number.longValue()));
            } else {
                out.append('b').append(Boolean.TRUE.equals(arg) ? '1' : '0');
            }
        }
    }

    private static void string(StringBuilder out, String text) {
        out.append(text.length()).append(':').append(text);
    }

    private static void count(StringBuilder out, int count) {
        out.append(count).append(';');
    }

    private static final class Reader {
        private static final int MAX_DEPTH = 8;
        private final String text;
        private int at;

        Reader(String text, int at) {
            this.text = text;
            this.at = at;
        }

        char next() {
            return text.charAt(at++);
        }

        boolean done() {
            return at == text.length();
        }

        int number(char end) {
            int stop = text.indexOf(end, at);
            if (stop < 0 || stop == at || stop - at > 9) {
                throw new IllegalArgumentException("bad number");
            }
            int value = Integer.parseInt(text.substring(at, stop));
            if (value < 0) {
                throw new IllegalArgumentException("negative");
            }
            at = stop + 1;
            return value;
        }

        int count() {
            return number(';');
        }

        String string() {
            int length = number(':');
            String value = text.substring(at, at + length);
            at += length;
            return value;
        }

        Message message(int depth) {
            if (depth > MAX_DEPTH) {
                throw new IllegalArgumentException("too deep");
            }
            String key = string();
            int count = count();
            List<Object> args = new ArrayList<>(Math.min(count, 16));
            for (int i = 0; i < count; i++) {
                char type = next();
                args.add(switch (type) {
                    case 's' -> string();
                    case 'i' -> Long.parseLong(string());
                    case 'd' -> Double.parseDouble(string());
                    case 'b' -> next() == '1';
                    case 'm' -> message(depth + 1);
                    default -> throw new IllegalArgumentException("bad argument type");
                });
            }
            return new Message(key, args);
        }
    }
}
