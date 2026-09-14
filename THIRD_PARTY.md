# 依賴與相容介面

本專案的 Java 程式與介面文字使用 MIT 授權。假面騎士、Minecraft、Kamen Rider Craft、Generic Henshin 與各依賴的名稱和資產屬於各自權利人。

附屬模組在執行時呼叫 KRC／GH 的程式與動畫，不在自身 JAR 內重包它們的模型、材質、聲音、動畫或程式類別。`dependencies.lock.json` 記錄所使用 JAR 內宣告的授權和 SHA-256；其他模組依各自授權取得與使用。

使用介面包括 KRC `RiderDriverItem`、`RiderFormChangeItem`、`AbilityUtil`、附件及形態效果；GH `HenshinTimingConfig`、`KickService`、`KrcCompat`、`FormAbilitySpecial`、`ZioTimeStopService`、`KabutoBulletService`；Mob Player Animator 與 KosmX Player Animator；Time Stop Clock `TimeData`。

Mixin 相容處理僅針對 README 列出的版本，發行時未修改使用者提供的 KRC／GH JAR。升級依賴時應重新檢查目標簽章、側別、技能扣能量、形態規則及實機渲染。
