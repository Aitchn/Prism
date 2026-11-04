package com.aitchn.prism.platform.spi;

/** Handle to a task scheduled through {@link PlatformScheduler}. */
public interface ScheduledTask {

    /** Cancels future executions. A run that already started is not interrupted. */
    void cancel();

    boolean isCancelled();
}
