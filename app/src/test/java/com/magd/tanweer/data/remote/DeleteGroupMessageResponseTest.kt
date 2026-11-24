package com.magd.tanweer.data.remote

import com.squareup.moshi.Moshi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteGroupMessageResponseTest {
    private val adapter = Moshi.Builder().build().adapter(DeleteGroupMessageResponse::class.java)

    @Test
    fun confirmsOnlyTheExactDeletedGroupAndMessage() {
        val response = adapter.fromJson(
            """
            {
              "success": true,
              "groupId": "class_10_A",
              "messageId": "msg_12345",
              "message": "تم حذف الرسالة بنجاح"
            }
            """.trimIndent()
        )

        assertNotNull(response)
        assertTrue(response!!.confirmsDeletion("msg_12345", "class_10_A"))
        assertFalse(response.confirmsDeletion("different-message", "class_10_A"))
        assertFalse(response.confirmsDeletion("msg_12345", "class_10_B"))
    }

    @Test
    fun doesNotConfirmMissingOrFailedAcknowledgement() {
        val missing = adapter.fromJson("""{"success": true}""")
        val failed = adapter.fromJson(
            """
            {
              "success": false,
              "groupId": "class_10_A",
              "messageId": "msg_12345"
            }
            """.trimIndent()
        )

        assertFalse(missing!!.confirmsDeletion("msg_12345", "class_10_A"))
        assertFalse(failed!!.confirmsDeletion("msg_12345", "class_10_A"))
    }
}
