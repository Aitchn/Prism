# 翻譯

每位玩家看到的物品、選單與訊息，都是自己客戶端所設定的語言。伺服器上每個物品堆疊只有一種形態，裡面不存任何語言的文字，
所以中文玩家丟出的物品可以和英文玩家撿起的物品堆疊在一起。基岩版玩家（透過 Geyser）也一樣，使用的是基岩版客戶端的語言。

## 語言檔

在 jar 中每個語系放一個 JSON 檔，和其他內容放在一起：

```
src/main/resources/prism/lang/en_us.json
src/main/resources/prism/lang/zh_tw.json
```

檔名是小寫的 Minecraft 語言代碼。檔案內容是一個字串組成的 JSON 物件；巢狀物件會以點相連，
所以 `{"item": {"examplemod": {"ruby": "Ruby"}}}` 等同 `{"item.examplemod.ruby": "Ruby"}`。值是 MiniMessage 文字
（`<red>紅寶石`），`{0}`、`{1}`…… 會換成參數。語言檔和其他內容一樣在模組啟用時從 jar 讀取，不會複製到伺服器資料夾。
格式錯誤的檔案會讓該模組無法啟用，並指出錯誤所在的行。

| 用途 | 鍵 |
|---|---|
| 物品名稱 | `item.<命名空間>.<路徑>`，路徑中的 `/` 換成 `.`（`examplemod:gems/ruby` 為 `item.examplemod.gems.ruby`） |
| 方塊名稱 | `block.<命名空間>.<路徑>`。會放置方塊、又沒有 `item.` 鍵的物品，以該方塊命名。 |
| 固定說明、選單標題、圖示、訊息 | 自訂的鍵，例如 `examplemod.lore.heal` |

`prism:display_name` 仍可使用，在沒有語言檔為該物品命名時採用。

## 玩家拿到哪一段文字

查詢順序如下：

1. 玩家的語系（`zh_tw`）；
2. 同一語言的其他語系：`fr_ca` 先讀 `fr_fr`，再讀其他。中文只在同一種文字內退回：`zh_hk` 讀 `zh_tw`，絕不讀 `zh_cn`；
3. `en_us`；
4. 鍵本身，當作 MiniMessage 文字。所以不是鍵的字串會原樣顯示。

啟動時 Prism 為每個模組印出一行缺漏摘要：

```
PRISM_LANG ExampleMod locales=en_us,zh_tw keys=26 missing=1 (zh_tw: examplemod.demo.english_only)
```

某個鍵在同一模組的其他語系存在、或它是某個完全沒有名稱的物品的名稱鍵時，就算是該語系的缺漏。

## 物品

物品名稱是它的 `item.` 鍵。說明文字是固定的 `prism:lore` 各行（每行是鍵或文字），接著是 `ItemLoreBehavior` 的各行。
在 `loreMessages` 回傳翻譯鍵與參數：

```java
@Override
public @NotNull List<Message> loreMessages(@NotNull ItemStack stack) {
    int uses = stack.getPersistentDataContainer().getOrDefault(USES, PersistentDataType.INTEGER, 0);
    return List.of(Message.of("examplemod.lore.used", uses));
}
```

```json
{ "examplemod.lore.used": "<gray>已使用 <white>{0}</white> 次" }
```

參數可以是文字（照原樣顯示，標籤不會被解讀）、數字（加上千分位）、布林值，或另一個 `Message`（以同一位玩家的語言翻譯，
例如 `Prism.translations().itemName(key)`）。這些行以鍵和參數的形式存在堆疊上，只在送給每位玩家的封包裡才變成文字。
不要自己替物品設定名稱或說明：Prism 管理自己物品的這兩者。玩家在鐵砧取的名字會保留。

想知道玩家看到什麼（例如寫進書本或聊天），用 `Prism.translations().name(stack, locale)` 與
`Prism.translations().lore(stack, locale)`。

## 選單

選單標題、按鈕文字、`MenuView.setIcon` 與 `setGauge` 的名稱和說明、輸入框標題都接受鍵或文字。需要參數時用 `Message` 版本：

```java
view.setIcon(COUNT_SLOT, PrismKey.parse("minecraft:clock"), Message.of("examplemod.counter.count", count),
        List.of(Message.of("examplemod.counter.count_hint")));
```

標題在選單開啟時翻譯。圖示和一般物品一樣依每位觀看者翻譯。按鈕文字留空會保留圖示自己的名稱，`/prism items` 就是這樣以
觀看者的語言列出所有物品。

## 訊息

```java
Prism.translations().send(player, Message.of("examplemod.counter.busy"));
Prism.translations().sendActionBar(player, Message.of("examplemod.counter.count", 3));
Component title = Prism.translations().render(Message.of("examplemod.title"), player);
```

可以在任何執行緒呼叫。

## 玩家切換語言時

Prism 會立即重送開啟中的容器與玩家自己的背包，物品不必重開就換成新語言。選單標題要到下次開啟才會改變。

## 先前的物品

這個功能之前做出的物品，名稱與說明是以單一語言存在物品裡的。Prism 會在第一次看到這種物品時改寫它（加入遊戲時的背包與
終界箱、開啟容器時的內容、撿起的物品），讓它重新能和新物品堆疊。

## 限制

- 聊天訊息懸浮顯示（`show_item`）中的物品名稱不會翻譯。
- 基岩版資源包裡的名稱（Geyser 物品對應與合成方塊名稱）在打包時就固定；遊戲中基岩版玩家看到的是隨每個堆疊送出的翻譯名稱。
- 選單標題要重新開啟選單才會換語言。
