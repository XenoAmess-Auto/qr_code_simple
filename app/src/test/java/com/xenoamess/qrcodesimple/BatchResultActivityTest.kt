package com.xenoamess.qrcodesimple

import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class BatchResultActivityTest {

    private fun idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun waitFor(maxMs: Long = 5000, condition: () -> Boolean): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < maxMs) {
            if (condition()) return true
            idleMain()
            Thread.sleep(50)
        }
        return false
    }

    private fun launchWith(contents: List<String>, format: BarcodeFormat = BarcodeFormat.QR_CODE): ActivityScenario<BatchResultActivity> {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            BatchResultActivity::class.java
        ).apply {
            putStringArrayListExtra(BatchGenerateActivity.EXTRA_CONTENTS, ArrayList(contents))
            putExtra(BatchGenerateActivity.EXTRA_FORMAT, format.name)
        }
        return ActivityScenario.launch<BatchResultActivity>(intent)
    }

    @Test
    fun emptyContentsFinishesActivity() {
        val scenario = launchWith(emptyList())
        idleMain()
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        scenario.close()
    }

    @Test
    fun batchGenerationRendersResults() {
        val scenario = launchWith(listOf("https://a.com", "https://b.com"))
        idleMain()

        assertTrue(
            "Generation should complete and render results",
            waitFor {
                var progressGone = false
                var itemCount = 0
                scenario.onActivity { activity ->
                    progressGone = activity.findViewById<ProgressBar>(R.id.progressBar).visibility == View.GONE
                    itemCount = activity.findViewById<RecyclerView>(R.id.recyclerView).adapter?.itemCount ?: 0
                }
                progressGone && itemCount == 2
            }
        )

        scenario.onActivity { activity ->
            val text = activity.findViewById<TextView>(R.id.tvProgress).text.toString()
            assertTrue(text.contains("Generated: 2/2"))
        }
        scenario.close()
    }

    @Test
    fun saveAllMenuItemDoesNotCrash() {
        val scenario = launchWith(listOf("https://example.com"))
        idleMain()

        assertTrue(
            "Generation should complete",
            waitFor {
                var gone = false
                scenario.onActivity { activity ->
                    gone = activity.findViewById<ProgressBar>(R.id.progressBar).visibility == View.GONE
                }
                gone
            }
        )

        scenario.onActivity { activity ->
            Shadows.shadowOf(activity).clickMenuItem(R.id.action_save_all)
        }
        idleMain()

        scenario.close()
    }
}
