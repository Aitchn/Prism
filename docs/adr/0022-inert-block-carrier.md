# ADR-0022: Inert block carrier and packet rewriting (stage 1)

Status: Accepted (2026-11-08). Stage 1 of the block carrier redesign; later stages (a larger look pool, display-entity
shells, Bedrock pools, piston and redstone support) are separate changes. The server side is covered by the unit tests
and the consistency self test of the smoke test. What a real client draws is not (see "Not verified").

## Context

Custom blocks were stored in the world as vanilla note block states (solid blocks) or tripwire states (passable
blocks), one state per look. That ties what players see to a mutable piece of world state, and the looks drift:

- **R1, client prediction.** When a player places or breaks a block next to a note block, the Java client runs the
  neighbour shape update locally and recomputes the instrument from the block below or above. The client now shows a
  different note block state, which is another block's look. The server never changed the state, so it never resends
  it, and the wrong look stays until the chunk is sent again. Tripwire connects to its neighbours the same way.
- **R2, paths around physics events.** `setBlockData(..., false)`, `/fill`, WorldEdit and structure placement change
  states without a `BlockPhysicsEvent`, so `NativeStateListener` could not guard them. A carrier replaced by another
  block also left a stale index entry behind.
- **R3, guards that cost features.** To keep the state, physics events on carriers were cancelled, which also hides
  redstone and neighbour updates from them.

Identity (key, facing, variant, connections) already lives in `BlockIndex`, in the chunk's persistent data.

## Decision

1. **The index is the only source of truth.** The world carries no look.
2. **The server stores an inert carrier**, chosen by physics class (`Carrier`):
   - `SOLID` (default): `BROWN_MUSHROOM_BLOCK`.
   - `PASSABLE` (`prism:passable`): `STRUCTURE_VOID`.
   - Blocks drawn by a display entity (BetterModel models, connected shapes, `blocks.backend: display`) keep the
     carrier their definition names (usually `barrier`) and their display entity. Their look is not a client state, so
     nothing about them drifts; Bedrock still gets its per-player note block look from `BedrockModelLooks`.
3. **Outgoing packets are rewritten** (`prism-client`, PacketEvents): chunk data, block change and multi block change.
   Every indexed position that holds its carrier is shown as the look's client state (the same note block and tripwire
   states and resource pack as before; the 799-state Java pool is unchanged in this stage). Only sections that contain
   custom blocks are decoded and re-encoded.
4. **A correction loop** (`LookReconciler`): after a player places, breaks, clicks or starts digging, or an action is
   denied, the target and its six neighbours are sent again, immediately and one tick later (the client applies server
   block data it received during a pending prediction when the acknowledgement arrives).
5. **External changes: the world wins.** If an outgoing packet carries something else than the carrier (or the look)
   for an indexed position, a region task checks the world; if the carrier is gone the entry is removed and the remove
   behaviours run, without dropping anything. The same check runs lazily in `BlockService.get`/`identify` (one
   material comparison) and for every entry when a chunk's index is first read.
6. **Migration needs no tool.** The index records which carrier an entry uses. When a chunk is first read, entries
   without one (written by earlier versions) whose world block is still a note block or tripwire are converted to the
   new carrier. The index is truth, so a note block anywhere else is left alone.
7. **Guards removed:** physics cancellation, note play, note re-tuning, tripwire press and connect handling. Kept: a
   right click on a carrier is denied for the vanilla use (defensive: no vanilla tool changes a mushroom block today), and
   fluid flowing into a passable carrier is cancelled. `blocks.backend` keeps its meaning; `auto`/`native` choose carriers, `display` is
   unchanged.

### Choice of carriers

A carrier needs: nothing that changes its state, no random or scheduled ticks, no reaction to neighbours, nothing that
destroys or replaces it on its own, and a survival break that is never slower on the server than the client predicts.

The last point decides the solid carrier. The client breaks a note block look with the note block's hardness (0.8,
axe preferred, no tool needed) and tells the server when it is done. The server decides with the block it really holds:
if its progress is slower (a block that needs a tool for drops, or a tool the block does not prefer) it finishes the
break late, after the client already removed the block, and a stopped dig is refused. Candidates:

