# 1.0.0 測試結果摘要

驗證日期：2026-09-14。環境為 Minecraft 1.21.1、NeoForge 21.1.244、Java 21，以及 `dependencies.lock.json` 列出的模組版本。

## 專用伺服器

執行命令：

```bash
bash gradlew build runGameTestServer --console=plain
```

本次成功輸出：

```text
ALL_BELTS_VERIFIED 593
All 21 required tests passed :)
BUILD SUCCESSFUL in 29s
```

腰帶數量指基本裝甲裝備及解除驗證。每項測試內容與實際覆蓋範圍見 [測試與相容報告](verification.md)。

## 真實客戶端

執行命令：

```bash
xvfb-run -a -s '-screen 0 1280x800x24' bash gradlew runClient -PuiSmoke --console=plain
```

本次完成了普通交易、Shift＋右鍵管理、招募、裝備拖放、縮放後的滑鼠按鈕操作、完整裝甲、GH 自動騎士拳／踢、救援、Odin 自動時停、解除時停和解約返還腰帶。

結束前的狀態檢查包括：

```text
LIVE_CHECK client TimeClock state is paused
LIVE_CHECK release restores world time
LIVE_CHECK release restores original villager
LIVE_CHECK release returns exactly one handed-over belt
LIVE_CHECK client TimeClock state restored after release
```

完整成功標記：

```text
KRC_VILLAGERS_LIVE_OK: real trade, Shift interaction, recruitment, equipment, scaled mouse controls, full armor, GH automatic punch and kick, rescue, Odin time stop and release verified
BUILD SUCCESSFUL in 1m 39s
```

截圖是 README 與相容報告使用的文件素材，收錄於 `evidence/`。原始執行日誌、測試世界、編譯產物和第三方依賴留在本機；測試程式及重現指令隨原始碼提供。
