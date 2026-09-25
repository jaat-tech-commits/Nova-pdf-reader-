package com.example.ui.screens.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.DocumentEntity
import com.example.data.repository.DocumentRepository
import kotlinx.coroutines.launch
import java.io.File

enum class ActivePdfTool {
    NONE, MERGE, SPLIT, COMPRESS, IMAGES_TO_PDF, PDF_TO_IMAGES, WATERMARK, ROTATE, COMPARE, ANALYZE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    repository: DocumentRepository,
    onBack: (() -> Unit)?,
    onOpenDocument: (Long, Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allDocs by repository.allDocuments.collectAsState(initial = emptyList())

    var activeTool by remember { mutableStateOf(ActivePdfTool.NONE) }
    var isProcessing by remember { mutableStateOf(false) }
    var resultDocument by remember { mutableStateOf<DocumentEntity?>(null) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    // Multi-file selection for merge
    val selectedDocIdsForMerge = remember { mutableStateListOf<Long>() }

    // Single selected doc for split/compress/watermark/rotate
    var selectedDocIdForTool by remember { mutableStateOf<Long?>(null) }

    // Tool specific params
    var splitStartPage by remember { mutableIntStateOf(1) }
    var splitEndPage by remember { mutableIntStateOf(2) }
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var compressLevel by remember { mutableStateOf("Balanced") }
    var rotateDegrees by remember { mutableFloatStateOf(90f) }
    var compareDocAId by remember { mutableStateOf<Long?>(null) }
    var compareDocBId by remember { mutableStateOf<Long?>(null) }
    var comparisonResult by remember { mutableStateOf<String?>(null) }
    var analysisResult by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF Utilities & Tools", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Professional PDF Suite",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Merge, split, compress, watermark, rotate, analyze, and convert PDFs natively on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Tools Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ToolCard(
                            title = "Merge PDFs",
                            subtitle = "Combine multiple PDFs into one",
                            icon = Icons.Default.CallMerge,
                            modifier = Modifier.weight(1f),
                            onClick = { activeTool = ActivePdfTool.MERGE }
                        )
                        ToolCard(
                            title = "Split PDF",
                            subtitle = "Extract pages or ranges",
                            icon = Icons.Default.CallSplit,
                            modifier = Modifier.weight(1f),
                            onClick = { activeTool = ActivePdfTool.SPLIT }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ToolCard(
                            title = "Compress PDF",
                            subtitle = "Reduce document file size",
                            icon = Icons.Default.Compress,
                            modifier = Modifier.weight(1f),
                            onClick = { activeTool = ActivePdfTool.COMPRESS }
                        )
                        ToolCard(
                            title = "Watermark",
                            subtitle = "Add custom stamp or text",
                            icon = Icons.Default.BrandingWatermark,
                            modifier = Modifier.weight(1f),
                            onClick = { activeTool = ActivePdfTool.WATERMARK }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ToolCard(
                            title = "Rotate Pages",
                            subtitle = "Rotate 90°, 180°, or 270°",
                            icon = Icons.Default.RotateRight,
                            modifier = Modifier.weight(1f),
                            onClick = { activeTool = ActivePdfTool.ROTATE }
                        )
                        ToolCard(
                            title = "PDF → Images",
                            subtitle = "Export pages as JPEG",
                            icon = Icons.Default.PhotoLibrary,
                            modifier = Modifier.weight(1f),
                            onClick = { activeTool = ActivePdfTool.PDF_TO_IMAGES }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ToolCard(
                            title = "Compare PDFs",
                            subtitle = "AI comparison of two PDFs",
                            icon = Icons.Default.CompareArrows,
                            modifier = Modifier.weight(1f),
                            onClick = { activeTool = ActivePdfTool.COMPARE }
                        )
                        ToolCard(
                            title = "Smart Analysis",
                            subtitle = "Definitions, formulas, tables",
                            icon = Icons.Default.Analytics,
                            modifier = Modifier.weight(1f),
                            onClick = { activeTool = ActivePdfTool.ANALYZE }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Interactive Dialogs for each tool

    // 1. Merge Dialog
    if (activeTool == ActivePdfTool.MERGE) {
        AlertDialog(
            onDismissRequest = { activeTool = ActivePdfTool.NONE },
            title = { Text("Merge PDFs") },
            text = {
                Column {
                    Text("Select at least 2 documents to merge:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.height(200.dp)) {
                        items(allDocs) { doc ->
                            val isChecked = selectedDocIdsForMerge.contains(doc.id)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) selectedDocIdsForMerge.remove(doc.id)
                                        else selectedDocIdsForMerge.add(doc.id)
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        if (it) selectedDocIdsForMerge.add(doc.id)
                                        else selectedDocIdsForMerge.remove(doc.id)
                                    }
                                )
                                Text(doc.title, maxLines = 1)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val paths = allDocs.filter { selectedDocIdsForMerge.contains(it.id) }.map { it.filePath }
                        if (paths.size >= 2) {
                            coroutineScope.launch {
                                isProcessing = true
                                val mergedFile = repository.pdfToolService.mergePdfs(paths, "Merged_Document")
                                val newDoc = repository.registerGeneratedPdf(mergedFile, "Merged Document")
                                isProcessing = false
                                activeTool = ActivePdfTool.NONE
                                resultDocument = newDoc
                            }
                        }
                    },
                    enabled = selectedDocIdsForMerge.size >= 2 && !isProcessing
                ) {
                    if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(16.dp)) else Text("Merge PDFs")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeTool = ActivePdfTool.NONE }) { Text("Cancel") }
            }
        )
    }

    // 2. Split Dialog
    if (activeTool == ActivePdfTool.SPLIT) {
        val targetDoc = allDocs.firstOrNull { it.id == selectedDocIdForTool } ?: allDocs.firstOrNull()
        AlertDialog(
            onDismissRequest = { activeTool = ActivePdfTool.NONE },
            title = { Text("Split PDF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Document: ${targetDoc?.title ?: "Select"}", fontWeight = FontWeight.Bold)
                    Text("Total Pages: ${targetDoc?.pageCount ?: 1}")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = splitStartPage.toString(),
                            onValueChange = { splitStartPage = it.toIntOrNull() ?: 1 },
                            label = { Text("Start Page") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = splitEndPage.toString(),
                            onValueChange = { splitEndPage = it.toIntOrNull() ?: 2 },
                            label = { Text("End Page") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (targetDoc != null) {
                            coroutineScope.launch {
                                isProcessing = true
                                val splitFile = repository.pdfToolService.splitPdf(targetDoc.filePath, splitStartPage, splitEndPage, "${targetDoc.title}_split")
                                val newDoc = repository.registerGeneratedPdf(splitFile, "${targetDoc.title} (Pages $splitStartPage-$splitEndPage)")
                                isProcessing = false
                                activeTool = ActivePdfTool.NONE
                                resultDocument = newDoc
                            }
                        }
                    },
                    enabled = targetDoc != null && !isProcessing
                ) {
                    Text("Split PDF")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeTool = ActivePdfTool.NONE }) { Text("Cancel") }
            }
        )
    }

    // 3. Compress Dialog
    if (activeTool == ActivePdfTool.COMPRESS) {
        val targetDoc = allDocs.firstOrNull { it.id == selectedDocIdForTool } ?: allDocs.firstOrNull()
        AlertDialog(
            onDismissRequest = { activeTool = ActivePdfTool.NONE },
            title = { Text("Compress PDF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Document: ${targetDoc?.title ?: ""}", fontWeight = FontWeight.SemiBold)
                    Text("Original Size: ${(targetDoc?.fileSize ?: 0) / 1024} KB")
                    Text("Select Compression Level:")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Low", "Balanced", "High").forEach { level ->
                            FilterChip(
                                selected = compressLevel == level,
                                onClick = { compressLevel = level },
                                label = { Text(level) }
                            )
                        }
                    }
                    Text(
                        text = when (compressLevel) {
                            "High" -> "Estimated reduction: ~40-60%"
                            "Balanced" -> "Estimated reduction: ~25-35%"
                            else -> "Estimated reduction: ~15-20%"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (targetDoc != null) {
                            coroutineScope.launch {
                                isProcessing = true
                                val compFile = repository.pdfToolService.compressPdf(targetDoc.filePath, compressLevel)
                                val newDoc = repository.registerGeneratedPdf(compFile, "${targetDoc.title} (Compressed)")
                                isProcessing = false
                                activeTool = ActivePdfTool.NONE
                                resultDocument = newDoc
                            }
                        }
                    },
                    enabled = targetDoc != null && !isProcessing
                ) {
                    Text("Compress")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeTool = ActivePdfTool.NONE }) { Text("Cancel") }
            }
        )
    }

    // 4. Watermark Dialog
    if (activeTool == ActivePdfTool.WATERMARK) {
        val targetDoc = allDocs.firstOrNull { it.id == selectedDocIdForTool } ?: allDocs.firstOrNull()
        AlertDialog(
            onDismissRequest = { activeTool = ActivePdfTool.NONE },
            title = { Text("Add Watermark") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Document: ${targetDoc?.title ?: ""}", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = watermarkText,
                        onValueChange = { watermarkText = it },
                        label = { Text("Watermark Text") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (targetDoc != null && watermarkText.isNotBlank()) {
                            coroutineScope.launch {
                                isProcessing = true
                                val wmFile = repository.pdfToolService.addWatermark(targetDoc.filePath, watermarkText, targetDoc.title)
                                val newDoc = repository.registerGeneratedPdf(wmFile, "${targetDoc.title} (Watermarked)")
                                isProcessing = false
                                activeTool = ActivePdfTool.NONE
                                resultDocument = newDoc
                            }
                        }
                    },
                    enabled = targetDoc != null && watermarkText.isNotBlank() && !isProcessing
                ) {
                    Text("Add Watermark")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeTool = ActivePdfTool.NONE }) { Text("Cancel") }
            }
        )
    }

    // 5. Rotate Dialog
    if (activeTool == ActivePdfTool.ROTATE) {
        val targetDoc = allDocs.firstOrNull { it.id == selectedDocIdForTool } ?: allDocs.firstOrNull()
        AlertDialog(
            onDismissRequest = { activeTool = ActivePdfTool.NONE },
            title = { Text("Rotate PDF Pages") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Document: ${targetDoc?.title ?: ""}", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(90f, 180f, 270f).forEach { deg ->
                            FilterChip(
                                selected = rotateDegrees == deg,
                                onClick = { rotateDegrees = deg },
                                label = { Text("${deg.toInt()}°") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (targetDoc != null) {
                            coroutineScope.launch {
                                isProcessing = true
                                val rotFile = repository.pdfToolService.rotatePages(targetDoc.filePath, rotateDegrees)
                                val newDoc = repository.registerGeneratedPdf(rotFile, "${targetDoc.title} (Rotated ${rotateDegrees.toInt()}°)")
                                isProcessing = false
                                activeTool = ActivePdfTool.NONE
                                resultDocument = newDoc
                            }
                        }
                    },
                    enabled = targetDoc != null && !isProcessing
                ) {
                    Text("Rotate Pages")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeTool = ActivePdfTool.NONE }) { Text("Cancel") }
            }
        )
    }

    // 6. PDF to Images Dialog
    if (activeTool == ActivePdfTool.PDF_TO_IMAGES) {
        val targetDoc = allDocs.firstOrNull { it.id == selectedDocIdForTool } ?: allDocs.firstOrNull()
        AlertDialog(
            onDismissRequest = { activeTool = ActivePdfTool.NONE },
            title = { Text("Export PDF as Images") },
            text = {
                Text("Convert each page of \"${targetDoc?.title ?: ""}\" into a high-resolution JPEG image.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (targetDoc != null) {
                            coroutineScope.launch {
                                isProcessing = true
                                val images = repository.pdfToolService.pdfToImages(targetDoc.filePath)
                                isProcessing = false
                                activeTool = ActivePdfTool.NONE
                                resultMessage = "Successfully exported ${images.size} page images to app gallery!"
                            }
                        }
                    },
                    enabled = targetDoc != null && !isProcessing
                ) {
                    Text("Export Images")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeTool = ActivePdfTool.NONE }) { Text("Cancel") }
            }
        )
    }

    // 7. Compare PDFs Dialog
    if (activeTool == ActivePdfTool.COMPARE) {
        val docA = allDocs.firstOrNull()
        val docB = allDocs.getOrNull(1) ?: allDocs.firstOrNull()
        AlertDialog(
            onDismissRequest = { activeTool = ActivePdfTool.NONE },
            title = { Text("Compare Two PDFs") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PDF A: ${docA?.title ?: ""}", fontWeight = FontWeight.SemiBold)
                    Text("PDF B: ${docB?.title ?: ""}", fontWeight = FontWeight.SemiBold)
                    Text("NOVA AI will find shared concepts, contrasting statements, and changed numbers.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (docA != null && docB != null) {
                            coroutineScope.launch {
                                isProcessing = true
                                val res = repository.geminiService.compareDocuments(
                                    titleA = docA.title,
                                    textA = docA.extractedText ?: docA.title,
                                    titleB = docB.title,
                                    textB = docB.extractedText ?: docB.title
                                )
                                comparisonResult = res
                                isProcessing = false
                                activeTool = ActivePdfTool.NONE
                            }
                        }
                    },
                    enabled = docA != null && docB != null && !isProcessing
                ) {
                    Text("Compare with AI")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeTool = ActivePdfTool.NONE }) { Text("Cancel") }
            }
        )
    }

    // 8. Smart Document Analysis Dialog
    if (activeTool == ActivePdfTool.ANALYZE) {
        val targetDoc = allDocs.firstOrNull()
        AlertDialog(
            onDismissRequest = { activeTool = ActivePdfTool.NONE },
            title = { Text("Smart Document Analysis") },
            text = {
                Text("Analyze \"${targetDoc?.title ?: ""}\" to extract key definitions, formulas, timeline dates, and structured tables.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (targetDoc != null) {
                            coroutineScope.launch {
                                isProcessing = true
                                val res = repository.geminiService.analyzeDocumentStructure(targetDoc.title, targetDoc.extractedText ?: targetDoc.title)
                                analysisResult = res
                                isProcessing = false
                                activeTool = ActivePdfTool.NONE
                            }
                        }
                    },
                    enabled = targetDoc != null && !isProcessing
                ) {
                    Text("Analyze Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeTool = ActivePdfTool.NONE }) { Text("Cancel") }
            }
        )
    }

    // Result Document Dialog (Success Screen)
    if (resultDocument != null) {
        val doc = resultDocument!!
        AlertDialog(
            onDismissRequest = { resultDocument = null },
            title = { Text("Processing Complete! 🎉") },
            text = {
                Column {
                    Text("Your new document is ready:")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(doc.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("${doc.pageCount} pages  •  ${doc.fileSize / 1024} KB")
                }
            },
            confirmButton = {
                Button(onClick = {
                    val target = doc
                    resultDocument = null
                    onOpenDocument(target.id, 1)
                }) {
                    Text("Open PDF")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, Uri.fromFile(File(doc.filePath)))
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Processed PDF"))
                }) {
                    Text("Share")
                }
            }
        )
    }

    // Generic Message Dialog
    if (resultMessage != null) {
        AlertDialog(
            onDismissRequest = { resultMessage = null },
            title = { Text("Success") },
            text = { Text(resultMessage!!) },
            confirmButton = {
                TextButton(onClick = { resultMessage = null }) { Text("OK") }
            }
        )
    }

    // Comparison Result Dialog
    if (comparisonResult != null) {
        AlertDialog(
            onDismissRequest = { comparisonResult = null },
            title = { Text("AI PDF Comparison Results") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    item { Text(comparisonResult!!, style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = {
                TextButton(onClick = { comparisonResult = null }) { Text("Close") }
            }
        )
    }

    // Analysis Result Dialog
    if (analysisResult != null) {
        AlertDialog(
            onDismissRequest = { analysisResult = null },
            title = { Text("Smart Document Analysis") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    item { Text(analysisResult!!, style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = {
                TextButton(onClick = { analysisResult = null }) { Text("Close") }
            }
        )
    }
}

@Composable
fun ToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.height(130.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
