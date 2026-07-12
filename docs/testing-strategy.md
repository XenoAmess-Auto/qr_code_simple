# QR Code Simple - 测试策略

## 1. 测试目标

- 所有可扫描条码格式都能成功生成，且生成的条码能被本项目自身扫描器准确识别。
- 所有仅生成条码格式都能成功生成（不强制扫描回环）。
- 非法输入能被正确校验拒绝。

## 2. 测试框架

- **JUnit 4**：基础单元测试。
- **Robolectric 4.16.1**：在 JVM 上模拟 Android `Bitmap`。
- **Kotlin test**：辅助断言。

## 3. Roundtrip 与生成测试模式

可扫描格式使用 roundtrip 模式：

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

仅生成格式（`isScannable = false`）只验证生成成功：

```kotlin
@Test
fun testGenerateOnlyFormat() {
    val bitmap = BarcodeGenerator.generate(content, config)
    assertNotNull(bitmap)
}
```

## 4. 测试目录结构

```
app/src/test/java/com/xenoamess/qrcodesimple/
├── generator/
│   ├── BarcodeGenerationRoundtripTest.kt     # 可扫描格式 roundtrip + 仅生成格式生成测试
│   ├── BarcodeFormatTestFixtures.kt          # 每种格式的合法测试内容
│   ├── AdvancedBarcodeGeneratorTest.kt       # 样式化生成与 roundtrip
│   ├── HanXinEncoderTest.kt                  # Han Xin Code 编码器
│   ├── HanXinRobustnessTest.kt               # Han Xin Code 鲁棒性（旋转/缩放/模糊）
│   ├── HanXinDecoderRobustnessTest.kt        # Han Xin Code 布局/反色鲁棒性
│   ├── MicroQrGenerationTest.kt              # Micro QR 容量边界
│   ├── CustomLinearGenerationTest.kt           # 自定义一维码
│   ├── Gs1DatabarGenerationTest.kt             # RSS-14 / RSS Expanded
│   ├── MaxiCodeGenerationTest.kt               # MaxiCode 各模式
│   ├── UpcEanExtensionGenerationTest.kt        # UPC/EAN 附加码
│   ├── BarcodeValidationTest.kt                # 校验规则
│   └── SvgBarcodeGenerationTest.kt             # SVG 全格式导出
├── decoder/
│   ├── BarcodeScanUtilsLogicTest.kt
│   ├── CustomLinearDecoderLogicTest.kt
│   └── hanxin/
│       ├── HanXinDecoderInternalTest.kt        # Han Xin Code 解码器内部测试
│       └── HanXinDecoderExternalTest.kt        # 外部参考样本（Zint 生成）解码测试
├── AppLockManagerTest.kt
├── HistoryBackupManagerTest.kt
├── TagManagerTest.kt
├── BarcodeGeneratorTest.kt
├── BarcodeFormatMappingTest.kt
├── ContentParserTest.kt
├── SecurityManagerTest.kt
└── ...
```

外部样本存放在 `app/src/test/resources/hanxin/`，由 `expected-results.txt` 索引。
当前包含：
- Zint 2.15.0 生成的汉信码参考图（`zint_*.png`），用于验证编码器/解码器与
  独立工具的字节级一致性。
- 历史遗留样本；非汉信码图片标记为 `FAIL`。

## 5. 测试内容

### 5.1 Roundtrip 与生成测试

- **可扫描格式**：对每种格式至少测试最短合法内容、典型内容、最长合法内容（如适用）；验证生成图像非空、扫描结果内容一致、格式正确。
- **仅生成格式**：对每种格式至少测试一种合法内容，验证 `BarcodeGenerator.generate()` 返回非空 `Bitmap`。
- 共享测试内容集中在 `BarcodeFormatTestFixtures.kt`，便于统一维护。

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

## 6. UI 与 Adapter 测试

所有用户可见的 UI 页面、Fragment、Activity、Adapter 和自定义 View 均已通过 Robolectric + Espresso 进行交互测试。全量计划见 `docs/ui-testing-plan.md`，当前已全部完成，整体测试套件约 **470 个测试**，0 失败。

重点覆盖：

- 下拉框与筛选：编辑输入、过滤、选择、非法输入回退。
- 列表与 RecyclerView：item 绑定、空状态、多选、删除、复制、分享。
- 搜索与标签：搜索文本变化、筛选 chip、tag chip 点击过滤。
- 对话框与设置：确认/取消、开关状态、外部链接 intent。
- 自定义 View：触摸事件、颜色/角度变化、回调。
- 导航：tab 切换、ViewPager2 联动、deep-link/shortcut。

所有页面和测试批次的具体计划见 `docs/ui-testing-plan.md`。

## 7. 运行测试

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

当前 `./gradlew :app:lintDebug` 已通过（0 error，0 warning）。历史遗留的 `MissingTranslation` / `ExtraTranslation` 等大量风格/质量/翻译债务已统一在 `app/lint.xml` 中忽略，核心 API 兼容性问题仍保持 error 级别。

CI 在 `.github/workflows/build.yml` 中配置，每次 push/PR 都会执行 `assembleDebug` 和 `testDebugUnitTest`。

## 8. CI 排查辅助

- `app/build.gradle` 已开启 `testLogging.showStandardStreams = true` 并设置 `robolectric.logging=stdout`，让 `android.util.Log` 输出进入 CI 日志。
- `QRCodeScanner` 内部使用 `Log.d` 记录每个引擎的启动、结束、耗时和总体超时事件，便于在 CI 超时事故中定位是哪个引擎或哪条测试挂起。
- `scanSync` 现在使用 `runBlocking()`（不带 dispatcher）在调用方线程上执行扫描，不再向 `Dispatchers.Default` 请求线程。因此即使其他测试或第三方库占满 `Dispatchers.Default`，扫描流程也不会在入口处死锁。
- 如果未来再次出现 CI 挂死，优先查看最后一条 `START TEST` 以及该测试的 `D/QRCodeScanner` 日志，确认是否有引擎只有 `Engine start` 没有 `Engine end`；同时确认 `scanSync` 是否已打印 `Starting scanAsFlow`（未打印说明卡在进入 `scanSync` 之前）。

## 9. 注意事项

- ML Kit 在 Robolectric 环境下可能无法初始化，因此 roundtrip 测试主要依赖 ZXing、BoofCV 和自定义解码器。
- 对于仅 ZXing 能扫描的格式（RSS、MaxiCode），确保生成图像质量足够高。
- 自定义一维码需预留足够 quiet zone，避免解码失败。
- `AppDatabase` 在 Robolectric 测试中会回退到未加密数据库，因为 SQLCipher 原生库在 JVM 单元测试中不可用。
- 部分 OkapiBarcode 生成的格式（如 Code One、Grid Matrix、各类邮政码）存在编码器限制或已知 bug，测试内容需使用合法样例，详见 `BarcodeFormatTestFixtures.kt`。
- 覆盖率由 JaCoCo 生成（`./gradlew :app:jacocoTestReport`）。`app/build.gradle` 关闭 AGP 内置覆盖率，改用 Gradle JaCoCo 插件并开启 `includeNoLocationClasses = true`，使 Robolectric 加载的类也能被计入；同时排除 `jdk.internal.reflect.*` 避免 Gradle worker 序列化异常。
