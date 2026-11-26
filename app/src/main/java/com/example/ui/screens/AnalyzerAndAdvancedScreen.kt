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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.BookmarkEntity
import com.example.data.local.OperationLogEntity
import com.example.domain.model.FileNode
import com.example.ui.ExplorerUiState
import com.example.ui.LumaFormatUtils
import com.example.ui.theme.AmberAdvanced
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldVault
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun StorageAnalyzerScreen(
    state: ExplorerUiState,
    onRescanStorage: () -> Unit,
    onOpenFile: (FileNode) -> Unit,
    onDeleteDuplicateFile: (FileNode) -> Unit
) {
    val report = state.analyzerReport

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("storage_analyzer_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = "Storage Analyzer",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Deep Storage & Duplicate Analyzer",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "SHA-256 Bit-Exact Duplicate Verification & Heavy File Ranker",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedButton(onClick = onRescanStorage) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rescan")
                        }
                    }

                    if (state.isAnalyzingStorage) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else if (report != null) {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Files Scanned", style = MaterialTheme.typography.labelSmall)
                                Text("${report.scannedFilesCount} files (${report.scannedDirectoriesCount} dirs)", style = MaterialTheme.typography.titleSmall)
                            }
                            Column {
                                Text("Total Scanned Volume", style = MaterialTheme.typography.labelSmall)
                                Text(LumaFormatUtils.formatBytes(report.totalScannedBytes), style = MaterialTheme.typography.titleSmall, color = ElectricCyan)
                            }
                            Column {
                                Text("Reclaimable Dupes", style = MaterialTheme.typography.labelSmall)
                                Text(LumaFormatUtils.formatBytes(report.reclaimableDuplicateBytes), style = MaterialTheme.typography.titleSmall, color = AmberAdvanced)
                            }
                        }
                    }
                }
            }
        }

        if (report != null) {
            // Format Breakdown
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "FORMAT CATEGORY FOOTPRINT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        report.categoryBreakdownBytes.entries
                            .sortedByDescending { it.value }
                            .forEach { (cat, bytes) ->
                                val ratio = if (report.totalScannedBytes > 0) {
                                    (bytes.toFloat() / report.totalScannedBytes.toFloat()).coerceIn(0.02f, 1f)
                                } else 0.05f
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(cat.label, style = MaterialTheme.typography.bodySmall)
                                        Text(LumaFormatUtils.formatBytes(bytes), style = MaterialTheme.typography.labelMedium)
                                    }
                                    LinearProgressIndicator(
                                        progress = { ratio },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                    )
                                }
                            }
                    }
                }
            }

            // SHA-256 Verified Duplicate Groups
            item {
                Text(
                    text = "SHA-256 VERIFIED DUPLICATE FILE GROUPS (${report.duplicateGroups.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (report.duplicateGroups.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, tint = EmeraldVault)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Zero duplicate files detected! Storage is clean.")
                        }
                    }
                }
            } else {
                items(report.duplicateGroups, key = { it.sha256Hash }) { group ->
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CopyAll, contentDescription = null, tint = AmberAdvanced)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${group.files.size} Identical Copies (${LumaFormatUtils.formatBytes(group.fileSizeEach)} each)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = "SHA-256: ${group.sha256Hash}",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            group.files.forEachIndexed { idx, fileNode ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (idx == 0) "${fileNode.name} (Original)" else "${fileNode.name} (Redundant Copy)",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Text(
                                            text = fileNode.path,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (idx > 0) {
                                        IconButton(onClick = { onDeleteDuplicateFile(fileNode) }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Duplicate",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Heavy & Large Files Ranking
            item {
                Text(
                    text = "HEAVY & LARGE FILES RANKING (LOSSLESS READY)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(report.largeFiles, key = { it.path }) { largeNode ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenFile(largeNode) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = largeNode.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${largeNode.category.label} • ${largeNode.permissionsRwx}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = LumaFormatUtils.formatBytes(largeNode.sizeBytes),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdvancedWorkstationHubScreen(
    state: ExplorerUiState,
    bookmarks: List<BookmarkEntity>,
    logs: List<OperationLogEntity>,
    onToggleAdvancedMode: () -> Unit,
    onOpenBatchRegexDialog: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onJumpToBookmark: (BookmarkEntity) -> Unit,
    onRemoveBookmark: (Long) -> Unit,
    onClearLogs: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("advanced_hub_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Advanced Workstation Switch Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (state.isAdvancedMode) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
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
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Advanced Mode",
                            tint = if (state.isAdvancedMode) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Power-User Advanced Mode",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Unlocks hidden dotfiles (.nomedia, .luma_hidden_config), POSIX rwx flags, Regex search, Batch Rename & Hex byte patching.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Switch(
                        checked = state.isAdvancedMode,
                        onCheckedChange = { onToggleAdvancedMode() },
                        modifier = Modifier.testTag("advanced_mode_hub_switch")
                    )
                }
            }
        }

        // 2. Quick Power Tools
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenBatchRegexDialog,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Batch Regex Rename")
                }
                OutlinedButton(
                    onClick = onRequestAllFilesAccess,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("All Files Access")
                }
            }
        }

        // 3. Saved Bookmarks & SAF Mounts
        item {
            Text(
                text = "BOOKMARKED DIRECTORIES & SAF MOUNTS (${bookmarks.size})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (bookmarks.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No bookmarked directories yet. Enable Advanced Mode in Explorer and tap 'Bookmark Path' or mount a SAF folder.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(bookmarks, key = { it.id }) { bm ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onJumpToBookmark(bm) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(bm.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = bm.pathOrUri,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(onClick = { onRemoveBookmark(bm.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove Bookmark")
                        }
                    }
                }
            }
        }

        // 4. Exact Error Diagnostics & Operation Audit Trail
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EXACT DIAGNOSTIC & OPERATION AUDIT TRAIL (${logs.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (logs.isNotEmpty()) {
                    OutlinedButton(onClick = onClearLogs) {
                        Text("Clear Logs")
                    }
                }
            }
        }

        if (logs.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "All operations healthy — zero unhandled exceptions recorded.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(logs, key = { it.id }) { log ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (log.isError) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (log.isError) {
                                    Icon(
                                        imageVector = Icons.Default.BugReport,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = log.operationType,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (log.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = LumaFormatUtils.formatTimestamp(log.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = log.summary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        if (log.exactTechnicalReason.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = log.exactTechnicalReason,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
