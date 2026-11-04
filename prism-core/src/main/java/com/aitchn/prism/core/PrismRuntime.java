package com.aitchn.prism.core;

import com.aitchn.prism.api.platform.PlatformInfo;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * The platform-neutral runtime. It receives everything platform specific (info and scheduler) from the bootstrapping
 * plugin and never looks at the concrete platform itself.
 */
public final class PrismRuntime {

    private final PlatformInfo platform;
    private final PlatformScheduler scheduler;

    public PrismRuntime(@NotNull PlatformInfo platform, @NotNull PlatformScheduler scheduler) {
        this.platform = Objects.requireNonNull(platform, "platform");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public @NotNull PlatformInfo platform() {
        return platform;
    }

    public @NotNull PlatformScheduler scheduler() {
        return scheduler;
    }
}
