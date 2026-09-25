package com.example.ui.screens.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AnnotationEntity
import com.example.data.local.BookmarkEntity
import com.example.data.local.DocumentEntity
import com.example.data.local.FlashcardEntity
import com.example.data.repository.DocumentRepository
import com.example.data.service.TtsService
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File

enum class AnnotationTool {
    NONE, HIGHLIGHT, UNDERLINE, PEN, PENCIL, STICKY_NOTE, RECTANGLE, CIRCLE, ARROW
}

enum class ReaderThemeMode {
    WHITE, WARM, DARK_GRAY, BLACK
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    docId: Long,
    initialPage: Int = 1,
    repository: DocumentRepository,
    ttsService: TtsService,
    onBack: () -> Unit,
    onNavigateToAi: (Long, String?) -> Unit,
    onNavigateToStudy: (Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var document by remember { mutableStateOf<DocumentEntity?>(null) }
    var totalPages by remember { mutableIntStateOf(1) }
    var currentPage by remember { mutableIntStateOf(initialPage) }

    // Page bitmaps cache (mapped by page index 0..pageCount-1)
    val pageBitmaps = remember { mutableStateMapOf<Int, Bitmap?>() }

    // Navigation and UI toggles
    var isContinuousMode by remember { mutableStateOf(false) }
    var readerThemeMode by remember { mutableStateOf(ReaderThemeMode.WHITE) }
    var isFullScreen by remember { mutableStateOf(false) }

    // Zoom & Pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Sheet / Dialog states
    var showTocSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var showExplainDialog by remember { mutableStateOf<Pair<String, String>?>(null) } // Text to explanation

    // Audio / TTS state
    val isTtsPlaying by ttsService.isPlaying.collectAsState()
    val ttsSpeed by ttsService.currentSpeed.collectAsState()
    var showTtsBar by remember { mutableStateOf(false) }

    // Annotation toolbar state
    var activeTool by remember { mutableStateOf(AnnotationTool.NONE) }
    var selectedColorHex by remember { mutableStateOf("#FACC15") } // Default highlighter yellow
    var strokeWidth by remember { mutableFloatStateOf(8f) }
    val currentDrawnPoints = remember { mutableStateListOf<Offset>() }

    // Bookmarks and annotations from database
    val bookmarks by repository.getBookmarks(docId).collectAsState(initial = emptyList())
    val annotations by repository.getAnnotations(docId).collectAsState(initial = emptyList())

    // Smart text selection / OCR state
    var showTextSelectionDialog by remember { mutableStateOf(false) }
    var isOcrLoading by remember { mutableStateOf(false) }
    var ocrError by remember { mutableStateOf<String?>(null) }
    var selectionValue by remember { mutableStateOf(TextFieldValue()) }

    // Back handler: save reading progress on exit
    BackHandler {
        coroutineScope.launch {
            repository.updateReadingProgress(docId, currentPage)
            ttsService.stop()
            onBack()
        }
    }

    // Load document on launch
    LaunchedEffect(docId) {
        val doc = repository.getDocumentById(docId)
        if (doc != null) {
            document = doc
            totalPages = doc.pageCount.coerceAtLeast(1)
            currentPage = initialPage.coerceIn(1, totalPages)
            repository.updateReadingProgress(docId, currentPage)
        }
    }

    // Render current page bitmap
    LaunchedEffect(docId, currentPage, readerThemeMode) {
        val doc = document ?: repository.getDocumentById(docId)
        if (doc != null) {
            val bgInt = when (readerThemeMode) {
                ReaderThemeMode.WHITE -> android.graphics.Color.WHITE
                ReaderThemeMode.WARM -> android.graphics.Color.parseColor("#FAF3E3")
                ReaderThemeMode.DARK_GRAY -> android.graphics.Color.parseColor("#1E293B")
                ReaderThemeMode.BLACK -> android.graphics.Color.BLACK
            }
            val pageIndex = currentPage - 1
            if (!pageBitmaps.containsKey(pageIndex)) {
                val bitmap = repository.pdfRendererService.renderPage(
                    filePath = doc.filePath,
                    pageIndex = pageIndex,
                    destWidth = 1200,
                    backgroundColor = bgInt
                )
                pageBitmaps[pageIndex] = bitmap
            }
            // Preload next page
            if (currentPage < totalPages && !pageBitmaps.containsKey(currentPage)) {
                coroutineScope.launch {
                    val nextBmp = repository.pdfRendererService.renderPage(
                        filePath = doc.filePath,
                        pageIndex = currentPage,
                        destWidth = 1200,
                        backgroundColor = bgInt
                    )
                    pageBitmaps[currentPage] = nextBmp
                }
            }
        }
    }

    val pageBgColor = when (readerThemeMode) {
        ReaderThemeMode.WHITE -> Color(0xFFFFFFFF)
        ReaderThemeMode.WARM -> Color(0xFFFAF3E3)
        ReaderThemeMode.DARK_GRAY -> Color(0xFF1E293B)
        ReaderThemeMode.BLACK -> Color(0xFF000000)
    }

    val isCurrentPageBookmarked = bookmarks.any { it.pageNumber == currentPage }

    Scaffold(
        topBar = {
            if (!isFullScreen) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = document?.title ?: "PDF Reader",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Page $currentPage of $totalPages",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                repository.updateReadingProgress(docId, currentPage)
                                ttsService.stop()
                                onBack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSearchDialog = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search PDF")
                        }
                        IconButton(onClick = {
                            if (isCurrentPageBookmarked) {
                                val bm = bookmarks.firstOrNull { it.pageNumber == currentPage }
                                if (bm != null) coroutineScope.launch { repository.deleteBookmark(bm.id) }
                            } else {
                                showAddBookmarkDialog = true
                            }
                        }) {
                            Icon(
                                imageVector = if (isCurrentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Bookmark Page",
                                tint = if (isCurrentPageBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { showBookmarksSheet = true }) {
                            Icon(Icons.Default.Bookmarks, contentDescription = "Bookmarks List")
                        }
                        IconButton(onClick = { showTocSheet = true }) {
                            Icon(Icons.Default.MenuBook, contentDescription = "Table of Contents")
                        }
                        IconButton(onClick = { showTtsBar = !showTtsBar }) {
                            Icon(
                                imageVector = if (isTtsPlaying) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                                contentDescription = "Read Aloud",
                                tint = if (isTtsPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!isFullScreen) {
                Column {
                    // Floating TTS bar if opened
                    AnimatedVisibility(visible = showTtsBar) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 4.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilledIconButton(
                                        onClick = {
                                            if (isTtsPlaying) {
                                                ttsService.stop()
                                            } else {
                                                val textToRead = document?.extractedText ?: "Reading page $currentPage of ${document?.title}"
                                                ttsService.speak(textToRead)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isTtsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play/Pause"
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Read Aloud", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                                    TextButton(onClick = {
                                        val nextIdx = (speeds.indexOf(ttsSpeed) + 1) % speeds.size
                                        ttsService.setSpeed(speeds[nextIdx])
                                    }) {
                                        Text("${ttsSpeed}x")
                                    }
                                    IconButton(onClick = {
                                        ttsService.stop()
                                        showTtsBar = false
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close TTS")
                                    }
                                }
                            }
                        }
                    }

                    // Annotation Palette when tool is selected
                    AnimatedVisibility(visible = activeTool != AnnotationTool.NONE) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Colors
                                val colors = listOf("#FACC15", "#4ADE80", "#60A5FA", "#F472B6", "#A78BFA", "#FB923C")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    for (c in colors) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(Color(android.graphics.Color.parseColor(c)))
                                                .clickable { selectedColorHex = c }
                                        ) {
                                            if (selectedColorHex == c) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(16.dp).align(Alignment.Center)
                                                )
                                            }
                                        }
                                    }
                                }

                                Row {
                                    TextButton(onClick = {
                                        coroutineScope.launch {
                                            repository.clearAnnotationsForPage(docId, currentPage)
                                            Toast.makeText(context, "Annotations cleared for page $currentPage", Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Text("Clear")
                                    }
                                    IconButton(onClick = { activeTool = AnnotationTool.NONE }) {
                                        Icon(Icons.Default.Done, contentDescription = "Done Annotating")
                                    }
                                }
                            }
                        }
                    }

                    // Main Reader Bottom Toolbar
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (currentPage > 1) {
                                        currentPage--
                                        coroutineScope.launch { repository.updateReadingProgress(docId, currentPage) }
                                    }
                                },
                                enabled = currentPage > 1
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Page")
                            }

