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
    }

    // 状态读取流 (提供首启默认值)
    val language: Flow<String> = dataStore.data.map { it[KEY_LANGUAGE] ?: "zh-CN" }
    val themeMode: Flow<String> = dataStore.data.map { it[KEY_THEME_MODE] ?: "system" }
    val appFont: Flow<String> = dataStore.data.map { it[KEY_APP_FONT] ?: "lxgw" }

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
    val readingFont: Flow<String> = dataStore.data.map { it[KEY_READING_FONT] ?: "system" }

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

    // 重置所有设置项
    suspend fun resetAllSettings() {
        dataStore.edit {
            it.clear()
        }
    }
}
