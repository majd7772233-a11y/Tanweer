package com.magd.tanweer.util

import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Robust on-device PDF text extractor powered by PdfBox-Android.
 * Extracts pure digital text layer per page, handling Arabic formatting and caching cleanly.
 */
object PdfTextExtractor {
    private const val TAG = "PdfTextExtractor"

    suspend fun extractPageText(pdfFile: File, pageIndex: Int): String = withContext(Dispatchers.IO) {
        try {
            if (!pdfFile.exists() || pdfFile.length() == 0L) {
                return@withContext ""
            }
            PDDocument.load(pdfFile).use { document ->
                val totalPages = document.numberOfPages
                if (pageIndex < 0 || pageIndex >= totalPages) {
                    return@withContext ""
                }
                val stripper = PDFTextStripper()
                // PDFBox pages are 1-based index
                stripper.startPage = pageIndex + 1
                stripper.endPage = pageIndex + 1
                val extracted = stripper.getText(document)
                extracted?.trim() ?: ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed extracting text from page $pageIndex of ${pdfFile.name}", e)
            ""
        }
    }
}
