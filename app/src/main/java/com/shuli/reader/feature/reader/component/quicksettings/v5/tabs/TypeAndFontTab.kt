package com.shuli.reader.feature.reader.component.quicksettings.v5.tabs

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
import com.shuli.reader.feature.reader.component.quicksettings.v5.SelectRow
import com.shuli.reader.feature.reader.component.quicksettings.v5.SettingsCard
import com.shuli.reader.feature.reader.component.quicksettings.v5.SwitchRow
import com.shuli.reader.feature.reader.component.quicksettings.v5.controls.InkStepperSlider
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
    Column(modifier = modifier.fillMaxWidth()) {
        // ── 基础排版 ──
        SettingsCard(title = "基础排版") {
            InkStepperSlider(
                value = prefs.fontSize,
                onValueChange = { onSettingChanged("font_size", it) },
                valueRange = 12f..32f,
                step = 1f,
                label = "字号",
                formatValue = { "${it.toInt()}" },
                testTagPrefix = "Slider_FontSize",
            )
            InkStepperSlider(
                value = prefs.lineSpacing,
                onValueChange = { onSettingChanged("line_spacing", it) },
                valueRange = 1.0f..2.5f,
                step = 0.1f,
                label = "行距",
                testTagPrefix = "Slider_LineSpacing",
            )
            InkStepperSlider(
                value = prefs.paragraphSpacing,
                onValueChange = { onSettingChanged("paragraph_spacing", it) },
                valueRange = 0.5f..2.0f,
                step = 0.1f,
                label = "段距",
                testTagPrefix = "Slider_ParaSpacing",
            )
            InkStepperSlider(
                value = prefs.indent,
                onValueChange = { onSettingChanged("indent", it) },
                valueRange = 0f..4f,
                step = 0.5f,
                label = "缩进",
                formatValue = { "%.1f".format(it) },
                testTagPrefix = "Slider_Indent",
            )
            InkStepperSlider(
                value = prefs.letterSpacing,
                onValueChange = { onSettingChanged("letter_spacing", it) },
                valueRange = 0f..0.2f,
                step = 0.01f,
                label = "字距",
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
                label = "上边距",
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
                label = "下边距",
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
                label = "左边距",
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
                label = "右边距",
                formatValue = { "${it.toInt()}" },
                testTagPrefix = "Slider_MarginRight",
            )
            SwitchRow(
                label = "同步上下 / 左右",
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
                label = "最大页宽",
                sublabel = "0 表示不限制",
                formatValue = { if (it <= 0f) "不限" else "${it.toInt()}" },
                testTagPrefix = "Slider_MaxPageWidth",
            )
        }

        // ── 字体 ──
        val fontLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let { onImportFont(it) } }
        SettingsCard(
            title = "字体",
            headerTrailing = {
                TextButton(
                    onClick = { fontLauncher.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream")) },
                    modifier = Modifier.testTag("Font_ImportButton"),
                ) {
                    Text(text = "导入字体", color = colors.accent)
                }
            },
        ) {
            com.shuli.reader.feature.reader.component.quicksettings.v5.controls.FontPreviewRow(
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
                label = "字重",
                options = listOf(
                    ReaderFontWeight.LIGHT to "细",
                    ReaderFontWeight.NORMAL to "常规",
                    ReaderFontWeight.MEDIUM to "中等",
                    ReaderFontWeight.BOLD to "粗",
                ),
                selected = prefs.fontWeight,
                onSelect = { onSettingChanged("font_weight", it) },
                topDivider = true,
            )
            SelectRow(
                label = "对齐",
                options = listOf(
                    ReaderTextAlign.LEFT to "左对齐",
                    ReaderTextAlign.JUSTIFY to "两端对齐",
                ),
                selected = prefs.textAlign,
                onSelect = { onSettingChanged("text_align", it) },
                topDivider = true,
            )
        }

        // ── 高级排版（可折叠）──
        SettingsCard(title = "高级排版", collapsible = true, initiallyExpanded = false) {
            SelectRow(
                label = "简繁转换",
                options = listOf(
                    ChineseConvert.NONE to "不转换",
                    ChineseConvert.SIMPLIFIED to "简体",
                    ChineseConvert.TRADITIONAL to "繁体",
                ),
                selected = prefs.chineseConvert,
                onSelect = { onSettingChanged("chinese_convert", it) },
            )
            SwitchRow(
                label = "盘古之白",
                sublabel = "中英文间自动加空格",
                checked = prefs.usePanguSpacing,
                onCheckedChange = { onSettingChanged("use_pangu_spacing", it) },
                topDivider = true,
            )
            SwitchRow(
                label = "底部对齐",
                sublabel = "均匀分布行间距",
                checked = prefs.bottomJustify,
                onCheckedChange = { onSettingChanged("bottom_justify", it) },
                topDivider = true,
            )
            SwitchRow(
                label = "去除空行",
                checked = prefs.removeEmptyLines,
                onCheckedChange = { onSettingChanged("remove_empty_lines", it) },
                topDivider = true,
            )
            SwitchRow(
                label = "段间分隔线",
                checked = prefs.paragraphDivider,
                onCheckedChange = { onSettingChanged("paragraph_divider", it) },
                topDivider = true,
            )
            SwitchRow(
                label = "Bionic Reading",
                sublabel = "仿生阅读加粗",
                checked = prefs.bionicReading,
                onCheckedChange = { onSettingChanged("bionic_reading", it) },
                topDivider = true,
            )
            SwitchRow(
                label = "清理章节标题",
                checked = prefs.cleanChapterTitle,
                onCheckedChange = { onSettingChanged("clean_chapter_title", it) },
                topDivider = true,
            )
            SwitchRow(
                label = "保留原文缩进",
                sublabel = "不覆盖TXT文件的行首缩进",
                checked = prefs.preserveOriginalIndent,
                onCheckedChange = { onSettingChanged("preserve_original_indent", it) },
                topDivider = true,
            )
            SwitchRow(
                label = "覆盖 EPUB 样式",
                sublabel = "强制使用阅读器排版",
                checked = prefs.epubOverrideStyle,
                onCheckedChange = { onSettingChanged("epub_override_style", it) },
                topDivider = true,
            )
        }
    }
}
