# QR Code Simple - UI/Adapter 测试补全计划

> 状态：已确认执行
> 目标：为所有用户可见的 UI 页面、Fragment、Activity、Adapter 和自定义 View 补全 Robolectric + Espresso 测试，避免类似格式选择器/填充比的交互回归。

## 1. 背景

最近一次修复了两个 UI/生成相关的回归：

- `AdvancedBarcodeGenerator` 的 `DEFAULT` 模块填充比导致模块形状异常。
- `GenerateFragment` 的格式下拉框无法编辑/过滤，且 `BarcodeFormatAdapter` 在过滤时会清空原始列表。

这些回归说明当前项目仅有少量 UI 测试（集中在 `GenerateFragment`），其他页面和 Adapter 的交互缺乏覆盖。本计划按批次为所有 UI 组件补全测试。

## 2. 总体原则

- **复用已有测试栈**：JUnit 4 + Robolectric 4.16.1 + Espresso。
- **每批独立可交付**：每完成一个批次，测试必须全部通过，可独立提交。
- **优先改可测性差的地方**：先把 Spinner/内部类改成和生成页一致的可测结构，再写测试。
- **不改业务行为**：测试为主，只调整必要的 UI 组件结构（如 `BatchGenerateActivity` 的 Spinner → `AutoCompleteTextView`）。

## 3. 当前状态

| 批次 | 主题 | 状态 | 备注 |
|---|---|---|---|
| 批次 1 | `BatchGenerateActivity` 格式选择器改造 | 完成 | `Spinner` 已替换为 `AutoCompleteTextView` |
| 批次 2 | 历史列表 | 完成 | `HistoryFragment` 增加 `loadHistoryJob` 取消，避免多个 Flow 收集器并发；`HistoryFragmentUiTest` 使用轮询等待列表尺寸 |
| 批次 3 | 结果页 | 完成 | 公共 `QRResultAdapter` 已提取并测试；`ResultActivity` / `VideoScanActivity` 覆盖列表展示和选择操作 |
| 批次 4 | 连续扫描 | 完成 | `ContinuousScanActivity` 通过反射注入 `handleScanResult` 测试扫描结果处理；`ContinuousScanAdapter` 独立测试 |
| 批次 5 | 生成页自定义控件 | 完成 | 新增 `ColorPickerViewTest` / `ColorPickerDialogTest` / `AngleDialViewTest`；修复 `ColorPickerView.setColor` 未更新 `currentColor` 和 `AngleDialView.angle` setter 未通知回调的问题 |
| 批次 6 | 主页导航 | 完成 | 新增 `MainActivityUiTest`；`MainActivity.updateTabSelection` 改用 `setTypeface` 实现加粗以便可测 |
| 批次 7 | 剩余页面 | 待执行 | |

## 4. 共享基础设施

| 文件 | 路径（建议） | 说明 |
|---|---|---|
| `TestDataFactory.kt` | `app/src/test/java/com/xenoamess/qrcodesimple/utils/test/` | 构造 `HistoryItem`、`ScanResult`、`QrResult` 等假数据。 |
| `BaseAdapterTest.kt` | `app/src/test/java/com/xenoamess/qrcodesimple/` | 提供 Adapter 测试通用 Robolectric 上下文、过滤等待等 helper。 |
| 公共 `QRResultAdapter` | `app/src/main/java/com/xenoamess/qrcodesimple/ui/result/QRResultAdapter.kt` | 把 `ResultActivity` 和 `VideoScanActivity` 里两个内部类提取为公共类，供测试复用。 |

## 5. 批次计划

### 批次 1：BatchGenerateActivity 格式选择器改造 ✅

当前 `BatchGenerateActivity` 仍使用普通 `Spinner` 显示格式名，需要改成和 `GenerateFragment` 一致的模糊搜索下拉框。

