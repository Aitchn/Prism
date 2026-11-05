package com.aitchn.prism.api.content;

import com.aitchn.prism.api.PrismKey;

/** Components every Prism install understands. Engine-level data only; gameplay content belongs in mods. */
public final class PrismComponents {

    /** Display name of an item; MiniMessage markup is allowed. Scalar form: {@code prism:display_name: "Ruby"}. */
    public static final ComponentType<DisplayName> DISPLAY_NAME = new ComponentType<>(
            new PrismKey(PrismKey.PRISM_NAMESPACE, "display_name"),
            DisplayName.class,
            section -> new DisplayName(section.requireString("value")));

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

    private PrismComponents() {}

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
}
