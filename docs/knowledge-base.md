# QR Code Simple - 项目知识库

## 1. 项目概述

QR Code Simple 是一款 Android 二维码/条码扫描与生成应用。
- 包名：`com.xenoamess.qrcodesimple`
- 版本由完整 Git 历史推导；本轮发布/更新系统的首个目标 Stable 标签为 `v0.2.6`。
- 目标：支持超过 50 种条码格式的生成，其中可扫描的格式会继续保证生成与扫描回环。

## 2. 技术栈

| 组件 | 技术/库 | 版本 |
|------|---------|------|
| 语言 | Kotlin | 2.3.21 |
| UI | Jetpack Compose / XML Layout | - |
| 相机 | CameraX | 1.5.3 |
| 数据库 | Room + SQLCipher（sqlcipher-android 新坐标） | 2.7.1 / 4.17.0 |
| 二维码识别 | ZXing | 3.5.3 |
| 条码识别 | ML Kit | 17.2.0 |
| 二维码识别 | WeChatQRCode | 2.6.0 |
| Micro QR | BoofCV | 1.4.0 |
| 复杂格式生成 | OkapiBarcode | 0.5.6 |
| 测试 | JUnit Platform（Jupiter + Vintage）+ Robolectric | 6.1.2 / 4.16.1 |
| 覆盖率 | JaCoCo + GitHub Pages | 0.8.12 / shields.io endpoint badge |

## 3. 支持格式总览

项目当前支持 **超过 50 种条码格式** 的生成，并在可扫描的格式上保持扫描回环。格式按扫描能力分为两类：

- **可扫描格式**：`BarcodeFormat.isScannable = true`，生成图片可被当前扫描栈（ZXing / ML Kit / BoofCV / WeChatQR / HanXin / 自定义一维码）识别。
- **仅生成格式**：`BarcodeFormat.isScannable = false`，由 OkapiBarcode 生成但不保证能被当前扫描器识别，生成页面会提示用户。

| 类别 | 可扫描 | 仅生成 |
|------|:------:|:------:|
| QR Code / Data Matrix / Aztec / PDF417 / Han Xin Code / MaxiCode / Micro QR | ✅ | - |
| Code 128 / Code 39 / Code 93 / EAN-13 / EAN-8 / UPC-A / UPC-E / Codabar / ITF / RSS-14 / RSS Expanded | ✅ | - |
| UPC/EAN Extension / Pharmacode / Plessey / MSI Plessey / Telepen | ✅ | - |
| Code 39 Extended / ITF-14 / Code 2 of 5 系列 / Code 11 / Code 16K / Code 32 / Code 49 / Codablock F / Channel Code / LOGMARS / NVE-18 / DPD Code / Pharmacode Two-Track / Pharmazentralnummer / Telepen Numeric / 各类邮政码 / GS1 DataBar Limited / Composite / EAN/UPC Add-On / Swiss QR Code / UPN QR Code / Aztec Rune / Code One / Grid Matrix | - | ✅ |

> 说明： 历史文档 doc/barcode-formats.md 已删除；其“仅扫描”表述已被本表取代——所有 OkapiBarcode 格式现均可生成，原 22 种可扫描格式保持完整回环。

## 4. 核心约定

### 命名
- 应用内条码格式枚举：`com.xenoamess.qrcodesimple.data.BarcodeFormat`
- ZXing 条码格式：`com.google.zxing.BarcodeFormat`
- 历史类型：`com.xenoamess.qrcodesimple.data.HistoryType`

### 生成入口

所有条码生成统一通过 `BarcodeGenerator.generate(content, config)`。
样式化生成走 `AdvancedBarcodeGenerator.generateStyled(content, format, width, height, style)`（另有旧版单尺寸重载 `generateStyled(content, format, size, style)` 保持兼容）：

- 生成器本身不清洗 `StyleConfig`，传入什么就用什么；调用方（`GenerateFragment`、历史页面）在生成前调用 `AdvancedBarcodeGenerator.sanitize(style, format)` 清洗。
- `GenerateFragment` 根据当前格式能力表隐藏不支持的控件，不做提示。
- 各格式的实际样式能力见 [`docs/style-roundtrip-matrix.md`](style-roundtrip-matrix.md)，其中包含 `moduleShape` / `moduleFillRatio` / `positionPatternShape` 对所有可扫描格式的真实回扫通过率。

