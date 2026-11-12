# 基岩版

基岩版玩家透過 Geyser 加入。Prism 以 Floodgate、再以 Geyser 偵測，並提供 `ClientProfile.supports(ClientCapability.X)`，讓
Mod 依「能力」而非「版本」調整。

| 功能 | Java | 基岩版 |
|---|---|---|
| 物品外觀 | 完整模型 | 取自物品貼圖的平面圖示 |
| 動態物品狀態 | 有 | 無 |
| 方塊（預設） | 借用狀態或顯示層＋資源包 | 每個外觀一個**真正的基岩版自訂方塊**，數量沒有上限（Geyser 在同一台伺服器；`bedrock.synthetic-blocks`，ADR-0024） |
| 使用 BetterModel 模型的方塊 | 動畫模型（顯示實體） | 以 `textures/block/<name>.png` 貼圖的靜態自訂方塊，個別送給每位基岩版玩家 |
| 連接方塊（`prism:connections`） | 屏障上的顯示實體 | 依連接組合自動產生形狀的自訂方塊 |
| 可穿過的方塊（`prism:passable`） | 絆線狀態＋資源包 | 無碰撞的自訂方塊 |
| 由顯示層繪製的方塊（超出借用狀態的部分） | 只存在封包中的顯示實體 | 自訂方塊（絕不送顯示實體）；合成方塊關閉或未註冊時**看不見** |
| 方塊外觀變體（`prism:variants`、`setVariant`） | 有 | 有 |
| 方塊光源（`prism:light`） | 旁邊放原版光源方塊 | 同一個光源方塊（經 Geyser） |
| 自訂音效（`sounds.json`、`prism:ambient_sound`） | 有 | 有（基岩版資源包的 `sound_definitions`，經 Geyser 播放） |
| 動畫方塊材質（`.png.mcmeta`） | 有 | 有（flipbook） |
| 選單量表（`prism:gauge`） | 分段圖示 | 分段圖示（每一階一個基岩版自訂物品） |
| 選單 | 箱子介面 | 箱子介面（Geyser 轉譯）；原生表單未實作 |
| 選單背景與像素元件（`prism/menus/*.yml`） | 標題中的圖片，元件會動 | 標題乾淨的箱子介面；元件以備用量表物品呈現 |

## 方塊如何送到基岩版（ADR-0024）

每個自訂方塊的每個外觀都會得到一個高於原版範圍的合成 Java 方塊狀態 id。Prism 把每個 id 當作非原版方塊狀態註冊到
Geyser，對應到一個真正的基岩版自訂方塊（幾何與貼圖來自基岩版資源包，碰撞來自方塊的物理類別，硬度來自 `prism:mining`），
並且只對 Geyser 與 Floodgate 玩家送出這個 id，而不是 Java 外觀。Java 玩家收到的內容與以前完全相同。Prism 也會為基岩版玩家
改寫區塊的 section，使超出原版調色盤寬度的 id 能被正確讀取。

`config.yml` 的 `bedrock.synthetic-blocks`：

| 值 | 意義 |
|---|---|
| `auto`（預設） | Geyser 在本伺服器上時，透過 Geyser API 註冊；沒有 Geyser 則什麼都不變。 |
| `proxy` | Geyser 在代理上。Prism 在伺服器載入時固定 id，並寫出 `plugins/Prism/geyser/prism_synthetic_blocks.json`。代理上需要 Prism 的 Geyser 擴充（目前沒有提供；檔案格式見 ADR-0024）。 |
| `off` | 只用舊路線：音符盒與絆線外觀的 Geyser JSON 對應（799 + 64 種外觀）。 |

## 設定

1. 在同一台伺服器安裝 Geyser（`Geyser-Spigot`），並確認 `plugins/Geyser-Spigot/config.yml` 的
   `gameplay.enable-custom-content: true`（舊版 Geyser 為 `enable-custom-content`）。關閉時 Prism 會在日誌警告。
2. 啟動伺服器。Prism 在 Geyser 啟動時註冊方塊，並記錄 `PRISM_BEDROCK_SYNTHETIC_OK blocks=... base=... global_bits=...`。
   它把 `custom_mappings/prism_items.json` 寫進 Geyser 資料夾（並移除那裡舊的 `prism_blocks.json` 與 `packs/prism.mcpack`，
   因為資源包現在經由 API 註冊），並記錄 `PRISM_GEYSER_INSTALLED changed=[...]`。基岩版資源包保存在
   `plugins/Prism/geyser/packs/prism.mcpack`。
3. 內容有變更時，**請重新啟動一次**：Geyser 只在啟動時讀取物品並註冊方塊。熱重載新增的外觀在此之前於基岩版顯示為其 Java 外觀。
4. 若看到 `PRISM_BEDROCK_SYNTHETIC_FAIL`，Prism 已維持舊行為並說明原因（Geyser API 無法使用、註冊錯誤，或 Geyser 從未要求自訂方塊）。
   此時會寫出舊版 Geyser 檔案，供下次 Geyser 啟動使用。

Geyser 在代理上：設定 `bedrock.synthetic-blocks: proxy`，在後端伺服器安裝 Floodgate（Prism 需要它才知道誰是基岩版玩家），並在代理上安裝
Prism 的 Geyser 擴充，由它註冊 `plugins/Prism/geyser/prism_synthetic_blocks.json` 列出的方塊與基岩版資源包。沒有這種擴充時，請保持
`auto` 或 `off`；此時照舊把 `plugins/Prism/geyser/` 的 `custom_mappings/` 與 `packs/` 複製到代理的 Geyser 資料夾（舊路線，799 + 64 種外觀）並重啟代理。

## 限制

- 使用合成方塊時，基岩版會繪製每一種外觀。沒有時（`off`、沒有 Geyser API，或註冊失敗），所有 Mod 合計最多 799 種方塊外觀，另加 64 種可穿過方塊外觀；超出借用狀態的外觀在基岩版看不到（見 `block-looks.md`）。
- 在 Java 版，`prism:mining` 決定每個自訂方塊的破壞時間（ADR-0023）。在基岩版，合成方塊的破壞時間是徒手的 `prism:mining` 秒數，由 Geyser 從 Prism 註冊的硬度推得；工具不會加速（該方塊在基岩版沒有工具標籤）。尚未以客戶端確認。
- 原版音符盒一律顯示成普通狀態，不會顯示成自訂外觀（ADR-0023）。
- 這類方塊在伺服器端存的是惰性載體（棕色蘑菇方塊或結構空位）；Geyser 與 Java 版玩家一樣，透過封包改寫看到外觀（ADR-0022），但使用的是合成 id。
- 部分形狀（連接方塊與模型方塊）在基岩版有正確形狀的外框，但碰撞是完整方塊，與 Java 版相同（其載體是屏障）。
- id 在一次啟動內固定（之後新增的外觀要重啟），且 Geyser 版本需支援自訂方塊（2.9.5 以上）。

本頁內容 CI 皆無法以真實客戶端驗證；手動測試清單見 ADR-0008。冒煙測試（`PRISM_BEDROCK_SELFTEST_OK`）檢查 10,000 個合成方塊的
每個外觀都有 id 與註冊資料，且對一段樣本 section 的基岩版改寫帶有這些 id、Java 版改寫則沒有。
