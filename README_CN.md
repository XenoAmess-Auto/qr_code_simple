# QR Code Simple

一款简洁的 Android 二维码扫描与生成应用。

**[English](README.md) | 中文**

![Screenshot_20260211_062927_com_xenoamess_qrcodesi.jpg](resources/Screenshot_20260211_062927_com_xenoamess_qrcodesi.jpg)
![Screenshot_20260211_062932_com_xenoamess_qrcodesi.jpg](resources/Screenshot_20260211_062932_com_xenoamess_qrcodesi.jpg)
![Screenshot_20260211_063044_com_xenoamess_qrcodesi.jpg](resources/Screenshot_20260211_063044_com_xenoamess_qrcodesi.jpg)
![Screenshot_20260211_063050_com_xenoamess_qrcodesi.jpg](resources/Screenshot_20260211_063050_com_xenoamess_qrcodesi.jpg)

## 功能特性

### 1. 实时扫描 (相机)
- 实时相机预览，即时识别二维码
- **缩放滑块** - 可调节变焦（1x 到最大相机变焦）
- **闪光灯开关** - 开启/关闭手电筒
- **摄像头切换** - 前后摄像头切换
- 自动保存扫描历史

### 2. 图片扫描
- 从相册选择图片
- 从文件管理器选取图片
- 从剪贴板粘贴图片
- 单张图片多二维码识别
- 结果带绿色边框标注
- 批量操作：全选、复制、分享、删除

### 3. 生成二维码
- 输入文本生成二维码
- 复制生成的二维码到剪贴板
- 分享二维码图片

### 4. 历史记录
- 查看所有扫描和生成的二维码
- 复制历史项目
- 分享历史项目
- 删除单条或清空全部
- 从生成记录分享二维码图片

### 5. 关于
- 应用信息
- 支持开发快捷链接 (Ko-fi)
- 源代码和维护者链接

## 支持的条码格式

应用当前支持 **21 种条码格式** 扫描，其中 13 种支持生成。

### 二维码

| 格式 | 扫描 | 生成 | 简介 |
|------|:----:|:----:|------|
| **QR Code** | ✅ | ✅ | 最常见的二维码，广泛用于支付、网址、名片和 WiFi 共享。 |
| **Data Matrix** | ✅ | ✅ | 可在极小空间存储数据，常用于电子元器件和医疗器械标识。 |
| **Aztec Code** | ✅ | ✅ | 无需静音区即可识别，常用于火车票、登机牌等场景。 |
| **PDF417** | ✅ | ✅ | 堆叠式线性条码，可存储大量文本与二进制数据，用于身份证、驾照和快递面单。 |

### 一维条码

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

### 仅扫描支持

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

## 技术栈

- **Kotlin** - 编程语言
- **CameraX** - 相机预览和图像分析
- **ZXing** - 二维码扫描后备方案
- **ML Kit** - 条码扫描（后备方案）
- **WeChatQRCode** - 主要二维码识别引擎
- **OpenCV** - 图像处理
- **Room** - 本地历史数据库
- **Material Design 3** - UI 组件
- **ViewPager2 + TabLayout** - 导航

## 权限说明

- `CAMERA` - 用于实时扫描
- `READ_MEDIA_IMAGES` - 用于从相册选择图片

## 构建

### 环境要求
- Java 17
- Android SDK (compileSdk 34)
- Gradle 8.2

### 本地构建

```bash
export JAVA_HOME=$HOME/opt/jdk-17.0.12
export ANDROID_HOME=$HOME/opt/android-sdk
./gradlew assembleDebug --no-daemon
```

### 持续集成
GitHub Actions 在每次推送时自动构建 APK。

## 项目结构

```
app/src/main/java/com/xenoamess/qrcodesimple/
├── MainActivity.kt              # 主活动，含 TabLayout + ViewPager2
├── QRCodeApp.kt                 # 应用类
├── QRCodeScanner.kt             # 统一扫描器（WeChatQRCode + ZXing + ML Kit）
├── adapter/
│   └── HistoryAdapter.kt        # 历史列表适配器
├── data/
│   ├── HistoryDao.kt            # Room DAO
│   ├── HistoryDatabase.kt       # Room 数据库
│   ├── HistoryEntity.kt         # 历史数据实体
│   ├── HistoryRepository.kt     # 仓库模式
│   └── HistoryType.kt           # 枚举：扫描、生成
├── fragment/
│   ├── AboutFragment.kt         # 关于页面
│   ├── CameraScanFragment.kt    # 实时相机扫描
│   ├── GenerateFragment.kt      # 二维码生成
│   ├── HistoryFragment.kt       # 历史列表
│   └── ScanImageFragment.kt     # 图片扫描
└── util/
    ├── ImagePicker.kt           # 图片选择工具
    └── QRCodeGenerator.kt       # 二维码生成工具

res/
├── drawable/
│   ├── ic_flash_on.xml          # 闪光灯开启图标
│   ├── ic_flash_off.xml         # 闪光灯关闭图标
│   └── ic_switch_camera.xml     # 摄像头切换图标
├── layout/
│   ├── activity_main.xml        # 主布局（含标签页）
│   ├── fragment_camera_scan.xml # 实时扫描布局
│   ├── fragment_scan_image.xml  # 图片扫描布局
│   ├── fragment_generate.xml    # 生成布局
│   ├── fragment_history.xml     # 历史布局
│   └── fragment_about.xml       # 关于布局
└── values/
    ├── colors.xml               # 主题颜色（青色主色）
    └── themes.xml               # Material Design 3 主题
```

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

## 参与贡献

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/新功能`)
3. 提交更改 (`git commit -m '添加新功能'`)
4. 推送分支 (`git push origin feature/新功能`)
5. 创建 Pull Request

## 开源协议

Apache License 2.0

详见 [LICENSE](LICENSE) 文件。

## 支持开发

如果本应用对你有帮助，欢迎支持开发：

[![Ko-fi](https://img.shields.io/badge/Ko--fi-请我喝咖啡-ff5f5f?logo=ko-fi)](https://ko-fi.com/xenoamess)
