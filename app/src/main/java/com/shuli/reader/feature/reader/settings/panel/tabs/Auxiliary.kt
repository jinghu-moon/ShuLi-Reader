package com.shuli.reader.feature.reader.settings.panel.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.shuli.reader.core.data.DualPageMode
import com.shuli.reader.core.data.OrientationLock
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.reader.settings.panel.SegmentedRow
import com.shuli.reader.feature.reader.settings.panel.SelectRow
import com.shuli.reader.feature.reader.settings.panel.SettingsCard
import com.shuli.reader.feature.reader.settings.panel.SwitchRow
import com.shuli.reader.feature.reader.settings.panel.controls.InkStepperSlider
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * Tab「辅助」内容组装。
 *
 * 卡片结构：护眼 / 屏幕状态 / 阅读形态。
 */
@Composable
fun Auxiliary(
    prefs: ReaderPreferences,
    onSettingChanged: (String, Any) -> Unit,
    onContinuousSettingChanged: (String, Any, Boolean) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        EyeCareSettingsCard(
            prefs = prefs,
            onSettingChanged = onSettingChanged,
            onContinuousSettingChanged = onContinuousSettingChanged,
        )
        ScreenStateSettingsCard(
            prefs = prefs,
            onSettingChanged = onSettingChanged,
        )
        ReadingFormSettingsCard(
            prefs = prefs,
            onSettingChanged = onSettingChanged,
        )
    }
}

@Composable
fun EyeCareSettingsCard(
    prefs: ReaderPreferences,
    onSettingChanged: (String, Any) -> Unit,
    onContinuousSettingChanged: (String, Any, Boolean) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    val colors = LocalReaderColorScheme.current
    val strings = LocalAppStrings.current.reader

    SettingsCard(title = strings.eyeCareCard, modifier = modifier) {
        InkStepperSlider(
            value = prefs.colorTemperature,
            onValueChange = { onContinuousSettingChanged("color_temperature", it, false) },
            onValueChangeFinished = { finalValue ->
                onContinuousSettingChanged("color_temperature", finalValue, true)
            },
            valueRange = 2000f..6500f,
            step = 100f,
            label = strings.colorTemperatureLabel,
            formatValue = { "%.0fK".format(it) },
            fillBrush = Brush.horizontalGradient(listOf(Color(0xFFFF8C00), colors.accent)),
            testTagPrefix = "Slider_ColorTemp",
        )
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
}

@Composable
fun ScreenStateSettingsCard(
    prefs: ReaderPreferences,
    onSettingChanged: (String, Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current.reader

    SettingsCard(title = "屏幕状态", modifier = modifier) {
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
        )
    }
}

@Composable
fun ReadingFormSettingsCard(
    prefs: ReaderPreferences,
    onSettingChanged: (String, Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current.reader

    SettingsCard(title = "阅读形态", modifier = modifier) {
        SegmentedRow(
            label = strings.dualPageModeLabel,
            options = listOf(
                DualPageMode.SINGLE to strings.singlePageLabel,
                DualPageMode.DUAL to strings.dualPageLabel,
                DualPageMode.AUTO to strings.autoLabel,
            ),
            selected = prefs.dualPageMode,
            onSelect = { onSettingChanged("dual_page_mode", it) },
        )
        SwitchRow(
            label = "竖排文字",
            checked = prefs.verticalText,
            onCheckedChange = { onSettingChanged("vertical_text", it) },
        )
        SelectRow(
            label = strings.backgroundTextureLabel,
            options = listOf(
                "" to strings.solidColorLabel,
                "kraft" to "Kraft",
                "linen" to strings.linenTextureLabel,
                "grid" to strings.gridTextureLabel,
            ),
            selected = prefs.backgroundTexture ?: "",
            onSelect = { onSettingChanged("background_texture", it) },
        )
    }
}
