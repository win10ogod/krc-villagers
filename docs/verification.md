# 測試與相容報告

## 環境

- Minecraft 1.21.1、NeoForge 21.1.244、Java 21、Gradle 9.2.1。
- KRC 1.1.4 與 GH 0.1.139，依賴完整清單和 SHA-256 見 `dependencies.lock.json`。
- 伺服器：NeoForge GameTest 專用伺服器。
- 實機：真實 Minecraft 客戶端與整合伺服器，在本專案建立的 Xvfb 顯示環境執行；測試世界位於 `run-ui/`。未操作既有遊戲世界。

## 自動伺服器測試

執行 `bash gradlew runGameTestServer --console=plain`。Gradle 會核對 `@GameTest` 數量與伺服器的成功摘要，若啟動失敗、測試未執行或數量不符，整個工作會失敗。

| 檢查 | 實際驗證 |
| --- | --- |
| 招募 | 生存模式收取 8 顆綠寶石；重複招募不再收費；幼年及不足額拒絕。 |
| 腰帶 | 註冊表中 **593 條 RiderDriverItem 腰帶**逐一完成基本裝甲裝備及解除。 |
| 原裝備 | 變身前的鐵頭盔恢復；交付武器的耐久損耗保留。 |
| 存檔 | 附件資料序列化；真正的村民實體存檔、移除、重載；UUID、職業、交易及穿戴物品連結保留。 |
| 形態 | Xtreme 連動安裝 Joker；Trial 在變更前檢查基本形態；已安裝 Trial 可再次變身；不相容的交付不會局部修改腰帶。 |
| 形態效果 | Kuuga Mighty 的 KRC 拳擊效果可作用於村民；外部加入的較強效果在解除時保留。 |
| 戰鬥 | 真正 entity tick 取得敵人；玩家、村民及寵物排除；友方傷害取消。 |
| 倒地／救援 | 致命傷轉為倒地並保留實體；再次受傷不死亡；4 顆綠寶石救援到半血。 |
| 騎士拳／踢 | GH 橋接開始原生 KRC 技能、單次扣能量 100／150；原技能 tick 啟動；重複施放拒絕。 |
| 失敗施放 | 停用、未知技能與不足能量不扣費。 |
| Clock Up | 原 KRC 起手、淨扣 50 能量、TimeClock 真實降低時間速度；施放鎖清理，結束時恢復時間並移除自身計時項。 |
| 時停 | Odin 識別、TimeClock 真實暫停、扣 100 能量與清理；第二名施法者不搶占，也不扣費；原有白名單保留。 |
| 多人權限邏輯 | 兩個伺服器玩家物件分別操作：非招募者、錯誤視窗及超距離封包拒絕；招募者操作成功。 |
| 原版生活 | 無戰鬥的自由生活模式恢復原版 Brain；駐守模式取得行為控制。 |

這些測試共 **21 項**。初始執行結果收錄在 [測試結果摘要](test-results.md)。初始伺服器日誌保留於本機 `evidence/gametest.log`；後續自動建置的日誌由 Actions 的 `diagnostics` artifact 提供，不納入 Git 倉庫或專案原始碼 ZIP。

## 實機與畫面

執行 `xvfb-run -a -s '-screen 0 1280x800x24' bash gradlew runClient -PuiSmoke --console=plain`。測試程式位於 `src/gametest/`，不會打包進正式模組。

實機流程以真正的互動／容器封包開啟原版交易、Shift＋右鍵管理、招募、交付腰帶，並以畫面座標點擊行為按鈕。GUI 比例 3 時縮放整個容器與滑鼠座標，確認駐守選擇到達伺服器。戰鬥階段由 GH AI 自行決定技能，不以直接呼叫技能冒充自動戰鬥。

測試世界使用創造模式玩家、固定位置且無 AI 的敵人，以及測試用生命和能量設定。為驗證時停，測試程式另外為同一村民交付 Odin 腰帶；此段檢查自動技能與連線客戶端，不重複測試 Odin 的拖放操作。生存模式實際收費由伺服器測試覆蓋。

