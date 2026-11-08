package com.aitchn.prism.core.look;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Which entities a display look needs at a position, given what surrounds it. This is the lighting trick of the display
 * layer: a client lights a flat-shaded face of a block from the cell the face looks into, so each face of the model is
 * drawn by an entity placed in that neighbouring cell and moved back onto the block.
 */
public final class DisplayParts {

    private static final int[] PREFERENCE = {Dir.UP, Dir.NORTH, Dir.EAST, Dir.SOUTH, Dir.WEST, Dir.DOWN};

    private DisplayParts() {}

    /**
     * @param look     the look
     * @param occluded for each {@link Dir} of the world, whether the neighbour there hides the faces looking at it
     * @param light    for each {@link Dir}, the light level of that neighbour (0 to 15); only used to anchor a model that
     *                 is not split
     * @return the entities, ordered by slot; empty if every neighbour hides the block
     */
    public static @NotNull List<DisplayLook.Part> compute(@NotNull DisplayLook look, boolean @NotNull [] occluded,
            int @NotNull [] light) {
        List<DisplayLook.Part> parts = new ArrayList<>();
        float[] rotation = Dir.quaternion(look.rotX(), look.rotY());
        DisplayPlan plan = look.plan();
        if (plan.isSplit()) {
            for (int face = 0; face < 6; face++) {
                String model = plan.faces().get(face);
                if (model.isEmpty()) {
                    continue;
                }
                int toward = Dir.rotate(face, look.rotX(), look.rotY());
                if (!occluded[toward]) {
                    parts.add(part(face, model, toward, rotation));
                }
            }
            return parts;
        }
        if (plan.whole().isEmpty()) {
            return parts;
        }
        int best = -1;
        for (int dir : PREFERENCE) {
            if (!occluded[dir] && (best < 0 || light[dir] > light[best])) {
                best = dir;
            }
        }
        if (best >= 0) {
            parts.add(part(DisplayLook.WHOLE, plan.whole(), best, rotation));
        }
        return parts;
    }

    private static DisplayLook.Part part(int slot, String model, int toward, float[] rotation) {
        int[] step = Dir.offset(toward);
        return new DisplayLook.Part(slot, model, step[0], step[1], step[2],
                new float[] {-step[0], -step[1], -step[2]}, rotation.clone());
    }
}
