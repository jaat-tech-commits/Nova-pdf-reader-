package com.example.data.service

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device OCR fallback for scanned/image-only PDF pages.
 * Uses the bundled Latin text recognition model so OCR works without
 * requiring a separate model download at first use.
 */
class OcrService(private val context: Context) {

    suspend fun recognize(bitmap: Bitmap): String =
        suspendCancellableCoroutine { continuation ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)

            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (continuation.isActive) continuation.resume(result.text.trim())
                    recognizer.close()
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resumeWithException(error)
                    recognizer.close()
                }

            continuation.invokeOnCancellation {
                recognizer.close()
            }
        }
}
