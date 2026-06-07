package com.shuli.reader.feature.reader

import com.shuli.reader.core.tts.TtsConfig
import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.tts.TtsController
import com.shuli.reader.core.tts.TtsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * TTS 朗读管理器（从 ReaderViewModel 拆出）
 *
 * 职责：TTS 引擎交互、朗读会话、睡眠定时。
 */
class TtsPlaybackManager(
    private val ttsController: TtsController?,
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val scope: CoroutineScope,
    private val appContextProvider: () -> android.content.Context?,
    private val loadedBookContentProvider: () -> com.shuli.reader.core.parser.model.BookContent?,
    private val nextPage: () -> Unit,
    private val openChapter: (Int) -> Unit,
    private val normalizedChapters: com.shuli.reader.core.parser.model.BookContent.() -> List<com.shuli.reader.core.parser.model.Chapter>,
) {
    private var ttsSentences: List<SelectionRange> = emptyList()
    private var ttsSentenceIndex: Int = -1
    private var ttsPendingResume: Boolean = false
    private var activeTtsConfig: TtsConfig = TtsConfig()
    private var sleepTimerJob: Job? = null

    // ── TTS 控制 ──────────────────────────────────────────────

    fun startTts(config: TtsConfig = TtsConfig()) {
        val controller = ttsController ?: return
        activeTtsConfig = config
        controller.initialize(config.copy(autoPage = false))
        var sentences = sentenceRangesForCurrentPage()
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

    fun pauseTtsOnBackground() {
        if (uiState.value.ttsState == TtsState.PLAYING) {
            pauseTts()
        }
    }

    // ── 睡眠定时 ──────────────────────────────────────────────

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

    // ── 章节加载完成回调 ──────────────────────────────────────

    fun onChapterLoaded() {
        if (ttsPendingResume) {
            ttsPendingResume = false
            ttsSentences = sentenceRangesForCurrentPage()
            ttsSentenceIndex = 0
            if (ttsSentences.isNotEmpty()) {
                speakCurrentTtsSentence()
            }
        }
    }

    // ── 释放资源 ──────────────────────────────────────────────

    fun release() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
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

    // ── 内部实现 ──────────────────────────────────────────────

    fun handleTtsUtteranceCompleted() {
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
                nextPage()
                ttsSentences = sentenceRangesForCurrentPage()
                ttsSentenceIndex = 0
                speakCurrentTtsSentence()
                return
            }

            val totalChapters = loadedBookContentProvider()?.normalizedChapters()?.size ?: 0
            if (state.chapterIndex < totalChapters - 1) {
                ttsPendingResume = true
                openChapter(state.chapterIndex + 1)
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
        uiState.value.currentPage?.invalidate()
    }

    private fun sentenceRangesForCurrentPage(): List<SelectionRange> {
        val page = uiState.value.currentPage ?: return emptyList()
        val content = uiState.value.currentChapter?.content ?: return emptyList()
        if (page.lines.isEmpty()) return emptyList()

        val fullText = StringBuilder()
        val lineOffsets = mutableListOf<Int>()
        for (line in page.lines) {
            lineOffsets.add(fullText.length)
            fullText.append(content, line.startCharOffset, line.endCharOffset)
            if (!line.isParagraphEnd) {
                fullText.append('\n')
            }
        }

        val ranges = mutableListOf<SelectionRange>()
        var start = 0
        val text = fullText.toString()
        text.forEachIndexed { index, char ->
            if (char.isSentenceTerminator()) {
                val sentenceText = text.substring(start, index + 1).trim()
                if (sentenceText.isNotBlank()) {
                    val firstLineIndex = lineOffsets.indexOfLast { it <= start }
                    val line = page.lines.getOrNull(firstLineIndex)
                    if (line != null) {
                        ranges += SelectionRange(
                            chapterIndex = page.chapterIndex,
                            startPos = line.startCharOffset + (start - lineOffsets[firstLineIndex]),
                            endPos = line.startCharOffset + (index + 1 - lineOffsets[firstLineIndex]),
                            selectedText = sentenceText,
                        )
                    }
                }
                start = index + 1
            }
        }
        if (start < text.length) {
            val sentenceText = text.substring(start).trim()
            if (sentenceText.isNotBlank()) {
                val firstLineIndex = lineOffsets.indexOfLast { it <= start }
                val line = page.lines.getOrNull(firstLineIndex)
                if (line != null) {
                    ranges += SelectionRange(
                        chapterIndex = page.chapterIndex,
                        startPos = line.startCharOffset + (start - lineOffsets[firstLineIndex]),
                        endPos = line.startCharOffset + (text.length - lineOffsets[firstLineIndex]),
                        selectedText = sentenceText,
                    )
                }
            }
        }
        return ranges
    }

    private fun Char.isSentenceTerminator(): Boolean {
        return this == '.' ||
            this == '!' ||
            this == '?' ||
            this == '。' ||  // 。
            this == '！' ||  // ！
            this == '？' ||  // ？
            this == '…'     // …
    }

    // ── TTS Service 管理 ──────────────────────────────────────

    private fun startTtsService() {
        val ctx = appContextProvider() ?: return
        com.shuli.reader.core.tts.TtsService.onPlay = { resumeTts() }
        com.shuli.reader.core.tts.TtsService.onPause = { pauseTts() }
        com.shuli.reader.core.tts.TtsService.onStop = { stopTts() }
        com.shuli.reader.core.tts.TtsService.isPlaying = { uiState.value.ttsState == TtsState.PLAYING }
        com.shuli.reader.core.tts.TtsService.currentTitle = { uiState.value.bookTitle }
        com.shuli.reader.core.tts.TtsService.currentSubtitle = { uiState.value.chapterTitle }
        val intent = android.content.Intent(ctx, com.shuli.reader.core.tts.TtsService::class.java)
        ctx.startForegroundService(intent)
    }

    private fun updateTtsServiceNotification() {
        val ctx = appContextProvider() ?: return
        val intent = android.content.Intent(ctx, com.shuli.reader.core.tts.TtsService::class.java)
        ctx.startForegroundService(intent)
    }

    private fun stopTtsService() {
        val ctx = appContextProvider() ?: return
        val intent = android.content.Intent(ctx, com.shuli.reader.core.tts.TtsService::class.java).apply {
            action = com.shuli.reader.core.tts.TtsService.ACTION_STOP
        }
        ctx.startService(intent)
        com.shuli.reader.core.tts.TtsService.onPlay = null
        com.shuli.reader.core.tts.TtsService.onPause = null
        com.shuli.reader.core.tts.TtsService.onStop = null
    }
}
