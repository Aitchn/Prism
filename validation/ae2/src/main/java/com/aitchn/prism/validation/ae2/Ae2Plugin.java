package com.aitchn.prism.validation.ae2;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.validation.ae2.crafting.CraftingPlanner;
import com.aitchn.prism.validation.ae2.crafting.Pattern;
import com.aitchn.prism.validation.ae2.grid.GridCalculator;
import com.aitchn.prism.validation.ae2.grid.GridNetwork;
import com.aitchn.prism.validation.ae2.grid.GridPos;
import com.aitchn.prism.validation.ae2.grid.NodeKind;
import com.aitchn.prism.validation.ae2.runtime.Ae2State;
import com.aitchn.prism.validation.ae2.storage.ItemKey;
import com.aitchn.prism.api.mod.ContentView;
import com.aitchn.prism.api.mod.ModContext;
import com.aitchn.prism.api.mod.PrismMod;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Applied Energistics 2, as far as Prism lets a plain plugin rebuild it. Everything the mod needs from Prism goes
 * through {@code prism-api}; what it cannot express is listed in docs/validation/ae2.md.
 */
public final class Ae2Plugin extends JavaPlugin implements PrismMod {

    private static final int EXPECTED_BLOCKS = NodeKind.values().length;
    private static final int EXPECTED_ITEMS = EXPECTED_BLOCKS + 5 + 3;

    private Ae2Services services;
    private ScheduledTask tick;
    private ScheduledTask save;

    @Override
    public void onInitialize(@NotNull ModContext context) {
        Ae2State state = new Ae2State(Prism.data().store(context.namespace()), getLogger());
        state.load();
        services = new Ae2Services(this, state);
        Ae2Content.bind(context.registerComponent("energy", Ae2Content.Energy.class,
                section -> new Ae2Content.Energy(section.requireInt("value"))));
        context.registerBehavior("node", NodeBehavior.class, section -> NodeBehavior.fromYaml(section, services));
        PluginCommand command = getCommand("ae2");
        if (command != null) {
            command.setExecutor(new Ae2Command(services));
        }
    }

    @Override
    public void onContentLoaded(@NotNull ContentView view) {
        Ae2Content.bind(view.items());
        services.grid.refresh();
        tick = Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, task -> services.grid.tick(), 1, 1);
        save = Bukkit.getAsyncScheduler().runAtFixedRate(this, task -> {
            services.grid.saveEnergy();
            services.state.flush();
        }, 2, 2, TimeUnit.SECONDS);
        boolean contentOk = view.blocks().size() == EXPECTED_BLOCKS && view.items().size() == EXPECTED_ITEMS;
        getLogger().info((contentOk ? "AE2_CONTENT_OK" : "AE2_CONTENT_FAIL") + " items=" + view.items().size()
                + " blocks=" + view.blocks().size());
        getLogger().info(selfTest() ? "AE2_SELFTEST_OK" : "AE2_SELFTEST_FAIL");
        if (Boolean.getBoolean("ae2.selftest")) {
            getServer().getPluginManager().registerEvents(new Ae2SelfTest(this, services), this);
        }
    }

    @Override
    public void onDisable() {
        if (tick != null) {
            tick.cancel();
        }
        if (save != null) {
            save.cancel();
        }
        if (services != null) {
            services.grid.saveEnergy();
            services.state.flush();
        }
    }

    /** Runs the grid and crafting logic once on the real server, so a broken jar shows up in the smoke test. */
    private static boolean selfTest() {
        Map<GridPos, NodeKind> nodes = new HashMap<>();
        nodes.put(new GridPos("test", 0, 0, 0), NodeKind.CONTROLLER);
        nodes.put(new GridPos("test", 1, 0, 0), NodeKind.CABLE);
        nodes.put(new GridPos("test", 1, 1, 0), NodeKind.TERMINAL);
        List<GridNetwork> networks = GridCalculator.compute(nodes);
        ItemKey ingot = new ItemKey("ingot");
        ItemKey plate = new ItemKey("plate");
        var plan = CraftingPlanner.plan(plate, 2, Map.of(ingot, 2L),
                List.of(new Pattern("p", Map.of(ingot, 1L), plate, 1)));
        return networks.size() == 1 && networks.get(0).isOnline(new GridPos("test", 1, 1, 0)) && plan.feasible();
    }
}
