package com.xenoamess.qrcodesimple

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

internal fun requiresLegacyPublicStoragePermission(context: Context): Boolean =
    requiresLegacyPublicStoragePermission(
        sdkInt = Build.VERSION.SDK_INT,
        permissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    )

internal fun requiresLegacyPublicStoragePermission(sdkInt: Int, permissionGranted: Boolean): Boolean =
    sdkInt <= Build.VERSION_CODES.P && !permissionGranted
