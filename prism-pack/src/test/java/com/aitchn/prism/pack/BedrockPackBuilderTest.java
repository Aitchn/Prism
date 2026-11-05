package com.aitchn.prism.pack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ComponentMap;
import com.aitchn.prism.api.content.ItemDefinition;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class BedrockPackBuilderTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    private static ItemDefinition item(String key) {
        return new ItemDefinition(PrismKey.parse(key), "mod", PrismKey.parse("minecraft:paper"), ComponentMap.empty(),
                List.of());
    }

    private static BedrockPackBuilder.Input input(Map<String, byte[]> files, ItemDefinition... items) {
        return new BedrockPackBuilder.Input(Map.of("mod", files), List.of(items), "test");
    }

    @Test
    void itemWithTextureGetsIconAndMapping() throws Exception {
        var result = BedrockPackBuilder.build(input(Map.of("assets/m/textures/item/ruby.png", PNG), item("m:ruby")));
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
        assertEquals(1, result.itemsWithIcon().size());
        assertTrue(result.mappings().contains("\"bedrock_identifier\": \"m:ruby\""), result.mappings());
        assertTrue(result.mappings().contains("minecraft:paper"));
        Map<String, byte[]> zip = entries(result.mcpack());
        assertArrayEquals(PNG, zip.get("textures/prism/m/ruby.png"));
        assertTrue(new String(zip.get("textures/item_texture.json"), StandardCharsets.UTF_8).contains("m_ruby"));
        assertTrue(zip.containsKey("manifest.json"));
    }

    @Test
    void itemWithoutTextureIsSkippedWithWarning() {
        var result = BedrockPackBuilder.build(input(new LinkedHashMap<>(), item("m:ghost")));
        assertTrue(result.itemsWithIcon().isEmpty());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("m:ghost"));
    }

    @Test
    void layerZeroOfTheModelPicksTheTexture() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("assets/m/models/item/ruby.json",
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"m:gems/red\"}}"
                        .getBytes(StandardCharsets.UTF_8));
        files.put("assets/m/textures/gems/red.png", PNG);
        var result = BedrockPackBuilder.build(input(files, item("m:ruby")));
        assertEquals(1, result.itemsWithIcon().size());
        assertArrayEquals(PNG, entries(result.mcpack()).get("textures/prism/m/ruby.png"));
    }

    @Test
    void buildIsDeterministic() {
        var files = Map.of("assets/m/textures/item/ruby.png", PNG);
        assertEquals(BedrockPackBuilder.build(input(files, item("m:ruby"))).sha1(),
                BedrockPackBuilder.build(input(files, item("m:ruby"))).sha1());
    }

    @Test
    void nativeBlocksGetGeyserStateOverridesAndTerrainTextures() throws Exception {
        var block = new com.aitchn.prism.api.content.BlockDefinition(PrismKey.parse("m:press"), "mod",
                PrismKey.parse("minecraft:barrier"), java.util.Optional.empty(),
                ComponentMap.builder().put(com.aitchn.prism.api.content.PrismComponents.ROTATION,
                        com.aitchn.prism.api.content.PrismComponents.Rotation.HORIZONTAL).build(), List.of());
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("assets/m/models/block/press.json",
                "{\"parent\":\"minecraft:block/orientable\",\"textures\":{\"front\":\"m:block/press_front\",\"side\":\"m:block/press_side\",\"top\":\"m:block/press_top\"}}"
                        .getBytes(StandardCharsets.UTF_8));
        files.put("assets/m/textures/block/press_front.png", PNG);
        files.put("assets/m/textures/block/press_side.png", PNG);
        files.put("assets/m/textures/block/press_top.png", PNG);
        var input = new BedrockPackBuilder.Input(Map.of("mod", files), List.of(), "test", List.of(block),
                Map.of("m:press", 5, "m:press@east", 6));
        var result = BedrockPackBuilder.build(input);
        assertEquals(java.util.Set.of("m:press", "m:press@east"), result.blockLooks());
        String mappings = result.blockMappings();
        assertTrue(mappings.contains("minecraft:note_block"), mappings);
        assertTrue(mappings.contains("\"only_override_states\": true"), mappings);
        assertTrue(mappings.contains(com.aitchn.prism.core.block.NoteBlockStates.state(5).variant()), mappings);
        assertTrue(mappings.contains("prismb_m_block_press_front"), mappings);
        assertTrue(mappings.contains("\"rotation\""), mappings);
        Map<String, byte[]> zip = entries(result.mcpack());
        assertTrue(new String(zip.get("textures/terrain_texture.json"), StandardCharsets.UTF_8)
                .contains("prismb_m_block_press_side"));
        assertTrue(zip.containsKey("textures/prism/blocks/prismb_m_block_press_top.png"));
    }

    @Test
    void manifestVersionFollowsContent() throws Exception {
        String a = new String(entries(BedrockPackBuilder.build(input(Map.of("assets/m/textures/item/ruby.png", PNG),
                item("m:ruby"))).mcpack()).get("manifest.json"), StandardCharsets.UTF_8);
        String b = new String(entries(BedrockPackBuilder.build(input(Map.of("assets/m/textures/item/ruby.png",
                new byte[] {1, 2}), item("m:ruby"))).mcpack()).get("manifest.json"), StandardCharsets.UTF_8);
        assertTrue(!a.equals(b));
    }

    @Test
    void miniMessageTagsAreStripped() {
        assertEquals("Ruby", BedrockPackBuilder.plain("<red><bold>Ruby</bold></red>"));
    }

    private static Map<String, byte[]> entries(byte[] zip) throws Exception {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (var e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                out.put(e.getName(), in.readAllBytes());
            }
        }
        return out;
    }
}
