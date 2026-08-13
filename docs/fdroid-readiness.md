# F-Droid 就绪说明

本项目不向 Google Play 分发（见 `docs/knowledge-base.md` 第 8 节需求黑名单），但支持 F-Droid 纯源码构建。本文记录已落地的改造与剩余人工步骤。

## 已完成（代码侧）

构建时传 `-Pfdroid` 即可获得 F-Droid 兼容产物：

```bash
./gradlew :app:assembleRelease -Pfdroid
```

`-Pfdroid` 的效果（全部在 `app/build.gradle` 的 `isFdroid` 分支内，不影响默认构建）：

1. **剔除 vendored 预编译 `libapkpatch.so` / `libc++_shared.so`**（`src/main/jniLibs`）。增量补丁功能随之缺席；F-Droid 签名与本仓库基线证书不同，应用内自更新本来就不可能在 F-Droid 产物上工作。
2. **剔除 proprietary ML Kit 依赖**（`com.google.mlkit:barcode-scanning`、`com.google.android.gms:play-services-mlkit-barcode-scanning`）。真实引擎 `MlKitEngine` 物理隔离在 `app/src/playstore/java/`，F-Droid 构建改用 `app/src/fdroid/java/` 下的同名 stub（直接返回空结果），扫描管线自动回退 ZXing / WeChatQR / BoofCV / 自研解码器。
3. **`BuildConfig.IS_FDROID=true`**，About 页隐藏"检查更新 / Beta / 自动检查"整行入口（更新由 F-Droid 客户端分发）。

验证：`assembleRelease -Pfdroid` 产物中无 `libapkpatch.so`、`libbarhopper_v3.so` 及 ML Kit 类。

## 剩余人工步骤（无法在本仓库完成）

1. 向 [fdroiddata](https://gitlab.com/fdroid/fdroiddata) 提交 metadata MR。recipe 要点：
   - `build.gradle` 调用加 `-Pfdroid`；
   - 需要完整非浅克隆（versionCode/versionName 由 Git 历史推导，见 `docs/versioning-and-update-system.md`）；
   - Fastlane 元数据已有 en-US / zh-CN（描述、截图、changelog），F-Droid 会直接消费 `fastlane/metadata/android/`。
2. 首次提交建议附注：ML Kit 为 proprietary，已通过 `-Pfdroid` 剔除；`libapkpatch.so` 为 MIT 上游预编译，同开关剔除。

## 注意

- 不要把 `-Pfdroid` 用于本仓库的 Stable/Beta 发布（那会移除增量更新客户端能力并隐藏自更新入口）。
- F-Droid 产物的数据库签名证书由 F-Droid 持有，与本仓库 debug 基线不同，无法与 GitHub 渠道 APK 互相覆盖安装。
