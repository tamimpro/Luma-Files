package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.DiagnosticError
import com.example.domain.model.FileNode
import com.example.domain.model.RecycleBinItem
import com.example.ui.LumaFormatUtils

@Composable
fun FirstLaunchPermissionOnboardingDialog(
    onGrantAllFilesAccess: () -> Unit,
    onMountSafFolder: () -> Unit,
    onContinueAndRemember: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onContinueAndRemember,
        title = { Text("Storage Access", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Allow access to browse and manage all folders on your device.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = onGrantAllFilesAccess,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_grant_all_files_btn")
                ) { Text("Allow All Files Access") }
                OutlinedButton(
                    onClick = onMountSafFolder,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Pick a Folder") }
            }
        },
        confirmButton = {
            TextButton(onClick = onContinueAndRemember) { Text("Not Now") }
        }
    )
}

@Composable
fun DeleteChoiceDialog(
    targets: List<FileNode>,
    onMoveToRecycleBin: () -> Unit,
    onPermanentShred: () -> Unit,
    onDismiss: () -> Unit
) {
    val label = if (targets.size == 1) "'${targets.first().name}'" else "${targets.size} items"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $label?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Choose Trash to recover later, or Shred to erase permanently.", style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = onMoveToRecycleBin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("delete_choice_recycle_btn")
                ) {
                    Icon(Icons.Default.RestoreFromTrash, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Move to Trash")
                }
                OutlinedButton(
                    onClick = onPermanentShred,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("delete_choice_shred_btn")
                ) {
                    Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Shred Permanently")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RecycleBinModalSheet(
    items: List<RecycleBinItem>,
    onRestoreItem: (RecycleBinItem) -> Unit,
    onShredItem: (RecycleBinItem) -> Unit,
    onEmptyAll: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Trash (${items.size})")
                if (items.isNotEmpty()) {
                    TextButton(onClick = onEmptyAll) {
                        Text("Empty", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        text = {
            if (items.isEmpty()) {
                Text("Trash is empty.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.originalName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(LumaFormatUtils.formatBytes(item.sizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onRestoreItem(item) }) {
                                    Icon(Icons.Default.Restore, contentDescription = "Restore", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { onShredItem(item) }) {
                                    Icon(Icons.Default.DeleteForever, contentDescription = "Shred", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun FileChecksumInspectorDialog(
    node: FileNode,
    md5: String,
    sha256: String,
    isComputing: Boolean,
    onOpenHex: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(node.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Size: ${LumaFormatUtils.formatBytes(node.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
                Text("Path: ${node.path}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Modified: ${LumaFormatUtils.formatTimestamp(node.lastModified)}", style = MaterialTheme.typography.bodySmall)
                Text("MD5: ${if (isComputing) "Computing…" else md5}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                Text("SHA-256: ${if (isComputing) "Computing…" else sha256}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            if (!node.isDirectory) {
                TextButton(onClick = onOpenHex) { Text("Hex View") }
            }
        }
    )
}

@Composable
fun CreateFileOrFolderDialog(
    onConfirm: (name: String, isDirectory: Boolean, content: String) -> Unit,
    onDismiss: () -> Unit
) {
    var isDir by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var initialContent by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isDir) "New Folder" else "New File") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = isDir, onClick = { isDir = true }, label = { Text("Folder") })
                    FilterChip(selected = !isDir, onClick = { isDir = false }, label = { Text("File") })
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isDir) "Folder name" else "File name (e.g. notes.txt)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isDir) {
                    OutlinedTextField(
                        value = initialContent,
                        onValueChange = { initialContent = it },
                        label = { Text("Initial text (optional)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), isDir, initialContent)
                        onDismiss()
                    }
                }
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun BatchRegexRenameDialog(
    currentDirectory: String,
    onConfirm: (pattern: String, replacement: String, prefix: String) -> Unit,
    onDismiss: () -> Unit
) {
    var pattern by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Batch Rename") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = prefix, onValueChange = { prefix = it }, label = { Text("Prefix (optional)") }, singleLine = true)
                OutlinedTextField(value = pattern, onValueChange = { pattern = it }, label = { Text("Find / Regex") }, singleLine = true)
                OutlinedTextField(value = replacement, onValueChange = { replacement = it }, label = { Text("Replace with") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(pattern, replacement, prefix); onDismiss() }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun BatchZipNameDialog(
    selectedCount: Int,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var archiveName by remember { mutableStateOf("archive.zip") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compress $selectedCount item(s)") },
        text = {
            OutlinedTextField(
                value = archiveName,
                onValueChange = { archiveName = it },
                label = { Text("ZIP name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { if (archiveName.isNotBlank()) onConfirm(archiveName.trim()); onDismiss() }) { Text("Create ZIP") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ExactDiagnosticErrorDialog(
    error: DiagnosticError,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(error.operationTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(error.exactReason, style = MaterialTheme.typography.bodyMedium)
                if (error.remediationHint.isNotBlank()) {
                    Text(error.remediationHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("OK") }
        }
    )
}
