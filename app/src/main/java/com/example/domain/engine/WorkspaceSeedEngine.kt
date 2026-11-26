package com.example.domain.engine

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.sin

object WorkspaceSeedEngine {

    fun ensureSeeded(workspaceRoot: File) {
        if (!workspaceRoot.exists()) workspaceRoot.mkdirs()
        val marker = File(workspaceRoot, ".luma_workspace_initialized_v11")
        if (marker.exists()) return

        val docsDir = File(workspaceRoot, "Documents").apply { mkdirs() }
        val codeDir = File(workspaceRoot, "Code_And_Configs").apply { mkdirs() }
        val dataDir = File(workspaceRoot, "Databases_And_Grids").apply { mkdirs() }
        val archivesDir = File(workspaceRoot, "Archives").apply { mkdirs() }
        val mediaDir = File(workspaceRoot, "Lossless_Media").apply { mkdirs() }
        val downloadsDir = File(workspaceRoot, "Downloads").apply { mkdirs() }

        // 1. Markdown Guide
        File(docsDir, "Welcome_to_Luma_Files_v1_1.md").writeText(
            """
            # Luma Files v1.1.0 — Lossless Studio & Maximum Access Engine
            
            Welcome to **Luma Files v1.1.0** (installed as **File Manager**).
            
            ## What's New in v1.1.0
            - **Internal Storage Default & Remembered Onboarding**: Opens directly into Internal Shared Storage and remembers your permission decisions.
            - **Cross-Volume Copy, Move & Batch Operations**: Select multiple files across Internal Storage, SD cards, and SAF folders to Copy, Move, Compress to ZIP, or Batch Encrypt with Conflict Resolution (`Keep Both`, `Overwrite`, `Skip`).
            - **Hardened 210k-PBKDF2 AES-256-GCM Secure Vault**: Isolated in `noBackupFilesDir` with UUID filename obfuscation (`.lvault`), explicit cloud/ADB backup exclusions, and zero-overwrite file shredding.
            - **DOM Structure-Preserving Word (.docx) Studio**: Modifies `<w:t>` nodes in-place via W3C DOM XML parsing so `<w:pPr>`, `<w:rPr>`, tables, styles, and media stay 100% intact.
            - **New Power-User Format Studios**: Built-in **SQLite Database Browser (`.db`, `.sqlite`)**, **CSV/TSV Spreadsheet Grid Editor**, **APK Package Inspector (`.apk`)**, and **JSON/XML Pretty-Formatter & Validator**.
            """.trimIndent()
        )

        // 2. Plain Text & Duplicate file for Duplicate Analyzer verification
        val specText = """
            LUMA FILES ENGINE TELEMETRY SPECIFICATION (REV 1.1.0)
            =====================================================
            Stream Buffer Window : 65,536 Bytes (64 KB)
            Vault Cipher Suite   : AES/GCM/NoPadding (256-Bit Key, 128-Bit Auth Tag)
            Key Derivation       : PBKDF2WithHmacSHA256 (210,000 Iterations, 256-Bit Salt)
            Vault Isolation      : noBackupFilesDir + Opaque UUID (.lvault) + Zero-Shredding
            Download Concurrency : Up to 4 Parallel HTTP 206 Content-Range Verified Workers
        """.trimIndent()
        File(docsDir, "Engine_Telemetry_Notes.txt").writeText(specText)
        File(downloadsDir, "Engine_Telemetry_Notes_Copy.txt").writeText(specText)

        // 3. Kotlin & JSON Code samples
        File(codeDir, "LumaStoragePipeline.kt").writeText(
            """
            package com.luma.files.pipeline

            import java.io.RandomAccessFile

            class LosslessByteStream(private val path: String) {
                fun readWindow(offset: Long, length: Int): ByteArray {
                    RandomAccessFile(path, "r").use { raf ->
                        val safeLen = minOf(length.toLong(), (raf.length() - offset).coerceAtLeast(0L)).toInt()
                        val buffer = ByteArray(safeLen)
                        raf.seek(offset)
                        raf.readFully(buffer)
                        return buffer
                    }
                }
            }
            """.trimIndent()
        )

        File(codeDir, "LumaManifestConfig.json").writeText(
            """
            {
              "applicationName": "File Manager",
              "originalBrandName": "Luma Files",
              "version": "1.1.0",
              "defaultRoot": "INTERNAL_SHARED_STORAGE",
              "vaultSecurity": {
                "cipher": "AES-256-GCM",
                "kdf": "PBKDF2WithHmacSHA256",
                "iterations": 210000,
                "storageDomain": "noBackupFilesDir",
                "uuidObfuscation": true,
                "zeroOverwriteShredding": true
              }
            }
            """.trimIndent()
        )

        // 4. CSV Spreadsheet Sample
        createSampleCsv(File(dataDir, "Storage_Benchmark_Metrics.csv"))

        // 5. Real SQLite Database Sample (.sqlite)
        runCatching {
            createSampleSqliteDatabase(File(dataDir, "Luma_System_Telemetry.sqlite"))
        }

        // 6. Hidden dotfile
        File(workspaceRoot, ".luma_hidden_config").writeText(
            """
            # Hidden Dotfile Configuration (v1.1.0)
            SHOW_POSIX_PERMISSIONS=true
            ENABLE_RAW_HEX_PATCHING=true
            VERIFY_ARCHIVE_CRC32=true
            STRICT_HTTP_206_RANGE_CHECK=true
            """.trimIndent()
        )
        marker.writeText("INITIALIZED=true\nVERSION=1.1.0\nTIMESTAMP=${System.currentTimeMillis()}\n")

        // 7. Rich Styled OOXML .docx Document (with <w:pPr>, <w:rPr>, styles, and table metadata)
        createSampleDocx(
            File(docsDir, "Project_Specification.docx"),
            listOf(
                "Luma Files v1.1.0 — Structure-Preserving OOXML (.docx) Studio",
                "This document contains styled paragraphs (<w:pPr>), run formatting (<w:rPr>), and relationship parts.",
                "When edited in Luma Files Studio, W3C DOM XML manipulation updates <w:t> text nodes in place without stripping paragraph styles, bold/color runs, or embedded objects.",
                "Cross-volume Copy, Move, and Batch operations work across Internal Shared Storage, SD Cards, and SAF folders."
            )
        )

        // 8. Real Multi-Page PDF Document
        runCatching { createSamplePdf(File(docsDir, "Architecture_Blueprint.pdf")) }

        // 9. Real ZIP Archive
        createSampleZip(File(archivesDir, "Release_Bundle_v1_1.zip"))

        // 10. Real Lossless PCM WAV Audio File
        createSampleWav(File(mediaDir, "HiRes_Audio_Sample.wav"))

        // 11. Real High-Resolution PNG Test Card
        runCatching { createSamplePng(File(mediaDir, "Lossless_Calibration_Card.png")) }
    }

