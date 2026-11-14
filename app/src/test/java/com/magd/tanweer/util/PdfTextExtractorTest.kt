package com.magd.tanweer.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfTextExtractorTest {

    @Test
    fun extractsDigitalTextAfterInitializingPdfBoxResources() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PdfTextExtractor.initialize(context)

        val pdfFile = File.createTempFile("tanweer-text-", ".pdf", context.cacheDir)
        try {
            PDDocument().use { document ->
                val page = PDPage()
                document.addPage(page)

                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(48f, 720f)
                    stream.showText("Tanweer PDF text extraction")
                    stream.endText()
                }

                document.save(pdfFile)
            }

            val extracted = PdfTextExtractor.extractPageText(context, pdfFile, 0)

            assertTrue(
                "The page's embedded text should be extracted after PDFBox initialization.",
                extracted.contains("Tanweer PDF text extraction")
            )
        } finally {
            pdfFile.delete()
        }
    }

    @Test
    fun returnsEmptyTextForAnOutOfRangePage() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PdfTextExtractor.initialize(context)

        val pdfFile = File.createTempFile("tanweer-empty-", ".pdf", context.cacheDir)
        try {
            PDDocument().use { document ->
                document.addPage(PDPage())
                document.save(pdfFile)
            }

            assertEquals("", PdfTextExtractor.extractPageText(context, pdfFile, 3))
        } finally {
            pdfFile.delete()
        }
    }
}