- Waxed copper, stone, deepslate: inert, but they need a tool for drops, so a hand break is about three times slower
  on the server than on the client.
- Coarse dirt: needs no tool and is inert, but a shovel is its preferred tool, so an axe break (the note block's
  tool) is slower on the server; a hoe also changes it.
- Wool: flammable. Sponge: absorbs water on a neighbour update and becomes wet sponge. Plain dirt: becomes grass.
- Brown mushroom block: hardness 0.2, axe preferred, no tool needed, full opaque cube that conducts redstone, not
  flammable, nothing in vanilla changes it. It has six boolean face properties; they stay at their default because
  no neighbour update touches them. Chosen.

The passable carrier is structure void: no collision, no states, no ticks, the client does not need to collide with
it because players are shown a tripwire state whose collision is also empty, fluids do not replace it, and it is
instantly breakable like the tripwire look.

The carrier material does not matter to players, who never see it, so a real brown mushroom block next to custom blocks
is harmless: only indexed positions are rewritten.

## Folia and threads

Packet listeners run on netty threads. They read only an immutable `ChunkLooks` snapshot per chunk (`BlockIndex.Loaded
.snapshot()`), rebuilt lazily from a concurrent map after a change and replaced as a whole, plus a per-world counter that
lets chunks of worlds without custom blocks pass undecoded. They never touch Bukkit world state; the only thing that
reaches the world is a task scheduled with `PlatformScheduler.runAt` on the position's owning region. Looks are
computed (and states allocated) on the owning thread when a block is placed, changes or its chunk is read. The one
world read on a network thread is the viewer's world and its minimum height, to map sections to y.

## Alternatives considered

- **Keep note blocks and add more event guards.** Cannot fix R1 (the client predicts locally) or R2 (events are bypassed).
- **Resend only (no new carrier).** Repairs R1 but not R2, leaves physics guards in place, and keeps redstone and
  neighbour behaviour broken for carriers.
- **Other solid carriers** (waxed copper, deepslate, coarse dirt, wool, sponge, dirt): rejected above.
- **Barrier for everything.** Invisible and inert, but waterloggable (has a state) and it cannot be drawn by Geyser or
  predicted by the client as a note block, so the look would still have to be a display entity.
- **Tripwire kept as the passable server carrier with a rewritten look.** Still has states and neighbour behaviour
  (connecting, powering); structure void has neither.
- **Inject real custom blocks into the server registry.** Most complete, but depends on server internals on every
  version and on Folia. Remains a research option.
- **Persisting the carrier in the world data only (no entry field).** An entry cannot then tell a display block from a
  carrier-drawn block when a chunk loads, so replaced carriers could not be detected for blocks of absent mods.

## Consequences

- Plugins that read the world see a brown mushroom block or structure void; `BlockService.identify` is the way to ask (as before).
- A piston still refuses to push custom blocks, and explosions still remove them (unchanged; later stages).
- Structure void is replaceable (tripwire was not), so a vanilla block placed into a passable block replaces it and
  the external change check drops the entry.
- The rewrite adds a decode and re-encode for chunk packets of chunks that contain custom blocks, and a lookup per block
  change packet in worlds that have any.
- A custom block of a mod that is not installed is shown with the look its allocation still owns; if none exists the
  viewer sees the carrier.

## Not verified

- No headless client joins the smoke test, so the packet listener itself (chunk, block change and multi block change
  rewriting against a real PacketEvents version, ack timing of the correction loop) has no automated test. The
  consistency self test checks the same logic server-side: index, carrier and the state the rewrite would send, through
  the same `ChunkLooks` code, after each disturbance. A headless client would need protocol-level login and chunk
  decoding of the current Minecraft version; it was judged too expensive for this stage.
- On a real client: place and break stone on every side of a custom block and watch that the look never changes;
  repeat with a plugin that calls `setBlockData(..., false)` on a carrier.
