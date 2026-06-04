package com.shuli.reader.sync.engine

import com.shuli.reader.sync.conflict.BookState
import com.shuli.reader.sync.conflict.BookmarkDto
import com.shuli.reader.sync.conflict.ConflictResolver
import com.shuli.reader.sync.conflict.DeviceInfo
import com.shuli.reader.sync.conflict.UserPreferences
import com.shuli.reader.sync.manifest.ManifestManager
import com.shuli.reader.sync.network.webdav.WebDavRateLimitException
import com.shuli.reader.sync.state.SyncState
import com.shuli.reader.sync.state.SyncStateMachine
import com.shuli.reader.sync.throttle.RateLimitHandler
import com.shuli.reader.sync.transport.SyncTransport
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 本地数据提供者接口，将 SyncEngine 与 Room DAO 解耦。
 */
interface SyncDataProvider {
    suspend fun getDirtyBookKeys(): List<String>
    suspend fun getLocalState(bookKey: String): SyncBookState?
    suspend fun getLocalBookmarks(bookKey: String): List<BookmarkDto>
    suspend fun getLocalNotes(bookKey: String): List<BookmarkDto>
    suspend fun getDirtyPreferenceKeys(): Set<String>
    suspend fun getLocalPreferences(): UserPreferences?
    suspend fun getLocalPreferencesJson(): ByteArray?

    /** 保存合并结果到本地数据库 */
    suspend fun saveLocalState(bookKey: String, state: SyncBookState)
    suspend fun saveLocalBookmarks(bookKey: String, bookmarks: List<BookmarkDto>)
    suspend fun saveLocalNotes(bookKey: String, notes: List<BookmarkDto>)
    suspend fun saveLocalPreferences(data: ByteArray)
    suspend fun saveLocalPreferencesMerged(prefs: UserPreferences)

    suspend fun clearDirtyFlags(bookKeys: List<String>)
    suspend fun clearDirtyPreferenceKeys()

    /** 获取书籍文件大小（用于进度冲突的 5% 阈值计算） */
    suspend fun getBookFileSize(bookKey: String): Long

    /** 获取所有已知设备列表（用于 tombstone 清理判断） */
    suspend fun getDevices(): List<DeviceInfo> = emptyList()

    /** 删除指定 bookKey 的已标记删除书签（tombstone 清理） */
    suspend fun deleteTombstoneBookmarks(bookKey: String, bookmarkIds: List<String>) {}
}

@Serializable
data class SyncBookState(
    val bookKey: String = "",
    val fileType: String = "TXT",
    val byteOffset: Long = 0L,
    val chapterIndex: Int = 0,
    val chapterPos: Int = 0,
    val chapterTitle: String = "",
    val readingProgress: Float = 0f,
    val version: Int = 1,
    val updatedAt: Long = 0L,
    val deviceId: String = "",
)

/**
 * 同步引擎（T-22）
 *
 * 核心编排器，驱动同步状态机完成 §6.3 定义的 9 步同步流程：
 * 1. 拉取远端 manifest
 * 2-3. 配置/预设变更检测
 * 4-6. 进度/书签/笔记变更检测
 * 7. 书籍文件变更检测（由上层控制）
 * 8. 下载远端变更 + 合并
 * 9. 上传本地变更 + 清除脏标记
 */
