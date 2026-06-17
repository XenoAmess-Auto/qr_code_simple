# QR Code Simple - 项目知识库

## 1. 项目概述

QR Code Simple 是一款 Android 二维码/条码扫描与生成应用。
- 包名：`com.xenoamess.qrcodesimple`
- 当前版本：`0.1.6`
- 目标：支持 21 种条码格式的扫描与生成。

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

## 3. 核心约定

### 命名
- 应用内条码格式枚举：`com.xenoamess.qrcodesimple.data.BarcodeFormat`
- ZXing 条码格式：`com.google.zxing.BarcodeFormat`
- 历史类型：`com.xenoamess.qrcodesimple.data.HistoryType`

### 生成入口
所有条码生成统一通过 `BarcodeGenerator.generate(content, config)`。

### 扫描入口
所有条码扫描统一通过 `QRCodeScanner.scan(context, bitmap)` 或 `QRCodeScanner.scanSync(context, bitmap)`。

### 历史记录
- `HistoryRepository.insertGenerate(content, type, barcodeFormat)` 保存生成记录。
- `HistoryItem.barcodeFormat` 字段保存格式名称字符串。

## 4. 扫描引擎优先级

1. WeChatQRCode（仅 QR Code）
2. ZXing MultiFormatReader（17 种格式）
3. ML Kit（13 种格式）
4. BoofCV MicroQrCodeDetector（Micro QR）
5. CustomLinearBarcodeScanner（Pharmacode / Plessey / MSI Plessey / Telepen）

## 5. 文件索引

| 文件 | 说明 |
|------|------|
| `BarcodeGenerator.kt` | 条码生成器主入口 |
| `AdvancedBarcodeGenerator.kt` | 带样式的高级生成器 |
| `QRCodeScanner.kt` | 多引擎扫描器 |
| `decoder/BarcodeScanUtils.kt` | 自定义一维码预处理工具 |
| `decoder/CustomLinearBarcodeScanner.kt` | 自定义一维码扫描入口 |
| `decoder/PharmacodeDecoder.kt` | Pharmacode 解码器 |
| `decoder/PlesseyDecoder.kt` | Plessey 解码器 |
| `decoder/TelepenDecoder.kt` | Telepen 解码器 |
| `decoder/MicroQrCodeScanner.kt` | Micro QR 扫描器 |
| `data/HistoryItem.kt` | 历史记录实体与枚举 |
| `data/HistoryRepository.kt` | 历史记录仓库 |
| `GenerateFragment.kt` | 生成界面 Fragment |
| `BatchGenerateActivity.kt` | 批量生成 Activity |

## 6. 开发原则

- 能扫描的格式必须能生成。
- 能生成的格式必须能被本应用自身扫描器识别。
- 每种新格式必须配套 roundtrip 单元测试。
- 新增枚举值时需同步更新 `toHistoryType()` 映射。
- 字符串资源需同时提供英文（`values/strings.xml`）和中文（`values-zh/strings.xml`）。
