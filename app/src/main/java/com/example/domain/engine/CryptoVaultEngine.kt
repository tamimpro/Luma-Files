package com.example.domain.engine

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.VaultItemEntity
import com.example.domain.model.DiagnosticError
import com.example.domain.model.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class CryptoVaultEngine(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("luma_vault_security_prefs", Context.MODE_PRIVATE)
    private val vaultDir: File = File(context.filesDir, ".luma_secure_vault").apply {
        if (!exists()) mkdirs()
    }
    private val secureRandom = SecureRandom()

    companion object {
        private const val KEY_PIN_HASH = "pin_verification_hash"
        private const val KEY_PIN_SALT = "pin_verification_salt"
        private const val KEY_PIN_LENGTH = "pin_length"
        private const val KEY_RECOVERY_QUESTION = "recovery_question"
        private const val KEY_RECOVERY_HASH = "recovery_answer_hash"
        private const val PBKDF2_ALGO = "PBKDF2WithHmacSHA256"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val PBKDF2_ITERATIONS = 12000
        private const val BUFFER_SIZE = 64 * 1024
    }

    fun isPinConfigured(): Boolean = prefs.contains(KEY_PIN_HASH)

    fun getConfiguredPinLength(): Int = prefs.getInt(KEY_PIN_LENGTH, 4)

    fun getRecoveryQuestion(): String =
        prefs.getString(KEY_RECOVERY_QUESTION, "What is the name of your first digital workspace?")
            ?: "What is the name of your first digital workspace?"

    fun setupPin(pin: String, pinLength: Int, recoveryQuestion: String, recoveryAnswer: String): Result<Unit> {
        return try {
            require(pin.length == 4 || pin.length == 6) {
                "PIN must be exactly 4 or 6 digits (received ${pin.length})."
            }
            require(pin.all { it.isDigit() }) {
                "PIN must contain numeric digits (0-9) only."
            }
            val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
            val pinVerifyHash = deriveKeyBytes(pin, salt, 32).toHex()
            val cleanRecovery = recoveryAnswer.trim().lowercase()
            val recoveryHash = if (cleanRecovery.isNotEmpty()) {
                deriveKeyBytes(cleanRecovery, salt, 32).toHex()
            } else ""

            prefs.edit()
                .putString(KEY_PIN_HASH, pinVerifyHash)
                .putString(KEY_PIN_SALT, salt.toHex())
                .putInt(KEY_PIN_LENGTH, pinLength)
                .putString(KEY_RECOVERY_QUESTION, recoveryQuestion.ifBlank { "Favorite security keyword?" })
                .putString(KEY_RECOVERY_HASH, recoveryHash)
                .apply()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val saltHex = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val salt = saltHex.hexToBytes()
        val candidateHash = deriveKeyBytes(pin, salt, 32).toHex()
        return MessageDigest.isEqual(storedHash.toByteArray(), candidateHash.toByteArray())
    }

    fun resetPinWithRecovery(recoveryAnswer: String, newPin: String, newPinLength: Int): Boolean {
        val storedRecoveryHash = prefs.getString(KEY_RECOVERY_HASH, null) ?: return false
        val saltHex = prefs.getString(KEY_PIN_SALT, null) ?: return false
        if (storedRecoveryHash.isBlank()) return false
        val salt = saltHex.hexToBytes()
        val candidate = deriveKeyBytes(recoveryAnswer.trim().lowercase(), salt, 32).toHex()
        if (!MessageDigest.isEqual(storedRecoveryHash.toByteArray(), candidate.toByteArray())) {
            return false
        }
        val newPinHash = deriveKeyBytes(newPin, salt, 32).toHex()
        prefs.edit()
            .putString(KEY_PIN_HASH, newPinHash)
            .putInt(KEY_PIN_LENGTH, newPinLength)
            .apply()
        return true
    }

    suspend fun encryptFileToVault(
        fileNode: FileNode,
        activePin: String,
        deleteOriginalAfterEncrypt: Boolean = true
    ): Result<VaultItemEntity> = withContext(Dispatchers.IO) {
        val sourceFile = File(fileNode.path)
        if (!sourceFile.exists() || !sourceFile.canRead()) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Cannot read source file '${fileNode.path}'. Verify POSIX read permission or storage access grant."
                )
            )
        }
        try {
            val sha256 = computeHash(sourceFile, "SHA-256")
            val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
            val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
            val keyBytes = deriveKeyBytes(activePin, salt, 32)
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))

            val encryptedFileName = "vault_${System.currentTimeMillis()}_${sourceFile.name}.enc"
            val encryptedDest = File(vaultDir, encryptedFileName)

            BufferedInputStream(FileInputStream(sourceFile), BUFFER_SIZE).use { input ->
                CipherOutputStream(BufferedOutputStream(FileOutputStream(encryptedDest), BUFFER_SIZE), cipher).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }

            if (deleteOriginalAfterEncrypt && sourceFile.canWrite()) {
                sourceFile.delete()
            }

            val entity = VaultItemEntity(
                originalName = fileNode.name,
                originalPath = fileNode.path,
                encryptedFilePath = encryptedDest.absolutePath,
                mimeType = fileNode.mimeType,
                formatCategory = fileNode.category.name,
                originalSizeBytes = fileNode.sizeBytes,
                ivHex = iv.toHex(),
                saltHex = salt.toHex(),
                sha256Checksum = sha256
            )
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun decryptVaultItemToDirectory(
        item: VaultItemEntity,
        activePin: String,
        targetDirectory: File,
        removeEncryptedCopy: Boolean = true
    ): Result<File> = withContext(Dispatchers.IO) {
        val encFile = File(item.encryptedFilePath)
        if (!encFile.exists()) {
            return@withContext Result.failure(
                IllegalStateException("Encrypted vault payload not found at ${item.encryptedFilePath}")
            )
        }
        try {
            if (!targetDirectory.exists()) targetDirectory.mkdirs()
            var outputFile = File(targetDirectory, item.originalName)
            if (outputFile.exists()) {
                val base = item.originalName.substringBeforeLast('.', item.originalName)
                val ext = item.originalName.substringAfterLast('.', "")
                val suffix = if (ext.isNotEmpty()) ".$ext" else ""
                outputFile = File(targetDirectory, "${base}_restored$suffix")
            }

            val salt = item.saltHex.hexToBytes()
            val iv = item.ivHex.hexToBytes()
            val keyBytes = deriveKeyBytes(activePin, salt, 32)
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))

            CipherInputStream(BufferedInputStream(FileInputStream(encFile), BUFFER_SIZE), cipher).use { input ->
                BufferedOutputStream(FileOutputStream(outputFile), BUFFER_SIZE).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }

            // Verify lossless restoration via SHA-256
            val restoredSha256 = computeHash(outputFile, "SHA-256")
            if (item.sha256Checksum.isNotEmpty() && !restoredSha256.equals(item.sha256Checksum, ignoreCase = true)) {
                outputFile.delete()
                return@withContext Result.failure(
                    IllegalStateException(
                        "SHA-256 integrity mismatch after decryption (Expected ${item.sha256Checksum.take(12)}..., got ${restoredSha256.take(12)}...)."
                    )
                )
            }

            if (removeEncryptedCopy) {
                encFile.delete()
            }
            Result.success(outputFile)
        } catch (e: AEADBadTagException) {
            Result.failure(
                IllegalStateException(
                    "AES-256-GCM Authentication Tag Verification Failed: The provided PIN does not match the encryption key used for '${item.originalName}', or the ciphertext header was altered.",
                    e
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun computeFileChecksums(file: File): Pair<String, String> = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile) return@withContext Pair("N/A", "N/A")
        val md5Digest = MessageDigest.getInstance("MD5")
        val sha256Digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(FileInputStream(file), BUFFER_SIZE).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                md5Digest.update(buffer, 0, read)
                sha256Digest.update(buffer, 0, read)
            }
        }
        Pair(md5Digest.digest().toHex(), sha256Digest.digest().toHex())
    }

    fun computeHash(file: File, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        BufferedInputStream(FileInputStream(file), BUFFER_SIZE).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    fun mapToDiagnosticError(operationTitle: String, targetPath: String, throwable: Throwable): DiagnosticError {
        val cause = throwable.cause ?: throwable
        val exactReason = buildString {
            append(throwable.message ?: "Unexpected cryptographic or I/O exception.")
            if (cause !== throwable && cause.message != null) {
                append(" | Root cause: ${cause.javaClass.simpleName}: ${cause.message}")
            }
        }
        val remediation = when {
            throwable is AEADBadTagException || exactReason.contains("Tag", ignoreCase = true) ->
                "Unlock the Secure Vault using the exact 4/6-digit PIN that was active when this file was encrypted."
            exactReason.contains("Permission denied", ignoreCase = true) || exactReason.contains("EACCES", ignoreCase = true) ->
                "Grant 'All Files Access' in Android Settings or select the folder via Storage Access Framework."
            exactReason.contains("ENOSPC", ignoreCase = true) ->
                "Insufficient free disk space to complete the atomic encryption/decryption stream."
            else ->
                "Verify file read/write permissions and available storage space."
        }
        return DiagnosticError(
            operationTitle = operationTitle,
            targetPathOrUrl = targetPath,
            exceptionClass = throwable.javaClass.name,
            errorCodeOrStatus = cause.javaClass.simpleName,
            exactReason = exactReason,
            remediationHint = remediation,
            stackSnippet = throwable.stackTraceToString().lineSequence().take(6).joinToString("\n")
        )
    }

    private fun deriveKeyBytes(secret: String, salt: ByteArray, keyLengthBytes: Int): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, PBKDF2_ITERATIONS, keyLengthBytes * 8)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGO)
        return factory.generateSecret(spec).encoded
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        val clean = if (length % 2 == 0) this else "0$this"
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
