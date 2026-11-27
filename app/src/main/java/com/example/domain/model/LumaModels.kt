package com.example.domain.model

import android.graphics.Bitmap

enum class LumaTab {
    EXPLORER,
    ANALYZER,
    VAULT,
    DOWNLOADS,
    ADVANCED_HUB
}

enum class FileFormatCategory(val label: String, val badgeText: String) {
    DIRECTORY("Folder", "DIR"),
    TEXT_CODE("Code", "CODE"),
    STRUCTURED_DATA("Data", "DATA"),
    MARKDOWN("Markdown", "MD"),
    PDF("PDF", "PDF"),
    DOCX("Document", "DOCX"),
    CSV_TSV("Sheet", "CSV"),
    SQLITE_DB("Database", "DB"),
    APK_PACKAGE("App", "APK"),
    ARCHIVE_ZIP("Archive", "ZIP"),
    VIDEO("Video", "VID"),
    AUDIO("Audio", "AUD"),
    IMAGE("Image", "IMG"),
    BINARY_HEX("Binary", "BIN")
}

enum class SortOption(val label: String) {
    NAME_ASC("Name A–Z"),
    NAME_DESC("Name Z–A"),
    SIZE_DESC("Largest"),
    SIZE_ASC("Smallest"),
    DATE_DESC("Newest"),
    TYPE("Type")
}

enum class ConflictPolicy(val label: String) {
    KEEP_BOTH("Keep Both"),
    OVERWRITE("Overwrite"),
    SKIP("Skip")
}

enum class VaultAuthMode(val label: String, val minLength: Int) {
    PIN_4("4-Digit PIN", 4),
    PIN_6("6-Digit PIN", 6),
    ALPHANUMERIC_PASSWORD("Password", 6)
}

enum class DownloadUiMode(val label: String) {
    DEFAULT("Default"),
    ADVANCED("Advanced")
}

enum class PhotoFilterCategory(val label: String) {
    ALL("All"),
    CINEMATIC("Cinematic"),
    BEAUTY("Beauty")
}

data class PhotoFilterPreset(
    val id: String,
    val name: String,
    val category: PhotoFilterCategory,
    val matrix: FloatArray,
    val glowStrength: Float = 0f,
    val vignetteStrength: Float = 0f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PhotoFilterPreset) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

data class PhotoAdjustments(
    val brightness: Float = 0f,     // -0.5f .. 0.5f
    val contrast: Float = 1f,       // 0.5f .. 1.8f
    val saturation: Float = 1f,     // 0f .. 2f
    val warmth: Float = 0f,         // -0.5f .. 0.5f
    val clarity: Float = 0f,        // 0f .. 1f
    val skinSmooth: Float = 0f,     // 0f .. 1f
    val vignette: Float = 0f        // 0f .. 0.8f
) {
    val isDefault: Boolean
        get() = brightness == 0f &&
            contrast == 1f &&
            saturation == 1f &&
            warmth == 0f &&
            clarity == 0f &&
            skinSmooth == 0f &&
            vignette == 0f
}

sealed interface AiEnhanceUiState {
    data object Idle : AiEnhanceUiState
    data class Loading(val label: String) : AiEnhanceUiState
    data class Success(val summary: String, val remasteredBitmap: Bitmap? = null) : AiEnhanceUiState
    data class Error(val message: String, val showApiKeyShortcut: Boolean = false) : AiEnhanceUiState
}

data class BrowserHandoffRequest(
    val url: String,
    val sequenceId: Long = System.nanoTime()
)

data class SniffedWebMedia(
    val url: String,
    val fileName: String,
    val mimeType: String,
    val contentLength: Long = -1L,
    val sourcePageUrl: String = "",
    val referer: String = "",
    val cookies: String = "",
    val userAgent: String = "",
    val isHlsOrDash: Boolean = false,
    val audioStreamUrl: String? = null,
    val qualityLabel: String = "",
    val mediaKind: String = "VIDEO",
    val detectedAtMillis: Long = System.currentTimeMillis()
)

