# QR Code Simple 下一轮计划（2026-08）

> 本文档是 2026-08 梳理的可落地计划清单。上一轮（12 功能批次 + 6 轮工程迭代，含 ApkDiffPatch 增量更新迁移）已全部完成。
> 范围约定：**不含安全相关整改**（黑名单/备份加密/证书/权限类已有专门治理，不在本计划内）。
>
> 执行规则：每项独立 commit + push，CI 全绿为验收门槛；本地验证命令统一为
> `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`（JDK 21）。

## 执行状态（2026-08-05 更新）

- [x] P0.1 Gradle 10 就绪：迁移 AGP 9 内置 Kotlin（移除 kotlin-android 插件 + kotlinOptions→compilerOptions）、清理 gradle.properties 全部旧 flag、显式 junit-platform-launcher、baselineprofile 插件升 1.5.0-beta01、jacoco 指向 built_in_kotlinc 输出（修复覆盖率塌陷）
- [x] P0.2 version catalog（gradle/libs.versions.toml，全部依赖版本集中管理）
- [x] P0.3 配置缓存（generateChangelog 内联 git 执行、jacoco doFirst 用捕获 layout）
- [x] P0.4 弃用 API 清理（Locale/CSVFormat/overridePendingTransition/WifiConfiguration 全限定名/AppDatabase 文件级 Suppress；编译 0 警告）
- [x] P1.1 HistoryDetailActivityTest 点击重试化（修复 CI 偶发 PerformException）
- [x] P1.2 ReleasePathSmokeTest（SVG/CSV/Excel 导入/ZIP MediaStore 导出 instrumented）
- [x] P1.3 覆盖盲区（ScannerOverlay/ScanRegion 已有测试；BatchResultActivity 由 P1.2 覆盖；processImage 记录为真实限制）
- [x] P1.4 文档纠错（barcode-formats.md 过时注记移除）
- [x] F1 法语 locale（431+61 条 100% 对齐，5 语言门禁生效——补漏 export_excel/stats 翻译时被 lint 捕获）
- [x] F2 ACTION_PROCESS_TEXT 入口（长按文本→生成）
- [x] F3 CSV 模板（已存在，确认无需新增）
- [x] F4 历史统计卡片（7/30 天扫码数 + 热门 Top3，DAO 查询 + 测试）
- [x] F5 扫描声音/震动开关（ScanFeedback helper，接入相机/连续扫描）
- [x] F6 Material You 动态色（DynamicColors + 主题切换，低版本回退青色）
- [x] F7 深链 qr-code-simple://generate?text=&format=
- [x] F8 历史 Excel 导出（XSSFWorkbook + SAF，roundtrip 测试）
- [ ] P3.1 v0.2.7 Stable 发布（等 CI 全绿后打 tag）
- [ ] P3.2 商店上架评估
- [ ] P3.3 通用 APK 体积优化调研

## P0 — 工程现代化（构建债，低风险高确定性）

### P0.1 Gradle 10 就绪清理

**现状**：每次构建输出 10+ 条 deprecation warning：

- `gradle.properties` 中 AGP 9 旧选项（Gradle 10 会移除）：
  `android.newDsl=false`、`android.builtInKotlin=false`、`android.usesSdkInManifest.disallowed=false`、
  `android.enableAppCompileTimeRClass=false`、`android.sdk.defaultTargetSdkToCompileSdkIfUnset=false`、
  `android.r8.optimizedResourceShrinking=false`、`android.defaults.buildfeatures.resvalues=true`、
  `android.enableBuildCache`、`android.uniquePackageNames=false`
- AGP obsolete API warning（`applicationVariants` / `testVariants` / `unitTestVariants`，需 `-Pandroid.debug.obsoleteApi=true` 定位来源）
- `kotlinOptions { jvmTarget }` → `compilerOptions` DSL（app 与 baselineprofile 两处）
- Baseline Profile plugin `maxAgpVersion` 警告
- `android.dependency.excludeLibraryComponentsFromConstraints` 建议开启（LIBRARY_CONSTRAINTS_SHOULD_BE_DISABLED）

**动作**：

