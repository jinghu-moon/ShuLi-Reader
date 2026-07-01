package com.shuli.reader.feature.reader.settings.panel.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.shuli.reader.core.data.ProgressStyle
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.reader.model.BoxInsetsDp
import com.shuli.reader.core.reader.model.HeaderVisibility
import com.shuli.reader.core.reader.model.TitleAlign
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.reader.settings.panel.SelectRow
import com.shuli.reader.feature.reader.settings.panel.SegmentedRow
import com.shuli.reader.feature.reader.settings.panel.SettingsCard
import com.shuli.reader.feature.reader.settings.panel.SlotMatrix
import com.shuli.reader.feature.reader.settings.panel.SwitchRow
import com.shuli.reader.feature.reader.settings.panel.controls.BoxMarginSection
import com.shuli.reader.feature.reader.settings.panel.controls.InkStepperSlider
import com.shuli.reader.feature.reader.settings.panel.controls.MarginPreset
import com.shuli.reader.feature.reader.settings.panel.controls.MarginPresetRow
import com.shuli.reader.feature.reader.settings.panel.dashboard.detectMarginPreset
import com.shuli.reader.feature.reader.settings.panel.dashboard.readerMarginPresets
import com.shuli.reader.ui.theme.LocalReaderColorScheme
import kotlin.math.roundToInt

/**
 * Tab「布局」内容组装。
 *
 * 卡片结构：正文区域 / 标题 / 页眉页脚 / 边距方案。
 */
@Composable
fun Layout(
    prefs: ReaderPreferences,
    onSettingChanged: (String, Any) -> Unit,
    onContinuousSettingChanged: (String, Any, Boolean) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        BodyAreaSettingsCard(
            prefs = prefs,
            onSettingChanged = onSettingChanged,
        )
        TitleStyleSettingsCard(
            prefs = prefs,
            onSettingChanged = onSettingChanged,
        )
        HeaderFooterSettingsCard(
            prefs = prefs,
            onSettingChanged = onSettingChanged,
        )
        MarginPresetSettingsCard(
            prefs = prefs,
            onSettingChanged = onSettingChanged,
        )
    }
}

