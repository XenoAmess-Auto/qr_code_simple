package com.xenoamess.qrcodesimple

import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * GenerateFragment UI tests (run with Robolectric + FragmentScenario).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class GenerateFragmentUiTest {

    private lateinit var scenario: FragmentScenario<GenerateFragment>

    @Before
    fun setup() {
        scenario = launchFragmentInContainer<GenerateFragment>(themeResId = R.style.Theme_QRCodeSimple)
    }

    @Test
    fun generateQRCodeWithDefaultStyle() {
        typeText("https://example.com")
        clickGenerate()
        assertBitmapGenerated()
    }

    @Test
    fun changeModuleShapeDoesNotCrash() {
        typeText("https://example.com")
        clickGenerate()

        onView(withId(R.id.chipModuleCircle)).perform(scrollTo(), click())
        clickGenerate()
        assertBitmapGenerated()

        onView(withId(R.id.chipModuleRounded)).perform(scrollTo(), click())
        clickGenerate()
        assertBitmapGenerated()
    }

    @Test
    fun changePositionPatternShapeDoesNotCrash() {
        typeText("https://example.com")
        clickGenerate()

        onView(withId(R.id.chipPositionCircle)).perform(scrollTo(), click())
        clickGenerate()
        assertBitmapGenerated()

        onView(withId(R.id.chipPositionFollow)).perform(scrollTo(), click())
        clickGenerate()
        assertBitmapGenerated()
    }

    @Test
    fun changeModuleFillRatioDoesNotCrash() {
        typeText("https://example.com")
        clickGenerate()

        onView(withId(R.id.seekBarModuleFillRatio)).perform(scrollTo(), setSliderValue(40f))
        clickGenerate()
        assertBitmapGenerated()
    }

    @Test
    fun toggleGradientAndPickStopColorDoesNotCrash() {
        typeText("https://example.com")
        clickGenerate()

        // Enable gradient
        onView(withId(R.id.switchGradient)).perform(scrollTo(), click())
        onView(withId(R.id.gradientControlsContainer)).check(matches(withEffectiveVisibility(androidx.test.espresso.matcher.ViewMatchers.Visibility.VISIBLE)))

        // Add a few stops
        onView(withId(R.id.btnAddGradientStop)).perform(clickWithoutVisibilityCheck())
        onView(withId(R.id.btnAddGradientStop)).perform(clickWithoutVisibilityCheck())
        onView(withId(R.id.btnAddGradientStop)).perform(clickWithoutVisibilityCheck())

        // Click the first stop color to open the color picker dialog
        onView(firstColorStopView()).perform(scrollTo(), click())
        scenario.onFragment { fragment ->
            val dialogs = fragment.parentFragmentManager.fragments.filterIsInstance<ColorPickerDialog>()
            Assert.assertTrue("ColorPickerDialog should be shown", dialogs.isNotEmpty())
        }

        // Add another stop to stress the gradient preview update path
        onView(withId(R.id.btnAddGradientStop)).perform(clickWithoutVisibilityCheck())
        onView(secondColorStopView()).perform(scrollTo(), click())
        scenario.onFragment { fragment ->
            val dialogs = fragment.parentFragmentManager.fragments.filterIsInstance<ColorPickerDialog>()
            Assert.assertTrue("ColorPickerDialog should be shown for second stop", dialogs.isNotEmpty())
        }

        assertBitmapGenerated()
    }

    @Test
    fun repeatedlyAddAndRemoveGradientStopsDoesNotCrash() {
        typeText("https://example.com")
        clickGenerate()

        onView(withId(R.id.switchGradient)).perform(scrollTo(), click())

        // Add stops until the button is disabled (max 5)
        repeat(5) {
            onView(withId(R.id.btnAddGradientStop)).perform(clickWithoutVisibilityCheck())
        }

        // Remove all stops except the minimum 2
        repeat(3) {
            onView(deleteButtonAtRow(0)).perform(clickWithoutVisibilityCheck())
        }

        // Add again
        repeat(2) {
            onView(withId(R.id.btnAddGradientStop)).perform(clickWithoutVisibilityCheck())
        }

        // Remove from the end this time
        onView(deleteButtonAtRow(3)).perform(clickWithoutVisibilityCheck())
        onView(deleteButtonAtRow(2)).perform(clickWithoutVisibilityCheck())

        assertBitmapGenerated()
    }

    @Test
    fun disableGradientHidesGradientControls() {
        typeText("https://example.com")

        onView(withId(R.id.switchGradient)).perform(scrollTo(), click())
        onView(withId(R.id.gradientControlsContainer)).check(matches(withEffectiveVisibility(androidx.test.espresso.matcher.ViewMatchers.Visibility.VISIBLE)))

        onView(withId(R.id.switchGradient)).perform(click())
        onView(withId(R.id.gradientControlsContainer)).check(matches(isNotDisplayed()))
    }

    private fun assertBitmapGenerated() {
        scenario.onFragment { fragment ->
            Assert.assertNotNull("Generated bitmap should not be null", fragment.currentBitmap)
        }
    }

    private fun typeText(text: String) {
        onView(withId(R.id.etContent)).perform(scrollTo(), replaceText(text), closeSoftKeyboard())
    }

    private fun clickGenerate() {
        onView(withId(R.id.btnGenerate)).perform(scrollTo(), clickWithoutVisibilityCheck())
    }

    private fun clickWithoutVisibilityCheck(): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> = withEffectiveVisibility(androidx.test.espresso.matcher.ViewMatchers.Visibility.VISIBLE)
            override fun getDescription(): String = "Click without visibility check"

            override fun perform(uiController: androidx.test.espresso.UiController, view: View) {
                view.callOnClick()
            }
        }
    }

    private fun setSliderValue(value: Float): ViewAction {
        return androidx.test.espresso.action.ViewActions.actionWithAssertions(
            object : androidx.test.espresso.ViewAction {
                override fun getConstraints(): Matcher<View> {
                    return isAssignableFrom(Slider::class.java)
                }

                override fun getDescription(): String = "Set Slider value to $value"

                override fun perform(uiController: androidx.test.espresso.UiController, view: View) {
                    (view as Slider).value = value
                }
            }
        )
    }

    private fun firstColorStopView(): Matcher<View> {
        return colorStopViewAtRow(0)
    }

    private fun secondColorStopView(): Matcher<View> {
        return colorStopViewAtRow(1)
    }

    private fun colorStopViewAtRow(rowIndex: Int): Matcher<View> {
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

    private fun deleteButtonAtRow(rowIndex: Int): Matcher<View> {
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

    private fun isNotDisplayed(): Matcher<View> {
        return androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility(
            androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE
        )
    }
}
