package com.example

import com.example.domain.engine.WorkspaceSeedEngine
import com.example.domain.model.FileFormatCategory
import com.example.ui.LumaFormatUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class ExampleRobolectricTest {

    @Test
    fun `verify byte size and transfer speed formatting`() {
        assertEquals("512 B", LumaFormatUtils.formatBytes(512L))
        assertEquals("2.0 KB", LumaFormatUtils.formatBytes(2048L))
        assertEquals("8.00 MB/s", LumaFormatUtils.formatSpeed(8L * 1024L * 1024L))
    }

    @Test
    fun `verify AES-256-GCM PBKDF2 PIN encryption and SHA-256 bit-exact decryption`() {
        val pin = "2580"
        val salt = ByteArray(16) { (it + 1).toByte() }
        val iv = ByteArray(12) { (it + 7).toByte() }

        val spec = PBEKeySpec(pin.toCharArray(), salt, 12000, 256)
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val originalPayload = "Luma Files Lossless Studio Payload • 100% Bit-Exact Quality".toByteArray(Charsets.UTF_8)
        val originalSha256 = MessageDigest.getInstance("SHA-256").digest(originalPayload).toList()

        val encCipher = Cipher.getInstance("AES/GCM/NoPadding")
        encCipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val cipherBytes = encCipher.doFinal(originalPayload)

        val decCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decCipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val restoredBytes = decCipher.doFinal(cipherBytes)

        val restoredSha256 = MessageDigest.getInstance("SHA-256").digest(restoredBytes).toList()
        assertEquals(originalSha256, restoredSha256)
        assertEquals(String(originalPayload, Charsets.UTF_8), String(restoredBytes, Charsets.UTF_8))
    }

    @Test
    fun `verify OOXML docx archive generation and lossless structure`() {
        val tempDir = Files.createTempDirectory("luma_test_workspace").toFile()
        try {
            val docxFile = File(tempDir, "Test_Document.docx")
            val paragraphs = listOf(
                "Luma Files Heading",
                "Lossless OOXML Paragraph Verification"
            )
            WorkspaceSeedEngine.createSampleDocx(docxFile, paragraphs)
            assertTrue(docxFile.exists())
            assertTrue(docxFile.length() > 100)

            ZipFile(docxFile).use { zip ->
                val docEntry = zip.getEntry("word/document.xml")
                assertTrue(docEntry != null)
                val xmlContent = zip.getInputStream(docEntry).bufferedReader().readText()
                assertTrue(xmlContent.contains("Luma Files Heading"))
                assertTrue(xmlContent.contains("Lossless OOXML Paragraph Verification"))
            }
            assertEquals("DOCX", FileFormatCategory.DOCX.badgeText)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
