package com.xenoamess.qrcodesimple

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
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
        val intent = BatchResultTransfer.createIntent(
            ApplicationProvider.getApplicationContext(),
            listOf(BatchGenerator.BatchItem("test"))
        )

        assertActionBarAndMenu(ActivityScenario.launch(intent), intArrayOf(R.id.action_save_all))
    }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [28], application = QRCodeApp::class)
class StandaloneActivityManifestTest(
    private val activityClass: Class<out AppCompatActivity>,
    private val labelRes: Int
) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun standaloneActivities(): Collection<Array<Any>> = listOf(
            arrayOf(CameraScanActivity::class.java, R.string.camera_scan),
            arrayOf(GenerateActivity::class.java, R.string.generate_qr),
            arrayOf(BatchGenerateActivity::class.java, R.string.batch_generate),
            arrayOf(ContinuousScanActivity::class.java, R.string.continuous_scan),
            arrayOf(PrivacySettingsActivity::class.java, R.string.privacy_settings),
            arrayOf(DatabaseSecurityActivity::class.java, R.string.database_security),
            arrayOf(BackupActivity::class.java, R.string.backup_restore)
        )
    }

    @Test
    fun manifestUsesActionBarThemeAndLocalizedLabel() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activityInfo = context.packageManager.getActivityInfo(
            ComponentName(context, activityClass),
            0
        )

        assertEquals(R.style.Theme_QRCodeSimple_ActionBar, activityInfo.theme)
        assertEquals(context.getString(labelRes), activityInfo.loadLabel(context.packageManager).toString())
    }

    @Test
    fun activityShowsTitleAndUpNavigation() {
        val scenario = ActivityScenario.launch(activityClass)
        scenario.onActivity { activity ->
            val actionBar = activity.supportActionBar
            assertNotNull("${activityClass.simpleName} must expose an action bar", actionBar)
            assertEquals(activity.getString(labelRes), actionBar?.title?.toString())
            assertTrue(
                "${activityClass.simpleName} must expose Up navigation",
                actionBar!!.displayOptions and ActionBar.DISPLAY_HOME_AS_UP != 0
            )
        }
        scenario.close()
    }
}
