package com.aitchn.prism.core.menu;

/**
 * Decides what happens to one click in a container menu, from the slot's role and the kind of click. Pure logic, so
 * every combination is unit tested; the plugin maps Bukkit's inventory actions onto {@link Action}.
 */
public final class SlotRules {

    /** What a top-inventory slot is for. Mirrors {@code com.aitchn.prism.api.menu.SlotRole}. */
    public enum Role {
        /** Shows something; clicks go to the menu's handler, items never move. */
        BUTTON,
        /** Players put items in (if the menu accepts them) and take them out. */
        INPUT,
        /** Players only take items out. */
        OUTPUT
    }

    /** A click, reduced to what matters for the rules. */
    public enum Action {
        /** Take some or all of the slot's stack onto the cursor. */
        TAKE,
        /** Put some or all of the cursor's stack into the slot. */
        PUT,
        /** Swap the cursor with the slot. */
        SWAP_CURSOR,
        /** Swap the slot with a hotbar or offhand slot (number keys, F). */
        HOTBAR_SWAP,
        /** Shift-click from the menu into the player's inventory. */
        SHIFT_OUT,
        /** Shift-click from the player's inventory into the menu. */
        SHIFT_IN,
        /** Drop the slot's stack (Q). */
        DROP,
        /** Double-click: gather matching items onto the cursor from everywhere. */
        COLLECT,
        /** Creative middle click copy. */
        CLONE,
        /** Anything else, or a click in the player's own inventory that cannot affect the menu. */
        OTHER
    }

    public enum Outcome {
        /** Let the server do it. */
        ALLOW,
        /** Cancel it. */
        DENY,
        /** Cancel it and let Prism move the items into the menu's input slots itself. */
        INSERT
    }

    private SlotRules() {}

    /**
     * @param role      role of the clicked top slot, or null when the click is in the player's own inventory
     * @param accepts   whether the menu accepts the item that would enter the slot (cursor, hotbar item)
     * @param incomingEmpty whether nothing would enter the slot (empty cursor or empty hotbar slot)
     */
    public static Outcome decide(Role role, Action action, boolean accepts, boolean incomingEmpty) {
        if (role == null) {
            return switch (action) {
                case SHIFT_IN -> Outcome.INSERT;
                // A double-click in the player's inventory may also pull items out of the menu.
                case COLLECT -> Outcome.DENY;
                default -> Outcome.ALLOW;
            };
        }
        return switch (role) {
            case BUTTON -> Outcome.DENY;
            case INPUT -> switch (action) {
                case TAKE, SHIFT_OUT, DROP, CLONE -> Outcome.ALLOW;
                case PUT, SWAP_CURSOR -> accepts ? Outcome.ALLOW : Outcome.DENY;
                case HOTBAR_SWAP -> incomingEmpty || accepts ? Outcome.ALLOW : Outcome.DENY;
                default -> Outcome.DENY;
            };
            case OUTPUT -> switch (action) {
                case TAKE, SHIFT_OUT, DROP, CLONE -> Outcome.ALLOW;
                case HOTBAR_SWAP -> incomingEmpty ? Outcome.ALLOW : Outcome.DENY;
                default -> Outcome.DENY;
            };
        };
    }

    /** A drag across slots is allowed only if every top slot it touches is an input that accepts the item. */
    public static boolean allowDrag(Iterable<Role> touchedTopRoles, boolean accepts) {
        for (Role role : touchedTopRoles) {
            if (role != Role.INPUT || !accepts) {
                return false;
            }
        }
        return true;
    }
}
