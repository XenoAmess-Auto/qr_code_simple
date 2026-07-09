# 样式回扫摸底矩阵

## 目的

把 `moduleShape`、`moduleFillRatio`、`positionPatternShape` 这三个样式配置，**不经过清洗**，直接喂给 `AdvancedBarcodeGenerator.generateStyled`，对所有条码格式做生成+回扫测试，摸清每个组合的真实可用性。

## 背景

- 清洗（sanitize）将提前到 UI/历史调用方，生成器本身不再主动清洗。
- 测试里强制绕过清洗，使用原始样式配置。
- `moduleShape`、`moduleFillRatio` 现在会对**所有格式**进行渲染：有结构化布局的格式走原生渲染，仅生成格式和 MaxiCode 走兜底图片后处理（连通域 + 腐蚀/形状）。`positionPatternShape` 只对有定位图案或 Guard 的格式生效。

## 测试维度

| 维度 | 取值 |
|---|---|
| `moduleShape` | DEFAULT, CIRCLE, ROUNDED |
| `moduleFillRatio` | 0.5, 0.8, 0.85, 0.9, 0.95, 1.0 |
| `positionPatternShape` | DEFAULT, CIRCLE, FOLLOW_MODULE |

共 54 种组合。

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
| AUSTRALIA_POST | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| AZTEC | true | 54 | 54 / 54 | 12 / 54 |  |
| AZTEC_RUNE | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| CHANNEL_CODE | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| CODABAR | true | 54 | 54 / 54 | 54 / 54 |  |
| CODABLOCK_F | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| CODE_11 | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| CODE_128 | true | 54 | 54 / 54 | 24 / 54 |  |
| CODE_16K | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| CODE_2_OF_5_DATALOGIC | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| CODE_2_OF_5_DEUTSCHE_POST_IDENTCODE | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| CODE_2_OF_5_DEUTSCHE_POST_LEITCODE | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| CODE_2_OF_5_IATA | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| CODE_2_OF_5_INDUSTRIAL | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| CODE_2_OF_5_MATRIX | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| CODE_2_OF_5_STANDARD | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| CODE_32 | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| CODE_39 | true | 54 | 54 / 54 | 30 / 54 |  |
| CODE_39_EXTENDED | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| CODE_49 | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| CODE_93 | true | 54 | 54 / 54 | 21 / 54 |  |
| CODE_ONE | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| COMPOSITE | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| DATA_BAR_LIMITED | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| DATA_MATRIX | true | 54 | 54 / 54 | 19 / 54 |  |
| DPD_CODE | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| EAN_13 | true | 54 | 54 / 54 | 31 / 54 |  |
| EAN_8 | true | 54 | 54 / 54 | 33 / 54 |  |
| EAN_UPC_ADD_ON | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| GRID_MATRIX | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| HAN_XIN | true | 54 | 54 / 54 | 40 / 54 |  |
| ITF | true | 54 | 54 / 54 | 39 / 54 |  |
| ITF_14 | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| JAPAN_POST | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| KIX_CODE | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| KOREA_POST | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| LOGMARS | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| MAXICODE | true | 54 | 54 / 54 | 54 / 54 |  |
| MICRO_QR | true | 54 | 54 / 54 | 14 / 54 |  |
| MSI_PLESSEY | true | 54 | 54 / 54 | 9 / 54 |  |
| NVE_18 | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| PDF417 | true | 54 | 54 / 54 | 54 / 54 |  |
| PHARMACODE | true | 54 | 54 / 54 | 26 / 54 |  |
| PHARMACODE_2_TRACK | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| PHARMAZENTRALNUMMER | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| PLESSEY | true | 54 | 54 / 54 | 54 / 54 |  |
| POSTNET | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| QR_CODE | true | 54 | 54 / 54 | 30 / 54 |  |
| ROYAL_MAIL_4_STATE | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| RSS_14 | true | 54 | 54 / 54 | 4 / 54 |  |
| RSS_EXPANDED | true | 54 | 54 / 54 | 4 / 54 |  |
| SWISS_QR_CODE | true | 54 | 54 / 54 | 30 / 54 |  |
| TELEPEN | true | 54 | 54 / 54 | 9 / 54 |  |
| TELEPEN_NUMERIC | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| UPC_A | true | 54 | 54 / 54 | 33 / 54 |  |
| UPC_E | true | 54 | 54 / 54 | 33 / 54 |  |
| UPC_EAN_EXTENSION | true | 54 | 54 / 54 | 54 / 54 |  |
| USPS_ONE_CODE | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |
| USPS_PACKAGE | false | 54 | 54 / 54 | 0 / 54 | 仅生成 |


### 关键结论

