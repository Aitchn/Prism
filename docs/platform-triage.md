# Platform triage of the validation gaps

The Botania, AE2 and Mekanism rebuilds (`validation/*`, see [gaps.md](gaps.md) and `docs/validation/`) produced about
forty gap reports. This file merges them and decides, for each one, **who owns it**:

- **Prism**: a platform primitive every content mod needs, whatever its theme. Prism provides the mechanism only.
- **Plugin**: a concept that belongs to one mod's own design (energy units, mana, channels, gas types, machine
  recipes, radiation). It stays in that plugin, built on Prism's primitives.

Rule applied to every row: *would a mod with a completely different theme need the same primitive?* If the answer
depends on the theme, it is plugin-owned. "Mod X has Y" is never a reason on its own.

A second rule comes from the maintainer: nothing may exist that a player cannot actually operate or see. Every Prism
row below therefore states how it reaches a player on Java and on Bedrock.

## Merged list

| # | Gap (merged) | Owner | Why | Player-facing result |
|---|---|---|---|---|
| 1 | Place / remove hooks for custom blocks (any cause) | Prism | Every block with state must know it exists | Block reacts the moment it is placed or broken |
| 2 | Chunk load / unload hooks and "custom blocks in this chunk" | Prism | Anything that ticks or joins a network must re-register after a restart | Machines resume after restart without a click |
| 3 | Neighbour changed | Prism | Connections, redstone-like reactions, structures | Cables and structures update when a neighbour changes |
| 4 | Per-block persistent data, saved with the chunk, removed with the block | Prism | Every stateful block | Contents survive restarts and vanish with the block |
| 5 | `identify` scans entities | Prism (internal) | Performance of every other primitive | none (speed) |
| 6 | Block ticking, batched per chunk, region-correct | Prism | Every machine, flower, generator | Things work without being touched |
| 7 | Public scheduler and the documented cross-region rule | Prism | Folia safety for every mod | none (correctness) |
| 8 | Sided capability lookup between blocks (`BlockCapability<T>`) | Prism | Any transport between blocks | Mods connect to each other |
| 9 | Shared energy / fluid / chemical / mana types | **Plugin** | Units and semantics are a mod's design; Prism only carries the lookup (8) | n/a. A shared convention can live in a separate, optional library later |
| 10 | Interaction context: face, hit position, hand, held stack; clicked block on item use | Prism | Wrenches, directional placement, "use on block" items | Correct reaction to where and with what the player clicked |
| 11 | Placement facing / rotation | Prism | Any directional block | Block faces the player when placed |
| 12 | Survival breaking of the `barrier` carrier, hardness | Prism | Every block; today survival players cannot break custom blocks at all | Block breaks with crack animation and drops |
| 13 | Non-full hitbox / no collision carriers | Prism (later) | Flowers, cables, decorations | Walk through flowers |
| 14 | Block appearance variants (state, facing) and runtime switching | Prism | Any block whose look depends on state | Active vs idle, facing, connected cable looks |
| 15 | Custom blocks are invisible on Bedrock (Geyser cannot draw item displays) | Prism | Every block, every Bedrock player | Bedrock players see the block |
| 16 | Custom item icons not registered with Geyser | Prism | Every item, every Bedrock player | Bedrock players see the item |
| 17 | Menus with real slots, click types, fixed layout, live refresh | Prism | Any GUI beyond a button list | Drag items in, watch progress, no flicker |
| 18 | Text input in menus; native Bedrock forms | Prism (later) | Search boxes, amounts | Typing a search term |
| 19 | Per-stack item data and dynamic lore | Prism | Tools, storage items, charged items | Item shows its own stored value |
| 20 | Runtime lookup of item and block definitions | Prism | Reading components during play | none |
| 21 | Recipe registry: mods register recipe *types*, recipes load from YAML | Prism (mechanism) | Every mod with processing | Recipes are data, other mods can add to them |
| 21b | The machine recipe types themselves (enrichment, infusion, petal) | **Plugin** | Theme-specific | n/a |
| 22 | Vanilla crafting / smelting recipes producing custom items | Prism | Every mod needs survival access to its items | Craft custom items in a crafting table |
| 23 | Multiblock: structure pattern matching on member changes | Prism (later) | Generic once 2 and 3 exist | Structures form and break |
| 23b | What a formed multiblock does (tank, reactor, CPU) | **Plugin** | Theme-specific | n/a |
| 24 | `DataStore`: async, batched, transactional writes | Prism (later) | Every mod that persists a lot | none (lag) |
| 25 | Getting items in game: `/prism give`, item browser | Prism | Every mod; today only per-mod commands exist | Admins and testers can get every item |
| 26 | Content variants / templates in YAML | Prism (later) | Colour sets, tiers | none |
| 27 | Particles / sounds that degrade on Bedrock | Prism (later) | Every mod | Effects on both editions |
| 28 | World generation hook for custom blocks | Prism (later) | Ores, flora | Find content in the world |
| 29 | Thousands of blocks (one entity per block) | Prism | Every large build | No lag with big bases; covered by 15 (native states) |
| 30 | Radiation, QIO, channels, mana bursts, spreader aiming, cell byte model, patterns | **Plugin** | Theme-specific | n/a |

## Order of work

1. **Block platform** (1-8, 10-12, 20, 25, done in ADR-0007): lifecycle, chunk index, per-block data, ticking, scheduler, capabilities,
   richer contexts, facing, survival breaking, definition lookup, `/prism give`.
2. **Visibility** (14-16, 29, done in ADR-0008): native block states as the default for Bedrock-visible blocks, Geyser block and item
   mappings installed into Geyser's folders, appearance variants and facing.
3. **Menus and stacks** (17, 19, done in ADR-0009): slot menus rendered as chest containers (Geyser translates them for Bedrock), per-stack
   data and lore.
4. **Recipes** (21, 22, done in ADR-0010).
5. Later rows (13, 18, 23, 24, 26-28) after the validation plugins move to 1-4.

## What cannot be verified in CI

CI starts real Paper, Folia and Purpur servers and runs server-side self tests, but no game client joins. The following
must be checked by hand with a real Java client and a real Bedrock client through Geyser; each PR lists its own items.

- Breaking a custom block in survival: crack animation, timing, drops.
- That blocks and items look right on Java (resource pack) and on Bedrock (Geyser pack and mappings).
- Menus: clicking, dragging, shift-click, live refresh, on both editions.
