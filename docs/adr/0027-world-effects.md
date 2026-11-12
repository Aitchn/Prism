# ADR-0027: World effects

Status: Accepted (2026-11-12). The pure logic (emitter cadence and phase, the nearest-first budget, the per-chunk
emitter sets, travel paths and moves, the per-player cap, component decoding, tinted item definitions and baked Bedrock
textures) is unit tested. A self test checks the server side on Paper, Folia and Purpur (see "Verification"). What a
Java or Bedrock client draws is not verified in CI.

## Context

Content that does things wants to show it in the world: a machine that glows and sparks while it works, a beam between
two blocks, something that flies from one block to another and arrives. Until now a mod did this with plain world calls
on its own timers, one per block, which costs server work for every block whether anyone is near, and it could not show
a model moving smoothly without spawning a real entity. Models taken from other games also often draw every face with
`"tintindex": 0` and expect the game to colour it; Prism had no way to give that index a colour, so such blocks came out
white or grey.

Requirements, as for the rest of Prism: generic primitives only; Paper, Folia and Purpur; no world access off the
owning region thread; no real server entities; nothing sent to a player who does not have the chunk; the least server
work possible; Bedrock players (Geyser) get a sensible fallback; only `prism-client` touches PacketEvents.

## Decision

### 1. Block particle emitters: `prism:particles`

A block lists up to 8 emitters. Each is one vanilla particle (`particle`, `count`, `spread`, `speed`, `color`, `size`,
`material`), a `rate` in emissions per second (above 0, at most 20; below 1 means "every few seconds"), an `offset`
from the block centre (turned with the block's facing), an optional `range` (at most 128 blocks; the server's
`effects.particles.range` otherwise) and an optional `variants` list.

- **One shared clock**, every `effects.particles.interval` ticks (default 5), on the global thread. It runs only while
  some loaded chunk has an emitting block. Nothing is scheduled per block.
- **Per-chunk emitter sets.** Placing, removing or switching the variant of a block, loading a chunk or changing content
  marks the chunk dirty from its own thread. At the next cycle the clock rebuilds each dirty chunk's immutable
  `EmitterSet` once, from Prism's concurrent block index (no world access), however many changes it had.
- **Per viewer.** For each player the clock looks up the chunks within the largest emitter range. A player near no
  emitter costs one lookup. For the others one task runs on the player's thread: it keeps the chunks the client has
  (`Player#isChunkSent`), selects the due emitters within each one's range, nearest block first, and sends them.
- **Cadence.** An emitter emits `floor((cycle + phase) * perCycle) - floor((cycle + phase - 1) * perCycle)` times in a
  cycle, so a rate that is not a multiple of the cycle is exact over time. The phase comes from the block position, so
  a row of identical blocks does not pulse in step.
- **Budget.** `effects.particles.max-per-player` (default 2000 per second) becomes a per-cycle budget. Emitters are
  taken nearest first and selection stops at the first one that does not fit, so the farthest are what is skipped.

### 2. `Prism.effects()`: travelling visuals, bursts and beams

```java
public interface EffectService {
    TravelHandle launch(Travel travel);
    void burst(Location at, List<ParticleSpec> particles);
    default void burst(Location at, ParticleSpec... particles);
    void line(Location from, Location to, ParticleSpec particle, double density); // at most 512 points
}
```

`Travel` holds `from`, `to`, `ticks` (1 to 6000), an optional item model (an item key or any item model id), `scale`,
`billboard`, an optional particle `trail` with 1 to 8 points per tick, and an optional `onArrive` callback.

- **The model is a packet-only `item_display`**, spawned in `prism-client` for each Java viewer with position
  interpolation. The client moves it: one teleport with an interpolation duration of at most 59 ticks (the protocol
  limit) per stretch, so a five second flight is two moves, then one removal. The first move goes out one clock tick
  after the spawn, so the client has the entity before it is told to move.
- **Viewers** are the players within `effects.view-range` (default 48) of the path who have the chunk the travel is in
  when it starts. A player who would see more than `effects.travel.max-per-player` (default 64) loses the farthest
  (ties: the newer one).
