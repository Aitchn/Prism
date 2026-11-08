package com.aitchn.prism.core.look;

import org.jetbrains.annotations.NotNull;

/**
 * A look drawn by the display layer: its {@link DisplayPlan} and the blockstate rotation of its facing.
 *
 * @param id       index in the {@link DisplayTable}, carried in {@code BlockLook#display}
 * @param lookId   {@code Appearances.Look#id()}
 * @param rotX     blockstate rotation about x in degrees
 * @param rotY     blockstate rotation about y in degrees
 * @param occludes whether the block hides the faces of its neighbours (neighbours skip display faces toward it)
 */
public record DisplayLook(int id, @NotNull String lookId, @NotNull DisplayPlan plan, int rotX, int rotY,
        boolean occludes) {

    /** Slot of the single entity of a model that is not split; face entities use their {@link Dir} index. */
    public static final int WHOLE = 6;

    /**
     * One entity of a block position. Its model is drawn in the cell {@code (dx, dy, dz)} away from the block (so the
     * client lights it from that cell) and moved back by {@code translation}, turned by {@code rotation}.
     *
     * @param slot        which of the block's entities this is: a {@link Dir} index, or {@link #WHOLE}
     * @param model       item model id
     * @param translation {@code (x, y, z)} for the display's transformation
     * @param rotation    {@code (x, y, z, w)} for the display's left rotation
     */
    public record Part(int slot, @NotNull String model, int dx, int dy, int dz, float @NotNull [] translation,
            float @NotNull [] rotation) {}
}
