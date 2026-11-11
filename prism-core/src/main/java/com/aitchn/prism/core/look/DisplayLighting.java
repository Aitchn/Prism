package com.aitchn.prism.core.look;

import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * How the display layer places the entities of a block, which decides where a client takes their light from
 * ({@code blocks.display.lighting}, ADR-0025).
 */
public enum DisplayLighting {

    /**
     * One entity per block, at the block's centre, drawing the whole model. The block is shown to the client as an
     * invisible shell that lets light through, so the client lights the entity from the block's own cell. The default.
     */
    BLOCK,

    /**
     * One entity per visible face, each placed in the neighbouring cell the face looks into and moved back onto the
     * block (ADR-0023). Lit per face like a vanilla block, but up to six entities per block.
     */
    FACES;

    /** The mode called {@code name} ({@code block} or {@code faces}, any case), or null. */
    public static @Nullable DisplayLighting parse(@Nullable String name) {
        if (name == null) {
            return null;
        }
        for (DisplayLighting mode : values()) {
            if (mode.name().equalsIgnoreCase(name.trim())) {
                return mode;
            }
        }
        return null;
    }

    /** The most entities one block can need in this mode. */
    public int maxPerBlock() {
        return this == BLOCK ? 1 : 6;
    }

    public @NotNull String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
