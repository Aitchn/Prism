# Prism 架構與路線圖（規劃草案 v0.2）

> 狀態：v0.2，已納入 Aitchn 於 2026-11-04 對十個開放問題的決定（見 §9）。本文是新版 Prism 的起點，不是最終合約。
> 日期：2026-11-04

## 1. 定位：Plugin → Prism → Mod

Prism 是一個**伺服器端的內容平台函式庫**，角色類似 Fabric 之於客戶端模組：

| 層 | 是什麼 | 誰負責 |
|---|---|---|
| **Plugin** | 安裝在 Paper / Folia / Purpur 上的 `Prism.jar`，負責平台橋接、封包、材質包、生命週期 | Prism 專案 |
| **Prism** | 公開 API（`prism-api`）與執行期（`prism-core`），提供註冊表、內容載入、客戶端呈現、持久化 | Prism 專案 |
| **Mod** | 創作者用 `prism-api` 寫的內容（物品、方塊、生物、機器、GUI …），以 jar + 內容包形式發布 | 社群創作者 |

玩家端只需要安裝（或自動下載）一個**材質包**。Java 客戶端走伺服器資源包；Bedrock 客戶端走 Geyser 的資源包協商。Prism 核心**不內建任何遊戲內容**；舊版把釣魚、工業、鍛造、指南等三千多個 YAML 全部塞進核心，是這次要拆掉的第一件事。

### 1.1 非目標（v1 不做）

- 不自製 class loader、不發明新的 Mod 檔案格式。Mod 就是一個依賴 Prism 的 Bukkit 插件（見 §5.3）。
- 不自己寫模型渲染與封包引擎：封包交給 PacketEvents，Blockbench 模型交給 BetterModel（見 §5.6）。
- 不在 Bedrock 端做自訂方塊外觀（Geyser 自訂方塊不穩定，列為 v2 實驗功能）。
- 不支援 Spigot / 非 Paper 系伺服器。

## 2. 從 Prism-old 學到的事

Sonnet 5.5 對 Prism-old 做了唯讀稽核，重點如下（細節見 Prism-old 的 `docs/`）：

**要避免的**

1. **寫死 Folia。** 77 個檔案直接呼叫 `RegionScheduler` / `GlobalRegionScheduler` / `EntityScheduler`，165 處 `isOwnedByCurrentRegion`，沒有任何排程抽象層；編譯依賴是 `folia-api`，在純 Paper 上不保證能跑。
2. **核心與內容混在一起。** 主 jar 內含 machine、industrial、forging、fishing、guide 等引擎與 3,493 個內容 YAML，`RegistryLoader` 一個檔案 5,107 行，`PrismPlugin` 1,086 行。
3. **API 靠 default method 長大。** `PrismApi` 用 `default … throw UnsupportedOperationException` 加功能，版本號無法代表能力，使用者得逐一 feature-detect。
4. **文件版本後綴漂移。** `platform-api-2.0.md` 實際描述 3.25；70 份 reference 文件各自帶版號，`docs/README.md` 甚至要讀者「不要從檔名推斷版本」。
5. **核心 listener 以 instanceof / switch 分支**處理原生行為，而不是查註冊表。
6. **驗證程式碼進了正式 jar**（`ServerBehaviorVerifier` 1,999 行）。
7. **自訂物品硬依賴 PacketEvents**，沒有 PacketEvents 就沒有物品。

**值得保留的概念（概念，不是程式碼）**

1. 獨立編譯的 API jar + 版本一致性檢查。
2. 不可變的註冊表快照、交易式發布、以擁有者（owner）為單位的註冊與反註冊、載入期驗證器。
3. 嚴格驗證的 `PrismKey`（`namespace:value`）。
4. 「YAML 宣告 → Java 處理器」的 capability-interface 行為模型，內建與外掛走同一套註冊表。
5. **身份與呈現分離**：伺服器上的 ItemStack 只帶身份，每個觀看者看到的外觀在出站時決定；Java 與 Bedrock 各有明確的呈現分支，沒有隱式 fallback。
6. 客戶端能力偵測（`ClientCapability`）以利優雅降級。
7. 內容包的 requires / capabilities / 條件式內容概念與兩階段啟動（但不再落地到伺服器資料夾）。
8. 以真實內容 fixture 做不需伺服器的測試，加上只依賴 API jar 的外部驗證外掛。
9. 以內容雜湊命名的材質包建置目錄與不可變 URL。

## 3. 設計原則

