# AGENTS.md

## Project

Single-module Android app (`:app`). Package `com.xenoamess.qrcodesimple`. Kotlin-first with XML layouts (ViewBinding; no Jetpack Compose). Build config is in `app/build.gradle`.

## Toolchain

- Kotlin 2.3.21, Android Gradle Plugin 9.3.1, Gradle 9.7.0
- `compileSdk 35`, `minSdk 28`, `targetSdk 35`
- **JDK 21 required** (`sourceCompatibility/targetCompatibility = VERSION_21`, `jvmTarget = '21'`).
- README and build config both require JDK 21.
- SDK path in `local.properties` (`/home/xenoamess/Android/Sdk`) is local-only and already gitignored.
- All Gradle tasks require a full, non-shallow Git checkout because Android version metadata is Git-derived. Do not build from a source archive; repair a shallow checkout with `git fetch --unshallow --tags`.

## Everyday commands

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Run unit tests (Robolectric). This is the canonical test command.
./gradlew :app:testDebugUnitTest

# Run a single test class
./gradlew :app:testDebugUnitTest --tests "*BarcodeGenerationRoundtripTest*"

# Lint. Runs in CI and must stay clean (0 errors / 0 warnings / 0 hints).
# MissingTranslation/ExtraTranslation are errors: new string resources must be
# added to all 10 locales (values, values-zh, values-de, values-ja, values-ko,
# values-fr, values-es, values-it, values-pt, values-ru).
# HardcodedText is an error: real layout texts use string resources, runtime
# placeholders use tools:text. The baseline holds one intentional entry
# (InconsistentLayout for the sw600dp two-pane variant).
./gradlew :app:lintDebug

# Coverage floor gate. Runs in CI (instruction >= 0.80, line >= 0.75).
./gradlew :app:jacocoTestCoverageVerification -PexcludeExtendedUiTests

