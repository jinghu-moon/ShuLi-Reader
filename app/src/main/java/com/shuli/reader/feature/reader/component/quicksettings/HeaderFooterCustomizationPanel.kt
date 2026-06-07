package com.shuli.reader.feature.reader.component.quicksettings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.core.reader.HeaderVisibility
import com.shuli.reader.feature.reader.component.ReaderFormPickerRow
import com.shuli.reader.feature.reader.component.ReaderSwitchRow
import com.shuli.reader.feature.reader.component.ReaderValueSlider
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 页眉/页脚自定义面板 — 从 [SettingsPanel] 的 ExpandableSection 抽出。
 *
 * 职责：
 * - 页眉：显示模式 / 三槽位内容 / 上边距 / 分隔线 / 字号比例
 * - 页脚：显示模式 / 三槽位内容 / 下边距 / 分隔线 / 字号比例
 *
 * Actor：排版产品 / 视觉设计师
 */
@Composable
internal fun HeaderFooterCustomizationPanel(
    prefs: ReaderPreferences,
    onHeaderVisibilityChange: (HeaderVisibility) -> Unit,
    onFooterVisibilityChange: (HeaderVisibility) -> Unit,
    onHeaderMarginTopChange: (Float) -> Unit,
    onFooterMarginBottomChange: (Float) -> Unit,
    onHeaderLeftChange: (com.shuli.reader.core.reader.SlotContent) -> Unit,
    onHeaderCenterChange: (com.shuli.reader.core.reader.SlotContent) -> Unit,
    onHeaderRightChange: (com.shuli.reader.core.reader.SlotContent) -> Unit,
    onFooterLeftChange: (com.shuli.reader.core.reader.SlotContent) -> Unit,
    onFooterCenterChange: (com.shuli.reader.core.reader.SlotContent) -> Unit,
    onFooterRightChange: (com.shuli.reader.core.reader.SlotContent) -> Unit,
    onShowHeaderLineChange: (Boolean) -> Unit,
    onShowFooterLineChange: (Boolean) -> Unit,
    onHeaderFontSizeRatioChange: (Float) -> Unit,
    onFooterFontSizeRatioChange: (Float) -> Unit,
) {
    val readerColors = LocalReaderColorScheme.current
    val strings = LocalAppStrings.current

    // ── 页眉 ──
    Text(
        text = strings.reader.headerLabel,
        style = MaterialTheme.typography.labelMedium,
        color = readerColors.textSecondary,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
    )
    if (prefs.header.visibility != HeaderVisibility.ALWAYS_HIDE) {
        ReaderFormPickerRow(
            label = strings.reader.displayLabel,
            options = listOf(
                HeaderVisibility.HIDE_WHEN_STATUS_BAR to strings.reader.displayFollowStatusBar,
                HeaderVisibility.ALWAYS_SHOW to strings.reader.displayAlwaysShow,
                HeaderVisibility.ALWAYS_HIDE to strings.reader.displayAlwaysHide,
            ),
            selected = prefs.header.visibility,
            onSelect = onHeaderVisibilityChange,
        )
        ReaderFormPickerRow(
            label = strings.reader.positionLeft,
            options = slotOptions(),
            selected = prefs.header.left,
            onSelect = onHeaderLeftChange,
            sheetTitle = strings.reader.headerLeft,
        )
        ReaderFormPickerRow(
            label = strings.reader.positionCenter,
            options = slotOptions(),
            selected = prefs.header.center,
            onSelect = onHeaderCenterChange,
            sheetTitle = strings.reader.headerCenter,
        )
        ReaderFormPickerRow(
            label = strings.reader.positionRight,
            options = slotOptions(),
            selected = prefs.header.right,
            onSelect = onHeaderRightChange,
            sheetTitle = strings.reader.headerRight,
        )
        ReaderValueSlider(
            label = strings.reader.headerMarginTop,
            value = prefs.header.marginTop,
            valueRange = 0f..100f,
            steps = 100,
            format = { "${it.toInt()}dp" },
            onValueChange = onHeaderMarginTopChange,
        )
        ReaderSwitchRow(
            label = strings.reader.headerLineLabel,
            checked = prefs.showHeaderLine,
            onCheckedChange = onShowHeaderLineChange,
        )
        ReaderValueSlider(
            label = strings.reader.headerFontSizeLabel,
            value = prefs.headerFontSizeRatio,
            valueRange = 0.5f..1.2f,
            steps = 6,
            format = { "%.0f%%".format(it * 100) },
            onValueChange = onHeaderFontSizeRatioChange,
        )
    } else {
        Text(
            text = strings.reader.headerHidden,
            style = MaterialTheme.typography.bodySmall,
            color = readerColors.textSecondary,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }

    // ── 页脚 ──
    Text(
        text = strings.reader.footerLabel,
        style = MaterialTheme.typography.labelMedium,
        color = readerColors.textSecondary,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
    )
    if (prefs.footer.visibility != HeaderVisibility.ALWAYS_HIDE) {
        ReaderFormPickerRow(
            label = strings.reader.displayLabel,
            options = listOf(
                HeaderVisibility.ALWAYS_SHOW to strings.reader.displayAlwaysShow,
                HeaderVisibility.HIDE_WHEN_STATUS_BAR to strings.reader.displayFollowStatusBar,
                HeaderVisibility.ALWAYS_HIDE to strings.reader.displayAlwaysHide,
            ),
            selected = prefs.footer.visibility,
            onSelect = onFooterVisibilityChange,
        )
        ReaderFormPickerRow(
            label = strings.reader.positionLeft,
            options = slotOptions(),
            selected = prefs.footer.left,
            onSelect = onFooterLeftChange,
            sheetTitle = strings.reader.footerLeft,
        )
        ReaderFormPickerRow(
            label = strings.reader.positionCenter,
            options = slotOptions(),
            selected = prefs.footer.center,
            onSelect = onFooterCenterChange,
            sheetTitle = strings.reader.footerCenter,
        )
        ReaderFormPickerRow(
            label = strings.reader.positionRight,
            options = slotOptions(),
            selected = prefs.footer.right,
            onSelect = onFooterRightChange,
            sheetTitle = strings.reader.footerRight,
        )
        ReaderValueSlider(
            label = strings.reader.footerMarginBottom,
            value = prefs.footer.marginBottom,
            valueRange = 0f..100f,
            steps = 100,
            format = { "${it.toInt()}dp" },
            onValueChange = onFooterMarginBottomChange,
        )
        ReaderSwitchRow(
            label = strings.reader.footerLineLabel,
            checked = prefs.showFooterLine,
            onCheckedChange = onShowFooterLineChange,
        )
        ReaderValueSlider(
            label = strings.reader.footerFontSizeLabel,
            value = prefs.footerFontSizeRatio,
            valueRange = 0.5f..1.2f,
            steps = 6,
            format = { "%.0f%%".format(it * 100) },
            onValueChange = onFooterFontSizeRatioChange,
        )
    } else {
        Text(
            text = strings.reader.footerHidden,
            style = MaterialTheme.typography.bodySmall,
            color = readerColors.textSecondary,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}
