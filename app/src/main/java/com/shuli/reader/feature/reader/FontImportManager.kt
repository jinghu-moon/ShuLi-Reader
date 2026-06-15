package com.shuli.reader.feature.reader

import com.shuli.reader.core.font.FontManager
import com.shuli.reader.core.i18n.AppStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 字体导入管理器（从 ReaderViewModel 拆出，SRP）
 *
 * 职责：自定义字体的加载、导入、删除。
 */
internal class FontImportManager(
    private val fontManager: FontManager?,
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val scope: CoroutineScope,
    private val stringResolver: () -> AppStrings,
) {

    fun loadCustomFonts() {
        val fm = fontManager
        if (fm == null) {
            android.util.Log.w("FontManager", "loadCustomFonts: fontManager 为 null")
            return
        }
        val fonts = fm.listFonts(currentAppLocale())
        android.util.Log.d("FontManager", "loadCustomFonts: 加载了 ${fonts.size} 个自定义字体")
        uiState.value = uiState.value.copy(customFonts = fonts)
    }

    fun importFont(uri: android.net.Uri, displayName: String? = null) {
        val fm = fontManager ?: run {
            android.util.Log.e("FontManager", "importFont: fontManager 为 null，无法导入")
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val entry = fm.importFont(uri, displayName, currentAppLocale())
                android.util.Log.d("FontManager", "字体导入成功: id=${entry.id}, file=${entry.file.name}, size=${entry.file.length()}")
                withContext(Dispatchers.Main) {
                    loadCustomFonts()
                    val count = uiState.value.customFonts.size
                    android.util.Log.d("FontManager", "loadCustomFonts 完成, customFonts.size=$count")
                    android.widget.Toast.makeText(
                        fm.context,
                        stringResolver().sync.fontImportSuccess(entry.name, count),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("FontManager", "字体导入失败: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        fm.context,
                        stringResolver().sync.fontImportFailed(e.message ?: ""),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    fun deleteFont(fontId: String) {
        val fm = fontManager ?: return
        fm.deleteFontById(fontId)
        loadCustomFonts()
    }

    /** 根据当前应用语言设置返回对应 Locale，用于 name 表的本地化条目选择。 */
    private fun currentAppLocale(): Locale = when (stringResolver()) {
        is AppStrings.ZhHant -> Locale.TRADITIONAL_CHINESE   // zh-TW
        is AppStrings.En -> Locale.ENGLISH                  // en
        else -> Locale.SIMPLIFIED_CHINESE                   // zh-CN (默认)
    }
}
