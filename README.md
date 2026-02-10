# QR Code Simple

基于 [WeChatQRCode](https://github.com/jenly1314/WeChatQRCode) 的 Android 二维码扫描与生成应用。

## 功能

### 1. 扫描图片中的二维码
- 从图库选择图片
- 拍照获取图片
- 从文件选择器选择图片
- 支持识别图片中的一个或多个二维码
- 检测结果展示（带绿色框标注）
- 结果操作：
  - 选择/全选/取消全选
  - 复制单个或多个结果
  - 分享单个或多个结果
  - 编辑结果内容
  - 删除结果

### 2. 相机实时扫描
- 使用 WeChatQRCode 引擎实时扫描
- 支持多二维码同时识别
- 快速跳转到结果页面

### 3. 生成二维码
- 输入文本生成二维码图片
- 保存到相册
- 分享二维码图片

## 技术栈

- **Kotlin** - 主要编程语言
- **WeChatQRCode** - 基于 OpenCV 的微信二维码识别引擎
- **ZXing** - 二维码生成
- **Material Design 3** - UI 组件
- **ViewBinding** - 视图绑定

## 依赖

```gradle
// WeChatQRCode
implementation 'com.github.jenly1314.WeChatQRCode:opencv:2.4.0'
implementation 'com.github.jenly1314.WeChatQRCode:opencv-armv7a:2.4.0'
implementation 'com.github.jenly1314.WeChatQRCode:opencv-armv64:2.4.0'
implementation 'com.github.jenly1314.WeChatQRCode:wechat-qrcode:2.4.0'
implementation 'com.github.jenly1314.WeChatQRCode:wechat-qrcode-scanning:2.4.0'

// ZXing for QR code generation
implementation 'com.google.zxing:core:3.5.3'
```

## 权限

- `CAMERA` - 相机扫描
- `READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` - 读取图片

## 项目结构

```
app/src/main/java/com/example/qrcodesimple/
├── QRCodeApp.kt           # Application 类，初始化 OpenCV 和 WeChatQRCode
├── MainActivity.kt        # 主界面，功能入口
├── ScanImageActivity.kt   # 选择图片来源
├── ResultActivity.kt      # 展示扫描结果
├── CameraScanActivity.kt  # 相机实时扫描
└── GenerateActivity.kt    # 生成二维码

res/layout/
├── activity_main.xml      # 主界面布局
├── activity_scan_image.xml
├── activity_result.xml
├── activity_generate.xml
└── item_qr_result.xml     # 结果列表项
```

## 构建

```bash
./gradlew assembleDebug
```

## 签名问题解决方案

如果安装 GitHub Actions 构建的 APK 时出现"更新包与已安装应用的签名不一致"错误，说明本地和 CI 使用的 debug 签名不同。

### 方法一：同步 CI 签名到本地（推荐）

GitHub Actions 每次构建都会生成 `debug.keystore`，你可以在 Actions 页面下载。

**步骤：**

1. 打开 GitHub 仓库页面 → Actions → 选择最新的工作流运行
2. 在 **Artifacts** 部分下载 `debug-keystore` 文件
3. 解压并安装到本地：
```bash
# 解压下载的文件
unzip debug-keystore.zip -d /tmp/

# 备份本地原有的 keystore（可选）
mv ~/.android/debug.keystore ~/.android/debug.keystore.backup.$(date +%Y%m%d)

# 安装 CI 的 keystore
mkdir -p ~/.android
cp /tmp/debug.keystore ~/.android/debug.keystore
```

4. 重新构建本地 APK：
```bash
./gradlew assembleDebug
```

### 方法二：下载 CI 构建的 APK 安装

直接使用 GitHub Actions 构建的 APK，不需要本地构建：

1. 打开 GitHub 仓库页面 → Actions → 选择最新的工作流运行
2. 下载 `debug-apk` artifact
3. 安装 APK：
```bash
adb install -r app-debug.apk
```

### 方法三：完全卸载重装（会丢失数据）

```bash
adb uninstall com.xenoamess.qrcodesimple
adb install app-debug.apk
```

### 方法四：使用项目内置签名（开发者）

如果你是项目开发者，可以运行以下命令生成固定的 debug.keystore：

```bash
# 生成 keystore
keytool -genkey -v -keystore app/debug.keystore -alias androiddebugkey \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass android -keypass android \
  -dname "CN=Android Debug,O=Android,C=US"

# 同时安装到本地
cp app/debug.keystore ~/.android/debug.keystore
```

**注意：** 如果你修改了 `app/debug.keystore`，需要将其 base64 编码后设置为 GitHub Secret `DEBUG_KEYSTORE`，这样 CI 会使用相同的签名。

```bash
# 生成 base64
cat app/debug.keystore | base64
```

然后在 GitHub 仓库 Settings → Secrets and variables → Actions → New repository secret 中添加：
- Name: `DEBUG_KEYSTORE`
- Value: 上面生成的 base64 字符串

## 使用说明

1. 打开应用后选择功能：
   - **Scan Image** - 从图片识别二维码
   - **Camera Scan** - 实时相机扫描
   - **Generate QR Code** - 生成二维码

2. 扫描结果页面：
   - 勾选要操作的结果
   - 使用底部按钮进行批量操作
   - 点击单项的按钮进行单独操作
   - 使用右上角菜单复制/分享全部结果

## License

Apache License 2.0

See [LICENSE](LICENSE) for details.
