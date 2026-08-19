package com.xenoamess.qrcodesimple

import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EdgeToEdgeInsetsDeviceTest {

    private val systemBarInsets = Insets.of(11, 29, 17, 43)
    private val displayCutoutInsets = Insets.of(23, 37, 31, 19)
    private val safeInsets = Insets.of(23, 37, 31, 43)

    @Test
    fun decorActionBarConsumesTopInsetWithoutAddingContentGap() {
        ActivityScenario.launch(GenerateActivity::class.java).use { scenario ->
            dispatchTestInsetsWhenSupported(scenario)

            scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                val contentChild = content.getChildAt(0)
                val actionBar = activity.findViewById<View>(androidx.appcompat.R.id.action_bar)
                val decor = activity.window.decorView
                val expectedInsets = expectedSafeInsets(decor)

                assertEquals(expectedInsets.left, content.paddingLeft)
                assertEquals(expectedInsets.right, content.paddingRight)
                assertEquals(expectedInsets.bottom, content.paddingBottom)

                val decorBounds = decor.screenBounds()
                val contentBounds = content.screenBounds()
                val childBounds = contentChild.screenBounds()
                val actionBarBounds = actionBar.screenBounds()
                assertTrue(actionBarBounds.width() > 0 && actionBarBounds.height() > 0)
                assertEquals(decorBounds.top + expectedInsets.top, actionBarBounds.top)
                assertEquals(actionBarBounds.bottom, childBounds.top)
                assertEquals(actionBarBounds.bottom - contentBounds.top, content.paddingTop)
                assertEquals(contentBounds.left + expectedInsets.left, childBounds.left)
                assertEquals(contentBounds.top + content.paddingTop, childBounds.top)
                assertEquals(contentBounds.right - expectedInsets.right, childBounds.right)
                assertEquals(contentBounds.bottom - expectedInsets.bottom, childBounds.bottom)
            }
        }
    }

    @Test
    fun noActionBarContentKeepsAllInsetsAndMatchingGeometry() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            dispatchTestInsetsWhenSupported(scenario)

            scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                val contentChild = content.getChildAt(0)
                val expectedInsets = expectedSafeInsets(activity.window.decorView)

                assertEquals(expectedInsets.left, content.paddingLeft)
                assertEquals(expectedInsets.top, content.paddingTop)
                assertEquals(expectedInsets.right, content.paddingRight)
                assertEquals(expectedInsets.bottom, content.paddingBottom)

                val contentBounds = content.screenBounds()
                val childBounds = contentChild.screenBounds()
                assertEquals(contentBounds.left + expectedInsets.left, childBounds.left)
                assertEquals(contentBounds.top + expectedInsets.top, childBounds.top)
                assertEquals(contentBounds.right - expectedInsets.right, childBounds.right)
                assertEquals(contentBounds.bottom - expectedInsets.bottom, childBounds.bottom)
            }
        }
    }

    private fun <T : androidx.appcompat.app.AppCompatActivity> dispatchTestInsetsWhenSupported(
        scenario: ActivityScenario<T>
    ) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            scenario.onActivity { activity ->
                val insets = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.systemBars(), systemBarInsets)
                    .setInsets(WindowInsetsCompat.Type.displayCutout(), displayCutoutInsets)
                    .build()
                ViewCompat.dispatchApplyWindowInsets(
                    activity.window.decorView,
                    insets
                )
                activity.window.decorView.requestLayout()
            }
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun expectedSafeInsets(decor: View): Insets {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) return safeInsets

        val rootInsets = requireNotNull(ViewCompat.getRootWindowInsets(decor))
        val systemBars = rootInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        val displayCutout = rootInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
        return Insets.of(
            maxOf(systemBars.left, displayCutout.left),
            maxOf(systemBars.top, displayCutout.top),
            maxOf(systemBars.right, displayCutout.right),
            maxOf(systemBars.bottom, displayCutout.bottom)
        )
    }

    private fun View.screenBounds(): Rect {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + width, location[1] + height)
    }
}
