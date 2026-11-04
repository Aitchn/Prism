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

    private PrismComponents() {}

    public record Model(PrismKey model) {}

    public record PlacesBlock(PrismKey block) {}

    public record DisplayName(String miniMessage) {}

    public record MaxStackSize(int value) {}
}
