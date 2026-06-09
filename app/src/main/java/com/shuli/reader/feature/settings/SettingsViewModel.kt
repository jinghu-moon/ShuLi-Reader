package com.shuli.reader.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shuli.reader.core.data.PageAnimConst
import com.shuli.reader.core.data.PageTurnDirConst
import com.shuli.reader.core.data.SyncMethodConst
import com.shuli.reader.core.data.UserPreferences
import com.shuli.reader.core.i18n.AppStrings
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
    val appFont: String = "harmony",
    val defaultFontSize: Float = 16f,
    val defaultLineSpacing: Float = 1.5f,
    val defaultParagraphSpacing: Float = 1.0f,
    val defaultIndent: Float = 2.0f,
    val defaultPageAnim: String = PageAnimConst.OVERLAY,
    val pageTurnDir: String = PageTurnDirConst.HORIZONTAL,
    val fullScreen: Boolean = false,
    val keepScreenOn: Boolean = false,
    val brightness: Float = -1f,
    val duplicateCheckEnabled: Boolean = true,
    val importCopyFile: Boolean = true,
    val readingTimeEnabled: Boolean = true,
    val readingDailyTarget: Int = 30,
    val syncMethod: String = SyncMethodConst.LOCAL,
    val webdavUrl: String = "",
    val webdavUser: String = "",
    val webdavPassword: String = "",
    val gpuAcceleration: Boolean = true,
    val loggingEnabled: Boolean = false,
    // 自动备份
    val autoBackupEnabled: Boolean = false,
    val backupOnAppStart: Boolean = false,
    val backupOnAppExit: Boolean = false,
    val backupIntervalHours: Int = 24,
    val backupLocation: String = "",
)

sealed interface SettingsEvent {
    data object Recreate : SettingsEvent
    data class ShowMessage(val message: (AppStrings) -> String) : SettingsEvent
}

