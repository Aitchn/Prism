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

以音符盒或絆線狀態呈現的方塊（預設）並不是把這些狀態存進世界。世界裡放的是一個惰性的原版方塊（實心方塊用棕色蘑菇方塊，
可穿過的方塊用結構空位），它沒有狀態、沒有 tick、也不會對鄰居起反應；Prism 依自己的索引改寫送出的方塊封包，
告訴每位玩家它長什麼樣子。玩家在這種方塊旁放置、破壞或點擊後，Prism 會把該方塊與鄰居的外觀重送一次，
因為 Java 版客戶端會自己預測形狀更新。若其他插件或 WorldEdit 把載體換掉，這個方塊就視為已消失。
讀取世界的插件看到的是載體，請用 `Prism.blocks().identify(block)` 或 `get(block)` 判斷方塊是什麼。詳見 ADR-0022。

Mod 能有的外觀數量不再受音符盒 799 個狀態限制：每次開機 Prism 會先給每個外觀一個借用的原版狀態，用完之後改用蓋在隱形外殼上、只存在於封包中的顯示實體繪製。
`prism:render` 與 `prism:surface` 可以調整這件事，說明見 `block-looks.md`。詳見 ADR-0023。

**音效與動畫材質。** 音效依 Java 資源包的方式放進 jar（`assets/<ns>/sounds.json` 加上 `.ogg` 檔），
用 Bukkit 的 `playSound(location, "<ns>:<event>", ...)` 播放。Prism 也會把它們放進基岩版資源包，基岩版玩家透過 Geyser 聽得到。
`prism:ambient_sound` 讓方塊每隔 `interval` 刻自己播放音效，可以限定只在列出的變體播放，方塊消失就停止。
動畫材質（`<texture>.png.mcmeta` 含 `animation`）在 Java 版會動，在基岩版則轉成 flipbook。紅寶石計數器啟動時會發出嗡嗡聲並閃爍。

**連接方塊。** `prism:connections` 讓方塊像線纜或管線一樣伸向鄰居：中央一個核心方塊，每個相連的面再伸出一支臂。
`connects_to` 可列出 `self`、方塊 key（自訂或原版），以及 `capability:prism:item_handler` 或 `capability:prism:inventory`；
`faces`、`core`、`arm` 與 `texture` 決定形狀。方塊本身或鄰居被放置、移除時，Prism 會更新臂，並用同一張貼圖自動產生所有組合。
`BlockConnectionBehavior` 可以用程式取代這些規則；答案改變時呼叫 `Prism.blocks().refreshConnections(block)`。
Java 版玩家看到的是屏障方塊上的顯示實體，所以相鄰方塊的面不會破洞。基岩版玩家看到的是自動產生的形狀，
每個變體與連接組合各佔一個音符盒狀態（六面為 64 個，四面為 16 個）。範例是 `examplemod:ruby_cable`。

**可穿過的方塊。** `prism:passable: true` 讓方塊像花或細線一樣可以直接走過去。Prism 用 64 個絆線狀態之一
（而不是音符盒狀態；玩家看到的是該狀態，世界裡放的是結構空位）呈現它，所以兩個版本都沒有碰撞、不會遮住周圍方塊，而且一碰就破。沒有自己的模型時，貼圖會畫成像花一樣的十字形。
範例是 `examplemod:ruby_flower`。

**多方塊結構。** `prism:structure` 讓方塊成為結構的控制器。寫法如下：
`layers`（由下往上，每層以 `/` 分隔由北到南的列）、`key`（單一字元對應方塊 key）、`@` 代表控制器本身、空白代表任何方塊、
`.` 代表空氣，以及是否允許旋轉（`rotate`，預設 true）。控制器或附近方塊被放置、移除時，以及區塊載入時，Prism 會比對圖樣，
並呼叫控制器的 `StructureBehavior.onFormed` 或 `onBroken`。隨時可用 `BlockService.structure(controller)` 與
`controllerOf(member)` 查詢。結構成形後要做什麼由 Mod 決定。範例 `examplemod:ruby_core` 放在 3x3 紅寶石地板中央上方時會亮起。

