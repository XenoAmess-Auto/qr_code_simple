package com.xenoamess.qrcodesimple

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import com.xenoamess.qrcodesimple.data.HistoryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 分享图片模板生成器
 */
object ShareTemplateGenerator {

    data class TemplateConfig(
        val title: String,
        val description: String = "",
        val showQrCode: Boolean = true,
        val backgroundColor: Int = Color.WHITE,
        val textColor: Int = Color.BLACK,
        val accentColor: Int = Color.parseColor("#00BCD4"),
        val showLogo: Boolean = true
    )

    /**
     * 生成带模板的分享图片
     */
    suspend fun generateShareImage(
        context: Context,
        qrCodeBitmap: Bitmap,
        content: String,
        type: HistoryType,
        config: TemplateConfig = TemplateConfig(
            title = getDefaultTitle(type),
            description = getDefaultDescription(content, type)
        )
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val width = 1080
            val qrSize = 600
            val padding = 80
            val headerHeight = 200
            val footerHeight = 160
            val totalHeight = headerHeight + qrSize + padding * 2 + footerHeight

            val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // 背景
            canvas.drawColor(config.backgroundColor)

            // 绘制装饰条
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = config.accentColor
            }
            canvas.drawRect(0f, 0f, width.toFloat(), 20f, paint)

            // 标题
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = config.textColor
                textSize = 56f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(config.title, width / 2f, 100f, titlePaint)

            // 副标题/描述
            if (config.description.isNotEmpty()) {
                val descPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.GRAY
                    textSize = 32f
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(
                    config.description.take(50) + if (config.description.length > 50) "..." else "",
                    width / 2f,
                    160f,
                    descPaint
                )
            }

            // 绘制二维码
            val qrLeft = (width - qrSize) / 2f
            val qrTop = headerHeight + padding / 2f
            
            // 二维码背景卡片
            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                setShadowLayer(10f, 0f, 4f, Color.parseColor("#20000000"))
            }
            canvas.drawRoundRect(
                qrLeft - 20,
                qrTop - 20,
                qrLeft + qrSize + 20,
                qrTop + qrSize + 20,
                24f,
                24f,
                cardPaint
            )

            // 绘制二维码
            val scaledQr = Bitmap.createScaledBitmap(qrCodeBitmap, qrSize, qrSize, true)
            canvas.drawBitmap(scaledQr, qrLeft, qrTop, null)

            // 底部信息
            val footerY = qrTop + qrSize + padding + 40
            
            // App 名称
            val appPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = config.textColor
                textSize = 36f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("QR Code Simple", width / 2f, footerY, appPaint)

            // 提示文字
            val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GRAY
                textSize = 28f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("扫码查看内容", width / 2f, footerY + 50, hintPaint)

            // 保存到缓存
            val cacheDir = File(context.cacheDir, "share_images").apply { mkdirs() }
            val file = File(cacheDir, "share_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 生成纯二维码分享图（无边框）
     */
    suspend fun generatePlainQrImage(
        context: Context,
        qrCodeBitmap: Bitmap,
        padding: Int = 40
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val size = qrCodeBitmap.width + padding * 2
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(qrCodeBitmap, padding.toFloat(), padding.toFloat(), null)

            val cacheDir = File(context.cacheDir, "share_images").apply { mkdirs() }
            val file = File(cacheDir, "qr_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getDefaultTitle(type: HistoryType): String {
        return when (type) {
            HistoryType.QR_CODE -> "二维码"
            HistoryType.BARCODE -> "条码"
            HistoryType.DATA_MATRIX -> "Data Matrix"
            HistoryType.AZTEC -> "Aztec Code"
            HistoryType.PDF417 -> "PDF417"
            HistoryType.TEXT -> "文本分享"
        }
    }

    private fun getDefaultDescription(content: String, type: HistoryType): String {
        return when {
            content.startsWith("WIFI:") -> "扫描连接 WiFi 网络"
            content.startsWith("http://") || content.startsWith("https://") -> content.take(30)
            content.startsWith("BEGIN:VCARD") -> "扫描添加联系人"
            content.startsWith("mailto:") -> "扫描发送邮件"
            else -> "扫描查看详情"
        }
    }
}
