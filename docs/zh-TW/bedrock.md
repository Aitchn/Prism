# 基岩版

基岩版玩家透過 Geyser 加入。Prism 以 Floodgate、再以 Geyser 偵測，並提供 `ClientProfile.supports(ClientCapability.X)`，讓
Mod 依「能力」而非「版本」調整。

| 功能 | Java | 基岩版 |
|---|---|---|
| 物品外觀 | 完整模型 | 取自物品貼圖的平面圖示 |
| 動態物品狀態 | 有 | 無 |
| 方塊 | 載體＋顯示實體 | 載體＋顯示實體（Geyser 轉譯） |
| BetterModel 模型 | 有 | 無（未實作） |
| 選單 | 箱子介面 | 箱子介面（Geyser 轉譯）；原生表單未實作 |

Prism 會建出基岩版 `.mcpack` 與 Geyser 自訂物品對應表，並在資源包伺服器的 `/bedrock/` 提供。**狀態：** 向 Geyser 註冊
（同伺服器或代理上的擴充）尚未實作，見[已知缺口](https://github.com/Aitchn/Prism/blob/main/docs/gaps.md)。以上內容皆未在真實基岩版客戶端驗證。
