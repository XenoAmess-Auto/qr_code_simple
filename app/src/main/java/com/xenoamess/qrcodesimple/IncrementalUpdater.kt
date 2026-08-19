package com.xenoamess.qrcodesimple

import android.content.Context
import android.util.Log
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Executes a verified delta chain (ApkDiffPatch). A null result is intentionally recoverable:
 * callers download the full APK through the normal verified path instead.
 */
class IncrementalUpdater(
    private val context: Context,
    internal var downloader: suspend (
        url: String,
        destination: File,
        hop: UpdateDecider.PatchHop,
        onProgress: (Int) -> Unit
    ) -> Boolean = { _, _, _, _ -> false },
    internal var patcher: (base: File, patch: File, output: File) -> Unit =
        { base, patch, output ->
            ApkPatcher.applyPatch(context, base, patch, output)
        },
    internal var installedApkProvider: (Context) -> File? = { ApkPatcher.installedApkFile(it) },
    internal var workArtifactListener: (File) -> Unit = {}
) {

    private companion object {
        const val TAG = "IncrementalUpdater"
    }

    suspend fun executeChain(
        chain: UpdateDecider.UpdateChain,
        outputFile: File,
        expectedFinalSha256: String,
        targetApkSizeBytes: Long,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val outputDirectory = outputFile.parentFile ?: return@withContext null
        val workId = UUID.randomUUID().toString()
        val workDirectory = File(context.filesDir, "updates/incremental-$workId")
        val finalPart = File(outputDirectory, ".${outputFile.name}.$workId.incremental.part")
        workArtifactListener(workDirectory)
        workArtifactListener(finalPart)
        var completed = false
        var replacedOutput = false

        try {
            ensureActive()
            if (targetApkSizeBytes !in 1..UpdateDecider.MAX_ARTIFACT_BYTES) {
                return@withContext null
            }
            if (!workDirectory.mkdirs() && !workDirectory.isDirectory) return@withContext null
            if (!outputDirectory.mkdirs() && !outputDirectory.isDirectory) return@withContext null

            val installedApk = installedApkProvider(context) ?: return@withContext null
            if (!installedApk.isFile || !installedApk.canRead()) return@withContext null
            // Planner checks this before choosing a chain; retain an executor-side guard as defense
            // against callers or metadata changing between planning and execution.
            if (!ApkPatcher.sha256(installedApk, isCancelled = { !isActive })
                    .equals(chain.fromApkSha256, ignoreCase = true)
            ) {
                return@withContext null
            }

            var currentApk = installedApk
            var downloadedBefore = 0L
            for ((index, hop) in chain.hops.withIndex()) {
                ensureActive()
                val patchFile = File(workDirectory, "hop-$index.patch")
                val progressBeforeHop = downloadedBefore
                val downloaded = downloader(hop.url, patchFile, hop) { hopPercent ->
                    val overall = (
                        (progressBeforeHop + hop.sizeBytes * hopPercent.coerceIn(0, 100) / 100) *
                            100 / chain.totalSizeBytes
                        ).toInt()
                    onProgress(overall.coerceIn(0, 100))
                }
                if (!downloaded || !patchFile.isFile || patchFile.length() != hop.sizeBytes) {
                    return@withContext null
                }
                if (!ApkPatcher.sha256(patchFile, isCancelled = { !isActive })
                        .equals(hop.patchSha256, ignoreCase = true)
                ) {
                    return@withContext null
                }

                val isLastHop = index == chain.hops.lastIndex
                val output = if (isLastHop) {
                    finalPart
                } else {
                    File(workDirectory, "hop-$index.apk")
                }
                output.delete()
                ensureActive()
                patcher(currentApk, patchFile, output)
                ensureActive()
                if (!output.isFile ||
                    output.length() > targetApkSizeBytes ||
                    (isLastHop && output.length() != targetApkSizeBytes) ||
                    !ApkPatcher.sha256(output, isCancelled = { !isActive })
                        .equals(hop.resultSha256, ignoreCase = true)
                ) {
                    return@withContext null
                }
                patchFile.delete()
                currentApk = output
                downloadedBefore += hop.sizeBytes
            }

            if (!ApkPatcher.sha256(finalPart, isCancelled = { !isActive })
                    .equals(expectedFinalSha256, ignoreCase = true)
            ) {
                return@withContext null
            }
            ensureActive()
            if (outputFile.exists() && !outputFile.delete()) return@withContext null
            replacedOutput = true
            if (!finalPart.renameTo(outputFile)) {
                finalPart.copyTo(outputFile, overwrite = true)
                finalPart.delete()
            }
            completed = outputFile.isFile
            outputFile.takeIf { completed }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "delta chain failed, caller falls back to full APK: ${e.message}")
            null
        } finally {
            workDirectory.deleteRecursively()
            finalPart.delete()
            if (!completed && replacedOutput) outputFile.delete()
        }
    }
}
