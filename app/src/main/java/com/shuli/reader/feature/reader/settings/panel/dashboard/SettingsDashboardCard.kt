package com.shuli.reader.feature.reader.settings.panel.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 仪表盘卡片壳。
 *
 * 只处理外层形状、点击、语义、测试标签和导航箭头，不读取业务状态。
 */
@Composable
fun SettingsDashboardCard(
    title: String,
    destination: SettingsDetailDestination,
    contentDescription: String,
    onClick: (SettingsDetailDestination) -> Unit,
    modifier: Modifier = Modifier,
    showChevron: Boolean = true,
    minHeight: Dp = dashboardCardMinHeight(destination.span),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalReaderColorScheme.current
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(shape)
            .background(colors.divider.copy(alpha = 0.16f))
            .clickable(role = Role.Button) { onClick(destination) }
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription.ifBlank { title }
            }
            .padding(14.dp)
            .testTag("SettingsDashboardCard_${destination.name}"),
    ) {
        Column(content = content)
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp),
            )
        }
    }
}

@Stable
fun dashboardCardMinHeight(span: DashboardCardSpan): Dp = when (span) {
    DashboardCardSpan.Half -> 112.dp
    DashboardCardSpan.Full -> 124.dp
}
