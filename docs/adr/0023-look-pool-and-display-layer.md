# ADR-0023: Look pool and display layer (stage 2)

Status: Accepted (2026-11-08). Stage 2 of the block carrier redesign (stage 1 is ADR-0022). The pure logic (pool,
allocation, canonicalization table, face splitting, pack output, break speed maths) is covered by unit tests and a
catalogue check against the game's own blockstate files. What a real client draws, and the packet layer against a real
PacketEvents and Paper, is not (see "Not verified").

## Context

Stage 1 made the index the only truth and the server carrier inert, but a Java client can still tell apart only as many
custom blocks as there are note block states: 799. Every look (variant x facing) owned one of them for good, in a
persistent `StateAllocator`. The goal of this stage is as many looks as a mod wants (10,000 and more), rendered and
played like vanilla, with only the server and the resource pack: no client mod, no replaced block registry.

Two things stand in the way. There are not enough vanilla block states whose model the pack may replace without
changing how real vanilla blocks look. And a persistent allocator ties the world to a table that must never change,
which does not scale to thousands of looks that mods add and remove.

## Decision

### 1. Looks are allocated per boot

`LookAllocator` hands out client representations from the registered content every time the content changes. Looks are
visited in id order, so the same content gives the same table, the same blockstate files and the same pack bytes (the
pack hash is stable for unchanged content, and a look of `examplemod:` keeps its state when a mod that sorts later is
added). Nothing is stored: the world and the index keep look names (key, variant, facing), never a client state.
`StateAllocator` and its stored data are gone; there is nothing to migrate, because stage 1 already made the index the
truth and the allocator data only said which state a look drew with.

### 2. S tier: borrowed vanilla states

A **family** (`BorrowedFamily`, catalogue `LookPool`) is a vanilla block whose states may be re-modelled by the pack:

- `borrow`: the property values the borrowed states take; a pinned property has one value.
- `invisible`: properties the block's vanilla blockstate file does not use to choose a model, with the value real blocks
  are shown with. A real block in a borrowed state is shown as its **canonical sibling** (invisible properties set to
  their canonical values), which the pack draws exactly as vanilla does.
- `guard`: properties that must have a value for that to hold (`waterlogged=false`).
- A **class** (`PhysicsClass`): collision (full or none), whether it occludes, and its sound group.

| Family | Class | Borrowable states |
|---|---|---|
| `note_block` | full, opaque, wood | 799 (800 pool states, one set aside as a shell) |
| `tripwire` (disarmed) | none, clear, stone | 64 |
| `target` | full, opaque, grass | 15 |
| `dispenser`, `dropper` | full, opaque, stone | 6 each |
| `spruce_leaves`, `birch_leaves` | full, clear, grass | 13 each |
| `sugar_cane` | none, clear, grass | 15 |

That is 931 states in six classes. The note block and tripwire come first because Bedrock mappings (Geyser) exist only
for them; `LegacyIndex` maps their states to the indexes the Bedrock pack and Geyser mappings use.

Verification: `tools/verify-borrowed-families.py` reads the game's `blockstates/*.json` and the block summary of the
same version and checks, for each family, that (1) the model the replacement blockstate gives every real state is the
model the game gives it, (2) every borrowed state resolves to the same model, rotation and variant as its canonical
sibling, and (3) the properties and values the catalogue enumerates are the block's own. It was run against 26.3 and a
26.4 snapshot, and it fails (and names the difference) on a deliberately altered catalogue.

Left out on purpose: mushroom blocks (each face shows or hides, so the states differ), anything waterlogged, fire and
redstone ore (particles), leaves that drop leaf particles (oak, jungle, acacia, dark oak, mangrove, cherry, pale oak),
doors, trapdoors and fence gates (the client changes their state on neighbour updates), and every block whose collision
is neither a full cube nor empty. The replacement blockstate file of a family lists every state of the server's block
(not a hard-coded list; the note block alone has 27 instruments), reproducing the vanilla model for real states.

