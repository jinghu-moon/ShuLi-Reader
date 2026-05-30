package com.shuli.reader.sync.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.SecureRandom

// Part of T-26 PBKDF2 密钥派生
class KeyDerivationTest {

    @Test
    fun `deriveKey uses PBKDF2WithHmacSHA256 with 600000 iterations`() {
        val params = KeyDerivationParams(
            algorithm = "PBKDF2WithHmacSHA256",
            iterations = 600_000,
            keyLengthBits = 256
        )
        val kdf = KeyDerivation(params)
        val key = kdf.derive(password = "test-password", salt = ByteArray(16) { it.toByte() })
        assertEquals(32, key.size) // 256 bits = 32 bytes
        assertNotEquals(ByteArray(32).toList(), key.toList()) // 非全零
    }

    @Test
    fun `same password and salt produce same key deterministically`() {
        val kdf = KeyDerivation(defaultParams())
        val salt = SecureRandom().generateSeed(16)
        val k1 = kdf.derive("password", salt)
        val k2 = kdf.derive("password", salt)
        assertEquals(k1.toList(), k2.toList())
    }

    @Test
    fun `different passwords produce different keys`() {
        val kdf = KeyDerivation(defaultParams())
        val salt = SecureRandom().generateSeed(16)
        assertNotEquals(
            kdf.derive("password1", salt).toList(),
            kdf.derive("password2", salt).toList()
        )
    }

    @Test
    fun `iterations below 600000 is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            KeyDerivationParams(algorithm = "PBKDF2WithHmacSHA256", iterations = 310_000, keyLengthBits = 256)
        }
    }

    private fun defaultParams() = KeyDerivationParams(
        algorithm = "PBKDF2WithHmacSHA256",
        iterations = 600_000,
        keyLengthBits = 256
    )
}
