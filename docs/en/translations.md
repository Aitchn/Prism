# Translations

Every player sees your items, menus and messages in the language their client is set to. The server keeps one form of
each item stack, with no text of any language in it, so a stack dropped by a Chinese player stacks with one picked up
by an English player. Bedrock players (through Geyser) get the same, in the language of their Bedrock client.

## Language files

Put one JSON file per locale in your jar, next to your other content:

```
src/main/resources/prism/lang/en_us.json
src/main/resources/prism/lang/zh_tw.json
```

The file name is the Minecraft language code in lower case. A file is one JSON object of strings; nested objects are
joined with dots, so `{"item": {"examplemod": {"ruby": "Ruby"}}}` is the same as `{"item.examplemod.ruby": "Ruby"}`.
Values are MiniMessage text (`<red>Ruby`). `{0}`, `{1}`, ... are replaced by arguments. Language files are read from
the jar when the mod is activated, like the rest of its content, and never copied to a server folder. A file that is
not valid stops the mod from activating, with the line of the mistake.

| What | Key |
|---|---|
| Item name | `item.<namespace>.<path>`, `/` in the path becomes `.` (`examplemod:gems/ruby` is `item.examplemod.gems.ruby`) |
| Block name | `block.<namespace>.<path>`. An item that places a block and has no `item.` key is named after the block. |
| Static lore, menu titles, icons, messages | any key you choose, for example `examplemod.lore.heal` |

`prism:display_name` still works and is used when no language file names the item.

## Which text a player gets

A key is looked up in this order:

1. the player's locale (`zh_tw`);
2. another locale of the same language: `fr_ca` reads `fr_fr`, then the others. Chinese stays within its script:
   `zh_hk` reads `zh_tw`, never `zh_cn`;
3. `en_us`;
4. the key itself, as MiniMessage text. So a string that is not a key is simply shown as written.

At startup Prism prints one line per mod with what is missing:

```
PRISM_LANG ExampleMod locales=en_us,zh_tw keys=27 missing=1 (zh_tw: examplemod.demo.english_only)
```

A key counts as missing in a locale when another locale of the same mod has it, or when it names an item that has no
name at all.

## Items

The name of an item is its `item.` key. Its lore is its static `prism:lore` lines (each a key or text), followed by the
lines of its `ItemLoreBehavior`. Return translation keys with arguments from `loreMessages`:

```java
@Override
public @NotNull List<Message> loreMessages(@NotNull ItemStack stack) {
    int uses = stack.getPersistentDataContainer().getOrDefault(USES, PersistentDataType.INTEGER, 0);
    return List.of(Message.of("examplemod.lore.used", uses));
}
```

```json
{ "examplemod.lore.used": "<gray>Used <white>{0}</white> times" }
```

Arguments are text (shown as typed, tags are not interpreted), numbers (with thousands separators), booleans, or another
`Message` (translated for the same player, for example `Prism.translations().itemName(key)`). The lines are kept on the
stack as keys and arguments and turned into words only in the packets each player receives. Do not set a name or lore
on your items yourself: Prism owns both on its items. A name given in an anvil is kept.

To know what a player sees, for a book or a chat line, use `Prism.translations().name(stack, locale)` and
`Prism.translations().lore(stack, locale)`.

## Menus

Menu titles, button labels, `MenuView.setIcon` and `setGauge` names and lore, and prompt titles all take a key or text.
Use the `Message` overloads for arguments:

```java
view.setIcon(COUNT_SLOT, PrismKey.parse("minecraft:clock"), Message.of("examplemod.counter.count", count),
        List.of(Message.of("examplemod.counter.count_hint")));
```

A title is translated when the menu opens. Icons are translated per viewer like any item. An empty label keeps an
icon's own name, which is how `/prism items` shows every item in the viewer's language.

## Messages

```java
Prism.translations().send(player, Message.of("examplemod.counter.busy"));
Prism.translations().sendActionBar(player, Message.of("examplemod.counter.count", 3));
Component title = Prism.translations().render(Message.of("examplemod.title"), player);
```

These can be called from any thread.

## When a player changes language

Prism resends the open inventory and the player's own inventory at once, so items switch language without reopening. A
menu title stays as it was until the menu is opened again.

## Stacks from before

Items made before this feature had their name and lore stored in them, in one language. Prism rewrites such a stack the
first time it is seen (the player's inventory and ender chest on join, a container when it is opened, an item when it
is picked up), so it stacks with new ones again.

## Limits

- Item names in chat hover text (`show_item`) are not translated.
- The names in the Bedrock resource pack (Geyser's item mappings and the synthetic block names) are fixed when the pack
  is built; in game, Bedrock players see the translated item name sent with each stack.
- Menu titles change language only when the menu is reopened.
