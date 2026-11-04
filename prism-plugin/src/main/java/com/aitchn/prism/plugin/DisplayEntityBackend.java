package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.BlockService;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.item.ItemService;
import com.aitchn.prism.api.registry.Registry;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Custom blocks as a vanilla carrier block plus a persistent item display entity that draws the model. The display
 * is saved with its chunk, so the block survives restarts without any Prism-side database; its persistent data
 * holds the block key. Everything here must run on the thread that owns the block's region.
 */
final class DisplayEntityBackend implements BlockService {

    private static final double SEARCH_RADIUS = 0.3;

    private final NamespacedKey markerKey;
    private final Registry<BlockDefinition> definitions;
    private final ItemService items;
    private final Appearance appearance;
    private final ModelAnchors anchors;

    DisplayEntityBackend(Plugin prism, Registry<BlockDefinition> definitions, ItemService items, Appearance appearance,
            ModelAnchors anchors) {
        this.markerKey = new NamespacedKey(prism, "block");
        this.anchors = anchors;
        this.definitions = definitions;
        this.items = items;
        this.appearance = appearance;
    }

    @Override
    public boolean place(@NotNull PrismKey key, @NotNull Location location) {
        Optional<BlockDefinition> found = definitions.get(key);
        if (found.isEmpty()) {
            return false;
        }
        BlockDefinition definition = found.get();
        Optional<Material> carrier = VanillaItems.blockMaterial(definition.carrier());
        Block block = location.getBlock();
        if (carrier.isEmpty() || !(block.isEmpty() || block.isReplaceable()) || identify(block).isPresent()) {
            return false;
        }
        block.setType(carrier.get(), false);
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        boolean modelled = ModelAnchors.modelOf(definition).isPresent();
        ItemDisplay anchor = block.getWorld().spawn(center, ItemDisplay.class, display -> {
            display.setPersistent(true);
            display.setBillboard(Display.Billboard.FIXED);
            display.getPersistentDataContainer().set(markerKey, PersistentDataType.STRING, key.toString());
            if (!modelled && appearance.hasBlock(key)) {
                ItemStack shown = new ItemStack(Material.PAPER);
                shown.editMeta(meta -> Keys.bukkit(new PrismKey(key.namespace(), "block/" + key.value()))
                        .ifPresent(meta::setItemModel));
                display.setItemStack(shown);
            }
        });
        if (modelled) {
            anchors.attach(anchor, key);
        }
        return true;
    }

    @Override
    public @NotNull Optional<PrismKey> identify(@NotNull Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (ItemDisplay display : block.getWorld().getNearbyEntitiesByType(ItemDisplay.class, center, SEARCH_RADIUS)) {
            String raw = display.getPersistentDataContainer().get(markerKey, PersistentDataType.STRING);
            if (raw != null) {
                try {
                    return Optional.of(PrismKey.parse(raw));
                } catch (IllegalArgumentException e) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean remove(@NotNull Block block, boolean dropItem) {
        Optional<PrismKey> key = identify(block);
        if (key.isEmpty()) {
            return false;
        }
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (ItemDisplay display : block.getWorld().getNearbyEntitiesByType(ItemDisplay.class, center, SEARCH_RADIUS)) {
            if (display.getPersistentDataContainer().has(markerKey, PersistentDataType.STRING)) {
                anchors.detach(display.getUniqueId());
                display.remove();
            }
        }
        block.setType(Material.AIR, false);
        if (dropItem) {
            definitions.get(key.get()).flatMap(BlockDefinition::drop)
                    .flatMap(drop -> items.create(drop, 1))
                    .ifPresent(stack -> block.getWorld().dropItemNaturally(center, stack));
        }
        return true;
    }
}