`StyleConfig` 字段及能力表：
- `foregroundColor` / `backgroundColor`：所有格式。
- `cornerRadius`（0~1）：所有格式开放。
- `logoScale` / `logoBitmap`：所有格式。
- `logoShape` / `logoCornerRadius`：中心 logo 的裁剪形状（SQUARE 默认 / ROUNDED_RECT / CIRCLE）与圆角半径；所有格式。遮罩为逐像素计算（Robolectric 与真机行为一致，不走 BitmapShader）。
- `gradientAngle` / `gradientStops` / `gradientType`：所有格式。
- `foregroundBitmap` / `backgroundBitmap`：所有格式。
- `moduleShape` / `moduleFillRatio`：对所有格式生效。有结构化布局的格式走原生渲染，仅生成格式和 MaxiCode 走兜底图片后处理（连通域 + 腐蚀/形状）。不同组合的回扫能力差异较大；具体见 [`docs/style-roundtrip-matrix.md`](style-roundtrip-matrix.md)。
- `positionPatternShape`：只对有定位图案或 Guard 的格式生效。详情见 `style-roundtrip-matrix.md`。
- `ecLevel`：QR Code 直接生效；Aztec / PDF417 / Han Xin / Micro QR / Grid Matrix 经映射后生效；MaxiCode 及其他格式不生效。

`ecLevel` 映射：

| 格式 | L | M | Q | H |
|---|---|---|---|---|
| QR Code | L | M | Q | H |
| Aztec | 25% | 40% | 55% | 70% |
| PDF417 | 2 | 4 | 6 | 8 |
| Han Xin | 1 | 2 | 3 | 4 |
| Micro QR | L | M | Q | H→Q |
| Grid Matrix | 1 | 2 | 3 | 5 |

生成历史保存前也会按能力表清洗，保证历史记录只包含实际生效的样式参数。

### 扫描入口

- 实时扫描（相机/视频）：`QRCodeScanner.scan(context, bitmap)` 或 `QRCodeScanner.scanSync(context, bitmap)`，内部并行执行 6 个引擎，等待全部结束后返回完整结果列表。
- 图片扫描：`QRCodeScanner.scanAsFlow(context, bitmap, config)`，6 个引擎并行执行，任一引擎识别到结果即通过 `Flow` 分批 emit；ResultActivity 收集到首个结果即展示页面，后续结果动态追加。图片扫描使用 `IMAGE_SCAN_CONFIG`（总超时 120s / 单引擎 60s），实时扫描使用 `CAMERA_SCAN_CONFIG`（总超时 15s / 单引擎 5s）。
- 修复重试：图片扫描常规流程无任何结果时，`RestorationRescan.rescan()` 会用 `QRCodeRestorationManager` 生成修复变体（灰度 / 对比度 / 锐化 / 二值化 / 缩放，最多 8 个）逐张重扫；识别成功则在结果页显示"经图像修复后识别"弱提示。相机实时扫描不走该路径。

