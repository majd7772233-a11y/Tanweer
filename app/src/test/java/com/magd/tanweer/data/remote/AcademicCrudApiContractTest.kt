package com.magd.tanweer.data.remote

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.DELETE
import retrofit2.http.PATCH

class AcademicCrudApiContractTest {

    private fun assertPatchEndpoint(methodName: String, expectedPath: String) {
        val method = TanweerApiService::class.java.methods.first { it.name == methodName }
        val annotation = method.getAnnotation(PATCH::class.java)
        assertNotNull("$methodName must declare PATCH", annotation)
        assertEquals(expectedPath, annotation.value)
    }

    private fun assertDeleteEndpoint(methodName: String, expectedPath: String) {
        val method = TanweerApiService::class.java.methods.first { it.name == methodName }
        val annotation = method.getAnnotation(DELETE::class.java)
        assertNotNull("$methodName must declare DELETE", annotation)
        assertEquals(expectedPath, annotation.value)
    }

    @Test
    fun academicUpdateAndDeleteMethodsMatchServerRoutes() {
        assertPatchEndpoint("updateContent", "api/v1/content/{id}")
        assertDeleteEndpoint("deleteContent", "api/v1/content/{id}")

        assertPatchEndpoint("updateHomework", "api/v1/homeworks/{id}")
        assertDeleteEndpoint("deleteHomework", "api/v1/homeworks/{id}")

        assertPatchEndpoint("updateExam", "api/v1/exams/{id}")
        assertDeleteEndpoint("deleteExam", "api/v1/exams/{id}")

        assertPatchEndpoint("updateEvent", "api/v1/events/{id}")
        assertDeleteEndpoint("deleteEvent", "api/v1/events/{id}")
    }

    @Test
    fun updateRequestDtosUseServerCamelCaseFieldNames() {
        val moshi = com.squareup.moshi.Moshi.Builder()
            .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()

        val content = JSONObject(
            moshi.adapter(UpdateContentRequest::class.java)
                .toJson(UpdateContentRequest(title = "عنوان", description = "وصف", type = "LESSON"))
        )
        assertEquals("عنوان", content.getString("title"))
        assertEquals("وصف", content.getString("description"))
        assertEquals("LESSON", content.getString("type"))

        val homework = JSONObject(
            moshi.adapter(UpdateHomeworkRequest::class.java)
                .toJson(UpdateHomeworkRequest(dueDate = "2026-11-03", pageNumbers = "10-15", isOfficial = true))
        )
        assertEquals("2026-11-03", homework.getString("dueDate"))
        assertEquals("10-15", homework.getString("pageNumbers"))
        assertTrue(homework.getBoolean("isOfficial"))

        val exam = JSONObject(
            moshi.adapter(UpdateExamRequest::class.java)
                .toJson(UpdateExamRequest(examDate = "2026-11-04", examType = "MIDTERM"))
        )
        assertEquals("2026-11-04", exam.getString("examDate"))
        assertEquals("MIDTERM", exam.getString("examType"))

        val event = JSONObject(
            moshi.adapter(UpdateEventRequest::class.java)
                .toJson(UpdateEventRequest(eventDate = "2026-11-05", timeStr = "09:30", isOfficial = false))
        )
        assertEquals("2026-11-05", event.getString("eventDate"))
        assertEquals("09:30", event.getString("timeStr"))
        assertEquals(false, event.getBoolean("isOfficial"))
    }
}
