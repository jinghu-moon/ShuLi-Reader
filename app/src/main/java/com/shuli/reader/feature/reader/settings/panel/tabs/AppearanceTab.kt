package com.shuli.reader.feature.reader.settings.panel.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CropPortrait
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material.icons.outlined.ViewHeadline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.data.DualPageMode
import com.shuli.reader.core.data.PageAnimSpeed
import com.shuli.reader.core.data.PageAnimType
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.core.reader.model.HeaderVisibility
import com.shuli.reader.feature.reader.settings.panel.SegmentedRow
import com.shuli.reader.feature.reader.settings.panel.SettingsCard
import com.shuli.reader.feature.reader.settings.panel.SlotMatrix
import com.shuli.reader.feature.reader.settings.panel.controls.InkStepperSlider
import com.shuli.reader.feature.reader.settings.panel.controls.SegmentedControl
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
            SegmentedRow(
                label = strings.dualPageModeLabel,
                options = listOf(
                    DualPageMode.SINGLE to strings.singlePageLabel,
                    DualPageMode.DUAL to strings.dualPageLabel,
                    DualPageMode.AUTO to strings.autoLabel,
                ),
                selected = prefs.dualPageMode,
                onSelect = { onSettingChanged("dual_page_mode", it) },
                icons = listOf(
                    Icons.Outlined.CropPortrait,
                    Icons.Outlined.ViewColumn,
                    Icons.Outlined.AutoAwesome,
                ),
            )
            TextureSwatchRow(
                label = strings.backgroundTextureLabel,
                options = listOf(
                    TextureOption("", strings.solidColorLabel),
                    TextureOption("kraft", "Kraft 纸"),
                    TextureOption("linen", strings.linenTextureLabel),
                    TextureOption("grid", strings.gridTextureLabel),
                ),
                selected = prefs.backgroundTexture ?: "",
                onSelect = { onSettingChanged("background_texture", it) },
                topDivider = true,
            )
            SegmentedRow(
                label = strings.pageAnimSpeedLabel,
                options = listOf(
                    PageAnimSpeed.SLOW to strings.pageAnimSpeedSlow,
                    PageAnimSpeed.NORMAL to strings.pageAnimSpeedNormal,
                    PageAnimSpeed.FAST to strings.pageAnimSpeedFast,
                ),
                selected = prefs.pageAnimSpeed,
                onSelect = { onSettingChanged("page_anim_speed", it) },
                topDivider = true,
            )
            PageAnimTypeSegmentedRow(
                label = strings.pageAnimTypeLabel,
                options = listOf(
                    PageAnimOption(
                        PageAnimType.HORIZONTAL,
                        strings.pageAnimTypeHorizontal,
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    ),
                    PageAnimOption(
                        PageAnimType.COVER,
                        strings.pageAnimOverlay,
                        Icons.Outlined.ViewHeadline,
                    ),
                    PageAnimOption(
                        PageAnimType.SIMULATION,
                        strings.pageAnimSimulation,
                        Icons.Outlined.AutoAwesome,
                    ),
                    PageAnimOption(
                        PageAnimType.VERTICAL_SLIDE,
                        strings.pageAnimTypeVerticalSlide,
                        Icons.Outlined.KeyboardArrowDown,
                    ),
                    PageAnimOption(
                        PageAnimType.SCROLL,
                        strings.pageAnimTypeScroll,
                        Icons.AutoMirrored.Outlined.ViewList,
                    ),
                    PageAnimOption(
                        PageAnimType.NONE,
                        strings.pageAnimNone,
                        Icons.Outlined.Block,
                    ),
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

    SegmentedRow(
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
        icons = listOf(
            Icons.Outlined.VisibilityOff,
            Icons.Outlined.Visibility,
            Icons.Outlined.Visibility,
        ),
    )
}

private data class TextureOption(
    val value: String,
    val label: String,
)

private data class PageAnimOption(
    val value: PageAnimType,
    val label: String,
    val icon: ImageVector,
)

@Composable
private fun PageAnimTypeSegmentedRow(
    label: String,
    options: List<PageAnimOption>,
    selected: PageAnimType,
    onSelect: (PageAnimType) -> Unit,
    modifier: Modifier = Modifier,
    topDivider: Boolean = false,
) {
    val colors = LocalReaderColorScheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("PageAnimTypeSegmentedRow_$label"),
    ) {
        if (topDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.divider),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            options.chunked(3).forEach { rowOptions ->
                SegmentedControl(
                    options = rowOptions.map { it.label },
                    selectedIndex = rowOptions.indexOfFirst { it.value == selected },
                    onSelectedChange = { index ->
                        rowOptions.getOrNull(index)?.value?.let(onSelect)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    equalWidth = true,
                    icons = rowOptions.map { it.icon },
                    activeColor = colors.accent,
                    activeTextColor = colors.background,
                    inactiveTextColor = colors.textSecondary,
                    containerColor = colors.divider.copy(alpha = 0.3f),
                )
            }
        }
    }
}

@Composable
private fun TextureSwatchRow(
    label: String,
    options: List<TextureOption>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    topDivider: Boolean = false,
) {
    val colors = LocalReaderColorScheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("TextureSwatchRow_$label"),
    ) {
        if (topDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.divider),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { option ->
                    TextureSwatch(
                        option = option,
                        selected = selected == option.value,
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TextureSwatch(
    option: TextureOption,
    selected: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalReaderColorScheme.current
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(shape)
                .clickable { onSelect(option.value) }
                .border(
                    BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.divider),
                    shape,
                )
                .testTag("TextureSwatch_${option.value.ifBlank { "solid" }}"),
        ) {
            TexturePattern(option.value, Modifier.matchParentSize())
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(colors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "已选中",
                        tint = colors.background,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
        Text(
            text = option.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.accent else colors.textSecondary,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TexturePattern(
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalReaderColorScheme.current
    Canvas(modifier = modifier) {
        val base = when (value) {
            "kraft" -> Color(0xFFE7D2AA)
            "linen" -> Color(0xFFEAE5DC)
            "grid" -> Color(0xFFF6F4F0)
            else -> colors.background
        }
        drawRect(base)
        when (value) {
            "kraft" -> {
                val speck = Color(0xFF9C7344).copy(alpha = 0.28f)
                for (x in 6 until size.width.toInt() step 14) {
                    val y = ((x * 17) % size.height.toInt().coerceAtLeast(1)).toFloat()
                    drawCircle(speck, radius = 1.2f, center = Offset(x.toFloat(), y))
                }
            }
            "linen" -> {
                val line = Color(0xFF8E8170).copy(alpha = 0.18f)
                for (x in 0..size.width.toInt() step 8) {
                    drawLine(line, Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height), strokeWidth = 1f)
                }
                for (y in 0..size.height.toInt() step 9) {
                    drawLine(line, Offset(0f, y.toFloat()), Offset(size.width, y.toFloat()), strokeWidth = 1f)
                }
            }
            "grid" -> {
                val line = colors.divider.copy(alpha = 0.55f)
                for (x in 0..size.width.toInt() step 12) {
                    drawLine(line, Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height), strokeWidth = 1f)
                }
                for (y in 0..size.height.toInt() step 12) {
                    drawLine(line, Offset(0f, y.toFloat()), Offset(size.width, y.toFloat()), strokeWidth = 1f)
                }
            }
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
