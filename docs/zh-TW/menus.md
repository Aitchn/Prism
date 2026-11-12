# 選單

Prism 的選單是兩種版本都能開啟的箱子介面：Java 玩家直接開啟，基岩版玩家透過 Geyser 開啟。本頁介紹兩種選單，以及如何為選單
加上自己的背景圖、會動的元件與隱藏格（[ADR-0009](../adr/0009-slot-menus-and-item-data.md)、
[ADR-0019](../adr/0019-menu-gauges.md)、[ADR-0028](../adr/0028-menu-backgrounds-and-pixel-widgets.md)）。

## 兩種選單

- **按鈕清單：** `Prism.menus().open(player, new MenuSpec(title, buttons), id -> ...)`，玩家從中選一個。
- **有真正格子的容器：** 實作 `ContainerMenu` 並呼叫 `Prism.menus().open(player, menu)`。每一格是 `BUTTON`、`INPUT` 或
  `OUTPUT`，Prism 會對各種點擊強制執行這些角色。`onRefresh` 每 `refreshTicks` 執行一次，用來更新即時數值。

標題、按鈕文字、名稱與說明文字都是翻譯鍵（見[翻譯](translations.md)）。

## 量表物品

量表是帶有 `prism:gauge` 的物品（`style: bar` 或 `arrow`、`color`、`steps` 1 到 32）。
`view.setGauge(slot, key, fraction, name, lore)` 會顯示最接近 `fraction` 的那一階。它佔用一格，在 Java 與基岩版上看起來相同。

## 背景與像素元件

選單檔描述一個選單的美術。裝了 Prism 資源包的 Java 玩家會看到墊在格子後面的圖、畫在任意像素位置的箭頭與量條，而圖上沒有格子
的地方就什麼都沒有。

### 完整範例

範例模組的計數器選單（`examples/example-mod`）有三列。它的選單檔是 `src/main/resources/prism/menus/counter.yml`，所以鍵是
`examplemod:counter`：

```yaml
background: gui/counter        # assets/examplemod/textures/gui/counter.png，176x71
rows: 3
slots: [11, 13, 15, 22]        # 圖上畫出的格子；其餘格子都隱藏
widgets:
  cycle:
    type: progress
    sprite: gui/counter_arrow  # 16x11，由左向右顯露
    x: 62
    y: 38
    steps: 10
    fallback:
      slot: 12                 # 在 Java 上隱藏；其他玩家在此看到量表物品
      item: cycle_gauge
  store:
    type: bar
    sprite: gui/counter_store  # 8x50，由下往上顯露
    x: 156
    y: 18
    steps: 16
```

選單指定自己的美術並推動元件：

```java
final class CounterMenu implements ContainerMenu {
    static final PrismKey ART = PrismKey.parse("examplemod:counter");

    @Override
    public PrismKey art() {
        return ART;
    }

    @Override
    public void onRefresh(MenuView view) {
        int cycle = count % 10;
        view.setWidget("cycle", cycle / 10.0, Message.of("examplemod.counter.cycle", cycle),
                List.of(Message.of("examplemod.counter.cycle_hint")));
    }

    private static void drawStore(MenuView view, ItemStack item) {
        view.setWidget("store", item == null ? 0 : (double) item.getAmount() / item.getMaxStackSize());
    }
}
```

傳給 `setWidget` 的名稱與說明文字用在備用物品上；在 Java 上圖片沒有提示框。

### 選單檔

| 欄位 | 意義 |
|---|---|
| `background` | 材質鍵：`gui/machine` 指的是 jar 中的 `assets/<命名空間>/textures/gui/machine.png`。可省略。 |
| `rows` | 1 到 6（預設 3）。取代選單本身的 `rows()`。 |
| `title` | `{x: 8}` 將標題文字左右移動（距左緣的像素）；`false` 在 Java 上隱藏標題。文字仍在原版的標題列上。 |
| `slots` | 圖上用到的格子編號與範圍（`"0-8"`）。其餘格子都隱藏。省略表示全部格子都用。 |
| `widgets` | 具名的元件，依此順序繪製。 |

元件欄位：

| 欄位 | 意義 |
|---|---|
| `type` | `progress`（分段顯露的圖）、`bar`（以圖或顏色填滿）、`icon`（每個狀態一張圖） |
| `x`、`y` | 左上角，距容器左上角的像素。 |
| `sprite` | progress 與 bar：完整的圖。 |
| `color`、`width`、`height` | 沒有圖的 bar：以此顏色與大小產生的填色。 |
| `sprites` | icon：每個狀態一張材質（最多 16 張）。 |
| `direction` | `right`（progress 預設）、`left`、`up`（bar 預設）或 `down`：填滿往哪個方向長。 |
| `steps` | progress 與 bar：1 到 64，預設 16。 |
| `fallback` | `{slot, item}`（一個 `prism:gauge` 物品），icon 則是 `{slot, items: [...]}`，每個狀態一個物品。 |

`view.setWidget(id, fraction)` 推動 progress 或 bar 元件；`view.setWidgetState(id, state)` 選擇 icon 的狀態。第 0 階什麼都
不畫；第 `steps` 階只在全滿時出現。

### 材質尺寸

- 一個字形最高 256 像素（用戶端的限制），Prism 也把寬度限制在 256。過大或缺少的材質會被略過，並記錄
  `PRISM_PACK_WARNING`。
- 原版容器寬 **176** 像素。選單部分高 **`列數 × 18 + 17`**（3 列為 71，6 列為 125）；下方玩家物品欄部分再多 96
  （合計 **`列數 × 18 + 114`**）。只畫選單部分可保留下方原版物品欄，畫滿整個高度則連物品欄一起取代。
- 第 `i` 格的物品位於 `x = 8 + (i % 9) × 18`、`y = 18 + (i / 9) × 18`（16×16）。格框畫在它外圍一像素。
- 玩家物品欄的「物品欄」標籤由用戶端畫在你的圖上方。

### 各玩家看到什麼

| | 有資源包的 Java | 基岩版，或沒有資源包的 Java |
|---|---|---|
| 背景 | 圖片 | 一般箱子介面 |
| 標題 | 在美術指定的 x，或隱藏 | 翻譯後的標題，一律顯示 |
| 元件 | 畫出且會動 | 備用物品，放在備用格 |
| 隱藏格 | 空的且無作用（滑鼠移過時用戶端仍會反白） | 空的且無作用 |

第一欄的玩家 `view.showsArt()` 為 true。只有畫出的階段改變時 Prism 才重送標題，且最多每兩刻一次，所以 `setWidget` 想呼叫幾次都
可以。

### 限制

- 元件改變時會以新標題重新開啟畫面，這會中斷玩家當下正在拖曳的物品。
- 標題文字無法上下移動。
- 資源包仍在載入的 Java 玩家，在載入完成前會在標題看到方框。
