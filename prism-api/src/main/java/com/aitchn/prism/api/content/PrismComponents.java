package com.aitchn.prism.api.content;

import com.aitchn.prism.api.PrismKey;

/** Components every Prism install understands. Engine-level data only; gameplay content belongs in mods. */
public final class PrismComponents {

    /** Display name of an item; MiniMessage markup is allowed. Scalar form: {@code prism:display_name: "Ruby"}. */
    public static final ComponentType<DisplayName> DISPLAY_NAME = new ComponentType<>(
            new PrismKey(PrismKey.PRISM_NAMESPACE, "display_name"),
            DisplayName.class,
            section -> new DisplayName(section.requireString("value")));

    /** Static lore lines of an item (MiniMessage), shown above any {@code ItemLoreBehavior} lines. */
    public static final ComponentType<Lore> LORE = new ComponentType<>(
            new PrismKey(PrismKey.PRISM_NAMESPACE, "lore"),
            Lore.class,
            section -> new Lore(java.util.List.copyOf(section.getStringList("value"))));

    /** Maximum stack size of an item, 1 to 99. */
    public static final ComponentType<MaxStackSize> MAX_STACK_SIZE = new ComponentType<>(
            new PrismKey(PrismKey.PRISM_NAMESPACE, "max_stack_size"),
            MaxStackSize.class,
            section -> {
                int size = section.requireInt("value");
                if (size < 1 || size > 99) {
                    throw section.error("max_stack_size must be between 1 and 99, got " + size);
                }
                return new MaxStackSize(size);
            });

    /**
     * The model an item or block is drawn with, as a resource-pack model id such as {@code mymod:item/ruby}.
     * Default when absent: {@code <namespace>:item/<key value>} for items and {@code <namespace>:block/<key value>}
     * for blocks. Scalar form: {@code prism:model: "mymod:item/ruby"}.
     */
    public static final ComponentType<Model> MODEL = new ComponentType<>(
            new PrismKey(PrismKey.PRISM_NAMESPACE, "model"),
            Model.class,
            section -> new Model(section.requireKey("value")));

    /** Makes an item place a custom block when used on a block face. Scalar form: {@code prism:places_block: ns:block}. */
    public static final ComponentType<PlacesBlock> PLACES_BLOCK = new ComponentType<>(
            new PrismKey(PrismKey.PRISM_NAMESPACE, "places_block"),
            PlacesBlock.class,
            section -> new PlacesBlock(section.requireKey("value")));

    /**
     * Draws a block with an animated Blockbench model from {@code prism/models/<value>.bbmodel} of the declaring mod
     * instead of a flat item model. Scalar form: {@code prism:model_ref: mymod:turret}.
     */
    public static final ComponentType<ModelRef> MODEL_REF = new ComponentType<>(
            new PrismKey(PrismKey.PRISM_NAMESPACE, "model_ref"),
            ModelRef.class,
            section -> new ModelRef(section.requireKey("value")));

    /**
     * How long a custom block takes to break in survival. {@code hardness} is seconds by hand (default 1.0, 0 breaks
     * instantly, -1 cannot be broken); {@code tool} ({@code pickaxe}, {@code axe}, {@code shovel}, {@code hoe},
     * {@code sword} or {@code none}) is the tool type that breaks it faster, scaled by the tool's material like
     * vanilla. Scalar form: {@code prism:mining: 2.5}. Blocks without this component use hardness 1.0 and no tool.
     */
    public static final ComponentType<Mining> MINING = new ComponentType<>(
            new PrismKey(PrismKey.PRISM_NAMESPACE, "mining"),
            Mining.class,
            section -> {
                section.rejectUnknownKeys(java.util.Set.of("value", "hardness", "tool"));
                double hardness = section.has("value") ? section.requireDouble("value")
                        : section.getDouble("hardness").orElse(Mining.DEFAULT.hardness());
                if (hardness < 0 && hardness != -1) {
                    throw section.error("hardness must be 0 or more, or -1 for unbreakable, got " + hardness);
                }
                String tool = section.getString("tool").orElse("none");
                if (!Mining.TOOLS.contains(tool)) {
                    throw section.error("tool must be one of " + Mining.TOOLS + ", got " + tool);
                }
                return new Mining(hardness, tool);
            });

    /**
     * Lets a block face a direction chosen when a player places it: {@code horizontal} (north, east, south, west,
     * toward the player, like a furnace) or {@code all} (also up and down, like a dispenser). Without it blocks face
     * north. Scalar form: {@code prism:rotation: horizontal}.
     */
    public static final ComponentType<Rotation> ROTATION = new ComponentType<>(
            new PrismKey(PrismKey.PRISM_NAMESPACE, "rotation"),
            Rotation.class,
            section -> {
                String mode = section.requireString("value");
                return switch (mode) {
                    case "horizontal" -> Rotation.HORIZONTAL;
                    case "all" -> Rotation.ALL;
                    case "none" -> Rotation.NONE;
                    default -> throw section.error("rotation must be none, horizontal or all, got " + mode);
                };
            });

