package com.shuli.reader.sync.crypto

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 加密元数据管理器（T-28）
 *
 * 使用 HKDF 从 masterKey 派生子密钥，再用子密钥计算 HMAC-SHA256 完整性。
 * 符合 §10.4 规范：HMAC 使用 HKDF 派生的子密钥，与数据加密密钥隔离。
 */
object CryptoMetadataManager {

    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val HKDF_INFO = "crypto-integrity"

    /**
     * 计算完整性 HMAC
     *
     * 先用 HKDF-SHA256 从 masterKey 派生子密钥，
     * 再用子密钥对元数据关键字段计算 HMAC-SHA256。
     */
    fun computeIntegrity(meta: CryptoMetadata, masterKey: ByteArray): CryptoMetadata {
        val hkdfKey = hkdfSha256(masterKey, salt = ByteArray(0), info = HKDF_INFO.toByteArray(), outputLength = 32)
        val payload = "${meta.kdf}:${meta.iterations}:${meta.salt}:${meta.cipher}:${meta.keyVersion}"
        val hmac = computeHmac(payload.toByteArray(), hkdfKey)
        return meta.copy(integrity = Base64.getEncoder().encodeToString(hmac))
    }

    /**
     * 验证完整性
     */
    fun verifyIntegrity(meta: CryptoMetadata, masterKey: ByteArray): Boolean {
        val hkdfKey = hkdfSha256(masterKey, salt = ByteArray(0), info = HKDF_INFO.toByteArray(), outputLength = 32)
        val payload = "${meta.kdf}:${meta.iterations}:${meta.salt}:${meta.cipher}:${meta.keyVersion}"
        val expectedHmac = computeHmac(payload.toByteArray(), hkdfKey)
        val expectedIntegrity = Base64.getEncoder().encodeToString(expectedHmac)
        return meta.integrity == expectedIntegrity
    }

    private fun computeHmac(data: ByteArray, key: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
        return mac.doFinal(data)
    }

    /**
     * HKDF-SHA256 密钥派生（RFC 5869）
     *
     * 使用 HMAC-SHA256 作为 PRF，从输入密钥材料派生指定长度的输出密钥。
     *
     * @param ikm 输入密钥材料（masterKey）
     * @param salt 盐值（可为空数组）
     * @param info 上下文信息
     * @param outputLength 输出密钥长度（字节）
     */
    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, outputLength: Int): ByteArray {
        // Step 1: Extract
        val actualSalt = if (salt.isEmpty()) ByteArray(32) else salt
        val prk = computeHmac(ikm, actualSalt)

        // Step 2: Expand
        val hashLen = 32 // SHA-256 output length
        val n = (outputLength + hashLen - 1) / hashLen
        require(n <= 255) { "HKDF output too long" }

        val okm = ByteArray(outputLength)
        var t = ByteArray(0)
        var offset = 0

        for (i in 1..n) {
            val input = t + info + byteArrayOf(i.toByte())
            t = computeHmac(input, prk)
            val copyLen = minOf(hashLen, outputLength - offset)
            System.arraycopy(t, 0, okm, offset, copyLen)
            offset += copyLen
        }

        return okm
    }
}
