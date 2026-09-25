package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.AiChatMessageEntity
import com.example.data.local.AnnotationEntity
import com.example.data.local.AppDatabase
import com.example.data.local.BookmarkEntity
import com.example.data.local.DocumentEntity
import com.example.data.local.FlashcardEntity
import com.example.data.local.FolderEntity
import com.example.data.local.StudyQuizEntity
import com.example.data.service.GeminiService
import com.example.data.service.OcrService
import com.example.data.service.PdfRendererService
import com.example.data.service.PdfToolService
import com.example.data.service.PdfTextExtractorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File

class DocumentRepository(
    private val database: AppDatabase,
    private val context: Context
) {
    val pdfRendererService = PdfRendererService(context)
    val pdfToolService = PdfToolService(context)
    val pdfTextExtractorService = PdfTextExtractorService(context)
    val geminiService = GeminiService(context)
    val ocrService = OcrService(context)

    private val docDao = database.documentDao()
    private val bookmarkDao = database.bookmarkDao()
    private val annotationDao = database.annotationDao()
    private val aiChatDao = database.aiChatDao()
    private val flashcardDao = database.flashcardDao()
    private val studyQuizDao = database.studyQuizDao()
    private val folderDao = database.folderDao()

    val allDocuments: Flow<List<DocumentEntity>> = docDao.getAllNonVaultDocuments()
    val recentDocuments: Flow<List<DocumentEntity>> = docDao.getRecentDocuments()
    val favoriteDocuments: Flow<List<DocumentEntity>> = docDao.getFavoriteDocuments()
    val vaultDocuments: Flow<List<DocumentEntity>> = docDao.getVaultDocuments()
    val folders: Flow<List<FolderEntity>> = folderDao.getAllFolders()

    suspend fun initializeDefaultDocumentsIfEmpty() = withContext(Dispatchers.IO) {
        // Production builds start with an empty library. User documents are the source
        // of truth; NOVA never injects demo PDFs, demo quizzes or demo flashcards.
    }

    suspend fun removeBundledSampleDocuments() = withContext(Dispatchers.IO) {
        val bundledTitles = setOf(
            "Welcome to NOVA PDF AI",
            "Biology - Genetics & Heritability Notes"
        )
        val docs = docDao.getAllNonVaultDocuments().firstOrNull().orEmpty()
        docs.filter { it.title in bundledTitles }.forEach { doc ->
            try { File(doc.filePath).delete() } catch (_: Exception) {}
            doc.thumbnailPath?.let { path -> try { File(path).delete() } catch (_: Exception) {} }
            flashcardDao.clearFlashcardsForDocument(doc.id)
            studyQuizDao.clearQuizzesForDocument(doc.id)
            bookmarkDao.getBookmarksForDocument(doc.id).firstOrNull().orEmpty().forEach { bookmark -> bookmarkDao.deleteBookmarkById(bookmark.id) }
            aiChatDao.clearHistoryForDocument(doc.id)
            docDao.deleteDocumentById(doc.id)
        }
    }

    suspend fun getDocumentById(id: Long): DocumentEntity? = docDao.getDocumentById(id)
    fun getDocumentByIdFlow(id: Long): Flow<DocumentEntity?> = docDao.getDocumentByIdFlow(id)

    suspend fun importDocument(uri: Uri, displayName: String): DocumentEntity = withContext(Dispatchers.IO) {
        val copiedFile = pdfRendererService.copyUriToLocalStorage(uri, displayName)
        val pageCount = pdfRendererService.getPageCount(copiedFile.absolutePath)
        val doc = DocumentEntity(
            title = displayName.removeSuffix(".pdf"),
            filePath = copiedFile.absolutePath,
            uriString = uri.toString(),
            fileSize = copiedFile.length(),
            pageCount = pageCount.coerceAtLeast(1),
            lastPageRead = 1,
            extractedText = pdfTextExtractorService.extract(copiedFile.absolutePath).ifBlank { "Imported PDF: $displayName ($pageCount pages). No selectable text was found; this may be a scanned/image-only PDF." }
        )
        val id = docDao.insertDocument(doc)
        val thumbPath = pdfRendererService.generateThumbnail(copiedFile.absolutePath, id)
        val updated = doc.copy(id = id, thumbnailPath = thumbPath)
        docDao.updateDocument(updated)
        updated
    }

    suspend fun registerGeneratedPdf(file: File, title: String): DocumentEntity = withContext(Dispatchers.IO) {
        val pageCount = pdfRendererService.getPageCount(file.absolutePath)
        val doc = DocumentEntity(
            title = title,
            filePath = file.absolutePath,
            fileSize = file.length(),
            pageCount = pageCount.coerceAtLeast(1),
            lastPageRead = 1,
            extractedText = pdfTextExtractorService.extract(file.absolutePath).ifBlank { "Document $title created with NOVA PDF AI ($pageCount pages)." }
        )
        val id = docDao.insertDocument(doc)
        val thumbPath = pdfRendererService.generateThumbnail(file.absolutePath, id)
        val updated = doc.copy(id = id, thumbnailPath = thumbPath)
        docDao.updateDocument(updated)
        updated
    }

    /**
     * OCR the requested page. This is the fallback for scanned/image-only PDFs
     * where PDFBox cannot extract a selectable text layer.
     */
    suspend fun ocrPage(docId: Long, pageNumber: Int): String? = withContext(Dispatchers.IO) {
        val doc = docDao.getDocumentById(docId) ?: return@withContext null
        val pageIndex = (pageNumber - 1).coerceIn(0, doc.pageCount.coerceAtLeast(1) - 1)
        val bitmap = pdfRendererService.renderPage(
            filePath = doc.filePath,
            pageIndex = pageIndex,
            destWidth = 1800,
            backgroundColor = android.graphics.Color.WHITE
        ) ?: return@withContext null
        ocrService.recognize(bitmap).trim().ifBlank { null }
    }

    /**
     * OCR all pages only when the document does not already have real extracted text.
     * The result is stored in the existing extractedText field with [Page N] markers,
     * so Study Mode, search and AI can use the same source of truth.
     */
    suspend fun ensureOcrTextIfNeeded(docId: Long): Boolean = withContext(Dispatchers.IO) {
        val doc = docDao.getDocumentById(docId) ?: return@withContext false
        val existing = doc.extractedText.orEmpty().trim()
        if (existing.length >= 80 && !existing.startsWith("Imported PDF:")) {
            return@withContext true
        }

        val pages = doc.pageCount.coerceAtLeast(1)
        val output = StringBuilder()

        for (page in 1..pages) {
            val bitmap = pdfRendererService.renderPage(
                filePath = doc.filePath,
                pageIndex = page - 1,
                destWidth = 1800,
                backgroundColor = android.graphics.Color.WHITE
            ) ?: continue

            val pageText = try {
                ocrService.recognize(bitmap).trim()
            } catch (_: Exception) {
                ""
            }

            if (pageText.isNotBlank()) {
                output.append("[Page ").append(page).append("]\n")
                output.append(pageText).append("\n\n")
            }
        }

        val text = output.toString().trim()
        if (text.length >= 20) {
            docDao.updateExtractedText(docId, text)
            true
        } else {
            false
        }
    }

    suspend fun updateReadingProgress(docId: Long, page: Int) = withContext(Dispatchers.IO) {
        docDao.updateReadingProgress(docId, page)
    }

    suspend fun toggleFavorite(docId: Long, isFav: Boolean) = withContext(Dispatchers.IO) {
        docDao.setFavorite(docId, isFav)
    }

    suspend fun toggleVault(docId: Long, isVault: Boolean) = withContext(Dispatchers.IO) {
        docDao.setVault(docId, isVault)
    }

    suspend fun renameDocument(docId: Long, newTitle: String) = withContext(Dispatchers.IO) {
        docDao.renameDocument(docId, newTitle)
    }

    suspend fun deleteDocument(docId: Long) = withContext(Dispatchers.IO) {
        val doc = docDao.getDocumentById(docId)
        if (doc != null) {
            try { File(doc.filePath).delete() } catch (_: Exception) {}
            if (doc.thumbnailPath != null) {
                try { File(doc.thumbnailPath).delete() } catch (_: Exception) {}
            }
            docDao.deleteDocumentById(docId)
        }
    }

    fun getBookmarks(docId: Long): Flow<List<BookmarkEntity>> = bookmarkDao.getBookmarksForDocument(docId)

    suspend fun addBookmark(docId: Long, pageNumber: Int, title: String): Long = withContext(Dispatchers.IO) {
        bookmarkDao.insertBookmark(BookmarkEntity(documentId = docId, pageNumber = pageNumber, title = title))
    }

    suspend fun deleteBookmark(bookmarkId: Long) = withContext(Dispatchers.IO) {
        bookmarkDao.deleteBookmarkById(bookmarkId)
    }

    fun getAnnotations(docId: Long): Flow<List<AnnotationEntity>> = annotationDao.getAnnotationsForDocument(docId)
    fun getAnnotationsForPage(docId: Long, page: Int): Flow<List<AnnotationEntity>> = annotationDao.getAnnotationsForPage(docId, page)

    suspend fun addAnnotation(annotation: AnnotationEntity): Long = withContext(Dispatchers.IO) {
        annotationDao.insertAnnotation(annotation)
    }

    suspend fun deleteAnnotation(id: Long) = withContext(Dispatchers.IO) {
        annotationDao.deleteAnnotationById(id)
    }

    suspend fun clearAnnotationsForPage(docId: Long, page: Int) = withContext(Dispatchers.IO) {
        annotationDao.clearAnnotationsForPage(docId, page)
    }

    fun getAiChatMessages(docId: Long): Flow<List<AiChatMessageEntity>> = aiChatDao.getMessagesForDocument(docId)

    suspend fun sendAiMessage(docId: Long, userMessage: String, pageContext: Int? = null): String = withContext(Dispatchers.IO) {
        val doc = docDao.getDocumentById(docId) ?: return@withContext "Document not found."

        // Save user message
        aiChatDao.insertMessage(
            AiChatMessageEntity(
                documentId = docId,
                role = "user",
                content = userMessage
            )
        )

        // Get past messages for context
        val pastList = aiChatDao.getMessagesForDocument(docId).firstOrNull() ?: emptyList()
        val historyPairs = pastList.map { Pair(it.role, it.content) }

        val answer = geminiService.askDocument(
            question = userMessage,
            documentTitle = doc.title,
            documentText = doc.extractedText ?: doc.title,
            pageContext = pageContext,
            history = historyPairs
        )

        // Extract citations e.g. [Page 2]
        val regex = Regex("\\[Page (\\d+)\\]")
        val citations = regex.findAll(answer).map { it.groupValues[1].toInt() }.distinct().toList()
        val citationsJson = org.json.JSONArray(citations).toString()

        aiChatDao.insertMessage(
            AiChatMessageEntity(
                documentId = docId,
                role = "assistant",
                content = answer,
                citationsJson = citationsJson
            )
        )

        answer
    }

    suspend fun clearAiChatHistory(docId: Long) = withContext(Dispatchers.IO) {
        aiChatDao.clearHistoryForDocument(docId)
    }

    fun getFlashcards(docId: Long): Flow<List<FlashcardEntity>> = flashcardDao.getFlashcardsForDocument(docId)

    suspend fun updateFlashcardKnown(id: Long, isKnown: Boolean) = withContext(Dispatchers.IO) {
        val cards = flashcardDao.getFlashcardsForDocument(0).firstOrNull() // query or update directly
        // Update flashcard status
    }

    suspend fun addFlashcard(card: FlashcardEntity): Long = withContext(Dispatchers.IO) {
        flashcardDao.insertFlashcard(card)
    }

    suspend fun updateFlashcard(card: FlashcardEntity) = withContext(Dispatchers.IO) {
        flashcardDao.updateFlashcard(card)
    }

    suspend fun deleteFlashcard(id: Long) = withContext(Dispatchers.IO) {
        flashcardDao.deleteFlashcardById(id)
    }

    fun getStudyQuizzes(docId: Long): Flow<List<StudyQuizEntity>> = studyQuizDao.getQuizzesForDocument(docId)

    suspend fun addQuiz(quiz: StudyQuizEntity): Long = withContext(Dispatchers.IO) {
        studyQuizDao.insertQuiz(quiz)
    }

    suspend fun generateStudyPackIfNeeded(docId: Long): String? = withContext(Dispatchers.IO) {
        val doc = docDao.getDocumentById(docId) ?: return@withContext "Document not found."
        val existingQuizzes = studyQuizDao.getQuizzesForDocument(docId).firstOrNull().orEmpty()
        val existingCards = flashcardDao.getFlashcardsForDocument(docId).firstOrNull().orEmpty()
        if (existingQuizzes.isNotEmpty() && existingCards.isNotEmpty()) return@withContext null

        val sourceText = doc.extractedText.orEmpty()
        if (sourceText.length < 80 || sourceText.startsWith("Imported PDF:")) {
            return@withContext "This PDF has no selectable text. It may be scanned/image-only. Use the OCR/AI tools on the PDF pages first, then regenerate Study Mode."
        }

        val prompt = """
            Create a study pack ONLY from the document content below.
            Do not use any unrelated example, demo, or memorized sample content.
            Return ONLY valid JSON:
            {
              "quizzes": [
                {
                  "question": "question",
                  "optionA": "option",
                  "optionB": "option",
                  "optionC": "option",
                  "optionD": "option",
                  "correctOptionIndex": 0,
                  "explanation": "short explanation",
                  "pageReference": 1
                }
              ],
              "flashcards": [
                {
                  "front": "question or term",
                  "back": "answer",
                  "pageReference": 1
                }
              ]
            }
            Generate 6 high-quality MCQs and 6 useful flashcards.
            Use only facts actually present in the document. Keep pageReference tied to [Page N] markers.
            
            DOCUMENT:
            ${sourceText.take(15000)}
        """.trimIndent()

        val raw = geminiService.askDocument(
            question = prompt,
            documentTitle = doc.title,
            documentText = sourceText
        ).trim()

        if (raw.startsWith("Gemini ")) return@withContext raw

        try {
            val clean = raw.replace('\u0060', ' ').trim()
            val root = org.json.JSONObject(clean)

            if (existingQuizzes.isEmpty()) {
                studyQuizDao.clearQuizzesForDocument(docId)
                val quizzes = root.optJSONArray("quizzes")
                if (quizzes != null) {
                    for (i in 0 until quizzes.length()) {
                        val q = quizzes.optJSONObject(i) ?: continue
                        val correct = q.optInt("correctOptionIndex", -1)
                        if (correct !in 0..3) continue
                        studyQuizDao.insertQuiz(
                            StudyQuizEntity(
                                documentId = docId,
                                question = q.optString("question").trim(),
                                optionA = q.optString("optionA").trim(),
                                optionB = q.optString("optionB").trim(),
                                optionC = q.optString("optionC").trim(),
                                optionD = q.optString("optionD").trim(),
                                correctOptionIndex = correct,
                                explanation = q.optString("explanation").trim(),
                                pageReference = q.optInt("pageReference", 1).coerceIn(1, doc.pageCount)
                            )
                        )
                    }
                }
            }

            if (existingCards.isEmpty()) {
                flashcardDao.clearFlashcardsForDocument(docId)
                val cards = root.optJSONArray("flashcards")
                if (cards != null) {
                    for (i in 0 until cards.length()) {
                        val card = cards.optJSONObject(i) ?: continue
                        val front = card.optString("front").trim()
                        val back = card.optString("back").trim()
                        if (front.isBlank() || back.isBlank()) continue
                        flashcardDao.insertFlashcard(
                            FlashcardEntity(
                                documentId = docId,
                                front = front,
                                back = back,
                                isKnown = false
                            )
                        )
                    }
                }
            }
            null
        } catch (_: Exception) {
            "Study content could not be generated from this PDF. Please try again."
        }
    }

    suspend fun regenerateStudyPack(docId: Long): String? = withContext(Dispatchers.IO) {
        flashcardDao.clearFlashcardsForDocument(docId)
        studyQuizDao.clearQuizzesForDocument(docId)
        generateStudyPackIfNeeded(docId)
    }

    suspend fun addFolder(name: String): Long = withContext(Dispatchers.IO) {
        folderDao.insertFolder(FolderEntity(name = name))
    }
}
