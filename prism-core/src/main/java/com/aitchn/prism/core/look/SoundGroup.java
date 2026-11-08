package com.aitchn.prism.core.look;

import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The vanilla sound groups a custom block can step, place and break with (the client plays them from its own state). */
public enum SoundGroup {
    WOOD, STONE, GRAVEL, GRASS, SAND, SNOW, WOOL, METAL, GLASS, BONE, SLIME, NETHERRACK, DEEPSLATE, COPPER;

    /** The group called {@code name} (as written in {@code prism:surface}), or null. */
    public static @Nullable SoundGroup parse(@NotNull String name) {
        for (SoundGroup group : values()) {
            if (group.name().toLowerCase(Locale.ROOT).equals(name)) {
                return group;
            }
        }
        return null;
    }

    public @NotNull String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
