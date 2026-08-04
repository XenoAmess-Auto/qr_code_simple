# 版本、发布与应用更新系统

本文以 `app/build.gradle`、`.github/workflows/build.yml`、`.github/workflows/release.yml` 和更新客户端实现为准，说明 Git 版本模型、Stable/Beta 发布、`version.json`、签名连续性及首轮发布操作。

## 1. Git 版本模型

所有 Gradle 任务都会在配置阶段读取 Git，因此必须在包含 Git 元数据的、完整且非浅克隆的仓库中运行。源码压缩包不能构建；浅克隆必须先执行：

```bash
git fetch --unshallow --tags
```

| 项目 | 规则 |
|---|---|
| `versionCode` | `git rev-list --count HEAD`。完整历史保证该值随提交历史单调增长。 |
| `versionName` | 从 `git describe --tags --long --match v*` 的最近标签推导。该最近 `v*` 标签必须严格匹配 `vMAJOR.MINOR.PATCH`。正好位于标签时为 `MAJOR.MINOR.PATCH`，领先 `N` 个提交时为 `MAJOR.MINOR.PATCH+N`。 |
| 无匹配 `v*` 标签 | 回退为 `0.0.0+<versionCode>`。 |
| 不合法标签 | 若最近的 `v*` 标签不是严格 `vMAJOR.MINOR.PATCH`，构建失败，不会静默回退。 |
| `BuildConfig.GIT_HASH` | `HEAD` 的八位短 hash。 |
| CI 元数据 | `:app:writeVersionInfo` 写出 `app/build/generated/version-info/version.json`，包含 `versionCode`、`versionName`、`gitHash`。 |

`preBuild` 依赖 `generateChangelog`。该任务按创建时间倒序枚举 `v*` 标签，每个标签写入日期和最多 20 条提交主题，生成 `CHANGELOG.txt` 并作为 app asset 打包；About 页的“版本历史”读取该文件。没有匹配标签时，asset 会明确说明没有可用的 `v*` 标签。

## 2. Stable 发布

`release.yml` 在推送 `v*` 标签时触发，但第一步会拒绝不严格的标签。通过的 Stable 标签必须同时满足：

- 标签名精确为 `vMAJOR.MINOR.PATCH`。
- 标签所指的提交等于工作流运行时的当前 `origin/master`。
- Gradle 推导出的 `versionName` 等于去掉 `v` 的标签名。

工作流使用完整 checkout，执行以下发布前验证：

```bash
./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:jacocoTestReport :app:jacocoTestCoverageVerification -PexcludeExtendedUiTests
```

验证通过后，工作流要求以下 GitHub Actions secrets，缺少任一项即失败：

| Secret | 用途 |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | Base64 编码的发布密钥库，工作流解码为临时 keystore。 |
| `RELEASE_KEYSTORE_PASSWORD` | 密钥库和 key 的密码。 |
| `RELEASE_KEYSTORE_ALIAS` | 用于签名的 alias。 |

使用该密钥构建 release APK、AAB 和版本信息后，GitHub Release 包含：

| 资产 | 说明 |
|---|---|
| `qr-code-simple-<version>.apk` | canonical Stable APK，也是客户端优先选择的文件。 |
| `qr-code-simple-<version>.aab` | canonical Stable AAB。 |
| `version.json` | Stable 更新元数据。 |
| `app-release.apk` | canonical APK 的兼容别名，不是新的独立构建。 |
| `patch-<fromCode>-to-<toCode>.bspatch` | 可选的 Stable 增量补丁；没有可用历史、工具或补丁不划算时可以不存在。 |

增量补丁生成失败不会使完整 APK/AAB 或 `version.json` 失效。Stable workflow 在创建 Release 前会再次确认 `version.json` 指向实际存在的 canonical APK，且 APK 的 SHA-256、字节数和标签版本一致。

## 3. Beta 通道和 GitHub Pages

`build.yml` 的 `beta` job 只在推送 `master` 时运行，且依赖 `build` 与 `android-test`：

- `build` 执行 debug 构建、JVM/Robolectric 单元测试、lint、JaCoCo 报告与覆盖率门禁。
- `android-test` 在 API 35 模拟器上运行 `:app:connectedDebugAndroidTest`，失败时最多重试三次。
- 只有两个 job 均成功，Beta 才会要求同一组发布签名 secrets，并构建 release-signed APK。

Beta 发布到 GitHub Pages 的固定路径：

```text
https://xenoamess-auto.github.io/qr_code_simple/beta/version.json
https://xenoamess-auto.github.io/qr_code_simple/beta/qr-code-simple-beta.apk
```

Pages 的同一部署仍会生成并保留：

```text
/coverage.html
/coverage.json
```

因此 Beta 发布依赖签名 secrets；签名配置缺失会阻止 Beta job，也会使依赖该 job 的 Pages 部署无法发布新的覆盖率内容。

### Beta 存档和补丁

`build_beta_delta_chains.py` 维护名为 `beta-archive` 的 GitHub **prerelease**。它固定为 prerelease，使 GitHub 的 `releases/latest` 继续只代表 Stable；首次创建时以仓库根提交为目标。

