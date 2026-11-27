package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.model.LumaTab
import com.example.ui.LumaFilesViewModel
import com.example.ui.LumaFormatUtils
import com.example.ui.components.BatchRegexRenameDialog
import com.example.ui.components.BatchZipNameDialog
import com.example.ui.components.CreateFileOrFolderDialog
import com.example.ui.components.DeleteChoiceDialog
import com.example.ui.components.ExactDiagnosticErrorDialog
import com.example.ui.components.FileChecksumInspectorDialog
import com.example.ui.components.FirstLaunchPermissionOnboardingDialog
import com.example.ui.components.OpenWithPermissionDialog
import com.example.ui.components.RecycleBinModalSheet
import com.example.ui.screens.AdvancedWorkstationHubScreen
import com.example.ui.screens.DownloadManagerScreen
import com.example.ui.screens.ExplorerScreen
import com.example.ui.screens.SecureVaultScreen
import com.example.ui.screens.StorageAnalyzerScreen
import com.example.ui.screens.StudioEditorModal
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val lumaViewModel: LumaFilesViewModel = viewModel()
            val state by lumaViewModel.uiState.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = state.isDarkThemeOverride ?: systemDark

            MyApplicationTheme(darkTheme = useDarkTheme) {
                LumaFilesRootScreen(viewModel = lumaViewModel)
            }
        }
    }
}

