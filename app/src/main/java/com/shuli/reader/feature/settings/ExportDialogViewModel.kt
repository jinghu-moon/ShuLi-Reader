package com.shuli.reader.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shuli.reader.sync.export.ExportOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 导出大小预估状态（T-39）
 */
sealed class SizeEstimate {
    /** 正在计算 */
    data object Calculating : SizeEstimate()

    /** 计算完成 */
    data class Calculated(val bytes: Long) : SizeEstimate() {
        val displaySize: String
            get() = when {
                bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
                bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
                bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
                else -> "$bytes B"
            }
    }

    /** 计算失败 */
    data class Error(val message: String) : SizeEstimate()
}

/**
 * 导出对话框 ViewModel（T-39）
 *
 * 管理导出选项状态和异步大小预估。
 */
class ExportDialogViewModel(
    private val sizeEstimator: ExportSizeEstimator? = null,
) : ViewModel() {

    private val _estimatedSize = MutableStateFlow<SizeEstimate>(SizeEstimate.Calculating)
    val estimatedSize: StateFlow<SizeEstimate> = _estimatedSize.asStateFlow()

    private val _options = MutableStateFlow(ExportOptions())
    val options: StateFlow<ExportOptions> = _options.asStateFlow()

    private var estimateJob: Job? = null

    init {
        reEstimate()
    }

    fun toggleIncludeBookFiles(include: Boolean) {
        _options.value = _options.value.copy(includeBookFiles = include)
        reEstimate()
    }

    fun toggleIncludeBookmarks(include: Boolean) {
        _options.value = _options.value.copy(includeBookmarks = include)
        reEstimate()
    }

    fun toggleIncludeNotes(include: Boolean) {
        _options.value = _options.value.copy(includeNotes = include)
        reEstimate()
    }

    fun toggleIncludeProgress(include: Boolean) {
        _options.value = _options.value.copy(includeProgress = include)
        reEstimate()
    }

    fun toggleIncludeConfig(include: Boolean) {
        _options.value = _options.value.copy(includeConfig = include)
        reEstimate()
    }

    fun setExportPassword(password: String?) {
        _options.value = _options.value.copy(encryptionPassword = password)
    }

    fun buildExportOptions(): ExportOptions = _options.value

    private fun reEstimate() {
        estimateJob?.cancel()
        _estimatedSize.value = SizeEstimate.Calculating

        val estimator = sizeEstimator
        if (estimator == null) {
            _estimatedSize.value = SizeEstimate.Error("Size estimator not available")
            return
        }

        estimateJob = viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    estimator.estimateSize(_options.value)
                }
                _estimatedSize.value = SizeEstimate.Calculated(bytes)
            } catch (e: Exception) {
                _estimatedSize.value = SizeEstimate.Error(e.message ?: "Unknown error")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        estimateJob?.cancel()
        // 清除密码，不持久化
        _options.value = _options.value.copy(encryptionPassword = null)
    }
}

/**
 * 导出大小预估器接口
 */
fun interface ExportSizeEstimator {
    suspend fun estimateSize(options: ExportOptions): Long
}
