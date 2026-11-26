package com.example.domain.engine

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import com.example.data.local.VaultItemEntity
import com.example.domain.model.DiagnosticError
import com.example.domain.model.FileNode
import com.example.domain.model.VaultAuthMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class CryptoVaultEngine(
    private val context: Context?,
    customVaultDir: File? = null
) {

    private val prefs: SharedPreferences? =
        context?.getSharedPreferences("luma_vault_security_prefs", Context.MODE_PRIVATE)

    // Store vault files inside noBackupFilesDir so Android Auto Backup, ADB backup, and external
    // file managers can NEVER access or index encrypted vault payloads.
    val vaultDir: File = (customVaultDir ?: File(
        context?.noBackupFilesDir ?: context?.filesDir ?: File(System.getProperty("java.io.tmpdir")),
        ".luma_vault_v11"
    )).apply {
        if (!exists()) mkdirs()
        val nomedia = File(this, ".nomedia")
        if (!nomedia.exists()) runCatching { nomedia.createNewFile() }
    }

    private val secureRandom = SecureRandom()

    // In-memory fallback store for headless JVM unit testing when Context is null
    private var memoryPinHash: String? = null
    private var memoryPinSalt: String? = null
    private var memoryAuthMode: String = VaultAuthMode.PIN_4.name
    private var memoryRecoveryQuestion: String = "What is your favorite security keyword?"
    private var memoryRecoveryHash: String? = null

    companion object {
        private const val KEY_PIN_HASH = "pin_verification_hash_v11"
        private const val KEY_PIN_SALT = "pin_verification_salt_v11"
        private const val KEY_AUTH_MODE = "vault_auth_mode_v11"
        private const val KEY_RECOVERY_QUESTION = "recovery_question_v11"
        private const val KEY_RECOVERY_HASH = "recovery_answer_hash_v11"
        private const val KEY_ONBOARDING_REMEMBERED = "permission_onboarding_remembered_v11"

        private const val PBKDF2_ALGO = "PBKDF2WithHmacSHA256"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        const val PBKDF2_ITERATIONS = 210_000
        private const val SALT_BYTES = 32 // 256-bit salt
        private const val IV_BYTES = 12   // 96-bit GCM IV
        private const val BUFFER_SIZE = 64 * 1024
    }

    fun isPermissionOnboardingRemembered(): Boolean =
        prefs?.getBoolean(KEY_ONBOARDING_REMEMBERED, false) ?: false

    fun setPermissionOnboardingRemembered(remembered: Boolean) {
        prefs?.edit()?.putBoolean(KEY_ONBOARDING_REMEMBERED, remembered)?.apply()
    }

    fun isPinConfigured(): Boolean =
        prefs?.contains(KEY_PIN_HASH) ?: (memoryPinHash != null)

    fun getConfiguredAuthMode(): VaultAuthMode {
        val raw = prefs?.getString(KEY_AUTH_MODE, VaultAuthMode.PIN_4.name) ?: memoryAuthMode
        return runCatching { VaultAuthMode.valueOf(raw) }.getOrDefault(VaultAuthMode.PIN_4)
    }

    fun getConfiguredPinLength(): Int = when (getConfiguredAuthMode()) {
        VaultAuthMode.PIN_4 -> 4
        VaultAuthMode.PIN_6 -> 6
        VaultAuthMode.ALPHANUMERIC_PASSWORD -> 0
    }

    fun getRecoveryQuestion(): String =
        prefs?.getString(KEY_RECOVERY_QUESTION, memoryRecoveryQuestion) ?: memoryRecoveryQuestion

    fun setupCredential(
        secret: String,
        authMode: VaultAuthMode,
        recoveryQuestion: String,
        recoveryAnswer: String
    ): Result<Unit> {
        return try {
            when (authMode) {
                VaultAuthMode.PIN_4 -> {
                    require(secret.length == 4 && secret.all { it.isDigit() }) {
                        "4-Digit PIN must contain exactly 4 numeric digits (0-9)."
                    }
                }
                VaultAuthMode.PIN_6 -> {
                    require(secret.length == 6 && secret.all { it.isDigit() }) {
                        "6-Digit PIN must contain exactly 6 numeric digits (0-9)."
                    }
                }
                VaultAuthMode.ALPHANUMERIC_PASSWORD -> {
                    require(secret.length >= 6) {
                        "Alphanumeric password must be at least 6 characters long."
                    }
                }
            }

            val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
            val verifyHash = deriveKeyBytes(secret, salt, 32).toHex()
            val cleanRecovery = recoveryAnswer.trim().lowercase()
            val recoveryHash = if (cleanRecovery.isNotEmpty()) {
                deriveKeyBytes(cleanRecovery, salt, 32).toHex()
            } else ""

            val question = recoveryQuestion.ifBlank { "What is your favorite security keyword?" }
            if (prefs != null) {
                prefs.edit()
                    .putString(KEY_PIN_HASH, verifyHash)
                    .putString(KEY_PIN_SALT, salt.toHex())
                    .putString(KEY_AUTH_MODE, authMode.name)
                    .putString(KEY_RECOVERY_QUESTION, question)
                    .putString(KEY_RECOVERY_HASH, recoveryHash)
                    .apply()
            } else {
                memoryPinHash = verifyHash
                memoryPinSalt = salt.toHex()
                memoryAuthMode = authMode.name
                memoryRecoveryQuestion = question
                memoryRecoveryHash = recoveryHash
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun setupPin(
        pin: String,
        pinLength: Int,
        recoveryQuestion: String,
        recoveryAnswer: String
    ): Result<Unit> {
        val mode = when (pinLength) {
            4 -> VaultAuthMode.PIN_4
            6 -> if (pin.all { it.isDigit() }) VaultAuthMode.PIN_6 else VaultAuthMode.ALPHANUMERIC_PASSWORD
            else -> VaultAuthMode.ALPHANUMERIC_PASSWORD
        }
        return setupCredential(pin, mode, recoveryQuestion, recoveryAnswer)
    }

    fun verifyPin(secret: String): Boolean {
        val storedHash = prefs?.getString(KEY_PIN_HASH, null) ?: memoryPinHash ?: return false
        val saltHex = prefs?.getString(KEY_PIN_SALT, null) ?: memoryPinSalt ?: return false
        val salt = saltHex.hexToBytes()
        val candidateHash = deriveKeyBytes(secret, salt, 32).toHex()
        return MessageDigest.isEqual(storedHash.toByteArray(), candidateHash.toByteArray())
    }

    fun resetPinWithRecovery(
        recoveryAnswer: String,
        newSecret: String,
        newAuthMode: VaultAuthMode = VaultAuthMode.PIN_4
    ): Boolean {
        val storedRecoveryHash = prefs?.getString(KEY_RECOVERY_HASH, null) ?: memoryRecoveryHash ?: return false
        val saltHex = prefs?.getString(KEY_PIN_SALT, null) ?: memoryPinSalt ?: return false
        if (storedRecoveryHash.isBlank()) return false
        val salt = saltHex.hexToBytes()
        val candidate = deriveKeyBytes(recoveryAnswer.trim().lowercase(), salt, 32).toHex()
        if (!MessageDigest.isEqual(storedRecoveryHash.toByteArray(), candidate.toByteArray())) {
            return false
        }
        val newHash = deriveKeyBytes(newSecret, salt, 32).toHex()
        if (prefs != null) {
            prefs.edit()
                .putString(KEY_PIN_HASH, newHash)
                .putString(KEY_AUTH_MODE, newAuthMode.name)
                .apply()
        } else {
            memoryPinHash = newHash
            memoryAuthMode = newAuthMode.name
        }
        return true
    }

    suspend fun encryptFileToVault(
        fileNode: FileNode,
        activeSecret: String,
        deleteOriginalAfterEncrypt: Boolean = true
    ): Result<VaultItemEntity> = withContext(Dispatchers.IO) {
        try {
            val isSaf = fileNode.isSafDocument && fileNode.safUriString != null && context != null
            val sourceFile = if (!isSaf) File(fileNode.path) else null
            if (!isSaf && (sourceFile == null || !sourceFile.exists() || !sourceFile.canRead())) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "Cannot read source file '${fileNode.path}'. Verify POSIX read permission or storage access grant."
                    )
                )
            }

            val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
            val iv = ByteArray(IV_BYTES).also { secureRandom.nextBytes(it) }
            val keyBytes = deriveKeyBytes(activeSecret, salt, 32)
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))

            // Opaque UUID filename obfuscation: zero filename or extension metadata on disk!
            val obfuscatedFileName = "${UUID.randomUUID().toString().replace("-", "")}.lvault"
            val encryptedDest = File(vaultDir, obfuscatedFileName)

            val sha256Digest = MessageDigest.getInstance("SHA-256")
            var totalReadBytes = 0L

            val inputStream: InputStream = if (isSaf) {
                context!!.contentResolver.openInputStream(Uri.parse(fileNode.safUriString!!))
                    ?: throw IllegalStateException("Unable to open SAF input stream for ${fileNode.name}")
            } else {
                FileInputStream(sourceFile!!)
            }

            BufferedInputStream(inputStream, BUFFER_SIZE).use { input ->
                CipherOutputStream(
                    BufferedOutputStream(FileOutputStream(encryptedDest), BUFFER_SIZE),
                    cipher
                ).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        sha256Digest.update(buffer, 0, bytesRead)
                        output.write(buffer, 0, bytesRead)
                        totalReadBytes += bytesRead
                    }
                    output.flush()
                }
            }

            val sha256Hex = sha256Digest.digest().toHex()

            // Securely shred (zero-overwrite) and delete the original file so no external file
            // manager or recovery scanner can ever read the unencrypted source bytes.
            if (deleteOriginalAfterEncrypt) {
                if (isSaf) {
                    runCatching {
                        DocumentsContract.deleteDocument(
                            context!!.contentResolver,
                            Uri.parse(fileNode.safUriString!!)
                        )
                    }
                } else if (sourceFile != null && sourceFile.canWrite()) {
                    shredAndDeleteFile(sourceFile)
                }
            }

            val entity = VaultItemEntity(
                originalName = fileNode.name,
                originalPath = fileNode.path,
                encryptedFilePath = encryptedDest.absolutePath,
                mimeType = fileNode.mimeType,
                formatCategory = fileNode.category.name,
                originalSizeBytes = if (totalReadBytes > 0) totalReadBytes else fileNode.sizeBytes,
                ivHex = iv.toHex(),
                saltHex = salt.toHex(),
                sha256Checksum = sha256Hex
            )
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Overwrites the entire file with 0x00 bytes, flushes to physical storage via fd.sync(),
     * renames to an anonymous temporary token, and unlinks it from the directory tree.
     */
    fun shredAndDeleteFile(target: File) {
        try {
            if (!target.exists() || !target.isFile) return
            val length = target.length()
            if (length > 0 && target.canWrite()) {
                RandomAccessFile(target, "rws").use { raf ->
                    raf.seek(0L)
                    val zeroChunk = ByteArray(minOf(length, BUFFER_SIZE.toLong()).toInt())
                    var remaining = length
                    while (remaining > 0) {
                        val toWrite = minOf(remaining, zeroChunk.size.toLong()).toInt()
                        raf.write(zeroChunk, 0, toWrite)
                        remaining -= toWrite
                    }
                    raf.fd.sync()
                }
            }
            val wipedFile = File(target.parentFile, ".wiped_${UUID.randomUUID()}")
            if (target.renameTo(wipedFile)) {
                wipedFile.delete()
            } else {
                target.delete()
            }
        } catch (_: Exception) {
            target.delete()
        }
    }

    suspend fun decryptVaultItemToDirectory(
        item: VaultItemEntity,
        activeSecret: String,
        targetDirectory: File,
        removeEncryptedCopy: Boolean = true
    ): Result<File> = withContext(Dispatchers.IO) {
        val encFile = File(item.encryptedFilePath)
        if (!encFile.exists()) {
            return@withContext Result.failure(
                IllegalStateException("Encrypted vault payload not found at ${item.encryptedFilePath}")
            )
        }
        val tempStagingFile = File(targetDirectory, ".luma_decrypt_${UUID.randomUUID()}.tmp")
        try {
            if (!targetDirectory.exists()) targetDirectory.mkdirs()

            val salt = item.saltHex.hexToBytes()
            val iv = item.ivHex.hexToBytes()
            val keyBytes = deriveKeyBytes(activeSecret, salt, 32)
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))

            val sha256Digest = MessageDigest.getInstance("SHA-256")

            BufferedInputStream(FileInputStream(encFile), BUFFER_SIZE).use { input ->
                BufferedOutputStream(FileOutputStream(tempStagingFile), BUFFER_SIZE).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        val decryptedChunk = cipher.update(buffer, 0, bytesRead)
                        if (decryptedChunk != null && decryptedChunk.isNotEmpty()) {
                            sha256Digest.update(decryptedChunk)
                            output.write(decryptedChunk)
                        }
                    }
                    val finalChunk = cipher.doFinal()
                    if (finalChunk != null && finalChunk.isNotEmpty()) {
                        sha256Digest.update(finalChunk)
                        output.write(finalChunk)
                    }
                    output.flush()
                }
            }

            val restoredSha256 = sha256Digest.digest().toHex()
            if (item.sha256Checksum.isNotEmpty() &&
                !restoredSha256.equals(item.sha256Checksum, ignoreCase = true)
            ) {
                shredAndDeleteFile(tempStagingFile)
                return@withContext Result.failure(
                    IllegalStateException(
                        "SHA-256 integrity mismatch after decryption (Expected ${item.sha256Checksum.take(12)}..., got ${restoredSha256.take(12)}...)."
                    )
                )
            }

            var outputFile = File(targetDirectory, item.originalName)
            if (outputFile.exists()) {
                val base = item.originalName.substringBeforeLast('.', item.originalName)
                val ext = item.originalName.substringAfterLast('.', "")
                val suffix = if (ext.isNotEmpty()) ".$ext" else ""
                outputFile = File(targetDirectory, "${base}_restored$suffix")
            }

            if (!tempStagingFile.renameTo(outputFile)) {
                tempStagingFile.copyTo(outputFile, overwrite = true)
                shredAndDeleteFile(tempStagingFile)
            }

            if (removeEncryptedCopy) {
                shredAndDeleteFile(encFile)
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            if (tempStagingFile.exists()) {
                shredAndDeleteFile(tempStagingFile)
            }
            if (e is AEADBadTagException || e.cause is AEADBadTagException) {
                Result.failure(
                    IllegalStateException(
                        "AES-256-GCM Authentication Tag Verification Failed: The provided PIN/Password does not match the encryption key used for '${item.originalName}', or the ciphertext was tampered with.",
                        e
                    )
                )
            } else {
                Result.failure(e)
            }
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

    fun mapToDiagnosticError(
        operationTitle: String,
        targetPath: String,
        throwable: Throwable
    ): DiagnosticError {
        val cause = throwable.cause ?: throwable
        val exactReason = buildString {
            append(throwable.message ?: "Unexpected cryptographic or I/O exception.")
            if (cause !== throwable && cause.message != null) {
                append(" | Root cause: ${cause.javaClass.simpleName}: ${cause.message}")
            }
        }
        val remediation = when {
            throwable is AEADBadTagException ||
                cause is AEADBadTagException ||
                exactReason.contains("Tag", ignoreCase = true) ->
                "Unlock the Secure Vault using the exact PIN or Alphanumeric Password that was active when this file was encrypted."
            exactReason.contains("Permission denied", ignoreCase = true) ||
                exactReason.contains("EACCES", ignoreCase = true) ->
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

    fun deriveKeyBytes(secret: String, salt: ByteArray, keyLengthBytes: Int): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, PBKDF2_ITERATIONS, keyLengthBytes * 8)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGO)
        return try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        val clean = if (length % 2 == 0) this else "0$this"
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
