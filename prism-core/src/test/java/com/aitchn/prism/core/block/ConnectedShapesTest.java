package com.aitchn.prism.core.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.content.PrismComponents;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConnectedShapesTest {

    private static final PrismComponents.Connections SIX =
            new PrismComponents.Connections(PrismComponents.Connections.SIDES, List.of("self"), 6, 4, Optional.empty());

    @Test
    void coreAloneThenOneArmPerConnectedSide() {
        assertEquals(List.of(new ConnectedShapes.Box(5, 5, 5, 11, 11, 11)), ConnectedShapes.boxes(SIX, 0));
        int northEast = PrismComponents.Connections.bit("NORTH") | PrismComponents.Connections.bit("EAST");
        List<ConnectedShapes.Box> boxes = ConnectedShapes.boxes(SIX, northEast);
        assertEquals(3, boxes.size());
        assertTrue(boxes.contains(new ConnectedShapes.Box(6, 6, 0, 10, 10, 5)), boxes.toString());
        assertTrue(boxes.contains(new ConnectedShapes.Box(11, 6, 6, 16, 10, 10)), boxes.toString());
    }

    @Test
    void masksCoverOnlyTheListedFaces() {
        var flat = new PrismComponents.Connections(List.of("NORTH", "EAST", "SOUTH", "WEST"), List.of(), 6, 6,
                Optional.empty());
        assertEquals(16, flat.masks().size());
        assertTrue(flat.masks().stream().noneMatch(mask -> (mask & PrismComponents.Connections.bit("UP")) != 0));
        assertEquals(64, SIX.masks().size());
    }

    @Test
    void fullCoreHasNoArms() {
        var full = new PrismComponents.Connections(PrismComponents.Connections.SIDES, List.of(), 16, 4,
                Optional.empty());
        assertEquals(1, ConnectedShapes.boxes(full, 63).size());
    }

    @Test
    void bedrockGeometryMirrorsXAndCentres() {
        Map<String, Object> geometry = ConnectedShapes.bedrockGeometry("geometry.test",
                List.of(new ConnectedShapes.Box(11, 6, 6, 16, 10, 10)));
        String text = geometry.toString();
        assertTrue(text.contains("origin=[-8.0, 6.0, -2.0]"), text);
        assertTrue(text.contains("size=[5.0, 4.0, 4.0]"), text);
        assertTrue(text.contains("identifier=geometry.test"), text);
    }

    @Test
    void drawnVariantNamesConnectedLooks() {
        assertEquals("c5", Appearances.drawnVariant("", 5));
        assertEquals("red_c0", Appearances.drawnVariant("red", 0));
        assertEquals("red", Appearances.drawnVariant("red", -1));
    }
}
