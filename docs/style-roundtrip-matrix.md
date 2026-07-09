# 样式回扫摸底矩阵

## 目的

把 `moduleShape`、`moduleFillRatio`、`positionPatternShape` 这三个样式配置，**不经过清洗**，直接喂给 `AdvancedBarcodeGenerator.generateStyled`，对所有条码格式做生成+回扫测试，摸清每个组合的真实可用性。

## 背景

- 清洗（sanitize）将提前到 UI/历史调用方，生成器本身不再主动清洗。
- 测试里强制绕过清洗，使用原始样式配置。
- 对非 QR 格式，这三个配置当前不会被渲染使用，因此输出等同于默认样式；本测试同样要记录这些格式在原始配置下是否仍然能回扫。

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

执行时间：2026-07-09  
命令：`JAVA_HOME=/home/xenoamess/.jdks/jdk-21.0.7+6 ./gradlew :app:testDebugUnitTest --tests "*StyleRawRoundtripMatrixTest*"`  
每个组合跑 5 次，原始样式不清洗。

### 汇总

| 格式 | 可扫描 | 组合数 | 生成成功 | 回扫通过组合 | 备注 |
|---|---|---|---|---|---|
| AUSTRALIA_POST | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| AZTEC | true | 27 | 27 / 27 | 27 / 27 |  |
| AZTEC_RUNE | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CHANNEL_CODE | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODABAR | true | 27 | 27 / 27 | 27 / 27 |  |
| CODABLOCK_F | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODE_11 | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODE_128 | true | 27 | 27 / 27 | 27 / 27 |  |
| CODE_16K | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODE_2_OF_5_DATALOGIC | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODE_2_OF_5_DEUTSCHE_POST_IDENTCODE | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODE_2_OF_5_DEUTSCHE_POST_LEITCODE | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODE_2_OF_5_IATA | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODE_2_OF_5_INDUSTRIAL | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODE_2_OF_5_MATRIX | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODE_2_OF_5_STANDARD | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODE_32 | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODE_39 | true | 27 | 27 / 27 | 27 / 27 |  |
| CODE_39_EXTENDED | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODE_49 | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| CODE_93 | true | 27 | 27 / 27 | 27 / 27 |  |
| CODE_ONE | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| COMPOSITE | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| DATA_BAR_LIMITED | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| DATA_MATRIX | true | 27 | 27 / 27 | 27 / 27 |  |
| DPD_CODE | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| EAN_13 | true | 27 | 27 / 27 | 27 / 27 |  |
| EAN_8 | true | 27 | 27 / 27 | 27 / 27 |  |
| EAN_UPC_ADD_ON | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| GRID_MATRIX | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| HAN_XIN | true | 27 | 27 / 27 | 27 / 27 |  |
| ITF | true | 27 | 27 / 27 | 27 / 27 |  |
| ITF_14 | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| JAPAN_POST | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| KIX_CODE | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| KOREA_POST | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| LOGMARS | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| MAXICODE | true | 27 | 27 / 27 | 27 / 27 |  |
| MICRO_QR | true | 27 | 27 / 27 | 27 / 27 |  |
| MSI_PLESSEY | true | 27 | 27 / 27 | 27 / 27 |  |
| NVE_18 | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| PDF417 | true | 27 | 27 / 27 | 27 / 27 |  |
| PHARMACODE | true | 27 | 27 / 27 | 27 / 27 |  |
| PHARMACODE_2_TRACK | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| PHARMAZENTRALNUMMER | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| PLESSEY | true | 27 | 27 / 27 | 27 / 27 |  |
| POSTNET | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| QR_CODE | true | 27 | 27 / 27 | 15 / 27 |  |
| ROYAL_MAIL_4_STATE | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| RSS_14 | true | 27 | 27 / 27 | 27 / 27 |  |
| RSS_EXPANDED | true | 27 | 27 / 27 | 27 / 27 |  |
| SWISS_QR_CODE | true | 27 | 27 / 27 | 27 / 27 |  |
| TELEPEN | true | 27 | 27 / 27 | 27 / 27 |  |
| TELEPEN_NUMERIC | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| UPC_A | true | 27 | 27 / 27 | 27 / 27 |  |
| UPC_E | true | 27 | 27 / 27 | 27 / 27 |  |
| UPC_EAN_EXTENSION | true | 27 | 27 / 27 | 27 / 27 |  |
| UPN_QR_CODE | true | 27 | 27 / 27 | 27 / 27 |  |
| USPS_ONE_CODE | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |
| USPS_PACKAGE | false | 27 | 27 / 27 | 0 / 27 | 仅生成 |

### 关键结论

- **所有 56 种格式（排除 UNKNOWN）在 27 种原始样式组合下均能成功生成**，无崩溃。
- **所有非 QR 的可扫描格式（AZTEC、PDF417、DATA_MATRIX、EAN-13、CODE-128 等）27 种组合全部回扫通过**，因为 `moduleShape` / `moduleFillRatio` / `positionPatternShape` 对非 QR 格式不生效，输出等同于默认样式。
- **QR Code 受这三个配置影响，27 种组合中 15 种通过、12 种失败**。
- **QR Code 能回扫的规律**：
  - `moduleShape = SQUARE` 时，任意 `moduleFillRatio` 和 `positionPatternShape` 均可回扫（9/9 通过）。
  - `moduleShape = CIRCLE` 或 `ROUNDED` 时，仅当 `positionPatternShape = CIRCLE` 可回扫（6/18 通过）；`positionPatternShape = SQUARE` 或 `FOLLOW_MODULE` 全部失败。

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
- **其他所有格式**：三个配置不生效，但也不会破坏回扫，可放心保留原始配置；若需严格限制 UI 展示，建议按 `FormatStyleCapabilities` 清洗后使用。
