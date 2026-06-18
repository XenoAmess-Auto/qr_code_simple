# QR Code Simple - 测试策略

## 1. 测试目标

- 所有 22 种条码格式都能成功生成。
- 生成的条码能被本项目自身扫描器准确识别。
- 非法输入能被正确校验拒绝。

## 2. 测试框架

- **JUnit 4**：基础单元测试。
- **Robolectric 4.16.1**：在 JVM 上模拟 Android `Bitmap`。
- **Kotlin test**：辅助断言。

## 3. Roundtrip 测试模式

每个 roundtrip 测试遵循以下模式：

```kotlin
@Test
fun testGenerateAndScanQRCode() {
    val content = "https://example.com"
    val config = BarcodeGenerator.BarcodeConfig(
        format = BarcodeFormat.QR_CODE,
        width = 600,
        height = 600
    )
    val bitmap = BarcodeGenerator.generate(content, config)
    assertNotNull(bitmap)

    val results = QRCodeScanner.scanSync(context, bitmap!!)
    assertTrue(results.isNotEmpty())
    assertEquals(content, results.first().text)
}
```

## 4. 测试目录结构

```
app/src/test/java/com/xenoamess/qrcodesimple/
├── generator/
│   ├── BarcodeGenerationRoundtripTest.kt   # 全部 22 种格式 roundtrip
│   ├── MicroQrGenerationTest.kt            # Micro QR 容量边界
│   ├── CustomLinearGenerationTest.kt       # 自定义一维码
│   ├── Gs1DatabarGenerationTest.kt         # RSS-14 / RSS Expanded
│   ├── MaxiCodeGenerationTest.kt           # MaxiCode 各模式
│   ├── UpcEanExtensionGenerationTest.kt    # UPC/EAN 附加码
│   └── BarcodeValidationTest.kt            # 校验规则
├── decoder/
│   ├── BarcodeScanUtilsLogicTest.kt
│   └── CustomLinearDecoderLogicTest.kt
├── BarcodeGeneratorTest.kt
├── BarcodeFormatMappingTest.kt
└── ...
```

## 5. 测试内容

### 5.1 Roundtrip 测试

对每种格式至少测试：
- 最短合法内容
- 典型内容
- 最长合法内容（如适用）
- 生成图像非空
- 扫描结果内容一致
- 扫描结果格式正确

### 5.2 自定义一维码测试

除 roundtrip 外，还需：
- 用对应解码器直接解码生成图像。
- 测试不同校验位配置（MSI Plessey）。
- 测试非法字符/长度被拒绝。

### 5.3 校验测试

对每种格式测试非法输入：
- 空内容
- 非法字符
- 长度不足/超长
- 超出数值范围（Pharmacode）

## 6. 运行测试

```bash
./gradlew :app:testDebugUnitTest
```

## 7. 注意事项

- ML Kit 在 Robolectric 环境下可能无法初始化，因此 roundtrip 测试主要依赖 ZXing、BoofCV 和自定义解码器。
- 对于仅 ZXing 能扫描的格式（RSS、MaxiCode），确保生成图像质量足够高。
- 自定义一维码需预留足够 quiet zone，避免解码失败。
