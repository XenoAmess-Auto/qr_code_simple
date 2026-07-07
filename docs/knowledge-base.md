# QR Code Simple - 项目知识库

## 1. 项目概述

QR Code Simple 是一款 Android 二维码/条码扫描与生成应用。
- 包名：`com.xenoamess.qrcodesimple`
- 当前版本：`0.1.7`
- 目标：支持 22 种条码格式的扫描与生成。

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

项目当前支持 **22 种条码格式** 的扫描与生成。详见 `README.md` 和 `README_CN.md` 中的格式表格。

| 类别 | 扫描支持 | 生成支持 |
|------|:------:|:------:|
| QR Code / Data Matrix / Aztec / PDF417 / Han Xin Code | ✅ | ✅ |
| Code 128 / Code 39 / Code 93 / EAN-13 / EAN-8 / UPC-A / UPC-E / Codabar / ITF | ✅ | ✅ |
| UPC/EAN Extension / RSS-14 / RSS Expanded / MaxiCode / Micro QR / Pharmacode / Plessey / MSI Plessey / Telepen | ✅ | ✅ |

> 说明： historically doc/barcode-formats.md claimed the last group was scan-only. That is now outdated; generation support was added and verified via roundtrip tests.

## 4. 核心约定

### 命名
- 应用内条码格式枚举：`com.xenoamess.qrcodesimple.data.BarcodeFormat`
- ZXing 条码格式：`com.google.zxing.BarcodeFormat`
- 历史类型：`com.xenoamess.qrcodesimple.data.HistoryType`

### 生成入口
所有条码生成统一通过 `BarcodeGenerator.generate(content, config)`。

### 扫描入口

- 实时扫描（相机/视频）：`QRCodeScanner.scan(context, bitmap)` 或 `QRCodeScanner.scanSync(context, bitmap)`，内部并行执行 6 个引擎，等待全部结束后返回完整结果列表。
- 图片扫描：`QRCodeScanner.scanAsFlow(context, bitmap, config)`，6 个引擎并行执行，任一引擎识别到结果即通过 `Flow` 分批 emit；ResultActivity 收集到首个结果即展示页面，后续结果动态追加。图片扫描使用 `IMAGE_SCAN_CONFIG`（总超时 120s / 单引擎 60s），实时扫描使用 `CAMERA_SCAN_CONFIG`（总超时 15s / 单引擎 5s）。

### 历史记录
- `HistoryRepository.insertGenerate(content, type, barcodeFormat)` 保存生成记录。
- `HistoryItem.barcodeFormat` 字段保存格式名称字符串。

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
| `BarcodeGenerator.kt` | 条码生成器主入口 |
| `AdvancedBarcodeGenerator.kt` | 带样式的高级生成器 |
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
| `AppLockManager.kt` | 应用锁（PIN / 生物识别） |
| `GenerateFragment.kt` | 生成界面 Fragment |
| `BatchGenerateActivity.kt` | 批量生成 Activity（CSV / Excel） |
| `ContinuousScanActivity.kt` | 连续扫描 Activity |
| `HistoryDetailActivity.kt` | 历史记录详情页 |
| `.github/workflows/build.yml` | CI 工作流（build + unit tests） |

## 7. 开发原则

- 能扫描的格式必须能生成。
- 能生成的格式必须能被本应用自身扫描器识别。
- 每种新格式必须配套 roundtrip 单元测试。
- 新增枚举值时需同步更新 `toHistoryType()` 映射。
- 字符串资源需同时提供英文（`values/strings.xml`）和中文（`values-zh/strings.xml`）。
