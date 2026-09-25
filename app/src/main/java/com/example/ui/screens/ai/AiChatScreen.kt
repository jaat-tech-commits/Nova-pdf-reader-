package com.example.ui.screens.ai

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AiChatMessageEntity
import com.example.data.local.DocumentEntity
import com.example.data.repository.DocumentRepository
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    initialDocId: Long?,
    initialPrompt: String?,
    repository: DocumentRepository,
    onBack: (() -> Unit)?,
    onOpenPageReference: (Long, Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allDocs by repository.allDocuments.collectAsState(initial = emptyList())

    // Selected document
    var selectedDocId by remember {
        mutableStateOf(initialDocId ?: allDocs.firstOrNull()?.id ?: 0L)
    }

    LaunchedEffect(allDocs) {
        if (selectedDocId == 0L && allDocs.isNotEmpty()) {
            selectedDocId = initialDocId ?: allDocs.first().id
        }
    }

    val selectedDoc = remember(allDocs, selectedDocId) {
        allDocs.firstOrNull { it.id == selectedDocId }
    }

    val messages by repository.getAiChatMessages(selectedDocId).collectAsState(initial = emptyList())
    var inputText by remember { mutableStateOf(initialPrompt ?: "") }
    var isLoading by remember { mutableStateOf(false) }
    var showSummarySheet by remember { mutableStateOf(false) }
    var docPickerExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Voice recognition launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                inputText = spoken
            }
        }
    }

    // Auto-send initial prompt if provided
    LaunchedEffect(initialPrompt, selectedDocId) {
        if (!initialPrompt.isNullOrBlank() && selectedDocId > 0) {
            isLoading = true
            repository.sendAiMessage(selectedDocId, initialPrompt)
            isLoading = false
            inputText = ""
        }
    }

    // Scroll to bottom when messages update
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val quickPrompts = listOf(
        "Summarize this PDF",
        "Explain this PDF simply",
        "What are the important points?",
        "Find important definitions",
        "Create exam notes",
        "Generate 5 MCQs",
        "Find important formulas",
        "Explain in Hindi",
        "Explain in Hinglish"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Ask Your PDF", fontWeight = FontWeight.Bold)
                        // Document selector dropdown trigger
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { docPickerExpanded = true }
                        ) {
                            Text(
                                text = selectedDoc?.title ?: "Select a Document",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }

                        DropdownMenu(
                            expanded = docPickerExpanded,
                            onDismissRequest = { docPickerExpanded = false }
                        ) {
                            for (doc in allDocs) {
                                DropdownMenuItem(
                                    text = { Text(doc.title, maxLines = 1) },
                                    onClick = {
                                        selectedDocId = doc.id
                                        docPickerExpanded = false
                                    }
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { showSummarySheet = true },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("✨ Summarize", style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            repository.clearAiChatHistory(selectedDocId)
                            Toast.makeText(context, "Chat history cleared", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Clear History")
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(bottom = 8.dp)
            ) {
                // Quick prompt suggestions
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quickPrompts) { prompt ->
                        SuggestionChip(
                            onClick = {
                                if (!isLoading && selectedDocId > 0) {
                                    coroutineScope.launch {
                                        isLoading = true
                                        repository.sendAiMessage(selectedDocId, prompt)
                                        isLoading = false
                                    }
                                }
                            },
                            label = { Text(prompt, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Input field + Voice button + Send button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_input_field"),
                        placeholder = { Text("Ask about this PDF...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Voice Microphone Button
                    IconButton(
                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask a question about your PDF...")
                            }
                            try {
                                speechLauncher.launch(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Voice recognition not available on this device", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Send Button
                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank() && !isLoading && selectedDocId > 0) {
                                val textToSend = inputText
                                inputText = ""
                                coroutineScope.launch {
                                    isLoading = true
                                    repository.sendAiMessage(selectedDocId, textToSend)
                                    isLoading = false
                                }
                            }
                        },
                        enabled = inputText.isNotBlank() && !isLoading,
                        modifier = Modifier.testTag("ai_send_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
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
        ) {
            if (messages.isEmpty() && !isLoading) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Psychology,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Ask anything about \"${selectedDoc?.title ?: "your document"}\"",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "I will answer directly from your PDF and provide clickable citations [Page X] for quick verification.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(messages, key = { it.id }) { msg ->
                        AiMessageBubble(
                            message = msg,
                            onCitationClick = { pageNum ->
                                onOpenPageReference(selectedDocId, pageNum)
                            },
                            onCopy = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("AI Answer", msg.content))
                                Toast.makeText(context, "Copied answer", Toast.LENGTH_SHORT).show()
                            },
                            onShare = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, msg.content)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share AI Answer"))
                            },
                            onRegenerate = {
                                coroutineScope.launch {
                                    isLoading = true
                                    repository.sendAiMessage(selectedDocId, "Please explain this in a different way with more details.")
                                    isLoading = false
                                }
                            }
                        )
                    }

                    if (isLoading) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Analyzing document and preparing answer...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ✨ Summarize Sheet
    if (showSummarySheet) {
        val summaryOptions = listOf(
            "Quick Summary",
            "Detailed Summary",
            "One-page Summary",
            "Chapter Summary",
            "Key Points",
            "Exam Notes",
            "5-Minute Revision"
        )
        ModalBottomSheet(onDismissRequest = { showSummarySheet = false }) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "✨ AI Document Summaries",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Select summary style for \"${selectedDoc?.title ?: ""}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                for (opt in summaryOptions) {
                    Surface(
                        onClick = {
                            showSummarySheet = false
                            coroutineScope.launch {
                                isLoading = true
                                repository.sendAiMessage(selectedDocId, "Generate a $opt of this document.")
                                isLoading = false
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(opt, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun AiMessageBubble(
    message: AiChatMessageEntity,
    onCitationClick: (Int) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit
) {
    val isUser = message.role == "user"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Icon(
                imageVector = if (isUser) Icons.Default.Person else Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = if (isUser) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isUser) "You" else "NOVA AI",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isUser) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
            )
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Parse citations e.g. [Page X]
                FormattedMessageText(text = message.content, onCitationClick = onCitationClick)

                // Extra citation pills if present
                if (!isUser) {
                    val citations = remember(message.citationsJson) {
                        try {
                            val arr = org.json.JSONArray(message.citationsJson)
                            (0 until arr.length()).map { arr.getInt(it) }
                        } catch (_: Exception) {
                            emptyList<Int>()
                        }
                    }
                    if (citations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Cited Pages:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            for (p in citations) {
                                SuggestionChip(
                                    onClick = { onCitationClick(p) },
                                    label = { Text("Page $p") },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    )
                                )
                            }
                        }
                    }

                    // Action buttons: Copy, Share, Regenerate
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                        }
                        IconButton(onClick = onShare, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(14.dp))
                        }
                        IconButton(onClick = onRegenerate, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Regenerate", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FormattedMessageText(text: String, onCitationClick: (Int) -> Unit) {
    // Look for [Page X] patterns and make them clickable
    val regex = Regex("\\[Page (\\d+)\\]")
    var lastIndex = 0
    val matches = regex.findAll(text).toList()

    if (matches.isEmpty()) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
        return
    }

    Column {
        var cursor = 0
        for (match in matches) {
            val beforeText = text.substring(cursor, match.range.first)
            if (beforeText.isNotEmpty()) {
                Text(text = beforeText, style = MaterialTheme.typography.bodyMedium)
            }
            val pageNum = match.groupValues[1].toIntOrNull() ?: 1
            AssistChip(
                onClick = { onCitationClick(pageNum) },
                label = { Text("📄 Page $pageNum", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.padding(vertical = 2.dp)
            )
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            Text(text = text.substring(cursor), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
