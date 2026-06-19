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
import com.shuli.reader.core.reader.model.BoxInsetsDp
import com.shuli.reader.core.i18n.LocalAppStrings
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
    val strings = LocalAppStrings.current.reader
    val isHidden = prefs.header.visibility == HeaderVisibility.ALWAYS_HIDE &&
        prefs.footer.visibility == HeaderVisibility.ALWAYS_HIDE

    Column(modifier = modifier.fillMaxWidth()) {
        // ── 页眉页脚 ──
        SettingsCard(title = strings.headerFooterCard) {
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
        }

        // ── 色温 ──
        SettingsCard(title = strings.colorTemperatureLabel) {
            InkStepperSlider(
                value = prefs.colorTemperature,
                onValueChange = { onContinuousSettingChanged("color_temperature", it, false) },
                onValueChangeFinished = { finalValue ->
                    onContinuousSettingChanged("color_temperature", finalValue, true)
                },
                valueRange = 2000f..6500f,
                step = 100f,
                label = strings.colorTemperatureLabel,
                formatValue = { "%.0fK".format(it) },
                fillBrush = Brush.horizontalGradient(listOf(Color(0xFFFF8C00), colors.accent)),
                testTagPrefix = "Slider_ColorTemp",
            )
        }

        // ── 显示模式 ──
        SettingsCard(title = strings.displayModeCard) {
            SelectRow(
                label = strings.dualPageModeLabel,
                options = listOf(
                    DualPageMode.AUTO to strings.autoLabel,
                    DualPageMode.SINGLE to strings.singlePageLabel,
                    DualPageMode.DUAL to strings.dualPageLabel,
                ),
                selected = prefs.dualPageMode,
                onSelect = { onSettingChanged("dual_page_mode", it) },
            )
            SelectRow(
                label = strings.backgroundTextureLabel,
                options = listOf(
                    "" to strings.solidColorLabel,
                    "kraft" to "Kraft 纸",
                    "linen" to strings.linenTextureLabel,
                    "grid" to strings.gridTextureLabel,
                ),
                selected = prefs.backgroundTexture ?: "",
                onSelect = { onSettingChanged("background_texture", it) },
                topDivider = true,
            )
            SelectRow(
                label = strings.pageAnimSpeedLabel,
                options = listOf(
                    PageAnimSpeed.FAST to strings.pageAnimSpeedFast,
                    PageAnimSpeed.NORMAL to strings.pageAnimSpeedNormal,
                    PageAnimSpeed.SLOW to strings.pageAnimSpeedSlow,
                ),
                selected = prefs.pageAnimSpeed,
                onSelect = { onSettingChanged("page_anim_speed", it) },
                topDivider = true,
            )
            SelectRow(
                label = strings.pageAnimTypeLabel,
                options = listOf(
                    PageAnimType.HORIZONTAL to strings.pageAnimTypeHorizontal,
                    PageAnimType.COVER to strings.pageAnimOverlay,
                    PageAnimType.SIMULATION to strings.pageAnimSimulation,
                    PageAnimType.SCROLL to strings.pageAnimTypeScroll,
                    PageAnimType.NONE to strings.pageAnimNone,
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
    val strings = LocalAppStrings.current.reader

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
        label = strings.visibilityLabel,
        options = listOf(
            HeaderVisibility.ALWAYS_HIDE to strings.displayAlwaysHide,
            HeaderVisibility.HIDE_WHEN_STATUS_BAR to strings.displayFollowStatusBar,
            HeaderVisibility.ALWAYS_SHOW to strings.displayAlwaysShowShort,
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