1. **API 先行。** 任何功能先定義在 `prism-api`，核心與平台模組只是實作。API 不得暴露可變的實作型別；盡量避免暴露 Folia 專屬型別。
2. **平台中立的核心。** `prism-core` 不得 import `io.papermc.paper.threadedregions.*`；所有排程與執行緒歸屬透過 SPI。
3. **一個 jar，執行期偵測平台與版本。** 單一 `Prism.jar` 在啟動時偵測 Folia / Purpur / Paper 與 Minecraft 版本，載入對應的平台實作。使用者不需要選檔案。
   - 版本**不寫死**：核心以 `PlatformInfo.minecraftVersion()` 與 `Feature` 旗標（例如 `ITEM_MODEL_COMPONENT`、`EQUIPMENT_COMPONENT`）判斷能力；某版本沒有的功能回報「不支援」而不是崩潰。首個開發目標是 Paper 26.3。
4. **核心零內容。** 範例內容放在 `examples/`，不隨核心發布。
5. **能力以版本號承諾。** 公開 API 採語意化版本；新功能進 minor 版，不用 default method 偷渡。以 japicmp 在 CI 擋相容性破壞。
6. **Bedrock 是一等公民，但承認差異。** 每個呈現相關的 API 都要回答「Bedrock 看到什麼」；允許明確降級，禁止靜默失敗。
7. **可以在沒有伺服器的情況下測試。** 核心邏輯用純 JUnit + fixture；平台層用 MockBukkit 或整合測試。
8. **文件不帶版本後綴。** 文件跟著程式碼走，版本差異寫在 changelog 與 ADR。

## 4. 模組佈局（Gradle 多模組）

```
Prism/
├── prism-api/              公開 API（創作者唯一需要依賴的東西）
├── prism-core/             平台中立的執行期：註冊表、內容載入、驗證、快照、事件
├── prism-platform/
│   ├── spi/                內部 SPI：PlatformScheduler、RegionAccess、PlatformInfo …
│   ├── paper/              Paper 實作（Purpur 直接沿用，另加可選的 Purpur 偵測）
│   └── folia/              Folia 實作（region / entity / global / async 排程）
├── prism-client/           客戶端呈現：物品外觀改寫、GUI、觀看者狀態（硬依賴 PacketEvents）
├── prism-model/            Blockbench 模型與顯示實體橋接（硬依賴 BetterModel API）
├── prism-bedrock/          Geyser / Floodgate 整合、Bedrock 表單（Cumulus）、Bedrock 物品註冊
├── prism-pack/             材質包編譯器：Java zip、Bedrock mcpack、對應檔、雜湊、HTTP 伺服
├── prism-plugin/           啟動用 Bukkit 插件 jar：plugin.yml、平台偵測、組裝以上模組
├── prism-testkit/          給創作者用的測試工具：fixture、假客戶端、內容包驗證
├── examples/
│   └── example-mod/        最小可用 Mod（一個物品、一個方塊、一個 GUI）
├── docs/                   文件站原始碼 + ADR
└── buildSrc / gradle/      共用建置邏輯、版本目錄（libs.versions.toml）
```

**依賴方向**（只能往下）：

```
prism-plugin → prism-platform/{paper,folia} → prism-platform/spi
prism-plugin → prism-client, prism-model, prism-bedrock, prism-pack → prism-core → prism-api
```

**外部硬依賴（plugin.yml `depend`）**：`packetevents`、`BetterModel`。軟依賴：`Geyser-Spigot`、`floodgate`、`PlaceholderAPI`。
`prism-api` 不得暴露 PacketEvents 或 BetterModel 的型別，創作者只面對 Prism 的抽象；這樣未來換後端時 Mod 不用改。

`prism-api` 可以依賴 `paper-api`（ItemStack、Player 等型別不值得重新發明），但**不得**依賴 `folia-api`。`prism-core` 同樣只依賴 `paper-api` 與 `prism-api`。

**建置與發布**

- Gradle Kotlin DSL、版本目錄、**Java 25**（BetterModel 要求 Java 25+，Paper 26.x 與 Prism-old 也都在 25；這是 Q8 的決定）。
- `prism-api` 與 `prism-testkit` 發布到 Maven（GitHub Packages 起步，穩定後上 Maven Central）。
- `prism-plugin` 以 shadow 打包成單一 jar，發布到 GitHub Releases 與 Hangar / Modrinth。
- CI：build、test、japicmp API 相容性檢查、Paper 與 Folia 的啟動煙霧測試。

