package com.aitchn.prism.pack.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.content.ContentException;
import com.aitchn.prism.core.content.YamlFiles;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
class BedrockGeometryTest {

    private static final String MODEL = """
            {
              "resolution": {"width": 32, "height": 32},
              "elements": [
                {"uuid": "c1", "name": "base", "type": "cube", "from": [0, 0, 0], "to": [4, 2, 6],
                 "box_uv": true, "uv_offset": [0, 0]},
                {"uuid": "c2", "name": "barrel", "type": "cube", "from": [1, 2, 1], "to": [3, 5, 2],
                 "origin": [2, 3, 1], "rotation": [10, 20, 30], "box_uv": false,
                 "faces": {"north": {"uv": [4, 4, 6, 7], "texture": 0}, "up": {"uv": [0, 0, 2, 1], "texture": 0},
                           "south": {"uv": [0, 0, 1, 1], "texture": null}}},
                {"uuid": "m1", "name": "blob", "type": "mesh"}
              ],
              "outliner": [
                "c1",
                {"name": "Turret Head", "origin": [2, 2, 0], "rotation": [0, 45, 0], "children": [
                  "c2",
                  {"name": "Muzzle", "origin": [2, 4, 1], "rotation": [0, 0, 0], "children": ["m1"]}
                ]}
              ],
              "textures": [{"name": "a"}, {"name": "b"}],
              "animations": [{"name": "spin"}]
            }
            """;

    private static Map<String, Object> parse(String json) throws ContentException {
        return (Map<String, Object>) YamlFiles.parse("out", json);
    }

    @Test
    void convertsCubesBonesAndMirrorsX() throws Exception {
        var result = BedrockGeometry.convert("mymod.turret", MODEL);
        Map<String, Object> doc = parse(result.json());

        assertEquals("1.12.0", doc.get("format_version"));
        Map<String, Object> geometry = ((List<Map<String, Object>>) doc.get("minecraft:geometry")).get(0);
        Map<String, Object> description = (Map<String, Object>) geometry.get("description");
        assertEquals("geometry.mymod.turret", description.get("identifier"));
        assertEquals(32, description.get("texture_width"));

        List<Map<String, Object>> bones = (List<Map<String, Object>>) geometry.get("bones");
        assertEquals(List.of("root", "turret_head", "muzzle"), bones.stream().map(b -> b.get("name")).toList());
        assertEquals("turret_head", bones.get(2).get("parent"));
        assertEquals(List.of(-2, 2, 0), bones.get(1).get("pivot"));
        assertEquals(List.of(0, -45, 0), bones.get(1).get("rotation"));

        Map<String, Object> base = ((List<Map<String, Object>>) bones.get(0).get("cubes")).get(0);
        assertEquals(List.of(-4, 0, 0), base.get("origin"));
        assertEquals(List.of(4, 2, 6), base.get("size"));
        assertEquals(List.of(0, 0), base.get("uv"));
    }

    @Test
    void perFaceUvAndCubeRotation() throws Exception {
        Map<String, Object> doc = parse(BedrockGeometry.convert("m.t", MODEL).json());
        var bones = (List<Map<String, Object>>) ((Map<String, Object>) ((List<Object>) doc.get("minecraft:geometry")).get(0)).get("bones");
        Map<String, Object> barrel = ((List<Map<String, Object>>) bones.get(1).get("cubes")).get(0);
        assertEquals(List.of(-10, -20, 30), barrel.get("rotation"));
        assertEquals(List.of(-2, 3, 1), barrel.get("pivot"));
        Map<String, Object> uv = (Map<String, Object>) barrel.get("uv");
        assertEquals(List.of(2, 3), ((Map<String, Object>) uv.get("north")).get("uv_size"));
        assertTrue(uv.containsKey("up"));
        assertTrue(!uv.containsKey("south"), "faces without a texture are omitted");
    }

    @Test
    void reportsEverythingThatWasDropped() throws Exception {
        var result = BedrockGeometry.convert("m.t", MODEL);
        String all = result.warnings().toString();
        assertTrue(all.contains("mesh"), all);
        assertTrue(all.contains("animation"), all);
        assertTrue(all.contains("textures"), all);
        assertEquals(2, result.cubeCount());
    }

    @Test
    void emptyModelWarnsInsteadOfFailing() throws Exception {
        var result = BedrockGeometry.convert("m.t", "{}");
        assertEquals(1, result.warnings().size());
    }
}
