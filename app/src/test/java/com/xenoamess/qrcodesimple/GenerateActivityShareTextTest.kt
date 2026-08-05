package com.xenoamess.qrcodesimple

import android.content.Intent
import android.os.Looper
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.textfield.TextInputEditText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * GenerateActivity 系统分享文本入口（ACTION_SEND text/plain）测试。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class GenerateActivityShareTextTest {

    private fun idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun launch(intent: Intent): GenerateActivity {
        val controller = Robolectric.buildActivity(GenerateActivity::class.java, intent)
            .create().start().resume()
        idleMain()
        return controller.get()
    }

    @Test
    fun `send plain text prefills content and generates`() {
        val sharedText = "https://example.com/shared-text"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, sharedText)
        }
        val activity = launch(intent)

        val fragment = activity.supportFragmentManager
            .findFragmentById(R.id.fragmentContainer) as GenerateFragment
        val editText = fragment.requireView().findViewById<TextInputEditText>(R.id.etContent)
        assertEquals(sharedText, editText.text?.toString())

        // 已自动生成：预览 ImageView 应被设置 drawable
        val preview = fragment.requireView().findViewById<ImageView>(R.id.ivQRCode)
        assertNotNull(preview.drawable)
    }

    @Test
    fun `send without extra text leaves input empty`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
        }
        val activity = launch(intent)

        val fragment = activity.supportFragmentManager
            .findFragmentById(R.id.fragmentContainer) as GenerateFragment
        val editText = fragment.requireView().findViewById<TextInputEditText>(R.id.etContent)
        assertEquals("", editText.text?.toString() ?: "")
    }

    @Test
    fun `process text prefill fills input from selection menu`() {
        val selectedText = "https://example.com/process-text"
        val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
            putExtra(Intent.EXTRA_PROCESS_TEXT, selectedText)
        }
        val activity = launch(intent)

        val fragment = activity.supportFragmentManager
            .findFragmentById(R.id.fragmentContainer) as GenerateFragment
        val editText = fragment.requireView().findViewById<TextInputEditText>(R.id.etContent)
        assertEquals(selectedText, editText.text?.toString())

        val preview = fragment.requireView().findViewById<ImageView>(R.id.ivQRCode)
        assertNotNull(preview.drawable)
    }

    @Test
    fun `deep link prefill fills input and format`() {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(
            "qr-code-simple://generate?text=https%3A%2F%2Fdeep.example.com&format=QR_CODE"
        ))
        val activity = launch(intent)

        val fragment = activity.supportFragmentManager
            .findFragmentById(R.id.fragmentContainer) as GenerateFragment
        val editText = fragment.requireView().findViewById<TextInputEditText>(R.id.etContent)
        assertEquals("https://deep.example.com", editText.text?.toString())

        val preview = fragment.requireView().findViewById<ImageView>(R.id.ivQRCode)
        assertNotNull(preview.drawable)
    }

    @Test
    fun `plain launch does not prefill`() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), GenerateActivity::class.java)
        val activity = launch(intent)

        val fragment = activity.supportFragmentManager
            .findFragmentById(R.id.fragmentContainer) as GenerateFragment
        val editText = fragment.requireView().findViewById<TextInputEditText>(R.id.etContent)
        assertEquals("", editText.text?.toString() ?: "")

        val preview = fragment.requireView().findViewById<ImageView>(R.id.ivQRCode)
        assertNull(preview.drawable)
    }
}
