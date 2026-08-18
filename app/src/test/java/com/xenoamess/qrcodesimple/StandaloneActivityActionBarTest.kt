package com.xenoamess.qrcodesimple

import android.content.Intent
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class StandaloneActivityActionBarTest {

    private fun assertActionBarAndMenu(
        scenario: ActivityScenario<out AppCompatActivity>,
        menuItemIds: IntArray
    ) {
        scenario.onActivity { activity ->
            val actionBar = activity.supportActionBar
            assertNotNull("Standalone activities must expose an action bar", actionBar)
            assertTrue(
                "The action bar must provide an Up navigation entry",
                actionBar!!.displayOptions and ActionBar.DISPLAY_HOME_AS_UP != 0
            )

            val menu = MenuBuilder(activity)
            assertTrue(activity.onCreateOptionsMenu(menu))
            menuItemIds.forEach { assertNotNull(menu.findItem(it)) }

            assertTrue(activity.onSupportNavigateUp())
            assertTrue(activity.isFinishing)
        }
        scenario.close()
    }

    @Test
    fun resultActivityExposesResultMenuAndUpNavigation() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), ResultActivity::class.java)
            .putExtra(ResultActivity.EXTRA_BITMAP_URI, "content://test/missing-image")

        assertActionBarAndMenu(
            ActivityScenario.launch(intent),
            intArrayOf(R.id.action_copy_all, R.id.action_share_all)
        )
    }

    @Test
    fun videoScanActivityExposesResultMenuAndUpNavigation() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), VideoScanActivity::class.java)
            .putExtra(VideoScanActivity.EXTRA_VIDEO_URI, "content://test/missing-video")

        assertActionBarAndMenu(
            ActivityScenario.launch(intent),
            intArrayOf(R.id.action_copy_all, R.id.action_share_all)
        )
    }

    @Test
    fun batchResultActivityExposesSaveMenuAndUpNavigation() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), BatchResultActivity::class.java).apply {
            putStringArrayListExtra(BatchGenerateActivity.EXTRA_CONTENTS, arrayListOf("test"))
            putExtra(BatchGenerateActivity.EXTRA_FORMAT, BarcodeFormat.QR_CODE.name)
        }

        assertActionBarAndMenu(ActivityScenario.launch(intent), intArrayOf(R.id.action_save_all))
    }
}
