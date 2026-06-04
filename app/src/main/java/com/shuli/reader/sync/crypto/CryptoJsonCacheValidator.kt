package com.shuli.reader.sync.crypto

import java.security.MessageDigest

/**
 * crypto.json 本地缓存校验器（T-28）
 *
 * 首次成功验证后，将 crypto.json 的 SHA-256 缓存。
 * 后续每次读取远端 crypto.json 时，与缓存 hash 比对：
 * - 一致 → 通过
 * - 不一致 → 抛出 CryptoConfigTamperedException
 *
 * 支持两种持久化模式：
 * - 内存模式（默认）：hash 在进程重启后丢失
 * - 持久化模式：通过 [PersistenceDelegate] 存储到 DataStore
 *
 * 设计理由：
 * 攻击者若修改远端 crypto.json（如降低 iterations），
 * 本地缓存可检测到变更并阻止继续操作。
 */
class CryptoJsonCacheValidator(
    private val persistence: PersistenceDelegate? = null,
) {

    /**
     * 持久化委托接口，用于将 hash 存储到 DataStore 等持久化存储。
     */
    interface PersistenceDelegate {
        suspend fun loadHash(): String?
        suspend fun saveHash(hash: String)
        suspend fun clearHash()
    }

    @Volatile
    private var cachedHash: String? = null

    /**
     * 从持久化存储加载缓存（应在首次使用时调用）
     */
    suspend fun loadFromPersistence() {
        if (cachedHash == null) {
            cachedHash = persistence?.loadHash()
        }
    }

    /**
     * 保存 crypto.json 的 hash 到缓存和持久化存储
     */
    suspend fun saveHash(meta: CryptoMetadata) {
        val hash = computeHash(meta)
        cachedHash = hash
        persistence?.saveHash(hash)
    }

    /**
     * 验证远端 crypto.json 是否与缓存一致
     *
     * @throws CryptoConfigTamperedException 如果 hash 不匹配
     */
    suspend fun verify(meta: CryptoMetadata) {
        val expected = cachedHash ?: persistence?.loadHash()
        if (expected == null) {
            // 首次验证，保存 hash
            saveHash(meta)
            return
        }

        val actual = computeHash(meta)
        if (expected != actual) {
            throw CryptoConfigTamperedException(
                "crypto.json content changed since last verification (expected=${expected.take(16)}..., actual=${actual.take(16)}...)"
            )
        }

        // 同步到内存缓存
        cachedHash = expected
    }

    /**
     * 清除缓存和持久化存储（密码重置时使用）
     */
    suspend fun clearCache() {
        cachedHash = null
        persistence?.clearHash()
    }

    /**
     * 是否有缓存
     */
    fun hasCache(): Boolean = cachedHash != null

    private fun computeHash(meta: CryptoMetadata): String {
        val payload = "${meta.kdf}|${meta.iterations}|${meta.salt}|${meta.cipher}|${meta.keyVersion}|${meta.createdAt}|${meta.integrity}"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(payload.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
