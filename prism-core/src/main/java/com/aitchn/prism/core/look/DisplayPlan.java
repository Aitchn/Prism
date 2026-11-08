package com.aitchn.prism.core.look;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * How the display layer draws the model of one look, decided when the pack is built.
 *
 * @param faces item model ids (as {@code ns:path}) of the faces of a model that is a plain cube-like model, indexed by
 *              {@link Dir} of the model before rotation, {@code ""} for a direction with no face; empty if the model is
 *              not split
 * @param whole item model id of a model that cannot be split by face (inner faces, rotated elements, a parent Prism
 *              does not know), drawn by one entity; {@code ""} if the model has nothing to draw
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

    public static DisplayPlan whole(String model) {
        return new DisplayPlan(List.of(), model);
    }

    public boolean isSplit() {
        return !faces.isEmpty();
    }
}