data class ClipboardState(
    val sourceNodes: List<FileNode> = emptyList(),
    val isMoveOperation: Boolean = false,
    val conflictPolicy: ConflictPolicy = ConflictPolicy.KEEP_BOTH
) {
    val isActive: Boolean get() = sourceNodes.isNotEmpty()
}

data class RecycleBinItem(
    val id: String,
    val originalName: String,
    val originalPath: String,
    val trashStoragePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val deletedAtMillis: Long,
    val mimeType: String
)

sealed class UndoOperation(open val summaryLabel: String) {
    data class RestoreRecycleBinItems(
        val items: List<RecycleBinItem>,
        override val summaryLabel: String
    ) : UndoOperation(summaryLabel)

    data class DeleteCreatedPaths(
        val createdPaths: List<String>,
        override val summaryLabel: String
    ) : UndoOperation(summaryLabel)

    data class ReverseMoveOperation(
        val movedPairs: List<Pair<String, String>>,
        override val summaryLabel: String
    ) : UndoOperation(summaryLabel)

    data class ReverseRenameOperation(
        val currentPath: String,
        val previousName: String,
        override val summaryLabel: String
    ) : UndoOperation(summaryLabel)
}

data class MediaStreamVariant(
    val variantId: String,
    val label: String,
    val containerExtension: String,
    val mimeType: String,
    val resolutionLabel: String,
    val widthPx: Int,
    val heightPx: Int,
    val frameRateFps: Int,
    val videoCodec: String,
    val videoBitrateKbps: Int,
    val videoStreamUrl: String,
    val audioCodec: String,
    val audioBitrateKbps: Int,
    val audioStreamUrl: String?,
    val hasEmbeddedAudio: Boolean,
    val requiresAudioVideoMux: Boolean,
    val isWatermarkFree: Boolean = true,
    val estimatedSizeBytes: Long = -1L,
    val isHlsPlaylist: Boolean = false,
    val refererHeader: String = "",
    val cookieHeader: String = "",
    val userAgentHeader: String = ""
) {
    val hasAudio: Boolean get() = hasEmbeddedAudio || !audioStreamUrl.isNullOrBlank()
}

data class ResolvedMediaManifest(
    val sourceUrl: String,
    val platformName: String,
    val title: String,
    val creatorAttribution: String,
    val suggestedFileName: String,
    val isSocialPlatform: Boolean,
    val variants: List<MediaStreamVariant>,
    val recommendedVariantId: String,
    val requiresBrowserSniffer: Boolean = false,
    val browserSnifferReason: String? = null
) {
    val recommendedVariant: MediaStreamVariant?
        get() = variants.firstOrNull { it.variantId == recommendedVariantId } ?: variants.firstOrNull()
}

data class FileNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val extension: String,
    val mimeType: String,
    val category: FileFormatCategory,
    val permissionsRwx: String,
    val isHidden: Boolean,
    val childCount: Int = 0,
    val isSafDocument: Boolean = false,
    val safUriString: String? = null,
    val safTreeRootUriString: String? = null
)

data class StorageVolumeRoot(
    val title: String,
    val subtitle: String,
    val path: String,
    val totalBytes: Long,
    val freeBytes: Long,
    val isSafTree: Boolean = false,
    val isRemovableSd: Boolean = false,
    val requiresAllFilesPermission: Boolean = false
)

