package com.magd.tanweer.util

/**
 * HTTP 2xx means the transport completed; only the application-level success
 * flag confirms that the server accepted and persisted a mutation.
 */
internal fun isConfirmedServerWrite(
    httpSuccessful: Boolean,
    serverSuccess: Boolean?
): Boolean = httpSuccessful && serverSuccess == true