## 5. 關鍵抽象

### 5.1 身份：`PrismKey` 與 `Registry`

```java
record PrismKey(String namespace, String value)   // [a-z0-9._-]+ : [a-z0-9._-/]+
interface Registry<T>     { Optional<T> get(PrismKey); Set<PrismKey> keys(); RegistrySnapshot<T> snapshot(); }
interface RegistrySnapshot<T> extends Registry<T> {}  // 不可變，供多次查詢保持一致
```

每個 Mod 一個 namespace，由 `prism-mod.json` 宣告；核心保留 `prism` 與 `minecraft`。

### 5.2 Mod 生命週期（Fabric 風格進入點）

```java
public interface PrismMod {
    void onInitialize(ModContext ctx);      // 註冊型別、行為、元件
    default void onContentLoaded(ContentView view) {}   // 內容包已驗證並凍結
    default void onDisable() {}
}
```

- Mod 的身份來自 Bukkit 插件本身（名稱、版本、depend）；`prism-mod.json` 為選填覆寫（見 §5.3）。
- 階段：`DISCOVER → INITIALIZE(註冊型別) → LOAD(讀 YAML) → VALIDATE → FREEZE(發布快照) → ACTIVATE(客戶端註冊、材質包編譯) → RUNNING`。
- 一個 Mod 驗證失敗，整個 Mod 不啟用（沿用舊版「不部分綁定」原則），但不影響其他 Mod。
- 所有註冊都綁定 owner（Mod），卸載時整組移除。

### 5.3 Mod 的發布形式（已決定：Mod = 依賴 Prism 的 Bukkit 插件）

創作者**還是在寫 Plugin**，只是依賴 Prism 取得「模組等級」的能力。Prism 不另創 Mod 格式、不自製 loader。

- 創作者寫一個 Paper 插件，`plugin.yml` 中 `depend: [Prism]`。**內容包放在 jar 的 resources 內**（預設路徑 `prism/`），Prism 直接從 classpath 讀取，**不會**把內容解壓或複製到伺服器資料夾（舊版 `prism_register/<ns>/` 的外部註冊目錄被廢除，實測幾乎沒人用到卻每次都要清理）。
- **零設定預設值**：namespace 預設為插件名稱小寫，內容路徑預設為 `prism/`。只有要覆寫時才在 jar 內放一個選填的 `prism-mod.json`（namespace、內容路徑、需要的 Prism API 版本範圍與能力）。
- 進入點：主類別實作 `PrismMod`，或在 `onEnable` 呼叫 `Prism.mods().register(this, initializer)`。Prism 監聽 `PluginEnableEvent` 並在 jar 內找到 `prism/` 或 `prism-mod.json` 時接管該插件的內容生命週期（§5.2）。
- 伺服器管理員若要覆寫某個 Mod 的內容（例如改配方），走 Mod 自己的 config，不走 Prism 的外部註冊目錄。這類「覆寫層」若社群有需求再以選用功能加入。
- 好處：class loading、`folia-supported` 旗標、指令與權限都由 Paper 處理；創作者原本會的 Bukkit 知識全部沿用；一個插件可以同時用 Prism 與其他插件 API。
- 代價：Prism 無法隔離壞掉的 Mod 的 class loading，只能隔離其內容（驗證失敗整組不啟用）。
- `plugins/Prism/mods/` 專屬 loader 不在路線圖上，除非社群明確需要。

### 5.4 平台抽象（含 Folia 區域排程）

```java
interface PlatformScheduler {
    void runGlobal(Runnable task);                         // Folia: GlobalRegionScheduler；Paper: 主執行緒
    void runAt(Location loc, Runnable task);               // Folia: RegionScheduler；Paper: 主執行緒
    void runOn(Entity entity, Runnable task, Runnable retired);  // Folia: EntityScheduler
    void runAsync(Runnable task);
    ScheduledTask runAtLater(Location loc, Runnable task, long delayTicks);
    ScheduledTask runAtTimer(Location loc, Runnable task, long delay, long period);
    boolean isOwnedByCurrentThread(Location loc);
    boolean isOwnedByCurrentThread(Entity entity);
}
interface PlatformInfo { PlatformKind kind(); boolean isRegionized(); String brand(); }
```

規則：

