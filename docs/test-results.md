# 1.3.0 測試結果摘要

驗證日期：2026-09-29。Minecraft 1.21.1、NeoForge 21.1.244、Java 21、KRC 1.1.4、GH 0.1.139（CurseForge 8929723／v431）、GeckoLib 4.9.3；完整依賴見 `dependencies.lock.json`。

```bash
python3 -m unittest discover -s scripts/tests -v
./gradlew --no-daemon --console=plain build runGameTestServer
xvfb-run -a -s '-screen 0 1280x800x24' ./gradlew --no-daemon --console=plain runClient -PuiSmoke
```

實際結果：18 項自動化測試、51 項 GameTest 通過。包括原有腰帶、技能、武器、游泳與按鍵回歸，以及下列新增規則：

- 兩位玩家各自從 16 起算；第一位依序扣 16、20、24，解約不重置。失敗不計次、創造免費但計次；UUID 紀錄序列化保留，選單取得玩家自己的價格。
- 隨機倒地期限落在 3600～6000 刻。本次實際等待 **4560 刻**後由 entity tick 自動站起；期限前保持倒地且不吃食物。重載保留期限，舊救援封包無法提前恢復或扣款。
- 麵包一次消耗一份、回復 5 點生命，間隔 100 刻，滿血不再吃；手持武器保留。湯、金蘋果與蜂蜜的容器／原生效果亦通過檢查。
- 玩家近戰、其他玩家及玩家箭矢不能傷害已招募村民；普通村民和敵怪傷害照常。移動速度減少 25%，重複更新不疊加，解約恢復。
- Musou Saber 和 Neo GM-01 Scorpion 的射擊間隔至少 20 刻，換槍不能跳過，射擊期限存檔保留。

```text
All 51 required tests passed :)
AUTO_RECOVERY_WAIT_VERIFIED 4560 ticks
LIVE_CHECK 3 to 5 minute recovery countdown reaches client
LIVE_CHECK server charged exactly 24 emeralds
LIVE_CHECK injured villager consumes one transferred bread
LIVE_CHECK food heals 5 HP while keeping weapon
LIVE_CHECK food heals at intervals and stops when full
LIVE_CHECK connected player attack cannot damage recruited villager
KRC_VILLAGERS_BALANCE_OK: progressive recruitment, food consumption/healing and player protection verified
KRC_VILLAGERS_LIVE_OK
BUILD SUCCESSFUL in 2m 54s
```

圖形客戶端完整流程包含原有戰鬥、回血形態、時停、物品返還與改鍵，以及生存模式第三次招募、交付食物與武器、食物消耗和玩家攻擊封包。倒地 UI 先驗證真實 3～5 分鐘的剩餘時間，再僅將此圖形測試場景的期限提前，檢查恢復同步；完整等待由上方 GameTest 驗證。

24 張畫面見 [驗證報告](verification.md)。本機日誌為 `.work/balance-tests.log`、`.work/balance-client.log`、`.work/balance-automation.log`。GameTest 與圖形測試程式不包含在正式 JAR；測試世界及日誌不納入專案 ZIP。

---

# 1.2.0 測試結果摘要

驗證日期：2026-09-17。Minecraft 1.21.1、NeoForge 21.1.244、Java 21、KRC 1.1.4、GH 0.1.139（CurseForge 8893780）、GeckoLib 4.9.3；完整檔案與 SHA-256 見 `dependencies.lock.json`。

## 自動化與專用伺服器

```bash
python3 -m unittest discover -s scripts/tests -v
./gradlew --no-daemon --console=plain build runGameTestServer sourceZip
```

實際通過結果：

```text
Ran 18 tests
OK
WEAPONS_RETAINED 525
ALL_BELTS_VERIFIED 593
All 40 required tests passed :)
```

525 種武器覆蓋原版交易／耕作展示下的保管；593 條腰帶覆蓋基本裝甲裝備／解除。回歸測試涵蓋鎧武飽和與再生的真實回血、自然回血規則、重載與解約的武器數量、正常耐久損壞、多個同槽形態、Kiwami 前置、選單開啟時的形態切換、弓弩和兩類 KRC 槍械的實際投射物命中、獨立彈匣與深水上岸。1.2.0 增加管理封包的有效目標、互動距離、視線、玩家狀態、交易狀態與主人權限檢查。完整覆蓋範圍見 [測試與相容報告](verification.md)。

## 真實客戶端

```bash
xvfb-run -a -s '-screen 0 1280x800x24' ./gradlew --no-daemon --console=plain build runGameTestServer runClient -PuiSmoke
```

真實 Minecraft 客戶端與整合伺服器完成普通交易、Shift＋右鍵管理、招募、裝備拖放、GUI 比例 3 的滑鼠控制、Kuuga 裝甲、GH 自動騎士拳／踢、救援、Odin 自動時停與解約清理。流程亦在保持變身、管理視窗開啟時，以容器封包交付 Golden Ringo 與 Black Ringo，滑鼠開啟自動換形態，再檢查回血及 Musou Saber 射擊。

1.2.0 另外完成原版按鍵設定畫面的改鍵、保存後重新載入、真實鍵盤 N 與滑鼠中鍵開啟管理、普通右鍵交易、Esc 取消綁定與重設 Shift＋右鍵。鍵鼠事件由獨立 Xvfb 的 XTest 注入，不操作使用者桌面。

新增的實際成功斷言：

```text
LIVE_CHECK two same-slot forms transferred while transformed
LIVE_CHECK auto form switch enabled through real mouse control
LIVE_CHECK Gaim automatically selected Black Ringo on connected client
LIVE_CHECK Gaim armor healed after automatic form change
LIVE_CHECK Gaim retains and fires Musou Saber
LIVE_CHECK native ranged projectile hit in integrated world
LIVE_CHECK fired weapon returned once on release
LIVE_CHECK custom key persists after options reload
LIVE_CHECK physical rebound key opens management without sneaking
LIVE_CHECK old Shift + right click no longer opens management after rebinding
LIVE_CHECK plain right click still opens vanilla trades after rebinding
LIVE_CHECK plain mouse binding clears previous Shift modifier
LIVE_CHECK physical middle mouse opens management
LIVE_CHECK Escape unbinds management
LIVE_CHECK unbound management leaves vanilla trading available
LIVE_CHECK physical default shortcut works after native reset
KRC_VILLAGERS_LIVE_OK: trade, Shift UI, recruitment, scaled controls, armor, GH punch/kick, rescue, Odin time stop, Gaim reserve forms, auto switching, healing, native ranged combat, exact equipment returns and configurable keyboard/mouse shortcuts verified
BUILD SUCCESSFUL
```

20 張畫面證據位於 `evidence/`。完整測試日誌位於本機 `.work/keybindings-client.log`、`.work/keybindings-tests.log`、`.work/keybindings-automation.log`，不打包原始日誌、測試世界或第三方模組。GitHub Actions 會重跑全部伺服器測試，再發布同次建置的 JAR、原始碼和 SHA-256 清單。

只重跑按鍵流程時，可在同一個 `xvfb-run ... runClient -PuiSmoke` 命令前加入 `JAVA_TOOL_OPTIONS=-Dkrcvillagers.keysOnly=true`；此模式只輸出 `KRC_VILLAGERS_KEYS_OK`，不代表戰鬥等完整流程已執行。本版亦已通過完整流程。
