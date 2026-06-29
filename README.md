# QR Code Simple

[English](README.md) | [中文](README_CN.md)

A feature-rich Android QR/Barcode scanning and generation app.

---

## Features

### Core

- ✅ **22 Barcode Formats** - Scan and generate QR Code, Data Matrix, Aztec, PDF417, Han Xin Code (汉信码), MaxiCode, Micro QR, Code 128/39/93, EAN-13/8, UPC-A/E, Codabar, ITF, Pharmacode, Plessey, MSI Plessey, Telepen, RSS-14, RSS Expanded, and UPC/EAN Extension.
- ✅ **Smart Content Parsing** - Auto-detect WiFi, contacts, calendar, email, URLs, and geo-location with one-tap actions.
- ✅ **Batch Generation** - Import CSV data and generate QR codes in bulk, with ZIP export. (Excel import is **not** currently supported; only `commons-csv` is wired in.)
- ✅ **Style Customization** - Foreground/background colors, center logo, rounded and dot styles.
- ✅ **QR Code Repair** - Enhancement model for blurry or damaged QR codes (TensorFlow Lite).

### History

- ✅ **Full-text Search** - Search by content, time, or type.
- ✅ **Smart Categories** - Auto-classify into links, text, WiFi, contacts, and more.
- ✅ **Favorites / Pinning** - Mark important items.
- ✅ **Tag System** - Custom tag management.
- ✅ **Import / Export** - JSON / CSV backup.

### Scanning Experience

- ✅ **Continuous Scan Mode** - Scan multiple codes without confirmation dialogs.
- ✅ **Haptic Feedback** - Vibration on successful decode.
- ✅ **Auto / Tap-to-focus** - Adapts to code size; tap to focus manually.
- ✅ **Scan Region Limit** - Decode only within a selected area.
- ✅ **Video Scan** - Decode barcodes directly from video files.

### Share & Export

- ✅ **Vector Export** - SVG format for lossless scaling.
- ✅ **Share Templates** - Generate share images with description text.

### Security & Privacy

- ✅ **Malicious Link Detection** - Local blacklist + URL suspicious-feature analysis.
- ✅ **Privacy Mode** - Incognito scanning; nothing is written to history.
- ✅ **App Lock** - Fingerprint or password protection for sensitive history.
- ✅ **Local Encryption** - SQLCipher (AES-256) on the history database.

### UI & UX

- ✅ **Material You** - Android 12+ dynamic colors.
- ✅ **Landscape Support** - Tablet and landscape optimized.
- ✅ **Shortcuts** - Long-press the launcher icon to scan or generate.
- ✅ **Home Widgets** - Quick Scan and Quick Generate widgets.
- ✅ **Internationalization** - Simplified Chinese, English, Japanese, Korean, German.
- ✅ **Animation** - Page transitions and scan-line animation.

### Technical

- ✅ **Unit Tests** - Roundtrip tests for every supported format (Robolectric).
- ✅ **Memory-aware Image Loading** - Large image handling.
- ✅ **Crash Monitoring** - Firebase Crashlytics.
- ✅ **Offline Enhancement** - TensorFlow Lite model.

---

## Supported Barcode Formats

The app supports **22 barcode formats** for both scanning and generation.

### 2D Matrix Codes

| Format | Scan | Generate | Description |
|--------|:----:|:--------:|-------------|
| **QR Code** | ✅ | ✅ | Most common 2D code; widely used for payments, URLs, and contact sharing. |
| **Data Matrix** | ✅ | ✅ | Compact 2D code for very small spaces; common on electronic components and medical devices. |
| **Aztec Code** | ✅ | ✅ | 2D code with no required quiet zone; often used on train tickets and boarding passes. |
| **PDF417** | ✅ | ✅ | Stacked linear barcode capable of holding large text and binary payloads; used on IDs and shipping labels. |
| **MaxiCode** | ✅ | ✅ | Fixed-size 2D code developed by UPS; used in international logistics and air freight. |
| **Micro QR Code** | ✅ | ✅ | Miniaturized QR code for extremely small marking spaces. |
| **Han Xin Code** | ✅ | ✅ | Chinese 2D matrix code (GB/T 36527); supports Chinese characters and ECI. The in-app display name is `Han Xin`. |