    fun createSampleCsv(targetFile: File) {
        targetFile.writeText(
            """
            Module,Throughput_MBps,Latency_ms,Integrity_Status,Notes
            Internal_Storage_Stream,485.4,1.2,VERIFIED,Direct FileChannel Zero-Copy
            SAF_DocumentTree_Bridge,210.8,3.8,VERIFIED,Persisted URI Descriptor
            AES256_GCM_Vault_Cipher,312.0,2.1,VERIFIED,210k PBKDF2 + 128-bit Auth Tag
            MultiThread_Range_Engine,142.6,8.4,VERIFIED,4x RFC-7233 HTTP 206 Workers
            """.trimIndent()
        )
    }

    fun createSampleDocx(targetFile: File, paragraphs: List<String>) {
        ZipOutputStream(FileOutputStream(targetFile)).use { zos ->
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
                </Types>
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("word/styles.xml"))
            zos.write(
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                    <w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/></w:style>
                    <w:style w:type="paragraph" w:styleId="Subtitle"><w:name w:val="Subtitle"/></w:style>
                    <w:style w:type="paragraph" w:styleId="BodyText"><w:name w:val="Body Text"/></w:style>
                </w:styles>
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("word/document.xml"))
            val bodyXml = paragraphs.mapIndexed { idx, p ->
                val escaped = p.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                val styleId = when (idx) {
                    0 -> "Heading1"
                    1 -> "Subtitle"
                    else -> "BodyText"
                }
                val runProps = if (idx == 0) "<w:rPr><w:b/><w:color w:val=\"0284C7\"/></w:rPr>" else "<w:rPr><w:lang w:val=\"en-US\"/></w:rPr>"
                "<w:p><w:pPr><w:pStyle w:val=\"$styleId\"/></w:pPr><w:r>$runProps<w:t xml:space=\"preserve\">$escaped</w:t></w:r></w:p>"
            }.joinToString("")

            val documentXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
                "<w:body>" +
                bodyXml +
                "<w:sectPr><w:pgSz w:w=\"12240\" w:h=\"15840\"/></w:sectPr>" +
                "</w:body>" +
                "</w:document>"

            zos.write(documentXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
    }

    private fun createSampleSqliteDatabase(targetFile: File) {
        if (targetFile.exists()) targetFile.delete()
        val db = SQLiteDatabase.openOrCreateDatabase(targetFile, null)
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS storage_audit (id INTEGER PRIMARY KEY AUTOINCREMENT, module TEXT, status TEXT, latency_ms INTEGER)"
            )
            db.execSQL("INSERT INTO storage_audit (module, status, latency_ms) VALUES ('InternalSharedStorage', 'ONLINE_READ_WRITE', 1)")
            db.execSQL("INSERT INTO storage_audit (module, status, latency_ms) VALUES ('AES256_NoBackup_Vault', 'LOCKED_PBKDF2_210K', 2)")
            db.execSQL("INSERT INTO storage_audit (module, status, latency_ms) VALUES ('SAF_DocumentTree_Engine', 'MOUNT_READY', 3)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS format_handlers (format_ext TEXT PRIMARY KEY, engine_name TEXT, lossless INTEGER)"
            )
            db.execSQL("INSERT INTO format_handlers VALUES ('docx', 'W3C_DOM_OOXML_Engine', 1)")
            db.execSQL("INSERT INTO format_handlers VALUES ('sqlite', 'Native_SQLite_Cursor_Grid', 1)")
            db.execSQL("INSERT INTO format_handlers VALUES ('csv', 'Interactive_Spreadsheet_Grid', 1)")
            db.execSQL("INSERT INTO format_handlers VALUES ('apk', 'PackageArchive_Manifest_Inspector', 1)")
        } finally {
            db.close()
        }
    }

    private fun createSamplePdf(targetFile: File) {
        val pdfDocument = PdfDocument()
        try {
            val pages = listOf(
                "Page 1: Luma Files v1.1.0 Architecture & Cross-Volume Engine" to
                    listOf(
                        "• Internal Shared Storage Default + Remembered Permission Onboarding",
                        "• 210,000-Iteration PBKDF2 AES-256-GCM Vault in noBackupFilesDir",
                        "• Cross-Volume Copy, Move & Batch Operations (Internal + SD + SAF)",
                        "• Structure-Preserving W3C DOM Word (.docx) Editor"
                    ),
                "Page 2: Power-User Studios & Strict RFC-7233 Range Downloader" to
                    listOf(
                        "• Native SQLite (.db / .sqlite) Table Browser & Query Executor",
                        "• Interactive CSV & TSV Spreadsheet Cell Grid Editor",
                        "• APK Package Inspector (Permissions, SDK Levels, DEX & Signatures)",
                        "• HTTP 206 Content-Range Boundary Validator & SHA-256 Verifier"
                    )
            )

            pages.forEachIndexed { index, (headline, bullets) ->
                val pageInfo = PdfDocument.PageInfo.Builder(612, 792, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                val bgPaint = Paint().apply { color = Color.rgb(15, 23, 42) }
                canvas.drawRect(0f, 0f, 612f, 792f, bgPaint)

                val accentPaint = Paint().apply {
                    color = Color.rgb(0, 229, 255)
                    strokeWidth = 4f
                }
                canvas.drawLine(48f, 92f, 564f, 92f, accentPaint)

                val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(0, 229, 255)
                    textSize = 20f
                    isFakeBoldText = true
                }
                canvas.drawText("LUMA FILES v1.1.0 TECHNICAL SPECIFICATION", 48f, 72f, titlePaint)

                val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 15f
                    isFakeBoldText = true
                }
                canvas.drawText(headline, 48f, 135f, subPaint)

                val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(203, 213, 225)
                    textSize = 14f
                }
                var y = 185f
                bullets.forEach { line ->
                    canvas.drawText(line, 60f, y, bodyPaint)
                    y += 38f
                }

                val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(30, 41, 59)
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(48f, 380f, 564f, 520f, 16f, 16f, boxPaint)
                canvas.drawText("Lossless Quality Verification Stamp: PASSED (v1.1.0)", 72f, 435f, titlePaint)
                canvas.drawText("Page ${index + 1} of ${pages.size} • Rendered natively by Luma Studio", 72f, 475f, bodyPaint)

                pdfDocument.finishPage(page)
            }

            FileOutputStream(targetFile).use { out ->
                pdfDocument.writeTo(out)
            }
        } finally {
            pdfDocument.close()
        }
    }

    private fun createSampleZip(targetFile: File) {
        ZipOutputStream(FileOutputStream(targetFile)).use { zos ->
            zos.putNextEntry(ZipEntry("manifest/release_notes_v1_1.md"))
            zos.write(
                "# Release Bundle v1.1.0\nIncludes cross-volume batch engine, 210k PBKDF2 vault, and DOM DOCX editor.".toByteArray()
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("config/security_policy.json"))
            zos.write(
                "{\n  \"cipher\": \"AES-256-GCM\",\n  \"pbkdf2Iterations\": 210000,\n  \"noBackupDir\": true\n}".toByteArray()
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("scripts/verify_integrity.sh"))
            zos.write(
                "#!/usr/bin/env sh\nsha256sum -c manifest.sha256\necho \"All v1.1.0 files verified losslessly.\"\n".toByteArray()
            )
            zos.closeEntry()
        }
    }

    private fun createSampleWav(targetFile: File) {
        val sampleRate = 44100
        val durationSeconds = 2
        val numSamples = sampleRate * durationSeconds
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val dataSize = numSamples * numChannels * bitsPerSample / 8

        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(numChannels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort((numChannels * bitsPerSample / 8).toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = (1.0 - (t / durationSeconds)).coerceIn(0.0, 1.0)
            val wave = (sin(2.0 * Math.PI * 440.0 * t) +
                0.7 * sin(2.0 * Math.PI * 554.37 * t) +
                0.5 * sin(2.0 * Math.PI * 659.25 * t)) / 2.2
            val sample = (wave * envelope * 18000).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            buffer.putShort(sample)
        }

        FileOutputStream(targetFile).use { out ->
            out.write(buffer.array())
        }
    }

    private fun createSamplePng(targetFile: File) {
        val width = 800
        val height = 480
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                Color.rgb(11, 15, 23),
                Color.rgb(15, 32, 55),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(16, 185, 129)
            strokeWidth = 6f
            style = Paint.Style.STROKE
        }
        canvas.drawCircle(width / 2f, height / 2f, 130f, ringPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("LUMA FILES v1.1.0 LOSSLESS 32-BIT RGBA", width / 2f, height / 2f - 10f, textPaint)

        ByteArrayOutputStream().use { bos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
            FileOutputStream(targetFile).use { fos ->
                fos.write(bos.toByteArray())
            }
        }
        bitmap.recycle()
    }
}
