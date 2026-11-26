package com.magd.tanweer.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudentNotebookManagerTest {
    private lateinit var context: Context
    private lateinit var notebookFile: File
    private lateinit var corruptBackup: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notebookFile = File(context.filesDir, "student_notebook_records.json")
        corruptBackup = File(context.filesDir, "student_notebook_records.json.corrupt")
        notebookFile.delete()
        File(notebookFile.path + ".bak").delete()
        corruptBackup.delete()
    }

    @Test
    fun `valid notebook records round trip through atomic storage`() {
        assertTrue(StudentNotebookManager.saveLessonRecord(context, record("lesson-1", setOf(0, 2))))
        assertTrue(StudentNotebookManager.saveLessonRecord(context, record("lesson-2")))
        val restored = StudentNotebookManager.getAllNotebookLessons(context)
        assertEquals(listOf("lesson-2", "lesson-1"), restored.map { it.contentId })
        assertEquals(setOf(0, 2), restored.last().completedPages)
        assertEquals("/tmp/board-1.jpg", restored.last().localImagePaths[0])
    }

    @Test
    fun `text-only lesson summary completion survives with zero image pages`() {
        val textOnly = record("lesson-text-only", setOf(0)).copy(
            totalPages = 0,
            localImagePaths = emptyMap()
        )
        assertTrue(StudentNotebookManager.saveLessonRecord(context, textOnly))
        val restored = StudentNotebookManager.getLessonRecord(context, "lesson-text-only")
        assertEquals(0, restored?.totalPages)
        assertEquals(setOf(0), restored?.completedPages)
    }

    @Test
    fun `corrupt notebook is backed up and cannot be overwritten`() {
        val original = """[{"contentId":"lesson-old","completedPages":[0],"totalPages":2},BROKEN]"""
        notebookFile.writeText(original)
        assertTrue(StudentNotebookManager.getAllNotebookLessons(context).isEmpty())
        assertTrue(corruptBackup.exists())
        assertEquals(original, corruptBackup.readText())
        assertFalse(StudentNotebookManager.saveLessonRecord(context, record("lesson-new")))
        assertEquals(original, notebookFile.readText())
        assertEquals(original, corruptBackup.readText())
    }

    @Test
    fun `empty existing file is damaged rather than an empty notebook`() {
        notebookFile.writeText("")
        assertFalse(StudentNotebookManager.saveLessonRecord(context, record("lesson-new")))
        assertTrue(corruptBackup.exists())
        assertEquals("", notebookFile.readText())
    }

    private fun record(id: String, completed: Set<Int> = emptySet()) =
        TranscribedLessonRecord(
            contentId = id, subjectId = "math", subjectName = "Mathematics",
            title = id, studyDate = "2026-11-15", totalPages = 3,
            completedPages = completed,
            localImagePaths = mapOf(0 to "/tmp/board-1.jpg"),
            transcribedAt = 1L
        )
}
