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
