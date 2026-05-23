package com.shuli.reader.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shuli.reader.core.data.UserPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val language: String = "zh-CN",
    val themeMode: String = "system",
    val appFont: String = "lxgw",
    val defaultFontSize: Float = 16f,
    val defaultLineSpacing: Float = 1.5f,
    val defaultPageAnim: String = "覆盖",
    val pageTurnDir: String = "左右滑动",
    val duplicateCheckEnabled: Boolean = true,
    val importCopyFile: Boolean = true,
    val readingTimeEnabled: Boolean = true,
    val readingDailyTarget: Int = 30,
    val syncMethod: String = "本地备份",
    val webdavUrl: String = "",
    val webdavUser: String = "",
    val webdavPassword: String = "",
    val ttsSpeed: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsAutoPage: Boolean = false,
    val ttsHighlightSentence: Boolean = false,
    val gpuAcceleration: Boolean = true,
    val loggingEnabled: Boolean = false
)

sealed interface SettingsEvent {
    data object Recreate : SettingsEvent
    data class ShowMessage(val message: String) : SettingsEvent
}

class SettingsViewModel(
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    // 组合全部设置项流
    val uiState: StateFlow<SettingsUiState> = combine(
        userPreferences.language,
        userPreferences.themeMode,
        userPreferences.appFont,
        userPreferences.defaultFontSize,
        userPreferences.defaultLineSpacing,
        userPreferences.defaultPageAnim,
        userPreferences.pageTurnDir,
        userPreferences.duplicateCheckEnabled,
        userPreferences.importCopyFile,
        userPreferences.readingTimeEnabled,
        userPreferences.readingDailyTarget,
        userPreferences.syncMethod,
        userPreferences.webdavUrl,
        userPreferences.webdavUser,
        userPreferences.webdavPassword,
        userPreferences.ttsSpeed,
        userPreferences.ttsPitch,
        userPreferences.ttsAutoPage,
        userPreferences.ttsHighlightSentence,
        userPreferences.gpuAcceleration,
        userPreferences.loggingEnabled
    ) { arr ->
        SettingsUiState(
            language = arr[0] as String,
            themeMode = arr[1] as String,
            appFont = arr[2] as String,
            defaultFontSize = arr[3] as Float,
            defaultLineSpacing = arr[4] as Float,
            defaultPageAnim = arr[5] as String,
            pageTurnDir = arr[6] as String,
            duplicateCheckEnabled = arr[7] as Boolean,
            importCopyFile = arr[8] as Boolean,
            readingTimeEnabled = arr[9] as Boolean,
            readingDailyTarget = arr[10] as Int,
            syncMethod = arr[11] as String,
            webdavUrl = arr[12] as String,
            webdavUser = arr[13] as String,
            webdavPassword = arr[14] as String,
            ttsSpeed = arr[15] as Float,
            ttsPitch = arr[16] as Float,
            ttsAutoPage = arr[17] as Boolean,
            ttsHighlightSentence = arr[18] as Boolean,
            gpuAcceleration = arr[19] as Boolean,
            loggingEnabled = arr[20] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    // 修改设置包装函数
    private fun updateSetting(action: suspend () -> Unit, triggerRecreate: Boolean = false) {
        viewModelScope.launch {
            runCatching {
                action()
            }.onSuccess {
                if (triggerRecreate) {
                    _events.emit(SettingsEvent.Recreate)
                }
            }.onFailure {
                _events.emit(SettingsEvent.ShowMessage("保存设置失败 / Failed to save settings"))
            }
        }
    }

    fun updateLanguage(value: String) = updateSetting({ userPreferences.setLanguage(value) }, triggerRecreate = true)
    fun updateThemeMode(value: String) = updateSetting({ userPreferences.setThemeMode(value) })
    fun updateAppFont(value: String) = updateSetting({ userPreferences.setAppFont(value) })

    fun updateDefaultFontSize(value: Float) = updateSetting({ userPreferences.setDefaultFontSize(value) })
    fun updateDefaultLineSpacing(value: Float) = updateSetting({ userPreferences.setDefaultLineSpacing(value) })
    fun updateDefaultPageAnim(value: String) = updateSetting({ userPreferences.setDefaultPageAnim(value) })
    fun updatePageTurnDir(value: String) = updateSetting({ userPreferences.setPageTurnDir(value) })

    fun updateDuplicateCheckEnabled(value: Boolean) = updateSetting({ userPreferences.setDuplicateCheckEnabled(value) })
    fun updateImportCopyFile(value: Boolean) = updateSetting({ userPreferences.setImportCopyFile(value) })

    fun updateReadingTimeEnabled(value: Boolean) = updateSetting({ userPreferences.setReadingTimeEnabled(value) })
    fun updateReadingDailyTarget(value: Int) = updateSetting({ userPreferences.setReadingDailyTarget(value) })

    fun updateSyncMethod(value: String) = updateSetting({ userPreferences.setSyncMethod(value) })
    fun updateWebdavUrl(value: String) = updateSetting({ userPreferences.setWebdavUrl(value) })
    fun updateWebdavUser(value: String) = updateSetting({ userPreferences.setWebdavUser(value) })
    fun updateWebdavPassword(value: String) = updateSetting({ userPreferences.setWebdavPassword(value) })

    fun updateTtsSpeed(value: Float) = updateSetting({ userPreferences.setTtsSpeed(value) })
    fun updateTtsPitch(value: Float) = updateSetting({ userPreferences.setTtsPitch(value) })
    fun updateTtsAutoPage(value: Boolean) = updateSetting({ userPreferences.setTtsAutoPage(value) })
    fun updateTtsHighlightSentence(value: Boolean) = updateSetting({ userPreferences.setTtsHighlightSentence(value) })

    fun updateGpuAcceleration(value: Boolean) = updateSetting({ userPreferences.setGpuAcceleration(value) })
    fun updateLoggingEnabled(value: Boolean) = updateSetting({ userPreferences.setLoggingEnabled(value) })

    fun resetAllSettings() {
        updateSetting({
            userPreferences.resetAllSettings()
        }, triggerRecreate = true)
    }
}
