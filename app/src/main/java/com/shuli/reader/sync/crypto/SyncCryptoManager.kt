package com.shuli.reader.sync.crypto

/**
 * 同步加密管理器（T-29）
 *
 * 封装 AesGcmCipher，提供 encrypt/decrypt 接口。
 * Transport 层在 E2EE 模式下透明注入加密管道。
 */
class SyncCryptoManager(
    private val cipher: AesGcmCipher,
    private val masterKey: ByteArray,
) {

    /**
     * 加密数据
     */
    fun encrypt(plaintext: ByteArray): ByteArray {
        return cipher.encrypt(plaintext, masterKey)
    }

    /**
     * 解密数据
     */
    fun decrypt(ciphertext: ByteArray): ByteArray {
        return cipher.decrypt(ciphertext, masterKey)
    }
}