1. 逐项删除 gradle.properties 旧 flag（每次删除后全量验证三套件不变）
2. `kotlinOptions` 迁移到 `android { compilerOptions { jvmTarget = JvmTarget.JVM_21 } }`（或 `kotlin { compilerOptions }`）
3. baselineprofile 模块加 `warnings { maxAgpVersion = false }`（构建脚本中配置，非静默跳过）
4. 定位并消除 obsolete API 警告来源

**验收**：`assembleDebug/testDebugUnitTest/lintDebug` 全绿且无 deprecation warning（库内 warning 除外）。

### P0.2 依赖版本集中管理（version catalog）

**现状**：全部版本硬编码在 `app/build.gradle`（30+ 依赖），无 `gradle/libs.versions.toml`；KSP `2.3.10` 与 Kotlin `2.2.21` 版本错位（KSP 2.3.x 对应 Kotlin 2.3.x，当前能构建但版本语义不严格）。

**动作**：

1. 引入 `gradle/libs.versions.toml`，迁移 app 与 baselineprofile 全部依赖版本
2. KSP 对齐：降到 `2.2.21-2.0.5`（与 Kotlin 2.2.21 配套），或升 Kotlin 到 2.3.x——二选一，需全量回归后定
3. 项目级插件版本（AGP 9.3.1、Kotlin、KSP、baselineprofile）一并进 catalog

**验收**：构建产物无行为变化；后续依赖升级 PR 只改 toml。

### P0.3 配置缓存启用验证

**现状**：未开启 `org.gradle.configuration-cache`；CI build job 8-9 分钟。

**动作**：

1. gradle.properties 开启 configuration cache
2. 处理配置缓存敏感点（`providers.exec`、`outputs.upToDateWhen { false }`、自定义 task 等）
3. CI 与本地验证缓存命中与增量加速

**验收**：配置缓存启用后本地增量构建显著加速；CI 全绿。

### P0.4 弃用 API 清理（非安全类）

**现状**（Kotlin 编译警告）：

- `Locale` 单参构造 deprecated：`BarcodeFormatAdapter.kt:98`、`BarcodeFormatUtils.kt:38` → 用无参 `Locale.getDefault()`
- `CSVFormat.build()` deprecated：`BatchGenerator.kt:52`
- `overridePendingTransition` deprecated：`AnimationUtils.kt:146,153`
- `WifiConfiguration` deprecated：`ContentActionHandler.kt:10`
- `ImagePerformanceManager.kt:58,59` inPurgeable/inInputShareable

**验收**：Kotlin 编译 0 条项目自有 deprecated warning。

## P1 — 质量补强

### P1.1 flaky 测试根治

**现状**：CI 上 `HistoryDetailActivityTest.toggleFavoriteUpdatesButton` 两次偶发失败（Espresso PerformException，需人工 rerun）；模拟器类测试历史多次 flaky（已靠 ATD 镜像 + 重试脚本缓解）。

**动作**：

1. `HistoryDetailActivityTest`：把 `Thread.sleep` 等待改为显式轮询/IdlingResource（等数据绑定完成）
2. `CameraScanCloseButtonDeviceTest` / `CameraScanReShowDiagTest`：统一稳定等待工具

**验收**：CI 连续 5 次全绿且无需 rerun。

### P1.2 release 路径 instrumented 冒烟（遗留风险闭环）

**现状**：next-iteration-plan 标记"R8 混淆 release 包未做全流程冒烟"（Excel 导入、批量 ZIP、SVG 导出、微信引擎回退）。

**动作**：

1. 新增 instrumented 冒烟测试覆盖 release 路径核心功能（debug 变体运行即覆盖逻辑）
2. 视情况跑一次 `connectedReleaseAndroidTest` 验证 R8 后行为

**验收**：新增 3-5 条 instrumented 用例；CI android-test 绿。

### P1.3 覆盖盲区补测

**现状**：`ScannerOverlayView` 绘制、`BatchResultActivity` MediaStore 导出、`CameraScanFragment.processImage` 为已知低覆盖。

**动作**：

1. `ScannerOverlayView`：Robolectric 状态机/绘制断言
2. `BatchResultActivity`：MediaStore 导出 instrumented（参考 MediaStoreSaveTest）
3. `CameraScanFragment.processImage`：伪造 `ImageProxy` 注入单测（难度高则降级为编排测试并记录）

**验收**：新增用例落地，覆盖率门禁维持。

### P1.4 文档纠错

