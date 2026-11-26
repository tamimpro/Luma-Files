package com.example.ui

import android.app.Application
import android.content.Intent
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
import com.example.domain.engine.DownloadPreset
import com.example.domain.engine.FastDownloadEngine
import com.example.domain.engine.FileSystemAndFormatEngine
import com.example.domain.model.ClipboardState
import com.example.domain.model.ConflictPolicy
import com.example.domain.model.DiagnosticError
import com.example.domain.model.FileFormatCategory
import com.example.domain.model.FileNode
import com.example.domain.model.LumaTab
import com.example.domain.model.SortOption
import com.example.domain.model.StorageAnalyzerReport
import com.example.domain.model.StorageVolumeRoot
import com.example.domain.model.StudioContent
import com.example.domain.model.VaultAuthMode
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
    val isDarkThemeOverride: Boolean? = null, // null = follow system, true = dark, false = light
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
    // Open-With Permission & Routing Prompt
    val pendingOpenWithFile: FileNode? = null,
    val alwaysAskBeforeOpen: Boolean = true,
    // Active Built-in Studio Content
    val activeStudioContent: StudioContent? = null,
    val studioHasUnsavedChanges: Boolean = false,
    // Secure Vault Auth State
    val isPinConfigured: Boolean = false,
    val configuredPinLength: Int = 4,
    val vaultAuthMode: VaultAuthMode = VaultAuthMode.PIN_4,
    val isVaultUnlocked: Boolean = false,
    val unlockedPinInMemory: String = "",
    val recoveryQuestion: String = "",
    val vaultAuthError: String? = null,
    // Storage Analyzer State
    val analyzerReport: StorageAnalyzerReport? = null,
    val isAnalyzingStorage: Boolean = false,
    // Checksum / File Inspector Modal
    val inspectedFileNode: FileNode? = null,
    val inspectedFileMd5: String = "",
    val inspectedFileSha256: String = "",
    val isComputingChecksums: Boolean = false,
    // User Feedback & Exact Error Diagnostics
    val statusBannerMessage: String? = null,
    val activeDiagnosticError: DiagnosticError? = null
)

class LumaFilesViewModel(application: Application) : AndroidViewModel(application) {

    private val database = LumaDatabase.getInstance(application)
    private val repository = LumaRepository(database.lumaDao())
    val cryptoEngine = CryptoVaultEngine(application)
    val fileEngine = FileSystemAndFormatEngine(application, cryptoEngine)
    val downloadEngine = FastDownloadEngine(repository, cryptoEngine, viewModelScope)

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

    val downloadPresets: List<DownloadPreset> = downloadEngine.quickPresets

