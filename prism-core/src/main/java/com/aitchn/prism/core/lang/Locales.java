package com.aitchn.prism.core.lang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Minecraft locale codes ({@code en_us}, {@code zh_tw}) and the order they fall back in. */
public final class Locales {

    /** The locale every lookup ends with. */
    public static final String FALLBACK = "en_us";

    private static final List<String> TRADITIONAL_CHINESE = List.of("zh_tw", "zh_hk", "zh_mo");
    private static final List<String> SIMPLIFIED_CHINESE = List.of("zh_cn", "zh_sg");

    private Locales() {}

    /** {@code zh-TW}, {@code zh_TW} and {@code ZH_tw} all become {@code zh_tw}; null or blank becomes {@code en_us}. */
    public static @NotNull String normalize(@Nullable String locale) {
        if (locale == null || locale.isBlank()) {
            return FALLBACK;
        }
        return locale.trim().replace('-', '_').toLowerCase(Locale.ROOT);
    }

    /**
     * The locales to look a key up in, best first: {@code locale} itself, then other locales of the same language
     * that {@code available} has, then {@link #FALLBACK}. Chinese only falls back within its script: {@code zh_hk}
     * reads {@code zh_tw} but never {@code zh_cn}. Other languages prefer the language's home country ({@code fr_ca}
     * reads {@code fr_fr} first), then the rest alphabetically.
     */
    public static @NotNull List<String> chain(@NotNull String locale, @NotNull Collection<String> available) {
        String wanted = normalize(locale);
        Set<String> chain = new LinkedHashSet<>();
        chain.add(wanted);
        int split = wanted.indexOf('_');
        String language = split < 0 ? wanted : wanted.substring(0, split);
        if (language.equals("zh")) {
            List<String> group = SIMPLIFIED_CHINESE.contains(wanted) || wanted.equals("zh") ? SIMPLIFIED_CHINESE
                    : TRADITIONAL_CHINESE;
            for (String candidate : group) {
                if (available.contains(candidate)) {
                    chain.add(candidate);
                }
            }
        } else {
            String home = language + "_" + language;
            if (available.contains(home)) {
                chain.add(home);
            }
            List<String> rest = new ArrayList<>();
            for (String candidate : available) {
                if (candidate.startsWith(language + "_") || candidate.equals(language)) {
                    rest.add(candidate);
                }
            }
            rest.sort(null);
            chain.addAll(rest);
        }
        chain.add(FALLBACK);
        return List.copyOf(chain);
    }
}
