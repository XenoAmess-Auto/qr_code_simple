package com.xenoamess.qrcodesimple

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class CropImageConfigurationTest {

    @Test
    fun cropActivityUsesActionBarTheme() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activityInfo = context.packageManager.getActivityInfo(
            ComponentName(context.packageName, "com.canhub.cropper.CropImageActivity"),
            0
        )

        assertEquals(R.style.Theme_QRCodeSimple_ActionBar, activityInfo.theme)
    }

    @Test
    fun cropOptionsExposeLocalizedConfirmationAction() {
        val scenario = launchFragmentInContainer<GenerateFragment>(
            themeResId = R.style.Theme_QRCodeSimple
        )
        scenario.onFragment { fragment ->
            val context = fragment.requireContext()
            val options = fragment.createCropOptions(
                Uri.parse("content://test/source"),
                Uri.parse("content://test/destination")
            ).cropImageOptions

            assertEquals(fragment.getString(R.string.confirm), options.cropMenuCropButtonTitle)
            assertEquals(context.getColor(R.color.app_primary), options.activityMenuTextColor)
            assertEquals(context.getColor(R.color.app_primary), options.activityMenuIconColor)
            assertEquals(context.getColor(R.color.app_surface), options.toolbarColor)
            assertEquals(context.getColor(R.color.app_text_primary), options.toolbarTitleColor)
            assertEquals(context.getColor(R.color.app_text_secondary), options.toolbarBackButtonColor)
        }
        scenario.close()
    }
}
