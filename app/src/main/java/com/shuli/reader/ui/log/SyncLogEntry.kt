package com.shuli.reader.ui.log

// Part of T-38 同步日志页
data class SyncLogEntry(
    val timestamp: Long,
    val duration: Long,
    val requestCount: Int,
    val transferSize: Long,
    val result: SyncResult,
    val syncType: SyncLogFilter,
    val errorMessage: String? = null,
)
