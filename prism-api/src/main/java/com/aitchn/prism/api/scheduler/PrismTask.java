package com.aitchn.prism.api.scheduler;

/** A repeating or delayed task from {@link PrismScheduler}. */
public interface PrismTask {

    /** Stops future runs. A run in progress is not interrupted. */
    void cancel();

    boolean isCancelled();
}
