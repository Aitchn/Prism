package com.aitchn.prism.pack;

import com.aitchn.prism.api.platform.MinecraftVersion;
import java.util.Map;
import java.util.TreeMap;
import org.jetbrains.annotations.NotNull;

/**
 * Resource pack format numbers by Minecraft version. A version newer than the table gets the newest known number and
 * is reported as not exact; the pack also declares an open-ended supported range, so clients still load it. The
 * server owner can pin a number in config.
 */
public final class PackFormats {

    /** @param exact false when the version is newer than the table and the number is an estimate */
    public record Format(int number, boolean exact) {}

    private static final TreeMap<MinecraftVersion, Integer> TABLE = new TreeMap<>(Map.ofEntries(
            Map.entry(new MinecraftVersion(1, 21, 4), 46),
            Map.entry(new MinecraftVersion(1, 21, 5), 55),
            Map.entry(new MinecraftVersion(1, 21, 6), 63),
            Map.entry(new MinecraftVersion(1, 21, 7), 64),
            Map.entry(new MinecraftVersion(1, 21, 9), 69),
            Map.entry(new MinecraftVersion(1, 21, 11), 75)));

    /** Oldest format Prism packs declare support for (the format of {@code item_model}). */
    public static final int MINIMUM = 46;

    private PackFormats() {}

    public static @NotNull Format forVersion(@NotNull MinecraftVersion version) {
        Map.Entry<MinecraftVersion, Integer> floor = TABLE.floorEntry(version);
        if (floor == null) {
            return new Format(MINIMUM, false);
        }
        boolean newerThanTable = version.compareTo(TABLE.lastKey()) > 0;
        return new Format(floor.getValue(), !newerThanTable);
    }
}
