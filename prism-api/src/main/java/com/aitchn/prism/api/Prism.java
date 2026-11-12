package com.aitchn.prism.api;

import com.aitchn.prism.api.block.BlockService;
import com.aitchn.prism.api.client.ClientService;
import com.aitchn.prism.api.data.DataService;
import com.aitchn.prism.api.effect.EffectService;
import com.aitchn.prism.api.menu.MenuService;
import com.aitchn.prism.api.item.ItemService;
import com.aitchn.prism.api.mod.ModRegistrar;
import com.aitchn.prism.api.recipe.RecipeService;
import com.aitchn.prism.api.scheduler.PrismScheduler;
import com.aitchn.prism.api.text.TranslationService;
import java.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Static entry point for mods, like Fabric's loader facade. Available once Prism has enabled. */
public final class Prism {

    private static volatile Services services;

    private Prism() {}

    public static @NotNull ModRegistrar mods() {
        return current().mods();
    }

    public static @NotNull ItemService items() {
        return current().items();
    }

    public static @NotNull BlockService blocks() {
        return current().blocks();
    }

    public static @NotNull ClientService clients() {
        return current().clients();
    }

    public static @NotNull MenuService menus() {
        return current().menus();
    }

    public static @NotNull DataService data() {
        return current().data();
    }

    /** Recipes of every active mod, by type; Prism's own types are also real vanilla recipes. */
    public static @NotNull RecipeService recipes() {
        return current().recipes();
    }

    /** Thread-correct scheduling for Paper, Folia and Purpur. */
    public static @NotNull PrismScheduler scheduler() {
        return current().scheduler();
    }

    /** Translations from the mods' language files, for text a mod sends to a player itself. */
    public static @NotNull TranslationService translations() {
        return current().translations();
    }

    /** Particles, travelling visuals and beams that exist only in packets; block emitters are {@code prism:particles}. */
    public static @NotNull EffectService effects() {
        return current().effects();
    }

    private static Services current() {
        Services current = services;
        if (current == null) {
            throw new IllegalStateException("Prism is not enabled; add `depend: [Prism]` to your plugin.yml");
        }
        return current;
    }

    /** Installed by the Prism plugin only. */
    @ApiStatus.Internal
    public static void install(
            @NotNull ModRegistrar mods,
            @NotNull ItemService items,
            @NotNull BlockService blocks,
            @NotNull ClientService clients,
            @NotNull MenuService menus,
            @NotNull DataService data,
            @NotNull PrismScheduler scheduler,
            @NotNull RecipeService recipes,
            @NotNull TranslationService translations,
            @NotNull EffectService effects) {
        services = new Services(Objects.requireNonNull(mods), Objects.requireNonNull(items),
                Objects.requireNonNull(blocks), Objects.requireNonNull(clients), Objects.requireNonNull(menus),
                Objects.requireNonNull(data), Objects.requireNonNull(scheduler), Objects.requireNonNull(recipes),
                Objects.requireNonNull(translations), Objects.requireNonNull(effects));
    }

    @ApiStatus.Internal
    public static void uninstall() {
        services = null;
    }

    private record Services(ModRegistrar mods, ItemService items, BlockService blocks, ClientService clients,
            MenuService menus, DataService data, PrismScheduler scheduler, RecipeService recipes,
            TranslationService translations, EffectService effects) {}
}
