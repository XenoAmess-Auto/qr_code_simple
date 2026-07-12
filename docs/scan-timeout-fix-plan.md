# QRCodeScanner 超时问题修复计划

## 1. 问题根因

`QRCodeScanner.scanSync` 在内部调用 `scan()`，而 `scanAsFlow()` 使用 `coroutineScope { ... allDeferred.awaitAll() }` 等待所有扫描引擎完成。

虽然代码里已经写了 `withTimeoutOrNull(config.totalTimeoutMs)` 和 `withTimeoutOrNull(perEngineTimeoutMs)`，但协程取消只能取消“可挂起”的代码。对于以下阻塞引擎，取消信号不会中断实际线程：

- **ZXing** 的 `MultiFormatReader.decode()` 是同步 CPU 调用，且不检查协程取消状态。
- **WeChatQRCode** 的 `WeChatQRCodeDetector.detectAndDecode()` 是 native 调用。
- **BoofCV** 的 `MicroQrCodeDetector.process()` 是同步 CPU 调用。
- **ML Kit** 虽然使用 `suspendCancellableCoroutine`，但取消回调中同步调用 `scanner.close()`，在 Robolectric/CI 下 `close()` 可能阻塞，把取消流程卡住。

因此当某个引擎卡住时，`coroutineScope` 会无限等待，导致 `scanSync` 挂起，最终触发 GitHub Actions 60 分钟 job 超时。

## 2. 修复目标

让 `scanSync` 在 `totalTimeoutMs`（camera 模式 15s，image 模式 120s）后**必定返回**，不再因为某个阻塞引擎而无限挂起；同时尽量让我们自己的引擎支持安全取消。

## 3. 引擎分类

| 引擎 | 是否支持安全取消 | 处理策略 |
|------|------------------|----------|
| ZXing | 不支持 | 超时后 abandon |
| WeChatQRCode | 不支持 | 超时后 abandon |
| BoofCV Micro QR | 不支持 | 超时后 abandon |
| ML Kit | 基本支持，但 `close()` 有阻塞风险 | 移除取消回调里的同步 `close()`，保留 `onComplete` 关闭 |
| Han Xin | 不支持（我们自己的代码） | 加入 `ensureActive()` 检查，支持安全取消 |
| Custom Linear | 不支持（我们自己的代码） | 加入 `ensureActive()` 检查，支持安全取消 |

## 4. 具体改动

### 4.1 `QRCodeScanner.kt` — 重构 `scanAsFlow`

- 废弃 `coroutineScope { ... allDeferred.awaitAll() }` 结构。
- 创建独立的 `CoroutineScope(SupervisorJob() + Dispatchers.IO)` 启动所有引擎。
  - 使用 `Dispatchers.IO` 而不是 `Dispatchers.Default`，防止阻塞型/不响应取消的引擎占满 Default 线程池，导致后续 `scanSync` 的 `runBlocking(Dispatchers.Default)` 因线程饥饿而挂起。这是 firebase-bom 34.16.0 在 CI 上诱发超时的新根因。
- 引入 `sealed class EngineEvent { Result, Completed }` 和一个 `Channel<EngineEvent>`。
- 每个引擎在独立 scope 中执行：
  - 调用 `runEngineSafely(...)` 获取结果；
  - 通过 `trySend` 发送 `Result` 事件；
  - 在 `finally` 中发送 `Completed` 事件。
- 在 `withTimeoutOrNull(config.totalTimeoutMs)` 内循环接收事件：
  - 收到 `Result` 时去重并 emit；
  - 收到所有引擎的 `Completed` 时提前退出；
  - 超时后直接退出，不再等待未完成引擎。
- `finally` 中：
  - 取消 engine scope；
  - 关闭 event channel；
  - **仅当所有引擎都自然完成时**才 recycle `processedBitmap`；否则放弃回收，避免 abandon 引擎访问已释放 Bitmap。

