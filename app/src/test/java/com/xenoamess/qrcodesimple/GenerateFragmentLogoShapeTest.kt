package com.xenoamess.qrcodesimple

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import android.view.View
import android.widget.Button
import androidx.fragment.app.testing.FragmentScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * 生成页 logo 形状选择 UI 测试：切换形状应用配置、半径滑杆仅圆角矩形可见。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class GenerateFragmentLogoShapeTest {

    private lateinit var scenario: FragmentScenario<GenerateFragment>

    @Before
    fun setup() {
        scenario = FragmentScenario.launchInContainer(GenerateFragment::class.java, themeResId = R.style.Theme_QRCodeSimple)
        idleMain()
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    private fun idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun injectLogoAndGenerate() {
        scenario.onFragment { fragment ->
            val logo = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.BLUE)
            }
            val logoField = GenerateFragment::class.java.getDeclaredField("logoBitmap")
            logoField.isAccessible = true
            logoField.set(fragment, logo)

            fragment.requireView().findViewById<TextInputEditText>(R.id.etContent).setText("logo-shape-ui-test")
            fragment.requireView().findViewById<Button>(R.id.btnGenerate).performClick()
        }
        idleMain()
        Thread.sleep(300)
        idleMain()
    }

    @Test
    fun `rounded shape shows radius slider and hides for other shapes`() {
        injectLogoAndGenerate()

        scenario.onFragment { fragment ->
            val toggle = fragment.requireView().findViewById<MaterialButtonToggleGroup>(R.id.toggleLogoShape)
            val radiusSection = fragment.requireView().findViewById<View>(R.id.logoCornerRadiusSection)

            // 默认方形：滑杆隐藏
            assertEquals(R.id.btnLogoShapeSquare, toggle.checkedButtonId)
            assertEquals(View.GONE, radiusSection.visibility)

            // 圆角矩形：滑杆显示
            toggle.check(R.id.btnLogoShapeRounded)
            assertEquals(View.VISIBLE, radiusSection.visibility)

            // 圆形：滑杆隐藏
            toggle.check(R.id.btnLogoShapeCircle)
            assertEquals(View.GONE, radiusSection.visibility)
        }
    }

    @Test
    fun `circle shape regenerates code with cropped logo`() {
        injectLogoAndGenerate()

        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<MaterialButtonToggleGroup>(R.id.toggleLogoShape)
                .check(R.id.btnLogoShapeCircle)
        }
        idleMain()
        Thread.sleep(300)
        idleMain()

        // 生成结果应非空（裁剪路径执行成功）
        scenario.onFragment { fragment ->
            val iv = fragment.requireView().findViewById<android.widget.ImageView>(R.id.ivQRCode)
            assertNotNull(iv.drawable)
        }
    }

    @Test
    fun `logo preview shows masked shape`() {
        injectLogoAndGenerate()

        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<MaterialButtonToggleGroup>(R.id.toggleLogoShape)
                .check(R.id.btnLogoShapeCircle)
        }
        idleMain()

        scenario.onFragment { fragment ->
            val preview = fragment.requireView().findViewById<android.widget.ImageView>(R.id.ivLogoPreview)
            assertEquals(View.VISIBLE, preview.visibility)
            assertNotNull(preview.drawable)
        }
    }
}