### 1D / Linear Barcodes

| Format | Scan | Generate | Description |
|--------|:----:|:--------:|-------------|
| **Code 128** | ✅ | ✅ | High-density alphanumeric encoding; widely used in logistics and supply chain. |
| **Code 39** | ✅ | ✅ | Digits, uppercase letters, and a few symbols; common in industrial and military use. |
| **Code 93** | ✅ | ✅ | Compact improvement over Code 39; common in logistics and industry. |
| **EAN-13** | ✅ | ✅ | 13-digit European Article Number; the standard retail barcode in most countries. |
| **EAN-8** | ✅ | ✅ | Short form of EAN-13 for small packaging. |
| **UPC-A** | ✅ | ✅ | 12-digit Universal Product Code standard in North American retail. |
| **UPC-E** | ✅ | ✅ | Compressed form of UPC-A for small packages. |
| **Codabar** | ✅ | ✅ | Digits and a few symbols; historically used in libraries and blood banks. |
| **ITF** | ✅ | ✅ | Interleaved 2 of 5; numeric-only, common on carton packaging. |
| **Pharmacode** | ✅ | ✅ | One-dimensional code designed for pharmaceutical packaging (numeric, 3 - 131070). |
| **Plessey Code** | ✅ | ✅ | Common in libraries and inventory management. |
| **MSI Plessey** | ✅ | ✅ | Variant of Plessey; common in libraries and inventory management. |
| **Telepen** | ✅ | ✅ | Common in libraries and academic institutions. |

### UPC/EAN Extension and GS1 DataBar

| Format | Scan | Generate | Description |
|--------|:----:|:--------:|-------------|
| **UPC/EAN Extension** | ✅¹ | ✅ | 2- or 5-digit add-on for UPC/EAN. |
| **RSS-14 / GS1 DataBar** | ✅ | ✅ | GS1 standard barcode designed to replace traditional UPC/EAN in retail. |
| **RSS Expanded** | ✅ | ✅ | Variable-length alphanumeric GS1 barcode for batch, weight, and similar attributes. |

¹ The UPC/EAN Extension **cannot be scanned standalone**. The generator attaches it to a dummy EAN-13, and the extension value comes back via ZXing's `ResultMetadataType.UPC_EAN_EXTENSION` rather than as a primary decode.

---

## Screenshots

| Live Scan Result | Image Scan |
|:----------------:|:----------:|
| ![Scan Result](./screenshots/scan_result.jpg) | ![Image Scan](./screenshots/link_history.jpg) |

| QR Generation | History Management |
|:-------------:|:------------------:|
| ![QR Generate](./screenshots/generate_qr.jpg) | ![History](./screenshots/history_list.jpg) |

---

## Tech Stack

- **Language**: Kotlin 2.2.10
- **UI**: Jetpack Compose + XML Layouts (viewBinding)
- **Database**: Room 2.7.1 + SQLCipher 4.5.4 (encrypted)
- **Async**: Kotlin Coroutines
- **Camera**: CameraX 1.3.3
- **Barcode Recognition**: ZXing 3.5.3, ML Kit 17.2.0, WeChatQRCode 2.6.0 (OpenCV)
- **Micro QR**: BoofCV 1.4.0
- **Complex Generation**: OkapiBarcode 0.5.6 (RSS-14 / RSS Expanded / MaxiCode)
- **ML**: TensorFlow Lite 2.17.0
- **CSV Parsing**: Apache Commons CSV 1.14.1
- **Biometric**: androidx.biometric 1.1.0
- **Crash Monitoring**: Firebase Crashlytics
- **Tests**: JUnit 4 + Robolectric 4.16.1

