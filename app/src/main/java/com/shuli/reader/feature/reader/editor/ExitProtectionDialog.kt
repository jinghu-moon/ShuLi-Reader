package com.shuli.reader.feature.reader.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 模块 E: 退出保护确认对话框
 *
 * 参考 edit-interface-demo.html 设计：
 * - 屏幕绝对居中
 * - 暗色半透明遮罩
 * - 弹窗 300dp 宽，16dp 圆角
 * - 弹入动画：scale(0.95 → 1.0)
 */
@Composable
fun ExitProtectionDialog(
    visible: Boolean,
    editCount: Int,
    onSaveAndExit: () -> Unit,
    onDiscardAndExit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = EditorTokens

    if (visible) {
        // 遮罩层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.Backdrop)
                .padding(horizontal = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            // 弹窗动画
            val scale by animateFloatAsState(
                targetValue = if (visible) 1f else 0.95f,
                animationSpec = tween(200),
                label = "dialogScale",
            )

            Surface(
                modifier = Modifier
                    .width(tokens.DialogWidth)
                    .scale(scale),
                shape = RoundedCornerShape(tokens.DialogCornerRadius),
                color = tokens.Surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, tokens.Outline),
                shadowElevation = 16.dp,
            ) {
                Column(
                    modifier = Modifier.padding(tokens.DialogPadding),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 标题
                    Text(
                        text = "退出阅读器",
                        fontSize = tokens.DialogTitleFontSize,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.TextPrimary,
                    )

                    // 描述
                    Text(
                        text = "当前文件有 $editCount 处未保存的文本编辑修改，直接退出将会丢失这些修改。",
                        fontSize = tokens.DialogDescFontSize,
                        color = tokens.TextSecondary,
                        lineHeight = (tokens.DialogDescFontSize.value * 1.5).sp,
                    )

                    // 操作区
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    ) {
                        // 放弃修改按钮
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = tokens.Surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, tokens.Outline),
                            onClick = onDiscardAndExit,
                        ) {
                            Text(
                                text = "放弃修改",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = tokens.TextSecondary,
                            )
                        }

                        // 保存并退出按钮
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = tokens.Primary,
                            onClick = onSaveAndExit,
                        ) {
                            Text(
                                text = "保存并退出",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                fontSize = 14.sp,
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
