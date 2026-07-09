package com.xenoamess.qrcodesimple

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.action.ViewActions.swipeRight
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Extended GenerateFragment UI tests. These are heavier or stress-oriented and
 * are intended to run locally, not in CI.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class GenerateFragmentExtendedUiTest : BaseGenerateFragmentUiTest() {

    @Test
    fun dragGradientStopSliderDoesNotCrash() {
        typeText("https://example.com")
        clickGenerate()

        onView(withId(R.id.switchGradient)).perform(scrollTo(), click())
        onView(withId(R.id.btnAddGradientStop)).perform(clickWithoutVisibilityCheck())
        onView(withId(R.id.btnAddGradientStop)).perform(clickWithoutVisibilityCheck())

        onView(sliderAtRow(1)).perform(scrollTo(), swipeRight())
        onView(sliderAtRow(1)).perform(scrollTo(), swipeLeft())
        onView(sliderAtRow(1)).perform(scrollTo(), swipeRight())

        assertBitmapGenerated()
    }

    @Test
    fun allViewsSurviveClickLongClickAndDrag() {
        scenario.onFragment { fragment ->
            val root = fragment.requireView()
            val allViews = collectAllViews(root)
            for (view in allViews) {
                if (view.width <= 0 || view.height <= 0) continue
                tryClick(view)
                tryLongClick(view)
                tryDrag(view, -20f, 0f)
                tryDrag(view, 20f, 0f)
                tryDrag(view, 0f, -20f)
                tryDrag(view, 0f, 20f)
            }
        }
    }
}
