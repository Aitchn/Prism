package com.aitchn.prism.validation.mekanism.logic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A kind of machine: where its recipes come from and its buffer sizes. Shared by all machines of that block. The
 * recipes are looked up on every call because they are data that can be reloaded.
 */
public record MachineType(String id, Supplier<List<Recipe>> source, long energyCapacity, long maxReceive,
        long tankCapacity) {

    /** A type with a fixed recipe list (tests). */
    public static MachineType fixed(String id, List<Recipe> recipes, long energyCapacity, long maxReceive,
            long tankCapacity) {
        List<Recipe> copy = List.copyOf(recipes);
        return new MachineType(id, () -> copy, energyCapacity, maxReceive, tankCapacity);
    }

    public List<Recipe> recipes() {
        return source.get();
    }

    /** Whether some recipe consumes the item {@code id}. */
    public boolean acceptsItem(String itemId) {
        for (Recipe r : recipes()) {
            if (r.itemIn() != null && r.itemIn().id().equals(itemId)) {
                return true;
            }
        }
        return false;
    }

    public boolean takesItems() {
        return recipes().stream().anyMatch(r -> r.itemIn() != null);
    }

    public boolean makesItems() {
        return recipes().stream().anyMatch(r -> r.itemOut() != null);
    }

    /** Chemicals some recipe consumes: the only ones the input tank accepts. */
    public Set<Chemical> inputChemicals() {
        Set<Chemical> result = new HashSet<>();
        for (Recipe r : recipes()) {
            if (r.chemicalIn() != null) {
                result.add(r.chemicalIn());
            }
        }
        return result;
    }
}
