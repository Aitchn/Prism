# ADR-0016: Connected blocks

Status: Accepted (2026-11-08). CI checks that connections follow placement and removal, that the display entity shows the model of
the current connections, and that a Bedrock look exists for them. Seeing the shapes on clients is not covered (see
below).

## Context

Cables, pipes and posts change shape with their neighbours (rows 38 and 39 of [platform-triage.md](../platform-triage.md)). Two problems stood in the way:

- A note block state is opaque to the Java client. A thin shape drawn by one hides the faces of its neighbours, so
  players see through the world around every cable.
- Every look needs its own carrier state for Bedrock, and writing a model for each combination of sides by hand does
  not scale (64 for six sides).

## Decision

- **`prism:connections`** (`connects_to`, `faces`, `core`, `arm`, `texture`) declares a connected block. `connects_to`
  holds `self`, block keys (custom or vanilla) and `capability:<key>` for the built-in capabilities. A
  `BlockConnectionBehavior` replaces the rules with code, and `BlockService.refreshConnections` redraws a block when
  that code would answer differently.
- **Prism works out the mask** (one bit per side, north, east, south, west, up, down) when the block is placed, and
  again for every neighbour of a block that is placed or removed (custom or vanilla; after a vanilla break, one tick
  later, once the block is gone). The mask is stored with the block in the chunk index. Neighbours in unloaded
  chunks or owned by another Folia region count as not connected. Generated blocks (ores) start unconnected.
- **Shapes are generated** from boxes: a centred core cube and an arm per connected side. The same boxes become a Java
  block model plus item definition per variant and mask (`<ns>:block/<name>[_<variant>]_c<mask>`) and a Bedrock
  geometry per allocated look. Files the mod ships under those names win.
- **Java: display entity over the carrier.** Connected blocks never use note block states on Java (like BetterModel
  blocks), so neighbours stay drawn. The barrier base gives full collision and outline.
- **Bedrock: per-player looks.** As in ADR-0013, each look (variant x mask) gets a note block state that only Bedrock
  players are sent. Its Geyser mapping carries the generated geometry and the texture.

## Consequences

- Java has no state budget for connected blocks; Bedrock uses one state per variant and mask. Six sides take 64
  states per variant, four take 16. A mod with many cable colours should list fewer faces or use one variant.
- Collision and the outline are a full block on both editions.
- Variant names of a connected block must not look like `c<number>`.
- Bedrock geometry mirrors x against Java; whether arms point the same way on both editions needs a client check.

## Not verified by CI

- A Java client sees the cable's arms follow its neighbours, and no holes in the blocks next to it.
- A Bedrock client through Geyser sees the same shapes, with arms on the same sides as Java.
