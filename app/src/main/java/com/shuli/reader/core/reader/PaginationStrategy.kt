package com.shuli.reader.core.reader

import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.reader.model.PageSize
import com.shuli.reader.core.reader.model.TextChapter

/**
 * 分页策略接口。
 *
 * 各策略（横排 / 竖排）自行从 [ReaderPreferences] 解析所需参数，
 * 不绑死 [com.shuli.reader.core.reader.model.ReaderLayoutConfig]。
 */
interface PaginationStrategy {
    fun paginate(
        chapterIndex: Int,
        title: String,
        content: String,
        prefs: ReaderPreferences,
        pageSize: PageSize,
        density: Float,
        showHeader: Boolean = true,
        showFooter: Boolean = true,
    ): TextChapter
}
