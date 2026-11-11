package com.aitchn.prism.core.lang;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Reads a language file: one JSON object whose values are strings. Nested objects are flattened with dots, so
 * {@code {"item": {"mymod": {"ruby": "Ruby"}}}} defines {@code item.mymod.ruby}. Anything else (numbers, arrays,
 * duplicate keys, trailing text) is an error, so a typo is reported at startup instead of silently losing keys.
 */
public final class LangJson {

    private final String text;
    private int at;

    private LangJson(String text) {
        this.text = text;
    }

    /** The key/value pairs of {@code json}, in file order. */
    public static @NotNull Map<String, String> parse(@NotNull String json) {
        LangJson parser = new LangJson(json);
        Map<String, String> out = new LinkedHashMap<>();
        parser.skipSpace();
        if (parser.at < json.length() && json.charAt(parser.at) == '﻿') {
            parser.at++; // byte order mark
            parser.skipSpace();
        }
        parser.object("", out);
        parser.skipSpace();
        if (parser.at != json.length()) {
            throw parser.error("unexpected text after the object");
        }
        return out;
    }

    private void object(String prefix, Map<String, String> out) {
        expect('{');
        skipSpace();
        if (peek() == '}') {
            at++;
            return;
        }
        while (true) {
            skipSpace();
            String key = string();
            if (key.isEmpty()) {
                throw error("empty key");
            }
            skipSpace();
            expect(':');
            skipSpace();
            String full = prefix.isEmpty() ? key : prefix + "." + key;
            if (peek() == '{') {
                object(full, out);
            } else if (peek() == '"') {
                if (out.putIfAbsent(full, string()) != null) {
                    throw error("duplicate key " + full);
                }
            } else {
                throw error("the value of " + full + " must be a string or an object");
            }
            skipSpace();
            char next = next();
            if (next == '}') {
                return;
            }
            if (next != ',') {
                throw error("expected , or }");
            }
        }
    }

    private String string() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return out.toString();
            }
            if (c == '\\') {
                char escaped = next();
                switch (escaped) {
                    case '"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (at + 4 > text.length()) {
                            throw error("short \\u escape");
                        }
                        try {
                            out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                        } catch (NumberFormatException e) {
                            throw error("bad \\u escape");
                        }
                        at += 4;
                    }
                    default -> throw error("bad escape \\" + escaped);
                }
            } else if (c < 0x20) {
                throw error("control character in a string");
            } else {
                out.append(c);
            }
        }
    }

    private void skipSpace() {
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
            at++;
        }
    }

    private char peek() {
        if (at >= text.length()) {
            throw error("unexpected end");
        }
        return text.charAt(at);
    }

    private char next() {
        char c = peek();
        at++;
        return c;
    }

    private void expect(char c) {
        if (next() != c) {
            at--;
            throw error("expected " + c);
        }
    }

    private IllegalArgumentException error(String message) {
        int line = 1;
        for (int i = 0; i < Math.min(at, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return new IllegalArgumentException(message + " (line " + line + ")");
    }
}
