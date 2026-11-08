package com.aitchn.prism.validation.ae2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.validation.ae2.grid.GridCalculator;
import com.aitchn.prism.validation.ae2.grid.GridNetwork;
import com.aitchn.prism.validation.ae2.grid.GridNetwork.DeviceState;
import com.aitchn.prism.validation.ae2.grid.GridPos;
import com.aitchn.prism.validation.ae2.grid.NodeKind;
import com.aitchn.prism.validation.ae2.grid.PowerState;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GridCalculatorTest {

    private static GridPos at(int x, int y, int z) {
        return new GridPos("world", x, y, z);
    }

    /** A line of cable along +x starting next to a controller at the origin. */
    private static Map<GridPos, NodeKind> line(NodeKind conduit, int length) {
        Map<GridPos, NodeKind> nodes = new HashMap<>();
        nodes.put(at(0, 0, 0), NodeKind.CONTROLLER);
        for (int i = 1; i <= length; i++) {
            nodes.put(at(i, 0, 0), conduit);
        }
        return nodes;
    }

    @Test
    void deviceNextToCableOnControllerGetsAChannel() {
        Map<GridPos, NodeKind> nodes = line(NodeKind.CABLE, 3);
        nodes.put(at(2, 1, 0), NodeKind.TERMINAL);
        List<GridNetwork> networks = GridCalculator.compute(nodes);
        assertEquals(1, networks.size());
        GridNetwork network = networks.get(0);
        assertEquals(GridNetwork.Status.ONLINE, network.status());
        assertTrue(network.isOnline(at(2, 1, 0)));
        assertEquals(1, network.channelsUsed().get(at(2, 0, 0)));
        assertEquals(1, network.channelsUsed().get(at(1, 0, 0)));
    }

    @Test
    void withoutControllerNothingIsOnline() {
        Map<GridPos, NodeKind> nodes = new HashMap<>();
        nodes.put(at(0, 0, 0), NodeKind.CABLE);
        nodes.put(at(0, 1, 0), NodeKind.TERMINAL);
        GridNetwork network = GridCalculator.compute(nodes).get(0);
        assertEquals(GridNetwork.Status.NO_CONTROLLER, network.status());
        assertEquals(DeviceState.NO_CONTROLLER, network.states().get(at(0, 1, 0)));
    }

    @Test
    void aSmartCableCarriesEightChannelsAndADenseCableThirtyTwo() {
        for (var tc : List.of(new Object[] {NodeKind.CABLE, 8}, new Object[] {NodeKind.DENSE_CABLE, 32})) {
            NodeKind conduit = (NodeKind) tc[0];
            int capacity = (int) tc[1];
            Map<GridPos, NodeKind> trunk = line(conduit, capacity + 2);
            int devices = 0;
            for (int i = 1; i <= capacity + 2; i++) {
                trunk.put(at(i, 1, 0), NodeKind.TERMINAL);
                devices++;
            }
            GridNetwork network = GridCalculator.compute(trunk).get(0);
            long online = network.states().values().stream().filter(s -> s == DeviceState.ONLINE).count();
            assertEquals(capacity, online, conduit + " should limit the trunk to " + capacity + " channels of "
                    + devices + " devices");
            assertEquals(capacity, network.channelsUsed().get(at(1, 0, 0)));
        }
    }

    @Test
    void twoSeparateControllersConflict() {
        Map<GridPos, NodeKind> nodes = new HashMap<>();
        nodes.put(at(0, 0, 0), NodeKind.CONTROLLER);
        nodes.put(at(1, 0, 0), NodeKind.CABLE);
        nodes.put(at(2, 0, 0), NodeKind.CONTROLLER);
        nodes.put(at(1, 1, 0), NodeKind.DRIVE);
        GridNetwork network = GridCalculator.compute(nodes).get(0);
        assertEquals(GridNetwork.Status.CONTROLLER_CONFLICT, network.status());
        assertEquals(DeviceState.CONTROLLER_CONFLICT, network.states().get(at(1, 1, 0)));
    }

    @Test
    void touchingControllersFormOneController() {
        Map<GridPos, NodeKind> nodes = new HashMap<>();
        nodes.put(at(0, 0, 0), NodeKind.CONTROLLER);
        nodes.put(at(1, 0, 0), NodeKind.CONTROLLER);
        nodes.put(at(1, 1, 0), NodeKind.DRIVE);
        assertEquals(GridNetwork.Status.ONLINE, GridCalculator.compute(nodes).get(0).status());
    }

    @Test
    void devicesDoNotRelayChannels() {
        Map<GridPos, NodeKind> nodes = line(NodeKind.CABLE, 1);
        nodes.put(at(1, 1, 0), NodeKind.DRIVE);
        nodes.put(at(1, 2, 0), NodeKind.DRIVE);
        GridNetwork network = GridCalculator.compute(nodes).get(0);
        assertTrue(network.isOnline(at(1, 1, 0)));
        assertFalse(network.isOnline(at(1, 2, 0)));
    }

    @Test
    void separateGroupsAreSeparateNetworks() {
        Map<GridPos, NodeKind> nodes = line(NodeKind.CABLE, 1);
        nodes.put(at(10, 0, 0), NodeKind.CABLE);
        assertEquals(2, GridCalculator.compute(nodes).size());
    }

    @Test
    void craftingBlocksFormAClusterWithBytesAndParallelism() {
        Map<GridPos, NodeKind> nodes = line(NodeKind.CABLE, 1);
        nodes.put(at(1, 1, 0), NodeKind.CRAFTING_STORAGE_4K);
        nodes.put(at(1, 2, 0), NodeKind.CRAFTING_STORAGE_1K);
        nodes.put(at(1, 3, 0), NodeKind.CRAFTING_COPROCESSOR);
        nodes.put(at(5, 5, 5), NodeKind.CRAFTING_COPROCESSOR);
        List<GridNetwork> networks = GridCalculator.compute(nodes);
        GridNetwork main = networks.stream().filter(n -> n.kinds().containsKey(at(0, 0, 0))).findFirst().orElseThrow();
        assertEquals(1, main.clusters().size());
        assertEquals(4096 + 1024, main.clusters().get(0).bytes());
        assertEquals(2, main.clusters().get(0).parallelism());
        GridNetwork lonely = networks.stream().filter(n -> n.kinds().containsKey(at(5, 5, 5))).findFirst().orElseThrow();
        assertFalse(lonely.clusters().get(0).valid());
    }

    @Test
    void powerDrainsAndRunsOut() {
        Map<GridPos, NodeKind> nodes = line(NodeKind.CABLE, 1);
        nodes.put(at(1, 1, 0), NodeKind.DRIVE);
        nodes.put(at(1, -1, 0), NodeKind.ENERGY_CELL);
        GridNetwork network = GridCalculator.compute(nodes).get(0);
        assertEquals(8.0, network.idleDrain(), 1e-9);
        PowerState power = new PowerState();
        assertFalse(power.tick(network));
        assertEquals(16.0, power.inject(network, 16.0), 1e-9);
        assertTrue(power.tick(network));
        assertTrue(power.tick(network));
        assertFalse(power.tick(network));
        assertEquals(0.0, power.stored(), 1e-9);
    }

    @Test
    void creativeCellAlwaysPowers() {
        Map<GridPos, NodeKind> nodes = line(NodeKind.CABLE, 1);
        nodes.put(at(1, 1, 0), NodeKind.CREATIVE_ENERGY_CELL);
        assertTrue(new PowerState().tick(GridCalculator.compute(nodes).get(0)));
    }

    @Test
    void positionsRoundTrip() {
        GridPos pos = new GridPos("my world", -4, 70, 12);
        assertEquals(pos, GridPos.decode(pos.encode()));
    }
}
