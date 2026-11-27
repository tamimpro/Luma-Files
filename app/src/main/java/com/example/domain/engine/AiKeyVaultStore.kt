package com.example.domain.engine

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Stores an optional user-supplied Gemini API key encrypted at rest (AES-256-GCM)
 * inside app-private no-backup storage for sideloaded APKs running outside AI Studio.
 * Never hardcodes or bundles any API key.
 */
class AiKeyVaultStore(
    private val context: Context? = null,
    private val customStoreDir: File? = null
) {
    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "luma_files_user_ai_key_v1"
        private const val FILE_NAME = ".luma_ai_key.enc"
        private const val GCM_TAG_BITS = 128
        private const val IV_LEN = 12
    }

    private fun resolveKeyFile(): File {
        val dir = customStoreDir
            ?: context?.noBackupFilesDir
            ?: context?.filesDir
            ?: File(System.getProperty("java.io.tmpdir") ?: ".", "luma_ai_key_store")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, FILE_NAME)
    }

    fun hasApiKey(): Boolean = getApiKey().isNotBlank()

    fun getApiKey(): String {
        val file = resolveKeyFile()
        if (!file.exists()) return ""
        return runCatching {
            val raw = file.readText(Charsets.UTF_8).trim()
            if (raw.isBlank()) return ""
            val parts = raw.split(':')
            if (parts.size != 3) return ""
            val mode = parts[0]
            val iv = decodeB64(parts[1])
            val cipherBytes = decodeB64(parts[2])
            val key = if (mode == "KS") getOrCreateKeystoreSecretKey() ?: return "" else deriveFallbackLocalKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8).trim()
        }.getOrDefault("")
    }

    fun saveApiKey(rawKey: String) {
        val trimmed = rawKey.trim()
        val file = resolveKeyFile()
        if (trimmed.isBlank()) {
            clearApiKey()
            return
        }
        val ksKey = getOrCreateKeystoreSecretKey()
        val mode = if (ksKey != null) "KS" else "FB"
        val secretKey = ksKey ?: deriveFallbackLocalKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv: ByteArray
        val encrypted: ByteArray
        if (mode == "KS") {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            iv = cipher.iv
            encrypted = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
        } else {
            iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            encrypted = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
        }
        file.writeText("$mode:${encodeB64(iv)}:${encodeB64(encrypted)}", Charsets.UTF_8)
    }

    fun clearApiKey() {
        runCatching {
            val file = resolveKeyFile()
            if (file.exists()) {
                file.writeBytes(ByteArray(32))
                file.delete()
            }
        }
    }

    fun getMaskedPreview(): String {
        val key = getApiKey()
        if (key.isBlank()) return ""
        if (key.length <= 8) return "••••••••"
        return "${key.take(4)}••••••••${key.takeLast(4)}"
    }

    private fun getOrCreateKeystoreSecretKey(): SecretKey? {
        if (context == null) return null
        return runCatching {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            (ks.getKey(KEY_ALIAS, null) as? SecretKey) ?: run {
                val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGen.init(spec)
                keyGen.generateKey()
            }
        }.getOrNull()
    }

    private fun deriveFallbackLocalKey(): SecretKey {
        val seed = (context?.packageName ?: "com.lumafiles.filemanager") + "|" + resolveKeyFile().parent
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }

    private fun encodeB64(bytes: ByteArray): String =
        runCatching { Base64.encodeToString(bytes, Base64.NO_WRAP) }
            .getOrElse { java.util.Base64.getEncoder().encodeToString(bytes) }

    private fun decodeB64(str: String): ByteArray =
        runCatching { Base64.decode(str, Base64.NO_WRAP) }
            .getOrElse { java.util.Base64.getDecoder().decode(str) }
}
