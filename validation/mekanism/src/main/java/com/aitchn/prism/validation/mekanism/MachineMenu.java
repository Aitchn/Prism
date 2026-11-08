package com.aitchn.prism.validation.mekanism;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.menu.ContainerMenu;
import com.aitchn.prism.api.menu.MenuView;
import com.aitchn.prism.api.menu.SlotRole;
import com.aitchn.prism.validation.mekanism.logic.Chemical;
import com.aitchn.prism.validation.mekanism.logic.ChemicalTank;
import com.aitchn.prism.validation.mekanism.logic.Machine;
import com.aitchn.prism.validation.mekanism.logic.Pos;
import com.aitchn.prism.validation.mekanism.logic.Slots;
import com.aitchn.prism.validation.mekanism.logic.Stack;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * A machine's GUI: real input and output slots, an energy meter, a progress arrow and the chemical tanks, refreshed
 * while open. The items belong to the {@link Machine}; the slots only show them. Every callback runs on the viewer's
 * thread and touches the machine under the grid's lock, never the world.
 *
 * <p>The menu reports a click a tick late, while the machine keeps working, so slot contents are not simply copied
 * back: {@link Slots} applies only what the player changed, and a slot is redrawn only when it still shows what the
 * menu last drew (otherwise a click is waiting to be reported and redrawing would undo it).
 */
final class MachineMenu implements ContainerMenu {

    static final int ENERGY = 4;
    static final int INPUT = 11;
    static final int EXTRA = 12;
    static final int PROGRESS = 13;
    private static final PrismKey ENERGY_GAUGE = PrismKey.parse("mekanism:energy_gauge");
    private static final PrismKey PROGRESS_GAUGE = PrismKey.parse("mekanism:progress_gauge");
    private static final PrismKey CHEMICAL_GAUGE = PrismKey.parse("mekanism:chemical_gauge");
    static final int OUTPUT = 15;
    static final int HINT = 22;
    static final int TANK_IN = 20;
    static final int TANK_IN_2 = 19;
    static final int TANK_OUT_1 = 24;
    static final int TANK_OUT_2 = 25;
    private static final List<Integer> USED = List.of(ENERGY, INPUT, EXTRA, PROGRESS, OUTPUT, HINT, TANK_IN, TANK_IN_2, TANK_OUT_1,
            TANK_OUT_2);

    private final Grid grid;
    private final Pos pos;
    private final Machine machine;
    private final String id;
    private final long created = System.currentTimeMillis();
    private volatile MenuView view;
    private Stack shownInput;
    private Stack shownExtra;
    private Stack shownOutput;

    MachineMenu(Grid grid, Pos pos, Machine machine, String id) {
        this.grid = grid;
        this.pos = pos;
        this.machine = machine;
        this.id = id;
    }

    /** The menu is gone: transporters may take from the output slot again. */
    void releaseMachine() {
        grid.locked(() -> {
            machine.setMenuOpen(false);
            return null;
        });
    }

    /** Whether someone has this menu open (or is about to). */
    boolean active() {
        MenuView current = view;
        return current != null ? current.isOpen() : System.currentTimeMillis() - created < 5_000;
    }

