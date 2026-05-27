package com.shuli.reader.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object PageAnimConst {
    const val OVERLAY = "overlay"
    const val SLIDE = "slide"
    const val SIMULATION = "simulation"
    const val FADE = "fade"
    const val NONE = "none"
}

object PageTurnDirConst {
    const val HORIZONTAL = "horizontal"
    const val VERTICAL = "vertical"
}

object SyncMethodConst {
    const val LOCAL = "local"
    const val WEBDAV = "webdav"
}

/** 统一封面颜色模式：[COVER_PALETTE_AUTO] 走自动散列；其他取值是 "0".."19" 强制使用对应色盘索引。 */
const val COVER_PALETTE_AUTO = "auto"

class UserPreferences(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        // 外观选项
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_APP_FONT = stringPreferencesKey("app_font")

        // 阅读器偏好
        val KEY_DEFAULT_FONT_SIZE = floatPreferencesKey("default_font_size")
        val KEY_DEFAULT_LINE_SPACING = floatPreferencesKey("default_line_spacing")
        val KEY_DEFAULT_PARAGRAPH_SPACING = floatPreferencesKey("default_paragraph_spacing")
        val KEY_DEFAULT_INDENT = floatPreferencesKey("default_indent")
        val KEY_DEFAULT_PAGE_ANIM = stringPreferencesKey("default_page_anim")
        val KEY_PAGE_TURN_DIR = stringPreferencesKey("page_turn_dir")
        val KEY_FULL_SCREEN = booleanPreferencesKey("full_screen")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_BRIGHTNESS = floatPreferencesKey("brightness")
        val KEY_MARGIN_HORIZONTAL = floatPreferencesKey("margin_horizontal")
        val KEY_MARGIN_VERTICAL = floatPreferencesKey("margin_vertical")
        val KEY_READING_FONT = stringPreferencesKey("reading_font")
        // 阶段三新增
        val KEY_LETTER_SPACING = floatPreferencesKey("letter_spacing")
        val KEY_FONT_WEIGHT = stringPreferencesKey("font_weight")
        val KEY_TEXT_ALIGN = stringPreferencesKey("text_align")
        val KEY_CHINESE_CONVERT = stringPreferencesKey("chinese_convert")
        // 阶段五新增：页眉脚
        val KEY_HEADER_VISIBILITY = stringPreferencesKey("header_visibility")
        val KEY_HEADER_LEFT = stringPreferencesKey("header_left")
        val KEY_HEADER_CENTER = stringPreferencesKey("header_center")
        val KEY_HEADER_RIGHT = stringPreferencesKey("header_right")
        val KEY_FOOTER_VISIBILITY = stringPreferencesKey("footer_visibility")
        val KEY_FOOTER_LEFT = stringPreferencesKey("footer_left")
        val KEY_FOOTER_CENTER = stringPreferencesKey("footer_center")
        val KEY_FOOTER_RIGHT = stringPreferencesKey("footer_right")
        val KEY_HEADER_FOOTER_ALPHA = floatPreferencesKey("header_footer_alpha")
        val KEY_SHOW_PROGRESS = booleanPreferencesKey("show_progress")
        // 阶段五新增：正文标题样式
        val KEY_TITLE_ALIGN = stringPreferencesKey("title_align")
        val KEY_TITLE_SIZE_OFFSET = intPreferencesKey("title_size_offset")
        val KEY_TITLE_MARGIN_TOP = floatPreferencesKey("title_margin_top")
        val KEY_TITLE_MARGIN_BOTTOM = floatPreferencesKey("title_margin_bottom")
        // 排版增强
        val KEY_USE_ZH_LAYOUT = booleanPreferencesKey("use_zh_layout")
        val KEY_USE_PANGU_SPACING = booleanPreferencesKey("use_pangu_spacing")
        // 阶段六新增
        val KEY_VOLUME_KEY_TURN_PAGE = booleanPreferencesKey("volume_key_turn_page")
        val KEY_EDGE_TURN_PAGE = booleanPreferencesKey("edge_turn_page")

        // 书库与导入
        val KEY_DUPLICATE_CHECK = booleanPreferencesKey("duplicate_check_enabled")
        val KEY_IMPORT_COPY = booleanPreferencesKey("import_copy_file")
        val KEY_UNIFIED_COVER_PALETTE = stringPreferencesKey("unified_cover_palette")

        // 阅读统计
        val KEY_READING_TIME_ENABLED = booleanPreferencesKey("reading_time_enabled")
        val KEY_READING_DAILY_TARGET = intPreferencesKey("reading_daily_target")

        // 同步配置
        val KEY_SYNC_METHOD = stringPreferencesKey("sync_method")
        val KEY_WEBDAV_URL = stringPreferencesKey("webdav_url")
        val KEY_WEBDAV_USER = stringPreferencesKey("webdav_user")
        val KEY_WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")

        // TTS 朗读
        val KEY_TTS_SPEED = floatPreferencesKey("tts_speed")
        val KEY_TTS_PITCH = floatPreferencesKey("tts_pitch")
        val KEY_TTS_AUTO_PAGE = booleanPreferencesKey("tts_auto_page")
        val KEY_TTS_HIGHLIGHT_SENTENCE = booleanPreferencesKey("tts_highlight_sentence")

        // 高级设置
        val KEY_GPU_ACCELERATION = booleanPreferencesKey("gpu_acceleration")
        val KEY_LOGGING_ENABLED = booleanPreferencesKey("logging_enabled")

        // 书架设置
        val KEY_VIEW_MODE = stringPreferencesKey("view_mode")
    }

    // 状态读取流 (提供首启默认值)
    val language: Flow<String> = dataStore.data.map { it[KEY_LANGUAGE] ?: "zh-CN" }
    val themeMode: Flow<String> = dataStore.data.map { it[KEY_THEME_MODE] ?: "system" }
    val appFont: Flow<String> = dataStore.data.map { it[KEY_APP_FONT] ?: "harmony" }

    val defaultFontSize: Flow<Float> = dataStore.data.map { it[KEY_DEFAULT_FONT_SIZE] ?: 16f }
    val defaultLineSpacing: Flow<Float> = dataStore.data.map { it[KEY_DEFAULT_LINE_SPACING] ?: 1.5f }
    val defaultParagraphSpacing: Flow<Float> = dataStore.data.map { it[KEY_DEFAULT_PARAGRAPH_SPACING] ?: 1.0f }
    val defaultIndent: Flow<Float> = dataStore.data.map { it[KEY_DEFAULT_INDENT] ?: 2.0f }
    val defaultPageAnim: Flow<String> = dataStore.data.map { it[KEY_DEFAULT_PAGE_ANIM] ?: PageAnimConst.OVERLAY }
    val pageTurnDir: Flow<String> = dataStore.data.map { it[KEY_PAGE_TURN_DIR] ?: PageTurnDirConst.HORIZONTAL }
    val fullScreen: Flow<Boolean> = dataStore.data.map { it[KEY_FULL_SCREEN] ?: false }
    val keepScreenOn: Flow<Boolean> = dataStore.data.map { it[KEY_KEEP_SCREEN_ON] ?: false }
    val brightness: Flow<Float> = dataStore.data.map { it[KEY_BRIGHTNESS] ?: -1f } // -1 表示跟随系统
    val marginHorizontal: Flow<Float> = dataStore.data.map { it[KEY_MARGIN_HORIZONTAL] ?: 24f }
    val marginVertical: Flow<Float> = dataStore.data.map { it[KEY_MARGIN_VERTICAL] ?: 48f }
    val readingFont: Flow<String> = dataStore.data.map { it[KEY_READING_FONT] ?: "harmony" }
    // 阶段三新增
    val letterSpacing: Flow<Float> = dataStore.data.map { it[KEY_LETTER_SPACING] ?: 0f }
    val fontWeight: Flow<String> = dataStore.data.map { it[KEY_FONT_WEIGHT] ?: "normal" }
    val textAlign: Flow<String> = dataStore.data.map { it[KEY_TEXT_ALIGN] ?: "left" }
    val chineseConvert: Flow<String> = dataStore.data.map { it[KEY_CHINESE_CONVERT] ?: "none" }
    // 阶段五新增：页眉脚
    val headerVisibility: Flow<String> = dataStore.data.map { it[KEY_HEADER_VISIBILITY] ?: "hide_when_status_bar" }
    val headerLeft: Flow<String> = dataStore.data.map { it[KEY_HEADER_LEFT] ?: "chapter_title" }
    val headerCenter: Flow<String> = dataStore.data.map { it[KEY_HEADER_CENTER] ?: "none" }
    val headerRight: Flow<String> = dataStore.data.map { it[KEY_HEADER_RIGHT] ?: "none" }
    val footerVisibility: Flow<String> = dataStore.data.map { it[KEY_FOOTER_VISIBILITY] ?: "always_show" }
    val footerLeft: Flow<String> = dataStore.data.map { it[KEY_FOOTER_LEFT] ?: "progress" }
    val footerCenter: Flow<String> = dataStore.data.map { it[KEY_FOOTER_CENTER] ?: "page_number" }
    val footerRight: Flow<String> = dataStore.data.map { it[KEY_FOOTER_RIGHT] ?: "time" }
    val headerFooterAlpha: Flow<Float> = dataStore.data.map { it[KEY_HEADER_FOOTER_ALPHA] ?: 0.4f }
    val showProgress: Flow<Boolean> = dataStore.data.map { it[KEY_SHOW_PROGRESS] ?: true }
    // 阶段五新增：正文标题样式
    val titleAlign: Flow<String> = dataStore.data.map { it[KEY_TITLE_ALIGN] ?: "center" }
    val titleSizeOffset: Flow<Int> = dataStore.data.map { it[KEY_TITLE_SIZE_OFFSET] ?: 4 }
    val titleMarginTop: Flow<Float> = dataStore.data.map { it[KEY_TITLE_MARGIN_TOP] ?: 9f }
    val titleMarginBottom: Flow<Float> = dataStore.data.map { it[KEY_TITLE_MARGIN_BOTTOM] ?: 60f }
    // 排版增强
    val useZhLayout: Flow<Boolean> = dataStore.data.map { it[KEY_USE_ZH_LAYOUT] ?: false }
    val usePanguSpacing: Flow<Boolean> = dataStore.data.map { it[KEY_USE_PANGU_SPACING] ?: false }
    // 阶段六新增
    val volumeKeyTurnPage: Flow<Boolean> = dataStore.data.map { it[KEY_VOLUME_KEY_TURN_PAGE] ?: false }
    val edgeTurnPage: Flow<Boolean> = dataStore.data.map { it[KEY_EDGE_TURN_PAGE] ?: true }

    val duplicateCheckEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_DUPLICATE_CHECK] ?: true }
    val importCopyFile: Flow<Boolean> = dataStore.data.map { it[KEY_IMPORT_COPY] ?: true }
    val unifiedCoverPalette: Flow<String> = dataStore.data.map { it[KEY_UNIFIED_COVER_PALETTE] ?: COVER_PALETTE_AUTO }

    val readingTimeEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_READING_TIME_ENABLED] ?: true }
    val readingDailyTarget: Flow<Int> = dataStore.data.map { it[KEY_READING_DAILY_TARGET] ?: 30 }

    val syncMethod: Flow<String> = dataStore.data.map { it[KEY_SYNC_METHOD] ?: SyncMethodConst.LOCAL }
    val webdavUrl: Flow<String> = dataStore.data.map { it[KEY_WEBDAV_URL] ?: "" }
    val webdavUser: Flow<String> = dataStore.data.map { it[KEY_WEBDAV_USER] ?: "" }
    val webdavPassword: Flow<String> = dataStore.data.map { it[KEY_WEBDAV_PASSWORD] ?: "" }

    val ttsSpeed: Flow<Float> = dataStore.data.map { it[KEY_TTS_SPEED] ?: 1.0f }
    val ttsPitch: Flow<Float> = dataStore.data.map { it[KEY_TTS_PITCH] ?: 1.0f }
    val ttsAutoPage: Flow<Boolean> = dataStore.data.map { it[KEY_TTS_AUTO_PAGE] ?: false }
    val ttsHighlightSentence: Flow<Boolean> = dataStore.data.map { it[KEY_TTS_HIGHLIGHT_SENTENCE] ?: false }

    val gpuAcceleration: Flow<Boolean> = dataStore.data.map { it[KEY_GPU_ACCELERATION] ?: true }
    val loggingEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_LOGGING_ENABLED] ?: false }

    val viewMode: Flow<String> = dataStore.data.map { it[KEY_VIEW_MODE] ?: "GRID" }

    // 状态编辑写入方法
    suspend fun setLanguage(value: String) = dataStore.edit { it[KEY_LANGUAGE] = value }
    suspend fun setThemeMode(value: String) = dataStore.edit { it[KEY_THEME_MODE] = value }
    suspend fun setAppFont(value: String) = dataStore.edit { it[KEY_APP_FONT] = value }

    suspend fun setDefaultFontSize(value: Float) = dataStore.edit { it[KEY_DEFAULT_FONT_SIZE] = value }
    suspend fun setDefaultLineSpacing(value: Float) = dataStore.edit { it[KEY_DEFAULT_LINE_SPACING] = value }
    suspend fun setDefaultParagraphSpacing(value: Float) = dataStore.edit { it[KEY_DEFAULT_PARAGRAPH_SPACING] = value }
    suspend fun setDefaultIndent(value: Float) = dataStore.edit { it[KEY_DEFAULT_INDENT] = value }
    suspend fun setDefaultPageAnim(value: String) = dataStore.edit { it[KEY_DEFAULT_PAGE_ANIM] = value }
    suspend fun setPageTurnDir(value: String) = dataStore.edit { it[KEY_PAGE_TURN_DIR] = value }
    suspend fun setFullScreen(value: Boolean) = dataStore.edit { it[KEY_FULL_SCREEN] = value }
    suspend fun setKeepScreenOn(value: Boolean) = dataStore.edit { it[KEY_KEEP_SCREEN_ON] = value }
    suspend fun setBrightness(value: Float) = dataStore.edit { it[KEY_BRIGHTNESS] = value }
    suspend fun setMarginHorizontal(value: Float) = dataStore.edit { it[KEY_MARGIN_HORIZONTAL] = value }
    suspend fun setMarginVertical(value: Float) = dataStore.edit { it[KEY_MARGIN_VERTICAL] = value }
    suspend fun setReadingFont(value: String) = dataStore.edit { it[KEY_READING_FONT] = value }
    // 阶段三新增
    suspend fun setLetterSpacing(value: Float) = dataStore.edit { it[KEY_LETTER_SPACING] = value }
    suspend fun setFontWeight(value: String) = dataStore.edit { it[KEY_FONT_WEIGHT] = value }
    suspend fun setTextAlign(value: String) = dataStore.edit { it[KEY_TEXT_ALIGN] = value }
    suspend fun setChineseConvert(value: String) = dataStore.edit { it[KEY_CHINESE_CONVERT] = value }
    // 阶段五新增：页眉脚
    suspend fun setHeaderVisibility(value: String) = dataStore.edit { it[KEY_HEADER_VISIBILITY] = value }
    suspend fun setHeaderLeft(value: String) = dataStore.edit { it[KEY_HEADER_LEFT] = value }
    suspend fun setHeaderCenter(value: String) = dataStore.edit { it[KEY_HEADER_CENTER] = value }
    suspend fun setHeaderRight(value: String) = dataStore.edit { it[KEY_HEADER_RIGHT] = value }
    suspend fun setFooterVisibility(value: String) = dataStore.edit { it[KEY_FOOTER_VISIBILITY] = value }
    suspend fun setFooterLeft(value: String) = dataStore.edit { it[KEY_FOOTER_LEFT] = value }
    suspend fun setFooterCenter(value: String) = dataStore.edit { it[KEY_FOOTER_CENTER] = value }
    suspend fun setFooterRight(value: String) = dataStore.edit { it[KEY_FOOTER_RIGHT] = value }
    suspend fun setHeaderFooterAlpha(value: Float) = dataStore.edit { it[KEY_HEADER_FOOTER_ALPHA] = value }
    suspend fun setShowProgress(value: Boolean) = dataStore.edit { it[KEY_SHOW_PROGRESS] = value }
    // 阶段五新增：正文标题样式
    suspend fun setTitleAlign(value: String) = dataStore.edit { it[KEY_TITLE_ALIGN] = value }
    suspend fun setTitleSizeOffset(value: Int) = dataStore.edit { it[KEY_TITLE_SIZE_OFFSET] = value }
    suspend fun setTitleMarginTop(value: Float) = dataStore.edit { it[KEY_TITLE_MARGIN_TOP] = value }
    suspend fun setTitleMarginBottom(value: Float) = dataStore.edit { it[KEY_TITLE_MARGIN_BOTTOM] = value }
    // 排版增强
    suspend fun setUseZhLayout(value: Boolean) = dataStore.edit { it[KEY_USE_ZH_LAYOUT] = value }
    suspend fun setUsePanguSpacing(value: Boolean) = dataStore.edit { it[KEY_USE_PANGU_SPACING] = value }
    // 阶段六新增
    suspend fun setVolumeKeyTurnPage(value: Boolean) = dataStore.edit { it[KEY_VOLUME_KEY_TURN_PAGE] = value }
    suspend fun setEdgeTurnPage(value: Boolean) = dataStore.edit { it[KEY_EDGE_TURN_PAGE] = value }

    suspend fun setDuplicateCheckEnabled(value: Boolean) = dataStore.edit { it[KEY_DUPLICATE_CHECK] = value }
    suspend fun setImportCopyFile(value: Boolean) = dataStore.edit { it[KEY_IMPORT_COPY] = value }
    suspend fun setUnifiedCoverPalette(value: String) = dataStore.edit { it[KEY_UNIFIED_COVER_PALETTE] = value }

    suspend fun setReadingTimeEnabled(value: Boolean) = dataStore.edit { it[KEY_READING_TIME_ENABLED] = value }
    suspend fun setReadingDailyTarget(value: Int) = dataStore.edit { it[KEY_READING_DAILY_TARGET] = value }

    suspend fun setSyncMethod(value: String) = dataStore.edit { it[KEY_SYNC_METHOD] = value }
    suspend fun setWebdavUrl(value: String) = dataStore.edit { it[KEY_WEBDAV_URL] = value }
    suspend fun setWebdavUser(value: String) = dataStore.edit { it[KEY_WEBDAV_USER] = value }
    suspend fun setWebdavPassword(value: String) = dataStore.edit { it[KEY_WEBDAV_PASSWORD] = value }

    suspend fun setTtsSpeed(value: Float) = dataStore.edit { it[KEY_TTS_SPEED] = value }
    suspend fun setTtsPitch(value: Float) = dataStore.edit { it[KEY_TTS_PITCH] = value }
    suspend fun setTtsAutoPage(value: Boolean) = dataStore.edit { it[KEY_TTS_AUTO_PAGE] = value }
    suspend fun setTtsHighlightSentence(value: Boolean) = dataStore.edit { it[KEY_TTS_HIGHLIGHT_SENTENCE] = value }

    suspend fun setGpuAcceleration(value: Boolean) = dataStore.edit { it[KEY_GPU_ACCELERATION] = value }
    suspend fun setLoggingEnabled(value: Boolean) = dataStore.edit { it[KEY_LOGGING_ENABLED] = value }

    suspend fun setViewMode(value: String) = dataStore.edit { it[KEY_VIEW_MODE] = value }

    // 重置所有设置项
    suspend fun resetAllSettings() {
        dataStore.edit {
            it.clear()
        }
    }
}
