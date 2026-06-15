package com.shuli.reader.feature.reader.settings.panel
import com.shuli.reader.feature.reader.screen.ReaderSettingKey
import com.shuli.reader.feature.reader.screen.ReaderSettingValue

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.DualPageMode
import com.shuli.reader.core.data.OrientationLock
import com.shuli.reader.core.data.PageAnimSpeed
import com.shuli.reader.core.data.PageAnimType
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.reader.model.SlotContent
import com.shuli.reader.feature.reader.screen.ReaderIntent
import com.shuli.reader.feature.reader.screen.ReaderUiState
import com.shuli.reader.feature.reader.settings.SettingsScope
import com.shuli.reader.feature.reader.settings.GestureConfig
import com.shuli.reader.ui.theme.LocalReaderColorScheme
import com.shuli.reader.ui.theme.ReaderMaterialTheme
import kotlinx.coroutines.launch

/**
 * ModalBottomSheet 适配层：把 [ReaderSettingsSheetContent] 嵌入 ModalBottomSheet，
 * 与现有 ReaderOverlayPanels 的 overlay 模式保持一致。
 *
 * 内部把泛型 onSettingChanged 桥接为类型安全的 [ReaderIntent.UpdateSetting] / [ReaderIntent.SetPageAnimType]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsModal(
    uiState: ReaderUiState,
    dispatch: (ReaderIntent) -> Unit,
) {
    val readerColors = LocalReaderColorScheme.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = { dispatch(ReaderIntent.ToggleQuickSettings) },
        sheetState = sheetState,
        containerColor = readerColors.surface,
        contentColor = readerColors.textPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = readerColors.textSecondary) },
        modifier = Modifier.testTag("ReaderSettingsModal"),
    ) {
        val prefs = uiState.readerPreferences
        var selectedTab by rememberSaveable { mutableIntStateOf(0) }

        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            ReaderMaterialTheme(readerTheme = prefs.backgroundColor) {
                ReaderSettingsSheetContent(
                    prefs = prefs,
                    selectedTab = selectedTab,
                    onTabChange = { selectedTab = it },
                    onThemeChange = { theme ->
                        dispatch(ReaderIntent.UpdateSetting(ReaderSettingKey.THEME, ReaderSettingValue.Theme(theme)))
                    },
                    onCustomThemeConfirm = { bg, text, title, headerFooter ->
                        dispatch(
                            ReaderIntent.UpdateSetting(
                                ReaderSettingKey.CUSTOM_THEME_COLOR,
                                ReaderSettingValue.CustomThemeColor(
                                    backgroundColor = bg,
                                    textColor = text,
                                    titleColor = title,
                                    headerFooterColor = headerFooter,
                                ),
                            )
                        )
                    },
                    onSettingChanged = { key, value ->
                        bridgeSettingChange(key, value)?.let { dispatch(it) }
                    },
                    onContinuousSettingChanged = { key, value, finished ->
                        bridgeContinuousSettingChange(key, value, finished)?.let { dispatch(it) }
                    },
                    settingsScope = uiState.settingsScope,
                    hasBookOverrides = uiState.hasBookOverrides,
                    onScopeChange = { dispatch(ReaderIntent.SetSettingsScope(it)) },
                    onResetDefaults = {
                        if (uiState.settingsScope == SettingsScope.BOOK && uiState.hasBookOverrides) {
                            dispatch(ReaderIntent.ResetBookOverrides)
                        } else {
                            dispatch(ReaderIntent.ResetSettingsToDefault)
                        }
                    },
                    customFonts = uiState.customFonts,
                    onImportFont = { dispatch(ReaderIntent.ImportFont(it)) },
                    onDeleteFont = { dispatch(ReaderIntent.DeleteFont(it)) },
                    gestureConfig = prefs.gestureConfig,
                    onGestureChange = { config ->
                        dispatch(
                            ReaderIntent.UpdateSetting(
                                ReaderSettingKey.GESTURE_CONFIG,
                                ReaderSettingValue.GestureConfigValue(config),
                            )
                        )
                    },
                    onOpenGestureZoneEditor = {
                        scope.launch {
                            sheetState.hide()
                            dispatch(ReaderIntent.OpenGestureZoneEditor)
                        }
                    },
                )
            }
        }
    }
}

/**
 * 将泛型 (key, value, finished) 桥接到类型安全的 [ReaderIntent.UpdateContinuousSetting]。
 *
 * 复用 [bridgeSettingChange] 的 key→value 映射；结果若不是 UpdateSetting 则返回 null
 * （即仅对已知 key 有效）。
 */
