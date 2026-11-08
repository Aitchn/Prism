# ADR-0014: Item handler capability and text prompts

Status: Accepted (2026-11-08). The item handler is covered by the platform self test against a real chest. The text
prompt needs a player, so CI does not cover it (see below).

## Context

Rows 35 and 37 of [platform-triage.md](../platform-triage.md).
- **Row 35.** Every mod that moves items (pipes, transporters, storage buses, import and export) needs to put items
  into a block and take them out of it. It needs this without knowing whether the block is a chest, a furnace, a
  machine or a storage network. Items are a vanilla resource, so the unit is not a mod's design, unlike energy or mana.
- **Row 37.** Search boxes, amounts and names need text input, and chest menus cannot take text.

## Decision

- **`ItemHandler`** has `insert(stack, simulate)`, `extract(filter, amount, simulate)` and `contents()`.
  - It has no slots, so a storage network can answer it without inventing them.
  - Passed stacks are never modified. Results are new stacks, and the empty stack means none.
- **`PrismCapabilities.ITEM_HANDLER`** is answered in this order:
  1. by a custom block's own `BlockCapabilityProvider`;
  2. otherwise from the custom block's `INVENTORY` (all slots);
  3. for vanilla containers, by an adapter.
- **The vanilla adapter** inserts onto similar stacks first, then into empty slots, and respects stack limits.
  - Furnaces follow hopper rules: the top fills the input, the sides fill the fuel (fuel only), and only the bottom
    gives the result.
  - Shulker boxes refuse shulker boxes.
- **`MenuService.prompt(player, title, initial, onSubmit, onCancel)`** opens an anvil, created with
  `MenuType.ANVIL`.
  - The left slot holds a paper named with the initial text. The result follows what the player types, at no cost.
    Taking the result submits.
  - Every click in the view is cancelled, and the anvil is emptied before it closes, so no item is given or taken.
  - Geyser shows Bedrock players the anvil's rename box, so both editions can type.
  - Closing the anvil any other way calls `onCancel`.

## Consequences

- Moving items is one call for any block. Mods' own resources (energy, mana, chemicals) stay mod capabilities.
- A text prompt replaces the open menu. A menu that wants the player back reopens itself in `onSubmit`.

## Not verified by CI

- Clicking the counter's label button, typing a label and taking the result, on Java and on Bedrock. The label
  should show the next time the menu opens. Closing the anvil with Escape should change nothing and return no paper.
- A hopper-like pipe built on `ITEM_HANDLER` feeding a furnace from the top and the side.