    init {
        if (initialHasAccess) {
            cryptoEngine.setPermissionOnboardingRemembered(true)
        }
        refreshCurrentDirectory()
        runStorageAnalyzer()
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
                statusBannerMessage = if (nextMode) {
                    "Advanced Mode Enabled: Dotfiles, POSIX rwx flags, Checksums, Hex Patching, SQLite/APK & Regex unlocked."
                } else {
                    "User-Friendly Default Mode Active."
                }
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
                        title = "SAF Mount (${treeUri.lastPathSegment ?: "Folder"})",
                        pathOrUri = treeUri.toString(),
                        isSafTree = true
                    )
                )
                refreshPermissionsAndRoots()
                navigateToDirectory(treeUri.toString(), resetStack = true)
                showStatus("Mounted SAF Directory Tree: ${treeUri.lastPathSegment}")
            } catch (e: Exception) {
                reportDiagnosticError("SAF Tree Permission Grant", treeUri.toString(), e)
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
                        it.copy(files = filteredByCategory, isLoading = false)
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false) }
                    reportDiagnosticError("List Directory Contents", state.currentPath, err)
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

    // ==================== MULTI-SELECT & CROSS-VOLUME COPY / MOVE / BATCH ====================

    fun toggleFileSelection(node: FileNode) {
        _uiState.update { state ->
            val updated = state.selectedFilePaths.toMutableSet()
            if (updated.contains(node.path)) {
                updated.remove(node.path)
            } else {
                updated.add(node.path)
            }
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
                ),
                statusBannerMessage = "${if (isMove) "Cut" else "Copied"} '${node.name}' to clipboard. Navigate to destination and tap Paste."
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
                ),
                statusBannerMessage = "${if (isMove) "Cut" else "Copied"} ${selectedNodes.size} item(s). Navigate to target folder and tap Paste."
            )
        }
    }

    fun setClipboardConflictPolicy(policy: ConflictPolicy) {
        _uiState.update {
            it.copy(clipboardState = it.clipboardState.copy(conflictPolicy = policy))
        }
    }

    fun clearClipboard() {
        _uiState.update {
            it.copy(clipboardState = ClipboardState())
        }
    }

    fun pasteClipboardToCurrentDirectory() {
        val state = _uiState.value
        val clipboard = state.clipboardState
        if (!clipboard.isActive) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val res = fileEngine.copyOrMoveBatch(
                sourceNodes = clipboard.sourceNodes,
                destinationDirPathOrUri = state.currentPath,
                isMove = clipboard.isMoveOperation,
                conflictPolicy = clipboard.conflictPolicy
            )
            res.fold(
                onSuccess = { processedCount ->
                    val skippedCount = (clipboard.sourceNodes.size - processedCount).coerceAtLeast(0)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            clipboardState = ClipboardState(),
                            selectedFilePaths = emptySet()
                        )
                    }
                    repository.recordLog(
                        operationType = if (clipboard.isMoveOperation) "BATCH_MOVE" else "BATCH_COPY",
                        targetPath = state.currentPath,
                        isError = false,
                        summary = "${if (clipboard.isMoveOperation) "Moved" else "Copied"} $processedCount item(s), Skipped $skippedCount [${clipboard.conflictPolicy.label}]"
                    )
                    refreshCurrentDirectory()
                    runStorageAnalyzer()
                    showStatus(
                        "${if (clipboard.isMoveOperation) "Moved" else "Copied"} $processedCount item(s) • Skipped: $skippedCount (${clipboard.conflictPolicy.label})"
                    )
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false) }
                    reportDiagnosticError(
                        if (clipboard.isMoveOperation) "Batch Move Operation" else "Batch Copy Operation",
                        state.currentPath,
                        err
                    )
                }
            )
        }
    }

    fun batchDeleteSelected() {
        val state = _uiState.value
        val selectedNodes = state.files.filter { state.selectedFilePaths.contains(it.path) }
        if (selectedNodes.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            var deletedCount = 0
            selectedNodes.forEach { node ->
                val res = fileEngine.deleteFileOrDirectory(node)
                if (res.isSuccess) deletedCount++
            }
            _uiState.update { it.copy(isLoading = false, selectedFilePaths = emptySet()) }
            refreshCurrentDirectory()
            runStorageAnalyzer()
            showStatus("Deleted $deletedCount selected item(s).")
        }
    }

    fun batchCompressSelectedToZip(archiveName: String) {
        val state = _uiState.value
        val selectedNodes = state.files.filter { state.selectedFilePaths.contains(it.path) }
        if (selectedNodes.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val res = fileEngine.compressNodesToZip(
                sourceNodes = selectedNodes,
                destinationDirPathOrUri = state.currentPath,
                archiveName = archiveName
            )
            res.fold(
                onSuccess = { zipNode ->
                    _uiState.update { it.copy(isLoading = false, selectedFilePaths = emptySet()) }
                    refreshCurrentDirectory()
                    showStatus("Created ZIP Archive '${zipNode.name}' (${LumaFormatUtils.formatBytes(zipNode.sizeBytes)})")
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false) }
                    reportDiagnosticError("Batch Compress to ZIP", state.currentPath, err)
                }
            )
        }
    }

    fun batchEncryptSelectedToVault() {
        val state = _uiState.value
        val selectedNodes = state.files.filter { state.selectedFilePaths.contains(it.path) && !it.isDirectory }
        if (selectedNodes.isEmpty()) {
            showStatus("Select one or more files to encrypt into the AES-256 Secure Vault.")
            return
        }
        if (!state.isVaultUnlocked || state.unlockedPinInMemory.isBlank()) {
            _uiState.update {
                it.copy(
                    activeTab = LumaTab.VAULT,
                    statusBannerMessage = "Unlock the Secure Vault first to batch-encrypt ${selectedNodes.size} file(s)."
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            var encryptedCount = 0
            selectedNodes.forEach { node ->
                val res = cryptoEngine.encryptFileToVault(
                    fileNode = node,
                    activeSecret = state.unlockedPinInMemory,
                    deleteOriginalAfterEncrypt = true
                )
                res.onSuccess { entity ->
                    repository.addVaultItem(entity)
                    encryptedCount++
                }
            }
            _uiState.update { it.copy(isLoading = false, selectedFilePaths = emptySet()) }
            refreshCurrentDirectory()
            showStatus("Encrypted & shredded $encryptedCount file(s) into isolated AES-256 Vault (.lvault).")
        }
    }

    // ==================== OPEN-WITH PERMISSION & STUDIO ROUTING ====================

    fun onFileNodeTapped(node: FileNode) {
        val state = _uiState.value
        if (state.selectedFilePaths.isNotEmpty()) {
            toggleFileSelection(node)
            return
        }
        if (node.isDirectory) {
            navigateToDirectory(node.path)
        } else {
            _uiState.update { it.copy(pendingOpenWithFile = node) }
        }
    }

    fun dismissOpenWithDialog() {
        _uiState.update { it.copy(pendingOpenWithFile = null) }
    }

    fun confirmOpenInLumaStudio(
        node: FileNode,
        forceHexInspector: Boolean = false,
        charsetName: String = "UTF-8",
        pdfPageIndex: Int = 0,
        hexOffset: Long = 0L
    ) {
        _uiState.update { it.copy(pendingOpenWithFile = null, isLoading = true) }
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
                    _uiState.update {
                        it.copy(
                            activeStudioContent = content,
                            studioHasUnsavedChanges = false,
                            isLoading = false
                        )
                    }
                    repository.recordLog(
                        operationType = "OPEN_STUDIO",
                        targetPath = node.path,
                        isError = false,
                        summary = "Opened ${node.name} in Luma Studio (${content.javaClass.simpleName})"
                    )
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false) }
                    reportDiagnosticError("Open File in Luma Studio", node.path, err)
                }
            )
        }
    }

    fun closeStudioEditor() {
        _uiState.update {
            it.copy(activeStudioContent = null, studioHasUnsavedChanges = false)
        }
    }

    fun saveTextEditorChanges(node: FileNode, newText: String, encoding: String) {
        viewModelScope.launch {
            val res = fileEngine.saveTextOrCodeFileLosslessly(node, newText, encoding)
            res.fold(
                onSuccess = { updatedNode ->
                    confirmOpenInLumaStudio(updatedNode, charsetName = encoding)
                    refreshCurrentDirectory()
                    showStatus("Saved '${updatedNode.name}' losslessly (${LumaFormatUtils.formatBytes(updatedNode.sizeBytes)})")
                },
                onFailure = { err ->
                    reportDiagnosticError("Save Text/Code File", node.path, err)
                }
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
            val res = fileEngine.saveDocxParagraphsLosslessly(node, updatedParagraphs)
            res.fold(
                onSuccess = { updatedNode ->
                    confirmOpenInLumaStudio(updatedNode)
                    refreshCurrentDirectory()
                    showStatus("Saved Word OOXML '${updatedNode.name}' via W3C DOM with styles & run properties preserved.")
                },
                onFailure = { err ->
                    reportDiagnosticError("Save Word .docx Document", node.path, err)
                }
            )
        }
    }

    fun saveCsvGridChanges(
        node: FileNode,
        delimiter: Char,
        headers: List<String>,
        rows: List<List<String>>
    ) {
        viewModelScope.launch {
            val res = fileEngine.saveCsvGridLosslessly(node, delimiter, headers, rows)
            res.fold(
                onSuccess = { updatedNode ->
                    confirmOpenInLumaStudio(updatedNode)
                    refreshCurrentDirectory()
                    showStatus("Saved Spreadsheet Grid '${updatedNode.name}' (${rows.size} rows, ${headers.size} columns).")
                },
                onFailure = { err ->
                    reportDiagnosticError("Save CSV/TSV Spreadsheet", node.path, err)
                }
            )
        }
    }

    fun selectSqliteTableInStudio(node: FileNode, tableName: String) {
        viewModelScope.launch {
            val res = fileEngine.loadSqliteStudioForTable(node, selectedTable = tableName)
            res.fold(
                onSuccess = { studio ->
                    _uiState.update { it.copy(activeStudioContent = studio) }
                },
                onFailure = { err ->
                    reportDiagnosticError("Query SQLite Table '$tableName'", node.path, err)
                }
            )
        }
    }

    fun executeSqliteQueryInStudio(node: FileNode, activeTable: String, sqlQuery: String) {
        viewModelScope.launch {
            val res = fileEngine.executeSqliteQuery(node, activeTable, sqlQuery)
            res.fold(
                onSuccess = { studio ->
                    _uiState.update { it.copy(activeStudioContent = studio) }
                    showStatus("Executed SQL query on '${node.name}' (${studio.rows.size} rows returned).")
                },
                onFailure = { err ->
                    reportDiagnosticError("Execute SQLite Query", node.path, err)
                }
            )
        }
    }

    fun savePdfAnnotation(node: FileNode, notes: String) {
        viewModelScope.launch {
            val res = fileEngine.savePdfAnnotationNotes(node, notes)
            res.fold(
                onSuccess = { noteFile ->
                    refreshCurrentDirectory()
                    showStatus("Saved PDF companion notes to ${noteFile.name}")
                },
                onFailure = { err ->
                    reportDiagnosticError("Save PDF Annotation Notes", node.path, err)
                }
            )
        }
    }

    fun extractArchiveInStudio(node: FileNode) {
        viewModelScope.launch {
            val parentDir = File(node.path).parentFile ?: fileEngine.lumaWorkspaceDir
            val res = fileEngine.extractZipArchive(node, parentDir)
            res.fold(
                onSuccess = { count ->
                    refreshCurrentDirectory()
                    showStatus("Extracted $count files from '${node.name}' with CRC-32 verification.")
                },
                onFailure = { err ->
                    reportDiagnosticError("Extract ZIP Archive", node.path, err)
                }
            )
        }
    }

    fun addEntryToZipInStudio(node: FileNode, entryName: String, entryContent: String) {
        viewModelScope.launch {
            val res = fileEngine.addTextEntryToZipArchive(node, entryName, entryContent)
            res.fold(
                onSuccess = { updatedNode ->
                    confirmOpenInLumaStudio(updatedNode)
                    refreshCurrentDirectory()
                    showStatus("Added '$entryName' into archive '${updatedNode.name}'.")
                },
                onFailure = { err ->
                    reportDiagnosticError("Add Entry to ZIP Archive", node.path, err)
                }
            )
        }
    }

    fun patchHexByteInStudio(node: FileNode, byteOffset: Long, newHexByte: String, currentWindowOffset: Long) {
        viewModelScope.launch {
            val res = fileEngine.patchSingleByteInFile(node, byteOffset, newHexByte)
            res.fold(
                onSuccess = {
                    confirmOpenInLumaStudio(node, forceHexInspector = true, hexOffset = currentWindowOffset)
                    refreshCurrentDirectory()
                    showStatus("Patched byte at offset 0x${"%08X".format(byteOffset)} to 0x${newHexByte.uppercase()}")
                },
                onFailure = { err ->
                    reportDiagnosticError("Hex Byte Patch Operation", node.path, err)
                }
            )
        }
    }

    // ==================== FILE CRUD & ADVANCED TOOLS ====================

    fun createNewItem(name: String, isDirectory: Boolean, initialContent: String = "") {
        viewModelScope.launch {
            val currentDir = _uiState.value.currentPath
            val res = fileEngine.createNewFileOrFolder(currentDir, name, isDirectory, initialContent)
            res.fold(
                onSuccess = { created ->
                    refreshCurrentDirectory()
                    showStatus("Created ${if (isDirectory) "folder" else "file"} '${created.name}'")
                },
                onFailure = { err ->
                    reportDiagnosticError("Create File/Folder", "$currentDir/$name", err)
                }
            )
        }
    }

    fun renameItem(node: FileNode, newName: String) {
        viewModelScope.launch {
            val res = fileEngine.renameFile(node, newName)
            res.fold(
                onSuccess = { renamed ->
                    refreshCurrentDirectory()
                    showStatus("Renamed '${node.name}' to '${renamed.name}'")
                },
                onFailure = { err ->
                    reportDiagnosticError("Rename File", node.path, err)
                }
            )
        }
    }

    fun deleteItem(node: FileNode) {
        viewModelScope.launch {
            val res = fileEngine.deleteFileOrDirectory(node)
            res.fold(
                onSuccess = {
                    refreshCurrentDirectory()
                    runStorageAnalyzer()
                    showStatus("Deleted '${node.name}'")
                },
                onFailure = { err ->
                    reportDiagnosticError("Delete File/Directory", node.path, err)
                }
            )
        }
    }

    fun runBatchRegexRename(pattern: String, replacement: String, prefix: String) {
        viewModelScope.launch {
            val dir = _uiState.value.currentPath
            val res = fileEngine.batchRegexRename(dir, pattern, replacement, prefix)
            res.fold(
                onSuccess = { count ->
                    refreshCurrentDirectory()
                    showStatus("Batch renamed $count files in current directory.")
                },
                onFailure = { err ->
                    reportDiagnosticError("Batch Regex Rename", dir, err)
                }
            )
        }
    }

    fun inspectFileChecksums(node: FileNode) {
        _uiState.update {
            it.copy(
                inspectedFileNode = node,
                inspectedFileMd5 = "Computing...",
                inspectedFileSha256 = "Computing...",
                isComputingChecksums = true
            )
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
                _uiState.update {
                    it.copy(
                        inspectedFileMd5 = md5,
                        inspectedFileSha256 = sha256,
                        isComputingChecksums = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isComputingChecksums = false, inspectedFileNode = null) }
                reportDiagnosticError("Checksum Calculation", node.path, e)
            }
        }
    }

    fun dismissFileInspector() {
        _uiState.update { it.copy(inspectedFileNode = null) }
    }

    fun bookmarkCurrentDirectory() {
        viewModelScope.launch {
            val path = _uiState.value.currentPath
            val name = path.substringAfterLast('/').ifBlank { "Root Storage" }
            repository.addBookmark(
                BookmarkEntity(
                    title = name,
                    pathOrUri = path,
                    isSafTree = path.startsWith("content://")
                )
            )
            showStatus("Bookmarked '$name'")
        }
    }

    fun removeBookmark(id: Long) {
        viewModelScope.launch {
            repository.removeBookmark(id)
        }
    }

    fun runStorageAnalyzer() {
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzingStorage = true) }
            val targetRoot = _uiState.value.currentPath.takeIf { !it.startsWith("content://") }
                ?: fileEngine.lumaWorkspaceDir.absolutePath
            val res = fileEngine.runStorageAnalyzer(targetRoot)
            res.fold(
                onSuccess = { report ->
                    _uiState.update {
                        it.copy(analyzerReport = report, isAnalyzingStorage = false)
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isAnalyzingStorage = false) }
                    reportDiagnosticError("Storage & Duplicate Analyzer", targetRoot, err)
                }
            )
        }
    }

    // ==================== AES-256 PIN / PASSWORD SECURE VAULT (210K PBKDF2) ====================

    fun setupVaultCredential(
        secret: String,
        authMode: VaultAuthMode,
        recoveryQuestion: String,
        recoveryAnswer: String
    ) {
        val res = cryptoEngine.setupCredential(secret, authMode, recoveryQuestion, recoveryAnswer)
        res.fold(
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
                        statusBannerMessage = "AES-256-GCM Secure Vault configured (${authMode.label} • 210,000 PBKDF2 iterations)."
                    )
                }
            },
            onFailure = { err ->
                _uiState.update { it.copy(vaultAuthError = err.message) }
            }
        )
    }

    fun unlockVaultWithPin(secret: String) {
        if (cryptoEngine.verifyPin(secret)) {
            _uiState.update {
                it.copy(
                    isVaultUnlocked = true,
                    unlockedPinInMemory = secret,
                    vaultAuthError = null,
                    statusBannerMessage = "Secure Vault Unlocked (210k PBKDF2 Verified)."
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    vaultAuthError = "Authentication Failed: Incorrect ${it.vaultAuthMode.label} entered."
                )
            }
        }
    }

    fun lockVaultSession() {
        _uiState.update {
            it.copy(
                isVaultUnlocked = false,
                unlockedPinInMemory = "",
                vaultAuthError = null,
                statusBannerMessage = "Secure Vault Locked. In-memory key wiped."
            )
        }
    }

    fun resetPinViaRecovery(answer: String, newSecret: String, newMode: VaultAuthMode) {
        val ok = cryptoEngine.resetPinWithRecovery(answer, newSecret, newMode)
        if (ok) {
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
                    statusBannerMessage = "Vault ${newMode.label} reset and unlocked."
                )
            }
        } else {
            _uiState.update {
                it.copy(vaultAuthError = "Recovery Answer Mismatch: Unable to reset credential.")
            }
        }
    }

    fun moveFileToSecureVault(node: FileNode) {
        val state = _uiState.value
        if (!state.isVaultUnlocked || state.unlockedPinInMemory.isBlank()) {
            _uiState.update {
                it.copy(
                    activeTab = LumaTab.VAULT,
                    statusBannerMessage = "Please unlock the AES-256 Secure Vault first to encrypt '${node.name}'."
                )
            }
            return
        }
        viewModelScope.launch {
            val res = cryptoEngine.encryptFileToVault(
                fileNode = node,
                activeSecret = state.unlockedPinInMemory,
                deleteOriginalAfterEncrypt = true
            )
            res.fold(
                onSuccess = { entity ->
                    repository.addVaultItem(entity)
                    repository.recordLog(
                        operationType = "VAULT_ENCRYPT",
                        targetPath = node.path,
                        isError = false,
                        summary = "Encrypted '${node.name}' into isolated .lvault UUID container & zero-shredded source (SHA-256: ${entity.sha256Checksum.take(10)}...)"
                    )
                    refreshCurrentDirectory()
                    showStatus("Locked '${node.name}' into isolated AES-256 Vault (.lvault) & shredded original.")
                },
                onFailure = { err ->
                    reportDiagnosticError("AES-256 Vault Encryption", node.path, err)
                }
            )
        }
    }

    fun restoreFileFromSecureVault(item: VaultItemEntity) {
        val state = _uiState.value
        if (!state.isVaultUnlocked || state.unlockedPinInMemory.isBlank()) {
            showStatus("Unlock Secure Vault first.")
            return
        }
        viewModelScope.launch {
            val originalParent = File(item.originalPath).parentFile
            val restoreDir = if (originalParent != null && originalParent.exists() && originalParent.canWrite()) {
                originalParent
            } else {
                File(fileEngine.lumaWorkspaceDir, "Restored_From_Vault")
            }
            val res = cryptoEngine.decryptVaultItemToDirectory(
                item = item,
                activeSecret = state.unlockedPinInMemory,
                targetDirectory = restoreDir,
                removeEncryptedCopy = true
            )
            res.fold(
                onSuccess = { restoredFile ->
                    repository.removeVaultItem(item.id)
                    repository.recordLog(
                        operationType = "VAULT_RESTORE",
                        targetPath = restoredFile.absolutePath,
                        isError = false,
                        summary = "Restored '${restoredFile.name}' with verified 100% SHA-256 bit-exact quality."
                    )
                    refreshCurrentDirectory()
                    showStatus("Restored '${restoredFile.name}' to ${restoreDir.name} (SHA-256 Verified).")
                },
                onFailure = { err ->
                    reportDiagnosticError("AES-256 Vault Decryption", item.originalName, err)
                }
            )
        }
    }

    // ==================== MULTI-THREADED DOWNLOAD MANAGER ====================

    fun enqueueDownload(url: String, customFileName: String, threads: Int) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return
        val fallbackName = cleanUrl.substringAfterLast('/').substringBefore('?').ifBlank {
            "download_${System.currentTimeMillis()}.bin"
        }
        val finalName = customFileName.trim().ifBlank { fallbackName }
        val downloadsDir = File(fileEngine.lumaWorkspaceDir, "Downloads").apply { mkdirs() }
        val destFile = File(downloadsDir, finalName)

        viewModelScope.launch {
            val task = DownloadTaskEntity(
                url = cleanUrl,
                fileName = finalName,
                destinationPath = destFile.absolutePath,
                totalBytes = 0L,
                downloadedBytes = 0L,
                status = "QUEUED",
                threadCount = threads.coerceIn(1, 4)
            )
            val taskId = repository.addDownloadTask(task)
            downloadEngine.startOrResumeDownload(taskId) { diagnosticError ->
                _uiState.update { it.copy(activeDiagnosticError = diagnosticError) }
            }
            showStatus("Started $threads-thread download for '$finalName'")
        }
    }

    fun pauseDownloadTask(taskId: Long) {
        downloadEngine.pauseDownload(taskId)
    }

    fun resumeDownloadTask(taskId: Long) {
        downloadEngine.startOrResumeDownload(taskId) { diagnosticError ->
            _uiState.update { it.copy(activeDiagnosticError = diagnosticError) }
        }
    }

    fun deleteDownloadTask(task: DownloadTaskEntity, deleteFileFromDisk: Boolean) {
        downloadEngine.pauseDownload(task.id)
        viewModelScope.launch {
            if (deleteFileFromDisk) {
                runCatching { File(task.destinationPath).delete() }
            }
            repository.removeDownloadTask(task.id)
            refreshCurrentDirectory()
        }
    }

    fun openDownloadedFile(task: DownloadTaskEntity) {
        val file = File(task.destinationPath)
        if (!file.exists()) {
            reportDiagnosticError(
                "Open Downloaded File",
                task.destinationPath,
                IllegalStateException("Downloaded file no longer exists on disk at '${task.destinationPath}'.")
            )
            return
        }
        val node = with(fileEngine) { file.toFileNode() }
        onFileNodeTapped(node)
    }

    // ==================== DIAGNOSTICS & LOGS ====================

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
                summary = "${diag.errorCodeOrStatus}: ${diag.exactReason}",
                exactTechnicalReason = "${diag.exceptionClass}\n${diag.exactReason}\nHint: ${diag.remediationHint}"
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
            showStatus("Cleared operation & diagnostic logs.")
        }
    }
}
