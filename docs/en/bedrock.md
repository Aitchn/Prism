# Bedrock

Bedrock players join through Geyser. Prism detects them (Floodgate, then Geyser) and exposes
`ClientProfile.supports(ClientCapability.X)` so a mod adapts by capability, not by edition.

| Feature | Java | Bedrock |
|---|---|---|
| Item appearance | full model | flat icon from the item's texture |
| Dynamic item state | yes | no |
| Blocks (default) | borrowed state or display layer + resource pack | **real Bedrock custom block** per look, no count limit (Geyser on the same server; `bedrock.synthetic-blocks`, ADR-0024) |
| Blocks with a BetterModel model | animated model (display entity) | static custom block textured with `textures/block/<name>.png`, sent to each Bedrock player |
| Connected blocks (`prism:connections`) | display entity over a barrier | custom block with the generated shape per combination of sides |
| Passable blocks (`prism:passable`) | tripwire state + resource pack | custom block without collision |
| Any block drawn by the display layer (beyond the borrowed states) | packet-only display entities | custom block (never a display entity); **not visible** if synthetic blocks are off or not registered |
| Any block with `blocks.backend: display` | display entity | custom block; **not visible** if synthetic blocks are off or not registered |
| Block variants (`prism:variants`, `setVariant`) | yes | yes |
| Block light (`prism:light`) | vanilla light block next to it | the same light block, through Geyser |
| Custom sounds (`sounds.json`, `prism:ambient_sound`) | yes | yes (Bedrock pack `sound_definitions`, played through Geyser) |
| Animated block textures (`.png.mcmeta`) | yes | yes (flipbook) |
| Menu gauges (`prism:gauge`) | stepped icon | stepped icon (Bedrock custom item per step) |
| Menus | chest container | chest container (Geyser translates); native forms not implemented |

## How blocks reach Bedrock (ADR-0024)

Every look of every custom block gets a synthetic Java block state id above the vanilla range. Prism registers each one
with Geyser as a non-vanilla block state mapped to a real Bedrock custom block (geometry and texture from the Bedrock
pack, collision from the block's physics class, hardness from `prism:mining`), and sends that id, instead of the Java
look, to Geyser and Floodgate players only. Java players receive exactly what they did before. Prism also rewrites the
chunk sections for Bedrock players so that ids beyond the vanilla palette width read correctly.

`bedrock.synthetic-blocks` in `config.yml`:

| Value | Meaning |
|---|---|
| `auto` (default) | Register through the Geyser API when Geyser runs on this server. Without Geyser nothing changes. |
| `proxy` | Geyser runs on a proxy. Prism freezes the ids at server load and writes `plugins/Prism/geyser/prism_synthetic_blocks.json`. Needs a Prism Geyser extension on the proxy (none ships yet; the file format is described in ADR-0024). |
| `off` | The older route only: Geyser JSON mappings for the note block and tripwire looks (799 + 64 looks). |

## Setup

1. Install Geyser on the same server (`Geyser-Spigot`), and make sure custom content is on: `gameplay.enable-custom-content: true`
   (older Geyser: `enable-custom-content`) in `plugins/Geyser-Spigot/config.yml`. Prism warns in the log if it is off.
2. Start the server. Prism registers the blocks while Geyser starts and logs `PRISM_BEDROCK_SYNTHETIC_OK blocks=... base=...
   global_bits=...`. It writes `custom_mappings/prism_items.json` into Geyser's folder (and removes the older
   `prism_blocks.json` and `packs/prism.mcpack` there, since the pack is now registered through the API) and logs
   `PRISM_GEYSER_INSTALLED changed=[...]`. The Bedrock pack is kept in `plugins/Prism/geyser/packs/prism.mcpack`.
3. **Restart once** whenever content changed: Geyser reads items and registers blocks only at startup. A look added by
   a hot reload shows on Bedrock as its Java look until then.
4. If you see `PRISM_BEDROCK_SYNTHETIC_FAIL`, Prism has kept the older behaviour and says why (Geyser API unreachable,
   registration error, or Geyser never asked for custom blocks). The older Geyser files are then written for the next
   Geyser start.

Geyser on a proxy: set `bedrock.synthetic-blocks: proxy`, install Floodgate on the backend server (Prism needs it to know
who is a Bedrock player), and install a Prism Geyser extension on the proxy that registers the blocks listed in
`plugins/Prism/geyser/prism_synthetic_blocks.json` and the Bedrock pack. Without such an extension, leave the setting at
`auto` or `off`; then copy `custom_mappings/` and `packs/` from `plugins/Prism/geyser/` into the proxy's Geyser folder as
before (the older route, 799 + 64 looks) and restart the proxy.

## Limits

- With synthetic blocks, Bedrock draws every look. Without them (`off`, no Geyser API, or a failed registration) it is
  at most 799 block looks across all mods plus 64 passable looks, and looks beyond the borrowed states are invisible on
  Bedrock (see `block-looks.md`).
- On Java, `prism:mining` sets the break time of every custom block (ADR-0023). On Bedrock the break time of a synthetic block
  is `prism:mining` seconds with a bare hand, derived by Geyser from the hardness Prism registers; tools do not speed
  it up (the block has no tool tags on Bedrock). That has not been checked against a client.
- A vanilla note block always shows as its plain state, never as a custom look (ADR-0023).
- The server stores an inert carrier (a brown mushroom block or structure void) for these blocks; Geyser is shown the look through the same packet rewriting as Java players (ADR-0022), but with the synthetic id.
- Partial shapes (connected and modelled blocks) have a Bedrock outline of the right shape but full collision, as on Java (their carrier is a barrier).
- Ids are fixed for a boot (a restart is needed for looks added later), and Geyser must be a version with custom block support (2.9.5 or newer).

Nothing on this page is checked by CI with a real client; see ADR-0008 for the hand test list. The smoke test
(`PRISM_BEDROCK_SELFTEST_OK`) checks that every look of 10,000 synthetic blocks has an id and registration data and that a
Bedrock rewrite of a sample section carries them while the Java rewrite does not.
