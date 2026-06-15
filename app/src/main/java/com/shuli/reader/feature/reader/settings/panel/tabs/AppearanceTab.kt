package com.shuli.reader.feature.reader.settings.panel.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.data.DualPageMode
import com.shuli.reader.core.data.PageAnimSpeed
import com.shuli.reader.core.data.PageAnimType
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.reader.model.HeaderVisibility
import com.shuli.reader.feature.reader.settings.panel.DividerSwitchRow
import com.shuli.reader.feature.reader.settings.panel.SelectRow
import com.shuli.reader.feature.reader.settings.panel.SettingsCard
import com.shuli.reader.feature.reader.settings.panel.SlotMatrix
import com.shuli.reader.feature.reader.settings.panel.SwitchRow
import com.shuli.reader.feature.reader.settings.panel.controls.InkStepperSlider
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * Tab 2「外观显示」内容组装。
 *
 * 卡片：页眉页脚 / 屏幕与亮度 / 显示模式。
 */
@Composable
fun AppearanceTab(
    prefs: ReaderPreferences,
    onSettingChanged: (String, Any) -> Unit,
    modifier: Modifier = Modifier,
    onContinuousSettingChanged: (String, Any, Boolean) -> Unit = { _, _, _ -> },
) {
    val colors = LocalReaderColorScheme.current
    val isHidden = prefs.header.visibility == HeaderVisibility.ALWAYS_HIDE &&
        prefs.footer.visibility == HeaderVisibility.ALWAYS_HIDE

    Column(modifier = modifier.fillMaxWidth()) {
        // ── 页眉页脚 ──
        SettingsCard(title = "页眉页脚") {
            VisibilityDropdown(
                headerVisibility = prefs.header.visibility,
                footerVisibility = prefs.footer.visibility,
                onHeaderChange = { onSettingChanged("header_visibility", it) },
                onFooterChange = { onSettingChanged("footer_visibility", it) },
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
                enabled = !isHidden,
            )
            InkStepperSlider(
                value = prefs.header.marginTop,
                onValueChange = { onContinuousSettingChanged("header_margin_top", it, false) },
                onValueChangeFinished = { onContinuousSettingChanged("header_margin_top", it, true) },
                valueRange = 0f..100f,
                step = 4f,
                label = "页眉上边距",
                formatValue = { "%.0f dp".format(it) },
                testTagPrefix = "Slider_HeaderMarginTop",
                enabled = !isHidden,
            )
            InkStepperSlider(
                value = prefs.footer.marginBottom,
                onValueChange = { onContinuousSettingChanged("footer_margin_bottom", it, false) },
                onValueChangeFinished = { onContinuousSettingChanged("footer_margin_bottom", it, true) },
                valueRange = 0f..100f,
                step = 4f,
                label = "页脚下边距",
                formatValue = { "%.0f dp".format(it) },
                testTagPrefix = "Slider_FooterMarginBottom",
                topDivider = true,
                enabled = !isHidden,
            )
            InkStepperSlider(
                value = prefs.headerFooterAlpha,
                onValueChange = { onSettingChanged("header_footer_alpha", it) },
                valueRange = 0.1f..1.0f,
                step = 0.1f,
                label = "透明度",
                formatValue = { "%.0f%%".format(it * 100) },
                testTagPrefix = "Slider_HeaderFooterAlpha",
                topDivider = true,
                enabled = !isHidden,
            )
            DividerSwitchRow(
                label = "页眉分隔线",
                checked = prefs.showHeaderLine,
                onCheckedChange = { onSettingChanged("show_header_line", it) },
                topDivider = true,
                enabled = !isHidden,
            )
            DividerSwitchRow(
                label = "页脚分隔线",
                checked = prefs.showFooterLine,
                onCheckedChange = { onSettingChanged("show_footer_line", it) },
                enabled = !isHidden,
            )
        }

        // ── 色温 ──
        SettingsCard(title = "色温") {
            InkStepperSlider(
                value = prefs.colorTemperature,
                onValueChange = { onContinuousSettingChanged("color_temperature", it, false) },
                onValueChangeFinished = { finalValue ->
                    onContinuousSettingChanged("color_temperature", finalValue, true)
                },
                valueRange = 2000f..6500f,
                step = 100f,
                label = "色温",
                formatValue = { "%.0fK".format(it) },
                fillBrush = Brush.horizontalGradient(listOf(Color(0xFFFF8C00), colors.accent)),
                testTagPrefix = "Slider_ColorTemp",
            )
        }

        // ── 显示模式 ──
        SettingsCard(title = "显示模式") {
            SelectRow(
                label = "双页模式",
                options = listOf(
                    DualPageMode.AUTO to "自动",
                    DualPageMode.SINGLE to "单页",
                    DualPageMode.DUAL to "双页",
                ),
                selected = prefs.dualPageMode,
                onSelect = { onSettingChanged("dual_page_mode", it) },
            )
            SelectRow(
                label = "背景纹理",
                options = listOf(
                    "" to "纯色",
                    "kraft" to "Kraft 纸",
                    "linen" to "仿麻",
                    "grid" to "米格",
                ),
                selected = prefs.backgroundTexture ?: "",
                onSelect = { onSettingChanged("background_texture", it) },
                topDivider = true,
            )
            SelectRow(
                label = "翻页动画速度",
                options = listOf(
                    PageAnimSpeed.FAST to "快 (100ms)",
                    PageAnimSpeed.NORMAL to "标准 (250ms)",
                    PageAnimSpeed.SLOW to "慢 (400ms)",
                ),
                selected = prefs.pageAnimSpeed,
                onSelect = { onSettingChanged("page_anim_speed", it) },
                topDivider = true,
            )
            SelectRow(
                label = "翻页动画类型",
                options = listOf(
                    PageAnimType.HORIZONTAL to "水平滑动",
                    PageAnimType.COVER to "覆盖",
                    PageAnimType.SIMULATION to "仿真",
                    PageAnimType.SCROLL to "连续滚动",
                    PageAnimType.NONE to "无动画",
                ),
                selected = prefs.pageAnimType,
                onSelect = { onSettingChanged("page_anim_type", it) },
                topDivider = true,
            )
        }
    }
}

