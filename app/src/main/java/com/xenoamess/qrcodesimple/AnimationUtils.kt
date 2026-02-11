package com.xenoamess.qrcodesimple

import android.app.Activity
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.view.animation.TranslateAnimation

/**
 * 动画工具类
 */
object AnimationUtils {

    /**
     * 淡入动画
     */
    fun fadeIn(view: View, duration: Long = 300) {
        val animation = AlphaAnimation(0f, 1f).apply {
            this.duration = duration
            fillAfter = true
        }
        view.startAnimation(animation)
        view.visibility = View.VISIBLE
    }

    /**
     * 淡出动画
     */
    fun fadeOut(view: View, duration: Long = 300) {
        val animation = AlphaAnimation(1f, 0f).apply {
            this.duration = duration
            fillAfter = true
        }
        view.startAnimation(animation)
        view.visibility = View.GONE
    }

    /**
     * 缩放进入动画
     */
    fun scaleIn(view: View, duration: Long = 300) {
        val animation = ScaleAnimation(
            0.8f, 1f,
            0.8f, 1f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            this.duration = duration
            fillAfter = true
        }
        view.startAnimation(animation)
        view.visibility = View.VISIBLE
    }

    /**
     * 从底部滑入
     */
    fun slideUp(view: View, duration: Long = 300) {
        val animation = TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 1f,
            Animation.RELATIVE_TO_SELF, 0f
        ).apply {
            this.duration = duration
            fillAfter = true
        }
        view.startAnimation(animation)
        view.visibility = View.VISIBLE
    }

    /**
     * 从顶部滑入
     */
    fun slideDown(view: View, duration: Long = 300) {
        val animation = TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, -1f,
            Animation.RELATIVE_TO_SELF, 0f
        ).apply {
            this.duration = duration
            fillAfter = true
        }
        view.startAnimation(animation)
        view.visibility = View.VISIBLE
    }

    /**
     * 脉冲动画（用于按钮点击反馈）
     */
    fun pulse(view: View, duration: Long = 200) {
        val scaleDown = ScaleAnimation(
            1f, 0.95f,
            1f, 0.95f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            this.duration = duration / 2
            fillAfter = true
        }

        val scaleUp = ScaleAnimation(
            0.95f, 1f,
            0.95f, 1f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            this.duration = duration / 2
            fillAfter = true
            startOffset = duration / 2
        }

        val animationSet = AnimationSet(true).apply {
            addAnimation(scaleDown)
            addAnimation(scaleUp)
        }

        view.startAnimation(animationSet)
    }

    /**
     * 弹跳动画
     */
    fun bounce(view: View, duration: Long = 600) {
        val animation = ScaleAnimation(
            0.3f, 1f,
            0.3f, 1f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            this.duration = duration
            interpolator = android.view.animation.BounceInterpolator()
            fillAfter = true
        }
        view.startAnimation(animation)
        view.visibility = View.VISIBLE
    }

    /**
     * Activity 进入动画
     */
    fun Activity.overrideEnterAnimation() {
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    /**
     * Activity 退出动画
     */
    fun Activity.overrideExitAnimation() {
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
