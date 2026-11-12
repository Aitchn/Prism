package com.aitchn.prism.api.effect;

import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * A particle source bound to a block ({@code prism:particles}): what it emits, how often, where, and how far away a
 * player still gets it.
 *
 * @param particle what one emission sends
 * @param rate     emissions per second; may be below 1 ({@code 0.25} is one every four seconds). Prism spreads the
 *                 emissions of different blocks over time, so a wall of emitters does not pulse in step
 * @param offsetX  where the particles start, relative to the block's centre, for a block facing north; turned with
 *                 the block's horizontal facing
 * @param range    the farthest a player may be from the point and still get the particles, in blocks; 0 means the
 *                 server's {@code effects.particles.range}
 * @param variants the variants this emitter runs in; empty means every variant
 */
public record Emitter(
        @NotNull ParticleSpec particle,
        double rate,
        double offsetX,
        double offsetY,
        double offsetZ,
        double range,
        @NotNull List<String> variants) {

    /** The most emissions per second one emitter may ask for. */
    public static final double MAX_RATE = 20;

    /** The farthest range an emitter may ask for, in blocks. */
    public static final double MAX_RANGE = 128;

    public Emitter {
        Objects.requireNonNull(particle, "particle");
        variants = List.copyOf(variants);
        if (!(rate > 0) || rate > MAX_RATE) {
            throw new IllegalArgumentException("rate must be above 0 and at most " + MAX_RATE + ", got " + rate);
        }
        if (!(range >= 0) || range > MAX_RANGE) {
            throw new IllegalArgumentException("range must be 0 to " + MAX_RANGE + ", got " + range);
        }
        if (!Double.isFinite(offsetX + offsetY + offsetZ)) {
            throw new IllegalArgumentException("offset must be finite");
        }
    }

    /** An emitter of {@code particle} at the block's centre, {@code rate} times a second, in every variant. */
    public static @NotNull Emitter of(@NotNull ParticleSpec particle, double rate) {
        return new Emitter(particle, rate, 0, 0, 0, 0, List.of());
    }

    /** Whether the emitter runs while the block shows {@code variant}. */
    public boolean runsIn(@NotNull String variant) {
        return variants.isEmpty() || variants.contains(variant);
    }

    /** Reads one emitter mapping: the {@link ParticleSpec} keys plus {@code rate}, {@code offset}, {@code range}, {@code variants}. */
    public static @NotNull Emitter decode(@NotNull ConfigSection section) throws ContentException {
        Set<String> allowed = new HashSet<>(ParticleSpec.KEYS);
        allowed.addAll(Set.of("rate", "offset", "range", "variants"));
        section.rejectUnknownKeys(allowed);
        ParticleSpec particle = ParticleSpec.decode(section);
        double rate = section.getDouble("rate").orElse(1.0);
        if (!(rate > 0) || rate > MAX_RATE) {
            throw section.error("rate must be above 0 and at most " + MAX_RATE + " per second, got " + rate);
        }
        double[] offset = ParticleSpec.vector(section, "offset", 0);
        if (Math.abs(offset[0]) > 8 || Math.abs(offset[1]) > 8 || Math.abs(offset[2]) > 8) {
            throw section.error("offset must stay within 8 blocks of the block");
        }
        double range = section.getDouble("range").orElse(0.0);
        if (!(range >= 0) || range > MAX_RANGE) {
            throw section.error("range must be 0 to " + MAX_RANGE + " blocks, got " + range);
        }
        return new Emitter(particle, rate, offset[0], offset[1], offset[2], range,
                List.copyOf(section.getStringList("variants")));
    }
}
