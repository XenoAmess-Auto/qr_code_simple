# AGENTS.md

## Project

Single-module Android app (`:app`). Package `com.xenoamess.qrcodesimple`. Kotlin-first with XML layouts and some Jetpack Compose. Build config is in `app/build.gradle`.

## Toolchain

- Kotlin 2.2.10, Android Gradle Plugin 9.2.1, Gradle 9.6.1
- `compileSdk 35`, `minSdk 24`, `targetSdk 35`
- **JDK 21 required** (`sourceCompatibility/targetCompatibility = VERSION_21`, `jvmTarget = '21'`).
  README says JDK 17 — trust build config.
- SDK path in `local.properties` (`/home/xenoamess/Android/Sdk`) is local-only and already gitignored.

## Everyday commands

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Run unit tests (Robolectric). This is the canonical test command.
./gradlew :app:testDebugUnitTest

# Run a single test class
./gradlew :app:testDebugUnitTest --tests "*BarcodeGenerationRoundtripTest*"

# Lint (currently fails on pre-existing errors; do not treat as a required gate)
./gradlew :app:lintDebug
```

## Agent workflow

- After finishing work, stage and commit changes yourself: `git add .` then `git commit -m "..."`. Write concise, repo-style commit messages.
- After finishing work, sync documentation and the knowledge base (`docs/`, `README.md`, `README_CN.md`) so they stay consistent with the code.

## High-signal gotchas

### Bitmap rendering in unit tests
Robolectric's `Canvas.drawColor`/`drawRect`/`drawBitmap` produces bitmaps that ZXing cannot decode reliably. Use `Bitmap.setPixel` / `Bitmap.setPixels` for barcode rendering in tests and production code. The current generators already follow this rule.

### Scanning pipeline order
`QRCodeScanner.scanSync` tries engines in this order:

1. WeChatQRCode (QR only, needs native `opencv_java4`)
2. ZXing MultiFormatReader
3. ML Kit
4. BoofCV Micro QR detector
5. HanXinDecoder (Han Xin Code / 汉信码)
6. Custom linear decoders (Pharmacode, Plessey, MSI Plessey, Telepen)

If you add a format that ZXing/ML Kit cannot read, add it here.

### Adding a new barcode format
At minimum update:

- `data/BarcodeFormat.kt` enum
- `data/HistoryType.kt` enum
- `BarcodeGenerator.generate()` + `validateContent()`
- `QRCodeScanner.toHistoryType()` (app format → history type)
- `decoder/hanxin/HanXinDecoder.kt` for Han Xin Code
- `GenerateFragment.kt` format selector mapping
- `HistoryAdapter.kt` / `ShareTemplateGenerator.kt` if it needs display icons
- Roundtrip test in `app/src/test/java/.../generator/BarcodeGenerationRoundtripTest.kt`

The project rule is: **if it can be scanned it should be generatable, and generated images must scan back**.

### RSS Expanded / GS1 syntax
OkapiBarcode expects GS1 Application Identifiers in square brackets (e.g. `[01]12345678901231`), but ZXing returns them with parentheses (`(01)12345678901231`). Generator converts brackets; tests assert the ZXing-shaped output.

### UPC/EAN Extension
Cannot be scanned standalone. Generator attaches it to a dummy EAN-13 and the extension value is returned in `ResultMetadataType.UPC_EAN_EXTENSION`, not as the primary result.

### Micro QR
BoofCV detector requires a quiet zone. `BarcodeGenerator` pads the raw BoofCV render with 40 px of white before scaling. `MicroQrCodeScanner` does its own ARGB → GrayU8 conversion because `ConvertBitmap.bitmapToGray` does not threshold correctly in tests.

### Tests do not exercise WeChatQRCode
Native OpenCV is not loaded in Robolectric unit tests; `QRCodeApp.isWeChatQRCodeInitialized` stays false and the scan pipeline falls through to ZXing/BoofCV/custom decoders.

### Docs
`docs/` contains architecture notes (mostly accurate). `README.md` project-structure section is stale (e.g. missing `decoder/` package). Treat `docs/knowledge-base.md` and `docs/testing-strategy.md` as the live references.

### No CI
There are no GitHub Actions workflows. Verify locally with `./gradlew :app:testDebugUnitTest` before considering a change done.

### Avoid `git credential fill` as a no-op
Do not use `: | git credential fill` as a placeholder or no-op command. It always fails with `fatal: refusing to work with credential missing host field` and produces noisy output. Use a real no-op such as `true` or `:` (colon by itself) instead.
