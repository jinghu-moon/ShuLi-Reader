package com.shuli.reader.sync.conflict

import kotlin.math.abs

/**
 * 冲突解决器（T-18, T-19, T-20）
 *
 * 实现进度冲突解决逻辑：
 * 1. logicalVersion 高的 wins
 * 2. 版本相同时，比较 updatedAt
 * 3. 时间戳也相同时，使用内容比较（TXT: byteOffset, EPUB: chapterIndex + chapterPos）
 *
 * 书签/笔记合并：UUID 级别合并，同一 UUID 取时间戳最新者。
 * tombstone 清理：基于设备全量同步确认。
 * 配置合并：key-level merge，脏 key 用本地值，非脏 key 用远端值。
 */
object ConflictResolver {

    private const val PROGRESS_GAP_THRESHOLD = 0.05 // 5%

    /**
     * 解决进度冲突，返回胜出的 BookState
     */
    fun resolveProgress(local: BookState, remote: BookState): BookState {
        // 1. 逻辑版本比较
        if (remote.version > local.version) return remote
        if (local.version > remote.version) return local

        // 2. 版本相同，比较时间戳
        if (remote.updatedAt > local.updatedAt) return remote
        if (local.updatedAt > remote.updatedAt) return local

        // 3. 时间戳也相同，使用内容比较
        return resolveByContent(local, remote)
    }

    /**
     * 分类进度冲突：自动合并或需要用户输入
     */
    fun classifyProgressConflict(local: BookState, remote: BookState): ConflictDecision {
        // 先用标准逻辑解决
        val winner = resolveProgress(local, remote)

        // 计算进度差距
        val totalSize = maxOf(local.totalSize, remote.totalSize)
        if (totalSize <= 0L) return ConflictDecision.AUTO_MERGE

        val gap = abs(local.byteOffset - remote.byteOffset).toDouble() / totalSize
        return if (gap > PROGRESS_GAP_THRESHOLD) {
            ConflictDecision.REQUIRE_USER_INPUT
        } else {
            ConflictDecision.AUTO_MERGE
        }
    }

    /**
     * 内容比较：根据文件类型选择比较方式
     */
    private fun resolveByContent(local: BookState, remote: BookState): BookState {
        return when (local.fileType) {
            "TXT" -> {
                // TXT：比较 byteOffset，取较大值
                if (remote.byteOffset > local.byteOffset) remote else local
            }
            "EPUB" -> {
                // EPUB：比较 chapterIndex，相同时比较 chapterPos
                when {
                    remote.chapterIndex > local.chapterIndex -> remote
                    remote.chapterIndex < local.chapterIndex -> local
                    remote.chapterPos > local.chapterPos -> remote
                    else -> local
                }
            }
            else -> remote // 默认采用远端
        }
    }

    /**
     * 合并书签：UUID 级别合并，同一 UUID 取时间戳最新者
     */
    fun mergeBookmarks(local: List<BookmarkDto>, remote: List<BookmarkDto>): List<BookmarkDto> {
        val merged = mutableMapOf<String, BookmarkDto>()

        // 先加入本地
        for (item in local) {
            merged[item.id] = item
        }

        // 合并远端
        for (item in remote) {
            val existing = merged[item.id]
            if (existing == null || item.updatedAt > existing.updatedAt) {
                merged[item.id] = item
            }
        }

        return merged.values.toList()
    }

    /**
     * 合并笔记：与书签逻辑相同
     */
    fun mergeNotes(local: List<BookmarkDto>, remote: List<BookmarkDto>): List<BookmarkDto> {
        return mergeBookmarks(local, remote)
    }

    /**
     * 检查 tombstone 是否可以清理
     *
     * 条件：所有设备的 lastSyncAt 都晚于 tombstone 的 updatedAt（严格大于）
     */
    fun canCompactTombstone(tombstone: BookmarkDto, devices: List<DeviceInfo>): Boolean {
        if (devices.isEmpty()) return false
        return devices.all { device ->
            device.lastSyncAt > tombstone.updatedAt
        }
    }

    /**
     * 合并用户偏好：key-level merge
     *
     * 脏 key 使用本地值，非脏 key 使用远端值。
     */
    fun mergePreferences(
        local: UserPreferences,
        remote: UserPreferences,
        localDirtyKeys: Set<String>,
    ): UserPreferences {
        return UserPreferences(
            fontSize = if ("fontSize" in localDirtyKeys) local.fontSize else remote.fontSize,
            themeMode = if ("themeMode" in localDirtyKeys) local.themeMode else remote.themeMode,
            lineSpacing = if ("lineSpacing" in localDirtyKeys) local.lineSpacing else remote.lineSpacing,
        )
    }
}
