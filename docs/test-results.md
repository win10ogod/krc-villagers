# 1.1.0 測試結果摘要

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
All 37 required tests passed :)
```

525 種武器覆蓋原版交易／耕作展示下的保管；593 條腰帶覆蓋基本裝甲裝備／解除。新增測試包含鎧武飽和與再生的真實回血、自然回血規則、重載與解約的武器數量、正常耐久損壞、多個同槽形態、Kiwami 前置、選單開啟時的形態切換、弓弩和兩類 KRC 槍械的實際投射物命中、獨立彈匣與深水上岸。完整覆蓋範圍見 [測試與相容報告](verification.md)。

## 真實客戶端

```bash
xvfb-run -a -s '-screen 0 1280x800x24' ./gradlew --no-daemon --console=plain build runGameTestServer runClient -PuiSmoke
```

真實 Minecraft 客戶端與整合伺服器完成普通交易、Shift＋右鍵管理、招募、裝備拖放、GUI 比例 3 的滑鼠控制、Kuuga 裝甲、GH 自動騎士拳／踢、救援、Odin 自動時停與解約清理。新增流程在保持變身、管理視窗開啟時，以容器封包交付 Golden Ringo 與 Black Ringo，滑鼠開啟自動換形態，再檢查回血及 Musou Saber 射擊。

新增的實際成功斷言：

```text
LIVE_CHECK two same-slot forms transferred while transformed
LIVE_CHECK auto form switch enabled through real mouse control
LIVE_CHECK Gaim automatically selected Black Ringo on connected client
LIVE_CHECK Gaim armor healed after automatic form change
LIVE_CHECK Gaim retains and fires Musou Saber
LIVE_CHECK native ranged projectile hit in integrated world
LIVE_CHECK fired weapon returned once on release
KRC_VILLAGERS_LIVE_OK: trade, Shift UI, recruitment, scaled controls, armor, GH punch/kick, rescue, Odin time stop, Gaim reserve forms, auto switching, healing, native ranged combat and exact equipment returns verified
BUILD SUCCESSFUL in 2m 33s
```

16 張畫面證據位於 `evidence/`。完整測試日誌位於本機 `.work/improvements-client.log`、`.work/improvements-tests.log`、`.work/automation-tests.log`，不打包原始日誌、測試世界或第三方模組。GitHub Actions 會重跑全部伺服器測試，再發布同次建置的 JAR、原始碼和 SHA-256 清單。
