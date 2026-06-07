package com.shuli.reader.feature.reader.component.quicksettings

import androidx.compose.runtime.Composable
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.reader.component.ReaderValueSlider

/**
 * Tab 1: 排版面板 — 字号、行距、段距、缩进、边距、字距
 */
@Composable
internal fun LayoutPanel(
    prefs: ReaderPreferences,
    onFontSizeChange: (Float) -> Unit,
    onLetterSpacingChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onParagraphSpacingChange: (Float) -> Unit,
    onIndentChange: (Float) -> Unit,
    onMarginVerticalChange: (Float) -> Unit,
    onMarginHorizontalChange: (Float) -> Unit,
) {
    val strings = LocalAppStrings.current

    ReaderValueSlider(
        label = strings.reader.defaultFontSize,
        value = prefs.fontSize,
        valueRange = 10f..32f,
        steps = 21,
        format = { "${it.toInt()}sp" },
        onValueChange = onFontSizeChange,
    )
    ReaderValueSlider(
        label = strings.reader.defaultLineSpacing,
        value = prefs.lineSpacing,
        valueRange = 1.0f..3.0f,
        steps = 19,
        format = { "%.1f".format(it) },
        onValueChange = onLineSpacingChange,
    )
    ReaderValueSlider(
        label = strings.reader.marginTopBottom,
        value = prefs.marginVertical,
        valueRange = 0f..96f,
        steps = 23,
        format = { "${it.toInt()}dp" },
        onValueChange = onMarginVerticalChange,
    )
    ReaderValueSlider(
        label = strings.reader.marginLeftRight,
        value = prefs.marginHorizontal,
        valueRange = 0f..64f,
        steps = 15,
        format = { "${it.toInt()}dp" },
        onValueChange = onMarginHorizontalChange,
    )
    ReaderValueSlider(
        label = strings.reader.paragraphSpacing,
        value = prefs.paragraphSpacing,
        valueRange = 0.5f..3.0f,
        steps = 24,
        format = { "%.1f".format(it) },
        onValueChange = onParagraphSpacingChange,
    )
    ReaderValueSlider(
        label = strings.reader.firstLineIndent,
        value = prefs.indent,
        valueRange = 0f..10f,
        steps = 19,
        format = { "%.1f".format(it) },
        onValueChange = onIndentChange,
    )
    ReaderValueSlider(
        label = strings.reader.letterSpacingLabel,
        value = prefs.letterSpacing,
        valueRange = 0f..0.2f,
        steps = 19,
        format = { "%.2f".format(it) },
        onValueChange = onLetterSpacingChange,
    )
}
