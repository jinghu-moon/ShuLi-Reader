package com.shuli.reader.feature.reader.settings.panel.tabs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.FormatAlignJustify
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.IndentUnit
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.font.FontManager
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.reader.settings.panel.SegmentedRow
import com.shuli.reader.feature.reader.settings.panel.SettingsCard
import com.shuli.reader.feature.reader.settings.panel.SwitchRow
import com.shuli.reader.feature.reader.settings.panel.controls.FontPreviewRow
import com.shuli.reader.feature.reader.settings.panel.controls.InkStepperSlider

/**
 * Tab「排版」内容组装。
 *
 * 卡片结构：字体 / 正文排版 / 文本处理。
 */
@Composable
fun Typesetting(
    prefs: ReaderPreferences,
    onSettingChanged: (String, Any) -> Unit,
    modifier: Modifier = Modifier,
    customFonts: List<FontManager.FontEntry> = emptyList(),
    onImportFont: (android.net.Uri) -> Unit = {},
    onDeleteFont: (String) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        FontSettingsCard(
            prefs = prefs,
            onSettingChanged = onSettingChanged,
            customFonts = customFonts,
            onImportFont = onImportFont,
            onDeleteFont = onDeleteFont,
        )
        BodyTypographySettingsCard(
            prefs = prefs,
            onSettingChanged = onSettingChanged,
        )
        TextProcessingSettingsCard(
            prefs = prefs,
            onSettingChanged = onSettingChanged,
        )
    }
}

@Composable
fun FontSettingsCard(
    prefs: ReaderPreferences,
    onSettingChanged: (String, Any) -> Unit,
    modifier: Modifier = Modifier,
    customFonts: List<FontManager.FontEntry> = emptyList(),
    onImportFont: (android.net.Uri) -> Unit = {},
    onDeleteFont: (String) -> Unit = {},
) {
    val strings = LocalAppStrings.current.reader
    val fontLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { onImportFont(it) } }

    SettingsCard(
        title = strings.fontCardTitle,
        modifier = modifier,
        headerTrailing = {
            androidx.compose.material3.TextButton(
                onClick = { fontLauncher.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream")) },
                modifier = Modifier.testTag("Font_ImportButton"),
            ) {
                androidx.compose.material3.Text(
                    text = strings.importFont,
                    color = com.shuli.reader.ui.theme.LocalReaderColorScheme.current.accent,
                )
            }
        },
    ) {
        FontPreviewRow(
            selectedKey = prefs.readingFont,
            customFonts = customFonts,
            onSelect = { onSettingChanged("reading_font", it) },
            onImport = {},
            onDelete = { entry ->
                if (prefs.readingFont == entry.key) onSettingChanged("reading_font", "harmony")
                onDeleteFont(entry.key)
            },
        )
        SegmentedRow(
            label = strings.fontWeightLabel,
            options = listOf(
                ReaderFontWeight.LIGHT to strings.fontWeightLight,
                ReaderFontWeight.NORMAL to strings.fontWeightNormal,
                ReaderFontWeight.MEDIUM to strings.fontWeightMediumFull,
                ReaderFontWeight.BOLD to strings.fontWeightBold,
            ),
            selected = prefs.fontWeight,
            onSelect = { onSettingChanged("font_weight", it) },
        )
    }
}

