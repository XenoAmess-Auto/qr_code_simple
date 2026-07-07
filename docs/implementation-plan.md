# QR Code Simple 实施计划

> 本计划基于当前代码状态梳理，覆盖基础修复、入口统一、功能补齐、测试补全与体验扩展。
> 
> **跳过项**：TFLite 二维码增强模型（`qr_enhance_model.tflite`）不在本次处理范围内。

---

## 项目现状概述

- 已支持 22 种条码格式的扫描与生成
- 核心生成逻辑在 `BarcodeGenerator.kt`，核心扫描逻辑在 `QRCodeScanner.kt`
- 主界面 5 个 Tab 使用 Fragment：`CameraScanFragment`、`ScanImageFragment`、`GenerateFragment`、`HistoryFragment`、`AboutFragment`
- 存在独立的 Activity 入口：`CameraScanActivity`、`ScanImageActivity`、`GenerateActivity`、`ContinuousScanActivity`、`BatchGenerateActivity` 等
- 当前存在 Activity/Fragment 逻辑重复、部分功能未接入（应用锁）、备份导入逻辑错误、数据库密钥明文存储等问题

---

## 阶段一：基础修复

| # | 任务 | 目标文件 | 关键改动 | 验收标准 |
|---|------|----------|----------|----------|
| 1.1 | 修正构建文档 | `AGENTS.md` | 将 `build.gradle.kts` / Gradle 9.5.1 修正为实际 `app/build.gradle` / Gradle 9.6.1；补充 JDK 21 环境要求 | 文档与项目实际一致 |
| 1.2 | 修复 Lint 阻塞错误 | `CrashlyticsManager.kt:38` | `packageInfo.longVersionCode` 改为兼容 API 24 的写法 | `./gradlew :app:lintDebug` 不再因 `NewApi` 失败 |
| 1.3 | 修复 GenerateActivity 崩溃 | `GenerateActivity.kt:221` | 失败分支 `getString(R.string.failed_to_generate)` 缺少参数，需补齐参数或拆分字符串 | 生成失败时不崩溃 |
| 1.4 | 修复备份导入逻辑错误 | `BackupActivity.kt:135` | `[` 开头应识别为 JSON，其余为 CSV；当前分支互换 | 导入 JSON 和 CSV 均走正确解析路径 |
| 1.5 | 修复 CSV 引号解析 bug | `HistoryBackupManager.kt:207` | `line.indexOf(char)` 在循环中始终返回第一个引号位置，改为基于当前 index 的转义判断 | 含引号/逗号内容正确解析 |
| 1.6 | 修复备份漏 tags | `HistoryBackupManager.kt` | JSON/CSV 导出导入都包含 `tags` 字段 | 标签备份不丢失 |
| 1.7 | 安全存储数据库密钥 | `data/AppDatabase.kt:56-67` | 将 SQLCipher 密码从普通 `SharedPreferences` 迁移到 Android Keystore / EncryptedSharedPreferences | 密钥不再以明文存储 |

---

## 阶段二：入口统一、样式迁移、自检、应用锁

| # | 任务 | 目标文件 | 关键改动 | 验收标准 |
|---|------|----------|----------|----------|
| 2.1 | 统一扫描入口 | `CameraScanActivity.kt`、`CameraScanFragment.kt` | `CameraScanActivity` 作为 `CameraScanFragment` 的薄包装，扫描逻辑统一在 Fragment | 两个入口行为一致，Activity 不再重复维护 |
| 2.2 | 统一图片扫描入口 | `ScanImageActivity.kt`、`ScanImageFragment.kt` | `ScanImageActivity` 作为 `ScanImageFragment` 的薄包装 | 两个入口行为一致 |
| 2.3 | 统一生成入口 + 样式迁移 | `GenerateActivity.kt`、`GenerateFragment.kt` | `GenerateActivity` 作为 `GenerateFragment` 的薄包装；将 `GenerateActivity` 的样式控件（颜色、圆角、点阵、Logo）迁移到 `GenerateFragment` | 主界面"生成" Tab 可见样式设置 |
| 2.4 | 所有格式开放样式 | `AdvancedBarcodeGenerator.kt`、`GenerateFragment.kt` | 不对非 QR 格式隐藏样式；为 Data Matrix / Aztec / PDF417 / 1D 码等实现渐变/圆角/点阵/Logo 绘制（用户自担风险） | 所有格式都能应用样式 |
| 2.5 | 生成后自检与黄色弱警告 | `GenerateFragment.kt`、`GenerateActivity.kt`、新增 `BarcodeGenerationValidator.kt`（或放在 `BarcodeGenerator.kt`） | 生成后调用 `QRCodeScanner.scanSync()` 扫描生成图片，比对原始内容；无法识别或内容不一致时，在生成图片下方显示黄色文字提示；不弹窗、不阻止导出 | 异常时显示黄色弱警告，不影响保存/分享 |
| 2.6 | 接入应用锁 | `AppLockManager.kt`、`QRCodeApp.kt`、设置页 | 默认关闭；设置中可开启 PIN/生物识别；开启后锁定历史页 | 锁开关生效 |