### 历史记录
- `HistoryRepository.insertGenerate(content, type, barcodeFormat, styleJson)` 保存生成记录。
- `HistoryItem.barcodeFormat` 字段保存格式名称字符串；`HistoryItem.styleJson` 字段保存生成样式参数 JSON（不含图片）。
- 按 `content` + `isGenerated` 去重：扫描记录和生成记录各自独立。同一文本扫描重复时更新 `timestamp` 置顶；生成重复时更新最新参数/格式/时间/样式；不新增多条。备份导入时同样按此规则合并，避免重复记录。
- 生成、保存、分享按钮均会触发历史记录写入/更新。
- 历史列表的二维码分享使用原始 `barcodeFormat` 和 `styleJson` 重新生成图片，保持与生成时一致。
- 历史详情页提供“自定义样式生成”按钮，可将文本带入 `GenerateFragment` 重新选择样式。
- 保留策略：`PrivacySettingsActivity` 可配置自动清理（永久/30/90/365 天），存于 `app_settings`；`QRCodeApp.onCreate` 启动时执行一次 `deleteOlderThan`（收藏豁免），0 表示永久保留。
- 平板双栏：`layout-sw600dp/fragment_history.xml` 为列表 + 详情双栏；`HistoryFragment.openHistoryDetail` 检测到 `detailPaneContainer` 时嵌入 `HistoryDetailFragment`，否则启动 `HistoryDetailActivity`。列表布局经 `<include android:id="@+id/listPart">` 在两种配置间复用（ViewBinding 生成嵌套绑定 `binding.listPart`）。
- 备份导出支持明文 JSON / CSV 与加密备份（`QRBK1` magic + AES-256/GCM + PBKDF2 10 万次）；导入按内容自动识别（magic → 密码框，`{` / `[` → JSON，其余 → CSV）。
- 恶意链接黑名单：`assets/security/blacklist.json` 内置（version 1），`PrivacySettingsActivity` 可开启静默在线更新（默认关；24h 节流；任何失败仅记日志）。开启需要 `INTERNET` 权限；其他网络用途为用户手动检查更新，以及用户显式打开后的 Stable 自动检查。

## 5. 扫描引擎

当前包含 6 个扫描引擎，图片扫描时并行运行：

1. WeChatQRCode（仅 QR Code）
2. ZXing MultiFormatReader（17 种格式）
3. ML Kit（13 种格式）
4. BoofCV MicroQrCodeDetector（Micro QR）
5. HanXinDecoder（Han Xin Code / 汉信码）
6. CustomLinearBarcodeScanner（Pharmacode / Plessey / MSI Plessey / Telepen）

图片扫描的结果按 `text + format` 去重，保留最先识别到的引擎标签。

## 6. 文件索引

