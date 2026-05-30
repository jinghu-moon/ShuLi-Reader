package com.shuli.reader.sync.crypto

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Part of T-28 crypto.json 完整性保护
class CryptoMetadataManagerTest {

    @Test
    fun `computeIntegrity produces non-empty HMAC`() {
        val masterKey = ByteArray(32) { it.toByte() }
        val meta = CryptoMetadata(
            kdf = "PBKDF2WithHmacSHA256",
            iterations = 600_000,
            salt = "aGVsbG8=",
            cipher = "AES-256-GCM",
            keyVersion = 1,
            createdAt = 0L,
            integrity = ""
        )
        val signed = CryptoMetadataManager.computeIntegrity(meta, masterKey)
        assertNotEquals("", signed.integrity)
    }

    @Test
    fun `verifyIntegrity returns true for untampered data`() {
        val masterKey = ByteArray(32) { it.toByte() }
        val meta = CryptoMetadata(
            kdf = "PBKDF2WithHmacSHA256",
            iterations = 600_000,
            salt = "aGVsbG8=",
            cipher = "AES-256-GCM",
            keyVersion = 1,
            createdAt = 0L,
            integrity = ""
        )
        val signed = CryptoMetadataManager.computeIntegrity(meta, masterKey)
        assertTrue(CryptoMetadataManager.verifyIntegrity(signed, masterKey))
    }

    @Test
    fun `verifyIntegrity returns false for tampered iterations`() {
        val masterKey = ByteArray(32) { it.toByte() }
        val meta = CryptoMetadata(
            kdf = "PBKDF2WithHmacSHA256",
            iterations = 600_000,
            salt = "aGVsbG8=",
            cipher = "AES-256-GCM",
            keyVersion = 1,
            createdAt = 0L,
            integrity = ""
        )
        val signed = CryptoMetadataManager.computeIntegrity(meta, masterKey)
        val tampered = signed.copy(iterations = 1)
        assertFalse(CryptoMetadataManager.verifyIntegrity(tampered, masterKey))
    }
}
