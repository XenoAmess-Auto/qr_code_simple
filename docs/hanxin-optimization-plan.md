# HanXin 编解码器专项优化计划

> 状态：Phase 1 与 Phase 2 已完成，全部测试通过。Phase 3 尚未开始。
>
> 目标：先补齐测试用例，再对 `HanXinEncoder` 和 `HanXinDecoder` 进行性能与鲁棒性优化，同时不降低扫描成功率。
>
> 优化兼顾：CI 测试时间、手机扫描帧率、生成图片质量。

---

## 项目决策

| # | 事项 | 决策 |
|---|---|---|
| 1 | 是否先补测试再优化 | 先补测试用例，再做优化 |
| 2 | 是否接受 `renderBitmap` 改用 `setPixels` | 接受 |
| 3 | 是否把 `HanXinPerformanceTest` 改为信息性测试 | 接受 |
| 4 | 是否保留 `decodePerspective` 透视回退 | 保留 |
| 5 | 优化目标 | 同时优化 CI 测试时间和手机扫描帧率 |

---

## 当前问题

### 1. HanXinDecoder 性能问题

| 瓶颈 | 位置 | 问题 |
|---|---|---|
| 版本尺寸暴力扫描 | `HanXinDecoder.kt:346-363` | `extractGrid` 从 23 到 189 步进 2 遍历 84 个版本，每个版本 4 次旋转 |
| 透视回退滥用 | `HanXinDecoder.kt:134-170` | 轴对齐但严重损坏的符号会触发 7 个尺度 × 完整网格提取 × 多次重编码验证 |
| 重编码验证 | `HanXinDecoder.kt:946-980` | 干净符号解码成功后仍走完整 `HanXinEncoder.encode()` + bitmap 渲染验证 |
| 全图暗像素凸包 | `HanXinDecoder.kt:173-234` | 收集所有暗像素再排序求凸包，大图 O(W·H) |
| 采样/旋转分配 | `HanXinDecoder.kt:365-460` | 每个候选尺寸都分配新 `IntArray`，且 4 个旋转全生成 |
| 二值化多通道 | `HanXinDecoder.kt:60-87` | 灰度、求和、暗像素计数分三趟 |
| 无快速拒绝 | `HanXinDecoder.kt:36-54` | 非汉信码图片也会跑完整 pipeline 才返回 null |

### 2. HanXinEncoder 性能问题

| 瓶颈 | 位置 | 问题 |
|---|---|---|
| 逐像素渲染 | `HanXinEncoder.kt:1608-1630` | 600×600 输出调用 36 万次 `setPixel()`，每像素两次除法 |
| 掩码评估重复 | `HanXinEncoder.kt:1381-1437` | 4 种 mask 各做一次完整 grid 复制 + `setFunctionInfo()` + `evaluate()` |
| RS 表重复重建 | `HanXinEncoder.kt:1675-1709` | 每次 `encode()` 都重建 GF(2⁸)/GF(2⁴) 对数/反对数表 |
| 罚分切片分配 | `HanXinEncoder.kt:1481-1602` | `evaluate()` 循环内 `copyOfRange(7)` 创建大量小数组 |
| 模式选择 2D 数组 | `HanXinEncoder.kt:643-753` | `Array(length) { CharArray(7) }` 内存较大 |
| 输入转换重复 | `HanXinEncoder.kt:579-637` | 非 GB18030 内容被编码两次，中间走 `MutableList` |
| 功能信息重复计算 | `HanXinEncoder.kt:1439-1479` | `applyBitmask` 里 `setFunctionInfo()` 被调用 4 次，每次重算 RS(4) |

### 3. 测试用例缺口

**Encoder 未覆盖：**
- 显式 ECC L1–L4
- 手动 mask 0–3
- ECI/UTF-8 内容（emoji、日文、阿拉伯文）
- 四字节 GB18030
- 版本边界 / 最大容量
- 非法输入（空内容、超长）
- Text submode 切换

**Decoder 未覆盖：**
- 非汉信码图片的拒绝（随机噪声、QR、空白）
- finder 图案损坏
- 功能信息区损坏（在/超出 RS 预算）
- 4 种 mask 手动解码
- 最大纠错预算
- 空内容 / 单字符 / 超大内容

**脆弱测试：**
- `HanXinPerformanceTest` 固定墙钟阈值，CI 易 flake
- `HanXinEncoderTest` 中 `Reed-Solomon GF 2^4 generator roots` 只 assertTrue，未真正验证
- `HanXinDecoderExternalTest` 资源缺失时可能空跑通过
- `HanXinDecoderRobustnessTest` 中 salt-and-pepper 比例接近 RS 预算，易因渲染改动失败

---

## Phase 1：测试补强（必须先完成）

### 1.1 新建 `HanXinEncoderAdvancedTest.kt`

位置：`app/src/test/java/com/xenoamess/qrcodesimple/decoder/hanxin/HanXinEncoderAdvancedTest.kt`

新增用例：
- `encodeWithExplicitEccLevels_roundtrips`：L1–L4 分别生成并解码
- `encodeWithAllMasks_decodes`：mask 0–3 分别生成并解码
- `encodeUtf8Content_withEci_roundtrips`：emoji/日文等 UTF-8 ECI 回环
- `versionBoundary_justFitsVersion1`：刚好塞入 version 1
- `versionBoundary_overflowsToVersion2`：刚好溢出到 version 2
- `rejectEmptyContent`：空内容返回 null
- `rejectContentExceedingMaxCapacity`：超长内容返回 null
- `encodeFourByteGb18030Content_roundtrips`：四字节 GB18030 字符回环
- `encodeTextSubmodeSwitchingContent_roundtrips`：触发 Text submode 切换的内容回环

