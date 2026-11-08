# 核心概念

**分層。** 插件（`Prism.jar`）提供 API 與執行環境；*Mod* 是依賴它的插件。主類別實作 `PrismMod`、呼叫
`Prism.mods().register`，或 jar 內有 `prism/` 資料夾，就會被視為 Mod。

**鍵。** 內容以 `PrismKey`（`namespace:value`）識別；`prism` 與 `minecraft` 為保留命名空間。

**生命週期。** `onInitialize` 註冊元件與行為類型；之後 Prism 載入並驗證所有 YAML（一次收集所有問題），再原子式發布。
只要有任何問題，該 Mod 就不發布任何內容。發布後才呼叫 `onContentLoaded`。

**物品。** 原版基底物品＋資料元件＋行為。身分存在持久資料，外觀由指向產生的物品定義之 `item_model` 決定。

**方塊。** 原版載體方塊（如 `barrier`）加上負責繪製模型的持久顯示實體。Prism 把每個區塊裡的自訂方塊索引存在區塊的持久資料中，
所以方塊、朝向與每格方塊的資料重啟後都還在，不需要資料庫。行為可以回應放置、移除、破壞、區塊載入/卸載、鄰居變化與 tick
（`BlockTickBehavior`，同一區塊合併成一個工作）。`CustomBlock.data()` 是每格方塊自己的 `PersistentDataContainer`，方塊移除時一起刪除。
方塊透過 `BlockCapability` 與 `BlockCapabilityProvider` 對鄰居提供服務；Prism 只提供查找機制與 `PrismCapabilities.INVENTORY`。
`prism:rotation` 讓方塊面向玩家放置，`prism:mining` 設定生存模式挖掘所需時間。

**礦石。** `prism:ore` 讓方塊以礦脈形式出現在新生成的區塊：`dimension`、`replace`（可取代的原版方塊）、`min_y`、`max_y`、
`veins_per_chunk`、`rarity` 與 `size`，也可以寫成多組設定的清單。礦脈不會超出所在區塊。安裝模組之前就存在的區塊不會長出礦石。
範例是 `examplemod:ruby_ore`；它使用音符盒狀態呈現，所以基岩版玩家也看得到。

**取得物品。** `/prism give <玩家> <物品> [數量]` 與 `/prism items`（物品瀏覽選單）適用所有模組，權限 `prism.command.give`（預設 OP）。

**模型。** 方塊可用 Blockbench 動畫模型：`prism:model_ref: <ns>:<name>` 加上 jar 內的 `prism/models/<name>.bbmodel`，
由 BetterModel 呈現（Prism 唯一會寫檔的地方，因為 BetterModel 只讀自己的資料夾）。

**選單。** `Prism.menus().open(player, MenuSpec, handler)` 顯示按鈕清單。機器介面請實作 `ContainerMenu` 並呼叫
`Prism.menus().open(player, menu)`：每格是 `BUTTON`（按鈕）、`INPUT`（玩家可放入，`accepts` 決定可放什麼）或 `OUTPUT`
（只能取出）。Prism 會替所有點擊、Shift 點擊、數字鍵、丟棄、雙擊與拖曳執行這些規則。`refreshTicks` 與 `onRefresh`
可就地更新即時數值，不必重開。介面是箱子容器，Geyser 會轉譯給基岩版玩家。物品請保存在自己的模型（例如方塊資料）。
完整範例見 `examplemod:ruby_counter`。`allowChange` 在處理點擊的當下同步拒絕格子變更；`onSlotChanged` 在下一刻才回報，
這段期間 `MenuView.isChangePending(slot)` 為 true，同時在運作的機器應避開該格。

**連同資料撿起方塊。** `BlockDropBehavior` 在移除行為之前，把方塊資料寫進掉落物（`Prism.items().editData(drop, ...)`）；
`BlockPlaceBehavior` 再從 `context.placedFrom()` 讀回。計數器就是這樣保留計數。

**讓點擊通過。** 互動行為呼叫 `context.pass()` 表示沒有使用這次點擊，手上的物品會像對原版方塊一樣使用或放置。手上拿著方塊點擊計數器時會放行。

**輸入文字。** `Prism.menus().prompt(player, title, initial, text -> ..., onCancel)` 會用鐵砧介面請玩家輸入一行文字，
基岩版玩家透過 Geyser 看到鐵砧的改名欄位。過程中不會移動任何物品。計數器的標籤按鈕就是範例。

**搬運物品。** `PrismCapabilities.ITEM_HANDLER` 提供 `ItemHandler`：`insert(stack, simulate)`、
`extract(filter, amount, simulate)` 與 `contents()`。它不以格子為單位，所以管線與儲存匯流排可以對任何方塊運作。
Prism 為原版容器提供這個能力（熔爐依漏斗的面向規則），也為提供 `PrismCapabilities.INVENTORY` 的自訂方塊提供。
方塊也可以自己直接提供，例如儲存網路。

**物品資料與說明文字。** `Prism.items().editData(stack, data -> ...)` 寫入該物品堆疊自己的持久資料並重新產生說明文字。
`prism:lore` 是固定說明，`ItemLoreBehavior` 可依堆疊內容加上動態行。兩個版本都看得到。`examplemod:ruby` 用這個方式記錄使用次數。

**配方。** 配方放在 `prism/recipes/**.yml`。`type: prism:shaped`、`prism:shapeless`、`prism:smelting`、`prism:blasting`、
`prism:smoking` 與 `prism:campfire_cooking` 會成為真正的工作台與熔爐配方，玩家在兩個版本的生存模式都能合成你的物品。自訂物品
不會被當成它的載體材質使用。機器可以用 `context.registerRecipeType("polishing", Polishing.class, Polishing::decode)` 註冊自己的
配方類型，再以 `Prism.recipes().all(type)` 讀取。任何模組都能加入這個類型的配方，`Ingredient`、`ItemResult` 與
`Prism.items().matches(...)` 會同樣處理自訂與原版物品。範例見 `examples/example-mod/src/main/resources/prism/recipes/`。

**資料。** `Prism.data().store("examplemod")` 提供各命名空間獨立的持久化字串鍵值儲存（SQLite 檔
`plugins/Prism/data.db`）。呼叫會讀寫磁碟，請批次寫入並避開高頻路徑。

**客戶端。** `Prism.clients().profile(player)` 回報版本（Java/基岩）與客戶端能力。

**Folia。** 行為在擁有該玩家或位置的執行緒上執行；延後的工作請用 `Prism.scheduler()`，不要用 Bukkit 排程器。
只在擁有該方塊的執行緒（`runAt`）上碰方塊；跨區域的工作自己保持執行緒安全，並切到每個方塊的執行緒再操作。`runGlobalTimer` 是全伺服器的時鐘（例如網路刻），
它不屬於任何區域，所以同樣要用 `runAt` 切換。