    @Override
    public @NotNull String title() {
        return "<dark_gray>" + Text.title(id);
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public @NotNull SlotRole role(int slot) {
        if (slot == INPUT && grid.locked(() -> machine.type().takesItems())) {
            return SlotRole.INPUT;
        }
        if (slot == EXTRA && grid.locked(() -> machine.type().takesExtra())) {
            return SlotRole.INPUT;
        }
        if (slot == OUTPUT && grid.locked(() -> machine.type().makesItems())) {
            return SlotRole.OUTPUT;
        }
        return SlotRole.BUTTON;
    }

    @Override
    public boolean accepts(int slot, @NotNull ItemStack stack) {
        if (slot == INPUT) {
            return grid.locked(() -> machine.type().acceptsItem(Grid.itemId(stack)));
        }
        return slot == EXTRA && grid.locked(() -> machine.type().acceptsExtra(Grid.itemId(stack)));
    }

    @Override
    public void onOpen(@NotNull MenuView view) {
        this.view = view;
        for (int slot = 0; slot < view.size(); slot++) {
            if (!USED.contains(slot) || (slot == EXTRA && !grid.locked(() -> machine.type().takesExtra()))
                    || (slot == TANK_IN_2 && !grid.locked(() -> !machine.type().inputChemicals2().isEmpty()))) {
                view.setIcon(slot, PrismKey.parse("minecraft:black_stained_glass_pane"), "<dark_gray> ", List.of());
            }
        }
        grid.underLock(() -> {
            drawStatus(view);
            forceItems(view);
        });
    }

    @Override
    public int refreshTicks() {
        return 2;
    }

    @Override
    public void onRefresh(@NotNull MenuView view) {
        grid.underLock(() -> {
            if (machine.isDead()) {
                view.close(); // onClose hands over what the slots still show
                return;
            }
            drawStatus(view);
            syncItems(view);
        });
    }

    @Override
    public void onSlotChanged(@NotNull MenuView view, int slot) {
        grid.underLock(() -> {
            if (machine.isDead()) {
                return;
            }
            if (slot == INPUT) {
                Stack now = read(view, INPUT);
                machine.setInput(Slots.reconcileInput(shownInput, machine.input(), now));
                shownInput = now;
            } else if (slot == EXTRA) {
                Stack now = read(view, EXTRA);
                machine.setExtra(Slots.reconcileInput(shownExtra, machine.extra(), now));
                shownExtra = now;
            } else if (slot == OUTPUT) {
                Stack now = read(view, OUTPUT);
                machine.setOutput(Slots.reconcileOutput(shownOutput, machine.output(), now));
                shownOutput = now;
            }
        });
    }

    @Override
    public void onClose(@NotNull MenuView view) {
        grid.menuClosed(pos, this);
        if (!grid.locked(machine::isDead)) {
            return;
        }
        // The block was broken while open: what the slots still hold goes to the viewer, once.
        Player player = view.player();
        for (int slot : new int[] {INPUT, EXTRA, OUTPUT}) {
            ItemStack left = view.item(slot);
            if (left != null && left.getType() != Material.AIR) {
                view.setItem(slot, null);
                player.getInventory().addItem(left).values()
                        .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            }
        }
    }

    // ---------------------------------------------------------------- drawing (grid lock held)

    private void drawStatus(MenuView view) {
        long stored = machine.energy().stored();
        long capacity = machine.energy().capacity();
        view.setGauge(ENERGY, ENERGY_GAUGE, capacity == 0 ? 0 : stored / (double) capacity, "<red>Energy",
                List.of("<white>" + Text.number(stored) + " <gray>/ <white>" + Text.number(capacity) + " J",
                        Text.bar(capacity == 0 ? 0 : stored / (double) capacity, 20, "red"),
                        "<dark_gray>Fed by cables from a generator"));
        double progress = machine.progressFraction();
        boolean working = machine.running();
        view.setGauge(PROGRESS, PROGRESS_GAUGE, working ? progress : 0,
                working ? "<yellow>Working " + Math.round(progress * 100) + "%" : "<gray>Idle",
                List.of(Text.bar(progress, 20, "yellow"),
                        working && machine.idleTicks() > 0 ? "<red>Waiting for energy" : "<dark_gray>"));
        if (machine.type().tankCapacity() > 0) {
            boolean storage = machine.type().buffer() != null;
            drawTank(view, TANK_IN, storage ? "Tank" : "Input tank", machine.inTank());
            if (!machine.type().inputChemicals2().isEmpty()) {
                drawTank(view, TANK_IN_2, "Second input tank", machine.inTank2());
            }
            if (!storage) {
                drawTank(view, TANK_OUT_1, "Output tank", machine.outTank());
                drawTank(view, TANK_OUT_2, "Second output tank", machine.outTank2());
            }
        }
        view.setIcon(HINT, PrismKey.parse("minecraft:paper"), "<gray>How it works", hint());
    }

    private List<String> hint() {
        if (machine.type().inputChemicals().contains(Chemical.WATER)) {
            return List.of("<gray>Right click the machine with a", "<gray>water bucket to fill the input tank.",
                    "<gray>Gas leaves through pressurized tubes.");
        }
        if (!machine.type().inputChemicals().isEmpty()) {
            return List.of("<gray>Put the item in the left slot.", "<gray>Gas arrives through pressurized tubes",
                    "<gray>from an electrolytic separator.");
        }
        if (machine.type().takesExtra()) {
            return List.of("<gray>Put the main item in the first slot", "<gray>and the second item (coal, redstone,",
                    "<gray>cobblestone...) next to it.");
        }
        return List.of("<gray>Put the item in the left slot,", "<gray>take the result from the right.");
    }

    private void drawTank(MenuView view, int slot, String label, ChemicalTank tank) {
        Chemical type = tank.type();
        String name = type == null ? "Empty" : Text.title(type.id());
        view.setGauge(slot, CHEMICAL_GAUGE, tank.capacity() == 0 ? 0 : tank.amount() / (double) tank.capacity(),
                "<aqua>" + label,
                List.of("<white>" + name, "<white>" + Text.number(tank.amount()) + " <gray>/ <white>"
                        + Text.number(tank.capacity()) + " mB",
                        Text.bar(tank.capacity() == 0 ? 0 : tank.amount() / (double) tank.capacity(), 20, "aqua")));
    }

    // ---------------------------------------------------------------- item slots (grid lock held)

    private static Stack read(MenuView view, int slot) {
        ItemStack item = view.item(slot);
        return item == null || item.getType() == Material.AIR ? null : new Stack(Grid.itemId(item), item.getAmount());
    }

    private void show(MenuView view, int slot, Stack stack) {
        view.setItem(slot, stack == null ? null : Grid.create(stack).orElse(null));
    }

    /** First draw: the slots show exactly what the machine holds. */
    private void forceItems(MenuView view) {
        show(view, INPUT, machine.input());
        shownInput = read(view, INPUT);
        show(view, EXTRA, machine.extra());
        shownExtra = read(view, EXTRA);
        show(view, OUTPUT, machine.output());
        shownOutput = read(view, OUTPUT);
    }

    /** Redraws a slot only when no click is waiting to be reported. */
    private void syncItems(MenuView view) {
        if (Objects.equals(read(view, INPUT), shownInput) && !Objects.equals(machine.input(), shownInput)) {
            show(view, INPUT, machine.input());
            shownInput = read(view, INPUT);
        }
        if (Objects.equals(read(view, EXTRA), shownExtra) && !Objects.equals(machine.extra(), shownExtra)) {
            show(view, EXTRA, machine.extra());
            shownExtra = read(view, EXTRA);
        }
        if (Objects.equals(read(view, OUTPUT), shownOutput) && !Objects.equals(machine.output(), shownOutput)) {
            show(view, OUTPUT, machine.output());
            shownOutput = read(view, OUTPUT);
        }
    }
}
