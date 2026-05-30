package com.shuli.reader.sync.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

// Part of T-29 SyncCryptoManager — 完整 E2EE 管道
class SyncCryptoManagerTest {

    @Test
    fun `encrypt then decrypt roundtrip for manifest`() {
        val manager = SyncCryptoManager(AesGcmCipher(), masterKey = ByteArray(32) { it.toByte() })
        val json = """{"schemaVersion":2,"version":1}"""
        val encrypted = manager.encrypt(json.toByteArray())
        val decrypted = manager.decrypt(encrypted)
        assertEquals(json, String(decrypted))
    }

    @Test
    fun `encrypted output is enc format`() {
        val manager = SyncCryptoManager(AesGcmCipher(), masterKey = ByteArray(32))
        val result = manager.encrypt("test".toByteArray())
        // nonce(12) + ciphertext(4) + tag(16) = 32
        assertTrue(result.size >= 12 + 4 + 16) // nonce + data + tag
    }

    @Test
    fun `decrypt with wrong key throws`() {
        val m1 = SyncCryptoManager(AesGcmCipher(), masterKey = ByteArray(32) { 1 })
        val m2 = SyncCryptoManager(AesGcmCipher(), masterKey = ByteArray(32) { 2 })
        val enc = m1.encrypt("secret".toByteArray())
        assertThrows(Exception::class.java) { m2.decrypt(enc) }
    }
}
