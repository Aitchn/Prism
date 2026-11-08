package com.aitchn.prism.plugin;

import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.util.List;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Light;

/**
 * Light from custom blocks ({@code prism:light}). A note block or barrier carrier cannot glow, so a lit block puts a
 * vanilla light block ({@code minecraft:light}, invisible, no collision, replaceable) in an air cell next to it: above
 * first, then the sides, then below. The side is saved in the chunk index so the light is removed with the block or
 * when its variant goes dark. Geyser shows the light block to Bedrock players as theirs, so both editions get the
 * same light. If a player builds into the cell, the light moves to another free side; with none free, the block is
 * dark until one frees up. Call everything on the block's thread.
 */
final class BlockLights {

    private static final List<BlockFace> ORDER = List.of(BlockFace.UP, BlockFace.NORTH, BlockFace.EAST,
            BlockFace.SOUTH, BlockFace.WEST, BlockFace.DOWN);

    private final BlockIndex index;
    private final PlatformScheduler scheduler;

    BlockLights(BlockIndex index, PlatformScheduler scheduler) {
        this.index = index;
        this.scheduler = scheduler;
    }

    /** The light level {@code custom} should give now: its {@code prism:light} level in a lit variant, else 0. */
    static int level(CustomBlock custom) {
        return custom.definition().components().get(PrismComponents.LIGHT)
                .filter(light -> light.variants().isEmpty() || light.variants().contains(custom.variant()))
                .map(PrismComponents.Light::level).orElse(0);
    }

    /** Puts, moves or removes the light of {@code custom} to match its current variant. */
    void update(CustomBlock custom) {
        Block block = custom.block();
        int level = level(custom);
        Optional<String> saved = index.light(block);
        if (saved.isEmpty() && level == 0) {
            return;
        }
        Block current = saved.map(side -> block.getRelative(BlockFace.valueOf(side))).orElse(null);
        if (current != null && !here(current)) {
            if (level == 0) {
                clear(current);
                index.setLight(block, null);
            }
            return; // a light across a region border is left as it is while the block stays lit
        }
        boolean standing = current != null && current.getType() == Material.LIGHT;
        if (level == 0) {
            if (standing) {
                current.setType(Material.AIR, false);
            }
            index.setLight(block, null);
            return;
        }
        if (standing) {
            set(current, level);
            return;
        }
        for (BlockFace side : ORDER) {
            Block cell = block.getRelative(side);
            if (here(cell) && cell.getType().isAir() && index.get(cell).isEmpty()) {
                set(cell, level);
                index.setLight(block, side.name());
                return;
            }
        }
        index.setLight(block, null); // nowhere to put it now; a neighbour change tries again
    }

    /** {@code custom} is being removed: take its light away. */
    void removed(CustomBlock custom) {
        index.light(custom.block()).ifPresent(side -> clear(custom.block().getRelative(BlockFace.valueOf(side))));
    }

    /** Whether {@code cell} is loaded and may be changed on this thread (Folia regions). */
    private boolean here(Block cell) {
        return cell.getWorld().isChunkLoaded(cell.getX() >> 4, cell.getZ() >> 4)
                && scheduler.isOwnedByCurrentThread(cell.getLocation());
    }

    /** Removes the light block in {@code cell}, on the thread that owns it. */
    private void clear(Block cell) {
        Runnable clear = () -> {
            if (cell.getType() == Material.LIGHT) {
                cell.setType(Material.AIR, false);
            }
        };
        if (scheduler.isOwnedByCurrentThread(cell.getLocation())) {
            clear.run();
        } else {
            scheduler.runAt(cell.getLocation(), clear);
        }
    }

    private static void set(Block cell, int level) {
        if (cell.getBlockData() instanceof Light light && light.getLevel() == level) {
            return;
        }
        Light light = (Light) Material.LIGHT.createBlockData();
        light.setLevel(level);
        cell.setBlockData(light, false);
    }
}
