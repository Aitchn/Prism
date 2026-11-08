package com.aitchn.prism.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.content.ComponentMap;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.core.bedrock.BedrockShapes;
import com.aitchn.prism.core.bedrock.SyntheticBlock;
import com.aitchn.prism.core.bedrock.SyntheticIds;
import com.aitchn.prism.core.block.Appearances;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The Bedrock pack builder's synthetic blocks: one real custom block per look, whatever the legacy states. */
class SyntheticBlocksTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
    private static final int BASE = SyntheticIds.defaultBase(29_000);

    private static BlockDefinition block(String key, ComponentMap components) {
        return new BlockDefinition(PrismKey.parse(key), "mod", PrismKey.parse("minecraft:barrier"), Optional.empty(),
                components, List.of());
    }

    private static BedrockPackBuilder.Result build(Map<String, byte[]> files, List<BlockDefinition> blocks) {
        List<String> looks = new ArrayList<>();
        blocks.forEach(b -> Appearances.looks(b).forEach(l -> looks.add(l.id())));
        return BedrockPackBuilder.build(new BedrockPackBuilder.Input(Map.of("mod", files), List.of(), "test", blocks,
                Map.of(), SyntheticIds.plan(looks, BASE)));
    }

    private static SyntheticBlock find(BedrockPackBuilder.Result result, String lookId) {
        return result.syntheticBlocks().stream().filter(b -> b.lookId().equals(lookId)).findFirst().orElse(null);
    }

    @Test
    void aSolidLookIsACubeWithAFullCollisionAndItsOwnId() {
        var press = block("m:press", ComponentMap.builder().put(PrismComponents.ROTATION,
                PrismComponents.Rotation.HORIZONTAL).put(PrismComponents.MINING,
                new PrismComponents.Mining(3.0, "pickaxe")).build());
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("assets/m/models/block/press.json",
                "{\"parent\":\"minecraft:block/orientable\",\"textures\":{\"front\":\"m:block/press_front\",\"side\":\"m:block/press_side\",\"top\":\"m:block/press_top\"}}"
                        .getBytes(StandardCharsets.UTF_8));
        files.put("assets/m/textures/block/press_front.png", PNG);
        files.put("assets/m/textures/block/press_side.png", PNG);
        files.put("assets/m/textures/block/press_top.png", PNG);
        var result = build(files, List.of(press));

        assertEquals(4, result.syntheticBlocks().size(), "one block per facing");
        SyntheticBlock north = find(result, "m:press");
        SyntheticBlock east = find(result, "m:press@east");
        assertNotNull(north);
        assertEquals(BASE, north.javaId(), "ids follow look id order, and the plain look sorts first");
        assertEquals(BedrockShapes.collision(com.aitchn.prism.core.look.Collision.FULL), north.collision());
        assertFalse(north.passable());
        assertNull(north.geometry(), "the default cube");
        assertEquals("prismb_m_block_press_front", north.materials().get("north").texture());
        assertTrue(north.materials().containsKey("*"));
        assertEquals(2.0f, north.destroyTime(), 1e-6, "3 seconds by hand is hardness 2 with no tool needed");
        assertTrue(north.canBreakWithHand());
        assertEquals(0, north.rotationY());
        assertEquals(270, east.rotationY(), "Bedrock turns the other way round");
        assertTrue(north.name().startsWith("p_m_press"), north.name());
        assertEquals(Set.of(), result.blockLooks(), "no legacy note block state was asked for");
        assertFalse(result.blockMappings().contains("note_block"), result.blockMappings());
    }

    @Test
    void aPassableLookHasNoCollisionAndTheShapeOfItsModel() {
        var flower = block("m:flower", ComponentMap.builder().put(PrismComponents.PASSABLE,
                new PrismComponents.Passable(true)).build());
        var plate = block("m:plate", ComponentMap.builder().put(PrismComponents.PASSABLE,
                new PrismComponents.Passable(true)).build());
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("assets/m/textures/block/flower.png", PNG);
        files.put("assets/m/textures/block/plate.png", PNG);
        files.put("assets/m/models/block/plate.json",
                "{\"textures\":{\"all\":\"m:block/plate\"},\"elements\":[{\"from\":[0,0,0],\"to\":[16,2,16]}]}"
                        .getBytes(StandardCharsets.UTF_8));
        var result = build(files, List.of(flower, plate));
        SyntheticBlock cross = find(result, "m:flower");
        SyntheticBlock flat = find(result, "m:plate");
        assertTrue(cross.passable());
        assertEquals("geometry.prism.prismb_m_flower", cross.geometry());
        assertTrue(cross.selection().isEmpty(), "a cross is aimed at as the full cube");
        assertTrue(flat.passable());
        assertEquals(List.of(new BedrockShapes.Box(0, 0, 0, 1, 2.0 / 16, 1)), flat.selection());
        assertEquals(2, result.geometries());
        assertEquals(BedrockShapes.BedrockBox.EMPTY, BedrockShapes.collisionForBedrock(cross.collision()).get(0));
    }

    @Test
    void aConnectedLookGetsItsShapeButTheFullCollisionOfItsBarrierCarrier() {
        var connections = new PrismComponents.Connections(PrismComponents.Connections.SIDES, List.of("self"), 6, 4,
                Optional.empty());
        var cable = block("m:cable", ComponentMap.builder().put(PrismComponents.CONNECTIONS, connections).build());
        Map<String, byte[]> files = Map.of("assets/m/textures/block/cable.png", PNG);
        var result = build(files, List.of(cable));
        assertEquals(connections.masks().size(), result.syntheticBlocks().size());
        SyntheticBlock core = find(result, "m:cable#c0");
        assertNotNull(core);
        assertEquals("geometry.prism.prismb_m_cable_c0", core.geometry());
        assertFalse(core.passable(), "Java sees a barrier");
        assertEquals(1, core.selection().size());
        assertEquals(6.0 / 16, core.selection().get(0).sizeX(), 1e-9);
    }

    @Test
    void aLookWithoutATextureGetsNoSyntheticBlockAndASaneWarning() {
        var bare = block("m:bare", ComponentMap.empty());
        var result = build(Map.of(), List.of(bare));
        assertTrue(result.syntheticBlocks().isEmpty());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("m:bare")), result.warnings().toString());
    }

    @Test
    void everyLookOfThousandsOfBlocksGetsOneBlockAndTheSameBytesEachTime() {
        List<BlockDefinition> blocks = new ArrayList<>();
        Map<String, byte[]> files = new HashMap<>();
        for (int i = 0; i < 3000; i++) {
            blocks.add(block("s:b" + i, i % 10 == 0 ? ComponentMap.builder().put(PrismComponents.PASSABLE,
                    new PrismComponents.Passable(true)).build() : ComponentMap.empty()));
            files.put("assets/s/textures/block/b" + i + ".png", PNG);
        }
        var first = build(files, blocks);
        var second = build(files, blocks);
        assertEquals(3000, first.syntheticBlocks().size());
        assertEquals(first.sha1(), second.sha1());
        assertEquals(first.syntheticExport(), second.syntheticExport());
        long distinctNames = first.syntheticBlocks().stream().map(SyntheticBlock::name).distinct().count();
        assertEquals(3000, distinctNames);
        for (int i = 1; i < first.syntheticBlocks().size(); i++) {
            assertTrue(first.syntheticBlocks().get(i).javaId() > first.syntheticBlocks().get(i - 1).javaId());
        }
        assertEquals(300, first.syntheticBlocks().stream().filter(SyntheticBlock::passable).count());
        assertTrue(first.warnings().isEmpty(), first.warnings().toString());
    }

    @Test
    void theExportCarriesWhatAProxyExtensionNeeds() {
        var cube = block("m:cube", ComponentMap.empty());
        var result = build(Map.of("assets/m/textures/block/cube.png", PNG), List.of(cube));
        String json = result.syntheticExport();
        assertTrue(json.contains("\"format_version\": 1"), json);
        assertTrue(json.contains("\"base\": " + BASE), json);
        assertTrue(json.contains("\"state_count\": " + (BASE + 1)), json);
        assertTrue(json.contains("\"global_bits\": 16"), json);
        assertTrue(json.contains("\"java_id\": " + BASE), json);
        assertTrue(json.contains("\"java_identifier\": \"prism:p_m_cube_" + BASE + "\""), json);
        assertTrue(json.contains("\"piston_behavior\": \"IMMOVEABLE\""), json);
        assertTrue(json.contains("\"origin\": [-8, 0, -8]"), json);
        assertTrue(json.contains("\"material_instances\""), json);
    }
}
