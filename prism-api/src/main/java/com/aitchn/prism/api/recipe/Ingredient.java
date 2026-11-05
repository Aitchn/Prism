package com.aitchn.prism.api.recipe;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What a recipe slot accepts: one item (custom or vanilla) or a vanilla item tag, and how many. Written in YAML as
 * {@code minecraft:iron_ingot}, {@code examplemod:ruby}, {@code "#minecraft:planks"} (quoted, {@code #} starts a YAML
 * comment), or {@code {item: examplemod:ruby, count: 2}}. Unqualified keys use the recipe file's namespace, except
 * tags, which default to {@code minecraft}. Match stacks with {@code ItemService.matches}.
 */
public record Ingredient(@Nullable PrismKey item, @Nullable PrismKey tag, int count) {

    public Ingredient {
        if ((item == null) == (tag == null)) {
            throw new IllegalArgumentException("an ingredient names exactly one item or one tag");
        }
        if (count < 1) {
            throw new IllegalArgumentException("count must be at least 1");
        }
    }

    public static @NotNull Ingredient of(@NotNull PrismKey item) {
        return new Ingredient(Objects.requireNonNull(item), null, 1);
    }

    public boolean isTag() {
        return tag != null;
    }

    /** Parses the string form. */
    public static @NotNull Ingredient parse(@NotNull String text, @NotNull String namespace, int count) {
        String trimmed = text.trim();
        if (trimmed.startsWith("#")) {
            return new Ingredient(null, PrismKey.parse(trimmed.substring(1), PrismKey.MINECRAFT_NAMESPACE), count);
        }
        return new Ingredient(PrismKey.parse(trimmed, namespace), null, count);
    }

    /** Reads {@code field} of {@code section} in the string or {@code {item|tag, count}} form. */
    public static @NotNull Ingredient read(@NotNull ConfigSection section, @NotNull String field, @NotNull String namespace)
            throws ContentException {
        try {
            if (section.isSection(field)) {
                ConfigSection entry = section.requireSection(field);
                entry.rejectUnknownKeys(java.util.Set.of("item", "tag", "count"));
                int count = entry.getInt("count").orElse(1);
                if (entry.has("tag")) {
                    return parse("#" + entry.requireString("tag"), namespace, count);
                }
                return parse(entry.requireString("item"), namespace, count);
            }
            return parse(section.requireString(field), namespace, 1);
        } catch (IllegalArgumentException e) {
            throw new ContentException(section.path().isEmpty() ? field : section.path() + "." + field, e.getMessage());
        }
    }

    @Override
    public @NotNull String toString() {
        return (count > 1 ? count + "x " : "") + (tag != null ? "#" + tag : String.valueOf(item));
    }
}
