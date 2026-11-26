package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.VaultItemEntity
import com.example.domain.model.VaultAuthMode
import com.example.ui.ExplorerUiState
import com.example.ui.LumaFormatUtils
import com.example.ui.theme.EmeraldVault
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun SecureVaultScreen(
    state: ExplorerUiState,
    vaultItems: List<VaultItemEntity>,
    onSetupCredential: (secret: String, mode: VaultAuthMode, recoveryQ: String, recoveryA: String) -> Unit,
    onUnlockVault: (secret: String) -> Unit,
    onLockVault: () -> Unit,
    onResetWithRecovery: (answer: String, newSecret: String, newMode: VaultAuthMode) -> Unit,
    onRestoreVaultItem: (VaultItemEntity) -> Unit,
    onGoToExplorerToLockFiles: () -> Unit
) {
    if (!state.isPinConfigured) {
        VaultCredentialSetupPane(
            authError = state.vaultAuthError,
            onSetupCredential = onSetupCredential
        )
    } else if (!state.isVaultUnlocked) {
        VaultUnlockPane(
            authMode = state.vaultAuthMode,
            pinLength = state.configuredPinLength,
            recoveryQuestion = state.recoveryQuestion,
            authError = state.vaultAuthError,
            onUnlock = onUnlockVault,
            onResetWithRecovery = onResetWithRecovery
        )
    } else {
        UnlockedVaultDashboardPane(
            vaultItems = vaultItems,
            authMode = state.vaultAuthMode,
            onLockVault = onLockVault,
            onRestoreItem = onRestoreVaultItem,
            onGoToExplorer = onGoToExplorerToLockFiles
        )
    }
}

