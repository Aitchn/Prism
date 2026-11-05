package com.aitchn.prism.validation.mekanism.logic;

import java.util.List;
import java.util.Optional;

/**
 * State and tick logic of one machine block: input/output slots, energy, three tanks, progress. Mekanism machines
 * are all variations of this shape (recipe machine with energy, optional gas in/out). Not thread-safe: the owner
 * serialises access (the adapter holds one lock around every call).
 */
public final class Machine {

    /** Ticks without work before a machine counts as idle (stops flickering between looks). */
    public static final int IDLE_AFTER = 20;

    private final MachineType type;
    private final EnergyBuffer energy;
    private final ChemicalTank inTank;
    private final ChemicalTank outTank;
    private final ChemicalTank outTank2;
    private final ChemicalPorts ports;
    private Stack input;
    private Stack extra;
    private int speedUpgrades;
    private int energyUpgrades;
    private Stack output;
    private Recipe active;
    private int progress;
    private int idleTicks = IDLE_AFTER;
    private long ticks;
    private boolean dead;

    public Machine(MachineType type) {
        this.type = type;
        this.energy = new EnergyBuffer(type.energyCapacity(), type.maxReceive(), type.maxExtract());
        this.inTank = new ChemicalTank(type.tankCapacity(), c -> type.inputChemicals().contains(c));
        this.outTank = new ChemicalTank(type.tankCapacity());
        this.outTank2 = new ChemicalTank(type.tankCapacity());
        this.ports = new ChemicalPorts(List.of(inTank), List.of(outTank, outTank2));
    }

    public MachineType type() {
        return type;
    }

    public EnergyBuffer energy() {
        return energy;
    }

    public ChemicalTank inTank() {
        return inTank;
    }

    public ChemicalTank outTank() {
        return outTank;
    }

    public ChemicalTank outTank2() {
        return outTank2;
    }

    /** The tanks and the sides pipes may use. */
    public ChemicalPorts ports() {
        return ports;
    }

    public Stack input() {
        return input;
    }

    /** The second input slot (combiner, metallurgic infuser); empty for machines without one. */
    public Stack extra() {
        return extra;
    }

    public void setExtra(Stack stack) {
        extra = stack == null || stack.isEmpty() ? null : stack;
    }

    public static final int MAX_UPGRADES = 8;

    public int speedUpgrades() {
        return speedUpgrades;
    }

    public int energyUpgrades() {
        return energyUpgrades;
    }

    /** Progress added per tick: 1 without upgrades, one more for every speed upgrade. */
    public int speedFactor() {
        return 1 + speedUpgrades;
    }

    /** Installs upgrades; returns how many were taken (the rest does not fit). */
    public int addUpgrades(boolean speed, int count) {
        int have = speed ? speedUpgrades : energyUpgrades;
        int taken = Math.max(0, Math.min(count, MAX_UPGRADES - have));
        if (speed) {
            speedUpgrades += taken;
        } else {
            energyUpgrades += taken;
        }
        return taken;
    }

    /** Energy one tick of {@code r} costs with the installed upgrades: speed multiplies, energy upgrades save 8% each. */
    long tickCost(Recipe r) {
        return r.energyPerTick() * speedFactor() * (100 - 8L * energyUpgrades) / 100;
    }

    public Stack output() {
        return output;
    }

    /** Replaces the input slot; null empties it. */
    public void setInput(Stack stack) {
        input = stack == null || stack.isEmpty() ? null : stack;
    }

    /** Replaces the output slot; null empties it. */
    public void setOutput(Stack stack) {
        output = stack == null || stack.isEmpty() ? null : stack;
    }

    public int progress() {
        return progress;
    }

    /** Progress of the running operation, 0 to 1; 0 when idle. */
    public double progressFraction() {
        return active == null ? 0 : Math.min(1.0, progress / (double) active.ticks());
    }

    public boolean running() {
        return active != null;
    }

    /** Ticks since the last tick that did work; below {@link #IDLE_AFTER} the machine counts as working. */
    public int idleTicks() {
        return idleTicks;
    }

    public long ticks() {
        return ticks;
    }

    /** Set when the block was removed; open menus must stop using the machine. */
    public boolean isDead() {
        return dead;
    }