| 證據 | 畫面 |
| --- | --- |
| 普通右鍵原版交易 | [01-vanilla-trading.png](../evidence/01-vanilla-trading.png) |
| Shift＋右鍵管理 | [02-recruitment.png](../evidence/02-recruitment.png) |
| 繁體中文裝備和行為 | [03-equipment-and-controls.png](../evidence/03-equipment-and-controls.png) |
| 變身後管理狀態 | [04-transformed-menu.png](../evidence/04-transformed-menu.png) |
| 完整 Kuuga 裝甲 | [05-full-rider-armor.png](../evidence/05-full-rider-armor.png) |
| GH 自動騎士拳 | [06-native-combat-rider_punch.png](../evidence/06-native-combat-rider_punch.png) |
| GH 自動騎士踢起手 | [07-rider-kick.png](../evidence/07-rider-kick.png) |
| 倒地 | [08-downed-companion.png](../evidence/08-downed-companion.png) |
| GUI 比例 3 | [09-compact-gui-scale-3.png](../evidence/09-compact-gui-scale-3.png) |
| 騎士踢過程 | [10-rider-kick-strike.png](../evidence/10-rider-kick-strike.png) |
| 招募者救援 | [11-rescued-companion.png](../evidence/11-rescued-companion.png) |
| Odin 自動時停 | [12-odin-time-stop.png](../evidence/12-odin-time-stop.png) |
| 解約恢復村民 | [13-released-villager.png](../evidence/13-released-villager.png) |

客戶端執行結果收錄在 [測試結果摘要](test-results.md)，成功標記為 `KRC_VILLAGERS_LIVE_OK`。完整日誌保留於本機 `evidence/client-live.log`，不納入 Git 倉庫或專案原始碼 ZIP。截圖顯示外觀，技能開始、狀態清理與物品數量另有狀態斷言；時停亦檢查客戶端的 TimeClock 暫停狀態，以及解約後的客戶端恢復狀態。

以上圖形客戶端證據使用 1.0.0 初始依賴組合。依賴自動更新的發布條件為編譯及全部伺服器 GameTest 通過，不代表逐版重跑了圖形客戶端或完整模組包遊玩。

## 版本相容處理

以下修正都在本附屬模組內；提供的兩個原始 JAR 未被修改。

1. GH 0.1.139 的 `MightyCombatService.init` 使用 `Dist.CLIENT.isClient()`，在專用伺服器仍得到 true。Mixin 改為查詢實際執行側，保留共用事件與技能註冊。
2. Mob Player Animator 1.4.0 的建構子無條件建立客戶端設定畫面 lambda，專用伺服器會解析到 `Screen` 類別。僅在專用伺服器保留原建構子的共用 `MobPlayerAnimator.init()`；客戶端建構子保留原樣。相容插件檢查建構子簽章與原初始化呼叫；版本範圍限定 1.4.0。
3. GH 的生物騎士辨識加入「已招募、完整變身、未倒地且變身鎖已結束的村民」。KRC 的原生 LivingEntity mixin 持續負責腰帶與技能 tick，附屬模組沒有重複執行 tick。
4. KRC 的部分技能入口要求玩家，及 `shrink` 缺少執行分派，使用僅作用於招募村民的適配。形態效果亦只針對招募村民接上完整效果列表。
5. TimeClock 4.7 的 `removeAbilityTick` 以物件身分比較 ID。本模組僅對自身計時 ID 使用值相等清理，避免取消後仍殘留計時器，日後影響其他施法者。其他施法者接管時間時不強制重設對方的時間狀態。

## 驗證範圍

- 593 條腰帶的覆蓋是**基本裝甲裝備／解除**，不是逐一渲染所有衍生形態。完整視覺實測為 Kuuga 與 Odin；資料驅動的形態連動另有 W／Accel 測試。
- 多人檢查覆蓋伺服器權限與真實客戶端封包；未同時啟動兩個圖形客戶端，也未在原有完整大型模組包逐項遊玩。
- `RiderFormChangeItem` 的標準相容、前置物品、前置形態、重設、連動槽位與原 KRC 腰帶更新路徑均有接入。若其他附屬模組把新形態邏輯完全寫在玩家專用 `Item.use` 覆寫中，需為該項新增村民適配。
- 一般主手武器走村民近戰；KRC 形態的炮擊／格林技能走原技能彈體。本版沒有通用「任意玩家武器右鍵使用」代理。
- 測試載入時，GH 內針對未安裝 LittleJoys／Super Sentai 的配方，以及 KRC 部分原始配方會產生 `RecipeManager` 錯誤訊息；相關資源 ID 和原始錯誤保留在日誌。它們未阻止本次遊戲載入與功能驗證。
