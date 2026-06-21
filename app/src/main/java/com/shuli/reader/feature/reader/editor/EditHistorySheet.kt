package com.shuli.reader.feature.reader.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 模块 D: 编辑记录面板（Bottom Sheet）
 *
 * 参考 edit-interface-demo.html 设计：
 * - 底部弹出抽屉，最大高度 85vh
 * - 头部：图标 + 标题 + 徽章 + 关闭
 * - Diff 卡片：原文（删除线）+ 新文（加粗）
 * - 底部栏：全部撤销 + 保存修改
 */
@Composable
fun EditHistorySheet(
    visible: Boolean,
    patches: List<EditStore.Patch>,
    onUndo: () -> Unit,
    onClearAll: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = EditorTokens

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(300)) { it },
        exit = slideOutVertically(tween(300)) { it },
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(tokens.SheetMaxHeightRatio),
            shape = RoundedCornerShape(topStart = tokens.SheetCornerRadius, topEnd = tokens.SheetCornerRadius),
            color = tokens.Surface,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.fillMaxHeight()) {
                // 头部
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(tokens.SheetHeaderHeight)
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = tokens.TextPrimary,
                        )
                        Text(
                            text = "编辑记录",
                            fontSize = tokens.SheetTitleFontSize,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.TextPrimary,
                        )
                        // 徽章
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = tokens.SurfaceVariant,
                        ) {
                            Text(
                                text = "${patches.size}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 12.sp,
                                color = tokens.TextSecondary,
                            )
                        }
                    }
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

                HorizontalDivider(color = tokens.Outline)

                // 内容区
                if (patches.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "暂无编辑记录",
                            fontSize = 14.sp,
                            color = tokens.TextSecondary,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 20.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // 按章节分组
                        val grouped = patches.groupBy { it.chapterIndex }
                        grouped.forEach { (chapterIndex, chapterPatches) ->
                            // 章节标题
                            item(key = "chapter_$chapterIndex") {
                                Text(
                                    text = "第 ${chapterIndex + 1} 章",
                                    fontSize = tokens.ChapterTitleFontSize,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.TextSecondary,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }

                            // 编辑记录
                            items(chapterPatches.reversed()) { patch ->
                                DiffCard(
                                    patch = patch,
                                    onUndo = onUndo,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = tokens.Outline)

                // 底部栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 全部撤销按钮
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = tokens.Surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, tokens.Outline),
                        onClick = onClearAll,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "全部撤销",
                                fontSize = tokens.FooterButtonFontSize,
                                fontWeight = FontWeight.Medium,
                                color = tokens.TextSecondary,
                            )
                        }
                    }

                    // 保存修改按钮
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = tokens.Primary,
                        onClick = onSave,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "保存修改",
                                fontSize = tokens.FooterButtonFontSize,
                                fontWeight = FontWeight.Medium,
                                color = tokens.OnPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Diff 卡片
 */
@Composable
private fun DiffCard(
    patch: EditStore.Patch,
    onUndo: () -> Unit,
) {
    val tokens = EditorTokens

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(tokens.DiffCardCornerRadius),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, tokens.Outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Diff 详情
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (patch) {
                    is EditStore.SinglePatch -> {
                        DiffLine(
                            prefix = "-",
                            text = patch.delta.originalText,
                            isOriginal = true,
                        )
                        DiffLine(
                            prefix = "+",
                            text = patch.delta.newText,
                            isOriginal = false,
                        )
                    }
                    is EditStore.BatchPatch -> {
                        DiffLine(
                            prefix = "-",
                            text = "${patch.batch.findText} (${patch.batch.ranges.size}处)",
                            isOriginal = true,
                        )
                        DiffLine(
                            prefix = "+",
                            text = "${patch.batch.replaceText} (${patch.batch.ranges.size}处)",
                            isOriginal = false,
                        )
                    }
                }
            }

            // 撤销按钮
            IconButton(
                onClick = onUndo,
                modifier = Modifier
                    .size(28.dp)
                    .border(1.dp, tokens.Outline, RoundedCornerShape(6.dp)),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "撤销",
                    modifier = Modifier.size(16.dp),
                    tint = tokens.TextSecondary,
                )
            }
        }
    }
}

/**
 * Diff 行
 */
@Composable
private fun DiffLine(
    prefix: String,
    text: String,
    isOriginal: Boolean,
) {
    val tokens = EditorTokens

    val annotatedText = buildAnnotatedString {
        // 前缀
        withStyle(
            SpanStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = if (isOriginal) tokens.ErrorMain else tokens.SuccessMain,
            )
        ) {
            append("$prefix ")
        }

        // 内容（带高亮）
        if (isOriginal) {
            // 原文：删除线 + 红色背景
            withStyle(
                SpanStyle(
                    background = tokens.ErrorBg,
                    color = tokens.ErrorMain,
                    textDecoration = TextDecoration.LineThrough,
                )
            ) {
                append(text)
            }
        } else {
            // 新文：加粗 + 绿色背景
            withStyle(
                SpanStyle(
                    background = tokens.SuccessBg,
                    color = tokens.SuccessMain,
                    fontWeight = FontWeight.SemiBold,
                )
            ) {
                append(text)
            }
        }
    }

    Text(
        text = annotatedText,
        fontSize = tokens.DiffFontSize,
        lineHeight = 20.sp,
    )
}