**光源。** `prism:light`（`level` 1 到 15，可選 `variants`）讓方塊發光，可以一直亮，或只在列出的變體亮。音符盒與屏障本身無法發光，
所以 Prism 會在旁邊的空格（先上方，再四周，最後下方）放一個看不見的原版光源方塊，方塊變暗或被移除時再拿掉。Geyser 會把這個光源方塊
轉給基岩版玩家，所以兩個版本的亮度相同。若有人在那一格蓋了東西，光源會移到另一個空的方向。範例 `examplemod:ruby_counter` 運作時會發光。

**任意大小的長方體。** `prism:cuboid` 讓方塊成為中空長方體的控制器：`walls`（外殼可用的方塊 key）、`inside`（除空氣外允許放在內部的方塊，
預設沒有）、`min` 與 `max`（外部邊長，3 到 32）。控制器要放在某一面牆上，不能在邊或角。Prism 從控制器朝向空氣的那一側找出長方體，
呼叫同樣的 `StructureBehavior`，並以 `FormedStructure` 回報：`facing` 指向內部，`min` 與 `max` 是兩個角落，Mod 可據此算出容積。
內部放進其他東西或打破牆面會拆散結構；改建成其他大小會重新成形。範例 `examplemod:ruby_tank` 在紅寶石方塊與玻璃蓋成、
邊長 3 到 7 的長方體上會亮起。

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

**選單中的量表。** 為物品加上 `prism:gauge`（`style: bar` 或 `arrow`、`color: "#rrggbb"`、`steps` 1 到 32），再呼叫
`view.setGauge(slot, key, fraction, name, lore)`（例如在 `onRefresh` 中）。Prism 會把從空到滿的各階圖示畫進兩種資源包，
Java 與基岩版玩家看到相同的分段量條或箭頭。若要換成自己的圖，提供 `textures/item/<名稱>/gauge_<n>.png` 即可取代該階。
範例是計數器介面中的循環箭頭。

**連同資料撿起方塊。** `BlockDropBehavior` 在移除行為之前，把方塊資料寫進掉落物（`Prism.items().editData(drop, ...)`）；
`BlockPlaceBehavior` 再從 `context.placedFrom()` 讀回。計數器就是這樣保留計數。

**讓點擊通過。** 互動行為呼叫 `context.pass()` 表示沒有使用這次點擊，手上的物品會像對原版方塊一樣使用或放置。手上拿著方塊點擊計數器時會放行。

**輸入文字。** `Prism.menus().prompt(player, title, initial, text -> ..., onCancel)` 會用鐵砧介面請玩家輸入一行文字，
基岩版玩家透過 Geyser 看到鐵砧的改名欄位。過程中不會移動任何物品。計數器的標籤按鈕就是範例。

**搬運物品。** `PrismCapabilities.ITEM_HANDLER` 提供 `ItemHandler`：`insert(stack, simulate)`、
`extract(filter, amount, simulate)` 與 `contents()`。它不以格子為單位，所以管線與儲存匯流排可以對任何方塊運作。
Prism 為原版容器提供這個能力（熔爐依漏斗的面向規則），也為提供 `PrismCapabilities.INVENTORY` 的自訂方塊提供。
方塊也可以自己直接提供，例如儲存網路。

**物品資料與說明文字。** `Prism.items().editData(stack, data -> ...)` 寫入該物品堆疊自己的持久資料並重新計算說明文字。
`prism:lore` 是固定說明，`ItemLoreBehavior` 可依堆疊內容加上動態行（`loreMessages`：翻譯鍵加參數）。兩個版本都看得到，
每位玩家看到自己的語言。`examplemod:ruby` 用這個方式記錄使用次數。詳見[翻譯](translations.md)。

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
