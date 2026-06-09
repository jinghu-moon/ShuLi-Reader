package com.shuli.reader.feature.reader.component.quicksettings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.PageAnimType
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.reader.component.ReaderFormPickerRow
import com.shuli.reader.feature.reader.component.ReaderSwitchRow
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * Tab 2: 样式面板 — 翻页动画、字体、字重、对齐、简繁、盘古、底部对齐
 */
@Composable
internal fun StylePanel(
    prefs: ReaderPreferences,
    customFonts: List<com.shuli.reader.core.font.FontManager.FontEntry>,
    onImportFont: (android.net.Uri) -> Unit,
    onDeleteFont: (String) -> Unit,
    onPageAnimTypeChange: (PageAnimType) -> Unit,
    onReadingFontChange: (String) -> Unit,
    onFontWeightChange: (ReaderFontWeight) -> Unit,
    onTextAlignChange: (ReaderTextAlign) -> Unit,
    onChineseConvertChange: (ChineseConvert) -> Unit,
    onUseZhLayoutChange: (Boolean) -> Unit,
    onPanguSpacingChange: (Boolean) -> Unit,
    onBottomJustifyChange: (Boolean) -> Unit,
    onRemoveEmptyLinesChange: (Boolean) -> Unit = {},
    onCleanChapterTitleChange: (Boolean) -> Unit = {},
    onEpubOverrideStyleChange: (Boolean) -> Unit = {},
) {
    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { onImportFont(it) }
    }

    // 翻页动画
    ReaderFormPickerRow(
        label = strings.reader.defaultPageAnim,
        options = listOf(
            PageAnimType.NONE to strings.reader.pageAnimNone,
            PageAnimType.COVER to strings.reader.pageAnimOverlay,
            PageAnimType.HORIZONTAL to strings.reader.pageAnimSlide,
            PageAnimType.SIMULATION to strings.reader.pageAnimSimulation,
            PageAnimType.SCROLL to strings.reader.pageAnimFade,
        ),
        selected = prefs.pageAnimType,
        onSelect = onPageAnimTypeChange,
    )

    // 字体
    val fontOptions = buildList {
        add("harmony" to strings.reader.readingFontHarmony)
        add("system" to strings.reader.readingFontSystem)
        customFonts.forEach { entry ->
            add(entry.key to entry.name)
        }
    }
    val fontFileMap = remember(customFonts) {
        customFonts.associate { entry -> entry.key to entry.file }
    }
    ReaderFormPickerRow(
        label = strings.reader.readingFont,
        options = fontOptions,
        selected = prefs.readingFont,
        onSelect = onReadingFontChange,
        fontFiles = fontFileMap,
    )
    // 导入字体按钮
    androidx.compose.material3.TextButton(
        onClick = { launcher.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream")) },
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(strings.reader.importFont, style = MaterialTheme.typography.bodySmall)
    }
    // 已导入字体列表（可删除）
    if (customFonts.isNotEmpty()) {
        Column(modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)) {
            customFonts.forEach { entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = readerColors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = strings.common.deleteFont,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onDeleteFont(entry.id) },
                        tint = readerColors.textSecondary,
                    )
                }
            }
        }
    }
    // 字重
    ReaderFormPickerRow(
        label = strings.reader.fontWeightLabel,
        options = listOf(
            ReaderFontWeight.LIGHT to strings.reader.fontWeightLight,
            ReaderFontWeight.NORMAL to strings.reader.fontWeightNormal,
            ReaderFontWeight.MEDIUM to strings.reader.fontWeightMedium,
            ReaderFontWeight.BOLD to strings.reader.fontWeightBold,
        ),
        selected = prefs.fontWeight,
        onSelect = onFontWeightChange,
    )
    // 对齐
    ReaderFormPickerRow(
        label = strings.reader.textAlignLabel,
        options = listOf(
            ReaderTextAlign.LEFT to strings.reader.textAlignLeft,
            ReaderTextAlign.JUSTIFY to strings.reader.textAlignJustify,
        ),
        selected = prefs.textAlign,
        onSelect = onTextAlignChange,
    )
    // 简繁转换
    ReaderFormPickerRow(
        label = strings.reader.chineseConvertLabel,
        options = listOf(
            ChineseConvert.NONE to strings.reader.chineseConvertNone,
            ChineseConvert.SIMPLIFIED to strings.reader.chineseConvertSimplified,
            ChineseConvert.TRADITIONAL to strings.reader.chineseConvertTraditional,
        ),
        selected = prefs.chineseConvert,
        onSelect = onChineseConvertChange,
    )
    // 自定义中文分行
    ReaderSwitchRow(
        label = strings.reader.useZhLayoutLabel,
        checked = prefs.useZhLayout,
        onCheckedChange = onUseZhLayoutChange,
    )
    // 中英文间增加空格
    ReaderSwitchRow(
        label = strings.reader.usePanguSpacingLabel,
        checked = prefs.usePanguSpacing,
        onCheckedChange = onPanguSpacingChange,
    )
    // 底部对齐
    ReaderSwitchRow(
        label = strings.reader.bottomJustifyLabel,
        checked = prefs.bottomJustify,
        onCheckedChange = onBottomJustifyChange,
    )
    // P1: 文本净化
    ReaderSwitchRow(
        label = strings.reader.removeEmptyLinesLabel,
        description = strings.reader.removeEmptyLinesDesc,
        checked = prefs.removeEmptyLines,
        onCheckedChange = onRemoveEmptyLinesChange,
    )
    ReaderSwitchRow(
        label = strings.reader.cleanChapterTitleLabel,
        description = strings.reader.cleanChapterTitleDesc,
        checked = prefs.cleanChapterTitle,
        onCheckedChange = onCleanChapterTitleChange,
    )
    // P2: EPUB 覆盖样式
    ReaderSwitchRow(
        label = strings.reader.epubOverrideStyleLabel,
        description = strings.reader.epubOverrideStyleDesc,
        checked = prefs.epubOverrideStyle,
        onCheckedChange = onEpubOverrideStyleChange,
    )
}
