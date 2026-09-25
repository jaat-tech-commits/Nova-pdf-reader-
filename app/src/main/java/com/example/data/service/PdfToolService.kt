package com.example.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfToolService(private val context: Context) {

    private val toolsDir: File by lazy {
        val dir = File(context.filesDir, "pdf_tools_output")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    suspend fun mergePdfs(pdfPaths: List<String>, outputTitle: String): File = withContext(Dispatchers.IO) {
        val outputFile = File(toolsDir, "${outputTitle.replace(" ", "_")}_${System.currentTimeMillis()}.pdf")
        val mergedDoc = PdfDocument()
        var currentGlobalPage = 1

        for (path in pdfPaths) {
            val file = File(path)
            if (!file.exists()) continue
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val width = page.width
                val height = page.height

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val pageInfo = PdfDocument.PageInfo.Builder(width, height, currentGlobalPage).create()
                val docPage = mergedDoc.startPage(pageInfo)
                val canvas = docPage.canvas
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                mergedDoc.finishPage(docPage)

                bitmap.recycle()
                currentGlobalPage++
            }
            renderer.close()
            fd.close()
        }

        FileOutputStream(outputFile).use { out ->
            mergedDoc.writeTo(out)
        }
        mergedDoc.close()
        outputFile
    }

    suspend fun splitPdf(sourcePath: String, startPage: Int, endPage: Int, outputTitle: String): File = withContext(Dispatchers.IO) {
        val outputFile = File(toolsDir, "${outputTitle.replace(" ", "_")}_p${startPage}_p${endPage}_${System.currentTimeMillis()}.pdf")
        val splitDoc = PdfDocument()

        val sourceFile = File(sourcePath)
        val fd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)

        val actualStart = (startPage - 1).coerceAtLeast(0)
        val actualEnd = (endPage - 1).coerceAtMost(renderer.pageCount - 1)
        var newPageIndex = 1

        for (i in actualStart..actualEnd) {
            val page = renderer.openPage(i)
            val width = page.width
            val height = page.height

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()

            val pageInfo = PdfDocument.PageInfo.Builder(width, height, newPageIndex).create()
            val docPage = splitDoc.startPage(pageInfo)
            val canvas = docPage.canvas
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            splitDoc.finishPage(docPage)

            bitmap.recycle()
            newPageIndex++
        }

        renderer.close()
        fd.close()

        FileOutputStream(outputFile).use { out ->
            splitDoc.writeTo(out)
        }
        splitDoc.close()
        outputFile
    }

    suspend fun imagesToPdf(imagePaths: List<String>, outputTitle: String): File = withContext(Dispatchers.IO) {
        val outputFile = File(toolsDir, "${outputTitle.replace(" ", "_")}_${System.currentTimeMillis()}.pdf")
        val pdfDoc = PdfDocument()

        for ((index, path) in imagePaths.withIndex()) {
            val bitmap = BitmapFactory.decodeFile(path) ?: continue
            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            pdfDoc.finishPage(page)
            bitmap.recycle()
        }

        FileOutputStream(outputFile).use { out ->
            pdfDoc.writeTo(out)
        }
        pdfDoc.close()
        outputFile
    }

    suspend fun pdfToImages(pdfPath: String): List<File> = withContext(Dispatchers.IO) {
        val sourceFile = File(pdfPath)
        val fd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)
        val results = mutableListOf<File>()

        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            val imgFile = File(toolsDir, "${sourceFile.nameWithoutExtension}_page_${i + 1}.jpg")
            FileOutputStream(imgFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            bitmap.recycle()
            results.add(imgFile)
        }
        renderer.close()
        fd.close()
        results
    }

    suspend fun addWatermark(sourcePath: String, watermarkText: String, outputTitle: String): File = withContext(Dispatchers.IO) {
        val outputFile = File(toolsDir, "${outputTitle.replace(" ", "_")}_watermarked_${System.currentTimeMillis()}.pdf")
        val watermarkedDoc = PdfDocument()

        val sourceFile = File(sourcePath)
        val fd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)

        val wmPaint = Paint().apply {
            color = Color.parseColor("#44888888")
            textSize = 42f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val width = page.width
            val height = page.height

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val bgCanvas = Canvas(bitmap)
            bgCanvas.drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()

            val pageInfo = PdfDocument.PageInfo.Builder(width, height, i + 1).create()
            val docPage = watermarkedDoc.startPage(pageInfo)
            val canvas = docPage.canvas

            canvas.drawBitmap(bitmap, 0f, 0f, null)

            // Draw angled watermark in center
            canvas.save()
            canvas.rotate(-35f, width / 2f, height / 2f)
            canvas.drawText(watermarkText, width / 2f, height / 2f, wmPaint)
            canvas.restore()

            watermarkedDoc.finishPage(docPage)
            bitmap.recycle()
        }

        renderer.close()
        fd.close()

        FileOutputStream(outputFile).use { out ->
            watermarkedDoc.writeTo(out)
        }
        watermarkedDoc.close()
        outputFile
    }

    suspend fun compressPdf(sourcePath: String, qualityLevel: String): File = withContext(Dispatchers.IO) {
        // qualityLevel: "Low", "Balanced", "High"
        val (scaleFactor, jpegQuality) = when (qualityLevel) {
            "High" -> Pair(0.6f, 50)
            "Low" -> Pair(0.9f, 85)
            else -> Pair(0.75f, 70) // Balanced
        }

        val outputFile = File(toolsDir, "compressed_${System.currentTimeMillis()}.pdf")
        val compressedDoc = PdfDocument()

        val sourceFile = File(sourcePath)
        val fd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)

        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val width = (page.width * scaleFactor).toInt().coerceAtLeast(300)
            val height = (page.height * scaleFactor).toInt().coerceAtLeast(400)

            val rawBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val bgCanvas = Canvas(rawBitmap)
            bgCanvas.drawColor(Color.WHITE)
            page.render(rawBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()

            // Compress to JPEG bytes then reload to reduce byte footprint
            val stream = java.io.ByteArrayOutputStream()
            rawBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, stream)
            val compressedBytes = stream.toByteArray()
            val finalBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)

            val pageInfo = PdfDocument.PageInfo.Builder(width, height, i + 1).create()
            val docPage = compressedDoc.startPage(pageInfo)
            docPage.canvas.drawBitmap(finalBitmap, 0f, 0f, null)
            compressedDoc.finishPage(docPage)

            rawBitmap.recycle()
            finalBitmap.recycle()
        }

        renderer.close()
        fd.close()

        FileOutputStream(outputFile).use { out ->
            compressedDoc.writeTo(out)
        }
        compressedDoc.close()
        outputFile
    }

    suspend fun rotatePages(sourcePath: String, degrees: Float): File = withContext(Dispatchers.IO) {
        val outputFile = File(toolsDir, "rotated_${System.currentTimeMillis()}.pdf")
        val rotatedDoc = PdfDocument()

        val sourceFile = File(sourcePath)
        val fd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)

        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val is90or270 = degrees % 180f != 0f
            val targetWidth = if (is90or270) page.height else page.width
            val targetHeight = if (is90or270) page.width else page.height

            val rawBitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            val bgCanvas = Canvas(rawBitmap)
            bgCanvas.drawColor(Color.WHITE)
            page.render(rawBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()

            val matrix = Matrix().apply {
                postRotate(degrees, page.width / 2f, page.height / 2f)
            }
            val rotatedBitmap = Bitmap.createBitmap(rawBitmap, 0, 0, page.width, page.height, matrix, true)

            val pageInfo = PdfDocument.PageInfo.Builder(targetWidth, targetHeight, i + 1).create()
            val docPage = rotatedDoc.startPage(pageInfo)
            docPage.canvas.drawBitmap(rotatedBitmap, 0f, 0f, null)
            rotatedDoc.finishPage(docPage)

            rawBitmap.recycle()
            rotatedBitmap.recycle()
        }

        renderer.close()
        fd.close()

        FileOutputStream(outputFile).use { out ->
            rotatedDoc.writeTo(out)
        }
        rotatedDoc.close()
        outputFile
    }
}
