package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.VaultItemEntity
import com.example.ui.ExplorerUiState
import com.example.ui.LumaFormatUtils
import com.example.ui.theme.EmeraldVault
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun SecureVaultScreen(
    state: ExplorerUiState,
    vaultItems: List<VaultItemEntity>,
    onSetupPin: (pin: String, pinLength: Int, recoveryQ: String, recoveryA: String) -> Unit,
    onUnlockPin: (pin: String) -> Unit,
    onLockVault: () -> Unit,
    onResetPinWithRecovery: (answer: String, newPin: String, newLength: Int) -> Unit,
    onRestoreVaultItem: (VaultItemEntity) -> Unit,
    onGoToExplorerToLockFiles: () -> Unit
) {
    if (!state.isPinConfigured) {
        VaultPinSetupPane(
            authError = state.vaultAuthError,
            onSetupPin = onSetupPin
        )
    } else if (!state.isVaultUnlocked) {
        VaultPinUnlockKeypadPane(
            pinLength = state.configuredPinLength,
            recoveryQuestion = state.recoveryQuestion,
            authError = state.vaultAuthError,
            onUnlockPin = onUnlockPin,
            onResetWithRecovery = onResetPinWithRecovery
        )
    } else {
        UnlockedVaultDashboardPane(
            vaultItems = vaultItems,
            onLockVault = onLockVault,
            onRestoreItem = onRestoreVaultItem,
            onGoToExplorer = onGoToExplorerToLockFiles
        )
    }
}

@Composable
private fun VaultPinSetupPane(
    authError: String?,
    onSetupPin: (String, Int, String, String) -> Unit
) {
    var selectedPinLength by remember { mutableIntStateOf(4) }
    var enteredPin by remember { mutableStateOf("") }
    var recoveryQuestion by remember { mutableStateOf("What is your favorite security keyword?") }
    var recoveryAnswer by remember { mutableStateOf("LumaVault") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("vault_pin_setup_pane"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(EmeraldVault.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.EnhancedEncryption,
                    contentDescription = "AES-256 Vault",
                    tint = EmeraldVault,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        item {
            Text(
                text = "Initialize AES-256 PIN Secure Folder",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Files locked in the Secure Vault are encrypted on disk with AES-256-GCM using a key derived from your PIN via PBKDF2-HMAC-SHA256.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = selectedPinLength == 4,
                    onClick = {
                        selectedPinLength = 4
                        if (enteredPin.length > 4) enteredPin = enteredPin.take(4)
                    },
                    label = { Text("4-Digit PIN") }
                )
                FilterChip(
                    selected = selectedPinLength == 6,
                    onClick = { selectedPinLength = 6 },
                    label = { Text("6-Digit High-Entropy PIN") }
                )
            }
        }

        item {
            PinDotsIndicator(enteredLength = enteredPin.length, totalLength = selectedPinLength)
        }

        if (authError != null) {
            item {
                Text(
                    text = authError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        item {
            NumericPinPad(
                onDigitTap = { digit ->
                    if (enteredPin.length < selectedPinLength) {
                        enteredPin += digit
                    }
                },
                onBackspace = {
                    if (enteredPin.isNotEmpty()) {
                        enteredPin = enteredPin.dropLast(1)
                    }
                }
            )
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
                        text = "PIN Recovery Security Question",
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
                    onSetupPin(enteredPin, selectedPinLength, recoveryQuestion, recoveryAnswer)
                },
                enabled = enteredPin.length == selectedPinLength,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("activate_vault_pin_btn")
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Activate $selectedPinLength-Digit AES-256 Vault")
            }
        }
    }
}

@Composable
private fun VaultPinUnlockKeypadPane(
    pinLength: Int,
    recoveryQuestion: String,
    authError: String?,
    onUnlockPin: (String) -> Unit,
    onResetWithRecovery: (String, String, Int) -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var showRecoveryForm by remember { mutableStateOf(false) }
    var recoveryAnswer by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }

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
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Enter $pinLength-Digit Secure Folder PIN",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "AES-256-GCM Encrypted Vault is currently locked",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))
        PinDotsIndicator(enteredLength = enteredPin.length, totalLength = pinLength)

        if (authError != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = authError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (!showRecoveryForm) {
            NumericPinPad(
                onDigitTap = { digit ->
                    if (enteredPin.length < pinLength) {
                        enteredPin += digit
                        if (enteredPin.length == pinLength) {
                            onUnlockPin(enteredPin)
                            enteredPin = ""
                        }
                    }
                },
                onBackspace = {
                    if (enteredPin.isNotEmpty()) {
                        enteredPin = enteredPin.dropLast(1)
                    }
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = { showRecoveryForm = true }) {
                Text("Forgot PIN? Use Recovery Security Answer")
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
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) newPin = it },
                        label = { Text("New 4-Digit PIN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showRecoveryForm = false }) {
                            Text("Back to Keypad")
                        }
                        Button(
                            onClick = {
                                if (newPin.length == 4 || newPin.length == 6) {
                                    onResetWithRecovery(recoveryAnswer, newPin, newPin.length)
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
                                text = "AES-256-GCM Secure Vault Unlocked",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${vaultItems.size} Encrypted Files • 100% Bit-Exact SHA-256 Integrity",
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
                            text = "No Files Locked in Vault Yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "In Explorer, tap any file (or its 3-dot menu) and choose 'Encrypt into PIN Secure Vault (AES-256)' to protect it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        OutlinedButton(onClick = onGoToExplorer) {
                            Text("Browse Files to Encrypt")
                        }
                    }
                }
            }
        } else {
            items(vaultItems, key = { it.id }) { item ->
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
                                    text = "Original Size: ${LumaFormatUtils.formatBytes(item.originalSizeBytes)} • Locked ${LumaFormatUtils.formatTimestamp(item.lockedAt)}",
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
                                    text = "AES-256-GCM IV: ${item.ivHex} • SHA-256: ${item.sha256Checksum.take(28)}...",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldVault
                                )
                                Text(
                                    text = "Origin Path: ${item.originalPath}",
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
