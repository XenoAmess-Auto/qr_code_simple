# QR Code Simple - 条码格式支持文档

## 一、当前已支持的条码格式

本项目当前支持 **21 种** 条码格式的扫描，其中 13 种支持生成，覆盖二维码、堆叠式条码和一维条码三大类。

### 1.1 二维码 (2D Matrix Codes)

| 格式 | 说明 | 扫描 | 生成 | 典型应用场景 |
|------|------|:----:|:----:|--------------|
| **QR Code** | 快速响应码，最常见的二维码 | ✅ | ✅ | 支付、网址、名片、WiFi共享 |
| **Data Matrix** | 数据矩阵码，可在极小空间存储数据 | ✅ | ✅ | 电子元器件、医疗器械标识 |
| **Aztec Code** | 阿兹特克码，无需静音区 | ✅ | ✅ | 火车票、登机牌、会员码 |
| **PDF417** | 便携式数据文件，堆叠式线性条码 | ✅ | ✅ | 身份证、驾照、快递面单 |

### 1.2 一维条码 (1D/Linear Barcodes)

| 格式 | 说明 | 扫描 | 生成 | 典型应用场景 |
|------|------|:----:|:----:|--------------|
| **Code 128** | 高密度字母数字编码 | ✅ | ✅ | 物流、仓储、供应链 |
| **Code 39** | 支持数字、大写字母和部分符号 | ✅ | ✅ | 工业、军事、医疗 |
| **Code 93** | Code 39 的高密度改进版 | ✅ | ✅ | 物流、工业 |
| **EAN-13** | 欧洲商品编码，13位数字 | ✅ | ✅ | 零售商品（中国常见） |
| **EAN-8** | 短版 EAN，8位数字 | ✅ | ✅ | 小包装商品 |
| **UPC-A** | 通用产品代码，12位数字 | ✅ | ✅ | 北美零售商品 |
| **UPC-E** | UPC-A 的压缩版，6-8位 | ✅ | ✅ | 小包装商品（北美） |
| **Codabar** | 支持数字和特定符号 | ✅ | ✅ | 图书馆、血库、照片冲印 |
| **ITF** | 交叉25码，纯数字 | ✅ | ✅ | 纸箱包装、物流外箱 |

### 1.3 仅扫描支持的格式

| 格式 | 说明 | 扫描 | 生成 | 备注 |
|------|------|:----:|:----:|------|
| **UPC/EAN Extension** | UPC/EAN 的2位或5位扩展码 | ✅ | ❌ | 作为主码的附加信息 |
| **RSS-14 / GS1 DataBar** | GS1 标准条码，用于替代传统 UPC/EAN | ✅ | ❌ | 零售、生鲜、医疗 |
| **RSS Expanded** | RSS-14 的扩展版，可变长度字母数字 | ✅ | ❌ | 生产日期、批次号、重量 |
| **MaxiCode** | UPS 开发的固定大小二维条码 | ✅ | ❌ | 国际物流、航空货运 |
| **Micro QR Code** | 微型 QR 码，极小空间标识 | ✅ | ❌ | 极小空间标识 |
| **Pharmacode** | 药品包装专用一维码 | ✅ | ❌ | 药品包装 |
| **Plessey Code / MSI Plessey** | 图书馆、库存管理常用 | ✅ | ❌ | 图书馆、库存 |
| **Telepen** | 图书馆、学术机构常用 | ✅ | ❌ | 图书馆、学术 |

### 1.4 内容解析支持

扫描结果可自动识别以下内容格式并触发一键操作：

- **WiFi** (`WIFI:`) - 一键连接无线网络
- **联系人** (`BEGIN:VCARD` / `MECARD:`) - 导入通讯录
- **日历事件** (`BEGIN:VEVENT`) - 添加到日历
- **邮件** (`mailto:` / `MATMSG:`) - 打开邮件客户端
- **地理位置** (`geo:`) - 打开地图导航
- **短信** (`sms:` / `SMSTO:`) - 打开短信界面
- **电话** (`tel:` / 纯数字) - 拨打电话
- **URL** (`http://` / `www.`) - 打开浏览器
- **纯文本** - 复制或分享

---

## 二、扫描引擎支持矩阵

项目采用 **三引擎冗余扫描** 架构，各引擎对不同格式的支持能力如下：

| 条码格式 | WeChatQRCode | ZXing 3.5.3 | ML Kit 17.2.0 |
|----------|:------------:|:-----------:|:-------------:|
| QR Code | ✅ | ✅ | ✅ |
| Data Matrix | ❌ | ✅ | ✅ |
| Aztec | ❌ | ✅ | ✅ |
| PDF417 | ❌ | ✅ | ✅ |
| Code 128 | ❌ | ✅ | ✅ |
| Code 39 | ❌ | ✅ | ✅ |
| Code 93 | ❌ | ✅ | ✅ |
| EAN-13 | ❌ | ✅ | ✅ |
| EAN-8 | ❌ | ✅ | ✅ |
| UPC-A | ❌ | ✅ | ✅ |
| UPC-E | ❌ | ✅ | ✅ |
| UPC/EAN Extension | ❌ | ✅ | ❌ |
| Codabar | ❌ | ✅ | ✅ |
| ITF | ❌ | ✅ | ✅ |
| RSS-14 / GS1 DataBar | ❌ | ✅ | ❌ |
| RSS Expanded | ❌ | ✅ | ❌ |
| MaxiCode | ❌ | ✅ | ❌ |

> **说明**：WeChatQRCode 仅针对 QR Code 进行了深度优化，对低质量/扭曲二维码识别率最高；ZXing 覆盖最广；ML Kit 在设备端推理速度最快。此外，项目通过 BoofCV 支持 Micro QR Code 扫描，并通过自定义解码器支持 Pharmacode、Plessey、MSI Plessey 和 Telepen。

