package com.aitchn.prism.plugin;

import com.aitchn.prism.api.effect.ParticleSpec;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Turns a {@link ParticleSpec} into a Bukkit particle and its data, once per spec, and sends it to one player
 * (ADR-0027). Particles go out through {@link Player#spawnParticle}: one packet to that player only, on the player's
 * own thread, with the data checked by the server, and Geyser translates it for Bedrock players. A spec that cannot be
 * resolved (unknown type, data the type needs and the spec lacks, a data type Prism does not fill) is reported once as
 * {@code PRISM_EFFECTS_WARNING} and skipped.
 */
final class EffectParticles {

    /** A resolved spec: the particle and the data object its type takes (null for none). */
    record Resolved(Particle particle, @Nullable Object data, ParticleSpec spec) {}

    private final Logger log;
    private final Map<ParticleSpec, Optional<Resolved>> cache = new ConcurrentHashMap<>();
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    EffectParticles(Logger log) {
        this.log = log;
    }

    Optional<Resolved> resolve(ParticleSpec spec) {
        return cache.computeIfAbsent(spec, this::compute);
    }

    private Optional<Resolved> compute(ParticleSpec spec) {
        NamespacedKey key = NamespacedKey.fromString(spec.type().toString());
        Particle particle = key == null ? null : Registry.PARTICLE_TYPE.get(key);
        if (particle == null) {
            warn(spec.type() + " is not a particle of this server");
            return Optional.empty();
        }
        Class<?> type = particle.getDataType();
        Color color = Color.fromRGB(spec.color().orElse(0xFFFFFF) & 0xFFFFFF);
        Object data;
        if (type == Void.class) {
            data = null;
        } else if (type == Particle.DustTransition.class) {
            data = new Particle.DustTransition(color, color, spec.size());
        } else if (type == Particle.DustOptions.class) {
            data = new Particle.DustOptions(color, spec.size());
        } else if (type == Color.class) {
            data = color;
        } else if (type == Particle.Spell.class) {
            data = new Particle.Spell(color, spec.size());
        } else if (type == Float.class) {
            data = 0f;
        } else if (type == Integer.class) {
            data = 0;
        } else if (type == ItemStack.class || type == BlockData.class) {
            Material material = spec.material().map(m -> Material.matchMaterial(m.toString())).orElse(null);
            if (material == null) {
                warn(spec.type() + " needs a material (an item or block id), got "
                        + spec.material().map(Object::toString).orElse("none"));
                return Optional.empty();
            }
            if (type == ItemStack.class && !material.isItem() || type == BlockData.class && !material.isBlock()) {
                warn(spec.type() + " needs " + (type == ItemStack.class ? "an item" : "a block") + ", got " + material);
                return Optional.empty();
            }
            data = type == ItemStack.class ? new ItemStack(material) : material.createBlockData();
        } else {
            warn(spec.type() + " takes data Prism cannot fill (" + type.getSimpleName() + "); use another particle");
            return Optional.empty();
        }
        return Optional.of(new Resolved(particle, data, spec));
    }

    private void warn(String message) {
        if (warned.add(message)) {
            log.warning("PRISM_EFFECTS_WARNING " + message);
        }
    }

    /** Sends {@code emissions} emissions of {@code resolved} at {@code (x, y, z)} to {@code player}; its thread. */
    static void send(Player player, Resolved resolved, double x, double y, double z, int emissions) {
        ParticleSpec spec = resolved.spec();
        int count = Math.min(ParticleSpec.MAX_COUNT * 8, spec.count() * Math.max(1, emissions));
        player.spawnParticle(resolved.particle(), x, y, z, count, spec.spreadX(), spec.spreadY(), spec.spreadZ(),
                spec.speed(), resolved.data(), false);
    }
}
