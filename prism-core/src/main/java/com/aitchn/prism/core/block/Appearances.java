package com.aitchn.prism.core.block;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/**
 * The looks of a custom block: one per variant and facing. A look has a stable text id used to allocate native
 * block states and to name pack files: {@code ns:value}, then {@code #variant} if the block has variants, then
 * {@code @facing} unless it faces north.
 */
public final class Appearances {

    public static final List<String> HORIZONTAL = List.of("NORTH", "EAST", "SOUTH", "WEST");
    public static final List<String> ALL = List.of("NORTH", "EAST", "SOUTH", "WEST", "UP", "DOWN");

    /** One look. {@code facing} is a {@code BlockFace} name. */
    public record Look(@NotNull PrismKey block, @NotNull String variant, @NotNull String facing) {
        public @NotNull String id() {
            return block + (variant.isEmpty() ? "" : "#" + variant)
                    + ("NORTH".equals(facing) ? "" : "@" + facing.toLowerCase(Locale.ROOT));
        }
    }

    private Appearances() {}

    /** The variant names of {@code block}; a single {@code ""} when it has none. The first is the default. */
    public static @NotNull List<String> variants(@NotNull BlockDefinition block) {
        return block.components().get(PrismComponents.VARIANTS).map(PrismComponents.Variants::names)
                .orElse(List.of(""));
    }

    /** The facings a block can have. */
    public static @NotNull List<String> facings(@NotNull BlockDefinition block) {
        return switch (block.components().get(PrismComponents.ROTATION).orElse(PrismComponents.Rotation.NONE)) {
            case NONE -> List.of("NORTH");
            case HORIZONTAL -> HORIZONTAL;
            case ALL -> ALL;
        };
    }

    /** Every look of {@code block}, variants outer, facings inner. */
    public static @NotNull List<Look> looks(@NotNull BlockDefinition block) {
        List<Look> looks = new ArrayList<>();
        for (String variant : variants(block)) {
            for (String facing : facings(block)) {
                looks.add(new Look(block.key(), variant, facing));
            }
        }
        return looks;
    }

    /** The model a variant is drawn with: {@code prism:model} or {@code ns:block/value}, or {@code ..._variant}. */
    public static @NotNull PrismKey model(@NotNull BlockDefinition block, @NotNull String variant) {
        if (variant.isEmpty()) {
            return block.components().get(PrismComponents.MODEL).map(PrismComponents.Model::model)
                    .orElseGet(() -> new PrismKey(block.key().namespace(), "block/" + block.key().value()));
        }
        return new PrismKey(block.key().namespace(), "block/" + block.key().value() + "_" + variant);
    }

    /** The item model a display entity shows for a variant: {@code ns:block/value} or {@code ns:block/value_variant}. */
    public static @NotNull PrismKey itemModel(@NotNull PrismKey block, @NotNull String variant) {
        return new PrismKey(block.namespace(), "block/" + block.value() + (variant.isEmpty() ? "" : "_" + variant));
    }

    /** Blockstate rotation {x, y} in degrees for a model whose front faces north. */
    public static int @NotNull [] rotation(@NotNull String facing) {
        return switch (facing) {
            case "EAST" -> new int[] {0, 90};
            case "SOUTH" -> new int[] {0, 180};
            case "WEST" -> new int[] {0, 270};
            case "UP" -> new int[] {270, 0};
            case "DOWN" -> new int[] {90, 0};
            default -> new int[] {0, 0};
        };
    }
}
