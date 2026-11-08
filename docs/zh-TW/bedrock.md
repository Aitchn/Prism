# 基岩版

基岩版玩家透過 Geyser 加入。Prism 以 Floodgate、再以 Geyser 偵測，並提供 `ClientProfile.supports(ClientCapability.X)`，讓
Mod 依「能力」而非「版本」調整。

| 功能 | Java | 基岩版 |
|---|---|---|
| 物品外觀 | 完整模型 | 取自物品貼圖的平面圖示 |
| 動態物品狀態 | 有 | 無 |
| 方塊（預設） | 音符盒狀態＋資源包 | 音符盒狀態＋Geyser 自訂方塊對應 |
| 使用 BetterModel 模型的方塊 | 動畫模型（顯示實體） | 以 `textures/block/<name>.png` 貼圖的靜態方塊（沒有貼圖時是普通音符盒），個別送給每位基岩版玩家 |
| 連接方塊（`prism:connections`） | 屏障上的顯示實體 | 依連接組合自動產生的形狀，個別送給每位基岩版玩家 |
| 可穿過的方塊（`prism:passable`） | 絆線狀態＋資源包 | 絆線狀態＋無碰撞的 Geyser 對應 |
| `blocks.backend: display` 下的所有方塊 | 顯示實體 | **看不見**（Geyser 無法繪製顯示實體） |
| 方塊外觀變體（`prism:variants`、`setVariant`） | 有 | 有 |
| 自訂音效（`sounds.json`、`prism:ambient_sound`） | 有 | 有（基岩版資源包的 `sound_definitions`，經 Geyser 播放） |
| 動畫方塊材質（`.png.mcmeta`） | 有 | 有（flipbook） |
| 選單 | 箱子介面 | 箱子介面（Geyser 轉譯）；原生表單未實作 |

## 設定

1. 在同一台伺服器安裝 Geyser（`Geyser-Spigot`），並確認 `plugins/Geyser-Spigot/config.yml` 的
   `gameplay.enable-custom-content: true`（舊版 Geyser 為 `enable-custom-content`）。關閉時 Prism 會在日誌警告。
2. 啟動伺服器。Prism 會把 `custom_mappings/prism_items.json`、`custom_mappings/prism_blocks.json` 與 `packs/prism.mcpack`
   寫進 Geyser 資料夾，並記錄 `PRISM_GEYSER_INSTALLED changed=[...]`。
3. 只要那一行列出有變更的檔案（第一次啟動，或新增、修改內容後），**就重新啟動一次**：Geyser 只在啟動時讀取對應表與資源包。

若 Geyser 裝在代理（Proxy）上：Prism 會把相同檔案寫到 `plugins/Prism/geyser/`，請把 `custom_mappings/` 與 `packs/`
複製到代理的 Geyser 資料夾後重啟代理。

## 限制

- 所有 Mod 合計最多 799 種方塊外觀（變體 × 朝向，或變體 × 連接組合），另加 64 種可穿過方塊外觀。
- 以音符盒呈現的自訂方塊，挖掘方式與音符盒相同（斧頭、很快）；`prism:mining` 只對顯示實體方塊有效。
- 原版音符盒若被調到某個自訂外觀佔用的狀態，會顯示成那個自訂方塊。

本頁內容 CI 皆無法以真實客戶端驗證；手動測試清單見 ADR-0008。
