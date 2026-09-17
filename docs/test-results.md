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
