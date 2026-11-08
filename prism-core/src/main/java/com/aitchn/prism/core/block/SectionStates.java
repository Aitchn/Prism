package com.aitchn.prism.core.block;

/** Read and write access to the block state ids of one 16x16x16 chunk section, by section-local coordinates. */
public interface SectionStates {

    int get(int x, int y, int z);

    void set(int x, int y, int z, int state);
}