### 1.2 新建 `HanXinDecoderFailureTest.kt`

位置：`app/src/test/java/com/xenoamess/qrcodesimple/decoder/hanxin/HanXinDecoderFailureTest.kt`

新增用例：
- `decodeRandomNoiseBitmap_returnsNull`
- `decodeQrCodeBitmap_returnsNull`
- `decodeBlankBitmap_returnsNull`
- `decodeDamagedFinderPatterns_returnsNull`
- `decodeFunctionInfoCorruptionWithinBudget_recovers`
- `decodeFunctionInfoCorruptionBeyondBudget_returnsNull`
- `decodeAllGeneratedMasks`：mask 0–3 生成图都能解码

### 1.3 加固现有测试

| 文件 | 改动 |
|---|---|
| `HanXinDecoderInternalTest.kt` | 新增 `decodeAtMaximumCorrectionBudget` |
| `HanXinEncoderTest.kt` | 把 `Reed-Solomon GF 2^4 generator roots` 改为真正验证根是否为 0 |
| `HanXinDecoderExternalTest.kt` | 资源文件缺失时失败，避免空跑 |
| `HanXinPerformanceTest.kt` | 墙钟阈值改为信息性输出，不 gate build |
| `BarcodeGenerationRoundtripTest.kt` | 新增 `hanXinRoundtripThroughScannerPipeline` |

### 1.4 验收标准

```bash
JAVA_HOME=.../jdk-21 ./gradlew :app:testDebugUnitTest -PexcludeExtendedUiTests
```

- 所有现有测试通过
- 新增测试全部通过
- 无 stderr 污染

### 1.5 完成记录

- Phase 1 已完成并提交。
- 额外修复：`HanXinDecoder.decodeFunctionInfo()` 从仅 syndrome 检查升级为 RS(4) 纠错，使功能信息区在预算内损坏可恢复。
- 全量单测通过命令：`JAVA_HOME=/home/xenoamess/.jdks/jdk-21.0.7+6 ./gradlew :app:testDebugUnitTest -PexcludeExtendedUiTests`。

---

## Phase 2：HanXinEncoder 优化

按顺序执行：

1. **缓存 RS 单例与生成多项式**
   - 缓存 `ReedSolomon(0x163, 8)` 和 `ReedSolomon(0x13, 4)`
   - 对常用 `(eccLength, firstRoot)` 缓存 generator

2. **重构掩码评估 + 功能信息放置**
   - 只在数据区评估惩罚
   - 选定 mask 后一次性放置 function info

3. **优化 `renderBitmap()`**
   - 用 `setPixels` 批量写入
   - 预计算 `moduleWidth/moduleHeight` 的逆，避免每像素除法

4. **优化 `evaluate()`**
   - 去掉 `copyOfRange(7)`，直接按位比较

5. **清理 `convertInput()` 和 `chooseModes()`**
   - 一次编码、直接 `IntArray`、primitive 前驱数组

### 2.5 完成记录

- Phase 2 已完成并提交。
- 主要改动：`ReedSolomon` 增加 `initCodeCached` 缓存生成多项式；编码器复用 `rs8`/`rs4` 单例；掩码评估跳过 function info 重算；`renderBitmap` 改用 `setPixels`；`evaluate` 去掉 `copyOfRange`；`convertInput` 与 `chooseModes` 减少临时分配。
- 全量单测通过命令：`JAVA_HOME=/home/xenoamess/.jdks/jdk-21.0.7+6 ./gradlew :app:testDebugUnitTest -PexcludeExtendedUiTests`。

---

## Phase 3：HanXinDecoder 优化

按顺序执行：

1. **版本尺寸估计窗口**
   - 根据 `min(width, height)` 估算版本，优先尝试 `±2` 窗口，失败再全扫描

2. **验证减负**
   - 用 RS syndrome 检查替代 `validateByReencoding` 中的完整 `encode()`

3. **透视回退瘦身**
   - 每个尺度先做 finder 预检，不匹配直接跳过
   - 匹配后只做一次完整 decode
   - 直接通过逆 homography 采样模块，避免中间 warp bitmap

4. **角点检测轻量化**
   - 用图像轮廓/边界极值代替全图暗像素凸包

5. **内存和采样细节**
   - `sampleGrid`/`rotateGrid` buffer 复用、按需旋转
   - `binarize` 单通道化

---

## Phase 4：验证与性能门禁

1. 跑全量单测：`./gradlew :app:testDebugUnitTest -PexcludeExtendedUiTests`
2. 跑 extended UI 测试（如相关）
3. 记录 `HanXinPerformanceTest` 优化前后耗时，确保不 regress
4. 在 `BarcodeGenerationRoundtripTest` 中验证 Han Xin 回环
5. 验证 `QRCodeScanner.scanSync` 管道对 Han Xin 的识别率

---

## 风险与应对

| 风险 | 应对 |
|---|---|
| 渲染改动导致像素级偏差 | Phase 1 补像素级/回环测试；优化后全量回归 |
| RS 缓存引入可重入问题 | 使用单例 + 同步或线程局部状态 |
| 掩码评估改动影响自动选择 | 新增 mask 0–3 测试覆盖 demasking |
| 版本估计窗口漏掉真实版本 | 保留全扫描 fallback |
| 透视回退瘦身导致畸变图失败 | 外部图片测试 + robustness 测试必须继续通过 |
| 性能测试阈值 flake | 改为信息性输出，不 gate build |

---

## 创建日期

2026-07-09
