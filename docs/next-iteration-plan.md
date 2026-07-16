# QR Code Simple 下一轮迭代计划

> 本文档记录 2026-07 梳理出的优化/补全计划及实施方案。调研基于版本 `0.1.8`（versionCode 11）。
>
> 已确认的产品决策：
> 1. TFLite 增强为死代码：删除 `QREnhancementModel` / `ModelManager` 与 TFLite 依赖；保留并接入 `QRCodeRestorationManager`（纯位图处理）到图片扫描失败重试路径。
> 2. Firebase Crashlytics 从未接线（`initialize()` 无调用、无 `google-services.json`、无 gms 插件）：彻底移除。
> 3. 恶意链接黑名单：支持在线更新，设置中可选开启（默认关）；无网络/无权限时静默跳过，不提示、不崩溃。
> 4. P4 范围只做「Firebase 移除 + 无障碍」；Baseline Profile / Fastlane / 平板双栏推迟。

---

## 调研结论

- **TFLite 是彻底死代码**：`QREnhancementModel` / `ModelManager` / `QRCodeRestorationManager` 在 main 源码中零引用（仅测试引用），无模型文件、无 assets 目录。`QRCodeRestorationManager` 本身可工作、有测试。
- **Firebase Crashlytics 也是死的**：`CrashlyticsManager.initialize()` 全项目无人调用，无 `google-services.json`，未应用 google-services 插件，README 宣称的"崩溃监控"实际不工作。
- **Lint 实际通过**（0 errors / 0 warnings / 8 hints，JDK 21 下）：AGENTS.md 里"lint 失败"已过时。8 个 hint 都在 HanXin 编解码器（`IntArray(n){0}` 冗余）。
- **翻译债务**：en 326 条 / zh 195 / ko 169 / de 104 / ja 仅 32 条。lint.xml 此前 ignore 了 `MissingTranslation` / `ExtraTranslation`。
- **Manifest** 无任何 `ACTION_SEND` / `ACTION_PROCESS_TEXT` / QS Tile 入口；`GenerateFragment` / `GenerateActivity` 不支持外部预填内容。
- 其余：`HistoryDao` 无按时间清理查询；`HistoryBackupManager` 明文 JSON/CSV；`SecurityManager` 黑名单写死 5 个占位域名；app 无 INTERNET 权限（离线优先）。

---

## 批次 1 — TFLite 死代码清理 + 修复器接入

**删除**
- `QREnhancementModel.kt`（含 `ModelManager`）
- `app/build.gradle` 中 `tensorflow-lite:2.17.0`、`litert-support-api:1.0.1` 依赖

**接入修复重试**（保留 `QRCodeRestorationManager.kt`）
- 图片扫描（`ResultActivity` 收集 `QRCodeScanner.scanAsFlow`）flow 完成且结果为空时，调用 `QRCodeRestorationManager.restoreQRCode(bitmap)` 取变体（上限 ~8 张），在剩余超时预算内逐张重扫
- 识别成功后在结果页显示弱提示"经图像修复后识别"
- 相机实时扫描不接（帧率敏感），仅图片扫描路径

**文档**：README / README_CN / knowledge-base 移除 TFLite 相关宣称，改为如实描述经典图像处理修复重试

**测试**：重试编排单测 + 一张确定性低对比度 QR 位图的修复回扫测试（不稳定则降级为编排测试）

---

## 批次 2 — Lint 门禁

- 修复 8 个 `UnnecessaryArrayInit` hint：`HanXinDecoder.kt:503,672`、`HanXinEncoder.kt:566,576,1670,1693,1867,1868`
- `.github/workflows/build.yml` gradle 调用加入 `:app:lintDebug`
- AGENTS.md 删除"lint 目前失败"的过时表述，改为"lint 是 CI 门禁"
- 验收：`lintDebug` 0 errors / 0 warnings / 0 hints，CI 绿

---

## 批次 3 — 翻译完整性门禁