| 文件 | 说明 |
|------|------|
| `BarcodeGenerator.kt` | 条码生成器主入口（ZXing / 自定义 / BoofCV / HanXin / OkapiBarcode 路由） |
| `AdvancedBarcodeGenerator.kt` | 带样式的高级生成器（含 `FormatStyleCapabilities` 与 `sanitize`） |
| `BarcodeFormatAdapter.kt` | 生成界面格式下拉框适配器 |
| `BarcodeFormatUtils.kt` | 格式工具、本地化名称与校验辅助 |
| `BarcodeLayout.kt` | 统一布局抽象（Grid、Linear、MaxiCode、Fallback）供样式渲染器使用 |
| `StyleConfigSerialization.kt` | 样式配置 JSON 序列化/反序列化 |
| `SvgQRCodeGenerator.kt` | 全格式 SVG 导出（ZXing 路径 + bitmap 回退） |
| `QRCodeScanner.kt` | 多引擎扫描器 |
| `ScanImageProcessor.kt` | 图片/视频 Uri 扫描路由（ScanImageFragment 与系统分享入口共用） |
| `RestorationRescan.kt` | 图片扫描无结果时的修复重试编排 |
| `QRCodeRestorationManager.kt` | 修复变体生成（灰度 / 对比度 / 锐化 / 二值化 / 缩放） |
| `ScanImageProcessor.kt` | 图片/视频 Uri 扫描路由（ScanImageFragment 与系统分享入口共用） |
| `BackupCrypto.kt` | 备份加密原语（AES-256/GCM + PBKDF2，magic `QRBK1`） |
| `SecurityBlacklist.kt` | 恶意链接黑名单模型；加载顺序 filesDir 覆盖 > assets 内置 > 代码兜底 |
| `BlacklistUpdater.kt` | 黑名单在线更新（可选、静默；5s 超时 + 64KB 上限 + schema/版本校验） |
| `AppUpdateChecker.kt` | 更新元数据获取：Stable 使用 GitHub `releases/latest` 后再读取 Release 中的 `version.json`；Beta 固定读取 GitHub Pages。请求 5s 超时、1 MiB 上限，并校验初始和重定向后的受信 HTTPS 端点 |
| `UpdateDecider.kt` | 纯解析/决策层：校验 `version.json`、Stable canonical asset、可信 URL 与增量链；以 `versionCode` 为主、语义版本仅处理同 code 的并列比较 |
| `AppUpdateManager.kt` | 更新编排：Stable 自动检查默认关且 24h 节流；Beta 仅由 About 手动检查。下载到私有 `filesDir/updates`，按精确大小和 SHA-256 校验；增量失败或不安全时回退完整 APK；API 26+ 请求安装未知来源权限后继续安装 |
| `ApkArchiveVerifier.kt` | 安装前校验 APK archive 的包名、目标 `versionCode` 和签名证书集合必须与已安装应用一致 |
| `ApkPatcher.kt` / `IncrementalUpdater.kt` / `ChainPlanner.kt` | 已校验 ApkDiffPatch（`ZiPat1`，`libapkpatch.so` native）增量链：基础 APK hash 匹配、补丁总量更小且每跳 hash 校验通过才使用；否则完整下载 |
| `QuickScanTileService.kt` | 下拉快捷设置磁贴（一键进入相机扫描） |
| `baselineprofile/` | Baseline Profile 生成模块（`:app:generateReleaseBaselineProfile` 在模拟器/真机上生成 `app/src/release/generated/baselineProfiles/baseline-prof.txt`，release 构建自动合并进 R8 art profile） |
| `app/src/androidTest/` | 仪器测试（启动冒烟、MediaStore Q+、SQLCipher 真机加密、视频扫描全管线），CI `android-test` job 在 API 35 模拟器上运行 |
| `HistoryDetailFragment.kt` | 历史详情内容页；手机由 HistoryDetailActivity 薄包装承载，平板 sw600dp 双栏嵌入右侧面板 |
| `ScanRegionMapper.kt` | 框选区域视图坐标 → 帧 bitmap 像素坐标映射（FILL_CENTER + 旋转变换） |
| `decoder/BarcodeScanUtils.kt` | 自定义一维码预处理工具 |
| `decoder/CustomLinearBarcodeScanner.kt` | 自定义一维码扫描入口 |
| `decoder/PharmacodeDecoder.kt` | Pharmacode 解码器 |
| `decoder/PlesseyDecoder.kt` | Plessey 解码器 |
| `decoder/TelepenDecoder.kt` | Telepen 解码器 |
| `decoder/MicroQrCodeScanner.kt` | Micro QR 扫描器 |
| `decoder/hanxin/HanXinDecoder.kt` | Han Xin Code 扫描器 |
| `data/HistoryItem.kt` | 历史记录实体与枚举 |
| `data/HistoryRepository.kt` | 历史记录仓库 |
| `data/AppDatabase.kt` | 加密 Room 数据库（生产用 SQLCipher，Robolectric 回退到未加密） |
| `data/BarcodeFormat.kt` | 应用内条码格式枚举（含 `isScannable`） |
| `AppLockManager.kt` | 应用锁（PIN / 生物识别） |
| `GenerateFragment.kt` | 生成界面 Fragment |
| `ColorPickerView.kt` | 色谱式颜色选取自定义 View（SV 方格 + Hue 色相条 + Alpha 透明度条） |
| `ColorPickerDialog.kt` | 颜色选取对话框（含 hex / RGBA 输入） |
| `AngleDialView.kt` | 圆形角度旋钮（用于渐变角度） |
| `BatchGenerateActivity.kt` | 批量生成 Activity（CSV / Excel） |
| `ContinuousScanActivity.kt` | 连续扫描 Activity |
| `HistoryDetailActivity.kt` | 历史记录详情页 |
| `ui/result/QRResultAdapter.kt` | 多扫描结果 RecyclerView 适配器 |
| `docs/ui-testing-plan.md` | 全页面 UI/Adapter 测试补全计划 |
| `app/build.gradle` | Git 派生的 `versionCode` / `versionName` / `GIT_HASH`、`CHANGELOG.txt` 生成和 `writeVersionInfo` 元数据任务 |
| `.github/workflows/build.yml` | push/PR 验证、模拟器仪器测试、仅 master 的与 Debug 同证书 Beta 发布，以及覆盖率和 Beta 通道的 Pages 部署 |
| `.github/workflows/release.yml` | 严格 Stable 标签/`origin/master` 校验、与 Debug 同证书的 APK/AAB、`version.json`、GitHub Release 和可选增量补丁 |
| `.github/scripts/build_beta_delta_chains.py` / `build_stable_delta_chains.py` | 维护 Beta 存档或 Stable 历史的 ApkDiffPatch 单跳补丁（ZipDiff + ZipPatch 回打自验 + libapkpatch.so 守卫）；补丁源跨通道：Stable 覆盖最近 4 个已存档 Beta，Beta 覆盖最近 2 个 Stable，支持双向增量切换 |
| `docs/versioning-and-update-system.md` | Git 版本模型、Stable/Beta 发布、`version.json`、签名连续性和首轮发布操作说明 |
| `CrashLogger.kt` | 本地崩溃日志：全局未捕获异常写 filesDir/crash_logs（上限 10 份），About 页查看/分享/清除，不上报 |
| `WebDavClient.kt` / `WebDavSyncManager.kt` | WebDAV 云同步：加密备份手动上传/恢复，密码经 EncryptedSharedPreferences 落盘 |
| `NetworkUtils.kt` | 元数据 GET 的指数退避重试（默认 3 次）；大文件下载不走此路径 |
| `UpdateMirrors.kt` | GitHub 下载加速：可代理主机 URL 展开为公共镜像候选列表轮询；完整性由 SHA-256/签名校验兜底 |
| `DailyBuckets.kt` / `SimpleBarChartView.kt` | 历史页近 14 天扫码统计柱状图（自绘，无图表依赖） |
| `BatchStyleHolder.kt` | 批量生成样式（预设 + Logo）的进程内交接，BatchResultActivity 读取即清除 |
| `scanner/MlKitEngine.kt`（`src/playstore` / `src/fdroid`） | ML Kit 引擎隔离：默认构建用真实实现，`-Pfdroid` 用 stub 返回空结果 |
| `docs/fdroid-readiness.md` | F-Droid 纯源码构建（`-Pfdroid`）说明与上架剩余人工步骤 |

