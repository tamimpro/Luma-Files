package com.example.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BookmarkEntity
import com.example.data.local.DownloadTaskEntity
import com.example.data.local.LumaDatabase
import com.example.data.local.LumaRepository
import com.example.data.local.OperationLogEntity
import com.example.data.local.VaultItemEntity
import com.example.domain.engine.CryptoVaultEngine
import com.example.domain.engine.FastDownloadEngine
import com.example.domain.engine.FileSystemAndFormatEngine
import com.example.domain.engine.PersistedDownloadCheckpoint
import com.example.domain.engine.PhotoAiStudioEngine
import com.example.domain.model.AiEnhanceUiState
import com.example.domain.model.ClipboardState
import com.example.domain.model.ConflictPolicy
import com.example.domain.model.DiagnosticError
import com.example.domain.model.FileFormatCategory
import com.example.domain.model.FileNode
import com.example.domain.model.LumaTab
import com.example.domain.model.PhotoAdjustments
import com.example.domain.model.RecycleBinItem
import com.example.domain.model.ResolvedMediaManifest
import com.example.domain.model.SniffedWebMedia
import com.example.domain.model.SortOption
import com.example.domain.model.StorageAnalyzerReport
import com.example.domain.model.StorageVolumeRoot
import com.example.domain.model.StudioContent
import com.example.domain.model.UndoOperation
import com.example.domain.model.VaultAuthMode
import com.example.domain.model.ZipEntryPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ExplorerUiState(
    val activeTab: LumaTab = LumaTab.EXPLORER,
    val isAdvancedMode: Boolean = false,
    val isDarkThemeOverride: Boolean? = null,
    val hasAllFilesAccess: Boolean = false,
    val isPermissionDecisionRemembered: Boolean = false,
    val showPermissionOnboardingDialog: Boolean = false,
    val storageRoots: List<StorageVolumeRoot> = emptyList(),
    val currentPath: String = "",
    val directoryStack: List<String> = emptyList(),
    val files: List<FileNode> = emptyList(),
    val selectedFilePaths: Set<String> = emptySet(),
    val clipboardState: ClipboardState = ClipboardState(),
    val selectedCategoryFilter: FileFormatCategory? = null,
    val sortOption: SortOption = SortOption.NAME_ASC,
    val searchQuery: String = "",
    val useRegexSearch: Boolean = false,
    val isLoading: Boolean = false,
    // Recycle Bin & Per-Delete Choice Prompt + Undo System
    val pendingDeleteTargets: List<FileNode> = emptyList(),
    val recycleBinItems: List<RecycleBinItem> = emptyList(),
    val showRecycleBinSheet: Boolean = false,
    val lastUndoOperation: UndoOperation? = null,
    // Active Full-Screen V2 Studio Viewer
    val activeStudioContent: StudioContent? = null,
    val siblingMediaFiles: List<FileNode> = emptyList(),
    val activeZipEntryPreview: ZipEntryPreview? = null,
    val aiEnhanceState: AiEnhanceUiState = AiEnhanceUiState.Idle,
    // Seamless Zero-Trace In-Vault Viewing State
    val activeVaultPreviewItem: VaultItemEntity? = null,
    val activeVaultPreviewTempPath: String? = null,
    // Secure Vault Auth State (Wrapped 256-Bit Master DEK)
    val isPinConfigured: Boolean = false,
    val configuredPinLength: Int = 4,
    val vaultAuthMode: VaultAuthMode = VaultAuthMode.PIN_4,
    val isVaultUnlocked: Boolean = false,
    val unlockedPinInMemory: String = "",
    val recoveryQuestion: String = "",
    val vaultAuthError: String? = null,
    // Smart Media Resolver & Web Sniffer State
    val isResolvingMediaUrl: Boolean = false,
    val resolvedMediaManifest: ResolvedMediaManifest? = null,
    val selectedStreamVariantId: String = "",
    val sniffedWebMedia: List<SniffedWebMedia> = emptyList(),
    // Storage Analyzer State
    val analyzerReport: StorageAnalyzerReport? = null,
    val isAnalyzingStorage: Boolean = false,
    // Checksum / File Inspector Modal
    val inspectedFileNode: FileNode? = null,
    val inspectedFileMd5: String = "",
    val inspectedFileSha256: String = "",
    val isComputingChecksums: Boolean = false,
    // User Feedback & Diagnostics
    val statusBannerMessage: String? = null,
    val activeDiagnosticError: DiagnosticError? = null
)

class LumaFilesViewModel(application: Application) : AndroidViewModel(application) {

    private val database = LumaDatabase.getInstance(application)
    private val repository = LumaRepository(database.lumaDao())
    val cryptoEngine = CryptoVaultEngine(application)
    val fileEngine = FileSystemAndFormatEngine(application, cryptoEngine)
    val downloadEngine = FastDownloadEngine(repository, cryptoEngine, viewModelScope)
    val photoEngine = PhotoAiStudioEngine()

    private val initialDefaultPath = fileEngine.getDefaultStartPath()
    private val initialHasAccess = fileEngine.hasAllFilesAccessPermission()
    private val initialRemembered = cryptoEngine.isPermissionOnboardingRemembered()