- 存档最多保留 8 个已签名 Beta 基础 APK 和其历史元数据。
- 新 Beta 会尝试针对最近第 1、2、4 个基础版本生成直接 `bsdiff` 补丁；补丁必须能回放到目标 APK、SHA-256 正确且小于完整 APK。
- 存档元数据会在可用时生成扁平的多跳升级链。补丁和链都是优化，完整 Pages APK 及其 SHA-256/大小始终是可用的回退路径。

### 首个标签前的 Beta

如果仓库尚无任何匹配 `v*` 标签，Beta 的 `versionName` 会是 `0.0.0+<commit-count>`。本仓库在首次 `v0.2.6` 之前仍会匹配历史 `v0.1.4`，因此会暂时显示为 `0.1.4+N`。两种情形都只是迁移期 Beta 标识：不要将其分发为 Stable release baseline，也不要用它替代带 canonical Stable 资产和 `version.json` 的正式发布基线。

## 4. `version.json` schema

Stable 和 Beta 都发布 JSON 对象。下列字段是客户端接受元数据的安全必需项：

| 字段 | 类型和限制 | 用途 |
|---|---|---|
| `versionCode` | 正整数 JSON 数字 | 主要的更新顺序。 |
| `versionName` | 语义版本字符串 | 显示及 `versionCode` 相同情况下的比较。生成器产出 `MAJOR.MINOR.PATCH` 或 `MAJOR.MINOR.PATCH+N`。 |
| `apkSha256` | 64 位十六进制 SHA-256 | 下载及补丁结果校验。 |
| `apkSize` | 正整数，且不超过 512 MiB | 下载精确字节数和内存/磁盘边界。 |

`apkSha256` 和 `apkSize` 在本仓库不是可选兼容字段。缺少、格式不正确或超过上限的 metadata 会被拒绝，不能被当作“已经是最新”。

工作流还会写入或验证以下发布字段：

| 字段 | Stable | Beta | 说明 |
|---|:---:|:---:|---|
| `apkFile` | 是 | 是 | Stable 为 `qr-code-simple-<version>.apk`；Beta 为 `qr-code-simple-beta.apk`。 |
| `gitHash` | 是 | 是 | 八位 Git hash，由 `writeVersionInfo` 提供。 |
| `changelog` | 是 | 是 | 最多 20 条提交主题形成的文本。 |
| `releaseTag` | 是 | - | 严格 Stable tag，例如 `v0.2.6`。 |
| `aabFile` | 是 | - | Stable canonical AAB 文件名。 |
| `channel` | - | 是 | 值为 `beta`。 |
| `patches` | 可选 | 可选 | 当前目标可用的直接补丁清单。 |
| `chains` | 可选 | 可选 | 从旧 `versionCode` 到当前目标的已验证升级链。 |

典型 Stable manifest 的结构如下，hash 和大小仅为示意：

```json
{
  "aabFile": "qr-code-simple-0.2.6.aab",
  "apkFile": "qr-code-simple-0.2.6.apk",
  "apkSha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "apkSize": 123456789,
  "changelog": "fix: verified updates",
  "gitHash": "0123abcd",
  "releaseTag": "v0.2.6",
  "versionCode": 123,
  "versionName": "0.2.6"
}
```

Beta 使用同一组安全必需字段，但 `apkFile` 为 `qr-code-simple-beta.apk`，并额外包含 `"channel": "beta"`；没有 `releaseTag` 或 `aabFile`。

### 增量链字段

`chains` 以来源 `versionCode` 的字符串为键。每条链包含：

```json
{
  "fromApkSha256": "<installed-apk-sha256>",
  "totalSize": 345678,
  "hops": [
    {
      "toVersionCode": 123,
      "url": "https://github.com/XenoAmess-Auto/qr_code_simple/releases/download/v0.2.6/patch-122-to-123.bspatch",
      "size": 345678,
      "patchSha256": "<patch-sha256>",
      "resultSha256": "<resulting-apk-sha256>"
    }
  ]
}
```

客户端最多接受 16 跳。每一跳的目标 code 必须递增、累计 `size` 必须等于 `totalSize`、最后一跳结果 hash 必须等于完整 APK hash，且补丁 URL 必须是受信 GitHub Release 下载地址。`patches` 是发布端的直接补丁清单；客户端以完整校验后的 `chains` 决定是否尝试增量传输。

## 5. 客户端更新行为

### Stable 与 Beta 的元数据来源

| 通道 | 元数据来源 | APK 选择 |
|---|---|---|
| Stable | GitHub `releases/latest` API，再读取该 Release 的 `version.json` asset | 标签必须等于 `v<versionName>`；优先匹配 `qr-code-simple-<versionName>.apk`。只有没有任何 canonical APK 候选的旧 Release 才可使用 `app-release.apk` 兼容别名。 |
| Beta | 固定 GitHub Pages `/beta/version.json` | 固定 GitHub Pages `/beta/qr-code-simple-beta.apk`。 |

