# Prototype: Blockbench model to Bedrock geometry

Status: prototype, converter implemented and unit-tested; **not yet checked on a Bedrock client** (needs a real
Geyser + Bedrock session, which the CI and this sandbox do not have).

## What exists

`prism-pack` contains `BedrockGeometry.convert(identifier, bbmodelJson)`, which turns a `.bbmodel` (the asset format
BetterModel uses) into a Bedrock `minecraft:geometry` 1.12.0 document and returns a list of warnings for everything
it could not carry.

| Blockbench | Bedrock | Notes |
|---|---|---|
| cube `from`/`to` | `origin` + `size` | X is mirrored (`origin.x = -to.x`), as in Blockbench's own Bedrock export |
| group (outliner) | bone with `parent`, `pivot`, `rotation` | names are lower-cased and made unique |
| cube rotation | cube `pivot` + `rotation` | X and Y rotation negated, Z kept |
| box UV | `uv: [u, v]` | |
| per-face UV | `uv: {face: {uv, uv_size}}` | faces without a texture are omitted |
| mesh elements | skipped, warning | no Bedrock equivalent in geometry 1.12 |
| animations | dropped, warning | output is a static pose |
| several textures | first only, warning | one texture per geometry |

## Findings for M3

1. The geometry itself is straightforward; the hard part is **how a Bedrock player sees a BetterModel model**.
   BetterModel renders on Java with display entities that carry item models from the Java pack. Whether Geyser
   translates those display entities into something that can show a custom geometry depends on Geyser's display
   entity support and its custom item v2 API. This must be tested with a real client before M3 commits to a design.
2. Plan A (M3): register the model as a Geyser custom item with the generated geometry as an attachable, and show it
   through the carrier entity Geyser already translates. Plan B: show a static icon or the carrier block and tell the
   Mod through `ClientProfile` so it can offer an alternative.
3. The conversion rules above (mirroring and rotation signs, east/west face naming) are taken from how Blockbench
   exports Bedrock models and are covered by unit tests of *our* expectations only. The first client test may require
   adjustments; the unit tests are where the corrected rules should be pinned.
4. Animations would need a second converter to Bedrock animation files; out of scope until Plan A is confirmed.
