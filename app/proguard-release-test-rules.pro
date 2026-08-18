# AndroidTest omits dependencies already supplied by the target APK. Preserve the
# shared runtime only in -PreleaseInstrumentedTest builds so the runner can start.
-keep class androidx.tracing.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }

# The minified test APK calls these target-APK entry points by their source names.
-keep class com.xenoamess.qrcodesimple.SvgQRCodeGenerator { *; }
-keep class com.xenoamess.qrcodesimple.BatchGenerator { *; }
-keep class com.xenoamess.qrcodesimple.BatchGenerator$* { *; }
-keep class com.xenoamess.qrcodesimple.BatchGenerateActivity { *; }
-keep class com.xenoamess.qrcodesimple.BatchResultActivity { *; }
-keep class com.xenoamess.qrcodesimple.data.BarcodeFormat { *; }
-keep class com.xenoamess.qrcodesimple.R$* { *; }