# Release build (R8 + shrinkResources; uses RELEASE_KEYSTORE_* when set,
# otherwise app/debug.keystore. CI rejects a configured release certificate
# that differs from the Debug signing baseline.)
./gradlew :app:assembleRelease :app:bundleRelease :app:writeVersionInfo
```

## Agent workflow

- **Default behavior: after finishing work, always stage, commit, and push changes yourself unless the user explicitly says not to.** Do not wait for a separate confirmation to commit.
- Use `git add .`, `git commit -m "..."`, then `git push`. Write concise, repo-style commit messages. If the push is rejected, pull/rebase first and then push.
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

- `data/BarcodeFormat.kt` enum (set `isScannable` appropriately)
- `data/HistoryType.kt` enum (add `GENERATED_ONLY` fallback is usually enough)
- `BarcodeGenerator.generate()` + `validateContent()`
- `QRCodeScanner.toHistoryType()` (app format → history type)
- `decoder/hanxin/HanXinDecoder.kt` for Han Xin Code
- `GenerateFragment.kt` format selector mapping
- `HistoryAdapter.kt` / `ShareTemplateGenerator.kt` if it needs display icons
- Roundtrip / generation test in `app/src/test/java/.../generator/BarcodeGenerationRoundtripTest.kt`

The project rule is:
- **If a format can be scanned, it must be generatable and generated images must scan back.**
- **If a format can only be generated (not scanned by the current engines), mark it `isScannable = false`; the UI will show a generate-only warning.**

### Data Matrix generation and UTF-8
`BarcodeGenerator` uses OkapiBarcode `DataMatrix` with `setEciMode(26)` (UTF-8) for non-ASCII content, enabling Chinese and other Unicode text. For ASCII-only content it keeps the original ZXing generator to preserve the existing scan roundtrip.

### RSS Expanded / GS1 syntax
OkapiBarcode expects GS1 Application Identifiers in square brackets (e.g. `[01]12345678901231`), but ZXing returns them with parentheses (`(01)12345678901231`). Generator converts brackets; tests assert the ZXing-shaped output.

### Generate-only formats (OkapiBarcode-only formats)
Many OkapiBarcode formats (postal codes, Code 2 of 5 variants, Codablock F, Grid Matrix, Code One, etc.) are not supported by the current scanning stack. They are marked `isScannable = false` and rendered with a generate-only warning in `GenerateFragment`. Tests for these formats only verify that `BarcodeGenerator.generate()` succeeds.

### OkapiBarcode-specific encoder quirks
- **Code One**: OkapiBarcode 0.5.6 crashes when auto-selecting the version for some inputs. The generator works around this by trying fixed versions `S → T → A → B → … → H`.
- **Grid Matrix**: the same Okapi version crashes on pure ASCII content. The validator now requires at least one non-ASCII character (typically Chinese).

### UPC/EAN Extension
Cannot be scanned standalone. Generator attaches it to a dummy EAN-13 and the extension value is returned in `ResultMetadataType.UPC_EAN_EXTENSION`, not as the primary result.

### Micro QR
BoofCV detector requires a quiet zone. `BarcodeGenerator` pads the raw BoofCV render with 40 px of white before scaling. `MicroQrCodeScanner` does its own ARGB → GrayU8 conversion because `ConvertBitmap.bitmapToGray` does not threshold correctly in tests.

### Tests do not exercise WeChatQRCode
Native OpenCV is not loaded in Robolectric unit tests; `QRCodeApp.isWeChatQRCodeInitialized` stays false and the scan pipeline falls through to ZXing/BoofCV/custom decoders.

### Docs
`docs/` contains architecture notes. Keep `README.md`, `README_CN.md`, `docs/knowledge-base.md`, `docs/testing-strategy.md`, and `docs/versioning-and-update-system.md` consistent when relevant behavior changes.

### Versioning, releases, and updates

- `versionCode` is the complete-history `git rev-list --count HEAD`; `versionName` comes from the nearest valid `vMAJOR.MINOR.PATCH` tag, with `+N` commits ahead, or `0.0.0+<count>` when no `v*` tag exists. A malformed nearest `v*` tag fails the build.
- `BuildConfig.GIT_HASH` is the short eight-character commit hash. `generateChangelog` packages generated `CHANGELOG.txt` as an app asset, and `writeVersionInfo` is the workflow's source of version metadata.
- Stable tags must be pushed as strict `vMAJOR.MINOR.PATCH` and point at current `origin/master`. `.github/workflows/release.yml` validates the tag, runs debug/unit/lint/coverage validation, then publishes with the fixed `app/debug.keystore` certificate. Optional `RELEASE_KEYSTORE_*` secrets are accepted only when their certificate matches that baseline.
- Master beta publication waits for `build` and `android-test`, uses the same signing baseline, and deploys the signed beta APK plus `version.json` to Pages. Pages deployment also publishes coverage.
- Preserve the `app/debug.keystore` certificate across main-branch Debug, Beta, and Stable releases. CI does not upload the keystore artifact. See `docs/versioning-and-update-system.md` for the schema, delta constraints, and rollout process.

### CI and Coverage

- CI lives in `.github/workflows/build.yml`: push/PRs to `master`/`main` run the debug build, unit tests, lint, and coverage gate; emulator instrumented tests run separately. A master push can publish beta only after both jobs pass.
- Coverage is generated by JaCoCo (`./gradlew :app:jacocoTestReport`) and deployed alongside the beta channel to GitHub Pages by the `coverage-pages` job.
- `app/build.gradle` disables AGP's built-in test coverage (`enableUnitTestCoverage = false`) and uses the Gradle JaCoCo plugin directly, with `includeNoLocationClasses = true` so Robolectric-loaded classes are recorded. Internal JDK reflect classes are excluded from instrumentation to avoid Gradle worker serialization failures.
- The README badge points to `https://xenoamess-auto.github.io/qr_code_simple/coverage.html`.
- Verify locally with `./gradlew :app:testDebugUnitTest` before considering a change done.

### Avoid `git credential fill` as a no-op
Do not use `: | git credential fill` as a placeholder or no-op command. It always fails with `fatal: refusing to work with credential missing host field` and produces noisy output. Use a real no-op such as `true` or `:` (colon by itself) instead.

### Requirement blacklist
`docs/knowledge-base.md` section 8 lists explicitly rejected directions (e.g. Google Play distribution, upgrading apksigner past 34.0.0, per-ABI APK splits). Never re-propose them in reviews, gap analyses, or iteration plans.
