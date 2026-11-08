# Concepts

**Layers.** Plugin (`Prism.jar`) provides the API and runtime; a *mod* is a plugin that depends on it. A plugin is a
mod if its main class implements `PrismMod`, calls `Prism.mods().register`, or ships a `prism/` folder.

**Keys.** Content is identified by `PrismKey` (`namespace:value`). `prism` and `minecraft` are reserved.

**Lifecycle.** `onInitialize` registers component and behavior types. Prism then loads and validates all YAML,
collecting every problem, and publishes the content atomically. A mod with any problem publishes nothing.
`onContentLoaded` runs after publication.

**Items.** A vanilla base item plus data components and behaviors. Identity lives in persistent data; appearance
is the `item_model` component pointing at a generated item definition.

**Blocks.** A vanilla carrier block (for example `barrier`) plus a persistent display entity that draws the model.
Prism keeps an index of custom blocks in each chunk's persistent data, so blocks, their facing and their per-block
data survive restarts without a database. Behaviors can react to place, remove, break, chunk load/unload,
neighbour changes and ticks (`BlockTickBehavior`, batched per chunk). `CustomBlock.data()` is a
`PersistentDataContainer` per block, removed with the block. Blocks offer services to neighbours through
`BlockCapability` and `BlockCapabilityProvider`; Prism defines only the lookup and `PrismCapabilities.INVENTORY`.
`prism:rotation` makes a block face the player; `prism:mining` sets how long it takes to break in survival.

**Sounds and animated textures.** Put sounds in your jar as on a Java resource pack (`assets/<ns>/sounds.json` plus
`.ogg` files) and play them with Bukkit's `playSound(location, "<ns>:<event>", ...)`. Prism also copies them into the
Bedrock pack, so Bedrock players hear them through Geyser. `prism:ambient_sound` makes a block play a sound by itself
every `interval` ticks, only in the listed variants. It stops when the block goes. An animated texture
(`<texture>.png.mcmeta` with `animation`) animates on Java and becomes a flipbook on Bedrock. The ruby counter hums
and glows while active.

**Connected blocks.** `prism:connections` makes a block reach towards its neighbours, like a cable or pipe: a core cube
and an arm towards each connected side. `connects_to` lists `self`, block keys (custom or vanilla) and
`capability:prism:item_handler` or `capability:prism:inventory`; `faces`, `core`, `arm` and `texture` shape it. Prism
updates the arms when the block or a neighbour is placed or removed, and draws every combination itself from one
texture. A `BlockConnectionBehavior` replaces the rules with code; call `Prism.blocks().refreshConnections(block)` when
its answer changes. Java players see a display entity over a barrier, so the faces of neighbouring blocks stay drawn.
Bedrock players see a generated shape, one note block state per variant and combination of sides (64 for six sides,
16 for four). `examplemod:ruby_cable` is an example.

**Passable blocks.** `prism:passable: true` makes a block players walk through, like a flower or a wire. Prism draws
it with one of 64 tripwire states instead of a note block state, so it has no collision on either edition, does not
hide the blocks around it and breaks instantly. Without a model of its own, its texture is drawn as a cross, like a
flower. `examplemod:ruby_flower` is an example.

**Multiblock structures.** `prism:structure` makes a block the controller of a structure. You give `layers` (bottom
to top, rows north to south split by `/`), a `key` of one-character symbols to block keys, `@` for the controller,
a space for any block, `.` for air, and whether the pattern may be turned (`rotate`, default true). Prism checks the
pattern when the controller or a nearby block is placed or removed, and when the chunk loads. It then calls the
controller's `StructureBehavior.onFormed` or `onBroken`. `BlockService.structure(controller)` and `controllerOf(member)`
answer at any time. What a formed structure does is up to the mod. `examplemod:ruby_core` lights up on top of a 3x3
ruby floor.

**Boxes of any size.** `prism:cuboid` makes a block the controller of a hollow box: `walls` (block keys the shell may
be built from), `inside` (blocks allowed inside besides air, default none), `min` and `max` (outer side length, 3 to
32). The controller sits in a wall, not on an edge or corner. Prism finds the box from the controller's open side,
calls the same `StructureBehavior`, and reports the box as `FormedStructure` with `facing` pointing inside and `min`
and `max` corners, so the mod can work out its volume. Filling the inside with anything else, or breaking a wall,
breaks it; rebuilding it at another size forms it again. `examplemod:ruby_tank` lights up in a box of ruby blocks and
glass from 3 to 7 blocks a side.

**Ores.** `prism:ore` generates a block as veins in newly generated chunks: `dimension`, `replace` (vanilla blocks),
`min_y`, `max_y`, `veins_per_chunk`, `rarity` and `size`, or a list of such specs. Veins stay inside their chunk.
Chunks that existed before the mod was installed get none. `examplemod:ruby_ore` is an example; it is a note block
state, so Bedrock players see it too.