@Composable
fun BodyTypographySettingsCard(
    prefs: ReaderPreferences,
    onSettingChanged: (String, Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current.reader

    SettingsCard(title = "正文排版", modifier = modifier) {
        InkStepperSlider(
            value = prefs.fontSize,
            onValueChange = { onSettingChanged("font_size", it) },
            valueRange = 12f..32f,
            step = 1f,
            label = strings.defaultFontSize,
            defaultValue = 18f,
            formatValue = { "${it.toInt()}" },
            testTagPrefix = "Slider_FontSize",
        )
        InkStepperSlider(
            value = prefs.lineSpacing,
            onValueChange = { onSettingChanged("line_spacing", it) },
            valueRange = 1.0f..2.5f,
            step = 0.1f,
            label = strings.defaultLineSpacing,
            testTagPrefix = "Slider_LineSpacing",
        )
        InkStepperSlider(
            value = prefs.paragraphSpacing,
            onValueChange = { onSettingChanged("paragraph_spacing", it) },
            valueRange = 0.5f..2.0f,
            step = 0.1f,
            label = strings.paragraphSpacing,
            testTagPrefix = "Slider_ParaSpacing",
        )
        InkStepperSlider(
            value = prefs.indent,
            onValueChange = { onSettingChanged("indent", it) },
            valueRange = 0f..4f,
            step = 0.5f,
            label = strings.firstLineIndent,
            formatValue = { "%.1f".format(it) },
            testTagPrefix = "Slider_Indent",
        )
        SegmentedRow(
            label = strings.indentUnitLabel,
            options = listOf(
                IndentUnit.CHARACTER to strings.indentUnitChar,
                IndentUnit.PIXEL to strings.indentUnitDp,
            ),
            selected = prefs.indentUnit,
            onSelect = { onSettingChanged("indent_unit", it) },
        )
        InkStepperSlider(
            value = prefs.letterSpacing,
            onValueChange = { onSettingChanged("letter_spacing", it) },
            valueRange = 0f..0.2f,
            step = 0.01f,
            label = strings.letterSpacingLabel,
            formatValue = { "%.2f".format(it) },
            testTagPrefix = "Slider_LetterSpacing",
        )
        SegmentedRow(
            label = strings.textAlignLabel,
            options = listOf(
                ReaderTextAlign.LEFT to strings.textAlignLeft,
                ReaderTextAlign.JUSTIFY to strings.textAlignJustifyFull,
                ReaderTextAlign.RIGHT to strings.textAlignRight,
            ),
            selected = prefs.textAlign,
            onSelect = { onSettingChanged("text_align", it) },
            icons = listOf(
                Icons.AutoMirrored.Outlined.FormatAlignLeft,
                Icons.Outlined.FormatAlignJustify,
                Icons.AutoMirrored.Outlined.FormatAlignRight,
            ),
        )
        SwitchRow(
            label = strings.bottomJustifyLabel,
            sublabel = strings.bottomJustifyDesc,
            checked = prefs.bottomJustify,
            onCheckedChange = { onSettingChanged("bottom_justify", it) },
        )
    }
}

@Composable
fun TextProcessingSettingsCard(
    prefs: ReaderPreferences,
    onSettingChanged: (String, Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current.reader

    SettingsCard(
        title = strings.advancedTypesettingCard,
        modifier = modifier,
        collapsible = true,
        initiallyExpanded = true,
    ) {
        SegmentedRow(
            label = strings.chineseConvertFullLabel,
            options = listOf(
                ChineseConvert.NONE to strings.chineseConvertNoneFull,
                ChineseConvert.SIMPLIFIED to strings.chineseConvertSimplified,
                ChineseConvert.TRADITIONAL to strings.chineseConvertTraditional,
            ),
            selected = prefs.chineseConvert,
            onSelect = { onSettingChanged("chinese_convert", it) },
        )
        SwitchRow(
            label = strings.panguSpacingLabel,
            sublabel = strings.usePanguSpacingFullLabel,
            checked = prefs.usePanguSpacing,
            onCheckedChange = { onSettingChanged("use_pangu_spacing", it) },
        )
        SwitchRow(
            label = strings.useZhLayoutLabel,
            checked = prefs.useZhLayout,
            onCheckedChange = { onSettingChanged("use_zh_layout", it) },
        )
        SwitchRow(
            label = strings.removeEmptyLinesShortLabel,
            checked = prefs.removeEmptyLines,
            onCheckedChange = { onSettingChanged("remove_empty_lines", it) },
        )
        SwitchRow(
            label = strings.cleanChapterTitleShortLabel,
            checked = prefs.cleanChapterTitle,
            onCheckedChange = { onSettingChanged("clean_chapter_title", it) },
        )
        SwitchRow(
            label = strings.paragraphDividerLabel,
            checked = prefs.paragraphDivider,
            onCheckedChange = { onSettingChanged("paragraph_divider", it) },
        )
        SwitchRow(
            label = strings.bionicReadingLabel,
            sublabel = strings.bionicReadingDesc,
            checked = prefs.bionicReading,
            onCheckedChange = { onSettingChanged("bionic_reading", it) },
        )
        SwitchRow(
            label = strings.preserveOriginalIndentLabel,
            sublabel = strings.preserveOriginalIndentShortDesc,
            checked = prefs.preserveOriginalIndent,
            onCheckedChange = { onSettingChanged("preserve_original_indent", it) },
        )
        SwitchRow(
            label = strings.epubOverrideStyleShortLabel,
            sublabel = strings.epubOverrideStyleShortDesc,
            checked = prefs.epubOverrideStyle,
            onCheckedChange = { onSettingChanged("epub_override_style", it) },
        )
        SwitchRow(
            label = "广告过滤",
            checked = prefs.adFiltering,
            onCheckedChange = { onSettingChanged("ad_filtering", it) },
        )
    }
}
