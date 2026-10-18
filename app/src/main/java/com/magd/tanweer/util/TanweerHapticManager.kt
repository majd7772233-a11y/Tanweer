package com.magd.tanweer.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Universal Haptic Feedback Manager for Tanweer
 * Provides tactile haptic sensations on Android 8.0+ through Android 15.
 * Strictly respects the user's `hapticsEnabled` preference.
 */
object TanweerHapticManager {

    enum class HapticType {
        CLICK,
        TOGGLE,
        SUCCESS,
        WARNING,
        ERROR,
        BADGE_UNLOCK
    }

    fun performHaptic(context: Context, enabled: Boolean, type: HapticType = HapticType.CLICK) {
        if (!enabled) return

        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator == null || !vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effect = when (type) {
                    HapticType.CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    HapticType.TOGGLE -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    HapticType.SUCCESS -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    HapticType.BADGE_UNLOCK -> VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 80), intArrayOf(0, 180, 0, 255), -1)
                    HapticType.WARNING -> VibrationEffect.createWaveform(longArrayOf(0, 35, 45, 35), intArrayOf(0, 150, 0, 150), -1)
                    HapticType.ERROR -> VibrationEffect.createWaveform(longArrayOf(0, 60, 50, 60), intArrayOf(0, 220, 0, 220), -1)
                }
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val duration = when (type) {
                    HapticType.CLICK, HapticType.TOGGLE -> 20L
                    HapticType.SUCCESS -> 45L
                    HapticType.BADGE_UNLOCK -> 80L
                    HapticType.WARNING -> 50L
                    HapticType.ERROR -> 70L
                }
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                when (type) {
                    HapticType.CLICK, HapticType.TOGGLE -> vibrator.vibrate(20L)
                    HapticType.SUCCESS -> vibrator.vibrate(40L)
                    HapticType.BADGE_UNLOCK -> vibrator.vibrate(longArrayOf(0, 40, 50, 60), -1)
                    HapticType.WARNING, HapticType.ERROR -> vibrator.vibrate(longArrayOf(0, 40, 40, 40), -1)
                }
            }
        } catch (_: Exception) {
            // Gracefully ignore on devices without vibration hardware or restricted background vibration
        }
    }
}