| 项目 | 内容 |
|---|---|
| 涉及文件 | `BatchGenerateActivity.kt`、`activity_batch_generate.xml` |
| 改动 | 1. 将 `Spinner` 替换为 `TextInputLayout` + `AutoCompleteTextView`。<br>2. 绑定 `BarcodeFormatAdapter`，显示 `BarcodeFormat.localizedName()`。<br>3. 用 `selectedFormat` 字段保存当前选择。 |
| 新增测试 | `BatchGenerateActivityTest.kt` |
| 测试点 | 1. 点击输入框可展开下拉。<br>2. 输入字符能过滤列表。<br>3. 选择条目后 `selectedFormat` 更新。<br>4. 输入不存在的格式时恢复到默认/原值。<br>5. 旋屏/重建后选择保留。 |

### 批次 2：历史列表 ✅

| 项目 | 内容 |
|---|---|
| 涉及文件 | `HistoryFragment.kt`、`HistoryAdapter.kt` |
| 改动 | 1. 保持原有 UI 行为不变。<br>2. `HistoryFragment.loadHistory()` 增加 `loadHistoryJob` 取消，避免多个 Flow 收集器并发导致列表更新错乱。 |
| 新增测试 | `HistoryAdapterTest.kt`、`HistoryFragmentUiTest.kt` |
| 测试点（Adapter） | 1. 不同 `HistoryType`（scanned/generated）的 item 绑定显示正确。<br>2. 收藏图标、笔记缩略显示正确。<br>3. 六个操作按钮（复制、分享、编辑、删除、收藏、生成二维码）均触发正确回调。 |
| 测试点（Fragment） | 1. 顶部四个筛选 chip（All/Scanned/Generated/Favorite）点击后过滤列表。<br>2. 搜索框输入文本后列表过滤。<br>3. tag chip 点击过滤。<br>4. 空状态时显示 empty view。<br>5. 长按进入多选模式，支持全选、复制已选、删除已选。 |

### 批次 3：结果页 ✅

| 项目 | 内容 |
|---|---|
| 涉及文件 | `ResultActivity.kt`、`VideoScanActivity.kt`、公共 `QRResultAdapter.kt` |
| 改动 | 把 `ResultActivity` 和 `VideoScanActivity` 中的内部 `QRResultAdapter` 提取为公共类。 |
| 新增测试 | `QRResultAdapterTest.kt`、`ResultActivityUiTest.kt`、`VideoScanActivityUiTest.kt` |
| 测试点（Adapter） | 1. 不同内容类型（URL、文本、WiFi、联系人等）显示正确的标签。<br>2. smart action 按钮根据内容类型显示/隐藏。<br>3. 安全指示器（安全/不安全/未知）显示正确。<br>4. 选中状态改变时 UI 同步。 |
| 测试点（Activity） | 1. 多选/全选后显示选中计数。<br>2. 复制已选结果。<br>3. 删除已选结果。<br>4. VideoScanActivity 同样覆盖列表展示和选择操作。 |

### 批次 4：连续扫描

| 项目 | 内容 |
|---|---|
| 涉及文件 | `ContinuousScanActivity.kt`、`ContinuousScanAdapter.kt` |
| 改动 | 尽量让 Activity 的扫描回调依赖可注入，或保持现有结构通过假数据直接测试。 |
| 新增测试 | `ContinuousScanAdapterTest.kt`、`ContinuousScanActivityUiTest.kt` |
| 测试点（Adapter） | 1. 已保存/未保存结果的状态显示正确。<br>2. 时间戳格式化显示正确。<br>3. 选中状态同步。 |
| 测试点（Activity） | 1. 添加重复结果不新增 item。<br>2. 添加新结果 item 增加。<br>3. 自动保存开关影响保存状态图标。<br>4. 设置对话框可打开并保存选项。<br>5. 清空全部后列表为空。 |

### 批次 5：生成页自定义控件