- `docs/barcode-formats.md` 的"仅扫描"声明已过时（knowledge-base 已注明），修正或删除
- README/README_CN 与新功能（ApkDiffPatch、通用 ABI、更新系统）对齐复查

## P2 — 功能补全（用户价值排序）

| # | 功能 | 内容 | 预估 |
|---|---|---|---|
| F1 | 多语言扩展 | fr/es/ru/it/pt 五语言（en 为源，机翻起步，5 语言门禁内新增） | 小 |
| F2 | `ACTION_PROCESS_TEXT` 入口 | 长按选中文本 →「生成二维码/扫码」菜单（GenerateActivity 补 intent-filter + 解析） | 小 |
| F3 | 批量生成 CSV 模板 | 批量页一键下载模板 CSV + 示例 | 小 |
| F4 | 历史统计 | 历史页统计卡片：7/30 天扫码数、格式分布、热门内容 Top5 | 中 |
| F5 | 设置增强 | 扫描成功声音/震动开关、连续扫描间隔调节 | 小 |
| F6 | Material You 动态色 | Android 12+ 动态取色，与 values-night 融合 | 中 |
| F7 | 深链跳转 | `qr-code-simple://generate?...` + App Links 预填生成页 | 中 |
| F8 | 历史 Excel 导出 | 现有 CSV 之外补 `.xlsx`（POI 依赖已存在） | 中 |

## P3 — 发布与运营

### P3.1 v0.2.7 Stable 发布（ApkDiffPatch 全链路首验）

- 打 `v0.2.7` tag → release workflow 验证：发布物 = ApkNormalized + apksigner34 重签；对 v0.2.6（无 libapkpatch.so）不出补丁（lib 守卫），v0.2.7 之后的版本开始有增量
- 发布后验证 Beta/Stable 更新检查链路与签名连续性

### P3.2 商店上架评估（结论）

- **Google Play**：可行且阻碍小。AAB 构建已存在；需 Play Console 账号、隐私政策 URL、数据安全表单、内容评级与 Play App Signing 上传密钥（可与 debug keystore 解耦）。Fastlane 元数据（en-US/zh-CN 描述、截图、changelog）已就绪。targetSdk 35 满足 Play 要求。
- **F-Droid**：基本可行，有一个阻碍点——`libapkpatch.so` 是上游预编译二进制（MIT），F-Droid 构建通常要求纯源码；若上架 F-Droid 需评估：a) 在 F-Droid 构建变体剔除增量更新 native 库（增量仅自更新通道用，可开关）；b) 或向 F-Droid 提供 ApkDiffPatch 源码构建说明。建议优先 Play，F-Droid 作为后续选项。

### P3.3 通用 APK 体积优化调研（结论）

159MB 中约 125MB 是 `lib/`（4 ABI × OpenCV ~23-28MB + SQLCipher ~2-4MB + ML Kit/barhopper 等）。

| 方案 | 收益 | 工作量 | 建议 |
|---|---|---|---|
| Play App Bundle（已有 AAB） | Play 下载按 ABI/语言拆分 | 0（已具备） | 上架即得 |
| 自更新通道 per-ABI 分发 | 每包降至 ~50-60MB（单 ABI） | 中-大：发布侧 4 ABI 构建 + metadata 按 ABI 出条目；客户端 `Build.SUPPORTED_ABIS` 选择下载；补丁链按 ABI 独立维护 | 后续可选大项 |
| 依赖瘦身（裁剪 OpenCV 模块） | 有限（WeChatQR 需要完整 opencv_java4） | 大且风险高 | 不推荐 |
| 现有 R8 + shrinkResources + Baseline Profile | 已生效 | - | 保持 |

结论：体积优化的现实路径是「Play 上架吃 AAB 红利 + 自更新按 ABI 分发」两步；后者需要为 4 个 ABI 各维护一套发布物与补丁链，建议等增量更新体系稳定（v0.2.7 首验）后再实施。

## 执行顺序

1. P0.1 → P0.2 → P0.3 → P0.4（构建债，一次全量回归）
2. P1.1 → P1.2 → P1.3 → P1.4（质量）
3. F1/F2/F3/F5（小功能）→ F4/F6/F7/F8（中功能）
4. P3.1 可在 P0/P1 完成后随时执行
5. 每项独立 commit + push，CI 全绿为验收门槛
