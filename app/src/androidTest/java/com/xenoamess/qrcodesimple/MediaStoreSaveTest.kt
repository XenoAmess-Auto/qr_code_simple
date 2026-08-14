package com.xenoamess.qrcodesimple

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真机 MediaStore（API 29+）保存路径测试：生成条码写入相册并可通过
 * MediaStore 查询。这条路径在 Robolectric 单测中无法覆盖（单测走 API 28 传统路径）。
 */
@RunWith(AndroidJUnit4::class)
class MediaStoreSaveTest {

    // API ≤28 写 MediaStore 需要 WRITE_EXTERNAL_STORAGE 运行时授权；33+ 由系统豁免
    @get:Rule
    val storagePermissionRule: androidx.test.rule.GrantPermissionRule =
        androidx.test.rule.GrantPermissionRule.grant(
            *buildList {
                // WRITE_EXTERNAL_STORAGE 仅 API ≤32 存在（manifest maxSdk 限定），33+ 无需授权
                if (android.os.Build.VERSION.SDK_INT <= 32) {
                    add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
        )


    @Test
    fun generatedBarcodePersistsViaMediaStore() {
        // 该测试走 Q+ MediaStore 路径（RELATIVE_PATH 自 API 29 引入）；
        // API 28 及以下生产代码走 legacy 外部存储直写，不在本测试覆盖范围
        org.junit.Assume.assumeTrue(
            "MediaStore RELATIVE_PATH path requires API 29+",
            android.os.Build.VERSION.SDK_INT >= 29
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // 生成真实条码位图
        val bitmap = BarcodeGenerator.generate(
            "mediastore-test-content",
            BarcodeGenerator.BarcodeConfig(format = BarcodeFormat.QR_CODE)
        )
        assertNotNull(bitmap)

        // 走与 GenerateFragment.saveBarcode 相同的 Q+ MediaStore 路径
        val fileName = "qrcode_test_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
        )
        assertNotNull(uri)
        context.contentResolver.openOutputStream(uri!!)?.use { out ->
            bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        // MediaStore 可查询到刚写入的文件
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
            arrayOf(fileName),
            null
        ).use { cursor ->
            assertNotNull(cursor)
            assertTrue(cursor!!.moveToFirst())
            assertEquals(fileName, cursor.getString(1))
        }

        // 清理
        context.contentResolver.delete(uri, null, null)
    }
}