## 6.1 版本、发布与应用更新

- 所有 Gradle 任务都要求完整、非浅克隆 Git 历史。`versionCode = git rev-list --count HEAD`；最近匹配的 `v*` 标签必须严格符合 `vMAJOR.MINOR.PATCH`，构建的 `versionName` 为无 `v` 的基础版本或 `+N` 提交后缀。完全没有匹配标签时才使用 `0.0.0+<提交数>`；不合法的最近 `v*` 标签会直接使构建失败。
- `BuildConfig.GIT_HASH` 为 HEAD 的八位短 hash。`generateChangelog` 在每次预构建前从 `v*` 标签生成并打包 `CHANGELOG.txt`，About 页的“版本历史”读取该 asset；`writeVersionInfo` 写出 CI 使用的 `versionCode`、`versionName`、`gitHash`。
- Stable 必须推送精确 `vMAJOR.MINOR.PATCH` 标签，且标签提交必须等于当前 `origin/master`。Release 工作流先执行 debug 构建、单元测试、lint、JaCoCo 报告和覆盖率门禁，再以 Debug 基线证书发布 canonical APK/AAB、`version.json`、`app-release.apk` 兼容别名和可选补丁。
- 仅 master push 的 Beta 会等待常规 build 与 emulator `android-test` 成功，以同一 Debug 基线证书生成 APK；Pages 在 `/beta/` 下部署 Beta 元数据/APK，同时继续部署覆盖率报告。
- Stable 自动检查默认关闭，只检查 Stable；About 页有手动 Stable 和手动 Beta 按钮，Beta 没有自动检查。客户端必须通过 `version.json` 的 SHA-256、大小、APK 身份及签名校验后才请求系统安装器。

完整 schema、增量限制、archive 保留策略及 `v0.2.6` 首轮发布顺序见 [`versioning-and-update-system.md`](versioning-and-update-system.md)。

## 6.5 生成实现细节

### Data Matrix 与中文
- ASCII 内容继续走 ZXing 的 `DataMatrixWriter`，保证与现有扫描器完全回环。
- 非 ASCII 内容走 OkapiBarcode `DataMatrix` + `setEciMode(26)`（UTF-8），使中文、日文等 Unicode 文本可生成。

