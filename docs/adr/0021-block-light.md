# ADR-0021: Light from custom blocks

Status: Accepted (2026-11-08). The smoke test places a lit block, switches it, builds over its light and removes it,
on Paper, Folia and Purpur. Seeing the light on clients is not covered (see below).

## Context

Row 47 of [platform-triage.md](../platform-triage.md). Lamps and working machines glow. Custom blocks are drawn by a
note block, a tripwire or a barrier with a display entity, and none of those can give light. A separate
light-emitting carrier would need its own state budget per light level, and Java has no block with enough states
for that.

## Decision

- **`prism:light`** on a block: `level` (1 to 15) and optional `variants` (default every variant).
- **A vanilla light block next to it.** While the block should glow, Prism puts `minecraft:light` (invisible, no
  collision, replaceable) with that level in a free air cell next to it: above first, then north, east, south and
  west, then below. The chosen side is saved in the chunk index (`light`), so the light goes with the block. It is
  set without physics, so it does not disturb the carrier.
- **Updates.** The light is updated when the block is placed, changes variant, is removed, or when content changes.
  When a neighbour changes, a lit block whose light cell was built into moves its light to another free side. With
  no free side the block stays dark until one frees up.
- **Bedrock.** Geyser shows the light block to Bedrock players as their own light block, so both editions get the
  same light, with no pack or mapping change.
- The example counter glows (level 12) while active.

## Consequences

- The light source is one block away from the custom block, so it is centred slightly off (above, by default).
- Players holding a light item see the light block, and can break it in creative mode. It comes back on the next
  variant change or neighbour change.
- A cell holding the light is not air. A vanilla mechanic that checks for air there (for example a sapling that
  needs room) sees a replaceable block instead.

## Not verified by CI

- Switching the ruby counter to active at night, on Java and on Bedrock: the area around it lights up; switching it
  to idle makes it dark again.
