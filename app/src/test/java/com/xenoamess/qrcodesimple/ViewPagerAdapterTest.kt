package com.xenoamess.qrcodesimple

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class ViewPagerAdapterTest {

    @Test
    fun `adapter has five pages mapping to expected fragments`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val adapter = ViewPagerAdapter(activity)
                assertEquals(5, adapter.itemCount)
                assertTrue(adapter.createFragment(0) is CameraScanFragment)
                assertTrue(adapter.createFragment(1) is ScanImageFragment)
                assertTrue(adapter.createFragment(2) is GenerateFragment)
                assertTrue(adapter.createFragment(3) is HistoryFragment)
                assertTrue(adapter.createFragment(4) is AboutFragment)
            }
        }
    }

    @Test
    fun `invalid position throws`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val adapter = ViewPagerAdapter(activity)
                assertThrows(IllegalStateException::class.java) {
                    adapter.createFragment(5)
                }
            }
        }
    }
}
