package com.aitchn.prism.pack.json;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/** Minimal deterministic JSON writer for maps, lists, strings, numbers and booleans (insertion order kept). */
public final class JsonWriter {

    private JsonWriter() {}

    public static @NotNull String write(@NotNull Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value, 0);
        return out.append('\n').toString();
    }

    private static void write(StringBuilder out, Object value, int depth) {
        switch (value) {
            case null -> out.append("null");
            case String text -> string(out, text);
            case Boolean bool -> out.append(bool);
            case Number number -> out.append(number(number));
            case Map<?, ?> map -> {
                if (map.isEmpty()) {
                    out.append("{}");
                    return;
                }
                out.append("{\n");
                int i = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    indent(out, depth + 1);
                    string(out, String.valueOf(entry.getKey()));
                    out.append(": ");
                    write(out, entry.getValue(), depth + 1);
                    out.append(++i < map.size() ? ",\n" : "\n");
                }
                indent(out, depth);
                out.append('}');
            }
            case List<?> list -> {
                if (list.stream().allMatch(e -> e instanceof Number || e instanceof String)) {
                    out.append('[');
                    for (int i = 0; i < list.size(); i++) {
                        if (i > 0) {
                            out.append(", ");
                        }
                        write(out, list.get(i), depth);
                    }
                    out.append(']');
                    return;
                }
                out.append("[\n");
                for (int i = 0; i < list.size(); i++) {
                    indent(out, depth + 1);
                    write(out, list.get(i), depth + 1);
                    out.append(i + 1 < list.size() ? ",\n" : "\n");
                }
                indent(out, depth);
                out.append(']');
            }
            default -> throw new IllegalArgumentException("Cannot write " + value.getClass());
        }
    }

    private static String number(Number number) {
        double d = number.doubleValue();
        if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            return Long.toString((long) d);
        }
        return Double.toString(d);
    }

    private static void indent(StringBuilder out, int depth) {
        out.append("  ".repeat(depth));
    }

    private static void string(StringBuilder out, String text) {
        out.append('"');
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
