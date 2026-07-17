package com.xenoamess.qrcodesimple

import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * AnimationUtils 测试：验证动画实际应用到视图并设置正确的可见性与参数。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class AnimationUtilsTest {

    private fun newView(): View = View(ApplicationProvider.getApplicationContext())

    @Test
    fun `fadeIn applies alpha animation and makes view visible`() {
        val view = newView().apply { visibility = View.GONE }
        AnimationUtils.fadeIn(view, 123)

        assertEquals(View.VISIBLE, view.visibility)
        val anim = view.animation
        assertNotNull(anim)
        assertTrue(anim is AlphaAnimation)
        assertEquals(123L, anim.duration)
    }

    @Test
    fun `fadeOut applies animation and hides view`() {
        val view = newView()
        AnimationUtils.fadeOut(view)
        assertEquals(View.GONE, view.visibility)
        assertTrue(view.animation is AlphaAnimation)
    }

    @Test
    fun `scaleIn applies scale animation and makes view visible`() {
        val view = newView().apply { visibility = View.INVISIBLE }
        AnimationUtils.scaleIn(view)

        assertEquals(View.VISIBLE, view.visibility)
        assertTrue(view.animation is ScaleAnimation)
    }

    @Test
    fun `slideUp and slideDown and pulse and bounce attach animations`() {
        val view = newView()
        AnimationUtils.slideUp(view)
        assertNotNull(view.animation)

        AnimationUtils.slideDown(view)
        assertNotNull(view.animation)

        AnimationUtils.pulse(view)
        assertNotNull(view.animation)

        AnimationUtils.bounce(view)
        assertNotNull(view.animation)
    }

    @Test
    fun `activity transition helpers do not crash`() {
        // overrideEnterAnimation/overrideExitAnimation 依赖 Activity 环境，
        // 这里仅验证 View 层 API 不抛异常（Activity 层由 UI 测试覆盖）
        val view = ImageView(ApplicationProvider.getApplicationContext())
        AnimationUtils.fadeIn(view)
        AnimationUtils.scaleIn(view)
    }
}
