package com.shuli.reader.sync.notification

import com.shuli.reader.sync.state.SyncState
import com.shuli.reader.sync.state.SyncStateMachine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// Part of T-25 通知状态更新
class SyncNotifierTest {

    @Test
    fun `SyncStateTextMapper maps SCANNING correctly`() {
        assertEquals("正在扫描本地变更...", SyncStateTextMapper.map(SyncState.SCANNING))
    }

    @Test
    fun `SyncStateTextMapper maps UPLOADING correctly`() {
        assertEquals("正在上传书签与笔记...", SyncStateTextMapper.map(SyncState.UPLOADING))
    }

    @Test
    fun `SyncStateTextMapper maps SUCCESS correctly`() {
        assertEquals("同步完成", SyncStateTextMapper.map(SyncState.SUCCESS))
    }
}

/**
 * 测试用假通知器
 */
class FakeSyncNotifier : SyncNotifier {
    var updateCount = 0
    var lastText: String? = null

    override fun update(text: String) {
        updateCount++
        lastText = text
    }

    override fun cancel() {
        // no-op
    }
}
