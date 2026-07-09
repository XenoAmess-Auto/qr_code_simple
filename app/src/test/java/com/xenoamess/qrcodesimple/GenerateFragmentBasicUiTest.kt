package com.xenoamess.qrcodesimple

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Basic GenerateFragment UI tests. These run both locally and in CI.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class GenerateFragmentBasicUiTest : BaseGenerateFragmentUiTest() {

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

        onView(withId(R.id.switchGradient)).perform(scrollTo(), click())
        onView(withId(R.id.gradientControlsContainer)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))

        repeat(3) {
            onView(withId(R.id.btnAddGradientStop)).perform(clickWithoutVisibilityCheck())
        }

        onView(firstColorStopView()).perform(scrollTo(), click())
        scenario.onFragment { fragment ->
            val dialogs = fragment.parentFragmentManager.fragments.filterIsInstance<ColorPickerDialog>()
            Assert.assertTrue("ColorPickerDialog should be shown", dialogs.isNotEmpty())
        }

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

        repeat(5) {
            onView(withId(R.id.btnAddGradientStop)).perform(clickWithoutVisibilityCheck())
        }

        repeat(3) {
            onView(deleteButtonAtRow(0)).perform(clickWithoutVisibilityCheck())
        }

        repeat(2) {
            onView(withId(R.id.btnAddGradientStop)).perform(clickWithoutVisibilityCheck())
        }

        onView(deleteButtonAtRow(3)).perform(clickWithoutVisibilityCheck())
        onView(deleteButtonAtRow(2)).perform(clickWithoutVisibilityCheck())

        assertBitmapGenerated()
    }

    @Test
    fun changeGradientAngleViaTextInput() {
        typeText("https://example.com")
        clickGenerate()

        onView(withId(R.id.switchGradient)).perform(scrollTo(), click())
        onView(withId(R.id.etGradientAngle)).perform(scrollTo(), replaceText("45"), closeSoftKeyboard())

        scenario.onFragment { fragment ->
            Assert.assertEquals("Gradient angle should be 45", 45f, fragment.gradientAngle, 0.1f)
        }
        assertBitmapGenerated()
    }

    @Test
    fun selectColorSchemeAndModifyConfigClearsSelection() {
        typeText("https://example.com")

        onView(schemeButtonAt(0)).perform(scrollTo(), click())
        scenario.onFragment { fragment ->
            Assert.assertNotNull("A scheme should be selected", fragment.selectedScheme)
        }

        onView(withId(R.id.chipModuleCircle)).perform(scrollTo(), click())
        scenario.onFragment { fragment ->
            Assert.assertNull("Scheme selection should be cleared after config divergence", fragment.selectedScheme)
        }
    }

    @Test
    fun disableGradientHidesGradientControls() {
        typeText("https://example.com")

        onView(withId(R.id.switchGradient)).perform(scrollTo(), click())
        onView(withId(R.id.gradientControlsContainer)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))

        onView(withId(R.id.switchGradient)).perform(click())
        onView(withId(R.id.gradientControlsContainer)).check(matches(isNotDisplayed()))
    }
}
