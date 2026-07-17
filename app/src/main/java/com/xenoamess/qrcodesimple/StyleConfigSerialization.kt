package com.xenoamess.qrcodesimple

import android.graphics.Color
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.xenoamess.qrcodesimple.AdvancedBarcodeGenerator.ColorStop
import com.xenoamess.qrcodesimple.AdvancedBarcodeGenerator.GradientType
import com.xenoamess.qrcodesimple.AdvancedBarcodeGenerator.ModuleShape
import com.xenoamess.qrcodesimple.AdvancedBarcodeGenerator.PositionPatternShape
import com.xenoamess.qrcodesimple.AdvancedBarcodeGenerator.StyleConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * 将 StyleConfig 序列化为 JSON 字符串。
 * Bitmap 字段不保存（logoBitmap、foregroundBitmap、backgroundBitmap），恢复时为 null。
 */
fun StyleConfig.toJson(): String {
    val json = JSONObject().apply {
        put("foregroundColor", foregroundColor)
        put("backgroundColor", backgroundColor)
        put("cornerRadius", cornerRadius)
        put("logoScale", logoScale)
        put("logoShape", logoShape.name)
        put("logoCornerRadius", logoCornerRadius.toDouble())
        put("ecLevel", ecLevel.name)
        put("moduleShape", moduleShape.name)
        put("moduleFillRatio", moduleFillRatio.toDouble())
        put("positionPatternShape", positionPatternShape.name)
        put("gradientAngle", gradientAngle.toDouble())
        put("gradientType", gradientType.name)

        val stopsArray = JSONArray()
        gradientStops.forEach { stop ->
            stopsArray.put(JSONObject().apply {
                put("position", stop.position.toDouble())
                put("color", stop.color)
            })
        }
        put("gradientStops", stopsArray)
    }
    return json.toString()
}

fun parseModuleShape(name: String): ModuleShape =
    try {
        ModuleShape.valueOf(name)
    } catch (e: IllegalArgumentException) {
        ModuleShape.DEFAULT
    }

fun parsePositionPatternShape(name: String): PositionPatternShape =
    try {
        PositionPatternShape.valueOf(name)
    } catch (e: IllegalArgumentException) {
        PositionPatternShape.DEFAULT
    }

fun parseLogoShape(name: String): AdvancedBarcodeGenerator.LogoShape =
    try {
        AdvancedBarcodeGenerator.LogoShape.valueOf(name)
    } catch (e: IllegalArgumentException) {
        AdvancedBarcodeGenerator.LogoShape.SQUARE
    }

/**
 * 从 JSON 字符串反序列化 StyleConfig。
 * 解析失败时返回 null。
 */
fun styleConfigFromJson(jsonString: String): AdvancedBarcodeGenerator.StyleConfig? {
    return try {
        val json = JSONObject(jsonString)
        val stopsArray = json.optJSONArray("gradientStops")
        val stops = mutableListOf<ColorStop>()
        if (stopsArray != null) {
            for (i in 0 until stopsArray.length()) {
                val stopObj = stopsArray.getJSONObject(i)
                stops.add(ColorStop(
                    position = stopObj.getDouble("position").toFloat(),
                    color = stopObj.getInt("color")
                ))
            }
        }

        AdvancedBarcodeGenerator.StyleConfig(
            foregroundColor = json.optInt("foregroundColor", Color.BLACK),
            backgroundColor = json.optInt("backgroundColor", Color.WHITE),
            cornerRadius = json.optDouble("cornerRadius", 0.0).toFloat(),
            logoScale = json.optDouble("logoScale", 0.2).toFloat(),
            logoShape = parseLogoShape(json.optString("logoShape", "SQUARE")),
            logoCornerRadius = json.optDouble("logoCornerRadius", 0.2).toFloat(),
            ecLevel = ErrorCorrectionLevel.valueOf(json.optString("ecLevel", "H")),
            moduleShape = parseModuleShape(json.optString("moduleShape", "DEFAULT")),
            moduleFillRatio = json.optDouble("moduleFillRatio", 1.0).toFloat(),
            positionPatternShape = parsePositionPatternShape(json.optString("positionPatternShape", "DEFAULT")),
            gradientAngle = json.optDouble("gradientAngle", 0.0).toFloat(),
            gradientType = GradientType.valueOf(json.optString("gradientType", "LINEAR")),
            gradientStops = stops
        )
    } catch (e: Exception) {
        null
    }
}
