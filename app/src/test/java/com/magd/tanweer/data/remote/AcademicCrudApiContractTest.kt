package com.magd.tanweer.data.remote

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
        assertEquals(expectedPath, annotation?.value)
    }

    private fun assertDeleteEndpoint(methodName: String, expectedPath: String) {
        val method = TanweerApiService::class.java.methods.first { it.name == methodName }
        val annotation = method.getAnnotation(DELETE::class.java)
        assertNotNull("$methodName must declare DELETE", annotation)
        assertEquals(expectedPath, annotation?.value)
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

    private fun assertJsonField(json: String, field: String, expectedValue: String) {
        assertTrue(
            "Expected JSON field \"$field\" with value $expectedValue, actual: $json",
            json.contains("\"$field\":$expectedValue")
        )
    }

    @Test
    fun updateRequestDtosUseServerCamelCaseFieldNames() {
        val moshi = com.squareup.moshi.Moshi.Builder()
            .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()

        val content = moshi.adapter(UpdateContentRequest::class.java)
            .toJson(UpdateContentRequest(title = "عنوان", description = "وصف", type = "LESSON"))
        assertJsonField(content, "title", "\"عنوان\"")
        assertJsonField(content, "description", "\"وصف\"")
        assertJsonField(content, "type", "\"LESSON\"")

        val homework = moshi.adapter(UpdateHomeworkRequest::class.java)
            .toJson(UpdateHomeworkRequest(dueDate = "2026-11-03", pageNumbers = "10-15", isOfficial = true))
        assertJsonField(homework, "dueDate", "\"2026-11-03\"")
        assertJsonField(homework, "pageNumbers", "\"10-15\"")
        assertJsonField(homework, "isOfficial", "true")

        val exam = moshi.adapter(UpdateExamRequest::class.java)
            .toJson(UpdateExamRequest(examDate = "2026-11-04", examType = "MIDTERM"))
        assertJsonField(exam, "examDate", "\"2026-11-04\"")
        assertJsonField(exam, "examType", "\"MIDTERM\"")

        val event = moshi.adapter(UpdateEventRequest::class.java)
            .toJson(UpdateEventRequest(eventDate = "2026-11-05", timeStr = "09:30", isOfficial = false))
        assertJsonField(event, "eventDate", "\"2026-11-05\"")
        assertJsonField(event, "timeStr", "\"09:30\"")
        assertJsonField(event, "isOfficial", "false")
    }
}
