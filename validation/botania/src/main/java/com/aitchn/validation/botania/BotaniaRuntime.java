package com.aitchn.validation.botania;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.block.BlockContext;
import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.validation.botania.logic.Apothecary;
import com.aitchn.validation.botania.logic.BlockEntity;
import com.aitchn.validation.botania.logic.Burst;
import com.aitchn.validation.botania.logic.Flower;
import com.aitchn.validation.botania.logic.FlowerType;
import com.aitchn.validation.botania.logic.ManaCollector;
import com.aitchn.validation.botania.logic.ManaNetwork;
import com.aitchn.validation.botania.logic.Pool;
import com.aitchn.validation.botania.logic.Pos;
import com.aitchn.validation.botania.logic.RecipeBook;
import com.aitchn.validation.botania.logic.Spreader;
import com.aitchn.validation.botania.logic.Tablet;
import com.aitchn.validation.botania.logic.WandBinding;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * The rules of the mod on top of Prism's block platform. Prism finds the blocks again after a restart, ticks them on
 * the right region and keeps their data; this class only turns block events into calls on the Bukkit-free rules.
 * State lives in each block's own data (one string), so nothing is stored outside the world.
 */
final class BotaniaRuntime {

    static final String WAND = "botania:wand_of_the_forest";
    static final String TABLET = "botania:mana_tablet";
    static final NamespacedKey STATE = NamespacedKey.fromString("botania:state");
    static final NamespacedKey TABLET_MANA = NamespacedKey.fromString("botania:mana");
    private static final int SAVE_INTERVAL = 200;

    private final Logger log;
    private final ManaNetwork network = new ManaNetwork();
    private final WandBinding wand = new WandBinding();
    private final Map<Pos, int[]> ticks = new ConcurrentHashMap<>();
    private volatile RecipeBook recipes = new RecipeBook();

    BotaniaRuntime(Logger log) {
        this.log = log;
    }

    ManaNetwork network() {
        return network;
    }

    void recipes(RecipeBook book) {
        recipes = book;
    }

    private BukkitWorld view() {
        return new BukkitWorld(recipes);
    }

    // ---- block lifecycle --------------------------------------------------------------------------------------

    /** The entity of a block, created (or read from the block's data) on first use. */
    BlockEntity ensure(CustomBlock custom, String kind) {
        Pos pos = BukkitWorld.pos(custom.block());
        var existing = network.get(pos);
        if (existing.isPresent()) {
            return existing.get();
        }
        BlockEntity entity = null;
        String raw = custom.data().get(STATE, PersistentDataType.STRING);
        if (raw != null) {
            try {
                entity = BlockEntity.deserialize(pos, raw);
            } catch (IllegalArgumentException e) {
                log.log(Level.WARNING, "Unreadable state at " + pos + ", starting fresh", e);
            }
        }
        if (entity == null) {
            entity = create(pos, kind, custom.facing());
        }
        network.put(entity);
        save(custom, entity);
        return entity;
    }

    /** Same, for code that only has the block (the self test). */
    BlockEntity ensure(Block block, String kind) {
        return ensure(Prism.blocks().get(block).orElseThrow(), kind);
    }

    private static BlockEntity create(Pos pos, String kind, BlockFace facing) {
        return switch (kind) {
            case "pool" -> new Pool(pos, 0);
            case "spreader" -> {
                float[] aim = aim(facing);
                yield new Spreader(pos, 0, aim[0], aim[1]);
            }
            case "apothecary" -> new Apothecary(pos, false, List.of());
            default -> {
                if (kind.startsWith("flower:")) {
                    yield new Flower(pos, FlowerType.byId(kind.substring("flower:".length())), 0, null);
                }
                throw new IllegalArgumentException("unknown block entity kind '" + kind + "'");
            }
        };
    }

    /** A spreader shoots away from the side that faces the placer. Returns {yaw, pitch}. */
    static float[] aim(BlockFace facing) {
        return switch (facing.getOppositeFace()) {
            case SOUTH -> new float[] {0f, 0f};
            case WEST -> new float[] {90f, 0f};
            case NORTH -> new float[] {180f, 0f};
            case EAST -> new float[] {-90f, 0f};
            case UP -> new float[] {0f, -90f};
            case DOWN -> new float[] {0f, 90f};
            default -> new float[] {0f, 0f};
        };
    }

    void tick(CustomBlock custom, String kind) {
        BlockEntity entity = ensure(custom, kind);
        Pos pos = entity.pos();
        try {
            entity.tick(view(), network);
            if (entity instanceof Spreader spreader) {
                spreader.fire().ifPresent(burst -> fly(pos.world(), burst));
            }
            int[] counter = ticks.computeIfAbsent(pos, p -> new int[1]);
            if (++counter[0] % SAVE_INTERVAL == 0) {
                save(custom, entity);
            }
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Block entity at " + pos + " failed", e);
        }
    }

