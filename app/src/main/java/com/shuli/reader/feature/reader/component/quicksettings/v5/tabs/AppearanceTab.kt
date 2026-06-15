package com.shuli.reader.feature.reader.component.quicksettings.v5.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shuli.reader.core.data.DualPageMode
import com.shuli.reader.core.data.PageAnimSpeed
import com.shuli.reader.core.data.PageAnimType
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.reader.HeaderVisibility
import com.shuli.reader.feature.reader.component.quicksettings.v5.DividerSwitchRow
import com.shuli.reader.feature.reader.component.quicksettings.v5.SelectRow
import com.shuli.reader.feature.reader.component.quicksettings.v5.SettingsCard
import com.shuli.reader.feature.reader.component.quicksettings.v5.SlotMatrix
import com.shuli.reader.feature.reader.component.quicksettings.v5.SwitchRow
import com.shuli.reader.feature.reader.component.quicksettings.v5.controls.InkStepperSlider
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
    Column(modifier = modifier.fillMaxWidth()) {
        // ── 页眉页脚 ──
        SettingsCard(
            title = "页眉页脚",
            headerTrailing = {
                VisibilitySegmented(
                    headerVisibility = prefs.header.visibility,
                    footerVisibility = prefs.footer.visibility,
                    onHeaderChange = { onSettingChanged("header_visibility", it) },
                    onFooterChange = { onSettingChanged("footer_visibility", it) },
                )
            },
        ) {
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
            InkStepperSlider(
                value = prefs.headerFooterAlpha,
                onValueChange = { onSettingChanged("header_footer_alpha", it) },
                valueRange = 0.1f..1.0f,
                step = 0.1f,
                label = "透明度",
                formatValue = { "%.0f%%".format(it * 100) },
                testTagPrefix = "Slider_HeaderFooterAlpha",
            )
            DividerSwitchRow(
                label = "页眉分隔线",
                checked = prefs.showHeaderLine,
                onCheckedChange = { onSettingChanged("show_header_line", it) },
                topDivider = true,
            )
            DividerSwitchRow(
                label = "页脚分隔线",
                checked = prefs.showFooterLine,
                onCheckedChange = { onSettingChanged("show_footer_line", it) },
                topDivider = true,
            )
        }

        // ── 屏幕与亮度 ──
        SettingsCard(title = "屏幕与亮度") {
            InkStepperSlider(
                value = if (prefs.brightness < 0f) 0.5f else prefs.brightness,
                onValueChange = { onContinuousSettingChanged("brightness", it, false) },
                onValueChangeFinished = { finalValue ->
                    onContinuousSettingChanged("brightness", finalValue, true)
                },
                valueRange = 0.01f..1f,
                step = 0.05f,
                label = "屏幕亮度",
                formatValue = { "%.0f%%".format(it * 100) },
                testTagPrefix = "Slider_Brightness",
            )
            InkStepperSlider(
                value = prefs.colorTemperature,
                onValueChange = { onContinuousSettingChanged("color_temperature", it, false) },
                onValueChangeFinished = { finalValue ->
                    onContinuousSettingChanged("color_temperature", finalValue, true)
                },
                valueRange = 2000f..6500f,
                step = 100f,
                label = "色温",
                sublabel = "降低蓝光 · 6500K 关闭",
                formatValue = { "%.0fK".format(it) },
                fillBrush = Brush.horizontalGradient(listOf(Color(0xFFFF8C00), colors.accent)),
                testTagPrefix = "Slider_ColorTemp",
            )
            SwitchRow(
                label = "阅读聚焦线",
                checked = prefs.focusLine,
                onCheckedChange = { onSettingChanged("focus_line", it) },
                topDivider = true,
            )
            SwitchRow(
                label = "自动夜间模式",
                checked = prefs.autoNightMode,
                onCheckedChange = { onSettingChanged("auto_night_mode", it) },
                topDivider = true,
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
 * 页眉/页脚可见性联动分段控件。
 *
 * 根据 header/footer 的当前 visibility 推导出一个统一的模式（隐藏/自动/常驻）；
 * 切换时同步更新 header + footer 两条 visibility。
 */
@Composable
private fun VisibilitySegmented(
    headerVisibility: HeaderVisibility,
    footerVisibility: HeaderVisibility,
    onHeaderChange: (HeaderVisibility) -> Unit,
    onFooterChange: (HeaderVisibility) -> Unit,
) {
    val colors = LocalReaderColorScheme.current
    val currentMode: HeaderVisibility = when {
        headerVisibility == HeaderVisibility.ALWAYS_HIDE &&
            footerVisibility == HeaderVisibility.ALWAYS_HIDE -> HeaderVisibility.ALWAYS_HIDE
        headerVisibility == HeaderVisibility.ALWAYS_SHOW &&
            footerVisibility == HeaderVisibility.ALWAYS_SHOW -> HeaderVisibility.ALWAYS_SHOW
        else -> HeaderVisibility.HIDE_WHEN_STATUS_BAR
    }

    val segments = listOf(
        HeaderVisibility.ALWAYS_HIDE to "隐藏",
        HeaderVisibility.HIDE_WHEN_STATUS_BAR to "自动",
        HeaderVisibility.ALWAYS_SHOW to "常驻",
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.divider.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        segments.forEach { (mode, label) ->
            val selected = mode == currentMode
            val bgColor = if (selected) colors.accent else Color.Transparent
            val fgColor = if (selected) colors.surface else colors.textSecondary
            Text(
                text = label,
                fontSize = 11.sp,
                color = fgColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(bgColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        onHeaderChange(mode)
                        onFooterChange(mode)
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
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