class SettingsViewModel(
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    // 组合全部设置项流
    val uiState: StateFlow<SettingsUiState> = combine<Any, SettingsUiState>(
        userPreferences.language,
        userPreferences.themeMode,
        userPreferences.appFont,
        userPreferences.defaultFontSize,
        userPreferences.defaultLineSpacing,
        userPreferences.defaultParagraphSpacing,
        userPreferences.defaultIndent,
        userPreferences.defaultPageAnim,
        userPreferences.pageTurnDir,
        userPreferences.fullScreen,
        userPreferences.keepScreenOn,
        userPreferences.brightness,
        userPreferences.duplicateCheckEnabled,
        userPreferences.importCopyFile,
        userPreferences.readingTimeEnabled,
        userPreferences.readingDailyTarget,
        userPreferences.syncMethod,
        userPreferences.webdavUrl,
        userPreferences.webdavUser,
        userPreferences.webdavPassword,
        userPreferences.gpuAcceleration,
        userPreferences.loggingEnabled,
        userPreferences.autoBackupEnabled,
        userPreferences.backupOnAppStart,
        userPreferences.backupOnAppExit,
        userPreferences.backupIntervalHours,
        userPreferences.backupLocation,
    ) { arr ->
        SettingsUiState(
            language = arr[0] as String,
            themeMode = arr[1] as String,
            appFont = arr[2] as String,
            defaultFontSize = arr[3] as Float,
            defaultLineSpacing = arr[4] as Float,
            defaultParagraphSpacing = arr[5] as Float,
            defaultIndent = arr[6] as Float,
            defaultPageAnim = arr[7] as String,
            pageTurnDir = arr[8] as String,
            fullScreen = arr[9] as Boolean,
            keepScreenOn = arr[10] as Boolean,
            brightness = arr[11] as Float,
            duplicateCheckEnabled = arr[12] as Boolean,
            importCopyFile = arr[13] as Boolean,
            readingTimeEnabled = arr[14] as Boolean,
            readingDailyTarget = arr[15] as Int,
            syncMethod = arr[16] as String,
            webdavUrl = arr[17] as String,
            webdavUser = arr[18] as String,
            webdavPassword = arr[19] as String,
            gpuAcceleration = arr[20] as Boolean,
            loggingEnabled = arr[21] as Boolean,
            autoBackupEnabled = arr[22] as Boolean,
            backupOnAppStart = arr[23] as Boolean,
            backupOnAppExit = arr[24] as Boolean,
            backupIntervalHours = arr[25] as Int,
            backupLocation = arr[26] as String,
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
                _events.emit(SettingsEvent.ShowMessage { it.reader.saveFailed })
            }
        }
    }

    fun updateLanguage(value: String) = updateSetting({ userPreferences.setLanguage(value) }, triggerRecreate = true)
    fun updateThemeMode(value: String) = updateSetting({ userPreferences.setThemeMode(value) })
    fun updateAppFont(value: String) = updateSetting({ userPreferences.setAppFont(value) })

    fun updateDefaultFontSize(value: Float) = updateSetting({ userPreferences.setDefaultFontSize(value) })
    fun updateDefaultLineSpacing(value: Float) = updateSetting({ userPreferences.setDefaultLineSpacing(value) })
    fun updateDefaultParagraphSpacing(value: Float) = updateSetting({ userPreferences.setDefaultParagraphSpacing(value) })
    fun updateDefaultIndent(value: Float) = updateSetting({ userPreferences.setDefaultIndent(value) })
    fun updateDefaultPageAnim(value: String) = updateSetting({ userPreferences.setDefaultPageAnim(value) })
    fun updatePageTurnDir(value: String) = updateSetting({ userPreferences.setPageTurnDir(value) })
    fun updateFullScreen(value: Boolean) = updateSetting({ userPreferences.setFullScreen(value) })
    fun updateKeepScreenOn(value: Boolean) = updateSetting({ userPreferences.setKeepScreenOn(value) })
    fun updateBrightness(value: Float) = updateSetting({ userPreferences.setBrightness(value) })

    fun updateDuplicateCheckEnabled(value: Boolean) = updateSetting({ userPreferences.setDuplicateCheckEnabled(value) })
    fun updateImportCopyFile(value: Boolean) = updateSetting({ userPreferences.setImportCopyFile(value) })

    /** 设置全局统一封面色盘。传 [com.shuli.reader.core.data.COVER_PALETTE_AUTO] 走自动散列。 */
    fun setUnifiedCoverPalette(value: String) = updateSetting({ userPreferences.setUnifiedCoverPalette(value) })

    val unifiedCoverPaletteFlow: kotlinx.coroutines.flow.Flow<String> = userPreferences.unifiedCoverPalette

    fun updateReadingTimeEnabled(value: Boolean) = updateSetting({ userPreferences.setReadingTimeEnabled(value) })
    fun updateReadingDailyTarget(value: Int) = updateSetting({ userPreferences.setReadingDailyTarget(value) })

    fun updateSyncMethod(value: String) = updateSetting({ userPreferences.setSyncMethod(value) })
    fun updateWebdavUrl(value: String) = updateSetting({ userPreferences.setWebdavUrl(value) })
    fun updateWebdavUser(value: String) = updateSetting({ userPreferences.setWebdavUser(value) })
    fun updateWebdavPassword(value: String) = updateSetting({ userPreferences.setWebdavPassword(value) })

    fun updateGpuAcceleration(value: Boolean) = updateSetting({ userPreferences.setGpuAcceleration(value) })
    fun updateLoggingEnabled(value: Boolean) = updateSetting({ userPreferences.setLoggingEnabled(value) })

    // 自动备份
    fun updateAutoBackupEnabled(value: Boolean) = updateSetting({ userPreferences.setAutoBackupEnabled(value) })
    fun updateBackupOnAppStart(value: Boolean) = updateSetting({ userPreferences.setBackupOnAppStart(value) })
    fun updateBackupOnAppExit(value: Boolean) = updateSetting({ userPreferences.setBackupOnAppExit(value) })
    fun updateBackupIntervalHours(value: Int) = updateSetting({ userPreferences.setBackupIntervalHours(value) })
    fun updateBackupLocation(value: String) = updateSetting({ userPreferences.setBackupLocation(value) })

    fun resetAllSettings() {
        updateSetting({
            userPreferences.resetAllSettings()
        }, triggerRecreate = true)
    }
}
