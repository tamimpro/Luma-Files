package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.engine.CryptoVaultEngine
import com.example.domain.engine.FileSystemAndFormatEngine
import com.example.domain.model.StudioContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `verify File Manager app name and Luma Files brand identity`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("File Manager", context.getString(R.string.app_name))
        assertEquals("Luma Files", context.getString(R.string.brand_original_name))
    }

    @Test
    fun `verify AES-256-GCM PIN vault lossless encryption and SHA-256 restoration`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cryptoEngine = CryptoVaultEngine(context)
        val fileEngine = FileSystemAndFormatEngine(context, cryptoEngine)

        val setupRes = cryptoEngine.setupPin(
            pin = "2580",
            pinLength = 4,
            recoveryQuestion = "Test Question?",
            recoveryAnswer = "LumaSecret"
        )
        assertTrue(setupRes.isSuccess)
        assertTrue(cryptoEngine.verifyPin("2580"))
        assertFalse(cryptoEngine.verifyPin("9999"))

        val sampleFile = File(fileEngine.lumaWorkspaceDir, "vault_lossless_test.txt")
        val originalContent = "Lossless 100% Bit-Exact Quality Payload • Luma Files AES-256-GCM"
        sampleFile.writeText(originalContent)

        val node = with(fileEngine) { sampleFile.toFileNode() }
        val encryptRes = cryptoEngine.encryptFileToVault(node, activePin = "2580", deleteOriginalAfterEncrypt = true)
        assertTrue(encryptRes.isSuccess)
        assertFalse(sampleFile.exists())

        val vaultEntity = encryptRes.getOrThrow()
        val restoreDir = File(fileEngine.lumaWorkspaceDir, "Restored_Test")
        val decryptRes = cryptoEngine.decryptVaultItemToDirectory(
            item = vaultEntity,
            activePin = "2580",
            targetDirectory = restoreDir,
            removeEncryptedCopy = true
        )
        assertTrue(decryptRes.isSuccess)
        val restoredFile = decryptRes.getOrThrow()
        assertEquals(originalContent, restoredFile.readText())
    }

    @Test
    fun `verify multi-format studio docx editing and duplicate storage analyzer`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cryptoEngine = CryptoVaultEngine(context)
        val fileEngine = FileSystemAndFormatEngine(context, cryptoEngine)

        val docxFile = File(fileEngine.lumaWorkspaceDir, "Documents/Project_Specification.docx")
        assertTrue(docxFile.exists())
        val docxNode = with(fileEngine) { docxFile.toFileNode() }

        val opened = fileEngine.openFileInStudio(docxNode).getOrThrow() as StudioContent.DocxEditor
        assertTrue(opened.paragraphs.isNotEmpty())

        val updatedParagraphs = listOf("Updated Heading in Luma Studio", "Second Paragraph Verified Losslessly")
        val savedNode = fileEngine.saveDocxParagraphsLosslessly(docxNode, updatedParagraphs).getOrThrow()
        val reopened = fileEngine.openFileInStudio(savedNode).getOrThrow() as StudioContent.DocxEditor
        assertEquals(updatedParagraphs, reopened.paragraphs)

        val analyzerReport = fileEngine.runStorageAnalyzer(fileEngine.lumaWorkspaceDir.absolutePath).getOrThrow()
        assertTrue(analyzerReport.scannedFilesCount >= 8)
        assertTrue(analyzerReport.duplicateGroups.isNotEmpty())
    }
}
