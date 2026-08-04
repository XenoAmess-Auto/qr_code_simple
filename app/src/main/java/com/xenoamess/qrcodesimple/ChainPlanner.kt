package com.xenoamess.qrcodesimple

/** Chooses the safest transport for a verified update. */
object ChainPlanner {

    sealed interface UpdatePlan {
        data class Incremental(val chain: UpdateDecider.UpdateChain) : UpdatePlan
        data object FullApk : UpdatePlan
    }

    /**
     * A chain is optional optimization only. Any uncertainty, including memory pressure risk,
     * returns FullApk so the normal verified download path remains available.
     */
    fun choosePlan(
        chain: UpdateDecider.UpdateChain?,
        localApkSha256: String?,
        localApkSizeBytes: Long,
        remoteApkSizeBytes: Long
    ): UpdatePlan {
        if (chain == null || chain.hops.isEmpty()) return UpdatePlan.FullApk
        if (localApkSha256 == null ||
            !localApkSha256.equals(chain.fromApkSha256, ignoreCase = true)
        ) {
            return UpdatePlan.FullApk
        }
        if (remoteApkSizeBytes <= 0 || chain.totalSizeBytes >= remoteApkSizeBytes) {
            return UpdatePlan.FullApk
        }
        if (!ApkPatcher.hasSafeIncrementalInputSize(localApkSizeBytes, chain.totalSizeBytes)) {
            return UpdatePlan.FullApk
        }
        return UpdatePlan.Incremental(chain)
    }
}
