package com.magd.tanweer.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerWriteConfirmationTest {

    @Test
    fun onlyHttpSuccessWithApplicationSuccessIsConfirmed() {
        assertTrue(isConfirmedServerWrite(httpSuccessful = true, serverSuccess = true))
        assertFalse(isConfirmedServerWrite(httpSuccessful = true, serverSuccess = false))
        assertFalse(isConfirmedServerWrite(httpSuccessful = true, serverSuccess = null))
        assertFalse(isConfirmedServerWrite(httpSuccessful = false, serverSuccess = true))
        assertFalse(isConfirmedServerWrite(httpSuccessful = false, serverSuccess = false))
    }
}
