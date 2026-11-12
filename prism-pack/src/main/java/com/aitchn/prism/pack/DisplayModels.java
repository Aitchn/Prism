package com.aitchn.prism.pack;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ContentException;
import com.aitchn.prism.core.content.YamlFiles;
import com.aitchn.prism.core.look.Dir;
import com.aitchn.prism.core.look.DisplayLighting;
import com.aitchn.prism.core.look.DisplayPlan;
import com.aitchn.prism.pack.json.JsonWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.jetbrains.annotations.NotNull;

/**
 * Plans how the display layer draws a block model. With {@link DisplayLighting#BLOCK} (the default, ADR-0025) that is
 * the whole model as one item model, checked for models whose face count would make thousands of blocks expensive for
 * clients. With {@link DisplayLighting#FACES} (ADR-0023) the model is also split by face direction into up to six item
 * models, so the display layer can draw each face from the
 * cell it looks into (see {@code DisplayParts}). A model can be split when every element is an unrotated box and every
 * face lies on the boundary of the cell: then a client lights each face from the neighbouring cell, and an entity in
 * that cell reproduces it. Any other model (inner faces, rotated elements, a vanilla parent Prism cannot expand) is
 * drawn whole by a single entity.
 *
 * <p>The face models are standalone (merged textures, only the elements of one direction) and named by the hash of their
 * content, {@code prism:face/<hash>}, so blocks that share a texture share their face models.
 */
final class DisplayModels {

    /** Vanilla parents whose elements are known: name to the texture variable of each direction. */
    private static final Map<String, Map<String, String>> PARENTS = Map.of(
            "cube", faces("#down", "#up", "#north", "#south", "#west", "#east"),
            "cube_all", faces("#all", "#all", "#all", "#all", "#all", "#all"),
            "cube_column", faces("#end", "#end", "#side", "#side", "#side", "#side"),
            "cube_column_horizontal", faces("#end", "#end", "#side", "#side", "#side", "#side"),
            "cube_bottom_top", faces("#bottom", "#top", "#side", "#side", "#side", "#side"),
            "cube_top", faces("#side", "#top", "#side", "#side", "#side", "#side"),
            "orientable", faces("#top", "#top", "#front", "#side", "#side", "#side"));

    private final Map<String, byte[]> files;
    private final List<String> warnings;
    private final Map<String, DisplayPlan> cache = new TreeMap<>();

    DisplayModels(Map<String, byte[]> files, List<String> warnings) {
        this.files = files;
        this.warnings = warnings;
    }

    private static Map<String, String> faces(String down, String up, String north, String south, String west, String east) {
        Map<String, String> faces = new LinkedHashMap<>();
        faces.put("down", down);
        faces.put("up", up);
        faces.put("north", north);
        faces.put("south", south);
        faces.put("west", west);
        faces.put("east", east);
        return faces;
    }

    /**
     * A model with more faces than this is reported at pack build: a display entity draws every face of its model, with
     * no culling against neighbours, so a model of many faces repeated over thousands of blocks is what costs a client
     * frames. A plain cube has six.
     */
    static final int MANY_FACES = 24;

    /** As {@link #plan(String, PrismKey, String, DisplayLighting, List)} with face lighting (ADR-0023), untinted. */
    DisplayPlan plan(String label, PrismKey model, String wholeItemModel) {
        return plan(label, model, wholeItemModel, DisplayLighting.FACES, List.of());
    }

    /**
     * The plan for drawing {@code model}, writing the face models and item definitions it needs into the pack.
     *
     * @param wholeItemModel the item model that draws the whole model (written by the caller): what
     *                       {@link DisplayLighting#BLOCK} draws, and what face lighting uses when the model cannot be
     *                       split; {@code ""} if there is none
     * @param lighting       {@link DisplayLighting#BLOCK} needs only the whole model; face lighting also gets face models
     * @param tints          constant tint colours by tint index ({@code prism:tint}); the whole item model carries them
     *                       already (its definition is the caller's), face item definitions get them here
     */
    DisplayPlan plan(String label, PrismKey model, String wholeItemModel, DisplayLighting lighting,
            List<Integer> tints) {
        String cached = model + "|" + wholeItemModel + "|" + lighting + "|" + tints;
        DisplayPlan known = cache.get(cached);
        if (known != null) {
            return known;
        }
        Optional<Expanded> expanded = expand(model);
        expanded.ifPresent(e -> checkCost(label, model, e));
        DisplayPlan plan;
        if (lighting == DisplayLighting.BLOCK) {
            if (wholeItemModel.isEmpty()) {
                warnings.add(label + ": the model has no item model; it is invisible");
            }
            plan = DisplayPlan.whole(wholeItemModel);
        } else {
            plan = compute(label, model, wholeItemModel, expanded, tints);
        }
        cache.put(cached, plan);
        return plan;
    }