@Composable
private fun VaultCredentialSetupPane(
    authError: String?,
    onSetupCredential: (String, VaultAuthMode, String, String) -> Unit
) {
    var selectedMode by remember { mutableStateOf(VaultAuthMode.PIN_4) }
    var enteredSecret by remember { mutableStateOf("") }
    var recoveryQuestion by remember { mutableStateOf("What is your favorite security keyword?") }
    var recoveryAnswer by remember { mutableStateOf("LumaVault") }

    val targetPinLength = when (selectedMode) {
        VaultAuthMode.PIN_4 -> 4
        VaultAuthMode.PIN_6 -> 6
        VaultAuthMode.ALPHANUMERIC_PASSWORD -> 0
    }

    val isCredentialReady = when (selectedMode) {
        VaultAuthMode.PIN_4 -> enteredSecret.length == 4 && enteredSecret.all { it.isDigit() }
        VaultAuthMode.PIN_6 -> enteredSecret.length == 6 && enteredSecret.all { it.isDigit() }
        VaultAuthMode.ALPHANUMERIC_PASSWORD -> enteredSecret.length >= 6
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("vault_pin_setup_pane"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(EmeraldVault.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.EnhancedEncryption,
                    contentDescription = "AES-256 Vault",
                    tint = EmeraldVault,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        item {
            Text(
                text = "Initialize Isolated AES-256 Safe Folder",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "210,000 PBKDF2-HMAC-SHA256 • Isolated noBackupFilesDir • Opaque UUID (.lvault) • Zero-Overwrite Shredding",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Locked files are moved into app-private non-backed-up storage with randomized UUID names and zero-shredded from public folders so no external file manager can view or recover them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VaultAuthMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = {
                            selectedMode = mode
                            enteredSecret = ""
                        },
                        label = { Text(mode.label) }
                    )
                }
            }
        }

        if (authError != null) {
            item {
                Text(
                    text = authError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (selectedMode == VaultAuthMode.ALPHANUMERIC_PASSWORD) {
            item {
                OutlinedTextField(
                    value = enteredSecret,
                    onValueChange = { enteredSecret = it },
                    label = { Text("Alphanumeric Master Password (6+ chars)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_password_setup_input")
                )
            }
        } else {
            item {
                PinDotsIndicator(enteredLength = enteredSecret.length, totalLength = targetPinLength)
            }
            item {
                NumericPinPad(
                    onDigitTap = { digit ->
                        if (enteredSecret.length < targetPinLength) {
                            enteredSecret += digit
                        }
                    },
                    onBackspace = {
                        if (enteredSecret.isNotEmpty()) {
                            enteredSecret = enteredSecret.dropLast(1)
                        }
                    }
                )
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Credential Recovery Security Question",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = recoveryQuestion,
                        onValueChange = { recoveryQuestion = it },
                        label = { Text("Security Question") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = recoveryAnswer,
                        onValueChange = { recoveryAnswer = it },
                        label = { Text("Recovery Answer") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            Button(
                onClick = {
                    onSetupCredential(enteredSecret, selectedMode, recoveryQuestion, recoveryAnswer)
                },
                enabled = isCredentialReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("activate_vault_pin_btn")
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Activate AES-256 Safe Vault (${selectedMode.label})")
            }
        }
    }
}

@Composable
private fun VaultUnlockPane(
    authMode: VaultAuthMode,
    pinLength: Int,
    recoveryQuestion: String,
    authError: String?,
    onUnlock: (String) -> Unit,
    onResetWithRecovery: (String, String, VaultAuthMode) -> Unit
) {
    var enteredSecret by remember { mutableStateOf("") }
    var showRecoveryForm by remember { mutableStateOf(false) }
    var recoveryAnswer by remember { mutableStateOf("") }
    var newSecret by remember { mutableStateOf("") }
    var recoveryMode by remember { mutableStateOf(authMode) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .testTag("vault_unlock_pane"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Vault Locked",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(52.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (authMode == VaultAuthMode.ALPHANUMERIC_PASSWORD) {
                "Enter Master Password"
            } else {
                "Enter $pinLength-Digit Safe Folder PIN"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "210,000-Iteration PBKDF2 • Isolated .lvault Storage",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )

        if (authError != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = authError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (!showRecoveryForm) {
            if (authMode == VaultAuthMode.ALPHANUMERIC_PASSWORD) {
                OutlinedTextField(
                    value = enteredSecret,
                    onValueChange = { enteredSecret = it },
                    label = { Text("Master Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_password_unlock_input")
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        onUnlock(enteredSecret)
                        enteredSecret = ""
                    },
                    enabled = enteredSecret.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_password_unlock_btn")
                ) {
                    Text("Unlock AES-256 Safe Vault")
                }
            } else {
                PinDotsIndicator(enteredLength = enteredSecret.length, totalLength = pinLength)
                Spacer(modifier = Modifier.height(18.dp))
                NumericPinPad(
                    onDigitTap = { digit ->
                        if (enteredSecret.length < pinLength) {
                            enteredSecret += digit
                            if (enteredSecret.length == pinLength) {
                                onUnlock(enteredSecret)
                                enteredSecret = ""
                            }
                        }
                    },
                    onBackspace = {
                        if (enteredSecret.isNotEmpty()) {
                            enteredSecret = enteredSecret.dropLast(1)
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = { showRecoveryForm = true }) {
                Text("Forgot Credential? Use Recovery Security Answer")
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = recoveryQuestion,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = recoveryAnswer,
                        onValueChange = { recoveryAnswer = it },
                        label = { Text("Recovery Answer") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        VaultAuthMode.entries.forEach { mode ->
                            FilterChip(
                                selected = recoveryMode == mode,
                                onClick = {
                                    recoveryMode = mode
                                    newSecret = ""
                                },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = newSecret,
                        onValueChange = { newSecret = it },
                        label = { Text("New ${recoveryMode.label}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showRecoveryForm = false }) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (newSecret.isNotBlank()) {
                                    onResetWithRecovery(recoveryAnswer, newSecret, recoveryMode)
                                }
                            }
                        ) {
                            Text("Reset & Unlock")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnlockedVaultDashboardPane(
    vaultItems: List<VaultItemEntity>,
    authMode: VaultAuthMode,
    onLockVault: () -> Unit,
    onRestoreItem: (VaultItemEntity) -> Unit,
    onGoToExplorer: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("unlocked_vault_dashboard"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Vault Unlocked",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "AES-256-GCM Safe Vault Unlocked",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${vaultItems.size} Opaque .lvault Files • ${authMode.label} • 210k PBKDF2 • Hidden from Other Apps",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                    Button(
                        onClick = onLockVault,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("lock_vault_now_btn")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lock")
                    }
                }
            }
        }

        if (vaultItems.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Empty Vault",
                            tint = EmeraldVault,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No Files Locked in Safe Folder Yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "In Explorer, select one or more files and tap 'Vault' (or use the 3-dot menu) to encrypt them into opaque .lvault containers and zero-shred the original files.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        OutlinedButton(onClick = onGoToExplorer) {
                            Text("Browse Files to Lock")
                        }
                    }
                }
            }
        } else {
            items(vaultItems, key = { it.id }) { item ->
                val opaqueVaultFileName = item.encryptedFilePath.substringAfterLast('/')
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.originalName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Size: ${LumaFormatUtils.formatBytes(item.originalSizeBytes)} • Locked ${LumaFormatUtils.formatTimestamp(item.lockedAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { onRestoreItem(item) },
                                modifier = Modifier.testTag("restore_vault_item_${item.id}")
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Decrypt & Restore")
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "Opaque Container: $opaqueVaultFileName (noBackupFilesDir)",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldVault
                                )
                                Text(
                                    text = "IV: ${item.ivHex} • SHA-256: ${item.sha256Checksum.take(24)}...",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Shredded Source: ${item.originalPath}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinDotsIndicator(enteredLength: Int, totalLength: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until totalLength) {
            val isFilled = i < enteredLength
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(
                        if (isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun NumericPinPad(
    onDigitTap: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("CLR", "0", "DEL")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEach { rowDigits ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowDigits.forEach { key ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .clickable {
                                when (key) {
                                    "DEL" -> onBackspace()
                                    "CLR" -> repeat(6) { onBackspace() }
                                    else -> onDigitTap(key)
                                }
                            }
                            .testTag("pin_key_$key")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (key == "DEL") {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Backspace"
                                )
                            } else {
                                Text(
                                    text = key,
                                    style = if (key == "CLR") MaterialTheme.typography.labelLarge else MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
