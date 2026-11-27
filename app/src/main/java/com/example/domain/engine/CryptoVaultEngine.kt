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
import javax.crypto.Mac
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
    private var memoryRecoverySalt: String? = null
    private var memoryWrappedDekPrimary: String? = null
    private var memoryWrappedDekPrimaryIv: String? = null
    private var memoryWrappedDekRecovery: String? = null
    private var memoryWrappedDekRecoveryIv: String? = null

    companion object {
        private const val KEY_PIN_HASH = "pin_verification_hash_v12"
        private const val KEY_PIN_SALT = "pin_verification_salt_v12"
        private const val KEY_AUTH_MODE = "vault_auth_mode_v12"
        private const val KEY_RECOVERY_QUESTION = "recovery_question_v12"
        private const val KEY_RECOVERY_HASH = "recovery_answer_hash_v12"
        private const val KEY_RECOVERY_SALT = "recovery_salt_v12"
        private const val KEY_WRAPPED_DEK_PRIMARY = "wrapped_master_dek_primary_v12"
        private const val KEY_WRAPPED_DEK_PRIMARY_IV = "wrapped_master_dek_primary_iv_v12"
        private const val KEY_WRAPPED_DEK_RECOVERY = "wrapped_master_dek_recovery_v12"
        private const val KEY_WRAPPED_DEK_RECOVERY_IV = "wrapped_master_dek_recovery_iv_v12"
        private const val KEY_ONBOARDING_REMEMBERED = "permission_onboarding_remembered_v11"

        // Legacy v1.1 keys for seamless migration
        private const val LEGACY_KEY_PIN_HASH = "pin_verification_hash_v11"
        private const val LEGACY_KEY_PIN_SALT = "pin_verification_salt_v11"
        private const val LEGACY_KEY_AUTH_MODE = "vault_auth_mode_v11"

        private const val PBKDF2_ALGO = "PBKDF2WithHmacSHA256"
        private const val HMAC_SHA256_ALGO = "HmacSHA256"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        const val PBKDF2_ITERATIONS = 210_000
        private const val SALT_BYTES = 32 // 256-bit salt
        private const val DEK_BYTES = 32  // 256-bit Master Data Encryption Key
        private const val IV_BYTES = 12   // 96-bit GCM IV
        private const val BUFFER_SIZE = 64 * 1024
    }

    fun isPermissionOnboardingRemembered(): Boolean =
        prefs?.getBoolean(KEY_ONBOARDING_REMEMBERED, false) ?: false

    fun setPermissionOnboardingRemembered(remembered: Boolean) {
        prefs?.edit()?.putBoolean(KEY_ONBOARDING_REMEMBERED, remembered)?.apply()
    }

    fun isPinConfigured(): Boolean =
        (prefs?.contains(KEY_PIN_HASH) == true) ||
            (prefs?.contains(LEGACY_KEY_PIN_HASH) == true) ||
            (memoryPinHash != null)

    fun getConfiguredAuthMode(): VaultAuthMode {
        val raw = prefs?.getString(KEY_AUTH_MODE, null)
            ?: prefs?.getString(LEGACY_KEY_AUTH_MODE, null)
            ?: memoryAuthMode
        return runCatching { VaultAuthMode.valueOf(raw) }.getOrDefault(VaultAuthMode.PIN_4)
    }

    fun getConfiguredPinLength(): Int = when (getConfiguredAuthMode()) {
        VaultAuthMode.PIN_4 -> 4
        VaultAuthMode.PIN_6 -> 6
        VaultAuthMode.ALPHANUMERIC_PASSWORD -> 0
    }

    fun getRecoveryQuestion(): String =
        prefs?.getString(KEY_RECOVERY_QUESTION, memoryRecoveryQuestion) ?: memoryRecoveryQuestion

    private fun validateSecretForMode(secret: String, authMode: VaultAuthMode) {
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
    }

    /**
     * Initializes the v1.2.0 Wrapped 256-Bit Master DEK architecture:
     * 1. Generates a cryptographically random 256-bit Master DEK.
     * 2. Wraps the Master DEK with PrimaryKEK (210k PBKDF2 from user's PIN/Password).
     * 3. Independently wraps the same Master DEK with RecoveryKEK (210k PBKDF2 from recovery answer).
     */
    fun setupCredential(
        secret: String,
        authMode: VaultAuthMode,
        recoveryQuestion: String,
        recoveryAnswer: String
    ): Result<Unit> {
        return try {
            validateSecretForMode(secret, authMode)
            val cleanRecovery = recoveryAnswer.trim().lowercase()
            require(cleanRecovery.length >= 2) {
                "Recovery answer must be at least 2 characters so your Master DEK can be safely wrapped for password recovery."
            }

            val masterDek = ByteArray(DEK_BYTES).also { secureRandom.nextBytes(it) }

            val primarySalt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
            val primaryKek = deriveKeyBytes(secret, primarySalt, 32)
            val verifyHash = sha256Hex(primaryKek)
            val (wrappedPrimaryHex, primaryIvHex) = wrapDek(masterDek, primaryKek)

            val recoverySalt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
            val recoveryKek = deriveKeyBytes(cleanRecovery, recoverySalt, 32)
            val recoveryHash = sha256Hex(recoveryKek)
            val (wrappedRecoveryHex, recoveryIvHex) = wrapDek(masterDek, recoveryKek)

            val question = recoveryQuestion.ifBlank { "What is your favorite security keyword?" }
            if (prefs != null) {
                prefs.edit()
                    .putString(KEY_PIN_HASH, verifyHash)
                    .putString(KEY_PIN_SALT, primarySalt.toHex())
                    .putString(KEY_AUTH_MODE, authMode.name)
                    .putString(KEY_RECOVERY_QUESTION, question)
                    .putString(KEY_RECOVERY_HASH, recoveryHash)
                    .putString(KEY_RECOVERY_SALT, recoverySalt.toHex())
                    .putString(KEY_WRAPPED_DEK_PRIMARY, wrappedPrimaryHex)
                    .putString(KEY_WRAPPED_DEK_PRIMARY_IV, primaryIvHex)
                    .putString(KEY_WRAPPED_DEK_RECOVERY, wrappedRecoveryHex)
                    .putString(KEY_WRAPPED_DEK_RECOVERY_IV, recoveryIvHex)
                    .apply()
            } else {
                memoryPinHash = verifyHash
                memoryPinSalt = primarySalt.toHex()
                memoryAuthMode = authMode.name
                memoryRecoveryQuestion = question
                memoryRecoveryHash = recoveryHash
                memoryRecoverySalt = recoverySalt.toHex()
                memoryWrappedDekPrimary = wrappedPrimaryHex
                memoryWrappedDekPrimaryIv = primaryIvHex
                memoryWrappedDekRecovery = wrappedRecoveryHex
                memoryWrappedDekRecoveryIv = recoveryIvHex
            }
            masterDek.fill(0)
            primaryKek.fill(0)
            recoveryKek.fill(0)
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
        val unwrapped = runCatching { unwrapMasterDekWithSecret(secret) }.getOrNull()
        if (unwrapped != null) {
            unwrapped.fill(0)
            return true
        }
        // Fallback check for legacy v1.1 hash
        val legacyHash = prefs?.getString(LEGACY_KEY_PIN_HASH, null) ?: return false
        val legacySaltHex = prefs?.getString(LEGACY_KEY_PIN_SALT, null) ?: return false
        val candidateHash = deriveKeyBytes(secret, legacySaltHex.hexToBytes(), 32).toHex()
        return MessageDigest.isEqual(legacyHash.toByteArray(), candidateHash.toByteArray())
    }

    /**
     * Lossless Password Recovery via Wrapped 256-Bit Master DEK:
     * 1. Derives RecoveryKEK from the recovery answer + recoverySalt (210,000 PBKDF2 iterations).
     * 2. Unwraps the 256-bit Master DEK using RecoveryKEK.
     * 3. Derives a fresh PrimaryKEK from the new PIN/Password + fresh 32-byte salt.
     * 4. Re-wraps the exact same 256-bit Master DEK under the new PrimaryKEK.
     * All existing encrypted vault files remain 100% accessible without re-encryption or data loss.
     */
    fun resetPinWithRecovery(
        recoveryAnswer: String,
        newSecret: String,
        newAuthMode: VaultAuthMode = VaultAuthMode.PIN_4
    ): Boolean {
        return try {
            validateSecretForMode(newSecret, newAuthMode)
            val cleanRecovery = recoveryAnswer.trim().lowercase()
            if (cleanRecovery.isEmpty()) return false

            val storedRecoveryHash = prefs?.getString(KEY_RECOVERY_HASH, null) ?: memoryRecoveryHash ?: return false
            val recoverySaltHex = prefs?.getString(KEY_RECOVERY_SALT, null) ?: memoryRecoverySalt ?: return false
            val wrappedRecoveryHex = prefs?.getString(KEY_WRAPPED_DEK_RECOVERY, null) ?: memoryWrappedDekRecovery ?: return false
            val recoveryIvHex = prefs?.getString(KEY_WRAPPED_DEK_RECOVERY_IV, null) ?: memoryWrappedDekRecoveryIv ?: return false

            val recoveryKek = deriveKeyBytes(cleanRecovery, recoverySaltHex.hexToBytes(), 32)
            val candidateRecoveryHash = sha256Hex(recoveryKek)
            if (!MessageDigest.isEqual(storedRecoveryHash.toByteArray(), candidateRecoveryHash.toByteArray())) {
                recoveryKek.fill(0)
                return false
            }

            // Unwrap the 256-bit Master DEK using RecoveryKEK
            val masterDek = unwrapDek(wrappedRecoveryHex, recoveryIvHex, recoveryKek)
            recoveryKek.fill(0)

            // Re-wrap the Master DEK under the user's new PIN / Alphanumeric Password
            val newPrimarySalt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
            val newPrimaryKek = deriveKeyBytes(newSecret, newPrimarySalt, 32)
            val newVerifyHash = sha256Hex(newPrimaryKek)
            val (newWrappedPrimaryHex, newPrimaryIvHex) = wrapDek(masterDek, newPrimaryKek)

            masterDek.fill(0)
            newPrimaryKek.fill(0)

            if (prefs != null) {
                prefs.edit()
                    .putString(KEY_PIN_HASH, newVerifyHash)
                    .putString(KEY_PIN_SALT, newPrimarySalt.toHex())
                    .putString(KEY_AUTH_MODE, newAuthMode.name)
                    .putString(KEY_WRAPPED_DEK_PRIMARY, newWrappedPrimaryHex)
                    .putString(KEY_WRAPPED_DEK_PRIMARY_IV, newPrimaryIvHex)
                    .apply()
            } else {
                memoryPinHash = newVerifyHash
                memoryPinSalt = newPrimarySalt.toHex()
                memoryAuthMode = newAuthMode.name
                memoryWrappedDekPrimary = newWrappedPrimaryHex
                memoryWrappedDekPrimaryIv = newPrimaryIvHex
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Rotates the user's PIN/Password while the vault is unlocked by unwrapping the Master DEK
     * with the current credential and re-wrapping it with the new credential.
     */
    fun rotateCredentialWhileUnlocked(
        currentSecret: String,
        newSecret: String,
        newAuthMode: VaultAuthMode
    ): Result<Unit> {
        return try {
            validateSecretForMode(newSecret, newAuthMode)
            val masterDek = unwrapMasterDekWithSecret(currentSecret)
            val newPrimarySalt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
            val newPrimaryKek = deriveKeyBytes(newSecret, newPrimarySalt, 32)
            val newVerifyHash = sha256Hex(newPrimaryKek)
            val (newWrappedPrimaryHex, newPrimaryIvHex) = wrapDek(masterDek, newPrimaryKek)

            masterDek.fill(0)
            newPrimaryKek.fill(0)

            if (prefs != null) {
                prefs.edit()
                    .putString(KEY_PIN_HASH, newVerifyHash)
                    .putString(KEY_PIN_SALT, newPrimarySalt.toHex())
                    .putString(KEY_AUTH_MODE, newAuthMode.name)
                    .putString(KEY_WRAPPED_DEK_PRIMARY, newWrappedPrimaryHex)
                    .putString(KEY_WRAPPED_DEK_PRIMARY_IV, newPrimaryIvHex)
                    .apply()
            } else {
                memoryPinHash = newVerifyHash
                memoryPinSalt = newPrimarySalt.toHex()
                memoryAuthMode = newAuthMode.name
                memoryWrappedDekPrimary = newWrappedPrimaryHex
                memoryWrappedDekPrimaryIv = newPrimaryIvHex
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun unwrapMasterDekWithSecret(secret: String): ByteArray {
        val storedHash = prefs?.getString(KEY_PIN_HASH, null) ?: memoryPinHash
            ?: throw IllegalStateException("Vault credential is not configured.")
        val primarySaltHex = prefs?.getString(KEY_PIN_SALT, null) ?: memoryPinSalt
            ?: throw IllegalStateException("Vault primary salt is missing.")
        val wrappedPrimaryHex = prefs?.getString(KEY_WRAPPED_DEK_PRIMARY, null) ?: memoryWrappedDekPrimary
            ?: throw IllegalStateException("Wrapped Master DEK is missing.")
        val primaryIvHex = prefs?.getString(KEY_WRAPPED_DEK_PRIMARY_IV, null) ?: memoryWrappedDekPrimaryIv
            ?: throw IllegalStateException("Wrapped Master DEK IV is missing.")

        val primaryKek = deriveKeyBytes(secret, primarySaltHex.hexToBytes(), 32)
        val candidateHash = sha256Hex(primaryKek)
        if (!MessageDigest.isEqual(storedHash.toByteArray(), candidateHash.toByteArray())) {
            primaryKek.fill(0)
            throw AEADBadTagException("Invalid Vault PIN or Master Password.")
        }
        return try {
            unwrapDek(wrappedPrimaryHex, primaryIvHex, primaryKek)
        } finally {
            primaryKek.fill(0)
        }
    }

    private fun wrapDek(dekBytes: ByteArray, kekBytes: ByteArray): Pair<String, String> {
        val iv = ByteArray(IV_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kekBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val wrapped = cipher.doFinal(dekBytes)
        return Pair(wrapped.toHex(), iv.toHex())
    }

    private fun unwrapDek(wrappedHex: String, ivHex: String, kekBytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(kekBytes, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, ivHex.hexToBytes())
        )
        return cipher.doFinal(wrappedHex.hexToBytes())
    }

    /**
     * Derives a unique per-file 256-bit AES key from the 256-bit Master DEK and a per-file 256-bit random salt
     * using HMAC-SHA256 (HKDF-Expand).
     */
    private fun deriveFileSubKeyFromMasterDek(masterDek: ByteArray, fileSalt: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_SHA256_ALGO)
        mac.init(SecretKeySpec(masterDek, HMAC_SHA256_ALGO))
        mac.update(fileSalt)
        mac.update("LUMA_VAULT_V12_FILE_SUBKEY".toByteArray(Charsets.UTF_8))
        return mac.doFinal()
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

            val fileSalt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
            val iv = ByteArray(IV_BYTES).also { secureRandom.nextBytes(it) }

            val masterDek = runCatching { unwrapMasterDekWithSecret(activeSecret) }.getOrNull()
            val fileKeyBytes = if (masterDek != null) {
                try {
                    deriveFileSubKeyFromMasterDek(masterDek, fileSalt)
                } finally {
                    masterDek.fill(0)
                }
            } else {
                deriveKeyBytes(activeSecret, fileSalt, 32)
            }
            val secretKey = SecretKeySpec(fileKeyBytes, "AES")

            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))

            // Opaque UUID filename obfuscation: zero filename or extension metadata on disk
            val obfuscatedFileName = "${UUID.randomUUID().toString().replace("-", "")}.lvault"
            val encryptedDest = File(vaultDir, obfuscatedFileName)
            val tempEncryptedFile = File(vaultDir, ".luma_enc_${UUID.randomUUID()}.tmp")

            val sha256Digest = MessageDigest.getInstance("SHA-256")
            var totalReadBytes = 0L

            val inputStream: InputStream = if (isSaf) {
                context!!.contentResolver.openInputStream(Uri.parse(fileNode.safUriString!!))
                    ?: throw IllegalStateException("Unable to open SAF input stream for ${fileNode.name}")
            } else {
                FileInputStream(sourceFile!!)
            }

            try {
                BufferedInputStream(inputStream, BUFFER_SIZE).use { input ->
                    BufferedOutputStream(FileOutputStream(tempEncryptedFile), BUFFER_SIZE).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            sha256Digest.update(buffer, 0, bytesRead)
                            val encryptedChunk = cipher.update(buffer, 0, bytesRead)
                            if (encryptedChunk != null && encryptedChunk.isNotEmpty()) {
                                output.write(encryptedChunk)
                            }
                            totalReadBytes += bytesRead
                        }
                        val finalChunk = cipher.doFinal()
                        if (finalChunk != null && finalChunk.isNotEmpty()) {
                            output.write(finalChunk)
                        }
                        output.flush()
                    }
                }
                if (!tempEncryptedFile.renameTo(encryptedDest)) {
                    tempEncryptedFile.copyTo(encryptedDest, overwrite = true)
                    shredAndDeleteFile(tempEncryptedFile)
                }
            } finally {
                fileKeyBytes.fill(0)
                if (tempEncryptedFile.exists()) {
                    shredAndDeleteFile(tempEncryptedFile)
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
                saltHex = fileSalt.toHex(),
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
            if (!target.exists()) return
            if (target.isDirectory) {
                target.listFiles()?.forEach { child -> shredAndDeleteFile(child) }
                target.delete()
                return
            }
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

            val fileSalt = item.saltHex.hexToBytes()
            val iv = item.ivHex.hexToBytes()

            // Primary path: unwrap v1.2.0 Master DEK and derive per-file subkey.
            // If vault is configured with Master DEK, unwrapMasterDekWithSecret enforces authentication.
            val hasWrappedMasterDek =
                (prefs?.contains(KEY_WRAPPED_DEK_PRIMARY) == true) || (memoryWrappedDekPrimary != null)
            val fileKeyBytes = if (hasWrappedMasterDek) {
                val masterDek = unwrapMasterDekWithSecret(activeSecret)
                try {
                    deriveFileSubKeyFromMasterDek(masterDek, fileSalt)
                } finally {
                    masterDek.fill(0)
                }
            } else {
                deriveKeyBytes(activeSecret, fileSalt, 32)
            }

            val secretKey = SecretKeySpec(fileKeyBytes, "AES")
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))

            val sha256Digest = MessageDigest.getInstance("SHA-256")

            try {
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
            } finally {
                fileKeyBytes.fill(0)
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
                        "AES-256-GCM Authentication Tag Verification Failed: The provided PIN/Password is invalid or the ciphertext was tampered with.",
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
                exactReason.contains("Tag", ignoreCase = true) ||
                exactReason.contains("PIN", ignoreCase = true) ->
                "Unlock the Secure Vault using your active PIN/Master Password, or use Password Recovery to unwrap the 256-bit Master DEK."
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

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        val clean = if (length % 2 == 0) this else "0$this"
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
