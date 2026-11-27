package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.ConflictPolicy
import com.example.domain.model.FileFormatCategory
import com.example.domain.model.FileNode
import com.example.domain.model.SortOption
import com.example.domain.model.StorageVolumeRoot
import com.example.ui.ExplorerUiState
import com.example.ui.LumaFormatUtils

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExplorerScreen(
    state: ExplorerUiState,
    onRequestAllFilesAccess: () -> Unit,
    onRequestSafFolderMount: () -> Unit,
    onSelectStorageRoot: (StorageVolumeRoot) -> Unit,
    onSelectCategoryCard: (FileFormatCategory?) -> Unit,
    onOpenSecureVaultTab: () -> Unit,
    onOpenRecycleBin: () -> Unit,
    onExecuteUndo: () -> Unit,
    onDismissUndo: () -> Unit,
    onNavigateBreadcrumb: (String) -> Unit,
    onSearchChanged: (String, Boolean) -> Unit,
    onSortChanged: (SortOption) -> Unit,
    onFileTapped: (FileNode) -> Unit,
    onOpenExternal: (FileNode) -> Unit,
    onShareFile: (FileNode) -> Unit,
    onToggleSelectFile: (FileNode) -> Unit,
    onSelectAllInDir: () -> Unit,
    onClearSelection: () -> Unit,
    onStageSingleClipboard: (FileNode, Boolean) -> Unit,
    onStageBatchClipboard: (Boolean) -> Unit,
    onSetConflictPolicy: (ConflictPolicy) -> Unit,
    onPasteClipboardHere: () -> Unit,
    onClearClipboard: () -> Unit,
    onBatchDeleteSelected: () -> Unit,
    onShowBatchZipDialog: () -> Unit,
    onBatchEncryptSelected: () -> Unit,
    onEncryptToVault: (FileNode) -> Unit,
    onInspectChecksums: (FileNode) -> Unit,
    onRenameFile: (FileNode, String) -> Unit,
    onDeleteFile: (FileNode) -> Unit,
    onShowCreateDialog: () -> Unit,
    onShowBatchRenameDialog: () -> Unit,
    onBookmarkCurrentDir: () -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileNode?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }

    val primaryVolume = state.storageRoots.firstOrNull { !it.isSafTree && it.totalBytes > 0L }
    val isSelectionMode = state.selectedFilePaths.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("explorer_screen_root")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Compact Storage Bar + Volume Pills (only when at root/top level and not searching)
            if (!isSelectionMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (primaryVolume != null) {
                            val usedBytes = (primaryVolume.totalBytes - primaryVolume.freeBytes).coerceAtLeast(0L)
                            val usedFrac = (usedBytes.toFloat() / primaryVolume.totalBytes.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Storage",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${LumaFormatUtils.formatBytes(primaryVolume.freeBytes)} free of ${LumaFormatUtils.formatBytes(primaryVolume.totalBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            LinearProgressIndicator(
                                progress = { usedFrac },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }

                        // Volume & Trash Quick Pills
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.storageRoots, key = { it.path }) { root ->
                                val isSelected = state.currentPath == root.path
                                val shortLabel = when {
                                    root.title.contains("Internal", ignoreCase = true) -> "Internal"
                                    root.title.contains("SD", ignoreCase = true) -> "SD Card"
                                    root.isSafTree -> root.title.removePrefix("SAF (").removeSuffix(")")
                                    else -> root.title.take(12)
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSelectStorageRoot(root) },
                                    label = { Text(shortLabel, style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (root.isRemovableSd) Icons.Default.SdStorage else Icons.Default.Storage,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                )
                            }
                            if (!state.hasAllFilesAccess) {
                                item {
                                    FilterChip(
                                        selected = false,
                                        onClick = onRequestAllFilesAccess,
                                        label = { Text("Allow All Files", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = onRequestSafFolderMount,
                                    label = { Text("+ Folder", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = onOpenRecycleBin,
                                    label = {
                                        val cnt = state.recycleBinItems.size
                                        Text(if (cnt > 0) "Trash ($cnt)" else "Trash", style = MaterialTheme.typography.labelSmall)
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.RestoreFromTrash, null, modifier = Modifier.size(15.dp))
                                    },
                                    modifier = Modifier.testTag("explorer_trash_chip")
                                )
                            }
                        }
                    }
                }
            }

            // Quick Category Filter Strip + Action Icons (Search, Sort, Bookmark)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    val chips: List<Pair<FileFormatCategory?, String>> = listOf(
                        null to "All",
                        FileFormatCategory.IMAGE to "Images",
                        FileFormatCategory.VIDEO to "Media",
                        FileFormatCategory.PDF to "Docs",
                        FileFormatCategory.ARCHIVE_ZIP to "Archives",
                        FileFormatCategory.APK_PACKAGE to "APKs"
                    )
                    items(chips) { (cat, label) ->
                        FilterChip(
                            selected = state.selectedCategoryFilter == cat,
                            onClick = { onSelectCategoryCard(cat) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                IconButton(
                    onClick = {
                        showSearchBar = !showSearchBar
                        if (!showSearchBar && state.searchQuery.isNotEmpty()) {
                            onSearchChanged("", false)
                        }
                    },
                    modifier = Modifier.testTag("explorer_search_toggle")
                ) {
                    Icon(if (showSearchBar) Icons.Default.Close else Icons.Default.Search, contentDescription = "Search")
                }

                Box {
                    IconButton(onClick = { showSortMenu = true }, modifier = Modifier.testTag("explorer_sort_btn")) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        SortOption.entries.forEach { opt ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        opt.label,
                                        fontWeight = if (state.sortOption == opt) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    showSortMenu = false
                                    onSortChanged(opt)
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Bookmark folder") },
                            leadingIcon = { Icon(Icons.Default.BookmarkAdd, null) },
                            onClick = {
                                showSortMenu = false
                                onBookmarkCurrentDir()
                            }
                        )
                        if (state.isAdvancedMode) {
                            DropdownMenuItem(
                                text = { Text("Batch rename") },
                                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                                onClick = {
                                    showSortMenu = false
                                    onShowBatchRenameDialog()
                                }
                            )
                        }
                    }
                }
            }

            // Expandable Search Field
            AnimatedVisibility(visible = showSearchBar || state.searchQuery.isNotBlank()) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { onSearchChanged(it, state.useRegexSearch) },
                    placeholder = { Text(if (state.isAdvancedMode) "Search or regex…" else "Search files…") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChanged("", state.useRegexSearch) }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear Search")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("explorer_search_input")
                )
            }

            // Clean Breadcrumb Trail
            if (state.directoryStack.isNotEmpty() && state.selectedCategoryFilter == null) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(state.directoryStack) { dirPath ->
                        val isLast = dirPath == state.directoryStack.last()
                        val displayName = when {
                            dirPath.endsWith("/0") -> "Internal"
                            dirPath.startsWith("content://") -> "SAF"
                            else -> dirPath.substringAfterLast('/').ifBlank { "Root" }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                                color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .combinedClickable(onClick = { onNavigateBreadcrumb(dirPath) })
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                            if (!isLast) {
                                Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }

            // Clipboard Paste Bar (when Cut/Copy active)
            AnimatedVisibility(visible = state.clipboardState.isActive) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${if (state.clipboardState.isMoveOperation) "Move" else "Copy"} ${state.clipboardState.sourceNodes.size} item(s)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = onClearClipboard) { Text("Cancel") }
                            Button(onClick = onPasteClipboardHere) {
                                Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Paste")
                            }
                        }
                    }
                }
            }

            // Undo Banner
            AnimatedVisibility(visible = state.lastUndoOperation != null) {
                state.lastUndoOperation?.let { undo ->
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = undo.summaryLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onExecuteUndo) {
                                Icon(Icons.AutoMirrored.Filled.Undo, null, tint = MaterialTheme.colorScheme.inversePrimary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Undo", color = MaterialTheme.colorScheme.inversePrimary)
                            }
                            IconButton(onClick = onDismissUndo, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // ==================== CLEAN SINGLE-COLUMN FILE LIST ====================
            if (state.files.isEmpty() && !state.isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(52.dp)
                        )
                        Text(
                            text = if (state.searchQuery.isNotBlank()) "No matching files" else "Empty folder",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("explorer_file_list"),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 92.dp)
                ) {
                    items(state.files, key = { it.path }) { node ->
                        val isSelected = state.selectedFilePaths.contains(node.path)
                        CleanFileListRow(
                            node = node,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            isAdvancedMode = state.isAdvancedMode,
                            onTap = { onFileTapped(node) },
                            onLongPress = { onToggleSelectFile(node) },
                            onCopy = { onStageSingleClipboard(node, false) },
                            onMove = { onStageSingleClipboard(node, true) },
                            onRename = {
                                renameTarget = node
                                renameInput = node.name
                            },
                            onDelete = { onDeleteFile(node) },
                            onLockToVault = { onEncryptToVault(node) },
                            onShare = { onShareFile(node) },
                            onOpenExternal = { onOpenExternal(node) },
                            onDetails = { onInspectChecksums(node) }
                        )
                    }
                }
            }
        }

        // ==================== FLOATING MULTI-SELECT DOCK OR CREATE FAB ====================
        if (isSelectionMode) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 6.dp,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("explorer_multiselect_dock")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel Selection")
                        }
                        Text(
                            "${state.selectedFilePaths.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onSelectAllInDir) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                        }
                        IconButton(onClick = { onStageBatchClipboard(false) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                        IconButton(onClick = { onStageBatchClipboard(true) }) {
                            Icon(Icons.Default.ContentCut, contentDescription = "Move")
                        }
                        IconButton(onClick = onShowBatchZipDialog) {
                            Icon(Icons.Default.FolderZip, contentDescription = "Zip")
                        }
                        IconButton(onClick = onBatchEncryptSelected) {
                            Icon(Icons.Default.Lock, contentDescription = "Vault")
                        }
                        IconButton(onClick = onBatchDeleteSelected) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        } else {
            FloatingActionButton(
                onClick = onShowCreateDialog,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("explorer_create_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "New File or Folder")
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (renameInput.isNotBlank() && renameInput != target.name) {
                        onRenameFile(target, renameInput.trim())
                    }
                    renameTarget = null
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CleanFileListRow(
    node: FileNode,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isAdvancedMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onLockToVault: () -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit,
    onDetails: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val (icon, tint) = remember(node.category, node.isDirectory) { fileBadgeStyle(node) }

    val rowBg = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Minimal 40dp Tinted Icon Badge
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) MaterialTheme.colorScheme.primary else tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
            } else {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }
        }

        Spacer(Modifier.width(14.dp))

        // Concise 2-Line File Metadata
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val meta = buildString {
                if (node.isDirectory) {
                    append("${node.childCount} items")
                } else {
                    append(LumaFormatUtils.formatBytes(node.sizeBytes))
                }
                val date = LumaFormatUtils.formatCompactDate(node.lastModified)
                if (date.isNotBlank()) {
                    append(" · ")
                    append(date)
                }
                if (isAdvancedMode && node.permissionsRwx.isNotBlank()) {
                    append(" · ")
                    append(node.permissionsRwx.take(10))
                }
            }
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!isSelectionMode) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "File Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (!node.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            onClick = { showMenu = false; onShare() }
                        )
                        DropdownMenuItem(
                            text = { Text("Open with…") },
                            leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
                            onClick = { showMenu = false; onOpenExternal() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Copy") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                        onClick = { showMenu = false; onCopy() }
                    )
                    DropdownMenuItem(
                        text = { Text("Move") },
                        leadingIcon = { Icon(Icons.Default.ContentCut, null) },
                        onClick = { showMenu = false; onMove() }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                        onClick = { showMenu = false; onRename() }
                    )
                    if (!node.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("Lock to Vault") },
                            leadingIcon = { Icon(Icons.Default.Lock, null) },
                            onClick = { showMenu = false; onLockToVault() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Details") },
                        leadingIcon = { Icon(Icons.Default.Info, null) },
                        onClick = { showMenu = false; onDetails() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        }
    }
}

private fun fileBadgeStyle(node: FileNode): Pair<ImageVector, Color> {
    if (node.isDirectory) return Icons.Default.Folder to Color(0xFF3B82F6)
    return when (node.category) {
        FileFormatCategory.IMAGE -> Icons.Default.Image to Color(0xFFEC4899)
        FileFormatCategory.VIDEO -> Icons.Default.VideoFile to Color(0xFF8B5CF6)
        FileFormatCategory.AUDIO -> Icons.Default.AudioFile to Color(0xFF06B6D4)
        FileFormatCategory.PDF -> Icons.Default.PictureAsPdf to Color(0xFFEF4444)
        FileFormatCategory.DOCX -> Icons.Default.Description to Color(0xFF2563EB)
        FileFormatCategory.CSV_TSV -> Icons.Default.TableChart to Color(0xFF10B981)
        FileFormatCategory.ARCHIVE_ZIP -> Icons.Default.FolderZip to Color(0xFFF59E0B)
        FileFormatCategory.APK_PACKAGE -> Icons.Default.Android to Color(0xFF22C55E)
        FileFormatCategory.TEXT_CODE,
        FileFormatCategory.MARKDOWN,
        FileFormatCategory.STRUCTURED_DATA -> Icons.Default.Code to Color(0xFF6366F1)
        FileFormatCategory.SQLITE_DB -> Icons.Default.Storage to Color(0xFF14B8A6)
        else -> Icons.AutoMirrored.Filled.InsertDriveFile to Color(0xFF64748B)
    }
}
