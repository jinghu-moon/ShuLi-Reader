package com.shuli.reader.sync.worker.notification

import com.shuli.reader.core.i18n.AppStrings
import com.shuli.reader.sync.engine.state.SyncState
import com.shuli.reader.sync.engine.state.SyncStateMachine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// Part of T-25 通知状态更新
class SyncNotifierTest {

    private val strings = AppStrings.ZhHans

    @Test
    fun `SyncStateTextMapper maps SCANNING correctly`() {
        assertEquals(strings.sync.scanningLocalChanges, SyncStateTextMapper.map(SyncState.SCANNING, strings))
    }

    @Test
    fun `SyncStateTextMapper maps UPLOADING correctly`() {
        assertEquals(strings.sync.uploadingBookmarksNotes, SyncStateTextMapper.map(SyncState.UPLOADING, strings))
    }

    @Test
    fun `SyncStateTextMapper maps SUCCESS correctly`() {
        assertEquals(strings.sync.syncComplete, SyncStateTextMapper.map(SyncState.SUCCESS, strings))
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
