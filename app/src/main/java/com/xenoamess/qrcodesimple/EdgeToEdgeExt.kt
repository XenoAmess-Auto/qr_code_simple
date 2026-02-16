package com.xenoamess.qrcodesimple

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 为 Activity 设置 edge-to-edge 显示并处理安全区域（状态栏、导航栏、灵动岛等）
 * 在 setContentView() 之后调用
 */
fun AppCompatActivity.setupEdgeToEdge() {
    // 启用 edge-to-edge 显示
    WindowCompat.setDecorFitsSystemWindows(window, false)

    // 为根布局设置 WindowInsets 监听器来处理安全区域
    val rootView = window.decorView.findViewById<android.view.View>(android.R.id.content)
    ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())

        // 取 systemBars 和 displayCutout 的最大值作为内边距
        val topInset = maxOf(systemBars.top, displayCutout.top)
        val leftInset = maxOf(systemBars.left, displayCutout.left)
        val rightInset = maxOf(systemBars.right, displayCutout.right)
        val bottomInset = maxOf(systemBars.bottom, displayCutout.bottom)

        view.setPadding(leftInset, topInset, rightInset, bottomInset)
        insets
    }

    // 设置状态栏图标颜色根据主题自动调整
    val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
    windowInsetsController.isAppearanceLightStatusBars = !isDarkTheme()
}

private fun AppCompatActivity.isDarkTheme(): Boolean {
    return when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
        Configuration.UI_MODE_NIGHT_YES -> true
        else -> false
    }
}