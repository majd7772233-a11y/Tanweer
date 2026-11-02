package com.magd.tanweer.util

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

/**
 * Returns an opaque, stable installation/device identifier.
 *
 * Android ID is scoped by Android to the app signing key and Android user on modern
 * versions, and doesn't require device permissions. The raw Android ID is never sent
 * to the server; it is transformed into a namespace-specific SHA-256 identifier.
 * A random persisted fallback is used only when Android doesn't provide a usable ID.
 */
object DeviceIdentity {
    private const val PREFS_NAME = "tanweer_device_identity"
    private const val DEVICE_ID_KEY = "device_id"
    private const val NAMESPACE = "tanweer-active-device-v1:"

    fun getOrCreate(context: Context): String {
        val appContext = context.applicationContext
        val androidId = try {
            Settings.Secure.getString(
                appContext.contentResolver,
                Settings.Secure.ANDROID_ID
            )?.trim()
        } catch (_: Exception) {
            null
        }

        if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            return createOpaqueId(androidId)
        }

        val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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

    private fun createOpaqueId(androidId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((NAMESPACE + androidId).toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }
}
