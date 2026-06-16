package com.shuli.reader.feature.reader.settings.panel.tabs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.font.FontManager
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.reader.settings.panel.SelectRow
import com.shuli.reader.feature.reader.settings.panel.SettingsCard
import com.shuli.reader.feature.reader.settings.panel.SwitchRow
import com.shuli.reader.feature.reader.settings.panel.controls.InkStepperSlider
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * Tab 1「字体排版」内容组装。
 *
 * 卡片：基础排版 / 字体 / 边距 / 高级排版（可折叠）。
 * 全部写操作经 [onSettingChanged] 泛型通道，由 Modal 桥接为类型安全 Intent。
 */
@Composable
fun TypeAndFontTab(
    prefs: ReaderPreferences,
    onSettingChanged: (String, Any) -> Unit,
    modifier: Modifier = Modifier,
    customFonts: List<FontManager.FontEntry> = emptyList(),
    onImportFont: (android.net.Uri) -> Unit = {},
    onDeleteFont: (String) -> Unit = {},
) {
    val colors = LocalReaderColorScheme.current
    val strings = LocalAppStrings.current.reader
    Column(modifier = modifier.fillMaxWidth()) {
        // ── 基础排版 ──
        SettingsCard(title = strings.basicTypesettingCard) {
            InkStepperSlider(
                value = prefs.fontSize,
                onValueChange = { onSettingChanged("font_size", it) },
                valueRange = 12f..32f,
                step = 1f,
                label = strings.defaultFontSize,
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
            InkStepperSlider(
                value = prefs.letterSpacing,
                onValueChange = { onSettingChanged("letter_spacing", it) },
                valueRange = 0f..0.2f,
                step = 0.01f,
                label = strings.letterSpacingLabel,
                formatValue = { "%.2f".format(it) },
                testTagPrefix = "Slider_LetterSpacing",
            )
            // ── 边距 ──
            val marginSync = remember { mutableStateOf(false) }
            val mTop = prefs.marginTop ?: prefs.marginVertical
            val mBottom = prefs.marginBottom ?: prefs.marginVertical
            val mLeft = prefs.marginLeft ?: prefs.marginHorizontal
            val mRight = prefs.marginRight ?: prefs.marginHorizontal
            InkStepperSlider(
                value = mTop,
                onValueChange = { v ->
                    onSettingChanged("margin_top", v)
                    if (marginSync.value) onSettingChanged("margin_bottom", v)
                },
                valueRange = 0f..96f,
                step = 4f,
                label = strings.marginTopLabel,
                formatValue = { "${it.toInt()}" },
                testTagPrefix = "Slider_MarginTop",
            )
            InkStepperSlider(
                value = mBottom,
                onValueChange = { v ->
                    onSettingChanged("margin_bottom", v)
                    if (marginSync.value) onSettingChanged("margin_top", v)
                },
                valueRange = 0f..96f,
                step = 4f,
                label = strings.marginBottomLabel,
                formatValue = { "${it.toInt()}" },
                testTagPrefix = "Slider_MarginBottom",
            )
            InkStepperSlider(
                value = mLeft,
                onValueChange = { v ->
                    onSettingChanged("margin_left", v)
                    if (marginSync.value) onSettingChanged("margin_right", v)
                },
                valueRange = 0f..96f,
                step = 4f,
                label = strings.marginLeftLabel,
                formatValue = { "${it.toInt()}" },
                testTagPrefix = "Slider_MarginLeft",
            )
            InkStepperSlider(
                value = mRight,
                onValueChange = { v ->
                    onSettingChanged("margin_right", v)
                    if (marginSync.value) onSettingChanged("margin_left", v)
                },
                valueRange = 0f..96f,
                step = 4f,
                label = strings.marginRightLabel,
                formatValue = { "${it.toInt()}" },
                testTagPrefix = "Slider_MarginRight",
            )
            SwitchRow(
                label = strings.syncMarginsLabel,
                checked = marginSync.value,
                onCheckedChange = { sync ->
                    marginSync.value = sync
                    if (sync) {
                        onSettingChanged("margin_bottom", mTop)
                        onSettingChanged("margin_right", mLeft)
                    }
                },
            )
            InkStepperSlider(
                value = prefs.maxPageWidth,
                onValueChange = { onSettingChanged("max_page_width", it) },
                valueRange = 0f..900f,
                step = 50f,
                label = strings.maxPageWidthLabel,
                sublabel = strings.maxPageWidthUnlimited,
                formatValue = { if (it <= 0f) strings.maxPageWidthUnlimitedShort else "${it.toInt()}" },
                testTagPrefix = "Slider_MaxPageWidth",
            )
        }

        // ── 字体 ──
        val fontLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let { onImportFont(it) } }
        SettingsCard(
            title = strings.fontCardTitle,
            headerTrailing = {
                TextButton(
                    onClick = { fontLauncher.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream")) },
                    modifier = Modifier.testTag("Font_ImportButton"),
                ) {
                    Text(text = strings.importFont, color = colors.accent)
                }
            },
        ) {
            com.shuli.reader.feature.reader.settings.panel.controls.FontPreviewRow(
                selectedKey = prefs.readingFont,
                customFonts = customFonts,
                onSelect = { onSettingChanged("reading_font", it) },
                onImport = { /* 导入入口已迁移至卡片标题右侧 */ },
                onDelete = { entry ->
                    if (prefs.readingFont == entry.key) onSettingChanged("reading_font", "harmony")
                    onDeleteFont(entry.key)
                },
            )
            SelectRow(
                label = strings.fontWeightLabel,
                options = listOf(
                    ReaderFontWeight.LIGHT to strings.fontWeightLight,
                    ReaderFontWeight.NORMAL to strings.fontWeightNormal,
                    ReaderFontWeight.MEDIUM to strings.fontWeightMediumFull,
                    ReaderFontWeight.BOLD to strings.fontWeightBold,
                ),
                selected = prefs.fontWeight,
                onSelect = { onSettingChanged("font_weight", it) },
                topDivider = true,
            )
            SelectRow(
                label = strings.textAlignLabel,
                options = listOf(
                    ReaderTextAlign.LEFT to strings.textAlignLeft,
                    ReaderTextAlign.JUSTIFY to strings.textAlignJustifyFull,
                ),
                selected = prefs.textAlign,
                onSelect = { onSettingChanged("text_align", it) },
                topDivider = true,
            )
        }

        // ── 高级排版（可折叠）──
        SettingsCard(title = strings.advancedTypesettingCard, collapsible = true, initiallyExpanded = false) {
            SelectRow(
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
                topDivider = true,
            )
            SwitchRow(
                label = strings.bottomJustifyLabel,
                sublabel = strings.bottomJustifyDesc,
                checked = prefs.bottomJustify,
                onCheckedChange = { onSettingChanged("bottom_justify", it) },
                topDivider = true,
            )
            SwitchRow(
                label = strings.removeEmptyLinesShortLabel,
                checked = prefs.removeEmptyLines,
                onCheckedChange = { onSettingChanged("remove_empty_lines", it) },
                topDivider = true,
            )
            SwitchRow(
                label = strings.paragraphDividerLabel,
                checked = prefs.paragraphDivider,
                onCheckedChange = { onSettingChanged("paragraph_divider", it) },
                topDivider = true,
            )
            SwitchRow(
                label = "Bionic Reading",
                sublabel = strings.bionicReadingDesc,
                checked = prefs.bionicReading,
                onCheckedChange = { onSettingChanged("bionic_reading", it) },
                topDivider = true,
            )
            SwitchRow(
                label = strings.cleanChapterTitleShortLabel,
                checked = prefs.cleanChapterTitle,
                onCheckedChange = { onSettingChanged("clean_chapter_title", it) },
                topDivider = true,
            )
            SwitchRow(
                label = strings.preserveOriginalIndentLabel,
                sublabel = strings.preserveOriginalIndentShortDesc,
                checked = prefs.preserveOriginalIndent,
                onCheckedChange = { onSettingChanged("preserve_original_indent", it) },
                topDivider = true,
            )
            SwitchRow(
                label = strings.epubOverrideStyleShortLabel,
                sublabel = strings.epubOverrideStyleShortDesc,
                checked = prefs.epubOverrideStyle,
                onCheckedChange = { onSettingChanged("epub_override_style", it) },
                topDivider = true,
            )
        }
    }
}
