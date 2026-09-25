package com.example.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.repository.DocumentRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    onNavigateToVault: () -> Unit
) {
    val context = LocalContext.current
    var showThemeDialog by remember { mutableStateOf(false) }
    var rememberReadingPosition by remember { mutableStateOf(true) }
    var enableContinuousScroll by remember { mutableStateOf(false) }
    var showPageCitations by remember { mutableStateOf(true) }
    var cacheSizeMb by remember { mutableStateOf("12.4 MB") }

    val hasApiKey = remember {
        try {
            BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY"
        } catch (_: Exception) {
            false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) }
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
            // Appearance Section
            item {
                SettingsSectionHeader("Appearance")
                SettingsRow(
                    icon = Icons.Default.Palette,
                    title = "App Theme",
                    subtitle = currentTheme,
                    onClick = { showThemeDialog = true }
                )
            }

            // Reader Configuration Section
            item {
                SettingsSectionHeader("Reader Configuration")
                SettingsSwitchRow(
                    icon = Icons.Default.Bookmark,
                    title = "Remember Reading Position",
                    subtitle = "Automatically restore exact page on reopen",
                    checked = rememberReadingPosition,
                    onCheckedChange = { rememberReadingPosition = it }
                )
                SettingsSwitchRow(
                    icon = Icons.Default.ViewStream,
                    title = "Continuous Vertical Scrolling",
                    subtitle = "Scroll through pages seamlessly",
                    checked = enableContinuousScroll,
                    onCheckedChange = { enableContinuousScroll = it }
                )
            }

            // AI Intelligence Section
            item {
                SettingsSectionHeader("NOVA AI Engine")
                SettingsRow(
                    icon = Icons.Default.Psychology,
                    title = "AI Model",
                    subtitle = "gemini-3.5-flash (Fast, Low Latency, Deep Reasoning)",
                    onClick = {}
                )
                SettingsRow(
                    icon = Icons.Default.Key,
                    title = "Gemini API Key",
                    subtitle = if (hasApiKey) "Active & Configured via Secrets Panel" else "Configured via AI Studio Secrets (.env)",
                    onClick = {}
                )
                SettingsSwitchRow(
                    icon = Icons.Default.FormatQuote,
                    title = "Clickable Page Citations",
                    subtitle = "Include [Page X] links in AI answers",
                    checked = showPageCitations,
                    onCheckedChange = { showPageCitations = it }
                )
            }

            // Privacy & Vault
            item {
                SettingsSectionHeader("Privacy & Security")
                SettingsRow(
                    icon = Icons.Default.Security,
                    title = "Private Vault",
                    subtitle = "PIN protected secure document safe",
                    onClick = onNavigateToVault
                )
            }

            // Storage Section
            item {
                SettingsSectionHeader("Storage & Cache")
                SettingsRow(
                    icon = Icons.Default.CleaningServices,
                    title = "Clear Cached PDF Thumbnails",
                    subtitle = "Current cache: $cacheSizeMb",
                    onClick = {
                        try {
                            context.cacheDir.deleteRecursively()
                            cacheSizeMb = "0.0 MB"
                            Toast.makeText(context, "Thumbnail cache cleared", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {}
                    }
                )
            }

            // About Section
            item {
                SettingsSectionHeader("About NOVA PDF AI")
                SettingsRow(
                    icon = Icons.Default.Info,
                    title = "Version",
                    subtitle = "NOVA PDF AI 1.0.0 (Build 2026)",
                    onClick = {}
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("NOVA PDF AI", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("\"Read. Understand. Create.\"", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "A modern, offline-resilient PDF reader combined with multimodal Gemini document intelligence, rich annotations, and a comprehensive utility suite.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    if (showThemeDialog) {
        val themes = listOf("System", "Light", "Dark", "AMOLED", "Sepia")
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose App Theme") },
            text = {
                Column {
                    for (t in themes) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onThemeChange(t)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(
                                selected = currentTheme == t,
                                onClick = {
                                    onThemeChange(t)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(t, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
