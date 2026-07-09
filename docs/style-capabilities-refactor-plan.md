# 条码样式能力重构方案

## 目标

基于 `style-roundtrip-matrix.md` 的测试结果和渲染实现，重构 `AdvancedBarcodeGenerator` 的 `FormatStyleCapabilities`，让样式控件对所有格式都真正可用，同时避免对无效控件做暴露。

核心规则：
1. `moduleFillRatio == 1.0` 时不缩放。
2. `moduleShape` 的“方块”改为“默认”，选“默认”时只做 `moduleFillRatio` 缩放，不做任何形状形变。
3. 所有 Fallback 格式（以及 MaxiCode）增加兜底图片后处理：先对黑白蒙版做腐蚀/形状处理，再按蒙版上色、加前景图/后景图/logo。
4. `positionPatternShape`：只要有视觉改变且至少能回扫一次就开；不确定的也开。

---

## 1. 枚举重命名

| 枚举 | 旧值 | 新值 |
|---|---|---|
| `ModuleShape` | `SQUARE` | `DEFAULT` |
| `PositionPatternShape` | `SQUARE` | `DEFAULT` |

需要同步修改的地方：
- `AdvancedBarcodeGenerator.kt` 中枚举定义、默认值、所有 `when` 分支。
- `StyleConfig` 默认值改为 `ModuleShape.DEFAULT`、`PositionPatternShape.DEFAULT`。
- `FormatStyleCapabilities.sanitized` 中回退默认值也改为 `DEFAULT`。
- `GenerateFragment.kt` 中枚举映射，但 chip 的 id 可以保留 `chipModuleSquare` / `chipPositionSquare` 不动。
- 字符串资源：
  - `values/strings.xml`：`module_shape_square` → `Default`，`position_pattern_square` → `Default`。
  - `values-zh/strings.xml`：`module_shape_square` → `默认`，`position_pattern_square` → `默认`。

---

## 2. 历史数据平滑迁移

`StyleConfig` 通过 JSON 保存在历史记录里。迁移只在反序列化时做，不需要改数据库 schema。

在 `StyleConfigSerialization.kt` 中：
- 把 `ModuleShape.valueOf(...)` 改成辅助函数：
  ```kotlin
  fun parseModuleShape(name: String): ModuleShape = try {
      ModuleShape.valueOf(name)
  } catch (e: IllegalArgumentException) {
      if (name.uppercase() == "SQUARE") ModuleShape.DEFAULT else ModuleShape.DEFAULT
  }
  ```
- 同理给 `PositionPatternShape` 也加一个。
- JSON 默认 fallback 字符串从 `"SQUARE"` 改成 `"DEFAULT"`。

这样老的历史记录 `"moduleShape": "SQUARE"` 会正常解析成 `DEFAULT`。

需要新增单元测试验证历史 JSON 迁移：
- 验证 `"moduleShape": "SQUARE"` → `ModuleShape.DEFAULT`。
- 验证 `"positionPatternShape": "SQUARE"` → `PositionPatternShape.DEFAULT`。

---

## 3. 兜底图片后处理实现

新增 `AdvancedBarcodeGenerator.applyShapeAndFillFallback(bitmap, style)`：

1. **二值化**：把输入位图转成 Boolean 蒙版，黑=前景，白=背景。
2. **估算单元尺寸**：用距离变换或行/列游程长度，得到条码中最小条杠/模块宽度 `unitSize`。
3. **连通域分割**：使用 4-连通找出每个前景块。
4. **按形状/填充处理**：对每个前景块，计算其包围盒和质心：
   - `DEFAULT`：按 `moduleFillRatio` 向中心缩放。方块仍是方块，长条仍是长条。
   - `CIRCLE`：只保留以质心为中心、半径 `min(w, h) * fillRatio / 2` 的圆盘区域。
   - `ROUNDED`：保留圆角矩形区域，宽度/高度按 `fillRatio` 缩放，圆角半径取 `min(scaledW, scaledH) / 2`。
5. 返回处理后的新 Boolean 蒙版。

---

## 4. 渲染顺序

关键原则：先处理黑白蒙版，再按蒙版上色；不要先贴图/上色再做腐蚀。

