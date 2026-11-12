package com.aitchn.prism.api.effect;

/** A travelling visual that was launched. Safe to use from any thread. */
public interface TravelHandle {

    /**
     * Stops the travel now: viewers lose it at once and its arrival callback does not run.
     *
     * @return false if it had already arrived or was cancelled
     */
    boolean cancel();

    /** Whether the travel arrived or was cancelled. */
    boolean done();
}
