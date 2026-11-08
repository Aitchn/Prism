package com.aitchn.prism.core.structure;

import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Something a structure controller can find around itself: a fixed pattern ({@code prism:structure}) or a cuboid of
 * variable size ({@code prism:cuboid}). Pure: it sees the world only through a {@link Probe}.
 */
public interface StructureShape {

    /** What is at an offset from the controller. */
    @FunctionalInterface
    interface Probe {
        /**
         * Whether the block at the offset is {@code expect} (a block key, or "air"); null when that block cannot be
         * looked at now (an unloaded chunk, or another thread's region).
         */
        @Nullable Boolean is(int dx, int dy, int dz, @NotNull String expect);
    }

    record Offset(int dx, int dy, int dz) {}

    /**
     * A standing structure.
     *
     * @param facing    a direction name: the way a pattern faces, or the way from a cuboid's controller into the inside
     * @param signature what identifies this exact structure for its controller (facing, and size for a cuboid); saved
     *                  so a structure that is still the same after a chunk load is not announced again
     * @param members   every non-air block of the structure except the controller
     * @param watched   every checked cell, members plus the cells that must stay air
     */
    record Match(@NotNull String facing, @NotNull String signature, @NotNull List<Offset> members,
            @NotNull List<Offset> watched) {}

    /** {@code match} is null when nothing stands; {@code uncertain} then says part of it could not be looked at. */
    record Result(@Nullable Match match, boolean uncertain) {

        static final Result NONE = new Result(null, false);
        static final Result UNKNOWN = new Result(null, true);
    }

    /** The largest distance on any axis from the controller to a cell of the structure. */
    int reach();

    @NotNull Result find(@NotNull Probe probe);

    /** The shape of {@code block}, or null if it controls no structure. */
    static @Nullable StructureShape of(@NotNull BlockDefinition block) {
        var pattern = block.components().get(PrismComponents.STRUCTURE);
        if (pattern.isPresent()) {
            return StructurePattern.compile(pattern.get());
        }
        return block.components().get(PrismComponents.CUBOID).map(CuboidShape::new).orElse(null);
    }
}
