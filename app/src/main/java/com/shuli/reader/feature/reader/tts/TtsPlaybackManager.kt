package com.shuli.reader.feature.reader.tts

import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.tts.TtsConfig
import com.shuli.reader.core.tts.TtsController
import com.shuli.reader.core.tts.TtsEngine
import com.shuli.reader.core.tts.TtsState
import com.shuli.reader.feature.reader.ReaderUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * TTS 朗读管理器。
 *
 * 职责：TTS 播放控制、定时睡眠、句子分割、
 *       TTS Service 绑定与通知更新。
 *
 * 通过 [uiState] 读写共享状态，不反向依赖 ViewModel。
 */
class TtsPlaybackManager(
    private val uiState: MutableStateFlow<ReaderUiState>,
    ttsEngine: TtsEngine?,
    private val appContext: android.content.Context?,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "TtsPlaybackMgr"
    }

    // ── 回调（由 ViewModel 注入）────────────────────────────────────

    /** 翻到下一页（TTS autoPage 时调用） */
    var onNextPage: (() -> Unit)? = null

    /** 打开指定章节（TTS 跨章时调用） */
    var onOpenChapter: ((Int) -> Unit)? = null

    /** 获取当前书籍内容的章节数 */
    var onGetChapterCount: (() -> Int)? = null

    /** 获取当前页的句子范围列表 */
    var onSentenceRangesForCurrentPage: (() -> List<SelectionRange>)? = null

    // ── 内部状态 ──────────────────────────────────────────────────

    private val ttsController = ttsEngine?.let { engine ->
        TtsController(
            engine = engine,
            onUtteranceCompleted = ::handleTtsUtteranceCompleted,
        )
    }
    private var activeTtsConfig = TtsConfig()
    private var ttsSentences: List<SelectionRange> = emptyList()
    private var ttsSentenceIndex: Int = -1
    private var ttsPendingResume: Boolean = false
    private var sleepTimerJob: Job? = null

    // ── 公开 API ──────────────────────────────────────────────────

    fun startTts(config: TtsConfig = TtsConfig()) {
        val controller = ttsController ?: return
        activeTtsConfig = config
        controller.initialize(config.copy(autoPage = false))
        var sentences = onSentenceRangesForCurrentPage?.invoke().orEmpty()
        if (config.skipTitle && sentences.isNotEmpty()) {
            val title = uiState.value.chapterTitle.trim()
            if (title.isNotBlank() && sentences.first().selectedText.orEmpty().trim() == title) {
                sentences = sentences.drop(1)
            }
        }
        ttsSentences = sentences
        ttsSentenceIndex = 0
        speakCurrentTtsSentence()
        startTtsService()
    }

    fun pauseTts() {
        val controller = ttsController ?: return
        controller.pause()
        uiState.value = uiState.value.copy(ttsState = controller.state)
        updateTtsServiceNotification()
    }

    fun resumeTts() {
        val controller = ttsController ?: return
        controller.resume()
        uiState.value = uiState.value.copy(ttsState = controller.state)
        updateTtsServiceNotification()
    }

    fun stopTts() {
        cancelSleepTimer()
        val controller = ttsController ?: return
        controller.stop()
        ttsSentences = emptyList()
        ttsSentenceIndex = -1
        ttsPendingResume = false
        uiState.value = uiState.value.copy(
            ttsState = controller.state,
            ttsActiveRange = null,
        )
        stopTtsService()
    }

    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        if (minutes <= 0) return
        var remaining = minutes * 60
        uiState.value = uiState.value.copy(sleepTimerRemainingSeconds = remaining)
        sleepTimerJob = scope.launch {
            while (remaining > 0) {
                delay(1000)
                remaining--
                uiState.value = uiState.value.copy(sleepTimerRemainingSeconds = remaining)
            }
            stopTts()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        uiState.value = uiState.value.copy(sleepTimerRemainingSeconds = -1)
    }

    fun pauseTtsOnBackground() {
        if (uiState.value.ttsState == TtsState.PLAYING) {
            pauseTts()
        }
    }

    /** 释放资源（ViewModel.onCleared 时调用） */
    fun release() {
        ttsPendingResume = false
        ttsController?.release()
        stopTtsService()
        ttsSentences = emptyList()
        ttsSentenceIndex = -1
        uiState.value = uiState.value.copy(
            ttsState = TtsState.IDLE,
            ttsActiveRange = null,
        )
    }

    // ── 内部：TTS Service 管理 ────────────────────────────────────

    private fun startTtsService() {
        val ctx = appContext ?: return
        com.shuli.reader.core.tts.TtsService.onPlay = { resumeTts() }
        com.shuli.reader.core.tts.TtsService.onPause = { pauseTts() }
        com.shuli.reader.core.tts.TtsService.onStop = { stopTts() }
        com.shuli.reader.core.tts.TtsService.isPlaying = { uiState.value.ttsState == TtsState.PLAYING }
        com.shuli.reader.core.tts.TtsService.currentTitle = { uiState.value.bookTitle }
        com.shuli.reader.core.tts.TtsService.currentSubtitle = { uiState.value.chapterTitle }
        val intent = android.content.Intent(ctx, com.shuli.reader.core.tts.TtsService::class.java)
        ctx.startForegroundService(intent)
    }

    private fun stopTtsService() {
        val ctx = appContext ?: return
        val intent = android.content.Intent(ctx, com.shuli.reader.core.tts.TtsService::class.java).apply {
            action = com.shuli.reader.core.tts.TtsService.ACTION_STOP
        }
        ctx.startService(intent)
        com.shuli.reader.core.tts.TtsService.onPlay = null
        com.shuli.reader.core.tts.TtsService.onPause = null
        com.shuli.reader.core.tts.TtsService.onStop = null
    }

    private fun updateTtsServiceNotification() {
        val ctx = appContext ?: return
        val intent = android.content.Intent(ctx, com.shuli.reader.core.tts.TtsService::class.java)
        ctx.startForegroundService(intent)
    }

    // ── 内部：TTS 播放逻辑 ────────────────────────────────────────

    private fun handleTtsUtteranceCompleted() {
        val nextIndex = ttsSentenceIndex + 1
        if (nextIndex < ttsSentences.size) {
            ttsSentenceIndex = nextIndex
            speakCurrentTtsSentence()
            return
        }

        val state = uiState.value
        val chapter = state.currentChapter

        if (activeTtsConfig.autoPage && chapter != null) {
            if (state.pageIndex < chapter.lastIndex) {
                onNextPage?.invoke()
                ttsSentences = onSentenceRangesForCurrentPage?.invoke().orEmpty()
                ttsSentenceIndex = 0
                speakCurrentTtsSentence()
                return
            }

            val totalChapters = onGetChapterCount?.invoke() ?: 0
            if (state.chapterIndex < totalChapters - 1) {
                ttsPendingResume = true
                onOpenChapter?.invoke(state.chapterIndex + 1)
                return
            }
        }

        val controller = ttsController
        uiState.value = uiState.value.copy(
            ttsState = controller?.state ?: TtsState.READY,
            ttsActiveRange = null,
        )
        ttsSentences = emptyList()
        ttsSentenceIndex = -1
    }

    private fun speakCurrentTtsSentence() {
        val controller = ttsController ?: return
        val range = ttsSentences.getOrNull(ttsSentenceIndex)
        val text = range?.selectedText.orEmpty()
        if (range == null || text.isBlank()) {
            uiState.value = uiState.value.copy(
                ttsState = TtsState.ERROR,
                ttsActiveRange = null,
            )
            return
        }

        controller.play(text)
        val newRange = if (activeTtsConfig.highlightSentence) range else null
        uiState.value = uiState.value.copy(
            ttsState = controller.state,
            ttsActiveRange = newRange,
        )
        // TTS 高亮变化只需重绘整页（高亮矩形在 page canvas 上绘制）
        uiState.value.currentPage?.invalidate()
    }

    /** 章节加载完成后恢复 TTS（如果 pendingResume） */
    fun onResumeAfterChapterLoad() {
        if (ttsPendingResume) {
            ttsPendingResume = false
            ttsSentences = onSentenceRangesForCurrentPage?.invoke().orEmpty()
            ttsSentenceIndex = 0
            if (ttsSentences.isNotEmpty()) {
                speakCurrentTtsSentence()
            }
        }
    }
}
