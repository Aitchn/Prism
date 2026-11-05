# ADR-0012: Ore generation

Status: Accepted (2026-11-05). The vein algorithm is unit tested. Generation in a real server is covered by the ore
self test in the smoke test.

## Context

Row 36 of [platform-triage.md](../platform-triage.md). Every mod with ores (or with resources found in the world)
needs its blocks to appear in new chunks. Before this, rebuilds made ores craftable instead.

## Decision

- **A block component, not a new content type.** `prism:ore` sits on the block it generates. It takes one vein spec or
  a list of them:
  - `dimension`: `normal`, `nether` or `the_end`;
  - `replace`: vanilla blocks (defaults to the dimension's stone);
  - `min_y` and `max_y`;
  - `veins_per_chunk` (1 to 64), `rarity` (chance 1 in N per chunk) and `size` (1 to 32).
- **Generation runs in the chunk load of a new chunk**, on the thread that owns it (works the same on Folia). The
  following were rejected:
  - A Bukkit `BlockPopulator`, because its `LimitedRegion` has no chunk yet, so Prism could not index the block or
    store its data.
  - A custom chunk generator, because it would replace the server's own generator.
- **Veins stay inside their chunk.** A vein is a random walk that bounces off the chunk border and the height range, so
  generation never loads or touches a neighbour. Positions come from the world seed, the chunk and the spec
  (`OreVeins` in prism-core), so they are deterministic.
- **Generated blocks are drawn and indexed like placed ones**, with the first variant, facing north. They do not get
  place behaviors or neighbour notifications, because the chunk is still loading. Load behaviors and ticking start one
  tick later, with the rest of the chunk.
- Only listed vanilla blocks are replaced, so other plugins' blocks and Prism blocks are left alone.
- Problems are logged as `PRISM_ORE_UNKNOWN_BLOCK`, and the number of targets as `PRISM_ORES_READY targets=N`.

## Consequences

- Every ore block is an indexed custom block. With the note block backend it costs one carrier state per look and
  one chunk data entry per block, like any placed block.
- Chunks generated before the mod was installed get no ore. Prism does not retrogenerate. Spawn chunks of a brand-new
  world are generated before plugins load, so they have no ore either.
- Flora and surface features are not covered. They need placement rules other than "replace this block" and get
  their own spec when a rebuild needs them.

## Not verified by CI

- Finding the ore underground on Java and on Bedrock, mining it with a pickaxe, and getting rubies.
