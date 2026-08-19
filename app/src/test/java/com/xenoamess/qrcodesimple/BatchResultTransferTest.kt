package com.xenoamess.qrcodesimple

import android.content.Context
import android.os.Parcel
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class BatchResultTransferTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun largeBatchIntentContainsOnlySmallToken() {
        val marker = "must-not-enter-intent"
        val items = List(300) { BatchGenerator.BatchItem("$marker-$it-${"x".repeat(4096)}") }

        val intent = BatchResultTransfer.createIntent(context, items)

        assertNull(intent.getStringExtra("batch_items_json"))
        assertFalse(intent.extras.toString().contains(marker))
        assertTrue(parcelSize(intent) < 16 * 1024)
        val token = intent.getStringExtra(BatchGenerateActivity.EXTRA_BATCH_TOKEN)
        assertEquals(items, BatchResultTransfer.read(context, token).items)
        BatchResultTransfer.delete(context, token)
    }

    @Test
    fun tokenTraversalAndNonCanonicalTokensAreRejected() {
        val outside = java.io.File(context.noBackupFilesDir, "outside.json").apply { writeText("keep") }

        val traversal = runCatching {
            PrivateStateFileStore.read(context, "batch-result-transfer", "../outside", 100)
        }.exceptionOrNull()

        assertTrue(traversal is IOException)
        assertEquals("keep", outside.readText())
        assertNull(PrivateStateFileStore.validToken("550E8400-E29B-41D4-A716-446655440000"))
        assertNull(PrivateStateFileStore.validToken("550e8400-e29b-41d4-a716-446655440000.json"))
        outside.delete()
    }

    @Test
    fun missingTokenFileFailsAndResultActivityFinishes() {
        val token = PrivateStateFileStore.newToken()
        assertTrue(runCatching { BatchResultTransfer.read(context, token) }.exceptionOrNull() is IOException)

        val scenario = ActivityScenario.launch<BatchResultActivity>(
            android.content.Intent(context, BatchResultActivity::class.java)
                .putExtra(BatchGenerateActivity.EXTRA_BATCH_TOKEN, token)
        )

        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        scenario.close()
    }

    @Test
    fun finishingResultActivityDeletesConsumedTransfer() {
        val intent = BatchResultTransfer.createIntent(context, listOf(BatchGenerator.BatchItem("cleanup")))
        val token = intent.getStringExtra(BatchGenerateActivity.EXTRA_BATCH_TOKEN)!!
        val file = BatchResultTransfer.file(context, token)
        assertTrue(file.isFile)

        ActivityScenario.launch<BatchResultActivity>(intent).use { scenario ->
            assertTrue(file.isFile)
            scenario.moveToState(Lifecycle.State.DESTROYED)
        }

        assertFalse(file.exists())
    }

    @Test
    fun expiredFilesAreRemovedButActiveTransferIsKept() {
        val expiredIntent = BatchResultTransfer.createIntent(context, listOf(BatchGenerator.BatchItem("expired")))
        val activeIntent = BatchResultTransfer.createIntent(context, listOf(BatchGenerator.BatchItem("active")))
        val expiredToken = expiredIntent.getStringExtra(BatchGenerateActivity.EXTRA_BATCH_TOKEN)!!
        val activeToken = activeIntent.getStringExtra(BatchGenerateActivity.EXTRA_BATCH_TOKEN)!!
        val expiredFile = BatchResultTransfer.file(context, expiredToken)
        val activeFile = BatchResultTransfer.file(context, activeToken)
        expiredFile.setLastModified(1L)
        activeFile.setLastModified(1L)

        BatchResultTransfer.cleanupExpired(context, activeToken, nowMs = 2 * 24 * 60 * 60 * 1000L)

        assertFalse(expiredFile.exists())
        assertTrue(activeFile.exists())
        assertEquals("active", BatchResultTransfer.read(context, activeToken).items.single().content)
        assertTrue(activeFile.exists())
        BatchResultTransfer.delete(context, activeToken)
    }

    @Test
    fun itemCountLengthAndSerializedSizeLimitsAreHardFailures() {
        assertEquals(
            BatchResultTransfer.Limit.ITEM_COUNT,
            BatchResultTransfer.validate(List(BatchResultTransfer.MAX_ITEMS + 1) { BatchGenerator.BatchItem("x") })
        )
        assertEquals(
            BatchResultTransfer.Limit.ITEM_LENGTH,
            BatchResultTransfer.validate(
                listOf(BatchGenerator.BatchItem("x".repeat(BatchResultTransfer.MAX_ITEM_CHARACTERS + 1)))
            )
        )
        assertEquals(
            BatchResultTransfer.Limit.ITEM_LENGTH,
            BatchResultTransfer.validate(
                listOf(
                    BatchGenerator.BatchItem(
                        "valid",
                        fileName = "f".repeat(BatchResultTransfer.MAX_ITEM_CHARACTERS + 1)
                    )
                )
            )
        )
        assertEquals(
            BatchResultTransfer.Limit.TOTAL_BYTES,
            BatchResultTransfer.validate(
                List(300) { BatchGenerator.BatchItem("x".repeat(BatchResultTransfer.MAX_ITEM_CHARACTERS)) }
            )
        )
        assertTrue(
            runCatching {
                BatchResultTransfer.write(
                    context,
                    List(BatchResultTransfer.MAX_ITEMS + 1) { BatchGenerator.BatchItem("x") },
                    null,
                    null
                )
            }.isFailure
        )
    }

    private fun parcelSize(intent: android.content.Intent): Int = Parcel.obtain().let { parcel ->
        try {
            intent.writeToParcel(parcel, 0)
            parcel.dataSize()
        } finally {
            parcel.recycle()
        }
    }
}
