package com.xenoamess.qrcodesimple

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChainPlannerTest {

    @Test
    fun `matching base and smaller safe chain selects incremental`() {
        val plan = ChainPlanner.choosePlan(
            chain = chain(totalSizeBytes = 4L * MIB),
            localApkSha256 = "A".repeat(64),
            localApkSizeBytes = 20L * MIB,
            remoteApkSizeBytes = 100L * MIB
        )

        assertTrue(plan is ChainPlanner.UpdatePlan.Incremental)
    }

    @Test
    fun `missing base hash or oversized chain selects full apk`() {
        assertEquals(
            ChainPlanner.UpdatePlan.FullApk,
            ChainPlanner.choosePlan(chain(), null, 20L * MIB, 100L * MIB)
        )
        assertEquals(
            ChainPlanner.UpdatePlan.FullApk,
            ChainPlanner.choosePlan(
                chain(totalSizeBytes = 2L * MIB),
                "a".repeat(64),
                122L * MIB,
                200L * MIB
            )
        )
    }

    @Test
    fun `chain no smaller than full apk selects full apk`() {
        assertEquals(
            ChainPlanner.UpdatePlan.FullApk,
            ChainPlanner.choosePlan(chain(totalSizeBytes = 50L * MIB), "a".repeat(64), 8L * MIB, 50L * MIB)
        )
    }

    private fun chain(totalSizeBytes: Long = 4L * MIB) = UpdateDecider.UpdateChain(
        fromApkSha256 = "a".repeat(64),
        totalSizeBytes = totalSizeBytes,
        hops = listOf(
            UpdateDecider.PatchHop(
                toVersionCode = 19,
                url = "https://example.test/18-19.bspatch",
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
