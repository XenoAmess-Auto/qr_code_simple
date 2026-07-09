package com.xenoamess.qrcodesimple

import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.slider.Slider
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.junit.Assert
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Base UI test class for GenerateFragment. Provides common helpers for
 * Robolectric + FragmentScenario based tests.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
abstract class BaseGenerateFragmentUiTest {

    protected lateinit var scenario: FragmentScenario<GenerateFragment>

    @Before
    fun setup() {
        scenario = launchFragmentInContainer<GenerateFragment>(themeResId = R.style.Theme_QRCodeSimple)
    }

    protected fun assertBitmapGenerated() {
        scenario.onFragment { fragment ->
            Assert.assertNotNull("Generated bitmap should not be null", fragment.currentBitmap)
        }
    }

    protected fun typeText(text: String) {
        onView(withId(R.id.etContent)).perform(scrollTo(), replaceText(text), closeSoftKeyboard())
    }

    protected fun clickGenerate() {
        onView(withId(R.id.btnGenerate)).perform(scrollTo(), clickWithoutVisibilityCheck())
    }

    protected fun clickWithoutVisibilityCheck(): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> = withEffectiveVisibility(
                androidx.test.espresso.matcher.ViewMatchers.Visibility.VISIBLE
            )
            override fun getDescription(): String = "Click without visibility check"

            override fun perform(uiController: androidx.test.espresso.UiController, view: View) {
                view.callOnClick()
            }
        }
    }

    protected fun setSliderValue(value: Float): ViewAction {
        return androidx.test.espresso.action.ViewActions.actionWithAssertions(
            object : androidx.test.espresso.ViewAction {
                override fun getConstraints(): Matcher<View> = isAssignableFrom(Slider::class.java)
                override fun getDescription(): String = "Set Slider value to $value"
                override fun perform(uiController: androidx.test.espresso.UiController, view: View) {
                    (view as Slider).value = value
                }
            }
        )
    }

    protected fun firstColorStopView(): Matcher<View> = colorStopViewAtRow(0)
    protected fun secondColorStopView(): Matcher<View> = colorStopViewAtRow(1)

    protected fun colorStopViewAtRow(rowIndex: Int): Matcher<View> {
        return object : BoundedMatcher<View, View>(View::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("color stop view at row $rowIndex in gradientStopsContainer")
            }

            override fun matchesSafely(item: View): Boolean {
                val row = item.parent as? ViewGroup ?: return false
                val container = row.parent as? ViewGroup ?: return false
                if (container.id != R.id.gradientStopsContainer) return false
                if (container.indexOfChild(row) != rowIndex) return false
                return row.indexOfChild(item) == 0
            }
        }
    }

    protected fun sliderAtRow(rowIndex: Int): Matcher<View> {
        return object : BoundedMatcher<View, View>(View::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("slider at row $rowIndex in gradientStopsContainer")
            }

            override fun matchesSafely(item: View): Boolean {
                val row = item.parent as? ViewGroup ?: return false
                val container = row.parent as? ViewGroup ?: return false
                if (container.id != R.id.gradientStopsContainer) return false
                if (container.indexOfChild(row) != rowIndex) return false
                return row.indexOfChild(item) == 1
            }
        }
    }

    protected fun deleteButtonAtRow(rowIndex: Int): Matcher<View> {
        return object : BoundedMatcher<View, View>(View::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("delete button at row $rowIndex in gradientStopsContainer")
            }

            override fun matchesSafely(item: View): Boolean {
                val row = item.parent as? ViewGroup ?: return false
                val container = row.parent as? ViewGroup ?: return false
                if (container.id != R.id.gradientStopsContainer) return false
                if (container.indexOfChild(row) != rowIndex) return false
                return row.indexOfChild(item) == 2
            }
        }
    }

    protected fun schemeButtonAt(index: Int): Matcher<View> {
        return object : BoundedMatcher<View, View>(View::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("scheme button wrapper at index $index")
            }

            override fun matchesSafely(item: View): Boolean {
                val container = item.parent as? ViewGroup ?: return false
                if (container.id != R.id.schemeContainer) return false
                return container.indexOfChild(item) == index
            }
        }
    }

    protected fun isNotDisplayed(): Matcher<View> {
        return androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility(
            androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE
        )
    }

    protected fun collectAllViews(root: View): List<View> {
        val list = mutableListOf<View>()
        val queue = java.util.ArrayDeque<View>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val view = queue.removeFirst()
            list.add(view)
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    queue.add(view.getChildAt(i))
                }
            }
        }
        return list
    }

    protected fun tryClick(view: View) {
        try {
            view.callOnClick()
        } catch (_: Exception) {
        }
    }

    protected fun tryLongClick(view: View) {
        try {
            view.performLongClick()
        } catch (_: Exception) {
        }
    }

    protected fun tryDrag(view: View, dx: Float, dy: Float) {
        try {
            val cx = view.width / 2f
            val cy = view.height / 2f
            val downTime = android.os.SystemClock.uptimeMillis()
            val down = android.view.MotionEvent.obtain(
                downTime, downTime, android.view.MotionEvent.ACTION_DOWN, cx, cy, 0
            )
            view.dispatchTouchEvent(down)
            val move = android.view.MotionEvent.obtain(
                downTime, downTime + 10, android.view.MotionEvent.ACTION_MOVE, cx + dx, cy + dy, 0
            )
            view.dispatchTouchEvent(move)
            val up = android.view.MotionEvent.obtain(
                downTime, downTime + 20, android.view.MotionEvent.ACTION_UP, cx + dx, cy + dy, 0
            )
            view.dispatchTouchEvent(up)
        } catch (_: Exception) {
        }
    }
}
