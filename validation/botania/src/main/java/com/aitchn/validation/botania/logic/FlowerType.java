package com.aitchn.validation.botania.logic;

/** The flowers this validation implements. */
public enum FlowerType {
    /** Generating: mana in daylight. */
    DAYBLOOM("daybloom"),
    /** Generating: burns dropped fuel items. */
    ENDOFLAME("endoflame"),
    /** Functional: slowly turns surrounding blocks into living blocks. */
    PURE_DAISY("pure_daisy");

    private final String id;

    FlowerType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static FlowerType byId(String id) {
        for (FlowerType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        throw new IllegalArgumentException("unknown flower '" + id + "'");
    }
}
