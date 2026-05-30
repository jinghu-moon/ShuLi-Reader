package com.shuli.reader.sync.crypto

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 加密元数据管理器（T-28）
 *
 * 使用 HMAC-SHA256 计算和验证 crypto.json 的完整性。
 */
object CryptoMetadataManager {

    private const val HMAC_ALGORITHM = "HmacSHA256"

    /**
     * 计算完整性 HMAC
     *
     * 使用 masterKey 派生子密钥，对元数据的关键字段计算 HMAC。
     */
    fun computeIntegrity(meta: CryptoMetadata, masterKey: ByteArray): CryptoMetadata {
        val dataToSign = "${meta.kdf}|${meta.iterations}|${meta.salt}|${meta.cipher}|${meta.keyVersion}|${meta.createdAt}"
        val hmac = computeHmac(dataToSign.toByteArray(), masterKey)
        return meta.copy(integrity = Base64.getEncoder().encodeToString(hmac))
    }

    /**
     * 验证完整性
     */
    fun verifyIntegrity(meta: CryptoMetadata, masterKey: ByteArray): Boolean {
        val dataToSign = "${meta.kdf}|${meta.iterations}|${meta.salt}|${meta.cipher}|${meta.keyVersion}|${meta.createdAt}"
        val expectedHmac = computeHmac(dataToSign.toByteArray(), masterKey)
        val expectedIntegrity = Base64.getEncoder().encodeToString(expectedHmac)
        return meta.integrity == expectedIntegrity
    }

    private fun computeHmac(data: ByteArray, key: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
        return mac.doFinal(data)
    }
}
