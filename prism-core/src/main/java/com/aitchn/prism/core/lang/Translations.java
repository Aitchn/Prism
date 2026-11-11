package com.aitchn.prism.core.lang;

import com.aitchn.prism.api.text.Message;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An immutable set of translation tables, one per locale, merged from every active mod. A new instance replaces the
 * old one whenever a mod is activated or removed, so readers on network threads never see a half-built table.
 *
 * <p>Templates are MiniMessage text with positional placeholders {@code {0}}, {@code {1}}, ... A placeholder without
 * an argument is left as it is.
 */
public final class Translations {

    /** No mod has a language file. */
    public static final Translations EMPTY = new Translations(Map.of());

    private static final int MAX_DEPTH = 8;

    private final Map<String, Map<String, String>> tables;
    private final Set<String> locales;
    private final Map<String, List<String>> chains = new ConcurrentHashMap<>();

    private Translations(Map<String, Map<String, String>> tables) {
        this.tables = tables;
        Set<String> all = new TreeSet<>(tables.keySet());
        all.add(Locales.FALLBACK);
        this.locales = Collections.unmodifiableSet(all);
    }

    /**
     * Merges language bundles (locale to key to template) in order. When two bundles define the same key for the same
     * locale, the first one wins.
     */
    public static @NotNull Translations merge(@NotNull List<Map<String, Map<String, String>>> bundles) {
        Map<String, Map<String, String>> merged = new HashMap<>();
        for (Map<String, Map<String, String>> bundle : bundles) {
            bundle.forEach((locale, entries) -> {
                Map<String, String> table = merged.computeIfAbsent(Locales.normalize(locale), k -> new HashMap<>());
                entries.forEach(table::putIfAbsent);
            });
        }
        Map<String, Map<String, String>> frozen = new LinkedHashMap<>();
        merged.forEach((locale, table) -> frozen.put(locale, Map.copyOf(table)));
        return frozen.isEmpty() ? EMPTY : new Translations(Map.copyOf(frozen));
    }

    /** Every locale with a table, plus {@code en_us}. */
    public @NotNull Set<String> locales() {
        return locales;
    }

    /** Whether any locale defines {@code key}. */
    public boolean has(@NotNull String key) {
        for (Map<String, String> table : tables.values()) {
            if (table.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    /** The template of {@code key} along {@code locale}'s fallback chain, or null when no table has it. */
    public @Nullable String lookup(@NotNull String key, @NotNull String locale) {
        for (String candidate : chain(locale)) {
            Map<String, String> table = tables.get(candidate);
            if (table != null) {
                String template = table.get(key);
                if (template != null) {
                    return template;
                }
            }
        }
        return null;
    }

    /** {@code message} as MiniMessage text in {@code locale}. Never null; an unknown key is its own template. */
    public @NotNull String translate(@NotNull Message message, @NotNull String locale) {
        return translate(message, Locales.normalize(locale), 0);
    }

    private String translate(Message message, String locale, int depth) {
        String template = lookup(message.key(), locale);
        if (template == null) {
            template = message.key();
        }
        return format(template, message.args(), arg -> argument(arg, locale, depth));
    }

    private String argument(Object arg, String locale, int depth) {
        if (arg instanceof Message nested) {
            return depth >= MAX_DEPTH ? escape(nested.key()) : translate(nested, locale, depth + 1);
        }
        return formatValue(arg);
    }

    /** The fallback chain of {@code locale} over the locales this instance has. */
    public @NotNull List<String> chain(@NotNull String locale) {
        return chains.computeIfAbsent(Locales.normalize(locale), wanted -> Locales.chain(wanted, tables.keySet()));
    }

    /** Renders one non-message argument: plain text is escaped, numbers get thousands separators. */
    static String formatValue(Object arg) {
        if (arg instanceof String text) {
            return escape(text);
        }
        if (arg instanceof Integer || arg instanceof Long || arg instanceof Short || arg instanceof Byte) {
            return String.format(Locale.ROOT, "%,d", ((Number) arg).longValue());
        }
        if (arg instanceof Double || arg instanceof Float) {
            double value = ((Number) arg).doubleValue();
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return String.valueOf(value);
            }
            String text = String.format(Locale.ROOT, "%,.2f", value);
            if (text.endsWith(".00")) {
                text = text.substring(0, text.length() - 3);
            } else if (text.endsWith("0")) {
                text = text.substring(0, text.length() - 1);
            }
            return text;
        }
        return String.valueOf(arg);
    }

    /** Plain text made safe for MiniMessage: tags in it are shown, not interpreted. */
    public static @NotNull String escape(@NotNull String text) {
        return text.replace("\\", "\\\\").replace("<", "\\<");
    }

    /** Replaces {@code {n}} in {@code template} with the rendered argument {@code n}. */
    static String format(String template, List<Object> args, java.util.function.Function<Object, String> render) {
        if (args.isEmpty() || template.indexOf('{') < 0) {
            return template;
        }
        StringBuilder out = new StringBuilder(template.length() + 16);
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c == '{') {
                int close = template.indexOf('}', i + 1);
                if (close > i + 1 && close - i <= 4 && digits(template, i + 1, close)) {
                    int index = Integer.parseInt(template.substring(i + 1, close));
                    if (index < args.size()) {
                        out.append(render.apply(args.get(index)));
                        i = close + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static boolean digits(String text, int from, int to) {
        for (int i = from; i < to; i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
