package com.aitchn.prism.api.content;

/**
 * Marker for game logic bound to a definition from YAML. A behavior implements one or more capability interfaces
 * such as {@link com.aitchn.prism.api.item.ItemUseBehavior}; Prism dispatches by capability and never by id.
 * Instances are shared by every item stack of the definition, so they must be immutable or thread safe.
 */
public interface Behavior {}
