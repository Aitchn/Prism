# Validation: Botania on Prism

Goal: rebuild the core loop of Botania (flora magic) as a plain plugin on Prism and write down every place where the
API could not express it. It is a test of Prism, not a product and not a clone: amounts and recipes follow the
original where known and are approximations otherwise, and the textures are generated placeholders (no original
assets are in the repository).

Code: `validation/botania`. Rules that need no server live in `...botania.logic` and are unit tested; the Bukkit
adapter is `BotaniaRuntime`, `BukkitWorld` and `BotaniaPlugin`. `-Dbotania.selftest=true` makes a real server run
the whole chain once (Daybloom feeds a Spreader, the burst crosses four blocks, the Pool fills); the smoke test
checks that line on Paper, Folia and Purpur.

## What is implemented

| Mechanic | State |
|---|---|
| Mana Pool, Mana Spreader (aimed with the wand, sneak + click), Mana Burst | done; a burst is an instant ray trace that hands over between Folia regions, not a flying entity |
| Daybloom, Endoflame (generating flowers), Wand of the Forest binding | done |
| Pure Daisy (functional flower, vanilla block to living block) | done |
| Mana infusion (iron, ender pearl, diamond dropped into a pool) | done, no alchemy catalyst |
| Petal Apothecary (water, 16 petal colors, seeds to craft) | done, three approximate recipes |
| Persistence | per block in the DataStore, grouped by chunk, saved every 10 s and on unload |
| Not done | Orechid, Hydroangeas, runic altar, agglomeration plate multiblock, lenses, mana tablets and rings, tools and armor, world generation, Bedrock testing |

## Gaps found

Each item is also in [gaps.md](../gaps.md). **Fixed in Prism** means this PR changed Prism.

1. **No place hook.** A block could react to interact and break but not to being placed, so it could not start
   ticking. *Fixed in Prism:* `BlockPlaceBehavior`. The same review found that explosions removed custom blocks
   without running break behaviors; *fixed in Prism* too.
2. **No ticking API.** The mod schedules one `RegionScheduler` task per block itself. That works on Folia but is one
   task per flower; thousands of blocks need a Prism ticker that batches per chunk or region.
3. **Block entity data is DIY.** The DataStore is enough for one key per block (`be/<world>/<cx>,<cz>/<x>,<y>,<z>`),
   but Prism gives nothing for attaching data to a block: no cleanup when the block goes away (the mod checks every
   saved block against the world on chunk load), no chunk load/unload callbacks (the mod listens to
   `EntitiesLoadEvent` and `ChunkUnloadEvent` itself), writes are synchronous under one global lock on a region
   thread, and values are plain strings.
4. **`BlockService.identify` scans entities.** It looks for the marker display entity near the block, so it needs the
   entities loaded and costs a search per call. Fine per click, too slow per tick or per neighbour lookup. Prism needs
   a position to key index.
5. **Click context is thin.** `ItemUseContext` has no clicked block or face; `BlockContext` has no held item, hand,
   face or hit position. The wand and the apothecary read the player's inventory directly. Items cannot say "use me
   on a block" as a capability.
6. **No placement state.** `BlockService.place` takes no facing or rotation, and nothing tells a block how it was
   placed. The spreader takes the placer's yaw only because `BlockPlaceBehavior` receives the player.
7. **Carrier is a full solid.** Flowers use `minecraft:barrier`, so a flower has a full-block hitbox and collision.
   Needed: carriers without collision, or a configurable shape. *Suspected, not tested on a server:* barrier is
   unbreakable in survival, so breaking probably needs left-click handling instead of `BlockBreakEvent`.
8. **No recipe content type.** Petal, infusion and conversion recipes are Java tables. A shared recipe registry that
   mods can add types to (and that other mods can extend) is missing.
9. **Content is static YAML.** 16 petal colors meant 16 generated files. Variants, templates or programmatic
   registration would remove that.
10. **No per-stack data.** Components are per definition. Mana tablets, rings and lenses that store mana or a lens
    kind per item stack cannot be expressed; neither can dynamic tooltips.
11. **No region helper.** Cross-region work (burst hand-over, flower to pool in another region) uses
    `Bukkit.isOwnedByCurrentRegion` and `RegionScheduler` directly; Prism's `PlatformScheduler` is not in the API.
12. **No runtime control of a block's model.** The spreader's head cannot turn to show its aim: the display entity and
    model animation are internal to Prism.
13. **No effects API.** Particles, sounds and light come straight from Bukkit, so they are not Bedrock-aware.
