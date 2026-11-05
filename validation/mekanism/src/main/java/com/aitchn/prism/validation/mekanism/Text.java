package com.aitchn.prism.validation.mekanism;

import java.util.Locale;

/** Small formatting helpers for menus and action bars (MiniMessage). */
final class Text {

    private Text() {}

    /** A bar of {@code width} thin strokes, filled for {@code fraction} (0 to 1). */
    static String bar(double fraction, int width, String filled) {
        int on = (int) Math.round(Math.max(0, Math.min(1, fraction)) * width);
        return "<" + filled + ">" + "|".repeat(on) + "<dark_gray>" + "|".repeat(width - on);
    }

    static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /** {@code enrichment_chamber} becomes {@code Enrichment Chamber}. */
    static String title(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.split("_")) {
            if (!word.isEmpty()) {
                out.append(out.length() == 0 ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return out.toString();
    }
}
