# Prism 架構與路線圖（規劃草案 v0.1）

> 狀態：草案，待 Aitchn 審閱。本文是新版 Prism 的起點，不是最終合約。
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

- 不自製 class loader。Mod 在 v1 就是一個依賴 Prism 的 Bukkit 插件（見 §4.3）。
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
7. 內容包 `pack.yml`（requires / capabilities / 條件式內容）與兩階段啟動。
8. 以真實內容 fixture 做不需伺服器的測試，加上只依賴 API jar 的外部驗證外掛。
9. 以內容雜湊命名的材質包建置目錄與不可變 URL。

## 3. 設計原則

1. **API 先行。** 任何功能先定義在 `prism-api`，核心與平台模組只是實作。API 不得暴露可變的實作型別；盡量避免暴露 Folia 專屬型別。
2. **平台中立的核心。** `prism-core` 不得 import `io.papermc.paper.threadedregions.*`；所有排程與執行緒歸屬透過 SPI。
3. **一個 jar，執行期偵測平台。** 單一 `Prism.jar` 在啟動時偵測 Folia / Purpur / Paper，載入對應的平台實作。使用者不需要選檔案。
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
├── prism-client/           封包層抽象 + Java 呈現（物品外觀、顯示實體、GUI）
│   └── packetevents/       PacketEvents 後端（未來可換 Paper 原生 API 或其他後端）
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
prism-plugin → prism-client, prism-bedrock, prism-pack → prism-core → prism-api
```

`prism-api` 可以依賴 `paper-api`（ItemStack、Player 等型別不值得重新發明），但**不得**依賴 `folia-api`。`prism-core` 同樣只依賴 `paper-api` 與 `prism-api`。

**建置與發布**

- Gradle Kotlin DSL、版本目錄、Java 21（Paper 1.21+ 的最低需求；若鎖定 26.x 版 Paper 再評估升到 25）。
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

- Mod jar 內含 `prism-mod.json`：`id`、`version`、`entrypoints`、`depends`（含 `prism-api` 版本範圍）、`content`（內容包路徑）。
- 階段：`DISCOVER → INITIALIZE(註冊型別) → LOAD(讀 YAML) → VALIDATE → FREEZE(發布快照) → ACTIVATE(客戶端註冊、材質包編譯) → RUNNING`。
- 一個 Mod 驗證失敗，整個 Mod 不啟用（沿用舊版「不部分綁定」原則），但不影響其他 Mod。
- 所有註冊都綁定 owner（Mod），卸載時整組移除。

### 5.3 Mod 的發布形式（v1 決策）

v1 採用 **「Mod = 依賴 Prism 的 Bukkit 插件」**：

- 創作者寫一個 Paper 插件，`plugin.yml` 中 `depend: [Prism]`，jar 內附 `prism-mod.json` 與內容包。
- 好處：class loading、`folia-supported` 旗標、指令與權限都由 Paper 處理；Prism 不必自己寫 loader。
- 代價：創作者要懂一點 Bukkit。用 `prism-testkit` 與 Gradle 範本（`examples/example-mod`）降低門檻。
- v2 再評估 `plugins/Prism/mods/` 的專屬 loader（見 §9 開放問題 Q3）。

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

- **物品**：伺服器上是「基底原生物品 + PDC 身份」；出站封包時改寫為 `item_model` + `custom_model_data`（1.21.4+ 的做法）。封包改寫放在 `prism-client`，後端可替換（v1：PacketEvents；評估 Paper 原生 `ItemStack` 資料元件 + `ResourcePack` API 是否足以免去封包層，見 Q5）。
- **方塊**：以 `BlockBackend` SPI 抽象，v1 提供兩種：
  - `NativeStateBackend`：借用原生方塊狀態（音符盒、絆線、蘑菇…），效能好，數量有限。
  - `DisplayEntityBackend`：原生載體方塊 + 每觀看者的顯示實體（舊版做法），外觀自由，成本高。
  - Mod 以 YAML 選擇，核心可依平台能力決定 fallback。
- **GUI**：`Menu` 抽象，Java 用容器介面，Bedrock 自動轉 Geyser 表單（Cumulus）。
- **客戶端能力**：`ClientProfile { platform, capabilities, packState }`，每個呈現 API 都接受它。

### 5.7 持久化

- 方塊 / 機器狀態：chunk PDC + 自訂 codec（沿用舊版概念，但限制與版本升級規則寫進 API）。
- 玩家資料：`DataStore` SPI，預設 JSON 原子寫檔，v2 可換 SQLite / 外部 DB。
- 所有持久化格式帶 `schema-version`，核心提供 migration 註冊。

## 6. Java / Bedrock 材質包策略

### 6.1 目標版本

- Java：以 Minecraft 1.21.4 引入的 `item_model` 資料元件為基準（Prism-old 已走這條路，Geyser v2 對應格式也以此為鍵）。只支援 Paper 最新兩個版本線。
- Bedrock：以 Geyser 當前穩定版的 **custom items v2 API** 為準；`custom_model_data` 的 flag / string / float 可作為 Bedrock 端的 predicate。

### 6.2 編譯流程（`prism-pack`）

1. 收集所有已啟用 Mod 的 `assets/`（紋理、模型、語言檔、音效）。
2. 產生 Java 包：`assets/<ns>/items/<key>.json`（item definition 圖）、模型、紋理；輸出 `java.zip` 與 SHA-1。
3. 產生 Bedrock 包：`item_texture.json`、`textures/items/`、簡單立方體模型轉 geometry + attachable；輸出 `bedrock.mcpack` 與 `mappings.json`。
4. 輸出目錄 `plugins/Prism/packs/<content-hash>/`，URL 不可變；內容不變就不重編。
5. 不支援的 Java 模型節點（`.bbmodel`、自由網格、動畫）在 Bedrock 端降級為靜態圖示，**編譯期警告**，不是執行期才失敗。

### 6.3 傳遞

- Java：Paper `ResourcePackRequest`，預設內建 HTTP 伺服（可關閉改用外部 URL / CDN）。支援與其他插件的包**並列**（1.20.3+ 多包），v1 不做合併。
- Bedrock：在同一伺服器跑 Geyser 插件時，透過 `GeyserDefineCustomItemsEvent` 註冊定義並以 Geyser 的包協商送出。**Geyser 跑在 Velocity 等代理上時**，Prism 提供 `prism-geyser-extension`（Geyser 擴充）讀取同一份 `mappings.json` 與 mcpack（v1.x 里程碑，見 Q6）。
- Floodgate：用於識別 Bedrock 玩家與取得 Bedrock 專屬資訊；以反射或 soft-depend 存取，缺席時以 Geyser API 判定。

### 6.4 Bedrock 降級規則（API 層保證）

| 功能 | Java | Bedrock |
|---|---|---|
| 自訂物品外觀 | item_model + CMD 動態狀態 | 固定圖示 / 簡單 3D 模型，狀態組合需明列 |
| 自訂方塊外觀 | NativeState 或 DisplayEntity | v1 顯示原生載體方塊；v2 實驗 Geyser custom blocks |
| GUI | 容器介面 | Geyser 表單 |
| 裝備外觀 | equipment 資料元件 + 包內模型 | attachable（簡單）或原生外觀 |
| 文字 / 字型 | 自訂字型 | 僅 Unicode / 原生字型 |

每個降級都在 `ClientProfile.capabilities()` 可查，Mod 可自行決定替代行為。

## 7. 公開 API 與社群設計

- **版本**：`prism-api` 用 SemVer；`major.minor` 寫進 `prism-mod.json` 的 `depends`。CI 用 japicmp 擋破壞性變更，破壞只能在 major。
- **穩定性標記**：`@ApiStatus.Experimental` / `@ApiStatus.Internal`（JetBrains annotations），實驗 API 不受相容性承諾。
- **文件**：`docs/` 用 MkDocs Material（或 Docusaurus）建站，章節：Getting started（10 分鐘做出第一個物品）、Concepts、Guides（物品 / 方塊 / GUI / Bedrock）、API reference（Javadoc）、ADR。
- **社群檔案**：README（含架構圖與示範 GIF）、CONTRIBUTING、CODE_OF_CONDUCT、SECURITY、issue / PR 範本、`good first issue` 標籤、Discussions 開啟。
- **ADR**：`docs/adr/NNNN-title.md`，每個重大決策含備選方案與取捨（延續舊版好習慣，但不帶版本後綴）。
- **語言**：程式碼、註解、API 文件用英文（面向國際社群）；中文文件作為翻譯維護（見 Q1）。
- **授權**：見 Q2。

## 8. 里程碑

| 里程碑 | 內容 | 完成定義 |
|---|---|---|
| **M0 骨架**（1–2 週） | 多模組 Gradle、CI、`prism-api` 空殼、`PlatformScheduler` + Paper / Folia 實作、平台偵測、ArchUnit 規則、README / CONTRIBUTING / LICENSE / ADR-0001 | 單一 jar 在 Paper 與 Folia 上啟動並印出平台資訊 |
| **M1 核心內容模型**（3–4 週） | `PrismKey`、Registry / Snapshot、Mod 進入點、`prism-mod.json`、內容包載入與驗證、元件 / 行為註冊、事件派發 | example-mod 註冊一個有行為的物品，單元測試不需伺服器 |
| **M2 Java 呈現**（3–4 週） | `prism-pack` Java 包編譯、HTTP 伺服、`prism-client` 封包改寫、自訂物品外觀、`NativeStateBackend` 方塊 | Java 玩家看到自訂物品與方塊，重啟後狀態保留 |
| **M3 Bedrock**（3–4 週） | Bedrock 包編譯、Geyser v2 物品註冊、Floodgate 偵測、`ClientProfile`、Bedrock 表單 GUI | 同一個 example-mod 在 Bedrock 客戶端可見且可用；降級路徑有測試 |
| **M4 公開預覽**（2 週） | 文件站、testkit、Maven 發布、Hangar / Modrinth 上架、0.1.0 | 外部創作者可照 Getting started 在 30 分鐘內做出 Mod |
| **M5 擴充**（持續） | `DisplayEntityBackend`、裝備外觀、自訂生物、Geyser 代理擴充、DataStore SQLite、Gradle 外掛 | 依社群回饋排序 |
| **M6 1.0** | API 凍結、相容性承諾、Geyser custom blocks（實驗） | 至少兩個非作者的社群 Mod 在生產伺服器運行 |

時間估計以單人兼職為基準；Sonnet 5.5 負責程式碼撰寫可以壓縮，但 M2 / M3 需要真實客戶端驗證，這段無法自動化。

## 9. 開放問題（需要 Aitchn 決定）

1. **文件語言**：程式碼與 API 文件英文、使用者文件中英雙語？還是全英文、中文只在 README 放一份摘要？
2. **授權**：MIT（最寬鬆、對作品集友善）、Apache-2.0（含專利條款）、LGPL（要求修改 Prism 本身者開源，但 Mod 不受影響）？建議 **Apache-2.0** 或 MIT。
3. **Mod 載入方式**：v1 的「Mod 即 Bukkit 插件」是否可接受？或一開始就要 Prism 自己的 `mods/` 資料夾與 loader（開發成本高、但創作者門檻低）？
4. **Minecraft 最低版本**：只支援最新 Paper 版本線（開發最省力），還是回溯支援 1.21.4？
5. **封包層後端**：繼續硬依賴 PacketEvents，還是以 Paper 原生 API 為主、PacketEvents 為可選加速？後者對使用者安裝最友善，但方塊顯示實體與每觀看者外觀仍需封包層。
6. **Geyser 部署拓撲**：是否要在 v1 就支援 Geyser 跑在代理（Velocity）上的情境？影響 `prism-geyser-extension` 的優先順序。
7. **方塊後端優先順序**：v1 先做 `NativeStateBackend`（音符盒等，數量有限但穩定）還是 `DisplayEntityBackend`（舊版做法，外觀自由）？
8. **Java 版本**：鎖 Java 21（相容性廣）還是跟 Prism-old 一樣用 25？
9. **Prism-old 的處置**：封存（archive）並在新 README 連結，或保留為 `legacy` 分支？
10. **測試工具**：MockBukkit 對 Folia 支援有限，平台層整合測試是否接受用 Docker 起真實 Paper / Folia 伺服器跑煙霧測試？

## 10. 下一步

1. Aitchn 回覆 §9 的決定（至少 Q2、Q3、Q4、Q5）。
2. 依決定建立 ADR-0001（模組佈局）與 ADR-0002（平台抽象）。
3. 由 Sonnet 5.5 實作 M0 骨架。
