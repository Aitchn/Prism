# ADR-0026: Per-viewer item text, translated in packets

Status: Accepted (2026-11-11). The pure logic (language files, fallback chain, argument formatting, the stored text
form, the round trip of a stack's text and its restoration) is unit tested. The packet layer is checked on a real
server by a self test that runs a stack through PacketEvents' own conversion for two locales (see "Verification").
What a real Java or Bedrock client shows is not verified in CI.

Supersedes the "Lore" part of [ADR-0009](0009-slot-menus-and-item-data.md): lore is no longer text on the stack.

## Context

Players of one server use different languages. Until now an item's name and lore were rendered once, in the mod's
language, and stored on the stack (`item_name`, `lore`). Translating that per player has two hard requirements:

- Stacks must stay identical on the server whoever looked at them, so a stack from a Chinese player stacks with one
  from an English player, and machines, recipes and storage compare them correctly.
- Bedrock players join through Geyser. They must see the same names.

Prism only adds generic primitives: a mod supplies keys and language files, Prism decides how text reaches players.

## Decision

### 1. Language files in the mod jar

`<content root>/lang/<locale>.json` (`prism/lang/en_us.json`, `zh_tw.json`, ...). One JSON object of MiniMessage
templates; nested objects flatten with dots; `{0}`, `{1}` are positional arguments. They are read from the jar at
activation with the rest of the content (ADR-0003: nothing is extracted) and a broken file fails the mod like broken
YAML. The tables of all active mods are merged in activation order (first definition of a key wins) into one immutable
`Translations` snapshot, replaced on every activation and removal, so network threads read it without locks.

Lookup: the locale, then other locales of the same language (Chinese only within its script: `zh_hk` reads `zh_tw`,
never `zh_cn`; other languages prefer `<lang>_<lang>`), then `en_us`, then the key itself used as the template. The
last step makes every existing string a valid "key", so all text-taking APIs accept a key or plain MiniMessage.

At activation Prism prints one `PRISM_LANG` line per mod: its locales, its key count and the missing keys per locale
(a key another locale of the mod defines, or the name key of an item with no name at all).

### 2. The server form of a stack carries keys, never words

- The name comes from the item's key: `item.<namespace>.<path>`, else the placed block's `block.` key, else
  `prism:display_name`.
- The lore is the static `prism:lore` lines plus the `ItemLoreBehavior` lines. Behaviors now return `Message`s (key and
  arguments) from `loreMessages`; the old `lore()` strings still work and are taken as keys or literal text. The
  computed lines are stored in the stack's persistent data as `prism:text` in a deterministic encoding (`TextSpec`),
  recomputed on create, `editData` and `refresh` exactly as rendered lore was before. Equal data gives equal bytes.
- Menu icons use the same field for their name and lore (`MenuView.setIcon/setGauge` with keys or `Message`s).
- `item_name` and `lore` are never set on Prism's stacks. Prism owns both; a custom name from an anvil is kept.

`TranslationService` (`Prism.translations()`) exposes the tables to mods: `translate`, `render`, `send`,
`sendActionBar`, `itemName`, `blockName`, and `name`/`lore` of a stack as a given locale sees it. No PacketEvents type
is involved.

### 3. Packets add the words for each viewer and take them off again

`ItemTextRewriter` (prism-client, PacketEvents) finds Prism stacks by `prism:item` or `prism:text` in `custom_data` and,
for the viewer's locale, sets `item_name` and prepends the translated lines to whatever `lore` the stack has.

- Outgoing: set slot, window items and carried item, cursor item, player inventory slot, entity equipment, entity
  metadata (item entities, item frames, displays, any item value), merchant offer results, recipe book entries and
  ghost recipes (every slot display that holds a full stack), advancement icons.
- Incoming: creative inventory action (the client sends a whole stack: `item_name` is set back to the item type's
  default and exactly the prepended lines are dropped, so the patch disappears and the stack encodes like the original;
  the count of added lines does not depend on the language, because each message is one line). Click window: since
  1.21.5 the client sends only hashes; the hashes of `item_name` and `lore` are dropped so that the server's comparison
  matches its own stack instead of resending the slot. No other serverbound packet carries a stack the server keeps.

Container titles and prompt titles are translated on the server when the inventory is created (it is per viewer
already). Messages are translated on the server by `TranslationService.send`.

### 4. Locale and change

The locale is what the client reports: recorded on join and on `PlayerLocaleChangeEvent` into a concurrent map that
the network threads read; Paper's `Player#locale()` is the fallback. On a change Prism calls `updateInventory()` so the
open container and the player's inventory are resent in the new language. Geyser reports the Bedrock client's language
the same way, so Bedrock players take the same path.

### 5. Stacks from before

Stacks made before this change have `item_name` and `lore` baked in and would not stack with new ones. A Prism stack
with either is normalised (`refresh`: both removed, `prism:text` recomputed) when it is seen: the player's inventory and
ender chest on join, a container's contents when it is opened (not Prism's own menus), an item when it is picked up.
The check is one persistent data lookup per stack.

