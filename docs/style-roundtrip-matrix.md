# 样式回扫摸底矩阵

## 目的

把 `moduleShape`、`moduleFillRatio`、`positionPatternShape` 这三个样式配置，**不经过清洗**，直接喂给 `AdvancedBarcodeGenerator.generateStyled`，对所有条码格式做生成+回扫测试，摸清每个组合的真实可用性。

## 背景

- 清洗（sanitize）将提前到 UI/历史调用方，生成器本身不再主动清洗。
- 测试里强制绕过清洗，使用原始样式配置。
- `moduleShape`、`moduleFillRatio`、`positionPatternShape` 现在会对所有非 QR 的可扫描格式进行渲染；不同格式的可扫描性因此发生变化。本测试记录这些组合在原始配置下的真实回扫情况。

## 测试维度

| 维度 | 取值 |
|---|---|
| `moduleShape` | SQUARE, CIRCLE, ROUNDED |
| `moduleFillRatio` | 0.5, 0.8, 1.0 |
| `positionPatternShape` | SQUARE, CIRCLE, FOLLOW_MODULE |

共 27 种组合。

## 测试对象

- 所有 `isScannable == true` 的格式：每个组合强制不清洗，生成后执行 `QRCodeScanner.scanSync` 回扫。
- 所有仅生成格式（`isScannable == false`）：每个组合强制不清洗，仅验证生成成功、不崩溃。

## 判定标准

- 每个组合跑 5 次。
- 记录“通过次数 / 5”作为通过率。
- 只要 5 次中至少 1 次回扫成功，该组合即标记为 **通过**。
- 对仅生成格式，只记录生成成功/失败。

## 输出格式

结果表格包含：

- 格式
- `moduleShape`
- `moduleFillRatio`
- `positionPatternShape`
- 生成是否成功
- 回扫通过次数 / 5
- 是否通过

最终汇总“大概率能回扫”的组合列表。

## 执行结果

执行时间：2026-07-10  
命令：`JAVA_HOME=/home/xenoamess/.jdks/jdk-21.0.7+6 ./gradlew :app:testDebugUnitTest --tests "*StyleRawRoundtripMatrixTest*"`  
每个组合跑 5 次，原始样式不清洗。

### 汇总

| 格式 | 可扫描 | 组合数 | 生成成功 | 回扫通过组合 | 备注 |
|---|---|---|---|---|---|
| AUSTRALIA_POST | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| AZTEC | true | 27 | 27 / 27 | 6 / 27 |  |
| AZTEC_RUNE | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CHANNEL_CODE | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODABAR | true | 27 | 27 / 27 | 27 / 27 |  |
| CODABLOCK_F | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODE_11 | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODE_128 | true | 27 | 27 / 27 | 9 / 27 |  |
| CODE_16K | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODE_2_OF_5_DATALOGIC | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODE_2_OF_5_DEUTSCHE_POST_IDENTCODE | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODE_2_OF_5_DEUTSCHE_POST_LEITCODE | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODE_2_OF_5_IATA | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODE_2_OF_5_INDUSTRIAL | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODE_2_OF_5_MATRIX | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODE_2_OF_5_STANDARD | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODE_32 | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODE_39 | true | 27 | 27 / 27 | 9 / 27 |  |
| CODE_39_EXTENDED | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODE_49 | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODE_93 | true | 27 | 27 / 27 | 9 / 27 |  |
| CODE_ONE | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| COMPOSITE | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| DATA_BAR_LIMITED | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| DATA_MATRIX | true | 27 | 27 / 27 | 10 / 27 |  |
| DPD_CODE | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| EAN_13 | true | 27 | 27 / 27 | 9 / 27 |  |
| EAN_8 | true | 27 | 27 / 27 | 9 / 27 |  |
| EAN_UPC_ADD_ON | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| GRID_MATRIX | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| HAN_XIN | true | 27 | 27 / 27 | 22 / 27 |  |
| ITF | true | 27 | 27 / 27 | 15 / 27 |  |
| ITF_14 | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| JAPAN_POST | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| KIX_CODE | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| KOREA_POST | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| LOGMARS | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| MAXICODE | true | 27 | 27 / 27 | 27 / 27 |  |
| MICRO_QR | true | 27 | 27 / 27 | 7 / 27 |  |
| MSI_PLESSEY | true | 27 | 27 / 27 | 9 / 27 |  |
| NVE_18 | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| PDF417 | true | 27 | 27 / 27 | 27 / 27 |  |
| PHARMACODE | true | 27 | 27 / 27 | 9 / 27 |  |
| PHARMACODE_2_TRACK | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| PHARMAZENTRALNUMMER | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| PLESSEY | true | 27 | 27 / 27 | 27 / 27 |  |
| POSTNET | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| QR_CODE | true | 27 | 27 / 27 | 15 / 27 |  |
| ROYAL_MAIL_4_STATE | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| RSS_14 | true | 27 | 27 / 27 | 4 / 27 |  |
| RSS_EXPANDED | true | 27 | 27 / 27 | 4 / 27 |  |
| SWISS_QR_CODE | true | 27 | 27 / 27 | 17 / 27 |  |
| TELEPEN | true | 27 | 27 / 27 | 9 / 27 |  |
| TELEPEN_NUMERIC | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| UPC_A | true | 27 | 27 / 27 | 9 / 27 |  |
| UPC_E | true | 27 | 27 / 27 | 9 / 27 |  |
| UPC_EAN_EXTENSION | true | 27 | 27 / 27 | 27 / 27 |  |
| UPN_QR_CODE | true | 27 | 27 / 27 | 12 / 27 |  |
| USPS_ONE_CODE | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| USPS_PACKAGE | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |

