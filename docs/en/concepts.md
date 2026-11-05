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

**Picking blocks up with their data.** A `BlockDropBehavior` writes the block's data into the dropped item
(`Prism.items().editData(drop, ...)`) before remove behaviors run. A `BlockPlaceBehavior` reads it back from
`context.placedFrom()`. The counter keeps its count this way.

**Letting a click through.** An interact behavior that calls `context.pass()` does not use the click, so the held item
is used or placed against the block as with a vanilla block. The counter passes clicks made with a block in hand.

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
