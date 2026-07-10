package com.xenoamess.qrcodesimple

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.testing.FragmentScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class CameraScanFragmentTest {

    private lateinit var scenario: FragmentScenario<CameraScanFragment>

    @Before
    fun setup() {
        scenario = FragmentScenario.launchInContainer(CameraScanFragment::class.java, themeResId = R.style.Theme_QRCodeSimple)
        idleMain()
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    private fun idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun getNextStartedActivity(): Intent? {
        var intent: Intent? = null
        scenario.onFragment { fragment ->
            intent = Shadows.shadowOf(fragment.requireActivity()).nextStartedActivity
        }
        return intent
    }

    @Test
    fun shareResultButtonStartsSendIntent() {
        getNextStartedActivity()

        scenario.onFragment { fragment ->
            val view = fragment.requireView()
            val tvResult = view.findViewById<TextView>(R.id.tvResult)
            tvResult.text = "https://example.com"
            view.findViewById<MaterialButton>(R.id.btnShareResult).performClick()
        }
        idleMain()

        val startedIntent = getNextStartedActivity()
        assertNotNull(startedIntent)
        assertEquals(Intent.ACTION_CHOOSER, startedIntent?.action)
        val sharedIntent = startedIntent?.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals("https://example.com", sharedIntent?.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun copyResultButtonCopiesTextToClipboard() {
        scenario.onFragment { fragment ->
            val view = fragment.requireView()
            val tvResult = view.findViewById<TextView>(R.id.tvResult)
            tvResult.text = "copy-me"
            view.findViewById<MaterialButton>(R.id.btnCopyResult).performClick()
        }
        idleMain()

        val clipboard = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        assertNotNull(clip)
        assertEquals("copy-me", clip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun closeResultButtonHidesResultCard() {
        scenario.onFragment { fragment ->
            val view = fragment.requireView()
            val card = view.findViewById<CardView>(R.id.resultCard)
            card.visibility = View.VISIBLE
            view.findViewById<ImageButton>(R.id.btnCloseResult).performClick()
        }
        idleMain()

        scenario.onFragment { fragment ->
            val card = fragment.requireView().findViewById<CardView>(R.id.resultCard)
            assertEquals(View.GONE, card.visibility)
        }
    }

    @Test
    fun smartActionButtonOpensUrlForWebContent() {
        getNextStartedActivity()

        scenario.onFragment { fragment ->
            val method = CameraScanFragment::class.java.getDeclaredMethod("updateSmartActionButton", String::class.java)
            method.isAccessible = true
            method.invoke(fragment, "https://example.com")

            val view = fragment.requireView()
            view.findViewById<MaterialButton>(R.id.btnSmartAction).performClick()
        }
        idleMain()

        val startedIntent = getNextStartedActivity()
        assertNotNull(startedIntent)
        assertEquals(Intent.ACTION_VIEW, startedIntent?.action)
        assertEquals("https://example.com", startedIntent?.data.toString())
    }

    @Test
    fun flashAndSwitchCameraButtonsDoNotCrash() {
        scenario.onFragment { fragment ->
            val view = fragment.requireView()
            view.findViewById<ImageButton>(R.id.btnFlash).performClick()
            view.findViewById<ImageButton>(R.id.btnSwitchCamera).performClick()
        }
        idleMain()
    }
}