The full file index and architectural notes live in [`docs/knowledge-base.md`](docs/knowledge-base.md).

---

## Build Requirements

- **JDK 21** (required by `compileOptions` and `kotlinOptions.jvmTarget = '21'`)
- **Android SDK 35** (`compileSdk 35`, `targetSdk 35`, `minSdk 24`)
- **Gradle 9.5.1** (already pinned via `gradle-wrapper.properties`)
- **Android Studio Ladybug (2024.2.1) or newer** - AGP 9.2.1 will not load in older IDEs
- **NDK** is **not** required to build; only the native libraries shipped through the WeChatQRCode / OpenCV AARs are used

Set `JAVA_HOME` to your JDK 21 install and put `sdk.dir=/path/to/Android/Sdk` into `local.properties`. `local.properties` is git-ignored.

---

## Build Instructions

```bash
# Clone
git clone https://github.com/XenoAmess-Auto/qr_code_simple.git
cd qr_code_simple

# Build Debug APK (use the wrapper, not a global gradle)
./gradlew :app:assembleDebug

# Run unit tests (Robolectric)
./gradlew :app:testDebugUnitTest

# Install to a connected device
./gradlew :app:installDebug
```

If you see "App not installed" or signature mismatch errors, see `README_CN.md` → "签名问题解决方案" for three workarounds (download CI debug keystore, use CI-built APK, or uninstall and reinstall).

---

## Project Structure

```
app/src/main/java/com/xenoamess/qrcodesimple/
├── MainActivity.kt                  # TabLayout + ViewPager2 entry
├── QRCodeApp.kt                     # Application class; native-lib loader
├── QRCodeScanner.kt                 # Multi-engine scanner (WeChatQRCode -> ZXing -> ML Kit -> BoofCV -> HanXin -> custom)
├── BarcodeGenerator.kt              # Barcode generation entry
├── AdvancedBarcodeGenerator.kt      # Styled generation (colors, logos, shapes)
├── SvgQRCodeGenerator.kt            # SVG (vector) export
├── ShareTemplateGenerator.kt        # Share-image composition
├── ContentParser.kt                 # Plain-text -> WiFi / contact / URL / event / geo parsing
├── ContentActionHandler.kt          # Action dispatcher for parsed content
├── LocaleHelper.kt                  # Language switching helper
│
├── CameraScanActivity.kt            # Live camera scan UI
├── CameraScanFragment.kt            # Live camera scan logic
├── ScanImageActivity.kt             # Static-image scan UI
├── ScanImageFragment.kt             # Static-image scan logic
├── ContinuousScanActivity.kt        # Continuous (batch) scan mode
├── ContinuousScanAdapter.kt         # List adapter for continuous scan
├── VideoScanActivity.kt             # Decode barcodes from video files
├── GenerateActivity.kt              # Single-code generation UI
├── GenerateFragment.kt              # Single-code generation logic
├── BatchGenerateActivity.kt         # CSV-driven batch generation UI
├── BatchResultActivity.kt           # Batch results screen
├── BatchGenerator.kt                # CSV parsing + bulk generation
├── ResultActivity.kt                # Single-code result screen (action menu)
├── HistoryFragment.kt               # History list
├── HistoryAdapter.kt                # History list adapter
├── HistoryBackupManager.kt          # JSON / CSV import & export
├── TagManager.kt                    # Custom-tag CRUD
├── AboutFragment.kt                 # About / acknowledgements
│
├── AppLockManager.kt                # Biometric / PIN lock for history
├── SecurityManager.kt               # Malicious-link heuristics
├── PrivacySettingsActivity.kt       # Privacy mode toggle
├── DatabaseSecurityActivity.kt      # SQLCipher key rotation
├── QRCodeRestorationManager.kt      # QR restoration / enhancement glue
├── QREnhancementModel.kt            # TFLite wrapper for QR repair
├── ImagePerformanceManager.kt       # Large-image memory optimizer
├── CameraFocusManager.kt            # Auto / tap-to-focus
├── ScanRegionView.kt                # Region-of-interest overlay
├── ScannerOverlayView.kt            # Camera scan-line overlay
├── AnimationUtils.kt                # Shared animation helpers
├── EdgeToEdgeExt.kt                 # Edge-to-edge utilities
├── AppShortcutManager.kt            # Static + dynamic shortcuts
├── CrashlyticsManager.kt            # Crashlytics init wrappers
│
├── QuickScanWidget.kt               # Home-screen Quick Scan widget
├── QuickGenerateWidget.kt           # Home-screen Quick Generate widget
├── BackupActivity.kt                # Backup / restore UI
│
├── data/
│   ├── AppDatabase.kt               # Room database
│   ├── Converters.kt                # Room type converters
│   ├── HistoryDao.kt                # History DAO
│   ├── HistoryItem.kt               # History entity + BarcodeFormat + HistoryType enums
│   └── HistoryRepository.kt         # Repository wrapping DAO
│
├── decoder/
│   ├── BarcodeScanUtils.kt          # Shared pre-processing helpers
│   ├── CustomLinearBarcodeScanner.kt# Dispatches to Pharmacode / Plessey / MSI Plessey / Telepen
│   ├── MicroQrCodeScanner.kt        # BoofCV-based Micro QR scanner
│   ├── PharmacodeDecoder.kt         # Pharmacode
│   ├── PlesseyDecoder.kt            # Plessey
│   ├── MsiPlesseyDecoder.kt         # MSI Plessey
│   ├── TelepenDecoder.kt            # Telepen
│   └── hanxin/
│       ├── HanXinEncoder.kt         # Han Xin Code encoder
│       └── HanXinDecoder.kt         # Han Xin Code decoder
│
└── ViewPagerAdapter.kt              # Adapter for MainActivity tab pager
```

