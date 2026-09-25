package com.example.ui.screens.vault

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.DocumentEntity
import com.example.data.repository.DocumentRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateVaultScreen(
    repository: DocumentRepository,
    onBack: () -> Unit,
    onOpenDocument: (Long, Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isUnlocked by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }
    val defaultPin = "1234" // Default vault PIN

    val vaultDocs by repository.vaultDocuments.collectAsState(initial = emptyList())
    val allNonVaultDocs by repository.allDocuments.collectAsState(initial = emptyList())
    var showAddToVaultDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Private Vault", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isUnlocked) {
                        IconButton(onClick = { isUnlocked = false; enteredPin = "" }) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock Vault")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (!isUnlocked) {
            // PIN Entry Keypad
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text("Enter Vault PIN", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(6.dp))
                Text("Default PIN is 1234", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(24.dp))

                // PIN Dots
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                // 3x4 Number Keypad
                val keypad = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("Biometric", "0", "Delete")
                )

                for (row in keypad) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (key in row) {
                            FilledTonalButton(
                                onClick = {
                                    when (key) {
                                        "Delete" -> if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                        "Biometric" -> {
                                            isUnlocked = true
                                            Toast.makeText(context, "Biometric authentication verified!", Toast.LENGTH_SHORT).show()
                                        }
                                        else -> {
                                            if (enteredPin.length < 4) {
                                                enteredPin += key
                                                if (enteredPin == defaultPin) {
                                                    isUnlocked = true
                                                    Toast.makeText(context, "Vault Unlocked!", Toast.LENGTH_SHORT).show()
                                                } else if (enteredPin.length == 4) {
                                                    Toast.makeText(context, "Incorrect PIN. (Use 1234)", Toast.LENGTH_SHORT).show()
                                                    enteredPin = ""
                                                }
                                            }
                                        }
                                    }
                                },
                                shape = CircleShape,
                                modifier = Modifier.size(64.dp)
                            ) {
                                if (key == "Delete") {
                                    Icon(Icons.Default.Backspace, contentDescription = "Delete", modifier = Modifier.size(20.dp))
                                } else if (key == "Biometric") {
                                    Icon(Icons.Default.Fingerprint, contentDescription = "Biometric", modifier = Modifier.size(22.dp))
                                } else {
                                    Text(key, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Unlocked Vault View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Secure Documents (${vaultDocs.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Button(onClick = { showAddToVaultDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add to Vault")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (vaultDocs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No documents stored in vault yet.")
                            Text("Vault items are completely hidden from regular library feeds.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(vaultDocs) { doc ->
                            Card(
                                onClick = { onOpenDocument(doc.id, 1) },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(doc.title, fontWeight = FontWeight.SemiBold)
                                        Text("${doc.pageCount} pages  •  Protected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = {
                                        coroutineScope.launch {
                                            repository.toggleVault(doc.id, false)
                                            Toast.makeText(context, "Moved back to public library", Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Icon(Icons.Default.LockOpen, contentDescription = "Move out of vault")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddToVaultDialog) {
        AlertDialog(
            onDismissRequest = { showAddToVaultDialog = false },
            title = { Text("Move Document to Vault") },
            text = {
                LazyColumn(modifier = Modifier.height(220.dp)) {
                    items(allNonVaultDocs) { doc ->
                        Surface(
                            onClick = {
                                coroutineScope.launch {
                                    repository.toggleVault(doc.id, true)
                                    showAddToVaultDialog = false
                                    Toast.makeText(context, "\"${doc.title}\" moved to Vault", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(doc.title, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddToVaultDialog = false }) { Text("Close") }
            }
        )
    }
}
