package com.aitchn.prism.core.client;

import com.aitchn.prism.api.client.ClientCapability;
import com.aitchn.prism.api.client.ClientKind;
import com.aitchn.prism.api.client.ClientProfile;
import com.aitchn.prism.api.client.ClientService;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** The capability table for each edition (docs/adr/0006-bedrock.md) and the service that applies it. */
public final class Profiles implements ClientService {

    private static final ClientProfile JAVA = new Fixed(ClientKind.JAVA, EnumSet.allOf(ClientCapability.class)
            .stream().filter(c -> c != ClientCapability.FORM_MENUS).collect(
                    java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(ClientCapability.class))));

    private static final ClientProfile BEDROCK = new Fixed(ClientKind.BEDROCK, EnumSet.of(
            ClientCapability.CUSTOM_ITEM_APPEARANCE,
            ClientCapability.DISPLAY_ENTITIES,
            ClientCapability.CONTAINER_MENUS,
            ClientCapability.FORM_MENUS));

    private final Predicate<UUID> bedrockCheck;

    /** @param bedrockCheck true when the player with that id connects through Geyser or Floodgate */
    public Profiles(@NotNull Predicate<UUID> bedrockCheck) {
        this.bedrockCheck = bedrockCheck;
    }

    public static @NotNull ClientProfile java() {
        return JAVA;
    }

    public static @NotNull ClientProfile bedrock() {
        return BEDROCK;
    }

    @Override
    public @NotNull ClientProfile profile(@NotNull Player player) {
        return bedrockCheck.test(player.getUniqueId()) ? BEDROCK : JAVA;
    }

    private record Fixed(ClientKind kind, Set<ClientCapability> capabilities) implements ClientProfile {
        private Fixed {
            capabilities = Set.copyOf(capabilities);
        }
    }
}
