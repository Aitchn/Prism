package com.aitchn.prism.validation.mekanism.logic;

import java.util.List;

/** Small machine types for logic tests; the real recipes are YAML files (see MekanismContentTest). */
final class TestMachines {

    static final long TICK_ENERGY = 50;

    private TestMachines() {}

    static MachineType enrichment() {
        return MachineType.fixed("enrichment_chamber", List.of(
                Recipe.item("mekanism:osmium_ore", 1, "mekanism:osmium_dust", 2, 200, TICK_ENERGY),
                Recipe.item("minecraft:raw_iron", 1, "mekanism:iron_dust", 2, 200, TICK_ENERGY)), 20_000, 2_000, 0);
    }

    static MachineType crusher() {
        return MachineType.fixed("crusher", List.of(
                Recipe.item("mekanism:osmium_ingot", 1, "mekanism:osmium_dust", 1, 200, TICK_ENERGY),
                Recipe.item("minecraft:cobblestone", 1, "minecraft:gravel", 1, 200, TICK_ENERGY)), 20_000, 2_000, 0);
    }

    static MachineType purification() {
        return MachineType.fixed("purification_chamber", List.of(
                Recipe.itemWithChemical("mekanism:osmium_ore", 1, Chemical.OXYGEN, 1, "mekanism:osmium_clump", 3, 300,
                        TICK_ENERGY)), 20_000, 2_000, 2_000);
    }

    static MachineType separator() {
        return MachineType.fixed("electrolytic_separator", List.of(
                Recipe.split(Chemical.WATER, 2, Chemical.HYDROGEN, 2, Chemical.OXYGEN, 1, 1, 400)),
                100_000, 10_000, 10_000);
    }
}
