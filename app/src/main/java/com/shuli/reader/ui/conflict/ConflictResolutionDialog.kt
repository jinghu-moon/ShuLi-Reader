// Part of T-36 冲突解决弹窗
package com.shuli.reader.ui.conflict

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shuli.reader.sync.conflict.BookState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 冲突解决弹窗（T-36）
 *
 * 当检测到阅读进度冲突时显示，用户可以选择：
 * - 保留本地：使用本地进度覆盖远端
 * - 使用远端：使用远端进度覆盖本地
 * - 暂不处理：跳过此冲突，下次同步再处理
 *
 * 设备名显示优先级：model > deviceId 前 6 位 > "其他设备"
 */
@Composable
fun ConflictResolutionDialog(
    localState: BookState,
    remoteState: BookState,
    remoteDeviceName: String?,
    onKeepLocal: () -> Unit,
    onUseRemote: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val deviceDisplayName = remoteDeviceName ?: "其他设备"

    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("阅读进度冲突") },
        text = {
            Column {
                Text(
                    text = "检测到 \"$deviceDisplayName\" 与本机的阅读进度不一致。请选择要保留的进度：",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))

                // 本地进度
                ProgressCard(
                    label = "本机",
                    state = localState,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                // 远端进度
                ProgressCard(
                    label = deviceDisplayName,
                    state = remoteState,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onKeepLocal) {
                Text("保留本地")
            }
        },
        dismissButton = {
            TextButton(onClick = onUseRemote) {
                Text("使用远端")
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun ProgressCard(
    label: String,
    state: BookState,
    modifier: Modifier = Modifier,
) {
    val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "位置: 第 ${state.chapterIndex + 1} 章，偏移 ${state.byteOffset}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.updatedAt > 0) {
                Text(
                    text = "更新时间: ${dateFormat.format(Date(state.updatedAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 冲突解决结果
 */
enum class ConflictResolution {
    KEEP_LOCAL,
    USE_REMOTE,
    SKIP,
}
