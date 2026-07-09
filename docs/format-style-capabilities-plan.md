# 格式样式能力表整改计划

## 状态

已完成。最终方案调整为：生成器 `AdvancedBarcodeGenerator.generateStyled` 不再内部清洗样式；清洗逻辑由 `GenerateFragment`、历史列表、历史详情页在调用前显式执行。测试可绕过清洗直接传入原始样式。

## 背景

`StyleConfig` 中的 `moduleShape`、`moduleFillRatio`、`positionPatternShape` 以及 `ecLevel` 当前只对 QR Code 真正生效。生成其他格式时，`GenerateFragment` 仍把这些参数传给生成器，但生成器忽略它们，导致用户设置无效。需要按格式定义能力表，隐藏不支持的控件，并保证未生效的配置不写入历史。

## 能力表

| 能力项 | 默认 | 说明 |
|---|---|---|
| foregroundColor / backgroundColor | 开 | 全部格式支持 |
| foregroundBitmap / backgroundBitmap | 开 | 全部格式支持 |
| gradient | 开 | 全部格式支持 |
| logo | 开 | 全部格式支持 |
| cornerRadius | 开 | 全部格式开放 |
| ecLevel | 关 | 仅下方列出的格式支持 |
| moduleShape / moduleFillRatio / positionPatternShape | 关 | 仅 QR Code 真正渲染生效；其他格式不生效但不破坏回扫 |

### 各格式 EC 支持

| 格式 | 是否支持 EC | 映射（L / M / Q / H） |
|---|---|---|
| QR Code | 是 | 直接 L / M / Q / H |
| Aztec | 是 | 25 / 40 / 55 / 70（ZXing min ECC%） |
| PDF417 | 是 | 2 / 4 / 6 / 8（ZXing 纠错等级） |
| Han Xin | 是 | 1 / 2 / 3 / 4 |
| Micro QR | 是 | L / M / Q / H→Q（BoofCV 无 H） |
| Grid Matrix | 是 | 1 / 2 / 3 / 5（最高 50%） |
| 其他 | 否 | 隐藏 EC 控件，内部默认 H 但不生效 |

## 实施结果

1. **`AdvancedBarcodeGenerator`**
   - `FormatStyleCapabilities` 数据类已保留。
   - `generateStyled` 移除 `capabilities` 参数，不再内部清洗，直接使用传入的 `style`。
   - `sanitize(style, format)` 保持公开，供调用方使用。
   - `generateGenericWithStyle` 创建 `BarcodeConfig` 时传入 `styleConfig.ecLevel`。

2. **`BarcodeGenerator`**
   - `resolveErrorCorrectionLevel(format, ecLevel)` 已存在，返回格式特定值或 `null`。
   - QR / Aztec / PDF417 / Han Xin / Micro QR / Grid Matrix 已应用 EC 映射。

3. **`GenerateFragment`**
   - 给 `fragment_generate.xml` 中四个 QR 专属标签加了 id。
   - 根据当前格式能力表隐藏对应控件和标签（`GONE`）。
   - 生成和保存历史前均显式调用 `sanitize`。

4. **历史页面**
   - `HistoryFragment.shareQRCode` 和历史详情页在生成前显式调用 `sanitize`。

5. **测试**
   - 清洗逻辑：`FormatStyleCapabilitiesTest` 直接调用 `sanitize` 验证。
   - EC 映射：Aztec / PDF417 / Han Xin / Micro QR / Grid Matrix 不同等级产生不同条码。
   - 样式回扫摸底：新增 `StyleRawRoundtripMatrixTest`，27 组合 × 全格式 × 5 次，结果写入 `docs/style-roundtrip-matrix.md`。

6. **文档**
   - `docs/knowledge-base.md` 样式配置部分已更新。
   - `docs/style-roundtrip-matrix.md` 已创建并填入实际测试结果。
