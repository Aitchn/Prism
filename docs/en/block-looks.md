# Block looks at scale

A mod can have as many custom block looks as it likes. Java players need only the resource pack; nothing else is
installed on the client. (A *look* is one variant of a block facing one way.)

## How a look is drawn

Prism gives each look one of two representations, decided every boot from the registered content and the server's
settings (nothing is stored in the world, so mods can add and remove blocks freely):

- **The display layer** (D tier), the default. The block is an invisible shell to the client, with the right collision,
  and each player near it is sent an item display entity that draws the block's own model. Nothing is spawned on the
  server: the entities exist only in the packets sent to players. Every look is exact, and there is no limit on their
  number.
- **A borrowed vanilla state** (S tier), on request. 931 states of vanilla blocks whose model the pack may replace,
  because every real block of those kinds can be shown as another state that looks exactly the same: the note block
  (799), tripwire (64), target (15), dispenser and dropper (6 each), spruce and birch leaves (13 each) and sugar cane
  (15). They cost the client nothing beyond a normal block, but the pool is small and shared by all mods, and the block
  takes the physics of the state it borrows. Real vanilla blocks of these kinds are sent to players as their plain
  sibling, so a note block a player placed looks like a note block.

A look that asks for a borrowed state takes one of its own **class** while one is left. A class is collision (solid or
none), whether the block hides its neighbours, and its sound group.

### One entity per block

By default (`blocks.display.lighting: block`) a display block is one entity at the block's centre, inside a barrier: the
barrier is invisible without any pack model, has full collision and lets light through, so the entity is lit by its own
cell. The server stores glass there for the same reason. Players dig these blocks with Prism's own dig (a progress bar
appears in the action bar, since the barrier shows no cracks). A block hidden on all six sides by solid blocks gets no
entity at all. Passable blocks keep a shell without collision.

`blocks.display.lighting: faces` draws a block as one entity per visible face instead, each lit like the face of a
vanilla block, inside a shell that blocks light like a normal block, at up to six entities per block.

## Component

```yaml
components:
  prism:render: auto        # auto (default), state or display
  prism:surface:
    sound: glass            # wood, stone, gravel, grass, sand, snow, wool, metal, glass, bone, slime, netherrack, deepslate, copper
    occludes: false         # whether the block hides the faces of its neighbours
```

- `prism:render: auto` follows the server's `blocks.render` setting (`display` unless the owner changed it).
- `state` asks for a borrowed state; if none is left the block still works with the display layer, and the log says so
  (`PRISM_LOOK_WARNING`).
- `display` always uses the display layer.
- `prism:surface` is for the sound group and for blocks that do not hide their neighbours, like glass. A passable block
  (`prism:passable`) is already "no collision, hides nothing".

## Digging

Players break a custom block in survival with the time `prism:mining` asks for. On a borrowed state, Prism adjusts the
client's break speed for that dig only; on a display block in its barrier shell, Prism runs the dig itself. Passable
blocks break instantly, as before.

## Settings (`config.yml`)

| Key | Default | Meaning |
|---|---|---|
| `blocks.render` | `display` | What `prism:render: auto` means: `display` or `state` (borrowed states first). |
| `blocks.display.lighting` | `block` | `block`: one entity per block, light passes through. `faces`: one entity per visible face. |
| `blocks.display.max-per-player` | `12000` | The most display entities one player is sent; the nearest chunks are served first. |
| `blocks.display.view-distance` | `8` | Chunks around the player that get display entities; `0` is every chunk the client has. |
| `blocks.display.view-range` | `1.0` | The entities' view range multiplier. |
| `blocks.display.solid-shells` | `false` | Faces lighting only: use the opaque note block shell for solid display blocks. Leave off unless you have checked it on a client. |

All of them need a restart. The setting of earlier versions, `blocks.backend`, is still read when `blocks.render` is
not in your file: `auto` and `display` become `display`, `native` becomes `state`; the log says what it did
(`PRISM_RENDER_NOTE`).

## Measuring

`/prism bench displays <count> [radius]` (permission `prism.command.bench`, operators by default) places up to `count`
test blocks in a sparse lattice above you, where none hides another, and reports how many display entities you were
sent and the server's mean tick time before and five seconds after. The first use adds a test block to the pack, so
accept the pack again if the client asks. `/prism bench clear` removes the blocks.

## Limits

- Every display entity is drawn by every client that has it, each frame. The budget caps the count per player, and
  what does not fit is the farthest; on busy builds lower `max-per-player` or `view-distance`.
- In `block` lighting, light passes through display blocks: a roof of them does not keep out the sky, and a torch lights
  both sides. They do not conduct redstone, and mobs do not spawn on them. Use `blocks.render: state` or
  `blocks.display.lighting: faces` where that matters.
- Models with many faces cost more per entity; the pack build warns about models with more than 24 faces.
- Bedrock players see every look as a real custom block when Geyser runs on the same server (`bedrock.synthetic-blocks`,
  ADR-0024; see `bedrock.md`). Without that, they see only borrowed note block and tripwire looks, and a display-layer
  block is invisible to them: give it `prism:render: state` and keep the number of such blocks below the pool if it must
  be visible then.
- A block of a mod that is not installed has no look; players see the plain carrier.

See [ADR-0025](https://github.com/Aitchn/Prism/blob/main/docs/adr/0025-display-first.md) for the current defaults and
[ADR-0023](https://github.com/Aitchn/Prism/blob/main/docs/adr/0023-look-pool-and-display-layer.md) for the look pool,
the alternatives and what is not yet verified on a real client.