    public void markDead() {
        dead = true;
    }

    /** Offers items; returns what did not fit (same stack when rejected). */
    public Stack insertInput(Stack stack) {
        if (input != null && !input.id().equals(stack.id())) {
            return stack;
        }
        if (input == null && !type.acceptsItem(stack.id())) {
            return stack;
        }
        int have = input == null ? 0 : input.count();
        int accepted = Math.min(stack.count(), Stack.MAX - have);
        if (accepted > 0) {
            input = new Stack(stack.id(), have + accepted);
        }
        return stack.withCount(stack.count() - accepted);
    }

    /** Offers items to the second input slot; returns what did not fit. */
    public Stack insertExtra(Stack stack) {
        if (extra != null && !extra.id().equals(stack.id())) {
            return stack;
        }
        if (extra == null && !type.acceptsExtra(stack.id())) {
            return stack;
        }
        int have = extra == null ? 0 : extra.count();
        int accepted = Math.min(stack.count(), Stack.MAX - have);
        if (accepted > 0) {
            extra = new Stack(stack.id(), have + accepted);
        }
        return stack.withCount(stack.count() - accepted);
    }

    /** Removes and returns the whole output slot (empty Optional when nothing is there). */
    public Optional<Stack> takeOutput() {
        Stack out = output;
        output = null;
        return Optional.ofNullable(out);
    }

    /** Advances one tick. Returns true if work was done (progress made or an operation finished). */
    public boolean tick() {
        ticks++;
        boolean worked = work();
        idleTicks = worked ? 0 : Math.min(idleTicks + 1, IDLE_AFTER);
        return worked;
    }

    private boolean work() {
        if (active == null) {
            active = findStartable();
            if (active == null) {
                return false;
            }
            startOperation(active);
        }
        if (!energy.consume(tickCost(active))) {
            return false;
        }
        progress += speedFactor();
        if (progress >= active.ticks()) {
            finishOperation(active);
            active = null;
            progress = 0;
        }
        return true;
    }

    private Recipe findStartable() {
        for (Recipe r : type.recipes()) {
            if (r.itemIn() != null && (input == null || !input.id().equals(r.itemIn().id())
                    || input.count() < r.itemIn().count())) {
                continue;
            }
            if (r.itemIn2() != null && (extra == null || !extra.id().equals(r.itemIn2().id())
                    || extra.count() < r.itemIn2().count())) {
                continue;
            }
            if (r.chemicalIn() != null && inTank.drain(r.chemicalIn(), r.chemicalInAmount(), true)
                    < r.chemicalInAmount()) {
                continue;
            }
            if (r.itemOut() != null && !canFitOutput(r.itemOut())) {
                continue;
            }
            if (r.chemicalOut() != null && outTank.fill(r.chemicalOut(), r.chemicalOutAmount(), true)
                    < r.chemicalOutAmount()) {
                continue;
            }
            if (r.chemicalOut2() != null && outTank2.fill(r.chemicalOut2(), r.chemicalOut2Amount(), true)
                    < r.chemicalOut2Amount()) {
                continue;
            }
            if (energy.stored() < tickCost(r)) {
                continue;
            }
            return r;
        }
        return null;
    }

    private boolean canFitOutput(Stack out) {
        return output == null || (output.id().equals(out.id()) && output.count() + out.count() <= Stack.MAX);
    }

    private void startOperation(Recipe r) {
        if (r.itemIn() != null) {
            int left = input.count() - r.itemIn().count();
            input = left == 0 ? null : input.withCount(left);
        }
        if (r.itemIn2() != null) {
            int left = extra.count() - r.itemIn2().count();
            extra = left == 0 ? null : extra.withCount(left);
        }
        if (r.chemicalIn() != null) {
            inTank.drain(r.chemicalIn(), r.chemicalInAmount(), false);
        }
    }

    private void finishOperation(Recipe r) {
        if (r.itemOut() != null) {
            int have = output == null ? 0 : output.count();
            output = new Stack(r.itemOut().id(), have + r.itemOut().count());
        }
        if (r.chemicalOut() != null) {
            outTank.fill(r.chemicalOut(), r.chemicalOutAmount(), false);
        }
        if (r.chemicalOut2() != null) {
            outTank2.fill(r.chemicalOut2(), r.chemicalOut2Amount(), false);
        }
    }

