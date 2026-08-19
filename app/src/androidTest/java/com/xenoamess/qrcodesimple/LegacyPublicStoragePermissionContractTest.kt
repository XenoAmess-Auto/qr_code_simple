package com.xenoamess.qrcodesimple

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyPublicStoragePermissionContractTest {

    @Test
    fun manifestAndSdkPolicyMatchLegacyPublicWriteContract() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)

        val isLegacyDevice = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
        assertEquals(
            isLegacyDevice,
            packageInfo.requestedPermissions.orEmpty().contains(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        )
        assertEquals(isLegacyDevice, requiresLegacyPublicStoragePermission(Build.VERSION.SDK_INT, false))
        assertFalse(requiresLegacyPublicStoragePermission(Build.VERSION.SDK_INT, true))
        val permissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        assertEquals(
            isLegacyDevice && !permissionGranted,
            requiresLegacyPublicStoragePermission(context)
        )
        assertEquals(35, context.applicationInfo.targetSdkVersion)
    }
}
