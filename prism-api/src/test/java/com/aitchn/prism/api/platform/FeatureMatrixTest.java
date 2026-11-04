package com.aitchn.prism.api.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FeatureMatrixTest {

    @Test
    void floorVersionHasItemModel() {
        FeatureMatrix m = FeatureMatrix.of(MinecraftVersion.parse("1.21.4"), PlatformKind.PAPER);
        assertTrue(m.supports(Feature.ITEM_MODEL_COMPONENT));
        assertTrue(m.supports(Feature.EQUIPPABLE_COMPONENT));
    }

    @Test
    void olderVersionDegradesInsteadOfFailing() {
        FeatureMatrix m = FeatureMatrix.of(MinecraftVersion.parse("1.21.3"), PlatformKind.PAPER);
        assertFalse(m.supports(Feature.ITEM_MODEL_COMPONENT));
        assertTrue(m.supports(Feature.EQUIPPABLE_COMPONENT));
        assertFalse(FeatureMatrix.of(MinecraftVersion.parse("1.20.4"), PlatformKind.PAPER)
                .supports(Feature.DATA_COMPONENTS));
    }

    @Test
    void regionizedThreadingIsFoliaOnly() {
        MinecraftVersion v = MinecraftVersion.parse("26.3");
        assertTrue(FeatureMatrix.of(v, PlatformKind.FOLIA).supports(Feature.REGIONIZED_THREADING));
        assertFalse(FeatureMatrix.of(v, PlatformKind.PAPER).supports(Feature.REGIONIZED_THREADING));
        assertFalse(FeatureMatrix.of(v, PlatformKind.PURPUR).supports(Feature.REGIONIZED_THREADING));
    }

    @Test
    void coversEveryFeatureAndIsImmutable() {
        FeatureMatrix m = FeatureMatrix.of(MinecraftVersion.parse("26.3"), PlatformKind.PURPUR);
        assertEquals(Feature.values().length, m.asMap().size());
        assertThrows(UnsupportedOperationException.class, () -> m.asMap().put(Feature.DATA_COMPONENTS, false));
    }

    @Test
    void platformInfoDerivesFeatures() {
        PlatformInfo info = PlatformInfo.of(PlatformKind.FOLIA, "Folia", MinecraftVersion.parse("26.3"));
        assertTrue(info.isRegionized());
        assertTrue(info.features().supports(Feature.REGIONIZED_THREADING));
    }
}
