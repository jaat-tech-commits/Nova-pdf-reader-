package com.example.data.service

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Extracts real text from imported PDFs so AI, search and Study Mode use
 * the user's document instead of placeholder/sample content.
 */
class PdfTextExtractorService(context: Context) {

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    suspend fun extract(filePath: String): String = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) return@withContext ""

        try {
            PDDocument.load(file).use { document ->
                val pageCount = document.numberOfPages
                if (pageCount <= 0) return@withContext ""

                val stripper = PDFTextStripper().apply {
                    sortByPosition = true
                    addMoreFormatting = true
                }

                buildString {
                    for (page in 1..pageCount) {
                        stripper.startPage = page
                        stripper.endPage = page
                        val pageText = stripper.getText(document).trim()
                        if (pageText.isNotBlank()) {
                            append("[Page ").append(page).append("]\n")
                            append(pageText)
                            append("\n\n")
                        }
                    }
                }.trim()
            }
        } catch (_: Exception) {
            ""
        }
    }
}
