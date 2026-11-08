# ADR-0017: Passable blocks

Status: Accepted (2026-11-08). The smoke test places two passable blocks, puts stone next to one and drops an item on
the other, and checks that both keep their state and can be walked through. Seeing them on clients is not covered
(see below).

## Context

Flowers, grass, wires and rails are walked through (the rest of row 38 in
[platform-triage.md](../platform-triage.md)). A note block carrier is a solid, opaque cube. A display entity over a
barrier has full collision and is invisible on Bedrock. A carrier is needed that both editions draw from a block
state, without collision and without hiding its neighbours.

## Decision

- **`prism:passable: true`** draws a block with a tripwire state. Prism takes only the 64 states with
  `disarmed=true`. Vanilla string has that value only for the moment shears cut it, so placed string never collides
  with a custom look.
- **Allocation** works like note block states: the `tripwire:` rows of the same data store hold each look's state,
  and states are never reused. Looks are variant x facing. The four states with no side set are handed out first, so
  a vanilla shape update could not change them even if one slipped past the guards below.
- **Ids share one number space** with note block states. Tripwire ids start at 1000, so a look still maps to one int
  through the pack builders.
- **Java pack**: a full `tripwire` blockstates file. Every vanilla string state keeps its vanilla model and rotation
  (the game's table, computed), and allocated states get the look's model. A passable block without a model of its
  own gets a `block/cross` model of its texture.
- **Geyser**: a `minecraft:tripwire` mapping with `only_override_states` that overrides each allocated state. The
  override has the textures, an empty collision box, and a geometry: the Java model's element boxes, or a cross.
- **Guards** (`NativeStateListener`) cancel the following on custom tripwire:
  - physics updates;
  - presses by entities (`EntityInteractEvent`) and players (`PHYSICAL`);
  - water and lava flow.
- **Safety net**: after a player places or breaks a block next to a passable block, its state is put back one tick
  later.

## Consequences

- At most 64 passable looks across all mods.
- They break instantly, like string, whatever `prism:mining` says.
- Pistons refuse to push into them, as with every custom block (Bukkit's piston event lists the blocks a piston
  would break as well as the ones it moves).
- A Bedrock geometry made from a Java model ignores element rotations and per-face UVs.

## Not verified by CI

- A Java client sees the flower as a cross, can walk through it, and breaks it with one hit.
- A Bedrock client sees the same and does not bump into it, with no rubber-banding.
- On both editions, walking through it does not change it.