A look that wants a state takes the next unused borrowed state of a family of exactly its class. Class comes from
`Appearances.physics`: passable gives none/clear/stone, solid gives full/opaque/wood, both overridable with the new
`prism:surface` component (`sound`, `occludes`). A client predicts collision, occlusion and the sound of a state itself,
so a look must be given a state of the right class or it would stop players wrongly, shade neighbours wrongly or sound
wrong.

### 3. D tier: shells and packet-only display entities

When no borrowed state of the class is left (or the block says `prism:render: display`), the look is drawn by the display
layer:

- **Shell.** The server-side carrier is unchanged; clients are shown the invisible **shell** state of the closest class.
  A shell is one reserved state per class (`tripwire`: none/clear/stone, `spruce_leaves`: full/clear/grass,
  `sugar_cane`: none/clear/grass; `note_block`: full/opaque/wood, opt-in) whose model the pack replaces with
  `prism:block/shell`, a cube with a fully transparent texture. Collision, occlusion and sound are the vanilla ones of
  that state. A transparent texture is only invisible in the cutout and translucent layers, so the opaque note block
  shell (solid layer: transparent texels render opaque black) is off unless `blocks.display.solid-shells` is set. Without
  it, a block that wants full/opaque falls back to the full/clear/grass shell, and the allocator logs how many looks
  were relaxed (same collision, then occlusion, then sound).
- **Face split.** At pack build, `DisplayModels` resolves the model (the parent chain, textures, elements) and, when it
  is splittable (axis-aligned elements, no inner faces, a parent the builder knows), splits it by face into up to six item
  models `prism:face/<hash>` (content-hashed so equal faces are shared across looks), with item definitions under
  `assets/prism/items/face/`. A model that cannot be split becomes one whole item model.
- **Entities.** Per viewer, an `item_display` is spawned in packets only (`DisplayEntities`, PacketEvents; nothing exists
  on the server). For each face, the entity is placed in the **neighbouring cell on that face** and translated back onto
  the block (`translation = -offset`), turned by the block's rotation. The client lights an entity from the cell it is in,
  which is the cell that lights a boundary face of a vanilla block, so the face is lit like vanilla (per-face, flat
  shading). A face whose neighbour hides it is skipped (`DisplayParts`). A model that is not split is anchored in the
  most exposed (brightest) neighbour cell.
