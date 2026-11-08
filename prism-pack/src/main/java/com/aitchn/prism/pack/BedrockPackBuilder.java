package com.aitchn.prism.pack;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.core.block.Appearances;
import com.aitchn.prism.core.block.ConnectedShapes;
import com.aitchn.prism.core.block.NoteBlockStates;
import com.aitchn.prism.core.content.YamlFiles;
import com.aitchn.prism.pack.json.JsonWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * Compiles the Bedrock side of the content: a {@code .mcpack} that carries the item icons, and the Geyser custom
 * item mappings (format 2) that connect a Java {@code item_model} to a Bedrock custom item.
 *
 * <p>Bedrock gets a flat icon per item. Anything richer (3D item models, dynamic states) is a documented
 * degradation; see docs/adr/0006-bedrock.md. The output is deterministic, so the SHA-1 identifies the content.
 */
public final class BedrockPackBuilder {

    /** Input for one build. {@code assets} maps mod id to its pack files, as for the Java pack. */
    public record Input(
            @NotNull Map<String, Map<String, byte[]>> assets,
            @NotNull Collection<ItemDefinition> items,
            @NotNull String description,
            @NotNull Collection<BlockDefinition> blocks,
            @NotNull Map<String, Integer> nativeStates) {

        /** Items only (no native blocks). */
        public Input(@NotNull Map<String, Map<String, byte[]>> assets, @NotNull Collection<ItemDefinition> items,
                @NotNull String description) {
            this(assets, items, description, List.of(), Map.of());
        }
    }

    /**
     * @param mcpack           the Bedrock resource pack
     * @param mappings         Geyser custom item mappings, JSON
     * @param itemsWithIcon    items that got a Bedrock custom item; the others keep their vanilla Bedrock look
     * @param blockMappings    Geyser custom block mappings (format 1) for native-state blocks, JSON
     * @param blockLooks       looks ({@code Appearances.Look#id()}) that got a Bedrock custom block state
     * @param soundEvents      sound events in {@code sound_definitions.json}
     * @param flipbooks        animated block textures
     * @param geometries       generated geometries of connected blocks
     */
    public record Result(
            byte @NotNull [] mcpack,
            @NotNull String sha1,
            @NotNull String mappings,
            @NotNull List<String> warnings,
            @NotNull Set<PrismKey> itemsWithIcon,
            @NotNull String blockMappings,
            @NotNull Set<String> blockLooks,
            int soundEvents,
            int flipbooks,
            int geometries) {}

    private static final UUID HEADER = UUID.nameUUIDFromBytes("prism:bedrock:header".getBytes(StandardCharsets.UTF_8));
    private static final UUID MODULE = UUID.nameUUIDFromBytes("prism:bedrock:module".getBytes(StandardCharsets.UTF_8));

    private BedrockPackBuilder() {}

