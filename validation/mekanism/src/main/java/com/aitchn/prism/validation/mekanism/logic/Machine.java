package com.aitchn.prism.validation.mekanism.logic;

import java.util.Optional;

/**
 * State and tick logic of one machine block: input/output slots, energy, three tanks, progress. Mekanism machines
 * are all variations of this shape (recipe machine with energy, optional gas in/out).
 */
public final class Machine {

    private final MachineType type;
    private final EnergyBuffer energy;
    private final ChemicalTank inTank;
    private final ChemicalTank outTank;
    private final ChemicalTank outTank2;
    private Stack input;
    private Stack output;
    private Recipe active;
    private int progress;

    public Machine(MachineType type) {
        this.type = type;
        this.energy = new EnergyBuffer(type.energyCapacity(), type.maxReceive(), 0);
        this.inTank = new ChemicalTank(type.tankCapacity());
        this.outTank = new ChemicalTank(type.tankCapacity());
        this.outTank2 = new ChemicalTank(type.tankCapacity());
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

    public Stack input() {
        return input;
    }

    public Stack output() {
        return output;
    }

    public int progress() {
        return progress;
    }

    public boolean running() {
        return active != null;
    }

    /** Offers items; returns what did not fit (same stack when rejected). */
    public Stack insertInput(Stack stack) {
        if (input != null && !input.id().equals(stack.id())) {
            return stack;
        }
        if (input == null && type.recipes().stream().noneMatch(r -> r.itemIn() != null
                && r.itemIn().id().equals(stack.id()))) {
            return stack;
        }
        int have = input == null ? 0 : input.count();
        int accepted = Math.min(stack.count(), Stack.MAX - have);
        if (accepted > 0) {
            input = new Stack(stack.id(), have + accepted);
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
        if (active == null) {
            active = findStartable();
            if (active == null) {
                return false;
            }
            startOperation(active);
        }
        if (!energy.consume(active.energyPerTick())) {
            return false;
        }
        progress++;
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
            if (energy.stored() < r.energyPerTick()) {
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
}