class SyncEngine(
    private val manifestManager: ManifestManager,
    private val stateMachine: SyncStateMachine,
    private val dataProvider: SyncDataProvider? = null,
    private val deviceId: String = "",
    private val rateLimitHandler: RateLimitHandler = RateLimitHandler(),
    private val maxRetries: Int = 3,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun sync(transport: SyncTransport) {
        var attempt = 0
        while (true) {
            try {
                executeSyncOnce(transport)
                return
            } catch (e: WebDavRateLimitException) {
                attempt++
                if (attempt >= maxRetries) {
                    try { stateMachine.transition(SyncState.FAILED) } catch (_: Exception) {}
                    throw e
                }
                val waitMs = rateLimitHandler.computeWaitMs(e, attempt)
                stateMachine.transition(SyncState.RATE_LIMITED, retryAfterMs = waitMs)
                delay(waitMs)
                stateMachine.transition(SyncState.WAITING_RETRY)
                // SCANNING 转换由 executeSyncOnce 开头处理
            }
        }
    }

    private suspend fun executeSyncOnce(transport: SyncTransport) {
        try {
            // ── Step 1: SCANNING ──
            stateMachine.transition(SyncState.SCANNING)

            transport.ensureDirectories()
            manifestManager.readManifest()

            val remoteBookKeys = discoverRemoteBookKeys(transport)
            val dirtyBookKeys = dataProvider?.getDirtyBookKeys() ?: emptyList()
            val allBookKeys = (remoteBookKeys + dirtyBookKeys).distinct()

            // ── Step 2-7: DOWNLOADING ──
            stateMachine.transition(SyncState.DOWNLOADING)

            val remoteStates = mutableMapOf<String, SyncBookState>()
            val remoteBookmarks = mutableMapOf<String, List<BookmarkDto>>()
            val remoteNotes = mutableMapOf<String, List<BookmarkDto>>()

            for (bookKey in remoteBookKeys) {
                val state = downloadState(transport, bookKey)
                if (state != null) remoteStates[bookKey] = state
                remoteBookmarks[bookKey] = downloadBookmarks(transport, bookKey)
                remoteNotes[bookKey] = downloadNotes(transport, bookKey)
            }

            val remotePreferences = transport.read("config/preferences.json")

            // ── Step 8: MERGING ──
            stateMachine.transition(SyncState.MERGING)

            // 合并进度：结果写入本地数据库
            for (bookKey in allBookKeys) {
                val remote = remoteStates[bookKey]
                val local = dataProvider?.getLocalState(bookKey)

                when {
                    remote != null && local != null -> {
                        val fileSize = dataProvider?.getBookFileSize(bookKey) ?: 0L
                        val merged = ConflictResolver.resolveProgress(
                            local.toBookState(fileSize),
                            remote.toBookState(fileSize),
                        )
                        dataProvider?.saveLocalState(bookKey, merged.toSyncBookState(bookKey))
                    }
                    remote != null && local == null -> {
                        // 远端独有 → 下载到本地
                        dataProvider?.saveLocalState(bookKey, remote)
                    }
                    // local != null && remote == null → 上传阶段处理
                }
            }

            // 合并书签：结果写入本地数据库
            for (bookKey in allBookKeys) {
                val remote = remoteBookmarks[bookKey] ?: emptyList()
                val local = dataProvider?.getLocalBookmarks(bookKey) ?: emptyList()
                if (remote.isNotEmpty() || local.isNotEmpty()) {
                    val merged = ConflictResolver.mergeBookmarks(local, remote)
                    dataProvider?.saveLocalBookmarks(bookKey, merged)
                }
            }

            // 合并笔记：结果写入本地数据库
            for (bookKey in allBookKeys) {
                val remote = remoteNotes[bookKey] ?: emptyList()
                val local = dataProvider?.getLocalNotes(bookKey) ?: emptyList()
                if (remote.isNotEmpty() || local.isNotEmpty()) {
                    val merged = ConflictResolver.mergeNotes(local, remote)
                    dataProvider?.saveLocalNotes(bookKey, merged)
                }
            }

            // Tombstone 清理：删除所有设备都已同步过的已标记删除书签
            val devices = try { dataProvider?.getDevices() ?: emptyList() } catch (_: Exception) { emptyList() }
            if (devices.isNotEmpty()) {
                for (bookKey in allBookKeys) {
                    try {
                        val bookmarks = dataProvider?.getLocalBookmarks(bookKey) ?: emptyList()
                        val tombstones = bookmarks.filter { it.deleted }
                        val removable = tombstones.filter { tombstone ->
                            ConflictResolver.canCompactTombstone(tombstone, devices)
                        }
                        if (removable.isNotEmpty()) {
                            dataProvider?.deleteTombstoneBookmarks(bookKey, removable.map { it.id })
                        }
                    } catch (_: Exception) {
                        // tombstone 清理失败不应阻断同步流程
                    }
                }
            }

            // 合并配置：key-level merge
            if (remotePreferences != null) {
                val dirtyKeys = dataProvider?.getDirtyPreferenceKeys() ?: emptySet()
                val localPrefs = dataProvider?.getLocalPreferences()
                val remotePrefs = try {
                    json.decodeFromString(UserPreferences.serializer(), remotePreferences.decodeToString())
                } catch (_: Exception) { null }

                if (localPrefs != null && remotePrefs != null) {
                    val merged = ConflictResolver.mergePreferences(localPrefs, remotePrefs, dirtyKeys)
                    dataProvider?.saveLocalPreferencesMerged(merged)
                } else if (remotePrefs != null) {
                    dataProvider?.saveLocalPreferences(remotePreferences)
                }
            }

            // ── Step 9: UPLOADING ──
            stateMachine.transition(SyncState.UPLOADING)

            // 上传所有脏 bookKeys 的合并后数据
            for (bookKey in dirtyBookKeys) {
                val localState = dataProvider?.getLocalState(bookKey)
                if (localState != null) {
                    uploadState(transport, bookKey, localState.copy(bookKey = bookKey))
                }

                val localBm = dataProvider?.getLocalBookmarks(bookKey)
                if (!localBm.isNullOrEmpty()) {
                    uploadBookmarks(transport, bookKey, localBm)
                }

                val localNotes = dataProvider?.getLocalNotes(bookKey)
                if (!localNotes.isNullOrEmpty()) {
                    uploadNotes(transport, bookKey, localNotes)
                }
            }

            // 上传合并后的配置
            val dirtyPrefKeys = dataProvider?.getDirtyPreferenceKeys() ?: emptySet()
            if (dirtyPrefKeys.isNotEmpty()) {
                val localPrefs = dataProvider?.getLocalPreferencesJson()
                if (localPrefs != null) {
                    transport.write("config/preferences.json", localPrefs)
                }
            }

            // 更新 manifest
            manifestManager.updateManifest { current ->
                current.copy(
                    version = current.version + 1,
                    bookCount = allBookKeys.size,
                    updatedAt = System.currentTimeMillis(),
                    updatedBy = deviceId,
                )
            }

            // 清除脏标记
            dataProvider?.clearDirtyFlags(dirtyBookKeys)
            dataProvider?.clearDirtyPreferenceKeys()

            stateMachine.transition(SyncState.SUCCESS)
        } catch (e: WebDavRateLimitException) {
            // 限流异常不转 FAILED，由外层重试循环处理
            throw e
        } catch (e: Exception) {
            try {
                stateMachine.transition(SyncState.FAILED)
            } catch (_: Exception) {
                // 状态机可能已经不在可转换状态
            }
            throw e
        }
    }

    // ── 远端 bookKey 发现 ──

    private suspend fun discoverRemoteBookKeys(transport: SyncTransport): List<String> {
        return try {
            val resources = transport.list("state")
            resources.mapNotNull { resource ->
                resource.path.removePrefix("state/")
                    .removeSuffix(".json")
                    .takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── 下载辅助 ──

    private suspend fun downloadState(transport: SyncTransport, bookKey: String): SyncBookState? {
        val data = transport.read("state/$bookKey.json") ?: return null
        return try {
            json.decodeFromString<SyncBookState>(data.decodeToString())
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun downloadBookmarks(transport: SyncTransport, bookKey: String): List<BookmarkDto> {
        val data = transport.read("bookmarks/$bookKey.json") ?: return emptyList()
        return try {
            json.decodeFromString<BookmarkWrapper>(data.decodeToString()).items
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun downloadNotes(transport: SyncTransport, bookKey: String): List<BookmarkDto> {
        val data = transport.read("notes/$bookKey.json") ?: return emptyList()
        return try {
            json.decodeFromString<BookmarkWrapper>(data.decodeToString()).items
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── 上传辅助 ──

    private suspend fun uploadState(transport: SyncTransport, bookKey: String, state: SyncBookState) {
        val data = json.encodeToString(SyncBookState.serializer(), state).toByteArray()
        transport.write("state/$bookKey.json", data)
    }

    private suspend fun uploadBookmarks(transport: SyncTransport, bookKey: String, bookmarks: List<BookmarkDto>) {
        val wrapper = BookmarkWrapper(items = bookmarks)
        val data = json.encodeToString(BookmarkWrapper.serializer(), wrapper).toByteArray()
        transport.write("bookmarks/$bookKey.json", data)
    }

    private suspend fun uploadNotes(transport: SyncTransport, bookKey: String, notes: List<BookmarkDto>) {
        val wrapper = BookmarkWrapper(items = notes)
        val data = json.encodeToString(BookmarkWrapper.serializer(), wrapper).toByteArray()
        transport.write("notes/$bookKey.json", data)
    }

    // ── BookState ↔ SyncBookState 转换 ──

    private fun SyncBookState.toBookState(fileSize: Long = 0L) = BookState(
        version = version,
        updatedAt = updatedAt,
        byteOffset = byteOffset,
        fileType = fileType,
        chapterIndex = chapterIndex,
        chapterPos = chapterPos,
        totalSize = fileSize,
    )

    private fun BookState.toSyncBookState(bookKey: String) = SyncBookState(
        bookKey = bookKey,
        fileType = fileType,
        byteOffset = byteOffset,
        chapterIndex = chapterIndex,
        chapterPos = chapterPos,
        version = version,
        updatedAt = updatedAt,
        deviceId = deviceId,
    )

    @Serializable
    private data class BookmarkWrapper(val items: List<BookmarkDto> = emptyList())
}