@Composable
fun BodyAreaSettingsCard(
    prefs: ReaderPreferences,
    onSettingChanged: (String, Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current.reader
    SettingsCard(title = strings.bodyBoxLabel, modifier = modifier) {
        BoxMarginSection(
            title = strings.bodyBoxLabel,
            insets = prefs.bodyBox,
            defaultInsets = BoxInsetsDp(48f, 48f, 24f, 24f),
            onInsetsChange = { onSettingChanged("body_box", it) },
            topLabel = strings.boxMarginTop,
            bottomLabel = strings.boxMarginBottom,
            leftLabel = strings.boxMarginLeft,
            rightLabel = strings.boxMarginRight,
            collapsible = true,
            initiallyExpanded = true,
        )
    }
}

@Composable
fun TitleStyleSettingsCard(
    prefs: ReaderPreferences,
    onSettingChanged: (String, Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current.reader

    SettingsCard(title = strings.titleStyleLabel, modifier = modifier) {
        InkStepperSlider(
            value = prefs.titleFontSize,
            onValueChange = { onSettingChanged("title_font_size", it) },
            valueRange = 12f..48f,
            step = 1f,
            label = strings.titleSizeLabel,
            formatValue = { "${it.toInt()}sp" },
            testTagPrefix = "Slider_TitleFontSize",
        )
        SegmentedRow(
            label = strings.titleAlignLabel,
            options = listOf(
                TitleAlign.LEFT to strings.titleAlignLeft,
                TitleAlign.CENTER to strings.titleAlignCenter,
                TitleAlign.RIGHT to strings.titleAlignRight,
            ),
            selected = prefs.titleStyle.align,
            onSelect = { onSettingChanged("title_align", it) },
        )
        InkStepperSlider(
            value = prefs.titleStyle.sizeOffsetSp.toFloat(),
            onValueChange = { onSettingChanged("title_size_offset", it.roundToInt()) },
            valueRange = 0f..12f,
            step = 1f,
            label = strings.titleSizeOffset,
            defaultValue = 4f,
            formatValue = { "+${it.toInt()}sp" },
            testTagPrefix = "Slider_TitleSizeOffset",
        )
        InkStepperSlider(
            value = prefs.titleStyle.marginTopDp,
            onValueChange = { onSettingChanged("title_margin_top", it) },
            valueRange = 0f..96f,
            step = 4f,
            label = strings.titleMarginTop,
            defaultValue = 9f,
            formatValue = { "${it.toInt()}" },
            testTagPrefix = "Slider_TitleMarginTop",
        )
        InkStepperSlider(
            value = prefs.titleStyle.marginBottomDp,
            onValueChange = { onSettingChanged("title_margin_bottom", it) },
            valueRange = 0f..96f,
            step = 4f,
            label = strings.titleMarginBottom,
            defaultValue = 10f,
            formatValue = { "${it.toInt()}" },
            testTagPrefix = "Slider_TitleMarginBottom",
        )
        BoxMarginSection(
            title = strings.titleBoxLabel,
            insets = prefs.titleBox,
            defaultInsets = BoxInsetsDp(9f, 10f, 24f, 24f),
            onInsetsChange = { onSettingChanged("title_box", it) },
            topLabel = strings.boxMarginTop,
            bottomLabel = strings.boxMarginBottom,
            leftLabel = strings.boxMarginLeft,
            rightLabel = strings.boxMarginRight,
            collapsible = true,
            initiallyExpanded = false,
        )
    }
}

@Composable
fun HeaderFooterSettingsCard(
    prefs: ReaderPreferences,
    onSettingChanged: (String, Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current.reader

    SettingsCard(title = strings.headerFooterCard, modifier = modifier) {
        SelectRow(
            label = strings.headerLabel,
            options = headerVisibilityOptions(strings),
            selected = prefs.header.visibility,
            onSelect = { onSettingChanged("header_visibility", it) },
        )
        SelectRow(
            label = strings.footerLabel,
            options = headerVisibilityOptions(strings),
            selected = prefs.footer.visibility,
            onSelect = { onSettingChanged("footer_visibility", it) },
        )
        SlotMatrix(
            headerSlots = Triple(prefs.header.left, prefs.header.center, prefs.header.right),
            footerSlots = Triple(prefs.footer.left, prefs.footer.center, prefs.footer.right),
            onHeaderSlotChange = { index, content ->
                onSettingChanged(headerSlotKey(index), content)
            },
            onFooterSlotChange = { index, content ->
                onSettingChanged(footerSlotKey(index), content)
            },
        )
        SwitchRow(
            label = strings.progressBarLabel,
            checked = prefs.showProgress,
            onCheckedChange = { onSettingChanged("show_progress", it) },
        )
        SelectRow(
            label = strings.progressStyleLabel,
            options = progressStyleOptions(strings),
            selected = prefs.progressStyle,
            onSelect = { onSettingChanged("progress_style", it) },
        )
        InkStepperSlider(
            value = prefs.headerFontSizeRatio,
            onValueChange = { onSettingChanged("header_font_size_ratio", it) },
            valueRange = 0.5f..1.5f,
            step = 0.05f,
            label = strings.headerFontSizeLabel,
            sublabel = "${(prefs.fontSize * prefs.headerFontSizeRatio).toInt()}sp",
            formatValue = { "%.0f%%".format(it * 100) },
            testTagPrefix = "Slider_HeaderFontRatio",
        )
        InkStepperSlider(
            value = prefs.footerFontSizeRatio,
            onValueChange = { onSettingChanged("footer_font_size_ratio", it) },
            valueRange = 0.5f..1.5f,
            step = 0.05f,
            label = strings.footerFontSizeLabel,
            sublabel = "${(prefs.fontSize * prefs.footerFontSizeRatio).toInt()}sp",
            formatValue = { "%.0f%%".format(it * 100) },
            testTagPrefix = "Slider_FooterFontRatio",
        )
        InkStepperSlider(
            value = prefs.headerFooterAlpha,
            onValueChange = { onSettingChanged("header_footer_alpha", it) },
            valueRange = 0.1f..1.0f,
            step = 0.1f,
            label = strings.opacityLabel,
            formatValue = { "%.0f%%".format(it * 100) },
            testTagPrefix = "Slider_HeaderFooterAlpha",
        )
        SwitchRow(
            label = strings.headerSeparatorLineLabel,
            checked = prefs.showHeaderLine,
            onCheckedChange = { onSettingChanged("show_header_line", it) },
        )
        SwitchRow(
            label = strings.footerSeparatorLineLabel,
            checked = prefs.showFooterLine,
            onCheckedChange = { onSettingChanged("show_footer_line", it) },
        )
        BoxMarginSection(
            title = strings.headerBoxLabel,
            insets = prefs.headerBox,
            defaultInsets = BoxInsetsDp(16f, 0f, 24f, 24f),
            onInsetsChange = { onSettingChanged("header_box", it) },
            topLabel = strings.boxMarginTop,
            bottomLabel = strings.boxMarginBottom,
            leftLabel = strings.boxMarginLeft,
            rightLabel = strings.boxMarginRight,
            collapsible = true,
            initiallyExpanded = false,
        )
        BoxMarginSection(
            title = strings.footerBoxLabel,
            insets = prefs.footerBox,
            defaultInsets = BoxInsetsDp(0f, 16f, 24f, 24f),
            onInsetsChange = { onSettingChanged("footer_box", it) },
            topLabel = strings.boxMarginTop,
            bottomLabel = strings.boxMarginBottom,
            leftLabel = strings.boxMarginLeft,
            rightLabel = strings.boxMarginRight,
            collapsible = true,
            initiallyExpanded = false,
        )
    }
}

@Composable
fun MarginPresetSettingsCard(
    prefs: ReaderPreferences,
    onSettingChanged: (String, Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalReaderColorScheme.current
    val strings = LocalAppStrings.current.reader
    val unifiedSync = remember { mutableStateOf(false) }
    val presets = readerMarginPresets(strings)
    val detected = detectMarginPreset(prefs, strings)
    val selectedPreset = detected.first.takeIf { detected.second }

    fun applyPreset(preset: MarginPreset) {
        onSettingChanged("body_box", preset.bodyBox)
        onSettingChanged("header_box", preset.headerBox)
        onSettingChanged("footer_box", preset.footerBox)
        onSettingChanged("title_box", preset.titleBox)
    }

    fun syncLeftRightToOthers(source: BoxInsetsDp, exclude: String) {
        val left = source.left
        val right = source.right
        if (exclude != "body") onSettingChanged("body_box", prefs.bodyBox.copy(left = left, right = right))
        if (exclude != "header") onSettingChanged("header_box", prefs.headerBox.copy(left = left, right = right))
        if (exclude != "footer") onSettingChanged("footer_box", prefs.footerBox.copy(left = left, right = right))
        if (exclude != "title") onSettingChanged("title_box", prefs.titleBox.copy(left = left, right = right))
    }

    SettingsCard(title = strings.marginCardTitle, modifier = modifier) {
        MarginPresetRow(
            selected = selectedPreset,
            onSelect = ::applyPreset,
            presets = presets,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SwitchRow(
                label = strings.unifiedLeftRightLabel,
                checked = unifiedSync.value,
                onCheckedChange = { sync ->
                    unifiedSync.value = sync
                    if (sync) syncLeftRightToOthers(prefs.bodyBox, "none")
                },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    presets.getOrNull(1)?.let(::applyPreset)
                },
            ) {
                Text(
                    text = strings.resetMarginsLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.accent,
                )
            }
        }
    }
}

private fun headerVisibilityOptions(strings: com.shuli.reader.core.i18n.ReaderStrings): List<Pair<HeaderVisibility, String>> =
    listOf(
        HeaderVisibility.HIDE_WHEN_STATUS_BAR to strings.displayFollowStatusBar,
        HeaderVisibility.ALWAYS_SHOW to strings.displayAlwaysShow,
        HeaderVisibility.ALWAYS_HIDE to strings.displayAlwaysHide,
    )

private fun progressStyleOptions(strings: com.shuli.reader.core.i18n.ReaderStrings): List<Pair<ProgressStyle, String>> =
    listOf(
        ProgressStyle.CHAPTER_FRACTION to strings.progressStyleChapterFraction,
        ProgressStyle.CHAPTER_PERCENT to strings.progressStyleChapterPercent,
        ProgressStyle.PAGE_NUMBER to strings.progressStylePageNumber,
        ProgressStyle.BOOK_FRACTION to strings.progressStyleBookFraction,
        ProgressStyle.BOOK_PERCENT to strings.progressStyleBookPercent,
    )

private fun headerSlotKey(index: Int): String = when (index) {
    0 -> "header_left"
    1 -> "header_center"
    else -> "header_right"
}

private fun footerSlotKey(index: Int): String = when (index) {
    0 -> "footer_left"
    1 -> "footer_center"
    else -> "footer_right"
}
