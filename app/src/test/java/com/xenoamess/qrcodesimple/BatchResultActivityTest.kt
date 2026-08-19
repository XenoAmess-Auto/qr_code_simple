package com.xenoamess.qrcodesimple

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.lang.reflect.InvocationTargetException

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
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = BatchResultTransfer.createIntent(
            context,
            contents.mapIndexed { index, content -> BatchGenerator.BatchItem(content, format, fileName = "batch_${index + 1}") }
        )
        return ActivityScenario.launch<BatchResultActivity>(intent)
    }

    @Test
    fun batchGenerationWithStyleUsesStyledPipeline() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = BatchResultTransfer.createIntent(
            context,
            listOf(BatchGenerator.BatchItem("https://styled.example")),
            AdvancedBarcodeGenerator.ColorSchemes.BLUE.toJson()
        )
        val scenario = ActivityScenario.launch<BatchResultActivity>(intent)
        idleMain()

        assertTrue(
            "Styled generation should complete and render results",
            waitFor {
                var progressGone = false
                var itemCount = 0
                scenario.onActivity { activity ->
                    progressGone = activity.findViewById<ProgressBar>(R.id.progressBar).visibility == View.GONE
                    itemCount = activity.findViewById<RecyclerView>(R.id.recyclerView).adapter?.itemCount ?: 0
                }
                progressGone && itemCount == 1
            }
        )

        scenario.onActivity { activity ->
            val text = activity.findViewById<TextView>(R.id.tvProgress).text.toString()
            assertTrue(text.contains("Generated: 1/1"))
        }
        scenario.close()
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

    @Test
    fun itemIntentSurvivesActivityRecreationWithAllFields() {
        val item = BatchGenerator.BatchItem("preserved", BarcodeFormat.CODE_128, 0xff112233.toInt(), 0xfffefefe.toInt(), "named")
        val scenario = ActivityScenario.launch<BatchResultActivity>(
            BatchResultTransfer.createIntent(ApplicationProvider.getApplicationContext(), listOf(item))
        )
        scenario.recreate()
        assertTrue(waitFor {
            var preserved = false
            scenario.onActivity { activity ->
                val field = BatchResultActivity::class.java.getDeclaredField("results").apply { isAccessible = true }
                @Suppress("UNCHECKED_CAST") val results = field.get(activity) as List<BatchResultActivity.BatchResult>
                preserved = results.singleOrNull()?.item == item
            }
            preserved
        })
        scenario.close()
    }

    @Test
    fun rowColorsOverrideOnlyBatchStyleColors() {
        val scenario = launchWith(listOf("test"))
        scenario.onActivity { activity ->
            val original = AdvancedBarcodeGenerator.StyleConfig(foregroundColor = 0xff010203.toInt(), backgroundColor = 0xfff0f1f2.toInt(), logoScale = .4f)
            val resolved = activity.styleForItem(original, BatchGenerator.BatchItem("test", foregroundColor = 0xff111111.toInt()))
            assertEquals(0xff111111.toInt(), resolved.foregroundColor)
            assertEquals(original.backgroundColor, resolved.backgroundColor)
            assertEquals(original.logoScale, resolved.logoScale)
        }
        scenario.close()
    }

    @Test
    @Config(sdk = [29])
    fun api29SingleImageIsPendingUntilWriteCompletes() {
        val scenario = launchWith(listOf("pending-single"))
        scenario.onActivity { activity ->
            val source = cacheFile("single-source.png", byteArrayOf(1, 2, 3))
            val resolver = Shadows.shadowOf(activity.contentResolver)
            val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "1")
            val output = ByteArrayOutputStream()
            resolver.registerOutputStream(uri, output)

            invokeCopySingle(activity, source, "single")

            assertEquals(1, resolver.insertStatements.last().contentValues.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
            assertEquals(uri, resolver.updateStatements.last().uri)
            assertEquals(0, resolver.updateStatements.last().contentValues.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
            assertTrue(output.toByteArray().contentEquals(source.readBytes()))
            assertFalse(resolver.deletedUris.contains(uri))
        }
        scenario.close()
    }

    @Test
    @Config(sdk = [29])
    fun api29ZipIsPendingUntilWriteCompletes() {
        val scenario = launchWith(listOf("pending-zip"))
        scenario.onActivity { activity ->
            val source = cacheFile("zip-source.png", byteArrayOf(4, 5, 6))
            val resolver = Shadows.shadowOf(activity.contentResolver)
            val uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "1")
            resolver.registerOutputStream(uri, ByteArrayOutputStream())

            invokeWriteZip(activity, "pending.zip", listOf(batchResult(source)))

            assertEquals(1, resolver.insertStatements.last().contentValues.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
            assertEquals(uri, resolver.updateStatements.last().uri)
            assertEquals(0, resolver.updateStatements.last().contentValues.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
            assertFalse(resolver.deletedUris.contains(uri))
        }
        scenario.close()
    }

    @Test
    @Config(sdk = [29])
    fun api29SingleImageDeletesInsertedItemForEveryOutputFailure() {
        assertInsertedItemDeletedForOutputFailures(isZip = false)
    }

    @Test
    @Config(sdk = [29])
    fun api29ZipDeletesInsertedItemForEveryOutputFailure() {
        assertInsertedItemDeletedForOutputFailures(isZip = true)
    }

    @Test
    @Config(sdk = [29])
    fun api29NullOutputStreamDeletesInsertedItem() {
        val scenario = launchWith(listOf("null-output"))
        scenario.onActivity { activity ->
            val resolver = Shadows.shadowOf(activity.contentResolver)
            val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "1")

            val failure = runCatching {
                activity.writePendingMediaStore(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    ContentValues(),
                    openOutputStream = { null },
                    write = { }
                )
            }.exceptionOrNull()

            assertNotNull(failure)
            assertTrue(resolver.deletedUris.contains(uri))
            assertNull(resolver.updateStatements.lastOrNull { it.uri == uri })
        }
        scenario.close()
    }

    @Test
    fun api28FailedZipLeavesNeitherFinalNorPartialFile() {
        val scenario = launchWith(listOf("atomic-zip"))
        scenario.onActivity { activity ->
            val downloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val name = "atomic_${System.nanoTime()}.zip"
            val finalFile = File(downloads, name)
            val unreadableSource = File(activity.cacheDir, "directory-source-${System.nanoTime()}").apply { mkdirs() }

            assertTrue(invokeWriteZip(activity, name, listOf(batchResult(unreadableSource))) is IOException)
            assertFalse(finalFile.exists())
            assertTrue(downloads.listFiles { file -> file.name.startsWith(".$name") && file.name.endsWith(".partial") }.isNullOrEmpty())
        }
        scenario.close()
    }

    @Test
    fun api28AtomicWriterPreservesExistingTargetAfterPartialWriteFailure() {
        val scenario = launchWith(listOf("atomic-file"))
        scenario.onActivity { activity ->
            val target = File(activity.cacheDir, "atomic-target-${System.nanoTime()}.png").apply {
                writeBytes(byteArrayOf(1, 2, 3))
            }

            val failure = runCatching {
                activity.writeLegacyFileAtomically(target) { output ->
                    output.write(byteArrayOf(9, 9))
                    throw IOException("incomplete write")
                }
            }.exceptionOrNull()

            assertTrue(failure is IOException)
            assertArrayEquals(byteArrayOf(1, 2, 3), target.readBytes())
            assertTrue(target.parentFile?.listFiles { file ->
                file.name.startsWith(".${target.name}.") && file.name.endsWith(".partial")
            }.isNullOrEmpty())
        }
        scenario.close()
    }

    @Test
    fun logoCacheSurvivesRecreationAndIsDeletedWhenActivityFinishes() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(IntArray(4) { Color.MAGENTA }, 0, 2, 0, 0, 2, 2)
        val intent = BatchResultTransfer.createIntent(
            context,
            listOf(BatchGenerator.BatchItem("logo-recreate")),
            AdvancedBarcodeGenerator.StyleConfig().toJson(),
            bitmap
        )
        bitmap.recycle()
        val token = intent.getStringExtra(BatchGenerateActivity.EXTRA_BATCH_TOKEN)!!
        val payloadFile = BatchResultTransfer.file(context, token)

        val scenario = ActivityScenario.launch<BatchResultActivity>(intent)
        scenario.recreate()

        scenario.onActivity { activity ->
            val field = BatchResultActivity::class.java.getDeclaredField("batchStyle").apply { isAccessible = true }
            val style = field.get(activity) as AdvancedBarcodeGenerator.StyleConfig
            assertNotNull(style.logoBitmap)
            assertTrue(payloadFile.exists())
        }
        scenario.close()
        assertFalse(payloadFile.exists())
    }

    private fun assertInsertedItemDeletedForOutputFailures(isZip: Boolean) {
        val failures = listOf<(() -> OutputStream?)>(
            { FailingOutputStream(failOnWrite = true) },
            { FailingOutputStream(failOnClose = true) }
        )
        val scenario = launchWith(listOf("rollback"))
        scenario.onActivity { activity ->
            failures.forEachIndexed { index, outputSupplier ->
                val source = cacheFile("rollback-$isZip-$index.png", byteArrayOf(7, 8, 9))
                val resolver = Shadows.shadowOf(activity.contentResolver)
                val collection = if (isZip) MediaStore.Downloads.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val uri = Uri.withAppendedPath(collection, (index + 1).toString())
                resolver.registerOutputStreamSupplier(uri, outputSupplier)

                val failure = if (isZip) {
                    invokeWriteZip(activity, "rollback-$index.zip", listOf(batchResult(source)))
                } else {
                    invokeCopySingle(activity, source, "rollback-$index")
                }

                assertNotNull("Output failure $index must propagate", failure)
                assertTrue("Inserted URI must be deleted after output failure", resolver.deletedUris.contains(uri))
                assertNull(resolver.updateStatements.lastOrNull { it.uri == uri })
            }
        }
        scenario.close()
    }

    private fun invokeCopySingle(activity: BatchResultActivity, source: File, name: String): Throwable? =
        invokePrivate(activity, "copySingleToMediaStore", arrayOf(File::class.java, String::class.java), arrayOf(source, name))

    private fun invokeWriteZip(
        activity: BatchResultActivity,
        name: String,
        results: List<BatchResultActivity.BatchResult>
    ): Throwable? = invokePrivate(activity, "writeZipToMediaStore", arrayOf(String::class.java, List::class.java), arrayOf(name, results))

    private fun invokePrivate(
        activity: BatchResultActivity,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        arguments: Array<Any>
    ): Throwable? = try {
        BatchResultActivity::class.java.getDeclaredMethod(methodName, *parameterTypes).apply { isAccessible = true }
            .invoke(activity, *arguments)
        null
    } catch (exception: InvocationTargetException) {
        exception.cause
    }

    private fun cacheFile(name: String, bytes: ByteArray): File = File(
        ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
        "${System.nanoTime()}-$name"
    ).apply { writeBytes(bytes) }

    private fun batchResult(file: File) = BatchResultActivity.BatchResult(
        content = "content",
        bitmap = null,
        fileName = "barcode",
        imageFile = file
    )

    private class FailingOutputStream(
        private val failOnWrite: Boolean = false,
        private val failOnClose: Boolean = false
    ) : OutputStream() {
        override fun write(value: Int) {
            if (failOnWrite) throw IOException("write failed")
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            if (failOnWrite) throw IOException("write failed")
        }

        override fun close() {
            if (failOnClose) throw IOException("close failed")
        }
    }
}
