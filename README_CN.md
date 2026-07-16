# QR Code Simple

[English](README.md) | [中文](README_CN.md)

[![Coverage](https://img.shields.io/endpoint?url=https://xenoamess-auto.github.io/qr_code_simple/coverage.json)](https://xenoamess-auto.github.io/qr_code_simple/coverage.html)

一款功能丰富的 Android 二维码/条码扫描与生成应用。

---

## 功能特性

### 核心功能

- ✅ **50+ 种条码格式** - 支持 QR Code、Data Matrix（含中文/UTF-8）、Aztec、PDF417、汉信码（Han Xin Code）、MaxiCode、Micro QR、Code 128/39/93、EAN-13/8、UPC-A/E、Codabar、ITF、Pharmacode、Plessey、MSI Plessey、Telepen、RSS-14、RSS Expanded、UPC/EAN Extension 等可扫描格式的扫描与生成，同时支持大量 OkapiBarcode 仅生成格式（Code 2 of 5 系列、邮政码、Codablock F、Grid Matrix、Code One 等）。
- ✅ **智能内容解析** - 自动识别 WiFi、联系人、日历、邮件、URL、地理位置等，提供一键操作。
- ✅ **批量生成** - 从 CSV 或 Excel 导入数据批量生成条码，支持 ZIP 导出。
- ✅ **样式定制** - 前景/背景色、多段渐变、中心 Logo、模块形状（方形/圆点/圆角）、定位图案、圆角比例、纠错等级。
- ✅ **二维码修复** - 识别失败时自动进行图像修复重试（灰度 / 对比度 / 二值化等变体）。

### 历史记录

- ✅ **全文搜索** - 按内容、时间、类型搜索。
- ✅ **智能分类** - 自动归类为链接、文本、WiFi、联系人等。
- ✅ **收藏 / 置顶** - 标记重要内容。
- ✅ **标签系统** - 自定义标签管理。
- ✅ **导入 / 导出** - JSON / CSV 备份，支持密码加密的备份文件（AES-256-GCM + PBKDF2）。
- ✅ **保留策略** - 自动清理 30 / 90 / 365 天前的历史记录（收藏保留）。

### 扫描体验

- ✅ **连续扫描模式** - 无需确认连续扫描多个码。
- ✅ **震动反馈** - 识别成功时震动提示。
- ✅ **智能 / 点击对焦** - 根据码大小自动对焦，支持点击对焦。
- ✅ **扫描区域限定** - 开启框选模式后拖动选择区域，仅识别区域内条码。
- ✅ **视频扫描** - 直接从视频文件中解码条码。
- ✅ **分享扫描** - 从任意应用（相册、文件管理器等）分享图片或视频到本应用直接识别。

### 分享与导出

- ✅ **矢量导出** - SVG 格式（无损放大）。
- ✅ **分享模板** - 生成带说明文字的分享图片。
- ✅ **分享生成** - 从任意应用分享纯文本到本应用，自动预填并生成条码。

### 安全与隐私

- ✅ **恶意链接检测** - 本地黑名单 + URL 可疑特征分析，支持可选（默认关闭）的静默在线黑名单更新。
- ✅ **隐私模式** - 无痕扫描，不写入历史记录。
- ✅ **应用锁** - 指纹 / 密码保护敏感历史。
- ✅ **本地加密** - SQLCipher (AES-256) 加密历史数据库。

> **隐私说明**：应用唯一的网络权限（`INTERNET`）仅用于可选的、默认关闭的黑名单在线更新；其余功能完全离线可用。

### 界面与体验

- ✅ **Material You** - Android 12+ 动态取色。
- ✅ **横屏与平板** - 横屏布局优化；平板（sw600dp+）历史页列表-详情双栏。
- ✅ **快捷方式** - 长按图标快速扫码或生成。
- ✅ **快捷设置磁贴** - 下拉栏一键进入相机扫描。
- ✅ **桌面小组件** - 快速扫描、快速生成小组件。
- ✅ **国际化** - 英文、简体中文、日语、韩语、德语完整翻译。
- ✅ **动画** - 页面过渡、扫描线动画。

### 技术特性

- ✅ **单元测试** - 可扫描格式均提供基于 Robolectric 的 roundtrip 测试，仅生成格式均提供生成成功测试。
- ✅ **大图加载优化** - 大图加载内存优化。

---

## 支持的条码格式

应用支持 **50+ 种条码格式** 的生成，其中可扫描格式能通过应用自身的扫描器完成回环识别。

### 二维码

| 格式 | 扫描 | 生成 | 简介 |
|------|:----:|:----:|------|
| **QR Code** | ✅ | ✅ | 最常见的二维码，广泛用于支付、网址、名片和 WiFi 共享。 |
| **Data Matrix** | ✅ | ✅ | 可在极小空间存储数据，支持通过 ECI 编码中文等 Unicode 内容。 |
| **Aztec Code** | ✅ | ✅ | 无需静音区即可识别，常用于火车票、登机牌等场景。 |
| **PDF417** | ✅ | ✅ | 堆叠式线性条码，可存储大量文本与二进制数据，用于身份证、驾照和快递面单。 |
| **MaxiCode** | ✅ | ✅ | UPS 开发的固定大小二维条码，用于国际物流和航空货运。 |
| **Micro QR Code** | ✅ | ✅ | 微型 QR 码，用于极小空间的标识。 |
| **Han Xin Code（汉信码）** | ✅ | ✅ | 汉信码（GB/T 36527），支持中文与 ECI 的国产二维矩阵码。应用内显示名为 `Han Xin`。 |
| **Swiss QR Code** | - | ✅ | 瑞士 QR-bill 支付二维码。 |
| **UPN QR Code** | - | ✅ | 斯洛文尼亚 UPN 支付二维码。 |
| **Aztec Rune** | - | ✅ | 固定尺寸的 Aztec 小符号，可编码 0-255 的整数值。 |
| **Code One** | - | ✅ | Code One 二维矩阵码家族；受 OkapiBarcode 编码器限制，仅生成。 |
| **Grid Matrix** | - | ✅ | 国产 Grid Matrix 二维矩阵码；需至少包含一个非 ASCII 字符（通常为中文）。 |

### 一维条码

| 格式 | 扫描 | 生成 | 简介 |
|------|:----:|:----:|------|
| **Code 128** | ✅ | ✅ | 高密度字母数字编码，广泛用于物流与供应链。 |
| **Code 39** | ✅ | ✅ | 支持数字、大写字母及部分符号，常用于工业和军事领域。 |
| **Code 39 Extended** | - | ✅ | 支持完整 ASCII 的 Code 39 扩展版。 |
| **Code 93** | ✅ | ✅ | Code 39 的高密度改进版，常用于物流和工业场景。 |
| **EAN-13** | ✅ | ✅ | 13 位欧洲商品编码，是大多数国家零售商品的标准条码。 |
| **EAN-8** | ✅ | ✅ | EAN-13 的短版，用于小包装商品。 |
| **UPC-A** | ✅ | ✅ | 12 位通用产品代码，北美零售商品的标准条码。 |
| **UPC-E** | ✅ | ✅ | UPC-A 的压缩版，用于小包装商品。 |
| **Codabar** | ✅ | ✅ | 编码数字和少量符号，常用于图书馆和血库。 |
| **ITF** | ✅ | ✅ | 交叉 25 码，纯数字条码，常用于纸箱包装和物流外箱。 |
| **ITF-14** | - | ✅ | 14 位 GTIN 包装版 ITF。 |
| **Code 2 of 5 Standard** | - | ✅ | 标准 Interleaved 2 of 5。 |
| **Code 2 of 5 Matrix** | - | ✅ | Code 2 of 5 Matrix 变体。 |
| **Code 2 of 5 Industrial** | - | ✅ | Code 2 of 5 Industrial 变体。 |
| **Code 2 of 5 IATA** | - | ✅ | Code 2 of 5 IATA 变体。 |
| **Code 2 of 5 Datalogic** | - | ✅ | Code 2 of 5 Datalogic 变体。 |
| **Code 2 of 5 Deutsche Post Leitcode** | - | ✅ | 德国邮政 Leitcode（最多 13 位数字）。 |
| **Code 2 of 5 Deutsche Post Identcode** | - | ✅ | 德国邮政 Identcode（最多 11 位数字）。 |
| **Code 11** | - | ✅ | 支持数字与连字符，常用于电信行业。 |
| **Code 16K** | - | ✅ | 类 Code 49 的堆叠二维条码。 |
| **Code 32** | - | ✅ | 意大利药品码（最多 8 位数字）。 |
| **Code 49** | - | ✅ | 堆叠二维条码。 |
| **Codablock F** | - | ✅ | 多行堆叠条码。 |
| **Channel Code** | - | ✅ | 高密度数字 Channel Code。 |
| **LOGMARS** | - | ✅ | 美国国防部 LOGMARS 版 Code 39。 |
| **NVE-18** | - | ✅ | 18 位货运单元编号（Nummer der Versandeinheit）。 |
| **DPD Code** | - | ✅ | DPD 包裹路由码（27-28 位字符）。 |
| **Pharmacode** | ✅ | ✅ | 药品包装专用的一维码（纯数字，范围 3 - 131070）。 |
| **Pharmacode Two-Track** | - | ✅ | Pharmacode 双轨变体。 |
| **Pharmazentralnummer** | - | ✅ | 德国/奥地利 PZN（最多 7 位数字）。 |
| **Plessey Code** | ✅ | ✅ | 图书馆和库存管理中常用的条码。 |
| **MSI Plessey** | ✅ | ✅ | Plessey 的变体，常用于图书馆和库存管理。 |
| **Telepen** | ✅ | ✅ | 图书馆和学术机构常用的条码。 |
| **Telepen Numeric** | - | ✅ | 纯数字版 Telepen。 |
| **EAN/UPC Add-On** | - | ✅ | EAN/UPC 的 2-5 位附加码。 |

### UPC/EAN 扩展码与 GS1 DataBar

| 格式 | 扫描 | 生成 | 简介 |
|------|:----:|:----:|------|
| **UPC/EAN Extension** | ✅¹ | ✅ | UPC/EAN 的 2 位或 5 位扩展码。 |
| **RSS-14 / GS1 DataBar** | ✅ | ✅ | GS1 标准条码，用于替代传统 UPC/EAN，常见于零售生鲜和医疗。 |
| **RSS Expanded** | ✅ | ✅ | 可变长度字母数字，用于批次号、重量等扩展属性。 |
| **GS1 DataBar Limited** | - | ✅ | 有限容量 GS1 DataBar。 |
| **Composite** | - | ✅ | 复合条码，由线性码与二维码组成；内容需为 GS1 格式。 |

¹ **UPC/EAN Extension 无法独立扫描**。生成器会把它附加到一组合成的 EAN-13 上，扩展位会通过 ZXing 的 `ResultMetadataType.UPC_EAN_EXTENSION` 返回，而不是作为主结果。

### 邮政码

| 格式 | 扫描 | 生成 | 简介 |
|------|:----:|:----:|------|
| **Postnet** | - | ✅ | USPS POSTNET 条码。 |
| **Royal Mail 4-State** | - | ✅ | 英国皇家邮政 4-State 客户码。 |
| **USPS OneCode** | - | ✅ | USPS 智能邮件条码。 |
| **USPS Package** | - | ✅ | USPS 包裹 IMpb（基于 GS1-128）。 |
| **Japan Post** | - | ✅ | 日本邮政条码。 |
| **KIX Code** | - | ✅ | 荷兰 KIX 邮政码。 |
| **Korea Post** | - | ✅ | 韩国邮政条码。 |
| **Australia Post** | - | ✅ | 澳大利亚邮政条码。 |

> **仅生成格式**：表格中扫描列标记为 `-` 的格式暂不被当前扫描栈（ZXing / ML Kit / BoofCV / WeChatQR / HanXin / 自定义一维码）支持，应用可生成并在 UI 中提示用户。

---

## 使用效果图

图片位于仓库根目录的 `screenshots/` 目录下。若直接通过 `README_CN.md` 渲染，部分图床可能找不到相对路径，可访问 [`screenshots/`](./screenshots) 目录查看。

| 实时扫描结果 | 图片扫描识别 |
|:------------:|:------------:|
| ![扫描结果](./screenshots/scan_result.jpg) | ![图片扫描](./screenshots/link_history.jpg) |

| 二维码生成 | 历史记录管理 |
|:----------:|:------------:|
| ![二维码生成](./screenshots/generate_qr.jpg) | ![历史记录](./screenshots/history_list.jpg) |

---

## 技术栈

- **语言**：Kotlin 2.2.21
- **UI**：Jetpack Compose + XML 布局（viewBinding）
- **数据库**：Room 2.7.1 + SQLCipher 4.5.4（加密）
- **异步**：Kotlin Coroutines
- **相机**：CameraX 1.3.3
- **条码识别**：ZXing 3.5.3、ML Kit 17.2.0、WeChatQRCode 2.6.0（OpenCV）
- **Micro QR**：BoofCV 1.4.0
- **复杂格式生成**：OkapiBarcode 0.5.6（RSS-14 / RSS Expanded / MaxiCode / Data Matrix UTF-8 / 邮政码 / Code 2 of 5 / Code One / Grid Matrix / ...）
- **CSV 解析**：Apache Commons CSV 1.14.1
- **生物认证**：androidx.biometric 1.1.0
- **测试**：JUnit 5 Platform（Vintage 引擎运行既有 JUnit 4）+ Robolectric 4.16.1

完整文件索引和架构说明位于 [`docs/knowledge-base.md`](docs/knowledge-base.md)。

---

## 权限说明

`AndroidManifest.xml` 中声明的权限：

| 权限 | 用途 |
|------|------|
| `CAMERA` | 实时扫描 |
| `VIBRATE` | 识别成功震动反馈 |
| `ACCESS_WIFI_STATE` / `CHANGE_WIFI_STATE` | 一键连接 WiFi（需将 WiFi 配置写入系统） |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | 地理位置码的地图跳转 / WiFi 定位辅助 |
| `READ_MEDIA_IMAGES` | 从相册选择图片扫描 |
| `READ_MEDIA_VIDEO` | 从视频文件中扫描条码 |
| `READ_EXTERNAL_STORAGE` (`maxSdkVersion=32`) | 兼容 Android 12 及以下访问图片 |
| `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion=28`) | 兼容 Android 9 及以下写入图片 |

---

## 构建要求

- **JDK 21**（`compileOptions` 和 `kotlinOptions.jvmTarget = '21'` 强制要求）
- **Android SDK 35**（`compileSdk 35`、`targetSdk 35`、`minSdk 28`）
- **Gradle 9.6.1**（已通过 `gradle-wrapper.properties` 锁定）
- **Android Studio Ladybug (2024.2.1) 或更新版本** - AGP 9.2.1 无法在更老的 IDE 中加载
- **NDK 编译非必需** - 仅通过 WeChatQRCode / OpenCV 的 AAR 引入原生库

请将 `JAVA_HOME` 指向 JDK 21 安装目录，并在 `local.properties` 中写入 `sdk.dir=/path/to/Android/Sdk`。`local.properties` 已在 `.gitignore` 中。

---

## 构建说明

```bash
# 克隆项目
git clone https://github.com/XenoAmess-Auto/qr_code_simple.git

# 进入项目目录
cd qr_code_simple

# 构建 Debug 版本（始终使用 wrapper，不要用全局 gradle）
./gradlew :app:assembleDebug

# 运行单元测试（Robolectric）
./gradlew :app:testDebugUnitTest

# 安装到设备
./gradlew :app:installDebug

# Release 构建（R8 + shrinkResources）。设置 RELEASE_KEYSTORE_* 环境变量
# （RELEASE_KEYSTORE_FILE / _PASSWORD / _ALIAS）时使用正式签名，否则回退 debug 签名。
./gradlew :app:assembleRelease   # APK
./gradlew :app:bundleRelease     # Play 用 AAB

# Lint 与覆盖率门禁（均为 CI 门禁）
./gradlew :app:lintDebug :app:jacocoTestCoverageVerification -PexcludeExtendedUiTests
```

推送 `v*` 标签会触发 release 工作流（`.github/workflows/release.yml`）：构建 APK + AAB 并创建 GitHub Release。正式签名需配置 `RELEASE_KEYSTORE_BASE64` / `RELEASE_KEYSTORE_PASSWORD` / `RELEASE_KEYSTORE_ALIAS` secrets。

如遇到"应用未安装"或"签名不匹配"错误，请参见下方"签名问题解决方案"。

---

## 项目结构

```
app/src/main/java/com/xenoamess/qrcodesimple/
├── MainActivity.kt                  # TabLayout + ViewPager2 入口
├── QRCodeApp.kt                     # Application 类；原生库加载
├── QRCodeScanner.kt                 # 多引擎扫描器（WeChatQRCode → ZXing → ML Kit → BoofCV → HanXin → 自定义）
├── BarcodeGenerator.kt              # 条码生成入口
├── AdvancedBarcodeGenerator.kt      # 带样式的生成（颜色、Logo、形状）
├── BarcodeFormatAdapter.kt          # 生成界面格式下拉框适配器
├── BarcodeFormatUtils.kt            # 格式工具、本地化名称与校验辅助
├── BarcodeLayout.kt                 # 样式化条码渲染布局抽象（Grid / Linear / MaxiCode / Fallback）
├── StyleConfigSerialization.kt      # 样式配置 JSON 序列化/反序列化
├── SvgQRCodeGenerator.kt            # SVG（矢量）导出
├── ShareTemplateGenerator.kt        # 分享图片合成
├── ContentParser.kt                 # 纯文本 → WiFi / 联系人 / URL / 日历 / 地理位置
├── ContentActionHandler.kt          # 解析后内容的行为分发
├── LocaleHelper.kt                  # 语言切换助手
│
├── CameraScanActivity.kt            # 实时相机扫描 UI
├── CameraScanFragment.kt            # 实时相机扫描逻辑
├── ScanImageActivity.kt             # 图片扫描 UI + 系统分享入口（图片/视频）
├── ScanImageFragment.kt             # 图片扫描逻辑
├── ScanImageProcessor.kt            # 图片/视频扫描路由（共用）
├── ContinuousScanActivity.kt        # 连续（批量）扫描 UI
├── ContinuousScanAdapter.kt         # 连续扫描列表适配器
├── VideoScanActivity.kt             # 视频文件扫描
├── GenerateActivity.kt              # 单码生成 UI + 系统分享入口（文本）
├── GenerateFragment.kt              # 单码生成逻辑
├── BatchGenerateActivity.kt         # CSV / Excel 批量生成 UI
├── BatchResultActivity.kt           # 批量生成结果页
├── BatchGenerator.kt                # CSV / Excel 解析 + 批量生成
├── ResultActivity.kt                # 单码结果页（操作菜单）
├── ui/result/QRResultAdapter.kt     # 多扫描结果 RecyclerView 适配器
├── HistoryFragment.kt               # 历史记录列表
├── HistoryAdapter.kt                # 历史记录列表适配器
├── HistoryBackupManager.kt          # JSON / CSV / 加密备份导入导出
├── BackupCrypto.kt                  # 备份加密（AES-256-GCM + PBKDF2）
├── TagManager.kt                    # 自定义标签 CRUD
├── AboutFragment.kt                 # 关于 / 致谢
│
├── AppLockManager.kt                # 历史记录的生物认证 / PIN 锁
├── SecurityManager.kt               # 恶意链接启发式判断
├── SecurityBlacklist.kt             # 黑名单模型与 assets/覆盖加载
├── BlacklistUpdater.kt              # 可选静默在线黑名单更新
├── PrivacySettingsActivity.kt       # 隐私模式开关
├── DatabaseSecurityActivity.kt      # SQLCipher 密钥轮换
├── QRCodeRestorationManager.kt      # 修复变体生成（灰度 / 对比度 / 二值化）
├── RestorationRescan.kt             # 图片扫描无结果时的修复重试编排
├── ImagePerformanceManager.kt       # 大图加载内存优化
├── CameraFocusManager.kt            # 自动 / 点击对焦
├── ScanRegionView.kt                # 扫描区域（感兴趣区）覆盖
├── ScannerOverlayView.kt            # 相机扫描线覆盖
├── AnimationUtils.kt                # 通用动画工具
├── EdgeToEdgeExt.kt                 # Edge-to-edge 工具
├── AppShortcutManager.kt            # 静态 + 动态快捷方式
│
├── QuickScanWidget.kt               # 桌面"快速扫描"小组件
├── QuickGenerateWidget.kt           # 桌面"快速生成"小组件
├── QuickScanTileService.kt          # 下拉快捷设置磁贴（一键扫码）
├── BackupActivity.kt                # 备份 / 恢复 UI
│
├── data/
│   ├── AppDatabase.kt               # Room 数据库
│   ├── BarcodeFormat.kt             # 应用条码格式枚举（含 isScannable）
│   ├── Converters.kt                # Room 类型转换器
│   ├── HistoryDao.kt                # 历史记录 DAO
│   ├── HistoryItem.kt               # 历史记录实体 + HistoryType 枚举
│   └── HistoryRepository.kt         # 仓库（包装 DAO）
│
├── decoder/
│   ├── BarcodeScanUtils.kt          # 自定义一维码预处理工具
│   ├── CustomLinearBarcodeScanner.kt# 分发到 Pharmacode / Plessey / MSI Plessey / Telepen
│   ├── MicroQrCodeScanner.kt        # 基于 BoofCV 的 Micro QR 扫描器
│   ├── PharmacodeDecoder.kt         # Pharmacode 解码器
│   ├── PlesseyDecoder.kt            # Plessey 解码器
│   ├── MsiPlesseyDecoder.kt         # MSI Plessey 解码器
│   ├── TelepenDecoder.kt            # Telepen 解码器
│   └── hanxin/
│       ├── HanXinEncoder.kt         # 汉信码编码器
│       └── HanXinDecoder.kt         # 汉信码解码器
│
└── ViewPagerAdapter.kt              # MainActivity 标签页适配器
```

资源目录要点：

```
app/src/main/res/
├── layout/                          # XML 布局（activity / fragment / 列表 item / 小组件）
├── values/                          # 默认（英文）字符串、颜色、Material 3 主题
├── values-de/                       # 德语
├── values-ja/                       # 日语
├── values-ko/                       # 韩语
├── values-zh/                       # 简体中文
├── values-night/                    # 深色主题覆盖
├── drawable/                        # 矢量图标（闪光灯、切换摄像头等）
├── anim/                            # 通用动画
├── menu/                            # 工具栏菜单
├── xml/                             # locales_config.xml、shortcuts.xml、Widget provider 等
└── mipmap-*/                        # 启动器图标
```

---

## 签名问题解决方案

如果安装 APK 时遇到"应用未安装"或"签名不匹配"错误：

### 方案一：下载 CI 调试密钥库
1. 前往 GitHub → Actions → 最新工作流运行
2. 下载 `debug-keystore` 产物
3. 安装到本地：
   ```bash
   unzip debug-keystore.zip -d /tmp/
   mkdir -p ~/.android
   cp /tmp/debug.keystore ~/.android/debug.keystore
   ```

### 方案二：使用 CI 构建的 APK
从 GitHub Actions 下载 `debug-apk` 产物直接安装。

### 方案三：卸载后重装
```bash
adb uninstall com.xenoamess.qrcodesimple
adb install app-debug.apk
```

---

## 致谢

- [ZXing](https://github.com/zxing/zxing) - 二维码 / 条码识别后备方案
- [ML Kit](https://developers.google.com/ml-kit) - Google ML 条码扫描
- [WeChatQRCode](https://github.com/WeChatCV/opencv_3rdparty) - 主二维码识别引擎（基于 [jenly1314](https://github.com/jenly1314/WeChatQRCode)）
- [BoofCV](https://boofcv.org/) - Micro QR Code 检测
- [OkapiBarcode](https://github.com/woo-j/OkapiBarcode) - RSS-14 / RSS Expanded / MaxiCode / Data Matrix UTF-8 / 邮政码 / Code 2 of 5 / Code One / Grid Matrix 等生成
- [SQLCipher](https://www.zetetic.net/sqlcipher/) - 数据库加密
- [Apache Commons CSV](https://commons.apache.org/proper/commons-csv/) - 批量生成的 CSV 解析

---

## 开源协议

Apache License 2.0 - 详见 [LICENSE](LICENSE)。

---

## 参与贡献

欢迎提交 Issue 和 Pull Request。提交前请运行 `./gradlew :app:testDebugUnitTest`，并保持 `docs/knowledge-base.md` 与代码一致。

---

## 支持开发

如果本应用对你有帮助，欢迎支持开发：

[![Ko-fi](https://img.shields.io/badge/Ko--fi-请我喝咖啡-ff5f5f?logo=ko-fi)](https://ko-fi.com/xenoamess)