| 项目 | 内容 |
|---|---|
| 涉及文件 | `ColorPickerView.kt`、`ColorPickerDialog.kt`、`AngleDialView.kt` |
| 改动 | 无业务改动，主要为测试暴露必要状态或回调。 |
| 新增测试 | `ColorPickerViewTest.kt`、`ColorPickerDialogTest.kt`、`AngleDialViewTest.kt` |
| 测试点 | 1. 在 `ColorPickerView` 的色条/饱和度方格/透明度条上触摸，颜色值变化正确。<br>2. `ColorPickerDialog` 的 hex、RGBA 输入同步到颜色显示。<br>3. 确认/取消回调颜色正确。<br>4. `AngleDialView` 触摸不同角度返回正确角度并触发回调。 |

### 批次 6：主页导航

| 项目 | 内容 |
|---|---|
| 涉及文件 | `MainActivity.kt` |
| 改动 | 无。 |
| 新增测试 | `MainActivityUiTest.kt` |
| 测试点 | 1. 点击底部 5 个 tab 按钮切换 `ViewPager2` 到对应页面。<br>2. 切换时 tab 按钮颜色和加粗状态更新。<br>3. 通过 shortcut/deep-link 启动 `MainActivity` 后定位到指定 tab。<br>4. 携带 `EXTRA_GENERATE_CONTENT` 时进入生成 tab。 |

### 批次 7：剩余页面

| 页面 | 新增测试 | 主要测试点 |
|---|---|---|
| `HistoryDetailActivity` | `HistoryDetailActivityTest.kt` | tag chips 显示、笔记可见性、收藏按钮状态、重新生成 intent。 |
| `AboutFragment` | `AboutFragmentUiTest.kt` | 语言切换对话框、重启对话框、外部链接 intent。 |
| `PrivacySettingsActivity` | `PrivacySettingsActivityTest.kt` | 隐私模式开关、应用锁开关、确认对话框。 |
| `BackupActivity` | `BackupActivityTest.kt` | 导出 intent、导入 intent 启动。 |
| `DatabaseSecurityActivity` | `DatabaseSecurityActivityTest.kt` | 重置数据库确认、重启提示。 |
| `BatchResultActivity` + `BatchResultAdapter` | `BatchResultActivityTest.kt`、`BatchResultAdapterTest.kt` | 网格项绑定、单条保存、全部保存为 zip。 |
| `ScanImageFragment` | `ScanImageFragmentUiTest.kt` | 相册/相机/文件 launcher 选择入口。 |
| `CameraScanFragment` | `CameraScanFragmentUiTest.kt` | 闪光灯按钮切换、前后摄像头切换、结果卡片显示/隐藏。 |
| `ScannerOverlayView` | `ScannerOverlayViewTest.kt` | 动画区域绘制、裁剪区域变化。 |
| `ScanRegionView` | `ScanRegionViewTest.kt` | 裁剪区域设置、手势/触摸反馈。 |

## 6. 验证步骤

每批次完成后执行：

```bash
export JAVA_HOME=/home/xenoamess/.jdks/jdk-21.0.7+6
export PATH=$JAVA_HOME/bin:$PATH
./gradlew :app:testDebugUnitTest
```

全部完成后执行：

```bash
./gradlew :app:lintDebug
```

`lintDebug` 当前存在历史错误，不强制 0 错误，但要求不新增严重错误。

## 7. 完成标准

- 所有列出的页面/Fragment/Activity/Adapter/自定义 View 都有对应的测试文件。
- `./gradlew :app:testDebugUnitTest` 全部通过。
- `BatchGenerateActivity` 完成 Spinner → `AutoCompleteTextView` 迁移，行为与 `GenerateFragment` 一致。
- `ResultActivity` 和 `VideoScanActivity` 统一使用公共 `QRResultAdapter`。
- 测试策略文档和知识库已同步引用本计划。

## 8. 关联文档

- 测试策略总览：`docs/testing-strategy.md`
- 项目知识库：`docs/knowledge-base.md`
- 生成与样式能力：`docs/style-roundtrip-matrix.md`
