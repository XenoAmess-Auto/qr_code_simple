package com.xenoamess.qrcodesimple

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Runs one exact timeout check while visible, rather than polling for lock changes. */
internal suspend fun monitorAppLockTimeout(
    onUnlocked: () -> Unit = {},
    onLocked: () -> Unit
): Unit = AppLockManager.lockChanges.collectLatest {
    if (!AppLockManager.isUnlocked()) {
        onLocked()
        awaitCancellation()
    }
    onUnlocked()
    while (true) {
        val remaining = AppLockManager.remainingUnlockedMillis()
        when {
            remaining == null -> awaitCancellation()
            remaining == 0L -> {
                onLocked()
                awaitCancellation()
            }
            else -> delay(remaining)
        }
    }
}
