package com.xenoamess.qrcodesimple

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChainPlannerTest {

    @Test
    fun `matching base and smaller chain selects incremental`() {
        val plan = ChainPlanner.choosePlan(
            chain = chain(totalSizeBytes = 4L * MIB),
            localApkSha256 = "A".repeat(64),
            remoteApkSizeBytes = 100L * MIB
        )

        assertTrue(plan is ChainPlanner.UpdatePlan.Incremental)
    }

    @Test
    fun `missing or mismatched base hash selects full apk`() {
        assertEquals(
            ChainPlanner.UpdatePlan.FullApk,
            ChainPlanner.choosePlan(chain(), null, 100L * MIB)
        )
        assertEquals(
            ChainPlanner.UpdatePlan.FullApk,
            ChainPlanner.choosePlan(chain(), "B".repeat(64), 100L * MIB)
        )
    }

    @Test
    fun `chain no smaller than full apk selects full apk`() {
        assertEquals(
            ChainPlanner.UpdatePlan.FullApk,
            ChainPlanner.choosePlan(chain(totalSizeBytes = 50L * MIB), "a".repeat(64), 50L * MIB)
        )
    }

    @Test
    fun `missing chain or empty hops selects full apk`() {
        assertEquals(ChainPlanner.UpdatePlan.FullApk, ChainPlanner.choosePlan(null, "a".repeat(64), 100L * MIB))
        assertEquals(
            ChainPlanner.UpdatePlan.FullApk,
            ChainPlanner.choosePlan(UpdateDecider.UpdateChain("a".repeat(64), 0L, emptyList()), "a".repeat(64), 100L * MIB)
        )
    }

    private fun chain(totalSizeBytes: Long = 4L * MIB) = UpdateDecider.UpdateChain(
        fromApkSha256 = "a".repeat(64),
        totalSizeBytes = totalSizeBytes,
        hops = listOf(
            UpdateDecider.PatchHop(
                toVersionCode = 19,
                url = "https://example.test/18-19.patch",
                sizeBytes = totalSizeBytes,
                patchSha256 = "b".repeat(64),
                resultSha256 = "c".repeat(64)
            )
        )
    )

    companion object {
        private const val MIB = 1024L * 1024L
    }
}
