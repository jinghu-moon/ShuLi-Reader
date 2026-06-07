package com.shuli.reader.feature.reader

import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.database.dao.ReaderPresetDao
import com.shuli.reader.core.database.entity.ReaderPresetEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 阅读预设管理器（从 ReaderViewModel 拆出）
 *
 * 职责：预设增删改查、应用、重置。
 */
class ReaderPresetManager(
    private val presetDao: ReaderPresetDao?,
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val scope: CoroutineScope,
    private val applyPreferences: (ReaderPreferences) -> Unit,
) {
    fun loadPresets() {
        val dao = presetDao ?: return
        scope.launch {
            dao.observeAll().collect { presets ->
                uiState.value = uiState.value.copy(presets = presets)
            }
        }
    }

    fun saveCurrentAsPreset(name: String) {
        val dao = presetDao ?: return
        scope.launch {
            val currentPrefs = uiState.value.readerPreferences
            val configJson = kotlinx.serialization.json.Json.encodeToString(
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

    fun applyPreset(presetId: Long) {
        val dao = presetDao ?: return
        scope.launch {
            val entity = dao.getById(presetId) ?: return@launch
            try {
                val prefs = kotlinx.serialization.json.Json.decodeFromString(
                    ReaderPreferences.serializer(),
                    entity.configJson,
                )
                applyPreferences(prefs)
            } catch (e: Exception) {
                uiState.value = uiState.value.copy(error = "Failed to apply preset: ${e.message}")
            }
        }
    }

    fun renamePreset(presetId: Long, newName: String) {
        val dao = presetDao ?: return
        scope.launch {
            val entity = dao.getById(presetId) ?: return@launch
            dao.update(entity.copy(name = newName))
        }
    }

    fun deletePreset(presetId: Long) {
        val dao = presetDao ?: return
        scope.launch {
            dao.deleteById(presetId)
        }
    }

    fun resetToDefault() {
        applyPreferences(ReaderPreferences())
    }
}
