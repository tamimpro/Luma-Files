package com.example.domain.engine

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
        val marker = File(workspaceRoot, ".luma_workspace_initialized_v1")
        if (marker.exists()) return

        val docsDir = File(workspaceRoot, "Documents").apply { mkdirs() }
        val codeDir = File(workspaceRoot, "Code_And_Configs").apply { mkdirs() }
        val archivesDir = File(workspaceRoot, "Archives").apply { mkdirs() }
        val mediaDir = File(workspaceRoot, "Lossless_Media").apply { mkdirs() }
        val downloadsDir = File(workspaceRoot, "Downloads").apply { mkdirs() }

        // 1. Markdown Guide
        File(docsDir, "Welcome_to_Luma_Files.md").writeText(
            """
            # Luma Files — Lossless Studio & Maximum Access File Manager
            
            Welcome to **Luma Files** (installed on your device as **File Manager**).
            
            ## Core Capabilities
            - **Open-With Permission Routing**: Every file tap lets you choose between Luma Files' built-in lossless studio editors or any external Android application.
            - **AES-256-GCM PIN Secure Vault**: Lock sensitive files behind a 4-digit or 6-digit PIN with PBKDF2-HMAC-SHA256 key derivation and SHA-256 integrity checks.
            - **Multi-Format Studio**: View and edit `.txt`, `.md`, `.kt`, `.json`, `.xml`, `.pdf`, `.docx`, `.zip`, `.wav`, `.mp4`, `.png`, and raw binary files via the built-in Hex Inspector.
            - **Advanced Mode**: Toggle **Advanced Mode** in the top bar to reveal hidden dotfiles, POSIX `rwx` flags, MD5/SHA-256 checksums, Regex Batch Rename, and the Duplicate & Large File Analyzer.
            """.trimIndent()
        )

        // 2. Plain Text & Duplicate file for Duplicate Analyzer verification
        val specText = """
            LUMA FILES ENGINE TELEMETRY SPECIFICATION (REV 4.2)
            ===================================================
            Stream Buffer Window : 65,536 Bytes (64 KB)
            Vault Cipher Suite   : AES/GCM/NoPadding (256-Bit Key, 128-Bit Auth Tag)
            Key Derivation       : PBKDF2WithHmacSHA256 (12,000 Iterations, 128-Bit Salt)
            Download Concurrency : Up to 4 Parallel HTTP Range Workers (RFC 7233)
            Quality Preservation : 100% Bit-Exact Atomic File Swap
        """.trimIndent()
        File(docsDir, "Engine_Telemetry_Notes.txt").writeText(specText)
        File(downloadsDir, "Engine_Telemetry_Notes_Copy.txt").writeText(specText)

        // 3. Kotlin & JSON Code samples
        File(codeDir, "LumaStoragePipeline.kt").writeText(
            """
            package com.luma.files.pipeline

            import java.io.RandomAccessFile
            import java.security.MessageDigest

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
              "maxNonRootAccess": true,
              "supportedModes": ["DEFAULT_USER_FRIENDLY", "ADVANCED_WORKSTATION"],
              "vaultEncryption": "AES-256-GCM",
              "downloadEngine": {
                "maxParallelConnections": 4,
                "verifySha256OnComplete": true
              }
            }
            """.trimIndent()
        )

        // 4. Hidden dotfile (visible when Advanced Mode is enabled)
        File(workspaceRoot, ".luma_hidden_config").writeText(
            """
            # Hidden Dotfile Configuration
            SHOW_POSIX_PERMISSIONS=true
            ENABLE_RAW_HEX_PATCHING=true
            VERIFY_ARCHIVE_CRC32=true
            """.trimIndent()
        )
        marker.writeText("INITIALIZED=true\nTIMESTAMP=${System.currentTimeMillis()}\n")

        // 5. Real OOXML .docx Document
        createSampleDocx(
            File(docsDir, "Project_Specification.docx"),
            listOf(
                "Luma Files — Word OOXML (.docx) Lossless Studio Document",
                "This .docx file is a real OpenXML archive containing word/document.xml, [Content_Types].xml, and relationship metadata.",
                "You can edit any paragraph directly inside Luma Files Studio and save it back without losing OOXML structure or formatting.",
                "Maximum non-root Android storage access ensures seamless editing across internal workspaces and SAF mounted folders."
            )
        )

        // 6. Real Multi-Page PDF Document
        createSamplePdf(File(docsDir, "Architecture_Blueprint.pdf"))

        // 7. Real ZIP Archive
        createSampleZip(File(archivesDir, "Release_Bundle_v1.zip"))

        // 8. Real Lossless PCM WAV Audio File (2 seconds stereo harmonic chord)
        createSampleWav(File(mediaDir, "HiRes_Audio_Sample.wav"))

        // 9. Real High-Resolution PNG Test Card
        createSamplePng(File(mediaDir, "Lossless_Calibration_Card.png"))
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

            zos.putNextEntry(ZipEntry("word/document.xml"))
            val bodyXml = paragraphs.joinToString("\n") { p ->
                val escaped = p.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                "<w:p><w:r><w:t>$escaped</w:t></w:r></w:p>"
            }
            zos.write(
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                    <w:body>
                        $bodyXml
                    </w:body>
                </w:document>
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()
        }
    }

    private fun createSamplePdf(targetFile: File) {
        val pdfDocument = PdfDocument()
        try {
            val pages = listOf(
                "Page 1: Luma Files System Architecture & Zero-Loss Stream Pipeline" to
                    listOf(
                        "• Native PdfRenderer High-DPI Page Rendering",
                        "• AES-256-GCM Authenticated Vault Encryption",
                        "• Chunked 64KB Buffer Windowing for Heavy Files",
                        "• Built-in MD5 & SHA-256 Cryptographic Verification"
                    ),
                "Page 2: Non-Root Storage Access & Multi-Thread Download Specification" to
                    listOf(
                        "• Android 11+ All Files Access (MANAGE_EXTERNAL_STORAGE)",
                        "• Storage Access Framework (SAF) Persisted Directory Trees",
                        "• Up to 4 Concurrent HTTP Range Segment Workers",
                        "• Transparent POSIX & HTTP Error Diagnostics"
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
                canvas.drawText("LUMA FILES TECHNICAL SPECIFICATION", 48f, 72f, titlePaint)

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
                canvas.drawText("Lossless Quality Verification Stamp: PASSED (100% Bit-Exact)", 72f, 435f, titlePaint)
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
            zos.putNextEntry(ZipEntry("manifest/release_notes.md"))
            zos.write(
                "# Release Bundle v1.0\nContains verified source scripts, configuration schemas, and checksum manifests.".toByteArray()
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("config/security_policy.json"))
            zos.write(
                "{\n  \"cipher\": \"AES-256-GCM\",\n  \"pinDigits\": [4, 6],\n  \"zeroQualityLoss\": true\n}".toByteArray()
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("scripts/verify_integrity.sh"))
            zos.write(
                "#!/usr/bin/env sh\nsha256sum -c manifest.sha256\necho \"All files verified losslessly.\"\n".toByteArray()
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
        buffer.putInt(16) // PCM chunk size
        buffer.putShort(1) // AudioFormat = 1 (PCM)
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
            // A4 (440 Hz) + C#5 (554.37 Hz) + E5 (659.25 Hz) warm major chord
            val wave = (sin(2.0 * Math.PI * 440.0 * t) +
                0.7 * sin(2.0 * Math.PI * 554.37 * t) +
                0.5 * sin(2.0 * Math.PI * 659.25 * t)) / 2.2
            val sample = (wave * envelope * 18000).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
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

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(55, 0, 229, 255)
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        for (x in 0..width step 80) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), gridPaint)
        }
        for (y in 0..height step 80) {
            canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), gridPaint)
        }

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
        canvas.drawText("LUMA FILES LOSSLESS 32-BIT RGBA CARD", width / 2f, height / 2f - 10f, textPaint)
        textPaint.textSize = 18f
        textPaint.color = Color.rgb(0, 229, 255)
        canvas.drawText("800x480 • PNG Lossless Compression • Bit-Exact Preview", width / 2f, height / 2f + 28f, textPaint)

        ByteArrayOutputStream().use { bos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
            FileOutputStream(targetFile).use { fos ->
                fos.write(bos.toByteArray())
            }
        }
        bitmap.recycle()
    }
}
