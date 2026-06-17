# QR Code Simple

[English](#english) | [中文](#中文)

---

<a name="english"></a>
## English

A feature-rich Android QR/Barcode scanning and generation app.

### Features

#### Core Features
- ✅ **Full Barcode Support** - Supports 21 barcode formats for scanning, including 13 formats for generation (QR Code, Data Matrix, Aztec, PDF417, Code 128/39/93, EAN-13/8, UPC-A/E, Codabar, ITF, and more)
- ✅ **Smart Content Parsing** - Auto-detect WiFi, contacts, calendar, email, URL, geo-location with one-click actions
- ✅ **Batch Generation** - Import CSV/Excel data to generate QR codes in bulk, support ZIP export
- ✅ **Style Customization** - Foreground/background colors, center logo, rounded/dot styles
- ✅ **QR Code Repair** - Smart repair for blurry/damaged QR codes

#### History Management
- ✅ **Full-text Search** - Search by content/time/type
- ✅ **Smart Categories** - Auto-classify into links, text, WiFi, contacts, etc.
- ✅ **Favorites/Pinning** - Mark important items
- ✅ **Tag System** - Custom tag management
- ✅ **Import/Export** - JSON/CSV backup

#### Scanning Experience
- ✅ **Continuous Scan Mode** - Scan multiple codes without confirmation
- ✅ **Scan Feedback** - Vibration effects
- ✅ **Smart/Manual Focus** - Auto-adjust based on code size, tap to focus
- ✅ **Scan Area Limit** - Recognize within selected region

#### Share & Export
- ✅ **Vector Export** - SVG format QR code export (lossless scaling)
- ✅ **Share Templates** - Generate share images with description text

#### Security & Privacy
- ✅ **Malicious Link Detection** - Local blacklist, URL suspicious feature analysis
- ✅ **Privacy Mode** - Incognito scanning (no history saved)
- ✅ **App Lock** - Fingerprint/password protection for sensitive history
- ✅ **Local Encryption** - SQLCipher AES-256 database encryption

#### UI & UX
- ✅ **Material You** - Android 12+ dynamic colors
- ✅ **Landscape Support** - Optimized for tablets and landscape
- ✅ **Shortcuts** - Long-press icon for quick scan/generate
- ✅ **Home Widgets** - Quick scan/generate widgets
- ✅ **Animation** - Page transitions, scan line animation
- ✅ **Internationalization** - Simplified Chinese, Traditional Chinese, English, Japanese, Korean, German

#### Technical
- ✅ **Unit Tests** - Comprehensive test coverage
- ✅ **Performance** - Large image loading memory optimization
- ✅ **Crash Monitoring** - Firebase Crashlytics integration
- ✅ **Offline Enhancement** - TensorFlow Lite model support

### Supported Barcode Formats

The app supports **21 barcode formats** for scanning, including 13 formats for generation.

#### 2D Matrix Codes

| Format | Scan | Generate | Description |
|--------|:----:|:--------:|-------------|
| **QR Code** | ✅ | ✅ | Most common 2D code, widely used for payments, URLs, and contact sharing. |
| **Data Matrix** | ✅ | ✅ | Compact 2D code that can store data in very small spaces, common for electronic components and medical devices. |
| **Aztec Code** | ✅ | ✅ | 2D code that does not require a quiet zone, often used for train tickets and boarding passes. |
| **PDF417** | ✅ | ✅ | Stacked linear barcode capable of storing large amounts of text and binary data, used on IDs and shipping labels. |

#### 1D/Linear Barcodes

| Format | Scan | Generate | Description |
|--------|:----:|:--------:|-------------|
| **Code 128** | ✅ | ✅ | High-density alphanumeric encoding widely used in logistics and supply chain. |
| **Code 39** | ✅ | ✅ | Supports digits, uppercase letters, and several symbols, used in industrial and military applications. |
| **Code 93** | ✅ | ✅ | Compact improvement over Code 39, commonly used in logistics and industry. |
| **EAN-13** | ✅ | ✅ | 13-digit European Article Number, the standard retail barcode in most countries. |
| **EAN-8** | ✅ | ✅ | Short version of EAN-13 for small packaging. |
| **UPC-A** | ✅ | ✅ | 12-digit Universal Product Code standard in North American retail. |
| **UPC-E** | ✅ | ✅ | Compressed version of UPC-A for small packages. |
| **Codabar** | ✅ | ✅ | Encodes digits and a few symbols, historically used in libraries and blood banks. |
| **ITF** | ✅ | ✅ | Interleaved 2 of 5, a numeric-only barcode often used on carton packaging. |

#### Scan-Only Formats

| Format | Scan | Description |
|--------|:----:|-------------|
| **UPC/EAN Extension** | ✅ | 2 or 5-digit add-on supplementing UPC/EAN barcodes. |
| **RSS-14 / GS1 DataBar** | ✅ | GS1 standard barcode designed to replace traditional UPC/EAN in retail. |
| **RSS Expanded** | ✅ | Variable-length alphanumeric GS1 barcode for product attributes such as batch or weight. |
| **MaxiCode** | ✅ | Fixed-size 2D code developed by UPS, used in international logistics and air freight. |
| **Micro QR Code** | ✅ | Miniaturized QR code for extremely small marking spaces. |
| **Pharmacode** | ✅ | One-dimensional code specifically designed for pharmaceutical packaging. |
| **Plessey Code / MSI Plessey** | ✅ | Barcode commonly used in libraries and inventory management. |
| **Telepen** | ✅ | Barcode often used in libraries and academic institutions. |

### Screenshots

| Live Scan Result | Image Scan |
|:----------------:|:----------:|
| ![Scan Result](./screenshots/scan_result.jpg) | ![Image Scan](./screenshots/link_history.jpg) |

| QR Generation | History Management |
|:-------------:|:------------------:|
| ![QR Generate](./screenshots/generate_qr.jpg) | ![History](./screenshots/history_list.jpg) |

### Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose / XML Layout
- **Database**: Room + SQLCipher (encrypted)
- **DI**: Singleton pattern
- **Async**: Kotlin Coroutines
- **Camera**: CameraX
- **QR Recognition**: ZXing + ML Kit + WeChatQRCode
- **ML**: TensorFlow Lite
- **Crash Monitoring**: Firebase Crashlytics

### Build Requirements

- Android Studio Hedgehog (2023.1.1) or higher
- JDK 17
- Android SDK 34
- Gradle 8.2

### Build Instructions

```bash
# Clone
git clone https://github.com/XenoAmess-Auto/qr_code_simple.git
cd qr_code_simple

# Build Debug
./gradlew assembleDebug

# Run tests
./gradlew test

# Install to device
./gradlew installDebug
```

### Project Structure

```
app/src/main/java/com/xenoamess/qrcodesimple/
├── MainActivity.kt              # Main activity
├── QRCodeApp.kt                 # Application class
├── data/                        # Data layer
│   ├── AppDatabase.kt           # Encrypted database
│   ├── HistoryItem.kt           # History entity
│   ├── HistoryDao.kt            # Database access
│   └── HistoryRepository.kt     # Data repository
├── ui/                          # UI components
│   ├── CameraScanFragment.kt    # Real-time scan
│   ├── ScanImageActivity.kt     # Image scan
│   ├── GenerateActivity.kt      # QR generation
│   └── HistoryFragment.kt       # History
└── util/                        # Utils
    ├── BarcodeGenerator.kt      # Barcode generator
    ├── ContentParser.kt         # Content parser
    ├── QRCodeScanner.kt         # QR scanner
    └── LocaleHelper.kt          # Language helper
```

### License

MIT License

### Contributing

Issues and Pull Requests welcome!

---

<a name="中文"></a>
## 中文

一款功能丰富的 Android 二维码/条码扫描与生成应用。

### 功能特性

#### 核心功能
- ✅ **全条码支持** - 支持 21 种条码格式扫描，其中 13 种支持生成（QR Code、Data Matrix、Aztec、PDF417、Code 128/39/93、EAN-13/8、UPC-A/E、Codabar、ITF 等）
- ✅ **智能内容解析** - 自动识别 WiFi、联系人、日历、邮件、URL、地理位置等格式，提供一键操作
- ✅ **批量生成** - 从 CSV/Excel 导入数据批量生成二维码，支持 ZIP 导出
- ✅ **样式定制** - 前景色/背景色自定义、中心 Logo、圆角/点阵样式
- ✅ **二维码修复** - 对模糊/破损二维码进行智能修复识别

#### 历史记录
- ✅ **全文搜索** - 按内容/时间/类型搜索历史
- ✅ **智能分类** - 自动归类为链接、文本、WiFi、联系人等
- ✅ **收藏/置顶** - 重要内容标记
- ✅ **标签系统** - 自定义标签管理
- ✅ **导入/导出** - JSON/CSV 格式备份

#### 扫描体验
- ✅ **连续扫描模式** - 无需确认连续扫描多个码
- ✅ **扫描反馈** - 震动效果
- ✅ **智能/手动对焦** - 根据码大小自动调整对焦距离，支持点击对焦
- ✅ **扫描区域限定** - 框选特定区域识别

#### 分享与导出
- ✅ **矢量导出** - SVG 格式二维码导出（无损放大）
- ✅ **分享模板** - 生成带说明文字的分享图片

#### 安全与隐私
- ✅ **恶意链接检测** - 本地黑名单检测、URL 可疑特征分析
- ✅ **隐私模式** - 无痕扫描（不保存历史）
- ✅ **应用锁** - 指纹/密码保护敏感历史
- ✅ **本地加密** - SQLCipher AES-256 数据库加密

#### 界面与体验
- ✅ **Material You** - Android 12+ 动态取色
- ✅ **横屏适配** - 平板和横屏优化布局
- ✅ **快捷方式** - 长按图标快速扫码/生成
- ✅ **桌面小组件** - 快速扫描/生成小组件
- ✅ **动画优化** - 页面过渡、扫描线动画
- ✅ **国际化** - 支持简体中文、繁体中文、英语、日语、韩语、德语

#### 技术特性
- ✅ **单元测试** - 全面的单元测试覆盖
- ✅ **性能优化** - 大图加载内存优化
- ✅ **崩溃监控** - Firebase Crashlytics 集成
- ✅ **离线增强** - TensorFlow Lite 模型支持

### 支持的条码格式

应用当前支持 **21 种条码格式** 扫描，其中 13 种支持生成。

#### 二维码

| 格式 | 扫描 | 生成 | 简介 |
|------|:----:|:----:|------|
| **QR Code** | ✅ | ✅ | 最常见的二维码，广泛用于支付、网址、名片和 WiFi 共享。 |
| **Data Matrix** | ✅ | ✅ | 可在极小空间存储数据，常用于电子元器件和医疗器械标识。 |
| **Aztec Code** | ✅ | ✅ | 无需静音区即可识别，常用于火车票、登机牌等场景。 |
| **PDF417** | ✅ | ✅ | 堆叠式线性条码，可存储大量文本与二进制数据，用于身份证、驾照和快递面单。 |

#### 一维条码

| 格式 | 扫描 | 生成 | 简介 |
|------|:----:|:----:|------|
| **Code 128** | ✅ | ✅ | 高密度字母数字编码，广泛用于物流与供应链。 |
| **Code 39** | ✅ | ✅ | 支持数字、大写字母及部分符号，常用于工业和军事领域。 |
| **Code 93** | ✅ | ✅ | Code 39 的高密度改进版，常用于物流和工业场景。 |
| **EAN-13** | ✅ | ✅ | 13 位欧洲商品编码，是大多数国家零售商品的标准条码。 |
| **EAN-8** | ✅ | ✅ | EAN-13 的短版，用于小包装商品。 |
| **UPC-A** | ✅ | ✅ | 12 位通用产品代码，北美零售商品的标准条码。 |
| **UPC-E** | ✅ | ✅ | UPC-A 的压缩版，用于小包装商品。 |
| **Codabar** | ✅ | ✅ | 编码数字和少量符号，常用于图书馆和血库。 |
| **ITF** | ✅ | ✅ | 交叉 25 码，纯数字条码，常用于纸箱包装和物流外箱。 |

#### 仅扫描支持

| 格式 | 扫描 | 简介 |
|------|:----:|------|
| **UPC/EAN Extension** | ✅ | UPC/EAN 的 2 位或 5 位扩展码，作为主码的附加信息。 |
| **RSS-14 / GS1 DataBar** | ✅ | GS1 标准条码，用于替代传统 UPC/EAN，常见于零售生鲜和医疗。 |
| **RSS Expanded** | ✅ | RSS-14 的扩展版，可变长度字母数字，用于生产日期、批次号、重量等。 |
| **MaxiCode** | ✅ | UPS 开发的固定大小二维条码，用于国际物流和航空货运。 |
| **Micro QR Code** | ✅ | 微型 QR 码，用于极小空间的标识。 |
| **Pharmacode** | ✅ | 药品包装专用的一维码。 |
| **Plessey Code / MSI Plessey** | ✅ | 图书馆和库存管理中常用的条码。 |
| **Telepen** | ✅ | 图书馆和学术机构常用的条码。 |

### 使用效果图

| 实时扫描结果 | 图片扫描识别 |
|:------------:|:------------:|
| ![扫描结果](./screenshots/scan_result.jpg) | ![图片扫描](./screenshots/link_history.jpg) |

| 二维码生成 | 历史记录管理 |
|:----------:|:------------:|
| ![二维码生成](./screenshots/generate_qr.jpg) | ![历史记录](./screenshots/history_list.jpg) |

### 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose / XML Layout
- **数据库**: Room + SQLCipher（加密）
- **依赖注入**: 无（使用单例模式）
- **异步**: Kotlin Coroutines
- **相机**: CameraX
- **二维码识别**: ZXing + ML Kit + WeChatQRCode
- **机器学习**: TensorFlow Lite
- **崩溃监控**: Firebase Crashlytics

### 构建要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 34
- Gradle 8.2

### 构建说明

```bash
# 克隆项目
git clone https://github.com/XenoAmess-Auto/qr_code_simple.git

# 进入项目目录
cd qr_code_simple

# 构建 Debug 版本
./gradlew assembleDebug

# 运行单元测试
./gradlew test

# 安装到设备
./gradlew installDebug
```

### 项目结构

```
app/src/main/java/com/xenoamess/qrcodesimple/
├── MainActivity.kt              # 主界面
├── QRCodeApp.kt                 # Application 类
├── data/                        # 数据层
│   ├── AppDatabase.kt           # 加密数据库
│   ├── HistoryItem.kt           # 历史记录实体
│   ├── HistoryDao.kt            # 数据库访问
│   └── HistoryRepository.kt     # 数据仓库
├── ui/                          # UI 组件
│   ├── CameraScanFragment.kt    # 实时扫描
│   ├── ScanImageActivity.kt     # 图片扫描
│   ├── GenerateActivity.kt      # 二维码生成
│   └── HistoryFragment.kt       # 历史记录
└── util/                        # 工具类
    ├── BarcodeGenerator.kt      # 条码生成器
    ├── ContentParser.kt         # 内容解析器
    ├── QRCodeScanner.kt         # 二维码扫描器
    └── LocaleHelper.kt          # 语言切换助手
```

### 开源许可

MIT License

### 贡献

欢迎提交 Issue 和 Pull Request！

---

## Acknowledgments / 致谢

- [ZXing](https://github.com/zxing/zxing) - QR code recognition
- [ML Kit](https://developers.google.com/ml-kit) - Google ML suite
- [WeChatQRCode](https://github.com/WeChatCV/opencv_3rdparty) - WeChat QR engine
- [SQLCipher](https://www.zetetic.net/sqlcipher/) - Database encryption
- [TensorFlow Lite](https://www.tensorflow.org/lite) - On-device ML
