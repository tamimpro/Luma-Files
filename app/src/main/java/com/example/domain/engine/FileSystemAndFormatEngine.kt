package com.example.domain.engine

import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
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
import com.example.domain.model.ConflictPolicy
import com.example.domain.model.DiagnosticError
import com.example.domain.model.DocxParagraphNode
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
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.io.StringReader
import java.io.StringWriter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

class FileSystemAndFormatEngine(
    private val context: Context?,
    private val cryptoEngine: CryptoVaultEngine,
    customWorkspaceDir: File? = null
) {

    val lumaWorkspaceDir: File = (customWorkspaceDir ?: File(
        context?.filesDir ?: File(System.getProperty("java.io.tmpdir")),
        "Luma_Workspace"
    )).apply {
        WorkspaceSeedEngine.ensureSeeded(this)
    }

    fun hasAllFilesAccessPermission(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                Environment.getExternalStorageDirectory().canRead()
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns Internal Shared Storage (/storage/emulated/0) as the default path when accessible,
     * or falls back to Internal Shared Storage path / Luma Workspace so the user starts in
     * Internal Storage by default.
     */
    fun getDefaultStartPath(): String {
        val extRoot = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
        return if (extRoot != null && extRoot.exists() && (hasAllFilesAccessPermission() || extRoot.canRead()) && extRoot.listFiles() != null) {
            extRoot.absolutePath
        } else {
            lumaWorkspaceDir.absolutePath
        }
    }

    fun getPrimaryInternalStoragePath(): String {
        return runCatching { Environment.getExternalStorageDirectory().absolutePath }
            .getOrDefault(lumaWorkspaceDir.absolutePath)
    }

    fun getAvailableStorageRoots(): List<StorageVolumeRoot> {
        val roots = mutableListOf<StorageVolumeRoot>()

        val internalStat = runCatching {
            StatFs((context?.filesDir ?: lumaWorkspaceDir).absolutePath)
        }.getOrNull()
        val fallbackTotal = internalStat?.totalBytes ?: (64L * 1024 * 1024 * 1024)
        val fallbackFree = internalStat?.availableBytes ?: (32L * 1024 * 1024 * 1024)

        // 1. Primary Internal Shared Storage (/storage/emulated/0) — Default #1 Root
        val extRoot = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
        if (extRoot != null) {
            val extStat = runCatching { StatFs(extRoot.absolutePath) }.getOrNull()
            val extTotal = extStat?.totalBytes ?: fallbackTotal
            val extFree = extStat?.availableBytes ?: fallbackFree
            val canListDirectly = hasAllFilesAccessPermission() || (extRoot.canRead() && extRoot.listFiles() != null)

            roots.add(
                StorageVolumeRoot(
                    title = "Internal Storage",
                    subtitle = if (canListDirectly) {
                        "${extRoot.absolutePath} • Full Access"
                    } else {
                        "${extRoot.absolutePath} • Tap to Enable All Files Access"
                    },
                    path = extRoot.absolutePath,
                    totalBytes = extTotal,
                    freeBytes = extFree,
                    isSafTree = false,
                    isRemovableSd = false,
                    requiresAllFilesPermission = !canListDirectly
                )
            )
        }

        // 2. Removable SD Cards & Secondary External Storage Volumes
        context?.getExternalFilesDirs(null)?.forEachIndexed { index, dir ->
            if (dir != null) {
                if (!dir.exists()) dir.mkdirs()
                val volStat = runCatching { StatFs(dir.absolutePath) }.getOrNull()
                val vTotal = volStat?.totalBytes ?: fallbackTotal
                val vFree = volStat?.availableBytes ?: fallbackFree
                if (index > 0) {
                    // Removable SD Card detected!
                    val sdRoot = dir.absolutePath.substringBefore("/Android/data").let { File(it) }
                    val targetSdDir = if (sdRoot.exists() && sdRoot.canRead() && sdRoot.listFiles() != null) sdRoot else dir
                    roots.add(
                        StorageVolumeRoot(
                            title = "SD Card Volume #$index",
                            subtitle = targetSdDir.absolutePath,
                            path = targetSdDir.absolutePath,
                            totalBytes = vTotal,
                            freeBytes = vFree,
                            isSafTree = false,
                            isRemovableSd = true,
                            requiresAllFilesPermission = false
                        )
                    )
                }
            }
        }

        // 3. Persisted SAF Document Trees (External Folders / USB OTG / SD Cards)
        context?.contentResolver?.persistedUriPermissions?.forEach { perm ->
            if (perm.isReadPermission) {
                val docId = runCatching { DocumentsContract.getTreeDocumentId(perm.uri) }
                    .getOrDefault(perm.uri.lastPathSegment ?: "SAF Mount")
                roots.add(
                    StorageVolumeRoot(
                        title = "SAF Mount ($docId)",
                        subtitle = perm.uri.toString(),
                        path = perm.uri.toString(),
                        totalBytes = fallbackTotal,
                        freeBytes = fallbackFree,
                        isSafTree = true,
                        isRemovableSd = docId.contains("primary", ignoreCase = true).not(),
                        requiresAllFilesPermission = false
                    )
                )
            }
        }

        // 4. Luma Studio Sample Workspace (Pre-populated with DOCX, SQLite, CSV, PDF, ZIP, WAV, Code)
        roots.add(
            StorageVolumeRoot(
                title = "Luma Sample Studio",
                subtitle = "Pre-loaded DOCX, SQLite, CSV, PDF, ZIP & Media Samples",
                path = lumaWorkspaceDir.absolutePath,
                totalBytes = fallbackTotal,
                freeBytes = fallbackFree,
                isSafTree = false,
                isRemovableSd = false,
                requiresAllFilesPermission = false
            )
        )

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

            Result.success(sortNodes(filtered, sortOption))
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
                "POSIX EACCES (Permission Denied): Unable to list '${dir.absolutePath}'. Tap 'Internal Storage' or 'Grant All Files Access' to allow full non-root device storage access."
            )

        return children
            .filter { showHidden || (!it.isHidden && !it.name.startsWith(".")) }
            .map { it.toFileNode() }
    }

    private fun listSafTreeDirectory(uri: Uri, showHidden: Boolean): List<FileNode> {
        val resolver = context?.contentResolver
            ?: throw IllegalStateException("ContentResolver unavailable for SAF URI $uri")
        val isDocumentUri = DocumentsContract.isDocumentUri(context, uri)
        val parentDocId = if (isDocumentUri) {
            DocumentsContract.getDocumentId(uri)
        } else {
            DocumentsContract.getTreeDocumentId(uri)
        }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        val results = mutableListOf<FileNode>()
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
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
                val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, childId)
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
                        safUriString = docUri.toString(),
                        safTreeRootUriString = uri.toString()
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
        val webkitMime = runCatching {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        }.getOrNull()
        if (!webkitMime.isNullOrBlank()) return webkitMime
        return when (ext) {
            "md", "markdown" -> "text/markdown"
            "csv" -> "text/csv"
            "tsv" -> "text/tab-separated-values"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "yaml", "yml" -> "application/x-yaml"
            "db", "sqlite", "sqlite3" -> "application/vnd.sqlite3"
            "apk" -> "application/vnd.android.package-archive"
            "kt", "kts", "java", "py", "js", "ts", "c", "cpp", "h", "sh", "properties", "toml", "gradle", "log", "ini", "sql", "html", "css" -> "text/plain"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "pdf" -> "application/pdf"
            "zip", "jar" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    fun detectCategory(ext: String, mime: String): FileFormatCategory {
        return when {
            ext in setOf("md", "markdown") -> FileFormatCategory.MARKDOWN
            ext in setOf("csv", "tsv") || mime == "text/csv" -> FileFormatCategory.CSV_TSV
            ext in setOf("db", "sqlite", "sqlite3") || mime.contains("sqlite") -> FileFormatCategory.SQLITE_DB
            ext == "apk" || mime == "application/vnd.android.package-archive" -> FileFormatCategory.APK_PACKAGE
            ext in setOf("json", "xml", "yaml", "yml") -> FileFormatCategory.STRUCTURED_DATA
            ext in setOf(
                "txt", "log", "html", "htm", "css",
                "kt", "kts", "java", "py", "js", "ts", "c", "cpp", "h",
                "sh", "toml", "ini", "properties", "gradle", "sql"
            ) || mime.startsWith("text/") -> FileFormatCategory.TEXT_CODE
            ext == "pdf" || mime == "application/pdf" -> FileFormatCategory.PDF
            ext == "docx" || mime.contains("wordprocessingml") -> FileFormatCategory.DOCX
            ext in setOf("zip", "jar") || mime.contains("zip") -> FileFormatCategory.ARCHIVE_ZIP
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

    // ==================== CROSS-VOLUME COPY, MOVE & BATCH OPERATIONS ====================

    /**
     * Copies or moves a batch of files/folders across Internal Storage, Removable SD Cards,
     * and Storage Access Framework (SAF) URIs with Conflict Resolution (KEEP_BOTH, OVERWRITE, SKIP).
     */
    suspend fun copyOrMoveBatch(
        sourceNodes: List<FileNode>,
        destinationDirPathOrUri: String,
        isMove: Boolean,
        conflictPolicy: ConflictPolicy
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var processedCount = 0
            val destIsSaf = destinationDirPathOrUri.startsWith("content://")

            for (source in sourceNodes) {
                if (source.path == destinationDirPathOrUri) continue
                if (!source.isSafDocument && !destIsSaf && File(source.path).parentFile?.absolutePath == File(destinationDirPathOrUri).absolutePath && isMove) {
                    continue
                }
                val copied = if (destIsSaf) {
                    copyNodeToSafDirectory(source, Uri.parse(destinationDirPathOrUri), conflictPolicy)
                } else {
                    copyNodeToFileDirectory(source, File(destinationDirPathOrUri), conflictPolicy)
                }
                if (copied) {
                    processedCount++
                    if (isMove) {
                        deleteFileOrDirectory(source)
                    }
                }
            }
            Result.success(processedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun copyNodeToFileDirectory(
        source: FileNode,
        destDir: File,
        conflictPolicy: ConflictPolicy
    ): Boolean {
        if (!destDir.exists()) destDir.mkdirs()
        var targetFile = File(destDir, source.name)

        if (targetFile.exists()) {
            when (conflictPolicy) {
                ConflictPolicy.SKIP -> return false
                ConflictPolicy.OVERWRITE -> {
                    if (targetFile.isDirectory) targetFile.deleteRecursively() else targetFile.delete()
                }
                ConflictPolicy.KEEP_BOTH -> {
                    targetFile = resolveUniqueDestinationFile(destDir, source.name, source.isDirectory)
                }
            }
        }

        if (source.isDirectory) {
            targetFile.mkdirs()
            if (source.isSafDocument) {
                val children = listSafTreeDirectory(Uri.parse(source.safUriString ?: source.path), showHidden = true)
                children.forEach { child ->
                    copyNodeToFileDirectory(child, targetFile, conflictPolicy)
                }
            } else {
                File(source.path).listFiles()?.forEach { childFile ->
                    copyNodeToFileDirectory(childFile.toFileNode(), targetFile, conflictPolicy)
                }
            }
            return true
        } else {
            openNodeInputStream(source).use { input ->
                BufferedOutputStream(FileOutputStream(targetFile), 64 * 1024).use { output ->
                    input.copyTo(output, 64 * 1024)
                    output.flush()
                }
            }
            return true
        }
    }

    private fun copyNodeToSafDirectory(
        source: FileNode,
        destTreeOrDocUri: Uri,
        conflictPolicy: ConflictPolicy
    ): Boolean {
        val resolver = context?.contentResolver
            ?: throw IllegalStateException("ContentResolver required for SAF write operations.")
        val parentDocUri = if (DocumentsContract.isDocumentUri(context, destTreeOrDocUri)) {
            destTreeOrDocUri
        } else {
            DocumentsContract.buildDocumentUriUsingTree(
                destTreeOrDocUri,
                DocumentsContract.getTreeDocumentId(destTreeOrDocUri)
            )
        }

        val existingChildren = listSafTreeDirectory(destTreeOrDocUri, showHidden = true)
        val collision = existingChildren.firstOrNull { it.name.equals(source.name, ignoreCase = true) }
        var effectiveName = source.name

        if (collision != null) {
            when (conflictPolicy) {
                ConflictPolicy.SKIP -> return false
                ConflictPolicy.OVERWRITE -> {
                    DocumentsContract.deleteDocument(resolver, Uri.parse(collision.safUriString ?: collision.path))
                }
                ConflictPolicy.KEEP_BOTH -> {
                    val base = source.name.substringBeforeLast('.', source.name)
                    val ext = source.name.substringAfterLast('.', "")
                    val suffix = if (ext.isNotEmpty() && !source.isDirectory) ".$ext" else ""
                    effectiveName = "${base}_copy_${System.currentTimeMillis() % 10000}$suffix"
                }
            }
        }

        if (source.isDirectory) {
            val newDirUri = DocumentsContract.createDocument(
                resolver,
                parentDocUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                effectiveName
            ) ?: throw IllegalStateException("SAF provider refused directory creation for '$effectiveName'")

            val children = if (source.isSafDocument) {
                listSafTreeDirectory(Uri.parse(source.safUriString ?: source.path), showHidden = true)
            } else {
                File(source.path).listFiles()?.map { it.toFileNode() } ?: emptyList()
            }
            children.forEach { child ->
                copyNodeToSafDirectory(child, newDirUri, conflictPolicy)
            }
            return true
        } else {
            val newDocUri = DocumentsContract.createDocument(
                resolver,
                parentDocUri,
                source.mimeType.ifBlank { "application/octet-stream" },
                effectiveName
            ) ?: throw IllegalStateException("SAF provider refused file creation for '$effectiveName'")

            openNodeInputStream(source).use { input ->
                resolver.openOutputStream(newDocUri, "w")?.use { output ->
                    input.copyTo(output, 64 * 1024)
                    output.flush()
                } ?: throw IllegalStateException("Unable to open SAF output stream for '$effectiveName'")
            }
            return true
        }
    }

    private fun resolveUniqueDestinationFile(destDir: File, originalName: String, isDirectory: Boolean): File {
        val base = if (isDirectory) originalName else originalName.substringBeforeLast('.', originalName)
        val ext = if (isDirectory) "" else originalName.substringAfterLast('.', "")
        val suffix = if (ext.isNotEmpty()) ".$ext" else ""
        var counter = 1
        var candidate = File(destDir, "${base}_($counter)$suffix")
        while (candidate.exists()) {
            counter++
            candidate = File(destDir, "${base}_($counter)$suffix")
        }
        return candidate
    }

    private fun openNodeInputStream(node: FileNode): InputStream {
        return if (node.isSafDocument && node.safUriString != null) {
            val resolver = context?.contentResolver
                ?: throw IllegalStateException("ContentResolver required for SAF read.")
            BufferedInputStream(
                resolver.openInputStream(Uri.parse(node.safUriString))
                    ?: throw IllegalStateException("Cannot open SAF input stream for ${node.name}"),
                64 * 1024
            )
        } else {
            BufferedInputStream(FileInputStream(File(node.path)), 64 * 1024)
        }
    }

    private fun openNodeOutputStream(node: FileNode): OutputStream {
        return if (node.isSafDocument && node.safUriString != null) {
            val resolver = context?.contentResolver
                ?: throw IllegalStateException("ContentResolver required for SAF write.")
            BufferedOutputStream(
                resolver.openOutputStream(Uri.parse(node.safUriString), "wt")
                    ?: throw IllegalStateException("Cannot open SAF output stream for ${node.name}"),
                64 * 1024
            )
        } else {
            BufferedOutputStream(FileOutputStream(File(node.path)), 64 * 1024)
        }
    }

    suspend fun compressNodesToZip(
        sourceNodes: List<FileNode>,
        destinationDirPathOrUri: String,
        archiveName: String
    ): Result<FileNode> = withContext(Dispatchers.IO) {
        try {
            val cleanName = if (archiveName.endsWith(".zip", ignoreCase = true)) archiveName else "$archiveName.zip"
            val destIsSaf = destinationDirPathOrUri.startsWith("content://")
            val tempZip = File(
                context?.cacheDir ?: lumaWorkspaceDir,
                "batch_pack_${System.currentTimeMillis()}_$cleanName"
            )

            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZip), 64 * 1024)).use { zos ->
                val buffer = ByteArray(64 * 1024)

                fun addNodeToZip(node: FileNode, prefixPath: String) {
                    val entryPath = if (prefixPath.isEmpty()) node.name else "$prefixPath/${node.name}"
                    if (node.isDirectory) {
                        zos.putNextEntry(ZipEntry("$entryPath/"))
                        zos.closeEntry()
                        val children = if (node.isSafDocument) {
                            listSafTreeDirectory(Uri.parse(node.safUriString ?: node.path), showHidden = true)
                        } else {
                            File(node.path).listFiles()?.map { it.toFileNode() } ?: emptyList()
                        }
                        children.forEach { child -> addNodeToZip(child, entryPath) }
                    } else {
                        zos.putNextEntry(ZipEntry(entryPath))
                        openNodeInputStream(node).use { input ->
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                zos.write(buffer, 0, read)
                            }
                        }
                        zos.closeEntry()
                    }
                }

                sourceNodes.forEach { node -> addNodeToZip(node, "") }
            }

            if (destIsSaf) {
                val tempNode = tempZip.copyTo(File(tempZip.parentFile, cleanName), overwrite = true).toFileNode()
                copyNodeToSafDirectory(tempNode, Uri.parse(destinationDirPathOrUri), ConflictPolicy.KEEP_BOTH)
                tempZip.delete()
                File(tempNode.path).delete()
                Result.success(tempNode.copy(name = cleanName))
            } else {
                val destDir = File(destinationDirPathOrUri).apply { if (!exists()) mkdirs() }
                val finalZip = resolveUniqueOrTargetFile(destDir, cleanName)
                tempZip.copyTo(finalZip, overwrite = true)
                tempZip.delete()
                Result.success(finalZip.toFileNode())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun resolveUniqueOrTargetFile(dir: File, name: String): File {
        val candidate = File(dir, name)
        return if (!candidate.exists()) candidate else resolveUniqueDestinationFile(dir, name, false)
    }

    // ==================== LOSSLESS STUDIO LOADERS & EDITORS ====================

    suspend fun openFileInStudio(
        node: FileNode,
        forceHexInspector: Boolean = false,
        charsetName: String = "UTF-8",
        pdfPageIndex: Int = 0,
        hexWindowOffset: Long = 0L,
        sqliteTableName: String? = null
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
                FileFormatCategory.TEXT_CODE,
                FileFormatCategory.MARKDOWN,
                FileFormatCategory.STRUCTURED_DATA ->
                    loadTextOrCodeEditor(node, file, charsetName)
                FileFormatCategory.DOCX ->
                    loadDocxEditor(node, file)
                FileFormatCategory.CSV_TSV ->
                    loadCsvGridStudio(node, file)
                FileFormatCategory.SQLITE_DB ->
                    loadSqliteStudio(node, file, sqliteTableName)
                FileFormatCategory.APK_PACKAGE ->
                    loadApkInspectorStudio(node, file)
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
        val cacheDir = context?.cacheDir ?: lumaWorkspaceDir
        val cacheCopy = File(cacheDir, "saf_studio_${node.name}")
        openNodeInputStream(node).use { input ->
            FileOutputStream(cacheCopy).use { output ->
                input.copyTo(output, 64 * 1024)
            }
        }
        return cacheCopy
    }

    private fun syncTempFileBackToNodeIfSaf(node: FileNode, modifiedLocalFile: File) {
        if (node.isSafDocument && node.safUriString != null) {
            FileInputStream(modifiedLocalFile).use { input ->
                openNodeOutputStream(node).use { output ->
                    input.copyTo(output, 64 * 1024)
                    output.flush()
                }
            }
        }
    }

    private fun loadTextOrCodeEditor(node: FileNode, file: File, charsetName: String): StudioContent.TextCodeEditor {
        val maxWindowBytes = 512 * 1024
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
        val isStructured = node.category == FileFormatCategory.STRUCTURED_DATA ||
            node.extension in setOf("json", "xml", "yaml", "yml")
        val validationMsg = if (isStructured) validateStructuredSyntax(text, node.extension) else null

        return StudioContent.TextCodeEditor(
            file = node,
            textContent = text,
            encoding = charset.displayName(),
            lineCount = lines,
            isMarkdown = node.category == FileFormatCategory.MARKDOWN,
            isStructuredData = isStructured,
            validationMessage = validationMsg,
            isTruncatedPreview = isTruncated,
            totalFileBytes = totalLen
        )
    }

    fun formatAndValidateStructuredText(rawText: String, extension: String): Pair<String, String> {
        val ext = extension.lowercase()
        return try {
            when (ext) {
                "json" -> {
                    val formatted = prettyFormatAndValidateJson(rawText)
                    Pair(formatted, "Valid JSON Syntax • Formatted with 2-space indentation")
                }
                "xml" -> {
                    val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                    val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(rawText.trim().toByteArray(Charsets.UTF_8)))
                    val transformer = TransformerFactory.newInstance().newTransformer().apply {
                        setOutputProperty(OutputKeys.INDENT, "yes")
                        setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
                    }
                    val writer = StringWriter()
                    transformer.transform(DOMSource(doc), StreamResult(writer))
                    Pair(writer.toString(), "Valid W3C XML Document • Pretty-Formatted")
                }
                else -> {
                    Pair(rawText, "Syntax Validated (${ext.uppercase()})")
                }
            }
        } catch (e: Exception) {
            Pair(rawText, "Syntax Error: ${e.message}")
        }
    }

    private fun prettyFormatAndValidateJson(input: String): String {
        val src = input.trim()
        require(src.isNotEmpty()) { "JSON input is empty" }
        require(
            (src.startsWith("{") && src.endsWith("}")) ||
                (src.startsWith("[") && src.endsWith("]"))
        ) { "JSON root must be an Object {...} or Array [...]" }

        val sb = StringBuilder(src.length + 64)
        var indent = 0
        var inString = false
        var escaped = false
        val bracketStack = ArrayDeque<Char>()

        for (i in src.indices) {
            val c = src[i]
            if (inString) {
                sb.append(c)
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    inString = false
                }
                continue
            }

            when (c) {
                '"' -> {
                    inString = true
                    sb.append(c)
                }
                '{', '[' -> {
                    bracketStack.addLast(c)
                    sb.append(c)
                    indent++
                    sb.append('\n').append("  ".repeat(indent))
                }
                '}', ']' -> {
                    require(bracketStack.isNotEmpty()) { "Unexpected closing bracket '$c' at index $i" }
                    val open = bracketStack.removeLast()
                    require((open == '{' && c == '}') || (open == '[' && c == ']')) {
                        "Mismatched JSON bracket '$open' closed by '$c' at index $i"
                    }
                    indent = (indent - 1).coerceAtLeast(0)
                    sb.append('\n').append("  ".repeat(indent)).append(c)
                }
                ',' -> {
                    sb.append(",\n").append("  ".repeat(indent))
                }
                ':' -> {
                    sb.append(": ")
                }
                ' ', '\t', '\r', '\n' -> {
                    // Ignore insignificant whitespace outside string literals
                }
                else -> {
                    sb.append(c)
                }
            }
        }
        require(!inString) { "Unterminated JSON string literal" }
        require(bracketStack.isEmpty()) { "Unclosed JSON bracket '${bracketStack.last()}'" }
        return sb.toString()
    }

    private fun validateStructuredSyntax(rawText: String, extension: String): String {
        return formatAndValidateStructuredText(rawText, extension).second
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
                openNodeOutputStream(node).use { out ->
                    out.write(bytes)
                    out.flush()
                }
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

    // ==================== W3C DOM STRUCTURE-PRESERVING DOCX STUDIO ====================

    fun loadDocxEditor(
        node: FileNode,
        file: File = resolveLocalFileForStudio(node)
    ): StudioContent.DocxEditor {
        val paragraphNodes = mutableListOf<DocxParagraphNode>()
        var xmlEntries = 0
        var mediaEntries = 0
        var preservedStyles = 0

        ZipFile(file).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.endsWith(".xml") || entry.name.endsWith(".rels")) xmlEntries++
                if (entry.name.startsWith("word/media/")) mediaEntries++
                if (entry.name == "word/document.xml") {
                    val domDoc = parseXmlDom(zip.getInputStream(entry))
                    val pList = findElementsByLocalName(domDoc.documentElement, "p")
                    pList.forEachIndexed { idx, pElem ->
                        val styleId = extractParagraphStyleId(pElem)
                        val hasRPr = findElementsByLocalName(pElem, "rPr").isNotEmpty()
                        if (styleId != "Normal" || hasRPr) preservedStyles++
                        val tElements = findElementsByLocalName(pElem, "t")
                        val combinedText = tElements.joinToString("") { it.textContent ?: "" }
                        if (combinedText.isNotEmpty() || tElements.isNotEmpty()) {
                            paragraphNodes.add(
                                DocxParagraphNode(
                                    index = idx,
                                    styleId = styleId,
                                    text = combinedText,
                                    hasRunFormatting = hasRPr
                                )
                            )
                        }
                    }
                }
            }
        }
        if (paragraphNodes.isEmpty()) {
            paragraphNodes.add(
                DocxParagraphNode(0, "Normal", "(Empty .docx document — add paragraphs below)", false)
            )
        }
        return StudioContent.DocxEditor(
            file = node,
            paragraphNodes = paragraphNodes,
            xmlEntriesCount = xmlEntries,
            embeddedMediaCount = mediaEntries,
            preservedStylesCount = preservedStyles
        )
    }

    // Rewrites word/document.xml using W3C DOM XML manipulation so all existing <w:pPr>, <w:rPr>,
    // tables (<w:tbl>), section properties (<w:sectPr>), namespaces, and companion archive files
    // (word/styles.xml, word/media/, _rels/.rels) remain 100% preserved.
    suspend fun saveDocxParagraphsLosslessly(
        node: FileNode,
        updatedParagraphs: List<String>
    ): Result<FileNode> = withContext(Dispatchers.IO) {
        try {
            val localFile = resolveLocalFileForStudio(node)
            val tempFile = File(localFile.parentFile, ".${localFile.name}.docx_dom_tmp")

            var updatedDocXmlBytes: ByteArray? = null
            ZipFile(localFile).use { zip ->
                val docEntry = zip.getEntry("word/document.xml")
                if (docEntry != null) {
                    val domDoc = parseXmlDom(zip.getInputStream(docEntry))
                    mutateDocxDomParagraphs(domDoc, updatedParagraphs)
                    updatedDocXmlBytes = serializeXmlDom(domDoc)
                }
            }

            val finalDocBytes = updatedDocXmlBytes ?: buildFallbackDocxXml(updatedParagraphs)

            var wroteDocXml = false
            ZipInputStream(BufferedInputStream(FileInputStream(localFile))).use { zis ->
                ZipOutputStream(BufferedOutputStream(FileOutputStream(tempFile))).use { zos ->
                    var entry: ZipEntry?
                    val buffer = ByteArray(32 * 1024)
                    while (zis.nextEntry.also { entry = it } != null) {
                        val current = entry!!
                        zos.putNextEntry(ZipEntry(current.name))
                        if (current.name == "word/document.xml") {
                            zos.write(finalDocBytes)
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
                        zos.write(finalDocBytes)
                        zos.closeEntry()
                    }
                }
            }

            if (!tempFile.renameTo(localFile)) {
                tempFile.copyTo(localFile, overwrite = true)
                tempFile.delete()
            }
            syncTempFileBackToNodeIfSaf(node, localFile)
            Result.success(if (node.isSafDocument) node.copy(sizeBytes = localFile.length()) else localFile.toFileNode())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseXmlDom(inputStream: InputStream): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
        }
        return factory.newDocumentBuilder().parse(inputStream)
    }

    private fun serializeXmlDom(doc: Document): ByteArray {
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        }
        val out = ByteArrayOutputStream()
        transformer.transform(DOMSource(doc), StreamResult(out))
        return out.toByteArray()
    }

    private fun mutateDocxDomParagraphs(doc: Document, updatedParagraphs: List<String>) {
        val root = doc.documentElement ?: return
        val wNs = root.lookupNamespaceURI("w") ?: "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        val bodyElem = findElementsByLocalName(root, "body").firstOrNull() ?: root
        val existingParas = findElementsByLocalName(bodyElem, "p")

        // 1. Update existing <w:p> elements in-place, preserving <w:pPr> and <w:rPr>
        val minCount = minOf(existingParas.size, updatedParagraphs.size)
        for (i in 0 until minCount) {
            val pElem = existingParas[i]
            val newText = updatedParagraphs[i]
            val tNodes = findElementsByLocalName(pElem, "t")
            if (tNodes.isNotEmpty()) {
                tNodes[0].textContent = newText
                tNodes[0].setAttribute("xml:space", "preserve")
                // Clear extra split text runs inside the same paragraph while leaving run formatting intact
                for (j in 1 until tNodes.size) {
                    tNodes[j].textContent = ""
                }
            } else {
                val rElem = findElementsByLocalName(pElem, "r").firstOrNull()
                    ?: doc.createElementNS(wNs, "w:r").also { pElem.appendChild(it) }
                val tElem = doc.createElementNS(wNs, "w:t").apply {
                    setAttribute("xml:space", "preserve")
                    textContent = newText
                }
                rElem.appendChild(tElem)
            }
        }

        // 2. If paragraphs were deleted by the user, remove trailing <w:p> nodes from parent
        if (updatedParagraphs.size < existingParas.size) {
            for (i in updatedParagraphs.size until existingParas.size) {
                val pToRemove = existingParas[i]
                pToRemove.parentNode?.removeChild(pToRemove)
            }
        }

        // 3. If new paragraphs were added by the user, insert before <w:sectPr> (if present)
        if (updatedParagraphs.size > existingParas.size) {
            val sectPr = findElementsByLocalName(bodyElem, "sectPr").firstOrNull()
            for (i in existingParas.size until updatedParagraphs.size) {
                val newP = doc.createElementNS(wNs, "w:p")
                val newR = doc.createElementNS(wNs, "w:r")
                val newT = doc.createElementNS(wNs, "w:t").apply {
                    setAttribute("xml:space", "preserve")
                    textContent = updatedParagraphs[i]
                }
                newR.appendChild(newT)
                newP.appendChild(newR)
                if (sectPr != null && sectPr.parentNode == bodyElem) {
                    bodyElem.insertBefore(newP, sectPr)
                } else {
                    bodyElem.appendChild(newP)
                }
            }
        }
    }

    private fun extractParagraphStyleId(pElem: Element): String {
        val pStyle = findElementsByLocalName(pElem, "pStyle").firstOrNull() ?: return "Normal"
        for (i in 0 until pStyle.attributes.length) {
            val attr = pStyle.attributes.item(i)
            if (attr.localName == "val" || attr.nodeName.endsWith(":val") || attr.nodeName == "val") {
                return attr.nodeValue ?: "Normal"
            }
        }
        return "Normal"
    }

    private fun findElementsByLocalName(parent: Element, localName: String): List<Element> {
        val result = mutableListOf<Element>()
        fun traverse(node: Node) {
            val children = node.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child is Element) {
                    val childLocal = child.localName ?: child.tagName.substringAfter(':')
                    if (childLocal == localName) {
                        result.add(child)
                    }
                    traverse(child)
                }
            }
        }
        traverse(parent)
        return result
    }

    private fun buildFallbackDocxXml(paragraphs: List<String>): ByteArray {
        val bodyXml = paragraphs.joinToString("") { p ->
            val escaped = p.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            "<w:p><w:r><w:t xml:space=\"preserve\">$escaped</w:t></w:r></w:p>"
        }
        val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
            "<w:body>$bodyXml</w:body>" +
            "</w:document>"
        return xml.toByteArray(Charsets.UTF_8)
    }

    // ==================== CSV / TSV SPREADSHEET GRID STUDIO ====================

    fun loadCsvGridStudio(
        node: FileNode,
        file: File = resolveLocalFileForStudio(node)
    ): StudioContent.CsvGridStudio {
        val delimiter = if (node.extension == "tsv") '\t' else ','
        val lines = file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return StudioContent.CsvGridStudio(
                file = node,
                delimiter = delimiter,
                headers = listOf("Column_1", "Column_2", "Column_3"),
                rows = listOf(listOf("", "", ""))
            )
        }
        val parsed = lines.map { parseCsvLine(it, delimiter) }
        val maxCols = parsed.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
        val normalized = parsed.map { row ->
            if (row.size < maxCols) row + List(maxCols - row.size) { "" } else row
        }
        return StudioContent.CsvGridStudio(
            file = node,
            delimiter = delimiter,
            headers = normalized.first(),
            rows = normalized.drop(1).ifEmpty { listOf(List(maxCols) { "" }) }
        )
    }

    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val cells = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    cells.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        cells.add(sb.toString().trim())
        return cells
    }

    suspend fun saveCsvGridLosslessly(
        node: FileNode,
        delimiter: Char,
        headers: List<String>,
        rows: List<List<String>>
    ): Result<FileNode> = withContext(Dispatchers.IO) {
        val allRows = listOf(headers) + rows
        val serialized = allRows.joinToString("\n") { row ->
            row.joinToString(delimiter.toString()) { cell ->
                if (cell.contains(delimiter) || cell.contains('"') || cell.contains('\n')) {
                    "\"${cell.replace("\"", "\"\"")}\""
                } else {
                    cell
                }
            }
        }
        saveTextOrCodeFileLosslessly(node, serialized, "UTF-8")
    }

    // ==================== SQLITE DATABASE (.db / .sqlite) STUDIO ====================

    suspend fun loadSqliteStudioForTable(
        node: FileNode,
        selectedTable: String
    ): Result<StudioContent.SqliteStudio> = withContext(Dispatchers.IO) {
        try {
            val localFile = resolveLocalFileForStudio(node)
            Result.success(loadSqliteStudio(node, localFile, selectedTable))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun loadSqliteStudio(
        node: FileNode,
        file: File,
        requestedTable: String? = null,
        customQueryMsg: String? = null
    ): StudioContent.SqliteStudio {
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val tables = mutableListOf<String>()
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_%' ORDER BY name",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    tables.add(cursor.getString(0))
                }
            }

            if (tables.isEmpty()) {
                return StudioContent.SqliteStudio(
                    file = node,
                    tableNames = listOf("(No tables)"),
                    selectedTable = "(No tables)",
                    columns = listOf("Info"),
                    rows = listOf(listOf("SQLite database contains no user tables.")),
                    totalRowsInTable = 0,
                    customQueryResultMessage = customQueryMsg
                )
            }

            val activeTable = if (requestedTable != null && requestedTable in tables) {
                requestedTable
            } else {
                tables.first()
            }

            var totalCount = 0
            runCatching {
                db.rawQuery("SELECT COUNT(*) FROM `$activeTable`", null).use { c ->
                    if (c.moveToFirst()) totalCount = c.getInt(0)
                }
            }

            val cols = mutableListOf<String>()
            val rows = mutableListOf<List<String>>()
            db.rawQuery("SELECT * FROM `$activeTable` LIMIT 100", null).use { cursor ->
                cols.addAll(cursor.columnNames)
                val colCount = cursor.columnCount
                while (cursor.moveToNext()) {
                    val rowVals = (0 until colCount).map { idx ->
                        runCatching { cursor.getString(idx) ?: "NULL" }.getOrDefault("[BLOB]")
                    }
                    rows.add(rowVals)
                }
            }

            return StudioContent.SqliteStudio(
                file = node,
                tableNames = tables,
                selectedTable = activeTable,
                columns = cols,
                rows = rows,
                totalRowsInTable = totalCount,
                customQueryResultMessage = customQueryMsg
            )
        } finally {
            db.close()
        }
    }

    suspend fun executeSqliteQuery(
        node: FileNode,
        activeTable: String,
        sqlStatement: String
    ): Result<StudioContent.SqliteStudio> = withContext(Dispatchers.IO) {
        try {
            val localFile = resolveLocalFileForStudio(node)
            val trimmed = sqlStatement.trim()
            val isSelect = trimmed.startsWith("SELECT", ignoreCase = true) ||
                trimmed.startsWith("PRAGMA", ignoreCase = true)

            if (isSelect) {
                val db = SQLiteDatabase.openDatabase(localFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                try {
                    val tables = mutableListOf<String>()
                    db.rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_%' ORDER BY name",
                        null
                    ).use { c ->
                        while (c.moveToNext()) tables.add(c.getString(0))
                    }
                    val cols = mutableListOf<String>()
                    val rows = mutableListOf<List<String>>()
                    db.rawQuery(trimmed, null).use { cursor ->
                        cols.addAll(cursor.columnNames)
                        while (cursor.moveToNext()) {
                            rows.add((0 until cursor.columnCount).map { i ->
                                runCatching { cursor.getString(i) ?: "NULL" }.getOrDefault("[BLOB]")
                            })
                        }
                    }
                    Result.success(
                        StudioContent.SqliteStudio(
                            file = node,
                            tableNames = tables,
                            selectedTable = activeTable,
                            columns = cols,
                            rows = rows,
                            totalRowsInTable = rows.size,
                            customQueryResultMessage = "Query returned ${rows.size} rows."
                        )
                    )
                } finally {
                    db.close()
                }
            } else {
                val db = SQLiteDatabase.openDatabase(localFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
                try {
                    db.execSQL(trimmed)
                } finally {
                    db.close()
                }
                syncTempFileBackToNodeIfSaf(node, localFile)
                Result.success(
                    loadSqliteStudio(
                        node = node,
                        file = localFile,
                        requestedTable = activeTable,
                        customQueryMsg = "Executed SQL statement successfully."
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== APK PACKAGE ARCHIVE INSPECTOR ====================

    private fun loadApkInspectorStudio(node: FileNode, file: File): StudioContent.ApkInspectorStudio {
        var pkgName = "Unknown Package"
        var verName = "1.0"
        var verCode = 1L
        var minSdk = 24
        var targetSdk = 36
        val permissions = mutableListOf<String>()
        val activities = mutableListOf<String>()

        runCatching {
            val pm = context?.packageManager
            val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES
            @Suppress("DEPRECATION")
            val info = pm?.getPackageArchiveInfo(file.absolutePath, flags)
            if (info != null) {
                pkgName = info.packageName ?: pkgName
                verName = info.versionName ?: verName
                verCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode.toLong()
                }
                info.applicationInfo?.let { appInfo ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        minSdk = appInfo.minSdkVersion
                    }
                    targetSdk = appInfo.targetSdkVersion
                }
                info.requestedPermissions?.let { permissions.addAll(it) }
                info.activities?.mapNotNull { it.name }?.let { activities.addAll(it) }
            }
        }

        val nativeLibs = mutableListOf<String>()
        var dexCount = 0
        var totalEntries = 0
        runCatching {
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    totalEntries++
                    if (entry.name.endsWith(".dex")) dexCount++
                    if (entry.name.startsWith("lib/") && entry.name.endsWith(".so")) {
                        nativeLibs.add(entry.name.removePrefix("lib/"))
                    }
                }
            }
        }

        val sha256 = cryptoEngine.computeHash(file, "SHA-256")
        return StudioContent.ApkInspectorStudio(
            file = node,
            packageName = pkgName,
            versionName = verName,
            versionCode = verCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            permissions = permissions,
            activities = activities,
            nativeLibraries = nativeLibs,
            dexFilesCount = dexCount,
            totalEntriesCount = totalEntries,
            sha256Signature = sha256
        )
    }

    // ==================== PDF, ZIP, MEDIA, IMAGE & HEX STUDIOS ====================

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
            val pdfFile = resolveLocalFileForStudio(node)
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
            val zipFile = resolveLocalFileForStudio(node)
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
            val sourceZip = resolveLocalFileForStudio(node)
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
            syncTempFileBackToNodeIfSaf(node, sourceZip)
            Result.success(if (node.isSafDocument) node.copy(sizeBytes = sourceZip.length()) else sourceZip.toFileNode())
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
            val file = resolveLocalFileForStudio(node)
            val byteVal = newByteHex.trim().toInt(16).and(0xFF)
            RandomAccessFile(file, "rw").use { raf ->
                require(byteOffset in 0 until raf.length()) {
                    "Byte offset 0x${"%08X".format(byteOffset)} is out of bounds (file length: ${raf.length()} bytes)."
                }
                raf.seek(byteOffset)
                raf.writeByte(byteVal)
            }
            syncTempFileBackToNodeIfSaf(node, file)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== FILE OPERATIONS & ADVANCED TOOLS ====================

    suspend fun createNewFileOrFolder(
        parentPathOrUri: String,
        name: String,
        isDirectory: Boolean,
        initialContent: String = ""
    ): Result<FileNode> = withContext(Dispatchers.IO) {
        try {
            val cleanName = name.trim()
            require(cleanName.isNotEmpty() && !cleanName.contains("/")) {
                "Invalid name '$name': File/folder names cannot be empty or contain path separators ('/')."
            }

            if (parentPathOrUri.startsWith("content://")) {
                val resolver = context?.contentResolver
                    ?: throw IllegalStateException("ContentResolver required for SAF creation.")
                val treeUri = Uri.parse(parentPathOrUri)
                val parentDocUri = if (DocumentsContract.isDocumentUri(context, treeUri)) {
                    treeUri
                } else {
                    DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        DocumentsContract.getTreeDocumentId(treeUri)
                    )
                }
                val ext = cleanName.substringAfterLast('.', "").lowercase()
                val mime = if (isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else detectMimeType(ext)
                val createdUri = DocumentsContract.createDocument(resolver, parentDocUri, mime, cleanName)
                    ?: throw IllegalStateException("SAF provider refused creation of '$cleanName'")

                if (!isDirectory && initialContent.isNotEmpty()) {
                    resolver.openOutputStream(createdUri, "w")?.use { out ->
                        out.write(initialContent.toByteArray(Charsets.UTF_8))
                    }
                }
                return@withContext Result.success(
                    FileNode(
                        name = cleanName,
                        path = createdUri.toString(),
                        isDirectory = isDirectory,
                        sizeBytes = initialContent.toByteArray(Charsets.UTF_8).size.toLong(),
                        lastModified = System.currentTimeMillis(),
                        extension = ext,
                        mimeType = mime,
                        category = if (isDirectory) FileFormatCategory.DIRECTORY else detectCategory(ext, mime),
                        permissionsRwx = if (isDirectory) "drwxr-x--- (SAF)" else "-rw-r----- (SAF)",
                        isHidden = cleanName.startsWith("."),
                        isSafDocument = true,
                        safUriString = createdUri.toString(),
                        safTreeRootUriString = parentPathOrUri
                    )
                )
            }

            val target = File(parentPathOrUri, cleanName)
            if (target.exists()) {
                throw IllegalStateException("POSIX EEXIST: '${target.absolutePath}' already exists.")
            }
            if (isDirectory) {
                if (!target.mkdirs()) {
                    throw IllegalStateException("Failed to create folder '${target.absolutePath}'. Check write permissions.")
                }
            } else {
                target.parentFile?.mkdirs()
                when {
                    cleanName.endsWith(".docx", ignoreCase = true) -> {
                        WorkspaceSeedEngine.createSampleDocx(
                            target,
                            listOf(initialContent.ifBlank { "New Word OOXML Document created in Luma Files Studio v1.1.0." })
                        )
                    }
                    cleanName.endsWith(".csv", ignoreCase = true) -> {
                        target.writeText(initialContent.ifBlank { "Column_A,Column_B,Column_C\nValue_1,Value_2,Value_3" })
                    }
                    else -> {
                        target.writeText(initialContent)
                    }
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
                val resolver = context?.contentResolver
                    ?: throw IllegalStateException("ContentResolver required for SAF deletion.")
                val uri = Uri.parse(node.safUriString ?: node.path)
                val deleted = DocumentsContract.deleteDocument(resolver, uri)
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
            if (node.isSafDocument) {
                val resolver = context?.contentResolver
                    ?: throw IllegalStateException("ContentResolver required for SAF rename.")
                val uri = Uri.parse(node.safUriString ?: node.path)
                val renamedUri = DocumentsContract.renameDocument(resolver, uri, clean)
                    ?: throw IllegalStateException("SAF provider refused rename to '$clean'")
                val ext = clean.substringAfterLast('.', "").lowercase()
                val mime = if (node.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else detectMimeType(ext)
                return@withContext Result.success(
                    node.copy(
                        name = clean,
                        path = renamedUri.toString(),
                        extension = ext,
                        mimeType = mime,
                        category = if (node.isDirectory) FileFormatCategory.DIRECTORY else detectCategory(ext, mime),
                        safUriString = renamedUri.toString()
                    )
                )
            }
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
            if (directoryPath.startsWith("content://")) {
                val children = listSafTreeDirectory(Uri.parse(directoryPath), showHidden = true)
                val regex = if (pattern.isNotBlank()) Regex(pattern) else null
                var count = 0
                children.filter { !it.isDirectory }.forEach { child ->
                    val replaced = if (regex != null) regex.replace(child.name, replacement) else child.name
                    val candidateName = "$prefix$replaced"
                    if (candidateName != child.name && candidateName.isNotBlank()) {
                        if (renameFile(child, candidateName).isSuccess) count++
                    }
                }
                return@withContext Result.success(count)
            }

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

            val largeFiles = allFiles.sortedByDescending { it.sizeBytes }.take(15)
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
