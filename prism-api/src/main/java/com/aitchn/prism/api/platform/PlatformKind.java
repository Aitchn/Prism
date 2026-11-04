package com.aitchn.prism.api.platform;

/** The server software family Prism detected at startup. */
public enum PlatformKind {
    /** Paper, or a Paper fork without a dedicated Prism code path. */
    PAPER,
    /** Purpur: runs the Paper implementation, with optional Purpur-only features enabled. */
    PURPUR,
    /** Folia: regionized multithreading. World access must go through region or entity schedulers. */
    FOLIA;

    /** Whether world state is split across multiple threads (regions). */
    public boolean isRegionized() {
        return this == FOLIA;
    }
}
