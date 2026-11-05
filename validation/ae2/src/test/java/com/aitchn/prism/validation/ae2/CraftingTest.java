package com.aitchn.prism.validation.ae2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.validation.ae2.crafting.CraftingJob;
import com.aitchn.prism.validation.ae2.crafting.CraftingPlan;
import com.aitchn.prism.validation.ae2.crafting.CraftingPlanner;
import com.aitchn.prism.validation.ae2.crafting.Pattern;
import com.aitchn.prism.validation.ae2.storage.ItemKey;
import com.aitchn.prism.validation.ae2.storage.NetworkStorage;
import com.aitchn.prism.validation.ae2.storage.StorageCell;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CraftingTest {

    private static final ItemKey COBBLE = new ItemKey("cobble");
    private static final ItemKey STICK = new ItemKey("stick");
    private static final ItemKey PLANK = new ItemKey("plank");
    private static final ItemKey LOG = new ItemKey("log");
    private static final ItemKey PICK = new ItemKey("pickaxe");

    private static final Pattern PLANKS = new Pattern("planks", Map.of(LOG, 1L), PLANK, 4);
    private static final Pattern STICKS = new Pattern("sticks", Map.of(PLANK, 2L), STICK, 4);
    private static final Pattern PICKAXE = new Pattern("pickaxe", Map.of(COBBLE, 3L, STICK, 2L), PICK, 1);

    private static final List<Pattern> ALL = List.of(PLANKS, STICKS, PICKAXE);

    @Test
    void usesStockBeforeCrafting() {
        CraftingPlan plan = CraftingPlanner.plan(PICK, 1, Map.of(COBBLE, 3L, STICK, 2L), ALL);
        assertTrue(plan.feasible());
        assertEquals(1, plan.steps().size());
        assertEquals(Map.of(COBBLE, 3L, STICK, 2L), plan.extractFromStorage());
    }

    @Test
    void craftsIntermediatesFromRawMaterial() {
        CraftingPlan plan = CraftingPlanner.plan(PICK, 2, Map.of(COBBLE, 6L, LOG, 1L), ALL);
        assertTrue(plan.feasible(), plan.missing().toString());
        // 4 sticks needed -> 1 craft of sticks (yields 4) -> 2 planks -> 1 craft of planks (yields 4)
        Map<String, Long> crafts = new java.util.TreeMap<>();
        plan.steps().forEach(s -> crafts.put(s.pattern().id(), s.crafts()));
        assertEquals(Map.of("pickaxe", 2L, "sticks", 1L, "planks", 1L), crafts);
        assertEquals(Map.of(COBBLE, 6L, LOG, 1L), plan.extractFromStorage());
    }

    @Test
    void reportsMissingMaterials() {
        CraftingPlan plan = CraftingPlanner.plan(PICK, 1, Map.of(COBBLE, 1L), ALL);
        assertFalse(plan.feasible());
        assertEquals(2L, plan.missing().get(COBBLE));
        assertEquals(1L, plan.missing().get(LOG));
    }

    @Test
    void surplusIsReusedWithinOnePlan() {
        // Two pickaxes need 4 sticks; the single sticks craft yields exactly 4, nothing is crafted twice.
        CraftingPlan plan = CraftingPlanner.plan(PICK, 2, Map.of(COBBLE, 6L, PLANK, 2L), ALL);
        assertEquals(1L, plan.steps().stream().filter(s -> s.pattern() == STICKS).findFirst().orElseThrow().crafts());
    }

    @Test
    void detectsCycles() {
        Pattern a = new Pattern("a", Map.of(new ItemKey("y"), 1L), new ItemKey("x"), 1);
        Pattern b = new Pattern("b", Map.of(new ItemKey("x"), 1L), new ItemKey("y"), 1);
        CraftingPlan plan = CraftingPlanner.plan(new ItemKey("x"), 1, Map.of(), List.of(a, b));
        assertTrue(plan.cycle());
        assertFalse(plan.feasible());
    }

    @Test
    void jobRunsAndDeliversToTheNetwork() {
        StorageCell cell = new StorageCell(UUID.randomUUID(), StorageCell.Tier.K4);
        NetworkStorage network = new NetworkStorage(List.of(cell));
        network.insert(COBBLE, 6);
        network.insert(LOG, 1);
        CraftingPlan plan = CraftingPlanner.plan(PICK, 2, network.snapshot(), ALL);
        CraftingJob job = new CraftingJob(plan, network, 1, 2);
        assertTrue(job.start());
        assertEquals(0, network.count(COBBLE));
        int ticks = 0;
        while (job.status() != CraftingJob.Status.DONE && ticks++ < 200) {
            job.tick();
        }
        assertEquals(CraftingJob.Status.DONE, job.status());
        assertEquals(2, network.count(PICK));
        assertEquals(0, network.count(STICK)); // 1 sticks craft makes 4, two pickaxes use all 4
        assertEquals(2, network.count(PLANK)); // 1 planks craft makes 4, sticks used 2
    }

    @Test
    void coprocessorsSpeedTheJobUp() {
        long slow = ticksFor(1);
        long fast = ticksFor(4);
        assertTrue(fast < slow, "parallelism 4 took " + fast + " ticks vs " + slow);
    }

    private static long ticksFor(int parallelism) {
        NetworkStorage network = new NetworkStorage(List.of(new StorageCell(UUID.randomUUID(), StorageCell.Tier.K16)));
        network.insert(COBBLE, 30);
        network.insert(STICK, 20);
        CraftingJob job = new CraftingJob(CraftingPlanner.plan(PICK, 10, network.snapshot(), ALL), network,
                parallelism, 5);
        job.start();
        long ticks = 0;
        while (job.status() != CraftingJob.Status.DONE && ticks < 1000) {
            job.tick();
            ticks++;
        }
        return ticks;
    }

    @Test
    void startFailsAtomicallyWhenStockVanished() {
        NetworkStorage network = new NetworkStorage(List.of(new StorageCell(UUID.randomUUID(), StorageCell.Tier.K1)));
        network.insert(COBBLE, 3);
        network.insert(STICK, 2);
        CraftingPlan plan = CraftingPlanner.plan(PICK, 1, network.snapshot(), ALL);
        network.extract(STICK, 2);
        CraftingJob job = new CraftingJob(plan, network, 1, 1);
        assertFalse(job.start());
        assertEquals(3, network.count(COBBLE));
    }

    @Test
    void cancelReturnsTheBuffer() {
        NetworkStorage network = new NetworkStorage(List.of(new StorageCell(UUID.randomUUID(), StorageCell.Tier.K1)));
        network.insert(COBBLE, 3);
        network.insert(STICK, 2);
        CraftingJob job = new CraftingJob(CraftingPlanner.plan(PICK, 1, network.snapshot(), ALL), network, 1, 10);
        job.start();
        job.cancel();
        assertEquals(CraftingJob.Status.CANCELLED, job.status());
        assertEquals(3, network.count(COBBLE));
        assertEquals(2, network.count(STICK));
    }

    @Test
    void patternCodecRoundTripsOneAndMany() {
        var decoded = com.aitchn.prism.validation.ae2.crafting.PatternCodec.decode(
                com.aitchn.prism.validation.ae2.crafting.PatternCodec.encode(PICKAXE));
        assertEquals(PICKAXE, decoded.orElseThrow());
        var all = com.aitchn.prism.validation.ae2.crafting.PatternCodec.decodeAll(
                com.aitchn.prism.validation.ae2.crafting.PatternCodec.encodeAll(ALL));
        assertEquals(3, all.size());
        assertTrue(all.containsAll(ALL));
        assertTrue(com.aitchn.prism.validation.ae2.crafting.PatternCodec.decode("junk").isEmpty());
    }
}
