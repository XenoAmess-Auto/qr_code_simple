package com.xenoamess.qrcodesimple

import android.content.Context
import android.os.Looper
import androidx.appcompat.app.AlertDialog
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
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** BackupActivity 的 WebDAV 区域：开关持久化、上次同步文案、未配置拦截。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class BackupActivityWebdavTest {

    private class FakeConnection(url: URL, private val code: Int) : HttpURLConnection(url) {
        private val output = ByteArrayOutputStream()

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getOutputStream() = output
        override fun getResponseCode(): Int = code
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<BackupActivity>? = null

    @Before
    fun setup() {
        WebDavSyncManager.clearConfig(context)
        WebDavSyncManager.setAutoUploadEnabled(context, false)
        WebDavClient.connectionFactoryForTesting = null
        scenario = ActivityScenario.launch(BackupActivity::class.java)
        idleMain()
    }

    @After
    fun tearDown() {
        scenario?.close()
        WebDavClient.connectionFactoryForTesting = null
        WebDavSyncManager.clearConfig(context)
    }

    private fun idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun waitUntil(timeoutMs: Long = 5000, predicate: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            idleMain()
            if (predicate()) return true
            Thread.sleep(25)
        }
        idleMain()
        return predicate()
    }

    private fun setForm(url: String, username: String, password: String) {
        scenario?.onActivity { activity ->
            activity.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etWebdavUrl)
                .setText(url)
            activity.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etWebdavUsername)
                .setText(username)
            activity.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etWebdavPassword)
                .setText(password)
        }
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

    @Test
    fun `webdav password disables view state and is not restored after recreation`() {
        scenario?.onActivity { activity ->
            val password = activity.findViewById<com.google.android.material.textfield.TextInputEditText>(
                R.id.etWebdavPassword
            )
            assertFalse(password.isSaveEnabled)
            password.setText("must-not-survive")
        }

        scenario?.recreate()
        idleMain()

        scenario?.onActivity { activity ->
            val password = activity.findViewById<com.google.android.material.textfield.TextInputEditText>(
                R.id.etWebdavPassword
            )
            assertFalse(password.isSaveEnabled)
            assertTrue(password.text.isNullOrEmpty())
        }
    }

    @Test
    fun `webdav operation clears password editable`() {
        setForm("", "user", "transient-password")

        scenario?.onActivity { activity ->
            activity.runWebdav(isUpload = true)
            assertTrue(
                activity.findViewById<com.google.android.material.textfield.TextInputEditText>(
                    R.id.etWebdavPassword
                ).text.isNullOrEmpty()
            )
        }
    }

    @Test
    fun `download requires confirmation before contacting server`() {
        WebDavSyncManager.saveConfig(context, "https://dav.example.com/dav", "bob", "pw".toCharArray())
        scenario?.close()
        scenario = ActivityScenario.launch(BackupActivity::class.java)
        var connectionCount = 0
        WebDavClient.connectionFactoryForTesting = { url ->
            connectionCount++
            FakeConnection(url, 404)
        }

        scenario?.onActivity { activity ->
            activity.findViewById<android.widget.Button>(R.id.btnWebdavDownload).performClick()
        }
        idleMain()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertTrue(dialog.isShowing)
        assertEquals(0, connectionCount)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(waitUntil { connectionCount == 1 })
    }

    @Test
    fun `failed upload keeps previously saved config`() {
        WebDavSyncManager.saveConfig(context, "https://old.example.com/dav", "old-user", "old-pass".toCharArray())
        setForm("https://new.example.com/dav", "new-user", "new-pass")
        WebDavClient.connectionFactoryForTesting = { url -> FakeConnection(url, 401) }

        scenario?.onActivity { it.runWebdav(isUpload = true) }
        assertTrue(waitUntil {
            ShadowToast.getTextOfLatestToast() == context.getString(R.string.webdav_auth_failed)
        })

        val config = WebDavSyncManager.loadConfig(context)!!
        assertEquals("https://old.example.com/dav", config.url)
        assertEquals("old-user", config.username)
        assertEquals("old-pass", config.password.concatToString())
    }

    @Test
    fun `successful upload saves candidate config`() {
        WebDavSyncManager.saveConfig(context, "https://old.example.com/dav", "old-user", "old-pass".toCharArray())
        setForm("https://new.example.com/dav", "new-user", "new-pass")
        WebDavClient.connectionFactoryForTesting = { url -> FakeConnection(url, 201) }

        scenario?.onActivity { it.runWebdav(isUpload = true) }
        assertTrue(waitUntil {
            ShadowToast.getTextOfLatestToast() == context.getString(R.string.webdav_upload_success)
        })

        val config = WebDavSyncManager.loadConfig(context)!!
        assertEquals("https://new.example.com/dav", config.url)
        assertEquals("new-user", config.username)
        assertEquals("new-pass", config.password.concatToString())
    }
}
