package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.ConflictPolicy
import com.example.domain.model.FileFormatCategory
import com.example.domain.model.FileNode
import com.example.domain.model.SortOption
import com.example.domain.model.StorageVolumeRoot
import com.example.ui.ExplorerUiState
import com.example.ui.LumaFormatUtils
import com.example.ui.theme.AmberAdvanced
import com.example.ui.theme.BlueDoc
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldVault
import com.example.ui.theme.PurpleArchive
import com.example.ui.theme.RoseMedia

@Composable
fun ExplorerScreen(
    state: ExplorerUiState,
    onRequestAllFilesAccess: () -> Unit,
    onRequestSafFolderMount: () -> Unit,
    onSelectStorageRoot: (StorageVolumeRoot) -> Unit,
    onSelectCategoryCard: (FileFormatCategory?) -> Unit,
    onOpenSecureVaultTab: () -> Unit,
    onNavigateBreadcrumb: (String) -> Unit,
    onSearchChanged: (String, Boolean) -> Unit,
    onSortChanged: (SortOption) -> Unit,
    onFileTapped: (FileNode) -> Unit,
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
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var renameTargetNode by remember { mutableStateOf<FileNode?>(null) }
    var renameInputText by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("explorer_lazy_column"),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = if (state.selectedFilePaths.isNotEmpty() || state.clipboardState.isActive) 150.dp else 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Streamlined Luma Files Storage Bar (Hides persistent permission buttons once remembered or granted)
            item {
                LumaStorageHeroCard(
                    state = state,
                    onRequestAllFilesAccess = onRequestAllFilesAccess,
                    onRequestSafFolderMount = onRequestSafFolderMount
                )
            }

            // 2. Storage Volume Roots Switcher (Internal Shared Storage First!)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "STORAGE VOLUMES (INTERNAL, SD CARD & SAF)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = onRequestSafFolderMount,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Mount SAF / SD", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.storageRoots) { root ->
                            val isSelected = state.currentPath == root.path
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { onSelectStorageRoot(root) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Storage,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = root.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${LumaFormatUtils.formatBytes(root.freeBytes)} Free",
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

            // 3. Quick Format Studios & Secure Vault
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BUILT-IN LOSSLESS STUDIOS & VAULT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.selectedCategoryFilter != null) {
                            TextButton(onClick = { onSelectCategoryCard(null) }) {
                                Text("Clear Filter")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryQuickCard(
                            title = "DOCX / CSV",
                            subtitle = "DOM & Grid",
                            icon = Icons.Default.Description,
                            accentColor = BlueDoc,
                            isSelected = state.selectedCategoryFilter == FileFormatCategory.PDF,
                            onClick = {
                                onSelectCategoryCard(
                                    if (state.selectedCategoryFilter == FileFormatCategory.PDF) null else FileFormatCategory.PDF
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        CategoryQuickCard(
                            title = "Code / JSON",
                            subtitle = "Format & Edit",
                            icon = Icons.Default.Code,
                            accentColor = ElectricCyan,
                            isSelected = state.selectedCategoryFilter == FileFormatCategory.TEXT_CODE,
                            onClick = {
                                onSelectCategoryCard(
                                    if (state.selectedCategoryFilter == FileFormatCategory.TEXT_CODE) null else FileFormatCategory.TEXT_CODE
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        CategoryQuickCard(
                            title = "SQLite DB",
                            subtitle = "SQL & Rows",
                            icon = Icons.Default.TableChart,
                            accentColor = AmberAdvanced,
                            isSelected = state.selectedCategoryFilter == FileFormatCategory.SQLITE_DB,
                            onClick = {
                                onSelectCategoryCard(
                                    if (state.selectedCategoryFilter == FileFormatCategory.SQLITE_DB) null else FileFormatCategory.SQLITE_DB
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryQuickCard(
                            title = "ZIP & APK",
                            subtitle = "Pack & Inspect",
                            icon = Icons.Default.Archive,
                            accentColor = PurpleArchive,
                            isSelected = state.selectedCategoryFilter == FileFormatCategory.ARCHIVE_ZIP,
                            onClick = {
                                onSelectCategoryCard(
                                    if (state.selectedCategoryFilter == FileFormatCategory.ARCHIVE_ZIP) null else FileFormatCategory.ARCHIVE_ZIP
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        CategoryQuickCard(
                            title = "Media",
                            subtitle = "WAV, MP4, PNG",
                            icon = Icons.Default.Audiotrack,
                            accentColor = RoseMedia,
                            isSelected = state.selectedCategoryFilter == FileFormatCategory.VIDEO,
                            onClick = {
                                onSelectCategoryCard(
                                    if (state.selectedCategoryFilter == FileFormatCategory.VIDEO) null else FileFormatCategory.VIDEO
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        CategoryQuickCard(
                            title = "Safe Vault",
                            subtitle = "210k PBKDF2",
                            icon = Icons.Default.Lock,
                            accentColor = EmeraldVault,
                            isSelected = false,
                            onClick = onOpenSecureVaultTab,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 4. Search, Multi-Select Toggle & Sort Toolbar
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { onSearchChanged(it, state.useRegexSearch) },
                            placeholder = {
                                Text(if (state.isAdvancedMode && state.useRegexSearch) "Regex pattern (e.g. .*\\.kt$)" else "Search files in folder...")
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("file_search_input")
                        )

                        IconButton(
                            onClick = onSelectAllInDir,
                            modifier = Modifier.testTag("select_all_files_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = "Select All Files",
                                tint = if (state.selectedFilePaths.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box {
                            OutlinedButton(
                                onClick = { sortMenuExpanded = true },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.FilterList, contentDescription = "Sort", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sort")
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                SortOption.entries.forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(opt.label) },
                                        onClick = {
                                            onSortChanged(opt)
                                            sortMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Advanced Mode Quick Actions Row
                    if (state.isAdvancedMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = state.useRegexSearch,
                                onClick = { onSearchChanged(state.searchQuery, !state.useRegexSearch) },
                                label = { Text("Regex Match") }
                            )
                            OutlinedButton(onClick = onShowBatchRenameDialog) {
                                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Batch Regex Rename")
                            }
                            OutlinedButton(onClick = onBookmarkCurrentDir) {
                                Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Bookmark Path")
                            }
                        }
                    }
                }
            }

            // 5. Breadcrumb Navigation & New File/Folder Button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        state.directoryStack.forEachIndexed { idx, pathItem ->
                            val label = pathItem.substringAfterLast('/').ifBlank { "Internal Root" }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (idx == state.directoryStack.lastIndex) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                } else {
                                    Color.Transparent
                                },
                                modifier = Modifier.clickable { onNavigateBreadcrumb(pathItem) }
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (idx == state.directoryStack.lastIndex) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            if (idx < state.directoryStack.lastIndex) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onShowCreateDialog,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("new_file_or_folder_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New File or Folder", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New")
                    }
                }
            }

            // 6. Directory Files & Folders List
            if (state.files.isEmpty()) {
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
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Empty Directory",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "No matching files in this view",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Tap '+ New' to create a lossless file or folder, or switch storage volumes above.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(state.files, key = { it.path }) { node ->
                    val isSelected = state.selectedFilePaths.contains(node.path)
                    FileNodeRowCard(
                        node = node,
                        isSelected = isSelected,
                        isAdvancedMode = state.isAdvancedMode,
                        onTap = { onFileTapped(node) },
                        onToggleSelect = { onToggleSelectFile(node) },
                        onCopySingle = { onStageSingleClipboard(node, false) },
                        onMoveSingle = { onStageSingleClipboard(node, true) },
                        onEncryptToVault = { onEncryptToVault(node) },
                        onInspectChecksums = { onInspectChecksums(node) },
                        onRequestRename = {
                            renameTargetNode = node
                            renameInputText = node.name
                        },
                        onDelete = { onDeleteFile(node) }
                    )
                }
            }
        }

        // 7. Floating Multi-Select & Clipboard Action Dock
        if (state.selectedFilePaths.isNotEmpty() || state.clipboardState.isActive) {
            FloatingBatchAndClipboardDock(
                state = state,
                onCopySelected = { onStageBatchClipboard(false) },
                onMoveSelected = { onStageBatchClipboard(true) },
                onZipSelected = onShowBatchZipDialog,
                onEncryptSelected = onBatchEncryptSelected,
                onDeleteSelected = onBatchDeleteSelected,
                onClearSelection = onClearSelection,
                onSetConflictPolicy = onSetConflictPolicy,
                onPasteHere = onPasteClipboardHere,
                onClearClipboard = onClearClipboard,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
            )
        }
    }

    if (renameTargetNode != null) {
        val target = renameTargetNode!!
        AlertDialog(
            onDismissRequest = { renameTargetNode = null },
            title = { Text("Rename '${target.name}'") },
            text = {
                OutlinedTextField(
                    value = renameInputText,
                    onValueChange = { renameInputText = it },
                    label = { Text("New File/Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInputText.isNotBlank()) {
                            onRenameFile(target, renameInputText)
                            renameTargetNode = null
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetNode = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FloatingBatchAndClipboardDock(
    state: ExplorerUiState,
    onCopySelected: () -> Unit,
    onMoveSelected: () -> Unit,
    onZipSelected: () -> Unit,
    onEncryptSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onClearSelection: () -> Unit,
    onSetConflictPolicy: (ConflictPolicy) -> Unit,
    onPasteHere: () -> Unit,
    onClearClipboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
            .testTag("floating_batch_action_dock")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.selectedFilePaths.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${state.selectedFilePaths.size} Item(s) Selected",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = onClearSelection) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCopySelected,
                        modifier = Modifier.testTag("batch_copy_btn")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy")
                    }
                    Button(
                        onClick = onMoveSelected,
                        modifier = Modifier.testTag("batch_move_btn")
                    ) {
                        Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Move")
                    }
                    OutlinedButton(
                        onClick = onZipSelected,
                        modifier = Modifier.testTag("batch_zip_btn")
                    ) {
                        Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ZIP")
                    }
                    OutlinedButton(
                        onClick = onEncryptSelected,
                        modifier = Modifier.testTag("batch_vault_btn")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Vault")
                    }
                    OutlinedButton(
                        onClick = onDeleteSelected,
                        modifier = Modifier.testTag("batch_delete_btn")
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            } else if (state.clipboardState.isActive) {
                val clip = state.clipboardState
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${if (clip.isMoveOperation) "Move" else "Copy"} Clipboard: ${clip.sourceNodes.size} item(s)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Conflict Resolution Policy:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = onPasteHere,
                            modifier = Modifier.testTag("paste_clipboard_here_btn")
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste Here")
                        }
                        IconButton(onClick = onClearClipboard) {
                            Icon(Icons.Default.Clear, contentDescription = "Cancel Clipboard")
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ConflictPolicy.entries.forEach { policy ->
                        FilterChip(
                            selected = clip.conflictPolicy == policy,
                            onClick = { onSetConflictPolicy(policy) },
                            label = { Text(policy.label) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LumaStorageHeroCard(
    state: ExplorerUiState,
    onRequestAllFilesAccess: () -> Unit,
    onRequestSafFolderMount: () -> Unit
) {
    val activeRoot = state.storageRoots.firstOrNull { state.currentPath.startsWith(it.path) }
        ?: state.storageRoots.firstOrNull()
    val totalBytes = activeRoot?.totalBytes ?: (64L * 1024 * 1024 * 1024)
    val freeBytes = activeRoot?.freeBytes ?: (32L * 1024 * 1024 * 1024)
    val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedFraction = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.05f, 1f) else 0.5f

    // Show permission buttons only if All Files Access is not granted AND the user hasn't dismissed/remembered the onboarding decision
    val showAccessControlButtons = !state.hasAllFilesAccess && !state.isPermissionDecisionRemembered
    val cardHeight = if (showAccessControlButtons) 172.dp else 124.dp

    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_luma_hero_banner_1795684570893),
                contentDescription = "Luma Files Storage Telemetry Banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC0B0F17),
                                Color(0xF20B0F17)
                            )
                        )
                    )
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "LUMA FILES v1.1.0 • STORAGE ENGINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                            Text(
                                text = activeRoot?.title ?: "Internal Shared Storage",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (state.hasAllFilesAccess) {
                                EmeraldVault.copy(alpha = 0.25f)
                            } else {
                                ElectricCyan.copy(alpha = 0.22f)
                            },
                            modifier = Modifier.clickable { onRequestAllFilesAccess() }
                        ) {
                            Text(
                                text = if (state.hasAllFilesAccess) "ALL FILES ACTIVE" else "STORAGE SETTINGS",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.hasAllFilesAccess) EmeraldVault else ElectricCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Used: ${LumaFormatUtils.formatBytes(usedBytes)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                            Text(
                                text = "Free: ${LumaFormatUtils.formatBytes(freeBytes)} / ${LumaFormatUtils.formatBytes(totalBytes)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricCyan
                            )
                        }
                        LinearProgressIndicator(
                            progress = { usedFraction },
                            color = ElectricCyan,
                            trackColor = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }

                    if (showAccessControlButtons) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onRequestAllFilesAccess,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("grant_all_files_access_btn")
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "All Files Access",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            OutlinedButton(
                                onClick = onRequestSafFolderMount,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("mount_saf_folder_btn")
                            ) {
                                Icon(
                                    Icons.Default.CreateNewFolder,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Mount SAF Folder",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White
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
private fun CategoryQuickCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                accentColor.copy(alpha = 0.16f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(accentColor.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FileNodeRowCard(
    node: FileNode,
    isSelected: Boolean,
    isAdvancedMode: Boolean,
    onTap: () -> Unit,
    onToggleSelect: () -> Unit,
    onCopySingle: () -> Unit,
    onMoveSingle: () -> Unit,
    onEncryptToVault: () -> Unit,
    onInspectChecksums: () -> Unit,
    onRequestRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val (icon, tint) = when (node.category) {
        FileFormatCategory.DIRECTORY -> Icons.Default.Folder to ElectricCyan
        FileFormatCategory.MARKDOWN, FileFormatCategory.TEXT_CODE, FileFormatCategory.STRUCTURED_DATA ->
            Icons.Default.Code to ElectricCyan
        FileFormatCategory.CSV_TSV -> Icons.Default.TableChart to EmeraldVault
        FileFormatCategory.SQLITE_DB -> Icons.Default.Storage to AmberAdvanced
        FileFormatCategory.APK_PACKAGE -> Icons.Default.Android to EmeraldVault
        FileFormatCategory.PDF -> Icons.Default.PictureAsPdf to RoseMedia
        FileFormatCategory.DOCX -> Icons.Default.Description to BlueDoc
        FileFormatCategory.ARCHIVE_ZIP -> Icons.Default.Archive to PurpleArchive
        FileFormatCategory.VIDEO -> Icons.Default.Movie to RoseMedia
        FileFormatCategory.AUDIO -> Icons.Default.Audiotrack to EmeraldVault
        FileFormatCategory.IMAGE -> Icons.Default.Image to AmberAdvanced
        FileFormatCategory.BINARY_HEX -> Icons.AutoMirrored.Filled.InsertDriveFile to MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onTap() }
            .testTag("file_row_${node.name}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleSelect,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("select_file_${node.name}")
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = "Select ${node.name}",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(tint.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = node.category.label,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = tint.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = node.category.badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = tint,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                val sizeOrItems = if (node.isDirectory) {
                    "${node.childCount} items"
                } else {
                    LumaFormatUtils.formatBytes(node.sizeBytes)
                }

                Text(
                    text = "$sizeOrItems • ${LumaFormatUtils.formatTimestamp(node.lastModified)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isAdvancedMode) {
                    Text(
                        text = "POSIX: ${node.permissionsRwx} • MIME: ${node.mimeType}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("file_menu_${node.name}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "File Actions"
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (node.isDirectory) "Open Directory" else "Open / Edit in Studio...") },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onTap()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onCopySingle()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Move (Cut)") },
                        leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onMoveSingle()
                        }
                    )
                    if (!node.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("Lock & Shred into AES-256 Vault") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEncryptToVault()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("MD5 / SHA-256 Checksums") },
                            leadingIcon = { Icon(Icons.Default.Fingerprint, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onInspectChecksums()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRequestRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
