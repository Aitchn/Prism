package com.aitchn.prism.api.recipe;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** What a recipe produces: an item key (custom or vanilla) and a count. YAML: a key, or {@code {item, count}}. */
public record ItemResult(@NotNull PrismKey item, int count) {

    public ItemResult {
        Objects.requireNonNull(item, "item");
        if (count < 1 || count > 99) {
            throw new IllegalArgumentException("count must be 1 to 99");
        }
    }

    public static @NotNull ItemResult read(@NotNull ConfigSection section, @NotNull String field, @NotNull String namespace)
            throws ContentException {
        try {
            if (section.isSection(field)) {
                ConfigSection entry = section.requireSection(field);
                entry.rejectUnknownKeys(java.util.Set.of("item", "count"));
                return new ItemResult(PrismKey.parse(entry.requireString("item"), namespace), entry.getInt("count").orElse(1));
            }
            return new ItemResult(PrismKey.parse(section.requireString(field), namespace), 1);
        } catch (IllegalArgumentException e) {
            throw new ContentException(section.path().isEmpty() ? field : section.path() + "." + field, e.getMessage());
        }
    }
}