**Getting items.** `/prism give <player> <item> [amount]` and `/prism items` (a browser menu) work for every mod.
Permission: `prism.command.give` (operators by default).

**Models.** A block can use an animated Blockbench model: `prism:model_ref: <ns>:<name>` plus
`prism/models/<name>.bbmodel` in your jar. Prism hands the model to BetterModel (the one place Prism writes files,
because BetterModel only reads from its own folder).

**Menus.** `Prism.menus().open(player, MenuSpec, handler)` shows a list of buttons. For a machine GUI implement
`ContainerMenu` and call `Prism.menus().open(player, menu)`: each slot is a `BUTTON`, an `INPUT` (players put items in,
if `accepts` says so) or an `OUTPUT` (take only). Prism enforces the roles for every click, shift-click, number key,
drop, double-click and drag. `refreshTicks` and `onRefresh` update live values in place without reopening. It is a
chest container, which Geyser shows to Bedrock players. Keep the items in your own model, such as per-block data.
`examplemod:ruby_counter` is a complete example. `allowChange` refuses a slot change synchronously, while the click
is handled; `onSlotChanged` reports it one tick later, and `MenuView.isChangePending(slot)` is true in between, so a
machine ticking meanwhile leaves that slot alone.

**Gauges in menus.** Give an item `prism:gauge` (`style: bar` or `arrow`, `color: "#rrggbb"`, `steps` 1 to 32) and
call `view.setGauge(slot, key, fraction, name, lore)`, for example from `onRefresh`. Prism draws the step icons, empty
to full, into both packs, so Java and Bedrock players see the same stepped bar or arrow. Ship
`textures/item/<name>/gauge_<n>.png` to replace a step with your own art. The counter's cycle arrow is the example.

**Picking blocks up with their data.** A `BlockDropBehavior` writes the block's data into the dropped item
(`Prism.items().editData(drop, ...)`) before remove behaviors run. A `BlockPlaceBehavior` reads it back from
`context.placedFrom()`. The counter keeps its count this way.

**Letting a click through.** An interact behavior that calls `context.pass()` does not use the click, so the held item
is used or placed against the block as with a vanilla block. The counter passes clicks made with a block in hand.

**Typing text.** `Prism.menus().prompt(player, title, initial, text -> ..., onCancel)` asks for a line of text in an
anvil. Bedrock players get the anvil's rename box through Geyser. No item moves. The counter's label button uses it.

**Moving items.** `PrismCapabilities.ITEM_HANDLER` gives an `ItemHandler`: `insert(stack, simulate)`,
`extract(filter, amount, simulate)` and `contents()`. It has no slots, so pipes and storage buses work with any block.
Prism answers it for vanilla containers (furnaces follow hopper side rules) and for custom blocks that offer
`PrismCapabilities.INVENTORY`. A block can also answer it directly, for example a storage network.

**Item data and lore.** `Prism.items().editData(stack, data -> ...)` writes the stack's own persistent data and
re-renders its lore. `prism:lore` holds static lines, and an `ItemLoreBehavior` adds lines computed from the stack.
Both editions show the lore. `examplemod:ruby` counts its uses this way (`UseCounterBehavior`).

**Recipes.** Put recipes in `prism/recipes/**.yml`. `type: prism:shaped`, `prism:shapeless`, `prism:smelting`,
`prism:blasting`, `prism:smoking` and `prism:campfire_cooking` become real crafting table and furnace recipes, so
players craft your items in survival on both editions. A custom item is never used as its carrier material. A machine
registers its own type with `context.registerRecipeType("polishing", Polishing.class, Polishing::decode)` and reads
the recipes with `Prism.recipes().all(type)`. Any mod can add recipes of that type, and `Ingredient`, `ItemResult`
and `Prism.items().matches(...)` handle custom and vanilla items alike. See `examples/example-mod/src/main/resources/prism/recipes/`.

**Data.** `Prism.data().store("examplemod")` is a persistent string key-value store per namespace (SQLite file
`plugins/Prism/data.db`). Calls block on disk, so batch writes and keep them off hot paths.

**Clients.** `Prism.clients().profile(player)` tells you the edition and what the client can show.

**Folia.** Behaviors run on the thread that owns the player or location. Use `Prism.scheduler()`, not the Bukkit
scheduler, for anything you defer. Touch a block only on the thread that owns it (`runAt`); work that spans regions
keeps its own state thread-safe and hops to each block's thread. `runGlobalTimer` is a server-wide clock (a network
tick); it owns no region, so it hops with `runAt` too.
