package com.shuli.reader.sync.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM 加密/解密（T-27）
 *
 * 输出格式：nonce(12) || ciphertext+tag
 * nonce 使用 SecureRandom 随机生成，确保每次加密结果不同。
 */
class AesGcmCipher {

    companion object {
        private const val NONCE_SIZE = 12 // 96 bits
        private const val TAG_SIZE = 128 // 128 bits
        private const val ALGORITHM = "AES/GCM/NoPadding"
    }

    private val secureRandom = SecureRandom()

    /**
     * 加密
     *
     * @param plaintext 明文
     * @param key 密钥（32 字节）
     * @return nonce(12) + ciphertext + tag(16)
     */
    fun encrypt(plaintext: ByteArray, key: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_SIZE)
        secureRandom.nextBytes(nonce)

        val cipher = Cipher.getInstance(ALGORITHM)
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(TAG_SIZE, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        val ciphertext = cipher.doFinal(plaintext)

        // nonce || ciphertext+tag
        return nonce + ciphertext
    }

    /**
     * 解密
     *
     * @param data nonce(12) + ciphertext + tag(16)
     * @param key 密钥（32 字节）
     * @return 明文
     * @throws javax.crypto.AEADBadTagException 如果密文被篡改
     */
    fun decrypt(data: ByteArray, key: ByteArray): ByteArray {
        require(data.size > NONCE_SIZE + TAG_SIZE / 8) { "Invalid ciphertext" }

        val nonce = data.copyOfRange(0, NONCE_SIZE)
        val ciphertext = data.copyOfRange(NONCE_SIZE, data.size)

        val cipher = Cipher.getInstance(ALGORITHM)
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(TAG_SIZE, nonce)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        return cipher.doFinal(ciphertext)
    }
}
