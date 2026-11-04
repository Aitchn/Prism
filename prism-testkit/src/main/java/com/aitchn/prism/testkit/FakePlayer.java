package com.aitchn.prism.testkit;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link Player} stand-in for behavior tests. It remembers what was sent to it and answers only the few calls
 * listed below; any other method returns the default value (null, 0 or false).
 */
public final class FakePlayer {

    private final List<String> messages = new ArrayList<>();
    private final Player player;
    private double health = 20;

    public FakePlayer(@NotNull String name) {
        UUID id = UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.player = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[] {Player.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getName" -> {
                            return name;
                        }
                        case "getUniqueId" -> {
                            return id;
                        }
                        case "getHealth" -> {
                            return health;
                        }
                        case "setHealth" -> {
                            health = (Double) args[0];
                            return null;
                        }
                        case "sendMessage" -> {
                            if (args != null && args.length == 1 && args[0] instanceof String text) {
                                messages.add(text);
                            }
                            return null;
                        }
                        default -> {
                            return defaultValue(method.getReturnType());
                        }
                    }
                });
    }

    public @NotNull Player player() {
        return player;
    }

    public @NotNull List<String> messages() {
        return Collections.unmodifiableList(messages);
    }

    public double health() {
        return health;
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == float.class) {
            return 0f;
        }
        return null;
    }
}
