package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.VaultItemEntity
import com.example.domain.model.VaultAuthMode
import com.example.ui.ExplorerUiState
import com.example.ui.LumaFormatUtils

@Composable
fun SecureVaultScreen(
    state: ExplorerUiState,
    vaultItems: List<VaultItemEntity>,
    onSetupCredential: (secret: String, mode: VaultAuthMode, question: String, answer: String) -> Unit,
    onUnlockVault: (String) -> Unit,
    onLockVault: () -> Unit,
    onResetWithRecovery: (answer: String, newSecret: String, newMode: VaultAuthMode) -> Unit,
    onOpenVaultItem: (VaultItemEntity) -> Unit,
    onRestoreVaultItem: (VaultItemEntity) -> Unit,
    onDeleteVaultItem: (VaultItemEntity) -> Unit,
    onGoToExplorerToLockFiles: () -> Unit
) {
    when {
        !state.isPinConfigured -> {
            VaultSetupCard(
                authError = state.vaultAuthError,
                onSetupCredential = onSetupCredential
            )
        }
        !state.isVaultUnlocked -> {
            VaultUnlockCard(
                state = state,
                onUnlock = onUnlockVault,
                onResetWithRecovery = onResetWithRecovery
            )
        }
        else -> {
            VaultUnlockedFileList(
                vaultItems = vaultItems,
                onLockVault = onLockVault,
                onOpenVaultItem = onOpenVaultItem,
                onRestoreVaultItem = onRestoreVaultItem,
                onDeleteVaultItem = onDeleteVaultItem,
                onAddFiles = onGoToExplorerToLockFiles
            )
        }
    }
}

