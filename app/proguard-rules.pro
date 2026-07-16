# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep OpenCV classes
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# Keep WeChatQRCode classes
-keep class com.king.opencv.wechat.qrcode.** { *; }
-dontwarn com.king.opencv.wechat.qrcode.**

# Keep ZXing classes
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Keep CameraScan classes
-keep class com.king.camera.scan.** { *; }
-dontwarn com.king.camera.scan.**

# OkapiBarcode（条码生成，含反射/内部工具类）
-keep class uk.org.okapibarcode.** { *; }
-dontwarn uk.org.okapibarcode.**

# BoofCV（Micro QR 检测）
-keep class boofcv.** { *; }
-dontwarn boofcv.**

# SQLCipher（native 加载）
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# Apache POI（Excel 导入；大量反射与 XML Schema 类）
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
-keep class org.apache.xmlbeans.** { *; }
-dontwarn org.apache.xmlbeans.**
-keep class org.openxmlformats.** { *; }
-dontwarn org.openxmlformats.**
-keep class com.microsoft.schemas.** { *; }
-dontwarn com.microsoft.schemas.**
-keep class org.etsi.** { *; }
-dontwarn org.etsi.**
-dontwarn org.apache.commons.io.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.logging.**
-dontwarn org.apache.log4j.**
-keep class javax.xml.stream.** { *; }
-dontwarn javax.xml.stream.**

# ML Kit / play-services 通常自带 consumer rules，这里兜底
-dontwarn com.google.android.gms.**
-dontwarn com.google.mlkit.**

# 以下为本库在 Android 上不可用的传递引用（R8 missing classes 抑制）：
# POI snakeyaml 的 java.beans 反射、XDDF 曲线用的 java.awt（仅服务端路径）
-dontwarn java.awt.**
-dontwarn java.beans.**
# BoofCV 传递依赖的 lombok 注解
-dontwarn lombok.**
# uCrop 的 BitmapLoadTask 仅在加载 http(s) 图片时用到 okhttp，本应用只用本地 Uri
-dontwarn okhttp3.**
-dontwarn okio.**
