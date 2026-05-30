package com.shuli.reader.sync.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException

// Part of T-27 AES-256-GCM 加密/解密
class AesGcmCipherTest {

    @Test
    fun `encrypt then decrypt returns original plaintext`() {
        val cipher = AesGcmCipher()
        val key = ByteArray(32) { it.toByte() }
        val plain = "Hello, 光屿!".toByteArray(Charsets.UTF_8)
        val ciphertext = cipher.encrypt(plain, key)
        val decrypted = cipher.decrypt(ciphertext, key)
        assertEquals(plain.toList(), decrypted.toList())
    }

    @Test
    fun `each encrypt call produces different ciphertext (unique nonce)`() {
        val cipher = AesGcmCipher()
        val key = ByteArray(32) { it.toByte() }
        val plain = "test".toByteArray()
        val c1 = cipher.encrypt(plain, key)
        val c2 = cipher.encrypt(plain, key)
        assertNotEquals(c1.toList(), c2.toList())
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val cipher = AesGcmCipher()
        val key = ByteArray(32) { it.toByte() }
        val ciphertext = cipher.encrypt("data".toByteArray(), key)
        // 篡改密文
        val idx = ciphertext.size / 2
        ciphertext[idx] = (ciphertext[idx].toInt() xor 0xFF).toByte()
        assertThrows(AEADBadTagException::class.java) {
            cipher.decrypt(ciphertext, key)
        }
    }

    @Test
    fun `ciphertext format is nonce 12B plus ciphertext plus tag 16B`() {
        val cipher = AesGcmCipher()
        val key = ByteArray(32)
        val plain = ByteArray(100)
        val result = cipher.encrypt(plain, key)
        // nonce(12) + payload(100) + GCM tag(16) = 128
        assertEquals(128, result.size)
    }
}
