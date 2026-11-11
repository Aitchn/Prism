package com.aitchn.prism.core.lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jetbrains.annotations.NotNull;

/** The one line per mod that says which of its translations are missing. */
public final class LangSummary {

    /** How many missing keys the line names before it only counts them. */
    static final int SHOWN = 8;

    private LangSummary() {}

    /**
     * {@code PRISM_LANG <mod> locales=<list> keys=<n> missing=<n>}, followed by the first missing keys per locale. A key
     * counts as missing in a locale when another locale of the same mod defines it, or when it names one of the mod's
     * items that has no name at all ({@code expected}). {@code en_us} is always checked.
     *
     * @param bundle   the mod's own tables, locale to key to template
     * @param expected keys the mod's content needs and no table defines
     */
    public static @NotNull String line(@NotNull String mod, @NotNull Map<String, Map<String, String>> bundle,
            @NotNull Set<String> expected) {
        Set<String> all = new TreeSet<>(expected);
        bundle.values().forEach(table -> all.addAll(table.keySet()));
        Set<String> locales = new TreeSet<>(bundle.keySet());
        locales.add(Locales.FALLBACK);
        Map<String, List<String>> missing = new TreeMap<>();
        int total = 0;
        for (String locale : locales) {
            Map<String, String> table = bundle.getOrDefault(locale, Map.of());
            List<String> absent = new ArrayList<>();
            for (String key : all) {
                if (!table.containsKey(key)) {
                    absent.add(key);
                }
            }
            if (!absent.isEmpty()) {
                missing.put(locale, absent);
                total += absent.size();
            }
        }
        StringBuilder out = new StringBuilder("PRISM_LANG ").append(mod)
                .append(" locales=").append(String.join(",", locales))
                .append(" keys=").append(all.size())
                .append(" missing=").append(total);
        if (total > 0) {
            int shown = 0;
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : missing.entrySet()) {
                List<String> keys = new ArrayList<>();
                for (String key : entry.getValue()) {
                    if (shown++ < SHOWN) {
                        keys.add(key);
                    }
                }
                int hidden = entry.getValue().size() - keys.size();
                parts.add(entry.getKey() + ": " + String.join(", ", keys) + (hidden > 0 ? " (+" + hidden + ")" : ""));
            }
            out.append(" (").append(String.join("; ", parts)).append(')');
        }
        return out.toString();
    }
}
