# 大量方塊外觀

一個 Mod 可以有任意多種自訂方塊外觀。Java 版玩家只需要資源包，客戶端不用安裝其他東西。（*外觀*指方塊某個變體朝向某個方向的樣子。）

## 外觀怎麼畫

Prism 每次開機都依目前註冊的內容與伺服器設定，為每個外觀決定其中一種呈現方式（世界裡不存任何外觀，所以 Mod 可以隨意新增或移除方塊）：

- **顯示層**（D 層），預設。對客戶端來說方塊是一個碰撞正確的隱形外殼，附近每位玩家會收到一個物品顯示實體，畫出方塊自己的模型。
  伺服器上不會生成任何實體：這些實體只存在於送給玩家的封包裡。每個外觀都完全正確，數量沒有上限。
- **借用的原版狀態**（S 層），需要時才用。共 931 個原版方塊狀態，資源包可以替換它們的模型，因為這些方塊的真實方塊都能改以另一個
  外觀完全相同的狀態顯示：音符盒（799）、絆線（64）、標靶（15）、發射器與投擲器（各 6）、杉木與白樺樹葉（各 13）、甘蔗（15）。
  它們對客戶端的成本和一般方塊一樣，但數量少、由所有 Mod 共用，而且方塊會帶有所借狀態的物理特性。這些種類的原版方塊會以對應的
  普通兄弟狀態送給玩家，所以玩家放的音符盒看起來還是音符盒。

要求借用狀態的外觀，會先取同一**類別**還剩下的狀態。類別由碰撞（實心或無）、是否遮擋鄰居、以及音效組組成。

### 每個方塊一個實體

預設（`blocks.display.lighting: block`）每個顯示方塊是一個位於方塊中心的實體，外面是屏障：屏障沒有任何資源包模型也是隱形的、
有完整碰撞、會讓光通過，所以實體由它自己所在的格子照亮。伺服器在那裡存的是玻璃，原因相同。這類方塊由 Prism 自己處理挖掘
（屏障不會顯示裂痕，所以進度會顯示在動作列）。六面都被實心方塊擋住的方塊完全不會有實體。可穿過的方塊仍使用沒有碰撞的外殼。

`blocks.display.lighting: faces` 則改為每個可見的面一個實體，每個面都像原版方塊的面一樣受光，外殼會像一般方塊一樣擋光，
每個方塊最多六個實體。

## 元件

```yaml
components:
  prism:render: auto        # auto（預設）、state 或 display
  prism:surface:
    sound: glass            # wood、stone、gravel、grass、sand、snow、wool、metal、glass、bone、slime、netherrack、deepslate、copper
    occludes: false         # 是否遮擋鄰居方塊的面
```

- `prism:render: auto`：依伺服器的 `blocks.render` 設定（除非服主改過，否則是 `display`）。
- `state`：要求借用狀態；沒有時方塊仍會用顯示層運作，日誌會提示（`PRISM_LOOK_WARNING`）。
- `display`：一律走顯示層。
- `prism:surface` 用來設定音效組，以及像玻璃這種不遮擋鄰居的方塊。可穿過的方塊（`prism:passable`）本來就是「無碰撞、不遮擋」。

## 挖掘

生存模式下，玩家挖自訂方塊的時間依 `prism:mining` 而定。借用狀態的方塊，Prism 只針對這一次挖掘調整客戶端的破壞速度；
在屏障外殼裡的顯示方塊，則由 Prism 自己執行挖掘。可穿過的方塊和以前一樣瞬間破壞。

## 設定（`config.yml`）

| 設定 | 預設 | 意義 |
|---|---|---|
| `blocks.render` | `display` | `prism:render: auto` 的意思：`display`，或 `state`（先借用狀態）。 |
| `blocks.display.lighting` | `block` | `block`：每個方塊一個實體，光會穿過。`faces`：每個可見的面一個實體。 |
| `blocks.display.max-per-player` | `12000` | 每位玩家最多收到的顯示實體數；最近的區塊優先。 |
| `blocks.display.view-distance` | `8` | 玩家周圍會收到顯示實體的區塊數；`0` 表示客戶端有的所有區塊。 |
| `blocks.display.view-range` | `1.0` | 實體的可視範圍倍率。 |
| `blocks.display.solid-shells` | `false` | 僅限 faces：實心顯示方塊改用不透明的音符盒外殼。除非已在客戶端確認，否則請維持關閉。 |

以上設定都需要重新啟動。舊版的設定 `blocks.backend` 在設定檔沒有 `blocks.render` 時仍會讀取：`auto` 與 `display` 視為
`display`，`native` 視為 `state`；日誌會說明做了什麼（`PRISM_RENDER_NOTE`）。

## 測量

`/prism bench displays <數量> [半徑]`（權限 `prism.command.bench`，預設只有管理員）會在你上方以稀疏的格點放置最多「數量」個
測試方塊，彼此不互相遮擋，並回報你收到多少顯示實體，以及放置前與五秒後伺服器的平均 tick 時間。第一次使用會把測試方塊加進資源包，
客戶端詢問時請再接受一次資源包。`/prism bench clear` 會移除這些方塊。

## 限制

- 每個顯示實體在每個擁有它的客戶端上每一幀都要繪製。預算限制每位玩家的數量，放不下的是最遠的；建築很多時請調低
  `max-per-player` 或 `view-distance`。
- `block` 模式下光會穿過顯示方塊：用它們蓋的屋頂擋不住天光，火把會照亮兩側。它們不傳導紅石，生物也不會在上面生成。
  在意這些時請用 `blocks.render: state` 或 `blocks.display.lighting: faces`。
- 面數多的模型每個實體成本更高；資源包建置時會對超過 24 個面的模型發出警告。
- Geyser 在同一台伺服器時，基岩版玩家會把每個外觀都看成真正的自訂方塊（`bedrock.synthetic-blocks`，ADR-0024；見 `bedrock.md`）。否則他們只看得到借用的音符盒與絆線外觀，顯示層方塊對他們是隱形的：若方塊在那時必須在基岩版可見，請設 `prism:render: state`，並讓這類方塊的數量保持在借用狀態的數量以內。
- 未安裝的 Mod 的方塊沒有外觀，玩家看到的是普通載體。

目前的預設請見 [ADR-0025](https://github.com/Aitchn/Prism/blob/main/docs/adr/0025-display-first.md)；外觀池、
替代方案與尚未在真實客戶端驗證的部分，請見
[ADR-0023](https://github.com/Aitchn/Prism/blob/main/docs/adr/0023-look-pool-and-display-layer.md)。
