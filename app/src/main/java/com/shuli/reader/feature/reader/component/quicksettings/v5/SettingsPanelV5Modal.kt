package com.shuli.reader.feature.reader.component.quicksettings.v5

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.feature.reader.ReaderIntent
import com.shuli.reader.feature.reader.ReaderSettingKey
import com.shuli.reader.feature.reader.ReaderSettingValue
import com.shuli.reader.feature.reader.ReaderUiState
import com.shuli.reader.feature.reader.settings.toChromePrefs
import com.shuli.reader.feature.reader.settings.toLayoutPrefs
import com.shuli.reader.feature.reader.settings.toOverlayPrefs
import com.shuli.reader.feature.reader.settings.toStylePrefs
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * ModalBottomSheet 适配层：把 SettingsPanelV5 的 sheetContent 嵌入 ModalBottomSheet，
 * 与现有 ReaderOverlayPanels 的 overlay 模式保持一致，无需重构 ReaderScreen。
 *
 * 内部把 [SettingsPanelV5] 的泛型 onSettingChanged 桥接为 [ReaderIntent.UpdateSetting]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPanelV5Modal(
    uiState: ReaderUiState,
    dispatch: (ReaderIntent) -> Unit,
    previewText: String? = null,
) {
    val readerColors = LocalReaderColorScheme.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { dispatch(ReaderIntent.ToggleQuickSettings) },
        sheetState = sheetState,
        containerColor = readerColors.surface,
        contentColor = readerColors.textPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = readerColors.textSecondary) },
        modifier = Modifier.testTag("SettingsPanelV5Modal"),
    ) {
        val prefs = uiState.readerPreferences
        val overlayPrefs = remember(prefs) { prefs.toOverlayPrefs() }
        val chromePrefs = remember(prefs) { prefs.toChromePrefs() }
        val stylePrefs = remember(prefs) { prefs.toStylePrefs() }
        val layoutPrefs = remember(prefs) { prefs.toLayoutPrefs() }
        var selectedTab by rememberSaveable { mutableIntStateOf(0) }

        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f)) {
            SettingsPanelV5SheetContent(
                prefs = prefs,
                previewText = previewText,
                onFontSizeChange = { v ->
                    dispatch(ReaderIntent.UpdateSetting(ReaderSettingKey.FONT_SIZE, ReaderSettingValue.Float(v)))
                },
                onThemeChange = { theme ->
                    dispatch(ReaderIntent.UpdateSetting(ReaderSettingKey.THEME, ReaderSettingValue.Theme(theme)))
                },
                onSettingChanged = { key, value ->
                    bridgeSettingChange(key, value)?.let { dispatch(it) }
                },
                overlayPrefs = overlayPrefs,
                chromePrefs = chromePrefs,
                stylePrefs = stylePrefs,
                layoutPrefs = layoutPrefs,
                selectedTab = selectedTab,
                onTabChange = { selectedTab = it },
            )
        }
    }
}

/**
 * 将泛型 (key: String, value: Any) 桥接到类型安全的 ReaderIntent.UpdateSetting。
 *
 * 未知 key 或不支持的 value 类型返回 null，调用方应忽略。
 */
private fun bridgeSettingChange(key: String, value: Any): ReaderIntent? {
    val intent = when (key) {
        "line_spacing" -> (value as? Float)?.let {
            ReaderIntent.UpdateSetting(ReaderSettingKey.LINE_SPACING, ReaderSettingValue.Float(it))
        }
        "paragraph_spacing" -> (value as? Float)?.let {
            ReaderIntent.UpdateSetting(ReaderSettingKey.PARAGRAPH_SPACING, ReaderSettingValue.Float(it))
        }
        "letter_spacing" -> (value as? Float)?.let {
            ReaderIntent.UpdateSetting(ReaderSettingKey.LETTER_SPACING, ReaderSettingValue.Float(it))
        }
        "margin_top" -> (value as? Float)?.let {
            ReaderIntent.UpdateSetting(ReaderSettingKey.MARGIN_TOP, ReaderSettingValue.Float(it))
        }
        "margin_bottom" -> (value as? Float)?.let {
            ReaderIntent.UpdateSetting(ReaderSettingKey.MARGIN_BOTTOM, ReaderSettingValue.Float(it))
        }
        "margin_left" -> (value as? Float)?.let {
            ReaderIntent.UpdateSetting(ReaderSettingKey.MARGIN_LEFT, ReaderSettingValue.Float(it))
        }
        "margin_right" -> (value as? Float)?.let {
            ReaderIntent.UpdateSetting(ReaderSettingKey.MARGIN_RIGHT, ReaderSettingValue.Float(it))
        }
        "header_footer_alpha" -> (value as? Float)?.let {
            ReaderIntent.UpdateSetting(ReaderSettingKey.HEADER_FOOTER_ALPHA, ReaderSettingValue.Float(it))
        }
        else -> null
    }
    return intent
}
