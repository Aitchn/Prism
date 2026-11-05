package com.aitchn.prism.validation.mekanism.logic;

import java.util.ArrayList;
import java.util.List;

/** The machines and the five-step ore chain (as in Mekanism, reduced to osmium and iron). Numbers are reduced. */
public final class Catalog {

    public static final String NS = "mekanism:";

    private Catalog() {}

    public static final long TICK_ENERGY = 50;

    /** Basic tier: ore to 2 dust (enrichment), dust smelting is vanilla, ingot to dust (crusher). */
    public static MachineType enrichmentChamber() {
        List<Recipe> r = new ArrayList<>();
        for (String ore : List.of("osmium", "tin", "lead")) {
            r.add(Recipe.item(NS + ore + "_ore", 1, NS + ore + "_dust", 2, 200, TICK_ENERGY));
        }
        r.add(Recipe.item("minecraft:iron_ore", 1, NS + "iron_dust", 2, 200, TICK_ENERGY));
        return new MachineType(NS + "enrichment_chamber", r, 20_000, 2_000, 0);
    }

    public static MachineType crusher() {
        List<Recipe> r = new ArrayList<>();
        r.add(Recipe.item(NS + "osmium_ingot", 1, NS + "osmium_dust", 1, 200, TICK_ENERGY));
        r.add(Recipe.item("minecraft:iron_ingot", 1, NS + "iron_dust", 1, 200, TICK_ENERGY));
        r.add(Recipe.item("minecraft:cobblestone", 1, "minecraft:gravel", 1, 200, TICK_ENERGY));
        return new MachineType(NS + "crusher", r, 20_000, 2_000, 0);
    }

    /** 3x step: ore + oxygen to 3 clumps. */
    public static MachineType purificationChamber() {
        return new MachineType(NS + "purification_chamber",
                List.of(Recipe.itemWithChemical(NS + "osmium_ore", 1, Chemical.OXYGEN, 1, NS + "osmium_clump", 3, 300,
                        TICK_ENERGY)),
                20_000, 2_000, 2_000);
    }

    /** 4x step: ore + hydrogen chloride to 4 shards. */
    public static MachineType chemicalInjectionChamber() {
        return new MachineType(NS + "chemical_injection_chamber",
                List.of(Recipe.itemWithChemical(NS + "osmium_ore", 1, Chemical.HYDROGEN_CHLORIDE, 1, NS + "osmium_shard",
                        4, 300, TICK_ENERGY)),
                20_000, 2_000, 2_000);
    }

    /** Clump to dirty dust, dirty dust to dust. */
    public static MachineType crusher2() {
        return new MachineType(NS + "crusher_chain",
                List.of(Recipe.item(NS + "osmium_clump", 1, NS + "osmium_dirty_dust", 1, 200, TICK_ENERGY),
                        Recipe.item(NS + "osmium_shard", 1, NS + "osmium_clump", 1, 200, TICK_ENERGY)),
                20_000, 2_000, 0);
    }

    /** Water into hydrogen and oxygen. */
    public static MachineType electrolyticSeparator() {
        return new MachineType(NS + "electrolytic_separator",
                List.of(Recipe.split(Chemical.WATER, 2, Chemical.HYDROGEN, 2, Chemical.OXYGEN, 1, 1, 400)),
                100_000, 10_000, 10_000);
    }
}
