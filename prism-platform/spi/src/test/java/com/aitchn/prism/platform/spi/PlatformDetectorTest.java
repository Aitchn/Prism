package com.aitchn.prism.platform.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aitchn.prism.api.platform.PlatformKind;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformDetectorTest {

    private static PlatformKind detect(String... present) {
        return PlatformDetector.detect(Set.of(present)::contains);
    }

    @Test
    void foliaWinsEvenThoughItAlsoLooksLikePaper() {
        assertEquals(
                PlatformKind.FOLIA,
                detect(PlatformDetector.FOLIA_MARKER, PlatformDetector.PAPER_MARKER));
    }

    @Test
    void purpurIsDetectedBeforePaper() {
        assertEquals(
                PlatformKind.PURPUR, detect(PlatformDetector.PURPUR_MARKER, PlatformDetector.PAPER_MARKER));
    }

    @Test
    void plainPaper() {
        assertEquals(PlatformKind.PAPER, detect(PlatformDetector.PAPER_MARKER));
    }

    @Test
    void spigotIsRejected() {
        assertThrows(UnsupportedPlatformException.class, () -> detect());
    }
}
