package com.aitchn.prism.validation.mekanism;

import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import com.aitchn.prism.api.recipe.Ingredient;
import com.aitchn.prism.api.recipe.ItemResult;
import com.aitchn.prism.validation.mekanism.logic.Chemical;
import com.aitchn.prism.validation.mekanism.logic.Recipe;
import com.aitchn.prism.validation.mekanism.logic.Stack;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The mod's own recipe type {@code mekanism:machine}: one operation of one machine. Prism loads and lists these (they
 * are data in {@code prism/recipes/}); what they mean is this mod's business, and other mods can add recipes for
 * these machines from their own jars.
 *
 * <pre>
 * type: mekanism:machine
 * machine: enrichment_chamber     # the id given in the machine block's behavior
 * input2: minecraft:cobblestone    # optional second input item (combiner, metallurgic infuser)
 * input: mekanism:osmium_ore      # optional; {item, count} form works too (no tags)
 * output: {item: mekanism:osmium_dust, count: 2}
 * chemical_in: {chemical: oxygen, amount: 1}                  # optional, consumed when the operation starts
 * chemical_out: [{chemical: hydrogen, amount: 2}]             # optional, at most two
 * ticks: 200
 * energy: 50              # joules per tick
 * </pre>
 */
public record MachineRecipe(@NotNull String machine, @Nullable Ingredient input, @Nullable Ingredient input2, @Nullable ItemResult output,
        @Nullable ChemicalAmount chemicalIn, @NotNull List<ChemicalAmount> chemicalOut, int ticks, long energyPerTick) {

    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");

    /** A chemical and a number of millibuckets. */
    public record ChemicalAmount(@NotNull Chemical chemical, long amount) {}

    static MachineRecipe decode(ConfigSection section, String namespace) throws ContentException {
        section.rejectUnknownKeys(Set.of("machine", "input", "input2", "output", "chemical_in", "chemical_out", "ticks", "energy"));
        String machine = section.requireString("machine");
        if (!ID.matcher(machine).matches()) {
            throw section.error("machine must be a lower-case id, got '" + machine + "'");
        }
        Ingredient input = section.has("input") ? Ingredient.read(section, "input", namespace) : null;
        if (input != null && (input.isTag() || input.count() > Stack.MAX)) {
            throw section.error("input must be one item (no tag), at most " + Stack.MAX);
        }
        Ingredient input2 = section.has("input2") ? Ingredient.read(section, "input2", namespace) : null;
        if (input2 != null && (input2.isTag() || input2.count() > Stack.MAX || input == null)) {
            throw section.error("input2 must be one item (no tag), at most " + Stack.MAX + ", and needs an input");
        }
        ItemResult output = section.has("output") ? ItemResult.read(section, "output", namespace) : null;
        if (output != null && output.count() > Stack.MAX) {
            throw section.error("output count must be at most " + Stack.MAX);
        }
        ChemicalAmount chemicalIn = section.has("chemical_in") ? chemical(section.requireSection("chemical_in")) : null;
        List<ChemicalAmount> chemicalOut = new ArrayList<>();
        for (ConfigSection entry : section.getSectionList("chemical_out")) {
            chemicalOut.add(chemical(entry));
        }
        if (chemicalOut.size() > 2) {
            throw section.error("chemical_out takes at most two entries");
        }
        if (input == null && chemicalIn == null) {
            throw section.error("a recipe needs an input or a chemical_in");
        }
        if (output == null && chemicalOut.isEmpty()) {
            throw section.error("a recipe needs an output or a chemical_out");
        }
        int ticks = section.getInt("ticks").orElse(200);
        int energy = section.getInt("energy").orElse(50);
        if (ticks < 1 || energy < 0) {
            throw section.error("ticks must be at least 1 and energy must not be negative");
        }
        return new MachineRecipe(machine, input, input2, output, chemicalIn, chemicalOut, ticks, energy);
    }

    private static ChemicalAmount chemical(ConfigSection section) throws ContentException {
        section.rejectUnknownKeys(Set.of("chemical", "amount"));
        String id = section.requireString("chemical");
        if (!ID.matcher(id).matches()) {
            throw section.error("chemical must be a lower-case id, got '" + id + "'");
        }
        int amount = section.requireInt("amount");
        if (amount < 1) {
            throw section.error("amount must be at least 1");
        }
        return new ChemicalAmount(new Chemical(id), amount);
    }

    /** The Bukkit-free form the machine logic runs. */
    Recipe toLogic() {
        Stack in = input == null ? null : new Stack(input.item().toString(), input.count());
        Stack in2 = input2 == null ? null : new Stack(input2.item().toString(), input2.count());
        Stack out = output == null ? null : new Stack(output.item().toString(), output.count());
        ChemicalAmount out1 = chemicalOut.size() > 0 ? chemicalOut.get(0) : null;
        ChemicalAmount out2 = chemicalOut.size() > 1 ? chemicalOut.get(1) : null;
        return new Recipe(in, out, chemicalIn == null ? null : chemicalIn.chemical(),
                chemicalIn == null ? 0 : chemicalIn.amount(), out1 == null ? null : out1.chemical(),
                out1 == null ? 0 : out1.amount(), out2 == null ? null : out2.chemical(),
                out2 == null ? 0 : out2.amount(), ticks, energyPerTick, in2);
    }
}
