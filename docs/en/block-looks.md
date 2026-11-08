# Block looks at scale

A mod can have as many custom block looks as it likes. Java players need only the resource pack; nothing else is
installed on the client. (A *look* is one variant of a block facing one way.)

## How a look is drawn

Prism gives each look one of two representations, decided every boot from the registered content (nothing is stored in
the world, so mods can add and remove blocks freely):

- **A borrowed vanilla state** (S tier). 931 states of vanilla blocks whose model the pack may replace, because every
  real block of those kinds can be shown as another state that looks exactly the same: the note block (799), tripwire
  (64), target (15), dispenser and dropper (6 each), spruce and birch leaves (13 each) and sugar cane (15). They are the
  fastest: a block is just a block state, no entity. Real vanilla blocks of these kinds are sent to players as their plain
  sibling, so a note block a player placed looks like a note block.
- **The display layer** (D tier), for everything beyond that. The block is an invisible shell to the client (it has the
  right collision, hides neighbours or not, and plays the right sound), and each player near it is sent item display
  entities that draw the model, one per visible face. Nothing is spawned on the server: the entities exist only in the
  packets of players within `blocks.display.view-distance` chunks.

A look takes a borrowed state of its own **class** while one is left. A class is collision (solid or none), whether the
block hides its neighbours, and its sound group. A solid block that hides its neighbours and sounds like wood, for
example, is a different class from a passable block.

## Component

```yaml
components:
  prism:render: auto        # auto (default), state or display
  prism:surface:
    sound: glass            # wood, stone, gravel, grass, sand, snow, wool, metal, glass, bone, slime, netherrack, deepslate, copper
    occludes: false         # whether the block hides the faces of its neighbours
```

- `prism:render: auto` takes a borrowed state while one is left, then the display layer.
- `state` asks for a borrowed state; if none is left it still works, and the log says so (`PRISM_LOOK_WARNING`).
- `display` always uses the display layer. Use it for blocks that should not take one of the 931 states.
- `prism:surface` is for the sound group and for blocks that do not hide their neighbours, like glass. A passable block
  (`prism:passable`) is already "no collision, hides nothing".

The default class of a solid block is solid, opaque and wood; `prism:surface` changes it, and `prism:passable` makes it
passable.

## Digging

Players break a custom block in survival with the time `prism:mining` asks for: while a player digs one, Prism adjusts
the client's break speed for that dig only, and removes the change when they stop. Blocks that are passable break
instantly, as before.

## Settings (`config.yml`)

| Key | Default | Meaning |
|---|---|---|
| `blocks.display.view-distance` | `8` | Chunks around the player that get display entities; `0` is every chunk the client has. |
| `blocks.display.view-range` | `1.0` | The entities' view range multiplier. |
| `blocks.display.solid-shells` | `false` | Use the opaque note block shell for solid display blocks. Leave off unless you have checked it on a client. |

## Limits

- Display-layer blocks cost entities per viewer: up to six per block, only for chunks within the cap. Keep large builds
  of display-layer blocks to blocks that matter, or lower the distance.
- A solid display block is shown to the client as a cube that does not hide neighbouring faces and sounds like grass
  (unless you opted into the note block shell). The layer itself hides faces behind other solid display blocks.
- Bedrock players see only borrowed note block and tripwire looks (through Geyser). A display-layer block is invisible
  to them: give it `prism:render: state` and keep the number of such blocks below the pool if it must be visible on Bedrock.
- A block of a mod that is not installed has no look; players see the plain carrier.

See [ADR-0023](https://github.com/Aitchn/Prism/blob/main/docs/adr/0023-look-pool-and-display-layer.md) for the design,
the alternatives and what is not yet verified on a real client.
