package com.aitchn.prism.core.mod;

import com.aitchn.prism.core.content.Problem;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Outcome of activating one mod. A failed mod published nothing. */
public record ModReport(@NotNull String id, @NotNull Status status, @NotNull List<Problem> problems, int itemCount, int blockCount) {

    public enum Status { ACTIVE, FAILED }

    public boolean active() {
        return status == Status.ACTIVE;
    }
}
