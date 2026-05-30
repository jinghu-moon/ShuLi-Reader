package com.shuli.reader.ui.log

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Part of T-38 同步日志页
class SyncLogViewModel(
    private val logs: List<SyncLogEntry>,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
) {

    private val _groupedLogs = MutableStateFlow<Map<String, List<SyncLogEntry>>>(emptyMap())
    val groupedLogs: StateFlow<Map<String, List<SyncLogEntry>>> = _groupedLogs.asStateFlow()

    private val _currentFilter = MutableStateFlow(SyncLogFilter.ALL)
    val currentFilter: StateFlow<SyncLogFilter> = _currentFilter.asStateFlow()

    init {
        scope.launch {
            applyFilter(SyncLogFilter.ALL)
        }
    }

    fun applyFilter(filter: SyncLogFilter) {
        _currentFilter.value = filter
        val filteredLogs = when (filter) {
            SyncLogFilter.ALL -> logs
            SyncLogFilter.CLOUD -> logs.filter { it.syncType == SyncLogFilter.CLOUD }
            SyncLogFilter.LOCAL -> logs.filter { it.syncType == SyncLogFilter.LOCAL }
            SyncLogFilter.FAILED -> logs.filter { it.result == SyncResult.FAILED }
        }
        _groupedLogs.value = groupLogsByDate(filteredLogs)
    }

    private fun groupLogsByDate(logs: List<SyncLogEntry>): Map<String, List<SyncLogEntry>> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = dateFormat.format(Date())
        val yesterday = dateFormat.format(Date(System.currentTimeMillis() - 86400000))

        return logs.groupBy { entry ->
            val date = dateFormat.format(Date(entry.timestamp))
            when (date) {
                today -> "今天"
                yesterday -> "昨天"
                else -> date
            }
        }
    }
}
