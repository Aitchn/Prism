# ADR-0005: Java presentation: item_model, generated pack, display-entity blocks

Status: Accepted (2026-11-04)

## Context

M2 must make custom items and blocks visible to Java players who only install a resource pack, on Paper, Folia and
Purpur, without a client mod and without PacketEvents-level complexity where a server-side component is enough.

## Decision

- **Items** carry identity in persistent data and appearance in the vanilla `item_model` component, set to the item's
  own key. The pack contains `assets/<ns>/items/<value>.json` pointing at the model. This replaces the roadmap's
  "rewrite packets per viewer": `item_model` is plain server-side data, works on every thread model, and is also the
  key Geyser uses to map Bedrock custom items (M3). Per-viewer rewriting stays available through `prism-client` for
  fallbacks (for example players without the pack) but is not needed for the baseline.
- **Pack compiler** (`prism-pack`): merges every mod's `assets/` (read from the jar into memory, never extracted),
  generates item definitions, generates a trivial model from a texture of the same name when the mod ships none
  (`item/generated`, `block/cube_all`), warns about conflicts and about definitions without assets, and writes a
  deterministic zip. The SHA-1 is the URL: `/<sha1>.zip`. Only definitions that got assets receive `item_model`.
- **Pack format number** comes from a version table with the newest known entry as fallback for newer Minecraft
  versions (reported as not exact) and an open-ended `supported_formats`/`min_format`/`max_format` range, so a new
  Minecraft release does not require a Prism release. `pack.format` in config pins it.
- **Delivery**: a built-in HTTP server (`pack.port`, default 8765, `pack.public-host`) and `Player#setResourcePack`
  with a fixed pack id, so a rebuilt pack replaces the previous one. Appearance is resolved synchronously when a mod
  activates (cheap) while compression and serving happen in the background.
- **Blocks** (`DisplayEntityBackend`): a vanilla carrier block (for example `barrier`) plus a persistent `ItemDisplay`
  whose item model is `<ns>:block/<value>`. The display is saved with its chunk, so no Prism database exists and
  restarts keep every block. Its persistent data holds the block key. Break, explosion and piston events are handled;
  pistons refuse to move custom blocks.
- **BetterModel** (M2b): a block with `prism:model_ref` is drawn by an animated Blockbench model. BetterModel only
  reads models from `plugins/BetterModel/models`, so Prism keeps generated, Prism-owned copies of every active mod's
  `prism/models/*.bbmodel` there (`prism_<ns>_<name>.bbmodel`, rewritten only when different, deleted when the mod
  goes away). This is the single exception to "mod content is never copied to the server folder" and it exists
  because of BetterModel's loader; mod authors never touch it. BetterModel is reloaded only when a file changed, its
  `build.zip` is served and sent next to Prism's pack (several packs per client since 1.20.3), and the packs are not
  merged. The BetterModel instance is not persistent: the block's anchor display entity is, and the instance is
  re-created when the anchor's chunk loads or the models reload, and closed when the chunk unloads. Only
  `prism-model` touches BetterModel types.

## Consequences

- Block appearance needs the pack; without it players see the carrier (an invisible barrier).
- Carrier blocks have vanilla properties (barrier: no sound, instant break handled by Prism). Mods choose a carrier
  that fits; a state-based backend arrives in M5.
- One display entity per block is fine for decorative blocks and machines, not for millions of blocks; this is
  recorded in [gaps](../gaps.md).
- The HTTP server needs an open port; operators who use a CDN can disable it and host the zip themselves later.

## Native-state blocks (M5, experimental)

An opt-in second block backend (`blocks.backend: native`). Each custom block is assigned one of the 799 usable note
block states by `StateAllocator`, persisted in the DataStore so assignments survive restarts and mod changes (a state
is never reused). The pack carries a generated `assets/minecraft/blockstates/note_block.json` that lists all 800
states, mapping allocated blocks to `<ns>:block/<value>` and everything else to the vanilla model. A listener
cancels tuning, neighbor-driven instrument changes and note playback for custom states. Rejected for v1 as the
default because of the collision with player-made note blocks and the state cap; `DisplayEntityBackend` stays the
default and the only backend that supports BetterModel models.