- 所有觸碰世界、方塊、實體的核心程式碼**只能**透過 `PlatformScheduler`，CI 用 ArchUnit 禁止 `prism-core` import Folia 套件。
- 每個世界狀態寫入前以 `isOwnedByCurrentThread` 斷言；在 Paper 上恆為主執行緒。
- 跨區域操作（例如多方塊結構跨 region）用「請求 + 回呼」模型，不保證原子性，文件明說（舊版已證明這是正確做法）。
- Purpur：以 Paper 實作為基礎，偵測到 Purpur 時開啟可選功能（例如 Purpur 專屬 API），不另開模組。

### 5.5 內容模型：元件 + 行為

- **Component**：不可變資料，YAML 宣告（`prism:food`、`prism:tool`、`prism:placeable` …）。Mod 可註冊新元件型別與驗證器。
- **Behavior**：遊戲回呼介面（`ItemUseBehavior`、`BlockBreakBehavior`、`BlockRandomTickBehavior` …）。YAML 以 `type` 綁定到 Java 處理器。
- **Definition**：`ItemDefinition`、`BlockDefinition`、`EntityDefinition`（v2）各由「身份 + 元件集合 + 呈現（Java / Bedrock 分支）」組成。
- 事件派發由核心統一做（含執行緒歸屬檢查與 owner 停用後的清理），listener 不得對特定 ID 分支。

### 5.6 客戶端呈現

- **物品**：伺服器上是「基底原生物品 + PDC 身份」；出站封包時改寫為 `item_model` + `custom_model_data`（1.21.4+ 的做法）。封包改寫放在 `prism-client`，以 **PacketEvents** 為硬依賴（Q5 決定），但 PacketEvents 型別不出 `prism-client`。
- **模型**：Blockbench 模型（`.bbmodel`）的載入、動畫、顯示實體管理交給 **BetterModel**（Q5 決定）。`prism-model` 把 Prism 的 `ModelRef`（PrismKey）對應到 BetterModel 的模型，並把 BetterModel 產生的資源併入 Prism 的材質包流程（§6.2）。創作者在 YAML 寫 `model: mymod:turret`，不直接碰 BetterModel API。
- **方塊**：以 `BlockBackend` SPI 抽象，**先做 `DisplayEntityBackend`**（Q7 決定）：
  - `DisplayEntityBackend`（v1）：原生載體方塊（例如 barrier / 指定材質）+ 每觀看者的顯示實體；簡單立方體由 Prism 自己送 item display，複雜模型走 BetterModel。外觀自由，成本較高，需要 chunk 載入 / 卸載時的顯示實體生命週期管理與裂痕覆蓋。
  - `NativeStateBackend`（M5）：借用原生方塊狀態（音符盒、絆線、蘑菇…），效能好，數量有限，Bedrock 端可透過 Geyser custom blocks 對應。
  - Mod 以 YAML 選擇，核心可依平台能力決定 fallback。
- **GUI**：`Menu` 抽象，Java 用容器介面，Bedrock 自動轉 Geyser 表單（Cumulus）。
- **客戶端能力**：`ClientProfile { platform, capabilities, packState }`，每個呈現 API 都接受它。

### 5.7 持久化

- 方塊 / 機器狀態：chunk PDC + 自訂 codec（沿用舊版概念，但限制與版本升級規則寫進 API）。
- 玩家資料：`DataStore` SPI，預設 JSON 原子寫檔，v2 可換 SQLite / 外部 DB。
- 所有持久化格式帶 `schema-version`，核心提供 migration 註冊。

## 6. Java / Bedrock 材質包策略

### 6.1 目標版本

- Java：首個開發目標是 **Paper 26.3**（Q4 決定），但版本不寫死：材質包格式號、資料元件可用性都從 `PlatformInfo` 與 `Feature` 旗標查，核心對每個版本輸出「支援 / 降級 / 不支援」矩陣。最低能運作的版本是 1.21.4（`item_model` 出現的版本，也是 BetterModel 的下限）。
- Bedrock：以 Geyser 當前穩定版的 **custom items v2 API** 為準；`custom_model_data` 的 flag / string / float 可作為 Bedrock 端的 predicate。

### 6.2 編譯流程（`prism-pack`）

1. 收集所有已啟用 Mod 的 `assets/`（紋理、模型、語言檔、音效）。
2. 產生 Java 包：`assets/<ns>/items/<key>.json`（item definition 圖）、模型、紋理；**合併 BetterModel 產生的模型資源**；輸出 `java.zip` 與 SHA-1。
3. 產生 Bedrock 包：`item_texture.json`、`textures/items/`、簡單立方體模型轉 geometry + attachable；輸出 `bedrock.mcpack` 與 `mappings.json`。
4. 輸出目錄 `plugins/Prism/packs/<content-hash>/`，URL 不可變；內容不變就不重編。
5. 不支援的 Java 模型節點（`.bbmodel`、自由網格、動畫）在 Bedrock 端降級為靜態圖示，**編譯期警告**，不是執行期才失敗。

