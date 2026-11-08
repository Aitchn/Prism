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

## Round 2 (2026-11-05): gaps found while growing the rebuilds to about 80%

Sources: the Mekanism migration (`docs/gaps.md`, plan in the project's `mekanism-validation/scope-and-stages.md`)
and the AE2 plan (`ae2/scope-plan.md`). Botania's report is still to come and will be added here. Same two rules:
the theme test and "a player must be able to operate and see it".

| # | Gap | Owner | Why | Player-facing result (Java / Bedrock) | Needed by |
|---|---|---|---|---|---|
| 31 | Block drops that carry the block's data; placement that reads the placing stack | Prism | Any block that keeps something: a chest keeping its contents, a charged battery, a tank | Break a machine, place it again, its contents and charge are back. The stack's lore shows what it carries on both editions | Mekanism stage 1 (energy cube, upgrades) |
| 32 | An interact behavior that lets the click through | Prism | Any block that reacts to some clicks only, such as wrenchable cables you also build against | Clicking a cable with a block in hand places the block | Mekanism stage 3, AE2 stage 3 |
| 33 | Synchronous slot click hook in `ContainerMenu` | Prism | Any GUI where the server also changes the slots (machines, factories, terminals) | No item flicker or double take when a machine and a player touch the same slot | Mekanism stage 1-4, AE2 stage 4 |
| 34 | `PrismScheduler.runGlobalTimer` | Prism | Any server-wide clock (networks, daily resets) | none (correctness on Folia) | Mekanism now |
| 35 | Slotless item handler capability (insert/extract by stack, with simulate), with an adapter for vanilla containers | Prism | Items are a vanilla resource every mod moves: pipes, hoppers-like blocks, storage buses. Unlike energy or mana, the unit is not a mod's design | Mods' pipes and buses move items into each other and into chests | AE2 stage 4, Mekanism stage 3 |
| 36 | World generation of custom ore blocks (row 28) | Prism | Every mod with ores or flora | Find the ore in new chunks, mine it, get its drop, on both editions (note block carrier, so it is visible on Bedrock) | Mekanism stage 1, AE2 stage 2 |
| 37 | Text input in menus (row 18) | Prism | Search boxes, amounts, names | Type a search term in a prompt; an anvil screen works on Java and Geyser shows it on Bedrock | AE2 stage 4 |
| 38 | Looks chosen from neighbours (connected cables) and thin or no-collision carriers (rows 13/14) | Prism | Cables, pipes, fences, flowers in every tech or magic mod | A cable shows arms towards what it connects to; walk through flowers. Limited by the number of carrier states (see below) | Mekanism stage 3, AE2 stage 3 |
| 39 | Generated connection variants in batches (one look per neighbour mask without writing 64 files) | Prism, with 38 | Same as 38; writing every mask by hand does not scale | none beyond 38 | AE2 stage 3 |
| 40 | Structure (multiblock) pattern service (row 23) | Prism (mechanism only) | Formed structures exist in many mods (tanks, furnaces, altars); the pattern check on member change is the same everywhere | A structure forms when its last block is placed and unforms when one is broken | Mekanism stage 5 |
| 40b | What a formed structure does (dynamic tank, induction matrix, ME controller rules) | **Plugin** | Theme-specific | n/a | |
| 41 | Several parts on one block face (AE2 cable bus parts), per-part hit test | **Plugin**, with a Prism note | The part model (what a bus is, how parts stack) is AE2's design. Prism already gives the hit position (`BlockContext.hitPosition`) and per-block data; a part can be drawn only as a block look, so the state budget in 38 applies | Parts as looks of the cable block; clicking the face where a part sits reaches it | AE2 stage 3 |
| 42 | Entity-free display of items moving through transporters | Prism (later, undecided) | Any visible item transport; display entities are invisible on Bedrock, so no edition-neutral solution exists yet | none yet | Mekanism stage 3, decide then |
| 43 | Chunk move API (spatial IO) | Out of scope | Excluded from the 80% target by the AE2 plan | n/a | |
| 44 | Custom sounds from the mod's jar (Java `sounds.json`, Bedrock `sound_definitions.json`) and a looping sound bound to a block (Mekanism E6) | Prism | Every mod with its own sounds | Same sound on both editions; a machine's hum stops when it stops or goes | Mekanism effects |
| 45 | Particles | **Plugin** (Bukkit API) | Vanilla particles already reach both editions (Geyser maps most); servers cannot add particle types | Prism documents which particles look different on Bedrock | n/a |
| 46 | Animated block and item textures: Java `.mcmeta`, Bedrock flipbook (E2) | Prism | Any animated panel, fluid or glow | Animated on both editions | Mekanism effects |
| 47 | Light level per block variant (E1) | Prism | Any lit machine or lamp | Bedrock: through the Geyser mapping. Java: a note block cannot emit light, so a lit look needs a light-emitting carrier (state budget) | Mekanism effects |
| 48 | A Bedrock look for blocks drawn by a model | Prism | Display entities are invisible on Bedrock, so every animated block of every mod was invisible there | Java: the animated model. Bedrock: a static block with the block's texture. ADR-0013 | all rebuilds |
| 49 | Gauges and progress in menus as stepped item icons; full custom menu backgrounds | Prism | Every machine GUI | Icons on both editions; custom backgrounds are Java only (Bedrock keeps the chest UI). Gauges done in ADR-0019; backgrounds open | Mekanism, AE2 |
| 50 | Worn and held item models (jetpack, tools) (E5) | Prism | Any armor or tool mod | Java: equippable assets. Bedrock: needs attachables; partial | Mekanism effects |
| 51 | HUD feedback (action bar, boss bar, titles) | **Plugin** (Bukkit API) | Works on both editions already | n/a | n/a |
| 52 | Contents shown in tanks and pipes | Prism (with 38) | Any tank or visible transport | Fill levels as variants on both editions (state budget); smooth rendering is Java only | Mekanism |
| 53 | Block-bound particle emitters and beams between two points (E7) | Prism | Lasers, portals, rituals | Particles on both editions; guardian or end-crystal style beams are Java only | Mekanism effects |
| 54 | Entities as moving block parts (E8) | Prism (via BetterModel, row 48 for Bedrock) | Any machine with moving parts | Java: animated; Bedrock: static look from row 48 | Mekanism effects |

**The carrier state budget (rows 38, 39, 41).** Bedrock can only see custom blocks drawn by real block states (Geyser
cannot draw display entities). Every distinct look needs its own state. The note block gives 799 states for full
cubes. More carriers can be added: the three mushroom blocks give 192 more full-cube states, and tripwire gives 128
states with no collision and a thin outline (flowers, wires). A cable with 64 connection masks in several colours
would use thousands of states, more than every carrier together. So connected looks are offered with a budget:
- a mod declares which neighbours connect;
- Prism computes the mask and maps it to a look;
- a mod can reduce the masks it needs, for example straight, corner, junction and end with rotation, which is 5-10
  looks instead of 64;
- Prism reports in the log how many states each mod uses.
Truly free shapes (64 masks x 16 colours) stay Java-only display looks, and the docs say so.

### Order of work (round 2)

1. **Small primitives** (31-34): drops and placement with data, click pass-through, synchronous slot hook,
   `runGlobalTimer`. Mekanism stage 1 needs 31 and 34 now.
2. **World generation** (36). Mekanism stage 1 and AE2 stage 2 need ore.
3. **Item handler capability** (35) and **text input** (37).
4. **Connected looks and carriers** (38, 39), then the **structure service** (40). Connected looks and generated
   shapes are done in ADR-0016 (display entity on Java, per-player states on Bedrock); passable, no-collision
   blocks on tripwire states (the rest of 38) in ADR-0017. The structure service (40, 23) is in ADR-0018;
   boxes of variable size (`prism:cuboid`) in ADR-0020.
5. Botania's rows, once reported.
