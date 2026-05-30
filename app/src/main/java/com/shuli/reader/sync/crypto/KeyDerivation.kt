package com.shuli.reader.sync.crypto

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 密钥派生（T-26）
 *
 * 使用 PBKDF2WithHmacSHA256 派生密钥。
 */
class KeyDerivation(
    private val params: KeyDerivationParams,
) {

    /**
     * 从密码和盐派生密钥
     *
     * @param password 密码
     * @param salt 盐（至少 16 字节）
     * @return 派生的密钥字节数组
     */
    fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            params.iterations,
            params.keyLengthBits
        )
        val factory = SecretKeyFactory.getInstance(params.algorithm)
        return factory.generateSecret(spec).encoded
    }
}
