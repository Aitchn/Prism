# World effects

Blocks can give off particles by themselves, mods can send something flying from one point to another, and models can
be coloured per variant. None of it is a server entity: particles and moving models exist only in the packets sent to
the players near enough who have the chunk, so an empty area costs the server nothing.

## Particles from a block: `prism:particles`

```yaml
# prism/blocks/ruby_lamp.yml
base: minecraft:barrier
components:
  prism:variants: [idle, active]
  prism:particles:
    # In every variant: two emissions a second, three particles each, just above the block.
    - particle: minecraft:dust
      color: "#ff4060"
      size: 0.8
      rate: 2
      count: 3
      offset: [0, 0.7, 0]
      spread: [0.25, 0.05, 0.25]
    # Only while active: one spark every two seconds, seen up to 24 blocks away.
    - particle: minecraft:end_rod
      rate: 0.5
      offset: [0, 0.6, 0]
      speed: 0.02
      range: 24
      variants: [active]
```

| Key | Meaning |
|---|---|
| `particle` | A vanilla particle id, such as `minecraft:flame`, `minecraft:dust`, `minecraft:end_rod`. |
| `count` | Particles per emission, 1 to 256 (default 1). |
| `spread` | How far they scatter, a number or `[x, y, z]` in blocks (default 0). |
| `speed` | Particle speed (default 0). |
| `color` | `"#rrggbb"`, for particles that take a colour (`dust`, `entity_effect`, ...). |
| `size` | Dust size, above 0 and at most 16 (default 1). |
| `material` | An item or block id, for `item`, `block`, `falling_dust`, `dust_pillar`, `block_marker`. |
| `rate` | Emissions per second, above 0 and at most 20 (default 1). `0.5` is one every two seconds. |
| `offset` | `[x, y, z]` from the block centre, turned with the block's facing (default `[0, 0, 0]`). |
| `range` | How far away a player still sees it, at most 128 blocks (default: the server's `effects.particles.range`). |
| `variants` | Only in these variants (default: all). |

A block has at most 8 emitters. Switching its variant with `Prism.blocks().setVariant(...)` changes which ones run; removing the block
stops them. A particle id that does not exist, or that misses the data it needs, is logged once as
`PRISM_EFFECTS_WARNING` and skipped.

How it runs: one shared clock checks all emitting blocks every few ticks. A player gets only the emitters in range in
chunks their client has, nearest first, up to a per-player budget. Identical blocks side by side do not pulse in step.

## Travelling visuals, bursts and beams: `Prism.effects()`

```java
EffectService effects = Prism.effects();

// A model that flies from one block to another in two seconds, leaving sparks, then does something on arrival.
Travel travel = Travel.between(from.getLocation().add(0.5, 1, 0.5), to.getLocation().add(0.5, 1, 0.5), 40)
        .withModel(PrismKey.parse("examplemod:ruby"))
        .withScale(0.5f)
        .withTrail(ParticleSpec.of("minecraft:end_rod"), 2)
        .onArrive(() -> to.getWorld().playSound(to.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1, 1));
TravelHandle handle = effects.launch(travel);
// handle.cancel() stops it early; the callback then never runs.

// Particles once at a point, and an instant beam (four points per block).
effects.burst(location, ParticleSpec.dust(0xff4060, 1.5f).withCount(20).withSpread(0.3, 0.3, 0.3));
effects.line(start, end, ParticleSpec.of("minecraft:electric_spark"), 4);
```

- `Travel.between(from, to, ticks)` or `Travel.along(from, direction, distance, ticks)`; up to 6000 ticks.
- `withModel` takes a Prism item key (its model) or any item model id (`minecraft:ender_pearl`). `withBillboard`
  picks how it turns towards the viewer (`CENTER` by default).
- `withTrail(particle, points)` sends 1 to 8 points per tick along the stretch flown in that tick.
- `onArrive` runs once, when the flight is over, on the thread that owns the destination (on Folia its region), so it
  may change the block there. It runs even if no player saw the travel.
- All methods may be called from any thread.

The client moves the model itself, so a flight costs one spawn, a move every three seconds at most and one removal per
player. It is shown to the players near the path who have the chunk when it starts. A player who would see more than
`effects.travel.max-per-player` at once loses the farthest.

## Tints: `prism:tint`

Models from other games often draw faces with `"tintindex": 0` and expect the game to colour them. `prism:tint` gives
each index a colour, optionally per variant:

```yaml
  prism:tint:
    colors: ["#ffb0b0"]          # index 0, 1, ...
    variants:
      active: ["#3ab5ff"]        # replaces colors in this variant
```

The short form is a plain list: `prism:tint: ["#ffb0b0"]`. Colours apply to the item and to blocks drawn by the
display layer. A block drawn as a borrowed block state cannot be tinted; Prism warns (`PRISM_PACK_WARNING`) and you
can add `prism:render: display` to that block (see [Block looks at scale](block-looks.md)).

## Server settings

```yaml
effects:
  view-range: 48            # how far from a travel, burst or beam a player still gets it
  particles:
    interval: 5             # ticks between runs of the emitter clock (1 to 100)
    range: 32               # range of an emitter that sets none (1 to 128)
    max-per-player: 2000    # particles per second one player gets from block emitters; nearest blocks first
  travel:
    max-per-player: 64      # travelling visuals one player is shown at once; the farthest are dropped
```

Changing these needs a restart.

## Bedrock

| Effect | Bedrock (Geyser) |
|---|---|
| Block particles, bursts, beams, trails | yes, translated by Geyser |
| Travelling model | no, only its trail |
| `onArrive` | yes (it runs on the server) |
| Tints | yes: the Bedrock pack holds a recoloured copy of each tinted texture; the item icon uses the first colour |

Particles that Geyser cannot translate are not shown to Bedrock players. Nothing here has been checked on a real
Bedrock client yet.