    /**
     * Named looks a block can switch between at runtime with {@code BlockService.setVariant}, for example
     * {@code [idle, active]}. The first is the default. Variant {@code v} is drawn with model
     * {@code <namespace>:block/<value>_<v>} (generated from a texture of the same name when there is no model JSON).
     * Without this component a block has one unnamed look. Scalar form: {@code prism:variants: [idle, active]}.
     */
    public static final ComponentType<Variants> VARIANTS = new ComponentType<>(
            new PrismKey(PrismKey.PRISM_NAMESPACE, "variants"),
            Variants.class,
            section -> {
                java.util.List<String> names = section.getStringList("value");
                if (names.isEmpty()) {
                    throw section.error("variants needs at least one name");
                }
                for (String name : names) {
                    if (name.equals("true") || name.equals("false")) {
                        throw section.error("variant name " + name + " came from an unquoted YAML boolean "
                                + "(on, off, yes, no, true, false); quote it or pick another name");
                    }
                    if (!name.matches("[a-z0-9_]+")) {
                        throw section.error("variant names must match [a-z0-9_]+, got " + name);
                    }
                }
                if (names.stream().distinct().count() != names.size()) {
                    throw section.error("variant names must be unique");
                }
                return new Variants(java.util.List.copyOf(names));
            });

    private PrismComponents() {}

    /**
     * Generates this block as ore veins in newly generated chunks. One vein spec, or a list of them:
     * <pre>{@code
     * prism:ore:
     *   dimension: normal        # normal (default), nether or the_end
     *   replace: [minecraft:stone, minecraft:andesite]   # vanilla blocks it may replace; per-dimension stone by default
     *   min_y: -16               # inclusive, clamped to the world
     *   max_y: 48
     *   veins_per_chunk: 6       # 1 to 64
     *   rarity: 1                # a chunk gets veins with chance 1 in rarity (default 1: every chunk)
     *   size: 8                  # blocks per vein, 1 to 32
     * }</pre>
     * Veins stay inside their chunk, so generation never loads a neighbour. Generated blocks get their first variant
     * and face north; place behaviors do not run (load behaviors and ticking start as for any loaded block). Chunks
     * generated before the mod was installed get no veins.
     */
    public static final ComponentType<Ore> ORE = new ComponentType<>(
            new PrismKey(PrismKey.PRISM_NAMESPACE, "ore"),
            Ore.class,
            section -> {
                java.util.List<ConfigSection> specs = section.has("value")
                        ? section.getSectionList("value") : java.util.List.of(section);
                if (specs.isEmpty()) {
                    throw section.error("expected a vein mapping or a list of them");
                }
                java.util.List<Ore.Vein> veins = new java.util.ArrayList<>();
                for (ConfigSection spec : specs) {
                    veins.add(Ore.Vein.decode(spec));
                }
                return new Ore(java.util.List.copyOf(veins));
            });

    /**
     * A sound a block plays by itself while it is loaded, repeated every {@code interval} ticks: a machine's hum, a
     * portal's whoosh. Prism stops it when the block goes, and plays it only in the listed variants (all when none are
     * listed), so switching a machine to idle silences it. Pick a clip about as long as the interval. Custom sounds come
     * from the mod's {@code assets/<ns>/sounds.json}; Prism also puts them in the Bedrock pack.
     * <pre>{@code
     * prism:ambient_sound:
     *   sound: examplemod:block.counter.hum   # a sound event, custom or vanilla
     *   interval: 20                          # ticks, default 20
     *   volume: 0.6                           # default 1
     *   pitch: 1.0                            # default 1
     *   variants: [active]                    # default: every variant
     * }</pre>
     */
    public static final ComponentType<AmbientSound> AMBIENT_SOUND = new ComponentType<>(
            new PrismKey(PrismKey.PRISM_NAMESPACE, "ambient_sound"),
            AmbientSound.class,
            section -> {
                section.rejectUnknownKeys(java.util.Set.of("value", "sound", "interval", "volume", "pitch",
                        "variants"));
                PrismKey sound = section.has("value") ? section.requireKey("value") : section.requireKey("sound");
                int interval = section.getInt("interval").orElse(20);
                double volume = section.getDouble("volume").orElse(1.0);
                double pitch = section.getDouble("pitch").orElse(1.0);
                if (interval < 1) {
                    throw section.error("interval must be at least 1, got " + interval);
                }
                if (volume <= 0 || pitch <= 0) {
                    throw section.error("volume and pitch must be above 0");
                }
                return new AmbientSound(sound, interval, (float) volume, (float) pitch,
                        java.util.List.copyOf(section.getStringList("variants")));
            });

