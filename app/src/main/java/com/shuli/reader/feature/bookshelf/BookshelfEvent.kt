package com.shuli.reader.feature.bookshelf

import com.shuli.reader.core.i18n.AppStrings

/**
 * 书架 ViewModel 事件。
 * 从 BookshelfViewModel 拆出，避免事件定义与业务逻辑混杂。
 */
sealed class BookshelfEvent {
    data class NavigateToReader(val bookId: Long) : BookshelfEvent()
    data class ShowMessage(val message: (AppStrings) -> String) : BookshelfEvent()
    data class HighlightBook(val bookId: Long) : BookshelfEvent()
}

internal class UnableToReadFileException : IllegalStateException()
internal class InvalidFolderPathException : IllegalArgumentException()

internal fun Throwable.toImportErrorMessage(strings: AppStrings): String {
    return when (this) {
        is UnableToReadFileException -> strings.bookshelf.unableToReadFile
        is InvalidFolderPathException -> strings.bookshelf.invalidFolderPath
        else -> message.orEmpty()
    }
}
