package com.aitchn.prism.core.look;

import org.jetbrains.annotations.NotNull;

/**
 * What a Java client does with a block state besides drawing it: it predicts collision from it, culls the faces of
 * neighbouring blocks if it occludes, and plays its sound group. A look may only borrow a vanilla state of the same
 * class, or the client would walk, cull or sound differently from what the custom block promises.
 */
public record PhysicsClass(@NotNull Collision collision, boolean occludes, @NotNull SoundGroup sound) {

    /** Text form for logs, for example {@code full/opaque/wood}. */
    public @NotNull String id() {
        return collision.name().toLowerCase(java.util.Locale.ROOT) + "/" + (occludes ? "opaque" : "clear") + "/"
                + sound.id();
    }

    /** The same collision and occlusion with another sound group. */
    public @NotNull PhysicsClass withSound(@NotNull SoundGroup other) {
        return new PhysicsClass(collision, occludes, other);
    }
}
