# 世界特效

方塊可以自己冒出粒子，模組可以讓東西從一點飛到另一點，模型也能依變體上色。這些都不是伺服器實體：粒子與移動的模型只存在於
送給「距離夠近且已載入該區塊」的玩家的封包中，沒有人的區域不會讓伺服器多花任何成本。

## 方塊冒出粒子：`prism:particles`

```yaml
# prism/blocks/ruby_lamp.yml
base: minecraft:barrier
components:
  prism:variants: [idle, active]
  prism:particles:
    # 所有變體：每秒兩次，每次三顆粒子，位於方塊正上方。
    - particle: minecraft:dust
      color: "#ff4060"
      size: 0.8
      rate: 2
      count: 3
      offset: [0, 0.7, 0]
      spread: [0.25, 0.05, 0.25]
    # 只在 active 時：每兩秒一顆火花，24 格內看得到。
    - particle: minecraft:end_rod
      rate: 0.5
      offset: [0, 0.6, 0]
      speed: 0.02
      range: 24
      variants: [active]
```

| 鍵 | 意義 |
|---|---|
| `particle` | 原版粒子 id，例如 `minecraft:flame`、`minecraft:dust`、`minecraft:end_rod`。 |
| `count` | 每次發出的粒子數，1 到 256（預設 1）。 |
| `spread` | 散開的範圍，一個數字或 `[x, y, z]`，單位為格（預設 0）。 |
| `speed` | 粒子速度（預設 0）。 |
| `color` | `"#rrggbb"`，用於可上色的粒子（`dust`、`entity_effect` 等）。 |
| `size` | 粉塵大小，大於 0、最多 16（預設 1）。 |
| `material` | 物品或方塊 id，用於 `item`、`block`、`falling_dust`、`dust_pillar`、`block_marker`。 |
| `rate` | 每秒發出次數，大於 0、最多 20（預設 1）。`0.5` 代表每兩秒一次。 |
| `offset` | 相對方塊中心的 `[x, y, z]`，會隨方塊朝向旋轉（預設 `[0, 0, 0]`）。 |
| `range` | 玩家在多遠內仍看得到，最多 128 格（預設為伺服器的 `effects.particles.range`）。 |
| `variants` | 只在這些變體中發出（預設：全部）。 |

一個方塊最多 8 個發射器。用 `Prism.blocks().setVariant(...)` 切換變體會改變哪些發射器運作；方塊移除後就停止。不存在的粒子
id，或缺少所需資料的粒子，只會記錄一次 `PRISM_EFFECTS_WARNING` 並略過。

運作方式：一個共用時鐘每隔幾刻檢查所有會發出粒子的方塊。玩家只會收到「客戶端已有的區塊」中、在範圍內的發射器，由近到遠，
直到每位玩家的上限。並排的相同方塊不會同步閃爍。

## 飛行特效、爆發與光束：`Prism.effects()`

```java
EffectService effects = Prism.effects();

// 一個模型在兩秒內從一個方塊飛到另一個方塊，沿途留下火花，抵達時做一件事。
Travel travel = Travel.between(from.getLocation().add(0.5, 1, 0.5), to.getLocation().add(0.5, 1, 0.5), 40)
        .withModel(PrismKey.parse("examplemod:ruby"))
        .withScale(0.5f)
        .withTrail(ParticleSpec.of("minecraft:end_rod"), 2)
        .onArrive(() -> to.getWorld().playSound(to.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1, 1));
TravelHandle handle = effects.launch(travel);
// handle.cancel() 可提早停止；之後回呼不會執行。

// 在一點發出一次粒子，以及一道瞬間光束（每格四個點）。
effects.burst(location, ParticleSpec.dust(0xff4060, 1.5f).withCount(20).withSpread(0.3, 0.3, 0.3));
effects.line(start, end, ParticleSpec.of("minecraft:electric_spark"), 4);
```

- `Travel.between(from, to, ticks)` 或 `Travel.along(from, direction, distance, ticks)`；最多 6000 刻。
- `withModel` 接受 Prism 物品鍵（使用其模型）或任何物品模型 id（`minecraft:ender_pearl`）。`withBillboard` 決定它如何
  面向觀看者（預設 `CENTER`）。
- `withTrail(particle, points)` 每刻沿該刻飛過的路段送出 1 到 8 個點。
- `onArrive` 在飛行結束時執行一次，在擁有目的地的執行緒上（Folia 上是該區域），因此可以修改那裡的方塊。即使沒有玩家看到
  也會執行。
- 所有方法都可以從任何執行緒呼叫。

模型由客戶端自己移動，所以每位玩家每次飛行只需一次生成、最多每三秒一次移動、一次移除。開始時位於路徑附近且已載入該區塊的
玩家會看到它。同時會看到超過 `effects.travel.max-per-player` 個的玩家，最遠的會被捨棄。

## 染色：`prism:tint`

從其他遊戲取得的模型常以 `"tintindex": 0` 繪製面，並期待遊戲為它上色。`prism:tint` 為每個索引指定顏色，可依變體不同：

```yaml
  prism:tint:
    colors: ["#ffb0b0"]          # 索引 0、1、...
    variants:
      active: ["#3ab5ff"]        # 在此變體取代 colors
```

簡短寫法是直接給清單：`prism:tint: ["#ffb0b0"]`。顏色套用在物品，以及由顯示層繪製的方塊。以借用方塊狀態繪製的方塊無法染色；
Prism 會發出警告（`PRISM_PACK_WARNING`），你可以為該方塊加上 `prism:render: display`（見[大量方塊外觀](block-looks.md)）。

## 伺服器設定

```yaml
effects:
  view-range: 48            # 玩家在離飛行特效、爆發或光束多遠內仍會收到
  particles:
    interval: 5             # 發射器時鐘兩次執行之間的刻數（1 到 100）
    range: 32               # 未自行設定範圍的發射器範圍（1 到 128）
    max-per-player: 2000    # 每位玩家每秒從方塊發射器收到的粒子上限；近的方塊優先
  travel:
    max-per-player: 64      # 每位玩家同時看到的飛行特效上限；最遠的會被捨棄
```

修改這些設定需要重新啟動。

## 基岩版

| 特效 | 基岩版（Geyser） |
|---|---|
| 方塊粒子、爆發、光束、軌跡 | 有，由 Geyser 轉換 |
| 飛行中的模型 | 沒有，只有軌跡 |
| `onArrive` | 有（在伺服器上執行） |
| 染色 | 有：基岩版資源包內含每張染色材質的重新上色副本；物品圖示使用第一個顏色 |

Geyser 無法轉換的粒子不會顯示給基岩版玩家。以上尚未在實際的基岩版客戶端上驗證。