### 生成后自检细节

- 调用 `QRCodeScanner.scanSync(context, bitmap)` 获取扫描结果
- 状态判定：
  - **通过**：至少一条结果 `text == 原始 content`
  - **无法识别**：扫描结果为空
  - **内容不一致**：扫描结果非空，但无任何一条 `text == 原始 content`
- 特殊格式处理：
  - **RSS Expanded**：生成用 `[01]...`，扫描返回 `(01)...`，比对时统一格式
  - **UPC/EAN Extension**：从 `ResultMetadataType.UPC_EAN_EXTENSION` 取出 extension 比对
  - **Pharmacode / Plessey / MSI / Telepen / Han Xin / Micro QR**：根据自定义解码器返回值标准化比对
- UI 文案：
  - 无法识别：「生成的条码无法被识别，请检查内容或格式」
  - 内容不一致：「扫描结果与原始内容不一致，请确认是否可接受」

---

## 阶段三：连续扫描

| # | 任务 | 目标文件 | 关键改动 | 验收标准 |
|---|------|----------|----------|----------|
| 3.1 | 实现真正的连续扫描 | `ContinuousScanActivity.kt` | 复用 `CameraScanFragment` 扫描逻辑；支持扫描间隔控制、去重、震动反馈、自动保存到历史 | 能连续扫描并追加结果列表 |

---

## 阶段四：Excel 导入

| # | 任务 | 目标文件 | 关键改动 | 验收标准 |
|---|------|----------|----------|----------|
| 4.1 | 引入 POI 依赖 | `app/build.gradle` | 引入 `org.apache.poi:poi:5.3.0` 和 `poi-ooxml:5.3.0` | 构建通过 |
| 4.2 | 实现 Excel 解析 | `BatchGenerator.kt` | 新增 `parseExcel()`；兼容三种情况：<br>① 有标题行，列名 `content`/`format`/`filename`/`fg_color`/`bg_color`<br>② 无标题行，单列内容按顺序读取<br>③ 列数不足时默认 `QR_CODE` | 三种 Excel 都能导入 |
| 4.3 | 扩展文件选择器 | `BatchGenerateActivity.kt` | MIME type 增加 `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` 和 `application/vnd.ms-excel` | 可选择 `.xlsx` / `.xls` |

---

## 阶段五：测试与 CI

| # | 任务 | 目标文件 | 关键改动 | 验收标准 |
|---|------|----------|----------|----------|
| 5.1 | 补全缺失测试 | `app/src/test/java/.../generator/` | 新增 `MicroQrGenerationTest`、`CustomLinearGenerationTest`、`Gs1DatabarGenerationTest`、`MaxiCodeGenerationTest`、`UpcEanExtensionGenerationTest`、`BarcodeValidationTest` | 全部通过 |
| 5.2 | 扩展模块测试 | 对应模块 | 为 `AppLockManager`、`HistoryBackupManager`、`TagManager` 补单元测试 | 覆盖主要路径 |
| 5.3 | CI 跑单元测试 | `.github/workflows/build.yml` | 在 Build Debug APK 后增加 `./gradlew :app:testDebugUnitTest` | CI 每次 PR 都跑测试 |

---

## 阶段六：体验扩展

| # | 任务 | 目标文件 | 关键改动 | 验收标准 |
|---|------|----------|----------|----------|
| 6.1 | SVG 导出扩展到所有格式 | `SvgQRCodeGenerator.kt` | 支持 Data Matrix、Aztec、PDF417、1D 码等 | 这些格式可导出 SVG |
| 6.2 | 历史详情页 | 新建 `HistoryDetailActivity.kt` | 展示完整内容、类型、时间、标签、备注，并提供编辑/分享/删除 | 历史项可进入详情页 |
| 6.3 | 标签管理 UI | `HistoryFragment.kt` | 支持为历史项添加/编辑标签，按标签筛选 | 历史页可按标签过滤 |
| 6.4 | 清理已弃用 API | 多个文件 | 替换 `WifiConfiguration`、旧 `CSVFormat` builder、`LocaleHelper` 直接配置修改、`fallbackToDestructiveMigration` 重载等 | 弃用警告显著减少 |

---

## 实施顺序

1. **阶段一**（基础修复）
2. **阶段二**（入口统一 + 样式迁移 + 所有格式样式 + 生成自检 + 应用锁）
3. **阶段三**（连续扫描）
4. **阶段四**（Excel 导入）
5. **阶段五**（测试 + CI）
6. **阶段六**（体验扩展）

---

## 已知跳过项

- **TFLite 二维码增强**：`qr_enhance_model.tflite` 模型文件缺失，且开源社区无专门的开源二维码增强 TFLite 模型。本次不做处理，保留现有传统图像增强（`QRCodeRestorationManager`）作为 fallback。
