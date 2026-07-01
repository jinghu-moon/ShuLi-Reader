package com.shuli.reader.core.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * App 级旧书索引回填任务。
 * 任务不绑定搜索页 ViewModel，离开页面或 Activity 重建后仍可继续并恢复进度。
 */
class SearchIndexBackfillManager(
    private val searchIndexRepository: SearchIndexRepository,
    private val applicationScope: CoroutineScope,
) {
    private val _progress = MutableStateFlow<SearchIndexBackfillProgress?>(null)
    val progress: StateFlow<SearchIndexBackfillProgress?> = _progress.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return

        job = applicationScope.launch {
            try {
                searchIndexRepository.backfillMissingIndexes().collect { progress ->
                    _progress.value = progress
                }
            } catch (e: CancellationException) {
                _progress.update { progress ->
                    progress?.copy(
                        currentBookTitle = null,
                        isRunning = false,
                        isCompleted = false,
                    )
                }
                throw e
            } catch (e: Exception) {
                _progress.update { progress ->
                    SearchIndexBackfillProgress(
                        totalBooks = progress?.totalBooks ?: 0,
                        processedBooks = progress?.processedBooks ?: 0,
                        indexedBooks = progress?.indexedBooks ?: 0,
                        skippedBooks = progress?.skippedBooks ?: 0,
                        failedBooks = (progress?.failedBooks ?: 0) + 1,
                        currentBookTitle = null,
                        isRunning = false,
                        isCompleted = true,
                    )
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _progress.update { progress ->
            progress?.copy(
                currentBookTitle = null,
                isRunning = false,
                isCompleted = false,
            )
        }
    }
}