                            // Page jump trigger
                            TextButton(onClick = { showJumpDialog = true }) {
                                Text(
                                    text = "$currentPage / $totalPages",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (currentPage < totalPages) {
                                        currentPage++
                                        coroutineScope.launch { repository.updateReadingProgress(docId, currentPage) }
                                    }
                                },
                                enabled = currentPage < totalPages
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Page")
                            }

                            // Annotation Tool Toggle
                            IconButton(
                                onClick = {
                                    activeTool = if (activeTool == AnnotationTool.NONE) AnnotationTool.PEN else AnnotationTool.NONE
                                }
                            ) {
                                Icon(
                                    imageVector = if (activeTool != AnnotationTool.NONE) Icons.Default.EditOff else Icons.Default.Draw,
                                    contentDescription = "Annotate",
                                    tint = if (activeTool != AnnotationTool.NONE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // AI Shortcut Button
                            FilledTonalButton(
                                onClick = { onNavigateToAi(docId, null) },
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ask AI")
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(pageBgColor)
        ) {
            val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
                scale = (scale * zoomChange).coerceIn(0.75f, 4.0f)
                offset += panChange
            }

            // PDF Canvas & Page Viewer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(state = transformableState)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .pointerInput(activeTool) {
                        if (activeTool != AnnotationTool.NONE) {
                            detectDragGestures(
                                onDragStart = { currentDrawnPoints.clear(); currentDrawnPoints.add(it) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentDrawnPoints.add(change.position)
                                },
                                onDragEnd = {
                                    if (currentDrawnPoints.size > 1) {
                                        val pointsArray = JSONArray()
                                        for (pt in currentDrawnPoints) {
                                            val pArr = JSONArray().apply { put(pt.x); put(pt.y) }
                                            pointsArray.put(pArr)
                                        }
                                        coroutineScope.launch {
                                            repository.addAnnotation(
                                                AnnotationEntity(
                                                    documentId = docId,
                                                    pageNumber = currentPage,
                                                    toolType = activeTool.name,
                                                    colorHex = selectedColorHex,
                                                    strokeWidth = strokeWidth,
                                                    pointsJson = pointsArray.toString()
                                                )
                                            )
                                            currentDrawnPoints.clear()
                                        }
                                    }
                                }
                            )
                        } else {
                            detectTapGestures(
                                onDoubleTap = {
                                    scale = if (scale > 1.2f) 1f else 2f
                                    offset = Offset.Zero
                                },
                                onTap = {
                                    // Scanned PDFs have no native text layer. OCR the actual
                                    // visible page, then let the user select the exact passage.
                                    showTextSelectionDialog = true
                                    isOcrLoading = true
                                    ocrError = null
                                    selectionValue = TextFieldValue("")
                                    coroutineScope.launch {
                                        val text = try {
                                            repository.ocrPage(docId, currentPage)
                                        } catch (e: Exception) {
                                            null
                                        }
                                        if (text.isNullOrBlank()) {
                                            ocrError = "No readable text was found on this page. Try a clearer scan or use Ask AI on the page."
                                        } else {
                                            selectionValue = TextFieldValue(text)
                                        }
                                        isOcrLoading = false
                                    }
                                }
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val currentBmp = pageBitmaps[currentPage - 1]
                if (currentBmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = currentBmp.asImageBitmap(),
                        contentDescription = "Page $currentPage",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                }

                // Saved annotations overlay for this page
                val pageAnnotations = annotations.filter { it.pageNumber == currentPage }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    for (anno in pageAnnotations) {
                        try {
                            val pts = JSONArray(anno.pointsJson)
                            if (pts.length() > 1) {
                                val path = Path()
                                val first = pts.getJSONArray(0)
                                path.moveTo(first.getDouble(0).toFloat(), first.getDouble(1).toFloat())
                                for (i in 1 until pts.length()) {
                                    val pt = pts.getJSONArray(i)
                                    path.lineTo(pt.getDouble(0).toFloat(), pt.getDouble(1).toFloat())
                                }
                                val c = Color(android.graphics.Color.parseColor(anno.colorHex)).copy(alpha = anno.opacity)
                                drawPath(
                                    path = path,
                                    color = c,
                                    style = Stroke(
                                        width = anno.strokeWidth,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        } catch (_: Exception) {}
                    }

                    // Draw in-progress stroke
                    if (currentDrawnPoints.size > 1) {
                        val path = Path()
                        path.moveTo(currentDrawnPoints.first().x, currentDrawnPoints.first().y)
                        for (i in 1 until currentDrawnPoints.size) {
                            path.lineTo(currentDrawnPoints[i].x, currentDrawnPoints[i].y)
                        }
                        val c = Color(android.graphics.Color.parseColor(selectedColorHex)).copy(alpha = 0.8f)
                        drawPath(
                            path = path,
                            color = c,
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }

            // Quick Zoom Controls Overlay (Top Right)
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = { scale = (scale + 0.25f).coerceAtMost(3.5f) },
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
                }
                SmallFloatingActionButton(
                    onClick = {
                        scale = (scale - 0.25f).coerceAtLeast(0.75f)
                        if (scale <= 1.0f) offset = Offset.Zero
                    },
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
                }
                SmallFloatingActionButton(
                    onClick = {
                        scale = 1.0f
                        offset = Offset.Zero
                    },
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ) {
                    Icon(Icons.Default.FitScreen, contentDescription = "Fit Page")
                }
            }
        }
    }

    // Table of Contents Sheet
    if (showTocSheet) {
        ModalBottomSheet(onDismissRequest = { showTocSheet = false }) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "TABLE OF CONTENTS",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                val sampleChapters = listOf(
                    Pair("1. Introduction & Overview", 1),
                    Pair("2. Key Principles & Calculations", 2),
                    Pair("3. Exam Revision, Practice & MCQs", 3)
                )

                for ((chapter, targetPage) in sampleChapters) {
                    Surface(
                        onClick = {
                            currentPage = targetPage.coerceIn(1, totalPages)
                            coroutineScope.launch { repository.updateReadingProgress(docId, currentPage) }
                            showTocSheet = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (currentPage == targetPage) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(chapter, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                            Text("Page $targetPage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Bookmarks Sheet
    if (showBookmarksSheet) {
        ModalBottomSheet(onDismissRequest = { showBookmarksSheet = false }) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BOOKMARKS (${bookmarks.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = {
                        showBookmarksSheet = false
                        showAddBookmarkDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Bookmark")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (bookmarks.isEmpty()) {
                    Text("No bookmarks added yet. Tap bookmark icon on any page to save it.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(bookmarks) { bm ->
                            Card(
                                onClick = {
                                    currentPage = bm.pageNumber.coerceIn(1, totalPages)
                                    coroutineScope.launch { repository.updateReadingProgress(docId, currentPage) }
                                    showBookmarksSheet = false
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(bm.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                        Text("Page ${bm.pageNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { coroutineScope.launch { repository.deleteBookmark(bm.id) } }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Add Bookmark Dialog
    if (showAddBookmarkDialog) {
        var bmTitle by remember { mutableStateOf("Page $currentPage Bookmark") }
        AlertDialog(
            onDismissRequest = { showAddBookmarkDialog = false },
            title = { Text("Bookmark Page $currentPage") },
            text = {
                OutlinedTextField(
                    value = bmTitle,
                    onValueChange = { bmTitle = it },
                    label = { Text("Bookmark Title / Note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            repository.addBookmark(docId, currentPage, bmTitle.trim())
                            showAddBookmarkDialog = false
                            Toast.makeText(context, "Page $currentPage bookmarked!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddBookmarkDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Search in PDF Dialog
    if (showSearchDialog) {
        var query by remember { mutableStateOf("") }
        var isCaseSensitive by remember { mutableStateOf(false) }
        val searchResults = remember(query, isCaseSensitive, document?.extractedText) {
            if (query.isBlank()) emptyList()
            else {
                val source = document?.extractedText.orEmpty()
                if (source.isBlank() || source.startsWith("Imported PDF:")) {
                    emptyList()
                } else {
                    val pageRegex = Regex("\\[Page (\\d+)\\]")
                    val lines = source.lines()
                    var page = 1
                    val results = mutableListOf<Pair<Int, String>>()
                    for (line in lines) {
                        val marker = pageRegex.find(line)
                        if (marker != null) {
                            page = marker.groupValues[1].toIntOrNull() ?: page
                        } else if (line.contains(query, ignoreCase = !isCaseSensitive)) {
                            results.add(page to line.trim().take(180))
                        }
                    }
                    results.take(30)
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Search in this PDF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search text, formulas, definitions...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isCaseSensitive,
                            onCheckedChange = { isCaseSensitive = it }
                        )
                        Text("Case sensitive")
                    }

                    if (query.isNotBlank()) {
                        Text(
                            text = "${searchResults.size} matches found",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        LazyColumn(modifier = Modifier.height(180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(searchResults) { (page, snippet) ->
                                Card(
                                    onClick = {
                                        currentPage = page
                                        coroutineScope.launch { repository.updateReadingProgress(docId, currentPage) }
                                        showSearchDialog = false
                                    },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("Page $page", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        Text(snippet, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchDialog = false }) { Text("Close") }
            }
        )
    }

    // Jump to Page Slider Dialog
    if (showJumpDialog) {
        var sliderPos by remember { mutableFloatStateOf(currentPage.toFloat()) }
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Jump to Page") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Page ${sliderPos.toInt()} of $totalPages",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Slider(
                        value = sliderPos,
                        onValueChange = { sliderPos = it },
                        valueRange = 1f..totalPages.toFloat(),
                        steps = (totalPages - 2).coerceAtLeast(0)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    currentPage = sliderPos.toInt()
                    coroutineScope.launch { repository.updateReadingProgress(docId, currentPage) }
                    showJumpDialog = false
                }) { Text("Jump") }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Real OCR text selection dialog
    if (showTextSelectionDialog) {
        val start = minOf(selectionValue.selection.start, selectionValue.selection.end)
        val end = maxOf(selectionValue.selection.start, selectionValue.selection.end)
        val selectedText = if (start < end && end <= selectionValue.text.length) {
            selectionValue.text.substring(start, end).trim()
        } else {
            ""
        }

        AlertDialog(
            onDismissRequest = {
                showTextSelectionDialog = false
                isOcrLoading = false
            },
            title = { Text("Select text from Page $currentPage") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        isOcrLoading -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Reading this scanned page…")
                            }
                        }
                        ocrError != null -> {
                            Text(
                                text = ocrError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        else -> {
                            Text(
                                "Long-press and drag to select the exact passage you want.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = selectionValue,
                                onValueChange = { selectionValue = it },
                                readOnly = true,
                                minLines = 8,
                                maxLines = 14,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 220.dp, max = 420.dp)
                            )
                            Text(
                                if (selectedText.isBlank()) "Select some text to enable AI actions." else "${selectedText.length} characters selected",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilledTonalButton(
                            enabled = selectedText.isNotBlank(),
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("PDF Text", selectedText))
                                Toast.makeText(context, "Selected text copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Copy") }

                        FilledTonalButton(
                            enabled = selectedText.isNotBlank(),
                            onClick = {
                                showTextSelectionDialog = false
                                coroutineScope.launch {
                                    val explanation = repository.geminiService.explainSelectedText(selectedText, "Simple")
                                    showExplainDialog = Pair("Simple Explanation", explanation)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Explain") }

                        FilledTonalButton(
                            enabled = selectedText.isNotBlank(),
                            onClick = {
                                showTextSelectionDialog = false
                                onNavigateToAi(docId, "Explain and summarize this selected passage: \"${selectedText}\"")
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Ask AI") }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilledTonalButton(
                            enabled = selectedText.isNotBlank(),
                            onClick = {
                                showTextSelectionDialog = false
                                coroutineScope.launch {
                                    val hindi = repository.geminiService.explainSelectedText(selectedText, "Hindi")
                                    showExplainDialog = Pair("Hindi Explanation", hindi)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Hindi") }

                        FilledTonalButton(
                            enabled = selectedText.isNotBlank(),
                            onClick = {
                                showTextSelectionDialog = false
                                coroutineScope.launch {
                                    val hinglish = repository.geminiService.explainSelectedText(selectedText, "Hinglish")
                                    showExplainDialog = Pair("Hinglish", hinglish)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Hinglish") }

                        FilledTonalButton(
                            enabled = selectedText.isNotBlank(),
                            onClick = {
                                showTextSelectionDialog = false
                                coroutineScope.launch {
                                    val front = selectedText
                                        .replace("\\n", " ")
                                        .trim()
                                        .split(Regex("(?<=[.!?])\\s+"))
                                        .firstOrNull()
                                        ?.take(80)
                                        .orEmpty()
                                        .ifBlank { "Selected passage" }
                                    repository.addFlashcard(
                                        FlashcardEntity(
                                            documentId = docId,
                                            front = front,
                                            back = selectedText
                                        )
                                    )
                                    Toast.makeText(context, "Flashcard created from selected text", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("+ Flashcard") }
                    }
                    TextButton(
                        onClick = { showTextSelectionDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("Close") }
                }
            }
        )
    }

    // Smart Selection Explanation Dialog
    if (showExplainDialog != null) {
        val (title, text) = showExplainDialog!!
        AlertDialog(
            onDismissRequest = { showExplainDialog = null },
            title = { Text(title) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    item { Text(text, style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExplainDialog = null }) { Text("Got It") }
            }
        )
    }
}