    /**
     * A block whose look follows its neighbours: a cable, pipe or post shows a core and an arm towards every side it
     * connects to. Prism works out the connections when the block or a neighbour is placed or removed, and draws the
     * shape itself from boxes, so a mod writes no model per combination. Java players see it drawn by a display entity
     * over the block's carrier (use {@code base: minecraft:barrier}), so neighbouring blocks never show holes. Bedrock
     * players get one note block state per variant and combination of sides, sent to them per player; list fewer
     * {@code faces} to use fewer of the 799 states (six sides take 64 per variant, four take 16).
     * <pre>{@code
     * prism:connections:
     *   connects_to: [self, examplemod:ruby_counter, minecraft:chest, capability:prism:item_handler]
     *   faces: [north, east, south, west, up, down]   # default: all six
     *   core: 6                                      # core cube edge in pixels, default 6
     *   arm: 4                                       # arm thickness in pixels, default the core size
     *   texture: examplemod:block/ruby_cable         # default ns:block/<name>, or <name>_<variant>
     * }</pre>
     * {@code self} is any block of the same kind; a key is a custom or vanilla block; {@code capability:<key>} is any
     * block offering that built-in capability on the facing side. A {@code BlockConnectionBehavior} replaces these
     * rules with code. Variant names of a connected block must not look like {@code c<number>}.
     */
    public static final ComponentType<Connections> CONNECTIONS = new ComponentType<>(
            new PrismKey(PrismKey.PRISM_NAMESPACE, "connections"),
            Connections.class,
            section -> {
                section.rejectUnknownKeys(java.util.Set.of("connects_to", "faces", "core", "arm", "texture"));
                java.util.List<String> faces = section.has("faces")
                        ? section.getStringList("faces").stream()
                                .map(face -> face.toUpperCase(java.util.Locale.ROOT)).toList()
                        : Connections.SIDES;
                for (String face : faces) {
                    if (!Connections.SIDES.contains(face)) {
                        throw section.error("unknown face " + face.toLowerCase(java.util.Locale.ROOT)
                                + "; use north, east, south, west, up or down");
                    }
                }
                if (faces.isEmpty() || new java.util.HashSet<>(faces).size() != faces.size()) {
                    throw section.error("faces must list each side at most once, and at least one");
                }
                int core = section.getInt("core").orElse(6);
                int arm = section.getInt("arm").orElse(core);
                if (core < 1 || core > 16 || arm < 1 || arm > core) {
                    throw section.error("core must be 1-16 pixels and arm 1-core, got core " + core + " arm " + arm);
                }
                java.util.Optional<PrismKey> texture = section.has("texture")
                        ? java.util.Optional.of(section.requireKey("texture")) : java.util.Optional.empty();
                java.util.List<String> rules = java.util.List.copyOf(section.getStringList("connects_to"));
                for (String rule : rules) {
                    String key = rule.startsWith("capability:") ? rule.substring("capability:".length()) : rule;
                    if (!rule.equals("self")) {
                        try {
                            PrismKey.parse(key);
                        } catch (IllegalArgumentException e) {
                            throw section.error("connects_to entry " + rule
                                    + " is not self, a block key or capability:<key>");
                        }
                    }
                }
                java.util.List<String> ordered = Connections.SIDES.stream().filter(faces::contains).toList();
                return new Connections(ordered, rules, core, arm, texture);
            });

    /**
     * A block players walk through, like a flower, grass or a wire: no collision on either edition, and it does not hide
     * the faces of the blocks around it. Prism draws it with one of 64 tripwire states (instead of a note block state),
     * so it breaks instantly like string, and Bedrock players see it too. Without a model of its own the block's texture
     * is drawn as a cross, like a flower. Each look (variant x facing) takes one of the 64 states. Ignored on blocks with
     * {@code prism:connections} or a BetterModel model.
     * <pre>{@code
     * prism:passable: true
     * }</pre>
     */
    public static final ComponentType<Passable> PASSABLE = new ComponentType<>(
            new PrismKey(PrismKey.PRISM_NAMESPACE, "passable"),
            Passable.class,
            section -> new Passable(section.getBoolean("value", true)));

    /** @see #VARIANTS */
    public record Variants(java.util.List<String> names) {}

    /** @see #ORE */
    public record Ore(java.util.List<Vein> veins) {

