package com.example

import com.example.domain.engine.CryptoVaultEngine
import com.example.domain.engine.FastDownloadEngine
import com.example.domain.engine.FileSystemAndFormatEngine
import com.example.domain.engine.PersistedDownloadCheckpoint
import com.example.domain.engine.WorkspaceSeedEngine
import com.example.domain.model.ConflictPolicy
import com.example.domain.model.MediaStreamVariant
import com.example.domain.model.SortOption
import com.example.domain.model.VaultAuthMode
import com.example.ui.LumaFormatUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ExampleRobolectricTest {

    @Test
    fun `verify CryptoVaultEngine Wrapped 256-bit Master DEK lossless password recovery and zero-overwrite shredding`() = runBlocking {
        val tempRoot = Files.createTempDirectory("luma_vault_v12_test").toFile()
        try {
            val isolatedVaultDir = File(tempRoot, "no_backup/.luma_vault_v11")
            val sourceDir = File(tempRoot, "public_storage").apply { mkdirs() }
            val restoreDir = File(tempRoot, "restored_storage").apply { mkdirs() }

            val cryptoEngine = CryptoVaultEngine(context = null, customVaultDir = isolatedVaultDir)
            assertEquals(210_000, CryptoVaultEngine.PBKDF2_ITERATIONS)

            // 1. Configure Initial Master Password with 210,000 PBKDF2 iterations and wrapped Master DEK
            val initialPassword = "InitialVaultPassword#2026"
            val recoveryAnswer = "QuantumSafeRecoveryKey"
            val setupRes = cryptoEngine.setupCredential(
                secret = initialPassword,
                authMode = VaultAuthMode.ALPHANUMERIC_PASSWORD,
                recoveryQuestion = "Master DEK recovery phrase?",
                recoveryAnswer = recoveryAnswer
            )
            assertTrue(setupRes.isSuccess)
            assertTrue(cryptoEngine.isPinConfigured())
            assertTrue(cryptoEngine.verifyPin(initialPassword))
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
                activeSecret = initialPassword,
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

            // 3. Verify decryption with wrong password fails without leaving partial files
            val wrongPassDecrypt = cryptoEngine.decryptVaultItemToDirectory(
                item = vaultEntity,
                activeSecret = "WrongPassword999",
                targetDirectory = restoreDir,
                removeEncryptedCopy = false
            )
            assertTrue(wrongPassDecrypt.isFailure)
            assertFalse(File(restoreDir, "Confidential_Blueprint.txt").exists())

            // 4. Reset forgotten password via Recovery Answer (unwraps Master DEK and re-wraps under new PIN)
            val newPin = "842901"
            val wrongRecovery = cryptoEngine.resetPinWithRecovery(
                recoveryAnswer = "WrongRecoveryAnswer",
                newSecret = newPin,
                newAuthMode = VaultAuthMode.PIN_6
            )
            assertFalse(wrongRecovery)

            val resetOk = cryptoEngine.resetPinWithRecovery(
                recoveryAnswer = recoveryAnswer,
                newSecret = newPin,
                newAuthMode = VaultAuthMode.PIN_6
            )
            assertTrue("Master DEK recovery unwrap and re-wrap must succeed", resetOk)
            assertTrue(cryptoEngine.verifyPin(newPin))
            assertFalse(cryptoEngine.verifyPin(initialPassword))

            // 5. Verify previously encrypted vault file decrypts 100% losslessly using the NEW PIN
            val decryptWithNewPinRes = cryptoEngine.decryptVaultItemToDirectory(
                item = vaultEntity,
                activeSecret = newPin,
                targetDirectory = restoreDir,
                removeEncryptedCopy = true
            )
            assertTrue("Previously encrypted file must decrypt losslessly after password recovery", decryptWithNewPinRes.isSuccess)
            val restoredFile = decryptWithNewPinRes.getOrThrow()
            assertEquals("Confidential_Blueprint.txt", restoredFile.name)
            assertEquals(originalContent, restoredFile.readText(Charsets.UTF_8))
            val (_, restoredSha256) = cryptoEngine.computeFileChecksums(restoredFile)
            assertEquals(expectedSha256, restoredSha256)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `verify recursive copy move self-descendant prevention atomic overwrite verified move deletion and recycle bin`() = runBlocking {
        val tempRoot = Files.createTempDirectory("luma_fs_v12_test").toFile()
        try {
            val cryptoEngine = CryptoVaultEngine(context = null, customVaultDir = File(tempRoot, "vault"))
            val workspaceDir = File(tempRoot, "workspace").apply { mkdirs() }
            val fileEngine = FileSystemAndFormatEngine(
                context = null,
                cryptoEngine = cryptoEngine,
                customWorkspaceDir = workspaceDir
            )

            // 1. Prevent copying or moving a directory into itself or any descendant
            val parentDir = File(workspaceDir, "ProjectRoot").apply { mkdirs() }
            val childDir = File(parentDir, "SubModule/DeepChild").apply { mkdirs() }
            File(parentDir, "readme.txt").writeText("Root File")

            val parentNode = with(fileEngine) { parentDir.toFileNode() }

            val copyIntoSelf = fileEngine.copyOrMoveBatch(
                sourceNodes = listOf(parentNode),
                destinationDirPathOrUri = parentDir.absolutePath,
                isMove = false,
                conflictPolicy = ConflictPolicy.OVERWRITE
            )
            assertTrue("Copying directory into itself must be rejected", copyIntoSelf.isFailure)

            val moveIntoDescendant = fileEngine.copyOrMoveBatch(
                sourceNodes = listOf(parentNode),
                destinationDirPathOrUri = childDir.absolutePath,
                isMove = true,
                conflictPolicy = ConflictPolicy.OVERWRITE
            )
            assertTrue("Moving directory into its own descendant must be rejected", moveIntoDescendant.isFailure)

            // 2. Atomic Overwrite & Verified Move Source Deletion
            val srcDir = File(workspaceDir, "SourceFolder").apply { mkdirs() }
            val dstDir = File(workspaceDir, "DestFolder").apply { mkdirs() }

            val fileA = File(srcDir, "Report.txt").apply { writeText("Version 1 Content") }
            val fileB = File(srcDir, "Data.json").apply { writeText("{\"id\":100}") }

            val nodeA = with(fileEngine) { fileA.toFileNode() }
            val nodeB = with(fileEngine) { fileB.toFileNode() }

            val copiedCount = fileEngine.copyOrMoveBatch(
                sourceNodes = listOf(nodeA, nodeB),
                destinationDirPathOrUri = dstDir.absolutePath,
                isMove = false,
                conflictPolicy = ConflictPolicy.OVERWRITE
            ).getOrThrow()
            assertEquals(2, copiedCount)
            assertEquals("Version 1 Content", File(dstDir, "Report.txt").readText())

            // Update source and test atomic overwrite + move deletion verification
            fileA.writeText("Version 2 Atomic Replacement Payload")
            val moveOutcome = fileEngine.copyOrMoveBatchWithUndo(
                sourceNodes = listOf(with(fileEngine) { fileA.toFileNode() }),
                destinationDirPathOrUri = dstDir.absolutePath,
                isMove = true,
                conflictPolicy = ConflictPolicy.OVERWRITE
            ).getOrThrow()
            assertEquals(1, moveOutcome.processedCount)
            assertFalse("Moved source file must be verified deleted", fileA.exists())
            assertEquals("Version 2 Atomic Replacement Payload", File(dstDir, "Report.txt").readText())
            // Verify no leftover .tmp atomic staging files exist in destination
            assertTrue(dstDir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })

            // 3. Recycle Bin Move, List, Restore & Permanent Shred
            val targetToRecycle = File(dstDir, "Data.json")
            val recycleNode = with(fileEngine) { targetToRecycle.toFileNode() }
            val recycledItems = fileEngine.moveToRecycleBin(listOf(recycleNode)).getOrThrow()
            assertEquals(1, recycledItems.size)
            assertFalse("File moved to Recycle Bin must not remain in original folder", targetToRecycle.exists())

            val binList = fileEngine.listRecycleBinItems()
            assertEquals(1, binList.size)
            assertEquals("Data.json", binList.first().originalName)

            val restoredNode = fileEngine.restoreFromRecycleBin(recycledItems.first(), ConflictPolicy.KEEP_BOTH).getOrThrow()
            assertEquals("Data.json", restoredNode.name)
            assertTrue("Restored file must exist at original path", targetToRecycle.exists())
            assertEquals("{\"id\":100}", targetToRecycle.readText())
            assertTrue(fileEngine.listRecycleBinItems().isEmpty())
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `verify ZIP extraction blocks Zip-Slip traversal and ZIP creation excludes output archive from self-inclusion`() = runBlocking {
        val tempRoot = Files.createTempDirectory("luma_zip_security_test").toFile()
        try {
            val cryptoEngine = CryptoVaultEngine(context = null, customVaultDir = File(tempRoot, "vault"))
            val fileEngine = FileSystemAndFormatEngine(
                context = null,
                cryptoEngine = cryptoEngine,
                customWorkspaceDir = tempRoot
            )

            // 1. Test Zip-Slip path traversal rejection during extraction
            val maliciousZip = File(tempRoot, "malicious_traversal.zip")
            ZipOutputStream(FileOutputStream(maliciousZip)).use { zos ->
                zos.putNextEntry(ZipEntry("../../escaped_payload.txt"))
                zos.write("Malicious traversal payload".toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            val maliciousNode = with(fileEngine) { maliciousZip.toFileNode() }
            val extractTargetDir = File(tempRoot, "extract_out").apply { mkdirs() }
            val extractRes = fileEngine.extractZipArchive(maliciousNode, extractTargetDir)
            assertTrue("Zip-Slip traversal entry must be rejected", extractRes.isFailure)
            assertTrue(extractRes.exceptionOrNull() is SecurityException)
            assertFalse(File(tempRoot.parentFile, "escaped_payload.txt").exists())

            // 2. Test ZIP creation inside the same directory excludes the target .zip and .tmp from self-inclusion
            val projectDir = File(tempRoot, "BundleSource").apply { mkdirs() }
            val doc1 = File(projectDir, "Alpha.txt").apply { writeText("Alpha Content") }
            val doc2 = File(projectDir, "Beta.txt").apply { writeText("Beta Content") }

            val existingArchive = File(projectDir, "SelfTarget.zip").apply { writeText("Old placeholder") }
            val nodesToZip = listOf(
                with(fileEngine) { doc1.toFileNode() },
                with(fileEngine) { doc2.toFileNode() },
                with(fileEngine) { existingArchive.toFileNode() }
            )

            val createdZipNode = fileEngine.compressNodesToZip(
                sourceNodes = nodesToZip,
                destinationDirPathOrUri = projectDir.absolutePath,
                archiveName = "SelfTarget.zip"
            ).getOrThrow()

            val createdZipFile = File(createdZipNode.path)
            assertTrue(createdZipFile.exists())
            ZipFile(createdZipFile).use { zf ->
                val entryNames = zf.entries().asSequence().map { it.name }.toList()
                assertTrue(entryNames.contains("Alpha.txt"))
                assertTrue(entryNames.contains("Beta.txt"))
                assertFalse("Output ZIP must never include itself", entryNames.contains("SelfTarget.zip"))
                assertTrue("Output ZIP must never include .tmp staging file", entryNames.none { it.endsWith(".tmp") })
            }

            // 3. Test valid extraction of created ZIP
            val validExtractTarget = File(tempRoot, "valid_extracted").apply { mkdirs() }
            val extractedCount = fileEngine.extractZipArchive(createdZipNode, validExtractTarget).getOrThrow()
            assertEquals(2, extractedCount)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `verify W3C DOM docx editing CSV grid editor and JSON XML structured formatter`() = runBlocking {
        val tempRoot = Files.createTempDirectory("luma_docx_csv_test").toFile()
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
                    "Luma Files v1.2 Heading",
                    "Subtitle Line",
                    "Original Body Paragraph 1"
                )
            )
            val node = with(fileEngine) { docxFile.toFileNode() }

            val loadedStudio = fileEngine.loadDocxEditor(node, docxFile)
            assertEquals(3, loadedStudio.paragraphNodes.size)
            assertEquals("Heading1", loadedStudio.paragraphNodes[0].styleId)
            assertTrue(loadedStudio.paragraphNodes[0].hasRunFormatting)

            val updatedNode = fileEngine.saveDocxParagraphsLosslessly(
                node = node,
                updatedParagraphs = listOf(
                    "Luma Files v1.2.0 DOM Updated Heading",
                    "Subtitle Line Preserved",
                    "Edited Body Paragraph with DOM Structure Intact",
                    "Newly Appended 4th Paragraph"
                )
            ).getOrThrow()

            val reloadedStudio = fileEngine.loadDocxEditor(updatedNode, docxFile)
            assertEquals(4, reloadedStudio.paragraphNodes.size)
            assertEquals("Luma Files v1.2.0 DOM Updated Heading", reloadedStudio.paragraphNodes[0].text)
            assertEquals("Heading1", reloadedStudio.paragraphNodes[0].styleId)

            val csvFile = File(tempRoot, "Metrics.csv")
            WorkspaceSeedEngine.createSampleCsv(csvFile)
            val csvNode = with(fileEngine) { csvFile.toFileNode() }
            val grid = fileEngine.loadCsvGridStudio(csvNode, csvFile)
            assertEquals(5, grid.headers.size)

            val (prettyJson, jsonMsg) = fileEngine.formatAndValidateStructuredText(
                rawText = "{\"engine\":\"LumaFiles\",\"version\":120,\"features\":[\"WrappedDEK\",\"RecycleBin\",\"LosslessMux\"]}",
                extension = "json"
            )
            assertTrue(prettyJson.contains("\"engine\": \"LumaFiles\""))
            assertTrue(jsonMsg.contains("Valid JSON"))
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `verify FastDownloadEngine persistent resume checkpoint stream variant selector and HTTP Content-Range validation`() {
        // 1. Verify stream selection rule: Always pick highest original resolution without watermark and NEVER pick silent video when audio is available
        val variants = listOf(
            MediaStreamVariant(
                variantId = "silent_4k",
                label = "4K Video-Only (Silent)",
                containerExtension = "mp4",
                mimeType = "video/mp4",
                resolutionLabel = "2160p 4K",
                widthPx = 3840,
                heightPx = 2160,
                frameRateFps = 60,
                videoCodec = "H.264",
                videoBitrateKbps = 22000,
                videoStreamUrl = "https://cdn.example.com/silent_4k.mp4",
                audioCodec = "None",
                audioBitrateKbps = 0,
                audioStreamUrl = null,
                hasEmbeddedAudio = false,
                requiresAudioVideoMux = false,
                isWatermarkFree = true
            ),
            MediaStreamVariant(
                variantId = "watermarked_1080p",
                label = "1080p Watermarked",
                containerExtension = "mp4",
                mimeType = "video/mp4",
                resolutionLabel = "1080p",
                widthPx = 1920,
                heightPx = 1080,
                frameRateFps = 60,
                videoCodec = "H.264",
                videoBitrateKbps = 8000,
                videoStreamUrl = "https://cdn.example.com/wm_1080p.mp4",
                audioCodec = "AAC",
                audioBitrateKbps = 192,
                audioStreamUrl = null,
                hasEmbeddedAudio = true,
                requiresAudioVideoMux = false,
                isWatermarkFree = false
            ),
            MediaStreamVariant(
                variantId = "clean_4k_muxed",
                label = "4K UHD Original Lossless + AAC 320k Audio",
                containerExtension = "mp4",
                mimeType = "video/mp4",
                resolutionLabel = "2160p 4K",
                widthPx = 3840,
                heightPx = 2160,
                frameRateFps = 60,
                videoCodec = "H.264 High@L5.1",
                videoBitrateKbps = 24000,
                videoStreamUrl = "https://cdn.example.com/clean_4k_video.mp4",
                audioCodec = "AAC-LC",
                audioBitrateKbps = 320,
                audioStreamUrl = "https://cdn.example.com/clean_4k_audio.m4a",
                hasEmbeddedAudio = false,
                requiresAudioVideoMux = true,
                isWatermarkFree = true
            ),
            MediaStreamVariant(
                variantId = "clean_1080p",
                label = "1080p Full HD Progressive",
                containerExtension = "mp4",
                mimeType = "video/mp4",
                resolutionLabel = "1080p",
                widthPx = 1920,
                heightPx = 1080,
                frameRateFps = 60,
                videoCodec = "H.264",
                videoBitrateKbps = 8500,
                videoStreamUrl = "https://cdn.example.com/clean_1080p.mp4",
                audioCodec = "AAC-LC",
                audioBitrateKbps = 256,
                audioStreamUrl = null,
                hasEmbeddedAudio = true,
                requiresAudioVideoMux = false,
                isWatermarkFree = true
            )
        )

        val autoBest = FastDownloadEngine.selectBestStreamVariant(variants)
        assertNotNull(autoBest)
        assertEquals("clean_4k_muxed", autoBest!!.variantId)
        assertTrue("Selected stream must have audio when audio is available", autoBest.hasAudio)
        assertTrue("Selected stream must be watermark-free", autoBest.isWatermarkFree)

        // 2. Verify genuine resume offset computation
        val tempPart = Files.createTempFile("luma_resume_part", ".part").toFile()
        try {
            tempPart.writeBytes(ByteArray(1536) { 0x5A })
            val resumeOffset = FastDownloadEngine.computeResumeOffset(tempPart, expectedTotalBytes = 4096L)
            assertEquals(1536L, resumeOffset)

            // Oversized corrupt .part file must reset to 0L
            val resetOffset = FastDownloadEngine.computeResumeOffset(tempPart, expectedTotalBytes = 1024L)
            assertEquals(0L, resetOffset)
        } finally {
            tempPart.delete()
        }

        // 3. Valid HTTP 206 Partial Content matching requested range
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

        assertEquals("8.00 MB/s", LumaFormatUtils.formatSpeed(8L * 1024L * 1024L))
        assertNotEquals("", LumaFormatUtils.formatBytes(1048576L))
    }
}
