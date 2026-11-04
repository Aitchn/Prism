package com.aitchn.prism.plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Decides whether a connection is Bedrock. Floodgate is preferred, then the Geyser API; both are reached by
 * reflection so Prism has no compile or class loading dependency on either. Without both, everyone is Java.
 */
final class BedrockDetector implements Predicate<UUID> {

    private final Method isPlayer;
    private final Object target;
    private final String source;

    private BedrockDetector(Object target, Method isPlayer, String source) {
        this.target = target;
        this.isPlayer = isPlayer;
        this.source = source;
    }

    static BedrockDetector create(Logger log) {
        try {
            Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return new BedrockDetector(instance, api.getMethod("isFloodgatePlayer", UUID.class), "floodgate");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // not installed
        }
        try {
            Class<?> api = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Object instance = api.getMethod("api").invoke(null);
            return new BedrockDetector(instance, api.getMethod("isBedrockPlayer", UUID.class), "geyser");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // not installed
        }
        log.info("Bedrock detection: neither Floodgate nor Geyser found, all players are treated as Java");
        return new BedrockDetector(null, null, "none");
    }

    String source() {
        return source;
    }

    @Override
    public boolean test(UUID id) {
        if (isPlayer == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(isPlayer.invoke(target, id));
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
