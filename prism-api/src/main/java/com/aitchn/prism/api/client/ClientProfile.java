package com.aitchn.prism.api.client;

import java.util.Set;
import org.jetbrains.annotations.NotNull;

/** What Prism knows about the client a player is using. Immutable. */
public interface ClientProfile {

    @NotNull ClientKind kind();

    @NotNull Set<ClientCapability> capabilities();

    default boolean isBedrock() {
        return kind() == ClientKind.BEDROCK;
    }

    default boolean supports(@NotNull ClientCapability capability) {
        return capabilities().contains(capability);
    }
}
