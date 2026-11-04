package com.aitchn.prism.pack.bedrock;

import com.aitchn.prism.api.content.ContentException;
import com.aitchn.prism.core.content.YamlFiles;
import com.aitchn.prism.pack.json.JsonWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Prototype: converts a Blockbench {@code .bbmodel} (the format BetterModel consumes) into a Bedrock
 * {@code minecraft:geometry} 1.12 document, so the same model asset can be shown to Bedrock players.
 *
 * <p>What converts: cubes, groups as bones (with pivot, rotation and nesting), box UV and per-face UV. What does not,
 * and is reported as a {@link Warning} instead of failing silently: mesh elements, animations, locators, null
 * objects, additional textures. Coordinates follow Blockbench's own Bedrock export (X is mirrored). This has not
 * been checked on a real Bedrock client yet; see docs/prototypes/bedrock-model-mapping.md.
 */
public final class BedrockGeometry {

    /** A feature of the source model that the Bedrock output does not carry. */
    public record Warning(String where, String message) {
        @Override
        public String toString() {
            return where + ": " + message;
        }
    }

    /** The geometry JSON and everything that was dropped. */
    public record Result(String json, List<Warning> warnings, int boneCount, int cubeCount) {}

    private static final List<String> FACES = List.of("north", "east", "south", "west", "up", "down");

    private final List<Warning> warnings = new ArrayList<>();
    private final Map<String, Map<String, Object>> cubes = new HashMap<>();
    private final List<Map<String, Object>> bones = new ArrayList<>();
    private final Set<String> usedBoneNames = new HashSet<>();
    private int cubeCount;

    private BedrockGeometry() {}

    /**
     * @param identifier geometry identifier without the {@code geometry.} prefix, for example {@code mymod.turret}
     */
    public static @NotNull Result convert(@NotNull String identifier, @NotNull String bbmodelJson)
            throws ContentException {
        return new BedrockGeometry().run(identifier, bbmodelJson);
    }

