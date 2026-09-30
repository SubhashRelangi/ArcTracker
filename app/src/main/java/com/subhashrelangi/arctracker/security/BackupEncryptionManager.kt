package com.subhashrelangi.arctracker.security

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Custom exception thrown when decryption fails due to an invalid passphrase or tampered ciphertext.
 */
class InvalidPassphraseException(message: String = "Incorrect passphrase or corrupted backup file.") : Exception(message)

/**
 * Standard envelope representation for AES-256-GCM encrypted backup files.
 */
data class EncryptedBackupEnvelope(
    @SerializedName("formatVersion")
    val formatVersion: Int = 1,
    @SerializedName("isEncrypted")
    val isEncrypted: Boolean = true,
    @SerializedName("algorithm")
    val algorithm: String = "AES-256-GCM",
    @SerializedName("kdf")
    val kdf: String = "PBKDF2WithHmacSHA256",
    @SerializedName("iterations")
    val iterations: Int = 65536,
    @SerializedName("salt")
    val salt: String,
    @SerializedName("iv")
    val iv: String,
    @SerializedName("ciphertext")
    val ciphertext: String
)

/**
 * Production-quality, hardware-standard AES-256-GCM Authenticated Encryption Manager (Milestone 16).
 *
 * Guarantees:
 * - Uses standard Android javax.crypto platform APIs.
 * - Authenticated encryption (AES-256-GCM with 128-bit authentication tag).
 * - Key derivation via PBKDF2WithHmacSHA256 with 65,536 iterations.
 * - Cryptographically secure 128-bit salt and 96-bit nonce/IV per file.
 * - Clears passphrase memory immediately after key derivation.
 * - Safe detection of encrypted vs unencrypted backups.
 */
object BackupEncryptionManager {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 65536
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12

    private val secureRandom = SecureRandom()
    private val gson = Gson()

    /**
     * Checks if a JSON string represents an encrypted backup envelope.
     */
    fun isEncryptedBackup(json: String): Boolean {
        val trimmed = json.trim()
        return trimmed.startsWith("{") &&
            trimmed.contains("\"isEncrypted\"") &&
            trimmed.contains("true") &&
            trimmed.contains("\"ciphertext\"")
    }

    /**
     * Encrypts plaintext JSON string using AES-256-GCM with a user-supplied passphrase.
     */
    fun encrypt(plaintext: String, passphrase: CharArray): String {
        if (passphrase.isEmpty()) {
            throw IllegalArgumentException("Passphrase cannot be empty")
        }

        // Generate random 16-byte salt and 12-byte IV
        val salt = ByteArray(SALT_LENGTH_BYTES)
        val iv = ByteArray(IV_LENGTH_BYTES)
        secureRandom.nextBytes(salt)
        secureRandom.nextBytes(iv)

        // Derive 256-bit AES key via PBKDF2
        val key = deriveKey(passphrase, salt)

        try {
            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

            val plaintextBytes = plaintext.toByteArray(StandardCharsets.UTF_8)
            val ciphertext = cipher.doFinal(plaintextBytes)

            val envelope = EncryptedBackupEnvelope(
                formatVersion = 1,
                isEncrypted = true,
                algorithm = "AES-256-GCM",
                kdf = KDF_ALGORITHM,
                iterations = ITERATIONS,
                salt = Base64Compat.encodeToString(salt),
                iv = Base64Compat.encodeToString(iv),
                ciphertext = Base64Compat.encodeToString(ciphertext)
            )

            return gson.toJson(envelope)
        } finally {
            // Safely clear passphrase array from memory
            Arrays.fill(passphrase, '\u0000')
        }
    }

    /**
     * Decrypts an [EncryptedBackupEnvelope] JSON string using the supplied passphrase.
     * Throws [InvalidPassphraseException] if passphrase is incorrect or data was tampered with.
     */
    fun decrypt(encryptedJson: String, passphrase: CharArray): String {
        if (passphrase.isEmpty()) {
            throw IllegalArgumentException("Passphrase cannot be empty")
        }

        val envelope = try {
            gson.fromJson(encryptedJson, EncryptedBackupEnvelope::class.java)
                ?: throw IllegalArgumentException("Invalid encrypted backup structure")
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse encrypted backup envelope: ${e.message}", e)
        }

        val salt = Base64Compat.decode(envelope.salt)
        val iv = Base64Compat.decode(envelope.iv)
        val ciphertext = Base64Compat.decode(envelope.ciphertext)

        val key = deriveKey(passphrase, salt)

        try {
            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

            val decryptedBytes = cipher.doFinal(ciphertext)
            return String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: AEADBadTagException) {
            throw InvalidPassphraseException("Incorrect passphrase or corrupted backup file.")
        } catch (e: javax.crypto.BadPaddingException) {
            throw InvalidPassphraseException("Incorrect passphrase or corrupted backup file.")
        } catch (e: Exception) {
            if (e.cause is AEADBadTagException || e.cause is javax.crypto.BadPaddingException) {
                throw InvalidPassphraseException("Incorrect passphrase or corrupted backup file.")
            }
            throw e
        } finally {
            Arrays.fill(passphrase, '\u0000')
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_LENGTH_BITS)
        val secretKey = factory.generateSecret(spec)
        return SecretKeySpec(secretKey.encoded, "AES")
    }
}

/**
 * Cross-platform Base64 helper compatible with Android runtime and JVM unit test runners.
 */
internal object Base64Compat {
    fun encodeToString(bytes: ByteArray): String {
        val androidBase64 = try {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (t: Throwable) {
            null
        }
        return androidBase64 ?: java.util.Base64.getEncoder().encodeToString(bytes)
    }

    fun decode(base64: String): ByteArray {
        val androidDecoded = try {
            android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
        } catch (t: Throwable) {
            null
        }
        return androidDecoded ?: java.util.Base64.getDecoder().decode(base64)
    }
}
