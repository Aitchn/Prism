# Bedrock

Bedrock players join through Geyser. Prism detects them (Floodgate, then Geyser) and exposes
`ClientProfile.supports(ClientCapability.X)` so a mod adapts by capability, not by edition.

| Feature | Java | Bedrock |
|---|---|---|
| Item appearance | full model | flat icon from the item's texture |
| Dynamic item state | yes | no |
| Blocks (default) | note block state + resource pack | note block state + Geyser custom block mapping |
| Blocks with a BetterModel model | animated model (display entity) | static block textured with `textures/block/<name>.png` (a plain note block if there is none), sent to each Bedrock player |
| Connected blocks (`prism:connections`) | display entity over a barrier | generated shape per combination of sides, sent to each Bedrock player |
| Passable blocks (`prism:passable`) | tripwire state + resource pack | tripwire state + Geyser mapping without collision |
| Any block drawn by the display layer (beyond the borrowed states) | packet-only display entities | **not visible** (Geyser cannot draw display entities) |
| Any block with `blocks.backend: display` | display entity | **not visible** (Geyser cannot draw display entities) |
| Block variants (`prism:variants`, `setVariant`) | yes | yes |
| Block light (`prism:light`) | vanilla light block next to it | the same light block, through Geyser |
| Custom sounds (`sounds.json`, `prism:ambient_sound`) | yes | yes (Bedrock pack `sound_definitions`, played through Geyser) |
| Animated block textures (`.png.mcmeta`) | yes | yes (flipbook) |
| Menu gauges (`prism:gauge`) | stepped icon | stepped icon (Bedrock custom item per step) |
| Menus | chest container | chest container (Geyser translates); native forms not implemented |

## Setup

1. Install Geyser on the same server (`Geyser-Spigot`), and make sure custom content is on: `gameplay.enable-custom-content: true`
   (older Geyser: `enable-custom-content`) in `plugins/Geyser-Spigot/config.yml`. Prism warns in the log if it is off.
2. Start the server. Prism writes `custom_mappings/prism_items.json`, `custom_mappings/prism_blocks.json` and
   `packs/prism.mcpack` into Geyser's folder and logs `PRISM_GEYSER_INSTALLED changed=[...]`.
3. **Restart once** whenever that line lists changed files (first start, or after adding or changing content): Geyser
   only reads mappings and packs at startup.

Geyser on a proxy instead: Prism writes the same files to `plugins/Prism/geyser/`; copy `custom_mappings/` and `packs/`
into the proxy's Geyser folder and restart the proxy.

## Limits

- Bedrock sees at most 799 block looks (variant x facing, or variant x combination of sides) across all mods, plus 64 passable looks. Looks beyond the borrowed states are drawn by the display layer on Java only and are invisible on Bedrock (see `block-looks.md`).
- On Java, `prism:mining` sets the break time of every custom block (ADR-0023). Bedrock players break custom blocks drawn as note blocks like note blocks (axe, quick); that has not been checked against a client.
- A vanilla note block always shows as its plain state, never as a custom look (ADR-0023).
- The server stores an inert carrier (a brown mushroom block or structure void) for these blocks; Geyser is shown the look through the same packet rewriting as Java players (ADR-0022).

Nothing on this page is checked by CI with a real client; see ADR-0008 for the hand test list.
