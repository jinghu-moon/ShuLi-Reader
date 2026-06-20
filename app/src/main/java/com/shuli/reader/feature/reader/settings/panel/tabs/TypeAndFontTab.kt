package com.shuli.reader.feature.reader.settings.panel.tabs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.testTag
import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.reader.model.BoxInsetsDp
import com.shuli.reader.core.font.FontManager
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.reader.settings.panel.SelectRow
import com.shuli.reader.feature.reader.settings.panel.SettingsCard
import com.shuli.reader.feature.reader.settings.panel.SwitchRow
import com.shuli.reader.feature.reader.settings.panel.controls.BoxMarginSection
import com.shuli.reader.feature.reader.settings.panel.controls.InkStepperSlider
import com.shuli.reader.feature.reader.settings.panel.controls.MarginPreset
import com.shuli.reader.feature.reader.settings.panel.controls.MarginPresetRow
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * Tab 1「字体排版」内容组装。
 *
 * 卡片结构：正文 / 页眉 / 页脚 / 标题 / 字体 / 高级排版。
 * 每个盒子独立卡片，正文含排版参数 + 边距，其余仅含边距。
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
    val m = LocalAppStrings.current.reader // margin strings alias

    // 全局左右同步状态（所有盒子卡片共享）
    val unifiedSync = remember { mutableStateOf(false) }

    // 预设定义
    val presets = listOf(
        MarginPreset(
            label = m.marginPresetCompact,
            bodyBox = BoxInsetsDp(32f, 32f, 16f, 16f),
            headerBox = BoxInsetsDp(8f, 0f, 16f, 16f),
            footerBox = BoxInsetsDp(0f, 8f, 16f, 16f),
            titleBox = BoxInsetsDp(6f, 6f, 16f, 16f),
        ),
        MarginPreset(
            label = m.marginPresetStandard,
            bodyBox = BoxInsetsDp(48f, 48f, 24f, 24f),
            headerBox = BoxInsetsDp(16f, 0f, 24f, 24f),
            footerBox = BoxInsetsDp(0f, 16f, 24f, 24f),
            titleBox = BoxInsetsDp(9f, 10f, 24f, 24f),
        ),
        MarginPreset(
            label = m.marginPresetRelaxed,
            bodyBox = BoxInsetsDp(64f, 64f, 32f, 32f),
            headerBox = BoxInsetsDp(24f, 0f, 32f, 32f),
            footerBox = BoxInsetsDp(0f, 24f, 32f, 32f),
            titleBox = BoxInsetsDp(12f, 14f, 32f, 32f),
        ),
    )

    // 最近似预设检测（曼哈顿距离）
    fun BoxInsetsDp.distanceTo(other: BoxInsetsDp): Float =
        kotlin.math.abs(top - other.top) + kotlin.math.abs(bottom - other.bottom) +
            kotlin.math.abs(left - other.left) + kotlin.math.abs(right - other.right)

    val currentPreset = presets.minByOrNull {
        prefs.bodyBox.distanceTo(it.bodyBox) + prefs.headerBox.distanceTo(it.headerBox) +
            prefs.footerBox.distanceTo(it.footerBox) + prefs.titleBox.distanceTo(it.titleBox)
    }
    val isExactMatch = currentPreset != null &&
        prefs.bodyBox == currentPreset.bodyBox && prefs.headerBox == currentPreset.headerBox &&
        prefs.footerBox == currentPreset.footerBox && prefs.titleBox == currentPreset.titleBox

    /** 同步左右边距到其他盒子 */
    fun syncLeftRightToOthers(source: BoxInsetsDp, exclude: String) {
        val l = source.left
        val r = source.right
        if (exclude != "body") onSettingChanged("body_box", prefs.bodyBox.copy(left = l, right = r))
        if (exclude != "header") onSettingChanged("header_box", prefs.headerBox.copy(left = l, right = r))
        if (exclude != "footer") onSettingChanged("footer_box", prefs.footerBox.copy(left = l, right = r))
        if (exclude != "title") onSettingChanged("title_box", prefs.titleBox.copy(left = l, right = r))
    }

    Column(modifier = modifier.fillMaxWidth()) {

        // ════════════════════════════════════════════
        //  正文卡片（排版参数 + 边距）
        // ════════════════════════════════════════════
        SettingsCard(title = m.bodyBoxLabel) {
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
            InkStepperSlider(
                value = prefs.letterSpacing,
                onValueChange = { onSettingChanged("letter_spacing", it) },
                valueRange = 0f..0.2f,
                step = 0.01f,
                label = strings.letterSpacingLabel,
                formatValue = { "%.2f".format(it) },
                testTagPrefix = "Slider_LetterSpacing",
            )
            BoxMarginSection(
                title = m.bodyBoxLabel,
                insets = prefs.bodyBox,
                defaultInsets = BoxInsetsDp(48f, 48f, 24f, 24f),
                onInsetsChange = { newBox ->
                    onSettingChanged("body_box", newBox)
                    if (unifiedSync.value) syncLeftRightToOthers(newBox, "body")
                },
                topLabel = m.boxMarginTop,
                bottomLabel = m.boxMarginBottom,
                leftLabel = m.boxMarginLeft,
                rightLabel = m.boxMarginRight,
            )
        }

        // ════════════════════════════════════════════
        //  页眉卡片
        // ════════════════════════════════════════════
        SettingsCard(title = m.headerBoxLabel) {
            InkStepperSlider(
                value = prefs.headerFontSizeRatio,
                onValueChange = { onSettingChanged("header_font_size_ratio", it) },
                valueRange = 0.5f..1.5f,
                step = 0.05f,
                label = strings.fontSizeLabel,
                sublabel = "${(prefs.fontSize * prefs.headerFontSizeRatio).toInt()}sp",
                formatValue = { "%.0f%%".format(it * 100) },
                testTagPrefix = "Slider_HeaderFontRatio",
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
            BoxMarginSection(
                title = m.headerBoxLabel,
                insets = prefs.headerBox,
                defaultInsets = BoxInsetsDp(16f, 0f, 24f, 24f),
                onInsetsChange = { newBox ->
                    onSettingChanged("header_box", newBox)
                    if (unifiedSync.value) syncLeftRightToOthers(newBox, "header")
                },
                topLabel = m.boxMarginTop,
                bottomLabel = m.boxMarginBottom,
                leftLabel = m.boxMarginLeft,
                rightLabel = m.boxMarginRight,
                collapsible = true,
                initiallyExpanded = false,
            )
        }

        // ════════════════════════════════════════════
        //  页脚卡片
        // ════════════════════════════════════════════
        SettingsCard(title = m.footerBoxLabel) {
            InkStepperSlider(
                value = prefs.footerFontSizeRatio,
                onValueChange = { onSettingChanged("footer_font_size_ratio", it) },
                valueRange = 0.5f..1.5f,
                step = 0.05f,
                label = strings.fontSizeLabel,
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
                testTagPrefix = "Slider_FooterAlpha",
            )
            SwitchRow(
                label = strings.footerSeparatorLineLabel,
                checked = prefs.showFooterLine,
                onCheckedChange = { onSettingChanged("show_footer_line", it) },
            )
            BoxMarginSection(
                title = m.footerBoxLabel,
                insets = prefs.footerBox,
                defaultInsets = BoxInsetsDp(0f, 16f, 24f, 24f),
                onInsetsChange = { newBox ->
                    onSettingChanged("footer_box", newBox)
                    if (unifiedSync.value) syncLeftRightToOthers(newBox, "footer")
                },
                topLabel = m.boxMarginTop,
                bottomLabel = m.boxMarginBottom,
                leftLabel = m.boxMarginLeft,
                rightLabel = m.boxMarginRight,
                collapsible = true,
                initiallyExpanded = false,
            )
        }

        // ════════════════════════════════════════════
        //  标题卡片
        // ════════════════════════════════════════════
        SettingsCard(title = m.titleBoxLabel) {
            InkStepperSlider(
                value = prefs.titleFontSize,
                onValueChange = { onSettingChanged("title_font_size", it) },
                valueRange = 12f..48f,
                step = 1f,
                label = strings.fontSizeLabel,
                formatValue = { "${it.toInt()}sp" },
                testTagPrefix = "Slider_TitleFontSize",
            )
            SelectRow(
                label = strings.titleAlignLabel,
                options = listOf(
                    com.shuli.reader.core.reader.model.TitleAlign.LEFT to strings.titleAlignLeft,
                    com.shuli.reader.core.reader.model.TitleAlign.CENTER to strings.titleAlignCenter,
                    com.shuli.reader.core.reader.model.TitleAlign.HIDDEN to strings.titleAlignHidden,
                ),
                selected = prefs.titleStyle.align,
                onSelect = { onSettingChanged("title_align", it) },
            )
            BoxMarginSection(
                title = m.titleBoxLabel,
                insets = prefs.titleBox,
                defaultInsets = BoxInsetsDp(9f, 10f, 24f, 24f),
                onInsetsChange = { newBox ->
                    onSettingChanged("title_box", newBox)
                    if (unifiedSync.value) syncLeftRightToOthers(newBox, "title")
                },
                topLabel = m.boxMarginTop,
                bottomLabel = m.boxMarginBottom,
                leftLabel = m.boxMarginLeft,
                rightLabel = m.boxMarginRight,
                collapsible = true,
                initiallyExpanded = false,
            )
        }

        // ════════════════════════════════════════════
        //  边距预设 + 全局同步 + 重置
        // ════════════════════════════════════════════
        SettingsCard(title = m.marginCardTitle) {
            MarginPresetRow(
                selected = if (isExactMatch) currentPreset else null,
                onSelect = { preset ->
                    onSettingChanged("body_box", preset.bodyBox)
                    onSettingChanged("header_box", preset.headerBox)
                    onSettingChanged("footer_box", preset.footerBox)
                    onSettingChanged("title_box", preset.titleBox)
                },
                presets = presets,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SwitchRow(
                    label = m.unifiedLeftRightLabel,
                    checked = unifiedSync.value,
                    onCheckedChange = { sync ->
                        unifiedSync.value = sync
                        if (sync) syncLeftRightToOthers(prefs.bodyBox, "none")
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        onSettingChanged("body_box", BoxInsetsDp(48f, 48f, 24f, 24f))
                        onSettingChanged("header_box", BoxInsetsDp(16f, 0f, 24f, 24f))
                        onSettingChanged("footer_box", BoxInsetsDp(0f, 16f, 24f, 24f))
                        onSettingChanged("title_box", BoxInsetsDp(9f, 10f, 24f, 24f))
                    },
                ) {
                    Text(
                        text = m.resetMarginsLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.accent,
                    )
                }
            }
        }

        // ════════════════════════════════════════════
        //  字体
        // ════════════════════════════════════════════
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
                onImport = { },
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

        // ════════════════════════════════════════════
        //  高级排版（可折叠）
        // ════════════════════════════════════════════
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
