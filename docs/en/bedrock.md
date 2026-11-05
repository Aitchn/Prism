# Bedrock

Bedrock players join through Geyser. Prism detects them (Floodgate, then Geyser) and exposes
`ClientProfile.supports(ClientCapability.X)` so a mod adapts by capability, not by edition.

| Feature | Java | Bedrock |
|---|---|---|
| Item appearance | full model | flat icon from the item's texture |
| Dynamic item state | yes | no |
| Blocks (default) | note block state + resource pack | note block state + Geyser custom block mapping |
| Blocks with a BetterModel model, or `blocks.backend: display` | display entity | **not visible** (Geyser cannot draw display entities) |
| Block variants (`prism:variants`, `setVariant`) | yes | yes |
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

- 799 block looks (variant x facing) across all mods.
- Custom blocks drawn as note blocks break like note blocks (axe, quick); `prism:mining` applies only to display blocks.
- A vanilla note block tuned to a state owned by a custom look shows that look.

Nothing on this page is checked by CI with a real client; see ADR-0008 for the hand test list.
