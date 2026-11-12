package com.aitchn.prism.api.effect;

import java.util.List;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

/**
 * Shows things happening in the world: travelling visuals, bursts and beams. Everything here exists only in the packets
 * sent to the players who can see it (within the server's {@code effects.view-range} and holding the chunk): no entity
 * is spawned, ticked or saved on the server, and nothing is sent to anyone else. Every method may be called from any
 * thread. Blocks emit particles on their own with the {@code prism:particles} component.
 *
 * <p>Bedrock players (Geyser) get the particles of every effect, but not the item model of a travelling visual.
 */
public interface EffectService {

    /**
     * Starts a travelling visual. The client moves the model itself (one spawn, a move every three seconds at most, one
     * removal); the trail is sent each tick only to players in range. A player who sees more than the server's
     * {@code effects.travel.max-per-player} at once loses the farthest.
     *
     * @return a handle to cancel the travel early
     */
    @NotNull TravelHandle launch(@NotNull Travel travel);

    /** Sends each of {@code particles} once at {@code at} to the players in range. */
    void burst(@NotNull Location at, @NotNull List<ParticleSpec> particles);

    /** As {@link #burst(Location, List)}. */
    default void burst(@NotNull Location at, @NotNull ParticleSpec... particles) {
        burst(at, List.of(particles));
    }

    /**
     * An instant beam: {@code particle} at points along the straight line from {@code from} to {@code to},
     * {@code density} points per block (at most 512 points), sent once to the players in range.
     */
    void line(@NotNull Location from, @NotNull Location to, @NotNull ParticleSpec particle, double density);
}