- **The travel clock** runs every tick on the global thread, and only while a travel is active. It sends moves when a
  stretch ends and the trail to the players then in range (each on their own thread).
- **The callback** is a separate task scheduled with `runAtLater` on the region that owns `to`, so on Folia it may touch
  the destination block. It runs even when no player saw the travel, and not after `TravelHandle#cancel`.
- `burst` and `line` send once to the players in range, on each player's thread.

### 3. `prism:tint`

A list of `#rrggbb` colours (index n for `tintindex` n, at most 16), or `{colors: [...], variants: {name: [...]}}` where
a variant's list replaces the default.

- **Java**: every item model definition Prism writes for the block or item (the item itself, each variant's block item,
  each display-layer face) gets `"tints": [{"type": "minecraft:constant", "value": <rgb>}, ...]`. A mod that ships its
  own item model definition keeps it and gets a `PRISM_PACK_WARNING`.
- **Borrowed block states** cannot be tinted (a block state model has no tint source of its own). A look drawn that way
  gets one `PRISM_PACK_WARNING` per block naming `prism:render: display` as the fix.
- **Bedrock**: Bedrock geometry has no tint index. For each face whose model element has a `tintindex` (or whose parent
  is a vanilla tinted parent such as `block/leaves`) the Bedrock pack holds a copy of the texture multiplied by the
  colour, named `<texture>_t<rrggbb>`. The item icon is multiplied by the first colour.

### 4. Particles are sent with `Player#spawnParticle`

Particles go through Paper's per-player `spawnParticle` on the player's thread rather than a PacketEvents wrapper.
Paper resolves and validates the particle data for the running version, and Geyser translates the result for Bedrock
players. `ParticleSpec` is plain data resolved against the server's particle registry when it is first used; a type that
does not exist, or that needs data the spec does not give, is logged once (`PRISM_EFFECTS_WARNING`) and skipped.

## Alternatives considered

- **A timer per emitting block.** Simple, but costs a task per block in every loaded chunk whether anyone is near, and
  thousands of independent tasks on Folia. Rejected for the shared clock.
- **Real display entities for travelling visuals.** They would be ticked, tracked and possibly saved by the server, and
  need cleanup after a crash. Rejected: the packet-only display costs the server nothing between moves.
- **Teleporting the display every tick.** Smooth on any client, but one packet per tick per viewer. The protocol's
  position interpolation gives the same look with one packet per 59 ticks at most.
- **Particles through PacketEvents.** Would add a second particle mapping to keep in step with each version, and
  bypass Paper's data validation. Rejected; `spawnParticle` is already per player.
- **Tinting by rewriting textures on Java too.** Would multiply texture count and lose the shading the client applies to
  the tint. Rejected; item model definitions support constant tints.

## Consequences

- An idle server (no emitting block loaded, no travel) runs no effect task at all.
- Cost per cycle: one chunk lookup per online player; one task per player near an emitter, which sends at most the
  budget. Rebuilds are per dirty chunk, not per change.
- Travelling models are seen by Java players only; Bedrock players see the trail. Players who reach the path after the
  travel started see the trail, not the model.
- Emitters run only while the chunk is loaded and only for players the chunk was sent to.
- Tints are constant per variant. A colour that changes with data needs a variant per colour.

## Verification

`-Dprism.selftest.effects=<block key>` (set in the smoke test to `examplemod:ruby_lamp`) places the block and checks
that it has its idle emitters and that a player position near it selects them and a far one does not, that switching it
to `active` adds the active-only emitter, that a 20 tick travel with a cancelled twin completes once on the thread that
owns the destination after 19 to 22 clock ticks, that a burst and a line run, that the Java pack holds tinted
definitions and the Bedrock pack a tinted texture, and that removing the block leaves no emitters. It logs
`PRISM_EFFECTS_SELFTEST_OK emitters=<n> launched=<n> completed=<n> tinted=<n>` or `PRISM_EFFECTS_SELFTEST_FAIL <reason>`.

## Not verified by CI

- What a Java client draws: particle look, the smoothness of the display interpolation across moves, tinted models.
- Bedrock: the particle translation by Geyser and the baked tint textures on a real client.
- Timing under load on Folia beyond the self test's window.
