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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("explorer_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Luma Files Hero Storage & Access Card
        item {
            LumaStorageHeroCard(
                state = state,
                onRequestAllFilesAccess = onRequestAllFilesAccess,
                onRequestSafFolderMount = onRequestSafFolderMount
            )
        }

        // 2. Storage Volume Roots Switcher
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "MOUNTED STORAGE VOLUMES & WORKSPACES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.storageRoots) { root ->
                        val isSelected = state.currentPath.startsWith(root.path)
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

        // 3. Sleek Category Quick-Filter Cards + Secure Vault Shortcut
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "QUICK FORMAT STUDIOS & SECURE VAULT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.selectedCategoryFilter != null) {
                        TextButton(onClick = { onSelectCategoryCard(null) }) {
                            Text("Clear Filter (Show Directory)")
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CategoryQuickCard(
                        title = "PDF & DOCX",
                        subtitle = "Office & Vector",
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
                        title = "Code & MD",
                        subtitle = "TXT, MD, KT, JSON",
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
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CategoryQuickCard(
                        title = "ZIP Archives",
                        subtitle = "Extract & Pack",
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
                        title = "Media Studio",
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
                        title = "PIN Vault",
                        subtitle = "AES-256 Folder",
                        icon = Icons.Default.Lock,
                        accentColor = EmeraldVault,
                        isSelected = false,
                        onClick = onOpenSecureVaultTab,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 4. Search, Regex & Sort Toolbar
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
                        val label = pathItem.substringAfterLast('/').ifBlank { "Root" }
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
                FileNodeRowCard(
                    node = node,
                    isAdvancedMode = state.isAdvancedMode,
                    onTap = { onFileTapped(node) },
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
                    .height(185.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(185.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC0B0F17),
                                Color(0xF20B0F17)
                            )
                        )
                    )
                    .padding(16.dp)
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
                        Column {
                            Text(
                                text = "LUMA FILES • STORAGE ENGINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                            Text(
                                text = activeRoot?.title ?: "Luma Studio Workspace",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (state.hasAllFilesAccess) {
                                EmeraldVault.copy(alpha = 0.25f)
                            } else {
                                AmberAdvanced.copy(alpha = 0.25f)
                            }
                        ) {
                            Text(
                                text = if (state.hasAllFilesAccess) "MAX ACCESS ACTIVE" else "WORKSPACE + SAF",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.hasAllFilesAccess) EmeraldVault else AmberAdvanced,
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
                                text = if (state.hasAllFilesAccess) "Access Settings" else "All Files Access",
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
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
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
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(accentColor.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
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
    isAdvancedMode: Boolean,
    onTap: () -> Unit,
    onEncryptToVault: () -> Unit,
    onInspectChecksums: () -> Unit,
    onRequestRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val (icon, tint) = when (node.category) {
        FileFormatCategory.DIRECTORY -> Icons.Default.Folder to ElectricCyan
        FileFormatCategory.MARKDOWN, FileFormatCategory.TEXT_CODE -> Icons.Default.Code to ElectricCyan
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
            .clickable { onTap() }
            .testTag("file_row_${node.name}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(tint.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = node.category.label,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
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
                        text = { Text(if (node.isDirectory) "Open Directory" else "Open / Edit File...") },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onTap()
                        }
                    )
                    if (!node.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("Lock in AES-256 PIN Vault") },
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
