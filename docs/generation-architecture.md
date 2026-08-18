# QR Code Simple - 生成架构

## 1. 生成入口

所有条码生成统一通过 `BarcodeGenerator` 对象：

```kotlin
object BarcodeGenerator {
    data class BarcodeConfig(
        val format: AppBarcodeFormat = AppBarcodeFormat.QR_CODE,
        val width: Int = 600,
        val height: Int = 600,
        val foregroundColor: Int = Color.BLACK,
        val backgroundColor: Int = Color.WHITE
    )

    fun generate(content: String, config: BarcodeConfig): Bitmap?
    fun validateContent(content: String, format: AppBarcodeFormat): ValidationResult
}
```

## 2. 生成流程

1. 调用方传入内容和 `BarcodeConfig`。
2. `generate()` 根据 `config.format` 路由到具体生成函数。
3. 生成失败时返回 `null`，不会抛异常。
4. 一维码生成后会附加人工可读文本。

### 2.1 生成页状态与导出

- `GenerateFragment` 把完整的内容、格式和样式封装为不可变 `GenerateRequest`。
- `GenerateViewModel` 对预览请求做 180ms 防抖，取消旧请求，并统一拥有和回收预览 Bitmap；配置变更后会用最新请求恢复编辑器和预览。
- 预览状态仅在 View 生命周期达到 `STARTED` 时收集；生成后回扫直接调用挂起版 `QRCodeScanner.scan`，取消页面会同步取消校验。
- 位图保存与分享按单任务串行执行，检查 `Bitmap.compress()` 结果并在完成、失败或取消时回收临时 Bitmap。批量结果则把全尺寸 PNG 落到 cache，仅在列表保留缩略图。

## 3. 当前实现方式

| 格式 | 实现方式 |
|------|----------|
| QR Code | ZXing `MultiFormatWriter` |
| Data Matrix | ZXing `MultiFormatWriter` |
| Aztec | ZXing `MultiFormatWriter` |
| PDF417 | ZXing `MultiFormatWriter` |
| Code 128/39/93 | ZXing `MultiFormatWriter` |
| EAN-13/8 | ZXing `MultiFormatWriter` |
| UPC-A/E | ZXing `MultiFormatWriter` |
| Codabar | ZXing `MultiFormatWriter` |
| ITF | ZXing `MultiFormatWriter` |
| Micro QR Code | BoofCV `MicroQrCodeEncoder` |
| Pharmacode | 自定义编码 + 绘制 |
| UPC/EAN Extension | 自定义 EAN-2/EAN-5 编码 + 绘制 |
| Telepen | 自定义编码 + 绘制 |
| Plessey | 自定义编码 + 绘制 |
| MSI Plessey | 自定义编码 + 绘制 |
| RSS-14 | OkapiBarcode |
| RSS Expanded | OkapiBarcode |
| MaxiCode | OkapiBarcode |

## 4. 渲染工具

- `createBitmap(bitMatrix, config)`：将 ZXing `BitMatrix` 转为 Android `Bitmap`。
- `createLinearBarcodeBitmap(bitMatrix, config, content)`：将一维码 `BitMatrix` 转为带文本的 `Bitmap`。
- `createGenericLinearBitmap(bars, config, content)`：用于自定义一维编码，直接根据 bar/space 宽度列表绘制。

## 5. 校验规则

`validateContent()` 针对不同格式校验：
- EAN-13：13 位数字
- EAN-8：8 位数字
- UPC-A：12 位数字
- UPC-E：6 或 8 位数字
- Code 39：数字、大写字母和 `- . $ / + %` 空格
- RSS-14：最多 13 位数字
- Micro QR：内容长度不超过版本容量
- Pharmacode：3..131070 的整数
- Telepen：ASCII 字符
- Plessey：十六进制字符
- MSI Plessey：数字

## 6. 扩展指南

新增生成格式时：
1. 在 `data.BarcodeFormat` 枚举中添加新值。
2. 在 `BarcodeGenerator.generate()` 中增加分支。
3. 实现具体生成函数。
4. 在 `validateContent()` 中添加校验。
5. 在 `QRCodeScanner.toHistoryType()` 中更新映射。
6. 添加单元测试验证生成 → 扫描 roundtrip。
