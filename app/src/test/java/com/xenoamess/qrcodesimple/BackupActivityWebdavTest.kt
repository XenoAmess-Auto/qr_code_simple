package com.xenoamess.qrcodesimple

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.switchmaterial.SwitchMaterial
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/** BackupActivity 的 WebDAV 区域：开关持久化、上次同步文案、未配置拦截。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class BackupActivityWebdavTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<BackupActivity>? = null

    @Before
    fun setup() {
        WebDavSyncManager.clearConfig(context)
        WebDavSyncManager.setAutoUploadEnabled(context, false)
        scenario = ActivityScenario.launch(BackupActivity::class.java)
        idleMain()
    }

    @After
    fun tearDown() {
        scenario?.close()
        WebDavSyncManager.clearConfig(context)
    }

    private fun idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `auto upload switch persists`() {
        scenario?.onActivity { activity ->
            val toggle = activity.findViewById<SwitchMaterial>(R.id.switchWebdavAutoUpload)
            assertFalse(toggle.isChecked)
            toggle.performClick()
        }
        idleMain()
        assertTrue(WebDavSyncManager.isAutoUploadEnabled(context))
    }

    @Test
    fun `last sync shows never when never synced`() {
        scenario?.onActivity { activity ->
            val text = activity.findViewById<android.widget.TextView>(R.id.tvWebdavLastSync).text.toString()
            assertTrue(text.contains(context.getString(R.string.webdav_never_synced)))
        }
    }

    @Test
    fun `upload without config shows not-configured toast`() {
        scenario?.onActivity { activity ->
            activity.findViewById<android.widget.Button>(R.id.btnWebdavUpload).performClick()
        }
        idleMain()
        assertEquals(
            context.getString(R.string.webdav_not_configured),
            ShadowToast.getTextOfLatestToast()
        )
    }

    @Test
    fun `saved config prefill url and username fields`() {
        WebDavSyncManager.saveConfig(context, "https://dav.example.com/dav", "bob", "pw".toCharArray())
        scenario?.close()
        scenario = ActivityScenario.launch(BackupActivity::class.java)
        idleMain()
        scenario?.onActivity { activity ->
            val url = activity.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etWebdavUrl)
                .text?.toString()
            val username = activity.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etWebdavUsername)
                .text?.toString()
            assertEquals("https://dav.example.com/dav", url)
            assertEquals("bob", username)
        }
    }
}
