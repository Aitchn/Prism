package com.aitchn.prism.core.look;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Which entities a display look needs at a position, given what surrounds it.
 *
 * <p>{@link DisplayLighting#BLOCK} (the default, ADR-0025): one entity at the block's centre with the whole model, or
 * none when all six neighbours hide the block (nothing of it can be seen).
 *
 * <p>{@link DisplayLighting#FACES} (ADR-0023): a client lights a flat-shaded face of a block from the cell the face
 * looks into, so each face of the model is drawn by an entity placed in that neighbouring cell and moved back onto the
 * block; faces toward a neighbour that hides them are skipped.
 */
public final class DisplayParts {

    private static final int[] PREFERENCE = {Dir.UP, Dir.NORTH, Dir.EAST, Dir.SOUTH, Dir.WEST, Dir.DOWN};

    private DisplayParts() {}

    /** As {@link #compute(DisplayLook, boolean[], int[], DisplayLighting)} with face lighting (ADR-0023). */
    public static @NotNull List<DisplayLook.Part> compute(@NotNull DisplayLook look, boolean @NotNull [] occluded,
            int @NotNull [] light) {
        return compute(look, occluded, light, DisplayLighting.FACES);
    }

    /** Whether all six neighbours hide the block, so nothing of it can be seen. */
    public static boolean enclosed(boolean @NotNull [] occluded) {
        for (int dir = 0; dir < 6; dir++) {
            if (!occluded[dir]) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param look     the look
     * @param occluded for each {@link Dir} of the world, whether the neighbour there hides the faces looking at it
     * @param light    for each {@link Dir}, the light level of that neighbour (0 to 15); only used to anchor a model that
     *                 is not split
     * @param lighting where the entities go
     * @return the entities, ordered by slot; empty if every neighbour hides the block
     */
    public static @NotNull List<DisplayLook.Part> compute(@NotNull DisplayLook look, boolean @NotNull [] occluded,
            int @NotNull [] light, @NotNull DisplayLighting lighting) {
        List<DisplayLook.Part> parts = new ArrayList<>();
        float[] rotation = Dir.quaternion(look.rotX(), look.rotY());
        DisplayPlan plan = look.plan();
        if (lighting == DisplayLighting.BLOCK && !plan.whole().isEmpty()) {
            if (!enclosed(occluded)) {
                parts.add(new DisplayLook.Part(DisplayLook.WHOLE, plan.whole(), 0, 0, 0, new float[3], rotation));
            }
            return parts;
        }
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