        /** One vein spec of {@link #ORE}; {@code replace} holds vanilla block keys. */
        public record Vein(String dimension, java.util.List<PrismKey> replace, int minY, int maxY, int veinsPerChunk,
                int rarity, int size) {

            public static final java.util.Set<String> DIMENSIONS = java.util.Set.of("normal", "nether", "the_end");

            /** The vanilla blocks a vein replaces when {@code replace} is not given. */
            public static java.util.List<PrismKey> defaultReplace(String dimension) {
                return switch (dimension) {
                    case "nether" -> java.util.List.of(PrismKey.parse("minecraft:netherrack"));
                    case "the_end" -> java.util.List.of(PrismKey.parse("minecraft:end_stone"));
                    default -> java.util.List.of(PrismKey.parse("minecraft:stone"), PrismKey.parse("minecraft:granite"),
                            PrismKey.parse("minecraft:diorite"), PrismKey.parse("minecraft:andesite"));
                };
            }

            static Vein decode(ConfigSection spec) throws ContentException {
                spec.rejectUnknownKeys(java.util.Set.of("dimension", "replace", "min_y", "max_y", "veins_per_chunk",
                        "rarity", "size"));
                String dimension = spec.getString("dimension").orElse("normal");
                if (!DIMENSIONS.contains(dimension)) {
                    throw spec.error("dimension must be normal, nether or the_end, got " + dimension);
                }
                java.util.List<PrismKey> replace = new java.util.ArrayList<>();
                for (String key : spec.getStringList("replace")) {
                    PrismKey parsed;
                    try {
                        parsed = PrismKey.parse(key);
                    } catch (IllegalArgumentException e) {
                        throw spec.error("replace: " + e.getMessage());
                    }
                    if (!parsed.namespace().equals("minecraft")) {
                        throw spec.error("replace takes vanilla blocks (minecraft:...), got " + key);
                    }
                    replace.add(parsed);
                }
                int minY = spec.getInt("min_y").orElse(-64);
                int maxY = spec.getInt("max_y").orElse(64);
                if (minY > maxY) {
                    throw spec.error("min_y " + minY + " is above max_y " + maxY);
                }
                int perChunk = spec.getInt("veins_per_chunk").orElse(4);
                int rarity = spec.getInt("rarity").orElse(1);
                int size = spec.getInt("size").orElse(8);
                if (perChunk < 1 || perChunk > 64) {
                    throw spec.error("veins_per_chunk must be 1 to 64, got " + perChunk);
                }
                if (rarity < 1) {
                    throw spec.error("rarity must be at least 1, got " + rarity);
                }
                if (size < 1 || size > 32) {
                    throw spec.error("size must be 1 to 32, got " + size);
                }
                return new Vein(dimension, java.util.List.copyOf(replace.isEmpty() ? defaultReplace(dimension) : replace),
                        minY, maxY, perChunk, rarity, size);
            }
        }
    }

    /** @see #CONNECTIONS */
    public record Connections(java.util.List<String> faces, java.util.List<String> connectsTo, int core, int arm,
            java.util.Optional<PrismKey> texture) {

        /** Every side in mask bit order: bit 0 is north, then east, south, west, up and down. */
        public static final java.util.List<String> SIDES = java.util.List.of("NORTH", "EAST", "SOUTH", "WEST", "UP",
                "DOWN");

        /** The mask bit of {@code face} (a {@code BlockFace} name). */
        public static int bit(String face) {
            return 1 << SIDES.indexOf(face);
        }

        /** Every mask these faces can form, from 0 (no connection) upwards. */
        public java.util.List<Integer> masks() {
            int all = 0;
            for (String face : faces) {
                all |= bit(face);
            }
            java.util.List<Integer> masks = new java.util.ArrayList<>();
            for (int mask = 0; mask <= all; mask++) {
                if ((mask & ~all) == 0) {
                    masks.add(mask);
                }
            }
            return java.util.List.copyOf(masks);
        }
    }

    /** @see #PASSABLE */
    public record Passable(boolean value) {}

    /** @see #AMBIENT_SOUND */
    public record AmbientSound(PrismKey sound, int interval, float volume, float pitch,
            java.util.List<String> variants) {

        /** Whether the sound plays while the block shows {@code variant}. */
        public boolean playsIn(String variant) {
            return variants.isEmpty() || variants.contains(variant);
        }
    }

    /** @see #MINING */
    public record Mining(double hardness, String tool) {
        public static final Mining DEFAULT = new Mining(1.0, "none");
        public static final java.util.Set<String> TOOLS =
                java.util.Set.of("none", "pickaxe", "axe", "shovel", "hoe", "sword");
    }

    /** @see #ROTATION */
    public enum Rotation { NONE, HORIZONTAL, ALL }

    public record ModelRef(PrismKey model) {}

    public record Model(PrismKey model) {}

    public record PlacesBlock(PrismKey block) {}

    public record DisplayName(String miniMessage) {}

    public record MaxStackSize(int value) {}

    public record Lore(java.util.List<String> lines) {}
}
