package com.example.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfRendererService(private val context: Context) {

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8 // 1/8th of available memory
    private val bitmapCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private var activeRenderer: PdfRenderer? = null
    private var activeDescriptor: ParcelFileDescriptor? = null
    private var currentFilePath: String? = null

    @Synchronized
    private fun getOrCreateRenderer(filePath: String): PdfRenderer {
        if (activeRenderer != null && currentFilePath == filePath) {
            return activeRenderer!!
        }
        closeCurrentRenderer()
        val file = File(filePath)
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor)
        activeDescriptor = descriptor
        activeRenderer = renderer
        currentFilePath = filePath
        return renderer
    }

    @Synchronized
    fun closeCurrentRenderer() {
        try {
            activeRenderer?.close()
        } catch (_: Exception) {}
        try {
            activeDescriptor?.close()
        } catch (_: Exception) {}
        activeRenderer = null
        activeDescriptor = null
        currentFilePath = null
    }

    suspend fun getPageCount(filePath: String): Int = withContext(Dispatchers.IO) {
        try {
            val renderer = getOrCreateRenderer(filePath)
            renderer.pageCount
        } catch (e: Exception) {
            1
        }
    }

    suspend fun renderPage(
        filePath: String,
        pageIndex: Int,
        destWidth: Int = 1080,
        backgroundColor: Int = Color.WHITE
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${filePath}_${pageIndex}_$destWidth"
        bitmapCache.get(cacheKey)?.let { return@withContext it }

        try {
            val renderer = getOrCreateRenderer(filePath)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

            val page = renderer.openPage(pageIndex)
            val aspect = page.height.toFloat() / page.width.toFloat()
            val targetHeight = (destWidth * aspect).toInt().coerceAtLeast(100)

            val bitmap = Bitmap.createBitmap(destWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(backgroundColor)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            bitmapCache.put(cacheKey, bitmap)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun generateThumbnail(filePath: String, documentId: Long): String? = withContext(Dispatchers.IO) {
        try {
            val bitmap = renderPage(filePath, 0, destWidth = 360) ?: return@withContext null
            val thumbFile = File(context.cacheDir, "thumb_doc_${documentId}.jpg")
            FileOutputStream(thumbFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            thumbFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    suspend fun copyUriToLocalStorage(uri: Uri, fileName: String): File = withContext(Dispatchers.IO) {
        val destDir = File(context.filesDir, "documents")
        if (!destDir.exists()) destDir.mkdirs()

        val safeName = fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val destFile = File(destDir, safeName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        destFile
    }
}
