# QR Code Simple - 测试策略

## 1. 测试目标

- 所有可扫描条码格式都能成功生成，且生成的条码能被本项目自身扫描器准确识别。
- 所有仅生成条码格式都能成功生成（不强制扫描回环）。
- 非法输入能被正确校验拒绝。

## 2. 测试框架

- **JUnit Platform（Jupiter / Vintage 6.1.2）**：`useJUnitPlatform()`；既有 JUnit 4 测试经 **Vintage Engine** 运行，新测试可用 Jupiter 注解。版本以 `app/build.gradle` 为准。
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
├── AppUpdateCheckerTest.kt              # Stable/Beta 元数据端点与必填字段
├── AppUpdateManagerTest.kt              # 默认关闭、通道选择、下载大小/SHA 校验
├── UpdateDeciderTest.kt                 # Release/manifest 解析、可信端点、升级链
├── ApkArchiveVerifierTest.kt            # 包名、versionCode、签名证书集合匹配
├── ChainPlannerTest.kt                  # 增量与完整 APK 的安全选择
├── IncrementalUpdaterTest.kt            # 多跳补丁、临时文件清理、64 MiB 防线
├── AboutFragmentUiTest.kt               # Stable/Beta 手动入口与打包 changelog
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

### 5.5 版本、发布与更新测试

- `UpdateDeciderTest` 断言 `version.json` 必须具有正整数 `versionCode`、语义 `versionName`、64 位十六进制 `apkSha256` 和正数 `apkSize`；不合格 metadata 不会被视为“已是最新”。
- Stable 测试要求 GitHub Release 的 `version.json`、标签和 `qr-code-simple-<version>.apk` 一致；只有不存在 canonical APK 候选时才允许旧 `app-release.apk` 兼容别名。
- Beta 测试固定使用 Pages 的 `/beta/version.json` 和 `/beta/qr-code-simple-beta.apk`，并确认 Beta 只能手动检查。
- 下载测试确认精确大小、SHA-256、HTTPS 可信端点和临时文件清理；`ApkArchiveVerifierTest` 覆盖 APK 包名、目标版本号和签名证书集合验证。
- `ChainPlannerTest` 与 `IncrementalUpdaterTest` 覆盖 hash 匹配、补丁更小、64 MiB 输入上限、多跳结果 hash 和失败后完整 APK 回退所依赖的安全条件。

## 6. UI 与 Adapter 测试

所有用户可见的 UI 页面、Fragment、Activity、Adapter 和自定义 View 均已通过 Robolectric + Espresso 进行交互测试。全量计划见 `docs/ui-testing-plan.md`，当前已全部完成，整体测试套件约 **552 个测试**，0 失败。

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
# 所有 Gradle 任务都会推导 Git 版本元数据。请在完整、非浅克隆中运行；
# 浅克隆必须先执行：git fetch --unshallow --tags

# 常规 JVM/Robolectric 测试套件
./gradlew :app:testDebugUnitTest

# 更新 metadata/传输的定向测试
./gradlew :app:testDebugUnitTest --tests "*UpdateDeciderTest*"
./gradlew :app:testDebugUnitTest --tests "*AppUpdateCheckerTest*"

# 静态检查、覆盖率报告与门禁
./gradlew :app:lintDebug
./gradlew :app:jacocoTestReport :app:jacocoTestCoverageVerification -PexcludeExtendedUiTests

# 需要已连接的模拟器/设备；CI 最多重试三次
./gradlew :app:connectedDebugAndroidTest

# build 与 Stable 发布工作流使用的同一 JVM 验证命令
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:jacocoTestReport :app:jacocoTestCoverageVerification -PexcludeExtendedUiTests
```

`lintDebug` 与覆盖率门禁均为 CI 验证的一部分。Lint baseline 只保留已记录项；不要以跳过 lint 代替修复新问题。

CI 在 `.github/workflows/build.yml` 中配置：`master`/`main` 的 push/PR 执行上述 JVM 验证，另有 API 35 模拟器的 `connectedDebugAndroidTest` job。仅 `master` 的 push 会在这两个 job 成功后构建正式签名 Beta；Stable 标签工作流单独执行同一 JVM 验证命令。

### 7.1 版本元数据与发布产物核验

本地可运行 `./gradlew :app:writeVersionInfo`，检查 `app/build/generated/version-info/version.json` 中的 `versionCode`、`versionName` 和八位 `gitHash`。该任务和所有测试任务一样依赖完整 Git 历史。

- Stable workflow 会验证 tag 为严格 `vMAJOR.MINOR.PATCH` 且等于当前 `origin/master`，随后验证 Release 的 `version.json` 指向存在的 canonical APK、APK SHA-256/字节数正确，并且 `versionName` 与标签一致。
- Beta workflow 会验证 Pages 的 `version.json` 描述实际生成的 `qr-code-simple-beta.apk`，其中 `apkSha256`、`apkSize`、`versionCode` 和 `versionName` 均存在且一致。
- 手工检查已下载的发布 APK 时，可用 `sha256sum <apk>` 与 `stat -c%s <apk>` 对照 `version.json` 的 `apkSha256` 与 `apkSize`。这不能替代应用在安装前执行的包名、`versionCode` 和签名证书校验。

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
- 覆盖率门禁：`jacocoTestCoverageVerification` 已接入 CI（指令 ≥ 0.80，行 ≥ 0.75，`-PexcludeExtendedUiTests` 口径）。
- 更新通道：Stable 自动检查默认关闭且仅检查 Stable；Beta 只从 About 页按钮手动检查。`version.json` 的 SHA-256 和大小是必填安全边界，不允许为了兼容旧 metadata 删除它们。
- 增量更新：补丁链只是优化。安装包与补丁输入无法保持在 64 MiB 安全上限内、基础 APK hash 不匹配、补丁验证失败或补丁不比完整 APK 小时，都必须走已校验的完整 APK 下载。
- 金样测试：`GenerationGoldenTest` 固定输入断言 SVG 输出 SHA-256，防止生成图案在依赖升级时静默变化；预期变更需更新金样并注明原因。
- 场景测试套件（0.2.3）：`ContentActionScenarioTest`（各内容类型动作分发）、`ContentActionWifiModernTest`（API 29 WiFi 路径）、`BackupActivityFileRoundtripTest`（真实文件备份往返）、`HistoryScenarioTest` / `HistoryDetailScenarioTest`（筛选/搜索/标签/分享/详情操作）、`BatchFileScenarioTest`（CSV/Excel 导入 + ZIP/PNG 落盘）、`ScanRegionTouchTest`、`CameraScanScenarioTest`、`BlacklistUpdaterDownloadTest`、`AppShortcutManagerTest`、`CameraFocusManagerTest`。
- Robolectric 测试要点：
  - AlertDialog/AppCompat 按钮点击经 Handler 投递，断言前必须 idle 主 Looper；
  - Dispatchers.IO/后台协程不受主 Looper 控制，用谓词轮询（waitUntil）代替固定 sleep；
  - FileProvider 的静态路径缓存按 authority 冻结首个测试的沙盒根路径，所有使用它的测试类在 setup 中清理 `FileProvider.sCache`，否则测试类间按执行顺序互相污染；
  - CameraX 接口（Camera/CameraControl/CameraInfo/ZoomState）可用 JDK 动态代理伪造；
  - 网络层用可注入的连接工厂（如 `BlacklistUpdater.connectionFactoryForTesting`）替代真实请求。
