# 格式样式能力表整改计划

## 背景

`StyleConfig` 中的 `moduleShape`、`moduleFillRatio`、`positionPatternShape` 以及 `ecLevel` 当前只对 QR Code 真正生效。生成其他格式时，`GenerateFragment` 仍把这些参数传给生成器，但生成器忽略它们，导致用户设置无效。需要按格式定义能力表，隐藏不支持的控件，并保证未生效的配置不写入历史。

## 能力表

| 能力项 | 默认 | 说明 |
|---|---|---|
| foregroundColor / backgroundColor | 开 | 全部格式支持 |
| foregroundBitmap / backgroundBitmap | 开 | 全部格式支持 |
| gradient | 开 | 全部格式支持 |
| logo | 开 | 全部格式支持 |
| cornerRadius | 开 | 全部格式开放（按用户要求） |
| ecLevel | 关 | 仅下方列出的格式支持 |
| moduleShape / moduleFillRatio / positionPatternShape | 关 | 仅 QR Code 支持 |

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

## 实施步骤

1. **`AdvancedBarcodeGenerator`**
   - 新增 `FormatStyleCapabilities` 数据类，覆盖 `StyleConfig` 全部字段。
   - `generateStyled` 增加可选参数 `capabilities`，默认按 format 自动查表。
   - 生成前按能力表清洗 `StyleConfig`，原配置保留不变。
   - `generateGenericWithStyle` 创建 `BarcodeConfig` 时传入 `styleConfig.ecLevel`。

2. **`BarcodeGenerator`**
   - 新增 `resolveErrorCorrectionLevel(format, ecLevel)`，返回格式特定值或 `null`。
   - 更新 `generateQRCode`、`generateAztec`、`generatePDF417`、`generateHanXin`、`generateMicroQr`、`generateGridMatrix` 应用映射。

3. **`GenerateFragment`**
   - 给 `fragment_generate.xml` 中四个 QR 专属标签加 id。
   - 根据当前格式能力表隐藏对应控件和标签（`GONE`）。
   - 生成和保存历史前均用能力表清洗 `StyleConfig`。

4. **测试**
   - 清洗逻辑：非 QR 格式传入 QR 专属配置后清洗为默认值。
   - EC 映射：Aztec / PDF417 / Han Xin / Micro QR / Grid Matrix 不同等级产生不同条码。
   - UI 隐藏：格式切换后对应控件可见性正确。

5. **文档**
   - 更新 `docs/knowledge-base.md` 的样式配置部分。