- **Lifecycle.** `DisplayLayer` spawns a chunk's entities when the client has the chunk (`PlayerChunkLoadEvent`), destroys
  them when it unloads or the viewer changes world or respawns, and re-evaluates a block, for every viewer, when it or
  one of its six neighbours changes (place, break, explosion, piston, Prism's own look changes). A diff is sent, not a
  respawn. Neighbour occlusion is read from the world only where the current thread owns the cell (Folia); elsewhere the
  neighbour is taken as not hiding the face, which draws an unseen face behind the neighbour's own surface. Bedrock
  viewers get no entities.
- **Cap.** `blocks.display.view-distance` (chunks, default 8; 0 = every chunk the client has) limits how far from the
  viewer entities exist, and `view-range` sets the entities' view range multiplier. Moving across chunk borders shows
  and hides chunks at the edge.

### 4. Canonicalization in the rewrite layer

The packet rewriter now also covers worlds without custom blocks when a family is in use. A chunk section whose palette
contains a state of a borrowed family is rewritten so real blocks show their canonical sibling (`ClientCanon`, an
`int[]` over the global state ids of the server version, built from the same `LookTable`); sections whose palette has
none are not touched. Single and multi block changes get the same, except at positions that hold a custom block (they
get the look; the look states themselves are never canonicalized).

### 5. Dig feel

A client predicts how long it takes to break the block it was shown: a borrowed state or a shell has the hardness and
tool rules of the vanilla block, not of the custom block. When a survival player starts digging a custom block that the
client breaks, `BreakFeel` adds a transient `block_break_speed` modifier with the factor from `DigMath.breakSpeedFactor`
(custom speed times the client's break seconds over the client's speed times the custom hardness; haste, fatigue, water
and air cancel). It is removed when digging aborts or finishes and when the player leaves, never saved, and replaced on
every new dig. A state the client breaks instantly (tripwire and sugar cane, so every passable look) cannot be slowed: the factor
is NaN, nothing is applied, and such blocks keep breaking instantly, as they did in stage 1. The server stays authoritative: a survival break that arrives before half the custom break time has passed
(vanilla accepts at 0.7) is refused and the block resent.

### 6. `prism:render` and `prism:surface`

`prism:render: auto | state | display` (prism-api, plain data, no PacketEvents types). `auto` (default) takes a borrowed
state while one is left and falls back to the display layer; `state` asks for a borrowed state and logs when it had to
fall back; `display` always uses the display layer (for a block that should not take a scarce state, or has a model that
should not be a block model). `prism:surface` sets the sound group and whether it hides neighbours.

## Alternatives considered

- **A persistent allocator with more states.** Does not scale beyond the 931 safe states, and keeps the stage 1 coupling.
- **Mushroom blocks and other multi-face blocks as extra states.** 4096+ states, but each face is drawn or not, so a look
  would change how real mushrooms look; there is no canonical sibling that draws the same.
- **Display entities on the server (persistent, as BetterModel does).** Simple, but entities are ticked, saved, loaded
  and tracked; thousands of blocks mean thousands of entities. Packet-only entities cost nothing on the server and only
  exist for viewers who can see the chunk.
- **One display for a whole model, anchored at the block.** The client lights it from the block's own cell, which for the
  invisible shell is full-bright or dark, so the model is lit wrongly. Face entities in neighbouring cells are lit like
  vanilla faces.
- **Block displays instead of item displays.** A block display can only show a block state, so it would again need a
  borrowed state per look; an item model can show any model.
- **A client mod, or a replaced registry.** Out of scope: it must work with only the server and the pack.

## Consequences

- No hard cap on looks. The cost of a D-tier block is up to six entities per viewer within the view cap.
- D-tier blocks have the physics of the closest shell. A full/opaque look without the opt-in note shell is shown as a
  non-occluding grass-sound cube to the client: neighbouring faces stay drawn, and the client plays grass sounds for it.
  The display layer itself still skips faces behind other opaque display blocks.
- Real vanilla blocks of a family are replaced by a sibling in every outgoing packet; a protocol translation layer
  (ViaVersion) converts the ids after this listener, as in stage 1. Plugins that read block data from packets before
  Prism will see the ids of the server.
- While a family is in use, every chunk packet is inspected, in every world: a section is decoded and re-encoded only if
  its palette holds a state that is replaced, but the palette scan is not free on a server with many players.
- Bedrock sees only S-tier looks of the note block and tripwire families (Geyser mappings), and blocks that Prism draws
  for Bedrock only. A display-layer block is invisible on Bedrock.
- A block of a mod that is not installed has no look in this boot: viewers see the carrier. (Stage 1 kept the old state.)
- Per-boot allocation reshuffles borrowed states when content changes; clients receive a new pack and the new states in
  the same session, and the rewrite uses the current table for every packet.

## Not verified

- No client was connected, and the PacketEvents and Paper code (rewriter, display entity packets, the attribute and event
  code) could only be type-checked against PacketEvents sources and reviewed, not run. The unit tests cover pool
  partitioning, canonicalization, face splitting and entity placement maths, allocation determinism, break speed and
  pack output; the smoke test's synthetic self test (`-Dprism.selftest.synthetic=10000`) checks 10,000 looks end to end
  on a server once CI runs it.
- Entity data indexes of `Display` and `ItemDisplay` (translation 11, left rotation 13, view range 17, item 23) are those
  of 1.20.2 to 1.21; check them against the running version.
- What to look at on a real client: a D-tier cube beside stone, lit by a torch on one side; faces behind a wall are not
  drawn; breaking a custom block feels as long as `prism:mining` says; a real note block, dispenser or leaves next to
  custom blocks look vanilla; a player crossing the view cap sees blocks appear and disappear at its edge.
