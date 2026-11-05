package com.aitchn.prism.validation.mekanism.logic;

/** An item stack reduced to what the logic needs: an item key string ("mekanism:osmium_dust") and a count. */
public record Stack(String id, int count) {

    public static final int MAX = 64;

    public Stack {
        if (count < 0) {
            throw new IllegalArgumentException("count < 0");
        }
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public Stack withCount(int newCount) {
        return new Stack(id, newCount);
    }
}