    // ---------------------------------------------------------------- persistence

    /**
     * The machine's state as one line for per-block data: {@code e=<energy>|p=<progress>|r=<recipe index>|i=<id>,<n>|
     * o=<id>,<n>|a=<chemical>,<mB>|b=..|c=..}. Empty parts are left out. A running operation is stored by the index of
     * its recipe in the type's list; if the recipes change before the next load it is dropped.
     */
    public String save() {
        StringBuilder out = new StringBuilder("e=").append(energy.stored());
        if (active != null) {
            out.append("|p=").append(progress).append("|r=").append(type.recipes().indexOf(active));
        }
        if (speedUpgrades > 0 || energyUpgrades > 0) {
            out.append("|u=").append(speedUpgrades).append(',').append(energyUpgrades);
        }
        if (input != null) {
            out.append("|i=").append(input.id()).append(',').append(input.count());
        }
        if (extra != null) {
            out.append("|x=").append(extra.id()).append(',').append(extra.count());
        }
        if (output != null) {
            out.append("|o=").append(output.id()).append(',').append(output.count());
        }
        appendTank(out, 'a', inTank);
        appendTank(out, 'b', outTank);
        appendTank(out, 'c', outTank2);
        return out.toString();
    }

    /**
     * What a machine keeps when it is picked up: energy, upgrades and tank contents, but not the item slots (those drop
     * as items) and not a running operation. {@link #load} reads it like a full state.
     */
    public String saveSettings() {
        StringBuilder out = new StringBuilder("e=").append(energy.stored());
        if (speedUpgrades > 0 || energyUpgrades > 0) {
            out.append("|u=").append(speedUpgrades).append(',').append(energyUpgrades);
        }
        appendTank(out, 'a', inTank);
        appendTank(out, 'b', outTank);
        appendTank(out, 'c', outTank2);
        return out.toString();
    }

    private static void appendTank(StringBuilder out, char key, ChemicalTank tank) {
        if (tank.type() != null && tank.amount() > 0) {
            out.append('|').append(key).append('=').append(tank.type().id()).append(',').append(tank.amount());
        }
    }

    /** Restores a state written by {@link #save}; unknown or damaged parts are skipped. */
    public void load(String text) {
        int recipeIndex = -1;
        int savedProgress = 0;
        for (String part : text.split("\\|")) {
            int eq = part.indexOf('=');
            if (eq != 1) {
                continue;
            }
            String value = part.substring(2);
            try {
                switch (part.charAt(0)) {
                    case 'e' -> energy.set(Long.parseLong(value));
                    case 'p' -> savedProgress = Integer.parseInt(value);
                    case 'r' -> recipeIndex = Integer.parseInt(value);
                    case 'i' -> setInput(stack(value));
                    case 'x' -> setExtra(stack(value));
                    case 'u' -> {
                        String[] parts = value.split(",");
                        speedUpgrades = Math.max(0, Math.min(MAX_UPGRADES, Integer.parseInt(parts[0])));
                        energyUpgrades = Math.max(0, Math.min(MAX_UPGRADES, Integer.parseInt(parts[1])));
                    }
                    case 'o' -> setOutput(stack(value));
                    case 'a' -> loadTank(inTank, value);
                    case 'b' -> loadTank(outTank, value);
                    case 'c' -> loadTank(outTank2, value);
                    default -> { }
                }
            } catch (RuntimeException e) {
                // damaged part: skip it, keep the rest
            }
        }
        List<Recipe> recipes = type.recipes();
        if (recipeIndex >= 0 && recipeIndex < recipes.size() && savedProgress > 0) {
            active = recipes.get(recipeIndex);
            progress = Math.min(savedProgress, active.ticks() - 1);
        }
    }

    private static Stack stack(String value) {
        int comma = value.lastIndexOf(',');
        return new Stack(value.substring(0, comma), Integer.parseInt(value.substring(comma + 1)));
    }

    private static void loadTank(ChemicalTank tank, String value) {
        int comma = value.lastIndexOf(',');
        tank.set(new Chemical(value.substring(0, comma)), Long.parseLong(value.substring(comma + 1)));
    }
}
