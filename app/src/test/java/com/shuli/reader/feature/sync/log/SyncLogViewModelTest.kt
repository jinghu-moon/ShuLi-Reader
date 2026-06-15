package com.shuli.reader.feature.sync.log

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Part of T-38 同步日志页
@OptIn(ExperimentalCoroutinesApi::class)
class SyncLogViewModelTest {

    private fun createTestLogs(): List<SyncLogEntry> {
        val now = System.currentTimeMillis()
        return listOf(
            SyncLogEntry(
                timestamp = now,
                duration = 1000,
                requestCount = 5,
                transferSize = 1024,
                result = SyncResult.SUCCESS,
                syncType = SyncLogFilter.CLOUD,
            ),
            SyncLogEntry(
                timestamp = now - 86400000, // Yesterday
                duration = 2000,
                requestCount = 10,
                transferSize = 2048,
                result = SyncResult.FAILED,
                syncType = SyncLogFilter.LOCAL,
                errorMessage = "Network error",
            ),
        )
    }

    @Test
    fun `logs are grouped by date`() = runTest(UnconfinedTestDispatcher()) {
        val vm = SyncLogViewModel(createTestLogs(), scope = backgroundScope)
        val groups = vm.groupedLogs.value
        assertTrue(groups.containsKey("今天"))
        assertTrue(groups.containsKey("昨天"))
    }

    @Test
    fun `filter FAILED shows only failed logs`() = runTest(UnconfinedTestDispatcher()) {
        val vm = SyncLogViewModel(createTestLogs(), scope = backgroundScope)
        vm.applyFilter(SyncLogFilter.FAILED)
        val groups = vm.groupedLogs.value
        val allLogs = groups.values.flatten()
        assertTrue(allLogs.all { it.result == SyncResult.FAILED })
    }

    @Test
    fun `filter ALL shows all logs`() = runTest(UnconfinedTestDispatcher()) {
        val vm = SyncLogViewModel(createTestLogs(), scope = backgroundScope)
        vm.applyFilter(SyncLogFilter.ALL)
        val groups = vm.groupedLogs.value
        val allLogs = groups.values.flatten()
        assertEquals(2, allLogs.size)
    }
}
