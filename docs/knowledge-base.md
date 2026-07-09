# QR Code Simple - 项目知识库

## 1. 项目概述

QR Code Simple 是一款 Android 二维码/条码扫描与生成应用。
- 包名：`com.xenoamess.qrcodesimple`
- 当前版本：`0.1.7`
- 目标：支持超过 50 种条码格式的生成，其中可扫描的格式会继续保证生成与扫描回环。

## 2. 技术栈

| 组件 | 技术/库 | 版本 |
|------|---------|------|
| 语言 | Kotlin | 2.2.10 |
| UI | Jetpack Compose / XML Layout | - |
| 相机 | CameraX | 1.3.3 |
| 数据库 | Room + SQLCipher | 2.7.1 / 4.5.4 |
| 二维码识别 | ZXing | 3.5.3 |
| 条码识别 | ML Kit | 17.2.0 |
| 二维码识别 | WeChatQRCode | 2.5.0 |
| Micro QR | BoofCV | 1.4.0 |
| 复杂格式生成 | OkapiBarcode | 0.5.6 |
| 测试 | JUnit + Robolectric | 4.13.2 / 4.16.1 |

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

> 说明： historically doc/barcode-formats.md claimed the last group was scan-only. That is now outdated; generation support was added for all OkapiBarcode formats, and the old 22 scannable formats remain fully roundtrippable.

## 4. 核心约定

### 命名
- 应用内条码格式枚举：`com.xenoamess.qrcodesimple.data.BarcodeFormat`
- ZXing 条码格式：`com.google.zxing.BarcodeFormat`
- 历史类型：`com.xenoamess.qrcodesimple.data.HistoryType`

### 生成入口

所有条码生成统一通过 `BarcodeGenerator.generate(content, config)`。
样式化生成走 `AdvancedBarcodeGenerator.generateStyled(content, format, size, style)`：

- 生成器本身不清洗 `StyleConfig`，传入什么就用什么；调用方（`GenerateFragment`、历史页面）在生成前调用 `AdvancedBarcodeGenerator.sanitize(style, format)` 清洗。
- `GenerateFragment` 根据当前格式能力表隐藏不支持的控件，不做提示。
- 各格式的实际样式能力见 [`docs/style-roundtrip-matrix.md`](style-roundtrip-matrix.md)，其中包含 `moduleShape` / `moduleFillRatio` / `positionPatternShape` 对所有可扫描格式的真实回扫通过率。

`StyleConfig` 字段及能力表：
- `foregroundColor` / `backgroundColor`：所有格式。
- `cornerRadius`（0~1）：所有格式开放。
- `logoScale` / `logoBitmap`：所有格式。
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

### 历史记录
- `HistoryRepository.insertGenerate(content, type, barcodeFormat, styleJson)` 保存生成记录。
- `HistoryItem.barcodeFormat` 字段保存格式名称字符串；`HistoryItem.styleJson` 字段保存生成样式参数 JSON（不含图片）。
- 按 `content` + `isGenerated` 去重：扫描记录和生成记录各自独立。同一文本扫描重复时更新 `timestamp` 置顶；生成重复时更新最新参数/格式/时间/样式；不新增多条。备份导入时同样按此规则合并，避免重复记录。
- 生成、保存、分享按钮均会触发历史记录写入/更新。
- 历史列表的二维码分享使用原始 `barcodeFormat` 和 `styleJson` 重新生成图片，保持与生成时一致。
- 历史详情页提供“自定义样式生成”按钮，可将文本带入 `GenerateFragment` 重新选择样式。

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
| `SvgQRCodeGenerator.kt` | 全格式 SVG 导出（ZXing 路径 + bitmap 回退） |
| `QRCodeScanner.kt` | 多引擎扫描器 |
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
| `BarcodeLayout.kt` | 统一布局抽象（Grid、Linear、MaxiCode、Fallback）供样式渲染器使用 |
| `ColorPickerView.kt` | 色谱式颜色选取自定义 View（SV 方格 + Hue 色相条） |
| `ColorPickerDialog.kt` | 颜色选取对话框（含 hex 输入） |
| `BatchGenerateActivity.kt` | 批量生成 Activity（CSV / Excel） |
| `ContinuousScanActivity.kt` | 连续扫描 Activity |
| `HistoryDetailActivity.kt` | 历史记录详情页 |
| `docs/ui-testing-plan.md` | 全页面 UI/Adapter 测试补全计划 |
| `.github/workflows/build.yml` | CI 工作流（build + unit tests） |

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
- 字符串资源需同时提供英文（`values/strings.xml`）和中文（`values-zh/strings.xml`）。