## Alternatives considered

- **Client-side translatable components plus resource-pack language files.** Put
  `Component.translatable("item.examplemod.ruby")` on the stack and ship `assets/<ns>/lang/*.json` in the pack. No
  packet work, and the stack is the same for everyone. Rejected: Geyser translates Java translatable components with
  Bedrock's vanilla language data only, so Bedrock players would see raw keys unless Prism also generated Bedrock
  language files and kept both packs in sync; arguments in lore (a count, a stored item) need `TranslationArgument`s
  that differ per stack anyway; the anvil and name tags show the raw key as the default name; and a mod could not send a
  chat message or a title in the player's language without the same client lookup. Translating in packets gives one
  path for both editions and lets Prism own the fallback chain and the missing-key report.
- **Baked stacks per locale.** Render the text into the stack for each player when it enters their inventory. Rejected:
  stacks then differ between players, so they do not stack, hoppers and storage compare them differently, and every
  transfer between players would need a rewrite of the server's own stack.
- **Rendering lore in the packet listener from the live stack** (call `ItemLoreBehavior` on the network thread). Rejected:
  mod code would run on network threads and on Folia outside the owning region, and it would run once per packet. The
  computed keys and arguments are stored instead, which keeps the listener free of mod code and world state.

## Consequences

- Item text is generic: any mod gets per-player names, lore, menu text and messages from language files.
- Every packet that can carry a Prism stack is decoded by PacketEvents, including entity metadata. Untouched packets are
  not re-encoded.
- A plugin that sets lore on a Prism item: the lines show below Prism's, but a Prism stack with lore is normalised when
  seen, so such lore does not last outside Prism's menus. Name and lore of Prism items belong to Prism.
- The Bedrock resource pack still names custom items from `prism:display_name` (Geyser mappings) and synthetic blocks by
  their key; in game the translated `item_name` of each stack is what Bedrock players see.
- Not translated: item names inside chat hover events (`show_item`), menu titles of an inventory that is already open
  when the locale changes, and merchant trade costs (they are item predicates, not stacks).

## Verification

- Unit tests (`prism-core`): `LangJsonTest`, `TranslationsTest` (fallback chain, normalisation, arguments, escaping,
  nested messages), `ItemTextRendererTest` (per-locale output, byte-for-byte round trip on a model of the network
  stack, server lore kept, icons, foreign stacks untouched, encoding), `LangSummaryTest` (summary line, loading from a
  jar source, failure on a broken file). The example mod's tests check its tables and its summary.
- Smoke test: `PRISM_LANG ExampleMod ... missing=1 (zh_tw: examplemod.demo.english_only)` (the example keeps one key
  untranslated on purpose) and `PRISM_LANG_SELFTEST_OK`: a real `examplemod:ruby` stack goes through PacketEvents'
  conversion, is rewritten for `en_us` and `zh_tw` (two different names), restored, converted back, and must equal the
  original and be `isSimilar` to it; a copy with a baked name and lore must be normalised to the same stack.
  `EXAMPLE_LORE_OK` checks that the stack has no lore and reads in both languages through the API.

### Not verified in CI (hand test with real clients)

- A Java client in `zh_tw` and one in `en_us` see the ruby, the counter's menu and `/prism items` in their language,
  and a ruby dropped by one stacks in the other's inventory.
- Switching the client language with an inventory open changes the item text at once.
- Creative mode: taking a ruby from the inventory and placing it back gives a stack equal to the original.
- A Bedrock client through Geyser sees translated names and lore.
