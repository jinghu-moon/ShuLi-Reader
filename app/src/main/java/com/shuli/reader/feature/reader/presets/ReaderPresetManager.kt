package com.shuli.reader.feature.reader.presets

import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.database.dao.ReaderPresetDao
import com.shuli.reader.core.database.entity.ReaderPresetEntity
import com.shuli.reader.feature.reader.ReaderUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * 预设管理器。
 *
 * 职责：阅读预设的增删改查、应用、恢复默认。
 * 通过 [uiState] 读写共享状态，不反向依赖 ViewModel。
 */
class ReaderPresetManager(
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val presetDao: ReaderPresetDao?,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "ReaderPresetMgr"
    }

    // ── 回调（由 ViewModel 注入）────────────────────────────────────

    /** 应用一组阅读偏好（由 ViewModel 提供，内部调用各 setter + reflow） */
    var onApplyPreferences: ((ReaderPreferences) -> Unit)? = null

    // ── 内部状态 ──────────────────────────────────────────────────

    private var presetsJob: Job? = null

    // ── 公开 API ──────────────────────────────────────────────────

    /** 加载预设列表 */
    fun loadPresets() {
        val dao = presetDao ?: return
        presetsJob?.cancel()
        presetsJob = scope.launch {
            dao.observeAll().collect { presets ->
                uiState.value = uiState.value.copy(presets = presets)
            }
        }
    }

    /** 保存当前设置为预设 */
    fun saveCurrentAsPreset(name: String) {
        val dao = presetDao ?: return
        scope.launch {
            val currentPrefs = uiState.value.readerPreferences
            val configJson = Json.encodeToString(
                ReaderPreferences.serializer(),
                currentPrefs,
            )
            val entity = ReaderPresetEntity(
                name = name,
                createdAt = System.currentTimeMillis(),
                configJson = configJson,
            )
            dao.insert(entity)
        }
    }

    /** 应用预设 */
    fun applyPreset(presetId: Long) {
        val dao = presetDao ?: return
        scope.launch {
            val entity = dao.getById(presetId) ?: return@launch
            try {
                val prefs = Json.decodeFromString(
                    ReaderPreferences.serializer(),
                    entity.configJson,
                )
                onApplyPreferences?.invoke(prefs)
            } catch (e: Exception) {
                uiState.value = uiState.value.copy(error = "Failed to apply preset: ${e.message}")
            }
        }
    }

    /** 重命名预设 */
    fun renamePreset(presetId: Long, newName: String) {
        val dao = presetDao ?: return
        scope.launch {
            val entity = dao.getById(presetId) ?: return@launch
            dao.update(entity.copy(name = newName))
        }
    }

    /** 删除预设 */
    fun deletePreset(presetId: Long) {
        val dao = presetDao ?: return
        scope.launch {
            dao.deleteById(presetId)
        }
    }

    /** 恢复默认设置 */
    fun resetToDefault() {
        val defaults = ReaderPreferences()
        onApplyPreferences?.invoke(defaults)
    }

    /** 释放资源（ViewModel.onCleared 时调用） */
    fun release() {
        presetsJob?.cancel()
        presetsJob = null
    }
}
