package com.shuli.reader.feature.reader.settings.panel.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.data.PageAnimSpeed
import com.shuli.reader.core.data.PageAnimType
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.reader.settings.GestureConfig
import com.shuli.reader.feature.reader.settings.panel.SegmentedRow
import com.shuli.reader.feature.reader.settings.panel.SettingsCard
import com.shuli.reader.feature.reader.settings.panel.SwitchRow
import com.shuli.reader.feature.reader.settings.panel.controls.InkStepperSlider
import com.shuli.reader.feature.reader.settings.panel.controls.MiniGestureZonePreview
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * Tab「翻页」内容组装。
 *
 * 卡片结构：翻页方式 / 触控区域 / 翻页动效。
 */
@Composable
fun PageTurn(
    prefs: ReaderPreferences,
    onSettingChanged: (String, Any) -> Unit,
    modifier: Modifier = Modifier,
    gestureConfig: GestureConfig = GestureConfig(),
    onGestureChange: (GestureConfig) -> Unit = {},
    onOpenGestureZoneEditor: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        PageTurnMethodSettingsCard(
            prefs = prefs,
            onSettingChanged = onSettingChanged,
        )
        TouchZoneSettingsCard(
            prefs = prefs,
            onSettingChanged = onSettingChanged,
            gestureConfig = gestureConfig,
            onOpenGestureZoneEditor = onOpenGestureZoneEditor,
        )
        PageTurnAnimationSettingsCard(
            prefs = prefs,
            onSettingChanged = onSettingChanged,
        )
    }
}

@Composable
fun PageTurnMethodSettingsCard(
    prefs: ReaderPreferences,
    onSettingChanged: (String, Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current.reader

    SettingsCard(title = strings.pageTurnCard, modifier = modifier) {
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
}

@Composable
fun TouchZoneSettingsCard(
    prefs: ReaderPreferences,
    onSettingChanged: (String, Any) -> Unit,
    modifier: Modifier = Modifier,
    gestureConfig: GestureConfig = GestureConfig(),
    onOpenGestureZoneEditor: () -> Unit = {},
) {
    val strings = LocalAppStrings.current.reader

    SettingsCard(title = strings.touchZoneCard, modifier = modifier) {
        TouchZoneEditorEntry(
            config = gestureConfig,
            onClick = onOpenGestureZoneEditor,
        )
        SwitchRow(
            label = strings.hapticFeedbackLabel,
            checked = prefs.hapticFeedback,
            onCheckedChange = { onSettingChanged("haptic_feedback", it) },
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
}

@Composable
fun PageTurnAnimationSettingsCard(
    prefs: ReaderPreferences,
    onSettingChanged: (String, Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current.reader

    SettingsCard(title = "翻页动效", modifier = modifier) {
        SegmentedRow(
            label = strings.pageAnimTypeLabel,
            options = listOf(
                PageAnimType.NONE to strings.pageAnimNone,
                PageAnimType.COVER to strings.pageAnimOverlay,
                PageAnimType.HORIZONTAL to strings.pageAnimTypeHorizontal,
                PageAnimType.SIMULATION to strings.pageAnimSimulation,
                PageAnimType.VERTICAL_SLIDE to strings.pageAnimTypeVerticalSlide,
                PageAnimType.SCROLL to strings.pageAnimTypeScroll,
            ),
            selected = prefs.pageAnimType,
            onSelect = { onSettingChanged("page_anim_type", it) },
        )
        SegmentedRow(
            label = strings.pageAnimSpeedLabel,
            options = listOf(
                PageAnimSpeed.SLOW to strings.pageAnimSpeedSlow,
                PageAnimSpeed.NORMAL to strings.pageAnimSpeedNormal,
                PageAnimSpeed.FAST to strings.pageAnimSpeedFast,
            ),
            selected = prefs.pageAnimSpeed,
            onSelect = { onSettingChanged("page_anim_speed", it) },
        )
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
        MiniGestureZonePreview(config = config)
    }
}
