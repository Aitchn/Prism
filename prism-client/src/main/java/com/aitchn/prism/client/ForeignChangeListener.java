package com.aitchn.prism.client;

import java.util.UUID;

/**
 * Told, from a network thread, that an outgoing packet put something other than the expected carrier at a custom
 * block's position. Implementations must only schedule work; they must not touch world state directly.
 */
@FunctionalInterface
public interface ForeignChangeListener {

    void foreign(UUID world, int x, int y, int z);
}
