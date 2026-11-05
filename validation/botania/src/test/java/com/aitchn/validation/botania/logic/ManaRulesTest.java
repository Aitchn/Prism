package com.aitchn.validation.botania.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ManaRulesTest {

    private static Pos at(int x, int y, int z) {
        return new Pos("world", x, y, z);
    }

    @Test
    void spreaderBurstTravelsAlongTheAimAndFillsAPool() {
        var network = new ManaNetwork();
        var world = new FakeWorld();
        // yaw -90 looks towards +x
        var spreader = new Spreader(at(0, 64, 0), Spreader.BURST_MANA, -90f, 0f);
        var pool = new Pool(at(8, 64, 0), 0);
        network.put(spreader);
        network.put(pool);

        var burst = spreader.fire().orElseThrow();
        var result = burst.advance(world, network);

        assertEquals(Burst.Outcome.HIT, result.outcome());
        assertEquals(at(8, 64, 0), result.at());
        assertEquals(Spreader.BURST_MANA, pool.mana());
        assertEquals(0, spreader.mana());
    }

    @Test
    void solidBlockAbsorbsTheBurstAndRangeLimitsIt() {
        var network = new ManaNetwork();
        var world = new FakeWorld();
        var spreader = new Spreader(at(0, 64, 0), 1000, -90f, 0f);
        network.put(spreader);
        world.solid.add(at(3, 64, 0));
        assertEquals(Burst.Outcome.ABSORBED, spreader.fire().orElseThrow().advance(world, network).outcome());

        world.solid.clear();
        var far = new Pool(at(40, 64, 0), 0);
        network.put(far);
        for (int i = 0; i < Spreader.COOLDOWN_TICKS; i++) {
            spreader.tick(world, network);
        }
        assertEquals(Burst.Outcome.EXPIRED, spreader.fire().orElseThrow().advance(world, network).outcome());
        assertEquals(0, far.mana());
    }

    @Test
    void spreaderNeedsManaAndRespectsCooldown() {
        var spreader = new Spreader(at(0, 0, 0), Spreader.BURST_MANA - 1, 0f, 0f);
        assertTrue(spreader.fire().isEmpty());
        spreader.receive(1);
        assertTrue(spreader.fire().isPresent());
        spreader.receive(500);
        assertTrue(spreader.fire().isEmpty(), "cooldown");
    }

    @Test
    void burstHandsOverWhenItLeavesTheOwnedRegion() {
        var network = new ManaNetwork();
        var world = new FakeWorld();
        var pool = new Pool(at(10, 64, 0), 0);
        network.put(pool);
        var burst = new Burst(at(0, 64, 0), 1, 0, 0, 100, 16);

        world.ownedMaxX = 4;
        var first = burst.advance(world, network);
        assertEquals(Burst.Outcome.NEED_REGION, first.outcome());
        assertEquals(5, first.at().x());

        world.ownedMaxX = Integer.MAX_VALUE;
        var second = burst.advance(world, network);
        assertEquals(Burst.Outcome.HIT, second.outcome());
        assertEquals(100, pool.mana());
    }

    @Test
    void daybloomOnlyGeneratesInDaylightAndPushesToItsPool() {
        var network = new ManaNetwork();
        var world = new FakeWorld();
        var pool = new Pool(at(2, 64, 0), 0);
        var flower = new Flower(at(0, 64, 0), FlowerType.DAYBLOOM, 0, null);
        network.put(pool);
        network.put(flower);
        assertTrue(flower.bind(pool.pos()));

        world.daylight = false;
        flower.tick(world, network);
        assertEquals(0, pool.mana());

        world.daylight = true;
        for (int i = 0; i < 50; i++) {
            flower.tick(world, network);
        }
        assertEquals(50, pool.mana());
    }

    @Test
    void flowerCannotBindFarAwayAndKeepsManaWhenPoolIsFull() {
        var flower = new Flower(at(0, 64, 0), FlowerType.DAYBLOOM, 0, null);
        assertFalse(flower.bind(at(20, 64, 0)));
        assertFalse(flower.bind(new Pos("nether", 1, 64, 0)));
        assertTrue(flower.bound().isEmpty());

        var network = new ManaNetwork();
        var full = new Pool(at(1, 64, 0), Pool.CAPACITY);
        network.put(full);
        network.put(flower);
        flower.bind(full.pos());
        flower.tick(new FakeWorld(), network);
        assertEquals(1, flower.buffer());
    }

    @Test
    void endoflameBurnsFuelForTheBurnTime() {
        var network = new ManaNetwork();
        var world = new FakeWorld();
        var pool = new Pool(at(1, 64, 0), 0);
        var flower = new Flower(at(0, 64, 0), FlowerType.ENDOFLAME, 0, null);
        network.put(pool);
        network.put(flower);
        flower.bind(pool.pos());
        world.dropped.add("minecraft:stick");
        world.dropped.add("minecraft:diamond");

        for (int i = 0; i < 200; i++) {
            flower.tick(world, network);
        }
        assertEquals(100 * 3, pool.mana(), "a stick burns 100 ticks at 3 mana per tick");
        assertEquals(List.of("minecraft:diamond"), world.dropped, "only fuel is consumed");
    }

    @Test
    void pureDaisyConvertsOneNeighbourEveryCycle() {
        var network = new ManaNetwork();
        var world = new FakeWorld();
        var flower = new Flower(at(0, 64, 0), FlowerType.PURE_DAISY, 0, null);
        world.blocks.put(at(1, 64, 0), "minecraft:stone");
        world.blocks.put(at(0, 64, 1), "minecraft:oak_log");
        world.blocks.put(at(-1, 64, 0), "minecraft:dirt");

        for (int i = 0; i < Flower.DAISY_TICKS - 1; i++) {
            flower.tick(world, network);
        }
        assertEquals("minecraft:stone", world.blockId(at(1, 64, 0)));
        flower.tick(world, network);
        long converted = world.blocks.values().stream().filter(id -> id.startsWith("botania:")).count();
        assertEquals(1, converted);
        for (int i = 0; i < Flower.DAISY_TICKS; i++) {
            flower.tick(world, network);
        }
        assertEquals("botania:livingrock", world.blockId(at(1, 64, 0)));
        assertEquals("botania:livingwood", world.blockId(at(0, 64, 1)));
        assertEquals("minecraft:dirt", world.blockId(at(-1, 64, 0)));
    }

    @Test
    void poolInfusesDroppedIronIntoManasteelWhenItHasEnoughMana() {
        var network = new ManaNetwork();
        var world = new FakeWorld();
        var pool = new Pool(at(0, 64, 0), 2999);
        world.dropped.add("minecraft:iron_ingot");
        for (int i = 0; i < 20; i++) {
            pool.tick(world, network);
        }
        assertTrue(world.spawned.isEmpty());
        pool.receive(1);
        for (int i = 0; i < 20; i++) {
            pool.tick(world, network);
        }
        assertEquals(List.of("botania:manasteel_ingot"), world.spawned);
        assertEquals(0, pool.mana());
        assertTrue(world.dropped.isEmpty());
    }

    @Test
    void apothecaryNeedsWaterPetalsAndSeeds() {
        var apothecary = new Apothecary(at(0, 64, 0), false, List.of());
        assertEquals(Apothecary.Kind.REJECTED, apothecary.use(Recipes.petalItem("white")).kind());
        assertEquals(Apothecary.Kind.FILLED, apothecary.use(Apothecary.WATER_BUCKET).kind());
        for (int i = 0; i < 4; i++) {
            assertEquals(Apothecary.Kind.ADDED, apothecary.use(Recipes.petalItem("white")).kind());
        }
        assertEquals(Apothecary.Kind.REJECTED, apothecary.use("minecraft:dirt").kind());
        var crafted = apothecary.use(Apothecary.TRIGGER);
        assertEquals(Apothecary.Kind.CRAFTED, crafted.kind());
        assertEquals("botania:pure_daisy", crafted.item());
        assertFalse(apothecary.water());
        assertTrue(apothecary.ingredients().isEmpty());
    }

    @Test
    void apothecaryRecipeIgnoresOrderAndFailsOnMismatch() {
        var apothecary = new Apothecary(at(0, 64, 0), true, List.of());
        for (String dye : List.of("light_blue", "yellow", "yellow")) {
            apothecary.use(Recipes.petalItem(dye));
        }
        assertEquals("botania:daybloom", apothecary.use(Apothecary.TRIGGER).item());

        apothecary.use(Apothecary.WATER_BUCKET);
        apothecary.use(Recipes.petalItem("red"));
        assertEquals(Apothecary.Kind.NO_MATCH, apothecary.use(Apothecary.TRIGGER).kind());
        var removed = apothecary.use(null);
        assertEquals(Apothecary.Kind.REMOVED, removed.kind());
        assertEquals("botania:petal_red", removed.item());
        assertNull(Recipes.petal(List.of()));
    }

    @Test
    void everyBlockEntitySurvivesSerialization() {
        List<BlockEntity> all = List.of(
                new Pool(at(1, 2, 3), 1234),
                new Spreader(at(-4, 5, 6), 321, 90f, -12.5f),
                new Flower(at(7, 8, 9), FlowerType.ENDOFLAME, 42, at(10, 8, 9)),
                new Flower(at(7, 8, 9), FlowerType.DAYBLOOM, 0, null),
                new Apothecary(at(0, 0, 0), true, List.of("botania:petal_red", "botania:petal_white")),
                new Apothecary(at(0, 0, 0), false, List.of()));
        for (BlockEntity original : all) {
            BlockEntity copy = BlockEntity.deserialize(original.pos(), original.serialize());
            assertEquals(original.serialize(), copy.serialize());
            assertEquals(original.getClass(), copy.getClass());
        }
    }

    @Test
    void endoflameKeepsItsRemainingBurnTimeAcrossSaveAndLoad() {
        var flower = new Flower(at(0, 64, 0), FlowerType.ENDOFLAME, 0, null);
        var world = new FakeWorld();
        world.dropped.add("minecraft:coal");
        flower.tick(world, new ManaNetwork());
        assertTrue(flower.serialize().endsWith(";1599"), flower.serialize());

        var copy = BlockEntity.deserialize(flower.pos(), flower.serialize());
        assertEquals(flower.serialize(), copy.serialize());
    }

    @Test
    void storeKeysGroupByChunk() {
        assertEquals("be/world/0,0/", at(15, 64, 15).chunkPrefix());
        assertEquals("be/world/-1,1/", at(-1, 64, 16).chunkPrefix());
        assertEquals("be/world/0,0/3,64,5", at(3, 64, 5).storeKey());
        assertEquals(at(3, 64, 5), Pos.parseCoords("world", "3,64,5"));
    }

    @Test
    void wandSelectsFlowerThenBindsToCollector() {
        var network = new ManaNetwork();
        var wand = new WandBinding();
        var flower = new Flower(at(0, 64, 0), FlowerType.DAYBLOOM, 0, null);
        var pool = new Pool(at(3, 64, 0), 0);
        var far = new Pool(at(30, 64, 0), 0);
        network.put(flower);
        network.put(pool);
        network.put(far);

        assertEquals(WandBinding.Result.NOTHING, wand.click("alex", pool.pos(), network));
        assertEquals(WandBinding.Result.SELECTED, wand.click("alex", flower.pos(), network));
        assertEquals(WandBinding.Result.OUT_OF_RANGE, wand.click("alex", far.pos(), network));
        assertEquals(WandBinding.Result.BOUND, wand.click("alex", pool.pos(), network));
        assertEquals(pool.pos(), flower.bound().orElseThrow());
        assertTrue(wand.selected("alex").isEmpty());
    }
}
