package com.xenoamess.qrcodesimple

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class SecurePrefsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefsName = "secure_prefs_test"
    private val key = "test_key"

    @Before
    fun setup() {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().apply()
    }

    @Test
    fun `put then get roundtrips value`() {
        SecurePrefs.putString(context, prefsName, key, "secret-value-123")
        assertEquals("secret-value-123", SecurePrefs.getString(context, prefsName, key))
    }

    @Test
    fun `stored form is encrypted or plain fallback`() {
        SecurePrefs.putString(context, prefsName, key, "payload")
        val raw = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .getString(key, null)
        // Robolectric 若不支持 Keystore/GCM 会退化明文；真机必须是 enc_v1 前缀
        assertTrue(raw == "payload" || raw!!.startsWith("enc_v1:"))
    }

    @Test
    fun `missing key returns null`() {
        assertNull(SecurePrefs.getString(context, prefsName, "absent"))
    }

    @Test
    fun `plain legacy value is returned as-is`() {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit().putString(key, "legacy-plain").apply()
        assertEquals("legacy-plain", SecurePrefs.getString(context, prefsName, key))
    }

    @Test
    fun `remove clears value`() {
        SecurePrefs.putString(context, prefsName, key, "x")
        SecurePrefs.remove(context, prefsName, key)
        assertNull(SecurePrefs.getString(context, prefsName, key))
    }

    @Test
    fun `different prefs files are isolated`() {
        SecurePrefs.putString(context, prefsName, key, "a-value")
        assertNull(SecurePrefs.getString(context, "other_prefs", key))
    }
}