- `app/lint.xml`：`MissingTranslation`、`ExtraTranslation` 由 ignore 改为 error
- `app/build.gradle` android 块加 `lint { baseline = file("lint-baseline.xml") }`，生成并提交 baseline
- 效果：现有 zh/de/ja/ko 债务进 baseline 不阻塞；**新增字符串必须 5 语言（en/zh/de/ja/ko）齐全否则 build 失败**
- 此后所有批次新增 UI 字符串必须 5 语言齐全（de/ja/ko 机翻即可）
- AGENTS.md / knowledge-base 更新该规则

---

## 批次 4 — 分享图片/视频到 App

- Manifest：`ScanImageActivity` 加 `ACTION_SEND`（`image/*`、`video/*`）+ `ACTION_SEND_MULTIPLE`（`image/*`），`exported="true"`
- 抽 `ScanImageFragment.processMedia/processImage` 为共享 `ScanImageProcessor`，Activity 与 Fragment 共用
- 路由：image → `ResultActivity.EXTRA_BITMAP_URI`；video → `VideoScanActivity.EXTRA_VIDEO_URI`；SEND_MULTIPLE → 第一张 + Toast
- 测试：Robolectric intent 路由测试（image / video / multiple 三路径）

---

## 批次 5 — 分享文本到生成页

- Manifest：`GenerateActivity` 加 `ACTION_SEND text/plain`，`exported="true"`
- `GenerateActivity` 解析 `EXTRA_TEXT` → arguments 传给 `GenerateFragment`（`ARG_PREFILL_CONTENT`），预填输入框并自动生成
- 测试：预填 UI 测试（仿 `GenerateFragmentBasicUiTest`）

---

## 批次 6 — Quick Settings Tile

- 新增 `QuickScanTileService : TileService`，点击收起通知栏并启动 `CameraScanActivity`（API 34+ 用 `startActivityAndCollapse(PendingIntent)`）
- Manifest：service + `BIND_QUICK_SETTINGS_TILE` 权限 + `ACTION_QS_TILE` filter + icon/label
- 测试：轻量（服务可实例化 / manifest 声明）

---

## 批次 7 — 历史保留策略

- `HistoryDao` 新增按时间清理查询（收藏豁免）：`DELETE FROM history WHERE timestamp < :cutoff AND isFavorite = 0`
- `HistoryRepository` 包装；`PrivacySettingsActivity` 加保留时长选项（永久/30/90/365 天），存 `app_settings`
- `QRCodeApp.onCreate` 启动时 IO 协程执行一次清理（0=永久则跳过）
- 测试：DAO 级 Robolectric 测试（过期删除 / 收藏保留 / 近期保留）

---

## 批次 8 — 加密备份

- `HistoryBackupManager` 新增 `exportEncrypted(context, password): ByteArray` / `importEncrypted(context, bytes, password)`
- 格式：magic `QRBK1` + version(1B) + salt(16B) + IV(12B) + AES-256/GCM 密文；PBKDF2-HmacSHA256 10 万次派生
- `BackupActivity`：导出对话框加"加密"复选框 + 密码确认；导入检测 magic → 弹密码框；错误密码/损坏文件给明确 Toast
- 明文 JSON/CSV 路径保持不变
- 测试：加密往返、错误密码、截断文件、magic 识别

---

## 批次 9 — 黑名单在线更新（可选、静默）

- 黑名单/可疑关键词/短链服务列表抽到 `app/src/main/assets/security/blacklist.json`（含 `version`）
- `SecurityManager` 加载顺序：assets 内置 → filesDir 覆盖（仅当 schema 合法且 version 更新）
- `BlacklistUpdater`：`HttpURLConnection` 拉取本仓库 raw 地址，5s 超时，校验后写 filesDir；一切失败仅 Log，不 UI、不崩溃
- Manifest 新增 `INTERNET` 权限
- `PrivacySettingsActivity` 加开关"自动更新安全黑名单"（默认关）；开启时立即静默拉一次；`QRCodeApp.onCreate` 若已开启则后台静默检查（prefs 时间戳节流 24h）
- 测试：schema 校验、损坏覆盖回退、版本比较、拒绝非法内容（不测真实网络）

---

## 批次 10 — Firebase 彻底移除

