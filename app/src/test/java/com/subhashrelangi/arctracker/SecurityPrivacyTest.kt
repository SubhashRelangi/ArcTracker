package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.security.BackupEncryptionManager
import com.subhashrelangi.arctracker.security.InvalidPassphraseException
import com.subhashrelangi.arctracker.security.SensitiveDataSanitizer
import org.junit.Assert.*
import org.junit.Test

/**
 * Milestone 16: Security & Privacy Unit Tests.
 *
 * Verifies:
 * 1. Sensitive data sanitization (cards, accounts, OTP, PIN, error messages).
 * 2. AES-256-GCM authenticated encryption and decryption.
 * 3. Passphrase derivation (PBKDF2) and wrong-passphrase rejection.
 * 4. Authenticated ciphertext integrity and tamper detection.
 * 5. Format detection and backwards compatibility with unencrypted backups.
 */
class SecurityPrivacyTest {

    // =========================================================================
    // 1. SENSITIVE DATA SANITIZER TESTS
    // =========================================================================

    @Test
    fun testCardNumberMasking() {
        val input = "Payment done using Card 4111222233334444 at Store"
        val sanitized = SensitiveDataSanitizer.sanitize(input)
        assertFalse("Card number should not be visible", sanitized.contains("4111222233334444"))
        assertTrue("Card suffix should be preserved", sanitized.contains("•••• 4444"))
    }

    @Test
    fun testHyphenatedCardNumberMasking() {
        val input = "Card 5500-1234-5678-9999 charged with USD 50"
        val sanitized = SensitiveDataSanitizer.sanitize(input)
        assertFalse("Hyphenated card number should not be visible", sanitized.contains("5500-1234-5678-9999"))
        assertTrue("Card suffix should be preserved", sanitized.contains("•••• 9999"))
    }

    @Test
    fun testAccountNumberMasking() {
        val input = "A/c 123456789012 debited for INR 1,500.00"
        val sanitized = SensitiveDataSanitizer.sanitize(input)
        assertFalse("Account number should not be visible", sanitized.contains("123456789012"))
        assertTrue("Account suffix should be preserved", sanitized.contains("•••• 9012"))
    }

    @Test
    fun testOtpAndPinRedaction() {
        val input = "Your OTP is 492019 for login. Do not share. Your PIN is 1234."
        val sanitized = SensitiveDataSanitizer.sanitize(input)
        assertFalse("OTP number should be redacted", sanitized.contains("492019"))
        assertTrue("Placeholder should be present", sanitized.contains("[REDACTED]"))
        assertFalse("PIN should be redacted", sanitized.contains("1234"))
    }

    @Test
    fun testSafeAccountSuffixFormatting() {
        assertEquals("•••• 1234", SensitiveDataSanitizer.maskAccountSuffix("1234"))
        assertEquals("•••• 5678", SensitiveDataSanitizer.maskAccountSuffix("5678"))
        assertEquals("•••• 9999", SensitiveDataSanitizer.maskAccountSuffix("•••• 9999"))
        assertEquals("•••• 0001", SensitiveDataSanitizer.maskAccountSuffix("000000000001"))
        assertEquals("", SensitiveDataSanitizer.maskAccountSuffix(null))
        assertEquals("", SensitiveDataSanitizer.maskAccountSuffix(""))
    }

    @Test
    fun testErrorMessageSanitization() {
        val sqliteError = Exception("android.database.sqlite.SQLiteConstraintException: UNIQUE constraint failed: expenses.id")
        val sanitizedMsg = SensitiveDataSanitizer.sanitizeErrorMessage(sqliteError)
        assertFalse("Internal SQLite details should not leak", sanitizedMsg.contains("UNIQUE constraint"))
        assertFalse("Table names should not leak", sanitizedMsg.contains("expenses.id"))
        assertTrue("User-friendly message returned", sanitizedMsg.contains("Database operation failed"))

        val pathError = Exception("FileNotFoundException: /data/user/0/com.subhashrelangi.arctracker/databases/arctracker.db")
        val sanitizedPath = SensitiveDataSanitizer.sanitizeErrorMessage(pathError)
        assertFalse("Internal filesystem paths should not leak", sanitizedPath.contains("/data/user/0"))
    }

