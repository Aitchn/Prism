package com.aitchn.prism.core.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.core.block.ConnectedShapes;
import com.aitchn.prism.core.look.Collision;
import java.util.List;
import org.junit.jupiter.api.Test;

class BedrockShapesTest {

    @Test
    void aSolidLookCollidesAsOneFullCube() {
        List<BedrockShapes.Box> boxes = BedrockShapes.collision(Collision.FULL);
        assertEquals(List.of(BedrockShapes.Box.UNIT), boxes);
        // the frame Geyser's own BoxComponent.fullBox() uses: x and z centred, 16 pixels
        assertEquals(new BedrockShapes.BedrockBox(-8, 0, -8, 16, 16, 16), BedrockShapes.toBedrock(BedrockShapes.Box.UNIT));
        assertEquals(List.of(new BedrockShapes.BedrockBox(-8, 0, -8, 16, 16, 16)),
                BedrockShapes.collisionForBedrock(boxes));
    }

    @Test
    void aPassableLookHasNoCollisionButGeyserGetsTheEmptyBox() {
        List<BedrockShapes.Box> boxes = BedrockShapes.collision(Collision.NONE);
        assertTrue(boxes.isEmpty());
        List<BedrockShapes.BedrockBox> bedrock = BedrockShapes.collisionForBedrock(boxes);
        assertEquals(1, bedrock.size());
        assertTrue(bedrock.get(0).isEmpty());
    }

    @Test
    void partialBoxesAreMirroredOnXAndCentred() {
        // a slab of the west quarter and the lower half in Java: Bedrock's x axis is mirrored, so it sits at the east
        BedrockShapes.Box box = new BedrockShapes.Box(0, 0, 0, 0.25, 0.5, 1);
        BedrockShapes.BedrockBox bedrock = BedrockShapes.toBedrock(box);
        assertEquals(4f, bedrock.originX());
        assertEquals(0f, bedrock.originY());
        assertEquals(-8f, bedrock.originZ());
        assertEquals(4f, bedrock.sizeX());
        assertEquals(8f, bedrock.sizeY());
        assertEquals(16f, bedrock.sizeZ());
        // and the east quarter lands in the west
        assertEquals(-8f, BedrockShapes.toBedrock(new BedrockShapes.Box(0.75, 0, 0, 1, 1, 1)).originX());
    }

    @Test
    void boxesAreClampedToTheBlockLikeGeysersOwn() {
        BedrockShapes.BedrockBox tall = BedrockShapes.toBedrock(new BedrockShapes.Box(-0.5, -1, 0, 2, 3, 1));
        assertEquals(-8f, tall.originX());
        assertEquals(0f, tall.originY());
        assertEquals(16f, tall.sizeX());
        assertEquals(24f, tall.sizeY(), "a fence may reach 1.5 blocks");
    }

    @Test
    void pixelBoxesOfAModelBecomeBlockUnitsAndFlatOnesAreDropped() {
        List<BedrockShapes.Box> boxes = BedrockShapes.fromPixels(List.of(
                new ConnectedShapes.Box(0, 0, 0, 16, 1, 16), new ConnectedShapes.Box(4, 4, 4, 4, 8, 8)));
        assertEquals(List.of(new BedrockShapes.Box(0, 0, 0, 1, 1.0 / 16, 1)), boxes);
    }

    @Test
    void theOutlineIsTheBoundsOfTheShapeOrTheFullCube() {
        assertEquals(BedrockShapes.toBedrock(BedrockShapes.Box.UNIT), BedrockShapes.selection(List.of()));
        PrismComponents.Connections connections = new PrismComponents.Connections(
                PrismComponents.Connections.SIDES, List.of("self"), 6, 4, java.util.Optional.empty());
        // a cable with only its core: 6 pixels around the centre
        List<BedrockShapes.Box> core = BedrockShapes.fromPixels(ConnectedShapes.boxes(connections, 0));
        BedrockShapes.BedrockBox outline = BedrockShapes.selection(core);
        assertEquals(6f, outline.sizeX());
        assertEquals(-3f, outline.originX());
        assertEquals(5f, outline.originY());
        assertFalse(outline.isEmpty());
    }

    @Test
    void hardnessIsTheSecondsByHandOverTheVanillaFactor() {
        assertEquals(1.5f / 1.5f, BedrockHardness.of(new PrismComponents.Mining(1.5, "pickaxe")).destroyTime(), 1e-6);
        assertEquals(0f, BedrockHardness.of(new PrismComponents.Mining(0, "none")).destroyTime());
        assertEquals(-1f, BedrockHardness.of(new PrismComponents.Mining(-1, "none")).destroyTime());
        assertTrue(BedrockHardness.of(PrismComponents.Mining.DEFAULT).canBreakWithHand());
        assertEquals(1.0 / 1.5, BedrockHardness.of(PrismComponents.Mining.DEFAULT).destroyTime(), 1e-6);
    }

    @Test
    void blockNamesAreUniqueSafeIdentifiers() {
        String name = SyntheticBlock.nameOf("examplemod:ruby#on@east", 33001);
        assertEquals("p_examplemod_ruby_on_east_33001", name);
        assertTrue(name.matches("[a-z_][a-z0-9_]*"), name);
        assertEquals("p_" + "a".repeat(32) + "_5", SyntheticBlock.nameOf("a".repeat(80), 5));
        assertFalse(SyntheticBlock.nameOf("1:2", 1).charAt(0) >= '0' && SyntheticBlock.nameOf("1:2", 1).charAt(0) <= '9');
    }
}
