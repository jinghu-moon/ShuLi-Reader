package com.shuli.reader.core.repository

/**
 * 书籍操作的领域错误模型。
 * 避免 ViewModel 直接拼接异常信息，UI 层根据类型展示本地化文案。
 */
sealed class BookResult<out T> {
    data class Success<T>(val data: T) : BookResult<T>()
    data class Failure(val error: BookError) : BookResult<Nothing>()
}

sealed class BookError {
    /** 文件格式不支持 */
    data object UnsupportedFormat : BookError()

    /** 文件读取失败 */
    data class FileReadFailed(val detail: String) : BookError()

    /** EPUB 结构损坏（缺少 OPF/container） */
    data object EpubStructureInvalid : BookError()

    /** 书籍已存在 */
    data class Duplicate(val bookId: Long, val bookTitle: String) : BookError()

    /** 数据库操作失败 */
    data class DatabaseError(val detail: String) : BookError()

    /** 未知错误 */
    data class Unknown(val detail: String) : BookError()
}

/**
 * 将 BookResult 的失败分支映射为本地化字符串。
 * 由 UI 层调用，注入 AppStrings。
 */
inline fun <T> BookResult<T>.onSuccess(action: (T) -> Unit): BookResult<T> {
    if (this is BookResult.Success) action(data)
    return this
}

inline fun <T> BookResult<T>.onFailure(action: (BookError) -> Unit): BookResult<T> {
    if (this is BookResult.Failure) action(error)
    return this
}