data class DiagnosticError(
    val operationTitle: String,
    val targetPathOrUrl: String,
    val exceptionClass: String,
    val errorCodeOrStatus: String,
    val exactReason: String,
    val remediationHint: String,
    val stackSnippet: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class ZipEntryInfo(
    val name: String,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val crc32Hex: String,
    val isDirectory: Boolean,
    val lastModified: Long
)

data class ZipEntryPreview(
    val entryName: String,
    val textContent: String? = null,
    val imageBitmap: Bitmap? = null,
    val sizeBytes: Long = 0L,
    val isBinaryUnsupported: Boolean = false
)

data class HexRow(
    val offsetHex: String,
    val byteValuesHex: List<String>,
    val asciiPreview: String
)

data class DuplicateGroup(
    val sha256Hash: String,
    val fileSizeEach: Long,
    val files: List<FileNode>
)

data class StorageAnalyzerReport(
    val scannedFilesCount: Int,
    val scannedDirectoriesCount: Int,
    val totalScannedBytes: Long,
    val categoryBreakdownBytes: Map<FileFormatCategory, Long>,
    val largeFiles: List<FileNode>,
    val duplicateGroups: List<DuplicateGroup>,
    val reclaimableDuplicateBytes: Long
)

data class DocxParagraphNode(
    val index: Int,
    val styleId: String,
    val text: String,
    val hasRunFormatting: Boolean = false
)

sealed class StudioContent {
    data class TextCodeEditor(
        val file: FileNode,
        val textContent: String,
        val encoding: String = "UTF-8",
        val lineCount: Int,
        val isMarkdown: Boolean,
        val isStructuredData: Boolean = false,
        val validationMessage: String? = null,
        val isTruncatedPreview: Boolean = false,
        val totalFileBytes: Long
    ) : StudioContent()

    data class DocxEditor(
        val file: FileNode,
        val paragraphNodes: List<DocxParagraphNode>,
        val xmlEntriesCount: Int,
        val embeddedMediaCount: Int,
        val preservedStylesCount: Int
    ) : StudioContent()

    data class CsvGridStudio(
        val file: FileNode,
        val delimiter: Char,
        val headers: List<String>,
        val rows: List<List<String>>
    ) : StudioContent()

    data class SqliteStudio(
        val file: FileNode,
        val tableNames: List<String>,
        val selectedTable: String,
        val columns: List<String>,
        val rows: List<List<String>>,
        val totalRowsInTable: Int,
        val customQueryResultMessage: String? = null
    ) : StudioContent()

    data class ApkInspectorStudio(
        val file: FileNode,
        val appLabel: String = "",
        val appIconBitmap: Bitmap? = null,
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        val minSdk: Int,
        val targetSdk: Int,
        val permissions: List<String>,
        val activities: List<String>,
        val nativeLibraries: List<String>,
        val dexFilesCount: Int,
        val totalEntriesCount: Int,
        val sha256Signature: String
    ) : StudioContent()

    data class PdfViewer(
        val file: FileNode,
        val pageCount: Int,
        val currentPageIndex: Int,
        val renderedPageBitmap: Bitmap?,
        val annotationNotes: String
    ) : StudioContent()

    data class ZipArchiveManager(
        val file: FileNode,
        val entries: List<ZipEntryInfo>,
        val totalUncompressedBytes: Long,
        val compressionRatioPercent: Int
    ) : StudioContent()

    data class MediaStudio(
        val file: FileNode,
        val isVideo: Boolean,
        val durationMs: Long,
        val sampleRateOrResolution: String,
        val bitrateOrChannels: String,
        val formatHeaderInfo: String,
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val albumArtBitmap: Bitmap? = null,
        val widthPx: Int = 0,
        val heightPx: Int = 0
    ) : StudioContent()

    data class ImageStudio(
        val file: FileNode,
        val bitmap: Bitmap?,
        val widthPx: Int,
        val heightPx: Int,
        val colorSpaceInfo: String,
        val exifSummary: Map<String, String>
    ) : StudioContent()

    data class HexBinaryStudio(
        val file: FileNode,
        val windowOffset: Long,
        val windowSize: Int,
        val totalSize: Long,
        val rows: List<HexRow>,
        val md5Hash: String,
        val sha256Hash: String
    ) : StudioContent()
}