    public static @NotNull Result build(@NotNull Input input) {
        List<String> warnings = new ArrayList<>();
        Map<String, byte[]> sources = new LinkedHashMap<>();
        input.assets().values().forEach(sources::putAll);

        Map<String, byte[]> files = new TreeMap<>();
        Map<String, Object> textureData = new TreeMap<>();
        Map<String, List<Object>> byBase = new TreeMap<>();
        Set<PrismKey> done = new LinkedHashSet<>();

        for (ItemDefinition item : input.items()) {
            PrismKey model = item.components().get(PrismComponents.MODEL).map(PrismComponents.Model::model)
                    .orElseGet(() -> new PrismKey(item.key().namespace(), "item/" + item.key().value()));
            String texturePath = textureFor(sources, model);
            byte[] png = sources.get(texturePath);
            if (png == null) {
                warnings.add("bedrock " + item.key() + ": no texture " + texturePath
                        + "; it keeps the vanilla Bedrock look of " + item.base());
                continue;
            }
            String icon = item.key().namespace() + "_" + item.key().value().replace('/', '_');
            String packTexture = "textures/prism/" + item.key().namespace() + "/"
                    + item.key().value().replace('/', '_');
            files.put(packTexture + ".png", png);
            textureData.put(icon, Map.of("textures", packTexture));

            Map<String, Object> options = new LinkedHashMap<>();
            options.put("icon", icon);
            options.put("allow_offhand", true);
            Map<String, Object> definition = new LinkedHashMap<>();
            definition.put("type", "definition");
            definition.put("model", item.key().toString());
            definition.put("bedrock_identifier", item.key().toString());
            item.components().get(PrismComponents.DISPLAY_NAME)
                    .ifPresent(name -> definition.put("display_name", plain(name.miniMessage())));
            definition.put("bedrock_options", options);
            byBase.computeIfAbsent(item.base().toString(), k -> new ArrayList<>()).add(definition);
            done.add(item.key());
        }

        Map<String, Object> terrainData = new TreeMap<>();
        Map<String, Object> flipbooks = new TreeMap<>();
        Map<String, Object> stateOverrides = new TreeMap<>();
        Set<String> looksDone = new LinkedHashSet<>();
        int geometries = 0;
        for (BlockDefinition block : input.blocks()) {
            var connections = block.components().get(PrismComponents.CONNECTIONS);
            if (connections.isPresent()) {
                // Generated shapes: one geometry per variant and mask, built from the same boxes as the Java model.
                for (String variant : Appearances.variants(block)) {
                    PrismKey texture = Appearances.connectionTexture(block, variant);
                    String texturePath = "assets/" + texture.namespace() + "/textures/" + texture.value() + ".png";
                    for (int mask : connections.get().masks()) {
                        Appearances.Look look = new Appearances.Look(block.key(),
                                Appearances.drawnVariant(variant, mask), "NORTH");
                        Integer state = input.nativeStates().get(look.id());
                        if (state == null) {
                            continue;
                        }
                        if (!sources.containsKey(texturePath)) {
                            warnings.add("bedrock block " + look.id() + ": no texture " + texturePath
                                    + "; Bedrock players see a plain note block");
                            continue;
                        }
                        String name = "prismb_" + look.id().replace(':', '_').replace('#', '_').replace('/', '_');
                        String geometry = "geometry.prism." + name;
                        files.put("models/blocks/prism/" + name + ".geo.json", JsonWriter.write(
                                ConnectedShapes.bedrockGeometry(geometry,
                                        ConnectedShapes.boxes(connections.get(), mask)))
                                .getBytes(StandardCharsets.UTF_8));
                        geometries++;
                        Map<String, Object> override = new LinkedHashMap<>();
                        override.put("display_name", look.block().toString());
                        override.put("geometry", geometry);
                        override.put("material_instances", Map.of("*",
                                material(terrainTexture(sources, files, terrainData, flipbooks, texturePath))));
                        stateOverrides.put(NoteBlockStates.state(state).variant(), override);
                        looksDone.add(look.id());
                    }
                }
                continue;
            }
            for (Appearances.Look look : Appearances.looks(block)) {
                Integer state = input.nativeStates().get(look.id());
                if (state == null) {
                    continue;
                }
                PrismKey model = Appearances.model(block, look.variant());
                Map<String, String> faces = blockFaces(sources, model);
                if (faces.isEmpty()) {
                    warnings.add("bedrock block " + look.id() + ": no texture for model " + model
                            + "; Bedrock players see a plain note block");
                    continue;
                }
                Map<String, Object> materials = new LinkedHashMap<>();
                faces.forEach((face, texturePath) -> materials.put(face,
                        material(terrainTexture(sources, files, terrainData, flipbooks, texturePath))));
                Map<String, Object> override = new LinkedHashMap<>();
                override.put("display_name", look.block().toString());
                override.put("material_instances", materials);
                int[] rotation = Appearances.rotation(look.facing());
                if (rotation[0] != 0 || rotation[1] != 0) {
                    // Bedrock turns counterclockwise for positive angles, Java clockwise (to verify on a client).
                    Map<String, Object> transformation = new LinkedHashMap<>();
                    transformation.put("rotation", List.of((360 - rotation[0]) % 360, (360 - rotation[1]) % 360, 0));
                    override.put("transformation", transformation);
                }
                stateOverrides.put(NoteBlockStates.state(state).variant(), override);
                looksDone.add(look.id());
            }
        }
        if (!terrainData.isEmpty()) {
            Map<String, Object> terrain = new LinkedHashMap<>();
            terrain.put("resource_pack_name", "prism");
            terrain.put("texture_name", "atlas.terrain");
            terrain.put("padding", 8);
            terrain.put("num_mip_levels", 4);
            terrain.put("texture_data", terrainData);
            files.put("textures/terrain_texture.json", JsonWriter.write(terrain).getBytes(StandardCharsets.UTF_8));
        }
        if (!flipbooks.isEmpty()) {
            files.put("textures/flipbook_textures.json",
                    JsonWriter.write(new ArrayList<>(flipbooks.values())).getBytes(StandardCharsets.UTF_8));
        }
        Map<String, Object> soundDefinitions = sounds(sources, files, warnings);
        if (!soundDefinitions.isEmpty()) {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("format_version", "1.14.0");
            root.put("sound_definitions", soundDefinitions);
            files.put("sounds/sound_definitions.json", JsonWriter.write(root).getBytes(StandardCharsets.UTF_8));
        }
        Map<String, Object> blockMappings = new LinkedHashMap<>();
        blockMappings.put("format_version", 1);
        Map<String, Object> blocks = new LinkedHashMap<>();
        if (!stateOverrides.isEmpty()) {
            Map<String, Object> noteBlock = new LinkedHashMap<>();
            noteBlock.put("name", "prism_native_block");
            noteBlock.put("display_name", "Prism block");
            noteBlock.put("included_in_creative_inventory", false);
            noteBlock.put("only_override_states", true);
            noteBlock.put("state_overrides", stateOverrides);
            blocks.put("minecraft:note_block", noteBlock);
        }
        blockMappings.put("blocks", blocks);

        Map<String, Object> itemTexture = new LinkedHashMap<>();
        itemTexture.put("resource_pack_name", "prism");
        itemTexture.put("texture_name", "atlas.items");
        itemTexture.put("texture_data", textureData);
        files.put("textures/item_texture.json", JsonWriter.write(itemTexture).getBytes(StandardCharsets.UTF_8));
        // The version must change with the content: Bedrock clients cache packs by uuid and version.
        files.put("manifest.json", manifest(input.description(), contentVersion(files)).getBytes(StandardCharsets.UTF_8));

        Map<String, Object> mappings = new LinkedHashMap<>();
        mappings.put("format_version", 2);
        mappings.put("items", new LinkedHashMap<String, Object>(byBase));
        byte[] zip = JavaPackBuilder.zip(files);
        return new Result(zip, JavaPackBuilder.sha1(zip), JsonWriter.write(mappings), List.copyOf(warnings),
                Set.copyOf(done), JsonWriter.write(blockMappings), Set.copyOf(looksDone), soundDefinitions.size(),
                flipbooks.size(), geometries);
    }

