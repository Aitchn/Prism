package com.aitchn.prism.pack;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.core.bedrock.BedrockHardness;
import com.aitchn.prism.core.bedrock.BedrockShapes;
import com.aitchn.prism.core.bedrock.SyntheticBlock;
import com.aitchn.prism.core.bedrock.SyntheticIds;
import com.aitchn.prism.core.block.Appearances;
import com.aitchn.prism.core.block.ConnectedShapes;
import com.aitchn.prism.core.block.NoteBlockStates;
import com.aitchn.prism.core.block.TripwireStates;
import com.aitchn.prism.core.content.YamlFiles;
import com.aitchn.prism.core.look.Collision;
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

    /**
     * Input for one build. {@code assets} maps mod id to its pack files, as for the Java pack. {@code nativeStates} maps
     * a look to the note block or tripwire state Geyser's JSON mappings know (the legacy route, a few hundred looks at
     * most); {@code synthetic} maps a look to its synthetic Java state id (ADR-0024), one real Bedrock custom block per
     * look and no limit.
     */
    public record Input(
            @NotNull Map<String, Map<String, byte[]>> assets,
            @NotNull Collection<ItemDefinition> items,
            @NotNull String description,
            @NotNull Collection<BlockDefinition> blocks,
            @NotNull Map<String, Integer> nativeStates,
            @NotNull SyntheticIds.Plan synthetic) {

        /** Legacy states only, no synthetic blocks. */
        public Input(@NotNull Map<String, Map<String, byte[]>> assets, @NotNull Collection<ItemDefinition> items,
                @NotNull String description, @NotNull Collection<BlockDefinition> blocks,
                @NotNull Map<String, Integer> nativeStates) {
            this(assets, items, description, blocks, nativeStates, SyntheticIds.Plan.empty(0));
        }

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
     * @param geometries       generated geometries of connected and passable blocks
     * @param gaugeIcons       step icons of gauge items ({@code prism:gauge})
     * @param syntheticBlocks  one registration record per look that has a synthetic state and a texture, in id order
     * @param syntheticExport  the same as JSON, for a Geyser extension on a proxy ({@code prism_synthetic_blocks.json})
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
            int geometries,
            int gaugeIcons,
            @NotNull List<SyntheticBlock> syntheticBlocks,
            @NotNull String syntheticExport) {}

    /**
     * Suffix of a {@link Input#nativeStates} key for the state a look owns on its other carrier: a block that gained or
     * lost {@code prism:passable} keeps drawing its earlier placements.
     */
    public static final String OTHER_CARRIER = "|other";

    /** Internal marker of the synthetic pass over a look's states (never a key of {@link Input#nativeStates}). */
    private static final String SYNTHETIC = "|synthetic";

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

        int gaugeIcons = 0;
        for (ItemDefinition item : input.items()) {
            int steps = GaugeIcons.addTextures(sources, item);
            if (steps > 0) {
                // A gauge: one Bedrock item per step, keyed on the same item_model as the Java step definitions.
                boolean all = true;
                for (int step = 0; step <= steps; step++) {
                    PrismKey look = Appearances.gaugeLook(item.key(), step);
                    all &= icon(sources, files, textureData, byBase, warnings, item, look,
                            Appearances.gaugeModel(item.key(), step));
                }
                if (all && icon(sources, files, textureData, byBase, warnings, item, item.key(),
                        Appearances.gaugeModel(item.key(), steps))) {
                    done.add(item.key());
                    gaugeIcons += steps + 1;
                }
                continue;
            }
            PrismKey model = item.components().get(PrismComponents.MODEL).map(PrismComponents.Model::model)
                    .orElseGet(() -> new PrismKey(item.key().namespace(), "item/" + item.key().value()));
            if (icon(sources, files, textureData, byBase, warnings, item, item.key(), model)) {
                done.add(item.key());
            }
        }

        Map<String, Object> terrainData = new TreeMap<>();
        Map<String, Object> flipbooks = new TreeMap<>();
        Map<String, Object> stateOverrides = new TreeMap<>();
        Map<String, Object> tripwireOverrides = new TreeMap<>();
        Set<String> looksDone = new LinkedHashSet<>();
        List<SyntheticBlock> synthetics = new ArrayList<>();
        Set<String> geometriesDone = new LinkedHashSet<>();
        int geometries = 0;
        for (BlockDefinition block : input.blocks()) {
            var connections = block.components().get(PrismComponents.CONNECTIONS);
            var physics = Appearances.physics(block);
            var hardness = BedrockHardness.of(block.components().get(PrismComponents.MINING)
                    .orElse(PrismComponents.Mining.DEFAULT));
            if (connections.isPresent()) {
                // Generated shapes: one geometry per variant and mask, built from the same boxes as the Java model.
                for (String variant : Appearances.variants(block)) {
                    PrismKey texture = Appearances.connectionTexture(block, variant);
                    String texturePath = "assets/" + texture.namespace() + "/textures/" + texture.value() + ".png";
                    for (int mask : connections.get().masks()) {
                        Appearances.Look look = new Appearances.Look(block.key(),
                                Appearances.drawnVariant(variant, mask), "NORTH");
                        Integer state = input.nativeStates().get(look.id());
                        Integer synthetic = syntheticId(input, look.id());
                        if (state == null && synthetic == null) {
                            continue;
                        }
                        if (!sources.containsKey(texturePath)) {
                            warnings.add("bedrock block " + look.id() + ": no texture " + texturePath
                                    + "; Bedrock players see a plain block");
                            continue;
                        }
                        String name = "prismb_" + look.id().replace(':', '_').replace('#', '_').replace('/', '_');
                        String geometry = "geometry.prism." + name;
                        List<ConnectedShapes.Box> boxes = ConnectedShapes.boxes(connections.get(), mask);
                        files.put("models/blocks/prism/" + name + ".geo.json", JsonWriter.write(
                                ConnectedShapes.bedrockGeometry(geometry, boxes))
                                .getBytes(StandardCharsets.UTF_8));
                        if (geometriesDone.add(name)) {
                            geometries++;
                        }
                        String atlas = terrainTexture(sources, files, terrainData, flipbooks, texturePath);
                        if (state != null) {
                            Map<String, Object> override = new LinkedHashMap<>();
                            override.put("display_name", look.block().toString());
                            override.put("geometry", geometry);
                            override.put("material_instances", Map.of("*", material(atlas)));
                            stateOverrides.put(NoteBlockStates.state(state).variant(), override);
                            looksDone.add(look.id());
                        }
                        if (synthetic != null) {
                            synthetics.add(synthetic(look, synthetic, geometry, Map.of("*", atlas),
                                    physics.collision(), BedrockShapes.fromPixels(boxes), 0, 0, hardness));
                        }
                    }
                }
                continue;
            }
            for (Appearances.Look look : Appearances.looks(block)) {
                Integer synthetic = syntheticId(input, look.id());
                for (String key : List.of(look.id(), look.id() + OTHER_CARRIER, SYNTHETIC)) {
                    boolean isSynthetic = key.equals(SYNTHETIC);
                    Integer state = isSynthetic ? synthetic : input.nativeStates().get(key);
                    if (state == null) {
                        continue;
                    }
                    PrismKey model = Appearances.model(block, look.variant());
                    Map<String, String> faces = blockFaces(sources, model);
                    if (faces.isEmpty()) {
                        if (!isSynthetic || input.nativeStates().get(look.id()) == null) {
                            warnings.add("bedrock block " + look.id() + ": no texture for model " + model
                                    + "; Bedrock players see a plain block");
                        }
                        continue;
                    }
                    boolean tripwire = isSynthetic ? physics.collision() == Collision.NONE
                            : TripwireStates.isTripwire(state);
                    Map<String, Object> materials = new LinkedHashMap<>();
                    Map<String, String> atlasNames = new LinkedHashMap<>();
                    // prism:tint: Bedrock has no tint index, so tinted textures get a copy with the colour baked in.
                    List<Integer> tints = JavaPackBuilder.tints(block.components(), look.variant());
                    Map<String, Integer> tinted = tints.isEmpty() ? Map.of() : TintBake.tintIndexes(sources, model);
                    faces.forEach((face, texturePath) -> {
                        Integer index = tinted.get(texturePath);
                        int tint = index != null && index >= 0 && index < tints.size() ? tints.get(index) : NO_TINT;
                        String atlas = terrainTexture(sources, files, terrainData, flipbooks, texturePath, tint,
                                warnings);
                        atlasNames.put(face, atlas);
                        materials.put(face, material(atlas));
                    });
                    Map<String, Object> override = new LinkedHashMap<>();
                    override.put("display_name", look.block().toString());
                    override.put("material_instances", materials);
                    String geometry = null;
                    List<BedrockShapes.Box> outline = List.of();
                    if (tripwire) {
                        // Passable: no collision, and the shape of the Java model (its boxes, or a cross like a flower).
                        String name = "prismb_" + look.id().replace(':', '_').replace('#', '_').replace('@', '_')
                                .replace('/', '_');
                        geometry = "geometry.prism." + name;
                        List<ConnectedShapes.Box> boxes = modelBoxes(sources, model);
                        files.put("models/blocks/prism/" + name + ".geo.json", JsonWriter.write(boxes == null
                                ? ConnectedShapes.bedrockCross(geometry)
                                : ConnectedShapes.bedrockGeometry(geometry, boxes)).getBytes(StandardCharsets.UTF_8));
                        if (geometriesDone.add(name)) {
                            geometries++;
                        }
                        override.put("geometry", geometry);
                        Map<String, Object> noBox = new LinkedHashMap<>();
                        noBox.put("origin", List.of(0, 0, 0));
                        noBox.put("size", List.of(0, 0, 0));
                        override.put("collision_box", noBox);
                        outline = boxes == null ? List.of() : BedrockShapes.fromPixels(boxes);
                    }
                    int[] rotation = Appearances.rotation(look.facing());
                    if (rotation[0] != 0 || rotation[1] != 0) {
                        // Bedrock turns counterclockwise for positive angles, Java clockwise (to verify on a client).
                        Map<String, Object> transformation = new LinkedHashMap<>();
                        transformation.put("rotation", List.of((360 - rotation[0]) % 360, (360 - rotation[1]) % 360, 0));
                        override.put("transformation", transformation);
                    }
                    if (isSynthetic) {
                        synthetics.add(synthetic(look, state, geometry, atlasNames,
                                physics.collision(), outline, (360 - rotation[0]) % 360, (360 - rotation[1]) % 360,
                                hardness));
                        continue;
                    }
                    if (tripwire) {
                        tripwireOverrides.put(TripwireStates.state(state).variant(), override);
                    } else {
                        stateOverrides.put(NoteBlockStates.state(state).variant(), override);
                    }
                    looksDone.add(look.id());
                }
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
        if (!tripwireOverrides.isEmpty()) {
            Map<String, Object> tripwire = new LinkedHashMap<>();
            tripwire.put("name", "prism_passable_block");
            tripwire.put("display_name", "Prism passable block");
            tripwire.put("included_in_creative_inventory", false);
            tripwire.put("only_override_states", true);
            tripwire.put("state_overrides", tripwireOverrides);
            blocks.put("minecraft:tripwire", tripwire);
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
        synthetics.sort(java.util.Comparator.comparingInt(SyntheticBlock::javaId));
        return new Result(zip, JavaPackBuilder.sha1(zip), JsonWriter.write(mappings), List.copyOf(warnings),
                Set.copyOf(done), JsonWriter.write(blockMappings), Set.copyOf(looksDone), soundDefinitions.size(),
                flipbooks.size(), geometries, gaugeIcons, List.copyOf(synthetics),
                SyntheticExport.json(input.synthetic(), synthetics));
    }

    /** No {@code prism:tint} colour for a texture. */
    private static final int NO_TINT = -1;

    /** Registers a Java block texture in the Bedrock terrain atlas (and as a flipbook if animated); returns its name. */
    private static String terrainTexture(Map<String, byte[]> sources, Map<String, byte[]> files,
            Map<String, Object> terrainData, Map<String, Object> flipbooks, String texturePath) {
        return terrainTexture(sources, files, terrainData, flipbooks, texturePath, NO_TINT, new ArrayList<>());
    }

    /**
     * As {@link #terrainTexture(Map, Map, Map, Map, String)}; with a {@code tint} ({@code 0xRRGGBB}) the atlas entry is a
     * copy of the texture with the colour multiplied in, named after the colour so equal tints share it.
     */
    private static String terrainTexture(Map<String, byte[]> sources, Map<String, byte[]> files,
            Map<String, Object> terrainData, Map<String, Object> flipbooks, String texturePath, int tint,
            List<String> warnings) {
        String name = "prismb_" + texturePath.substring("assets/".length(), texturePath.length() - 4)
                .replace("/textures/", "_").replace('/', '_');
        byte[] png = sources.get(texturePath);
        if (tint != NO_TINT) {
            byte[] baked = TintBake.multiply(png, tint);
            if (baked == null) {
                warnings.add("bedrock: cannot read " + texturePath + " to bake its prism:tint; Bedrock shows it untinted");
            } else {
                name = name + "_t" + String.format(java.util.Locale.ROOT, "%06x", tint & 0xFFFFFF);
                png = baked;
            }
        }
        String packTexture = "textures/prism/blocks/" + name;
        files.put(packTexture + ".png", png);
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


    private static Integer syntheticId(Input input, String lookId) {
        var id = input.synthetic().idOf(lookId);
        return id.isPresent() ? id.getAsInt() : null;
    }

    /** One registration record: the texture names are entries of the terrain atlas this build wrote. */
    private static SyntheticBlock synthetic(Appearances.Look look, int javaId, String geometry,
            Map<String, String> atlasNames, Collision collision, List<BedrockShapes.Box> outline, int rotationX,
            int rotationY, BedrockHardness.Hardness hardness) {
        Map<String, SyntheticBlock.Material> materials = new LinkedHashMap<>();
        atlasNames.forEach((face, texture) -> materials.put(face, new SyntheticBlock.Material(texture, "alpha_test")));
        return new SyntheticBlock(look.id(), javaId, SyntheticBlock.nameOf(look.id(), javaId),
                look.block().toString(), geometry, materials, BedrockShapes.collision(collision), outline, rotationX,
                rotationY, hardness.destroyTime(), hardness.canBreakWithHand());
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

    /**
     * Adds a Bedrock custom item for {@code item} drawn as {@code model}'s texture, mapped from the Java
     * {@code item_model} {@code look}; false (with a warning) if there is no texture.
     */
    private static boolean icon(Map<String, byte[]> sources, Map<String, byte[]> files,
            Map<String, Object> textureData, Map<String, List<Object>> byBase, List<String> warnings,
            ItemDefinition item, PrismKey look, PrismKey model) {
        String texturePath = textureFor(sources, model);
        byte[] png = sources.get(texturePath);
        if (png == null) {
            warnings.add("bedrock " + look + ": no texture " + texturePath
                    + "; it keeps the vanilla Bedrock look of " + item.base());
            return false;
        }
        String name = look.value().replace('/', '_');
        String icon = look.namespace() + "_" + name;
        String packTexture = "textures/prism/" + look.namespace() + "/" + name;
        // prism:tint: layer 0 of a generated item model is tint index 0, so its colour goes into the icon.
        List<Integer> tints = look.equals(item.key()) ? JavaPackBuilder.tints(item.components(), "") : List.of();
        if (!tints.isEmpty()) {
            byte[] baked = TintBake.multiply(png, tints.getFirst());
            if (baked == null) {
                warnings.add("bedrock " + look + ": cannot read " + texturePath + " to bake its prism:tint");
            } else {
                png = baked;
            }
        }
        files.put(packTexture + ".png", png);
        textureData.put(icon, Map.of("textures", packTexture));

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("icon", icon);
        options.put("allow_offhand", true);
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("type", "definition");
        definition.put("model", look.toString());
        definition.put("bedrock_identifier", look.namespace() + ":" + name);
        item.components().get(PrismComponents.DISPLAY_NAME)
                .ifPresent(display -> definition.put("display_name", plain(display.miniMessage())));
        definition.put("bedrock_options", options);
        byBase.computeIfAbsent(item.base().toString(), k -> new ArrayList<>()).add(definition);
        return true;
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
     * The boxes of a mod's Java block model ({@code elements} from and to, rotations ignored, inherited through the mods'
     * own parent models), or null when the mod ships no model or the chain reaches a cross, so the block is a cross.
     */
    static List<ConnectedShapes.Box> modelBoxes(Map<String, byte[]> sources, PrismKey model) {
        PrismKey current = model;
        boolean shipped = false;
        // Follow the parent chain through the mods' own models (a vanilla parent is not in the sources).
        for (int depth = 0; depth < 8; depth++) {
            byte[] json = sources.get("assets/" + current.namespace() + "/models/" + current.value() + ".json");
            if (json == null) {
                break;
            }
            shipped = true;
            Map<?, ?> root;
            try {
                Object parsed = YamlFiles.parse(current.toString(), new String(json, StandardCharsets.UTF_8));
                if (!(parsed instanceof Map<?, ?> map)) {
                    return null;
                }
                root = map;
            } catch (RuntimeException | com.aitchn.prism.api.content.ContentException e) {
                return null;
            }
            List<ConnectedShapes.Box> boxes = new ArrayList<>();
            if (root.get("elements") instanceof List<?> elements) {
                for (Object element : elements) {
                    if (element instanceof Map<?, ?> e && e.get("from") instanceof List<?> from
                            && e.get("to") instanceof List<?> to && from.size() == 3 && to.size() == 3) {
                        boxes.add(new ConnectedShapes.Box(number(from.get(0)), number(from.get(1)),
                                number(from.get(2)), number(to.get(0)), number(to.get(1)), number(to.get(2))));
                    }
                }
            }
            if (!boxes.isEmpty()) {
                return boxes;
            }
            String parent = root.get("parent") == null ? null : String.valueOf(root.get("parent"));
            if (parent == null) {
                break;
            }
            if (parent.contains("cross")) {
                return null;
            }
            try {
                current = parent.contains(":") ? PrismKey.parse(parent) : new PrismKey("minecraft", parent);
            } catch (IllegalArgumentException e) {
                break;
            }
        }
        // No model shipped: Prism generates a cross. A model without elements or a cross parent: a full block.
        return shipped ? List.of(new ConnectedShapes.Box(0, 0, 0, 16, 16, 16)) : null;
    }

    private static double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0;
    }

    /**
     * Bedrock material instances for a block model: {@code *} plus per-face keys, each mapped to a texture file
     * that exists in the mods' assets. Reads the model's own {@code textures} (not its parent's): {@code all}, {@code cross},
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
            fallback = resolve.apply("cross");
        }
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
