package com.example.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R

data class OnboardingPageData(
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector
)

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit
) {
    val pages = listOf(
        OnboardingPageData(
            title = "Meet NOVA PDF AI",
            subtitle = "Read. Understand. Create.",
            description = "A next-generation PDF reader built with modern offline reading, real-time AI document reasoning, and creative tools.",
            icon = Icons.Default.AutoAwesome
        ),
        OnboardingPageData(
            title = "Read PDFs Beautifully",
            subtitle = "Fluid Offline Performance",
            description = "Smooth pinch zoom, continuous vertical scrolling, custom page themes, bookmarks, and rich in-page annotations.",
            icon = Icons.Default.MenuBook
        ),
        OnboardingPageData(
            title = "Ask AI Anything",
            subtitle = "Ground Truth Page Citations",
            description = "Ask questions, generate deep summaries, explain complex passages in Hindi or Hinglish, with clickable [Page X] references.",
            icon = Icons.Default.Psychology
        ),
        OnboardingPageData(
            title = "Study Smarter",
            subtitle = "Quizzes & Flashcards",
            description = "Turn any textbook or research document into interactive revision quizzes, formula sheets, and study flashcards.",
            icon = Icons.Default.School
        )
    )

    var currentPageIndex by remember { mutableIntStateOf(0) }
    val isLastPage = currentPageIndex == pages.size - 1

    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Page Indicator Dots
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in pages.indices) {
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(if (i == currentPageIndex) 24.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == currentPageIndex) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                )
                        )
                    }
                }

                // Next or Start Reading Button
                Button(
                    onClick = {
                        if (isLastPage) onFinish()
                        else currentPageIndex++
                    },
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(if (isLastPage) "Start Reading" else "Next")
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (isLastPage) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val page = pages[currentPageIndex]

            // Top skip button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (!isLastPage) {
                    TextButton(onClick = onFinish) {
                        Text("Skip")
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // Hero Art or Icon
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(140.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = page.icon,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = page.subtitle,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = page.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
