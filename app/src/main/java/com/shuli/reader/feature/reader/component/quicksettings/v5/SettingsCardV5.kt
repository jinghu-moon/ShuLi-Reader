package com.shuli.reader.feature.reader.component.quicksettings.v5

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 标签 / 数值列宽度对齐状态。
 *
 * 各 InkStepperSlider 通过 [onReport] / [onReportValue] 上报自身标签和右侧数值的自然宽度，
 * [alignedWidth] / [alignedValueWidth] 自动取所有上报值的最大值，让同卡片内多个 slider
 * 的标签列和数值列分别对齐。
 */
@Stable
class LabelWidthState internal constructor() {
    private val reportedLabelWidths = mutableStateMapOf<String, Dp>()
    private val reportedValueWidths = mutableStateMapOf<String, Dp>()

    val alignedWidth: Dp
        get() = reportedLabelWidths.values.maxOrNull() ?: Dp.Unspecified

    val alignedValueWidth: Dp
        get() = reportedValueWidths.values.maxOrNull() ?: Dp.Unspecified

    fun onReport(key: String, width: Dp) {
        val current = reportedLabelWidths[key]
        if (current == null || width > current) {
            reportedLabelWidths[key] = width
        }
    }

    fun onReportValue(key: String, width: Dp) {
        val current = reportedValueWidths[key]
        if (current == null || width > current) {
            reportedValueWidths[key] = width
        }
    }
}

/**
 * 提供 [LabelWidthState]，由 SettingsCard 注入，InkStepperSlider 消费。
 */
val LocalLabelWidthState = staticCompositionLocalOf<LabelWidthState?> { null }

/**
 * 设置卡片容器（对应原型 .settings-card）。
 *
 * 描边圆角卡片 + 大写字距标题。默认静态展开（[collapsible] = false）；
 * 高级分组传 [collapsible] = true 时标题可点击折叠，附旋转箭头。
 *
 * 内容区通过 [LabelWidthState] 自动对齐所有 InkStepperSlider 的标签列宽度。
 */
@Composable
fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    collapsible: Boolean = false,
    initiallyExpanded: Boolean = true,
    testTag: String = "SettingsCard_$title",
    headerTrailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalReaderColorScheme.current
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val showContent = !collapsible || expanded
    val labelWidthState = remember { LabelWidthState() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(BorderStroke(1.dp, colors.divider), RoundedCornerShape(10.dp))
            .padding(14.dp)
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (collapsible) it.clickable { expanded = !expanded } else it },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                letterSpacing = 0.06.em,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (headerTrailing != null) {
                    Box { headerTrailing() }
                }
                if (collapsible) {
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = colors.textTertiary,
                        modifier = Modifier
                            .rotate(if (expanded) 180f else 0f)
                            .testTag("${testTag}_Toggle"),
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = showContent,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalLabelWidthState provides labelWidthState,
                ) {
                    content()
                }
            }
        }
    }
}