/**
 * 页眉/页脚可见性联动下拉菜单。
 *
 * 根据 header/footer 的当前 visibility 推导出一个统一的模式；
 * 切换时同步更新 header + footer 两条 visibility。
 *
 * 使用本地状态避免 header/footer 分别更新导致的 UI 闪烁。
 */
@Composable
private fun VisibilityDropdown(
    headerVisibility: HeaderVisibility,
    footerVisibility: HeaderVisibility,
    onHeaderChange: (HeaderVisibility) -> Unit,
    onFooterChange: (HeaderVisibility) -> Unit,
) {
    // 计算当前模式
    val derivedMode: HeaderVisibility = when {
        headerVisibility == HeaderVisibility.ALWAYS_HIDE &&
            footerVisibility == HeaderVisibility.ALWAYS_HIDE -> HeaderVisibility.ALWAYS_HIDE
        headerVisibility == HeaderVisibility.ALWAYS_SHOW &&
            footerVisibility == HeaderVisibility.ALWAYS_SHOW -> HeaderVisibility.ALWAYS_SHOW
        else -> HeaderVisibility.HIDE_WHEN_STATUS_BAR
    }

    // 本地状态，立即响应用户选择
    var selectedMode by remember { mutableStateOf(derivedMode) }

    // 同步外部状态变化
    LaunchedEffect(derivedMode) {
        selectedMode = derivedMode
    }

    SelectRow(
        label = "可见性",
        options = listOf(
            HeaderVisibility.ALWAYS_HIDE to "隐藏",
            HeaderVisibility.HIDE_WHEN_STATUS_BAR to "跟随状态栏",
            HeaderVisibility.ALWAYS_SHOW to "常驻",
        ),
        selected = selectedMode,
        onSelect = { mode ->
            selectedMode = mode  // 立即更新本地状态
            onHeaderChange(mode)
            onFooterChange(mode)
        },
    )
}

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
