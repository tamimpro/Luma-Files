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
    DIRECTORY("Directory", "DIR"),
    TEXT_CODE("Text & Code", "CODE"),
    MARKDOWN("Markdown Document", "MD"),
    PDF("PDF Document", "PDF"),
    DOCX("Word OOXML Document", "DOCX"),
    ARCHIVE_ZIP("ZIP / Archive", "ZIP"),
    VIDEO("Lossless Video", "VID"),
    AUDIO("Lossless Audio", "AUD"),
    IMAGE("High-Res Image", "IMG"),
    BINARY_HEX("Raw Binary / Hex", "BIN")
}

enum class SortOption(val label: String) {
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    SIZE_DESC("Size (Largest First)"),
    SIZE_ASC("Size (Smallest First)"),
    DATE_DESC("Modified (Newest)"),
    TYPE("Format Type")
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
    val safUriString: String? = null
)

data class StorageVolumeRoot(
    val title: String,
    val subtitle: String,
    val path: String,
    val totalBytes: Long,
    val freeBytes: Long,
    val isSafTree: Boolean = false,
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

sealed class StudioContent {
    data class TextCodeEditor(
        val file: FileNode,
        val textContent: String,
        val encoding: String = "UTF-8",
        val lineCount: Int,
        val isMarkdown: Boolean,
        val isTruncatedPreview: Boolean = false,
        val totalFileBytes: Long
    ) : StudioContent()

    data class DocxEditor(
        val file: FileNode,
        val paragraphs: List<String>,
        val xmlEntriesCount: Int,
        val embeddedMediaCount: Int
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
        val formatHeaderInfo: String
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