    // =========================================================================
    // 2. AES-256-GCM BACKUP ENCRYPTION TESTS
    // =========================================================================

    @Test
    fun testBackupEncryptionAndDecryptionRoundTrip() {
        val originalPayload = """{"formatVersion":1,"exportedAt":1700000000000,"transactions":[{"id":"tx1","amount":500.0}]}"""
        val passphrase = "StrongPassword@123".toCharArray()

        // Encrypt
        val encryptedEnvelope = BackupEncryptionManager.encrypt(originalPayload, passphrase)
        assertNotNull(encryptedEnvelope)
        assertTrue(BackupEncryptionManager.isEncryptedBackup(encryptedEnvelope))

        // Inspect envelope JSON
        val envelopeJson = com.google.gson.JsonParser.parseString(encryptedEnvelope).asJsonObject
        assertTrue(envelopeJson.get("isEncrypted").asBoolean)
        assertEquals("AES-256-GCM", envelopeJson.get("algorithm").asString)
        assertEquals("PBKDF2WithHmacSHA256", envelopeJson.get("kdf").asString)
        assertEquals(65536, envelopeJson.get("iterations").asInt)
        assertTrue(envelopeJson.has("salt"))
        assertTrue(envelopeJson.has("iv"))
        assertTrue(envelopeJson.has("ciphertext"))

        // Decrypt with correct passphrase
        val decrypted = BackupEncryptionManager.decrypt(encryptedEnvelope, "StrongPassword@123".toCharArray())
        assertEquals("Decrypted payload must match original", originalPayload, decrypted)
    }

    @Test(expected = InvalidPassphraseException::class)
    fun testDecryptionFailsWithWrongPassphrase() {
        val originalPayload = """{"test":"secret_financial_data"}"""
        val encryptedEnvelope = BackupEncryptionManager.encrypt(originalPayload, "CorrectPassword123".toCharArray())

        // Attempt decrypt with wrong passphrase
        BackupEncryptionManager.decrypt(encryptedEnvelope, "WrongPassword456".toCharArray())
    }

    @Test(expected = InvalidPassphraseException::class)
    fun testTamperedCiphertextIsRejected() {
        val originalPayload = """{"test":"secret_financial_data"}"""
        val encryptedEnvelope = BackupEncryptionManager.encrypt(originalPayload, "MyPassword".toCharArray())

        // Tamper with the ciphertext by flipping characters
        val envelopeJson = com.google.gson.JsonParser.parseString(encryptedEnvelope).asJsonObject
        val ciphertext = envelopeJson.get("ciphertext").asString
        val tamperedCiphertext = if (ciphertext.startsWith("A")) "B" + ciphertext.substring(1) else "A" + ciphertext.substring(1)
        envelopeJson.addProperty("ciphertext", tamperedCiphertext)

        // Decrypt should fail authenticated tag verification
        BackupEncryptionManager.decrypt(envelopeJson.toString(), "MyPassword".toCharArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testEmptyPassphraseIsRejectedOnEncrypt() {
        BackupEncryptionManager.encrypt("some data", "".toCharArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testEmptyPassphraseIsRejectedOnDecrypt() {
        BackupEncryptionManager.decrypt("""{"isEncrypted":true}""", "".toCharArray())
    }

    // =========================================================================
    // 3. BACKWARD COMPATIBILITY TESTS
    // =========================================================================

    @Test
    fun testUnencryptedM15BackupIsNotDetectedAsEncrypted() {
        val m15Backup = """
            {
                "formatVersion": 1,
                "exportedAt": 1727700000000,
                "appVersion": "1.0",
                "databaseVersion": 10,
                "transactions": [],
                "categories": [],
                "accounts": [],
                "rules": [],
                "aliases": [],
                "budgets": []
            }
        """.trimIndent()

        assertFalse("M15 unencrypted backup should not be detected as encrypted",
            BackupEncryptionManager.isEncryptedBackup(m15Backup))
    }

    @Test
    fun testRandomTextIsNotDetectedAsEncrypted() {
        assertFalse(BackupEncryptionManager.isEncryptedBackup("Hello, this is not JSON"))
        assertFalse(BackupEncryptionManager.isEncryptedBackup("{}"))
        assertFalse(BackupEncryptionManager.isEncryptedBackup("""{"isEncrypted":false}"""))
    }
}
