# QR Code Simple - 扫描架构

## 1. 扫描入口

`QRCodeScanner` 提供两个入口：

```kotlin
object QRCodeScanner {
    suspend fun scan(context: Context, bitmap: Bitmap): List<ScanResult>
    fun scanSync(context: Context, bitmap: Bitmap): List<ScanResult>
}
```

## 2. 扫描引擎优先级

扫描按以下顺序尝试，任一引擎返回结果即停止：

1. **WeChatQRCode**：仅 QR Code，对低质量/扭曲二维码识别率高。
2. **ZXing MultiFormatReader**：覆盖最广，支持 17 种格式。
3. **ML Kit**：设备端推理速度最快，支持 13 种格式。
4. **BoofCV MicroQrCodeDetector**：Micro QR Code。
5. **HanXinDecoder**：Han Xin Code / 汉信码。
6. **CustomLinearBarcodeScanner**：Pharmacode、Plessey、MSI Plessey、Telepen。

## 3. 各引擎输入

| 引擎 | 输入 | 预处理 |
|------|------|--------|
| WeChatQRCode | `Bitmap` | 内部转换 |
| ZXing | `Bitmap.getPixels()` → `RGBLuminanceSource` → `HybridBinarizer` / `GlobalHistogramBinarizer` | 灰度化、二值化 |
| ML Kit | `InputImage.fromBitmap(bitmap, 0)` | 内部处理 |
| BoofCV | `ConvertBitmap.bitmapToGray()` → `GrayU8` | 灰度化 |
| HanXinDecoder | `Bitmap.getPixel()` → 灰度 → 二值化 | 内部二值化与网格采样 |
| Custom Linear | `BarcodeScanUtils.extractBars(bitmap)` | 中间行灰度阈值 |

## 4. 自定义一维码预处理

`BarcodeScanUtils` 提供三个工具函数：

```kotlin
fun extractBars(bitmap: Bitmap): List<Boolean>      // 中间行二值化
fun groupBars(bars: List<Boolean>): List<Pair<Boolean, Int>>  // 游程编码
fun normalizeWidths(groups: List<Pair<Boolean, Int>>): List<Pair<Boolean, Int>>  // 归一化
```

- `extractBars` 读取 Bitmap 水平中线像素。
- 灰度化公式：`0.299R + 0.587G + 0.114B`。
- 以行平均灰度为阈值：低于平均为 bar（true），高于为 space（false）。
- `normalizeWidths` 将宽度除以最小宽度，得到相对模块宽度。

## 5. 自定义解码器

### PharmacodeDecoder
- 奇数个 group，首尾为 bar。
- 每对 bar/space 宽度为 1 或 2 模块。
- 从右向左解析为四进制数。
- 有效范围：3..131070。

### PlesseyDecoder
- 至少 20 个 group。
- 每 4 位解析为一个十六进制字符，跳过第 5 位分隔符。
- 返回长度 ≥4 的十六进制字符串。

### MsiPlesseyDecoder（新增）
- 识别 `21` 起始符和 `121` 停止符。
- 每位数字为 8 模块模式。
- 支持 mod-10 / mod-11 校验位。

### TelepenDecoder
- 偶数个 group，首尾为 bar。
- 每 8 个交替 bar/space 组成一个字符。
- 每个字符总宽度为 8 模块。
- bar=1，space=0，转换为字符。

## 6. 扫描结果映射

`BarcodeFormat.toHistoryType()` 将 ZXing/应用格式映射为 `HistoryType`：
- QR_CODE → QR_CODE
- DATA_MATRIX → DATA_MATRIX
- AZTEC → AZTEC
- PDF417 → PDF417
- RSS_14 → RSS_14
- RSS_EXPANDED → RSS_EXPANDED
- MAXICODE → MAXICODE
- MICRO_QR → MICRO_QR
- UPC_EAN_EXTENSION → UPC_EAN_EXTENSION
- HAN_XIN → HAN_XIN
- PHARMACODE → PHARMACODE
- PLESSEY → PLESSEY
- MSI_PLESSEY → MSI_PLESSEY
- TELEPEN → TELEPEN
- 其他一维码 → BARCODE

## 7. 测试策略

单元测试通过 `QRCodeScanner.scanSync(context, bitmap)` 对生成的 Bitmap 进行 roundtrip 验证。
