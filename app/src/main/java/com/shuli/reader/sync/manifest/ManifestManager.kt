package com.shuli.reader.sync.manifest

import com.shuli.reader.sync.transport.SyncTransport
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
     * 原子更新 manifest：读取 → 应用变换 → 写回
     *
     * 整个过程在 Mutex.withLock 内完成，防止并发写丢失。
     */
    suspend fun updateManifest(update: (SyncManifest) -> SyncManifest) {
        mutex.withLock {
            val current = readManifest() ?: SyncManifest()
            val updated = update(current)
            val data = json.encodeToString(SyncManifest.serializer(), updated).toByteArray()
            transport.write("manifest.json", data)
        }
    }
}
