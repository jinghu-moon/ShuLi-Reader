package com.shuli.reader.feature.reader

import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.core.data.ThemeColors
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.database.entity.ChapterReadingStatsEntity
import com.shuli.reader.core.database.entity.NoteEntity
import com.shuli.reader.core.reader.model.PageRenderMode
import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.reader.model.TextChapter
import com.shuli.reader.core.reader.model.TextPage
import com.shuli.reader.core.repository.SearchResult
import com.shuli.reader.core.reader.animation.PageDelegateFactory
import com.shuli.reader.ui.theme.resolveCustomColorScheme
import com.shuli.reader.ui.theme.toCanvasThemeColors
import com.shuli.reader.ui.theme.toReaderColorScheme

/**
 * 阅读器浮层面板类型
 */
enum class OverlayPanel {
    NONE, DIRECTORY, QUICK_SETTINGS
}

/**
 * 阅读器 UI 状态
 */
data class ReaderUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val bookId: Long = 0L,
    val bookTitle: String = "",
    val chapterTitle: String = "",
    val currentPage: TextPage? = null,
    val currentChapter: TextChapter? = null,
    val chapterIndex: Int = 0,
    val pageIndex: Int = 0,
    val totalPages: Int = 0,
    val totalChapters: Int = 0,
    val showToolbar: Boolean = false,
    val overlayPanel: OverlayPanel = OverlayPanel.NONE,
    val showMenu: Boolean = false,
    val showSearch: Boolean = false,
    val pageAnimType: PageDelegateFactory.PageAnimType = PageDelegateFactory.PageAnimType.HORIZONTAL,
    val pageRenderMode: PageRenderMode = PageRenderMode.SEQUENTIAL,
    val readerPreferences: ReaderPreferences = ReaderPreferences(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val chapterTitles: List<String> = emptyList(),
    val chapterWordCounts: List<Int> = emptyList(),
    /** 每章阅读统计（已读/未读、阅读耗时） */
    val chapterStats: List<ChapterReadingStatsEntity> = emptyList(),
    /** 已分页章节的真实页数（key=chapterIndex），用于精确计算全书进度 */
    val chapterPageCounts: Map<Int, Int> = emptyMap(),
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val currentSearchResultIndex: Int = -1,
    val selectedRange: SelectionRange? = null,
    val sleepTimerRemainingSeconds: Int = -1,
    val presets: List<com.shuli.reader.core.database.entity.ReaderPresetEntity> = emptyList(),
    /** 用户导入的自定义字体列表 */
    val customFonts: List<com.shuli.reader.core.font.FontManager.FontEntry> = emptyList(),
    /** 缓存的主题颜色，避免每次访问 themeColors 都创建中间对象 */
    val themeColors: ThemeColors = resolveThemeColors(readerPreferences),
    /** 排版版本号，每次 reflow 递增，用于 Canvas 层 crossfade 判断 */
    val layoutVersion: Int = 0,
    /** 排版 reflow 进行中，为 true 时跳过 Paint 更新，避免旧页面用新字号渲染导致闪烁 */
    val isReflowing: Boolean = false,
    /** §11.1.1.1: T0 fallback 用的持久化摘要（进程死亡恢复） */
    val snapshotDigest: com.shuli.reader.core.database.dao.SnapshotDigestTuple? = null,
    /** 当前设置作用域：GLOBAL（全局默认）或 BOOK（本书覆盖） */
    val settingsScope: SettingsScope = SettingsScope.GLOBAL,
    /** 本书是否有自定义覆盖（用于 UI 显示"已自定义"标记） */
    val hasBookOverrides: Boolean = false,
) {
    val showDirectory: Boolean get() = overlayPanel == OverlayPanel.DIRECTORY
    val showQuickSettings: Boolean get() = overlayPanel == OverlayPanel.QUICK_SETTINGS
}

/** 根据主题类型解析 ThemeColors，CUSTOM 主题使用自定义颜色值 */
internal fun resolveThemeColors(prefs: ReaderPreferences): ThemeColors {
    if (prefs.backgroundColor == ReaderTheme.CUSTOM) {
        val bg = prefs.customBackgroundColor ?: 0xFFF6F4F0.toInt()
        val text = prefs.customTextColor ?: 0xFF453B2E.toInt()
        val accent = prefs.customAccentColor ?: 0xFF6B5B4E.toInt()
        return resolveCustomColorScheme(bg, text, accent).toCanvasThemeColors()
    }
    return prefs.backgroundColor.toReaderColorScheme().toCanvasThemeColors()
}
