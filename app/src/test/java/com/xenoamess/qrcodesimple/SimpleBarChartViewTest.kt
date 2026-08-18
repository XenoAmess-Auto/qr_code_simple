package com.xenoamess.qrcodesimple

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class SimpleBarChartViewTest {

    private val context: Context by lazy {
        ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_QRCodeSimple
        )
    }

    @Test
    fun dataProvidesMeaningfulAccessibilityDescription() {
        val view = SimpleBarChartView(context)

        view.setData(intArrayOf(2, 0, 5))

        val description = view.contentDescription.toString()
        assertTrue(description.startsWith(context.getString(R.string.stats_title)))
        assertTrue(description.contains("1: 2"))
        assertTrue(description.contains("3: 5"))
    }

    @Test
    fun setDataCopiesInput() {
        val counts = intArrayOf(1, 2)
        val view = SimpleBarChartView(context)
        view.setData(counts)

        counts[0] = 99

        assertEquals(false, view.contentDescription.toString().contains("1: 99"))
    }
}
