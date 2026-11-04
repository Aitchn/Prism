package com.aitchn.prism.api;

import com.aitchn.prism.api.block.BlockService;
import com.aitchn.prism.api.client.ClientService;
import com.aitchn.prism.api.data.DataService;
import com.aitchn.prism.api.menu.MenuService;
import com.aitchn.prism.api.item.ItemService;
import com.aitchn.prism.api.mod.ModRegistrar;
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
            @NotNull DataService data) {
        services = new Services(Objects.requireNonNull(mods), Objects.requireNonNull(items),
                Objects.requireNonNull(blocks), Objects.requireNonNull(clients), Objects.requireNonNull(menus),
                Objects.requireNonNull(data));
    }

    @ApiStatus.Internal
    public static void uninstall() {
        services = null;
    }

    private record Services(ModRegistrar mods, ItemService items, BlockService blocks, ClientService clients,
            MenuService menus, DataService data) {}
}
