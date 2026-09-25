package com.example.ui.screens.library

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.DocumentEntity
import com.example.data.repository.DocumentRepository
import com.example.ui.screens.home.DocumentItemRow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class LibraryCategory {
    ALL, RECENT, FAVORITES, DOWNLOADS, FOLDERS, OFFLINE
}

enum class SortOption {
    NAME, DATE, SIZE, LAST_OPENED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    repository: DocumentRepository,
    onOpenDocument: (Long, Int) -> Unit,
    onNavigateToStudy: (Long) -> Unit,
    onNavigateToAi: (Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allDocs by repository.allDocuments.collectAsState(initial = emptyList())
    val folders by repository.folders.collectAsState(initial = emptyList())

    var selectedCategory by remember { mutableStateOf(LibraryCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(SortOption.LAST_OPENED) }
    var sortAscending by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }

    // Multi-selection state
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedDocIds = remember { mutableStateListOf<Long>() }

    // System PDF Picker
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Document.pdf"
                val cleanName = if (fileName.endsWith(".pdf", ignoreCase = true)) fileName else "$fileName.pdf"
                val doc = repository.importDocument(uri, cleanName)
                onOpenDocument(doc.id, 1)
            }
        }
    }

    // Filter documents by category & search query
    val filteredDocs = remember(allDocs, selectedCategory, searchQuery, sortOption, sortAscending) {
        var list = when (selectedCategory) {
            LibraryCategory.ALL -> allDocs
            LibraryCategory.RECENT -> allDocs.sortedByDescending { it.lastOpenedTimestamp }
            LibraryCategory.FAVORITES -> allDocs.filter { it.isFavorite }
            LibraryCategory.DOWNLOADS -> allDocs.filter { it.folderName == "Downloads" || it.title.contains("Download", ignoreCase = true) }
            LibraryCategory.FOLDERS -> allDocs
            LibraryCategory.OFFLINE -> allDocs // All files in repository are local & offline
        }

        if (searchQuery.isNotBlank()) {
            list = list.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }

        when (sortOption) {
            SortOption.NAME -> if (sortAscending) list.sortedBy { it.title.lowercase() } else list.sortedByDescending { it.title.lowercase() }
            SortOption.DATE -> if (sortAscending) list.sortedBy { it.dateAdded } else list.sortedByDescending { it.dateAdded }
            SortOption.SIZE -> if (sortAscending) list.sortedBy { it.fileSize } else list.sortedByDescending { it.fileSize }
            SortOption.LAST_OPENED -> if (sortAscending) list.sortedBy { it.lastOpenedTimestamp } else list.sortedByDescending { it.lastOpenedTimestamp }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isMultiSelectMode) {
                        Text("${selectedDocIds.size} Selected")
                    } else {
                        Text("Document Library", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    if (isMultiSelectMode) {
                        IconButton(onClick = {
                            isMultiSelectMode = false
                            selectedDocIds.clear()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel Multi-select")
                        }
                    }
                },
                actions = {
                    if (isMultiSelectMode) {
                        // Multi-select bulk actions
                        IconButton(onClick = {
                            coroutineScope.launch {
                                for (id in selectedDocIds) {
                                    repository.toggleFavorite(id, true)
                                }
                                isMultiSelectMode = false
                                selectedDocIds.clear()
                            }
                        }) {
                            Icon(Icons.Default.Star, contentDescription = "Favorite All")
                        }
                        IconButton(onClick = {
                            coroutineScope.launch {
                                for (id in selectedDocIds) {
                                    repository.deleteDocument(id)
                                }
                                isMultiSelectMode = false
                                selectedDocIds.clear()
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete All", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        // Normal top bar actions
                        IconButton(onClick = { isGridView = !isGridView }) {
                            Icon(
                                imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = "Toggle Grid/List"
                            )
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Name ${if (sortOption == SortOption.NAME) "✓" else ""}") },
                                    onClick = {
                                        sortOption = SortOption.NAME
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Last Opened ${if (sortOption == SortOption.LAST_OPENED) "✓" else ""}") },
                                    onClick = {
                                        sortOption = SortOption.LAST_OPENED
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Date Added ${if (sortOption == SortOption.DATE) "✓" else ""}") },
                                    onClick = {
                                        sortOption = SortOption.DATE
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("File Size ${if (sortOption == SortOption.SIZE) "✓" else ""}") },
                                    onClick = {
                                        sortOption = SortOption.SIZE
                                        showSortMenu = false
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(if (sortAscending) "Switch to Descending" else "Switch to Ascending") },
                                    onClick = {
                                        sortAscending = !sortAscending
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Import PDF")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("library_search_input"),
                placeholder = { Text("Search by document title...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )

            // Category Filter Pills
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(LibraryCategory.values()) { category ->
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = {
                            Text(
                                when (category) {
                                    LibraryCategory.ALL -> "All PDFs"
                                    LibraryCategory.RECENT -> "Recent"
                                    LibraryCategory.FAVORITES -> "Favorites"
                                    LibraryCategory.DOWNLOADS -> "Downloads"
                                    LibraryCategory.FOLDERS -> "Folders"
                                    LibraryCategory.OFFLINE -> "Offline"
                                }
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = when (category) {
                                    LibraryCategory.ALL -> Icons.Default.AllInclusive
                                    LibraryCategory.RECENT -> Icons.Default.History
                                    LibraryCategory.FAVORITES -> Icons.Default.Star
                                    LibraryCategory.DOWNLOADS -> Icons.Default.Download
                                    LibraryCategory.FOLDERS -> Icons.Default.Folder
                                    LibraryCategory.OFFLINE -> Icons.Default.CloudDone
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            // If Folders category is selected, show folders row + "New Folder" button
            if (selectedCategory == LibraryCategory.FOLDERS) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Folders (${folders.size})", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { showNewFolderDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Folder")
                    }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(folders) { folder ->
                        AssistChip(
                            onClick = { /* Filter by folder */ },
                            label = { Text(folder.name) },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        )
                    }
                }
            }

            // Empty state or list
            if (filteredDocs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No PDFs match '$searchQuery'" else "No documents in this category",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Import a PDF or tap + to get started.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredDocs, key = { it.id }) { doc ->
                        DocumentGridCard(
                            doc = doc,
                            isSelected = selectedDocIds.contains(doc.id),
                            isMultiSelect = isMultiSelectMode,
                            onOpen = {
                                if (isMultiSelectMode) {
                                    if (selectedDocIds.contains(doc.id)) selectedDocIds.remove(doc.id)
                                    else selectedDocIds.add(doc.id)
                                } else {
                                    onOpenDocument(doc.id, doc.lastPageRead)
                                }
                            },
                            onLongPress = {
                                isMultiSelectMode = true
                                if (!selectedDocIds.contains(doc.id)) selectedDocIds.add(doc.id)
                            },
                            onFavorite = { coroutineScope.launch { repository.toggleFavorite(doc.id, !doc.isFavorite) } }
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredDocs, key = { it.id }) { doc ->
                        DocumentItemRow(
                            doc = doc,
                            onOpen = { onOpenDocument(doc.id, doc.lastPageRead) },
                            onFavorite = { coroutineScope.launch { repository.toggleFavorite(doc.id, !doc.isFavorite) } },
                            onDelete = { coroutineScope.launch { repository.deleteDocument(doc.id) } },
                            onRename = { /* Handle rename */ },
                            onShare = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, Uri.fromFile(File(doc.filePath)))
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share PDF"))
                            },
                            onStudy = { onNavigateToStudy(doc.id) },
                            onAiChat = { onNavigateToAi(doc.id) }
                        )
                    }
                }
            }
        }
    }

    // New Folder Dialog
    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Create Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (folderName.isNotBlank()) {
                            coroutineScope.launch {
                                repository.addFolder(folderName.trim())
                                showNewFolderDialog = false
                            }
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun DocumentGridCard(
    doc: DocumentEntity,
    isSelected: Boolean,
    isMultiSelect: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onFavorite: () -> Unit
) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("doc_grid_${doc.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (doc.thumbnailPath != null && File(doc.thumbnailPath).exists()) {
                    AsyncImage(
                        model = File(doc.thumbnailPath),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }

                // Favorite badge
                IconButton(
                    onClick = onFavorite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = if (doc.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (doc.isFavorite) Color(0xFFF59E0B) else Color.White
                    )
                }

                // Multi-select check icon
                if (isMultiSelect) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = doc.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${doc.pageCount} pages",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
