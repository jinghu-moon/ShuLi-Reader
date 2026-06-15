package com.shuli.reader.sync.engine.dirty

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 偏好设置脏标记追踪器（T-05）
 *
 * 第一层脏标记：追踪哪些偏好设置键被修改过。
 * 使用 MutableStateFlow 保证线程安全。
 */
class PreferencesDirtyTracker {

    private val _dirtyKeys = MutableStateFlow<Set<String>>(emptySet())
    val dirtyKeys: StateFlow<Set<String>> = _dirtyKeys.asStateFlow()

    /**
     * 检查是否有脏标记
     */
    fun hasDirty(): Boolean = _dirtyKeys.value.isNotEmpty()

    /**
     * 标记某个偏好键为脏
     */
    fun markDirty(key: String) {
        _dirtyKeys.update { current -> current + key }
    }

    /**
     * 清除所有脏标记
     */
    fun clearDirty() {
        _dirtyKeys.value = emptySet()
    }
}
