package com.aitchn.prism.api.effect;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * One vanilla particle as it is sent to a client: the particle type, how many, how far they scatter and how fast they
 * move, plus the data a type needs. Plain data; Prism resolves it against the server's particle registry when it is
 * used, and logs (once) a type that does not exist or needs data the spec does not give.
 *
 * <ul>
 *   <li>{@code type}: a vanilla particle id such as {@code minecraft:flame} or {@code minecraft:dust}.
 *   <li>{@code count}: particles per emission, 1 to {@link #MAX_COUNT}.
 *   <li>{@code spreadX/Y/Z}: how far (in blocks, a Gaussian spread) the particles scatter around the point.
 *   <li>{@code speed}: the particle speed (the {@code extra} value of the protocol).
 *   <li>{@code color}: {@code 0xRRGGBB} for types that take a colour ({@code dust}, {@code dust_color_transition},
 *       {@code entity_effect}, {@code effect}, {@code instant_effect}, {@code tinted_leaves}, ...).
 *   <li>{@code size}: dust size (default 1) or the power of {@code effect} particles.
 *   <li>{@code material}: an item or block id for types that show one ({@code item}, {@code block},
 *       {@code falling_dust}, {@code dust_pillar}, {@code block_marker}).
 * </ul>
 */
public record ParticleSpec(
        @NotNull PrismKey type,
        int count,
        double spreadX,
        double spreadY,
        double spreadZ,
        double speed,
        @NotNull OptionalInt color,
        float size,
        @NotNull Optional<PrismKey> material) {

    /** The most particles one emission may carry. */
    public static final int MAX_COUNT = 256;

    /** The keys {@link #decode} reads. */
    public static final Set<String> KEYS = Set.of("particle", "count", "spread", "speed", "color", "size", "material");

    public ParticleSpec {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(material, "material");
        if (count < 1 || count > MAX_COUNT) {
            throw new IllegalArgumentException("count must be 1 to " + MAX_COUNT + ", got " + count);
        }
        if (!(spreadX >= 0 && spreadY >= 0 && spreadZ >= 0) || !Double.isFinite(spreadX + spreadY + spreadZ)) {
            throw new IllegalArgumentException("spread must be 0 or more");
        }
        if (!Double.isFinite(speed) || !(size > 0)) {
            throw new IllegalArgumentException("speed must be finite and size above 0");
        }
    }

    /** One particle of {@code type} ({@code minecraft:flame}; a bare name gets the {@code minecraft} namespace). */
    public static @NotNull ParticleSpec of(@NotNull String type) {
        return of(PrismKey.parse(type, PrismKey.MINECRAFT_NAMESPACE));
    }

    /** One particle of {@code type}, no spread, no speed, no data. */
    public static @NotNull ParticleSpec of(@NotNull PrismKey type) {
        return new ParticleSpec(type, 1, 0, 0, 0, 0, OptionalInt.empty(), 1f, Optional.empty());
    }

    /** A {@code minecraft:dust} particle of {@code rgb} ({@code 0xRRGGBB}) and {@code size}. */
    public static @NotNull ParticleSpec dust(int rgb, float size) {
        return of("minecraft:dust").withColor(rgb).withSize(size);
    }

    public @NotNull ParticleSpec withCount(int count) {
        return new ParticleSpec(type, count, spreadX, spreadY, spreadZ, speed, color, size, material);
    }

    public @NotNull ParticleSpec withSpread(double x, double y, double z) {
        return new ParticleSpec(type, count, x, y, z, speed, color, size, material);
    }

    public @NotNull ParticleSpec withSpeed(double speed) {
        return new ParticleSpec(type, count, spreadX, spreadY, spreadZ, speed, color, size, material);
    }

    /** @param rgb {@code 0xRRGGBB}; the alpha byte is ignored */
    public @NotNull ParticleSpec withColor(int rgb) {
        return new ParticleSpec(type, count, spreadX, spreadY, spreadZ, speed, OptionalInt.of(rgb & 0xFFFFFF), size,
                material);
    }

    public @NotNull ParticleSpec withSize(float size) {
        return new ParticleSpec(type, count, spreadX, spreadY, spreadZ, speed, color, size, material);
    }

    public @NotNull ParticleSpec withMaterial(@NotNull PrismKey material) {
        return new ParticleSpec(type, count, spreadX, spreadY, spreadZ, speed, color, size, Optional.of(material));
    }

    /**
     * Reads the particle keys of {@code section} ({@link #KEYS}); the caller rejects unknown keys. {@code spread} is
     * one number (every axis) or a list of three.
     */
    public static @NotNull ParticleSpec decode(@NotNull ConfigSection section) throws ContentException {
        PrismKey type;
        try {
            type = PrismKey.parse(section.requireString("particle"), PrismKey.MINECRAFT_NAMESPACE);
        } catch (IllegalArgumentException e) {
            throw section.error("particle: " + e.getMessage());
        }
        int count = section.getInt("count").orElse(1);
        if (count < 1 || count > MAX_COUNT) {
            throw section.error("count must be 1 to " + MAX_COUNT + ", got " + count);
        }
        double[] spread = vector(section, "spread", 0);
        if (spread[0] < 0 || spread[1] < 0 || spread[2] < 0) {
            throw section.error("spread must be 0 or more");
        }
        double speed = section.getDouble("speed").orElse(0.0);
        OptionalInt color = OptionalInt.empty();
        if (section.has("color")) {
            color = OptionalInt.of(color(section, section.requireString("color")));
        }
        double size = section.getDouble("size").orElse(1.0);
        if (!(size > 0) || size > 16) {
            throw section.error("size must be above 0 and at most 16, got " + size);
        }
        Optional<PrismKey> material = Optional.empty();
        if (section.has("material")) {
            try {
                material = Optional.of(PrismKey.parse(section.requireString("material"), PrismKey.MINECRAFT_NAMESPACE));
            } catch (IllegalArgumentException e) {
                throw section.error("material: " + e.getMessage());
            }
        }
        return new ParticleSpec(type, count, spread[0], spread[1], spread[2], speed, color, (float) size, material);
    }

    /** {@code #rrggbb} as {@code 0xRRGGBB}. */
    public static int color(@NotNull ConfigSection section, @NotNull String text) throws ContentException {
        if (!text.matches("#[0-9a-fA-F]{6}")) {
            throw section.error("colour must look like #rrggbb, got " + text);
        }
        return Integer.parseInt(text.substring(1), 16);
    }

    /** One number (every axis) or a list of three numbers under {@code key}; {@code fallback} on every axis if absent. */
    static double[] vector(ConfigSection section, String key, double fallback) throws ContentException {
        if (!section.has(key)) {
            return new double[] {fallback, fallback, fallback};
        }
        Optional<Double> single;
        try {
            single = section.getDouble(key);
        } catch (ContentException notANumber) {
            single = Optional.empty(); // a list, read below
        }
        if (single.isPresent()) {
            double value = single.get();
            if (!Double.isFinite(value)) {
                throw section.error(key + " must be a finite number");
            }
            return new double[] {value, value, value};
        }
        List<String> parts = section.getStringList(key);
        if (parts.size() != 3) {
            throw section.error(key + " must be a number or a list of three numbers");
        }
        double[] values = new double[3];
        for (int i = 0; i < 3; i++) {
            try {
                values[i] = Double.parseDouble(parts.get(i));
            } catch (NumberFormatException e) {
                throw section.error(key + " must hold numbers, got " + parts.get(i));
            }
            if (!Double.isFinite(values[i])) {
                throw section.error(key + " must hold finite numbers");
            }
        }
        return values;
    }
}