客户端只接受受信 HTTPS 初始地址及允许的重定向终点。Stable Release metadata、canonical APK 和 `version.json` 必须相互一致，客户端不会从任意资产文件名推断版本。

`versionCode` 是主要的新旧判断依据；仅当两个 code 相同时，才比较 `versionName` 的 major/minor/patch。Beta 存档为 prerelease，因此不会被 GitHub `releases/latest` 当作 Stable。

### 用户入口

- `QRCodeApp.isAppUpdateAutoCheckEnabled()` 默认返回 `false`。`MainActivity` 仅在用户开启 About 页的自动检查开关后，以 24 小时节流检查 **Stable**。
- About 页的“检查更新”按钮可以手动检查 Stable。
- About 页的“检查 Beta 版更新”按钮是 Beta 唯一入口。Beta 没有自动检查，也不会因 Stable 自动检查而被查询。

### 下载、身份和签名校验

下载 APK 或补丁前后都执行边界检查：metadata 最大 1 MiB，APK/补丁声明大小最大 512 MiB；完整 APK 下载到 `.part` 文件后必须同时满足精确字节数和 SHA-256，才替换目标文件。

在调用系统安装器前，`ApkArchiveVerifier` 会读取已安装 app 与下载 archive 的身份，要求以下内容全部一致：

- 包名必须为当前应用包名。
- archive 的 `versionCode` 必须等于 metadata 的目标 `versionCode`。
- archive 的 APK 内容签名证书 SHA-256 集合必须与已安装应用完全相同，且不能为空。

因此，即使文件大小和 hash 正确，错误包名、错误版本或不同签名的 APK 也不会由应用内更新流程安装。Stable 安装失败时可打开 Release 页面；Beta 没有 Stable Release 页面回退。

### 增量更新与完整下载回退

增量链只是一项可选优化。只有在以下条件都满足时才会使用：

- 已安装 APK 的 SHA-256 等于 `fromApkSha256`。
- 补丁链总下载量小于完整 APK。
- 已安装 APK 与补丁输入的组合严格低于 64 MiB 安全上限。
- 每个补丁的大小、SHA-256、每一跳输出 SHA-256，以及最终 APK SHA-256 均通过校验。

补丁实现会将 base APK 和 patch 读入内存，所以 `ApkPatcher.MAX_INCREMENTAL_INPUT_BYTES` 固定为 64 MiB。当前体积较大的 APK 会直接走已校验的完整下载，而不会冒内存压力风险。链缺失、基础 hash 不匹配、补丁不够小、输入过大、下载失败或任一回放校验失败时，更新器都会清理临时文件并回退完整 APK。

## 6. 签名连续性

本地 release 构建可以通过 `RELEASE_KEYSTORE_FILE`、`RELEASE_KEYSTORE_PASSWORD`、`RELEASE_KEYSTORE_ALIAS` 环境变量或 Gradle 属性提供正式签名；未配置时构建脚本会回退 debug 签名，便于本地开发。

该本地回退不适用于发布：Stable 和 Beta CI 都要求三项 `RELEASE_KEYSTORE_*` secrets。必须长期保留并复用同一发布密钥和 alias，特别是不能让 Beta 与 Stable 使用不同签名。否则 Android 无法覆盖安装，且客户端的 archive 签名集合校验会在启动安装器前拒绝更新。

CI 的 `debug-apk` artifact 仅用于构建/测试诊断，**不包含也不上传** `debug-keystore` artifact。它不能作为官方 Stable/Beta 更新基线；更换签名时用户只能卸载旧 app 后重新安装，并会失去本地 app 数据。

## 7. 首轮发布：`v0.2.6`

本轮选择的首个新系统 Stable 发布是 `v0.2.6`。必须先发布 commit，再打标签，顺序如下：

```bash
# 在包含目标发布提交的本地 master 上
git push origin master

# 在 GitHub 上等待该 master push 的 build 和 android-test 成功，
# 并确认没有其他提交推进 origin/master。
git fetch --tags origin master
git rev-parse origin/master

# 为当前 origin/master 的同一提交创建严格标签并推送。
git tag v0.2.6 origin/master
git push origin v0.2.6
```

标签工作流会再次从远端读取 `origin/master` 并比较提交 SHA。因此在打标签与 workflow 校验之间若 `master` 又前进，Stable 发布会被拒绝；应重新确认目标提交，而不是为旧提交强行重跑发布。

发布后应确认：

- GitHub Release 中存在 canonical APK、canonical AAB、`version.json` 和 `app-release.apk` 别名。
- `version.json` 的 `versionName` 为 `0.2.6`，`releaseTag` 为 `v0.2.6`，并且 `apkSha256`、`apkSize` 与 canonical APK 一致。
- Pages 的 Beta URL 可取得最新的 release-signed APK 和相符 metadata，`coverage.html` 与 `coverage.json` 仍可访问。
- 用同一发布签名的已安装 app 执行一次 Stable/Beta 更新检查和安装前验证；不要用首个无标签回退 Beta 作为 Stable 分发基线。

测试命令、metadata 单测和手工 SHA/大小核验方法见 [`testing-strategy.md`](testing-strategy.md)。
