# ADR-0010: Recipe registry and vanilla recipes

Status: Accepted (2026-11-05). Loading is unit tested, and registration with the server is covered by the smoke test.
Crafting by hand is not covered (see below).

## Context

Every mod with processing has recipes, and every mod needs players to get its items in survival. Before this, each
rebuild hard-coded its machine recipes in Java, and custom items could not be crafted at all. Rows 21 and 22 of
[platform-triage.md](../platform-triage.md). The machine recipe types themselves (enrichment, infusion, petals) stay
plugin-owned (row 21b).

## Decision

- **Recipes are data.** Each recipe is a file at `prism/recipes/**.yml` in a mod's jar. Its `type:` names a recipe
  type, and its id is the mod's namespace plus the path of the file.
  - `ModContext.registerRecipeType(path, Class, decoder)` adds a type. Types work like component types: a type from
    an earlier mod can be used by later mods, so mods can add recipes to each other's machines.
  - `Prism.recipes()` lists recipes by type and follows reloads.
  - Prism never runs a mod's own recipe types. It only loads them, validates them and lists them.
- **Shared vocabulary.** `Ingredient` names an item (custom or vanilla) or a vanilla tag, with a count. `ItemResult`
  names an item and a count. `ItemService.matches(ingredient, stack)` and `ItemService.create(result)` let a machine
  match its inputs and produce its outputs without knowing whether an item is custom.
- **Prism's own types become real vanilla recipes:** `prism:shaped`, `prism:shapeless`, `prism:smelting`,
  `prism:blasting`, `prism:smoking` and `prism:campfire_cooking`.
  - They are re-registered on the global thread after every content change, and the result is logged as
    `PRISM_RECIPES_READY added=N skipped=M`.
  - A custom ingredient becomes an exact choice, so the recipe book shows the real item. Geyser sends server recipes
    to Bedrock clients, so Bedrock players can craft them too.
- **Carrier protection.** A custom item is never used as its carrier material.
  - In crafting, Prism clears the result when the number of custom items in the grid differs from the number of
    custom ingredient cells in the recipe. That covers vanilla recipes and vanilla slots of Prism recipes.
  - In cooking, Prism cancels the cook when a custom item's recipe does not expect that custom item.

## Consequences

- Exact choices compare the whole stack. A custom ingredient whose per-stack data or lore differs from a freshly
  created stack will not match in a crafting table. Mods whose ingredients carry data should match in their own
  machines with `ItemService.matches`, which compares identity only.
- Smithing, stonecutting, anvils, brewing and loom recipes are not covered, and neither is carrier protection for
  them.

## Not verified in CI (hand test with real clients)

- On Java, the example recipes craft and smelt:
  - nine rubies make a ruby block, and the block turns back into nine rubies;
  - rubies around a clock make a ruby counter;
  - a redstone block smelts into a ruby.
- The recipe book shows the custom items.
- A ruby cannot be used as paper in a vanilla recipe.
- All of the above on Bedrock through Geyser.
