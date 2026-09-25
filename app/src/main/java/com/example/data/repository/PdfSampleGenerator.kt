package com.example.data.repository

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

object PdfSampleGenerator {

    fun generateWelcomeSamplePdf(context: Context): File {
        val file = File(context.filesDir, "Welcome_to_NOVA_PDF_AI.pdf")
        if (file.exists() && file.length() > 0) return file

        val pdfDoc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        // Page 1: Welcome & Quick Guide
        run {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas
            drawPageBackground(canvas, pageWidth, pageHeight, "#F8FAFC")

            val titlePaint = Paint().apply {
                color = Color.parseColor("#1E1B4B")
                textSize = 26f
                isFakeBoldText = true
                isAntiAlias = true
            }
            val subtitlePaint = Paint().apply {
                color = Color.parseColor("#4F46E5")
                textSize = 14f
                isFakeBoldText = true
                isAntiAlias = true
            }
            val bodyPaint = Paint().apply {
                color = Color.parseColor("#334155")
                textSize = 12f
                isAntiAlias = true
            }

            canvas.drawText("NOVA PDF AI", 40f, 60f, titlePaint)
            canvas.drawText("Read. Understand. Create.", 40f, 85f, subtitlePaint)

            // Accent bar
            val barPaint = Paint().apply { color = Color.parseColor("#4F46E5") }
            canvas.drawRoundRect(RectF(40f, 100f, 555f, 104f), 2f, 2f, barPaint)

            var y = 140f
            y = drawSectionHeading(canvas, "1. Welcome to Your AI-Powered Document Hub", 40f, y)
            y = drawParagraph(
                canvas,
                "NOVA PDF AI combines a high-performance offline PDF reader with state-of-the-art AI intelligence. " +
                        "Whether you are studying academic papers, preparing for exams, reviewing legal contracts, or organizing personal documents, " +
                        "NOVA PDF AI empowers you to read faster and understand deeper.",
                40f, y, 515f, bodyPaint
            )

            y += 20f
            y = drawSectionHeading(canvas, "2. Key Capabilities at a Glance", 40f, y)

            val bulletPoints = listOf(
                "• In-depth AI Analysis: Summarize documents, extract formulas, and generate study revision notes.",
                "• Clickable Page Citations: Every AI answer links back to the original PDF page.",
                "• Full Annotation Suite: Highlight, underline, draw freehand, and place sticky notes.",
                "• Voice Read Aloud: Listen to documents with customizable narration speed.",
                "• Interactive Study Mode: Turn any PDF into flashcards and multiple-choice quizzes with instant grading.",
                "• PDF Utilities: Merge, split, compress, watermark, and scan physical paper to PDF."
            )

            for (bullet in bulletPoints) {
                y = drawParagraph(canvas, bullet, 50f, y, 500f, bodyPaint)
                y += 6f
            }

            // Tip Box
            y += 25f
            val tipBox = RectF(40f, y, 555f, y + 90f)
            val boxPaint = Paint().apply { color = Color.parseColor("#EEF2FF") }
            canvas.drawRoundRect(tipBox, 8f, 8f, boxPaint)
            val strokePaint = Paint().apply {
                color = Color.parseColor("#C7D2FE")
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }
            canvas.drawRoundRect(tipBox, 8f, 8f, strokePaint)

            val tipTitlePaint = Paint().apply {
                color = Color.parseColor("#3730A3")
                textSize = 13f
                isFakeBoldText = true
                isAntiAlias = true
            }
            canvas.drawText("💡 Pro Tip: Smart Selection & Ask AI", 55f, y + 26f, tipTitlePaint)
            drawParagraph(
                canvas,
                "Select any passage in the reader or tap the AI Assistant tab to ask follow-up questions, request Hindi or Hinglish explanations, or generate exam flashcards.",
                55f, y + 45f, 480f, bodyPaint
            )

            drawFooter(canvas, 1, 3, pageWidth, pageHeight)
            pdfDoc.finishPage(page)
        }

        // Page 2: Navigation & Annotation Guide
        run {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas
            drawPageBackground(canvas, pageWidth, pageHeight, "#FFFFFF")

            val titlePaint = Paint().apply {
                color = Color.parseColor("#0F172A")
                textSize = 20f
                isFakeBoldText = true
                isAntiAlias = true
            }
            val bodyPaint = Paint().apply {
                color = Color.parseColor("#334155")
                textSize = 12f
                isAntiAlias = true
            }

            canvas.drawText("Reading, Annotating & Bookmarking", 40f, 60f, titlePaint)
            var y = 100f
            y = drawSectionHeading(canvas, "Table of Contents & Quick Navigation", 40f, y)
            y = drawParagraph(
                canvas,
                "NOVA PDF AI automatically discovers sections, headings, and page bookmarks. " +
                        "Use the Table of Contents drawer in the reader top toolbar to jump instantly to any chapter or bookmarked page.",
                40f, y, 515f, bodyPaint
            )

            y += 20f
            y = drawSectionHeading(canvas, "Annotation Tools", 40f, y)
            y = drawParagraph(
                canvas,
                "Switch to Annotation Mode to mark important insights. Your annotations are stored persistently in the local database and can be edited, toggled, or exported.",
                40f, y, 515f, bodyPaint
            )

            // Draw a sample styled table
            y += 20f
            drawSampleTable(canvas, 40f, y, listOf(
                listOf("Tool", "Usage", "Shortcut"),
                listOf("Highlight", "Emphasize key terms and phrases", "Yellow / Green / Pink"),
                listOf("Underline", "Mark critical definitions and sentences", "Direct stroke"),
                listOf("Pen & Pencil", "Freehand sketching and marginalia", "Pressure responsive"),
                listOf("Sticky Note", "Add personal thoughts and revision tags", "Expandable card"),
                listOf("Shapes", "Box diagrams, circles, and emphasis arrows", "Geometric snap")
            ))

            drawFooter(canvas, 2, 3, pageWidth, pageHeight)
            pdfDoc.finishPage(page)
        }

        // Page 3: Offline Resilience & Privacy
        run {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 3).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas
            drawPageBackground(canvas, pageWidth, pageHeight, "#FFFFFF")

            val titlePaint = Paint().apply {
                color = Color.parseColor("#0F172A")
                textSize = 20f
                isFakeBoldText = true
                isAntiAlias = true
            }
            val bodyPaint = Paint().apply {
                color = Color.parseColor("#334155")
                textSize = 12f
                isAntiAlias = true
            }

            canvas.drawText("Privacy, Vault & Offline First", 40f, 60f, titlePaint)
            var y = 100f
            y = drawSectionHeading(canvas, "Private Vault Protection", 40f, y)
            y = drawParagraph(
                canvas,
                "Need to safeguard sensitive documents, statements, or identity cards? Move them to your Private Vault protected by a custom PIN and optional biometric lock. Vault documents never show up in recent feeds or public library queries.",
                40f, y, 515f, bodyPaint
            )

            y += 25f
            y = drawSectionHeading(canvas, "Offline Resilience", 40f, y)
            y = drawParagraph(
                canvas,
                "All PDF rendering, page flipping, text searching, bookmarking, and local annotation tools work 100% offline without needing an active internet connection. AI assistance connects seamlessly when online.",
                40f, y, 515f, bodyPaint
            )

            y += 30f
            val quoteBox = RectF(40f, y, 555f, y + 70f)
            val quotePaint = Paint().apply { color = Color.parseColor("#F1F5F9") }
            canvas.drawRoundRect(quoteBox, 6f, 6f, quotePaint)
            val quoteTextPaint = Paint().apply {
                color = Color.parseColor("#1E293B")
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                isAntiAlias = true
            }
            canvas.drawText("\"Knowledge is power, but understanding is freedom.\"", 60f, y + 32f, quoteTextPaint)
            canvas.drawText("— NOVA PDF AI Engineering Team", 60f, y + 50f, bodyPaint)

            drawFooter(canvas, 3, 3, pageWidth, pageHeight)
            pdfDoc.finishPage(page)
        }

