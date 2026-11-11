# ADR-0025: Display layer first, one entity per block

Status: Accepted (2026-11-11). Changes the defaults of ADR-0023 (stage 2); ADR-0022 (carrier) and ADR-0024 (Bedrock)
still hold. The pure logic (settings, allocation, cull, budget, per-viewer table, pack output) is covered by unit tests,
and the smoke test's synthetic self test runs the selection against a real Paper world. What a real client draws, the
cost on a real client and the packet layer against a live connection are not verified (see "Not verified").

## Context

ADR-0023 gave every look a borrowed vanilla state (note block, tripwire, target, leaves, ...) while one of its class was
left, and drew the rest with the display layer: an invisible shell block plus packet-only item display entities, one
per visible face. In practice that order is backwards for most servers:

- The pool is small (931 states, split by class) and is shared by every mod, so which block gets a state and which one
  falls back depends on what else is installed. Two servers with the same mod can show the same block differently.
- A borrowed state brings the physics, sounds and quirks of the vanilla block it borrows, and real vanilla blocks of
  those kinds are canonicalized to a sibling, which a server owner may notice.
- The display layer is exact for any model and has no limit, but drew a block with up to six entities (one per visible
  face, each lit by the cell in front of it), sent everything within the distance cap regardless of how many entities
  that was, and sent each block's entities as separate packets.

The client cost of item display entities is what matters for the display layer: every entity is drawn every frame, and
the client has no culling of its own for entities hidden behind blocks. The server never spawns them, so its cost is
only the per-viewer bookkeeping and the packets.

## Decision

### 1. Display first

`prism:render: auto` now follows a server setting, `blocks.render`, which defaults to `display`: every look of a block
drawn as a client-side look goes to the display layer. `state` restores the old order (borrowed state while one is left,
then the display layer). A block can still ask for itself with `prism:render: state` or `display`.

The old key `blocks.backend` is read only when `blocks.render` is absent from the server's file (the bundled defaults do
not count), and logged as `PRISM_RENDER_NOTE`: `auto` and `display` become `display` (the note says how to get the old
behaviour back), `native` becomes `state`. With `blocks.render` set, a non-`auto` backend is ignored with a note. Bad
values fall back to the defaults with a note. Blocks placed under `backend: display` with server-side display entities
keep them (their index entry has no carrier); new blocks use the carrier and the display layer.

Bedrock does not change: every look keeps its synthetic block id (ADR-0024), and Bedrock viewers never get display
entities.

### 2. One entity per block, inside a clear shell

New setting `blocks.display.lighting`, default `block`:

- `block`: one item display entity per block, at the block's centre, drawing the block's whole model. No entity at all
  if all six neighbours hide the block (a vanilla block that occludes, or a custom block whose look occludes).
- `faces`: the ADR-0023 behaviour, one entity per visible face in the neighbouring cell, kept as an opt-in.

An entity is lit by the light of the cell its centre is in. With one entity per block that cell is the block's own,
so the shell must let light through, or the whole model is drawn black. The client is therefore shown a **barrier**
(`minecraft:barrier[waterlogged=false]`) for every solid display look in `block` mode, and the server stores **glass**,
a new carrier (`Carrier.DISPLAY`), so the light the server computes and sends matches what the client expects:

| What | Server (glass) | Client (barrier) |
|---|---|---|
| Collision | full | full |
| Light | passes | passes |
| Hides neighbour faces | no | no |
| Survival break | normal (hardness 0.3) | cannot break (Prism digs it, `DigListener`) |
| Model | n/a | none: invisible without any pack model |
| Fluids | not waterloggable | `waterlogged=false` |

Why these two blocks:

- The barrier is the only vanilla block that is invisible without a pack model, has full collision, does not occlude
  and lets light through. Because a survival client cannot break it, `DigListener` runs the dig on the server, with
  the crack overlay (invisible on a barrier) replaced by the action-bar progress it already shows for such blocks. Its
  sound group is stone.
- Glass on the server: light passes, it is inert (no states, ticks or neighbour reactions), cannot hold water, breaks in
  explosions like the mushroom block did, and a Geyser client's own break of the synthetic block still ends in a vanilla
  break the server accepts (with the earliest-break check of `BreakFeel`, now recorded for every carrier). Pistons can
  push glass; the existing piston guard cancels that for custom blocks.

Passable display looks keep their no-collision pool shell (tripwire, sugar cane), stored as structure void as before:
an entity in a passable shell is already lit by its own cell. Blocks with a BetterModel model or connections keep their
server-side display entity.

A block's carrier follows its look's tier in each boot: when a look moves between "one entity in a clear shell" and
anything else (settings or content changed), `PrismBlockService.publishLook` converts the stored carrier (mushroom block
and glass, both ways) on the owning thread, after this boot's looks are known. A conversion cut short is adopted on the
next consistency check.

### 3. Client cost

- **Budget.** `blocks.display.max-per-player` (default 12000) caps the entities one player is sent. The chunks the
  player has (within `blocks.display.view-distance`) are ordered by distance from the player to their centre; each takes
  everything it needs while that fits, the first one that does not fit takes what is left, and every chunk farther away
  gets nothing. Inside that boundary chunk, blocks are taken nearest first. Before a chunk was evaluated its need is
  estimated from above (display blocks × entities per block), so the cap holds while estimates are refined. The plan is
  made again when the player crosses a chunk border, when a chunk arrives or leaves, and when what a chunk needs
  changes; at most once per tick per player. Dropped entities are destroyed, refilled ones spawned.
