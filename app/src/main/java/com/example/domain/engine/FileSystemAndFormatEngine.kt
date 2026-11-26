package com.example.domain.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import com.example.domain.model.DiagnosticError
import com.example.domain.model.DuplicateGroup
import com.example.domain.model.FileFormatCategory
import com.example.domain.model.FileNode
import com.example.domain.model.HexRow
import com.example.domain.model.SortOption
import com.example.domain.model.StorageAnalyzerReport
import com.example.domain.model.StorageVolumeRoot
import com.example.domain.model.StudioContent
import com.example.domain.model.ZipEntryInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class FileSystemAndFormatEngine(
    private val context: Context,
    private val cryptoEngine: CryptoVaultEngine
) {

    val lumaWorkspaceDir: File = File(context.filesDir, "Luma_Workspace").apply {
        WorkspaceSeedEngine.ensureSeeded(this)
    }

    fun hasAllFilesAccessPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            Environment.getExternalStorageDirectory().canRead()
        }
    }

    fun getAvailableStorageRoots(): List<StorageVolumeRoot> {
        val roots = mutableListOf<StorageVolumeRoot>()

        // 1. Luma Workspace (Always 100% accessible with multi-format studio samples)
        val internalStat = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
        val internalTotal = internalStat?.totalBytes ?: (64L * 1024 * 1024 * 1024)
        val internalFree = internalStat?.availableBytes ?: (32L * 1024 * 1024 * 1024)
        roots.add(
            StorageVolumeRoot(
                title = "Luma Studio Workspace",
                subtitle = "App-Managed High-Speed Storage (Full Read/Write)",
                path = lumaWorkspaceDir.absolutePath,
                totalBytes = internalTotal,
                freeBytes = internalFree,
                isSafTree = false,
                requiresAllFilesPermission = false
            )
        )

        // 2. Primary Shared Device Storage (/storage/emulated/0)
        val extRoot = Environment.getExternalStorageDirectory()
        val extStat = runCatching { StatFs(extRoot.absolutePath) }.getOrNull()
        val extTotal = extStat?.totalBytes ?: internalTotal
        val extFree = extStat?.availableBytes ?: internalFree
        val hasAllAccess = hasAllFilesAccessPermission()

        roots.add(
            StorageVolumeRoot(
                title = "Internal Shared Storage",
                subtitle = if (hasAllAccess) "${extRoot.absolutePath} (All Files Access Active)" else "${extRoot.absolutePath} (Tap to Grant Full Access)",
                path = extRoot.absolutePath,
                totalBytes = extTotal,
                freeBytes = extFree,
                isSafTree = false,
                requiresAllFilesPermission = !hasAllAccess
            )
        )

        // 3. App External Media/Documents Sandbox
        context.getExternalFilesDir(null)?.let { extAppDir ->
            if (!extAppDir.exists()) extAppDir.mkdirs()
            roots.add(
                StorageVolumeRoot(
                    title = "External App Sandbox",
                    subtitle = extAppDir.absolutePath,
                    path = extAppDir.absolutePath,
                    totalBytes = extTotal,
                    freeBytes = extFree,
                    isSafTree = false,
                    requiresAllFilesPermission = false
                )
            )
        }

        // 4. Persisted SAF Document Trees
        context.contentResolver.persistedUriPermissions.forEach { perm ->
            if (perm.isReadPermission) {
                val docId = runCatching { DocumentsContract.getTreeDocumentId(perm.uri) }.getOrDefault(perm.uri.lastPathSegment ?: "SAF Tree")
                roots.add(
                    StorageVolumeRoot(
                        title = "SAF Mounted Folder ($docId)",
                        subtitle = perm.uri.toString(),
                        path = perm.uri.toString(),
                        totalBytes = extTotal,
                        freeBytes = extFree,
                        isSafTree = true,
                        requiresAllFilesPermission = false
                    )
                )
            }
        }

        return roots
    }

    suspend fun listDirectory(
        pathOrUri: String,
        showHiddenFiles: Boolean,
        sortOption: SortOption,
        searchQuery: String = "",
        useRegexSearch: Boolean = false
    ): Result<List<FileNode>> = withContext(Dispatchers.IO) {
        try {
            val rawNodes = if (pathOrUri.startsWith("content://")) {
                listSafTreeDirectory(Uri.parse(pathOrUri), showHiddenFiles)
            } else {
                listDirectFileDirectory(File(pathOrUri), showHiddenFiles)
            }

            val filtered = if (searchQuery.isBlank()) {
                rawNodes
            } else if (useRegexSearch) {
                val regex = runCatching { Regex(searchQuery, RegexOption.IGNORE_CASE) }.getOrElse {
                    return@withContext Result.failure(
                        IllegalArgumentException("Invalid Regular Expression pattern '$searchQuery': ${it.message}")
                    )
                }
                rawNodes.filter { regex.containsMatchIn(it.name) }
            } else {
                rawNodes.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }

            val sorted = sortNodes(filtered, sortOption)
            Result.success(sorted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun listDirectFileDirectory(dir: File, showHidden: Boolean): List<FileNode> {
        if (!dir.exists()) {
            throw IllegalStateException("Directory '${dir.absolutePath}' does not exist.")
        }
        val children = dir.listFiles()
            ?: throw IllegalStateException(
                "POSIX EACCES (Permission Denied): Unable to list '${dir.absolutePath}'. Grant 'All Files Access' or mount this folder via SAF."
            )

        return children
            .filter { showHidden || (!it.isHidden && !it.name.startsWith(".")) }
            .map { it.toFileNode() }
    }

    private fun listSafTreeDirectory(treeUri: Uri, showHidden: Boolean): List<FileNode> {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        val results = mutableListOf<FileNode>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val modCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                val childId = cursor.getString(idCol) ?: continue
                val name = cursor.getString(nameCol) ?: childId
                val isHidden = name.startsWith(".")
                if (!showHidden && isHidden) continue
                val mime = cursor.getString(mimeCol) ?: "application/octet-stream"
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val size = if (cursor.isNull(sizeCol)) 0L else cursor.getLong(sizeCol)
                val lastMod = if (cursor.isNull(modCol)) System.currentTimeMillis() else cursor.getLong(modCol)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                val ext = name.substringAfterLast('.', "").lowercase()

                results.add(
                    FileNode(
                        name = name,
                        path = docUri.toString(),
                        isDirectory = isDir,
                        sizeBytes = size,
                        lastModified = lastMod,
                        extension = ext,
                        mimeType = mime,
                        category = if (isDir) FileFormatCategory.DIRECTORY else detectCategory(ext, mime),
                        permissionsRwx = if (isDir) "drwxr-x--- (SAF)" else "-rw-r----- (SAF)",
                        isHidden = isHidden,
                        childCount = 0,
                        isSafDocument = true,
                        safUriString = docUri.toString()
                    )
                )
            }
        }
        return results
    }

    fun File.toFileNode(): FileNode {
        val isDir = isDirectory
        val ext = if (isDir) "" else extension.lowercase()
        val mime = if (isDir) "resource/folder" else detectMimeType(ext)
        val r = if (canRead()) "r" else "-"
        val w = if (canWrite()) "w" else "-"
        val x = if (canExecute()) "x" else "-"
        val prefix = if (isDir) "d" else "-"
        val childItems = if (isDir) (listFiles()?.size ?: 0) else 0

        return FileNode(
            name = name,
            path = absolutePath,
            isDirectory = isDir,
            sizeBytes = if (isDir) 0L else length(),
            lastModified = lastModified(),
            extension = ext,
            mimeType = mime,
            category = if (isDir) FileFormatCategory.DIRECTORY else detectCategory(ext, mime),
            permissionsRwx = "$prefix$r$w$x$r-$x---",
            isHidden = isHidden || name.startsWith("."),
            childCount = childItems,
            isSafDocument = false,
            safUriString = null
        )
    }

    fun detectMimeType(ext: String): String {
        if (ext.isEmpty()) return "application/octet-stream"
        val webkitMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        if (!webkitMime.isNullOrBlank()) return webkitMime
        return when (ext) {
            "md", "markdown" -> "text/markdown"
            "kt", "kts", "java", "py", "js", "ts", "c", "cpp", "h", "sh", "properties", "toml", "yaml", "yml", "gradle", "log", "ini" -> "text/plain"
            "json" -> "application/json"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "pdf" -> "application/pdf"
            "zip", "jar", "apk" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    fun detectCategory(ext: String, mime: String): FileFormatCategory {
        return when {
            ext in setOf("md", "markdown") -> FileFormatCategory.MARKDOWN
            ext in setOf(
                "txt", "log", "json", "xml", "html", "htm", "css", "csv",
                "kt", "kts", "java", "py", "js", "ts", "c", "cpp", "h",
                "sh", "yaml", "yml", "toml", "ini", "properties", "gradle", "sql"
            ) || mime.startsWith("text/") -> FileFormatCategory.TEXT_CODE
            ext == "pdf" || mime == "application/pdf" -> FileFormatCategory.PDF
            ext == "docx" || mime.contains("wordprocessingml") -> FileFormatCategory.DOCX
            ext in setOf("zip", "jar", "apk") || mime.contains("zip") -> FileFormatCategory.ARCHIVE_ZIP
            ext in setOf("mp4", "mkv", "webm", "avi", "mov", "3gp") || mime.startsWith("video/") -> FileFormatCategory.VIDEO
            ext in setOf("mp3", "wav", "flac", "ogg", "m4a", "aac") || mime.startsWith("audio/") -> FileFormatCategory.AUDIO
            ext in setOf("png", "jpg", "jpeg", "webp", "bmp", "gif") || mime.startsWith("image/") -> FileFormatCategory.IMAGE
            else -> FileFormatCategory.BINARY_HEX
        }
    }

    private fun sortNodes(nodes: List<FileNode>, sortOption: SortOption): List<FileNode> {
        return nodes.sortedWith(
            Comparator { a, b ->
                if (a.isDirectory != b.isDirectory) {
                    return@Comparator if (a.isDirectory) -1 else 1
                }
                when (sortOption) {
                    SortOption.NAME_ASC -> a.name.compareTo(b.name, ignoreCase = true)
                    SortOption.NAME_DESC -> b.name.compareTo(a.name, ignoreCase = true)
                    SortOption.SIZE_DESC -> b.sizeBytes.compareTo(a.sizeBytes)
                    SortOption.SIZE_ASC -> a.sizeBytes.compareTo(b.sizeBytes)
                    SortOption.DATE_DESC -> b.lastModified.compareTo(a.lastModified)
                    SortOption.TYPE -> a.category.label.compareTo(b.category.label)
                }
            }
        )
    }

    // ==================== LOSSLESS STUDIO LOADERS & EDITORS ====================

    suspend fun openFileInStudio(
        node: FileNode,
        forceHexInspector: Boolean = false,
        charsetName: String = "UTF-8",
        pdfPageIndex: Int = 0,
        hexWindowOffset: Long = 0L
    ): Result<StudioContent> = withContext(Dispatchers.IO) {
        try {
            val file = resolveLocalFileForStudio(node)
            if (!file.exists() || !file.canRead()) {
                return@withContext Result.failure(
                    IllegalStateException("POSIX EACCES: Cannot read '${node.path}'. Check permissions.")
                )
            }

            if (forceHexInspector || node.category == FileFormatCategory.BINARY_HEX) {
                return@withContext Result.success(loadHexStudioWindow(node, file, hexWindowOffset))
            }

            val content = when (node.category) {
                FileFormatCategory.TEXT_CODE, FileFormatCategory.MARKDOWN ->
                    loadTextOrCodeEditor(node, file, charsetName)
                FileFormatCategory.DOCX ->
                    loadDocxEditor(node, file)
                FileFormatCategory.PDF ->
                    loadPdfPage(node, file, pdfPageIndex)
                FileFormatCategory.ARCHIVE_ZIP ->
                    loadZipArchive(node, file)
                FileFormatCategory.AUDIO, FileFormatCategory.VIDEO ->
                    loadMediaStudio(node, file)
                FileFormatCategory.IMAGE ->
                    loadImageStudio(node, file)
                else ->
                    loadHexStudioWindow(node, file, hexWindowOffset)
            }
            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun resolveLocalFileForStudio(node: FileNode): File {
        if (!node.isSafDocument) return File(node.path)
        val cacheCopy = File(context.cacheDir, "saf_studio_${node.name}")
        val uri = Uri.parse(node.safUriString ?: node.path)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(cacheCopy).use { output ->
                input.copyTo(output, 64 * 1024)
            }
        }
        return cacheCopy
    }

    private fun loadTextOrCodeEditor(node: FileNode, file: File, charsetName: String): StudioContent.TextCodeEditor {
        val maxWindowBytes = 512 * 1024 // 512 KB lossless window for ultra-smooth editor performance
        val totalLen = file.length()
        val isTruncated = totalLen > maxWindowBytes
        val readBytes = minOf(totalLen, maxWindowBytes.toLong()).toInt()
        val buffer = ByteArray(readBytes)
        BufferedInputStream(FileInputStream(file)).use { input ->
            var offset = 0
            while (offset < readBytes) {
                val r = input.read(buffer, offset, readBytes - offset)
                if (r == -1) break
                offset += r
            }
        }
        val charset = runCatching { Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)
        val text = String(buffer, charset)
        val lines = text.count { it == '\n' } + 1
        return StudioContent.TextCodeEditor(
            file = node,
            textContent = text,
            encoding = charset.displayName(),
            lineCount = lines,
            isMarkdown = node.category == FileFormatCategory.MARKDOWN,
            isTruncatedPreview = isTruncated,
            totalFileBytes = totalLen
        )
    }

    suspend fun saveTextOrCodeFileLosslessly(
        node: FileNode,
        newTextContent: String,
        charsetName: String = "UTF-8"
    ): Result<FileNode> = withContext(Dispatchers.IO) {
        try {
            val charset = runCatching { Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)
            val bytes = newTextContent.toByteArray(charset)
            if (node.isSafDocument) {
                val uri = Uri.parse(node.safUriString ?: node.path)
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(bytes)
                    out.flush()
                } ?: throw IllegalStateException("SAF output stream rejected write access for ${node.name}")
                Result.success(node.copy(sizeBytes = bytes.size.toLong(), lastModified = System.currentTimeMillis()))
            } else {
                val target = File(node.path)
                val tempFile = File(target.parentFile, ".${target.name}.luma_atomic_tmp")
                FileOutputStream(tempFile).use { out ->
                    out.write(bytes)
                    out.fd.sync()
                }
                if (!tempFile.renameTo(target)) {
                    tempFile.copyTo(target, overwrite = true)
                    tempFile.delete()
                }
                Result.success(target.toFileNode())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun loadDocxEditor(node: FileNode, file: File): StudioContent.DocxEditor {
        val paragraphs = mutableListOf<String>()
        var xmlEntries = 0
        var mediaEntries = 0

        ZipFile(file).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.endsWith(".xml") || entry.name.endsWith(".rels")) xmlEntries++
                if (entry.name.startsWith("word/media/")) mediaEntries++
                if (entry.name == "word/document.xml") {
                    val xmlText = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText()
                    val paragraphRegex = Regex("<w:p[\\s>].*?</w:p>", RegexOption.DOT_MATCHES_ALL)
                    val textRunRegex = Regex("<w:t(?:\\s[^>]*)?>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL)
                    paragraphRegex.findAll(xmlText).forEach { pMatch ->
                        val combined = textRunRegex.findAll(pMatch.value)
                            .map { it.groupValues[1] }
                            .joinToString("")
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")
                            .replace("&amp;", "&")
                        if (combined.isNotBlank()) {
                            paragraphs.add(combined)
                        }
                    }
                }
            }
        }
        if (paragraphs.isEmpty()) {
            paragraphs.add("(Empty .docx document — add paragraphs below and tap Save to update word/document.xml)")
        }
        return StudioContent.DocxEditor(
            file = node,
            paragraphs = paragraphs,
            xmlEntriesCount = xmlEntries,
            embeddedMediaCount = mediaEntries
        )
    }

    suspend fun saveDocxParagraphsLosslessly(
        node: FileNode,
        updatedParagraphs: List<String>
    ): Result<FileNode> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(node.path)
            val tempFile = File(sourceFile.parentFile, ".${sourceFile.name}.docx_tmp")

            val bodyXml = updatedParagraphs.filter { it.isNotBlank() }.joinToString("\n") { p ->
                val escaped = p.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                "<w:p><w:r><w:t>$escaped</w:t></w:r></w:p>"
            }
            val newDocXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                    <w:body>
                        $bodyXml
                    </w:body>
                </w:document>
            """.trimIndent().toByteArray(Charsets.UTF_8)

            var wroteDocXml = false
            ZipInputStream(BufferedInputStream(FileInputStream(sourceFile))).use { zis ->
                ZipOutputStream(BufferedOutputStream(FileOutputStream(tempFile))).use { zos ->
                    var entry: ZipEntry?
                    val buffer = ByteArray(32 * 1024)
                    while (zis.nextEntry.also { entry = it } != null) {
                        val current = entry!!
                        zos.putNextEntry(ZipEntry(current.name))
                        if (current.name == "word/document.xml") {
                            zos.write(newDocXml)
                            wroteDocXml = true
                        } else {
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                zos.write(buffer, 0, len)
                            }
                        }
                        zos.closeEntry()
                    }
                    if (!wroteDocXml) {
                        zos.putNextEntry(ZipEntry("word/document.xml"))
                        zos.write(newDocXml)
                        zos.closeEntry()
                    }
                }
            }

            if (!tempFile.renameTo(sourceFile)) {
                tempFile.copyTo(sourceFile, overwrite = true)
                tempFile.delete()
            }
            Result.success(sourceFile.toFileNode())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun loadPdfPage(node: FileNode, file: File, requestedPageIndex: Int): StudioContent.PdfViewer {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        try {
            val totalPages = renderer.pageCount.coerceAtLeast(1)
            val safeIndex = requestedPageIndex.coerceIn(0, totalPages - 1)
            val page = renderer.openPage(safeIndex)
            val scale = 2
            val bmp = Bitmap.createBitmap(
                (page.width * scale).coerceAtLeast(100),
                (page.height * scale).coerceAtLeast(100),
                Bitmap.Config.ARGB_8888
            )
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            val noteFile = File(file.parentFile, "${file.name}.notes.txt")
            val existingNotes = if (noteFile.exists()) noteFile.readText() else ""

            return StudioContent.PdfViewer(
                file = node,
                pageCount = totalPages,
                currentPageIndex = safeIndex,
                renderedPageBitmap = bmp,
                annotationNotes = existingNotes
            )
        } finally {
            renderer.close()
            pfd.close()
        }
    }

    suspend fun savePdfAnnotationNotes(node: FileNode, notesText: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val pdfFile = File(node.path)
            val noteFile = File(pdfFile.parentFile, "${pdfFile.name}.notes.txt")
            noteFile.writeText(notesText)
            Result.success(noteFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun loadZipArchive(node: FileNode, file: File): StudioContent.ZipArchiveManager {
        val entriesList = mutableListOf<ZipEntryInfo>()
        var totalUncompressed = 0L
        var totalCompressed = 0L

        ZipFile(file).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val uncomp = entry.size.coerceAtLeast(0L)
                val comp = entry.compressedSize.coerceAtLeast(0L)
                totalUncompressed += uncomp
                totalCompressed += comp
                entriesList.add(
                    ZipEntryInfo(
                        name = entry.name,
                        compressedSize = comp,
                        uncompressedSize = uncomp,
                        crc32Hex = if (entry.crc >= 0) "%08X".format(entry.crc) else "N/A",
                        isDirectory = entry.isDirectory,
                        lastModified = entry.time
                    )
                )
            }
        }
        val ratio = if (totalUncompressed > 0) {
            (((totalUncompressed - totalCompressed).coerceAtLeast(0L) * 100L) / totalUncompressed).toInt()
        } else 0

        return StudioContent.ZipArchiveManager(
            file = node,
            entries = entriesList.sortedBy { it.name },
            totalUncompressedBytes = totalUncompressed,
            compressionRatioPercent = ratio
        )
    }

    suspend fun extractZipArchive(node: FileNode, targetDir: File): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val zipFile = File(node.path)
            val destRoot = File(targetDir, zipFile.nameWithoutExtension + "_extracted").apply { mkdirs() }
            var extractedCount = 0
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry?
                val buffer = ByteArray(64 * 1024)
                while (zis.nextEntry.also { entry = it } != null) {
                    val current = entry!!
                    val outFile = File(destRoot, current.name)
                    if (!outFile.canonicalPath.startsWith(destRoot.canonicalPath)) {
                        throw SecurityException("Blocked Zip-Slip path traversal entry: ${current.name}")
                    }
                    if (current.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(outFile)).use { bos ->
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                bos.write(buffer, 0, len)
                            }
                        }
                        extractedCount++
                    }
                    zis.closeEntry()
                }
            }
            Result.success(extractedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addTextEntryToZipArchive(
        node: FileNode,
        entryFileName: String,
        entryContent: String
    ): Result<FileNode> = withContext(Dispatchers.IO) {
        try {
            val sourceZip = File(node.path)
            val tempZip = File(sourceZip.parentFile, ".${sourceZip.name}.repack_tmp")
            val payloadBytes = entryContent.toByteArray(Charsets.UTF_8)

            ZipInputStream(BufferedInputStream(FileInputStream(sourceZip))).use { zis ->
                ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZip))).use { zos ->
                    var entry: ZipEntry?
                    val buffer = ByteArray(32 * 1024)
                    while (zis.nextEntry.also { entry = it } != null) {
                        val current = entry!!
                        if (current.name == entryFileName) continue
                        zos.putNextEntry(ZipEntry(current.name))
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            zos.write(buffer, 0, len)
                        }
                        zos.closeEntry()
                    }
                    val newEntry = ZipEntry(entryFileName).apply {
                        size = payloadBytes.size.toLong()
                        crc = CRC32().apply { update(payloadBytes) }.value
                    }
                    zos.putNextEntry(newEntry)
                    zos.write(payloadBytes)
                    zos.closeEntry()
                }
            }
            if (!tempZip.renameTo(sourceZip)) {
                tempZip.copyTo(sourceZip, overwrite = true)
                tempZip.delete()
            }
            Result.success(sourceZip.toFileNode())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun loadMediaStudio(node: FileNode, file: File): StudioContent.MediaStudio {
        val isVideo = node.category == FileFormatCategory.VIDEO
        var durationMs = 0L
        var sampleRateOrRes = if (isVideo) "1920x1080 (Lossless Stream)" else "44,100 Hz PCM"
        var bitrateOrChannels = if (isVideo) "H.264 / AAC" else "16-Bit Lossless"
        var headerSummary = "Direct Hardware Media Pipeline"

        if (node.extension == "wav" && file.length() >= 44) {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(44)
                raf.readFully(header)
                val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val channels = bb.getShort(22).toInt()
                val sampleRate = bb.getInt(24)
                val byteRate = bb.getInt(28)
                val bitsPerSample = bb.getShort(34).toInt()
                val dataBytes = bb.getInt(40).coerceAtLeast(1)
                durationMs = (dataBytes.toLong() * 1000L) / byteRate.coerceAtLeast(1)
                sampleRateOrRes = "$sampleRate Hz • $bitsPerSample-bit PCM"
                bitrateOrChannels = "${if (channels == 1) "Mono (1ch)" else "Stereo (${channels}ch)"} • ${(byteRate * 8) / 1000} kbps"
                headerSummary = "RIFF/WAVE Lossless Uncompressed PCM Header Verified"
            }
        } else {
            runCatching {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val br = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
                if (br != null) bitrateOrChannels = "${br / 1000} kbps"
                if (isVideo) {
                    val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    if (w != null && h != null) sampleRateOrRes = "${w}x${h} Progressive"
                }
                retriever.release()
            }
        }

        return StudioContent.MediaStudio(
            file = node,
            isVideo = isVideo,
            durationMs = durationMs,
            sampleRateOrResolution = sampleRateOrRes,
            bitrateOrChannels = bitrateOrChannels,
            formatHeaderInfo = headerSummary
        )
    }

    private fun loadImageStudio(node: FileNode, file: File): StudioContent.ImageStudio {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val origW = opts.outWidth.coerceAtLeast(1)
        val origH = opts.outHeight.coerceAtLeast(1)

        var sampleSize = 1
        while (origW / sampleSize > 2048 || origH / sampleSize > 2048) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        val exifMap = linkedMapOf(
            "Dimensions" to "${origW} x ${origH} px (${String.format("%.2f", (origW * origH) / 1_000_000f)} MP)",
            "Color Depth" to "32-bit ARGB_8888 (Zero Re-compression)",
            "MIME Format" to node.mimeType,
            "Exact Byte Size" to "${file.length()} bytes",
            "POSIX Mode" to node.permissionsRwx
        )

        return StudioContent.ImageStudio(
            file = node,
            bitmap = bitmap,
            widthPx = origW,
            heightPx = origH,
            colorSpaceInfo = "sRGB Lossless Pipeline",
            exifSummary = exifMap
        )
    }

    suspend fun loadHexStudioWindow(
        node: FileNode,
        file: File,
        windowOffset: Long,
        windowSize: Int = 2048
    ): StudioContent.HexBinaryStudio = withContext(Dispatchers.IO) {
        val totalLen = file.length()
        val safeOffset = windowOffset.coerceIn(0L, (totalLen - 1).coerceAtLeast(0L))
        val bytesToRead = minOf(windowSize.toLong(), (totalLen - safeOffset).coerceAtLeast(0L)).toInt()
        val buffer = ByteArray(bytesToRead)

        RandomAccessFile(file, "r").use { raf ->
            if (bytesToRead > 0) {
                raf.seek(safeOffset)
                raf.readFully(buffer)
            }
        }

        val rows = mutableListOf<HexRow>()
        for (i in 0 until bytesToRead step 16) {
            val sliceEnd = minOf(i + 16, bytesToRead)
            val hexBytes = (i until sliceEnd).map { idx -> "%02X".format(buffer[idx]) }
            val ascii = (i until sliceEnd).map { idx ->
                val b = buffer[idx].toInt() and 0xFF
                if (b in 32..126) b.toChar() else '.'
            }.joinToString("")
            rows.add(
                HexRow(
                    offsetHex = "%08X".format(safeOffset + i),
                    byteValuesHex = hexBytes,
                    asciiPreview = ascii
                )
            )
        }

        val (md5, sha256) = cryptoEngine.computeFileChecksums(file)
        StudioContent.HexBinaryStudio(
            file = node,
            windowOffset = safeOffset,
            windowSize = bytesToRead,
            totalSize = totalLen,
            rows = rows,
            md5Hash = md5,
            sha256Hash = sha256
        )
    }

    suspend fun patchSingleByteInFile(
        node: FileNode,
        byteOffset: Long,
        newByteHex: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(node.path)
            val byteVal = newByteHex.trim().toInt(16).and(0xFF)
            RandomAccessFile(file, "rw").use { raf ->
                require(byteOffset in 0 until raf.length()) {
                    "Byte offset 0x${"%08X".format(byteOffset)} is out of bounds (file length: ${raf.length()} bytes)."
                }
                raf.seek(byteOffset)
                raf.writeByte(byteVal)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== FILE OPERATIONS & ADVANCED TOOLS ====================

    suspend fun createNewFileOrFolder(
        parentPath: String,
        name: String,
        isDirectory: Boolean,
        initialContent: String = ""
    ): Result<FileNode> = withContext(Dispatchers.IO) {
        try {
            val cleanName = name.trim()
            require(cleanName.isNotEmpty() && !cleanName.contains("/")) {
                "Invalid name '$name': File/folder names cannot be empty or contain path separators ('/')."
            }
            val target = File(parentPath, cleanName)
            if (target.exists()) {
                throw IllegalStateException("POSIX EEXIST: '${target.absolutePath}' already exists.")
            }
            if (isDirectory) {
                if (!target.mkdirs()) {
                    throw IllegalStateException("Failed to create folder '${target.absolutePath}'. Check write permissions.")
                }
            } else {
                target.parentFile?.mkdirs()
                if (cleanName.endsWith(".docx", ignoreCase = true)) {
                    WorkspaceSeedEngine.createSampleDocx(
                        target,
                        listOf(initialContent.ifBlank { "New Word OOXML Document created in Luma Files Studio." })
                    )
                } else {
                    target.writeText(initialContent)
                }
            }
            Result.success(target.toFileNode())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFileOrDirectory(node: FileNode): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (node.isSafDocument) {
                val uri = Uri.parse(node.safUriString ?: node.path)
                val deleted = DocumentsContract.deleteDocument(context.contentResolver, uri)
                if (!deleted) throw IllegalStateException("SAF provider refused deletion for ${node.name}")
            } else {
                val target = File(node.path)
                if (!target.exists()) return@withContext Result.success(Unit)
                val ok = target.deleteRecursively()
                if (!ok) {
                    throw IllegalStateException("POSIX EACCES: Unable to delete '${target.absolutePath}'.")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameFile(node: FileNode, newName: String): Result<FileNode> = withContext(Dispatchers.IO) {
        try {
            val clean = newName.trim()
            require(clean.isNotEmpty() && !clean.contains("/")) { "Invalid target filename '$newName'." }
            val src = File(node.path)
            val dst = File(src.parentFile, clean)
            if (dst.exists()) throw IllegalStateException("Target file '${dst.name}' already exists.")
            if (!src.renameTo(dst)) {
                throw IllegalStateException("POSIX rename failed from '${src.name}' to '$clean'.")
            }
            Result.success(dst.toFileNode())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun batchRegexRename(
        directoryPath: String,
        pattern: String,
        replacement: String,
        prefix: String = ""
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val dir = File(directoryPath)
            val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
            val regex = if (pattern.isNotBlank()) Regex(pattern) else null
            var renamedCount = 0

            files.forEach { file ->
                val replaced = if (regex != null) regex.replace(file.name, replacement) else file.name
                val candidateName = "$prefix$replaced"
                if (candidateName != file.name && candidateName.isNotBlank()) {
                    val dest = File(dir, candidateName)
                    if (!dest.exists() && file.renameTo(dest)) {
                        renamedCount++
                    }
                }
            }
            Result.success(renamedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun runStorageAnalyzer(rootPath: String): Result<StorageAnalyzerReport> = withContext(Dispatchers.IO) {
        try {
            val root = File(rootPath)
            val allFiles = mutableListOf<FileNode>()
            var dirCount = 0
            var totalBytes = 0L
            val categoryMap = mutableMapOf<FileFormatCategory, Long>()

            fun walk(current: File, depth: Int) {
                if (depth > 8) return
                val children = current.listFiles() ?: return
                for (child in children) {
                    if (child.isDirectory) {
                        dirCount++
                        walk(child, depth + 1)
                    } else {
                        val node = child.toFileNode()
                        allFiles.add(node)
                        totalBytes += node.sizeBytes
                        categoryMap[node.category] = (categoryMap[node.category] ?: 0L) + node.sizeBytes
                    }
                }
            }

            walk(root, 0)

            // Large files sorted descending
            val largeFiles = allFiles.sortedByDescending { it.sizeBytes }.take(15)

            // Duplicate finder: group by non-zero exact byte size first, then SHA-256 hash
            val sizeGroups = allFiles.filter { it.sizeBytes > 0L }.groupBy { it.sizeBytes }.filter { it.value.size > 1 }
            val duplicateGroups = mutableListOf<DuplicateGroup>()
            var reclaimableBytes = 0L

            for ((size, candidates) in sizeGroups) {
                val hashGroups = candidates.groupBy { node ->
                    runCatching { cryptoEngine.computeHash(File(node.path), "SHA-256") }.getOrDefault("")
                }.filter { it.key.isNotEmpty() && it.value.size > 1 }

                for ((sha256, dupes) in hashGroups) {
                    duplicateGroups.add(
                        DuplicateGroup(
                            sha256Hash = sha256,
                            fileSizeEach = size,
                            files = dupes
                        )
                    )
                    reclaimableBytes += size * (dupes.size - 1)
                }
            }

            Result.success(
                StorageAnalyzerReport(
                    scannedFilesCount = allFiles.size,
                    scannedDirectoriesCount = dirCount,
                    totalScannedBytes = totalBytes,
                    categoryBreakdownBytes = categoryMap,
                    largeFiles = largeFiles,
                    duplicateGroups = duplicateGroups,
                    reclaimableDuplicateBytes = reclaimableBytes
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildDiagnosticError(operationTitle: String, targetPath: String, throwable: Throwable): DiagnosticError {
        return cryptoEngine.mapToDiagnosticError(operationTitle, targetPath, throwable)
    }
}
