package com.aitchn.prism.core.look;

/**
 * The collision shape a Java client predicts for a look. Custom blocks drawn as client looks are either full cubes or
 * passable; shapes in between are not offered because the server carrier cannot match them.
 */
public enum Collision {
    FULL,
    NONE
}
