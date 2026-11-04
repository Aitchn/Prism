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
