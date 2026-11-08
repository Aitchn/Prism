package com.aitchn.prism.core.look;

import org.jetbrains.annotations.NotNull;

/** A model and blockstate rotation (degrees around x and y, multiples of 90) that draws one block state. */
public record ModelRef(@NotNull String model, int x, int y) {
    public ModelRef(@NotNull String model) {
        this(model, 0, 0);
    }
}
