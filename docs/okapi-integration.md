# QR Code Simple - OkapiBarcode 集成指南

## 1. 引入依赖

在 `app/build.gradle` 中添加：

```gradle
implementation 'uk.org.okapibarcode:okapibarcode:0.5.6'
```

## 2. 负责格式

OkapiBarcode 负责以下复杂格式的生成：
- RSS-14 / GS1 DataBar
- RSS Expanded / GS1 DataBar Expanded
- MaxiCode

## 3. 使用方式

### 3.1 生成符号

```kotlin
import uk.org.okapibarcode.backend.*

val symbol: Symbol = when (format) {
    AppBarcodeFormat.RSS_14 -> DataBar14()
    AppBarcodeFormat.RSS_EXPANDED -> DataBarExpanded()
    AppBarcodeFormat.MAXICODE -> MaxiCode()
    else -> throw IllegalArgumentException()
}

symbol.content = content
val okapiBitmap = symbol.buffer  // Okapi 内部位图
```

### 3.2 转换为 Android Bitmap

```kotlin
fun symbolToBitmap(symbol: Symbol, foreground: Int, background: Int): Bitmap {
    val width = symbol.width
    val height = symbol.height
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    for (y in 0 until height) {
        for (x in 0 until width) {
            val color = if (symbol.getModule(x, y) == 1) foreground else background
            bitmap.setPixel(x, y, color)
        }
    }
    return bitmap
}
```

## 4. 各格式特殊说明

### 4.1 RSS-14 / GS1 DataBar

- 输入：最多 13 位数字（不含前导 `(01)` 和 GTIN 校验位）。
- Okapi `DataBar14` 会自动处理 GTIN 校验。
- 支持 truncated、stacked、stacked omnidirectional 等变体，默认即可。

### 4.2 RSS Expanded

- 输入：GS1 数据，可包含多个 Application Identifiers。
- 示例：`(01)12345678901231(10)ABC123`
- Okapi `DataBarExpanded` 会自动选择编码方法。

### 4.3 MaxiCode

- 模式 2/3 需要 15 字符主字符串：
  - 邮编（9 位数字或 6 字母数字 + 空格）
  - 3 位国家码
  - 3 位服务码
- 模式 4/5/6 用于标准/增强/读者编程。
- 符号大小固定：30 x 33 六边形网格。

## 5. 校验与错误处理

- OkapiBarcode 在设置非法内容时会抛出 `OkapiException`。
- 应在调用前用 `BarcodeGenerator.validateContent()` 做基础校验。
- 捕获异常后返回 `null`，上层显示生成失败。

## 6. 测试

生成后使用 `QRCodeScanner.scanSync(context, bitmap)` 进行 roundtrip 验证。
- RSS-14 / RSS Expanded 由 ZXing 扫描。
- MaxiCode 由 ZXing 扫描。
