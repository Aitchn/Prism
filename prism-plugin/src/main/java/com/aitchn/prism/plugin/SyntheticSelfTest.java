package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.mod.ModContext;
import com.aitchn.prism.api.mod.PrismMod;
import com.aitchn.prism.core.block.Appearances;
import com.aitchn.prism.client.BedrockSelfCheck;
import com.aitchn.prism.client.ClientStates;
import com.aitchn.prism.core.bedrock.SyntheticBlock;
import com.aitchn.prism.core.bedrock.SyntheticIds;
import com.aitchn.prism.core.block.BlockLook;
import com.aitchn.prism.core.block.BlockPositions;
import com.aitchn.prism.core.content.MemoryContentSource;
import com.aitchn.prism.core.look.DisplayHeld;
import com.aitchn.prism.core.look.DisplayLighting;
import com.aitchn.prism.core.look.DisplayLook;
import com.aitchn.prism.core.look.DisplayParts;
import com.aitchn.prism.core.look.LookTable;
import com.aitchn.prism.core.mod.ModCandidate;
import com.aitchn.prism.core.mod.ModManager;
import com.aitchn.prism.core.mod.ModReport;
import com.aitchn.prism.pack.BedrockPackBuilder;
import com.aitchn.prism.pack.JavaPackBuilder;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Optional startup check of the look pool at scale, enabled with {@code -Dprism.selftest.synthetic=<count>} (the smoke
 * test passes 10000). Registers that many synthetic block definitions (solid, passable, forced to the display layer,
 * with other surfaces; every one with its own texture, so the pack has a model per look), then checks that every look
 * got a client representation, that the pack builds, twice, to the same bytes and without a warning about these blocks,
 * that the canonicalization table never maps a borrowed look to itself, and that blocks of both tiers placed in the
 * world read back with exactly the look the table promises. With one entity per block (ADR-0025) it also checks that
 * every display look is drawn by exactly one entity, and that the display layer, given a lattice of display blocks and
 * one block enclosed by stone, sends a viewer every exposed block, nothing for the enclosed one, and under a small
 * budget at most that many entities, the nearest ones ({@code PRISM_DISPLAY_SELFTEST_OK}). Off by default.
 */
final class SyntheticSelfTest implements Listener {

    private static final String NAMESPACE = "synth";
    private static final int SAMPLE = 200;

    private final int count;
    private final ModManager mods;
    private final Runnable contentChanged;
    private final PackManager packs;
    private final PrismBlockService blocks;
    private final DisplayLayer layer;
    private final PlatformScheduler scheduler;
    private final Logger log;