private fun bridgeContinuousSettingChange(
    key: String,
    value: Any,
    finished: Boolean,
): ReaderIntent? {
    val wrapped = bridgeSettingChange(key, value) ?: return null
    return when (wrapped) {
        is ReaderIntent.UpdateSetting -> ReaderIntent.UpdateContinuousSetting(
            key = wrapped.key,
            value = wrapped.value,
            finished = finished,
        )
        else -> null
    }
}

/**
 * 将泛型 (key, value) 桥接到类型安全的 [ReaderIntent]。
 *
 * 未知 key 或不支持的 value 类型返回 null，调用方应忽略。
 */
private fun bridgeSettingChange(key: String, value: Any): ReaderIntent? {
    fun f(k: ReaderSettingKey) = (value as? Float)?.let {
        ReaderIntent.UpdateSetting(k, ReaderSettingValue.Float(it))
    }
    fun b(k: ReaderSettingKey) = (value as? Boolean)?.let {
        ReaderIntent.UpdateSetting(k, ReaderSettingValue.Bool(it))
    }
    fun i(k: ReaderSettingKey) = (value as? Int)?.let {
        ReaderIntent.UpdateSetting(k, ReaderSettingValue.Int(it))
    }
    fun s(k: ReaderSettingKey) = (value as? String)?.let {
        ReaderIntent.UpdateSetting(k, ReaderSettingValue.Str(it))
    }
    fun slot(k: ReaderSettingKey) = (value as? SlotContent)?.let {
        ReaderIntent.UpdateSetting(k, ReaderSettingValue.SlotContent(it))
    }
    return when (key) {
        // ── 基础排版 ──
        "font_size" -> f(ReaderSettingKey.FONT_SIZE)
        "line_spacing" -> f(ReaderSettingKey.LINE_SPACING)
        "paragraph_spacing" -> f(ReaderSettingKey.PARAGRAPH_SPACING)
        "indent" -> f(ReaderSettingKey.INDENT)
        "letter_spacing" -> f(ReaderSettingKey.LETTER_SPACING)
        "max_page_width" -> f(ReaderSettingKey.MAX_PAGE_WIDTH)
        // ── 边距 ──
        "margin_top" -> f(ReaderSettingKey.MARGIN_TOP)
        "margin_bottom" -> f(ReaderSettingKey.MARGIN_BOTTOM)
        "margin_left" -> f(ReaderSettingKey.MARGIN_LEFT)
        "margin_right" -> f(ReaderSettingKey.MARGIN_RIGHT)
        // ── 字体 ──
        "reading_font" -> s(ReaderSettingKey.READING_FONT)
        "font_weight" -> (value as? ReaderFontWeight)?.let {
            ReaderIntent.UpdateSetting(ReaderSettingKey.FONT_WEIGHT, ReaderSettingValue.FontWeight(it))
        }
        "text_align" -> (value as? ReaderTextAlign)?.let {
            ReaderIntent.UpdateSetting(ReaderSettingKey.TEXT_ALIGN, ReaderSettingValue.TextAlign(it))
        }
        // ── 高级排版 ──
        "chinese_convert" -> (value as? ChineseConvert)?.let {
            ReaderIntent.UpdateSetting(ReaderSettingKey.CHINESE_CONVERT, ReaderSettingValue.ChineseConvert(it))
        }
        "use_pangu_spacing" -> b(ReaderSettingKey.USE_PANGU_SPACING)
        "bottom_justify" -> b(ReaderSettingKey.BOTTOM_JUSTIFY)
        "remove_empty_lines" -> b(ReaderSettingKey.REMOVE_EMPTY_LINES)
        "paragraph_divider" -> b(ReaderSettingKey.PARAGRAPH_DIVIDER)
        "bionic_reading" -> b(ReaderSettingKey.BIONIC_READING)
        "clean_chapter_title" -> b(ReaderSettingKey.CLEAN_CHAPTER_TITLE)
        "epub_override_style" -> b(ReaderSettingKey.EPUB_OVERRIDE_STYLE)
        // ── 页眉页脚 ──
        "header_visibility" -> (value as? com.shuli.reader.core.reader.model.HeaderVisibility)?.let {
            ReaderIntent.UpdateSetting(ReaderSettingKey.HEADER_VISIBILITY, ReaderSettingValue.HeaderVisibility(it))
        }
        "footer_visibility" -> (value as? com.shuli.reader.core.reader.model.HeaderVisibility)?.let {
            ReaderIntent.UpdateSetting(ReaderSettingKey.FOOTER_VISIBILITY, ReaderSettingValue.HeaderVisibility(it))
        }
        "header_left" -> slot(ReaderSettingKey.HEADER_LEFT)
        "header_center" -> slot(ReaderSettingKey.HEADER_CENTER)
        "header_right" -> slot(ReaderSettingKey.HEADER_RIGHT)
        "footer_left" -> slot(ReaderSettingKey.FOOTER_LEFT)
        "footer_center" -> slot(ReaderSettingKey.FOOTER_CENTER)
        "footer_right" -> slot(ReaderSettingKey.FOOTER_RIGHT)
        "header_margin_top" -> f(ReaderSettingKey.HEADER_MARGIN_TOP)
        "footer_margin_bottom" -> f(ReaderSettingKey.FOOTER_MARGIN_BOTTOM)
        "header_footer_alpha" -> f(ReaderSettingKey.HEADER_FOOTER_ALPHA)
        "show_header_line" -> b(ReaderSettingKey.SHOW_HEADER_LINE)
        "show_footer_line" -> b(ReaderSettingKey.SHOW_FOOTER_LINE)
        // ── 色温 ──
        "color_temperature" -> f(ReaderSettingKey.COLOR_TEMPERATURE)
        // ── 显示模式 ──
        "dual_page_mode" -> (value as? DualPageMode)?.let {
            ReaderIntent.UpdateSetting(ReaderSettingKey.DUAL_PAGE_MODE, ReaderSettingValue.DualPageMode(it))
        }
        "background_texture" -> s(ReaderSettingKey.BACKGROUND_TEXTURE)
        "page_anim_speed" -> (value as? PageAnimSpeed)?.let {
            ReaderIntent.UpdateSetting(ReaderSettingKey.PAGE_ANIM_SPEED, ReaderSettingValue.PageAnimSpeed(it))
        }
        "page_anim_type" -> (value as? PageAnimType)?.let { ReaderIntent.SetPageAnimType(it) }
        // ── 翻页 / 触控 ──
        "volume_key_turn_page" -> b(ReaderSettingKey.VOLUME_KEY_TURN_PAGE)
        "edge_turn_page" -> b(ReaderSettingKey.EDGE_TURN_PAGE)
        "edge_width_percent" -> f(ReaderSettingKey.EDGE_WIDTH_PERCENT)
        "auto_page_turn" -> b(ReaderSettingKey.AUTO_PAGE_TURN)
        "auto_page_turn_interval" -> f(ReaderSettingKey.AUTO_PAGE_TURN_INTERVAL)
        "haptic_feedback" -> b(ReaderSettingKey.HAPTIC_FEEDBACK)
        "left_zone_ratio" -> f(ReaderSettingKey.LEFT_ZONE_RATIO)
        "gesture_config" -> (value as? GestureConfig)?.let {
            ReaderIntent.UpdateSetting(ReaderSettingKey.GESTURE_CONFIG, ReaderSettingValue.GestureConfigValue(it))
        }
        // ── 护眼 / 通用 ──
        "eye_care_reminder_interval" -> i(ReaderSettingKey.EYE_CARE_REMINDER_INTERVAL)
        "immersive_mode" -> b(ReaderSettingKey.IMMERSIVE_MODE)
        "keep_screen_on" -> b(ReaderSettingKey.KEEP_SCREEN_ON)
        "orientation_lock" -> (value as? OrientationLock)?.let {
            ReaderIntent.UpdateSetting(ReaderSettingKey.ORIENTATION_LOCK, ReaderSettingValue.OrientationLock(it))
        }
        else -> null
    }
}
