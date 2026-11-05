# Validation: Botania on Prism

Goal: rebuild the core loop of Botania (flora magic) as a plain plugin on Prism and write down every place where the
API could not express it. It is a test of Prism, not a product and not a clone: amounts and recipes follow the
original where known and are approximations otherwise, and the textures are generated placeholders (no original
assets are in the repository).

Code: `validation/botania`. Rules that need no server live in `...botania.logic` and are unit tested; the Bukkit
adapter is `BotaniaRuntime`, `BukkitWorld` and the behavior classes. `-Dbotania.selftest=true` makes a real server
run the whole chain once (Daybloom feeds a Spreader, the burst crosses four blocks, the Pool fills); the smoke test
checks that line on Paper, Folia and Purpur.

## History

The first version (before ADR-0007 to ADR-0010) had to build its own block index, ticking, persistence and recipe
tables, and it found the gaps that became the block platform. This version uses the platform instead.

| Need | First version | Now |
|---|---|---|
| Reacting to place, break, chunk load | own listeners, `EntitiesLoadEvent` | `BlockPlaceBehavior`, `BlockLoadBehavior`, `BlockRemoveBehavior` |
| Ticking | one `RegionScheduler` task per block | `BlockTickBehavior`, batched by Prism |
| State | DataStore key per block, orphan checks | `CustomBlock.data()` (one string per block) |
| Finding a pool or spreader | own position map | `BlockCapability` (`botania:mana`), with the map only for cross-region reads |
| Cross-region bursts | `RegionScheduler` | `Prism.scheduler()` |
| Spreader direction | placer yaw | placement facing (`prism:rotation: all`) |
| Mana tablet | not possible | `ItemService.editData` plus `ItemLoreBehavior` |
| Recipes | Java tables | `prism/recipes/**.yml` with three mod recipe types, plus `prism:shaped` and `prism:shapeless` |
| Survival access | none | crafting recipes for every item; a test checks each item is obtainable |
| Breaking | not verified | `prism:mining` per block |

## What is implemented

| Mechanic | State |
|---|---|
| Mana Pool, Mana Spreader (aimed by placement, re-aimed with the wand sneak-click), Mana Burst | done; a burst is an instant ray trace that hands over between Folia regions, not a flying entity |
| Daybloom, Endoflame (generating flowers), Wand of the Forest binding | done |
| Pure Daisy (vanilla block to living block, recipes in `prism/recipes/daisy`) | done |
| Mana infusion (iron, ender pearl, diamond dropped into a pool; recipes in `prism/recipes/infusion`) | done, no alchemy catalyst |
| Petal Apothecary (water, 16 petal colors, seeds to craft; recipes in `prism/recipes/petals`) | done, three approximate recipes |
| Mana Tablet (charge and discharge at a pool, stored mana shown in the lore) | done |
| Survival crafting | done: petals from dyes (stand-in for picking them), apothecary, pool, spreader, wand, tablet |
| Not done | Orechid, Hydroangeas, runic altar, agglomeration plate multiblock, lenses, rings, tools and armor, world generation |

## Hand tests (real clients; CI cannot check these)

Run each on a Java client and on a Bedrock client through Geyser.

1. Craft: a dye in the crafting grid gives two petals; the apothecary, pool, spreader, wand and tablet recipes work
   (get livingrock and livingwood first, see 4). Nothing can use a ruby-like custom item as its carrier (paper).
2. Place the apothecary, right click with a water bucket, throw in the petals of a flower recipe, throw in wheat
   seeds: the flower item drops. Break the apothecary with petals inside: they drop.
3. Place a daybloom, a spreader and a pool a few blocks apart. Wand on the flower, then on the spreader: "Bound". Wand
   sneak-click on the spreader aims it where you look. In daylight the pool's mana grows (empty hand right click
   shows it in the action bar).
4. Place a pure daisy next to stone and an oak log: after about 150 ticks per block they turn into livingrock and
   livingwood; mining them in survival takes time (crack and bar) and drops the block.
5. Drop an iron ingot on a pool with 3000 mana: it becomes a manasteel ingot.
6. Charge a tablet at a pool (right click), discharge with sneak. The lore shows the amount and survives a relog.
7. Restart the server with machines in loaded chunks: pools keep their mana and flowers keep generating without a
   click.
8. Blocks and items are visible and have icons on both editions; flowers are full cubes (known gap).