### 关键结论

- 所有 56 种格式（排除 UNKNOWN）在 27 种原始样式组合下均能成功生成，无崩溃。
- 1D 条码（CODABAR 除外）在 `moduleFillRatio = 1.0` 时基本可回扫；当 `moduleFillRatio < 1.0` 时，条宽被压缩，回扫失败。
- 2D 矩阵码中：
  - PDF417、MaxiCode、Plessey 对三种样式最鲁棒，27 种组合全部回扫通过。
  - Data Matrix、Aztec、Micro QR、Swiss QR Code、UPN QR Code 在 `moduleShape = SQUARE` 且 `moduleFillRatio >= 0.8` 时大部分可回扫，CIRCLE/ROUNDED 配合小填充比容易失败。
  - Han Xin Code 表现较好，SQUARE 与多数 CIRCLE/ROUNDED 组合可回扫。
- RSS-14 / RSS Expanded 受布局缩放影响，仅部分组合（主要是 SQUARE + fillRatio 1.0）可回扫。
- QR Code 仍只推荐 `moduleShape = SQUARE`（任意填充、任意定位点），或 `moduleShape = CIRCLE/ROUNDED` 且 `positionPatternShape = CIRCLE`。
- UPC/EAN Extension 走 Fallback 渲染，27 种组合全部回扫通过，但样式本身不生效。

### QR Code 明细

| 格式 | moduleShape | moduleFillRatio | positionPatternShape | 生成成功 | 回扫通过次数/5 | 通过 |
|---|---|---|---|---|---|---|
| QR_CODE | SQUARE | 0.5 | SQUARE | 是 | 5/5 | 是 |
| QR_CODE | SQUARE | 0.5 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | SQUARE | 0.5 | FOLLOW_MODULE | 是 | 5/5 | 是 |
| QR_CODE | SQUARE | 0.8 | SQUARE | 是 | 5/5 | 是 |
| QR_CODE | SQUARE | 0.8 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | SQUARE | 0.8 | FOLLOW_MODULE | 是 | 5/5 | 是 |
| QR_CODE | SQUARE | 1.0 | SQUARE | 是 | 5/5 | 是 |
| QR_CODE | SQUARE | 1.0 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | SQUARE | 1.0 | FOLLOW_MODULE | 是 | 5/5 | 是 |
| QR_CODE | CIRCLE | 0.5 | SQUARE | 是 | 0/5 | 否 |
| QR_CODE | CIRCLE | 0.5 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | CIRCLE | 0.5 | FOLLOW_MODULE | 是 | 0/5 | 否 |
| QR_CODE | CIRCLE | 0.8 | SQUARE | 是 | 0/5 | 否 |
| QR_CODE | CIRCLE | 0.8 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | CIRCLE | 0.8 | FOLLOW_MODULE | 是 | 0/5 | 否 |
| QR_CODE | CIRCLE | 1.0 | SQUARE | 是 | 0/5 | 否 |
| QR_CODE | CIRCLE | 1.0 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | CIRCLE | 1.0 | FOLLOW_MODULE | 是 | 0/5 | 否 |
| QR_CODE | ROUNDED | 0.5 | SQUARE | 是 | 0/5 | 否 |
| QR_CODE | ROUNDED | 0.5 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | ROUNDED | 0.5 | FOLLOW_MODULE | 是 | 0/5 | 否 |
| QR_CODE | ROUNDED | 0.8 | SQUARE | 是 | 0/5 | 否 |
| QR_CODE | ROUNDED | 0.8 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | ROUNDED | 0.8 | FOLLOW_MODULE | 是 | 0/5 | 否 |
| QR_CODE | ROUNDED | 1.0 | SQUARE | 是 | 0/5 | 否 |
| QR_CODE | ROUNDED | 1.0 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | ROUNDED | 1.0 | FOLLOW_MODULE | 是 | 0/5 | 否 |

### 推荐配置

- **QR Code 稳定可用**：`moduleShape = SQUARE`（任意填充、任意定位点）；或 `moduleShape = CIRCLE/ROUNDED` 且 `positionPatternShape = CIRCLE`。
- **PDF417 / MaxiCode / Plessey**：任意三样式组合均可回扫。
- **1D 条码（EAN/UPC/CODE-128/CODE-39/CODE-93/ITF/MSI/Pharmacode/Telepen）**：仅当 `moduleFillRatio = 1.0` 时稳定回扫，可搭配任意 `moduleShape` / `positionPatternShape`。
- **CODABAR**：所有组合均可回扫。
- **Data Matrix / Aztec / Han Xin / Micro QR / Swiss QR / UPN QR**：优先使用 `moduleShape = SQUARE`，`moduleFillRatio` 从 0.8 起；CIRCLE/ROUNDED 需谨慎。
- **RSS-14 / RSS Expanded**：优先使用 `moduleShape = SQUARE`，`moduleFillRatio = 1.0`。
- **UPC/EAN Extension**：样式不生效，任意组合均可回扫。