    /** Reports a model whose face count makes thousands of its blocks expensive to draw (once per model). */
    private void checkCost(String label, PrismKey model, Expanded expanded) {
        if (!reported.add(model.toString())) {
            return;
        }
        int faces = 0;
        for (Map<String, Object> element : expanded.elements()) {
            if (element.get("faces") instanceof Map<?, ?> map) {
                faces += map.size();
            }
        }
        if (faces > MANY_FACES) {
            warnings.add(label + ": model " + model + " has " + expanded.elements().size() + " elements and " + faces
                    + " faces; every display entity of it draws all of them (a plain cube has 6), so thousands of"
                    + " such blocks cost clients frames. Consider a simpler model");
        }
    }

    private final java.util.Set<String> reported = new java.util.HashSet<>();

    private DisplayPlan compute(String label, PrismKey model, String wholeItemModel, Optional<Expanded> expanded,
            List<Integer> tints) {
        if (expanded.isEmpty() || !splittable(expanded.get())) {
            if (wholeItemModel.isEmpty()) {
                warnings.add(label + ": the model cannot be split by face and has no item model; it is invisible");
            }
            return DisplayPlan.whole(wholeItemModel);
        }
        Expanded model1 = expanded.get();
        List<String> ids = new ArrayList<>();
        for (int dir = 0; dir < 6; dir++) {
            List<Map<String, Object>> elements = new ArrayList<>();
            for (Map<String, Object> element : model1.elements()) {
                Map<?, ?> faces = (Map<?, ?>) element.get("faces");
                Object face = faces.get(Dir.name(dir));
                if (face != null) {
                    Map<String, Object> copy = new LinkedHashMap<>(element);
                    Map<String, Object> only = new LinkedHashMap<>();
                    only.put(Dir.name(dir), face);
                    copy.put("faces", only);
                    elements.add(copy);
                }
            }
            if (elements.isEmpty()) {
                ids.add("");
                continue;
            }
            Map<String, Object> json = new LinkedHashMap<>();
            if (model1.textureSize() != null) {
                json.put("texture_size", model1.textureSize());
            }
            json.put("textures", model1.textures());
            json.put("elements", elements);
            String text = JsonWriter.write(json);
            String name = "face/" + JavaPackBuilder.sha1(text.getBytes(StandardCharsets.UTF_8)).substring(0, 16);
            files.putIfAbsent("assets/prism/models/" + name + ".json", text.getBytes(StandardCharsets.UTF_8));
            // A tinted face shares the model of the untinted one but needs its own item definition.
            String item = tints.isEmpty() ? name : name + "_t" + JavaPackBuilder.sha1(tints.toString()
                    .getBytes(StandardCharsets.UTF_8)).substring(0, 8);
            files.putIfAbsent("assets/prism/items/" + item + ".json", JsonWriter.write(
                    Map.of("model", JavaPackBuilder.modelReference("prism:" + name, tints)))
                    .getBytes(StandardCharsets.UTF_8));
            ids.add("prism:" + item);
        }
        return DisplayPlan.split(ids, wholeItemModel);
    }

    /** A model with its parent chain merged: the elements of the first model that has some, and all textures. */
    private record Expanded(List<Map<String, Object>> elements, Map<String, Object> textures, Object textureSize) {}

