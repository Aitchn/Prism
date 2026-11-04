# 核心概念

**分層。** 插件（`Prism.jar`）提供 API 與執行環境；*Mod* 是依賴它的插件。主類別實作 `PrismMod`、呼叫
`Prism.mods().register`，或 jar 內有 `prism/` 資料夾，就會被視為 Mod。

**鍵。** 內容以 `PrismKey`（`namespace:value`）識別；`prism` 與 `minecraft` 為保留命名空間。

**生命週期。** `onInitialize` 註冊元件與行為類型；之後 Prism 載入並驗證所有 YAML（一次收集所有問題），再原子式發布。
只要有任何問題，該 Mod 就不發布任何內容。發布後才呼叫 `onContentLoaded`。

**物品。** 原版基底物品＋資料元件＋行為。身分存在持久資料，外觀由指向產生的物品定義之 `item_model` 決定。

**方塊。** 原版載體方塊（如 `barrier`）加上負責繪製模型的持久顯示實體，重啟不需要資料庫。

**模型。** 方塊可用 Blockbench 動畫模型：`prism:model_ref: <ns>:<name>` 加上 jar 內的 `prism/models/<name>.bbmodel`，
由 BetterModel 呈現（Prism 唯一會寫檔的地方，因為 BetterModel 只讀自己的資料夾）。

**選單。** `Prism.menus().open(player, MenuSpec, handler)` 顯示按鈕清單。

**資料。** `Prism.data().store("examplemod")` 提供各命名空間獨立的持久化字串鍵值儲存（SQLite 檔
`plugins/Prism/data.db`）。呼叫會讀寫磁碟，請批次寫入並避開高頻路徑。

**客戶端。** `Prism.clients().profile(player)` 回報版本（Java/基岩）與客戶端能力。

**Folia。** 行為在擁有該玩家或位置的執行緒上執行；延後的工作請用 Prism 的排程抽象，不要用 Bukkit 排程器。
