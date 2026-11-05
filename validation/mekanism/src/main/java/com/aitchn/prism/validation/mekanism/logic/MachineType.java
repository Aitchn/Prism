package com.aitchn.prism.validation.mekanism.logic;

import java.util.List;

/** A kind of machine: its recipes and buffer sizes. Shared by all machines of that block. */
public record MachineType(String id, List<Recipe> recipes, long energyCapacity, long maxReceive, long tankCapacity) {

    public MachineType {
        recipes = List.copyOf(recipes);
    }
}
