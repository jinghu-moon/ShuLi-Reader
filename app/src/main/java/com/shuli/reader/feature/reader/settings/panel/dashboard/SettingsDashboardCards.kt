package com.shuli.reader.feature.reader.settings.panel.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shuli.reader.core.reader.model.TitleAlign
import com.shuli.reader.ui.theme.LocalReaderColorScheme
import kotlin.math.max

@Composable
internal fun FontDashboardCard(
    title: String,
    summary: FontCardSummary,
    onOpenDetail: (SettingsDetailDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsDashboardCard(
        title = title,
        destination = SettingsDetailDestination.Font,
        contentDescription = summary.contentDescription,
        onClick = onOpenDetail,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "永",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = LocalReaderColorScheme.current.textPrimary,
                modifier = Modifier.width(40.dp),
            )
            Column(modifier = Modifier.padding(end = 14.dp)) {
                Text(
                    text = summary.fontName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = LocalReaderColorScheme.current.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summary.weightName,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalReaderColorScheme.current.textTertiary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun BodyTypographyDashboardCard(
    title: String,
    summary: BodyTypographySummary,
    onOpenDetail: (SettingsDetailDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsDashboardCard(
        title = title,
        destination = SettingsDetailDestination.BodyTypography,
        contentDescription = summary.contentDescription,
        onClick = onOpenDetail,
        modifier = modifier,
    ) {
        DashboardTitle(title)
        Spacer(Modifier.height(10.dp))
        BodyLinePreview(vertical = summary.verticalText)
        Spacer(Modifier.height(8.dp))
        DashboardValue(
            text = "${summary.fontSizeSp}sp · 行${formatCompact(summary.lineSpacing)} · 段${formatCompact(summary.paragraphSpacing)}",
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TextProcessingDashboardCard(
    title: String,
    summary: TextProcessingSummary,
    onOpenDetail: (SettingsDetailDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsDashboardCard(
        title = title,
        destination = SettingsDetailDestination.TextProcessing,
        contentDescription = summary.contentDescription,
        onClick = onOpenDetail,
        modifier = modifier,
    ) {
        DashboardTitle(title, prominent = true)
        Spacer(Modifier.height(12.dp))
        if (summary.chips.isEmpty()) {
            Text(
                text = summary.emptyLabel,
                style = MaterialTheme.typography.bodySmall,
                color = LocalReaderColorScheme.current.textTertiary,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                summary.chips.forEach { ChipLabel(it) }
                if (summary.hiddenCount > 0) ChipLabel("+${summary.hiddenCount}")
            }
        }
    }
}

@Composable
internal fun BodyAreaDashboardCard(
    title: String,
    summary: BodyAreaSummary,
    onOpenDetail: (SettingsDetailDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsDashboardCard(
        title = title,
        destination = SettingsDetailDestination.BodyArea,
        contentDescription = summary.contentDescription,
        onClick = onOpenDetail,
        modifier = modifier,
    ) {
        DashboardTitle(title)
        Spacer(Modifier.height(8.dp))
        BodyAreaPreview(summary = summary, modifier = Modifier.fillMaxWidth().height(56.dp))
        Spacer(Modifier.height(6.dp))
        DashboardValue("${summary.top}/${summary.bottom} · ${summary.left}/${summary.right}")
    }
}

@Composable
internal fun TitleStyleDashboardCard(
    title: String,
    summary: TitleStyleSummary,
    onOpenDetail: (SettingsDetailDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsDashboardCard(
        title = title,
        destination = SettingsDetailDestination.TitleStyle,
        contentDescription = summary.contentDescription,
        onClick = onOpenDetail,
        modifier = modifier,
    ) {
        DashboardTitle(title)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "标题文本",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = LocalReaderColorScheme.current.textPrimary,
            textAlign = summary.align.toComposeTextAlign(),
            modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
        )
        MiniLines(lineCount = 2)
        Spacer(Modifier.height(6.dp))
        DashboardValue("+${summary.sizeOffsetSp}sp · ${summary.alignLabel}")
    }
}

@Composable
internal fun HeaderFooterDashboardCard(
    title: String,
    summary: HeaderFooterSummary,
    onOpenDetail: (SettingsDetailDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsDashboardCard(
        title = title,
        destination = SettingsDetailDestination.HeaderFooter,
        contentDescription = summary.contentDescription,
        onClick = onOpenDetail,
        modifier = modifier,
    ) {
        DashboardTitle(title, prominent = true)
        Spacer(Modifier.height(12.dp))
        HeaderFooterRow("页眉", summary.headerSlots, summary.headerVisible)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(1.dp)
                .background(LocalReaderColorScheme.current.divider),
        )
        HeaderFooterRow("页脚", summary.footerSlots, summary.footerVisible)
    }
}

@Composable
internal fun MarginPresetDashboardCard(
    title: String,
    summary: MarginPresetSummary,
    onOpenDetail: (SettingsDetailDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsDashboardCard(
        title = title,
        destination = SettingsDetailDestination.MarginPreset,
        contentDescription = summary.contentDescription,
        onClick = onOpenDetail,
        modifier = modifier,
    ) {
        DashboardTitle(title)
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (summary.exactMatch) {
                        LocalReaderColorScheme.current.accent.copy(alpha = 0.16f)
                    } else {
                        Color.Transparent
                    },
                )
                .then(
                    if (summary.exactMatch) Modifier else Modifier.background(LocalReaderColorScheme.current.divider.copy(alpha = 0.12f))
                )
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = summary.label,
                style = MaterialTheme.typography.titleMedium,
                color = if (summary.exactMatch) {
                    LocalReaderColorScheme.current.accent
                } else {
                    LocalReaderColorScheme.current.textSecondary
                },
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun PageTurnMethodDashboardCard(
    title: String,
    summary: PageTurnMethodSummary,
    onOpenDetail: (SettingsDetailDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsDashboardCard(
        title = title,
        destination = SettingsDetailDestination.PageTurnMethod,
        contentDescription = summary.contentDescription,
        onClick = onOpenDetail,
        modifier = modifier,
    ) {
        DashboardTitle(title)
        Spacer(Modifier.height(10.dp))
        if (summary.items.isEmpty()) {
            DashboardValue(summary.emptyLabel)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                summary.items.take(3).forEach { StatusLineText(it) }
            }
        }
    }
}

@Composable
internal fun TouchZoneDashboardCard(
    title: String,
    summary: TouchZoneSummary,
    onOpenDetail: (SettingsDetailDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsDashboardCard(
        title = title,
        destination = SettingsDetailDestination.TouchZone,
        contentDescription = summary.contentDescription,
        onClick = onOpenDetail,
        modifier = modifier,
    ) {
        DashboardTitle(title)
        Spacer(Modifier.height(8.dp))
        TouchZonePreview(summary, modifier = Modifier.fillMaxWidth().height(54.dp))
        Spacer(Modifier.height(6.dp))
        DashboardValue("触觉反馈 ${if (summary.hapticEnabled) "✓" else "×"}")
    }
}

@Composable
internal fun PageTurnAnimationDashboardCard(
    title: String,
    summary: PageTurnAnimationSummary,
    onOpenDetail: (SettingsDetailDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsDashboardCard(
        title = title,
        destination = SettingsDetailDestination.PageTurnAnimation,
        contentDescription = summary.contentDescription,
        onClick = onOpenDetail,
        modifier = modifier,
    ) {
        DashboardTitle(title, prominent = true)
        Spacer(Modifier.height(12.dp))
        Text(
            text = summary.typeLabel,
            style = MaterialTheme.typography.titleMedium,
            color = LocalReaderColorScheme.current.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        if (summary.speedProgress > 0f) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "速度",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalReaderColorScheme.current.textTertiary,
                )
                Spacer(Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = { summary.speedProgress },
                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(99.dp)),
                    color = LocalReaderColorScheme.current.accent,
                    trackColor = LocalReaderColorScheme.current.divider.copy(alpha = 0.4f),
                )
                Spacer(Modifier.width(8.dp))
                DashboardValue(summary.speedLabel)
            }
        }
    }
}

@Composable
internal fun EyeCareDashboardCard(
    title: String,
    summary: EyeCareSummary,
    onOpenDetail: (SettingsDetailDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsDashboardCard(
        title = title,
        destination = SettingsDetailDestination.EyeCare,
        contentDescription = summary.contentDescription,
        onClick = onOpenDetail,
        modifier = modifier,
    ) {
        DashboardTitle(title)
        Spacer(Modifier.height(8.dp))
        EyeCareArc(summary, modifier = Modifier.align(Alignment.CenterHorizontally).size(56.dp))
        Spacer(Modifier.height(4.dp))
        DashboardValue(summary.reminderLabel?.let { "提醒 $it" } ?: if (summary.enabled) "${summary.colorTemperatureK}K" else "未开启")
    }
}

@Composable
internal fun ScreenStateDashboardCard(
    title: String,
    summary: ScreenStateSummary,
    onOpenDetail: (SettingsDetailDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsDashboardCard(
        title = title,
        destination = SettingsDetailDestination.ScreenState,
        contentDescription = summary.contentDescription,
        onClick = onOpenDetail,
        modifier = modifier,
    ) {
        DashboardTitle(title)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            summary.items.forEach { StatusLineText(it) }
        }
    }
}

@Composable
internal fun ReadingFormDashboardCard(
    title: String,
    summary: ReadingFormSummary,
    onOpenDetail: (SettingsDetailDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsDashboardCard(
        title = title,
        destination = SettingsDetailDestination.ReadingForm,
        contentDescription = summary.contentDescription,
        onClick = onOpenDetail,
        modifier = modifier,
    ) {
        DashboardTitle(title, prominent = true)
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ReadingFormPreview(summary, modifier = Modifier.width(112.dp).height(56.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DashboardValue("纹理：${summary.textureLabel}")
                DashboardValue("模式：${summary.modeLabel}")
                DashboardValue(if (summary.verticalText) "竖排" else "横排")
            }
        }
    }
}

@Composable
private fun DashboardTitle(text: String, prominent: Boolean = false) {
    Text(
        text = text,
        style = if (prominent) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelSmall,
        color = if (prominent) LocalReaderColorScheme.current.textPrimary else LocalReaderColorScheme.current.textTertiary,
        fontWeight = if (prominent) FontWeight.Medium else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(end = 18.dp),
    )
}

@Composable
private fun DashboardValue(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = LocalReaderColorScheme.current.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ChipLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = LocalReaderColorScheme.current.accent,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(LocalReaderColorScheme.current.accent.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

@Composable
private fun StatusLineText(item: StatusLine) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (item.enabled) LocalReaderColorScheme.current.textPrimary else LocalReaderColorScheme.current.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (item.value.isNotBlank()) {
            Text(
                text = item.value,
                style = MaterialTheme.typography.labelSmall,
                color = if (item.enabled) LocalReaderColorScheme.current.accent else LocalReaderColorScheme.current.textTertiary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BodyLinePreview(vertical: Boolean) {
    val colors = LocalReaderColorScheme.current
    Canvas(modifier = Modifier.fillMaxWidth().height(42.dp)) {
        val stroke = 2.dp.toPx()
        val lineColor = colors.textTertiary.copy(alpha = 0.35f)
        if (vertical) {
            val gap = size.width / 5f
            repeat(4) { index ->
                val x = gap * (index + 1)
                drawLine(lineColor, Offset(x, 2.dp.toPx()), Offset(x, size.height - 2.dp.toPx()), strokeWidth = stroke)
            }
        } else {
            val yGap = size.height / 4f
            repeat(3) { index ->
                val y = yGap * (index + 1)
                val end = size.width * when (index) {
                    0 -> 0.88f
                    1 -> 0.96f
                    else -> 0.62f
                }
                drawLine(lineColor, Offset(0f, y), Offset(end, y), strokeWidth = stroke)
            }
        }
    }
}

@Composable
private fun MiniLines(lineCount: Int) {
    val colors = LocalReaderColorScheme.current
    Canvas(modifier = Modifier.fillMaxWidth().height((lineCount * 12).dp).padding(end = 16.dp)) {
        repeat(lineCount) { index ->
            val y = 7.dp.toPx() + index * 11.dp.toPx()
            val end = size.width * if (index == 0) 0.9f else 0.68f
            drawLine(
                color = colors.textTertiary.copy(alpha = 0.28f),
                start = Offset(0f, y),
                end = Offset(end, y),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }
}

@Composable
private fun BodyAreaPreview(summary: BodyAreaSummary, modifier: Modifier = Modifier) {
    val colors = LocalReaderColorScheme.current
    Canvas(modifier = modifier) {
        val outerWidth = size.width * 0.62f
        val outerHeight = size.height
        val outerLeft = (size.width - outerWidth) / 2f
        val outerTop = 0f
        val maxMargin = 96f
        val left = (summary.left / maxMargin).coerceIn(0f, 1f) * outerWidth * 0.28f
        val right = (summary.right / maxMargin).coerceIn(0f, 1f) * outerWidth * 0.28f
        val top = (summary.top / maxMargin).coerceIn(0f, 1f) * outerHeight * 0.28f
        val bottom = (summary.bottom / maxMargin).coerceIn(0f, 1f) * outerHeight * 0.28f
        drawRoundRect(
            color = colors.divider,
            topLeft = Offset(outerLeft, outerTop),
            size = Size(outerWidth, outerHeight),
            cornerRadius = CornerRadius(7.dp.toPx()),
            style = Stroke(width = 1.dp.toPx()),
        )
        drawRoundRect(
            color = colors.accent.copy(alpha = 0.13f),
            topLeft = Offset(outerLeft + left, outerTop + top),
            size = Size(max(outerWidth - left - right, 8.dp.toPx()), max(outerHeight - top - bottom, 8.dp.toPx())),
            cornerRadius = CornerRadius(5.dp.toPx()),
        )
    }
}

@Composable
private fun HeaderFooterRow(label: String, slots: List<String>, visible: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = LocalReaderColorScheme.current.textTertiary,
            textDecoration = if (visible) null else TextDecoration.LineThrough,
            modifier = Modifier.width(34.dp),
        )
        slots.forEach { slot ->
            MiniSlotChip(label = slot, visible = visible, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniSlotChip(label: String, visible: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalReaderColorScheme.current
    val isBlank = label == "留白"
    Box(
        modifier = modifier
            .heightIn(min = 26.dp)
            .clip(RoundedCornerShape(7.dp))
            .then(
                if (isBlank) {
                    Modifier
                } else {
                    Modifier.background(colors.divider.copy(alpha = if (visible) 0.18f else 0.08f))
                },
            )
            .padding(horizontal = 5.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isBlank) "无" else label,
            style = MaterialTheme.typography.labelSmall,
            color = if (visible && !isBlank) colors.textSecondary else colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TouchZonePreview(summary: TouchZoneSummary, modifier: Modifier = Modifier) {
    val colors = LocalReaderColorScheme.current
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val frameWidth = size.width * 0.72f
            val frameLeft = (size.width - frameWidth) / 2f
            val leftWidth = (frameWidth * summary.leftPercent / 100f).coerceAtLeast(14.dp.toPx())
            val rightWidth = (frameWidth * summary.rightPercent / 100f).coerceAtLeast(14.dp.toPx())
            val centerWidth = (frameWidth - leftWidth - rightWidth).coerceAtLeast(18.dp.toPx())
            drawRoundRect(
                color = colors.divider,
                topLeft = Offset(frameLeft, 0f),
                size = Size(frameWidth, size.height),
                cornerRadius = CornerRadius(7.dp.toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )
            drawRect(colors.accent.copy(alpha = 0.13f), Offset(frameLeft, 0f), Size(leftWidth, size.height))
            drawRect(colors.accent.copy(alpha = 0.13f), Offset(frameLeft + leftWidth + centerWidth, 0f), Size(rightWidth, size.height))
            drawLine(colors.divider, Offset(frameLeft + leftWidth, 0f), Offset(frameLeft + leftWidth, size.height), strokeWidth = 1.dp.toPx())
            drawLine(colors.divider, Offset(frameLeft + leftWidth + centerWidth, 0f), Offset(frameLeft + leftWidth + centerWidth, size.height), strokeWidth = 1.dp.toPx())
        }
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${summary.leftPercent}", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            Spacer(Modifier.weight(1f))
            Text("${summary.centerPercent}", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
            Spacer(Modifier.weight(1f))
            Text("${summary.rightPercent}", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
        }
    }
}

@Composable
private fun EyeCareArc(summary: EyeCareSummary, modifier: Modifier = Modifier) {
    val colors = LocalReaderColorScheme.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = 6.dp.toPx()
            drawArc(
                color = colors.divider.copy(alpha = 0.35f),
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = Color(0xFFFF8C00).copy(alpha = if (summary.enabled) 0.75f else 0.2f),
                startAngle = 150f,
                sweepAngle = 240f * summary.warmthPercent / 100f,
                useCenter = false,
                style = Stroke(width = stroke),
            )
        }
        Text(
            text = if (summary.enabled) "${summary.warmthPercent}%" else "0%",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun ReadingFormPreview(summary: ReadingFormSummary, modifier: Modifier = Modifier) {
    val colors = LocalReaderColorScheme.current
    val pageColor = when (summary.textureLabel) {
        "Kraft" -> Color(0xFFD7B982)
        else -> colors.background
    }
    Canvas(modifier = modifier) {
        val dual = summary.dualPageMode != com.shuli.reader.core.data.DualPageMode.SINGLE
        val gap = 8.dp.toPx()
        val pageWidth = if (dual) (size.width - gap) / 2f else size.width * 0.58f
        val startX = if (dual) 0f else (size.width - pageWidth) / 2f
        val count = if (dual) 2 else 1
        repeat(count) { index ->
            val x = startX + index * (pageWidth + gap)
            drawRoundRect(
                color = pageColor,
                topLeft = Offset(x, 0f),
                size = Size(pageWidth, size.height),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
            drawRoundRect(
                color = colors.divider,
                topLeft = Offset(x, 0f),
                size = Size(pageWidth, size.height),
                cornerRadius = CornerRadius(6.dp.toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )
            repeat(3) { line ->
                val y = 12.dp.toPx() + line * 11.dp.toPx()
                drawLine(
                    color = colors.textTertiary.copy(alpha = 0.25f),
                    start = Offset(x + 8.dp.toPx(), y),
                    end = Offset(x + pageWidth - 8.dp.toPx(), y),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }
    }
}

private fun TitleAlign.toComposeTextAlign(): TextAlign = when (this) {
    TitleAlign.LEFT -> TextAlign.Left
    TitleAlign.CENTER -> TextAlign.Center
    TitleAlign.RIGHT -> TextAlign.Right
}

private fun formatCompact(value: Float): String {
    val rounded = (value * 10).toInt() / 10f
    return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
}
