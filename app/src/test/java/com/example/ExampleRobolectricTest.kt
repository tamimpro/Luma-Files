package com.example

import com.example.domain.engine.CryptoVaultEngine
import com.example.domain.engine.FastDownloadEngine
import com.example.domain.engine.FileSystemAndFormatEngine
import com.example.domain.engine.WorkspaceSeedEngine
import com.example.domain.model.ConflictPolicy
import com.example.domain.model.SortOption
import com.example.domain.model.VaultAuthMode
import com.example.ui.LumaFormatUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

class ExampleRobolectricTest {

    @Test
    fun `verify CryptoVaultEngine 210k PBKDF2 PIN and Password auth opaque UUID filenames and zero-overwrite shredding`() = runBlocking {
        val tempRoot = Files.createTempDirectory("luma_vault_engine_test").toFile()
        try {
            val isolatedVaultDir = File(tempRoot, "no_backup/.luma_vault_v11")
            val sourceDir = File(tempRoot, "public_storage").apply { mkdirs() }
            val restoreDir = File(tempRoot, "restored_storage").apply { mkdirs() }

            val cryptoEngine = CryptoVaultEngine(context = null, customVaultDir = isolatedVaultDir)
            assertEquals(210_000, CryptoVaultEngine.PBKDF2_ITERATIONS)

            // 1. Configure Alphanumeric Master Password with 210,000 PBKDF2 iterations
            val setupRes = cryptoEngine.setupCredential(
                secret = "LumaPowerVault#2026",
                authMode = VaultAuthMode.ALPHANUMERIC_PASSWORD,
                recoveryQuestion = "Primary encryption cipher?",
                recoveryAnswer = "AES-256-GCM"
            )
            assertTrue(setupRes.isSuccess)
            assertTrue(cryptoEngine.isPinConfigured())
            assertEquals(VaultAuthMode.ALPHANUMERIC_PASSWORD, cryptoEngine.getConfiguredAuthMode())
            assertTrue(cryptoEngine.verifyPin("LumaPowerVault#2026"))
            assertFalse(cryptoEngine.verifyPin("WrongPassword123"))

            // 2. Create sensitive source file and encrypt into isolated vault
            val originalContent = "Top Secret Financial & Architecture Blueprint • 100% Bit-Exact Payload"
            val secretFile = File(sourceDir, "Confidential_Blueprint.txt").apply {
                writeText(originalContent, Charsets.UTF_8)
            }
            val (_, expectedSha256) = cryptoEngine.computeFileChecksums(secretFile)

            val fileEngine = FileSystemAndFormatEngine(
                context = null,
                cryptoEngine = cryptoEngine,
                customWorkspaceDir = sourceDir
            )
            val fileNode = with(fileEngine) { secretFile.toFileNode() }

            val encryptRes = cryptoEngine.encryptFileToVault(
                fileNode = fileNode,
                activeSecret = "LumaPowerVault#2026",
                deleteOriginalAfterEncrypt = true
            )
            assertTrue(encryptRes.isSuccess)
            val vaultEntity = encryptRes.getOrThrow()

            // Verify original file was shredded and removed from public folder
            assertFalse("Original file must be shredded and deleted from public folder", secretFile.exists())

            // Verify isolated vault directory contains .nomedia and opaque UUID .lvault container
            assertTrue(File(isolatedVaultDir, ".nomedia").exists())
            val encryptedFile = File(vaultEntity.encryptedFilePath)
            assertTrue(encryptedFile.exists())
            assertTrue(encryptedFile.name.endsWith(".lvault"))
            assertFalse(
                "Opaque vault filename must NEVER leak original filename",
                encryptedFile.name.contains("Confidential", ignoreCase = true)
            )
            // Verify ciphertext on disk does not contain plaintext
            val diskBytes = encryptedFile.readText(Charsets.ISO_8859_1)
            assertFalse(diskBytes.contains("Top Secret Financial"))

            // 3. Verify decryption with wrong password fails with AEADBadTagException
            val wrongPassDecrypt = cryptoEngine.decryptVaultItemToDirectory(
                item = vaultEntity,
                activeSecret = "WrongPassword999",
                targetDirectory = restoreDir,
                removeEncryptedCopy = false
            )
            assertTrue(wrongPassDecrypt.isFailure)

            // 4. Verify decryption with correct password restores 100% bit-exact SHA-256 file
            val decryptRes = cryptoEngine.decryptVaultItemToDirectory(
                item = vaultEntity,
                activeSecret = "LumaPowerVault#2026",
                targetDirectory = restoreDir,
                removeEncryptedCopy = true
            )
            assertTrue(decryptRes.isSuccess)
            val restoredFile = decryptRes.getOrThrow()
            assertEquals("Confidential_Blueprint.txt", restoredFile.name)
            assertEquals(originalContent, restoredFile.readText(Charsets.UTF_8))
            val (_, restoredSha256) = cryptoEngine.computeFileChecksums(restoredFile)
            assertEquals(expectedSha256, restoredSha256)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `verify FileSystemAndFormatEngine copy move conflict policies and batch zip compression`() = runBlocking {
        val tempRoot = Files.createTempDirectory("luma_fs_engine_test").toFile()
        try {
            val cryptoEngine = CryptoVaultEngine(context = null, customVaultDir = File(tempRoot, "vault"))
            val workspaceDir = File(tempRoot, "workspace").apply { mkdirs() }
            val fileEngine = FileSystemAndFormatEngine(
                context = null,
                cryptoEngine = cryptoEngine,
                customWorkspaceDir = workspaceDir
            )

            val srcDir = File(workspaceDir, "SourceFolder").apply { mkdirs() }
            val dstDir = File(workspaceDir, "DestFolder").apply { mkdirs() }

            val fileA = File(srcDir, "Report.txt").apply { writeText("Version 1 Content") }
            val fileB = File(srcDir, "Data.json").apply { writeText("{\"id\":100}") }

            val nodeA = with(fileEngine) { fileA.toFileNode() }
            val nodeB = with(fileEngine) { fileB.toFileNode() }

            // 1. Batch Copy to DestFolder
            val copiedCount = fileEngine.copyOrMoveBatch(
                sourceNodes = listOf(nodeA, nodeB),
                destinationDirPathOrUri = dstDir.absolutePath,
                isMove = false,
                conflictPolicy = ConflictPolicy.OVERWRITE
            ).getOrThrow()
            assertEquals(2, copiedCount)
            assertTrue(File(srcDir, "Report.txt").exists())
            assertEquals("Version 1 Content", File(dstDir, "Report.txt").readText())

            // 2. ConflictPolicy.SKIP keeps destination unchanged
            fileA.writeText("Version 2 Updated Content")
            val skippedProcessed = fileEngine.copyOrMoveBatch(
                sourceNodes = listOf(with(fileEngine) { fileA.toFileNode() }),
                destinationDirPathOrUri = dstDir.absolutePath,
                isMove = false,
                conflictPolicy = ConflictPolicy.SKIP
            ).getOrThrow()
            assertEquals(0, skippedProcessed)
            assertEquals("Version 1 Content", File(dstDir, "Report.txt").readText())

            // 3. ConflictPolicy.KEEP_BOTH creates Report_(1).txt
            val keepBothCount = fileEngine.copyOrMoveBatch(
                sourceNodes = listOf(with(fileEngine) { fileA.toFileNode() }),
                destinationDirPathOrUri = dstDir.absolutePath,
                isMove = false,
                conflictPolicy = ConflictPolicy.KEEP_BOTH
            ).getOrThrow()
            assertEquals(1, keepBothCount)
            assertTrue(File(dstDir, "Report_(1).txt").exists())
            assertEquals("Version 2 Updated Content", File(dstDir, "Report_(1).txt").readText())

            // 4. ConflictPolicy.OVERWRITE replaces Report.txt and Move deletes source
            val moveCount = fileEngine.copyOrMoveBatch(
                sourceNodes = listOf(with(fileEngine) { fileA.toFileNode() }),
                destinationDirPathOrUri = dstDir.absolutePath,
                isMove = true,
                conflictPolicy = ConflictPolicy.OVERWRITE
            ).getOrThrow()
            assertEquals(1, moveCount)
            assertFalse("Moved source file must be deleted from source directory", fileA.exists())
            assertEquals("Version 2 Updated Content", File(dstDir, "Report.txt").readText())

            // 5. Batch Compress to ZIP
            val dstNodes = fileEngine.listDirectory(
                pathOrUri = dstDir.absolutePath,
                showHiddenFiles = true,
                sortOption = SortOption.NAME_ASC
            ).getOrThrow()
            val zipNode = fileEngine.compressNodesToZip(
                sourceNodes = dstNodes,
                destinationDirPathOrUri = workspaceDir.absolutePath,
                archiveName = "Batch_Output.zip"
            ).getOrThrow()
            assertTrue(File(zipNode.path).exists())
            assertTrue(zipNode.sizeBytes > 50L)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `verify W3C DOM docx editing preserves paragraph styles run formatting and styles xml`() = runBlocking {
        val tempRoot = Files.createTempDirectory("luma_docx_dom_test").toFile()
        try {
            val cryptoEngine = CryptoVaultEngine(context = null, customVaultDir = File(tempRoot, "vault"))
            val fileEngine = FileSystemAndFormatEngine(
                context = null,
                cryptoEngine = cryptoEngine,
                customWorkspaceDir = tempRoot
            )

            val docxFile = File(tempRoot, "Architecture_Spec.docx")
            WorkspaceSeedEngine.createSampleDocx(
                docxFile,
                listOf(
                    "Luma Files v1.1 Heading",
                    "Subtitle Line",
                    "Original Body Paragraph 1"
                )
            )
            val node = with(fileEngine) { docxFile.toFileNode() }

            // 1. Load DOCX via W3C DOM parser and verify styles are detected
            val loadedStudio = fileEngine.loadDocxEditor(node, docxFile)
            assertEquals(3, loadedStudio.paragraphNodes.size)
            assertEquals("Heading1", loadedStudio.paragraphNodes[0].styleId)
            assertTrue(loadedStudio.paragraphNodes[0].hasRunFormatting)
            assertEquals("Subtitle", loadedStudio.paragraphNodes[1].styleId)

            // 2. Mutate paragraph text and append a new paragraph
            val updatedNode = fileEngine.saveDocxParagraphsLosslessly(
                node = node,
                updatedParagraphs = listOf(
                    "Luma Files v1.1.0 DOM Updated Heading",
                    "Subtitle Line Preserved",
                    "Edited Body Paragraph with DOM Structure Intact",
                    "Newly Appended 4th Paragraph"
                )
            ).getOrThrow()

            // 3. Reload and verify XML structure (<w:pStyle>, <w:rPr>, word/styles.xml) is 100% preserved
            val reloadedStudio = fileEngine.loadDocxEditor(updatedNode, docxFile)
            assertEquals(4, reloadedStudio.paragraphNodes.size)
            assertEquals("Luma Files v1.1.0 DOM Updated Heading", reloadedStudio.paragraphNodes[0].text)
            assertEquals("Heading1", reloadedStudio.paragraphNodes[0].styleId)
            assertTrue(reloadedStudio.paragraphNodes[0].hasRunFormatting)
            assertEquals("Subtitle", reloadedStudio.paragraphNodes[1].styleId)

            ZipFile(docxFile).use { zip ->
                assertTrue("word/styles.xml must be preserved", zip.getEntry("word/styles.xml") != null)
                val docXml = zip.getInputStream(zip.getEntry("word/document.xml")).bufferedReader().readText()
                assertTrue("Heading1 w:pStyle must be preserved in DOM", docXml.contains("Heading1"))
                assertTrue("Section properties w:sectPr must be preserved", docXml.contains("sectPr"))
            }
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `verify CSV grid editor and JSON XML structured formatter`() = runBlocking {
        val tempRoot = Files.createTempDirectory("luma_csv_json_test").toFile()
        try {
            val cryptoEngine = CryptoVaultEngine(context = null, customVaultDir = File(tempRoot, "vault"))
            val fileEngine = FileSystemAndFormatEngine(
                context = null,
                cryptoEngine = cryptoEngine,
                customWorkspaceDir = tempRoot
            )

            val csvFile = File(tempRoot, "Metrics.csv")
            WorkspaceSeedEngine.createSampleCsv(csvFile)
            val csvNode = with(fileEngine) { csvFile.toFileNode() }

            val grid = fileEngine.loadCsvGridStudio(csvNode, csvFile)
            assertEquals(5, grid.headers.size)
            assertEquals("Module", grid.headers[0])
            assertTrue(grid.rows.size >= 4)

            // Save modified CSV grid
            val updatedRows = grid.rows.toMutableList().apply {
                add(listOf("ApkInspector", "Parsed", "19200", "2.1", "PASS"))
            }
            fileEngine.saveCsvGridLosslessly(csvNode, ',', grid.headers, updatedRows).getOrThrow()
            val reloadedGrid = fileEngine.loadCsvGridStudio(csvNode, csvFile)
            assertEquals(updatedRows.size, reloadedGrid.rows.size)
            assertEquals("ApkInspector", reloadedGrid.rows.last()[0])

            // Verify JSON validator & pretty-formatter
            val (prettyJson, jsonMsg) = fileEngine.formatAndValidateStructuredText(
                rawText = "{\"engine\":\"LumaFiles\",\"version\":110,\"features\":[\"Vault\",\"DOM\",\"SQLite\"]}",
                extension = "json"
            )
            assertTrue(prettyJson.contains("\"engine\": \"LumaFiles\""))
            assertTrue(jsonMsg.contains("Valid JSON"))
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `verify FastDownloadEngine HTTP Content-Range validation and post-download SHA256 integrity verification`() {
        // 1. Valid HTTP 206 Partial Content matching requested range
        val valid206 = FastDownloadEngine.validateHttpContentRange(
            httpStatusCode = 206,
            contentRangeHeader = "bytes 1024-2047/4096",
            requestedStart = 1024L,
            requestedEnd = 2047L
        )
        assertTrue(valid206.isValidPartialRange)
        assertFalse(valid206.shouldFallbackToSingleStream)
        assertEquals(1024L, valid206.startByte)
        assertEquals(2047L, valid206.endByte)
        assertEquals(4096L, valid206.totalBytes)

        // 2. Mismatched Content-Range start byte must be rejected to prevent silent file corruption
        val mismatchedRange = FastDownloadEngine.validateHttpContentRange(
            httpStatusCode = 206,
            contentRangeHeader = "bytes 0-2047/4096",
            requestedStart = 1024L,
            requestedEnd = 2047L
        )
        assertFalse(mismatchedRange.isValidPartialRange)

        // 3. Server ignoring Range header and returning HTTP 200 OK must trigger single-stream fallback
        val fallback200 = FastDownloadEngine.validateHttpContentRange(
            httpStatusCode = 200,
            contentRangeHeader = null,
            requestedStart = 1024L,
            requestedEnd = 2047L
        )
        assertFalse(fallback200.isValidPartialRange)
        assertTrue(fallback200.shouldFallbackToSingleStream)

        // 4. Post-download file size and SHA-256 verification
        val tempFile = Files.createTempFile("luma_dl_integrity", ".bin").toFile()
        try {
            val payload = "Verified Multi-Threaded Download Stream Payload".toByteArray(Charsets.UTF_8)
            tempFile.writeBytes(payload)

            val cryptoEngine = CryptoVaultEngine(context = null, customVaultDir = tempFile.parentFile)
            val actualSha256 = cryptoEngine.computeHash(tempFile, "SHA-256")

            val validIntegrity = FastDownloadEngine.verifyDownloadedFileIntegrity(
                file = tempFile,
                expectedTotalBytes = payload.size.toLong(),
                cryptoEngine = cryptoEngine,
                expectedSha256 = actualSha256
            )
            assertTrue(validIntegrity.isValid)

            val sizeMismatch = FastDownloadEngine.verifyDownloadedFileIntegrity(
                file = tempFile,
                expectedTotalBytes = payload.size.toLong() + 128L,
                cryptoEngine = cryptoEngine,
                expectedSha256 = actualSha256
            )
            assertFalse(sizeMismatch.isValid)

            val hashMismatch = FastDownloadEngine.verifyDownloadedFileIntegrity(
                file = tempFile,
                expectedTotalBytes = payload.size.toLong(),
                cryptoEngine = cryptoEngine,
                expectedSha256 = "0000000000000000000000000000000000000000000000000000000000000000"
            )
            assertFalse(hashMismatch.isValid)
        } finally {
            tempFile.delete()
        }

        assertEquals("8.00 MB/s", LumaFormatUtils.formatSpeed(8L * 1024L * 1024L))
        assertNotEquals("", LumaFormatUtils.formatBytes(1048576L))
    }
}
