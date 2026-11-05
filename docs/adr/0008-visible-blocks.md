# ADR-0008: Blocks both editions can see (note block states, Geyser install, variants)

Status: Accepted (2026-11-05). The server side is covered by the smoke test; what a client draws is not (see below).

## Context

Until now every custom block was an invisible `minecraft:barrier` with an `ItemDisplay` entity on top. Geyser cannot
draw item display entities, so **Bedrock players saw nothing** where a custom block stood, and Prism's Bedrock pack and
item mappings were only served over HTTP for an admin to copy by hand. One entity per block also does not scale to
large builds. Rows 14-16 and 29 of [platform-triage.md](../platform-triage.md).

## Decision

- **Note block states are the default appearance** (`blocks.backend: auto`). Each *look* of a block (variant x
  facing) gets one of the 799 non-default `minecraft:note_block` states. The assignment is persisted in Prism's data
  store and never reused, so placed blocks keep their look when mods are added or removed. Ported from the M5b native
  backend (#11).
  - Java: Prism's resource pack overrides `assets/minecraft/blockstates/note_block.json`, drawing each allocated state
    with the look's model and facing rotation; free states keep the vanilla note block model.
  - Bedrock: Prism writes Geyser custom block mappings (`minecraft:note_block`, `only_override_states`) whose
    `state_overrides` give each state its textures (from the model's `textures`) and rotation, and adds the textures
    to the `.mcpack` (`terrain_texture.json`).
  - Identity still comes from the chunk index (ADR-0007), never from the state.
  - Blocks with a BetterModel `prism:model_ref` stay on display entities (models need an entity to animate), and
    `blocks.backend: display` restores the old behaviour for everything. Existing blocks keep the appearance they were
    placed with.
- **Prism installs into Geyser.** After every pack build it writes `custom_mappings/prism_items.json`,
  `custom_mappings/prism_blocks.json` and `packs/prism.mcpack` into `plugins/Geyser-Spigot/`, only when bytes changed,
  and logs `PRISM_GEYSER_INSTALLED changed=[...]`. Geyser reads these only at startup, so new content needs one restart.
  It warns if Geyser's `enable-custom-content` is off. Without a local Geyser (Geyser on a proxy) the same files go to
  `plugins/Prism/geyser/` to copy over (`PRISM_GEYSER_EXPORTED`).
- **The Bedrock pack version follows its content** (hash), because Bedrock caches packs by uuid and version.
- **Variants.** `prism:variants: [idle, active]` declares named looks; the first is used on placement (unquoted `on`/`off`/`yes`/`no` are YAML booleans and are rejected).
  `BlockService.setVariant(block, name)` switches at runtime and `CustomBlock.variant()` reads it back. Variant
  `v` of `ns:x` is drawn with model `ns:block/x_v` (generated from texture `block/x_v.png` when no model file exists).
- **Breaking.** Note block carriers are broken by the client at note block speed (0.8 hardness, axe); Prism's
  server-side dig (ADR-0007) only runs for barrier carriers, so `prism:mining` applies to display blocks only.
- **Vanilla behaviour is suppressed** for custom note blocks: no re-tuning on right click, no note when powered, no
  instrument change from the block below.

## Consequences

- Up to 799 looks across all mods. Running out logs `PRISM_NATIVE_OUT_OF_STATES` and the block is not placed.
- Real (uncustomised) note blocks still work, but a vanilla note block tuned into a state a custom look owns will look
  like that custom block. Acceptable for now; a later carrier choice (mushroom blocks, tripwire) can add more states.
- Mods must not set note block data on custom blocks themselves.

## Not verified in CI (hand test with real clients)

- Java: custom blocks look right, rotate with facing, switch look on right click (`examplemod:ruby_counter`).
- Bedrock through Geyser, after one restart: the same blocks are visible, textured, rotated the same way (the sign of
  Geyser's rotation is a guess), and break; custom items show their icons.
- Geyser on a proxy: copying `plugins/Prism/geyser/` works.
