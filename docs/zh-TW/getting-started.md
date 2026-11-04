# 快速開始

需要 JDK 25、安裝了 [PacketEvents](https://github.com/retrooper/packetevents) 與
[BetterModel](https://github.com/toxicity188/BetterModel) 的 Paper/Folia/Purpur 伺服器，以及放在 `plugins/` 的 `Prism.jar`。

Mod 就是一般插件。下列內容的完整可測版本在
[`examples/example-mod`](https://github.com/Aitchn/Prism/tree/main/examples/example-mod)。

## 1. plugin.yml

```yaml
name: ExampleMod
main: com.example.prism.ExampleMod
api-version: '1.21.4'
folia-supported: true
depend:
  - Prism
```

內容的命名空間是插件名稱的小寫（`examplemod`），不需其他設定。

## 2. 用 YAML 定義物品

`src/main/resources/prism/items/ruby.yml`（鍵為 `examplemod:ruby`）：

```yaml
base: minecraft:paper
components:
  prism:display_name: "<red>Ruby"
  prism:max_stack_size: 16
```

## 3. 貼圖

把 `src/main/resources/assets/examplemod/textures/item/ruby.png` 放進 jar。Prism 會產生物品模型與資源包，並自行提供下載
（見 Prism `config.yml` 的 `pack.*`）。內容一律留在你的 jar 內，Prism 不會把它解壓到伺服器資料夾。

## 4. 行為（選用）

```java
public final class ExampleMod extends JavaPlugin implements PrismMod {
    @Override
    public void onInitialize(@NotNull ModContext context) {
        context.registerBehavior("heal", HealBehavior.class, HealBehavior::fromYaml);
    }
}
```

在 YAML 以 `behaviors: [{type: examplemod:heal, amount: 4}]` 綁定。行為類別實作如 `ItemUseBehavior` 的能力介面。

## 5. 不開伺服器的測試

```java
var harness = ModTestHarness.load("examplemod", new ExampleMod(), Path.of("src/main/resources"));
assertTrue(harness.report().active(), harness.report().problems().toString());
```

`prism-testkit` 以真實生命週期載入你的內容，一次回報所有驗證問題。

## 6. 取得物品

啟動伺服器，在你的插件中呼叫 `Prism.items().create(new PrismKey("examplemod", "ruby"), 1)`。接受資源包的玩家會看到貼圖。