    private static void save(CustomBlock custom, BlockEntity entity) {
        String now = entity.serialize();
        if (!now.equals(custom.data().get(STATE, PersistentDataType.STRING))) {
            custom.data().set(STATE, PersistentDataType.STRING, now);
        }
    }

    void unloaded(CustomBlock custom) {
        Pos pos = BukkitWorld.pos(custom.block());
        network.remove(pos).ifPresent(entity -> save(custom, entity));
        ticks.remove(pos);
    }

    /** The block is going away (any cause): forget the entity and hand back what it held. */
    void removed(CustomBlock custom) {
        Pos pos = BukkitWorld.pos(custom.block());
        ticks.remove(pos);
        network.remove(pos).ifPresent(entity -> {
            if (entity instanceof Apothecary apothecary) {
                for (String ingredient : apothecary.ingredients()) {
                    view().dropItem(pos.offset(0, 1, 0), ingredient, 1);
                }
            }
        });
    }

    // ---- bursts -----------------------------------------------------------------------------------------------

    private void fly(String worldName, Burst burst) {
        World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        double x0 = burst.x();
        double y0 = burst.y();
        double z0 = burst.z();
        Burst.Result result = burst.advance(view(), network);
        trail(world, x0, y0, z0, burst.x(), burst.y(), burst.z());
        if (result.outcome() == Burst.Outcome.NEED_REGION) {
            Location next = BukkitWorld.location(result.at());
            if (next != null) {
                Prism.scheduler().runAt(next, () -> fly(worldName, burst));
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

    // ---- interaction ------------------------------------------------------------------------------------------

    /** Right click on a Botania block. Cancels the vanilla action when it handled the click. */
    void interact(BlockContext context, String kind) {
        Player player = context.player();
        if (player == null) {
            return;
        }
        CustomBlock custom = context.custom();
        BlockEntity entity = ensure(custom, kind);
        ItemStack held = context.heldItem();
        String heldId = BukkitWorld.itemId(held);

        if (WAND.equals(heldId)) {
            useWand(player, entity);
            save(custom, entity);
            context.cancel();
        } else if (TABLET.equals(heldId) && entity instanceof Pool pool) {
            useTablet(player, pool, held, context.hand());
            save(custom, entity);
            context.cancel();
        } else if (entity instanceof Apothecary apothecary) {
            useApothecary(player, apothecary, held, heldId);
            save(custom, entity);
            context.cancel();
        } else if (entity instanceof ManaCollector collector && heldId == null) {
            player.sendActionBar(Component.text("Mana " + collector.mana() + " / " + collector.capacity()));
            context.cancel();
        }
    }

    private void useWand(Player player, BlockEntity entity) {
        if (player.isSneaking() && entity instanceof Spreader spreader) {
            spreader.aim(player.getLocation().getYaw(), player.getLocation().getPitch());
            player.sendActionBar(Component.text("Spreader aimed"));
            return;
        }
        var result = wand.click(player.getUniqueId().toString(), entity.pos(), network);
        player.sendActionBar(Component.text(switch (result) {
            case SELECTED -> "Flower selected: now click a pool or spreader";
            case BOUND -> "Bound";
            case OUT_OF_RANGE -> "Too far away (max " + (int) Flower.BIND_RANGE + " blocks)";
            case NOTHING -> "Nothing to bind";
        }));
    }

    private void useTablet(Player player, Pool pool, ItemStack tablet, EquipmentSlot hand) {
        int stored = TabletLore.stored(tablet);
        int moved;
        int next;
        if (player.isSneaking()) {
            moved = pool.receive(Tablet.toPool(stored, Pool.CAPACITY - pool.mana()));
            next = stored - moved;
        } else {
            moved = pool.take(Tablet.toTablet(pool.mana(), stored));
            next = stored + moved;
        }
        if (moved == 0) {
            player.sendActionBar(Component.text("Nothing to move"));
            return;
        }
        Prism.items().editData(tablet, data -> data.set(TABLET_MANA, PersistentDataType.INTEGER, next));
        if (hand != null) {
            player.getInventory().setItem(hand, tablet);
        }
        player.sendActionBar(Component.text("Tablet " + next + " / " + Tablet.CAPACITY));
    }

    private void useApothecary(Player player, Apothecary apothecary, ItemStack held, String heldId) {
        Apothecary.Outcome outcome = apothecary.use(heldId, recipes);
        boolean survival = player.getGameMode() != GameMode.CREATIVE;
        Pos above = apothecary.pos().offset(0, 1, 0);
        switch (outcome.kind()) {
            case FILLED -> {
                if (survival) {
                    player.getInventory().setItemInMainHand(new ItemStack(org.bukkit.Material.BUCKET));
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
                view().dropItem(above, outcome.item(), 1);
            }
            case REMOVED -> view().dropItem(above, outcome.item(), 1);
            case NO_MATCH -> player.sendActionBar(Component.text("These petals make nothing"));
            case REJECTED -> { }
        }
    }
}
