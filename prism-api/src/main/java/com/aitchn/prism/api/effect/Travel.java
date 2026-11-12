package com.aitchn.prism.api.effect;

import com.aitchn.prism.api.PrismKey;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Something that flies from one point to another over a number of ticks, as players see it: an item model the client
 * moves smoothly along the line, a particle trail, or both. Nothing exists on the server; see
 * {@link EffectService#launch}.
 *
 * @param from        where it starts (its world is the world of the whole flight)
 * @param to          where it arrives; same world as {@code from}
 * @param ticks       how long the flight takes, 1 to {@link #MAX_TICKS}
 * @param model       the item model shown, if any: a Prism item key (its item model) or any item model id such as
 *                    {@code mymod:block/core} or {@code minecraft:ender_pearl}
 * @param scale       the model's scale; 1 draws it at the size of an item display entity's default
 * @param billboard   how the model turns towards the viewer
 * @param trail       particles left along the path while it flies, if any
 * @param trailPoints how many points of the trail are sent per tick, spread along the stretch flown in that tick
 *                    (1 to 8); each point sends the trail's {@code count} particles
 * @param onArrive    run once the flight is over, on the thread that owns {@code to} (Folia: its region), even when
 *                    no player saw it; never run if the travel is cancelled first
 */
public record Travel(
        @NotNull Location from,
        @NotNull Location to,
        int ticks,
        @NotNull Optional<PrismKey> model,
        float scale,
        @NotNull Billboard billboard,
        @NotNull Optional<ParticleSpec> trail,
        int trailPoints,
        @Nullable Runnable onArrive) {

    /** The longest flight, in ticks (five minutes). */
    public static final int MAX_TICKS = 6000;

    /** How a model turns towards the viewer; the same as a display entity's billboard. */
    public enum Billboard { FIXED, VERTICAL, HORIZONTAL, CENTER }

    public Travel {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(billboard, "billboard");
        Objects.requireNonNull(trail, "trail");
        if (from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            throw new IllegalArgumentException("from and to must be in the same, loaded world");
        }
        if (ticks < 1 || ticks > MAX_TICKS) {
            throw new IllegalArgumentException("ticks must be 1 to " + MAX_TICKS + ", got " + ticks);
        }
        if (!(scale > 0) || scale > 16) {
            throw new IllegalArgumentException("scale must be above 0 and at most 16, got " + scale);
        }
        if (trailPoints < 1 || trailPoints > 8) {
            throw new IllegalArgumentException("trailPoints must be 1 to 8, got " + trailPoints);
        }
        from = from.clone();
        to = to.clone();
    }

    /** A flight from {@code from} to {@code to} in {@code ticks}, showing nothing yet: add a model or a trail. */
    public static @NotNull Travel between(@NotNull Location from, @NotNull Location to, int ticks) {
        return new Travel(from, to, ticks, Optional.empty(), 1f, Billboard.CENTER, Optional.empty(), 2, null);
    }

    /** A flight from {@code from} over {@code distance} blocks along {@code direction} (need not be normalised). */
    public static @NotNull Travel along(@NotNull Location from, @NotNull Vector direction, double distance, int ticks) {
        if (direction.lengthSquared() == 0 || !Double.isFinite(distance) || distance < 0) {
            throw new IllegalArgumentException("direction must not be zero and distance must be 0 or more");
        }
        Vector step = direction.clone().normalize().multiply(distance);
        return between(from, from.clone().add(step), ticks);
    }

    /** Shows {@code model} (an item key or an item model id), moved smoothly by the client. */
    public @NotNull Travel withModel(@NotNull PrismKey model) {
        return new Travel(from, to, ticks, Optional.of(model), scale, billboard, trail, trailPoints, onArrive);
    }

    public @NotNull Travel withScale(float scale) {
        return new Travel(from, to, ticks, model, scale, billboard, trail, trailPoints, onArrive);
    }

    public @NotNull Travel withBillboard(@NotNull Billboard billboard) {
        return new Travel(from, to, ticks, model, scale, billboard, trail, trailPoints, onArrive);
    }

    /** Leaves {@code particle} behind, {@code points} points per tick along the path. */
    public @NotNull Travel withTrail(@NotNull ParticleSpec particle, int points) {
        return new Travel(from, to, ticks, model, scale, billboard, Optional.of(particle), points, onArrive);
    }

    /** Runs {@code callback} when the flight is over, on the thread that owns the destination. */
    public @NotNull Travel onArrive(@NotNull Runnable callback) {
        return new Travel(from, to, ticks, model, scale, billboard, trail, trailPoints, callback);
    }
}
