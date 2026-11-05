package com.aitchn.validation.botania;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.block.BlockContext;
import com.aitchn.prism.api.data.DataStore;
import com.aitchn.validation.botania.logic.Apothecary;
import com.aitchn.validation.botania.logic.BlockEntity;
import com.aitchn.validation.botania.logic.Burst;
import com.aitchn.validation.botania.logic.Flower;
import com.aitchn.validation.botania.logic.FlowerType;
import com.aitchn.validation.botania.logic.ManaCollector;
import com.aitchn.validation.botania.logic.ManaNetwork;
import com.aitchn.validation.botania.logic.Pool;
import com.aitchn.validation.botania.logic.Pos;
import com.aitchn.validation.botania.logic.Spreader;
import com.aitchn.validation.botania.logic.WandBinding;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Owns the loaded block entities: one repeating task per block on the region scheduler, persistence in Prism's
 * DataStore (one key per block, grouped by chunk), and the hand-over of bursts between regions.
 */
final class BotaniaRuntime {

    static final String WAND = "botania:wand_of_the_forest";
    private static final int SAVE_INTERVAL = 200;

    private static final class Active {
        final BlockEntity entity;
        volatile ScheduledTask task;
        int ticks;
        String saved;

        Active(BlockEntity entity) {
            this.entity = entity;
        }
    }

    private final Plugin plugin;
    private final ManaNetwork network = new ManaNetwork();
    private final WandBinding wand = new WandBinding();
    private final Map<Pos, Active> active = new ConcurrentHashMap<>();
    private volatile DataStore store;

    BotaniaRuntime(Plugin plugin) {
        this.plugin = plugin;
    }

    ManaNetwork network() {
        return network;
    }

    int activeCount() {
        return active.size();
    }

    private DataStore store() {
        DataStore current = store;
        if (current == null) {
            current = Prism.data().store("botania");
            store = current;
        }
        return current;
    }

    // ---- lifecycle of a block entity -------------------------------------------------------------------------

    /** Creates, registers and persists the entity for a block that was just placed (or found without one). */
    BlockEntity ensure(Block block, String kind, Player placer) {
        Pos pos = BukkitWorld.pos(block);
        var existing = network.get(pos);
        if (existing.isPresent()) {
            return existing.get();
        }
        float yaw = placer == null ? 0f : placer.getLocation().getYaw();
        BlockEntity created = switch (kind) {
            case "pool" -> new Pool(pos, 0);
            case "spreader" -> new Spreader(pos, 0, yaw, 0f);
            case "apothecary" -> new Apothecary(pos, false, java.util.List.of());
            default -> {
                if (kind.startsWith("flower:")) {
                    yield new Flower(pos, FlowerType.byId(kind.substring("flower:".length())), 0, null);
                }
                throw new IllegalArgumentException("unknown block entity kind '" + kind + "'");
            }
        };
        register(created, true);
        return created;
    }

    private void register(BlockEntity entity, boolean persist) {
        Pos pos = entity.pos();
        Location at = BukkitWorld.location(pos);
        if (at == null) {
            return;
        }
        Active fresh = new Active(entity);
        Active previous = active.put(pos, fresh);
        if (previous != null && previous.task != null) {
            previous.task.cancel();
        }
        network.put(entity);
        fresh.saved = entity.serialize();
        if (persist) {
            store().put(pos.storeKey(), fresh.saved);
        }
        fresh.task = Bukkit.getRegionScheduler().runAtFixedRate(plugin, at, task -> tick(fresh, task), 1L, 1L);
    }

    private void tick(Active entry, ScheduledTask task) {
        Pos pos = entry.entity.pos();
        if (active.get(pos) != entry) {
            task.cancel();
            return;
        }
        try {
            entry.entity.tick(new BukkitWorld(), network);
            if (entry.entity instanceof Spreader spreader) {
                spreader.fire().ifPresent(burst -> fly(pos.world(), burst));
            }
            if (++entry.ticks % SAVE_INTERVAL == 0) {
                save(entry);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Block entity at " + pos + " failed", e);
        }
    }

    private void save(Active entry) {
        String now = entry.entity.serialize();
        if (!now.equals(entry.saved)) {
            store().put(entry.entity.pos().storeKey(), now);
            entry.saved = now;
        }
    }

    /** The block was broken: forget the entity and give back what it held. */
    void removed(BlockContext context) {
        discard(context.block());
    }

    void discard(Block block) {
        Pos pos = BukkitWorld.pos(block);
        Active entry = active.remove(pos);
        if (entry == null) {
            return;
        }
        if (entry.task != null) {
            entry.task.cancel();
        }
        network.remove(pos);
        store().remove(pos.storeKey());
        if (entry.entity instanceof Apothecary apothecary) {
            for (String ingredient : apothecary.ingredients()) {
                new BukkitWorld().dropItem(pos.offset(0, 1, 0), ingredient, 1);
            }
        }
    }

    // ---- chunks ------------------------------------------------------------------------------------------------

    /** Must run on the region thread of the chunk. */
    void loadChunk(World world, int chunkX, int chunkZ) {
        String prefix = Pos.chunkPrefix(world.getName(), chunkX, chunkZ);
        DataStore data = store();
        for (String key : data.keys(prefix)) {
            try {
                Pos pos = Pos.parseCoords(world.getName(), key.substring(prefix.length()));
                if (active.containsKey(pos)) {
                    continue;
                }
                String raw = data.get(key).orElse(null);
                if (raw == null) {
                    continue;
                }
                if (Prism.blocks().identify(world.getBlockAt(pos.x(), pos.y(), pos.z())).isEmpty()) {
                    data.remove(key); // the block is gone (removed while Botania was not running)
                    continue;
                }
                register(BlockEntity.deserialize(pos, raw), false);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, "Skipping unreadable block entity " + key, e);
            }
        }
    }

