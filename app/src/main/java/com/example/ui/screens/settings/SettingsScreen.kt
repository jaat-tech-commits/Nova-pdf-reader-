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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    currentAiModel: String,
    onAiModelChange: (String) -> Unit,
    apiKeyConfigured: Boolean,
    onApiKeyChange: (String) -> Unit,
    onNavigateToVault: () -> Unit
) {
    val context = LocalContext.current
    var showThemeDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var rememberReadingPosition by remember { mutableStateOf(true) }
    var enableContinuousScroll by remember { mutableStateOf(false) }
    var showPageCitations by remember { mutableStateOf(true) }
    var cacheSizeMb by remember { mutableStateOf("12.4 MB") }

    val models = listOf(
        "gemini-3.8-flash" to "Gemini 3.8 Flash • Recommended • Fast",
        "gemini-3.7-flash" to "Gemini 3.7 Flash • Strong reasoning",
        "gemini-3.6-flash" to "Gemini 3.6 Flash • Balanced",
        "gemini-3.5-flash" to "Gemini 3.5 Flash • Legacy Flash",
        "gemini-3.5-flash-lite" to "Gemini 3.5 Flash-Lite • Fast & economical",
        "gemini-2.5-flash" to "Gemini 2.5 Flash • Reasoning",
        "gemini-2.5-pro" to "Gemini 2.5 Pro • Advanced reasoning"
    )
    val selectedModelLabel = models.firstOrNull { it.first == currentAiModel }?.second ?: currentAiModel

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
            item {
                SettingsSectionHeader("Appearance")
                SettingsRow(
                    icon = Icons.Default.Palette,
                    title = "App Theme",
                    subtitle = currentTheme,
                    onClick = { showThemeDialog = true }
                )
            }

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

            item {
                SettingsSectionHeader("NOVA AI Engine")
                SettingsRow(
                    icon = Icons.Default.Psychology,
                    title = "AI Model",
                    subtitle = selectedModelLabel,
                    onClick = { showModelDialog = true }
                )
                SettingsRow(
                    icon = Icons.Default.Key,
                    title = "Gemini API Key",
                    subtitle = if (apiKeyConfigured) "Configured • Gemini AI is ready" else "Not configured • Tap to add your Google AI Studio key",
                    onClick = { showApiKeyDialog = true }
                )
                SettingsSwitchRow(
                    icon = Icons.Default.FormatQuote,
                    title = "Clickable Page Citations",
                    subtitle = "Include [Page X] links in AI answers",
                    checked = showPageCitations,
                    onCheckedChange = { showPageCitations = it }
                )
            }

            item {
                SettingsSectionHeader("Privacy & Security")
                SettingsRow(
                    icon = Icons.Default.Security,
                    title = "Private Vault",
                    subtitle = "PIN protected secure document safe",
                    onClick = onNavigateToVault
                )
            }

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

            item {
                SettingsSectionHeader("About NOVA PDF AI")
                SettingsRow(
                    icon = Icons.Default.Info,
                    title = "Version",
                    subtitle = "NOVA PDF AI 1.1.0",
                    onClick = {}
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("NOVA PDF AI", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(""Read. Understand. Create."", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Gemini-powered PDF chat and summaries, document navigation, study tools, annotations, voice input and private storage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(30.dp)) }
        }
    }

    if (showThemeDialog) {
        val themes = listOf("System", "Light", "Dark", "AMOLED", "Sepia")
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose App Theme") },
            text = {
                Column {
                    themes.forEach { theme ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onThemeChange(theme)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(
                                selected = currentTheme == theme,
                                onClick = {
                                    onThemeChange(theme)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(theme, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showModelDialog) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text("Choose Gemini Model") },
            text = {
                Column {
                    models.forEach { (id, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAiModelChange(id)
                                    showModelDialog = false
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            RadioButton(
                                selected = currentAiModel == id,
                                onClick = {
                                    onAiModelChange(id)
                                    showModelDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelDialog = false }) { Text("Close") }
            }
        )
    }

    if (showApiKeyDialog) {
        var keyInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("Gemini API Key") },
            text = {
                Column {
                    Text(
                        "Create a Gemini API key in Google AI Studio and paste it here. The key is stored locally on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("API key") },
                        placeholder = { Text("AIza...") },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    if (apiKeyConfigured) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "A key is already configured. Enter a new key to replace it, or clear it below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onApiKeyChange("")
                    showApiKeyDialog = false
                    Toast.makeText(context, "Gemini API key cleared", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Clear")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = keyInput.trim().isNotBlank(),
                    onClick = {
                        onApiKeyChange(keyInput.trim())
                        showApiKeyDialog = false
                        Toast.makeText(context, "Gemini API key saved", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Save")
                }
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
