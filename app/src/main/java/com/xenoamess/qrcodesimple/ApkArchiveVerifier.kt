package com.xenoamess.qrcodesimple

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException

/** Verifies that an archive can only replace this exact installed application identity. */
object ApkArchiveVerifier {

    data class PackageIdentity(
        val packageName: String,
        val versionCode: Long,
        val signerFingerprints: Set<String>
    )

    fun verify(
        context: Context,
        archiveFile: File,
        expectedVersionCode: Long
    ): Boolean {
        return try {
            val packageManager = context.packageManager
            val installed = toIdentity(
                packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            )
            val archive = toIdentity(
                packageManager.getPackageArchiveInfo(
                    archiveFile.absolutePath,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            )
            matches(context.packageName, expectedVersionCode, installed, archive)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    internal fun matches(
        expectedPackageName: String,
        expectedVersionCode: Long,
        installed: PackageIdentity?,
        archive: PackageIdentity?
    ): Boolean {
        if (expectedPackageName.isBlank() || expectedVersionCode <= 0) return false
        if (installed == null || archive == null) return false
        return installed.packageName == expectedPackageName &&
            archive.packageName == expectedPackageName &&
            archive.versionCode == expectedVersionCode &&
            installed.signerFingerprints.isNotEmpty() &&
            installed.signerFingerprints == archive.signerFingerprints
    }

    private fun toIdentity(packageInfo: PackageInfo?): PackageIdentity? {
        val info = packageInfo ?: return null
        val packageName = info.packageName?.takeIf { it.isNotBlank() } ?: return null
        val signatures = info.signingInfo?.apkContentsSigners ?: return null
        val signerFingerprints = signatures.mapTo(mutableSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
        }
        if (signerFingerprints.isEmpty()) return null
        return PackageIdentity(packageName, info.longVersionCode, signerFingerprints)
    }
}