- **Cull.** A block hidden on all six sides gets no entity (`block` mode). Nothing finer: a block visible from one side
  still costs one entity.
- **View range.** The entities carry `blocks.display.view-range` as their view range (unchanged).
- **Static entities.** No interpolation, no shadow, no glow, no billboard, no brightness override; nothing is sent per
  tick. A look change of a block sends one metadata packet to the entity it already has; a spawn leaves out the default
  translation and rotation.
- **Bundles.** All packets of one evaluation (a chunk's spawns, its destroys first) are written in one task on the
  connection's event loop, inside bundle delimiters (at most 4000 packets per bundle; the client disconnects above 4096),
  and flushed once, so the client applies them in one frame.

### 4. Server cost

No entity exists on the server, nothing ticks, and no work runs per tick in proportion to blocks. Per viewer:

| Work | When | Cost |
|---|---|---|
| Plan | border crossing, chunk sent or dropped, need changed (at most once a tick) | O(C log C), C = chunks with display blocks in range; no block is read |
| Chunk evaluation | the chunk was admitted, its allowance cut into it, or its content changed | O(B) for the chunk's B display blocks (six neighbour reads each), plus O(B log B) for the boundary chunk only |
| Block change | a block or a neighbour changed | O(1) per viewer that has the chunk |
| Memory | while shown | one table per admitted chunk: 16 to 32 bytes per entity in `block` mode (primitive open addressing, no object per block) |

### 5. Pack

In `block` mode the pack holds one whole-block item model per display look and no face models; the clear shell needs no
model (a barrier is invisible). A model with more than 24 faces is warned about once (`PRISM_PACK_WARNING`): every
entity of it draws all of them, where a plain cube has six.

### 6. Bench

`/prism bench displays <count> [radius]` (permission `prism.command.bench`, op by default) registers a test-only block
(`prismbench:lattice`) once, places up to `count` of it in a lattice above the player with every second position on
every axis, so nothing is culled, and reports the entities the player was sent and the mean tick time before and five
seconds after (`PRISM_BENCH_DISPLAYS`; the tick time is `n/a` on Folia). `/prism bench clear` removes them.

## Consequences

- Every look is exact on Java and no longer depends on what else is installed.
- Light passes through display-drawn solid blocks: a roof of them does not keep out skylight, and a torch lights the
  other side. Choose `blocks.render: state` (borrowed states) or `blocks.display.lighting: faces` (opaque shells) when
  that matters.
- Glass and barrier do not conduct redstone; mobs do not spawn on glass.
- The vanilla faces of neighbours next to a barrier are drawn (the barrier hides nothing), behind the entity's model.
- Without synthetic Bedrock blocks (`bedrock.synthetic-blocks: off` or not registered), display-drawn blocks are
  invisible on Bedrock, as in ADR-0023.
- The selection is nearest first by chunk, then by block in the one boundary chunk: a block in a nearer chunk's far
  corner can be kept while a closer block across the border in the boundary chunk is dropped.

## Alternatives

- **Borrowed states first (ADR-0023's order).** Cheapest for clients, but the look depends on the pool and other mods,
  and the block takes the physics of what it borrows. Kept as `blocks.render: state` and `prism:render: state`.
- **One entity per visible face.** Each face is lit by the cell in front of it, so blocks are shaded like vanilla, but
  up to six entities per block (and seven slots per block in the bookkeeping). Kept as `blocks.display.lighting: faces`.
- **Real entities on the server.** Saved, ticked and tracked by the server for every block, whether anyone sees it;
  the opposite of what the display layer is for.
- **Barrier stored on the server too.** Simpler (no conversion), but a barrier is blast-proof, waterloggable and cannot
  be broken by a Geyser client either, so explosions, fluids and Bedrock breaking would all need special cases.
- **Leaves as the client shell.** Lets light through and is breakable, but fast graphics draws leaves opaque, and the
  mushroom carrier underneath would still give the cell a light level of 0 (the entity drawn black).

## Item display metadata (checked for this change)

The entity data indexes the layer writes: 11 translation, 13 left rotation, 17 view range, 23 item. Checked against
PrismarineJS minecraft-data for Minecraft 26.1 (protocol 775), where the display and item display fields are the same
as in every version from 1.20.2 to 1.21.9. The serializer types are taken from PacketEvents' per-version tables.

## Not verified

- **Minecraft 26.3 itself.** minecraft-data has no 26.3 (protocol 777) entry yet and Mojang's data reports could not be
  fetched here, so the indexes above are confirmed for 26.1, not 26.3. The PacketEvents master branch adds a 26.3
  serializer table that only appends a dye colour type; PacketEvents 2.13.0, the version Prism compiles against, has no
  26.3 entry, so a 26.3 server needs a newer PacketEvents at runtime.
- What a client draws: the barrier shell, the light inside it, the lattice's frame time.
- Bundles and the event-loop write against a live connection and PacketEvents' own bundle handling.
- The carrier conversion on a world from an earlier version (only the consistency self test's simulated cases ran).