Resources of note:

```
app/src/main/res/
├── layout/                          # XML layouts (activities + fragments + list items + widgets)
├── values/                          # Default (English) strings, colors, themes (Material 3)
├── values-de/                       # German
├── values-ja/                       # Japanese
├── values-ko/                       # Korean
├── values-zh/                       # Simplified Chinese
├── values-night/                    # Dark theme overrides
├── drawable/                        # Vector icons (flash, camera switch, etc.)
├── anim/                            # Shared animations
├── menu/                            # Toolbar menus
├── xml/                             # locales_config.xml, shortcuts.xml, widget provider info, ...
└── mipmap-*/                        # Launcher icons
```

---

## Acknowledgements

- [ZXing](https://github.com/zxing/zxing) - QR / barcode recognition fallback
- [ML Kit](https://developers.google.com/ml-kit) - Google ML barcode scanning
- [WeChatQRCode](https://github.com/WeChatCV/opencv_3rdparty) - Primary QR engine (via [jenly1314](https://github.com/jenly1314/WeChatQRCode))
- [BoofCV](https://boofcv.org/) - Micro QR Code detector
- [OkapiBarcode](https://github.com/woo-j/OkapiBarcode) - RSS-14 / RSS Expanded / MaxiCode generation
- [SQLCipher](https://www.zetetic.net/sqlcipher/) - Database encryption
- [TensorFlow Lite](https://www.tensorflow.org/lite) - On-device ML
- [Apache Commons CSV](https://commons.apache.org/proper/commons-csv/) - CSV parsing for batch generation

---

## License

Apache License 2.0 - see [LICENSE](LICENSE).

---

## Contributing

Issues and Pull Requests are welcome. Before opening a PR, please run `./gradlew :app:testDebugUnitTest` and keep `docs/knowledge-base.md` in sync with the code.

---

## Support

If this project helps you, consider supporting development:

[![Ko-fi](https://img.shields.io/badge/Ko--fi-Buy%20me%20a%20coffee-ff5f5f?logo=ko-fi)](https://ko-fi.com/xenoamess)