    private Result run(String identifier, String text) throws ContentException {
        Object parsed = YamlFiles.parse("bbmodel", text);
        if (!(parsed instanceof Map<?, ?> root)) {
            throw new ContentException("bbmodel", "expected a JSON object");
        }
        Object resolution = root.get("resolution");
        int width = 16;
        int height = 16;
        if (resolution instanceof Map<?, ?> res) {
            width = intOf(res.get("width"), 16);
            height = intOf(res.get("height"), 16);
        }
        Object elements = root.get("elements");
        if (elements instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof Map<?, ?> map) {
                    readElement(map);
                }
            }
        }
        List<Object> rootCubes = new ArrayList<>();
        Object outliner = root.get("outliner");
        if (outliner instanceof List<?> nodes) {
            for (Object node : nodes) {
                if (node instanceof String uuid) {
                    Map<String, Object> cube = cubes.get(uuid);
                    if (cube != null) {
                        rootCubes.add(cube);
                    }
                } else if (node instanceof Map<?, ?> group) {
                    readGroup(group, null);
                }
            }
        }
        cubeCount += rootCubes.size();
        if (!rootCubes.isEmpty()) {
            Map<String, Object> bone = new LinkedHashMap<>();
            bone.put("name", uniqueName("root"));
            bone.put("pivot", List.of(0, 0, 0));
            bone.put("cubes", rootCubes);
            bones.add(0, bone);
        }
        if (root.get("animations") instanceof List<?> animations && !animations.isEmpty()) {
            warnings.add(new Warning("animations", animations.size()
                    + " animation(s) dropped; Bedrock output is a static pose"));
        }
        if (root.get("textures") instanceof List<?> textures && textures.size() > 1) {
            warnings.add(new Warning("textures", textures.size()
                    + " textures found; Bedrock geometry binds one texture, only the first is used"));
        }
        if (bones.isEmpty()) {
            warnings.add(new Warning("model", "no convertible geometry"));
        }

        Map<String, Object> description = new LinkedHashMap<>();
        description.put("identifier", "geometry." + identifier);
        description.put("texture_width", width);
        description.put("texture_height", height);
        Map<String, Object> geometry = new LinkedHashMap<>();
        geometry.put("description", description);
        geometry.put("bones", bones);
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("format_version", "1.12.0");
        document.put("minecraft:geometry", List.of(geometry));
        return new Result(JsonWriter.write(document), List.copyOf(warnings), bones.size(), cubeCount);
    }

    private void readElement(Map<?, ?> element) {
        String uuid = String.valueOf(element.get("uuid"));
        String name = String.valueOf(element.get("name"));
        String type = element.get("type") == null ? "cube" : String.valueOf(element.get("type"));
        if (!type.equals("cube")) {
            warnings.add(new Warning(name, "element type '" + type + "' is not supported on Bedrock and was skipped"));
            return;
        }
        double[] from = vec(element.get("from"));
        double[] to = vec(element.get("to"));
        Map<String, Object> cube = new LinkedHashMap<>();
        cube.put("origin", List.of(-to[0], from[1], from[2]));
        cube.put("size", List.of(to[0] - from[0], to[1] - from[1], to[2] - from[2]));
        double inflate = element.get("inflate") instanceof Number n ? n.doubleValue() : 0;
        if (inflate != 0) {
            cube.put("inflate", inflate);
        }
        double[] rotation = vec(element.get("rotation"));
        if (rotation[0] != 0 || rotation[1] != 0 || rotation[2] != 0) {
            double[] origin = vec(element.get("origin"));
            cube.put("pivot", List.of(-origin[0], origin[1], origin[2]));
            cube.put("rotation", List.of(-rotation[0], -rotation[1], rotation[2]));
        }
        if (Boolean.TRUE.equals(element.get("box_uv")) || element.get("faces") == null) {
            double[] offset = vec(element.get("uv_offset"));
            cube.put("uv", List.of(offset[0], offset[1]));
        } else if (element.get("faces") instanceof Map<?, ?> faces) {
            Map<String, Object> uv = new LinkedHashMap<>();
            for (String face : FACES) {
                if (faces.get(face) instanceof Map<?, ?> data && data.get("uv") != null
                        && data.get("texture") != null) {
                    double[] box = vec4(data.get("uv"));
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("uv", List.of(box[0], box[1]));
                    entry.put("uv_size", List.of(box[2] - box[0], box[3] - box[1]));
                    uv.put(face, entry);
                }
            }
            cube.put("uv", uv);
        }
        cubes.put(uuid, cube);
    }

    private void readGroup(Map<?, ?> group, String parent) {
        String name = uniqueName(String.valueOf(group.get("name")));
        double[] origin = vec(group.get("origin"));
        double[] rotation = vec(group.get("rotation"));
        Map<String, Object> bone = new LinkedHashMap<>();
        bone.put("name", name);
        if (parent != null) {
            bone.put("parent", parent);
        }
        bone.put("pivot", List.of(-origin[0], origin[1], origin[2]));
        if (rotation[0] != 0 || rotation[1] != 0 || rotation[2] != 0) {
            bone.put("rotation", List.of(-rotation[0], -rotation[1], rotation[2]));
        }
        List<Object> boneCubes = new ArrayList<>();
        bones.add(bone);
        if (group.get("children") instanceof List<?> children) {
            for (Object child : children) {
                if (child instanceof String uuid) {
                    Map<String, Object> cube = cubes.get(uuid);
                    if (cube != null) {
                        boneCubes.add(cube);
                        cubeCount++;
                    } else {
                        warnings.add(new Warning(name, "child " + uuid + " is not a supported element"));
                    }
                } else if (child instanceof Map<?, ?> nested) {
                    readGroup(nested, name);
                }
            }
        }
        if (!boneCubes.isEmpty()) {
            bone.put("cubes", boneCubes);
        }
    }

    private String uniqueName(String raw) {
        String base = raw.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (base.isEmpty() || base.equals("null")) {
            base = "bone";
        }
        String name = base;
        for (int i = 2; !usedBoneNames.add(name); i++) {
            name = base + "_" + i;
        }
        return name;
    }

    private static double[] vec(Object value) {
        double[] out = new double[3];
        if (value instanceof List<?> list) {
            for (int i = 0; i < Math.min(3, list.size()); i++) {
                if (list.get(i) instanceof Number n) {
                    out[i] = n.doubleValue();
                }
            }
        }
        return out;
    }

    private static double[] vec4(Object value) {
        double[] out = new double[4];
        if (value instanceof List<?> list) {
            for (int i = 0; i < Math.min(4, list.size()); i++) {
                if (list.get(i) instanceof Number n) {
                    out[i] = n.doubleValue();
                }
            }
        }
        return out;
    }

    private static int intOf(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }
}
