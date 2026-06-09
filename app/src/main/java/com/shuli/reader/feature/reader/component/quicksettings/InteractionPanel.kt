package com.shuli.reader.feature.reader.component.quicksettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.shuli.reader.core.data.PageAnimType
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.core.tts.TtsState
import com.shuli.reader.feature.reader.component.ReaderFormPickerRow
import com.shuli.reader.feature.reader.component.ReaderSwitchRow
import com.shuli.reader.feature.reader.component.ReaderValueSlider
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * Tab 4: 交互面板 — 翻页模式、行为开关、TTS 控制
 *
 * 按 §21.2 规范实现：
 * - 翻页模式
 * - 翻页方向（水平/垂直）
 * - 音量键翻页
 * - 保持亮屏
 * - 沉浸模式
 * - 边缘翻页
 * - TTS 简要控制
 */
@Composable
internal fun InteractionPanel(
    prefs: ReaderPreferences,
    ttsState: TtsState,
    onBrightnessChange: (Float) -> Unit = {},
    onPageAnimTypeChange: (PageAnimType) -> Unit,
    onVolumeKeyTurnPageChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onImmersiveModeChange: (Boolean) -> Unit,
    onEdgeTurnPageChange: (Boolean) -> Unit,
    onEdgeWidthPercentChange: (Float) -> Unit,
    onLeftZoneRatioChange: (Float) -> Unit = {},
    onAutoPageTurnChange: (Boolean) -> Unit = {},
    onAutoPageTurnIntervalChange: (Float) -> Unit = {},
    onTtsStart: () -> Unit,
    onTtsPause: () -> Unit,
    onTtsStop: () -> Unit,
    onTtsSpeedChange: (Float) -> Unit,
    onTtsPitchChange: (Float) -> Unit,
) {
    val readerColors = LocalReaderColorScheme.current
    val strings = LocalAppStrings.current

    // ── 亮度（顶部，可发现性优先） ──
    ReaderValueSlider(
        label = strings.reader.brightness,
        value = if (prefs.brightness < 0f) 0.5f else prefs.brightness,
        valueRange = 0.01f..1f,
        steps = 98,
        format = { "%.0f%%".format(it * 100) },
        onValueChange = onBrightnessChange,
    )

    // ── 翻页模式 ──
    val animOptions = remember {
        listOf(
            PageAnimType.SIMULATION to "仿真",
            PageAnimType.COVER to "覆盖",
            PageAnimType.HORIZONTAL to "平移",
            PageAnimType.SCROLL to "滚动",
            PageAnimType.NONE to "无动画",
        )
    }
    ReaderFormPickerRow(
        label = strings.reader.defaultPageAnim,
        options = animOptions,
        selected = prefs.pageAnimType,
        onSelect = onPageAnimTypeChange,
    )

    // ── 行为开关 ──
    ReaderSwitchRow(
        label = strings.reader.volumeKeyLabel,
        description = strings.reader.volumeKeyDesc,
        checked = prefs.volumeKeyTurnPage,
        onCheckedChange = onVolumeKeyTurnPageChange,
    )
    ReaderSwitchRow(
        label = strings.reader.keepScreenOnLabel,
        description = strings.reader.keepScreenOnDesc,
        checked = prefs.keepScreenOn,
        onCheckedChange = onKeepScreenOnChange,
    )
    ReaderSwitchRow(
        label = strings.reader.immersiveModeLabel,
        description = strings.reader.immersiveModeDesc,
        checked = prefs.immersiveMode,
        onCheckedChange = onImmersiveModeChange,
    )

    // ── 边缘翻页 ──
    ReaderSwitchRow(
        label = strings.reader.edgeTurnPageLabel,
        description = strings.reader.edgeTurnPageDesc,
        checked = prefs.edgeTurnPage,
        onCheckedChange = onEdgeTurnPageChange,
    )
    if (prefs.edgeTurnPage) {
        ReaderValueSlider(
            label = strings.reader.edgeWidthLabel,
            value = prefs.edgeWidthPercent,
            valueRange = 0.1f..0.5f,
            steps = 3,
            format = { "%.0f%%".format(it * 100) },
            onValueChange = onEdgeWidthPercentChange,
        )
    }

    // ── P0: 触控热区 ──
    ReaderValueSlider(
        label = strings.reader.leftZoneRatioLabel,
        value = prefs.leftZoneRatio,
        valueRange = 0.2f..0.5f,
        steps = 5,
        format = { "%.0f%%".format(it * 100) },
        onValueChange = onLeftZoneRatioChange,
    )

    // ── P2: 自动翻页 ──
    ReaderSwitchRow(
        label = strings.reader.autoPageTurnLabel,
        description = strings.reader.autoPageTurnDesc,
        checked = prefs.autoPageTurn,
        onCheckedChange = onAutoPageTurnChange,
    )
    if (prefs.autoPageTurn) {
        ReaderValueSlider(
            label = strings.reader.autoPageTurnIntervalLabel,
            value = prefs.autoPageTurnInterval,
            valueRange = 5f..60f,
            steps = 10,
            format = { "%.0fs".format(it) },
            onValueChange = onAutoPageTurnIntervalChange,
        )
    }

    // ── TTS 简要控制 ──
    var ttsExpanded by remember { mutableStateOf(false) }
    ExpandableSection(
        title = "TTS",
        expanded = ttsExpanded,
        onToggle = { ttsExpanded = !ttsExpanded },
    ) {
        TtsControlSection(
            ttsState = ttsState,
            ttsSpeed = prefs.ttsSpeed,
            ttsPitch = prefs.ttsPitch,
            onTtsStart = onTtsStart,
            onTtsPause = onTtsPause,
            onTtsStop = onTtsStop,
            onTtsSpeedChange = onTtsSpeedChange,
            onTtsPitchChange = onTtsPitchChange,
        )
    }
}

/**
 * TTS 控制区域
 */
@Composable
private fun TtsControlSection(
    ttsState: TtsState,
    ttsSpeed: Float,
    ttsPitch: Float,
    onTtsStart: () -> Unit,
    onTtsPause: () -> Unit,
    onTtsStop: () -> Unit,
    onTtsSpeedChange: (Float) -> Unit,
    onTtsPitchChange: (Float) -> Unit,
) {
    val strings = LocalAppStrings.current
    val isPlaying = ttsState == TtsState.PLAYING
    val isPaused = ttsState == TtsState.PAUSED

    Column {
        // 播放/暂停/停止按钮行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextButton(
                onClick = if (isPlaying) onTtsPause else onTtsStart,
            ) {
                Text(
                    text = if (isPlaying) "暂停" else if (isPaused) "继续" else "播放",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (isPlaying || isPaused) {
                TextButton(onClick = onTtsStop) {
                    Text(
                        text = "停止",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // 语速
        ReaderValueSlider(
            label = "语速",
            value = ttsSpeed,
            valueRange = 0.5f..2.0f,
            steps = 5,
            format = { "%.1fx".format(it) },
            onValueChange = onTtsSpeedChange,
        )

        // 音调
        ReaderValueSlider(
            label = "音调",
            value = ttsPitch,
            valueRange = 0.5f..2.0f,
            steps = 5,
            format = { "%.1fx".format(it) },
            onValueChange = onTtsPitchChange,
        )
    }
}
