package com.aitchn.prism.core.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.client.ClientCapability;
import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class ProfilesTest {

    private static Player player(UUID id) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> method.getName().equals("getUniqueId") ? id : null);
    }

    @Test
    void javaPlayersGetEverythingExceptForms() {
        var java = Profiles.java();
        assertFalse(java.isBedrock());
        assertTrue(java.supports(ClientCapability.ITEM_DYNAMIC_STATE));
        assertTrue(java.supports(ClientCapability.MODEL_ENTITIES));
        assertFalse(java.supports(ClientCapability.FORM_MENUS));
    }

    @Test
    void bedrockDegradesWhatItCannotShow() {
        var bedrock = Profiles.bedrock();
        assertTrue(bedrock.isBedrock());
        assertTrue(bedrock.supports(ClientCapability.CUSTOM_ITEM_APPEARANCE));
        assertTrue(bedrock.supports(ClientCapability.FORM_MENUS));
        assertFalse(bedrock.supports(ClientCapability.ITEM_DYNAMIC_STATE));
        assertFalse(bedrock.supports(ClientCapability.MODEL_ENTITIES));
        assertFalse(bedrock.supports(ClientCapability.CUSTOM_FONTS));
    }

    @Test
    void serviceFollowsTheDetector() {
        UUID geyser = UUID.randomUUID();
        var profiles = new Profiles(geyser::equals);
        assertSame(Profiles.bedrock(), profiles.profile(player(geyser)));
        assertSame(Profiles.java(), profiles.profile(player(UUID.randomUUID())));
    }
}