    void unloadChunk(World world, int chunkX, int chunkZ) {
        for (Active entry : active.values()) {
            Pos pos = entry.entity.pos();
            if (pos.world().equals(world.getName()) && pos.chunkX() == chunkX && pos.chunkZ() == chunkZ) {
                save(entry);
                if (entry.task != null) {
                    entry.task.cancel();
                }
                active.remove(pos, entry);
                network.remove(pos);
            }
        }
    }

    void shutdown() {
        for (Active entry : active.values()) {
            save(entry);
            if (entry.task != null) {
                entry.task.cancel();
            }
        }
        active.clear();
    }

    // ---- bursts ----------------------------------------------------------------------------------------------

    private void fly(String worldName, Burst burst) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        double x0 = burst.x();
        double y0 = burst.y();
        double z0 = burst.z();
        Burst.Result result = burst.advance(new BukkitWorld(), network);
        trail(world, x0, y0, z0, burst.x(), burst.y(), burst.z());
        if (result.outcome() == Burst.Outcome.NEED_REGION) {
            Location next = BukkitWorld.location(result.at());
            if (next != null) {
                Bukkit.getRegionScheduler().execute(plugin, next, () -> fly(worldName, burst));
            }
        }
    }

    private static void trail(World world, double x0, double y0, double z0, double x1, double y1, double z1) {
        double length = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
        int steps = (int) Math.ceil(length / 0.5);
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double) i / steps;
            world.spawnParticle(Particle.END_ROD, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, z0 + (z1 - z0) * t,
                    1, 0, 0, 0, 0);
        }
    }

    // ---- interaction -----------------------------------------------------------------------------------------

    /** Right click on a Botania block. Cancels the vanilla action when it handled the click. */
    void interact(BlockContext context, String kind) {
        Player player = context.player();
        if (player == null) {
            return;
        }
        Block block = context.block();
        BlockEntity entity = ensure(block, kind, player);
        ItemStack held = player.getInventory().getItemInMainHand();
        String heldId = BukkitWorld.itemId(held);

        if (WAND.equals(heldId)) {
            useWand(player, entity);
            context.cancel();
        } else if (entity instanceof Apothecary apothecary) {
            useApothecary(player, apothecary, held, heldId);
            context.cancel();
        } else if (entity instanceof ManaCollector collector && heldId == null) {
            player.sendActionBar(Component.text("Mana " + collector.mana() + " / " + collector.capacity()));
            context.cancel();
        }
    }

    private void useWand(Player player, BlockEntity entity) {
        if (player.isSneaking() && entity instanceof Spreader spreader) {
            spreader.aim(player.getLocation().getYaw(), player.getLocation().getPitch());
            mark(entity);
            player.sendActionBar(Component.text("Spreader aimed"));
            return;
        }
        String id = player.getUniqueId().toString();
        var from = wand.selected(id);
        var result = wand.click(id, entity.pos(), network);
        String message = switch (result) {
            case SELECTED -> "Flower selected: now click a pool or spreader";
            case BOUND -> "Bound";
            case OUT_OF_RANGE -> "Too far away (max " + (int) Flower.BIND_RANGE + " blocks)";
            case NOTHING -> "Nothing to bind";
        };
        if (result == WandBinding.Result.BOUND) {
            from.flatMap(network::get).ifPresent(this::mark);
        }
        player.sendActionBar(Component.text(message));
    }

    private void useApothecary(Player player, Apothecary apothecary, ItemStack held, String heldId) {
        Apothecary.Outcome outcome = apothecary.use(heldId);
        boolean survival = player.getGameMode() != GameMode.CREATIVE;
        Pos above = apothecary.pos().offset(0, 1, 0);
        switch (outcome.kind()) {
            case FILLED -> {
                if (survival) {
                    player.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
                }
            }
            case ADDED -> {
                if (survival) {
                    held.subtract();
                }
            }
            case CRAFTED -> {
                if (survival) {
                    held.subtract();
                }
                new BukkitWorld().dropItem(above, outcome.item(), 1);
            }
            case REMOVED -> new BukkitWorld().dropItem(above, outcome.item(), 1);
            case NO_MATCH -> player.sendActionBar(Component.text("These petals make nothing"));
            case REJECTED -> { }
        }
        mark(apothecary);
    }

    private void mark(BlockEntity entity) {
        Active entry = active.get(entity.pos());
        if (entry != null) {
            save(entry);
        }
    }
}