    private val _uiState = MutableStateFlow(
        ExplorerUiState(
            hasAllFilesAccess = initialHasAccess,
            isPermissionDecisionRemembered = initialRemembered,
            showPermissionOnboardingDialog = !initialHasAccess && !initialRemembered,
            storageRoots = fileEngine.getAvailableStorageRoots(),
            currentPath = initialDefaultPath,
            directoryStack = listOf(initialDefaultPath),
            recycleBinItems = fileEngine.listRecycleBinItems(),
            isPinConfigured = cryptoEngine.isPinConfigured(),
            configuredPinLength = cryptoEngine.getConfiguredPinLength(),
            vaultAuthMode = cryptoEngine.getConfiguredAuthMode(),
            recoveryQuestion = cryptoEngine.getRecoveryQuestion()
        )
    )
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    val vaultItems: StateFlow<List<VaultItemEntity>> = repository.vaultItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadTasks: StateFlow<List<DownloadTaskEntity>> = repository.downloadTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarks: StateFlow<List<BookmarkEntity>> = repository.bookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val operationLogs: StateFlow<List<OperationLogEntity>> = repository.operationLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        if (initialHasAccess) {
            cryptoEngine.setPermissionOnboardingRemembered(true)
        }
        // Shred any stale temporary vault previews from prior crashes
        viewModelScope.launch { cryptoEngine.shredAllVolatilePreviews() }
        refreshCurrentDirectory()
        runStorageAnalyzer()
        downloadEngine.recoverPersistentQueueOnLaunch { diag ->
            _uiState.update { it.copy(activeDiagnosticError = diag) }
        }
    }

    fun dismissPermissionOnboarding(rememberDecision: Boolean = true) {
        if (rememberDecision) {
            cryptoEngine.setPermissionOnboardingRemembered(true)
        }
        _uiState.update {
            it.copy(
                showPermissionOnboardingDialog = false,
                isPermissionDecisionRemembered = rememberDecision || it.isPermissionDecisionRemembered
            )
        }
    }

    fun selectTab(tab: LumaTab) {
        _uiState.update { it.copy(activeTab = tab) }
        if (tab == LumaTab.ANALYZER && _uiState.value.analyzerReport == null) {
            runStorageAnalyzer()
        }
    }

    fun toggleAdvancedMode() {
        val nextMode = !_uiState.value.isAdvancedMode
        _uiState.update {
            it.copy(
                isAdvancedMode = nextMode,
                useRegexSearch = if (!nextMode) false else it.useRegexSearch,
                statusBannerMessage = if (nextMode) "Advanced mode on" else "Standard mode"
            )
        }
        refreshCurrentDirectory()
    }

    fun cycleThemePosture() {
        _uiState.update { state ->
            val next = when (state.isDarkThemeOverride) {
                null -> true
                true -> false
                false -> null
            }
            state.copy(isDarkThemeOverride = next)
        }
    }

    fun refreshPermissionsAndRoots() {
        val hasAccess = fileEngine.hasAllFilesAccessPermission()
        val roots = fileEngine.getAvailableStorageRoots()
        if (hasAccess) {
            cryptoEngine.setPermissionOnboardingRemembered(true)
        }
        val currentState = _uiState.value
        val internalRootPath = Environment.getExternalStorageDirectory().absolutePath
        val shouldSwitchToInternalRoot = hasAccess &&
            !currentState.hasAllFilesAccess &&
            currentState.currentPath == fileEngine.lumaWorkspaceDir.absolutePath

        _uiState.update {
            it.copy(
                hasAllFilesAccess = hasAccess,
                isPermissionDecisionRemembered = cryptoEngine.isPermissionOnboardingRemembered(),
                showPermissionOnboardingDialog = if (hasAccess) false else it.showPermissionOnboardingDialog,
                storageRoots = roots,
                currentPath = if (shouldSwitchToInternalRoot) internalRootPath else it.currentPath,
                directoryStack = if (shouldSwitchToInternalRoot) listOf(internalRootPath) else it.directoryStack
            )
        }
        refreshCurrentDirectory()
    }

    fun onSafTreeGranted(treeUri: Uri) {
        viewModelScope.launch {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                getApplication<Application>().contentResolver.takePersistableUriPermission(treeUri, flags)
                cryptoEngine.setPermissionOnboardingRemembered(true)
                repository.addBookmark(
                    BookmarkEntity(
                        title = treeUri.lastPathSegment?.substringAfterLast(':') ?: "Folder",
                        pathOrUri = treeUri.toString(),
                        isSafTree = true
                    )
                )
                refreshPermissionsAndRoots()
                navigateToDirectory(treeUri.toString(), resetStack = true)
                showStatus("Folder mounted")
            } catch (e: Exception) {
                reportDiagnosticError("Folder Access", treeUri.toString(), e)
            }
        }
    }

    fun navigateToDirectory(pathOrUri: String, resetStack: Boolean = false) {
        _uiState.update { state ->
            val newStack = if (resetStack) {
                listOf(pathOrUri)
            } else {
                val existingIdx = state.directoryStack.indexOf(pathOrUri)
                if (existingIdx >= 0) {
                    state.directoryStack.subList(0, existingIdx + 1)
                } else {
                    state.directoryStack + pathOrUri
                }
            }
            state.copy(
                currentPath = pathOrUri,
                directoryStack = newStack,
                selectedFilePaths = emptySet(),
                selectedCategoryFilter = null
            )
        }
        refreshCurrentDirectory()
    }

    fun navigateUp(): Boolean {
        val currentState = _uiState.value
        if (currentState.activeStudioContent != null) {
            closeStudioEditor()
            return true
        }
        if (currentState.showRecycleBinSheet) {
            toggleRecycleBinSheet(false)
            return true
        }
        if (currentState.selectedFilePaths.isNotEmpty()) {
            clearSelection()
            return true
        }
        if (currentState.selectedCategoryFilter != null) {
            _uiState.update { it.copy(selectedCategoryFilter = null) }
            refreshCurrentDirectory()
            return true
        }
        if (currentState.directoryStack.size > 1) {
            val newStack = currentState.directoryStack.dropLast(1)
            val parentPath = newStack.last()
            _uiState.update {
                it.copy(
                    currentPath = parentPath,
                    directoryStack = newStack,
                    selectedFilePaths = emptySet()
                )
            }
            refreshCurrentDirectory()
            return true
        }
        return false
    }

    fun setCategoryFilter(category: FileFormatCategory?) {
        _uiState.update { it.copy(selectedCategoryFilter = category, selectedFilePaths = emptySet()) }
        refreshCurrentDirectory()
    }

    fun setSortOption(sortOption: SortOption) {
        _uiState.update { it.copy(sortOption = sortOption) }
        refreshCurrentDirectory()
    }

    fun updateSearchQuery(query: String, useRegex: Boolean = _uiState.value.useRegexSearch) {
        _uiState.update { it.copy(searchQuery = query, useRegexSearch = useRegex) }
        refreshCurrentDirectory()
    }

    fun refreshCurrentDirectory() {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { it.copy(isLoading = true) }
            val result = fileEngine.listDirectory(
                pathOrUri = state.currentPath,
                showHiddenFiles = state.isAdvancedMode,
                sortOption = state.sortOption,
                searchQuery = state.searchQuery,
                useRegexSearch = state.useRegexSearch && state.isAdvancedMode
            )
            result.fold(
                onSuccess = { listed ->
                    val filteredByCategory = if (state.selectedCategoryFilter != null) {
                        filterWorkspaceByCategory(state.selectedCategoryFilter)
                    } else {
                        listed
                    }
                    _uiState.update {
                        it.copy(
                            files = filteredByCategory,
                            recycleBinItems = fileEngine.listRecycleBinItems(),
                            isLoading = false
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false) }
                    reportDiagnosticError("Open Folder", state.currentPath, err)
                }
            )
        }
    }

    private fun filterWorkspaceByCategory(category: FileFormatCategory): List<FileNode> {
        val results = mutableListOf<FileNode>()
        val scanRoot = File(_uiState.value.currentPath).takeIf { it.exists() && it.canRead() }
            ?: fileEngine.lumaWorkspaceDir
        fun collect(dir: File, depth: Int) {
            if (depth > 4) return
            dir.listFiles()?.forEach { f ->
                if (f.isDirectory) {
                    if (!f.name.startsWith(".")) collect(f, depth + 1)
                } else {
                    val node = with(fileEngine) { f.toFileNode() }
                    val matches = when (category) {
                        FileFormatCategory.TEXT_CODE ->
                            node.category == FileFormatCategory.TEXT_CODE ||
                                node.category == FileFormatCategory.MARKDOWN ||
                                node.category == FileFormatCategory.STRUCTURED_DATA
                        FileFormatCategory.PDF ->
                            node.category == FileFormatCategory.PDF ||
                                node.category == FileFormatCategory.DOCX ||
                                node.category == FileFormatCategory.CSV_TSV
                        FileFormatCategory.VIDEO ->
                            node.category == FileFormatCategory.VIDEO ||
                                node.category == FileFormatCategory.AUDIO ||
                                node.category == FileFormatCategory.IMAGE
                        else -> node.category == category
                    }
                    if (matches) results.add(node)
                }
            }
        }
        collect(scanRoot, 0)
        return results
    }

    // ==================== MULTI-SELECT & CLIPBOARD ====================

    fun toggleFileSelection(node: FileNode) {
        _uiState.update { state ->
            val updated = state.selectedFilePaths.toMutableSet()
            if (updated.contains(node.path)) updated.remove(node.path) else updated.add(node.path)
            state.copy(selectedFilePaths = updated)
        }
    }

    fun selectAllInCurrentDir() {
        _uiState.update { state ->
            val allPaths = state.files.map { it.path }.toSet()
            val next = if (state.selectedFilePaths.size == allPaths.size) emptySet() else allPaths
            state.copy(selectedFilePaths = next)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedFilePaths = emptySet()) }
    }

    fun stageSingleFileForClipboard(node: FileNode, isMove: Boolean) {
        _uiState.update { state ->
            state.copy(
                selectedFilePaths = emptySet(),
                clipboardState = ClipboardState(
                    sourceNodes = listOf(node),
                    isMoveOperation = isMove,
                    conflictPolicy = state.clipboardState.conflictPolicy
                )
            )
        }
    }

    fun stageSelectedForClipboard(isMove: Boolean) {
        val state = _uiState.value
        val selectedNodes = state.files.filter { state.selectedFilePaths.contains(it.path) }
        if (selectedNodes.isEmpty()) return
        _uiState.update {
            it.copy(
                selectedFilePaths = emptySet(),
                clipboardState = ClipboardState(
                    sourceNodes = selectedNodes,
                    isMoveOperation = isMove,
                    conflictPolicy = it.clipboardState.conflictPolicy
                )
            )
        }
    }

    fun setClipboardConflictPolicy(policy: ConflictPolicy) {
        _uiState.update { it.copy(clipboardState = it.clipboardState.copy(conflictPolicy = policy)) }
    }

    fun clearClipboard() {
        _uiState.update { it.copy(clipboardState = ClipboardState()) }
    }

    fun pasteClipboardToCurrentDirectory() {
        val state = _uiState.value
        val clipboard = state.clipboardState
        if (!clipboard.isActive) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val res = fileEngine.copyOrMoveBatchWithUndo(
                sourceNodes = clipboard.sourceNodes,
                destinationDirPathOrUri = state.currentPath,
                isMove = clipboard.isMoveOperation,
                conflictPolicy = clipboard.conflictPolicy
            )
            res.fold(
                onSuccess = { batchResult ->
                    val count = batchResult.processedCount
                    val undoOp: UndoOperation? = if (clipboard.isMoveOperation && batchResult.movedPairs.isNotEmpty()) {
                        UndoOperation.ReverseMoveOperation(batchResult.movedPairs, "Moved $count item(s)")
                    } else if (!clipboard.isMoveOperation && batchResult.createdDestinationPaths.isNotEmpty()) {
                        UndoOperation.DeleteCreatedPaths(batchResult.createdDestinationPaths, "Copied $count item(s)")
                    } else null

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            clipboardState = ClipboardState(),
                            selectedFilePaths = emptySet(),
                            lastUndoOperation = undoOp
                        )
                    }
                    repository.recordLog(
                        operationType = if (clipboard.isMoveOperation) "MOVE" else "COPY",
                        targetPath = state.currentPath,
                        isError = false,
                        summary = "${if (clipboard.isMoveOperation) "Moved" else "Copied"} $count item(s)"
                    )
                    refreshCurrentDirectory()
                    runStorageAnalyzer()
                    showStatus("${if (clipboard.isMoveOperation) "Moved" else "Copied"} $count item(s)")
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false) }
                    reportDiagnosticError(if (clipboard.isMoveOperation) "Move" else "Copy", state.currentPath, err)
                }
            )
        }
    }

    // ==================== DELETE CHOICE (TRASH VS. SHRED) & UNDO ====================

    fun requestDeleteSingleItem(node: FileNode) {
        _uiState.update { it.copy(pendingDeleteTargets = listOf(node)) }
    }

    fun batchDeleteSelected() {
        val state = _uiState.value
        val selectedNodes = state.files.filter { state.selectedFilePaths.contains(it.path) }
        if (selectedNodes.isEmpty()) return
        _uiState.update { it.copy(pendingDeleteTargets = selectedNodes) }
    }

    fun dismissDeleteChoiceDialog() {
        _uiState.update { it.copy(pendingDeleteTargets = emptyList()) }
    }

    fun confirmMovePendingToRecycleBin() {
        val targets = _uiState.value.pendingDeleteTargets
        if (targets.isEmpty()) return
        _uiState.update { it.copy(pendingDeleteTargets = emptyList(), isLoading = true) }

        viewModelScope.launch {
            val res = fileEngine.moveToRecycleBin(targets)
            res.fold(
                onSuccess = { trashedItems ->
                    val undoOp = if (trashedItems.isNotEmpty()) {
                        UndoOperation.RestoreRecycleBinItems(trashedItems, "Moved ${trashedItems.size} item(s) to Trash")
                    } else null
                    // If currently viewing a deleted file, close viewer
                    val activePath = _uiState.value.activeStudioContent?.let { studioFileOf(it).path }
                    val deletedActive = activePath != null && targets.any { it.path == activePath }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            selectedFilePaths = emptySet(),
                            recycleBinItems = fileEngine.listRecycleBinItems(),
                            lastUndoOperation = undoOp,
                            activeStudioContent = if (deletedActive) null else it.activeStudioContent
                        )
                    }
                    refreshCurrentDirectory()
                    runStorageAnalyzer()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false) }
                    reportDiagnosticError("Move to Trash", targets.first().path, err)
                }
            )
        }
    }

    fun confirmPermanentlyShredPending() {
        val targets = _uiState.value.pendingDeleteTargets
        if (targets.isEmpty()) return
        _uiState.update { it.copy(pendingDeleteTargets = emptyList(), isLoading = true) }

        viewModelScope.launch {
            val res = fileEngine.permanentlyShredNodes(targets)
            res.fold(
                onSuccess = { count ->
                    val activePath = _uiState.value.activeStudioContent?.let { studioFileOf(it).path }
                    val deletedActive = activePath != null && targets.any { it.path == activePath }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            selectedFilePaths = emptySet(),
                            activeStudioContent = if (deletedActive) null else it.activeStudioContent
                        )
                    }
                    refreshCurrentDirectory()
                    runStorageAnalyzer()
                    showStatus("Shredded $count item(s)")
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false) }
                    reportDiagnosticError("Permanent Shred", targets.first().path, err)
                }
            )
        }
    }

    fun toggleRecycleBinSheet(show: Boolean) {
        _uiState.update {
            it.copy(showRecycleBinSheet = show, recycleBinItems = fileEngine.listRecycleBinItems())
        }
    }

    fun restoreRecycleBinItem(item: RecycleBinItem) {
        viewModelScope.launch {
            val res = fileEngine.restoreFromRecycleBin(item, ConflictPolicy.KEEP_BOTH)
            res.fold(
                onSuccess = { restoredNode ->
                    _uiState.update { it.copy(recycleBinItems = fileEngine.listRecycleBinItems()) }
                    refreshCurrentDirectory()
                    runStorageAnalyzer()
                    showStatus("Restored '${restoredNode.name}'")
                },
                onFailure = { err ->
                    reportDiagnosticError("Restore", item.originalPath, err)
                }
            )
        }
    }

    fun permanentlyDeleteRecycleBinItem(item: RecycleBinItem) {
        viewModelScope.launch {
            fileEngine.permanentlyShredRecycleBinItem(item.id)
            _uiState.update { it.copy(recycleBinItems = fileEngine.listRecycleBinItems()) }
            showStatus("Deleted '${item.originalName}'")
        }
    }

    fun emptyAllRecycleBin() {
        viewModelScope.launch {
            fileEngine.emptyRecycleBin().onSuccess { count ->
                _uiState.update { it.copy(recycleBinItems = emptyList()) }
                showStatus("Emptied Trash ($count item(s))")
            }
        }
    }

    fun dismissUndoBanner() {
        _uiState.update { it.copy(lastUndoOperation = null) }
    }

    fun performLastUndo() {
        val undoOp = _uiState.value.lastUndoOperation ?: return
        _uiState.update { it.copy(lastUndoOperation = null, isLoading = true) }

        viewModelScope.launch {
            try {
                when (undoOp) {
                    is UndoOperation.RestoreRecycleBinItems -> {
                        undoOp.items.forEach { fileEngine.restoreFromRecycleBin(it, ConflictPolicy.KEEP_BOTH) }
                        showStatus("Restored from Trash")
                    }
                    is UndoOperation.DeleteCreatedPaths -> {
                        undoOp.createdPaths.forEach { File(it).deleteRecursively() }
                        showStatus("Undid action")
                    }
                    is UndoOperation.ReverseMoveOperation -> {
                        for ((destPath, origPath) in undoOp.movedPairs) {
                            val destFile = File(destPath)
                            val origParent = File(origPath).parentFile
                            if (destFile.exists() && origParent != null) {
                                val node = with(fileEngine) { destFile.toFileNode() }
                                fileEngine.copyOrMoveBatch(listOf(node), origParent.absolutePath, isMove = true, conflictPolicy = ConflictPolicy.KEEP_BOTH)
                            }
                        }
                        showStatus("Moved back")
                    }
                    is UndoOperation.ReverseRenameOperation -> {
                        val currentFile = File(undoOp.currentPath)
                        if (currentFile.exists()) {
                            val node = with(fileEngine) { currentFile.toFileNode() }
                            fileEngine.renameFile(node, undoOp.previousName).getOrThrow()
                            showStatus("Restored '${undoOp.previousName}'")
                        }
                    }
                }
            } catch (e: Exception) {
                reportDiagnosticError("Undo", undoOp.summaryLabel, e)
            } finally {
                _uiState.update { it.copy(isLoading = false, recycleBinItems = fileEngine.listRecycleBinItems()) }
                refreshCurrentDirectory()
                runStorageAnalyzer()
            }
        }
    }

    fun batchCompressSelectedToZip(archiveName: String) {
        val state = _uiState.value
        val selectedNodes = state.files.filter { state.selectedFilePaths.contains(it.path) }
        if (selectedNodes.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val res = fileEngine.compressNodesToZip(selectedNodes, state.currentPath, archiveName)
            res.fold(
                onSuccess = { zipNode ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            selectedFilePaths = emptySet(),
                            lastUndoOperation = UndoOperation.DeleteCreatedPaths(listOf(zipNode.path), "Created '${zipNode.name}'")
                        )
                    }
                    refreshCurrentDirectory()
                    showStatus("Created '${zipNode.name}'")
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false) }
                    reportDiagnosticError("Compress ZIP", state.currentPath, err)
                }
            )
        }
    }

    fun batchEncryptSelectedToVault() {
        val state = _uiState.value
        val selectedNodes = state.files.filter { state.selectedFilePaths.contains(it.path) && !it.isDirectory }
        if (selectedNodes.isEmpty()) return
        if (!state.isVaultUnlocked || state.unlockedPinInMemory.isBlank()) {
            _uiState.update { it.copy(activeTab = LumaTab.VAULT, statusBannerMessage = "Unlock Vault first") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            var encryptedCount = 0
            selectedNodes.forEach { node ->
                cryptoEngine.encryptFileToVault(node, state.unlockedPinInMemory, deleteOriginalAfterEncrypt = true)
                    .onSuccess { entity ->
                        repository.addVaultItem(entity)
                        encryptedCount++
                    }
            }
            _uiState.update { it.copy(isLoading = false, selectedFilePaths = emptySet()) }
            refreshCurrentDirectory()
            showStatus("Locked $encryptedCount file(s) in Vault")
        }
    }

    // ==================== DIRECT VIEW-FIRST FULL-SCREEN STUDIO & MEDIA ====================

    fun onFileNodeTapped(node: FileNode) {
        val state = _uiState.value
        if (state.selectedFilePaths.isNotEmpty()) {
            toggleFileSelection(node)
            return
        }
        if (node.isDirectory) {
            navigateToDirectory(node.path)
        } else {
            confirmOpenInLumaStudio(node, forceHexInspector = false)
        }
    }

    fun confirmOpenInLumaStudio(
        node: FileNode,
        forceHexInspector: Boolean = false,
        charsetName: String = "UTF-8",
        pdfPageIndex: Int = 0,
        hexOffset: Long = 0L,
        vaultItemForPreview: VaultItemEntity? = null,
        vaultTempPath: String? = null
    ) {
        _uiState.update {
            it.copy(
                isLoading = true,
                activeZipEntryPreview = null,
                aiEnhanceState = AiEnhanceUiState.Idle
            )
        }
        viewModelScope.launch {
            val result = fileEngine.openFileInStudio(
                node = node,
                forceHexInspector = forceHexInspector,
                charsetName = charsetName,
                pdfPageIndex = pdfPageIndex,
                hexWindowOffset = hexOffset
            )
            result.fold(
                onSuccess = { content ->
                    val siblings = computeSiblingMediaFiles(node, content, vaultItemForPreview != null)
                    _uiState.update {
                        it.copy(
                            activeStudioContent = content,
                            siblingMediaFiles = siblings,
                            activeVaultPreviewItem = vaultItemForPreview,
                            activeVaultPreviewTempPath = vaultTempPath,
                            isLoading = false
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false) }
                    reportDiagnosticError("Open File", node.path, err)
                }
            )
        }
    }

    private fun computeSiblingMediaFiles(
        node: FileNode,
        content: StudioContent,
        isVaultPreview: Boolean
    ): List<FileNode> {
        if (isVaultPreview || node.isSafDocument) return listOf(node)
        val targetCat = when {
            content is StudioContent.ImageStudio -> FileFormatCategory.IMAGE
            content is StudioContent.MediaStudio && !content.isVideo -> FileFormatCategory.AUDIO
            else -> return emptyList()
        }
        val parent = File(node.path).parentFile ?: return listOf(node)
        return parent.listFiles()
            ?.filter { it.isFile }
            ?.map { with(fileEngine) { it.toFileNode() } }
            ?.filter { it.category == targetCat }
            ?.sortedBy { it.name.lowercase() }
            ?: listOf(node)
    }

    fun closeStudioEditor() {
        val hadVaultPreview = _uiState.value.activeVaultPreviewTempPath != null
        _uiState.update {
            it.copy(
                activeStudioContent = null,
                siblingMediaFiles = emptyList(),
                activeZipEntryPreview = null,
                activeVaultPreviewItem = null,
                activeVaultPreviewTempPath = null,
                aiEnhanceState = AiEnhanceUiState.Idle
            )
        }
        if (hadVaultPreview) {
            viewModelScope.launch { cryptoEngine.shredAllVolatilePreviews() }
        }
    }

    fun shredVolatileVaultPreviews() {
        val hadVaultPreview = _uiState.value.activeVaultPreviewItem != null || _uiState.value.activeVaultPreviewTempPath != null
        if (hadVaultPreview) {
            _uiState.update {
                it.copy(
                    activeStudioContent = null,
                    activeVaultPreviewItem = null,
                    activeVaultPreviewTempPath = null
                )
            }
        }
        viewModelScope.launch { cryptoEngine.shredAllVolatilePreviews() }
    }

    // ==================== AI PHOTO ENHANCE, EDIT SAVE & VIDEO SCREENSHOT ====================

    fun triggerAiAutoEnhance(
        sourceBitmap: Bitmap,
        onApplied: (PhotoAdjustments, String, String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(aiEnhanceState = AiEnhanceUiState.Loading("Analyzing photo…"))
            }
            val res = photoEngine.analyzeAndAutoEnhance(sourceBitmap)
            res.fold(
                onSuccess = { grade ->
                    _uiState.update {
                        it.copy(aiEnhanceState = AiEnhanceUiState.Success(grade.summary))
                    }
                    onApplied(grade.adjustments, grade.recommendedFilterId, grade.summary)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            aiEnhanceState = AiEnhanceUiState.Error(
                                err.message ?: "AI enhance unavailable"
                            )
                        )
                    }
                }
            )
        }
    }

    fun triggerAiRemaster(
        sourceBitmap: Bitmap,
        stylePrompt: String,
        onResultBitmap: (Bitmap) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(aiEnhanceState = AiEnhanceUiState.Loading("Remastering with AI…"))
            }
            val res = photoEngine.generateAiRemasteredPhoto(sourceBitmap, stylePrompt)
            res.fold(
                onSuccess = { remastered ->
                    _uiState.update {
                        it.copy(aiEnhanceState = AiEnhanceUiState.Success("AI remaster ready", remastered))
                    }
                    onResultBitmap(remastered)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            aiEnhanceState = AiEnhanceUiState.Error(
                                err.message ?: "AI remaster failed"
                            )
                        )
                    }
                }
            )
        }
    }

    fun saveEditedPhoto(node: FileNode, renderedBitmap: Bitmap, overwriteOriginal: Boolean) {
        viewModelScope.launch {
            val state = _uiState.value
            val vaultItem = state.activeVaultPreviewItem
            val tempVaultPath = state.activeVaultPreviewTempPath

            if (vaultItem != null && tempVaultPath != null && state.isVaultUnlocked) {
                val tempFile = File(tempVaultPath)
                photoEngine.saveBitmapAtomically(renderedBitmap, tempFile).fold(
                    onSuccess = { savedTemp ->
                        cryptoEngine.reEncryptEditedPreviewToVault(
                            existingItem = vaultItem,
                            modifiedPlainFile = savedTemp,
                            activeSecret = state.unlockedPinInMemory
                        ).fold(
                            onSuccess = { updatedEntity ->
                                repository.addVaultItem(updatedEntity)
                                confirmOpenInLumaStudio(
                                    node = with(fileEngine) { savedTemp.toFileNode() }.copy(name = vaultItem.originalName),
                                    vaultItemForPreview = updatedEntity,
                                    vaultTempPath = savedTemp.absolutePath
                                )
                                showStatus("Saved edits back to Vault")
                            },
                            onFailure = { err -> reportDiagnosticError("Save Vault Photo", vaultItem.originalName, err) }
                        )
                    },
                    onFailure = { err -> reportDiagnosticError("Save Photo", node.path, err) }
                )
                return@launch
            }

            val srcFile = File(node.path)
            val parentDir = srcFile.parentFile ?: fileEngine.lumaWorkspaceDir
            val targetFile = if (overwriteOriginal && !node.isSafDocument) {
                srcFile
            } else {
                val ext = srcFile.extension.ifBlank { "jpg" }
                val base = srcFile.nameWithoutExtension
                File(parentDir, "${base}_edited_${System.currentTimeMillis() % 10000}.$ext")
            }

            photoEngine.saveBitmapAtomically(renderedBitmap, targetFile).fold(
                onSuccess = { savedFile ->
                    val savedNode = with(fileEngine) { savedFile.toFileNode() }
                    confirmOpenInLumaStudio(savedNode)
                    refreshCurrentDirectory()
                    showStatus("Saved '${savedFile.name}'")
                },
                onFailure = { err ->
                    reportDiagnosticError("Save Photo", targetFile.absolutePath, err)
                }
            )
        }
    }

    fun captureVideoFrame(videoNode: FileNode, frameBitmap: Bitmap) {
        viewModelScope.launch {
            val parent = File(videoNode.path).parentFile?.takeIf { it.canWrite() && _uiState.value.activeVaultPreviewItem == null }
                ?: File(fileEngine.lumaWorkspaceDir, "Screenshots").apply { mkdirs() }
            val out = File(parent, "${videoNode.name.substringBeforeLast('.')}_frame_${System.currentTimeMillis() % 100000}.jpg")
            photoEngine.saveBitmapAtomically(frameBitmap, out).onSuccess {
                refreshCurrentDirectory()
                showStatus("Saved frame '${out.name}'")
            }
        }
    }

    suspend fun renderPdfPageBitmap(node: FileNode, pageIndex: Int): Bitmap? {
        return fileEngine.renderPdfPageBitmap(node, pageIndex)
    }

    fun previewZipEntry(node: FileNode, entryName: String) {
        viewModelScope.launch {
            fileEngine.readZipEntryPreview(node, entryName).fold(
                onSuccess = { preview ->
                    _uiState.update { it.copy(activeZipEntryPreview = preview) }
                },
                onFailure = { err ->
                    reportDiagnosticError("Preview Archive Entry", entryName, err)
                }
            )
        }
    }

    fun dismissZipEntryPreview() {
        _uiState.update { it.copy(activeZipEntryPreview = null) }
    }

    fun extractSingleZipEntry(node: FileNode, entryName: String) {
        viewModelScope.launch {
            val parentDir = File(node.path).parentFile ?: fileEngine.lumaWorkspaceDir
            fileEngine.extractSingleZipEntry(node, entryName, parentDir).fold(
                onSuccess = { extracted ->
                    refreshCurrentDirectory()
                    showStatus("Extracted '${extracted.name}'")
                },
                onFailure = { err ->
                    reportDiagnosticError("Extract File", entryName, err)
                }
            )
        }
    }

    fun saveTextEditorChanges(node: FileNode, newText: String, encoding: String) {
        viewModelScope.launch {
            val res = fileEngine.saveTextOrCodeFileLosslessly(node, newText, encoding)
            res.fold(
                onSuccess = { updatedNode ->
                    reEncryptActiveVaultPreviewIfNeeded(updatedNode)
                    confirmOpenInLumaStudio(
                        node = updatedNode,
                        charsetName = encoding,
                        vaultItemForPreview = _uiState.value.activeVaultPreviewItem,
                        vaultTempPath = _uiState.value.activeVaultPreviewTempPath
                    )
                    refreshCurrentDirectory()
                    showStatus("Saved '${updatedNode.name}'")
                },
                onFailure = { err -> reportDiagnosticError("Save File", node.path, err) }
            )
        }
    }

    fun formatStructuredTextInStudio(node: FileNode, currentText: String, encoding: String) {
        val (formatted, validationMsg) = fileEngine.formatAndValidateStructuredText(currentText, node.extension)
        val currentContent = _uiState.value.activeStudioContent
        if (currentContent is StudioContent.TextCodeEditor) {
            _uiState.update {
                it.copy(
                    activeStudioContent = currentContent.copy(
                        textContent = formatted,
                        lineCount = formatted.lines().size,
                        validationMessage = validationMsg
                    )
                )
            }
            showStatus(validationMsg)
        }
    }

    fun saveDocxEditorChanges(node: FileNode, updatedParagraphs: List<String>) {
        viewModelScope.launch {
            fileEngine.saveDocxParagraphsLosslessly(node, updatedParagraphs).fold(
                onSuccess = { updatedNode ->
                    reEncryptActiveVaultPreviewIfNeeded(updatedNode)
                    confirmOpenInLumaStudio(
                        node = updatedNode,
                        vaultItemForPreview = _uiState.value.activeVaultPreviewItem,
                        vaultTempPath = _uiState.value.activeVaultPreviewTempPath
                    )
                    refreshCurrentDirectory()
                    showStatus("Saved '${updatedNode.name}'")
                },
                onFailure = { err -> reportDiagnosticError("Save Document", node.path, err) }
            )
        }
    }

    fun saveCsvGridChanges(node: FileNode, delimiter: Char, headers: List<String>, rows: List<List<String>>) {
        viewModelScope.launch {
            fileEngine.saveCsvGridLosslessly(node, delimiter, headers, rows).fold(
                onSuccess = { updatedNode ->
                    reEncryptActiveVaultPreviewIfNeeded(updatedNode)
                    confirmOpenInLumaStudio(
                        node = updatedNode,
                        vaultItemForPreview = _uiState.value.activeVaultPreviewItem,
                        vaultTempPath = _uiState.value.activeVaultPreviewTempPath
                    )
                    refreshCurrentDirectory()
                    showStatus("Saved '${updatedNode.name}'")
                },
                onFailure = { err -> reportDiagnosticError("Save CSV", node.path, err) }
            )
        }
    }

    private suspend fun reEncryptActiveVaultPreviewIfNeeded(updatedNode: FileNode) {
        val state = _uiState.value
        val vaultItem = state.activeVaultPreviewItem ?: return
        val tempPath = state.activeVaultPreviewTempPath ?: return
        if (!state.isVaultUnlocked) return
        cryptoEngine.reEncryptEditedPreviewToVault(vaultItem, File(tempPath), state.unlockedPinInMemory)
            .onSuccess { updatedEntity ->
                repository.addVaultItem(updatedEntity)
                _uiState.update { it.copy(activeVaultPreviewItem = updatedEntity) }
            }
    }

    fun selectSqliteTableInStudio(node: FileNode, tableName: String) {
        viewModelScope.launch {
            fileEngine.loadSqliteStudioForTable(node, selectedTable = tableName).fold(
                onSuccess = { studio -> _uiState.update { it.copy(activeStudioContent = studio) } },
                onFailure = { err -> reportDiagnosticError("Query Table", node.path, err) }
            )
        }
    }

    fun executeSqliteQueryInStudio(node: FileNode, activeTable: String, sqlQuery: String) {
        viewModelScope.launch {
            fileEngine.executeSqliteQuery(node, activeTable, sqlQuery).fold(
                onSuccess = { studio -> _uiState.update { it.copy(activeStudioContent = studio) } },
                onFailure = { err -> reportDiagnosticError("SQL Query", node.path, err) }
            )
        }
    }

    fun savePdfAnnotation(node: FileNode, notes: String) {
        viewModelScope.launch {
            fileEngine.savePdfAnnotationNotes(node, notes).fold(
                onSuccess = {
                    refreshCurrentDirectory()
                    showStatus("Notes saved")
                },
                onFailure = { err -> reportDiagnosticError("Save Notes", node.path, err) }
            )
        }
    }

    fun extractArchiveInStudio(node: FileNode) {
        viewModelScope.launch {
            val parentDir = File(node.path).parentFile ?: fileEngine.lumaWorkspaceDir
            fileEngine.extractZipArchive(node, parentDir).fold(
                onSuccess = { count ->
                    refreshCurrentDirectory()
                    showStatus("Extracted $count files")
                },
                onFailure = { err -> reportDiagnosticError("Extract ZIP", node.path, err) }
            )
        }
    }

    fun addEntryToZipInStudio(node: FileNode, entryName: String, entryContent: String) {
        viewModelScope.launch {
            fileEngine.addTextEntryToZipArchive(node, entryName, entryContent).fold(
                onSuccess = { updatedNode ->
                    confirmOpenInLumaStudio(updatedNode)
                    refreshCurrentDirectory()
                    showStatus("Added '$entryName'")
                },
                onFailure = { err -> reportDiagnosticError("Add Archive Entry", node.path, err) }
            )
        }
    }

    fun patchHexByteInStudio(node: FileNode, byteOffset: Long, newHexByte: String, currentWindowOffset: Long) {
        viewModelScope.launch {
            fileEngine.patchSingleByteInFile(node, byteOffset, newHexByte).fold(
                onSuccess = {
                    confirmOpenInLumaStudio(node, forceHexInspector = true, hexOffset = currentWindowOffset)
                    refreshCurrentDirectory()
                    showStatus("Patched byte")
                },
                onFailure = { err -> reportDiagnosticError("Hex Patch", node.path, err) }
            )
        }
    }

    // ==================== FILE CRUD & ANALYZER ====================

    fun createNewItem(name: String, isDirectory: Boolean, initialContent: String = "") {
        viewModelScope.launch {
            val currentDir = _uiState.value.currentPath
            fileEngine.createNewFileOrFolder(currentDir, name, isDirectory, initialContent).fold(
                onSuccess = { created ->
                    _uiState.update {
                        it.copy(
                            lastUndoOperation = UndoOperation.DeleteCreatedPaths(listOf(created.path), "Created '${created.name}'")
                        )
                    }
                    refreshCurrentDirectory()
                    showStatus("Created '${created.name}'")
                },
                onFailure = { err -> reportDiagnosticError("Create", "$currentDir/$name", err) }
            )
        }
    }

    fun renameItem(node: FileNode, newName: String) {
        val oldName = node.name
        viewModelScope.launch {
            fileEngine.renameFile(node, newName).fold(
                onSuccess = { renamed ->
                    val currentlyViewing = _uiState.value.activeStudioContent?.let { studioFileOf(it).path == node.path } == true
                    _uiState.update {
                        it.copy(
                            lastUndoOperation = UndoOperation.ReverseRenameOperation(renamed.path, oldName, "Renamed to '${renamed.name}'")
                        )
                    }
                    if (currentlyViewing) {
                        confirmOpenInLumaStudio(renamed)
                    }
                    refreshCurrentDirectory()
                    showStatus("Renamed to '${renamed.name}'")
                },
                onFailure = { err -> reportDiagnosticError("Rename", node.path, err) }
            )
        }
    }

    fun deleteItem(node: FileNode) {
        requestDeleteSingleItem(node)
    }

    fun runBatchRegexRename(pattern: String, replacement: String, prefix: String) {
        viewModelScope.launch {
            val dir = _uiState.value.currentPath
            fileEngine.batchRegexRename(dir, pattern, replacement, prefix).fold(
                onSuccess = { count ->
                    refreshCurrentDirectory()
                    showStatus("Renamed $count file(s)")
                },
                onFailure = { err -> reportDiagnosticError("Batch Rename", dir, err) }
            )
        }
    }

    fun inspectFileChecksums(node: FileNode) {
        _uiState.update {
            it.copy(inspectedFileNode = node, inspectedFileMd5 = "", inspectedFileSha256 = "", isComputingChecksums = true)
        }
        viewModelScope.launch {
            try {
                val (md5, sha256) = if (node.isSafDocument) {
                    val bytes = getApplication<Application>().contentResolver
                        .openInputStream(Uri.parse(node.safUriString ?: node.path))?.use { it.readBytes() } ?: ByteArray(0)
                    val tempFile = File(getApplication<Application>().cacheDir, "hash_inspect.tmp")
                    tempFile.writeBytes(bytes)
                    val pair = cryptoEngine.computeFileChecksums(tempFile)
                    tempFile.delete()
                    pair
                } else {
                    cryptoEngine.computeFileChecksums(File(node.path))
                }
                _uiState.update { it.copy(inspectedFileMd5 = md5, inspectedFileSha256 = sha256, isComputingChecksums = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isComputingChecksums = false, inspectedFileNode = null) }
                reportDiagnosticError("File Details", node.path, e)
            }
        }
    }

    fun dismissFileInspector() {
        _uiState.update { it.copy(inspectedFileNode = null) }
    }

    fun bookmarkCurrentDirectory() {
        viewModelScope.launch {
            val path = _uiState.value.currentPath
            val name = path.substringAfterLast('/').ifBlank { "Storage" }
            repository.addBookmark(BookmarkEntity(title = name, pathOrUri = path, isSafTree = path.startsWith("content://")))
            showStatus("Bookmarked '$name'")
        }
    }

    fun removeBookmark(id: Long) {
        viewModelScope.launch { repository.removeBookmark(id) }
    }

    fun runStorageAnalyzer() {
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzingStorage = true) }
            val targetRoot = _uiState.value.currentPath.takeIf { !it.startsWith("content://") }
                ?: fileEngine.lumaWorkspaceDir.absolutePath
            fileEngine.runStorageAnalyzer(targetRoot).fold(
                onSuccess = { report -> _uiState.update { it.copy(analyzerReport = report, isAnalyzingStorage = false) } },
                onFailure = { err ->
                    _uiState.update { it.copy(isAnalyzingStorage = false) }
                    if (_uiState.value.activeTab == LumaTab.ANALYZER) {
                        reportDiagnosticError("Storage Scan", targetRoot, err)
                    }
                }
            )
        }
    }

    // ==================== AES-256 SECURE VAULT + SEAMLESS IN-VAULT VIEWING ====================

    fun setupVaultCredential(secret: String, authMode: VaultAuthMode, recoveryQuestion: String, recoveryAnswer: String) {
        cryptoEngine.setupCredential(secret, authMode, recoveryQuestion, recoveryAnswer).fold(
            onSuccess = {
                val pinLen = when (authMode) {
                    VaultAuthMode.PIN_4 -> 4
                    VaultAuthMode.PIN_6 -> 6
                    VaultAuthMode.ALPHANUMERIC_PASSWORD -> secret.length
                }
                _uiState.update {
                    it.copy(
                        isPinConfigured = true,
                        configuredPinLength = pinLen,
                        vaultAuthMode = authMode,
                        isVaultUnlocked = true,
                        unlockedPinInMemory = secret,
                        recoveryQuestion = cryptoEngine.getRecoveryQuestion(),
                        vaultAuthError = null,
                        statusBannerMessage = "Vault ready"
                    )
                }
            },
            onFailure = { err -> _uiState.update { it.copy(vaultAuthError = err.message) } }
        )
    }

    fun unlockVaultWithPin(secret: String) {
        if (cryptoEngine.verifyPin(secret)) {
            _uiState.update {
                it.copy(isVaultUnlocked = true, unlockedPinInMemory = secret, vaultAuthError = null)
            }
        } else {
            _uiState.update { it.copy(vaultAuthError = "Incorrect ${it.vaultAuthMode.label}") }
        }
    }

    fun lockVaultSession() {
        shredVolatileVaultPreviews()
        _uiState.update {
            it.copy(isVaultUnlocked = false, unlockedPinInMemory = "", vaultAuthError = null)
        }
    }

    fun resetPinViaRecovery(answer: String, newSecret: String, newMode: VaultAuthMode) {
        if (cryptoEngine.resetPinWithRecovery(answer, newSecret, newMode)) {
            val pinLen = when (newMode) {
                VaultAuthMode.PIN_4 -> 4
                VaultAuthMode.PIN_6 -> 6
                VaultAuthMode.ALPHANUMERIC_PASSWORD -> newSecret.length
            }
            _uiState.update {
                it.copy(
                    configuredPinLength = pinLen,
                    vaultAuthMode = newMode,
                    isVaultUnlocked = true,
                    unlockedPinInMemory = newSecret,
                    vaultAuthError = null,
                    statusBannerMessage = "Vault unlocked"
                )
            }
        } else {
            _uiState.update { it.copy(vaultAuthError = "Incorrect recovery answer") }
        }
    }

    /**
     * Seamless zero-trace in-vault viewer: once verified with PIN/Password, tapping any vault item
     * decrypts into an isolated volatile preview directory and opens immediately in the V2 full-screen viewer.
     */
    fun openVaultItemForSeamlessViewing(item: VaultItemEntity) {
        val state = _uiState.value
        if (!state.isVaultUnlocked || state.unlockedPinInMemory.isBlank()) {
            showStatus("Unlock Vault first")
            return
        }
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            cryptoEngine.decryptVaultItemToVolatilePreview(item, state.unlockedPinInMemory).fold(
                onSuccess = { previewFile ->
                    val node = with(fileEngine) { previewFile.toFileNode() }.copy(name = item.originalName)
                    confirmOpenInLumaStudio(
                        node = node,
                        vaultItemForPreview = item,
                        vaultTempPath = previewFile.absolutePath
                    )
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false) }
                    reportDiagnosticError("Open Vault File", item.originalName, err)
                }
            )
        }
    }

    fun moveFileToSecureVault(node: FileNode) {
        val state = _uiState.value
        if (!state.isVaultUnlocked || state.unlockedPinInMemory.isBlank()) {
            _uiState.update { it.copy(activeTab = LumaTab.VAULT, statusBannerMessage = "Unlock Vault first") }
            return
        }
        viewModelScope.launch {
            cryptoEngine.encryptFileToVault(node, state.unlockedPinInMemory, deleteOriginalAfterEncrypt = true).fold(
                onSuccess = { entity ->
                    repository.addVaultItem(entity)
                    val activePath = _uiState.value.activeStudioContent?.let { studioFileOf(it).path }
                    if (activePath == node.path) closeStudioEditor()
                    refreshCurrentDirectory()
                    showStatus("Locked '${node.name}' in Vault")
                },
                onFailure = { err -> reportDiagnosticError("Lock to Vault", node.path, err) }
            )
        }
    }

    fun restoreFileFromSecureVault(item: VaultItemEntity) {
        val state = _uiState.value
        if (!state.isVaultUnlocked || state.unlockedPinInMemory.isBlank()) return
        viewModelScope.launch {
            val originalParent = File(item.originalPath).parentFile
            val restoreDir = if (originalParent != null && originalParent.exists() && originalParent.canWrite()) {
                originalParent
            } else {
                File(fileEngine.lumaWorkspaceDir, "Restored")
            }
            cryptoEngine.decryptVaultItemToDirectory(item, state.unlockedPinInMemory, restoreDir, removeEncryptedCopy = true).fold(
                onSuccess = { restoredFile ->
                    repository.removeVaultItem(item.id)
                    refreshCurrentDirectory()
                    showStatus("Restored '${restoredFile.name}'")
                },
                onFailure = { err -> reportDiagnosticError("Restore Vault File", item.originalName, err) }
            )
        }
    }

    fun deleteVaultItemPermanently(item: VaultItemEntity) {
        viewModelScope.launch {
            runCatching { cryptoEngine.shredAndDeleteFile(File(item.encryptedFilePath)) }
            repository.removeVaultItem(item.id)
            showStatus("Deleted '${item.originalName}'")
        }
    }

    // ==================== DOWNLOADER & WEB SNIFFER BROWSER ====================

    fun onWebMediaSniffed(media: SniffedWebMedia) {
        _uiState.update { state ->
            if (state.sniffedWebMedia.any { it.url == media.url }) state
            else state.copy(sniffedWebMedia = (listOf(media) + state.sniffedWebMedia).take(40))
        }
    }

    fun clearSniffedWebMedia() {
        _uiState.update { it.copy(sniffedWebMedia = emptyList()) }
    }

    fun enqueueSniffedMediaDownload(media: SniffedWebMedia) {
        viewModelScope.launch {
            val downloadsDir = resolveDownloadsDirectory()
            val cleanName = media.fileName.ifBlank { "download_${System.currentTimeMillis()}.mp4" }
            val destFile = File(downloadsDir, cleanName)
            downloadEngine.saveCheckpointSidecar(
                destFile = destFile,
                checkpoint = PersistedDownloadCheckpoint(
                    url = media.url,
                    threadCount = if (media.isHlsOrDash) 1 else 4,
                    customUserAgent = media.userAgent,
                    refererHeader = media.referer,
                    cookieHeader = media.cookies,
                    isHlsPlaylist = media.isHlsOrDash,
                    resolutionLabel = if (media.isHlsOrDash) "HLS" else "Original"
                )
            )
            val task = DownloadTaskEntity(
                url = media.url,
                fileName = cleanName,
                destinationPath = destFile.absolutePath,
                totalBytes = media.contentLength.coerceAtLeast(0L),
                downloadedBytes = 0L,
                status = "QUEUED",
                threadCount = if (media.isHlsOrDash) 1 else 4
            )
            val taskId = repository.addDownloadTask(task)
            downloadEngine.startOrResumeDownload(taskId) { diag ->
                _uiState.update { it.copy(activeDiagnosticError = diag) }
            }
            showStatus("Downloading '$cleanName'")
        }
    }

    fun inspectMediaUrl(url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return
        _uiState.update { it.copy(isResolvingMediaUrl = true) }
        viewModelScope.launch {
            downloadEngine.resolveMediaStreamManifest(cleanUrl).fold(
                onSuccess = { manifest ->
                    _uiState.update {
                        it.copy(
                            isResolvingMediaUrl = false,
                            resolvedMediaManifest = manifest,
                            selectedStreamVariantId = manifest.recommendedVariantId
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isResolvingMediaUrl = false) }
                    reportDiagnosticError("Detect Media", cleanUrl, err)
                }
            )
        }
    }

    fun selectMediaStreamVariant(variantId: String) {
        _uiState.update { it.copy(selectedStreamVariantId = variantId) }
    }

    fun enqueueDownload(
        url: String,
        customFileName: String,
        threads: Int,
        customUserAgent: String = "",
        expectedSha256: String = "",
        selectedVariantId: String = ""
    ) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isResolvingMediaUrl = true) }
            val existingManifest = _uiState.value.resolvedMediaManifest?.takeIf { it.sourceUrl == cleanUrl }
            val manifest = existingManifest ?: downloadEngine.resolveMediaStreamManifest(cleanUrl).getOrNull()

            if (manifest != null && manifest.requiresBrowserSniffer && manifest.variants.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isResolvingMediaUrl = false,
                        resolvedMediaManifest = manifest,
                        statusBannerMessage = "Open this link in the Browser tab to capture the stream"
                    )
                }
                return@launch
            }

            val chosenVariant = if (manifest != null && manifest.variants.isNotEmpty()) {
                val targetId = selectedVariantId.ifBlank { _uiState.value.selectedStreamVariantId }
                manifest.variants.firstOrNull { it.variantId == targetId }
                    ?: FastDownloadEngine.selectBestStreamVariant(manifest.variants)
            } else null

            val effectiveStreamUrl = chosenVariant?.videoStreamUrl ?: cleanUrl
            val effectiveAudioUrl = chosenVariant?.audioStreamUrl ?: ""
            val isHls = chosenVariant?.isHlsPlaylist == true || effectiveStreamUrl.contains(".m3u8", ignoreCase = true)

            val autoFileName = when {
                customFileName.isNotBlank() -> {
                    val trimmed = customFileName.trim()
                    val ext = chosenVariant?.containerExtension ?: ""
                    if (ext.isNotBlank() && !trimmed.contains('.')) "$trimmed.$ext" else trimmed
                }
                manifest != null && chosenVariant != null -> {
                    val base = manifest.suggestedFileName.substringBeforeLast('.', manifest.suggestedFileName)
                    "$base.${chosenVariant.containerExtension}"
                }
                else -> {
                    cleanUrl.substringAfterLast('/').substringBefore('?').ifBlank {
                        "download_${System.currentTimeMillis()}.bin"
                    }
                }
            }

            val downloadsDir = resolveDownloadsDirectory()
            val destFile = File(downloadsDir, autoFileName)
            val safeThreads = if (isHls) 1 else threads.coerceIn(1, 8)

            downloadEngine.saveCheckpointSidecar(
                destFile = destFile,
                checkpoint = PersistedDownloadCheckpoint(
                    url = effectiveStreamUrl,
                    threadCount = safeThreads,
                    audioStreamUrl = effectiveAudioUrl,
                    creatorAttribution = manifest?.creatorAttribution.orEmpty(),
                    expectedSha256 = expectedSha256.trim(),
                    customUserAgent = chosenVariant?.userAgentHeader?.ifBlank { customUserAgent.trim() } ?: customUserAgent.trim(),
                    refererHeader = chosenVariant?.refererHeader.orEmpty(),
                    cookieHeader = chosenVariant?.cookieHeader.orEmpty(),
                    isHlsPlaylist = isHls,
                    resolutionLabel = chosenVariant?.resolutionLabel ?: "Original"
                )
            )

            val task = DownloadTaskEntity(
                url = effectiveStreamUrl,
                fileName = autoFileName,
                destinationPath = destFile.absolutePath,
                totalBytes = chosenVariant?.estimatedSizeBytes?.coerceAtLeast(0L) ?: 0L,
                downloadedBytes = 0L,
                status = "QUEUED",
                threadCount = safeThreads,
                sha256Checksum = expectedSha256.trim()
            )
            val taskId = repository.addDownloadTask(task)
            _uiState.update {
                it.copy(isResolvingMediaUrl = false, resolvedMediaManifest = null, selectedStreamVariantId = "")
            }
            downloadEngine.startOrResumeDownload(taskId) { diag ->
                _uiState.update { it.copy(activeDiagnosticError = diag) }
            }
            showStatus("Downloading '$autoFileName'")
        }
    }

    private fun resolveDownloadsDirectory(): File {
        return runCatching {
            val pubDl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (pubDl != null && (pubDl.exists() || pubDl.mkdirs()) && pubDl.canWrite()) pubDl else null
        }.getOrNull() ?: File(fileEngine.lumaWorkspaceDir, "Downloads").apply { mkdirs() }
    }

    fun pauseDownloadTask(taskId: Long) = downloadEngine.pauseDownload(taskId)

    fun resumeDownloadTask(taskId: Long) {
        downloadEngine.startOrResumeDownload(taskId) { diag ->
            _uiState.update { it.copy(activeDiagnosticError = diag) }
        }
    }

    fun recoverPersistentDownloads() {
        downloadEngine.recoverPersistentQueueOnLaunch { diag ->
            _uiState.update { it.copy(activeDiagnosticError = diag) }
        }
        showStatus("Checked pending downloads")
    }

    fun deleteDownloadTask(task: DownloadTaskEntity, deleteFileFromDisk: Boolean) {
        downloadEngine.pauseDownload(task.id)
        viewModelScope.launch {
            if (deleteFileFromDisk) runCatching { File(task.destinationPath).delete() }
            repository.removeDownloadTask(task.id)
            refreshCurrentDirectory()
        }
    }

    fun openDownloadedFile(task: DownloadTaskEntity) {
        val file = File(task.destinationPath)
        if (!file.exists()) {
            reportDiagnosticError("Open File", task.destinationPath, IllegalStateException("File no longer exists on disk."))
            return
        }
        val node = with(fileEngine) { file.toFileNode() }
        confirmOpenInLumaStudio(node)
    }

    // ==================== DIAGNOSTICS & HELPERS ====================

    private fun studioFileOf(content: StudioContent): FileNode = when (content) {
        is StudioContent.TextCodeEditor -> content.file
        is StudioContent.DocxEditor -> content.file
        is StudioContent.CsvGridStudio -> content.file
        is StudioContent.SqliteStudio -> content.file
        is StudioContent.ApkInspectorStudio -> content.file
        is StudioContent.PdfViewer -> content.file
        is StudioContent.ZipArchiveManager -> content.file
        is StudioContent.MediaStudio -> content.file
        is StudioContent.ImageStudio -> content.file
        is StudioContent.HexBinaryStudio -> content.file
    }

    fun showStatus(message: String) {
        _uiState.update { it.copy(statusBannerMessage = message) }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    fun reportDiagnosticError(operationTitle: String, targetPath: String, throwable: Throwable) {
        val diag = fileEngine.buildDiagnosticError(operationTitle, targetPath, throwable)
        _uiState.update { it.copy(activeDiagnosticError = diag) }
        viewModelScope.launch {
            repository.recordLog(
                operationType = operationTitle,
                targetPath = targetPath,
                isError = true,
                summary = diag.exactReason,
                exactTechnicalReason = "${diag.exceptionClass}\n${diag.exactReason}"
            )
        }
    }

    fun showCustomDiagnosticError(error: DiagnosticError) {
        _uiState.update { it.copy(activeDiagnosticError = error) }
    }

    fun dismissDiagnosticError() {
        _uiState.update { it.copy(activeDiagnosticError = null) }
    }

    fun clearAllOperationLogs() {
        viewModelScope.launch {
            repository.clearAllLogs()
            showStatus("Cleared logs")
        }
    }
}
