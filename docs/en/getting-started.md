# Getting started

You need JDK 25, a Paper/Folia/Purpur server with [PacketEvents](https://github.com/retrooper/packetevents) and
[BetterModel](https://github.com/toxicity188/BetterModel), and `Prism.jar` in `plugins/`.

A mod is a normal plugin. The complete, tested version of what follows is
[`examples/example-mod`](https://github.com/Aitchn/Prism/tree/main/examples/example-mod).

## 1. plugin.yml

```yaml
name: ExampleMod
main: com.example.prism.ExampleMod
api-version: '1.21.4'
folia-supported: true
depend:
  - Prism
```

The namespace of your content is the lower-cased plugin name (`examplemod`). Nothing else needs configuring.

## 2. An item, in YAML

`src/main/resources/prism/items/ruby.yml` (the key is `examplemod:ruby`):

```yaml
base: minecraft:paper
components:
  prism:display_name: "<red>Ruby"
  prism:max_stack_size: 16
```

## 3. A texture

Put `src/main/resources/assets/examplemod/textures/item/ruby.png` in the jar. Prism generates the item model and
the resource pack, and serves the pack itself (see `pack.*` in Prism's `config.yml`). Content stays inside your jar;
Prism never extracts it into a server folder.

## 4. A behavior (optional)

```java
public final class ExampleMod extends JavaPlugin implements PrismMod {
    @Override
    public void onInitialize(@NotNull ModContext context) {
        context.registerBehavior("heal", HealBehavior.class, HealBehavior::fromYaml);
    }
}
```

Bind it from YAML with `behaviors: [{type: examplemod:heal, amount: 4}]`. A behavior class implements a capability
interface such as `ItemUseBehavior`.

## 5. Test without a server

```java
var harness = ModTestHarness.load("examplemod", new ExampleMod(), Path.of("src/main/resources"));
assertTrue(harness.report().active(), harness.report().problems().toString());
```

`prism-testkit` loads your content through the real lifecycle and reports every validation problem at once.

## 6. Give yourself the item

Start the server and use `Prism.items().create(new PrismKey("examplemod", "ruby"), 1)` from your plugin. Players
who accept the resource pack see the texture.
