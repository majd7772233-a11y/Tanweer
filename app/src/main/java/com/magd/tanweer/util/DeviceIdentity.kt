package com.magd.tanweer.util

import android.content.Context
import java.util.UUID

/**
 * Creates one opaque identity per app installation.
 *
 * It is not an Android hardware identifier and does not require device permissions.
 * The value survives app restarts and normal app updates, but is cleared with app data.
 */
object DeviceIdentity {
    private const val PREFS_NAME = "tanweer_device_identity"
    private const val DEVICE_ID_KEY = "device_id"

    fun getOrCreate(context: Context): String {
        val preferences = context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val existing = preferences.getString(DEVICE_ID_KEY, null)
            ?.trim()
            ?.takeIf { it.length in 8..128 }

        if (existing != null) return existing

        val generated = UUID.randomUUID().toString()
        val saved = preferences.edit().putString(DEVICE_ID_KEY, generated).commit()
        if (!saved) {
            throw IllegalStateException("تعذر حفظ معرّف تثبيت الجهاز محلياً")
        }
        return generated
    }
}
