package com.shuli.reader.feature.reader.settings.panel.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.StayCurrentLandscape
import androidx.compose.material.icons.outlined.StayCurrentPortrait
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.data.OrientationLock
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.reader.settings.panel.SegmentedRow
import com.shuli.reader.feature.reader.settings.panel.SelectRow
import com.shuli.reader.feature.reader.settings.panel.SettingsCard
import com.shuli.reader.feature.reader.settings.panel.SwitchRow
import com.shuli.reader.feature.reader.settings.panel.controls.InkStepperSlider
import com.shuli.reader.feature.reader.settings.panel.controls.MiniGestureZonePreview
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
    val strings = LocalAppStrings.current.reader
    Column(modifier = modifier.fillMaxWidth()) {
        // ── 翻页 ──
        SettingsCard(title = strings.pageTurnCard) {
            SwitchRow(
                label = strings.volumeKeyTurnPageLabel,
                checked = prefs.volumeKeyTurnPage,
                onCheckedChange = { onSettingChanged("volume_key_turn_page", it) },
            )
            SwitchRow(
                label = strings.edgeTurnPageLabel,
                sublabel = strings.edgeTurnPageShortDesc,
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
                    label = strings.edgeWidthLabel,
                    formatValue = { "%.0f%%".format(it * 100) },
                    testTagPrefix = "Slider_EdgeWidth",
                )
            }
            SwitchRow(
                label = strings.autoPageTurnLabel,
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
                    label = strings.autoPageTurnIntervalLabel,
                    formatValue = { "%.0fs".format(it) },
                    testTagPrefix = "Slider_AutoTurnInterval",
                )
            }
        }

        // ── 触控区域 ──
        SettingsCard(title = strings.touchZoneCard) {
            TouchZoneEditorEntry(
                config = gestureConfig,
                onClick = onOpenGestureZoneEditor,
            )
            SwitchRow(
                label = strings.hapticFeedbackLabel,
                checked = prefs.hapticFeedback,
                onCheckedChange = { onSettingChanged("haptic_feedback", it) },
                topDivider = true,
            )
            InkStepperSlider(
                value = prefs.leftZoneRatio,
                onValueChange = { onSettingChanged("left_zone_ratio", it) },
                valueRange = 0.2f..0.5f,
                step = 0.05f,
                label = strings.leftZoneRatioLabel,
                formatValue = { "%.0f%%".format(it * 100) },
                testTagPrefix = "Slider_LeftZone",
            )
        }

        // ── 护眼 ──
        SettingsCard(title = strings.eyeCareCard) {
            SelectRow(
                label = strings.eyeCareReminderLabel,
                sublabel = strings.eyeCareReminderDesc,
                options = listOf(
                    0 to strings.offLabel,
                    15 to strings.minutes15,
                    30 to strings.minutes30,
                    45 to strings.minutes45,
                    60 to strings.minutes60,
                ),
                selected = prefs.eyeCareReminderInterval,
                onSelect = { onSettingChanged("eye_care_reminder_interval", it) },
            )
        }

        // ── 通用 ──
        SettingsCard(title = strings.generalCard) {
            SwitchRow(
                label = strings.immersiveModeLabel,
                sublabel = strings.immersiveModeDesc,
                checked = prefs.immersiveMode,
                onCheckedChange = { onSettingChanged("immersive_mode", it) },
            )
            SwitchRow(
                label = strings.keepScreenOnShortLabel,
                checked = prefs.keepScreenOn,
                onCheckedChange = { onSettingChanged("keep_screen_on", it) },
                topDivider = true,
            )
            SegmentedRow(
                label = strings.orientationLockLabel,
                options = listOf(
                    OrientationLock.SYSTEM to strings.brightnessFollowSystem,
                    OrientationLock.PORTRAIT to strings.portraitLockLabel,
                    OrientationLock.LANDSCAPE to strings.landscapeLockLabel,
                ),
                selected = prefs.orientationLock,
                onSelect = { onSettingChanged("orientation_lock", it) },
                topDivider = true,
                icons = listOf(
                    Icons.Outlined.ScreenRotation,
                    Icons.Outlined.StayCurrentPortrait,
                    Icons.Outlined.StayCurrentLandscape,
                ),
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
    val strings = LocalAppStrings.current.reader
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
            .testTag("OpenGestureZoneEditor"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.gestureZoneSettingsLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = strings.enterLabel,
                tint = colors.textTertiary,
            )
        }
        // 整行九宫格预览：让区域与动作关系成为主信息，而不是行尾小附件。
        MiniGestureZonePreview(config = config)
    }
}
