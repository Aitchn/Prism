package com.aitchn.prism.api.client;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Entry point for client capability queries. Obtain it with {@code Prism.clients()}. */
public interface ClientService {

    /** The profile of {@code player}. Safe to call from any thread. */
    @NotNull ClientProfile profile(@NotNull Player player);
}
