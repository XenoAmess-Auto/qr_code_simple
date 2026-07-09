package com.xenoamess.qrcodesimple

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Adapter 测试基类，提供 Robolectric 上下文和主线程 Looper flush 工具。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = QRCodeApp::class)
abstract class BaseAdapterTest {

    protected lateinit var context: Context

    @Before
    open fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    protected fun flushMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
}
