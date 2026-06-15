package com.shuli.reader.feature.reader.settings.panel.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.shuli.reader.core.data.OrientationLock
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.feature.reader.settings.panel.SelectRow
import com.shuli.reader.feature.reader.settings.panel.SettingRow
import com.shuli.reader.feature.reader.settings.panel.SettingsCard
import com.shuli.reader.feature.reader.settings.panel.SwitchRow
import com.shuli.reader.feature.reader.settings.panel.controls.InkStepperSlider
import com.shuli.reader.feature.reader.settings.panel.controls.label
import com.shuli.reader.feature.reader.settings.GestureConfig
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * Tab 3「行为交互」内容组装。
 *
 * 卡片：翻页 / 触控区域 / 护眼 / 通用。
 */
@Composable
fun BehaviorTab(
    prefs: ReaderPreferences,
    onSettingChanged: (String, Any) -> Unit,
    gestureConfig: GestureConfig,
    onGestureChange: (GestureConfig) -> Unit,
    onOpenGestureZoneEditor: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // ── 翻页 ──
        SettingsCard(title = "翻页") {
            SwitchRow(
                label = "音量键翻页",
                checked = prefs.volumeKeyTurnPage,
                onCheckedChange = { onSettingChanged("volume_key_turn_page", it) },
            )
            SwitchRow(
                label = "边缘翻页",
                sublabel = "触摸屏幕边缘区域翻页",
                checked = prefs.edgeTurnPage,
                onCheckedChange = { onSettingChanged("edge_turn_page", it) },
                topDivider = true,
            )
            if (prefs.edgeTurnPage) {
                InkStepperSlider(
                    value = prefs.edgeWidthPercent,
                    onValueChange = { onSettingChanged("edge_width_percent", it) },
                    valueRange = 0.1f..0.5f,
                    step = 0.05f,
                    label = "边缘宽度",
                    formatValue = { "%.0f%%".format(it * 100) },
                    testTagPrefix = "Slider_EdgeWidth",
                )
            }
            SwitchRow(
                label = "自动翻页",
                checked = prefs.autoPageTurn,
                onCheckedChange = { onSettingChanged("auto_page_turn", it) },
                topDivider = true,
            )
            if (prefs.autoPageTurn) {
                InkStepperSlider(
                    value = prefs.autoPageTurnInterval,
                    onValueChange = { onSettingChanged("auto_page_turn_interval", it) },
                    valueRange = 5f..60f,
                    step = 5f,
                    label = "翻页间隔",
                    formatValue = { "%.0fs".format(it) },
                    testTagPrefix = "Slider_AutoTurnInterval",
                )
            }
        }

        // ── 触控区域 ──
        SettingsCard(title = "触控区域") {
            TouchZoneEditorEntry(
                config = gestureConfig,
                onClick = onOpenGestureZoneEditor,
            )
            SwitchRow(
                label = "振动反馈",
                checked = prefs.hapticFeedback,
                onCheckedChange = { onSettingChanged("haptic_feedback", it) },
                topDivider = true,
            )
            InkStepperSlider(
                value = prefs.leftZoneRatio,
                onValueChange = { onSettingChanged("left_zone_ratio", it) },
                valueRange = 0.2f..0.5f,
                step = 0.05f,
                label = "左侧热区比例",
                formatValue = { "%.0f%%".format(it * 100) },
                testTagPrefix = "Slider_LeftZone",
            )
        }

        // ── 护眼 ──
        SettingsCard(title = "护眼") {
            SelectRow(
                label = "护眼提醒",
                sublabel = "基于翻页活动计时",
                options = listOf(
                    0 to "关闭",
                    15 to "15 分钟",
                    30 to "30 分钟",
                    45 to "45 分钟",
                    60 to "60 分钟",
                ),
                selected = prefs.eyeCareReminderInterval,
                onSelect = { onSettingChanged("eye_care_reminder_interval", it) },
            )
        }

        // ── 通用 ──
        SettingsCard(title = "通用") {
            SwitchRow(
                label = "沉浸模式",
                sublabel = "隐藏状态栏和导航栏",
                checked = prefs.immersiveMode,
                onCheckedChange = { onSettingChanged("immersive_mode", it) },
            )
            SwitchRow(
                label = "保持亮屏",
                checked = prefs.keepScreenOn,
                onCheckedChange = { onSettingChanged("keep_screen_on", it) },
                topDivider = true,
            )
            SelectRow(
                label = "屏幕方向",
                options = listOf(
                    OrientationLock.SYSTEM to "跟随系统",
                    OrientationLock.PORTRAIT to "竖屏锁定",
                    OrientationLock.LANDSCAPE to "横屏锁定",
                ),
                selected = prefs.orientationLock,
                onSelect = { onSettingChanged("orientation_lock", it) },
                topDivider = true,
            )
        }
    }
}

@Composable
private fun TouchZoneEditorEntry(
    config: GestureConfig,
    onClick: () -> Unit,
) {
    val colors = LocalReaderColorScheme.current
    SettingRow(
        label = "点击区域设置",
        sublabel = "当前中间区域：${config.middleCenter.label()}",
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("OpenGestureZoneEditor"),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = "进入",
            tint = colors.textTertiary,
        )
    }
}