- 所有 56 种格式（排除 UNKNOWN）在 54 种原始样式组合下均能成功生成，无崩溃。
- 全组合鲁棒格式（54/54 通过）：CODABAR、PDF417、MaxiCode、Plessey、UPC/EAN Extension。
- 1D 条码：
  - CODABAR 任意样式组合均可回扫。
  - EAN-13/8、UPC-A/E 在 `moduleShape = DEFAULT/ROUNDED` 且 `moduleFillRatio >= 0.85` 时基本通过，但 `moduleFillRatio = 0.85` 时个别定位点组合会失败；`moduleShape = CIRCLE` 需 `moduleFillRatio >= 0.9`。
  - ITF 在 `moduleShape = DEFAULT/ROUNDED` 且 `moduleFillRatio >= 0.8` 时通过，CIRCLE 需 `moduleFillRatio >= 0.9`。
  - CODE-128、CODE-93 在 `moduleShape = DEFAULT/ROUNDED` 需 `moduleFillRatio >= 0.9`，CIRCLE 基本需 `moduleFillRatio = 1.0`。
  - CODE-39 在 `moduleShape = DEFAULT/ROUNDED` 需 `moduleFillRatio >= 0.85`，CIRCLE 需 `moduleFillRatio = 0.95`。
  - Pharmacode 在 `moduleShape = DEFAULT/ROUNDED` 需 `moduleFillRatio >= 0.95` 才能全定位点组合通过；CIRCLE 同样需 `moduleFillRatio >= 0.95`。
  - MSI Plessey、Telepen 仅在 `moduleFillRatio = 1.0` 时通过。
- 2D 矩阵码：
  - PDF417、MaxiCode、Plessey 最鲁棒，所有样式组合均可回扫。
  - QR Code：仅 `moduleShape = DEFAULT`（任意填充、任意定位点）或 `moduleShape = CIRCLE/ROUNDED` 且 `positionPatternShape = CIRCLE`。
  - Data Matrix / Aztec / Micro QR：优先使用 `moduleShape = DEFAULT`，`moduleFillRatio` 从 0.5 起大部分可回扫；CIRCLE/ROUNDED 基本不可回扫。
  - Han Xin Code 表现最好，DEFAULT 全组合通过，多数 CIRCLE/ROUNDED 组合也能回扫。
  - Swiss QR Code / UPN QR Code：DEFAULT 在 `moduleFillRatio >= 0.95` 基本全通过，CIRCLE/ROUNDED 仅部分组合通过。
- RSS-14 / RSS Expanded 受布局缩放影响，仅 `moduleShape = DEFAULT/ROUNDED` 且 `moduleFillRatio = 1.0` 时少数组合可回扫。
- UPC/EAN Extension 走 Fallback 渲染，现在 `moduleShape` / `moduleFillRatio` 会生效；`positionPatternShape` 因无定位图案而不生效。默认样式仍可回扫。

### QR Code 明细