- 删 `CrashlyticsManager.kt`；移除 firebase-bom / crashlytics / analytics 依赖
- `play-services-tasks` / `basement` constraints 保留（ML Kit 也依赖，防 CI 挂起理由仍成立），更新注释
- README / README_CN / knowledge-base / testing-strategy 移除 Crash Monitoring、Firebase、Analytics 表述

---

## 批次 11 — 无障碍

- `lint.xml`：`ContentDescription` 改 error + 并入 lint baseline；主流程现存问题本轮修完（闪光灯/切相机图标按钮、格式下拉、颜色选择器、角度旋钮、历史列表项、扫描框自定义 View）
- `ClickableViewAccessibility` 维持 ignore（备注后续）
- 新字符串 5 语言补齐

---

## 批次 12 — 收尾

- `versionCode 12` / `versionName "0.1.9"`
- 全量文档同步（README 双语新功能：分享入口、QS Tile、保留策略、加密备份、黑名单自动更新；knowledge-base 文件索引增删；implementation-plan 后续计划标记完成）
- 终验：`./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` 全绿
- 每批次独立 commit + push

---

## 执行顺序与风险

批次 1 → 12 顺序执行；**批次 3 必须先于 4–9、11**（后续批次受 5 语言门禁约束）。

1. 修复回扫位图测试的确定性 → 以编排测试为主，位图测试为辅
2. lint baseline 含路径段 → 生成后确认可移植
3. TileService 在 API 34+ 的 `startActivityAndCollapse` 弃用差异 → 按 SDK 分支处理
4. 批次 9 新增 INTERNET 权限改变权限足迹 → README 隐私说明同步

## 完成状态

- [x] 批次 1
- [x] 批次 2
- [x] 批次 3
- [x] 批次 4
- [x] 批次 5
- [x] 批次 6
- [x] 批次 7
- [x] 批次 8
- [x] 批次 9
- [x] 批次 10
- [x] 批次 11
- [x] 批次 12

## 第二轮执行记录（构建/测试工程化，0.2.0）

- [x] P0.3 release 构建正经化：R8 + shrinkResources + 各库 ProGuard 规则；RELEASE_KEYSTORE_* 注入签名，未配置回退 debug（Release APK 78M / AAB 44M，Debug APK 120M）
- [x] P1.2 Release 工作流：`v*` 标签 → 构建 APK+AAB → GitHub Release（`.github/workflows/release.yml`）
- [x] P1.3 kapt → KSP 2.2.21-2.0.5
- [x] P3.1 JUnit 5 Platform + Vintage（5.14.4；6.x 已移除 Vintage 不可用）
- [x] P3.3 生成金样测试（QR/EAN-13/Code128/DataMatrix SVG SHA-256）
- [x] P3.5 覆盖率门禁（指令 ≥ 0.75 / 行 ≥ 0.70，探针验证会失败）
- [x] 跟进项：ScannerOverlayView / ScanRegionView 接入相机扫描页（`ScanRegionMapper` 坐标映射）
- 推迟：P1.4 targetSdk 36 / 依赖大版本升级（需真机验证）、P3.2 androidTest 真机冒烟（需模拟器基建）、P4.1 Baseline Profile、P4.3 Fastlane、P4.5 平板双栏
- **遗留风险**：R8 混淆后的 release 包尚未在真机做过全流程冒烟，首次正式发布前需人工验证（重点：Excel 导入、批量生成 ZIP、微信引擎、SVG 导出）

## 执行中新发现（已全部解决/记录）

1. ~~**ScannerOverlayView / ScanRegionView 也是死代码**~~：**已解决（接入）**。扫描线动画叠加在相机扫描页；ScanRegionView 通过顶栏框选按钮启用，选择区域经 `ScanRegionMapper`（FILL_CENTER 裁剪 + rotationDegrees 旋转变换）映射到帧像素坐标后裁剪识别。README 宣称的"Scan Region Limit / 扫描区域限定"与扫描线动画现已为真实功能。
2. 批次 8 顺带修复了一个存量 bug：JSON 备份因检测字符写错（`[` vs `{`）一直被路由到 CSV 导入。