@Composable
private fun VaultSetupCard(
    authError: String?,
    onSetupCredential: (String, VaultAuthMode, String, String) -> Unit
) {
    var mode by remember { mutableStateOf(VaultAuthMode.PIN_4) }
    var secret by remember { mutableStateOf("") }
    var confirmSecret by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("First pet or childhood city?") }
    var answer by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("vault_setup_screen"),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column {
                    Text("Set Up Vault", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Encrypt private files with AES-256", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VaultAuthMode.entries.forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { mode = m; secret = ""; confirmSecret = "" },
                        label = { Text(m.label) }
                    )
                }
            }
        }

        item {
            val isNumeric = mode != VaultAuthMode.ALPHANUMERIC_PASSWORD
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = if (isNumeric) it.filter(Char::isDigit).take(6) else it },
                label = { Text(mode.label) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = if (isNumeric) KeyboardType.NumberPassword else KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vault_setup_secret_input")
            )
        }

        item {
            val isNumeric = mode != VaultAuthMode.ALPHANUMERIC_PASSWORD
            OutlinedTextField(
                value = confirmSecret,
                onValueChange = { confirmSecret = if (isNumeric) it.filter(Char::isDigit).take(6) else it },
                label = { Text("Confirm ${mode.label}") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = if (isNumeric) KeyboardType.NumberPassword else KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                label = { Text("Recovery Question") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = answer,
                onValueChange = { answer = it },
                label = { Text("Recovery Answer") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        val err = localError ?: authError
        if (!err.isNullOrBlank()) {
            item {
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            Button(
                onClick = {
                    if (secret != confirmSecret) {
                        localError = "Entries do not match"
                    } else if (answer.isBlank()) {
                        localError = "Enter a recovery answer"
                    } else {
                        localError = null
                        onSetupCredential(secret, mode, question, answer)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vault_create_btn")
            ) {
                Text("Create Vault")
            }
        }
    }
}

@Composable
private fun VaultUnlockCard(
    state: ExplorerUiState,
    onUnlock: (String) -> Unit,
    onResetWithRecovery: (String, String, VaultAuthMode) -> Unit
) {
    var inputSecret by remember { mutableStateOf("") }
    var showRecovery by remember { mutableStateOf(false) }
    var recoveryAnswer by remember { mutableStateOf("") }
    var newSecret by remember { mutableStateOf("") }
    var newMode by remember { mutableStateOf(state.vaultAuthMode) }

    val isNumeric = state.vaultAuthMode != VaultAuthMode.ALPHANUMERIC_PASSWORD

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .testTag("vault_unlock_screen"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
                }

                Text(
                    text = if (showRecovery) "Recover Vault" else "Unlock Vault",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (!showRecovery) {
                    OutlinedTextField(
                        value = inputSecret,
                        onValueChange = { inputSecret = if (isNumeric) it.filter(Char::isDigit).take(6) else it },
                        label = { Text(state.vaultAuthMode.label) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = if (isNumeric) KeyboardType.NumberPassword else KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vault_unlock_input")
                    )

                    state.vaultAuthError?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = { onUnlock(inputSecret) },
                        enabled = inputSecret.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vault_unlock_btn")
                    ) {
                        Icon(Icons.Default.LockOpen, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Unlock")
                    }

                    TextButton(onClick = { showRecovery = true }) {
                        Text("Forgot ${state.vaultAuthMode.label}?")
                    }
                } else {
                    Text(
                        text = state.recoveryQuestion.ifBlank { "Security Question" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = recoveryAnswer,
                        onValueChange = { recoveryAnswer = it },
                        label = { Text("Recovery Answer") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VaultAuthMode.entries.forEach { m ->
                            FilterChip(
                                selected = newMode == m,
                                onClick = { newMode = m; newSecret = "" },
                                label = { Text(m.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = newSecret,
                        onValueChange = { newSecret = if (newMode != VaultAuthMode.ALPHANUMERIC_PASSWORD) it.filter(Char::isDigit).take(6) else it },
                        label = { Text("New ${newMode.label}") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.vaultAuthError?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { showRecovery = false }, modifier = Modifier.weight(1f)) {
                            Text("Back")
                        }
                        Button(
                            onClick = { onResetWithRecovery(recoveryAnswer, newSecret, newMode) },
                            enabled = recoveryAnswer.isNotBlank() && newSecret.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultUnlockedFileList(
    vaultItems: List<VaultItemEntity>,
    onLockVault: () -> Unit,
    onOpenVaultItem: (VaultItemEntity) -> Unit,
    onRestoreVaultItem: (VaultItemEntity) -> Unit,
    onDeleteVaultItem: (VaultItemEntity) -> Unit,
    onAddFiles: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("vault_unlocked_screen")
    ) {
        // Minimal Unlocked Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Vault", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${vaultItems.size} encrypted file${if (vaultItems.size == 1) "" else "s"} · Tap to view",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAddFiles) { Text("Add") }
                Button(onClick = onLockVault, modifier = Modifier.testTag("vault_lock_btn")) {
                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Lock")
                }
            }
        }

        if (vaultItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Text("No locked files", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onAddFiles) { Text("Browse Files to Lock") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(vaultItems, key = { it.id }) { item ->
                    VaultItemRow(
                        item = item,
                        onOpen = { onOpenVaultItem(item) },
                        onRestore = { onRestoreVaultItem(item) },
                        onDelete = { onDeleteVaultItem(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultItemRow(
    item: VaultItemEntity,
    onOpen: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val ext = item.originalName.substringAfterLast('.', "").lowercase()
    val (icon, tint) = when {
        ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp") -> Icons.Default.Image to Color(0xFFEC4899)
        ext in setOf("mp4", "mkv", "mov", "webm") -> Icons.Default.VideoFile to Color(0xFF8B5CF6)
        ext in setOf("mp3", "wav", "m4a", "flac", "ogg", "aac") -> Icons.Default.AudioFile to Color(0xFF06B6D4)
        ext == "pdf" -> Icons.Default.PictureAsPdf to Color(0xFFEF4444)
        else -> Icons.AutoMirrored.Filled.InsertDriveFile to MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag("vault_item_${item.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.originalName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${LumaFormatUtils.formatBytes(item.originalSizeBytes)} · ${LumaFormatUtils.formatCompactDate(item.lockedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options")
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Restore to Storage") },
                    leadingIcon = { Icon(Icons.Default.Restore, null) },
                    onClick = { showMenu = false; onRestore() }
                )
                DropdownMenuItem(
                    text = { Text("Delete Permanently", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; onDelete() }
                )
            }
        }
    }
}