| 格式 | moduleShape | moduleFillRatio | positionPatternShape | 生成成功 | 回扫通过次数/5 | 通过 |
|---|---|---|---|---|---|---|
| QR_CODE | DEFAULT | 0.5 | DEFAULT | 是 | 5/5 | 是 |
| QR_CODE | DEFAULT | 0.5 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | DEFAULT | 0.5 | FOLLOW_MODULE | 是 | 5/5 | 是 |
| QR_CODE | DEFAULT | 0.8 | DEFAULT | 是 | 5/5 | 是 |
| QR_CODE | DEFAULT | 0.8 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | DEFAULT | 0.8 | FOLLOW_MODULE | 是 | 5/5 | 是 |
| QR_CODE | DEFAULT | 0.85 | DEFAULT | 是 | 5/5 | 是 |
| QR_CODE | DEFAULT | 0.85 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | DEFAULT | 0.85 | FOLLOW_MODULE | 是 | 5/5 | 是 |
| QR_CODE | DEFAULT | 0.9 | DEFAULT | 是 | 5/5 | 是 |
| QR_CODE | DEFAULT | 0.9 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | DEFAULT | 0.9 | FOLLOW_MODULE | 是 | 5/5 | 是 |
| QR_CODE | DEFAULT | 0.95 | DEFAULT | 是 | 5/5 | 是 |
| QR_CODE | DEFAULT | 0.95 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | DEFAULT | 0.95 | FOLLOW_MODULE | 是 | 5/5 | 是 |
| QR_CODE | DEFAULT | 1.0 | DEFAULT | 是 | 5/5 | 是 |
| QR_CODE | DEFAULT | 1.0 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | DEFAULT | 1.0 | FOLLOW_MODULE | 是 | 5/5 | 是 |
| QR_CODE | CIRCLE | 0.5 | DEFAULT | 是 | 0/5 | 否 |
| QR_CODE | CIRCLE | 0.5 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | CIRCLE | 0.5 | FOLLOW_MODULE | 是 | 0/5 | 否 |
| QR_CODE | CIRCLE | 0.8 | DEFAULT | 是 | 0/5 | 否 |
| QR_CODE | CIRCLE | 0.8 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | CIRCLE | 0.8 | FOLLOW_MODULE | 是 | 0/5 | 否 |
| QR_CODE | CIRCLE | 0.85 | DEFAULT | 是 | 0/5 | 否 |
| QR_CODE | CIRCLE | 0.85 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | CIRCLE | 0.85 | FOLLOW_MODULE | 是 | 0/5 | 否 |
| QR_CODE | CIRCLE | 0.9 | DEFAULT | 是 | 0/5 | 否 |
| QR_CODE | CIRCLE | 0.9 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | CIRCLE | 0.9 | FOLLOW_MODULE | 是 | 0/5 | 否 |
| QR_CODE | CIRCLE | 0.95 | DEFAULT | 是 | 0/5 | 否 |
| QR_CODE | CIRCLE | 0.95 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | CIRCLE | 0.95 | FOLLOW_MODULE | 是 | 0/5 | 否 |
| QR_CODE | CIRCLE | 1.0 | DEFAULT | 是 | 0/5 | 否 |
| QR_CODE | CIRCLE | 1.0 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | CIRCLE | 1.0 | FOLLOW_MODULE | 是 | 0/5 | 否 |
| QR_CODE | ROUNDED | 0.5 | DEFAULT | 是 | 0/5 | 否 |
| QR_CODE | ROUNDED | 0.5 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | ROUNDED | 0.5 | FOLLOW_MODULE | 是 | 0/5 | 否 |
| QR_CODE | ROUNDED | 0.8 | DEFAULT | 是 | 0/5 | 否 |
| QR_CODE | ROUNDED | 0.8 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | ROUNDED | 0.8 | FOLLOW_MODULE | 是 | 0/5 | 否 |
| QR_CODE | ROUNDED | 0.85 | DEFAULT | 是 | 0/5 | 否 |
| QR_CODE | ROUNDED | 0.85 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | ROUNDED | 0.85 | FOLLOW_MODULE | 是 | 0/5 | 否 |
| QR_CODE | ROUNDED | 0.9 | DEFAULT | 是 | 0/5 | 否 |
| QR_CODE | ROUNDED | 0.9 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | ROUNDED | 0.9 | FOLLOW_MODULE | 是 | 0/5 | 否 |
| QR_CODE | ROUNDED | 0.95 | DEFAULT | 是 | 0/5 | 否 |
| QR_CODE | ROUNDED | 0.95 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | ROUNDED | 0.95 | FOLLOW_MODULE | 是 | 0/5 | 否 |
| QR_CODE | ROUNDED | 1.0 | DEFAULT | 是 | 0/5 | 否 |
| QR_CODE | ROUNDED | 1.0 | CIRCLE | 是 | 5/5 | 是 |
| QR_CODE | ROUNDED | 1.0 | FOLLOW_MODULE | 是 | 0/5 | 否 |

### 推荐配置

- **QR Code 稳定可用**：`moduleShape = DEFAULT`（任意填充、任意定位点）；或 `moduleShape = CIRCLE/ROUNDED` 且 `positionPatternShape = CIRCLE`。
- **PDF417 / MaxiCode / Plessey / CODABAR**：任意三样式组合均可回扫。
- **UPC/EAN Extension**：默认样式可回扫；非默认 `moduleShape` / `moduleFillRatio` 会改变外观，回扫稳定性需具体验证。
- **EAN-13 / EAN-8 / UPC-A / UPC-E**：推荐 `moduleShape = DEFAULT/ROUNDED`，`moduleFillRatio >= 0.9`；`moduleShape = CIRCLE` 时 `moduleFillRatio >= 0.9`。
- **ITF**：推荐 `moduleShape = DEFAULT/ROUNDED`，`moduleFillRatio >= 0.8`；`moduleShape = CIRCLE` 时 `moduleFillRatio >= 0.9`。
- **CODE-128 / CODE-93**：推荐 `moduleShape = DEFAULT/ROUNDED`，`moduleFillRatio >= 0.9`；CIRCLE 时建议 `moduleFillRatio = 1.0`。
- **CODE-39**：推荐 `moduleShape = DEFAULT/ROUNDED`，`moduleFillRatio >= 0.85`；CIRCLE 时建议 `moduleFillRatio = 0.95`。
- **Pharmacode**：推荐 `moduleShape = DEFAULT/ROUNDED`，`moduleFillRatio >= 0.95`。
- **MSI Plessey / Telepen**：仅推荐 `moduleFillRatio = 1.0`，任意 `moduleShape` / `positionPatternShape`。
- **Data Matrix / Aztec / Micro QR**：优先使用 `moduleShape = DEFAULT`，`moduleFillRatio` 从 0.5 起；CIRCLE/ROUNDED 不建议。
- **Han Xin Code**：`moduleShape = DEFAULT` 最稳；CIRCLE/ROUNDED 在 `moduleFillRatio >= 0.8` 时多数可用，但需测试。
- **Swiss QR Code / UPN QR Code**：推荐 `moduleShape = DEFAULT`，`moduleFillRatio >= 0.95`。
- **RSS-14 / RSS Expanded**：仅推荐 `moduleShape = DEFAULT/ROUNDED`，`moduleFillRatio = 1.0`。
