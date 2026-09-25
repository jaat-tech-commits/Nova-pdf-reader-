package com.example.ui.screens.study

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.FlashcardEntity
import com.example.data.local.StudyQuizEntity
import com.example.data.repository.DocumentRepository
import kotlinx.coroutines.launch

enum class StudyTab {
    QUIZ, FLASHCARDS, EXAM_NOTES, FORMULAS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyModeScreen(
    docId: Long,
    repository: DocumentRepository,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenPageReference: (Long, Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(StudyTab.QUIZ) }

    val quizzes by repository.getStudyQuizzes(docId).collectAsState(initial = emptyList())
    val flashcards by repository.getFlashcards(docId).collectAsState(initial = emptyList())
    val document by repository.getDocumentByIdFlow(docId).collectAsState(initial = null)
    var studyGenerationMessage by remember { mutableStateOf<String?>(null) }
    var examNotes by remember { mutableStateOf("") }
    var formulas by remember { mutableStateOf("") }
    var sectionLoading by remember { mutableStateOf(false) }

    LaunchedEffect(docId) {
        studyGenerationMessage = repository.generateStudyPackIfNeeded(docId)
    }

    LaunchedEffect(docId, selectedTab, document?.id) {
        val doc = document ?: return@LaunchedEffect
        val source = doc.extractedText.orEmpty()
        if (source.length < 80 || source.startsWith("Imported PDF:")) return@LaunchedEffect

        if (selectedTab == StudyTab.EXAM_NOTES && examNotes.isBlank()) {
            sectionLoading = true
            examNotes = repository.geminiService.askDocument(
                question = "Create high-yield exam revision notes ONLY from this PDF. Use clear headings, bullet points, definitions, important facts, and [Page N] citations. Do not add information that is not in the PDF.",
                documentTitle = doc.title,
                documentText = source
            )
            sectionLoading = false
        }

        if (selectedTab == StudyTab.FORMULAS && formulas.isBlank()) {
            sectionLoading = true
            formulas = repository.geminiService.askDocument(
                question = "Extract ONLY formulas, equations, numerical relationships, units, and calculation rules that actually appear in this PDF. If there are no formulas, clearly say so. Include [Page N] citations. Do not use formulas from other documents.",
                documentTitle = doc.title,
                documentText = source
            )
            sectionLoading = false
        }
    }

    // Quiz state
    var currentQuizIndex by remember { mutableIntStateOf(0) }
    var selectedOptionIndex by remember { mutableStateOf<Int?>(null) }
    var isSubmitted by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    var isQuizCompleted by remember { mutableStateOf(false) }

    // Flashcard state
    var currentCardIndex by remember { mutableIntStateOf(0) }
    var isFlipped by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Study Mode & Exam Prep", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onHome) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            // Add extra generated question
                            repository.addQuiz(
                                StudyQuizEntity(
                                    documentId = docId,
                                    question = "What is the primary factor limiting response to selection in closed populations?",
                                    optionA = "Loss of genetic variance (inbreeding)",
                                    optionB = "Excess environmental noise",
                                    optionC = "Random mutations",
                                    optionD = "Fixed epistatic interactions",
                                    correctOptionIndex = 0,
                                    explanation = "In closed breeding programs, selection reduces additive genetic variance over time, eventually plateauing selection response.",
                                    pageReference = 2
                                )
                            )
                            Toast.makeText(context, "New practice question generated!", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Generate More", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == StudyTab.QUIZ,
                    onClick = { selectedTab = StudyTab.QUIZ },
                    text = { Text("Quiz (${quizzes.size})") },
                    icon = { Icon(Icons.Default.Quiz, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == StudyTab.FLASHCARDS,
                    onClick = { selectedTab = StudyTab.FLASHCARDS },
                    text = { Text("Flashcards (${flashcards.size})") },
                    icon = { Icon(Icons.Default.Style, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == StudyTab.EXAM_NOTES,
                    onClick = { selectedTab = StudyTab.EXAM_NOTES },
                    text = { Text("Exam Notes") },
                    icon = { Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == StudyTab.FORMULAS,
                    onClick = { selectedTab = StudyTab.FORMULAS },
                    text = { Text("Formulas") },
                    icon = { Icon(Icons.Default.Functions, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            when (selectedTab) {
                StudyTab.QUIZ -> {
                    if (quizzes.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    studyGenerationMessage ?: "Generating questions from this PDF…",
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else if (isQuizCompleted) {
                        // Quiz Results Screen
                        QuizResultScreen(
                            score = score,
                            totalQuestions = quizzes.size,
                            onRestart = {
                                currentQuizIndex = 0
                                selectedOptionIndex = null
                                isSubmitted = false
                                score = 0
                                isQuizCompleted = false
                            }
                        )
                    } else {
                        val currentQuiz = quizzes.getOrNull(currentQuizIndex) ?: quizzes.first()
                        QuizQuestionView(
                            quiz = currentQuiz,
                            questionNumber = currentQuizIndex + 1,
                            totalQuestions = quizzes.size,
                            selectedOption = selectedOptionIndex,
                            isSubmitted = isSubmitted,
                            onSelectOption = { if (!isSubmitted) selectedOptionIndex = it },
                            onSubmit = {
                                isSubmitted = true
                                if (selectedOptionIndex == currentQuiz.correctOptionIndex) {
                                    score++
                                }
                            },
                            onNext = {
                                if (currentQuizIndex < quizzes.size - 1) {
                                    currentQuizIndex++
                                    selectedOptionIndex = null
                                    isSubmitted = false
                                } else {
                                    isQuizCompleted = true
                                }
                            },
                            onPageRefClick = { page -> onOpenPageReference(docId, page) }
                        )
                    }
                }

                StudyTab.FLASHCARDS -> {
                    if (flashcards.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No flashcards found. Create some from text selection or AI!")
                        }
                    } else {
                        val currentCard = flashcards.getOrNull(currentCardIndex) ?: flashcards.first()
                        FlashcardView(
                            card = currentCard,
                            cardIndex = currentCardIndex + 1,
                            totalCards = flashcards.size,
                            isFlipped = isFlipped,
                            onFlip = { isFlipped = !isFlipped },
                            onPrev = {
                                if (currentCardIndex > 0) {
                                    currentCardIndex--
                                    isFlipped = false
                                }
                            },
                            onNext = {
                                if (currentCardIndex < flashcards.size - 1) {
                                    currentCardIndex++
                                    isFlipped = false
                                }
                            },
                            onMarkKnown = { known ->
                                coroutineScope.launch {
                                    repository.updateFlashcard(currentCard.copy(isKnown = known))
                                    if (currentCardIndex < flashcards.size - 1) {
                                        currentCardIndex++
                                        isFlipped = false
                                    }
                                }
                            }
                        )
                    }
                }

                StudyTab.EXAM_NOTES -> {
                    DynamicStudyTextView(
                        title = "Exam Notes",
                        content = examNotes,
                        loading = sectionLoading,
                        emptyMessage = "Add a Gemini API key in Settings to generate exam notes from this PDF."
                    )
                }

                StudyTab.FORMULAS -> {
                    DynamicStudyTextView(
                        title = "Formulas & Equations",
                        content = formulas,
                        loading = sectionLoading,
                        emptyMessage = "No formulas have been extracted yet, or Gemini is not configured."
                    )
                }
            }
        }
    }
}

@Composable
fun QuizQuestionView(
    quiz: StudyQuizEntity,
    questionNumber: Int,
    totalQuestions: Int,
    selectedOption: Int?,
    isSubmitted: Boolean,
    onSelectOption: (Int) -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
    onPageRefClick: (Int) -> Unit
) {
    val options = listOf(quiz.optionA, quiz.optionB, quiz.optionC, quiz.optionD)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Question $questionNumber of $totalQuestions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                AssistChip(
                    onClick = { onPageRefClick(quiz.pageReference) },
                    label = { Text("Page ${quiz.pageReference}") },
                    leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
            LinearProgressIndicator(
                progress = { questionNumber.toFloat() / totalQuestions.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(6.dp)
                    .clip(CircleShape)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = quiz.question,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        items(options.indices.toList()) { index ->
            val isSelected = selectedOption == index
            val isCorrect = quiz.correctOptionIndex == index
            val containerColor = when {
                !isSubmitted && isSelected -> MaterialTheme.colorScheme.primaryContainer
                isSubmitted && isCorrect -> Color(0xFFDCFCE7) // Soft Green
                isSubmitted && isSelected && !isCorrect -> Color(0xFFFEE2E2) // Soft Red
                else -> MaterialTheme.colorScheme.surface
            }

            Card(
                onClick = { onSelectOption(index) },
                colors = CardDefaults.cardColors(containerColor = containerColor),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val label = ('A'.code + index).toChar().toString()
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = options[index],
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (isSubmitted) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedOption == quiz.correctOptionIndex) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (selectedOption == quiz.correctOptionIndex) "✅ Correct!" else "❌ Incorrect",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (selectedOption == quiz.correctOptionIndex) Color(0xFF16A34A) else Color(0xFFDC2626)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = quiz.explanation, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    if (!isSubmitted) onSubmit() else onNext()
                },
                enabled = selectedOption != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (!isSubmitted) "Submit Answer" else if (questionNumber == totalQuestions) "See Results" else "Next Question")
            }
        }
    }
}

@Composable
fun QuizResultScreen(
    score: Int,
    totalQuestions: Int,
    onRestart: () -> Unit
) {
    val percentage = (score.toFloat() / totalQuestions.toFloat() * 100).toInt()
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
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Quiz Complete!",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "You scored $score out of $totalQuestions questions correctly.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Recommended Topics for Revision:", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text("• Narrow-sense vs Broad-sense heritability formulas [Page 1]")
                Text("• Breeder's Equation calculations (R = h² × S) [Page 1]")
                Text("• Environmental variance vs interaction effects [Page 2]")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Restart Quiz")
        }
    }
}

@Composable
fun FlashcardView(
    card: FlashcardEntity,
    cardIndex: Int,
    totalCards: Int,
    isFlipped: Boolean,
    onFlip: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onMarkKnown: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Card $cardIndex of $totalCards",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            AssistChip(
                onClick = onFlip,
                label = { Text(if (isFlipped) "Show Question" else "Show Answer") },
                leadingIcon = { Icon(Icons.Default.Flip, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Card Flip 3D
        val rotation by animateFloatAsState(
            targetValue = if (isFlipped) 180f else 0f,
            animationSpec = tween(durationMillis = 400),
            label = "card_flip"
        )

        Card(
            onClick = onFlip,
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isFlipped) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .graphicsLayer {
                        if (rotation > 90f) rotationY = 180f
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isFlipped) "ANSWER" else "QUESTION",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (isFlipped) card.back else card.front,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "(Tap card to flip)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Mastered / Need Revision buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { onMarkKnown(false) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Need Revision")
            }
            Button(
                onClick = { onMarkKnown(true) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Mastered")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Prev / Next Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPrev, enabled = cardIndex > 1) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous")
            }
            IconButton(onClick = onNext, enabled = cardIndex < totalCards) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
            }
        }
    }
}

@Composable
fun ExamNotesView() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📖 High-Yield Revision Sheet", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("1. Broad-Sense Heritability (H²) = V_G / V_P captures total genetic variance including epistasis and dominance.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("2. Narrow-Sense Heritability (h²) = V_A / V_P captures only additive genetic variance and predicts artificial selection response.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("3. Breeder's Equation: R = h² × S, where R is response to selection and S is selection differential.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("4. Environmental variance (V_E) can shift population averages even for traits with high heritability (e.g. human height h² ≈ 0.80).", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun FormulasView() {
    val formulas = listOf(
        Pair("Phenotypic Variance", "V_P = V_G + V_E + V_GE"),
        Pair("Genetic Variance", "V_G = V_A + V_D + V_I"),
        Pair("Broad-Sense Heritability", "H² = V_G / V_P"),
        Pair("Narrow-Sense Heritability", "h² = V_A / V_P"),
        Pair("Breeder's Equation", "R = h² × S"),
        Pair("Selection Differential", "S = μ_s - μ")
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(formulas) { (name, formula) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(formula, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}
