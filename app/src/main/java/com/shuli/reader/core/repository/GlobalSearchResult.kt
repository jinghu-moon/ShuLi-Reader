package com.shuli.reader.core.repository

import androidx.compose.runtime.Immutable

@Immutable
data class GlobalSearchResult(
    val bookId: Long,
    val bookTitle: String,
    val author: String?,
    val chapterIndex: Int,
    val chapterTitle: String,
    val byteOffset: Long,
    val context: String,
    val matchedText: String,
)

@Immutable
data class GlobalSearchBookOption(
    val id: Long,
    val title: String,
    val author: String?,
    val isIndexed: Boolean,
)

sealed interface GlobalSearchEvent {
    data class Result(val item: GlobalSearchResult) : GlobalSearchEvent
    data class Progress(val processedChapters: Int, val resultsFound: Int) : GlobalSearchEvent
    data class Completed(val totalResults: Int) : GlobalSearchEvent
    data class Error(val message: String) : GlobalSearchEvent
}

sealed interface GlobalSearchScope {
    data object All : GlobalSearchScope
    data class Books(val bookIds: List<Long>) : GlobalSearchScope
}
