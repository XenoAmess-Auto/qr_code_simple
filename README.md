# QR Code Simple

A simple Android QR code scanner and generator app.

## Features

### 1. Realtime Scan (Camera)
- Live camera preview with real-time QR code detection
- **Zoom slider** - Adjustable zoom (1x to max camera zoom)
- **Flash toggle** - Turn flashlight on/off
- **Camera switch** - Switch between front and rear cameras
- Automatic history saving

### 2. Image Scan
- Select image from gallery
- Pick image from file manager
- Paste image from clipboard
- Multi-QR code detection in single image
- Results with green bounding boxes
- Batch operations: select all, copy, share, delete

### 3. Generate QR Code
- Input text to generate QR code
- Copy generated QR code to clipboard
- Share QR code image
- Save to history

### 4. History
- View all scanned and generated QR codes
- Copy history items
- Share history items
- Delete individual items or clear all
- Share QR code images from generated entries

### 5. About
- App information
- Quick link to support development (Ko-fi)
- Source code and maintainer links

## Tech Stack

- **Kotlin** - Programming language
- **CameraX** - Camera preview and image analysis
- **ZXing** - QR code scanning fallback
- **ML Kit** - Barcode scanning (fallback)
- **WeChatQRCode** - Primary QR detection engine
- **OpenCV** - Image processing
- **Room** - Local database for history
- **Material Design 3** - UI components
- **ViewPager2 + TabLayout** - Navigation

## Screenshots

| Realtime | Image | Generate | History | About |
|----------|-------|----------|---------|-------|
| Camera with zoom slider | Image selection & results | QR generation | Scan history | App info |

## Permissions

- `CAMERA` - For real-time scanning
- `READ_MEDIA_IMAGES` - For selecting images from gallery

## Build

### Prerequisites
- Java 17
- Android SDK (compileSdk 34)
- Gradle 8.2

### Local Build

```bash
export JAVA_HOME=$HOME/opt/jdk-17.0.12
export ANDROID_HOME=$HOME/opt/android-sdk
./gradlew assembleDebug --no-daemon
```

### CI/CD
GitHub Actions automatically builds APK on every push.

## Project Structure

```
app/src/main/java/com/xenoamess/qrcodesimple/
├── MainActivity.kt              # Main activity with TabLayout + ViewPager2
├── QRCodeApp.kt                 # Application class
├── QRCodeScanner.kt             # Unified scanner (WeChatQRCode + ZXing + ML Kit)
├── adapter/
│   └── HistoryAdapter.kt        # History list adapter
├── data/
│   ├── HistoryDao.kt            # Room DAO
│   ├── HistoryDatabase.kt       # Room database
│   ├── HistoryEntity.kt         # History data entity
│   ├── HistoryRepository.kt     # Repository pattern
│   └── HistoryType.kt           # Enum: SCAN, GENERATE
├── fragment/
│   ├── AboutFragment.kt         # About page
│   ├── CameraScanFragment.kt    # Realtime camera scan
│   ├── GenerateFragment.kt      # QR code generation
│   ├── HistoryFragment.kt       # History list
│   └── ScanImageFragment.kt     # Image scanning
└── util/
    ├── ImagePicker.kt           # Image selection utilities
    └── QRCodeGenerator.kt       # QR generation utilities

res/
├── drawable/
│   ├── ic_flash_on.xml          # Flash on icon
│   ├── ic_flash_off.xml         # Flash off icon
│   └── ic_switch_camera.xml     # Camera switch icon
├── layout/
│   ├── activity_main.xml        # Main layout with tabs
│   ├── fragment_camera_scan.xml # Realtime scan layout
│   ├── fragment_scan_image.xml  # Image scan layout
│   ├── fragment_generate.xml    # Generate layout
│   ├── fragment_history.xml     # History layout
│   └── fragment_about.xml       # About layout
└── values/
    ├── colors.xml               # Theme colors (cyan primary)
    └── themes.xml               # Material Design 3 themes
```

## Signature Issue Resolution

If you encounter "App not installed" or "Signature mismatch" errors when installing APK:

### Option 1: Download CI Debug Keystore
1. Go to GitHub → Actions → Latest workflow run
2. Download `debug-keystore` artifact
3. Install to local:
```bash
unzip debug-keystore.zip -d /tmp/
mkdir -p ~/.android
cp /tmp/debug.keystore ~/.android/debug.keystore
```

### Option 2: Use CI-built APK
Download `debug-apk` artifact from GitHub Actions and install directly.

### Option 3: Uninstall and Reinstall
```bash
adb uninstall com.xenoamess.qrcodesimple
adb install app-debug.apk
```

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

Apache License 2.0

See [LICENSE](LICENSE) for details.

## Support

If you find this app useful, consider supporting development:

[![Ko-fi](https://img.shields.io/badge/Ko--fi-Buy%20me%20a%20coffee-ff5f5f?logo=ko-fi)](https://ko-fi.com/xenoamess)
