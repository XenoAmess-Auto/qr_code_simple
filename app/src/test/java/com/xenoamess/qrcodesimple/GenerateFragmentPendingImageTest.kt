package com.xenoamess.qrcodesimple

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Looper
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.ComponentActivity
import androidx.fragment.app.testing.FragmentScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageContractOptions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class GenerateFragmentPendingImageTest {

    private lateinit var scenario: FragmentScenario<GenerateFragment>

    @Before
    fun setup() {
        scenario = FragmentScenario.launchInContainer(
            GenerateFragment::class.java,
            themeResId = R.style.Theme_QRCodeSimple
        )
        idleMain()
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun `foreground crop result is applied after recreation`() {
        assertCropResultAppliedAfterRecreation(
            pickerButtonId = R.id.btnPickForegroundImage,
            expectedVisibleId = R.id.btnRemoveForegroundImage,
            expectedGoneId = R.id.btnRemoveBackgroundImage
        )
    }

    @Test
    fun `background crop result is applied after recreation`() {
        assertCropResultAppliedAfterRecreation(
            pickerButtonId = R.id.btnPickBackgroundImage,
            expectedVisibleId = R.id.btnRemoveBackgroundImage,
            expectedGoneId = R.id.btnRemoveForegroundImage
        )
    }

    private fun assertCropResultAppliedAfterRecreation(
        pickerButtonId: Int,
        expectedVisibleId: Int,
        expectedGoneId: Int
    ) {
        var cropRequestCode = 0
        scenario.onFragment { fragment ->
            val viewModelField = GenerateFragment::class.java.getDeclaredField("generateViewModel").apply {
                isAccessible = true
            }
            val viewModel = viewModelField.get(fragment) as GenerateViewModel
            viewModel.beginImageCrop(
                if (pickerButtonId == R.id.btnPickForegroundImage) {
                    PendingImageType.FOREGROUND
                } else {
                    PendingImageType.BACKGROUND
                }
            )
            val launcherField = GenerateFragment::class.java.getDeclaredField("cropLauncher").apply {
                isAccessible = true
            }
            @Suppress("UNCHECKED_CAST")
            val launcher = launcherField.get(fragment) as ActivityResultLauncher<CropImageContractOptions>
            launcher.launch(
                fragment.createCropOptions(
                    Uri.parse("content://test/source"),
                    Uri.parse("content://test/destination")
                )
            )
            cropRequestCode = Shadows.shadowOf(fragment.requireActivity())
                .nextStartedActivityForResult.requestCode
        }

        val resultUri = createBitmapFile()
        scenario.recreate()
        scenario.onFragment { fragment ->
            val result = CropImage.ActivityResult(
                Uri.parse("content://test/source"),
                resultUri,
                null,
                floatArrayOf(0f, 0f, 2f, 0f, 2f, 2f, 0f, 2f),
                Rect(0, 0, 2, 2),
                0,
                Rect(0, 0, 2, 2),
                1
            )
            val data = Intent().putExtra(CropImage.CROP_IMAGE_EXTRA_RESULT, result)
            (fragment.requireActivity() as ComponentActivity).activityResultRegistry.dispatchResult(
                cropRequestCode,
                Activity.RESULT_OK,
                data
            )
        }

        repeat(50) {
            idleMain()
            var applied = false
            scenario.onFragment { fragment ->
                applied = fragment.requireView().findViewById<View>(expectedVisibleId).visibility == View.VISIBLE
            }
            if (applied) {
                scenario.onFragment { fragment ->
                    assertEquals(View.GONE, fragment.requireView().findViewById<View>(expectedGoneId).visibility)
                }
                return
            }
            Thread.sleep(20)
        }
        throw AssertionError("Cropped image was not applied to the expected target")
    }

    private fun createBitmapFile(): Uri {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "crop-result-${System.nanoTime()}.png")
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(IntArray(4) { Color.MAGENTA }, 0, 2, 0, 0, 2, 2)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return Uri.fromFile(file)
    }

    private fun idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
}
