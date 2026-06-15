package com.shuli.reader.feature.reader.session

import com.shuli.reader.core.reader.engine.Paginator
import com.shuli.reader.core.reader.model.ReaderLayoutConfig
import com.shuli.reader.core.reader.model.TextChapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

/**
 * 章节提供器，负责虚拟化章节加载
 */
class ChapterProvider(
    private val paginator: Paginator,
) {
    private var currentJob: Job? = null

    /**
     * 加载章节，只加载当前章节及前后一章
     */
    suspend fun loadChapter(
        chapterIndex: Int,
        chapters: List<Pair<String, String>>, // Pair<title, content>
        config: ReaderLayoutConfig,
        onChapterLoaded: (TextChapter) -> Unit,
    ) {
        // 取消之前的任务
        currentJob?.cancel()

        try {
            // 加载当前章节
            val chapter = loadSingleChapter(chapterIndex, chapters, config)
            onChapterLoaded(chapter)

            // 预加载前后章节
            if (chapterIndex > 0) {
                loadSingleChapter(chapterIndex - 1, chapters, config)
            }
            if (chapterIndex < chapters.size - 1) {
                loadSingleChapter(chapterIndex + 1, chapters, config)
            }
        } catch (e: CancellationException) {
            // 任务被取消，忽略
        }
    }

    /**
     * 加载单个章节
     */
    private suspend fun loadSingleChapter(
        chapterIndex: Int,
        chapters: List<Pair<String, String>>,
        config: ReaderLayoutConfig,
    ): TextChapter {
        return withContext(Dispatchers.Default) {
            val (title, content) = chapters[chapterIndex]
            paginator.paginateChapter(chapterIndex, title, content, config)
        }
    }

    /**
     * 取消当前任务
     */
    fun cancel() {
        currentJob?.cancel()
        currentJob = null
    }
}