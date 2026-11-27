package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.domain.engine.PhotoAiStudioEngine
import com.example.domain.model.AiEnhanceUiState
import com.example.domain.model.FileNode
import com.example.domain.model.PhotoAdjustments
import com.example.domain.model.StudioContent
import com.example.domain.model.ZipEntryPreview
import com.example.ui.LumaFormatUtils
import com.example.ui.screens.viewers.FullScreenAudioVfxPlayer
import com.example.ui.screens.viewers.FullScreenPhotoStudio
import com.example.ui.screens.viewers.FullScreenVideoTheater
import com.example.ui.screens.viewers.ProApkInspectorView
import com.example.ui.screens.viewers.ProCodeAndTextStudioView
import com.example.ui.screens.viewers.ProCsvSpreadsheetView
import com.example.ui.screens.viewers.ProDocxStudioView
import com.example.ui.screens.viewers.ProHexBinaryView
import com.example.ui.screens.viewers.ProPdfReaderView
import com.example.ui.screens.viewers.ProSqliteStudioView
import com.example.ui.screens.viewers.ProZipArchiveView

/**
 * Full-Screen V2 Studio Router: dispatches every file format to its dedicated modern viewer/studio.
 */
@Composable
fun StudioEditorModal(
    studioContent: StudioContent,
    isVaultPreview: Boolean,
    siblingMediaFiles: List<FileNode>,
    aiEnhanceState: AiEnhanceUiState,
    photoEngine: PhotoAiStudioEngine,
    activeZipEntryPreview: ZipEntryPreview?,
    onClose: () -> Unit,
    onOpenSiblingFile: (FileNode) -> Unit,
    onRenameFile: (FileNode, String) -> Unit,
    onDeleteFile: (FileNode) -> Unit,
    onLockToVault: (FileNode) -> Unit,
    onTriggerAiAutoEnhance: (Bitmap, (PhotoAdjustments, String, String) -> Unit) -> Unit,
    onTriggerAiRemaster: (Bitmap, String, (Bitmap) -> Unit) -> Unit,
    onSaveEditedPhoto: (FileNode, Bitmap, Boolean) -> Unit,
    onCaptureVideoFrame: (FileNode, Bitmap) -> Unit,
    onRenderPdfPage: suspend (FileNode, Int) -> Bitmap?,
    onSaveText: (FileNode, String, String) -> Unit,
    onFormatStructuredText: (FileNode, String, String) -> Unit,
    onSaveDocx: (FileNode, List<String>) -> Unit,
    onSaveCsvGrid: (FileNode, Char, List<String>, List<List<String>>) -> Unit,
    onSelectSqliteTable: (FileNode, String) -> Unit,
    onExecuteSqliteQuery: (FileNode, String, String) -> Unit,
    onSavePdfNotes: (FileNode, String) -> Unit,
    onPreviewZipEntry: (FileNode, String) -> Unit,
    onDismissZipEntryPreview: () -> Unit,
    onExtractSingleZipEntry: (FileNode, String) -> Unit,
    onExtractZip: (FileNode) -> Unit,
    onAddZipEntry: (FileNode, String, String) -> Unit,
    onSwitchToHex: (FileNode, Long) -> Unit,
    onPatchHexByte: (FileNode, Long, String, Long) -> Unit,
    onOpenExternal: (FileNode) -> Unit,
    onReportShareOrInstallError: (com.example.domain.model.DiagnosticError) -> Unit
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("studio_editor_modal")
    ) {
        when (studioContent) {
            is StudioContent.ImageStudio -> {
                FullScreenPhotoStudio(
                    studio = studioContent,
                    isVaultPreview = isVaultPreview,
                    siblingPhotos = siblingMediaFiles,
                    aiEnhanceState = aiEnhanceState,
                    photoEngine = photoEngine,
                    onClose = onClose,
                    onOpenSiblingPhoto = onOpenSiblingFile,
                    onRenamePhoto = onRenameFile,
                    onDeletePhoto = onDeleteFile,
                    onSharePhoto = { node ->
                        LumaFormatUtils.shareFile(context, node, onReportShareOrInstallError)
                    },
                    onLockToVault = onLockToVault,
                    onOpenExternal = onOpenExternal,
                    onTriggerAiAutoEnhance = onTriggerAiAutoEnhance,
                    onTriggerAiRemaster = onTriggerAiRemaster,
                    onSaveEditedBitmap = onSaveEditedPhoto
                )
            }

            is StudioContent.MediaStudio -> {
                if (studioContent.isVideo) {
                    FullScreenVideoTheater(
                        studio = studioContent,
                        isVaultPreview = isVaultPreview,
                        onClose = onClose,
                        onRenameFile = onRenameFile,
                        onShareFile = { node ->
                            LumaFormatUtils.shareFile(context, node, onReportShareOrInstallError)
                        },
                        onOpenExternal = onOpenExternal,
                        onCaptureFrameBitmap = onCaptureVideoFrame
                    )
                } else {
                    FullScreenAudioVfxPlayer(
                        studio = studioContent,
                        isVaultPreview = isVaultPreview,
                        siblingAudioFiles = siblingMediaFiles,
                        onClose = onClose,
                        onOpenSiblingAudio = onOpenSiblingFile,
                        onRenameFile = onRenameFile,
                        onShareFile = { node ->
                            LumaFormatUtils.shareFile(context, node, onReportShareOrInstallError)
                        },
                        onOpenExternal = onOpenExternal
                    )
                }
            }

            is StudioContent.PdfViewer -> {
                ProPdfReaderView(
                    studio = studioContent,
                    isVaultPreview = isVaultPreview,
                    onRenderPageBitmap = onRenderPdfPage,
                    onSaveNotes = onSavePdfNotes,
                    onClose = onClose,
                    onShare = { node -> LumaFormatUtils.shareFile(context, node, onReportShareOrInstallError) },
                    onOpenExternal = onOpenExternal
                )
            }

            is StudioContent.DocxEditor -> {
                ProDocxStudioView(
                    studio = studioContent,
                    isVaultPreview = isVaultPreview,
                    onSaveDocx = onSaveDocx,
                    onClose = onClose,
                    onShare = { node -> LumaFormatUtils.shareFile(context, node, onReportShareOrInstallError) },
                    onOpenExternal = onOpenExternal
                )
            }

            is StudioContent.TextCodeEditor -> {
                ProCodeAndTextStudioView(
                    studio = studioContent,
                    isVaultPreview = isVaultPreview,
                    onSaveText = onSaveText,
                    onFormatStructured = onFormatStructuredText,
                    onSwitchHex = { node -> onSwitchToHex(node, 0L) },
                    onClose = onClose,
                    onShare = { node -> LumaFormatUtils.shareFile(context, node, onReportShareOrInstallError) },
                    onOpenExternal = onOpenExternal
                )
            }

            is StudioContent.CsvGridStudio -> {
                ProCsvSpreadsheetView(
                    studio = studioContent,
                    isVaultPreview = isVaultPreview,
                    onSaveCsv = onSaveCsvGrid,
                    onClose = onClose,
                    onShare = { node -> LumaFormatUtils.shareFile(context, node, onReportShareOrInstallError) },
                    onOpenExternal = onOpenExternal
                )
            }

            is StudioContent.ZipArchiveManager -> {
                ProZipArchiveView(
                    studio = studioContent,
                    isVaultPreview = isVaultPreview,
                    activeEntryPreview = activeZipEntryPreview,
                    onPreviewEntry = onPreviewZipEntry,
                    onDismissEntryPreview = onDismissZipEntryPreview,
                    onExtractSingleEntry = onExtractSingleZipEntry,
                    onExtractAll = onExtractZip,
                    onAddEntry = onAddZipEntry,
                    onClose = onClose,
                    onShare = { node -> LumaFormatUtils.shareFile(context, node, onReportShareOrInstallError) }
                )
            }

            is StudioContent.SqliteStudio -> {
                ProSqliteStudioView(
                    studio = studioContent,
                    isVaultPreview = isVaultPreview,
                    onSelectTable = onSelectSqliteTable,
                    onExecuteSql = onExecuteSqliteQuery,
                    onClose = onClose
                )
            }

            is StudioContent.ApkInspectorStudio -> {
                ProApkInspectorView(
                    studio = studioContent,
                    isVaultPreview = isVaultPreview,
                    onClose = onClose,
                    onShare = { node -> LumaFormatUtils.shareFile(context, node, onReportShareOrInstallError) },
                    onInstallApk = { node -> LumaFormatUtils.installApk(context, node, onReportShareOrInstallError) }
                )
            }

            is StudioContent.HexBinaryStudio -> {
                ProHexBinaryView(
                    studio = studioContent,
                    isVaultPreview = isVaultPreview,
                    onJumpOffset = onSwitchToHex,
                    onPatchByte = onPatchHexByte,
                    onClose = onClose
                )
            }
        }
    }
}