### OkapiBarcode 仅生成格式
- 新增格式统一由 OkapiBarcode 生成，并在 `symbolToBitmap()` 中利用 `Symbol.getTexts()` 绘制人眼可读数字。
- 部分格式存在 OkapiBarcode 0.5.6 已知问题：
  - **Code One**：自动选择版本时可能数组越界，生成器按 `S → T → A → … → H` 尝试固定版本。
  - **Grid Matrix**：纯 ASCII 内容触发数组越界，验证器要求至少包含一个非 ASCII 字符（通常为中文）。

## 7. 开发原则

- **可扫描格式**：必须能生成，且生成的图片必须能被本应用自身扫描器识别。
- **仅生成格式**：允许只生成不扫描；在 `BarcodeFormat` 上标记 `isScannable = false`，并在生成页面向用户展示提示。
- 每种可扫描新格式必须配套 roundtrip 单元测试；仅生成格式至少保证 `BarcodeGenerator.generate()` 成功的生成测试。
- 新增枚举值时需同步更新 `toHistoryType()` 映射。
- 字符串资源需同时提供全部 10 个 locale（`values` / `values-zh` / `values-de` / `values-ja` / `values-ko` / `values-fr` / `values-es` / `values-it` / `values-pt` / `values-ru`）。`MissingTranslation` / `ExtraTranslation` 为 lint error；**10 个 locale 已全部 100% 对齐**。`HardcodedText` 同为 error：布局真实文本必须走字符串资源，运行时占位文本用 `tools:text`。fastlane 元数据仅保留 en-US / zh-CN 两份，属有意留档（商店分发见第 8 节黑名单）。
- `SecurityManager` 等无 Context 单例的文案经 `init(context)` 持有的 `appContext` 解析；未 init（单元测试）回退英文。
- 生成稳定性：固定输入的 SVG 输出哈希受 `GenerationGoldenTest` 金样保护；生成逻辑或依赖升级导致图案变化时会失败，属预期变更时更新金样并在提交信息说明。
- 测试在 JUnit Platform 上运行（Vintage Engine 跑既有 JUnit 4 / Robolectric；新测试可用 Jupiter）；当前 `build.gradle` 为 Jupiter 与 Vintage 配置 `6.1.2`。
- CI 覆盖率门禁：`jacocoTestCoverageVerification`（指令 ≥ 0.80，行 ≥ 0.75，`-PexcludeExtendedUiTests` 口径）。
- Release 构建开启 R8 + shrinkResources；`app/debug.keystore` 是主分支 Debug、Beta 与 Stable 的签名基线。本地可通过 `RELEASE_KEYSTORE_FILE` / `_PASSWORD` / `_ALIAS` 环境变量或 Gradle 属性使用证书相同的 keystore；CI 可选的 `RELEASE_KEYSTORE_BASE64` / `_PASSWORD` / `_ALIAS` secrets 也会被证书比对。发布细节见 [`versioning-and-update-system.md`](versioning-and-update-system.md)。

## 8. 需求黑名单（已明确拒绝，勿再提议）

以下方向项目所有者已明确否决。任何梳理、评审或迭代计划中**不得再次提出**，也不要把它们列为"欠缺"或"待做"：

1. **Google Play 分发**：不做。本项目只走 GitHub Release（Stable）+ GitHub Pages（Beta）自更新通道。不要提议 Play 上架、Play App Signing、AAB 上 Play 吃拆分红利、Play Console 相关任何事项。
2. **apksigner / build-tools 升级**：永久钉死 **34.0.0**，这是有意为之而非技术债。v35+ 会向首个 entry 的 local header 插入 padding extra field，ZipPatch 不还原 → 增量补丁字节不一致 → 签名失效（上游 issue #96/#107）。CI 中"apksigner 钉 34.0.0 绝不能升级"的注释是正式决策，不要提议升级、不要把它列为脆弱点。
3. **per-ABI 分发（APK 按 ABI 拆分）**：不做。虽与现有增量更新架构兼容，但补丁矩阵 ×4、CI 耗时 ×1.5-2.5、存档体积 ×1.7 的代价不被接受，APK 体积优化不走 ABI 拆分路线。不要提议 `splits.abi`、按 ABI 出多套发布物、版本元数据加 ABI 维度等相关方案。
