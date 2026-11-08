package com.aitchn.prism.validation.ae2;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.validation.ae2.grid.GridPos;
import com.aitchn.prism.validation.ae2.runtime.NetworkRuntime;
import com.aitchn.prism.validation.ae2.storage.ItemKey;
import com.aitchn.prism.validation.ae2.storage.StorageCell;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Optional startup check, enabled with {@code -Dae2.selftest=true}: builds a small network out of real Prism blocks
 * at the world spawn, checks that placing joined the grid, that storage and power work, and that removing a cable
 * cuts the terminal off. The smoke test uses it to prove the place/remove hooks on Paper, Folia and Purpur.
 */
final class Ae2SelfTest implements Listener {

    private final Ae2Plugin plugin;
    private final Ae2Services services;

    Ae2SelfTest(Ae2Plugin plugin, Ae2Services services) {
        this.plugin = plugin;
        this.services = services;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        if (Bukkit.getWorlds().isEmpty()) {
            plugin.getLogger().severe("AE2_GRID_SELFTEST_FAIL no world");
            return;
        }
        Location origin = Bukkit.getWorlds().get(0).getSpawnLocation().getBlock().getLocation();
        Bukkit.getRegionScheduler().run(plugin, origin, task -> {
            try {
                plugin.getLogger().info(run(origin));
            } catch (RuntimeException e) {
                plugin.getLogger().severe("AE2_GRID_SELFTEST_FAIL " + e);
            }
        });
    }

    private String run(Location origin) {
        List<Location> placed = new ArrayList<>();
        String[][] layout = {
            {"controller", "0", "0", "0"}, {"cable", "1", "0", "0"}, {"terminal", "2", "0", "0"},
            {"drive", "1", "0", "1"}, {"creative_energy_cell", "1", "1", "0"}, {"energy_cell", "1", "0", "-1"}};
        for (String[] entry : layout) {
            Location at = origin.clone().add(Integer.parseInt(entry[1]), Integer.parseInt(entry[2]),
                    Integer.parseInt(entry[3]));
            if (!Prism.blocks().place(new PrismKey("ae2", entry[0]), at)) {
                return "AE2_GRID_SELFTEST_FAIL could not place " + entry[0];
            }
            placed.add(at);
        }
        GridPos terminal = NodeBehavior.pos(placed.get(2));
        GridPos drive = NodeBehavior.pos(placed.get(3));
        Optional<NetworkRuntime> network = services.grid.networkAt(terminal);
        if (network.isEmpty() || !network.get().topology().isOnline(terminal)
                || !network.get().topology().isOnline(drive)) {
            return "AE2_GRID_SELFTEST_FAIL placed blocks did not form a network";
        }

        // A storage cell is an ordinary item; the drive keeps it in its per-block data.
        ItemStack cell = Prism.items().create(Ae2Content.cellKey(StorageCell.Tier.K1), 1).orElseThrow();
        boolean bound = services.grid.drive(drive).orElseThrow().bind(0, cell);
        services.grid.refresh();
        NetworkRuntime runtime = services.grid.networkAt(terminal).orElseThrow();
        long stored = runtime.storage().insert(new ItemKey("selftest"), 100);
        boolean powered = runtime.power().tick(runtime.topology());

        // Another block can charge the network through the capability lookup.
        double sunk = Prism.blocks().capability(placed.get(5).getBlock(), null, Ae2Content.ENERGY_SINK)
                .map(sink -> sink.insert(10)).orElse(-1.0);

        // The drive writes the cell, with its contents, into its own block data.
        Prism.blocks().get(placed.get(3).getBlock()).ifPresent(block ->
                services.grid.drive(drive).orElseThrow().write(block.data()));
        boolean saved = Prism.blocks().get(placed.get(3).getBlock()).map(block -> block.data().has(
                Ae2Content.slotKey("cell", 0), org.bukkit.persistence.PersistentDataType.BYTE_ARRAY)).orElse(false);

        Prism.blocks().remove(placed.get(1).getBlock(), false);
        boolean cutOff = services.grid.networkAt(terminal).map(n -> !n.topology().isOnline(terminal)).orElse(false);

        for (Location at : placed) {
            Prism.blocks().remove(at.getBlock(), false);
        }
        boolean clean = services.grid.networks().isEmpty();
        boolean ok = bound && stored == 100 && powered && sunk >= 0 && saved && cutOff && clean;
        return (ok ? "AE2_GRID_SELFTEST_OK" : "AE2_GRID_SELFTEST_FAIL") + " bound=" + bound + " stored=" + stored
                + " powered=" + powered + " sunk=" + sunk + " saved=" + saved + " cutOff=" + cutOff + " clean=" + clean;
    }
}
