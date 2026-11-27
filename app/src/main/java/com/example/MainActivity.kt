package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.example.ui.components.RecycleBinModalSheet
import com.example.ui.screens.AdvancedWorkstationHubScreen
import com.example.ui.screens.DownloadManagerScreen
import com.example.ui.screens.ExplorerScreen
import com.example.ui.screens.SecureVaultScreen
import com.example.ui.screens.StorageAnalyzerScreen
import com.example.ui.screens.StudioEditorModal
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize Firebase App Check BEFORE setContent / ViewModels for AI Photo Enhance
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            val lumaViewModel: LumaFilesViewModel = viewModel()
            val state by lumaViewModel.uiState.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = state.isDarkThemeOverride ?: systemDark

            // Zero-trace vault security: shred volatile previews when app goes to background
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) {
                        lumaViewModel.shredVolatileVaultPreviews()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            MyApplicationTheme(darkTheme = useDarkTheme, dynamicColor = true) {
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

    // Auto-dismiss transient status banner after 3.2s
    LaunchedEffect(state.statusBannerMessage) {
        if (state.statusBannerMessage != null) {
            delay(3200L)
            viewModel.clearStatusBanner()
        }
    }

    val allFilesSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshPermissionsAndRoots()
    }

    val safTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) viewModel.onSafTreeGranted(treeUri)
    }

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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                LumaMinimalHeader(
                    isAdvancedMode = state.isAdvancedMode,
                    onToggleAdvancedMode = { viewModel.toggleAdvancedMode() },
                    onCycleTheme = { viewModel.cycleThemePosture() }
                )
            },
            bottomBar = {
                NavigationBar(modifier = Modifier.testTag("luma_bottom_nav")) {
                    NavigationBarItem(
                        selected = state.activeTab == LumaTab.EXPLORER,
                        onClick = { viewModel.selectTab(LumaTab.EXPLORER) },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Files") },
                        label = { Text(stringResource(R.string.nav_explorer)) },
                        modifier = Modifier.testTag("nav_tab_explorer")
                    )
                    NavigationBarItem(
                        selected = state.activeTab == LumaTab.ANALYZER,
                        onClick = { viewModel.selectTab(LumaTab.ANALYZER) },
                        icon = { Icon(Icons.Default.PieChart, contentDescription = "Storage") },
                        label = { Text(stringResource(R.string.nav_analyzer)) },
                        modifier = Modifier.testTag("nav_tab_analyzer")
                    )
                    NavigationBarItem(
                        selected = state.activeTab == LumaTab.VAULT,
                        onClick = { viewModel.selectTab(LumaTab.VAULT) },
                        icon = { Icon(Icons.Default.Lock, contentDescription = "Vault") },
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
                        icon = { Icon(Icons.Default.Tune, contentDescription = "Tools") },
                        label = { Text(stringResource(R.string.nav_advanced)) },
                        modifier = Modifier.testTag("nav_tab_advanced")
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedVisibility(visible = state.statusBannerMessage != null) {
                    state.statusBannerMessage?.let { msg ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
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
                                    Text("OK")
                                }
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (state.activeTab) {
                        LumaTab.EXPLORER -> ExplorerScreen(
                            state = state,
                            onRequestAllFilesAccess = {
                                viewModel.dismissPermissionOnboarding(true)
                                runCatching {
                                    allFilesSettingsLauncher.launch(LumaFormatUtils.buildAllFilesAccessIntent(context))
                                }
                            },
                            onRequestSafFolderMount = {
                                viewModel.dismissPermissionOnboarding(true)
                                runCatching { safTreeLauncher.launch(null) }
                            },
                            onSelectStorageRoot = { root ->
                                if (root.requiresAllFilesPermission) {
                                    runCatching {
                                        allFilesSettingsLauncher.launch(LumaFormatUtils.buildAllFilesAccessIntent(context))
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
                            onOpenExternal = { node ->
                                LumaFormatUtils.launchExternalAppChooser(context, node) { diag ->
                                    viewModel.showCustomDiagnosticError(diag)
                                }
                            },
                            onShareFile = { node ->
                                LumaFormatUtils.shareFile(context, node) { diag ->
                                    viewModel.showCustomDiagnosticError(diag)
                                }
                            },
                            onToggleSelectFile = { node -> viewModel.toggleFileSelection(node) },
                            onSelectAllInDir = { viewModel.selectAllInCurrentDir() },
                            onClearSelection = { viewModel.clearSelection() },
                            onStageSingleClipboard = { node, isMove -> viewModel.stageSingleFileForClipboard(node, isMove) },
                            onStageBatchClipboard = { isMove -> viewModel.stageSelectedForClipboard(isMove) },
                            onSetConflictPolicy = { policy -> viewModel.setClipboardConflictPolicy(policy) },
                            onPasteClipboardHere = { viewModel.pasteClipboardToCurrentDirectory() },
                            onClearClipboard = { viewModel.clearClipboard() },
                            onBatchDeleteSelected = { viewModel.batchDeleteSelected() },
                            onShowBatchZipDialog = { showBatchZipDialog = true },
                            onBatchEncryptSelected = { viewModel.batchEncryptSelectedToVault() },
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
                            onSetupCredential = { secret, mode, q, a -> viewModel.setupVaultCredential(secret, mode, q, a) },
                            onUnlockVault = { secret -> viewModel.unlockVaultWithPin(secret) },
                            onLockVault = { viewModel.lockVaultSession() },
                            onResetWithRecovery = { ans, newSecret, newMode -> viewModel.resetPinViaRecovery(ans, newSecret, newMode) },
                            onOpenVaultItem = { item -> viewModel.openVaultItemForSeamlessViewing(item) },
                            onRestoreVaultItem = { item -> viewModel.restoreFileFromSecureVault(item) },
                            onDeleteVaultItem = { item -> viewModel.deleteVaultItemPermanently(item) },
                            onGoToExplorerToLockFiles = { viewModel.selectTab(LumaTab.EXPLORER) }
                        )

                        LumaTab.DOWNLOADS -> DownloadManagerScreen(
                            tasks = downloadTasks,
                            isAdvancedMode = state.isAdvancedMode,
                            isResolvingMediaUrl = state.isResolvingMediaUrl,
                            resolvedManifest = state.resolvedMediaManifest,
                            selectedStreamVariantId = state.selectedStreamVariantId,
                            sniffedWebMedia = state.sniffedWebMedia,
                            onInspectUrl = { url -> viewModel.inspectMediaUrl(url) },
                            onSelectStreamVariant = { id -> viewModel.selectMediaStreamVariant(id) },
                            onStartSmartDownload = { url, name, threads, expectedSha ->
                                viewModel.enqueueDownload(url, name, threads, expectedSha256 = expectedSha)
                            },
                            onPauseTask = { id -> viewModel.pauseDownloadTask(id) },
                            onResumeTask = { id -> viewModel.resumeDownloadTask(id) },
                            onRecoverQueue = { viewModel.recoverPersistentDownloads() },
                            onDeleteTask = { task -> viewModel.deleteDownloadTask(task, deleteFileFromDisk = true) },
                            onOpenCompletedFile = { task -> viewModel.openDownloadedFile(task) },
                            onMediaDetectedInBrowser = { media -> viewModel.onWebMediaSniffed(media) },
                            onClearDetectedMedia = { viewModel.clearSniffedWebMedia() },
                            onDownloadSniffedMedia = { media -> viewModel.enqueueSniffedMediaDownload(media) }
                        )

                        LumaTab.ADVANCED_HUB -> AdvancedWorkstationHubScreen(
                            state = state,
                            bookmarks = bookmarks,
                            logs = operationLogs,
                            onToggleAdvancedMode = { viewModel.toggleAdvancedMode() },
                            onOpenBatchRegexDialog = { showBatchRenameDialog = true },
                            onRequestAllFilesAccess = {
                                runCatching {
                                    allFilesSettingsLauncher.launch(LumaFormatUtils.buildAllFilesAccessIntent(context))
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
        }

        // True Edge-to-Edge Full-Screen V2 Media & Document Studio Overlay (covers top/bottom bars completely!)
        state.activeStudioContent?.let { studioContent ->
            StudioEditorModal(
                studioContent = studioContent,
                isVaultPreview = state.activeVaultPreviewItem != null,
                siblingMediaFiles = state.siblingMediaFiles,
                aiEnhanceState = state.aiEnhanceState,
                photoEngine = viewModel.photoEngine,
                activeZipEntryPreview = state.activeZipEntryPreview,
                onClose = { viewModel.closeStudioEditor() },
                onOpenSiblingFile = { sibling -> viewModel.confirmOpenInLumaStudio(sibling) },
                onRenameFile = { node, newName -> viewModel.renameItem(node, newName) },
                onDeleteFile = { node -> viewModel.deleteItem(node) },
                onLockToVault = { node -> viewModel.moveFileToSecureVault(node) },
                onTriggerAiAutoEnhance = { bmp, onApplied -> viewModel.triggerAiAutoEnhance(bmp, onApplied) },
                onTriggerAiRemaster = { bmp, prompt, onResult -> viewModel.triggerAiRemaster(bmp, prompt, onResult) },
                onSaveEditedPhoto = { node, bmp, overwrite -> viewModel.saveEditedPhoto(node, bmp, overwrite) },
                onCaptureVideoFrame = { node, bmp -> viewModel.captureVideoFrame(node, bmp) },
                onRenderPdfPage = { node, idx -> viewModel.renderPdfPageBitmap(node, idx) },
                onSaveText = { node, newText, enc -> viewModel.saveTextEditorChanges(node, newText, enc) },
                onFormatStructuredText = { node, rawText, enc -> viewModel.formatStructuredTextInStudio(node, rawText, enc) },
                onSaveDocx = { node, paragraphs -> viewModel.saveDocxEditorChanges(node, paragraphs) },
                onSaveCsvGrid = { node, delim, headers, rows -> viewModel.saveCsvGridChanges(node, delim, headers, rows) },
                onSelectSqliteTable = { node, tbl -> viewModel.selectSqliteTableInStudio(node, tbl) },
                onExecuteSqliteQuery = { node, tbl, sql -> viewModel.executeSqliteQueryInStudio(node, tbl, sql) },
                onSavePdfNotes = { node, notes -> viewModel.savePdfAnnotation(node, notes) },
                onPreviewZipEntry = { node, entry -> viewModel.previewZipEntry(node, entry) },
                onDismissZipEntryPreview = { viewModel.dismissZipEntryPreview() },
                onExtractSingleZipEntry = { node, entry -> viewModel.extractSingleZipEntry(node, entry) },
                onExtractZip = { node -> viewModel.extractArchiveInStudio(node) },
                onAddZipEntry = { node, entryName, body -> viewModel.addEntryToZipInStudio(node, entryName, body) },
                onSwitchToHex = { node, offset -> viewModel.confirmOpenInLumaStudio(node, forceHexInspector = true, hexOffset = offset) },
                onPatchHexByte = { node, offset, hexByte, winOffset -> viewModel.patchHexByteInStudio(node, offset, hexByte, winOffset) },
                onOpenExternal = { node ->
                    LumaFormatUtils.launchExternalAppChooser(context, node) { diag ->
                        viewModel.showCustomDiagnosticError(diag)
                    }
                },
                onReportShareOrInstallError = { diag -> viewModel.showCustomDiagnosticError(diag) }
            )
        }
    }

    // ==================== DIALOGS ====================

    if (state.showPermissionOnboardingDialog) {
        FirstLaunchPermissionOnboardingDialog(
            onGrantAllFilesAccess = {
                viewModel.dismissPermissionOnboarding(true)
                runCatching { allFilesSettingsLauncher.launch(LumaFormatUtils.buildAllFilesAccessIntent(context)) }
            },
            onMountSafFolder = {
                viewModel.dismissPermissionOnboarding(true)
                runCatching { safTreeLauncher.launch(null) }
            },
            onContinueAndRemember = { viewModel.dismissPermissionOnboarding(true) }
        )
    }

    if (state.pendingDeleteTargets.isNotEmpty()) {
        DeleteChoiceDialog(
            targets = state.pendingDeleteTargets,
            onMoveToRecycleBin = { viewModel.confirmMovePendingToRecycleBin() },
            onPermanentShred = { viewModel.confirmPermanentlyShredPending() },
            onDismiss = { viewModel.dismissDeleteChoiceDialog() }
        )
    }

    if (state.showRecycleBinSheet) {
        RecycleBinModalSheet(
            items = state.recycleBinItems,
            onRestoreItem = { item -> viewModel.restoreRecycleBinItem(item) },
            onShredItem = { item -> viewModel.permanentlyDeleteRecycleBinItem(item) },
            onEmptyAll = { viewModel.emptyAllRecycleBin() },
            onDismiss = { viewModel.toggleRecycleBinSheet(false) }
        )
    }

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

    if (showCreateDialog) {
        CreateFileOrFolderDialog(
            onConfirm = { name, isDir, content -> viewModel.createNewItem(name, isDir, content) },
            onDismiss = { showCreateDialog = false }
        )
    }

    if (showBatchRenameDialog) {
        BatchRegexRenameDialog(
            currentDirectory = state.currentPath,
            onConfirm = { pattern, replacement, prefix -> viewModel.runBatchRegexRename(pattern, replacement, prefix) },
            onDismiss = { showBatchRenameDialog = false }
        )
    }

    if (showBatchZipDialog) {
        BatchZipNameDialog(
            selectedCount = state.selectedFilePaths.size,
            onConfirm = { archiveName -> viewModel.batchCompressSelectedToZip(archiveName) },
            onDismiss = { showBatchZipDialog = false }
        )
    }

    state.activeDiagnosticError?.let { diagError ->
        ExactDiagnosticErrorDialog(
            error = diagError,
            onDismiss = { viewModel.dismissDiagnosticError() }
        )
    }
}

@Composable
private fun LumaMinimalHeader(
    isAdvancedMode: Boolean,
    onToggleAdvancedMode: () -> Unit,
    onCycleTheme: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.brand_original_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("luma_files_brand_header")
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = isAdvancedMode,
                    onClick = onToggleAdvancedMode,
                    label = {
                        Text(
                            text = if (isAdvancedMode) "Pro" else "Standard",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.testTag("top_advanced_mode_toggle")
                )

                IconButton(
                    onClick = onCycleTheme,
                    modifier = Modifier.testTag("theme_cycle_btn")
                ) {
                    Icon(
                        imageVector = if (isSystemInDarkTheme()) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Switch Theme"
                    )
                }
            }
        }
    }
}
