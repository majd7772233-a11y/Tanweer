package com.magd.tanweer.util

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImageProcessingDraftCleanupTest {

    @Test
    fun deletesOnlyManagedPendingUploadFiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pendingDirectory = File(context.filesDir, "pending_uploads").apply { mkdirs() }
        val managedPage = File(pendingDirectory, "page_unsent_1234abcd.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val unrelatedFile = File(context.filesDir, "page_unrelated.jpg").apply {
            writeBytes(byteArrayOf(4, 5, 6))
        }
        val nestedFile = File(File(pendingDirectory, "nested").apply { mkdirs() }, "page_nested.jpg").apply {
            writeBytes(byteArrayOf(7, 8, 9))
        }
        val unsupportedExtension = File(pendingDirectory, "page_unsent.png").apply {
            writeBytes(byteArrayOf(10, 11, 12))
        }

        ImageProcessingUtils.deleteProcessedDraftFile(context, managedPage.absolutePath)
        ImageProcessingUtils.deleteProcessedDraftFile(context, unrelatedFile.absolutePath)
        ImageProcessingUtils.deleteProcessedDraftFile(context, nestedFile.absolutePath)
        ImageProcessingUtils.deleteProcessedDraftFile(context, unsupportedExtension.absolutePath)

        assertFalse(managedPage.exists())
        assertTrue(unrelatedFile.exists())
        assertTrue(nestedFile.exists())
        assertTrue(unsupportedExtension.exists())
    }

    @Test
    fun deleteDraftPageRemovesItsPrivateSourceAndProcessedOutput() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourceDirectory = File(context.filesDir, "scan_draft_sources").apply { mkdirs() }
        val sourceFile = File(sourceDirectory, "source_test-page.source").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val processedDirectory = File(context.filesDir, "pending_uploads").apply { mkdirs() }
        val processedFile = File(processedDirectory, "page_test_5678abcd.jpg").apply {
            writeBytes(byteArrayOf(4, 5, 6))
        }

        ImageProcessingUtils.deleteDraftPage(
            context = context,
            sourceUri = Uri.fromFile(sourceFile),
            processedFilePath = processedFile.absolutePath
        )

        assertFalse(sourceFile.exists())
        assertFalse(processedFile.exists())
    }
}
