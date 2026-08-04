package com.xenoamess.qrcodesimple

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkArchiveVerifierTest {

    @Test
    fun `matching archive requires current package version code and signer set`() {
        val installed = identity(
            packageName = "com.xenoamess.qrcodesimple",
            versionCode = 18,
            signers = setOf("signer-a", "signer-b")
        )
        val archive = identity(
            packageName = "com.xenoamess.qrcodesimple",
            versionCode = 19,
            signers = setOf("signer-b", "signer-a")
        )

        assertTrue(
            ApkArchiveVerifier.matches(
                "com.xenoamess.qrcodesimple",
                19,
                installed,
                archive
            )
        )
    }

    @Test
    fun `archive identity rejects package version and signer mismatches`() {
        val installed = identity("com.xenoamess.qrcodesimple", 18, setOf("signer-a"))

        assertFalse(
            ApkArchiveVerifier.matches(
                "com.xenoamess.qrcodesimple",
                19,
                installed,
                identity("other.package", 19, setOf("signer-a"))
            )
        )
        assertFalse(
            ApkArchiveVerifier.matches(
                "com.xenoamess.qrcodesimple",
                19,
                installed,
                identity("com.xenoamess.qrcodesimple", 20, setOf("signer-a"))
            )
        )
        assertFalse(
            ApkArchiveVerifier.matches(
                "com.xenoamess.qrcodesimple",
                19,
                installed,
                identity("com.xenoamess.qrcodesimple", 19, setOf("signer-b"))
            )
        )
    }

    private fun identity(
        packageName: String,
        versionCode: Long,
        signers: Set<String>
    ) = ApkArchiveVerifier.PackageIdentity(packageName, versionCode, signers)
}
