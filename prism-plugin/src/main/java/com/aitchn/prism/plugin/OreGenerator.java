package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.api.registry.Registry;
import com.aitchn.prism.core.worldgen.OreVeins;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Generates {@code prism:ore} veins into newly generated chunks. Runs inside the chunk load, on the thread that owns
 * the chunk; veins stay inside the chunk so no neighbour is loaded. Only vanilla blocks listed in {@code replace} are
 * replaced, so other plugins' blocks and Prism blocks are left alone.
 */
final class OreGenerator {

    /** A block this content generates, with one resolved vein spec. */
    record Target(PrismKey block, World.Environment environment, Set<Material> replace, OreVeins.Spec spec,
            long salt) {}

    /** Places one generated block; returns whether it was placed. */
    interface Placer {
        boolean place(Block block, BlockDefinition definition);
    }

    private final Registry<BlockDefinition> definitions;
    private final Logger log;
    private volatile List<Target> targets;
    /** New chunks seen and blocks placed, for the self test's report. */
    final java.util.concurrent.atomic.AtomicLong chunks = new java.util.concurrent.atomic.AtomicLong();
    final java.util.concurrent.atomic.AtomicLong placedBlocks = new java.util.concurrent.atomic.AtomicLong();

    OreGenerator(Registry<BlockDefinition> definitions, Logger log) {
        this.definitions = definitions;
        this.log = log;
    }

    /** Content changed: rebuild the target list on next use. */
    void contentChanged() {
        targets = null;
    }

    List<Target> targets() {
        List<Target> current = targets;
        if (current == null) {
            current = resolve();
            targets = current;
        }
        return current;
    }

    /** Generates every target of {@code chunk}'s dimension; returns the number of blocks placed. */
    int generate(Chunk chunk, Placer placer) {
        World world = chunk.getWorld();
        int placed = 0;
        for (Target target : targets()) {
            if (target.environment() != world.getEnvironment()) {
                continue;
            }
            BlockDefinition definition = definitions.get(target.block()).orElse(null);
            if (definition == null) {
                continue;
            }
            for (OreVeins.Position p : OreVeins.positions(target.spec(), world.getSeed(), target.salt(),
                    chunk.getX(), chunk.getZ(), world.getMinHeight(), world.getMaxHeight())) {
                Block block = chunk.getBlock(p.x(), p.y(), p.z());
                if (target.replace().contains(block.getType()) && placer.place(block, definition)) {
                    placed++;
                }
            }
        }
        chunks.incrementAndGet();
        placedBlocks.addAndGet(placed);
        return placed;
    }

    private List<Target> resolve() {
        List<Target> out = new ArrayList<>();
        for (PrismKey key : definitions.keys()) {
            BlockDefinition definition = definitions.get(key).orElse(null);
            if (definition == null) {
                continue;
            }
            List<PrismComponents.Ore.Vein> veins = definition.components().get(PrismComponents.ORE)
                    .map(PrismComponents.Ore::veins).orElse(List.of());
            for (int i = 0; i < veins.size(); i++) {
                PrismComponents.Ore.Vein vein = veins.get(i);
                Set<Material> replace = EnumSet.noneOf(Material.class);
                for (PrismKey block : vein.replace()) {
                    Material material = Material.matchMaterial(block.toString());
                    if (material == null || !material.isBlock()) {
                        log.warning("PRISM_ORE_UNKNOWN_BLOCK " + key + " replace " + block + " is not a vanilla block");
                    } else {
                        replace.add(material);
                    }
                }
                if (replace.isEmpty()) {
                    continue;
                }
                World.Environment environment = switch (vein.dimension()) {
                    case "nether" -> World.Environment.NETHER;
                    case "the_end" -> World.Environment.THE_END;
                    default -> World.Environment.NORMAL;
                };
                out.add(new Target(key, environment, replace, new OreVeins.Spec(vein.veinsPerChunk(), vein.rarity(),
                        vein.size(), vein.minY(), vein.maxY()), key.toString().hashCode() * 31L + i));
            }
        }
        log.info("PRISM_ORES_READY targets=" + out.size());
        return List.copyOf(out);
    }
}
