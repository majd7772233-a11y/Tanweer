package com.magd.tanweer.util

import java.io.IOException
import java.net.URL

/**
 * Builds authentication headers only for the configured Tanweer API origin.
 * External media hosts must never receive the user's bearer token.
 */
internal fun buildMediaRequestHeaders(
    remoteUrl: String,
    apiBaseUrl: String,
    authToken: String?,
    groupId: String
): Map<String, String> {
    val target = URL(remoteUrl)
    val apiOrigin = URL(apiBaseUrl)
    val targetPort = if (target.port >= 0) target.port else target.defaultPort
    val apiPort = if (apiOrigin.port >= 0) apiOrigin.port else apiOrigin.defaultPort
    val isTanweerApiOrigin =
        target.protocol.equals(apiOrigin.protocol, ignoreCase = true) &&
            target.host.equals(apiOrigin.host, ignoreCase = true) &&
            targetPort == apiPort

    if (!isTanweerApiOrigin) return emptyMap()

    val token = authToken?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IOException("جلسة الدخول غير جاهزة. أعد فتح التطبيق أو سجّل الدخول ثم حاول مجدداً.")

    return buildMap {
        put("Authorization", "Bearer $token")
        if (groupId.isNotBlank()) put("X-Group-Id", groupId)
    }
}