1. 生成原始条码位图。
2. 二值化 → 黑白蒙版。
3. 在蒙版上应用 `applyShapeAndFillFallback`。
4. 创建输出位图，遍历每个像素：
   - 在 logo 区域先跳过（后续画 logo）。
   - 蒙版为前景 → 使用 `resolveColor()`（颜色/渐变/前景图）。
   - 蒙版为背景 → 使用 `resolveBackgroundColor()`（纯色/后景图）。
5. 最后画 logo。

---

## 5. 渲染路径改造

- `renderFallback`：改成上述蒙版优先流程。
- `MaxiCode`：先渲染到一个临时黑白位图，再走同样的蒙版后处理，最后上色。hexagon 和 target 都参与腐蚀。
- `GridLayout` / `LinearLayout` / `QR Code`：保持现有结构化渲染，仅把 `SQUARE` 分支对应到新的 `DEFAULT` 枚举名。

---

## 6. `FormatStyleCapabilities` 最终映射

| 控件 | 规则 |
|---|---|
| `moduleShape` | **所有格式都开**。Fallback 和 MaxiCode 也有兜底实现。 |
| `moduleFillRatio` | **所有格式都开**。 |
| `positionPatternShape` | 只给有定位图案或 Guard 的格式开。 |
| `ecLevel` | MaxiCode 关闭；其余保持现状。 |

`positionPatternShape` 开启清单：
- QR Code
- Data Matrix、Aztec、PDF417、Micro QR、Han Xin、Swiss QR、UPN QR、Grid Matrix
- EAN-13/8、UPC-A/E、RSS-14/RSS-Expanded、Pharmacode、Plessey、MSI Plessey、Telepen

`positionPatternShape` 不开的格式：
- CODABAR、CODE-128、CODE-39、CODE-93、ITF（无 Guard）
- MaxiCode
- UPC/EAN Extension
- 其它纯 Fallback 的仅生成格式

---

## 7. 测试更新

### 7.1 `FormatStyleCapabilitiesTest`
- 所有 `ModuleShape.SQUARE` / `PositionPatternShape.SQUARE` 改成 `DEFAULT`。
- 更新能力映射断言。
- 新增历史 JSON 迁移测试：验证 `"moduleShape":"SQUARE"` 和 `"positionPatternShape":"SQUARE"` 能正确解析成 `DEFAULT`。

### 7.2 `StyleRawRoundtripMatrixTest`
- 同步枚举名。

### 7.3 新增 `FallbackStyleShapeFillTest`
- 覆盖 `UPC_EAN_EXTENSION` 和若干仅生成 Fallback 格式。
- 验证 `moduleShape` / `moduleFillRatio` 能改变位图外观。
- 可扫描的格式（如 `UPC_EAN_EXTENSION`）验证仍能回扫。

### 7.4 UI 测试
- 如果校验了 chip 文本，同步改成“默认”。

---

## 8. 文档更新

- `docs/style-roundtrip-matrix.md`：
  - 更新枚举名。
  - 更新控件开关清单。
  - 说明 Fallback 格式现在支持 `moduleShape` / `moduleFillRatio`。
- 检查 `README.md` / `README_CN.md` 是否提到“方块”或 `SQUARE`，一并修改。
- 本文件 `docs/style-capabilities-refactor-plan.md` 记录本方案。

---

## 9. 验证命令

```bash
./gradlew :app:testDebugUnitTest --tests "*FormatStyleCapabilitiesTest*"
./gradlew :app:testDebugUnitTest --tests "*StyleRawRoundtripMatrixTest*"
./gradlew :app:testDebugUnitTest --tests "*FallbackStyleShapeFillTest*"
```

---

## 10. 风险与注意事项

1. **形态学腐蚀可能破坏扫描**：对一维码长条做圆角/圆形腐蚀后，可能导致扫描失败。新增 `FallbackStyleShapeFillTest` 必须覆盖回扫验证。
2. **MaxiCode 目标图案被腐蚀**：MaxiCode 的圆形目标也会参与腐蚀，可能影响识别。需要在测试中确认。
3. **历史 JSON 兼容**：反序列化时务必兼容旧 `"SQUARE"` 字符串，否则历史记录无法加载。
4. **性能**：连通域分析在大图（800x800）上可能耗时，需要确保测试超时足够。
