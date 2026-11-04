package com.aitchn.prism.api.client;

/**
 * Something a client may or may not be able to show. Mods query these instead of checking the edition, so a
 * behavior degrades by what is missing rather than by who is playing.
 */
public enum ClientCapability {
    /** Custom item appearance (a flat icon on Bedrock, a full model on Java). */
    CUSTOM_ITEM_APPEARANCE,
    /** Item appearance that changes with item state without a new item. Java only. */
    ITEM_DYNAMIC_STATE,
    /** Display entities, used for custom block appearance. */
    DISPLAY_ENTITIES,
    /** Animated multi-part models from BetterModel. Java only. */
    MODEL_ENTITIES,
    /** Chest-style container menus. */
    CONTAINER_MENUS,
    /** Form based menus (simple, modal and custom forms). Bedrock only. */
    FORM_MENUS,
    /** Custom fonts and glyph based GUI tricks. Java only. */
    CUSTOM_FONTS
}