---

## 三、当前依赖库原生不支持但已补充实现的条码格式

以下格式无法通过现有三引擎（WeChatQRCode / ZXing / ML Kit）直接解码，但项目已引入额外库或自定义解码器进行支持：

| 格式 | 实现方式 | 扫描 | 生成 | 备注 |
|------|----------|:----:|:----:|:----:|
| **Micro QR Code** | BoofCV 1.4.0 | ✅ | ❌ | 极小空间二维码 |
| **Pharmacode** | 自定义一维解码器 | ✅ | ❌ | 药品包装 |
| **Plessey Code** | 自定义一维解码器 | ✅ | ❌ | 图书馆、零售 |
| **MSI Plessey** | 自定义一维解码器 | ✅ | ❌ | 库存管理 |
| **Telepen** | 自定义一维解码器 | ✅ | ❌ | 图书馆、学术 |

## 四、当前技术栈暂不支持且未集成的条码格式

以下格式在当前技术栈中**暂不支持**，如需支持需要引入额外的第三方库或自研核心解码算法：

| 格式 | 类型 | 应用场景 | 推荐库/方案 |
|------|------|----------|-------------|
| **Han Xin Code (汉信码)** | 二维码 | 中国国家标准，政务、军用 | 专用解码库或自研（无成熟开源库） |
| **GM Code (GM码)** | 二维码 | 中国国家标准 | 专用解码库（无成熟开源库） |

---

## 五、扩展实现指南

### 4.1 启用 ZXing 已支持但未启用的扫描格式

以 RSS-14 为例，修改 `app/src/main/java/com/xenoamess/qrcodesimple/QRCodeScanner.kt`：

```kotlin
// 在 scanWithZXing() 方法的 configs 列表中
put(DecodeHintType.POSSIBLE_FORMATS, listOf(
    com.google.zxing.BarcodeFormat.QR_CODE,
    // ... 现有格式 ...
    com.google.zxing.BarcodeFormat.RSS_14,        // 新增
    com.google.zxing.BarcodeFormat.RSS_EXPANDED,  // 新增
    com.google.zxing.BarcodeFormat.MAXICODE       // 新增
))
```

### 4.2 添加历史记录类型（可选）

如需在历史记录中区分新格式，修改 `app/src/main/java/com/xenoamess/qrcodesimple/data/HistoryItem.kt`：

```kotlin
enum class HistoryType {
    // ... 现有类型 ...
    RSS_14,       // 新增
    RSS_EXPANDED, // 新增
    MAXICODE      // 新增
}
```

### 4.3 关于 RSS/GS1 DataBar 的注意事项

- RSS-14 有四种变体：RSS-14、RSS-14 Truncated、RSS-14 Stacked、RSS-14 Stacked Omnidirectional，ZXing 均可识别
- RSS Expanded 也有 Stacked 变体
- 这些格式在零售行业正在逐步替代传统 UPC/EAN，尤其在生鲜和医疗领域

### 4.4 关于自定义一维解码器的注意事项

- Pharmacode、Plessey、MSI Plessey、Telepen 基于项目内自定义的轻量解码器实现
- 这些解码器对条码质量和打印质量要求较高，建议在清晰、对比度高的图像上使用
- 目前仅支持扫描，不支持生成

---

## 五、格式选择建议

### 5.1 生成场景推荐

| 场景 | 推荐格式 | 理由 |
|------|----------|------|
| 通用分享/支付 | QR Code | 支持中文、容量大、识别率高 |
| 小尺寸标签 | Data Matrix | 可在 2x2mm 内编码 |
| 火车票/登机牌 | Aztec Code | 无需静音区，容错性好 |
| 身份证/驾照 | PDF417 | 可存储大量文本和二进制数据 |
| 物流仓储 | Code 128 | 高密度、支持全 ASCII |
| 零售商品 | EAN-13 | 中国/国际通用标准 |
| 北美零售 | UPC-A | 北美标准 |
| 工业/军事 | Code 39 | 自检能力强，工业环境稳定 |

### 5.2 扫描场景建议

- **默认模式**：启用全部 21 种扫描格式，多引擎自动切换
- **零售专用**：可关闭二维码，仅启用 EAN/UPC/Code 128/RSS-14/RSS Expanded 以提升速度
- **物流专用**：启用 Code 128、Code 39、PDF417、Data Matrix、MaxiCode
- **图书/档案**：启用 Codabar、Code 39
- **药品/医疗**：启用 Pharmacode、EAN-13、Data Matrix
- **极小空间标签**：启用 Data Matrix、Micro QR Code

---

## 六、相关文件索引

| 文件路径 | 说明 |
|----------|------|
| `app/src/main/java/com/xenoamess/qrcodesimple/data/HistoryItem.kt` | 条码格式枚举定义（第 38 行起） |
| `app/src/main/java/com/xenoamess/qrcodesimple/BarcodeGenerator.kt` | 条码生成器，支持 13 种格式生成 |
| `app/src/main/java/com/xenoamess/qrcodesimple/QRCodeScanner.kt` | 多引擎扫描器，第 139、162 行为格式配置 |
| `app/src/main/java/com/xenoamess/qrcodesimple/decoder/MicroQrCodeScanner.kt` | BoofCV Micro QR Code 扫描器 |
| `app/src/main/java/com/xenoamess/qrcodesimple/decoder/CustomLinearBarcodeScanner.kt` | 自定义一维码扫描器入口 |
| `app/build.gradle` | 依赖配置，ZXing 3.5.3 / ML Kit 17.2.0 / BoofCV 1.4.0 |

---

*文档版本：v1.2*  
*生成日期：2026-06-12*  
*基于代码版本：0.1.5*
