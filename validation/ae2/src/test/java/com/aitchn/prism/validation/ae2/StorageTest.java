package com.aitchn.prism.validation.ae2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aitchn.prism.validation.ae2.storage.ItemKey;
import com.aitchn.prism.validation.ae2.storage.NetworkStorage;
import com.aitchn.prism.validation.ae2.storage.StorageCell;
import com.aitchn.prism.validation.ae2.storage.StorageCell.Tier;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StorageTest {

    private static final ItemKey IRON = new ItemKey("minecraft:iron_ingot");
    private static final ItemKey GOLD = new ItemKey("minecraft:gold_ingot");

    @Test
    void oneKCellHoldsAeTwoCapacityForOneType() {
        StorageCell cell = new StorageCell(UUID.randomUUID(), Tier.K1);
        // (1024 - 8) bytes * 8 items per byte
        assertEquals(8128, cell.insert(IRON, 1_000_000));
        assertEquals(1024, cell.usedBytes());
        assertEquals(0, cell.insert(GOLD, 1));
    }

    @Test
    void secondTypeCostsAnotherTypeFee() {
        StorageCell cell = new StorageCell(UUID.randomUUID(), Tier.K4);
        cell.insert(IRON, 800);
        cell.insert(GOLD, 800);
        assertEquals(2 * 32 + 200, cell.usedBytes());
        assertEquals(2, cell.types());
    }

    @Test
    void cellStopsAtSixtyThreeTypes() {
        StorageCell cell = new StorageCell(UUID.randomUUID(), Tier.K256);
        for (int i = 0; i < 70; i++) {
            cell.insert(new ItemKey("test:item" + i), 1);
        }
        assertEquals(StorageCell.MAX_TYPES, cell.types());
    }

    @Test
    void extractRemovesTypeWhenEmpty() {
        StorageCell cell = new StorageCell(UUID.randomUUID(), Tier.K1);
        cell.insert(IRON, 10);
        assertEquals(4, cell.extract(IRON, 4));
        assertEquals(6, cell.extract(IRON, 100));
        assertEquals(0, cell.types());
        assertEquals(0, cell.usedBytes());
    }

    @Test
    void serializationRoundTrips() {
        StorageCell cell = new StorageCell(UUID.randomUUID(), Tier.K16);
        cell.insert(IRON, 123);
        cell.insert(GOLD, 45);
        StorageCell copy = StorageCell.load(cell.id(), Tier.K16, cell.serialize());
        assertEquals(cell.contents(), copy.contents());
        assertEquals(cell.usedBytes(), copy.usedBytes());
    }

    @Test
    void corruptLinesLoseOnlyTheirItem() {
        StorageCell cell = StorageCell.load(UUID.randomUUID(), Tier.K1, "garbage\nminecraft:stick\tabc\nok:item\t5\n");
        assertEquals(java.util.Map.of(new ItemKey("ok:item"), 5L), cell.contents());
    }

    @Test
    void networkPrefersCellsThatAlreadyHoldTheItemThenPriority() {
        StorageCell a = new StorageCell(new UUID(0, 1), Tier.K1);
        StorageCell b = new StorageCell(new UUID(0, 2), Tier.K1);
        b.priority(10);
        NetworkStorage network = new NetworkStorage(List.of(a, b));
        network.insert(IRON, 10);
        assertEquals(10, b.count(IRON));
        a.insert(GOLD, 1);
        network.insert(GOLD, 5);
        assertEquals(6, a.count(GOLD));
    }

    @Test
    void networkOverflowsIntoTheNextCellAndTotalsSnapshots() {
        StorageCell a = new StorageCell(new UUID(0, 1), Tier.K1);
        StorageCell b = new StorageCell(new UUID(0, 2), Tier.K1);
        NetworkStorage network = new NetworkStorage(List.of(a, b));
        assertEquals(16_000, network.insert(IRON, 16_000));
        assertEquals(16_000, network.count(IRON));
        assertEquals(16_000L, network.snapshot().get(IRON));
        assertEquals(500, network.extract(IRON, 500));
        assertEquals(15_500, network.count(IRON));
    }
}
