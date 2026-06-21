package com.shuli.reader.feature.reader.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 模块 C: 全书查找进度侧边栏
 *
 * 参考 edit-interface-demo.html 设计：
 * - 右侧抽屉 260dp
 * - 头部：标题 + 关闭按钮 + 进度条
 * - 章节列表：章节标题 + 匹配数/Loading
 */
@Composable
fun SearchProgressSidebar(
    visible: Boolean,
    chapterMatchCounts: Map<Int, Int?>,
    chapterTitles: List<String>,
    scanProgress: Float,
    totalMatches: Int,
    onClose: () -> Unit,
    onChapterClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = EditorTokens

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(tween(300)) { it },
        exit = slideOutHorizontally(tween(300)) { it },
    ) {
        Surface(
            modifier = modifier
                .width(tokens.SidebarWidth)
                .fillMaxHeight(),
            color = tokens.Surface,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.fillMaxHeight()) {
                // 头部
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "全书匹配 ($totalMatches)",
                        fontSize = tokens.SidebarTitleFontSize,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.TextPrimary,
                    )
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "关闭",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // 进度条
                LinearProgressIndicator(
                    progress = { scanProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(tokens.SidebarProgressHeight),
                    color = tokens.ScanBlue,
                    trackColor = tokens.SurfaceVariant,
                    strokeCap = StrokeCap.Butt,
                )

                HorizontalDivider(color = tokens.Outline)

                // 章节列表
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 8.dp),
                ) {
                    items(chapterTitles.size) { index ->
                        val count = chapterMatchCounts[index]
                        ChapterItem(
                            title = chapterTitles.getOrElse(index) { "第 ${index + 1} 章" },
                            matchCount = count,
                            isActive = false, // TODO: 当前章节高亮
                            onClick = { onChapterClick(index) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 章节项
 */
@Composable
private fun ChapterItem(
    title: String,
    matchCount: Int?,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val tokens = EditorTokens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isActive) tokens.Outline else tokens.Surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = tokens.SidebarItemFontSize,
            color = tokens.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Spacer(modifier = Modifier.width(12.dp))

        if (matchCount == null) {
            // 扫描中：Loading 动画
            LoadingSpinner()
        } else {
            // 匹配数徽标
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = tokens.SurfaceVariant,
            ) {
                Text(
                    text = "$matchCount",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    fontSize = 12.sp,
                    color = tokens.TextSecondary,
                )
            }
        }
    }
}

/**
 * Loading 动画
 */
@Composable
private fun LoadingSpinner() {
    val tokens = EditorTokens
    val infiniteTransition = rememberInfiniteTransition(label = "spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    Box(
        modifier = Modifier
            .size(16.dp)
            .rotate(rotation)
            .background(tokens.ScanBlue.copy(alpha = 0.3f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(tokens.Surface, CircleShape),
        )
    }
}
