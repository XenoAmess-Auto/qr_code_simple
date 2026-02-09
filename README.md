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

MIT