### 6.3 傳遞

- Java：Paper `ResourcePackRequest`，預設內建 HTTP 伺服（可關閉改用外部 URL / CDN）。支援與其他插件的包**並列**（1.20.3+ 多包），v1 不做合併。
- Bedrock：在同一伺服器跑 Geyser 插件時，透過 `GeyserDefineCustomItemsEvent` 註冊定義並以 Geyser 的包協商送出。**Geyser 跑在 Velocity 等代理上時**（Q6 決定 v1 就支援），Prism 提供 `prism-geyser-extension`（Geyser 擴充）：從後端 Prism 的 HTTP 端點或共享目錄取得同一份 `mappings.json` 與 mcpack，以內容雜湊比對後註冊。列入 M3。
- **BetterModel 與 Bedrock**：BetterModel 不支援 Bedrock。顯示實體本身 Geyser 會轉譯，但其上的自訂物品模型需要 Geyser 對應。`prism-model` 要為每個 BetterModel 模型產生 Bedrock 端的對應（簡單幾何轉 attachable，複雜模型降級為圖示或隱藏），這是 M3 最大的技術風險，需提早做原型。
- Floodgate：用於識別 Bedrock 玩家與取得 Bedrock 專屬資訊；以反射或 soft-depend 存取，缺席時以 Geyser API 判定。

### 6.4 Bedrock 降級規則（API 層保證）

| 功能 | Java | Bedrock |
|---|---|---|
| 自訂物品外觀 | item_model + CMD 動態狀態 | 固定圖示 / 簡單 3D 模型，狀態組合需明列 |
| 自訂方塊外觀 | DisplayEntity（v1）/ NativeState（M5） | v1 顯示原生載體方塊 + 可行時的簡化顯示實體；v2 實驗 Geyser custom blocks |
| GUI | 容器介面 | Geyser 表單 |
| 裝備外觀 | equipment 資料元件 + 包內模型 | attachable（簡單）或原生外觀 |
| 文字 / 字型 | 自訂字型 | 僅 Unicode / 原生字型 |

每個降級都在 `ClientProfile.capabilities()` 可查，Mod 可自行決定替代行為。

## 7. 公開 API 與社群設計

- **版本**：`prism-api` 用 SemVer；`major.minor` 寫進 `prism-mod.json` 的 `depends`。CI 用 japicmp 擋破壞性變更，破壞只能在 major。
- **穩定性標記**：`@ApiStatus.Experimental` / `@ApiStatus.Internal`（JetBrains annotations），實驗 API 不受相容性承諾。
- **文件**：`docs/` 用 MkDocs Material 建站並啟用 i18n，**使用者文件中英雙語**（Q1 決定）：`docs/en/` 為正本，`docs/zh-TW/` 為翻譯；章節：Getting started（10 分鐘做出第一個物品）、Concepts、Guides（物品 / 方塊 / GUI / Bedrock）、API reference（Javadoc，英文）、ADR（英文）。
- **社群檔案**：README（含架構圖與示範 GIF）、CONTRIBUTING、CODE_OF_CONDUCT、SECURITY、issue / PR 範本、`good first issue` 標籤、Discussions 開啟。
- **ADR**：`docs/adr/NNNN-title.md`，每個重大決策含備選方案與取捨（延續舊版好習慣，但不帶版本後綴）。
- **語言**：程式碼、註解、API 文件、ADR 用英文（Q1 決定）；本規劃文件在 M0 時改寫為英文 ADR，中文版保留為翻譯。
- **授權**：**MIT**（Q2 決定），與 BetterModel 相同。
- **Prism-old**：保留為唯讀參考，不改動；等新版 Prism 完善後再移除（Q9 決定）。

## 8. 里程碑

