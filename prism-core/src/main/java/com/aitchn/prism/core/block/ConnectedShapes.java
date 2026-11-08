package com.aitchn.prism.core.block;

import com.aitchn.prism.api.content.PrismComponents;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Generated block shapes. Connected blocks ({@code prism:connections}) are a centred core cube and one arm towards each
 * connected side, as boxes in model pixels (0-16), turned into a Java block model and a Bedrock geometry, so both
 * editions match. Passable blocks get a Bedrock geometry from their model's boxes, or a cross.
 */
public final class ConnectedShapes {

    /** One box, from and to corners in pixels. */
    public record Box(double x1, double y1, double z1, double x2, double y2, double z2) {}

    private ConnectedShapes() {}

    /** The core and the arms of {@code mask} (see {@link PrismComponents.Connections#bit}). */
    public static @NotNull List<Box> boxes(@NotNull PrismComponents.Connections connections, int mask) {
        double lo = 8 - connections.core() / 2.0;
        double hi = 8 + connections.core() / 2.0;
        double a = 8 - connections.arm() / 2.0;
        double b = 8 + connections.arm() / 2.0;
        List<Box> boxes = new ArrayList<>();
        boxes.add(new Box(lo, lo, lo, hi, hi, hi));
        if (lo <= 0) {
            return boxes; // a full-size core leaves no room for arms
        }
        for (String face : PrismComponents.Connections.SIDES) {
            if ((mask & PrismComponents.Connections.bit(face)) == 0) {
                continue;
            }
            boxes.add(switch (face) {
                case "NORTH" -> new Box(a, a, 0, b, b, lo);
                case "SOUTH" -> new Box(a, a, hi, b, b, 16);
                case "WEST" -> new Box(0, a, a, lo, b, b);
                case "EAST" -> new Box(hi, a, a, 16, b, b);
                case "UP" -> new Box(a, hi, a, b, 16, b);
                default -> new Box(a, 0, a, b, lo, b);
            });
        }
        return boxes;
    }

    /** A Java block model drawing {@code boxes} with one texture on every face (UVs follow the box position). */
    public static @NotNull Map<String, Object> javaModel(@NotNull List<Box> boxes, @NotNull String texture) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("parent", "minecraft:block/block");
        Map<String, Object> textures = new LinkedHashMap<>();
        textures.put("particle", texture);
        textures.put("all", texture);
        model.put("textures", textures);
        List<Object> elements = new ArrayList<>();
        for (Box box : boxes) {
            Map<String, Object> element = new LinkedHashMap<>();
            element.put("from", List.of(box.x1(), box.y1(), box.z1()));
            element.put("to", List.of(box.x2(), box.y2(), box.z2()));
            Map<String, Object> faces = new LinkedHashMap<>();
            for (String face : List.of("north", "east", "south", "west", "up", "down")) {
                faces.put(face, Map.of("texture", "#all")); // one entry: iteration order is fixed
            }
            element.put("faces", faces);
            elements.add(element);
        }
        model.put("elements", elements);
        return model;
    }

    /**
     * A Bedrock block geometry drawing {@code boxes}. Bedrock block geometry is centred on x and z (-8 to 8) and its x
     * axis is mirrored against Java's, so east arms stay east (to verify on a client).
     */
    public static @NotNull Map<String, Object> bedrockGeometry(@NotNull String identifier, @NotNull List<Box> boxes) {
        List<Object> cubes = new ArrayList<>();
        for (Box box : boxes) {
            double w = box.x2() - box.x1();
            double h = box.y2() - box.y1();
            double d = box.z2() - box.z1();
            Map<String, Object> cube = new LinkedHashMap<>();
            cube.put("origin", List.of(8 - box.x2(), box.y1(), box.z1() - 8));
            cube.put("size", List.of(w, h, d));
            Map<String, Object> uv = new LinkedHashMap<>();
            uv.put("north", face(16 - box.x2(), 16 - box.y2(), w, h));
            uv.put("south", face(box.x1(), 16 - box.y2(), w, h));
            uv.put("east", face(16 - box.z2(), 16 - box.y2(), d, h));
            uv.put("west", face(box.z1(), 16 - box.y2(), d, h));
            uv.put("up", face(box.x1(), box.z1(), w, d));
            uv.put("down", face(box.x1(), 16 - box.z2(), w, d));
            cube.put("uv", uv);
            cubes.add(cube);
        }
        Map<String, Object> bone = new LinkedHashMap<>();
        bone.put("name", "root");
        bone.put("pivot", List.of(0, 0, 0));
        bone.put("cubes", cubes);
        Map<String, Object> description = new LinkedHashMap<>();
        description.put("identifier", identifier);
        description.put("texture_width", 16);
        description.put("texture_height", 16);
        Map<String, Object> geometry = new LinkedHashMap<>();
        geometry.put("description", description);
        geometry.put("bones", List.of(bone));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format_version", "1.12.0");
        root.put("minecraft:geometry", List.of(geometry));
        return root;
    }

    /** A Bedrock geometry of two crossed planes across the block, like a Java {@code block/cross} model (a flower). */
    public static @NotNull Map<String, Object> bedrockCross(@NotNull String identifier) {
        Map<String, Object> root = bedrockGeometry(identifier, List.of());
        List<Object> cubes = new ArrayList<>();
        for (int angle : new int[] {45, -45}) {
            Map<String, Object> cube = new LinkedHashMap<>();
            cube.put("origin", List.of(-8, 0, 0));
            cube.put("size", List.of(16, 16, 0));
            cube.put("pivot", List.of(0, 8, 0));
            cube.put("rotation", List.of(0, angle, 0));
            Map<String, Object> uv = new LinkedHashMap<>();
            uv.put("north", face(0, 0, 16, 16));
            uv.put("south", face(0, 0, 16, 16));
            cube.put("uv", uv);
            cubes.add(cube);
        }
        bones(root).put("cubes", cubes);
        return root;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> bones(Map<String, Object> root) {
        var geometry = (Map<String, Object>) ((List<Object>) root.get("minecraft:geometry")).getFirst();
        return (Map<String, Object>) ((List<Object>) geometry.get("bones")).getFirst();
    }

    private static Map<String, Object> face(double u, double v, double w, double h) {
        Map<String, Object> face = new LinkedHashMap<>(); // fixed order: the pack's hash must not change per run
        face.put("uv", List.of(u, v));
        face.put("uv_size", List.of(w, h));
        return face;
    }
}
