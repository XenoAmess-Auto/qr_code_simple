package com.xenoamess.qrcodesimple

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast

/**
 * 图片/视频 Uri 的扫描路由，供 [ScanImageFragment]（应用内选图）与
 * [ScanImageActivity]（系统分享进来的图片/视频）共用。
 */
object ScanImageProcessor {

    /** 根据 MIME 类型路由：视频进 [VideoScanActivity]，其余按图片识别。 */
    fun processMedia(context: Context, uri: Uri, mimeTypeHint: String? = null) {
        try {
            val mimeType = mimeTypeHint ?: context.contentResolver.getType(uri)
            if (mimeType?.startsWith("video/") == true) {
                val intent = Intent(context, VideoScanActivity::class.java).apply {
                    putExtra(VideoScanActivity.EXTRA_VIDEO_URI, uri.toString())
                }
                context.startActivity(intent)
            } else {
                processImage(context, uri)
            }
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.failed_to_save, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    fun processImage(context: Context, uri: Uri) {
        try {
            val bitmap = loadBitmapFromUri(context, uri)
            if (bitmap != null) {
                val intent = Intent(context, ResultActivity::class.java).apply {
                    putExtra(ResultActivity.EXTRA_BITMAP_URI, uri.toString())
                }
                context.startActivity(intent)
            } else {
                Toast.makeText(context, context.getString(R.string.failed_to_load_image), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.failed_to_save, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)

                val maxDimension = 2048
                val maxDim = maxOf(options.outWidth, options.outHeight)
                val sampleSize = if (maxDim > maxDimension) {
                    Integer.highestOneBit((maxDim / maxDimension).coerceAtLeast(1))
                } else {
                    1
                }

                context.contentResolver.openInputStream(uri)?.use { decodeStream ->
                    BitmapFactory.decodeStream(
                        decodeStream,
                        null,
                        BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    )
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
