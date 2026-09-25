package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val uriString: String? = null,
    val fileSize: Long = 0L,
    val pageCount: Int = 1,
    val lastOpenedTimestamp: Long = System.currentTimeMillis(),
    val lastPageRead: Int = 1,
    val isFavorite: Boolean = false,
    val isVault: Boolean = false,
    val folderName: String? = null,
    val dateAdded: Long = System.currentTimeMillis(),
    val thumbnailPath: String? = null,
    val extractedText: String? = null
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val pageNumber: Int,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "annotations")
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val pageNumber: Int,
    val toolType: String, // HIGHLIGHT, UNDERLINE, PEN, NOTE, RECTANGLE, CIRCLE, ARROW
    val colorHex: String = "#FFEB3B",
    val strokeWidth: Float = 5f,
    val opacity: Float = 0.8f,
    val pointsJson: String = "",
    val textNote: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_messages")
data class AiChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val role: String, // user, assistant
    val content: String,
    val citationsJson: String = "[]",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "flashcards")
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val front: String,
    val back: String,
    val isKnown: Boolean = false,
    val lastReviewedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "study_quizzes")
data class StudyQuizEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val question: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctOptionIndex: Int,
    val explanation: String,
    val pageReference: Int = 1
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
