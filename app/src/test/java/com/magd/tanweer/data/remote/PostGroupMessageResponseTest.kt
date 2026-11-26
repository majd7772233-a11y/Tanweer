package com.magd.tanweer.data.remote

import com.squareup.moshi.Moshi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostGroupMessageResponseTest {
    private val adapter = Moshi.Builder().build().adapter(PostGroupMessageResponse::class.java)

    @Test
    fun parsesWorkerObjectAcknowledgementAndConfirmsPersistedMessage() {
        val response = adapter.fromJson(
            """
            {
              "success": true,
              "message": {
                "id": "msg_12345",
                "groupId": "class_10_A",
                "senderId": "usr_1",
                "senderName": "Teacher",
                "senderGradeSection": "Grade 10 - A",
                "text": "Biology reading",
                "timestamp": 1795280400000,
                "status": "SENT"
              }
            }
            """.trimIndent()
        )

        assertNotNull(response)
        assertTrue(response!!.confirmsMessage("msg_12345", "class_10_A", "Biology reading"))
        assertFalse(response.confirmsMessage("another_id", "class_10_A", "Biology reading"))
        assertFalse(response.confirmsMessage("msg_12345", "class_10_B", "Biology reading"))
        assertFalse(response.confirmsMessage("msg_12345", "class_10_A", "Different message"))
    }

    @Test
    fun doesNotConfirmMissingOrFailedMessageAcknowledgement() {
        val missing = adapter.fromJson("""{"success":true}""")
        val failed = adapter.fromJson(
            """
            {
              "success": false,
              "message": {
                "id": "msg_12345",
                "groupId": "class_10_A",
                "senderId": "usr_1",
                "senderName": "Teacher",
                "senderGradeSection": "Grade 10 - A",
                "text": "Biology reading",
                "status": "FAILED"
              }
            }
            """.trimIndent()
        )

        assertFalse(missing!!.confirmsMessage("msg_12345", "class_10_A", "Biology reading"))
        assertFalse(failed!!.confirmsMessage("msg_12345", "class_10_A", "Biology reading"))
    }
}