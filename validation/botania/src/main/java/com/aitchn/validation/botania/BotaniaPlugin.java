package com.aitchn.validation.botania;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.mod.ContentView;
import com.aitchn.prism.api.mod.ModContext;
import com.aitchn.prism.api.mod.PrismMod;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Botania rebuilt as a plain plugin on Prism. This is a validation of the Prism API, not a product: see
 * docs/validation/botania.md for what it covers and which gaps it found.
 */
public final class BotaniaPlugin extends JavaPlugin implements PrismMod, Listener {

    private BotaniaRuntime runtime;

    @Override
    public void onEnable() {
        runtime = new BotaniaRuntime(getLogger());
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onInitialize(@NotNull ModContext context) {
        BotaniaContent.register(context, () -> runtime);
    }

    @Override
    public void onContentLoaded(@NotNull ContentView view) {
        runtime.recipes(BotaniaRecipes.load(Prism.recipes()));
        getLogger().info("BOTANIA_ACTIVE items=" + view.items().size() + " blocks=" + view.blocks().size()
                + " infusions=" + Prism.recipes().all(BotaniaContent.INFUSION).size()
                + " petalRecipes=" + Prism.recipes().all(BotaniaContent.PETAL).size()
                + " conversions=" + Prism.recipes().all(BotaniaContent.PURE_DAISY).size());
    }

    /** Optional end-to-end check, enabled with {@code -Dbotania.selftest=true}; the smoke test uses it. */
    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        if (Boolean.getBoolean("botania.selftest") && !Bukkit.getWorlds().isEmpty()) {
            new SelfTest(runtime).start(Bukkit.getWorlds().get(0));
        }
    }
}
