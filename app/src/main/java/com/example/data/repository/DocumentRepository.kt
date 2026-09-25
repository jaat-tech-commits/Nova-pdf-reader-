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
import com.example.data.service.PdfRendererService
import com.example.data.service.PdfToolService
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
    val geminiService = GeminiService()

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
        val existing = docDao.getAllNonVaultDocuments().firstOrNull()
        if (!existing.isNullOrEmpty()) return@withContext

        // 1. Generate Welcome Sample PDF
        val welcomeFile = PdfSampleGenerator.generateWelcomeSamplePdf(context)
        val welcomeDocId = docDao.insertDocument(
            DocumentEntity(
                title = "Welcome to NOVA PDF AI",
                filePath = welcomeFile.absolutePath,
                fileSize = welcomeFile.length(),
                pageCount = 3,
                lastPageRead = 1,
                isFavorite = true,
                folderName = "Getting Started",
                extractedText = "Welcome to NOVA PDF AI. Read. Understand. Create. In-depth AI Analysis, Clickable Page Citations, Full Annotation Suite, Voice Read Aloud, Interactive Study Mode, PDF Utilities. Privacy, Vault and Offline First."
            )
        )
        val thumbWelcome = pdfRendererService.generateThumbnail(welcomeFile.absolutePath, welcomeDocId)
        if (thumbWelcome != null) {
            docDao.updateDocument(docDao.getDocumentById(welcomeDocId)!!.copy(thumbnailPath = thumbWelcome))
        }

        // Add sample bookmarks for welcome doc
        bookmarkDao.insertBookmark(BookmarkEntity(documentId = welcomeDocId, pageNumber = 1, title = "Overview & Quick Guide"))
        bookmarkDao.insertBookmark(BookmarkEntity(documentId = welcomeDocId, pageNumber = 2, title = "Reading & Annotations"))
        bookmarkDao.insertBookmark(BookmarkEntity(documentId = welcomeDocId, pageNumber = 3, title = "Vault & Offline First"))

        // Add sample flashcards for welcome doc
        flashcardDao.insertFlashcard(FlashcardEntity(documentId = welcomeDocId, front = "What is NOVA PDF AI's primary philosophy?", back = "Read. Understand. Create. Seamlessly combines fast offline PDF reading with deep AI document assistance and tools."))
        flashcardDao.insertFlashcard(FlashcardEntity(documentId = welcomeDocId, front = "Do PDF reading and annotations require internet?", back = "No. Viewing, zooming, text search, bookmarks, and annotations work 100% offline."))

        // 2. Generate Genetics Sample PDF
        val geneticsFile = PdfSampleGenerator.generateGeneticsSamplePdf(context)
        val geneticsDocId = docDao.insertDocument(
            DocumentEntity(
                title = "Biology - Genetics & Heritability Notes",
                filePath = geneticsFile.absolutePath,
                fileSize = geneticsFile.length(),
                pageCount = 3,
                lastPageRead = 1,
                isFavorite = true,
                folderName = "Academic",
                extractedText = """
                    Chapter 1: Principles of Heritability.
                    Heritability is a statistic used in genetics that estimates the degree of variation in a phenotypic trait in a population that is due to genetic variation among individuals in that population.
                    Broad-Sense Heritability (H²): H² = V_G / V_P.
                    Narrow-Sense Heritability (h²): h² = V_A / V_P.
                    Formulas: V_P = V_G + V_E + V_GE. Selection Response: R = h² × S (Breeder's Equation).
                    Chapter 2: Heritability Estimates in Species.
                    Adult Height: 0.80. Blood Pressure: 0.40. Cattle Milk Yield: 0.30. Poultry Egg Weight: 0.50. Corn Kernel Weight: 0.55.
                    Chapter 3: Exam Revision & Practice MCQs.
                """.trimIndent()
            )
        )
        val thumbGenetics = pdfRendererService.generateThumbnail(geneticsFile.absolutePath, geneticsDocId)
        if (thumbGenetics != null) {
            docDao.updateDocument(docDao.getDocumentById(geneticsDocId)!!.copy(thumbnailPath = thumbGenetics))
        }

        // Add sample bookmarks for genetics doc
        bookmarkDao.insertBookmark(BookmarkEntity(documentId = geneticsDocId, pageNumber = 1, title = "Chapter 1: Heritability Principles & Formulas"))
        bookmarkDao.insertBookmark(BookmarkEntity(documentId = geneticsDocId, pageNumber = 2, title = "Chapter 2: Species Estimates Table"))
        bookmarkDao.insertBookmark(BookmarkEntity(documentId = geneticsDocId, pageNumber = 3, title = "Chapter 3: Practice MCQs & Revision"))

        // Add pre-seeded flashcards for genetics doc
        flashcardDao.insertFlashcard(FlashcardEntity(documentId = geneticsDocId, front = "What is Heritability?", back = "A statistic estimating the proportion of observed phenotypic variation in a population attributable to genetic differences."))
        flashcardDao.insertFlashcard(FlashcardEntity(documentId = geneticsDocId, front = "What is the formula for Narrow-Sense Heritability?", back = "h² = V_A / V_P (Additive genetic variance divided by total phenotypic variance)."))
        flashcardDao.insertFlashcard(FlashcardEntity(documentId = geneticsDocId, front = "What is the Breeder's Equation?", back = "R = h² × S (Response to selection equals narrow-sense heritability times selection differential)."))
        flashcardDao.insertFlashcard(FlashcardEntity(documentId = geneticsDocId, front = "What is the approximate heritability of human height?", back = "Approximately 0.80 (80%), indicating high genetic contribution under standard nutritional conditions."))

        // Add pre-seeded study quizzes for genetics doc
        studyQuizDao.insertQuiz(
            StudyQuizEntity(
                documentId = geneticsDocId,
                question = "If phenotypic variance V_P = 100 and environmental variance V_E = 40 (with zero GxE interaction), what is broad-sense heritability H²?",
                optionA = "0.40",
                optionB = "0.60",
                optionC = "0.25",
                optionD = "0.80",
                correctOptionIndex = 1,
                explanation = "V_G = V_P - V_E = 100 - 40 = 60. H² = V_G / V_P = 60 / 100 = 0.60.",
                pageReference = 1
            )
        )
        studyQuizDao.insertQuiz(
            StudyQuizEntity(
                documentId = geneticsDocId,
                question = "Which component of variance determines the response of a population to artificial or natural selection?",
                optionA = "Dominance variance (V_D)",
                optionB = "Environmental variance (V_E)",
                optionC = "Additive genetic variance (V_A)",
                optionD = "Epistatic interaction variance (V_I)",
                correctOptionIndex = 2,
                explanation = "Narrow-sense heritability h² = V_A / V_P dictates the response to selection (R = h² × S).",
                pageReference = 1
            )
        )
        studyQuizDao.insertQuiz(
            StudyQuizEntity(
                documentId = geneticsDocId,
                question = "True or False: A trait with high heritability (e.g. 0.85) cannot be improved or modified by environmental changes.",
                optionA = "True, high heritability means traits are strictly fixed",
                optionB = "False, changing the environment (e.g. nutrition) can alter population averages",
                optionC = "True, only gene editing can alter it",
                optionD = "False, but only if heritability is below 0.10",
                correctOptionIndex = 1,
                explanation = "High heritability only measures variance in the current environment; altering environmental factors can shift average trait values significantly.",
                pageReference = 2
            )
        )

        // Seed initial folders
        folderDao.insertFolder(FolderEntity(name = "Getting Started"))
        folderDao.insertFolder(FolderEntity(name = "Academic"))
        folderDao.insertFolder(FolderEntity(name = "Work & Research"))
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
            extractedText = "Imported Document: $displayName ($pageCount pages)."
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
            extractedText = "Document $title created with NOVA PDF AI ($pageCount pages)."
        )
        val id = docDao.insertDocument(doc)
        val thumbPath = pdfRendererService.generateThumbnail(file.absolutePath, id)
        val updated = doc.copy(id = id, thumbnailPath = thumbPath)
        docDao.updateDocument(updated)
        updated
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

    suspend fun addFolder(name: String): Long = withContext(Dispatchers.IO) {
        folderDao.insertFolder(FolderEntity(name = name))
    }
}
