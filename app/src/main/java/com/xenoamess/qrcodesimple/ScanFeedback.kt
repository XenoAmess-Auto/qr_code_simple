package com.xenoamess.qrcodesimple

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Scan-success feedback: a short beep and/or vibration, each gated by the
 * user-facing switches in PrivacySettingsActivity (defaults on).
 */
object ScanFeedback {

    private const val TAG = "ScanFeedback"

    fun play(context: Context) {
        if (QRCodeApp.isScanSoundEnabled(context)) {
            playBeep()
        }
        if (QRCodeApp.isScanVibrationEnabled(context)) {
            vibrate(context)
        }
    }

    private fun playBeep() {
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            // ToneGenerator must be released after the tone finishes.
            Thread {
                try {
                    Thread.sleep(200)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                } finally {
                    toneGenerator.release()
                }
            }.start()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play scan beep", e)
        }
    }

    private fun vibrate(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to vibrate on scan", e)
        }
    }
}