        FileOutputStream(file).use { out ->
            pdfDoc.writeTo(out)
        }
        pdfDoc.close()
        return file
    }

    fun generateGeneticsSamplePdf(context: Context): File {
        val file = File(context.filesDir, "Genetics_and_Heritability_Notes.pdf")
        if (file.exists() && file.length() > 0) return file

        val pdfDoc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        // Page 1: Chapter 1 - Heritability Principles
        run {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas
            drawPageBackground(canvas, pageWidth, pageHeight, "#FFFFFF")

            val titlePaint = Paint().apply {
                color = Color.parseColor("#065F46")
                textSize = 22f
                isFakeBoldText = true
                isAntiAlias = true
            }
            val bodyPaint = Paint().apply {
                color = Color.parseColor("#1F2937")
                textSize = 12f
                isAntiAlias = true
            }

            canvas.drawText("Chapter 1: Principles of Heritability", 40f, 60f, titlePaint)
            var y = 100f
            y = drawSectionHeading(canvas, "1.1 What is Heritability?", 40f, y)
            y = drawParagraph(
                canvas,
                "Heritability is a statistic used in genetics that estimates the degree of variation in a phenotypic trait in a population that is due to genetic variation among individuals in that population. It is measured on a scale from 0.0 to 1.0 (or 0% to 100%).",
                40f, y, 515f, bodyPaint
            )

            y += 20f
            y = drawSectionHeading(canvas, "1.2 Types of Heritability", 40f, y)
            y = drawParagraph(
                canvas,
                "1. Broad-Sense Heritability (H²): Represents the proportion of total phenotypic variance (V_P) that is attributable to all genetic factors, including additive, dominance, and epistatic interactions.\n" +
                        "   Formula: H² = V_G / V_P\n\n" +
                        "2. Narrow-Sense Heritability (h²): Represents only the proportion attributable to additive genetic variance (V_A). This is the component that determines response to natural or artificial selection.\n" +
                        "   Formula: h² = V_A / V_P",
                40f, y, 515f, bodyPaint
            )

            // Formula card
            y += 20f
            val formulaBox = RectF(40f, y, 555f, y + 80f)
            val fbPaint = Paint().apply { color = Color.parseColor("#ECFDF5") }
            canvas.drawRoundRect(formulaBox, 6f, 6f, fbPaint)
            val fBorder = Paint().apply {
                color = Color.parseColor("#A7F3D0")
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }
            canvas.drawRoundRect(formulaBox, 6f, 6f, fBorder)
            val fTitle = Paint().apply {
                color = Color.parseColor("#047857")
                textSize = 13f
                isFakeBoldText = true
                isAntiAlias = true
            }
            val fText = Paint().apply {
                color = Color.parseColor("#064E3B")
                textSize = 13f
                typeface = Typeface.MONOSPACE
                isAntiAlias = true
            }
            canvas.drawText("Key Quantitative Genetics Formulas:", 55f, y + 25f, fTitle)
            canvas.drawText("V_P = V_G + V_E + V_GE", 55f, y + 48f, fText)
            canvas.drawText("Selection Response: R = h² × S  (Breeder's Equation)", 55f, y + 68f, fText)

            drawFooter(canvas, 1, 3, pageWidth, pageHeight)
            pdfDoc.finishPage(page)
        }

        // Page 2: Chapter 2 - Examples and Trait Estimates
        run {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas
            drawPageBackground(canvas, pageWidth, pageHeight, "#FFFFFF")

            val titlePaint = Paint().apply {
                color = Color.parseColor("#065F46")
                textSize = 22f
                isFakeBoldText = true
                isAntiAlias = true
            }
            val bodyPaint = Paint().apply {
                color = Color.parseColor("#1F2937")
                textSize = 12f
                isAntiAlias = true
            }

            canvas.drawText("Chapter 2: Heritability Estimates in Species", 40f, 60f, titlePaint)
            var y = 100f
            y = drawSectionHeading(canvas, "2.1 Common Traits Heritability Table", 40f, y)
            y = drawParagraph(
                canvas,
                "Heritability depends strongly on environmental conditions and the population studied. Below are representative empirical estimates:",
                40f, y, 515f, bodyPaint
            )

            y += 20f
            drawSampleTable(canvas, 40f, y, listOf(
                listOf("Organism", "Trait", "Heritability (h²)", "Significance"),
                listOf("Humans", "Adult Height", "0.80", "High genetic influence"),
                listOf("Humans", "Blood Pressure", "0.40", "Moderate environmental influence"),
                listOf("Cattle", "Milk Yield", "0.30", "Strong breeding selection target"),
                listOf("Poultry", "Egg Weight", "0.50", "Intermediate heritability"),
                listOf("Corn", "Kernel Weight", "0.55", "Optimized through hybridization")
            ))

            y += 180f
            y = drawSectionHeading(canvas, "2.2 Common Misconceptions", 40f, y)
            y = drawParagraph(
                canvas,
                "• Heritability does NOT indicate how much of an individual's trait is caused by genes.\n" +
                        "• A trait with high heritability can still be modified by environmental changes (e.g. nutrition).\n" +
                        "• Low heritability does not imply genes are uninvolved, but rather that genetic variance in that population is low.",
                40f, y, 515f, bodyPaint
            )

            drawFooter(canvas, 2, 3, pageWidth, pageHeight)
            pdfDoc.finishPage(page)
        }

        // Page 3: Chapter 3 - Exam Revision MCQs & Practice
        run {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 3).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas
            drawPageBackground(canvas, pageWidth, pageHeight, "#FFFFFF")

            val titlePaint = Paint().apply {
                color = Color.parseColor("#065F46")
                textSize = 22f
                isFakeBoldText = true
                isAntiAlias = true
            }
            val bodyPaint = Paint().apply {
                color = Color.parseColor("#1F2937")
                textSize = 12f
                isAntiAlias = true
            }

            canvas.drawText("Chapter 3: Exam Revision & Practice MCQs", 40f, 60f, titlePaint)
            var y = 100f
            y = drawSectionHeading(canvas, "Practice Questions for Study Mode", 40f, y)

            val q1 = "Q1: If phenotypic variance V_P = 100 and environmental variance V_E = 40 (assuming no gene-environment interaction), what is broad-sense heritability H²?\n" +
                    "A) 0.40   B) 0.60   C) 0.25   D) 0.80\n" +
                    "Answer: B (0.60). Explanation: V_G = V_P - V_E = 100 - 40 = 60. H² = 60/100 = 0.60."
            y = drawParagraph(canvas, q1, 40f, y, 515f, bodyPaint)

            y += 20f
            val q2 = "Q2: Which component of variance determines the response of a population to artificial selection?\n" +
                    "A) Dominance variance   B) Environmental variance   C) Additive genetic variance (V_A)   D) Epistatic variance\n" +
                    "Answer: C. Narrow-sense heritability h² = V_A / V_P determines selection response (R = h² × S)."
            y = drawParagraph(canvas, q2, 40f, y, 515f, bodyPaint)

            y += 20f
            val q3 = "Q3: True or False: If a trait has a heritability of 0.90 in a population, changing the environment cannot alter the average trait value.\n" +
                    "Answer: False. High heritability only reflects variance under current conditions; improving nutrition or healthcare can shift the whole distribution."
            y = drawParagraph(canvas, q3, 40f, y, 515f, bodyPaint)

            drawFooter(canvas, 3, 3, pageWidth, pageHeight)
            pdfDoc.finishPage(page)
        }

        FileOutputStream(file).use { out ->
            pdfDoc.writeTo(out)
        }
        pdfDoc.close()
        return file
    }

    private fun drawPageBackground(canvas: Canvas, width: Int, height: Int, colorHex: String) {
        val paint = Paint().apply { color = Color.parseColor(colorHex) }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun drawSectionHeading(canvas: Canvas, heading: String, x: Float, y: Float): Float {
        val paint = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 15f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText(heading, x, y, paint)
        return y + 20f
    }

    private fun drawParagraph(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint
    ): Float {
        var y = startY
        val lines = text.split("\n")
        for (paragraph in lines) {
            val words = paragraph.split(" ")
            var currentLine = ""
            for (word in words) {
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (paint.measureText(testLine) > maxWidth && currentLine.isNotEmpty()) {
                    canvas.drawText(currentLine, x, y, paint)
                    y += paint.textSize * 1.45f
                    currentLine = word
                } else {
                    currentLine = testLine
                }
            }
            if (currentLine.isNotEmpty()) {
                canvas.drawText(currentLine, x, y, paint)
                y += paint.textSize * 1.45f
            }
            y += 4f
        }
        return y
    }

    private fun drawSampleTable(canvas: Canvas, x: Float, y: Float, rows: List<List<String>>) {
        val colWidths = floatArrayOf(100f, 130f, 120f, 165f)
        var rowY = y
        val cellHeight = 24f

        val headerBg = Paint().apply { color = Color.parseColor("#F1F5F9") }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val headerText = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 11f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val bodyText = Paint().apply {
            color = Color.parseColor("#334155")
            textSize = 10f
            isAntiAlias = true
        }

        for ((rowIndex, row) in rows.withIndex()) {
            var colX = x
            val isHeader = rowIndex == 0
            if (isHeader) {
                canvas.drawRect(x, rowY, x + 515f, rowY + cellHeight, headerBg)
            }
            canvas.drawRect(x, rowY, x + 515f, rowY + cellHeight, borderPaint)

            for ((colIndex, cell) in row.withIndex()) {
                val width = colWidths.getOrElse(colIndex) { 100f }
                val textPaint = if (isHeader) headerText else bodyText
                canvas.drawText(cell, colX + 6f, rowY + 16f, textPaint)
                canvas.drawLine(colX + width, rowY, colX + width, rowY + cellHeight, borderPaint)
                colX += width
            }
            rowY += cellHeight
        }
    }

    private fun drawFooter(canvas: Canvas, current: Int, total: Int, width: Int, height: Int) {
        val paint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 10f
            isAntiAlias = true
        }
        val text = "NOVA PDF AI  •  Page $current of $total"
        canvas.drawText(text, 40f, height - 30f, paint)
    }
}
