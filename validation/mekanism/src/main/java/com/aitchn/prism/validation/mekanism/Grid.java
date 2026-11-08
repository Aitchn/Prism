package com.aitchn.prism.validation.mekanism;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.BlockContext;
import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.api.recipe.ItemResult;
import com.aitchn.prism.validation.mekanism.logic.ChemicalPorts;
import com.aitchn.prism.validation.mekanism.logic.ChemicalTank;
import com.aitchn.prism.validation.mekanism.logic.EnergyBuffer;
import com.aitchn.prism.validation.mekanism.logic.Machine;
import com.aitchn.prism.validation.mekanism.logic.MachineType;
import com.aitchn.prism.validation.mekanism.logic.Miner;
import com.aitchn.prism.validation.mekanism.logic.Networks;
import com.aitchn.prism.validation.mekanism.logic.Plant;
import com.aitchn.prism.validation.mekanism.logic.Pos;
import com.aitchn.prism.validation.mekanism.logic.Side;
import com.aitchn.prism.validation.mekanism.logic.SolarGenerator;
import com.aitchn.prism.validation.mekanism.logic.Stack;
import com.aitchn.prism.validation.mekanism.logic.Teleport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * The mod's running world: the machines, generators and conduits that are loaded, and the two networks joining them.
 *
 * <p>Prism owns <em>where blocks are</em> and <em>what data they keep</em>; this class owns what energy and chemicals
 * <em>mean</em>. Blocks register through Prism's place/load events and leave through remove/unload. Their state is
 * kept in memory while loaded and written back into their per-block data every few seconds, on unload and on removal.
 *
 * <p>Threads: ticks, interactions and menus arrive on different (Folia) threads, so every access to the shared state
 * below happens under one lock and touches buffers only, never world state. The network loop runs on the global
 * thread and also touches only buffers. Anything that touches a block hops to its thread first.
 */
final class Grid {

    static final NamespacedKey STATE = NamespacedKey.fromString("mekanism:state");
    static final NamespacedKey ENERGY = NamespacedKey.fromString("mekanism:energy");

    private static final Grid INSTANCE = new Grid();
    private static final Logger LOG = Logger.getLogger("Mekanism");
    private static final int SAVE_EVERY = 100;
    private final ReentrantLock lock = new ReentrantLock();
    private final RecipeBook recipes = new RecipeBook();
    private final Plant plant = new Plant();
    private final Map<Pos, Machine> machines = plant.machines();
    private final Map<Pos, SolarGenerator> solars = plant.solars();
    private final Set<Pos> cables = plant.cables();
    private final Set<Pos> pipes = plant.pipes();
    private final Map<Pos, String> saved = new HashMap<>();
    private final Map<Pos, Miner> miners = new HashMap<>();
    private final Map<Pos, MachineMenu> viewers = new ConcurrentHashMap<>();
    private volatile io.papermc.paper.threadedregions.scheduler.ScheduledTask loop;

    static Grid get() {
        return INSTANCE;
    }

