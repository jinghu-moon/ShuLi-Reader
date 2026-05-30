package com.shuli.reader.sync.crypto

import kotlinx.serialization.Serializable

/**
 * 加密元数据（T-28）
 *
 * 存储在 crypto.json 中，包含 KDF 参数和完整性校验。
 */
@Serializable
data class CryptoMetadata(
    val kdf: String = "PBKDF2WithHmacSHA256",
    val iterations: Int = 600_000,
    val salt: String = "",
    val cipher: String = "AES-256-GCM",
    val keyVersion: Int = 1,
    val createdAt: Long = 0L,
    val integrity: String = "",
)
