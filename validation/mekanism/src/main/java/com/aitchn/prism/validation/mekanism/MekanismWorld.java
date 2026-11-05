package com.aitchn.prism.validation.mekanism;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.BlockContext;
import com.aitchn.prism.api.menu.MenuButton;
import com.aitchn.prism.api.menu.MenuSpec;
import com.aitchn.prism.validation.mekanism.logic.Catalog;
import com.aitchn.prism.validation.mekanism.logic.Machine;
import com.aitchn.prism.validation.mekanism.logic.MachineType;
import com.aitchn.prism.validation.mekanism.logic.Networks;
import com.aitchn.prism.validation.mekanism.logic.Pos;
import com.aitchn.prism.validation.mekanism.logic.SolarGenerator;
import com.aitchn.prism.validation.mekanism.logic.Stack;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Glue between Prism blocks and the Bukkit-free logic. Prism has no "block entity" yet, so this class is the block
 * entity store: positions to machines, plus the ticking (see docs/gaps.md, "Block state" and "Ticking").
 */
final class MekanismWorld {

    private final Plugin plugin;
    private final Map<String, MachineType> types = new HashMap<>();
    private final Map<Pos, Machine> machines = new ConcurrentHashMap<>();
    private final Map<Pos, SolarGenerator> solars = new ConcurrentHashMap<>();
    private final Map<Pos, ScheduledTask> tasks = new ConcurrentHashMap<>();
    private final Set<Pos> cables = ConcurrentHashMap.newKeySet();
    private final Set<Pos> pipes = ConcurrentHashMap.newKeySet();
    /** One coarse lock: machines tick on their own regions while networks move energy across regions. */
    private final ReentrantLock lock = new ReentrantLock();
    private ScheduledTask networkTask;

    MekanismWorld(Plugin plugin) {
        this.plugin = plugin;
        for (MachineType t : new MachineType[] {Catalog.enrichmentChamber(), Catalog.crusher(),
                Catalog.purificationChamber(), Catalog.chemicalInjectionChamber(), Catalog.electrolyticSeparator()}) {
            types.put(t.id().substring(Catalog.NS.length()), t);
        }
    }

    void start() {
        // Networks run on the global region; they only touch buffers, never world state, under the shared lock.
        networkTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> tickNetworks(), 1, 1);
    }

    void stop() {
        if (networkTask != null) {
            networkTask.cancel();
        }
        tasks.values().forEach(ScheduledTask::cancel);
        tasks.clear();
    }

    static Pos pos(Block block) {
        return new Pos(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    /** Registers a freshly placed or first-seen block. Must run on the region thread of the block. */
    void register(MekanismBlock behavior, Block block) {
        Pos pos = pos(block);
        switch (behavior.kind()) {
            case MACHINE -> machines.computeIfAbsent(pos, p -> {
                Machine machine = new Machine(types.get(behavior.machine()));
                schedule(p, block.getLocation(), machine::tick);
                return machine;
            });
            case SOLAR -> solars.computeIfAbsent(pos, p -> {
                SolarGenerator solar = new SolarGenerator(() -> {
                    long time = block.getWorld().getTime();
                    return time < 12_000 && block.getLightFromSky() >= 15;
                });
                schedule(p, block.getLocation(), solar::tick);
                return solar;
            });
            case CABLE -> cables.add(pos);
            case PIPE -> pipes.add(pos);
            case CASING -> { }
        }
    }

    private void schedule(Pos pos, Location location, Runnable tick) {
        tasks.put(pos, Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, t -> {
            lock.lock();
            try {
                tick.run();
            } finally {
                lock.unlock();
            }
        }, 1, 1));
    }

    void broken(MekanismBlock behavior, Block block) {
        Pos pos = pos(block);
        machines.remove(pos);
        solars.remove(pos);
        cables.remove(pos);
        pipes.remove(pos);
        ScheduledTask task = tasks.remove(pos);
        if (task != null) {
            task.cancel();
        }
    }

    private void tickNetworks() {
        lock.lock();
        try {
            Networks.distributeEnergy(cables,
                    p -> solars.containsKey(p) ? solars.get(p).energy() : null,
                    p -> machines.containsKey(p) ? machines.get(p).energy() : null, 4_096);
            Networks.distributeChemical(pipes,
                    p -> machines.containsKey(p) ? machines.get(p).outTank() : null,
                    p -> machines.containsKey(p) ? machines.get(p).inTank() : null, 256);
        } finally {
            lock.unlock();
        }
    }

    /** Right click: machines open their menu; other blocks only get registered. */
    void interact(MekanismBlock behavior, BlockContext context) {
        register(behavior, context.block());
        Player player = context.player();
        if (behavior.kind() != MekanismBlock.Kind.MACHINE || player == null) {
            return;
        }
        context.cancel();
        Machine machine = machines.get(pos(context.block()));
        if (machine != null) {
            openMenu(player, machine, behavior.machine());
        }
    }

    private void openMenu(Player player, Machine machine, String name) {
        lock.lock();
        MenuSpec spec;
        try {
            spec = new MenuSpec("<dark_gray>" + name.replace('_', ' '), java.util.List.of(
                    new MenuButton("status", "Energy " + machine.energy().stored() + "/" + machine.energy().capacity()
                            + (machine.running() ? " (running " + machine.progress() + ")" : ""),
                            PrismKey.parse("minecraft:redstone")),
                    new MenuButton("insert", "Insert held item" + (machine.input() == null ? ""
                            : " (in: " + machine.input().id() + " x" + machine.input().count() + ")"),
                            PrismKey.parse("minecraft:hopper")),
                    new MenuButton("take", "Take output" + (machine.output() == null ? ""
                            : " (" + machine.output().id() + " x" + machine.output().count() + ")"),
                            PrismKey.parse("minecraft:chest"))));
        } finally {
            lock.unlock();
        }
        Prism.menus().open(player, spec, id -> {
            lock.lock();
            try {
                switch (id) {
                    case "insert" -> insertHeld(player, machine);
                    case "take" -> machine.takeOutput().ifPresent(s -> give(player, s));
                    default -> { }
                }
            } finally {
                lock.unlock();
            }
        });
    }

    private void insertHeld(Player player, Machine machine) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() == Material.AIR) {
            return;
        }
        String id = itemId(held);
        Stack left = machine.insertInput(new Stack(id, held.getAmount()));
        held.setAmount(left.count());
    }

    /** Custom items by their Prism key, vanilla by material key. */
    static String itemId(ItemStack stack) {
        return Prism.items().identify(stack).map(PrismKey::toString)
                .orElseGet(() -> "minecraft:" + stack.getType().name().toLowerCase(java.util.Locale.ROOT));
    }

    private void give(Player player, Stack stack) {
        PrismKey key = PrismKey.parse(stack.id());
        ItemStack item = key.namespace().equals("minecraft")
                ? new ItemStack(Material.matchMaterial(key.value()) == null ? Material.STONE
                        : Material.matchMaterial(key.value()), stack.count())
                : Prism.items().create(key, stack.count()).orElse(null);
        if (item != null) {
            player.getInventory().addItem(item).values()
                    .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        }
    }

    Set<Pos> cableView() {
        return new HashSet<>(cables);
    }
}
