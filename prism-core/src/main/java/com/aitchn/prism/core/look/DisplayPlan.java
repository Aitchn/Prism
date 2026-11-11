package com.aitchn.prism.core.look;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * How the display layer draws the model of one look, decided when the pack is built.
 *
 * @param faces item model ids (as {@code ns:path}) of the faces of a model that is a plain cube-like model, indexed by
 *              {@link Dir} of the model before rotation, {@code ""} for a direction with no face; empty if the model is
 *              not split (always, unless the server lights display looks by face)
 * @param whole item model id of the whole model, drawn by one entity: what {@link DisplayLighting#BLOCK} uses, and
 *              what {@link DisplayLighting#FACES} uses for a model that cannot be split (inner faces, rotated elements,
 *              a parent Prism does not know); {@code ""} if the model has nothing to draw
 */
public record DisplayPlan(@NotNull List<String> faces, @NotNull String whole) {

    public DisplayPlan {
        faces = List.copyOf(faces);
        if (!faces.isEmpty() && faces.size() != 6) {
            throw new IllegalArgumentException("a split plan has six face entries, got " + faces.size());
        }
    }

    public static DisplayPlan split(List<String> faces) {
        return new DisplayPlan(faces, "");
    }

    /** A split plan that also knows the whole model. */
    public static DisplayPlan split(List<String> faces, String whole) {
        return new DisplayPlan(faces, whole);
    }

    public static DisplayPlan whole(String model) {
        return new DisplayPlan(List.of(), model);
    }

    public boolean isSplit() {
        return !faces.isEmpty();
    }
}
