package com.shuli.reader.feature.reader.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 阅读状态管理器，负责进度保存与时长统计
 */
class ReadingStateManager(
    private val scope: CoroutineScope,
    private val saveAction: suspend (ReadingState) -> Unit = {},
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    // 防抖保存任务
    private var debounceJob: Job? = null

    // 当前阅读状态
    private var currentState: ReadingState? = null

    // 会话时长跟踪
    private var sessionStartTime: Long = 0L
    private var sessionPausedTime: Long = 0L
    private var isSessionActive: Boolean = false
    private var isSessionPaused: Boolean = false

    // 累计时长（当前会话，毫秒）
    private var sessionElapsedMs: Long = 0L

    /**
     * 阅读状态数据类
     */
    data class ReadingState(
        val bookId: Long,
        val chapterIndex: Int,
        val chapterPos: Int,
        val chapterTitle: String?,
        val timestamp: Long,
    )

    /**
     * 开始阅读会话
     */
    fun startSession() {
        if (isSessionActive) return
        isSessionActive = true
        isSessionPaused = false
        sessionStartTime = clock()
        sessionElapsedMs = 0L
    }

    /**
     * 暂停会话（进入后台、锁屏）
     */
    fun pauseSession() {
        if (!isSessionActive || isSessionPaused) return
        isSessionPaused = true
        sessionPausedTime = clock()
        sessionElapsedMs += sessionPausedTime - sessionStartTime
    }

    /**
     * 恢复会话（回到前台）
     */
    fun resumeSession() {
        if (!isSessionActive || !isSessionPaused) return
        isSessionPaused = false
        sessionStartTime = clock()
    }

    /**
     * 结束会话，返回本次会话累计时长（毫秒）
     */
    fun endSession(): Long {
        if (!isSessionActive) return 0L

        val now = clock()
        val elapsed = if (isSessionPaused) {
            sessionElapsedMs
        } else {
            sessionElapsedMs + (now - sessionStartTime)
        }

        // 重置状态
        isSessionActive = false
        isSessionPaused = false
        sessionElapsedMs = 0L
        sessionStartTime = 0L

        return elapsed
    }

    /**
     * 获取当前会话已累积时长（毫秒）
     */
    fun getSessionElapsedMs(): Long {
        if (!isSessionActive) return 0L
        val now = clock()
        return if (isSessionPaused) {
            sessionElapsedMs
        } else {
            sessionElapsedMs + (now - sessionStartTime)
        }
    }

    /**
     * 防抖保存，默认 500ms
     */
    fun saveReadDebounced(
        bookId: Long,
        chapterIndex: Int,
        chapterPos: Int,
        chapterTitle: String?,
        debounceMs: Long = 500L,
    ) {
        debounceJob?.cancel()

        currentState = ReadingState(
            bookId = bookId,
            chapterIndex = chapterIndex,
            chapterPos = chapterPos,
            chapterTitle = chapterTitle,
            timestamp = clock(),
        )

        debounceJob = scope.launch(Dispatchers.IO) {
            delay(debounceMs)
            saveToDatabase(currentState!!)
        }
    }

    /**
     * 立即保存，用于翻章、跳页、退出
     */
    fun saveReadNow(
        bookId: Long,
        chapterIndex: Int,
        chapterPos: Int,
        chapterTitle: String?,
    ) {
        debounceJob?.cancel()

        currentState = ReadingState(
            bookId = bookId,
            chapterIndex = chapterIndex,
            chapterPos = chapterPos,
            chapterTitle = chapterTitle,
            timestamp = clock(),
        )

        scope.launch(Dispatchers.IO) {
            saveToDatabase(currentState!!)
        }
    }

    /**
     * 保存到数据库
     */
    private suspend fun saveToDatabase(state: ReadingState) {
        saveAction(state)
    }

    /**
     * 获取当前状态
     */
    fun getCurrentState(): ReadingState? {
        return currentState
    }

    /**
     * 取消所有任务
     */
    fun cancel() {
        debounceJob?.cancel()
        debounceJob = null
    }
}