    static Pos pos(Block block) {
        return new Pos(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    RecipeBook recipes() {
        return recipes;
    }

    <T> T locked(Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    void underLock(Runnable action) {
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    // ---------------------------------------------------------------- network loop

    /**
     * Starts the network loop on the global region thread. Prism's scheduler has no global timer, and rescheduling
     * {@code runGlobal} from inside itself runs in the same tick forever, so this uses the platform timer directly.
     */
    void start(org.bukkit.plugin.Plugin plugin) {
        stop();
        loop = org.bukkit.Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            try {
                tickNetworks();
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Network tick failed", e);
            }
        }, 1, 1);
    }

    void stop() {
        if (loop != null) {
            loop.cancel();
            loop = null;
        }
    }

    private void tickNetworks() {
        underLock(plant::tickNetworks);
    }

    // ---------------------------------------------------------------- machines

    /** The machine of {@code block}, created (from the block's saved state) the first time it is asked for. */
    Machine machine(CustomBlock block, MachineBehavior spec) {
        Pos pos = pos(block.block());
        return locked(() -> {
            Machine existing = machines.get(pos);
            if (existing != null) {
                return existing;
            }
            MachineType type = new MachineType(spec.machine(), () -> recipes.forMachine(spec.recipesOf().isEmpty() ? spec.machine() : spec.recipesOf()),
                    spec.energyCapacity(), spec.maxReceive(), spec.tankCapacity(), spec.maxExtract(),
                    spec.bufferFilter(), spec.lanes());
            Machine machine = new Machine(type);
            String state = block.data().get(STATE, PersistentDataType.STRING);
            if (state != null) {
                machine.load(state);
                saved.put(pos, state);
            }
            machines.put(pos, machine);
            plant.markDirty();
            return machine;
        });
    }

    void tickMachine(BlockContext context, MachineBehavior spec) {
        CustomBlock custom = context.custom();
        Pos pos = pos(custom.block());
        boolean[] state = new boolean[2]; // worked, idle
        underLock(() -> {
            Machine machine = machine(custom, spec);
            machine.setPowered(custom.block().isBlockIndirectlyPowered());
            state[0] = machine.tick();
            state[1] = machine.idleTicks() >= Machine.IDLE_AFTER;
            if (Math.floorMod(machine.ticks() + pos.hashCode(), SAVE_EVERY) == 0) {
                saveMachine(custom, pos, machine);
            }
        });
        if (spec.miner()) {
            mineStep(custom, spec);
            state[0] |= minerBusy(pos);
            state[1] = !state[0];
        }
        boolean active = "active".equals(custom.variant());
        if (state[0] && !active) {
            Prism.blocks().setVariant(custom.block(), "active");
        } else if (state[1] && active) {
            Prism.blocks().setVariant(custom.block(), "idle");
        }
    }

    /** Lock held. */
    private void saveMachine(CustomBlock block, Pos pos, Machine machine) {
        String now = machine.save();
        if (!now.equals(saved.get(pos))) {
            block.data().set(STATE, PersistentDataType.STRING, now);
            saved.put(pos, now);
        }
    }

    /** The item a machine drops carries its energy, upgrades and tank contents (not the item slots, which drop as items). */
    void storeSettings(Machine machine, ItemStack drop) {
        String settings = locked(machine::saveSettings);
        Prism.items().editData(drop, data -> data.set(STATE, PersistentDataType.STRING, settings));
    }

    /** A block placed from an item that carries settings starts with them. */
    static void restoreFromItem(BlockContext context) {
        ItemStack from = context.placedFrom();
        if (from == null || !from.hasItemMeta()) {
            return;
        }
        String state = from.getItemMeta().getPersistentDataContainer().get(STATE, PersistentDataType.STRING);
        if (state != null) {
            context.custom().data().set(STATE, PersistentDataType.STRING, state);
        }
    }

    /** The chunk unloads: keep the state in the block's data and stop tracking it. */
    void unloadMachine(CustomBlock block, MachineBehavior spec) {
        Pos pos = pos(block.block());
        MachineMenu viewer = viewers.get(pos);
        boolean viewed = viewer != null && viewer.active();
        underLock(() -> {
            Machine machine = machines.remove(pos);
            if (machine != null) {
                if (viewed) {
                    // The open menu keeps (and later hands over) what its slots show; the saved state must not
                    // hold the same items again.
                    machine.markDead();
                    machine.setInput(null);
                    machine.setExtra(null);
                    machine.setOutput(null);
                }
                saveMachine(block, pos, machine);
            }
            saved.remove(pos);
            miners.remove(pos);
            plant.markDirty();
        });
    }

    /** The block is going away: stop tracking it and drop what is inside (or hand it to the open menu's viewer). */
    void removeMachine(CustomBlock block) {
        Pos pos = pos(block.block());
        MachineMenu viewer = viewers.get(pos);
        boolean viewed = viewer != null && viewer.active();
        Machine machine = locked(() -> {
            Machine removed = machines.remove(pos);
            saved.remove(pos);
            miners.remove(pos);
            plant.markDirty();
            if (removed != null) {
                removed.markDead();
            }
            return removed;
        });
        if (machine != null && !viewed) {
            Location at = block.block().getLocation().add(0.5, 0.5, 0.5);
            for (Stack stack : locked(() -> new Stack[] {machine.input(), machine.extra(), machine.output()})) {
                if (stack != null) {
                    create(stack).ifPresent(item -> block.block().getWorld().dropItemNaturally(at, item));
                }
            }
        }
    }

    static java.util.Optional<ItemStack> create(Stack stack) {
        return Prism.items().create(new ItemResult(PrismKey.parse(stack.id()), stack.count()));
    }

    /** Custom items by their Prism key, vanilla items by their material key; ignores per-stack data. */
    static String itemId(ItemStack stack) {
        return Prism.items().identify(stack).map(PrismKey::toString).orElseGet(() -> stack.getType().getKey().toString());
    }

    // ---------------------------------------------------------------- menus

    boolean openMenu(Player player, Pos pos, Machine machine, String id) {
        MachineMenu existing = viewers.get(pos);
        if (existing != null && existing.active()) {
            return false;
        }
        MachineMenu menu = new MachineMenu(this, pos, machine, id);
        viewers.put(pos, menu);
        underLock(() -> machine.setMenuOpen(true));
        Prism.menus().open(player, menu);
        return true;
    }

    void menuClosed(Pos pos, MachineMenu menu) {
        viewers.remove(pos, menu);
        if (!viewers.containsKey(pos)) {
            menu.releaseMachine();
        }
    }

    // ---------------------------------------------------------------- hands

    /** Right click on a machine or generator holding a bucket of water: fills its water tank. */
    boolean useBucket(BlockContext context, ChemicalTank tank) {
        ItemStack held = context.heldItem();
        Player player = context.player();
        if (tank == null || held == null || held.getType() != Material.WATER_BUCKET || player == null) {
            return false;
        }
        boolean filled = locked(() -> {
            if (tank.fill(com.aitchn.prism.validation.mekanism.logic.Chemical.WATER, 1_000, true) < 1_000) {
                return false;
            }
            tank.fill(com.aitchn.prism.validation.mekanism.logic.Chemical.WATER, 1_000, false);
            return true;
        });
        EquipmentSlot hand = context.hand();
        Prism.scheduler().runOn(player, () -> {
            if (filled) {
                if (hand != null && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    player.getInventory().setItem(hand, new ItemStack(Material.BUCKET));
                }
                player.sendActionBar(Component.text("Filled 1,000 mB of water"));
            } else {
                player.sendActionBar(Component.text("The water tank is full"));
            }
        }, null);
        return true;
    }

    /** Face number as in {@code Pos.neighbors()}: east, west, up, down, south, north; -1 for other directions. */
    static int faceIndex(org.bukkit.block.BlockFace face) {
        return face == null ? -1 : switch (face) {
            case EAST -> 0;
            case WEST -> 1;
            case UP -> 2;
            case DOWN -> 3;
            case SOUTH -> 4;
            case NORTH -> 5;
            default -> -1;
        };
    }

    /**
     * Right click on a machine with a setting tool. Configurator: the clicked face takes the next chemical side mode;
     * Item Configurator: the next item access for transporters instead. Redstone dust: next redstone mode. Hopper: auto-eject on or off.
     */
    boolean configure(BlockContext context, Machine machine) {
        ItemStack held = context.heldItem();
        Player player = context.player();
        if (held == null || player == null) {
            return false;
        }
        String id = itemId(held);
        int face = faceIndex(context.face());
        String text;
        if (id.equals("mekanism:configurator") && face >= 0) {
            text = locked(() -> {
                machine.setFace(face, machine.face(face).next());
                return Text.title(Side.FACE_NAMES[face]) + " face: " + machine.face(face).label();
            });
        } else if (id.equals("mekanism:item_configurator") && face >= 0) {
            text = locked(() -> {
                machine.setItemFace(face, machine.itemFace(face).next());
                return Text.title(Side.FACE_NAMES[face]) + " face (items): " + machine.itemFace(face).label();
            });
        } else if (id.equals("minecraft:redstone")) {
            text = locked(() -> {
                machine.setRedstone(machine.redstone().next());
                return machine.redstone().label();
            });
        } else if (id.equals("minecraft:hopper")) {
            text = locked(() -> {
                machine.setAutoEject(!machine.autoEject());
                return "Auto-eject " + (machine.autoEject() ? "on" : "off");
            });
        } else {
            return false;
        }
        Prism.scheduler().runOn(player, () -> player.sendActionBar(Component.text(text)), null);
        return true;
    }

    /** Right click on a machine holding a speed or energy upgrade: installs one (up to 8 of each kind). */
    boolean installUpgrade(BlockContext context, Machine machine) {
        ItemStack held = context.heldItem();
        Player player = context.player();
        if (held == null || player == null) {
            return false;
        }
        String id = itemId(held);
        boolean speed = id.equals("mekanism:speed_upgrade");
        if (!speed && !id.equals("mekanism:energy_upgrade")) {
            return false;
        }
        EquipmentSlot hand = context.hand();
        String wanted = id;
        // One transaction on the player's thread: check the hand still holds the upgrade, install, then take one.
        Runnable transaction = () -> {
            ItemStack now = hand == null ? null : player.getInventory().getItem(hand);
            boolean creative = player.getGameMode() == org.bukkit.GameMode.CREATIVE;
            if (!creative && (now == null || !itemId(now).equals(wanted))) {
                return;
            }
            int taken = locked(() -> machine.addUpgrades(speed, 1));
            if (taken > 0) {
                if (!creative) {
                    now.setAmount(now.getAmount() - 1);
                    player.getInventory().setItem(hand, now.getAmount() > 0 ? now : null);
                }
                player.sendActionBar(Component.text(locked(() -> (speed ? "Speed upgrades " : "Energy upgrades ")
                        + (speed ? machine.speedUpgrades() : machine.energyUpgrades()) + " / " + Machine.MAX_UPGRADES)));
            } else {
                player.sendActionBar(Component.text("This machine cannot take more of those"));
            }
        };
        if (Prism.scheduler().isOwnedByCurrentThread(player)) {
            transaction.run();
        } else {
            Prism.scheduler().runOn(player, transaction, null);
        }
        return true;
    }

    /** Right click on an energy block holding an Energy Tablet: pushes energy from the tablet into the block. */
    boolean pushTablet(BlockContext context, EnergyBuffer buffer) {
        ItemStack held = context.heldItem();
        Player player = context.player();
        if (held == null || player == null || !Tablets.is(held)) {
            return false;
        }
        long charge = Tablets.charge(held);
        long moved = locked(() -> buffer.produce(Math.min(Tablets.STEP, charge)));
        Tablets.change(player, context.hand(), -moved, "Pushed " + Text.number(moved) + " J into the block");
        return true;
    }

    /** Takes up to {@code max} joules out of {@code buffer}, whatever its extract rate. */
    long draw(EnergyBuffer buffer, long max) {
        return locked(() -> buffer.take(max));
    }

    // ---------------------------------------------------------------- generators

    SolarGenerator solar(CustomBlock block) {
        Pos pos = pos(block.block());
        return locked(() -> {
            SolarGenerator existing = solars.get(pos);
            if (existing != null) {
                return existing;
            }
            SolarGenerator solar = new SolarGenerator();
            Long stored = block.data().get(ENERGY, PersistentDataType.LONG);
            if (stored != null) {
                solar.energy().set(stored);
                saved.put(pos, Long.toString(stored));
            }
            solars.put(pos, solar);
            plant.markDirty();
            return solar;
        });
    }

    void tickSolar(BlockContext context) {
        CustomBlock custom = context.custom();
        Block block = custom.block();
        Pos pos = pos(block);
        boolean sunlit = block.getWorld().getTime() < 12_000 && block.getLightFromSky() >= 15;
        underLock(() -> {
            SolarGenerator solar = solar(custom);
            solar.tick(sunlit);
            if (Math.floorMod(pos.hashCode() + block.getWorld().getGameTime(), SAVE_EVERY) == 0) {
                saveSolar(custom, pos, solar);
            }
        });
    }

    /** Lock held. */
    private void saveSolar(CustomBlock block, Pos pos, SolarGenerator solar) {
        String now = Long.toString(solar.energy().stored());
        if (!now.equals(saved.get(pos))) {
            block.data().set(ENERGY, PersistentDataType.LONG, solar.energy().stored());
            saved.put(pos, now);
        }
    }

    void unloadSolar(CustomBlock block) {
        Pos pos = pos(block.block());
        underLock(() -> {
            SolarGenerator solar = solars.remove(pos);
            if (solar != null) {
                saveSolar(block, pos, solar);
            }
            saved.remove(pos);
            plant.markDirty();
        });
    }

    void removeSolar(CustomBlock block) {
        Pos pos = pos(block.block());
        underLock(() -> {
            solars.remove(pos);
            saved.remove(pos);
            plant.markDirty();
        });
    }

    String describeSolar(CustomBlock block) {
        SolarGenerator solar = solar(block);
        return locked(() -> "Solar generator: " + Text.number(solar.energy().stored()) + " / "
                + Text.number(solar.energy().capacity()) + " J, " + (solar.sunlit() ? "producing "
                + SolarGenerator.OUTPUT_PER_TICK + " J/t" : "no sunlight"));
    }


    // ---------------------------------------------------------------- digital miner

    private boolean minerBusy(Pos pos) {
        return locked(() -> {
            Miner miner = miners.get(pos);
            return miner != null && miner.pending() > 0;
        });
    }

    /** Lock held. The miner's scan state, restored from the machine's saved attachment the first time. */
    private Miner miner(Pos pos, Machine machine) {
        return miners.computeIfAbsent(pos, p -> {
            Miner miner = new Miner();
            if (machine.attachment() != null) {
                miner.load(machine.attachment());
            }
            return miner;
        });
    }

    /**
     * One tick of a digital miner. Positions are looked at on the thread that owns them (a Folia region may differ from
     * the miner's), ores are broken there, and the drops come back under the grid lock into the miner's output; what
     * does not fit is dropped beside the miner.
     */
    void mineStep(CustomBlock custom, MachineBehavior spec) {
        Block block = custom.block();
        Pos pos = pos(block);
        org.bukkit.World world = block.getWorld();
        int depth = block.getY() - world.getMinHeight();
        List<int[]> targets = new ArrayList<>();
        underLock(() -> {
            Machine machine = machine(custom, spec);
            Miner miner = miner(pos, machine);
            if (miner.pending() > 0 || machine.energy().stored() < Miner.COST_PER_BLOCK) {
                return;
            }
            for (int i = 0; i < Miner.LOOKS_PER_TICK && machine.energy().stored() >= Miner.COST_PER_BLOCK; i++) {
                int[] offset = miner.next(depth);
                if (offset == null) {
                    break;
                }
                miner.advance();
                targets.add(offset);
            }
            miner.begin(targets.size());
            machine.setAttachment(miner.save());
        });
        Location home = block.getLocation().add(0.5, 1.0, 0.5);
        for (int[] offset : targets) {
            Location at = new Location(world, block.getX() + offset[0], block.getY() + offset[1], block.getZ() + offset[2]);
            Prism.scheduler().runAt(at, () -> mineOne(at, home, pos, custom, spec));
        }
    }

    /** On the thread that owns {@code at}. */
    private void mineOne(Location at, Location home, Pos pos, CustomBlock miner, MachineBehavior spec) {
        List<ItemStack> drops = List.of();
        try {
            if (!at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
                return;
            }
            Block target = at.getBlock();
            String id = target.getType().getKey().toString();
            boolean custom = Prism.blocks().get(target).isPresent();
            if (!custom && Miner.accepts(id)) {
                boolean paid = locked(() -> {
                    Machine machine = machines.get(pos);
                    return machine != null && machine.energy().consume(Miner.COST_PER_BLOCK);
                });
                if (paid) {
                    drops = new ArrayList<>(target.getDrops());
                    target.setType(Material.AIR);
                }
            }
        } finally {
            List<ItemStack> mined = drops;
            List<ItemStack> spill = locked(() -> {
                Miner state = miners.get(pos);
                if (state != null) {
                    state.finish();
                }
                Machine machine = machines.get(pos);
                List<ItemStack> left = new ArrayList<>();
                for (ItemStack drop : mined) {
                    Stack rest = machine == null ? new Stack(itemId(drop), drop.getAmount())
                            : machine.insertOutput(new Stack(itemId(drop), drop.getAmount()));
                    if (rest.count() > 0) {
                        ItemStack copy = drop.clone();
                        copy.setAmount(rest.count());
                        left.add(copy);
                    }
                }
                return left;
            });
            if (!spill.isEmpty()) {
                Prism.scheduler().runAt(home, () -> spill.forEach(item -> home.getWorld().dropItemNaturally(home, item)));
            }
        }
    }

    /** Right click on a digital miner holding a compass: the next scan radius (the scan starts again). */
    boolean setRadius(BlockContext context, Machine machine) {
        ItemStack held = context.heldItem();
        Player player = context.player();
        if (held == null || player == null || held.getType() != Material.COMPASS) {
            return false;
        }
        Pos pos = pos(context.block());
        String text = locked(() -> {
            Miner miner = miner(pos, machine);
            int radius = miner.nextRadius();
            machine.setAttachment(miner.save());
            return "Scan radius " + radius + " (" + (2 * radius + 1) + " x " + (2 * radius + 1) + " columns)";
        });
        Prism.scheduler().runOn(player, () -> player.sendActionBar(Component.text(text)), null);
        return true;
    }


    // ---------------------------------------------------------------- teleporter

    /**
     * Right click on a teleporter. Holding a name tag with a name tunes it to that frequency. Otherwise the player
     * travels to the next teleporter of the same frequency, and the teleporter they clicked pays the energy.
     */
    void teleport(BlockContext context, Machine machine) {
        Player player = context.player();
        if (player == null) {
            return;
        }
        Pos here = pos(context.block());
        ItemStack held = context.heldItem();
        if (held != null && held.getType() == Material.NAME_TAG && held.hasItemMeta() && held.getItemMeta().hasDisplayName()) {
            String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(held.getItemMeta().displayName()).replace("|", "").replace("=", "");
            locked(() -> {
                machine.setAttachment(Teleport.Tuning.parse(machine.attachment()).tuned(name).save());
                return null;
            });
            Prism.scheduler().runOn(player, () -> player.sendActionBar(Component.text("Tuned to \"" + name + "\"")), null);
            return;
        }
        Object[] result = locked(() -> {
            Teleport.Tuning tuning = Teleport.Tuning.parse(machine.attachment());
            List<Pos> same = new ArrayList<>();
            for (var entry : machines.entrySet()) {
                Machine other = entry.getValue();
                if (other.type().id().equals(machine.type().id())
                        && Teleport.Tuning.parse(other.attachment()).frequency().equals(tuning.frequency())) {
                    same.add(entry.getKey());
                }
            }
            Pos target = Teleport.pick(here, same, tuning.counter());
            if (target == null) {
                return new Object[] {"No other teleporter on \"" + tuning.frequency() + "\"", null};
            }
            long cost = Teleport.cost(here, target);
            if (!machine.energy().consume(cost)) {
                return new Object[] {"Needs " + Text.number(cost) + " J, has " + Text.number(machine.energy().stored()), null};
            }
            machine.setAttachment(tuning.next().save());
            return new Object[] {"Teleporting (" + Text.number(cost) + " J)", target};
        });
        Pos target = (Pos) result[1];
        Prism.scheduler().runOn(player, () -> {
            player.sendActionBar(Component.text((String) result[0]));
            org.bukkit.World world = target == null ? null : org.bukkit.Bukkit.getWorld(target.world());
            if (world != null) {
                player.teleportAsync(new Location(world, target.x() + 0.5, target.y() + 1.0, target.z() + 0.5,
                        player.getLocation().getYaw(), player.getLocation().getPitch()));
            }
        }, null);
    }

    // ---------------------------------------------------------------- conduits

    enum Kind { CABLE, PIPE, ITEM }

    private Set<Pos> conduits(Kind kind) {
        return switch (kind) {
            case CABLE -> cables;
            case PIPE -> pipes;
            case ITEM -> plant.transporters();
        };
    }

    void addConduit(Block block, Kind kind, int tier) {
        Pos pos = pos(block);
        underLock(() -> {
            conduits(kind).add(pos);
            plant.setTier(pos, tier);
            plant.markDirty();
        });
    }

    void removeConduit(Block block, Kind kind) {
        Pos pos = pos(block);
        underLock(() -> {
            conduits(kind).remove(pos);
            plant.forgetConduit(pos);
        });
    }

    /** What the Energy Tablet shows for a cable or pipe, or null when {@code block} is neither. */
    String describeConduit(Block block) {
        Pos pos = pos(block);
        return locked(() -> {
            plant.rebuildIfNeeded();
            Networks.Network cable = Networks.containing(plant.cableNets(), pos);
            if (cable != null) {
                int generators = 0;
                int consumers = 0;
                long stored = 0;
                for (Pos end : cable.endpoints()) {
                    if (solars.containsKey(end)) {
                        generators++;
                        stored += solars.get(end).energy().stored();
                    }
                    if (machines.containsKey(end)) {
                        consumers++;
                        stored += machines.get(end).energy().stored();
                    }
                }
                return "Cable network: " + cable.conduits().size() + " cables, " + generators + " generators, "
                        + consumers + " machines, " + Text.number(stored) + " J stored, moving "
                        + Text.number(plant.energyMoved()) + " J/t (all networks)";
            }
            Networks.Network pipe = Networks.containing(plant.pipeNets(), pos);
            if (pipe != null) {
                int connected = (int) pipe.endpoints().stream().filter(machines::containsKey).count();
                return "Pipe network: " + pipe.conduits().size() + " pipes, " + connected + " machines, moving "
                        + Text.number(plant.chemicalMoved()) + " mB/t (all networks)";
            }
            Networks.Network items = Networks.containing(plant.itemNets(), pos);
            if (items != null) {
                int connected = (int) items.touches().stream().filter(e -> machines.containsKey(e.pos())).count();
                return "Transporter network: " + items.conduits().size() + " transporters, " + connected
                        + " machine faces, moving " + plant.itemsMoved() + " items/t (all networks)";
            }
            return null;
        });
    }

    // ---------------------------------------------------------------- for tests and the self test

    int machineCount() {
        return locked(() -> machines.size());
    }

    int solarCount() {
        return locked(() -> solars.size());
    }

    ChemicalPorts portsOf(Machine machine) {
        return locked(machine::ports);
    }
}