    /** Registers a Java block texture in the Bedrock terrain atlas (and as a flipbook if animated); returns its name. */
    private static String terrainTexture(Map<String, byte[]> sources, Map<String, byte[]> files,
            Map<String, Object> terrainData, Map<String, Object> flipbooks, String texturePath) {
        String name = "prismb_" + texturePath.substring("assets/".length(), texturePath.length() - 4)
                .replace("/textures/", "_").replace('/', '_');
        String packTexture = "textures/prism/blocks/" + name;
        files.put(packTexture + ".png", sources.get(texturePath));
        terrainData.put(name, Map.of("textures", packTexture));
        Animation animation = animation(sources, texturePath);
        if (animation != null) {
            // A Java animated texture (.png.mcmeta): the same vertical strip is a Bedrock flipbook.
            Map<String, Object> flipbook = new LinkedHashMap<>();
            flipbook.put("flipbook_texture", packTexture);
            flipbook.put("atlas_tile", name);
            flipbook.put("ticks_per_frame", animation.ticksPerFrame());
            if (animation.frames() != null) {
                flipbook.put("frames", animation.frames());
            }
            flipbooks.put(name, flipbook);
        }
        return name;
    }

    private static Map<String, Object> material(String texture) {
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("texture", texture);
        material.put("render_method", "alpha_test");
        return material;
    }

    /**
     * A Bedrock flipbook timing: ticks per frame, and the strip indices to show in order (null: every frame in order).
     * Java frames with their own {@code time} are repeated so one Bedrock tick step (the gcd of all times) fits them all.
     */
    record Animation(int ticksPerFrame, List<Integer> frames) {}

