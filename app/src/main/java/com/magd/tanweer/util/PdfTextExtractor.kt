package com.magd.tanweer.util

import android.content.Context
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts the actual digital text layer of one PDF page.
 *
 * PdfBox-Android requires its Android resource loader to be initialized before
 * PDFBox APIs are used. Initialization is process-wide, guarded and always uses
 * the application's context to avoid retaining an Activity.
 *
 * Image-only/scanned pages intentionally return an empty string; this class does
 * not pretend that PDF text extraction is OCR.
 */
object PdfTextExtractor {
    private const val TAG = "PdfTextExtractor"

    @Volatile
    private var isInitialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (isInitialized) return

        PDFBoxResourceLoader.init(context.applicationContext)
        isInitialized = true
    }

    suspend fun extractPageText(
        context: Context,
        pdfFile: File,
        pageIndex: Int
    ): String = withContext(Dispatchers.IO) {
        try {
            if (!pdfFile.exists() || pdfFile.length() == 0L || pageIndex < 0) {
                return@withContext ""
            }

            initialize(context)

            PDDocument.load(pdfFile).use { document ->
                if (pageIndex >= document.numberOfPages) {
                    return@withContext ""
                }

                val stripper = PDFTextStripper().apply {
                    // PDF pages are 1-based. Position sorting improves reading
                    // order in PDFs whose text objects were written out of order.
                    startPage = pageIndex + 1
                    endPage = pageIndex + 1
                    sortByPosition = true
                    lineSeparator = "\n"
                    wordSeparator = " "
                }

                stripper.getText(document)
                    .replace('\u0000'.toString(), "")
                    .replace('\u000C'.toString(), "\n")
                    .replace('\u00A0', ' ')
                    .trim()
            }
        } catch (error: Exception) {
            Log.e(TAG, "Failed extracting text from page $pageIndex of ${pdfFile.name}", error)
            ""
        }
    }
}
