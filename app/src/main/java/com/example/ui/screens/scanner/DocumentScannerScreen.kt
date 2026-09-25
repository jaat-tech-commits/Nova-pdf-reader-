package com.example.ui.screens.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.DocumentRepository
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

enum class ScanFilterMode {
    ORIGINAL, COLOR, GRAYSCALE, BLACK_WHITE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScannerScreen(
    repository: DocumentRepository,
    onBack: () -> Unit,
    onOpenCreatedDocument: (Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val capturedBitmaps = remember { mutableStateListOf<Bitmap>() }
    var selectedFilter by remember { mutableStateOf(ScanFilterMode.COLOR) }
    var documentTitle by remember { mutableStateOf("Scanned_Doc_${System.currentTimeMillis() / 1000}") }
    var isGeneratingPdf by remember { mutableStateOf(false) }

    // System image picker (Gallery / Camera capture)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        for (uri in uris) {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bmp = BitmapFactory.decodeStream(stream)
                if (bmp != null) {
                    capturedBitmaps.add(bmp)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Document Scanner", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (capturedBitmaps.isNotEmpty()) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isGeneratingPdf = true
                                    // Save filtered bitmaps to temp files
                                    val tempFiles = mutableListOf<String>()
                                    for ((i, bmp) in capturedBitmaps.withIndex()) {
                                        val filtered = applyScanFilter(bmp, selectedFilter)
                                        val tempFile = File(context.cacheDir, "scan_page_${i}_${System.currentTimeMillis()}.jpg")
                                        FileOutputStream(tempFile).use { out ->
                                            filtered.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                        }
                                        tempFiles.add(tempFile.absolutePath)
                                    }
                                    val finalPdf = repository.pdfToolService.imagesToPdf(tempFiles, documentTitle)
                                    val newDoc = repository.registerGeneratedPdf(finalPdf, documentTitle)
                                    isGeneratingPdf = false
                                    Toast.makeText(context, "Scanned PDF created!", Toast.LENGTH_SHORT).show()
                                    onOpenCreatedDocument(newDoc.id)
                                }
                            },
                            enabled = !isGeneratingPdf
                        ) {
                            if (isGeneratingPdf) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                            } else {
                                Text("Create PDF")
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Document Title
            OutlinedTextField(
                value = documentTitle,
                onValueChange = { documentTitle = it },
                label = { Text("Document Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Filter options
            Text("Enhance Filter:", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Pair(ScanFilterMode.ORIGINAL, "Original"),
                    Pair(ScanFilterMode.COLOR, "Magic Color"),
                    Pair(ScanFilterMode.GRAYSCALE, "Grayscale"),
                    Pair(ScanFilterMode.BLACK_WHITE, "B&W Document")
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = selectedFilter == mode,
                        onClick = { selectedFilter = mode },
                        label = { Text(label) }
                    )
                }
            }

            // Scanned Pages Preview
            Text("Pages (${capturedBitmaps.size}):", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            if (capturedBitmaps.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No pages scanned yet")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import Images from Gallery")
                        }
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(capturedBitmaps) { index, bmp ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .width(140.dp)
                                .height(190.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                val filtered = remember(bmp, selectedFilter) {
                                    applyScanFilter(bmp, selectedFilter)
                                }
                                androidx.compose.foundation.Image(
                                    bitmap = filtered.asImageBitmap(),
                                    contentDescription = "Page ${index + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Text(
                                    text = "Page ${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .fillMaxWidth()
                                        .padding(4.dp)
                                )
                                IconButton(
                                    onClick = { capturedBitmaps.removeAt(index) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(28.dp)
                                        .padding(4.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete page", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .width(120.dp)
                                .height(190.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add page", tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Add Page", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan / Add Photos")
            }
        }
    }
}

fun applyScanFilter(src: Bitmap, filter: ScanFilterMode): Bitmap {
    if (filter == ScanFilterMode.ORIGINAL) return src
    val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint()

    when (filter) {
        ScanFilterMode.GRAYSCALE -> {
            val cm = ColorMatrix().apply { setSaturation(0f) }
            paint.colorFilter = ColorMatrixColorFilter(cm)
        }
        ScanFilterMode.BLACK_WHITE -> {
            val cm = ColorMatrix().apply {
                setSaturation(0f)
                val contrast = 1.6f
                val scale = contrast
                val translate = (-.5f * contrast + .5f) * 255f
                postConcat(ColorMatrix(floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            paint.colorFilter = ColorMatrixColorFilter(cm)
        }
        ScanFilterMode.COLOR -> {
            // Enhanced saturation and subtle contrast
            val cm = ColorMatrix().apply {
                setSaturation(1.25f)
            }
            paint.colorFilter = ColorMatrixColorFilter(cm)
        }
        else -> {}
    }

    canvas.drawBitmap(src, 0f, 0f, paint)
    return result
}
