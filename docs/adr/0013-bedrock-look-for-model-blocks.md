# ADR-0013: Bedrock look for blocks drawn by a model

Status: Accepted (2026-11-08). The look's state allocation and Bedrock mapping are covered by the model self test and
the Bedrock pack in the smoke test. What a Bedrock client shows is not covered (see below).

Update: when synthetic Bedrock blocks are active (ADR-0024), the per-player block change carries the look's synthetic state id instead of the note block state; the note block route here is the fallback.

## Context

Row 48 of [platform-triage.md](../platform-triage.md). A block with `prism:model_ref` is drawn by BetterModel through
display entities, on a barrier carrier. Geyser cannot draw display entities, so Bedrock players saw nothing. They
could not find, aim at or recognise machines, which breaks the "operable and visible on both editions" rule for every
animated block of every mod.

## Decision

- **Every look of a model block also gets a note block state**, as long as `blocks.backend` is not `display`. The
  Bedrock pack and the Geyser mapping draw that state with the block's texture (`textures/block/<name>.png`, or its
  variant textures). The Java pack does not map it, because Java players never receive it.
- **Bedrock players get the state per player, and the world does not change.** The world keeps the barrier and the
  model, so Java is unchanged. When a chunk is sent to a Bedrock player (`PlayerChunkLoadEvent`), Prism sends that
  player a block change for every model block in the chunk. A placement or a variant change is sent to the Bedrock
  players in view.
- The following were rejected:
  - **A note block carrier for everyone, with an empty Java model.** A note block occludes its neighbours on Java, so
    the faces next to the block would be culled and players would see holes through the ground around every
    machine. A resource pack cannot change occlusion.
  - **Rewriting chunk packets.** It is more work and more fragile than block changes sent after the chunk.

## Consequences

- Each look of a model block uses a note block state (out of 799), like a native block.
- Interaction is unchanged: the server block is the barrier. Clicks and Prism's server-side dig work the same as
  for Java players.
- A Bedrock player sees a static block, not the animation. The block's texture is the mod's choice. Without one the
  pack logs a warning, and Bedrock players see a plain note block, which is still visible and clickable.

## Not verified by CI

- A Bedrock client near the example turret sees a grey block with a red centre, both on first join and after
  placing a new turret. A Java client sees the animated turret.