    SyntheticSelfTest(int count, ModManager mods, Runnable contentChanged, PackManager packs,
            PrismBlockService blocks, DisplayLayer layer, PlatformScheduler scheduler, Logger log) {
        this.count = count;
        this.mods = mods;
        this.contentChanged = contentChanged;
        this.packs = packs;
        this.blocks = blocks;
        this.layer = layer;
        this.scheduler = scheduler;
        this.log = log;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        try {
            MemoryContentSource source = content();
            ModReport report = mods.activate(new ModCandidate("prism-synthetic", NAMESPACE, "prism", source,
                    new PrismMod() {
                        @Override
                        public void onInitialize(@NotNull ModContext context) {}
                    }, log));
            if (!report.active() || report.blockCount() != count) {
                fail("registered " + report.blockCount() + " of " + count + " blocks: " + report.problems());
                return;
            }
            contentChanged.run();
            List<BlockDefinition> definitions = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                mods.blocks().get(new PrismKey(NAMESPACE, "b" + i)).ifPresent(definitions::add);
            }
            String problem = checkTable(definitions);
            if (problem == null) {
                problem = checkPack();
            }
            if (problem == null) {
                problem = checkBedrock(definitions);
            }
            if (problem == null) {
                problem = checkDisplayParts(definitions);
            }
            if (problem != null) {
                fail(problem);
                return;
            }
            World world = Bukkit.getWorlds().get(0);
            Location at = world.getSpawnLocation();
            scheduler.runAt(at, () -> {
                String failure;
                try {
                    failure = checkWorld(world, at, definitions);
                } catch (RuntimeException e) {
                    failure = "exception " + e;
                }
                if (failure != null) {
                    fail(failure);
                    return;
                }
                runDisplayLayer(world, at, definitions);
                LookTable table = packs.looks().table();
                log.info("PRISM_SYNTHETIC_SELFTEST_OK blocks=" + count + " looks=" + table.placements().size()
                        + " state=" + table.count(LookTable.Tier.STATE) + " display="
                        + table.count(LookTable.Tier.DISPLAY) + " unplaced=" + table.unplaced().size()
                        + " families=" + table.activeBlocks().size());
            });
        } catch (IOException | RuntimeException e) {
            fail("exception " + e);
        }
    }

    private void fail(String reason) {
        log.severe("PRISM_SYNTHETIC_SELFTEST_FAIL " + reason);
    }

    /** The kinds: solid, passable, forced display, forced borrowed state, glass-like and stone-like blocks. */
    private MemoryContentSource content() throws IOException {
        MemoryContentSource source = new MemoryContentSource();
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        for (int i = 0; i < count; i++) {
            StringBuilder yml = new StringBuilder("base: minecraft:barrier\n");
            switch (i % 10) {
                case 0 -> yml.append("components:\n  prism:passable: true\n");
                case 1, 2 -> yml.append("components:\n  prism:render: display\n");
                case 3 -> yml.append("components:\n  prism:surface:\n    sound: glass\n    occludes: false\n");
                case 4 -> yml.append("components:\n  prism:surface:\n    sound: stone\n");
                case 5 -> yml.append("components:\n  prism:render: state\n");
                default -> { }
            }
            source.put("prism/blocks/b" + i + ".yml", yml.toString());
            source.put("assets/" + NAMESPACE + "/textures/block/b" + i + ".png", png.toByteArray());
        }
        return source;
    }

    /** The carrier a block of {@code definition} drawn as {@code lookId} is stored with in this boot. */
    private static Carrier carrierOf(ClientLooks looks, BlockDefinition definition, String lookId) {
        Carrier carrier = looks.carrierFor(lookId, Appearances.passable(definition));
        return carrier != null ? carrier : Carrier.SOLID;
    }

    /** Every look has a representation, and no borrowed look is its own canonical sibling. */
    private String checkTable(List<BlockDefinition> definitions) {
        ClientLooks looks = packs.looks();
        int total = 0;
        for (BlockDefinition definition : definitions) {
            for (Appearances.Look look : Appearances.looks(definition)) {
                total++;
                BlockLook shown = looks.lookFor(carrierOf(looks, definition, look.id()), look.id());
                if (shown == null) {
                    return "no client representation for " + look.id();
                }
                LookTable.Placement placement = looks.table().placement(look.id());
                if (placement != null && placement.tier() == LookTable.Tier.DISPLAY && !shown.displayed()) {
                    return "display look without a display plan: " + look.id();
                }
                if (placement != null && placement.tier() == LookTable.Tier.STATE) {
                    int canonical = looks.canon().of(shown.look());
                    if (canonical == shown.look() || looks.canon().of(canonical) != canonical) {
                        return "borrowed state of " + look.id() + " is not canonicalized to a stable sibling";
                    }
                }
            }
        }
        return looks.table().unplaced().isEmpty() && total == count ? null
                : "unplaced=" + looks.table().unplaced().size() + " looks=" + total;
    }

    /** The pack builds twice to the same bytes, and says nothing about the synthetic blocks. */
    private String checkPack() {
        JavaPackBuilder.Result first = packs.buildNow();
        JavaPackBuilder.Result second = packs.buildNow();
        if (!first.sha1().equals(second.sha1())) {
            return "the pack differs between two builds of the same content";
        }
        Optional<String> warning = first.warnings().stream().filter(w -> w.contains(NAMESPACE + ":")).findFirst();
        return warning.map(w -> "pack warning " + w).orElse(null);
    }

    /**
     * Bedrock (ADR-0024): every look has a synthetic id and registration data (so all of them, at any count), the ids
     * are above every vanilla state and unique, and a chunk section carrying sample looks reads back through the packet
     * library's own encoder with the synthetic ids for a Geyser viewer and without them for a Java viewer, in a small
     * palette and in the global palette at the width Geyser would read.
     */
    private String checkBedrock(List<BlockDefinition> definitions) {
        BedrockBlocks bedrock = packs.bedrockBlocks();
        if (bedrock.mode() == BedrockBlocks.Mode.OFF) {
            log.info("PRISM_BEDROCK_SELFTEST_SKIPPED bedrock.synthetic-blocks is off");
            return null;
        }
        SyntheticIds.Plan plan = bedrock.planned();
        BedrockPackBuilder.Result result = packs.bedrockNow();
        Map<String, SyntheticBlock> byLook = new HashMap<>();
        Set<String> names = new HashSet<>();
        for (SyntheticBlock block : result.syntheticBlocks()) {
            byLook.put(block.lookId(), block);
            if (!names.add(block.name())) {
                return "bedrock block name used twice: " + block.name();
            }
        }
        int vanilla = ClientStates.stateCount();
        Set<Integer> used = new HashSet<>();
        int looksChecked = 0;
        int highest = 0;
        Map<Long, BlockLook> sample = new HashMap<>();
        ClientLooks looks = packs.looks();
        for (BlockDefinition definition : definitions) {
            for (Appearances.Look look : Appearances.looks(definition)) {
                looksChecked++;
                var id = plan.idOf(look.id());
                SyntheticBlock block = byLook.get(look.id());
                if (id.isEmpty() || block == null || block.javaId() != id.getAsInt()) {
                    return "no synthetic block for " + look.id();
                }
                if (id.getAsInt() < vanilla || !used.add(id.getAsInt()) || block.materials().isEmpty()) {
                    return "bad synthetic id or materials for " + look.id() + ": " + id.getAsInt();
                }
                if (block.passable() != Appearances.passable(definition)) {
                    return "collision of " + look.id() + " does not follow its physics class";
                }
                highest = Math.max(highest, id.getAsInt());
                BlockLook shown = looks.lookFor(carrierOf(looks, definition, look.id()), look.id());
                if (shown != null && sample.size() < SAMPLE) {
                    int i = sample.size();
                    sample.put(BlockPositions.pack(i & 15, 64 + (i >> 8), (i >> 4) & 15),
                            new BlockLook(shown.carrier(), shown.look(), shown.display(), id.getAsInt()));
                }
            }
        }
        if (looksChecked != count) {
            return "checked " + looksChecked + " looks of " + count + " blocks";
        }
        Optional<String> warning = result.warnings().stream()
                .filter(w -> w.contains("bedrock block " + NAMESPACE + ":")).findFirst();
        if (warning.isPresent()) {
            return "bedrock warning " + warning.get();
        }
        int bits = SyntheticIds.bitsFor(highest + 1);
        for (int filler : new int[] {0, 300}) {
            String problem = BedrockSelfCheck.check(sample, 4, filler, bits);
            if (problem != null) {
                return "bedrock rewrite (filler " + filler + "): " + problem;
            }
        }
        log.info("PRISM_BEDROCK_SELFTEST_OK looks=" + looksChecked + " synthetic=" + looksChecked
                + " total=" + result.syntheticBlocks().size() + " base=" + plan.base() + " bits=" + bits + " sample=" + sample.size());
        return null;
    }

    /** Both tiers and both carriers placed, read back and removed in the arena chunk. */
    private String checkWorld(World world, Location spawn, List<BlockDefinition> definitions) {
        ClientLooks looks = packs.looks();
        List<BlockDefinition> picked = new ArrayList<>();
        int states = 0;
        int displays = 0;
        int passable = 0;
        for (BlockDefinition definition : definitions) {
            LookTable.Placement placement = looks.table().placement(
                    new Appearances.Look(definition.key(), "", "NORTH").id());
            if (placement == null) {
                continue;
            }
            boolean display = placement.tier() == LookTable.Tier.DISPLAY;
            if (display ? displays < SAMPLE : states < SAMPLE) {
                picked.add(definition);
                states += display ? 0 : 1;
                displays += display ? 1 : 0;
            } else if (Appearances.passable(definition) && passable < 20) {
                picked.add(definition);
                passable++;
            }
        }
        int baseX = (spawn.getBlockX() >> 4 << 4);
        int baseZ = (spawn.getBlockZ() >> 4 << 4);
        int y = world.getMaxHeight() - 60;
        int placed = 0;
        for (int i = 0; i < picked.size(); i++) {
            BlockDefinition definition = picked.get(i);
            Block block = world.getBlockAt(baseX + i % 16, y + i / 256, baseZ + i / 16 % 16);
            if (!blocks.place(definition.key(), block.getLocation())) {
                return "cannot place " + definition.key();
            }
            placed++;
            String id = new Appearances.Look(definition.key(), "", "NORTH").id();
            BlockLook expected = looks.lookFor(carrierOf(looks, definition, id), id);
            BlockLook read = blocks.lookOf(block);
            if (!expected.equals(read)) {
                return "read back " + read + " for " + id + ", expected " + expected;
            }
            if (blocks.expectedLookId(block) != expected.look()) {
                return "index and table disagree about " + id;
            }
            if (!blocks.remove(block, false) || blocks.lookOf(block) != null) {
                return "cannot remove " + definition.key();
            }
        }
        return placed == picked.size() && placed > 0 ? null : "placed " + placed;
    }

    // ---------------------------------------------------------------- display layer (ADR-0025)

    /** With one entity per block, every display look is exactly one entity when exposed and none when enclosed. */
    private String checkDisplayParts(List<BlockDefinition> definitions) {
        if (layer.lighting() != DisplayLighting.BLOCK) {
            return null;
        }
        ClientLooks looks = packs.looks();
        int[] light = new int[6];
        java.util.Arrays.fill(light, 15);
        boolean[] open = new boolean[6];
        boolean[] closed = new boolean[6];
        java.util.Arrays.fill(closed, true);
        for (BlockDefinition definition : definitions) {
            for (Appearances.Look look : Appearances.looks(definition)) {
                LookTable.Placement placement = looks.table().placement(look.id());
                if (placement == null || placement.tier() != LookTable.Tier.DISPLAY) {
                    continue;
                }
                DisplayLook display = looks.displays().forLook(look.id());
                if (display == null) {
                    return "display look without a display plan: " + look.id();
                }
                int exposed = DisplayParts.compute(display, open, light, DisplayLighting.BLOCK).size();
                int enclosed = DisplayParts.compute(display, closed, light, DisplayLighting.BLOCK).size();
                if (exposed != 1 || enclosed != 0) {
                    return look.id() + " is drawn by " + exposed + " entities exposed and " + enclosed + " enclosed";
                }
            }
        }
        return null;
    }

    /**
     * Places a sparse lattice of display blocks (no block hides another) and one display block enclosed by stone in the
     * spawn chunk, asks the display layer what a viewer at the chunk's corner would get, and removes everything again.
     */
    private void runDisplayLayer(World world, Location spawn, List<BlockDefinition> definitions) {
        if (layer.lighting() != DisplayLighting.BLOCK) {
            log.info("PRISM_DISPLAY_SELFTEST_SKIPPED blocks.display.lighting is faces");
            return;
        }
        ClientLooks looks = packs.looks();
        BlockDefinition picked = null;
        for (BlockDefinition definition : definitions) {
            String id = new Appearances.Look(definition.key(), "", "NORTH").id();
            if (!Appearances.passable(definition) && carrierOf(looks, definition, id) == Carrier.DISPLAY) {
                picked = definition;
                break;
            }
        }
        if (picked == null) {
            log.severe("PRISM_DISPLAY_SELFTEST_FAIL no solid display look among the synthetic blocks");
            return;
        }
        int chunkX = spawn.getBlockX() >> 4;
        int chunkZ = spawn.getBlockZ() >> 4;
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int baseY = world.getMaxHeight() - 120;
        List<Block> placed = new ArrayList<>();
        List<Block> stone = new ArrayList<>();
        Set<Integer> lattice = new HashSet<>();
        String failure = null;
        try {
            for (int dy = 0; dy < 8 && failure == null; dy += 2) {
                for (int dx = 0; dx < 16 && failure == null; dx += 2) {
                    for (int dz = 0; dz < 16; dz += 2) {
                        Block block = world.getBlockAt(baseX + dx, baseY + dy, baseZ + dz);
                        if (!blocks.place(picked.key(), block.getLocation())) {
                            failure = "cannot place " + picked.key() + " at " + block.getX() + "," + block.getY() + ","
                                    + block.getZ();
                            break;
                        }
                        placed.add(block);
                        lattice.add(DisplayHeld.key(dx, baseY + dy, dz));
                    }
                }
            }
            // All coordinates odd: none of its neighbours is a lattice position, so the stone touches no lattice block.
            Block enclosed = world.getBlockAt(baseX + 9, baseY + 9, baseZ + 9);
            if (failure == null) {
                for (int dir = 0; dir < 6; dir++) {
                    int[] step = com.aitchn.prism.core.look.Dir.offset(dir);
                    Block side = enclosed.getRelative(step[0], step[1], step[2]);
                    side.setType(org.bukkit.Material.STONE, false);
                    stone.add(side);
                }
                if (blocks.place(picked.key(), enclosed.getLocation())) {
                    placed.add(enclosed);
                } else {
                    failure = "cannot place the enclosed block";
                }
            }
            if (failure == null) {
                failure = checkLayer(world, chunkX, chunkZ, baseX, baseY, baseZ, lattice,
                        DisplayHeld.key(9, baseY + 9, 9));
            }
        } catch (RuntimeException e) {
            failure = "exception " + e;
        } finally {
            placed.forEach(block -> blocks.remove(block, false));
            stone.forEach(block -> block.setType(org.bukkit.Material.AIR, false));
        }
        if (failure != null) {
            log.severe("PRISM_DISPLAY_SELFTEST_FAIL " + failure);
        }
    }

    private String checkLayer(World world, int chunkX, int chunkZ, int viewerX, int viewerY, int viewerZ,
            Set<Integer> lattice, int enclosedKey) {
        int[] xs = {chunkX};
        int[] zs = {chunkZ};
        DisplayLayer.Simulation all = layer.simulate(world, viewerX, viewerY, viewerZ, Integer.MAX_VALUE / 2, xs, zs);
        int exposed = 0;
        for (int b = 0; b < all.keys()[0].length; b++) {
            int key = all.keys()[0][b];
            int weight = all.weights()[0][b];
            boolean sent = all.sent()[0][b];
            if (key == enclosedKey) {
                if (weight != 0) {
                    return "the enclosed block needs " + weight + " entities";
                }
            } else if (lattice.contains(key)) {
                if (weight != 1 || !sent) {
                    return "lattice block " + key + " needs " + weight + " entities, sent=" + sent;
                }
                exposed++;
            }
        }
        if (exposed != lattice.size()) {
            return "the layer saw " + exposed + " of " + lattice.size() + " lattice blocks";
        }
        int budget = lattice.size() / 3;
        DisplayLayer.Simulation cut = layer.simulate(world, viewerX, viewerY, viewerZ, budget, xs, zs);
        long farthestSent = -1;
        long nearestLeft = Long.MAX_VALUE;
        for (int b = 0; b < cut.keys()[0].length; b++) {
            int key = cut.keys()[0][b];
            if (cut.weights()[0][b] == 0) {
                continue;
            }
            long distance = com.aitchn.prism.core.look.DisplayBudget.blockDistanceSq(viewerX, viewerY, viewerZ,
                    (chunkX << 4) + DisplayHeld.keyX(key), DisplayHeld.keyY(key), (chunkZ << 4) + DisplayHeld.keyZ(key));
            if (cut.sent()[0][b]) {
                farthestSent = Math.max(farthestSent, distance);
            } else {
                nearestLeft = Math.min(nearestLeft, distance);
            }
        }
        if (cut.entities() != budget) {
            return "budget " + budget + " sent " + cut.entities() + " entities";
        }
        if (farthestSent > nearestLeft) {
            return "budget " + budget + " sent a block farther than one it left out";
        }
        log.info("PRISM_DISPLAY_SELFTEST_OK lattice=" + lattice.size() + " entities=" + exposed
                + " enclosed=0 budget=" + budget + " sent=" + cut.entities());
        return null;
    }
}
