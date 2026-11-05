package com.aitchn.validation.botania;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.PrismKey;
import com.aitchn.validation.botania.logic.Pos;
import com.aitchn.validation.botania.logic.WorldView;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

/** {@link WorldView} over Bukkit. Every call must run on the thread that owns the position (see isOwned). */
final class BukkitWorld implements WorldView {

    static Pos pos(Block block) {
        return new Pos(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    static Location location(Pos pos) {
        World world = Bukkit.getWorld(pos.world());
        return world == null ? null : new Location(world, pos.x(), pos.y(), pos.z());
    }

    /** Vanilla id of an item, or the Prism key for custom items; null for air. */
    static String itemId(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        return Prism.items().identify(stack).map(PrismKey::toString)
                .orElseGet(() -> stack.getType().getKey().toString());
    }

    private static Block block(Pos pos) {
        Location at = location(pos);
        return at == null ? null : at.getBlock();
    }

    @Override
    public boolean isOwned(Pos pos) {
        Location at = location(pos);
        return at != null && Bukkit.isOwnedByCurrentRegion(at);
    }

    @Override
    public boolean isSolid(Pos pos) {
        Block block = block(pos);
        return block != null && block.getType().isSolid();
    }

    @Override
    public boolean isDaylight(Pos pos) {
        Block block = block(pos);
        return block != null && block.getWorld().isDayTime() && block.getLightFromSky() >= 12;
    }

    @Override
    public String blockId(Pos pos) {
        Block block = block(pos);
        if (block == null) {
            return "minecraft:air";
        }
        if (block.getType() == Material.BARRIER) {
            return Prism.blocks().identify(block).map(PrismKey::toString).orElse("minecraft:barrier");
        }
        return block.getType().getKey().toString();
    }

    @Override
    public boolean convert(Pos pos, String blockId) {
        Block block = block(pos);
        if (block == null) {
            return false;
        }
        PrismKey key = PrismKey.parse(blockId);
        if (key.namespace().equals(PrismKey.MINECRAFT_NAMESPACE)) {
            Material material = Material.matchMaterial(blockId);
            if (material == null) {
                return false;
            }
            block.setType(material);
            return true;
        }
        // Custom blocks need an empty target: clear whatever is there (a custom block must be removed properly).
        if (Prism.blocks().identify(block).isPresent()) {
            Prism.blocks().remove(block, false);
        }
        block.setType(Material.AIR, false);
        return Prism.blocks().place(key, block.getLocation());
    }

    @Override
    public List<String> droppedItems(Pos pos, double radius) {
        Location at = location(pos);
        List<String> ids = new ArrayList<>();
        if (at == null) {
            return ids;
        }
        for (Item item : at.getWorld().getNearbyEntitiesByType(Item.class, at.add(0.5, 0.5, 0.5), radius)) {
            String id = itemId(item.getItemStack());
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    @Override
    public boolean consumeDroppedItem(Pos pos, double radius, String itemId) {
        Location at = location(pos);
        if (at == null) {
            return false;
        }
        for (Item item : at.getWorld().getNearbyEntitiesByType(Item.class, at.add(0.5, 0.5, 0.5), radius)) {
            ItemStack stack = item.getItemStack();
            if (itemId.equals(itemId(stack))) {
                if (stack.getAmount() <= 1) {
                    item.remove();
                } else {
                    stack.setAmount(stack.getAmount() - 1);
                    item.setItemStack(stack);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void dropItem(Pos pos, String itemId, int amount) {
        Location at = location(pos);
        if (at == null) {
            return;
        }
        PrismKey key = PrismKey.parse(itemId);
        ItemStack stack;
        if (key.namespace().equals(PrismKey.MINECRAFT_NAMESPACE)) {
            Material material = Material.matchMaterial(itemId);
            stack = material == null ? null : new ItemStack(material, amount);
        } else {
            stack = Prism.items().create(key, amount).orElse(null);
        }
        if (stack != null) {
            at.getWorld().dropItem(at.add(0.5, 0.5, 0.5), stack);
        }
    }
}