@Composable
fun LumaFilesRootScreen(viewModel: LumaFilesViewModel) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val vaultItems by viewModel.vaultItems.collectAsStateWithLifecycle()
    val downloadTasks by viewModel.downloadTasks.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val operationLogs by viewModel.operationLogs.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showBatchRenameDialog by remember { mutableStateOf(false) }
    var showBatchZipDialog by remember { mutableStateOf(false) }

    // All Files Access Settings Launcher
    val allFilesSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshPermissionsAndRoots()
    }

    // SAF OpenDocumentTree Launcher
    val safTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.onSafTreeGranted(treeUri)
        }
    }

    // Back button handling for directory stack, multi-select & secondary tabs
    BackHandler(
        enabled = state.activeStudioContent != null ||
            state.selectedFilePaths.isNotEmpty() ||
            state.selectedCategoryFilter != null ||
            state.directoryStack.size > 1 ||
            state.activeTab != LumaTab.EXPLORER
    ) {
        if (!viewModel.navigateUp()) {
            viewModel.selectTab(LumaTab.EXPLORER)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            LumaTopBrandHeader(
                isAdvancedMode = state.isAdvancedMode,
                onToggleAdvancedMode = { viewModel.toggleAdvancedMode() },
                onCycleTheme = { viewModel.cycleThemePosture() }
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("luma_bottom_nav")
            ) {
                NavigationBarItem(
                    selected = state.activeTab == LumaTab.EXPLORER,
                    onClick = { viewModel.selectTab(LumaTab.EXPLORER) },
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Explorer") },
                    label = { Text(stringResource(R.string.nav_explorer)) },
                    modifier = Modifier.testTag("nav_tab_explorer")
                )
                NavigationBarItem(
                    selected = state.activeTab == LumaTab.ANALYZER,
                    onClick = { viewModel.selectTab(LumaTab.ANALYZER) },
                    icon = { Icon(Icons.Default.Analytics, contentDescription = "Analyzer") },
                    label = { Text(stringResource(R.string.nav_analyzer)) },
                    modifier = Modifier.testTag("nav_tab_analyzer")
                )
                NavigationBarItem(
                    selected = state.activeTab == LumaTab.VAULT,
                    onClick = { viewModel.selectTab(LumaTab.VAULT) },
                    icon = { Icon(Icons.Default.Lock, contentDescription = "Secure Vault") },
                    label = { Text(stringResource(R.string.nav_vault)) },
                    modifier = Modifier.testTag("nav_tab_vault")
                )
                NavigationBarItem(
                    selected = state.activeTab == LumaTab.DOWNLOADS,
                    onClick = { viewModel.selectTab(LumaTab.DOWNLOADS) },
                    icon = { Icon(Icons.Default.CloudDownload, contentDescription = "Downloads") },
                    label = { Text(stringResource(R.string.nav_downloads)) },
                    modifier = Modifier.testTag("nav_tab_downloads")
                )
                NavigationBarItem(
                    selected = state.activeTab == LumaTab.ADVANCED_HUB,
                    onClick = { viewModel.selectTab(LumaTab.ADVANCED_HUB) },
                    icon = { Icon(Icons.Default.Terminal, contentDescription = "Advanced") },
                    label = { Text(stringResource(R.string.nav_advanced)) },
                    modifier = Modifier.testTag("nav_tab_advanced")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Status Feedback Banner
                AnimatedVisibility(visible = state.statusBannerMessage != null) {
                    state.statusBannerMessage?.let { msg ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.clearStatusBanner() }) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }

                // Active Tab Screen
                Box(modifier = Modifier.weight(1f)) {
                    when (state.activeTab) {
                        LumaTab.EXPLORER -> ExplorerScreen(
                            state = state,
                            onRequestAllFilesAccess = {
                                viewModel.dismissPermissionOnboarding(rememberDecision = true)
                                runCatching {
                                    allFilesSettingsLauncher.launch(
                                        LumaFormatUtils.buildAllFilesAccessIntent(context)
                                    )
                                }.onFailure { err ->
                                    viewModel.reportDiagnosticError(
                                        "Launch All Files Access Settings",
                                        "android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION",
                                        err
                                    )
                                }
                            },
                            onRequestSafFolderMount = {
                                viewModel.dismissPermissionOnboarding(rememberDecision = true)
                                runCatching {
                                    safTreeLauncher.launch(null)
                                }.onFailure { err ->
                                    viewModel.reportDiagnosticError(
                                        "Launch SAF OpenDocumentTree Picker",
                                        "content://com.android.externalstorage.documents",
                                        err
                                    )
                                }
                            },
                            onSelectStorageRoot = { root ->
                                if (root.requiresAllFilesPermission) {
                                    runCatching {
                                        allFilesSettingsLauncher.launch(
                                            LumaFormatUtils.buildAllFilesAccessIntent(context)
                                        )
                                    }.onFailure {
                                        viewModel.navigateToDirectory(root.path, resetStack = true)
                                    }
                                } else {
                                    viewModel.navigateToDirectory(root.path, resetStack = true)
                                }
                            },
                            onSelectCategoryCard = { cat -> viewModel.setCategoryFilter(cat) },
                            onOpenSecureVaultTab = { viewModel.selectTab(LumaTab.VAULT) },
                            onOpenRecycleBin = { viewModel.toggleRecycleBinSheet(true) },
                            onExecuteUndo = { viewModel.performLastUndo() },
                            onDismissUndo = { viewModel.dismissUndoBanner() },
                            onNavigateBreadcrumb = { path -> viewModel.navigateToDirectory(path) },
                            onSearchChanged = { q, regex -> viewModel.updateSearchQuery(q, regex) },
                            onSortChanged = { opt -> viewModel.setSortOption(opt) },
                            onFileTapped = { node -> viewModel.onFileNodeTapped(node) },
                            onToggleSelectFile = { node -> viewModel.toggleFileSelection(node) },
                            onSelectAllInDir = { viewModel.selectAllInCurrentDir() },
                            onClearSelection = { viewModel.clearSelection() },
                            onStageSingleClipboard = { node, isMove ->
                                viewModel.stageSingleFileForClipboard(node, isMove)
                            },
                            onStageBatchClipboard = { isMove ->
                                viewModel.stageSelectedForClipboard(isMove)
                            },
                            onSetConflictPolicy = { policy ->
                                viewModel.setClipboardConflictPolicy(policy)
                            },
                            onPasteClipboardHere = {
                                viewModel.pasteClipboardToCurrentDirectory()
                            },
                            onClearClipboard = {
                                viewModel.clearClipboard()
                            },
                            onBatchDeleteSelected = {
                                viewModel.batchDeleteSelected()
                            },
                            onShowBatchZipDialog = {
                                showBatchZipDialog = true
                            },
                            onBatchEncryptSelected = {
                                viewModel.batchEncryptSelectedToVault()
                            },
                            onEncryptToVault = { node -> viewModel.moveFileToSecureVault(node) },
                            onInspectChecksums = { node -> viewModel.inspectFileChecksums(node) },
                            onRenameFile = { node, newName -> viewModel.renameItem(node, newName) },
                            onDeleteFile = { node -> viewModel.deleteItem(node) },
                            onShowCreateDialog = { showCreateDialog = true },
                            onShowBatchRenameDialog = { showBatchRenameDialog = true },
                            onBookmarkCurrentDir = { viewModel.bookmarkCurrentDirectory() }
                        )

                        LumaTab.ANALYZER -> StorageAnalyzerScreen(
                            state = state,
                            onRescanStorage = { viewModel.runStorageAnalyzer() },
                            onOpenFile = { node -> viewModel.onFileNodeTapped(node) },
                            onDeleteDuplicateFile = { node -> viewModel.deleteItem(node) }
                        )

                        LumaTab.VAULT -> SecureVaultScreen(
                            state = state,
                            vaultItems = vaultItems,
                            onSetupCredential = { secret, mode, q, a ->
                                viewModel.setupVaultCredential(secret, mode, q, a)
                            },
                            onUnlockVault = { secret ->
                                viewModel.unlockVaultWithPin(secret)
                            },
                            onLockVault = { viewModel.lockVaultSession() },
                            onResetWithRecovery = { ans, newSecret, newMode ->
                                viewModel.resetPinViaRecovery(ans, newSecret, newMode)
                            },
                            onRestoreVaultItem = { item -> viewModel.restoreFileFromSecureVault(item) },
                            onGoToExplorerToLockFiles = { viewModel.selectTab(LumaTab.EXPLORER) }
                        )

                        LumaTab.DOWNLOADS -> DownloadManagerScreen(
                            tasks = downloadTasks,
                            presets = viewModel.downloadPresets,
                            isAdvancedMode = state.isAdvancedMode,
                            isResolvingMediaUrl = state.isResolvingMediaUrl,
                            resolvedManifest = state.resolvedMediaManifest,
                            selectedStreamVariantId = state.selectedStreamVariantId,
                            onInspectUrl = { url -> viewModel.inspectMediaUrl(url) },
                            onSelectStreamVariant = { id -> viewModel.selectMediaStreamVariant(id) },
                            onStartSmartDownload = { url, name, threads, expectedSha ->
                                viewModel.enqueueDownload(
                                    url = url,
                                    customFileName = name,
                                    threads = threads,
                                    expectedSha256 = expectedSha
                                )
                            },
                            onPauseTask = { id -> viewModel.pauseDownloadTask(id) },
                            onResumeTask = { id -> viewModel.resumeDownloadTask(id) },
                            onRecoverQueue = { viewModel.recoverPersistentDownloads() },
                            onDeleteTask = { task -> viewModel.deleteDownloadTask(task, deleteFileFromDisk = true) },
                            onOpenCompletedFile = { task -> viewModel.openDownloadedFile(task) }
                        )

                        LumaTab.ADVANCED_HUB -> AdvancedWorkstationHubScreen(
                            state = state,
                            bookmarks = bookmarks,
                            logs = operationLogs,
                            onToggleAdvancedMode = { viewModel.toggleAdvancedMode() },
                            onOpenBatchRegexDialog = { showBatchRenameDialog = true },
                            onRequestAllFilesAccess = {
                                runCatching {
                                    allFilesSettingsLauncher.launch(
                                        LumaFormatUtils.buildAllFilesAccessIntent(context)
                                    )
                                }
                            },
                            onJumpToBookmark = { bm ->
                                viewModel.navigateToDirectory(bm.pathOrUri, resetStack = true)
                                viewModel.selectTab(LumaTab.EXPLORER)
                            },
                            onRemoveBookmark = { id -> viewModel.removeBookmark(id) },
                            onClearLogs = { viewModel.clearAllOperationLogs() }
                        )
                    }
                }
            }

            // Full-Screen Built-In Lossless Format Studio Overlay
            state.activeStudioContent?.let { studioContent ->
                StudioEditorModal(
                    studioContent = studioContent,
                    onClose = { viewModel.closeStudioEditor() },
                    onSaveText = { node, newText, enc ->
                        viewModel.saveTextEditorChanges(node, newText, enc)
                    },
                    onFormatStructuredText = { node, rawText, enc ->
                        viewModel.formatStructuredTextInStudio(node, rawText, enc)
                    },
                    onSaveDocx = { node, paragraphs ->
                        viewModel.saveDocxEditorChanges(node, paragraphs)
                    },
                    onSaveCsvGrid = { node, delimiter, headers, rows ->
                        viewModel.saveCsvGridChanges(node, delimiter, headers, rows)
                    },
                    onSelectSqliteTable = { node, tableName ->
                        viewModel.selectSqliteTableInStudio(node, tableName)
                    },
                    onExecuteSqliteQuery = { node, activeTable, sqlQuery ->
                        viewModel.executeSqliteQueryInStudio(node, activeTable, sqlQuery)
                    },
                    onSavePdfNotes = { node, notes ->
                        viewModel.savePdfAnnotation(node, notes)
                    },
                    onNavigatePdfPage = { node, nextIdx ->
                        viewModel.confirmOpenInLumaStudio(node, pdfPageIndex = nextIdx)
                    },
                    onExtractZip = { node ->
                        viewModel.extractArchiveInStudio(node)
                    },
                    onAddZipEntry = { node, entryName, entryBody ->
                        viewModel.addEntryToZipInStudio(node, entryName, entryBody)
                    },
                    onSwitchToHex = { node, offset ->
                        viewModel.confirmOpenInLumaStudio(node, forceHexInspector = true, hexOffset = offset)
                    },
                    onPatchHexByte = { node, offset, hexByte, windowOffset ->
                        viewModel.patchHexByteInStudio(node, offset, hexByte, windowOffset)
                    },
                    onOpenExternal = { node ->
                        LumaFormatUtils.launchExternalAppChooser(context, node) { diagErr ->
                            viewModel.showCustomDiagnosticError(diagErr)
                        }
                    }
                )
            }
        }
    }

    // ==================== DIALOGS & MODALS ====================

    // 0. First-Launch Permission & Storage Setup Modal (Remembered so it only shows once)
    if (state.showPermissionOnboardingDialog) {
        FirstLaunchPermissionOnboardingDialog(
            onGrantAllFilesAccess = {
                viewModel.dismissPermissionOnboarding(rememberDecision = true)
                runCatching {
                    allFilesSettingsLauncher.launch(
                        LumaFormatUtils.buildAllFilesAccessIntent(context)
                    )
                }.onFailure { err ->
                    viewModel.reportDiagnosticError(
                        "Launch All Files Access Settings",
                        "android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION",
                        err
                    )
                }
            },
            onMountSafFolder = {
                viewModel.dismissPermissionOnboarding(rememberDecision = true)
                runCatching {
                    safTreeLauncher.launch(null)
                }
            },
            onContinueAndRemember = {
                viewModel.dismissPermissionOnboarding(rememberDecision = true)
            }
        )
    }

    // 1. Delete Choice Dialog (Prompt on every delete: Recycle Bin vs Permanent Zero-Overwrite Shred)
    if (state.pendingDeleteTargets.isNotEmpty()) {
        DeleteChoiceDialog(
            targets = state.pendingDeleteTargets,
            onMoveToRecycleBin = { viewModel.confirmMovePendingToRecycleBin() },
            onPermanentShred = { viewModel.confirmPermanentlyShredPending() },
            onDismiss = { viewModel.dismissDeleteChoiceDialog() }
        )
    }

    // 2. Recycle Bin Management Sheet
    if (state.showRecycleBinSheet) {
        RecycleBinModalSheet(
            items = state.recycleBinItems,
            onRestoreItem = { item -> viewModel.restoreRecycleBinItem(item) },
            onShredItem = { item -> viewModel.permanentlyDeleteRecycleBinItem(item) },
            onEmptyAll = { viewModel.emptyAllRecycleBin() },
            onDismiss = { viewModel.toggleRecycleBinSheet(false) }
        )
    }

    // 3. Open-With Permission & Routing Prompt (Shown whenever user taps a file)
    state.pendingOpenWithFile?.let { targetNode ->
        OpenWithPermissionDialog(
            fileNode = targetNode,
            isAdvancedMode = state.isAdvancedMode,
            onOpenInLumaStudio = {
                viewModel.confirmOpenInLumaStudio(targetNode, forceHexInspector = false)
            },
            onOpenInHexStudio = {
                viewModel.confirmOpenInLumaStudio(targetNode, forceHexInspector = true)
            },
            onOpenWithExternalApp = {
                viewModel.dismissOpenWithDialog()
                LumaFormatUtils.launchExternalAppChooser(context, targetNode) { diagErr ->
                    viewModel.showCustomDiagnosticError(diagErr)
                }
            },
            onEncryptToVault = {
                viewModel.dismissOpenWithDialog()
                viewModel.moveFileToSecureVault(targetNode)
            },
            onDismiss = { viewModel.dismissOpenWithDialog() }
        )
    }

    // 4. Checksum & Attribute Inspector Modal
    state.inspectedFileNode?.let { inspected ->
        FileChecksumInspectorDialog(
            node = inspected,
            md5 = state.inspectedFileMd5,
            sha256 = state.inspectedFileSha256,
            isComputing = state.isComputingChecksums,
            onOpenHex = {
                viewModel.dismissFileInspector()
                viewModel.confirmOpenInLumaStudio(inspected, forceHexInspector = true)
            },
            onDismiss = { viewModel.dismissFileInspector() }
        )
    }

    // 5. Create New File / Folder Dialog
    if (showCreateDialog) {
        CreateFileOrFolderDialog(
            onConfirm = { name, isDir, content ->
                viewModel.createNewItem(name, isDir, content)
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    // 6. Batch Regex Rename Dialog
    if (showBatchRenameDialog) {
        BatchRegexRenameDialog(
            currentDirectory = state.currentPath,
            onConfirm = { pattern, replacement, prefix ->
                viewModel.runBatchRegexRename(pattern, replacement, prefix)
            },
            onDismiss = { showBatchRenameDialog = false }
        )
    }

    // 7. Batch Compress Selected to ZIP Dialog
    if (showBatchZipDialog) {
        BatchZipNameDialog(
            selectedCount = state.selectedFilePaths.size,
            onConfirm = { archiveName ->
                viewModel.batchCompressSelectedToZip(archiveName)
            },
            onDismiss = { showBatchZipDialog = false }
        )
    }

    // 8. Exact Error Diagnostic Report Dialog
    state.activeDiagnosticError?.let { diagError ->
        ExactDiagnosticErrorDialog(
            error = diagError,
            onDismiss = { viewModel.dismissDiagnosticError() }
        )
    }
}

@Composable
private fun LumaTopBrandHeader(
    isAdvancedMode: Boolean,
    onToggleAdvancedMode: () -> Unit,
    onCycleTheme: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Luma Files Brand Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.brand_original_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("luma_files_brand_header")
                    )
                    Text(
                        text = stringResource(R.string.brand_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = isAdvancedMode,
                    onClick = onToggleAdvancedMode,
                    label = {
                        Text(
                            text = if (isAdvancedMode) "ADV MODE" else "DEFAULT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Toggle Advanced Mode",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    modifier = Modifier.testTag("top_advanced_mode_toggle")
                )

                IconButton(
                    onClick = onCycleTheme,
                    modifier = Modifier.testTag("theme_cycle_btn")
                ) {
                    Icon(
                        imageVector = if (isSystemInDarkTheme()) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Switch Dark/Light Theme"
                    )
                }
            }
        }
    }
}
