package com.aitchn.prism.platform.spi;

import com.aitchn.prism.api.platform.PlatformKind;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;

/**
 * Decides which {@link PlatformKind} the server is by probing for marker classes. Uses no Bukkit types, so it can be
 * unit tested and cannot trigger Folia class loading on Paper.
 */
public final class PlatformDetector {

    /** Present only on Folia. */
    static final String FOLIA_MARKER = "io.papermc.paper.threadedregions.RegionizedServer";
    /** Present only on Purpur. */
    static final String PURPUR_MARKER = "org.purpurmc.purpur.PurpurConfig";
    /** Present on every supported Paper-based server. */
    static final String PAPER_MARKER = "io.papermc.paper.ServerBuildInfo";

    private PlatformDetector() {}

    /**
     * @param classExists answers whether a class with the given binary name is loadable on this server
     * @throws UnsupportedPlatformException if no Paper marker class exists (for example Spigot)
     */
    public static @NotNull PlatformKind detect(@NotNull Predicate<String> classExists) {
        if (classExists.test(FOLIA_MARKER)) {
            return PlatformKind.FOLIA;
        }
        if (classExists.test(PURPUR_MARKER)) {
            return PlatformKind.PURPUR;
        }
        if (classExists.test(PAPER_MARKER)) {
            return PlatformKind.PAPER;
        }
        throw new UnsupportedPlatformException(
                "Prism requires Paper, Folia or Purpur. Spigot and other servers are not supported.");
    }
}
