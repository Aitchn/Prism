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
    private final ChemicalTank inTank2;
    private final ChemicalTank outTank;
    private final ChemicalTank outTank2;
    private final ChemicalPorts ports;
    private Stack input;
    private Stack extra;
    private final Side[] faces = new Side[6];
    private int speedUpgrades;
    private int energyUpgrades;
    private Stack output;
    private Recipe active;
    private int progress;
    /** How many copies of the recipe the running operation does at once (factories: up to their lanes). */
    private int batch = 1;
    private int idleTicks = IDLE_AFTER;
    private long ticks;
    private boolean dead;
    private volatile boolean menuOpen;
    private String attachment;
    private boolean autoEject;
    private Redstone redstone = Redstone.IGNORE;
    private boolean powered;
    private final Access[] itemFaces = new Access[6];

    public Machine(MachineType type) {
        this.type = type;
        this.energy = new EnergyBuffer(type.energyCapacity(), type.maxReceive(), type.maxExtract());
        java.util.Arrays.fill(faces, Side.ALL);
        java.util.Arrays.fill(itemFaces, Access.ALL);
        if (type.buffer() != null) {
            // A storage tank: one tank that takes what the filter allows and gives it all back out.
            this.inTank = new ChemicalTank(type.tankCapacity(), type.buffer());
            this.inTank2 = new ChemicalTank(0);
            this.outTank = inTank;
            this.outTank2 = new ChemicalTank(0);
            this.ports = new ChemicalPorts(List.of(inTank), List.of(inTank));
        } else {
            this.inTank = new ChemicalTank(type.tankCapacity(), c -> type.inputChemicals().contains(c));
            this.inTank2 = new ChemicalTank(type.tankCapacity(), c -> type.inputChemicals2().contains(c));
            this.outTank = new ChemicalTank(type.tankCapacity());
            this.outTank2 = new ChemicalTank(type.tankCapacity());
            this.ports = new ChemicalPorts(List.of(inTank, inTank2), List.of(outTank, outTank2));
        }
    }

    /** While a player's menu shows the output slot, transporters leave it alone so both views stay in step. */
    public void setMenuOpen(boolean open) {
        menuOpen = open;
    }

    /** A short text kept with the machine for a part that lives beside it (a miner's scan); no '|' or '='. */
    public String attachment() {
        return attachment;
    }

    public void setAttachment(String text) {
        attachment = text;
    }

    /** Adds items to the output slot (not while a menu shows it); returns what did not fit. */
    public Stack insertOutput(Stack stack) {
        if (menuOpen || (output != null && !output.id().equals(stack.id()))) {
            return stack;
        }
        int have = output == null ? 0 : output.count();
        int accepted = Math.min(stack.count(), Stack.MAX - have);
        if (accepted > 0) {
            output = new Stack(stack.id(), have + accepted);
        }
        return stack.withCount(stack.count() - accepted);
    }

    public boolean autoEject() {
        return autoEject;
    }

    public void setAutoEject(boolean value) {
        autoEject = value;
    }

    public Redstone redstone() {
        return redstone;
    }

    public void setRedstone(Redstone mode) {
        redstone = mode;
    }

    /** The adapter reports whether the block currently has a redstone signal. */
    public void setPowered(boolean value) {
        powered = value;
    }

    public Access itemFace(int face) {
        return itemFaces[face];
    }

    public void setItemFace(int face, Access access) {
        itemFaces[face] = access;
    }

    /** What an item transporter touching {@code face} may do with this machine, or null for nothing. */
    public ItemPort itemPort(int face) {
        Access access = itemFaces[face];
        if (access == Access.OFF) {
            return null;
        }
        return new ItemPort() {
            @Override
            public Stack offer() {
                return autoEject && !menuOpen && access.canExtract() && output != null ? output : null;
            }

            @Override
            public void taken(int count) {
                if (output != null) {
                    output = output.count() > count ? output.withCount(output.count() - count) : null;
                }
            }

            @Override
            public Stack insert(Stack stack) {
                if (!access.canInsert()) {
                    return stack;
                }
                Stack rest = insertInput(stack);
                return rest.isEmpty() ? rest : insertExtra(rest);
            }
        };
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

    /** The second chemical input (washer water, infuser's second gas); never accepts anything on machines without one. */
    public ChemicalTank inTank2() {
        return inTank2;
    }

    /** The input tank that takes {@code chemical}, or null (a bucket of water goes where water is accepted). */
    public ChemicalTank tankFor(Chemical chemical) {
        if (type.tankCapacity() <= 0) {
            return null;
        }
        if (inTank.fill(chemical, 1, true) > 0) {
            return inTank;
        }
        return inTank2.fill(chemical, 1, true) > 0 ? inTank2 : null;
    }

    public Side face(int face) {
        return faces[face];
    }

    public void setFace(int face, Side mode) {
        faces[face] = mode;
    }

    /** Tanks pipes may fill through {@code face}. */
    public List<ChemicalTank> takeTanks(int face) {
        return switch (faces[face]) {
            case ALL -> ports.inputs();
            case IN1 -> List.of(inTank);
            case IN2 -> type.buffer() != null ? List.of() : List.of(inTank2);
            default -> List.of();
        };
    }

    /** Tanks pipes may drain through {@code face}. */
    public List<ChemicalTank> giveTanks(int face) {
        return switch (faces[face]) {
            case ALL -> ports.outputs();
            case OUT1 -> List.of(outTank);
            case OUT2 -> type.buffer() != null ? List.of() : List.of(outTank2);
            default -> List.of();
        };
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
        boolean worked = redstone.allows(powered) && work();
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
        if (!energy.consume(tickCost(active) * batch)) {
            return false;
        }
        progress += speedFactor();
        if (progress >= active.ticks()) {
            finishOperation(active);
            active = null;
            progress = 0;
            batch = 1;
        }
        return true;
    }

    private Recipe findStartable() {
        for (Recipe r : type.recipes()) {
            for (int k = type.lanes(); k >= 1; k--) {
                if (canStart(r, k)) {
                    batch = k;
                    return r;
                }
            }
        }
        return null;
    }

    /** Whether {@code k} copies of {@code r} can start now: inputs, room for outputs and energy for one tick. */
    private boolean canStart(Recipe r, int k) {
        if (r.itemIn() != null && (input == null || !input.id().equals(r.itemIn().id())
                || input.count() < r.itemIn().count() * k)) {
            return false;
        }
        if (r.itemIn2() != null && (extra == null || !extra.id().equals(r.itemIn2().id())
                || extra.count() < r.itemIn2().count() * k)) {
            return false;
        }
        if (r.chemicalIn() != null && inTank.drain(r.chemicalIn(), r.chemicalInAmount() * k, true)
                < r.chemicalInAmount() * k) {
            return false;
        }
        if (r.itemOut() != null && !canFitOutput(r.itemOut(), k)) {
            return false;
        }
        if (r.chemicalIn2() != null && inTank2.drain(r.chemicalIn2(), r.chemicalIn2Amount() * k, true)
                < r.chemicalIn2Amount() * k) {
            return false;
        }
        if (r.chemicalOut() != null && outTank.fill(r.chemicalOut(), r.chemicalOutAmount() * k, true)
                < r.chemicalOutAmount() * k) {
            return false;
        }
        if (r.chemicalOut2() != null && outTank2.fill(r.chemicalOut2(), r.chemicalOut2Amount() * k, true)
                < r.chemicalOut2Amount() * k) {
            return false;
        }
        return energy.stored() >= tickCost(r) * k;
    }

    private boolean canFitOutput(Stack out, int k) {
        int have = output == null ? 0 : output.count();
        return (output == null || output.id().equals(out.id())) && have + out.count() * k <= Stack.MAX;
    }

    private void startOperation(Recipe r) {
        if (r.itemIn() != null) {
            int left = input.count() - r.itemIn().count() * batch;
            input = left == 0 ? null : input.withCount(left);
        }
        if (r.itemIn2() != null) {
            int left = extra.count() - r.itemIn2().count() * batch;
            extra = left == 0 ? null : extra.withCount(left);
        }
        if (r.chemicalIn() != null) {
            inTank.drain(r.chemicalIn(), r.chemicalInAmount() * batch, false);
        }
        if (r.chemicalIn2() != null) {
            inTank2.drain(r.chemicalIn2(), r.chemicalIn2Amount() * batch, false);
        }
    }

    private void finishOperation(Recipe r) {
        if (r.itemOut() != null) {
            int have = output == null ? 0 : output.count();
            output = new Stack(r.itemOut().id(), have + r.itemOut().count() * batch);
        }
        if (r.chemicalOut() != null) {
            outTank.fill(r.chemicalOut(), r.chemicalOutAmount() * batch, false);
        }
        if (r.chemicalOut2() != null) {
            outTank2.fill(r.chemicalOut2(), r.chemicalOut2Amount() * batch, false);
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
            if (batch > 1) {
                out.append("|k=").append(batch);
            }
        }
        if (speedUpgrades > 0 || energyUpgrades > 0) {
            out.append("|u=").append(speedUpgrades).append(',').append(energyUpgrades);
        }
        appendFaces(out);
        appendTransport(out);
        if (attachment != null) {
            out.append("|t=").append(attachment);
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
        appendTank(out, 'd', inTank2);
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
        appendFaces(out);
        appendTransport(out);
        if (attachment != null) {
            out.append("|t=").append(attachment);
        }
        appendTank(out, 'a', inTank);
        appendTank(out, 'b', outTank);
        appendTank(out, 'c', outTank2);
        appendTank(out, 'd', inTank2);
        return out.toString();
    }

    private void appendTransport(StringBuilder out) {
        StringBuilder digits = new StringBuilder();
        boolean any = autoEject || redstone != Redstone.IGNORE;
        for (Access access : itemFaces) {
            digits.append(access.ordinal());
            any |= access != Access.ALL;
        }
        if (any) {
            out.append("|g=").append(autoEject ? 1 : 0).append(',').append(redstone.ordinal()).append(',')
                    .append(digits);
        }
    }

    private void appendFaces(StringBuilder out) {
        boolean any = false;
        StringBuilder digits = new StringBuilder();
        for (Side face : faces) {
            digits.append(face.ordinal());
            any |= face != Side.ALL;
        }
        if (any) {
            out.append("|f=").append(digits);
        }
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
        int savedBatch = 1;
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
                    case 'k' -> savedBatch = Integer.parseInt(value);
                    case 't' -> attachment = value;
                    case 'i' -> setInput(stack(value));
                    case 'x' -> setExtra(stack(value));
                    case 'f' -> {
                        for (int i = 0; i < 6 && i < value.length(); i++) {
                            faces[i] = Side.values()[Character.digit(value.charAt(i), 10)];
                        }
                    }
                    case 'g' -> {
                        String[] parts = value.split(",");
                        autoEject = parts[0].equals("1");
                        redstone = Redstone.values()[Integer.parseInt(parts[1])];
                        for (int i = 0; i < 6 && i < parts[2].length(); i++) {
                            itemFaces[i] = Access.values()[Character.digit(parts[2].charAt(i), 10)];
                        }
                    }
                    case 'u' -> {
                        String[] parts = value.split(",");
                        speedUpgrades = Math.max(0, Math.min(MAX_UPGRADES, Integer.parseInt(parts[0])));
                        energyUpgrades = Math.max(0, Math.min(MAX_UPGRADES, Integer.parseInt(parts[1])));
                    }
                    case 'o' -> setOutput(stack(value));
                    case 'a' -> loadTank(inTank, value);
                    case 'b' -> loadTank(outTank, value);
                    case 'c' -> loadTank(outTank2, value);
                    case 'd' -> loadTank(inTank2, value);
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
            batch = Math.max(1, Math.min(type.lanes(), savedBatch));
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
