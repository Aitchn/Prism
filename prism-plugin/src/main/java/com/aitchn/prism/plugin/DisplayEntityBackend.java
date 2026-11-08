package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.core.block.Appearances;
import com.aitchn.prism.core.block.Facings;
import java.util.Optional;
import java.util.function.BiConsumer;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * How a custom block looks in v1: a vanilla carrier block plus a persistent item display entity that draws the
 * model, rotated to the block's facing. Which block is where is kept by {@link BlockIndex}; the display's marker
 * (the block key in its persistent data) is still written so worlds from before the index can be migrated.
 * Everything here runs on the thread that owns the block's region.
 */
final class DisplayEntityBackend implements BlockAppearance {

    private static final double SEARCH_RADIUS = 0.3;

    private final NamespacedKey markerKey;
    private final Appearance appearance;
    private final ModelAnchors anchors;

    DisplayEntityBackend(Plugin prism, Appearance appearance, ModelAnchors anchors) {
        this.markerKey = new NamespacedKey(prism, "block");
        this.anchors = anchors;
        this.appearance = appearance;
    }

    @Override
    public boolean spawn(Block block, BlockDefinition definition, BlockFace facing, String variant) {
        Optional<Material> carrier = VanillaItems.blockMaterial(definition.carrier());
        if (carrier.isEmpty()) {
            return false;
        }
        PrismKey key = definition.key();
        block.setType(carrier.get(), false);
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        center.setYaw(Facings.yawOf(facing.name()));
        center.setPitch(Facings.pitchOf(facing.name()));
        boolean modelled = ModelAnchors.modelOf(definition).isPresent();
        ItemDisplay anchor = block.getWorld().spawn(center, ItemDisplay.class, display -> {
            display.setPersistent(true);
            display.setBillboard(Display.Billboard.FIXED);
            display.getPersistentDataContainer().set(markerKey, PersistentDataType.STRING, key.toString());
            if (!modelled && appearance.hasBlock(key)) {
                display.setItemStack(shown(key, variant));
            }
        });
        if (modelled) {
            anchors.attach(anchor, key);
        }
        return true;
    }

    private static ItemStack shown(PrismKey key, String variant) {
        ItemStack shown = new ItemStack(Material.PAPER);
        shown.editMeta(meta -> Keys.bukkit(Appearances.itemModel(key, variant)).ifPresent(meta::setItemModel));
        return shown;
    }

    @Override
    public void update(Block block, BlockDefinition definition, BlockFace facing, String variant) {
        if (ModelAnchors.modelOf(definition).isPresent() || !appearance.hasBlock(definition.key())) {
            return;
        }
        for (ItemDisplay display : displays(block)) {
            display.setItemStack(shown(definition.key(), variant));
        }
    }

    @Override
    public boolean draws(Block block) {
        return !displays(block).isEmpty();
    }

    private java.util.List<ItemDisplay> displays(Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        java.util.List<ItemDisplay> found = new java.util.ArrayList<>();
        for (ItemDisplay display : block.getWorld().getNearbyEntitiesByType(ItemDisplay.class, center, SEARCH_RADIUS)) {
            if (display.getPersistentDataContainer().has(markerKey, PersistentDataType.STRING)) {
                found.add(display);
            }
        }
        return found;
    }

    @Override
    public void despawn(Block block) {
        for (ItemDisplay display : displays(block)) {
            anchors.detach(display.getUniqueId());
            display.remove();
        }
        block.setType(Material.AIR, false);
    }

    @Override
    public void orphaned(Block block) {
        for (ItemDisplay display : displays(block)) {
            anchors.detach(display.getUniqueId());
            display.remove();
        }
    }

    /** Calls {@code found} for every marked display in {@code chunk}; used to migrate pre-index worlds. */
    void forEachMarker(Chunk chunk, BiConsumer<Block, PrismKey> found) {
        for (Entity entity : chunk.getEntities()) {
            forMarker(entity, found);
        }
    }

    void forMarker(Entity entity, BiConsumer<Block, PrismKey> found) {
        if (!(entity instanceof ItemDisplay)) {
            return;
        }
        String raw = entity.getPersistentDataContainer().get(markerKey, PersistentDataType.STRING);
        if (raw == null) {
            return;
        }
        try {
            found.accept(entity.getLocation().getBlock(), PrismKey.parse(raw));
        } catch (IllegalArgumentException e) {
            // Unreadable marker: leave the entity alone.
        }
    }
}