    /** The animation of a texture ({@code <texture>.png.mcmeta} with an {@code animation}), or null. */
    static Animation animation(Map<String, byte[]> sources, String texturePath) {
        byte[] meta = sources.get(texturePath + ".mcmeta");
        if (meta == null) {
            return null;
        }
        try {
            Object parsed = YamlFiles.parse(texturePath + ".mcmeta", new String(meta, StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?, ?> root) || !(root.get("animation") instanceof Map<?, ?> animation)) {
                return null;
            }
            int frameTime = animation.get("frametime") instanceof Number n ? Math.max(1, n.intValue()) : 1;
            if (!(animation.get("frames") instanceof List<?> declared) || declared.isEmpty()) {
                return new Animation(frameTime, null);
            }
            List<int[]> frames = new ArrayList<>();
            for (Object frame : declared) {
                if (frame instanceof Number index) {
                    frames.add(new int[] {Math.max(0, index.intValue()), frameTime});
                } else if (frame instanceof Map<?, ?> entry && entry.get("index") instanceof Number index) {
                    int time = entry.get("time") instanceof Number t ? Math.max(1, t.intValue()) : frameTime;
                    frames.add(new int[] {Math.max(0, index.intValue()), time});
                }
            }
            if (frames.isEmpty()) {
                return new Animation(frameTime, null);
            }
            int step = 0;
            for (int[] frame : frames) {
                step = gcd(step, frame[1]);
            }
            List<Integer> sequence = new ArrayList<>();
            for (int[] frame : frames) {
                for (int i = 0; i < frame[1] / step; i++) {
                    sequence.add(frame[0]);
                }
            }
            return new Animation(step, sequence);
        } catch (RuntimeException | com.aitchn.prism.api.content.ContentException ignored) {
            // malformed: the Java client reports it; Bedrock shows the strip as a still texture
        }
        return null;
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    /**
     * Bedrock {@code sound_definitions} from every mod's Java {@code assets/<ns>/sounds.json}, copying the .ogg files.
     * Geyser forwards a custom Java sound event under its full name ({@code ns:event}), so that is the Bedrock key.
     * Vanilla overrides ({@code minecraft} namespace) are left to Bedrock's own sounds.
     */
    static Map<String, Object> sounds(Map<String, byte[]> sources, Map<String, byte[]> files, List<String> warnings) {
        Map<String, Object> definitions = new TreeMap<>();
        for (Map.Entry<String, byte[]> entry : new TreeMap<>(sources).entrySet()) {
            String path = entry.getKey();
            String[] parts = path.split("/");
            if (parts.length != 3 || !parts[0].equals("assets") || !parts[2].equals("sounds.json")
                    || parts[1].equals("minecraft")) {
                continue;
            }
            String namespace = parts[1];
            Object parsed;
            try {
                parsed = YamlFiles.parse(path, new String(entry.getValue(), StandardCharsets.UTF_8));
            } catch (RuntimeException | com.aitchn.prism.api.content.ContentException e) {
                warnings.add("bedrock sounds: " + path + " is not valid JSON");
                continue;
            }
            if (!(parsed instanceof Map<?, ?> events)) {
                continue;
            }
            events.forEach((event, value) -> {
                if (!(value instanceof Map<?, ?> definition) || !(definition.get("sounds") instanceof List<?> list)) {
                    return;
                }
                List<Object> bedrockSounds = new ArrayList<>();
                for (Object sound : list) {
                    Map<?, ?> options = sound instanceof Map<?, ?> m ? m : Map.of();
                    Object name = sound instanceof Map<?, ?> m ? m.get("name") : sound;
                    if (!(name instanceof String file) || "event".equals(options.get("type"))) {
                        continue; // references to other events are not followed
                    }
                    PrismKey key = file.contains(":") ? PrismKey.parse(file) : new PrismKey("minecraft", file);
                    String source = "assets/" + key.namespace() + "/sounds/" + key.value() + ".ogg";
                    if (!sources.containsKey(source)) {
                        warnings.add("bedrock sound " + namespace + ":" + event + ": missing " + source);
                        continue;
                    }
                    String target = "sounds/prism/" + key.namespace() + "/" + key.value();
                    files.put(target + ".ogg", sources.get(source));
                    Map<String, Object> bedrock = new LinkedHashMap<>();
                    bedrock.put("name", target);
                    if (options.get("volume") instanceof Number volume) {
                        bedrock.put("volume", volume);
                    }
                    if (options.get("pitch") instanceof Number pitch) {
                        bedrock.put("pitch", pitch);
                    }
                    if (Boolean.TRUE.equals(options.get("stream"))) {
                        bedrock.put("stream", true);
                    }
                    bedrockSounds.add(bedrock);
                }
                if (!bedrockSounds.isEmpty()) {
                    Map<String, Object> bedrockDefinition = new LinkedHashMap<>();
                    bedrockDefinition.put("category", "block");
                    bedrockDefinition.put("sounds", bedrockSounds);
                    definitions.put(namespace + ":" + event, bedrockDefinition);
                }
            });
        }
        return definitions;
    }

    /** The texture an item icon is cut from: layer0 of its model JSON when present, else the model's own name. */
    private static String textureFor(Map<String, byte[]> sources, PrismKey model) {
        String modelPath = "assets/" + model.namespace() + "/models/" + model.value() + ".json";
        byte[] json = sources.get(modelPath);
        if (json != null) {
            try {
                Object parsed = YamlFiles.parse(modelPath, new String(json, StandardCharsets.UTF_8));
                if (parsed instanceof Map<?, ?> root && root.get("textures") instanceof Map<?, ?> textures
                        && textures.get("layer0") instanceof String layer) {
                    PrismKey key = layer.contains(":") ? PrismKey.parse(layer)
                            : new PrismKey(model.namespace(), layer);
                    return "assets/" + key.namespace() + "/textures/" + key.value() + ".png";
                }
            } catch (RuntimeException | com.aitchn.prism.api.content.ContentException ignored) {
                // fall through to the default texture; the Java pack reports malformed models
            }
        }
        return "assets/" + model.namespace() + "/textures/" + model.value() + ".png";
    }

    /** Strips MiniMessage tags; Bedrock names are plain text. */
    static String plain(String miniMessage) {
        return miniMessage.replaceAll("<[^<>]*>", "");
    }

    /** A pack version [1, a, b] derived from the files, so any content change is a new version. */
    static List<Integer> contentVersion(Map<String, byte[]> files) {
        String hash = JavaPackBuilder.sha1(JavaPackBuilder.zip(files));
        int a = Integer.parseInt(hash.substring(0, 4), 16);
        int b = Integer.parseInt(hash.substring(4, 8), 16);
        return List.of(1, a, b);
    }

    /**
     * Bedrock material instances for a block model: {@code *} plus per-face keys, each mapped to a texture file
     * that exists in the mods' assets. Reads the model's own {@code textures} (not its parent's): {@code all},
     * {@code side}, {@code end}, {@code top}, {@code bottom}, {@code front}, face names and {@code particle}.
     */
    static Map<String, String> blockFaces(Map<String, byte[]> sources, PrismKey model) {
        Map<String, String> faces = new LinkedHashMap<>();
        String modelPath = "assets/" + model.namespace() + "/models/" + model.value() + ".json";
        byte[] json = sources.get(modelPath);
        if (json == null) {
            String texture = "assets/" + model.namespace() + "/textures/" + model.value() + ".png";
            if (sources.containsKey(texture)) {
                faces.put("*", texture);
            }
            return faces;
        }
        Map<String, String> textures = new LinkedHashMap<>();
        try {
            Object parsed = YamlFiles.parse(modelPath, new String(json, StandardCharsets.UTF_8));
            if (parsed instanceof Map<?, ?> root && root.get("textures") instanceof Map<?, ?> map) {
                map.forEach((k, v) -> textures.put(String.valueOf(k), String.valueOf(v)));
            }
        } catch (RuntimeException | com.aitchn.prism.api.content.ContentException e) {
            return faces;
        }
        java.util.function.Function<String, String> resolve = name -> {
            String value = textures.get(name);
            for (int depth = 0; value != null && value.startsWith("#") && depth < 8; depth++) {
                value = textures.get(value.substring(1));
            }
            if (value == null) {
                return null;
            }
            PrismKey key = value.contains(":") ? PrismKey.parse(value) : new PrismKey("minecraft", value);
            String path = "assets/" + key.namespace() + "/textures/" + key.value() + ".png";
            return sources.containsKey(path) ? path : null;
        };
        String fallback = resolve.apply("all");
        if (fallback == null) {
            fallback = resolve.apply("side");
        }
        if (fallback == null) {
            fallback = resolve.apply("particle");
        }
        if (fallback != null) {
            faces.put("*", fallback);
        }
        String[][] faceSources = {
            {"up", "up", "top", "end"}, {"down", "down", "bottom", "end"}, {"north", "north", "front", "side"},
            {"south", "south", "side"}, {"east", "east", "side"}, {"west", "west", "side"}
        };
        for (String[] face : faceSources) {
            for (int i = 1; i < face.length; i++) {
                String found = resolve.apply(face[i]);
                if (found != null) {
                    if (!found.equals(faces.get("*"))) {
                        faces.put(face[0], found);
                    }
                    break;
                }
            }
        }
        if (!faces.containsKey("*") && !faces.isEmpty()) {
            faces.put("*", faces.values().iterator().next());
        }
        return faces;
    }

    private static String manifest(String description, List<Integer> version) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("name", "Prism");
        header.put("description", description);
        header.put("uuid", HEADER.toString());
        header.put("version", version);
        header.put("min_engine_version", List.of(1, 21, 0));
        Map<String, Object> module = new LinkedHashMap<>();
        module.put("type", "resources");
        module.put("uuid", MODULE.toString());
        module.put("version", version);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format_version", 2);
        root.put("header", header);
        root.put("modules", List.of(module));
        return JsonWriter.write(root);
    }
}
