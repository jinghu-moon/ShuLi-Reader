package com.shuli.reader.sync.engine

/**
 * 同步目标枚举（T-23）
 */
enum class SyncTarget {
    /** 仅云端 */
    CLOUD,
    /** 仅本地 */
    LOCAL,
    /** 云端和本地（独立失败语义） */
    BOTH,
}
