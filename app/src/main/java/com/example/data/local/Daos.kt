package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE isVault = 0 ORDER BY lastOpenedTimestamp DESC")
    fun getAllNonVaultDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isVault = 0 ORDER BY lastOpenedTimestamp DESC LIMIT 10")
    fun getRecentDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isVault = 0 AND isFavorite = 1 ORDER BY lastOpenedTimestamp DESC")
    fun getFavoriteDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isVault = 1 ORDER BY lastOpenedTimestamp DESC")
    fun getVaultDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    fun getDocumentByIdFlow(id: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE filePath = :path LIMIT 1")
    suspend fun getDocumentByPath(path: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    @Query("UPDATE documents SET lastPageRead = :page, lastOpenedTimestamp = :timestamp WHERE id = :id")
    suspend fun updateReadingProgress(id: Long, page: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET isFavorite = :isFav WHERE id = :id")
    suspend fun setFavorite(id: Long, isFav: Boolean)

    @Query("UPDATE documents SET isVault = :isVault WHERE id = :id")
    suspend fun setVault(id: Long, isVault: Boolean)

    @Query("UPDATE documents SET title = :newTitle WHERE id = :id")
    suspend fun renameDocument(id: Long, newTitle: String)

    @Query("UPDATE documents SET extractedText = :text WHERE id = :id")
    suspend fun updateExtractedText(id: Long, text: String)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE documentId = :docId ORDER BY pageNumber ASC")
    fun getBookmarksForDocument(docId: Long): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Long)

    @Query("UPDATE bookmarks SET title = :title WHERE id = :id")
    suspend fun updateBookmarkTitle(id: Long, title: String)
}

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE documentId = :docId ORDER BY createdAt ASC")
    fun getAnnotationsForDocument(docId: Long): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE documentId = :docId AND pageNumber = :page ORDER BY createdAt ASC")
    fun getAnnotationsForPage(docId: Long, page: Int): Flow<List<AnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: AnnotationEntity): Long

    @Delete
    suspend fun deleteAnnotation(annotation: AnnotationEntity)

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun deleteAnnotationById(id: Long)

    @Query("DELETE FROM annotations WHERE documentId = :docId AND pageNumber = :page")
    suspend fun clearAnnotationsForPage(docId: Long, page: Int)
}

@Dao
interface AiChatDao {
    @Query("SELECT * FROM ai_messages WHERE documentId = :docId ORDER BY timestamp ASC")
    fun getMessagesForDocument(docId: Long): Flow<List<AiChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AiChatMessageEntity): Long

    @Query("DELETE FROM ai_messages WHERE documentId = :docId")
    suspend fun clearHistoryForDocument(docId: Long)
}

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcards WHERE documentId = :docId ORDER BY id ASC")
    fun getFlashcardsForDocument(docId: Long): Flow<List<FlashcardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcard(flashcard: FlashcardEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(flashcards: List<FlashcardEntity>)

    @Update
    suspend fun updateFlashcard(flashcard: FlashcardEntity)

    @Query("DELETE FROM flashcards WHERE id = :id")
    suspend fun deleteFlashcardById(id: Long)

    @Query("DELETE FROM flashcards WHERE documentId = :docId")
    suspend fun clearFlashcardsForDocument(docId: Long)
}

@Dao
interface StudyQuizDao {
    @Query("SELECT * FROM study_quizzes WHERE documentId = :docId ORDER BY id ASC")
    fun getQuizzesForDocument(docId: Long): Flow<List<StudyQuizEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuiz(quiz: StudyQuizEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(quizzes: List<StudyQuizEntity>)

    @Query("DELETE FROM study_quizzes WHERE documentId = :docId")
    suspend fun clearQuizzesForDocument(docId: Long)
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity): Long

    @Delete
    suspend fun deleteFolder(folder: FolderEntity)
}
