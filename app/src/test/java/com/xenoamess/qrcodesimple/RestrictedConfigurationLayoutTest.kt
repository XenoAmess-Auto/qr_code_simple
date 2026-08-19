package com.xenoamess.qrcodesimple

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

class RestrictedLayoutHostActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_QRCodeSimple_ActionBar)
        super.onCreate(savedInstanceState)
    }
}

abstract class RestrictedConfigurationLayoutContract {

    private val activityControllers = mutableListOf<ActivityController<RestrictedLayoutHostActivity>>()

    private val context: Context
        get() = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_QRCodeSimple
        )

    private val configuration: Configuration
        get() = context.resources.configuration

    private val touchTarget: Int
        get() = context.resources.getDimensionPixelSize(R.dimen.touch_target_min)

    private fun inflate(layoutId: Int): View {
        val controller = Robolectric.buildActivity(RestrictedLayoutHostActivity::class.java).setup()
        activityControllers += controller
        val activity = controller.get()
        val inflater = LayoutInflater.from(context).cloneInContext(context)
        inflater.factory2 = object : LayoutInflater.Factory2 {
            override fun onCreateView(
                parent: View?,
                name: String,
                context: Context,
                attrs: AttributeSet
            ): View? = createView(name, context, attrs)

            override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? =
                createView(name, context, attrs)

            private fun createView(name: String, context: Context, attrs: AttributeSet): View? =
                if (name == "androidx.fragment.app.FragmentContainerView") {
                    // Standalone inflation has no FragmentManager; preserve the container's bounds contract.
                    FrameLayout(context, attrs)
                } else {
                    null
                }
        }
        val root = inflater.inflate(layoutId, null, false)
        activity.setContentView(root)

        val decor = activity.window.decorView as ViewGroup
        decor.measure(
            View.MeasureSpec.makeMeasureSpec(screenWidth(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(screenHeight(), View.MeasureSpec.EXACTLY)
        )
        decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)

        val actionBar = activity.findViewById<View>(androidx.appcompat.R.id.action_bar_container)
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val actionBarBounds = boundsIn(decor, actionBar)
        val contentBounds = boundsIn(decor, content)
        assertTrue("test host must expose a real ActionBar", actionBarBounds.height() > 0)
        assertEquals("content must start directly after the ActionBar", actionBarBounds.bottom, contentBounds.top)
        assertEquals(content.width, root.width)
        assertEquals(content.height, root.height)
        return root
    }

    private fun remeasure(view: View, height: Int = view.height) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    @After
    fun destroyActivities() {
        activityControllers.asReversed().forEach { it.pause().stop().destroy() }
        activityControllers.clear()
    }

    private fun screenWidth(): Int =
        (configuration.screenWidthDp * context.resources.displayMetrics.density).toInt()

    private fun screenHeight(): Int =
        (configuration.screenHeightDp * context.resources.displayMetrics.density).toInt()

    private fun boundsIn(root: ViewGroup, child: View): Rect =
        Rect(0, 0, child.width, child.height).also {
            root.offsetDescendantRectToMyCoords(child, it)
        }

    private fun assertTouchTargets(root: View, vararg ids: Int) {
        ids.forEach { id ->
            val target = root.findViewById<View>(id)
            assertTrue("${context.resources.getResourceEntryName(id)} width", target.width >= touchTarget)
            assertTrue("${context.resources.getResourceEntryName(id)} height", target.height >= touchTarget)
        }
    }

    private fun assertReachable(scrollView: NestedScrollView, targetId: Int) {
        val target = scrollView.findViewById<View>(targetId)
        val targetBounds = boundsIn(scrollView, target)
        val content = scrollView.getChildAt(0)
        assertTrue("target must start inside scroll content", targetBounds.top >= 0)
        assertTrue("target must be laid out inside scroll content", targetBounds.bottom <= content.height)
        assertTrue("target must fit in the scroll viewport", target.height <= scrollView.height)
        assertTrue(
            "${context.resources.getResourceEntryName(targetId)} must be visible or vertically scrollable",
            targetBounds.bottom <= scrollView.height || content.height > scrollView.height
        )
    }

    private fun assertHorizontallyReachable(scrollView: HorizontalScrollView, vararg targetIds: Int) {
        val content = scrollView.getChildAt(0) as ViewGroup
        val maxScroll = (content.width - scrollView.width).coerceAtLeast(0)
        targetIds.forEach { id ->
            val target = scrollView.findViewById<View>(id)
            val bounds = boundsIn(content, target)
            assertTrue("${context.resources.getResourceEntryName(id)} must start in action content", bounds.left >= 0)
            assertTrue("${context.resources.getResourceEntryName(id)} must end in action content", bounds.right <= content.width)
            val minimumScroll = (bounds.right - scrollView.width).coerceAtLeast(0)
            val maximumScroll = bounds.left.coerceAtMost(maxScroll)
            assertTrue(
                "${context.resources.getResourceEntryName(id)} must fit fully at a reachable scroll position",
                minimumScroll <= maximumScroll
            )
        }
    }

    private fun assertListAboveActions(root: ViewGroup, recyclerId: Int, actionsId: Int) {
        val recyclerBounds = boundsIn(root, root.findViewById(recyclerId))
        val actionBounds = boundsIn(root, root.findViewById(actionsId))
        assertFalse("RecyclerView and actions overlap", Rect.intersects(recyclerBounds, actionBounds))
    }

    private fun assertSelectionActionsReachable(root: ViewGroup) {
        val scrollView = root.findViewById<HorizontalScrollView?>(R.id.selectionActionsScrollView)
        if (scrollView != null) {
            assertTrue("landscape actions must overflow into a scroll viewport", scrollView.getChildAt(0).width > scrollView.width)
            assertHorizontallyReachable(
                scrollView,
                R.id.btnSelectAll,
                R.id.btnDeselectAll,
                R.id.btnCopySelected,
                R.id.btnShareSelected,
                R.id.btnDeleteSelected
            )
            return
        }

        val actionBounds = boundsIn(root, root.findViewById(R.id.layoutButtons))
        listOf(
            R.id.btnSelectAll,
            R.id.btnDeselectAll,
            R.id.btnCopySelected,
            R.id.btnShareSelected,
            R.id.btnDeleteSelected
        ).forEach { id ->
            val buttonBounds = boundsIn(root, root.findViewById(id))
            assertTrue("portrait action must remain inside its grid", actionBounds.contains(buttonBounds))
        }
    }

    private fun showResultsState(root: View) {
        root.findViewById<View>(R.id.progressBar).visibility = View.GONE
        root.findViewById<View>(R.id.tvNoResults).visibility = View.GONE
        root.findViewById<View>(R.id.recyclerView).visibility = View.VISIBLE
        root.findViewById<View>(R.id.layoutButtons).visibility = View.VISIBLE
    }

    @Test
    fun scrollBackedFormsKeepLastActionsReachable() {
        val batch = inflate(R.layout.activity_batch_generate) as NestedScrollView
        assertReachable(batch, R.id.btnGenerate)
        assertReachable(batch, R.id.btnClear)
        assertTouchTargets(
            batch,
            R.id.spinnerFormat,
            R.id.btnImportCsv,
            R.id.btnDownloadTemplate,
            R.id.btnBatchStyle,
            R.id.btnGenerate,
            R.id.btnClear
        )

        val image = inflate(R.layout.fragment_scan_image) as NestedScrollView
        assertReachable(image, R.id.btnFile)
        assertTouchTargets(image, R.id.btnGallery, R.id.btnCamera, R.id.btnFile)

        val dialog = inflate(R.layout.dialog_save_options) as NestedScrollView
        remeasure(dialog, touchTarget * 5)
        assertEquals(
            RadioGroup.VERTICAL,
            dialog.findViewById<RadioGroup>(R.id.rgSize).orientation
        )
        assertReachable(dialog, R.id.rbSize2048)
        assertTouchTargets(
            dialog,
            R.id.rbFormatPng,
            R.id.rbFormatJpeg,
            R.id.rbFormatWebp,
            R.id.rbFormatSvg,
            R.id.rbSize512,
            R.id.rbSize1024,
            R.id.rbSize2048
        )
    }

    @Test
    fun continuousScanKeepsARealResultViewport() {
        val root = inflate(R.layout.activity_continuous_scan) as LinearLayout

        val expectedOrientation = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            LinearLayout.HORIZONTAL
        } else {
            LinearLayout.VERTICAL
        }
        assertEquals(expectedOrientation, root.orientation)
        assertTrue(root.findViewById<RecyclerView>(R.id.recyclerView).height >= touchTarget)
        assertTouchTargets(root, R.id.btnSettings, R.id.btnClearAll, R.id.btnExport, R.id.btnSaveAll)

        val actions = root.findViewById<HorizontalScrollView>(R.id.continuousActionsScrollView)
        assertTrue("action content must fill or overflow its scroll viewport", actions.getChildAt(0).width >= actions.width)
        assertTrue("action viewport must preserve complete content width", actions.isFillViewport)
        assertHorizontallyReachable(actions, R.id.btnExport, R.id.btnSaveAll)
    }

    @Test
    fun imageResultListDoesNotOverlapScrollableActions() {
        val root = inflate(R.layout.activity_result) as LinearLayout
        showResultsState(root)
        root.findViewById<View>(R.id.ivProcessedImage).visibility = View.VISIBLE
        remeasure(root)

        val expectedOrientation = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            LinearLayout.HORIZONTAL
        } else {
            LinearLayout.VERTICAL
        }
        assertEquals(expectedOrientation, root.orientation)
        assertTrue(root.findViewById<RecyclerView>(R.id.recyclerView).height >= touchTarget)
        assertListAboveActions(root, R.id.recyclerView, R.id.layoutButtons)
        assertTouchTargets(
            root,
            R.id.btnSelectAll,
            R.id.btnDeselectAll,
            R.id.btnCopySelected,
            R.id.btnShareSelected,
            R.id.btnDeleteSelected
        )
        assertSelectionActionsReachable(root)
    }

    @Test
    fun videoResultListDoesNotOverlapScrollableActions() {
        val root = inflate(R.layout.activity_video_scan) as LinearLayout
        showResultsState(root)
        root.findViewById<View>(R.id.tvStatus).visibility = View.VISIBLE
        root.findViewById<View>(R.id.btnStopScan).visibility = View.VISIBLE
        remeasure(root)

        val expectedOrientation = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            LinearLayout.HORIZONTAL
        } else {
            LinearLayout.VERTICAL
        }
        assertEquals(expectedOrientation, root.orientation)
        assertTrue(root.findViewById<RecyclerView>(R.id.recyclerView).height >= touchTarget)
        assertListAboveActions(root, R.id.recyclerView, R.id.layoutButtons)
        assertTouchTargets(
            root,
            R.id.btnStopScan,
            R.id.btnSelectAll,
            R.id.btnDeselectAll,
            R.id.btnCopySelected,
            R.id.btnShareSelected,
            R.id.btnDeleteSelected
        )
        assertSelectionActionsReachable(root)
    }

    @Test
    fun tabletImageChooserKeepsItsFixedWidthAndReachableActions() {
        if (configuration.smallestScreenWidthDp < 600) return

        val root = inflate(R.layout.fragment_scan_image) as NestedScrollView
        val outer = root.getChildAt(0) as ViewGroup
        val fixedWidthContent = outer.getChildAt(0)
        val expectedWidth = (480 * context.resources.displayMetrics.density).toInt()
        val bounds = boundsIn(root, fixedWidthContent)

        assertEquals(expectedWidth, fixedWidthContent.width)
        assertEquals(root.width - bounds.right, bounds.left)
        assertReachable(root, R.id.btnGallery)
        assertReachable(root, R.id.btnCamera)
        assertReachable(root, R.id.btnFile)
        assertTouchTargets(root, R.id.btnGallery, R.id.btnCamera, R.id.btnFile)
    }
}

@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [28],
    application = QRCodeApp::class,
    qualifiers = "de-rDE-w320dp-h568dp-port-mdpi",
    fontScale = 2.0f
)
class GermanNarrowPortraitLayoutTest : RestrictedConfigurationLayoutContract()

@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [28],
    application = QRCodeApp::class,
    qualifiers = "ru-rRU-w568dp-h320dp-land-mdpi",
    fontScale = 2.0f
)
class RussianLandscapeLayoutTest : RestrictedConfigurationLayoutContract()

@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [28],
    application = QRCodeApp::class,
    qualifiers = "sw600dp-w600dp-h960dp-port-mdpi"
)
class TabletFixedWidthLayoutTest : RestrictedConfigurationLayoutContract()
