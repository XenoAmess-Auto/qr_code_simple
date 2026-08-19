package com.xenoamess.qrcodesimple

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import org.json.JSONObject
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class BatchGenerateActivityTest {

    private lateinit var scenario: ActivityScenario<BatchGenerateActivity>

    @Before
    fun setup() {
        scenario = ActivityScenario.launch(BatchGenerateActivity::class.java)
        scenario.moveToState(Lifecycle.State.RESUMED)
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) {
            scenario.close()
        }
    }

    private fun findSpinner(): AutoCompleteTextView {
        var spinner: AutoCompleteTextView? = null
        scenario.onActivity { activity ->
            spinner = activity.findViewById(R.id.spinnerFormat)
        }
        return spinner!!
    }

    private fun flushFilter() {
        Thread.sleep(300)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private fun idleMain() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private fun setField(activity: BatchGenerateActivity, name: String, value: Any?) {
        BatchGenerateActivity::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(activity, value)
        }
    }

    private fun getField(activity: BatchGenerateActivity, name: String): Any? =
        BatchGenerateActivity::class.java.getDeclaredField(name).run {
            isAccessible = true
            get(activity)
        }

    private fun findTaggedView(root: View, tag: Any): View? {
        if (root.tag == tag) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findTaggedView(root.getChildAt(index), tag)?.let { return it }
            }
        }
        return null
    }

    private fun saveInstanceState(activity: BatchGenerateActivity, state: Bundle) {
        var type: Class<*>? = activity.javaClass
        while (type != null) {
            runCatching {
                type.getDeclaredMethod("onSaveInstanceState", Bundle::class.java).apply {
                    isAccessible = true
                    invoke(activity, state)
                }
            }.onSuccess { return }
            type = type.superclass
        }
        error("onSaveInstanceState not found")
    }

    private fun parcelSize(state: Bundle): Int = Parcel.obtain().let { parcel ->
        try {
            state.writeToParcel(parcel, 0)
            parcel.dataSize()
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun formatSelectorIsEditable() {
        val spinner = findSpinner()
        assertTrue("Format selector should be enabled", spinner.isEnabled)
        assertNotEquals(
            "Format selector should be editable (inputType != TYPE_NULL)",
            InputType.TYPE_NULL,
            spinner.inputType
        )
    }

    @Test
    fun formatDropdownOpensOnClick() {
        onView(withId(R.id.spinnerFormat)).perform(click())
        val spinner = findSpinner()
        val adapter = spinner.adapter as BarcodeFormatAdapter
        assertEquals(
            "Dropdown should show all formats after click",
            BarcodeFormat.entries.filter { it != BarcodeFormat.UNKNOWN }.size,
            adapter.count
        )
    }

    @Test
    fun formatDropdownFiltersByTyping() {
        onView(withId(R.id.spinnerFormat)).perform(click(), replaceText("EAN-13"))
        flushFilter()
        val spinner = findSpinner()
        val adapter = spinner.adapter as BarcodeFormatAdapter
        val results = (0 until adapter.count).map { adapter.getItem(it) }
        assertTrue("EAN-13 should be in filtered results", results.contains(BarcodeFormat.EAN_13))
        assertTrue("QR Code should not be in filtered results", !results.contains(BarcodeFormat.QR_CODE))
    }

    @Test
    fun formatDropdownSelectsFormat() {
        onView(withId(R.id.spinnerFormat)).perform(click())
        closeSoftKeyboard()
        scenario.onActivity { activity ->
            val spinner = activity.findViewById<AutoCompleteTextView>(R.id.spinnerFormat)
            val adapter = spinner.adapter as BarcodeFormatAdapter
            val formats = BarcodeFormat.entries.filter { it != BarcodeFormat.UNKNOWN }
            val position = formats.indexOf(BarcodeFormat.CODE_128)
            assertTrue("CODE_128 should be in full adapter", position >= 0)
            spinner.onItemClickListener?.onItemClick(null, spinner, position, adapter.getItemId(position))
            assertEquals(BarcodeFormat.CODE_128, activity.selectedFormat)
            assertEquals(BarcodeFormat.CODE_128.localizedName(activity), spinner.text.toString())
        }
    }

    @Test
    fun formatDropdownRestoresInvalidInput() {
        var selectedBefore = BarcodeFormat.UNKNOWN
        scenario.onActivity { activity ->
            selectedBefore = activity.selectedFormat
        }

        onView(withId(R.id.spinnerFormat)).perform(click(), replaceText("not a format"))
        scenario.onActivity { activity ->
            val spinner = activity.findViewById<AutoCompleteTextView>(R.id.spinnerFormat)
            spinner.onFocusChangeListener?.onFocusChange(spinner, false)
            assertEquals(selectedBefore, activity.selectedFormat)
            assertEquals(selectedBefore.localizedName(activity), spinner.text.toString())
        }
    }

    @Test
    fun batchInputFormatStyleAndLogoSurviveRecreationWithoutBitmapInBundle() {
        val imported = listOf(
            BatchGenerator.BatchItem("first", BarcodeFormat.CODE_128, 0xff123456.toInt(), fileName = "one"),
            BatchGenerator.BatchItem("second", BarcodeFormat.EAN_13)
        )
        val scheme = AdvancedBarcodeGenerator.ColorSchemes.BLUE
        val logo = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xffabcdef.toInt())
        }
        scenario.onActivity { activity ->
            setField(activity, "importedItems", imported)
            activity.findViewById<android.widget.EditText>(R.id.etContent)
                .setText(imported.joinToString("\n") { it.content })
            activity.selectedFormat = BarcodeFormat.CODE_128
            activity.batchScheme = scheme
            activity.batchLogo = logo

            val state = Bundle()
            saveInstanceState(activity, state)
            assertNotNull(state.getString("batch_logo_token"))
            assertTrue("Saved state must not contain bitmap pixel data", parcelSize(state) < 100_000)
        }

        scenario.recreate()

        scenario.onActivity { activity ->
            assertEquals(imported, getField(activity, "importedItems"))
            assertEquals(BarcodeFormat.CODE_128, activity.selectedFormat)
            assertEquals(scheme, activity.batchScheme)
            assertNotNull(activity.batchLogo)
        }
    }

    @Test
    fun separateActivityInstancesUseIndependentLogoFilesAndDoNotDeleteEachOthersLogo() {
        val firstLogo = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xffff0000.toInt())
        }
        val secondLogo = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xff0000ff.toInt())
        }
        lateinit var firstToken: String
        lateinit var secondToken: String
        lateinit var firstFile: File
        lateinit var secondFile: File

        scenario.onActivity { activity ->
            activity.batchLogo = firstLogo
            val state = Bundle().also { saveInstanceState(activity, it) }
            firstToken = state.getString("batch_logo_token")!!
            firstFile = PrivateStateFileStore.file(activity, "batch-generate-state", firstToken, "png")
        }

        val secondController = Robolectric.buildActivity(BatchGenerateActivity::class.java)
            .create()
            .start()
            .resume()
            .visible()
        val second = secondController.get()
        second.batchLogo = secondLogo
        val secondState = Bundle().also { saveInstanceState(second, it) }
        secondToken = secondState.getString("batch_logo_token")!!
        secondFile = PrivateStateFileStore.file(second, "batch-generate-state", secondToken, "png")

        assertNotEquals(firstToken, secondToken)
        assertEquals(0xffff0000.toInt(), BitmapFactory.decodeFile(firstFile.absolutePath).getPixel(0, 0))
        assertEquals(0xff0000ff.toInt(), BitmapFactory.decodeFile(secondFile.absolutePath).getPixel(0, 0))

        scenario.close()
        idleMain()
        assertFalse(firstFile.exists())
        assertTrue(secondFile.isFile)

        val restoredController = Robolectric.buildActivity(BatchGenerateActivity::class.java)
            .create(Bundle(secondState))
            .start()
            .resume()
            .visible()
        assertEquals(0xff0000ff.toInt(), restoredController.get().batchLogo!!.getPixel(0, 0))
        restoredController.pause().stop().destroy()
        secondController.pause().stop().destroy()
        PrivateStateFileStore.delete(second, "batch-generate-state", secondToken)
    }

    @Test
    fun largeImportedBatchUsesReplaceableCacheFileAndSurvivesColdRestore() {
        val marker = "batch-large-marker"
        val imported = List(128) { index ->
            BatchGenerator.BatchItem("$marker-$index-${"x".repeat(4096)}", BarcodeFormat.CODE_128)
        }
        lateinit var savedState: Bundle
        lateinit var firstToken: String
        scenario.onActivity { activity ->
            val emptyState = Bundle().also { saveInstanceState(activity, it) }
            val emptyStateSize = parcelSize(emptyState)
            setField(activity, "importedItems", imported)
            activity.findViewById<android.widget.EditText>(R.id.etContent)
                .setText(imported.joinToString("\n") { it.content })
            val firstState = Bundle().also { saveInstanceState(activity, it) }
            firstToken = getField(activity, "stateCacheToken") as String

            savedState = Bundle().also { saveInstanceState(activity, it) }
            val secondToken = getField(activity, "stateCacheToken") as String
            assertNotEquals(firstToken, secondToken)
            assertFalse(PrivateStateFileStore.file(activity, "batch-generate-state", firstToken).exists())
            assertTrue(PrivateStateFileStore.file(activity, "batch-generate-state", secondToken).isFile)
            assertTrue(
                PrivateStateFileStore.file(activity, "batch-generate-state", secondToken).absolutePath
                    .startsWith(activity.noBackupFilesDir.absolutePath)
            )
            assertTrue(parcelSize(savedState) - emptyStateSize < 4096)
            assertTrue(parcelSize(firstState) - emptyStateSize < 4096)
        }

        val controller = Robolectric.buildActivity(BatchGenerateActivity::class.java)
            .create(Bundle(savedState))
            .start()
            .resume()
            .visible()
        val restored = controller.get()
        assertEquals(imported, getField(restored, "importedItems"))
        assertEquals(imported.joinToString("\n") { it.content }, restored.findViewById<android.widget.EditText>(R.id.etContent).text.toString())
        controller.pause().stop().destroy()
    }

    @Test
    fun directInputUsesSerializedUtf8StateBoundary() {
        val emptyBytes = BatchGenerateActivity.batchStateBytes("", null).size
        val acceptedCount = (BatchGenerateActivity.STATE_CACHE_MAX_BYTES - emptyBytes) / 3
        val accepted = "汉".repeat(acceptedCount)
        val rejected = accepted + "汉"

        assertTrue(
            BatchGenerateActivity.batchStateBytes(accepted, null).size <=
                BatchGenerateActivity.STATE_CACHE_MAX_BYTES
        )
        assertTrue(
            BatchGenerateActivity.batchStateBytes(rejected, null).size >
                BatchGenerateActivity.STATE_CACHE_MAX_BYTES
        )

        scenario.onActivity { activity ->
            val input = activity.findViewById<android.widget.EditText>(R.id.etContent)
            input.setText("recoverable")
            input.text.replace(0, input.length(), "汉".repeat(2 * 1024 * 1024))
            assertEquals("recoverable", input.text.toString())
        }
        idleMain()
        assertEquals(
            scenario.run {
                var message = ""
                onActivity { message = it.getString(R.string.batch_limit_total_size, 2) }
                message
            },
            ShadowToast.getTextOfLatestToast().toString()
        )
    }

    @Test
    fun importedStateStoresItemsWithoutDuplicatingPreviewText() {
        val items = List(128) { index ->
            BatchGenerator.BatchItem("item-$index-${"汉".repeat(2048)}", BarcodeFormat.CODE_128)
        }
        val preview = items.joinToString("\n") { it.content }
        val root = JSONObject(BatchGenerateActivity.batchStateBytes(preview, items).toString(Charsets.UTF_8))

        assertFalse(root.has("inputText"))
        assertEquals(items.size, root.getJSONArray("importedItems").length())
        assertTrue(
            BatchGenerateActivity.batchStateBytes(preview, items).size <=
                BatchGenerateActivity.STATE_CACHE_MAX_BYTES
        )
    }

    @Test
    fun oversizedStateSaveKeepsPreviousRecoverableTokenAndShowsFailure() {
        lateinit var originalToken: String
        lateinit var originalFile: File
        scenario.onActivity { activity ->
            val input = activity.findViewById<android.widget.EditText>(R.id.etContent)
            input.setText("recoverable")
            val originalState = Bundle().also { saveInstanceState(activity, it) }
            originalToken = originalState.getString("batch_cache_token")!!
            originalFile = PrivateStateFileStore.file(activity, "batch-generate-state", originalToken)

            input.filters = emptyArray()
            input.setText("汉".repeat(2 * 1024 * 1024))
            val failedState = Bundle().also { saveInstanceState(activity, it) }

            assertEquals(originalToken, failedState.getString("batch_cache_token"))
            assertEquals(originalToken, getField(activity, "stateCacheToken"))
            assertTrue(originalFile.isFile)
            val restored = JSONObject(originalFile.readText())
            assertEquals("recoverable", restored.getString("inputText"))
            assertTrue(
                ShadowToast.getTextOfLatestToast().toString()
                    .startsWith(activity.getString(R.string.failed_to_save, "").substringBefore(":"))
            )
        }
    }

    @Test
    fun missingImportedBatchCacheSafelyFallsBackToNoImportedItems() {
        lateinit var savedState: Bundle
        scenario.onActivity { activity ->
            setField(activity, "importedItems", listOf(BatchGenerator.BatchItem("missing")))
            activity.selectedFormat = BarcodeFormat.CODE_128
            savedState = Bundle().also { saveInstanceState(activity, it) }
            val token = getField(activity, "stateCacheToken") as String
            PrivateStateFileStore.file(activity, "batch-generate-state", token).delete()
        }

        val controller = Robolectric.buildActivity(BatchGenerateActivity::class.java)
            .create(Bundle(savedState))
            .start()
            .resume()
            .visible()
        val restored = controller.get()
        assertNull(getField(restored, "importedItems"))
        assertEquals(BarcodeFormat.CODE_128, restored.selectedFormat)
        controller.pause().stop().destroy()
    }

    @Test
    fun batchStyleDialogUsesDraftAndCancelDoesNotApply() {
        scenario.onActivity { it.findViewById<View>(R.id.btnBatchStyle).performClick() }
        idleMain()
        val dialog = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        val scheme = AdvancedBarcodeGenerator.ColorSchemes.BLUE
        val schemeView = findTaggedView(dialog.window!!.decorView, scheme)
        assertNotNull(schemeView)
        schemeView!!.performClick()

        scenario.onActivity { activity -> assertNull(activity.batchScheme) }
        val cancel = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)
        assertNotNull("Style dialog must provide an explicit cancel action", cancel)
        cancel.performClick()
        scenario.onActivity { activity -> assertNull(activity.batchScheme) }
    }

    @Test
    fun batchStyleDialogBackDoesNotApplyDraft() {
        scenario.onActivity { it.findViewById<View>(R.id.btnBatchStyle).performClick() }
        idleMain()
        val dialog = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        val scheme = AdvancedBarcodeGenerator.ColorSchemes.GREEN
        findTaggedView(dialog.window!!.decorView, scheme)!!.performClick()

        dialog.onBackPressed()

        scenario.onActivity { activity -> assertNull(activity.batchScheme) }
    }

    @Test
    fun templateNullAndExceptionalOutputStreamsShowFailureInsteadOfSuccess() {
        assertTemplateWriteFailure(Uri.parse("content://batch-template/null"), null)
        assertTemplateWriteFailure(Uri.parse("content://batch-template/error"), IOException("write failed"))
    }

    private fun assertTemplateWriteFailure(uri: Uri, failure: IOException?) {
        var requestCode = 0
        scenario.onActivity { activity ->
            Shadows.shadowOf(activity.contentResolver).registerOutputStreamSupplier(uri) {
                failure?.let { throw it }
                null
            }
            activity.findViewById<View>(R.id.btnDownloadTemplate).performClick()
        }
        idleMain()
        scenario.onActivity { activity ->
            requestCode = Shadows.shadowOf(activity).nextStartedActivityForResult.requestCode
            (activity as ComponentActivity).activityResultRegistry.dispatchResult(
                requestCode,
                Activity.RESULT_OK,
                Intent().setData(uri)
            )
        }
        idleMain()

        scenario.onActivity { activity ->
            val toast = ShadowToast.getTextOfLatestToast().toString()
            assertTrue(toast.startsWith(activity.getString(R.string.failed_to_save, "").substringBefore(":")))
            assertNotEquals(activity.getString(R.string.batch_template_saved), toast)
        }
    }
}
