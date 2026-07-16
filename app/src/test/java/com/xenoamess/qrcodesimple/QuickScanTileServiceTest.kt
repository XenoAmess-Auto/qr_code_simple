package com.xenoamess.qrcodesimple

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * QuickScanTileService 测试：点击 Tile 应启动 CameraScanActivity。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class QuickScanTileServiceTest {

    @Test
    fun `click starts CameraScanActivity`() {
        val controller = Robolectric.buildService(QuickScanTileService::class.java).create()
        val service = controller.get()

        service.onClick()

        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val next = Shadows.shadowOf(app).nextStartedActivity
        assertNotNull(next)
        assertEquals(CameraScanActivity::class.java.name, next!!.component?.className)
        // 不调用 controller.destroy()：Robolectric 的 ShadowTileService 在 onDestroy
        // 时存在已知的 ClassCastException 问题，与业务逻辑无关
    }
}
