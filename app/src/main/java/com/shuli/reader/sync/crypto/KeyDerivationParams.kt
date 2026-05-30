package com.shuli.reader.sync.crypto

/**
 * 密钥派生参数（T-26）
 *
 * @throws IllegalArgumentException 如果 iterations < 600_000
 */
data class KeyDerivationParams(
    val algorithm: String,
    val iterations: Int,
    val keyLengthBits: Int,
) {
    init {
        require(iterations >= 600_000) {
            "PBKDF2 iterations must be >= 600000 (OWASP 2023 recommendation), got $iterations"
        }
    }
}