### 4.2 `QRCodeScanner.kt` — 修复 `scanWithMLKit`

- 移除 `invokeOnCancellation { scanner.close() }` 中的同步 `close()`，避免取消路径被阻塞。
- 保留 `onComplete { scanner.close() }`，让任务自然结束时释放资源。

### 4.3 `QRCodeScanner.kt` — 给 `scanSync` 加外层保险

- 在 `runBlocking` 内再包一层 `withTimeout(config.totalTimeoutMs + 5_000)`，确保调用方永远不会永久阻塞。

### 4.4 给我们自己的引擎加入取消检查

- `decoder/hanxin/HanXinDecoder.kt`：在 `extractGrid` 外层循环、`decodePerspective` 的 scale 循环、`rsDecode` 等耗时路径中插入 `kotlinx.coroutines.ensureActive()`。
- `decoder/CustomLinearBarcodeScanner.kt` 及子解码器：在 `PharmacodeDecoder`、`PlesseyDecoder`、`MsiPlesseyDecoder`、`TelepenDecoder` 的循环中加入 `ensureActive()`。

### 4.5 不改造的第三方引擎

- **ZXing**、**WeChatQRCode**、**BoofCV Micro QR**：保持原样，超时后直接 abandon。

## 5. 验证计划

1. 本地运行 `./gradlew :app:testDebugUnitTest` 全量通过。
2. 多次单独运行 `AdvancedBarcodeGeneratorTest.Chinese sentence...`。
3. 用 `taskset -c 0` 限制单核再跑，验证低资源下不再挂死。
4. 重新触发 core-ktx PR 的 CI，确认 60 分钟超时消失。

## 6. 补充：firebase-bom 34.16.0 触发的线程饥饿

### 6.1 现象

- 升级到 `com.google.firebase:firebase-bom:34.16.0` 后，CI 间歇性 60 分钟超时。
- 在启用 CI 标准输出日志后，发现超时前最后一个 `START TEST` 常是 `AdvancedBarcodeGeneratorTest > Chinese sentence ... roundtrips for 2D formats`，但该测试本身无错；真正原因是前面的扫描请求已让 `Dispatchers.Default` 被阻塞引擎占满，导致新的 `scanSync` 无法分配到线程而挂起。

### 6.2 根因

- `scanAsFlow` 的引擎 scope 原来使用 `Dispatchers.Default`。
- `scanSync` 使用 `runBlocking(Dispatchers.Default)` 等待 `scanAsFlow` 结果。
- 当引擎任务（特别是 ML Kit 等不响应取消的阻塞任务）占用 Default 线程池后，`runBlocking` 无法获得线程，形成死锁/饥饿。
- GitHub Actions runner 通常只有 2 核，Default 线程池更小，比本地多核机器更容易触发。

### 6.3 修复

- 将引擎 scope 的 dispatcher 改为 `Dispatchers.IO`。
- `Dispatchers.IO` 与 `Dispatchers.Default` 共享统一调度器但允许更多线程，适合阻塞型任务，不会饿死 Default 池。
- `scanSync` 继续保留在 `Dispatchers.Default`，不再和阻塞引擎竞争线程。

## 7. 权衡与已知限制

- 使用 `Dispatchers.IO` 后，阻塞引擎仍可能占用 IO 线程，但 IO 池更大，不会阻塞 `scanSync` 的 Default 线程。
- 第三方阻塞引擎（ZXing/WeChatQRCode/BoofCV）超时后会被 abandon，可能继续占用后台线程并短暂持有 Bitmap。这是为了彻底避免 CI 挂死而做的取舍。
- 我们自己的 HanXin 和 CustomLinear 解码器会支持安全取消。
- ML Kit 取消时不再调用可能阻塞的 `close()`，依赖 `onComplete` 做资源释放；如果任务永远不完成，资源可能泄漏，但测试不会再挂死。
