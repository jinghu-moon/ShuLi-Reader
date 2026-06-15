package com.shuli.reader.feature.reader.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingStateManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun saveNow_updatesCurrentState() = testScope.runTest {
        var currentTime = 1000L
        val manager = ReadingStateManager(testScope) { currentTime }

        manager.saveReadNow(
            bookId = 1L,
            chapterIndex = 0,
            chapterPos = 100,
            chapterTitle = "Chapter 1",
        )

        val state = manager.getCurrentState()
        assertNotNull("状态不应为空", state)
        assertEquals("书籍ID应正确", 1L, state?.bookId)
        assertEquals("章节索引应正确", 0, state?.chapterIndex)
        assertEquals("章节位置应正确", 100, state?.chapterPos)
        assertEquals("章节标题应正确", "Chapter 1", state?.chapterTitle)
    }

    @Test
    fun debouncedSave_delaysUpdate() = testScope.runTest {
        var currentTime = 1000L
        val manager = ReadingStateManager(testScope) { currentTime }

        manager.saveReadDebounced(
            bookId = 1L,
            chapterIndex = 0,
            chapterPos = 100,
            chapterTitle = "Chapter 1",
            debounceMs = 500L,
        )

        // 立即检查，状态应该已更新
        val state = manager.getCurrentState()
        assertNotNull("状态不应为空", state)
        assertEquals("书籍ID应正确", 1L, state?.bookId)
    }

    @Test
    fun debouncedSave_cancelsPreviousJob() = testScope.runTest {
        var currentTime = 1000L
        val manager = ReadingStateManager(testScope) { currentTime }

        // 第一次保存
        manager.saveReadDebounced(
            bookId = 1L,
            chapterIndex = 0,
            chapterPos = 100,
            chapterTitle = "Chapter 1",
            debounceMs = 500L,
        )

        // 第二次保存
        manager.saveReadDebounced(
            bookId = 1L,
            chapterIndex = 1,
            chapterPos = 200,
            chapterTitle = "Chapter 2",
            debounceMs = 500L,
        )

        val state = manager.getCurrentState()
        assertNotNull("状态不应为空", state)
        assertEquals("章节索引应为最新值", 1, state?.chapterIndex)
    }

    @Test
    fun cancel_clearsAllJobs() = testScope.runTest {
        val manager = ReadingStateManager(testScope)

        manager.saveReadDebounced(
            bookId = 1L,
            chapterIndex = 0,
            chapterPos = 100,
            chapterTitle = "Chapter 1",
        )

        manager.cancel()

        val state = manager.getCurrentState()
        assertNotNull("状态不应为空", state)
    }

    // T10.3 - 会话时长跟踪测试

    @Test
    fun elapsedBeforeSessionStart_isZero() = testScope.runTest {
        var currentTime = 1000L
        val manager = ReadingStateManager(testScope) { currentTime }

        assertEquals(0L, manager.getSessionElapsedMs())
    }

    @Test
    fun startSession_startsTimer() = testScope.runTest {
        var currentTime = 1000L
        val manager = ReadingStateManager(testScope) { currentTime }

        manager.startSession()
        currentTime = 6000L // 过了 5 秒

        assertEquals(5000L, manager.getSessionElapsedMs())
    }

    @Test
    fun pauseSession_stopsTimer() = testScope.runTest {
        var currentTime = 1000L
        val manager = ReadingStateManager(testScope) { currentTime }

        manager.startSession()
        currentTime = 6000L // 过了 5 秒
        manager.pauseSession()
        currentTime = 11000L // 又过了 5 秒

        // 暂停后不应继续计时
        assertEquals(5000L, manager.getSessionElapsedMs())
    }

    @Test
    fun resumeSession_continuesTimer() = testScope.runTest {
        var currentTime = 1000L
        val manager = ReadingStateManager(testScope) { currentTime }

        manager.startSession()
        currentTime = 6000L // 过了 5 秒
        manager.pauseSession()
        currentTime = 11000L // 暂停 5 秒
        manager.resumeSession()
        currentTime = 16000L // 又过了 5 秒

        // 应该是 5 + 5 = 10 秒
        assertEquals(10000L, manager.getSessionElapsedMs())
    }

    @Test
    fun endSession_returnsAccumulatedDuration() = testScope.runTest {
        var currentTime = 1000L
        val manager = ReadingStateManager(testScope) { currentTime }

        manager.startSession()
        currentTime = 6000L // 过了 5 秒

        val elapsed = manager.endSession()
        assertEquals(5000L, elapsed)
    }

    @Test
    fun elapsedAfterEndSession_resetsToZero() = testScope.runTest {
        var currentTime = 1000L
        val manager = ReadingStateManager(testScope) { currentTime }

        manager.startSession()
        currentTime = 6000L
        manager.endSession()

        assertEquals(0L, manager.getSessionElapsedMs())
    }

    @Test
    fun endSessionAfterPause_returnsDurationBeforePause() = testScope.runTest {
        var currentTime = 1000L
        val manager = ReadingStateManager(testScope) { currentTime }

        manager.startSession()
        currentTime = 6000L // 5 秒
        manager.pauseSession()
        currentTime = 11000L // 暂停 5 秒

        val elapsed = manager.endSession()
        assertEquals(5000L, elapsed)
    }

    @Test
    fun repeatedStartSession_isIgnored() = testScope.runTest {
        var currentTime = 1000L
        val manager = ReadingStateManager(testScope) { currentTime }

        manager.startSession()
        currentTime = 6000L
        manager.startSession() // 重复开始
        currentTime = 11000L

        // 应该从第一次开始计时
        assertEquals(10000L, manager.getSessionElapsedMs())
    }

    @Test
    fun pauseBeforeStart_isIgnored() = testScope.runTest {
        var currentTime = 1000L
        val manager = ReadingStateManager(testScope) { currentTime }

        manager.pauseSession() // 未开始就暂停
        assertEquals(0L, manager.getSessionElapsedMs())
    }

    @Test
    fun resumeWhenNotPaused_isIgnored() = testScope.runTest {
        var currentTime = 1000L
        val manager = ReadingStateManager(testScope) { currentTime }

        manager.startSession()
        currentTime = 6000L
        manager.resumeSession() // 未暂停就恢复
        currentTime = 11000L

        assertEquals(10000L, manager.getSessionElapsedMs())
    }

    @Test
    fun multiplePauseResume_accumulatesCorrectly() = testScope.runTest {
        var currentTime = 1000L
        val manager = ReadingStateManager(testScope) { currentTime }

        manager.startSession()
        currentTime = 4000L // 3 秒
        manager.pauseSession()
        currentTime = 8000L // 暂停 4 秒
        manager.resumeSession()
        currentTime = 11000L // 又 3 秒
        manager.pauseSession()
        currentTime = 15000L // 暂停 4 秒
        manager.resumeSession()
        currentTime = 18000L // 又 3 秒

        // 3 + 3 + 3 = 9 秒
        assertEquals(9000L, manager.getSessionElapsedMs())
    }
}