| 里程碑 | 內容 | 完成定義 |
|---|---|---|
| **M0 骨架**（1–2 週） | 多模組 Gradle（Java 25）、CI、`prism-api` 空殼、`PlatformScheduler` + Paper / Folia 實作、平台與版本偵測、`Feature` 旗標、ArchUnit 規則、PacketEvents / BetterModel 依賴接線、README / CONTRIBUTING / MIT LICENSE / ADR-0001、Docker 煙霧測試（Paper 26.3 與 Folia） | 單一 jar 在 Paper 與 Folia 上啟動並印出平台、版本與功能矩陣 |
| **M1 核心內容模型**（3–4 週） | `PrismKey`、Registry / Snapshot、Mod 進入點、`prism-mod.json`、內容包載入與驗證、元件 / 行為註冊、事件派發 | example-mod 註冊一個有行為的物品，單元測試不需伺服器 |
| **M2 Java 呈現**（3–4 週） | `prism-pack` Java 包編譯（含 BetterModel 資源合併）、HTTP 伺服、`prism-client` 封包改寫、自訂物品外觀、`prism-model` 橋接、`DisplayEntityBackend` 方塊 | Java 玩家看到自訂物品與（含 Blockbench 模型的）方塊，重啟後狀態保留 |
| **M3 Bedrock**（4–5 週） | Bedrock 包編譯、Geyser v2 物品註冊、Floodgate 偵測、`ClientProfile`、Bedrock 表單 GUI、`prism-geyser-extension`（代理拓撲）、BetterModel 模型的 Bedrock 對應原型 | 同一個 example-mod 在 Bedrock 客戶端可見且可用（直連與 Velocity + Geyser 兩種拓撲）；降級路徑有測試 |
| **M4 公開預覽**（2 週） | 文件站、testkit、Maven 發布、Hangar / Modrinth 上架、0.1.0 | 外部創作者可照 Getting started 在 30 分鐘內做出 Mod |
| **M5 擴充**（持續） | `NativeStateBackend`、裝備外觀、自訂生物（BetterModel 動畫）、DataStore SQLite、Gradle 外掛、第二個 Minecraft 版本線的支援矩陣 | 依社群回饋排序 |
| **M6 1.0** | API 凍結、相容性承諾、Geyser custom blocks（實驗） | 至少兩個非作者的社群 Mod 在生產伺服器運行 |

時間估計以單人兼職為基準；Sonnet 5.5 負責程式碼撰寫可以壓縮，但 M2 / M3 需要真實客戶端驗證，這段無法自動化。

## 9. 決策記錄（Aitchn，2026-11-04）

| # | 問題 | 決定 | 影響 |
|---|---|---|---|
| 1 | 文件語言 | 程式碼與 API 文件英文；使用者文件中英雙語 | §7 文件站 i18n |
| 2 | 授權 | MIT | §7 |
| 3 | Mod 載入方式 | Mod 仍是 Bukkit 插件，依賴 Prism；不另創格式；內容只放 jar 內，不再落地到伺服器的外部註冊目錄；namespace 與內容路徑零設定 | §5.2、§5.3 |
| 4 | Minecraft 版本 | 先做 26.3，不寫死版本；舊版本以功能矩陣降級 | §3 原則 3、§6.1 |
| 5 | 封包 / 模型層 | 硬依賴 PacketEvents（封包）與 BetterModel（模型） | §4、§5.6、§6.2 |
| 6 | Geyser 代理拓撲 | v1 支援 | §6.3、M3 |
| 7 | 方塊後端 | 先做 DisplayEntityBackend | §5.6、M2 |
| 8 | Java 版本 | 由 Prism 決定：**Java 25**（BetterModel 的下限、Paper 26.x 與 Prism-old 皆用 25） | §4 |
| 9 | Prism-old | 唯讀參考，不改動，待新版完善後移除 | §7 |
| 10 | 整合測試 | 可用 Docker 起真實 Paper / Folia 做煙霧測試 | M0 |

### 9.1 尚待確認

- **BetterModel 版本鎖定策略**：BetterModel 的 API 以 semver 發布，Prism 應宣告相容範圍（例如 `>=1.x <2`）並在啟動時檢查，避免伺服器更新 BetterModel 後 Prism 無聲壞掉。
- **PacketEvents 與 BetterModel 的 Folia 執行緒模型**：兩者都標示支援 Folia，但 M0 要以煙霧測試確認在 region 執行緒上呼叫其 API 的安全範圍。

## 10. 下一步

1. 由 Sonnet 5.5 實作 M0 骨架（多模組 Gradle、平台 SPI、Docker 煙霧測試、社群檔案）。
2. 同一個 PR 把本文件改寫為英文 ADR-0001（模組佈局）與 ADR-0002（平台抽象），中文版留作翻譯。
3. M0 完成後先做 §6.3 提到的「BetterModel 模型 → Bedrock 對應」原型，確認 M3 風險。
