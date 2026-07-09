# JUnit 5 迁移计划

## 目标

- 将测试框架从 JUnit 4 迁移到 JUnit 5 Platform。
- 保留现有 JUnit 4 / Robolectric 测试继续运行。
- 新测试可以逐步使用 JUnit 5 Jupiter 注解。

## 当前状态

- 项目使用 `junit:junit:4.13.2` + `Robolectric 4.16.1`。
- 所有测试都是 JVM 单元测试，没有 `androidTest` 目录。
- Robolectric 官方没有发布 JUnit 5 extension（Maven Central 没有 `org.robolectric:robolectric-junit5` 这个 artifact）。

## 迁移方案

- 使用 **JUnit 5 Platform** + **JUnit Vintage Engine** 运行旧的 JUnit 4 / Robolectric 测试。
- 新增的纯 JVM 测试逐步使用 JUnit 5 Jupiter 注解。
- 必须继续使用 JUnit 4 的 Robolectric 测试保持 `@RunWith(RobolectricTestRunner::class)`。

## 实施步骤

1. **依赖调整**
   - 将 `org.jetbrains.kotlin:kotlin-test` 替换为 `org.jetbrains.kotlin:kotlin-test-junit5`。
   - 添加 `org.junit.jupiter:junit-jupiter:5.13.0`（包含 API、Params、Engine）。
   - 添加 `org.junit.vintage:junit-vintage-engine:5.13.0`。
   - 保留 `junit:junit:4.13.2` 供 Robolectric 使用。

2. **Gradle 配置**
   - 在 `app/build.gradle` 的 `testOptions.unitTests.all { ... }` 中启用 `useJUnitPlatform()`。

3. **验证**
   - 运行 `./gradlew :app:testDebugUnitTest` 确认所有现有测试通过。

4. **逐步迁移**
   - 将非 Robolectric 的纯 JVM 测试从 JUnit 4 注解迁移到 JUnit 5 Jupiter 注解。
   - 迁移完成后，全局测试日志可以通过 JUnit 5 的 `TestExecutionListener` + ServiceLoader 实现，真正“走 JUnit 框架”解决。

## 注意事项

- JUnit 4 的 `@Rule` 需要替换为 JUnit 5 的 `@RegisterExtension`。
- `@Before` / `@After` 需要改为 `@BeforeEach` / `@AfterEach`。
- Robolectric 测试因为 runner 依赖，暂时不能改注解；它们会通过 Vintage Engine 继续运行。
- 迁移前建议先完成“测试日志”短平快任务，便于定位迁移过程中可能出现的新失败。
