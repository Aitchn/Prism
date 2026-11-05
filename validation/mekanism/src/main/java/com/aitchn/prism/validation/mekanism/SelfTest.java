package com.aitchn.prism.validation.mekanism;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.validation.mekanism.logic.Chemical;
import com.aitchn.prism.validation.mekanism.logic.ChemicalPorts;
import com.aitchn.prism.validation.mekanism.logic.EnergyBuffer;
import com.aitchn.prism.validation.mekanism.logic.Stack;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Startup check on a real server, enabled with {@code -Dmekanism.selftest=true} (the smoke test sets it). It builds a
 * small factory next to the world spawn and proves, without a player, that the Prism platform carries the mod:
 * placement runs the mod's place behaviors, the energy capability resolves, a cable joins a generator and a machine,
 * the machine runs a data-driven recipe and keeps its state in per-block data, a pipe moves a chemical between two
 * machines, and removal forgets everything.
 */
final class SelfTest implements Listener {

    private final Plugin plugin;
    private final Logger log;
    private final List<Block> placed = new ArrayList<>();

    SelfTest(Plugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        if (Bukkit.getWorlds().isEmpty()) {
            log.severe("MEKANISM_SELFTEST_FAIL no world");
            return;
        }
        // Clear of Prism's own self tests (spawn, +3 x, +6 z, one block either side).
        Location base = Bukkit.getWorlds().get(0).getSpawnLocation().add(0, 0, 10);
        Prism.scheduler().runAt(base, () -> start(base));
    }

    private Block at(Location base, int dx, int dz) {
        return base.clone().add(dx, 0, dz).getBlock();
    }

    private boolean place(String key, Block block) {
        block.getChunk().addPluginChunkTicket(plugin); // no player is online: keep the chunk loaded and ticking
        boolean ok = Prism.blocks().place(new PrismKey("mekanism", key), block.getLocation());
        if (ok) {
            placed.add(block);
        }
        return ok;
    }

    private void start(Location base) {
        try {
            Block solar = at(base, 0, 0);
            Block machine = at(base, 3, 0);
            Block separator = at(base, 0, 3);
            Block purifier = at(base, 2, 3);
            boolean placedAll = place("solar_generator", solar) & place("universal_cable", at(base, 1, 0))
                    & place("universal_cable", at(base, 2, 0)) & place("enrichment_chamber", machine)
                    & place("electrolytic_separator", separator) & place("pressurized_tube", at(base, 1, 3))
                    & place("purification_chamber", purifier);

            EnergyBuffer solarEnergy = Prism.blocks().capability(solar, null, MekanismCapabilities.ENERGY).orElse(null);
            EnergyBuffer machineEnergy = Prism.blocks().capability(machine, null, MekanismCapabilities.ENERGY).orElse(null);
            EnergyBuffer separatorEnergy = Prism.blocks().capability(separator, null, MekanismCapabilities.ENERGY).orElse(null);
            ChemicalPorts separatorPorts = Prism.blocks().capability(separator, null, MekanismCapabilities.CHEMICAL).orElse(null);
            boolean capabilities = solarEnergy != null && machineEnergy != null && separatorEnergy != null
                    && separatorPorts != null;
            if (!placedAll || !capabilities) {
                finish(base, false, "placed=" + placedAll + " capabilities=" + capabilities);
                return;
            }
            Grid grid = Grid.get();
            grid.underLock(() -> {
                solarEnergy.produce(30_000);
                separatorEnergy.produce(50_000);
                separatorPorts.inputs().get(0).fill(Chemical.WATER, 1_000, false);
                grid.machine(Prism.blocks().get(machine).orElseThrow(), behavior(machine))
                        .insertInput(new Stack("mekanism:osmium_ore", 1));
            });
            // The energy and gas have to travel first.
            Prism.scheduler().runAtLater(base, () -> checkTransport(base, machine, purifier), 60);
        } catch (RuntimeException e) {
            log.severe("MEKANISM_SELFTEST_FAIL " + e);
        }
    }

    private MachineBehavior behavior(Block block) {
        return Prism.blocks().get(block).orElseThrow().definition().behaviors(MachineBehavior.class).getFirst();
    }

    private void checkTransport(Location base, Block machine, Block purifier) {
        try {
            Grid grid = Grid.get();
            long energy = grid.locked(() -> grid.machine(Prism.blocks().get(machine).orElseThrow(), behavior(machine))
                    .energy().stored());
            long oxygen = grid.locked(() -> grid.machine(Prism.blocks().get(purifier).orElseThrow(), behavior(purifier))
                    .inTank().amount());
            boolean ok = energy > 0 && oxygen > 0;
            String early = "cableEnergy=" + energy + " pipeOxygen=" + oxygen;
            if (!ok) {
                finish(base, false, early);
                return;
            }
            // 200 ticks of recipe plus margin.
            Prism.scheduler().runAtLater(base, () -> checkRecipe(base, machine, early), 260);
        } catch (RuntimeException e) {
            log.severe("MEKANISM_SELFTEST_FAIL " + e);
        }
    }

    private void checkRecipe(Location base, Block machine, String early) {
        try {
            Grid grid = Grid.get();
            CustomBlock custom = Prism.blocks().get(machine).orElseThrow();
            Stack output = grid.locked(() -> grid.machine(custom, behavior(machine)).output());
            boolean dust = output != null && output.id().equals("mekanism:osmium_dust") && output.count() == 2;
            boolean saved = custom.data().has(Grid.STATE, PersistentDataType.STRING);
            finish(base, dust && saved, early + " output=" + output + " saved=" + saved);
        } catch (RuntimeException e) {
            log.severe("MEKANISM_SELFTEST_FAIL " + e);
        }
    }

    private void finish(Location base, boolean ok, String details) {
        Grid grid = Grid.get();
        boolean removed = true;
        for (Block block : placed) {
            removed &= Prism.blocks().remove(block, false);
            block.getChunk().removePluginChunkTicket(plugin);
        }
        boolean forgotten = grid.machineCount() == 0 && grid.solarCount() == 0;
        log.info((ok && removed && forgotten ? "MEKANISM_SELFTEST_OK " : "MEKANISM_SELFTEST_FAIL ") + details
                + " removed=" + removed + " forgotten=" + forgotten);
    }
}
