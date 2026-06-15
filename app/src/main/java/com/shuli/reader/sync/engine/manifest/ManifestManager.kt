package com.shuli.reader.sync.engine.manifest

import com.shuli.reader.sync.network.transport.SyncTransport
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * 清单管理器（T-17）
 *
 * 负责读写 manifest.json，使用 Mutex 保证并发写入串行化。
 * 所有写操作必须通过 updateManifest() 完成完整的读-改-写周期。
 */
class ManifestManager(
    private val transport: SyncTransport,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 读取远端 manifest，不存在时返回 null
     */
    suspend fun readManifest(): SyncManifest? {
        val data = transport.read("manifest.json") ?: return null
        return json.decodeFromString<SyncManifest>(data.decodeToString())
    }

    /**
     * 原子更新 manifest：读取 → 应用变换 → 带 If-Match 写回
     *
     * 整个过程在 Mutex.withLock 内完成，防止本地并发写丢失。
     * 使用 ETag + If-Match 实现远端乐观锁，防止多设备并发覆盖。
     * 若远端返回 409/412（冲突），重新读取后重试，最多 3 次。
     */
    suspend fun updateManifest(update: (SyncManifest) -> SyncManifest) {
        mutex.withLock {
            var retries = 0
            while (retries < MAX_CONFLICT_RETRIES) {
                val meta = transport.getMetadata("manifest.json")
                val currentEtag = meta?.etag
                val current = readManifest() ?: SyncManifest()
                val updated = update(current)
                val data = json.encodeToString(SyncManifest.serializer(), updated).toByteArray()
                try {
                    transport.write("manifest.json", data, etag = currentEtag)
                    return
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    if (retries < MAX_CONFLICT_RETRIES - 1 && (msg.contains("412") || msg.contains("409") || msg.contains("Conflict"))) {
                        retries++
                        continue
                    }
                    throw e
                }
            }
        }
    }

    private companion object {
        const val MAX_CONFLICT_RETRIES = 3
    }
}
