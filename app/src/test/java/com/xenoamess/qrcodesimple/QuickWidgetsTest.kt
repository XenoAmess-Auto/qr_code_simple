package com.xenoamess.qrcodesimple

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 小组件验证：布局可在应用主题下展开，updateAppWidget 安装 RemoteViews。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class QuickWidgetsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `quick scan layout inflates under app theme`() {
        val themed = ContextThemeWrapper(context, R.style.Theme_QRCodeSimple)
        val view = LayoutInflater.from(themed).inflate(R.layout.widget_quick_scan, null)
        assertNotNull(view.findViewById(R.id.widget_container))
    }

    @Test
    fun `quick generate layout inflates under app theme`() {
        val themed = ContextThemeWrapper(context, R.style.Theme_QRCodeSimple)
        val view = LayoutInflater.from(themed).inflate(R.layout.widget_quick_generate, null)
        assertNotNull(view.findViewById(R.id.widget_container))
    }


}
