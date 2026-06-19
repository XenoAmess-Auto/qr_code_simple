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
│   ├── HanXinEncoderTest.kt                # Han Xin Code 编码器
│   ├── HanXinRobustnessTest.kt             # Han Xin Code 鲁棒性（旋转/缩放/模糊）
│   ├── HanXinDecoderRobustnessTest.kt     # Han Xin Code 布局/反色鲁棒性
│   ├── MicroQrGenerationTest.kt            # Micro QR 容量边界
│   ├── CustomLinearGenerationTest.kt       # 自定义一维码
│   ├── Gs1DatabarGenerationTest.kt         # RSS-14 / RSS Expanded
│   ├── MaxiCodeGenerationTest.kt           # MaxiCode 各模式
│   ├── UpcEanExtensionGenerationTest.kt    # UPC/EAN 附加码
│   └── BarcodeValidationTest.kt            # 校验规则
├── decoder/
│   ├── BarcodeScanUtilsLogicTest.kt
│   ├── CustomLinearDecoderLogicTest.kt
│   └── hanxin/
│       ├── HanXinDecoderInternalTest.kt  # Han Xin Code 解码器内部测试
│       └── HanXinDecoderExternalTest.kt  # 外部参考样本（Zint 生成）解码测试
├── BarcodeGeneratorTest.kt
├── BarcodeFormatMappingTest.kt
└── ...
```

外部样本存放在 `app/src/test/resources/hanxin/`，由 `expected-results.txt` 索引。
当前包含：
- Zint 2.15.0 生成的汉信码参考图（`zint_*.png`），用于验证编码器/解码器与
  独立工具的字节级一致性。
- 历史遗留样本；非汉信码图片标记为 `FAIL`。

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

### 5.4 Han Xin Code 鲁棒性测试

`HanXinRobustnessTest.kt` 和 `HanXinDecoderRobustnessTest.kt` 对生成的汉信码施加以下扰动后仍应解码成功：
- 缩放（0.5x / 1.5x / 0.75x）
- 90° / 180° / 270° 旋转
- 高斯随机噪声
- 均值模糊
- 白边填充、非居中放置、非正方形画布
- 反色（深色背景上的浅色码）
- 轻微的非等比缩放
- 中等程度的透视形变
- 少量随机椒盐噪声（已启用 RS 纠错）

> 注：汉信码已启用功能信息和数据区的 Reed-Solomon 纠错；强椒盐噪声或局部遮挡等超过 RS 纠错能力的扰动暂不在测试范围内。
> 编码器默认行为与 Zint 2.15.0 对齐：GB18030 可编码内容不写入 ECI 头，
> Reed-Solomon 使用 LFSR 编码并将 ECC 逆序输出，解码器按对应的互反根校验。

## 6. 运行测试

```bash
./gradlew :app:testDebugUnitTest
```

## 7. 注意事项

- ML Kit 在 Robolectric 环境下可能无法初始化，因此 roundtrip 测试主要依赖 ZXing、BoofCV 和自定义解码器。
- 对于仅 ZXing 能扫描的格式（RSS、MaxiCode），确保生成图像质量足够高。
- 自定义一维码需预留足够 quiet zone，避免解码失败。
