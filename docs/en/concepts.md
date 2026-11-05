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

**Getting items.** `/prism give <player> <item> [amount]` and `/prism items` (a browser menu) work for every mod.
Permission: `prism.command.give` (operators by default).

**Models.** A block can use an animated Blockbench model: `prism:model_ref: <ns>:<name>` plus
`prism/models/<name>.bbmodel` in your jar. Prism hands the model to BetterModel (the one place Prism writes files,
because BetterModel only reads from its own folder).

**Menus.** `Prism.menus().open(player, MenuSpec, handler)` shows a list of buttons.

**Data.** `Prism.data().store("examplemod")` is a persistent string key-value store per namespace (SQLite file
`plugins/Prism/data.db`). Calls block on disk, so batch writes and keep them off hot paths.

**Clients.** `Prism.clients().profile(player)` tells you the edition and what the client can show.

**Folia.** Behaviors run on the thread that owns the player or location. Use `Prism.scheduler()`, not the Bukkit
scheduler, for anything you defer. Touch a block only on the thread that owns it (`runAt`); work that spans regions
keeps its own state thread-safe and hops to each block's thread.