    private Optional<Expanded> expand(PrismKey model) {
        List<Map<?, ?>> chain = new ArrayList<>();
        List<Map<String, String>> builtin = new ArrayList<>();
        PrismKey current = model;
        for (int depth = 0; depth < 8; depth++) {
            byte[] json = files.get("assets/" + current.namespace() + "/models/" + current.value() + ".json");
            if (json == null) {
                if (depth == 0) {
                    // no model JSON: Prism generates a cube_all of the texture with the model's name
                    Map<String, Object> generated = new LinkedHashMap<>();
                    generated.put("parent", "minecraft:block/cube_all");
                    generated.put("textures", Map.of("all", model.toString()));
                    chain.add(generated);
                    builtin.add(PARENTS.get("cube_all"));
                    break;
                }
                return Optional.empty();
            }
            Map<?, ?> root;
            try {
                Object parsed = YamlFiles.parse(current.toString(), new String(json, StandardCharsets.UTF_8));
                if (!(parsed instanceof Map<?, ?> map)) {
                    return Optional.empty();
                }
                root = map;
            } catch (ContentException | RuntimeException e) {
                return Optional.empty();
            }
            chain.add(root);
            Object parent = root.get("parent");
            if (parent == null) {
                break;
            }
            PrismKey parentKey;
            try {
                String text = String.valueOf(parent);
                parentKey = text.contains(":") ? PrismKey.parse(text) : new PrismKey("minecraft", text);
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
            if (parentKey.namespace().equals("minecraft")
                    && files.get("assets/minecraft/models/" + parentKey.value() + ".json") == null) {
                String name = parentKey.value().startsWith("block/") ? parentKey.value().substring(6) : "";
                Map<String, String> known = PARENTS.get(name);
                if (known == null) {
                    return Optional.empty();
                }
                builtin.add(known);
                break;
            }
            current = parentKey;
        }
        Map<String, Object> textures = new LinkedHashMap<>();
        List<Map<String, Object>> elements = new ArrayList<>();
        Object textureSize = null;
        for (int i = chain.size() - 1; i >= 0; i--) { // parents first, children override
            Map<?, ?> root = chain.get(i);
            if (root.get("textures") instanceof Map<?, ?> map) {
                map.forEach((k, v) -> textures.put(String.valueOf(k), v));
            }
            if (root.get("elements") instanceof List<?> list) {
                elements = new ArrayList<>();
                for (Object element : list) {
                    if (!(element instanceof Map<?, ?> e)) {
                        return Optional.empty();
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) e;
                    elements.add(typed);
                }
            }
            if (root.get("texture_size") != null) {
                textureSize = root.get("texture_size");
            }
        }
        if (elements.isEmpty() && !builtin.isEmpty()) {
            // a vanilla cube-like parent: a full cube whose faces use the parent's texture variables
            Map<String, Object> faces = new LinkedHashMap<>();
            builtin.getFirst().forEach((dir, variable) -> {
                Map<String, Object> face = new LinkedHashMap<>();
                face.put("texture", variable);
                faces.put(dir, face);
            });
            Map<String, Object> cube = new LinkedHashMap<>();
            cube.put("from", List.of(0, 0, 0));
            cube.put("to", List.of(16, 16, 16));
            cube.put("faces", faces);
            elements.add(cube);
        }
        if (elements.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Expanded(elements, textures, textureSize));
    }

    /** Whether every element is an unrotated box with every face on the boundary of the cell. */
    private static boolean splittable(Expanded model) {
        for (Map<String, Object> element : model.elements()) {
            if (!(element.get("from") instanceof List<?> from) || !(element.get("to") instanceof List<?> to)
                    || from.size() != 3 || to.size() != 3 || !(element.get("faces") instanceof Map<?, ?> faces)) {
                return false;
            }
            if (element.get("rotation") instanceof Map<?, ?> rotation && number(rotation.get("angle")) != 0) {
                return false;
            }
            for (Object key : faces.keySet()) {
                int dir = Dir.of(String.valueOf(key));
                if (dir < 0) {
                    return false;
                }
                // the plane the face lies in: the low side for north, west and down, the high side otherwise
                boolean low = dir == Dir.NORTH || dir == Dir.WEST || dir == Dir.DOWN;
                int axis = dir == Dir.EAST || dir == Dir.WEST ? 0 : dir == Dir.UP || dir == Dir.DOWN ? 1 : 2;
                double plane = number((low ? from : to).get(axis));
                if (plane != (low ? 0 : 16)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0;
    }
}
