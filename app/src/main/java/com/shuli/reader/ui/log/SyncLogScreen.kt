// Part of T-38 同步日志页
package com.shuli.reader.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.shuli.reader.core.i18n.LocalAppStrings

/**
 * 同步日志页（T-38）
 *
 * 功能：日期分组展示、筛选（全部/云端/本地/失败）、日志详情。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncLogScreen(
    viewModel: SyncLogViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val groupedLogs by viewModel.groupedLogs.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val strings = LocalAppStrings.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("同步日志", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backIconDesc)
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 筛选器
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                FilterChip(
                    selected = currentFilter == SyncLogFilter.ALL,
                    onClick = { viewModel.applyFilter(SyncLogFilter.ALL) },
                    label = { Text("全部") },
                )
                FilterChip(
                    selected = currentFilter == SyncLogFilter.CLOUD,
                    onClick = { viewModel.applyFilter(SyncLogFilter.CLOUD) },
                    label = { Text("云端") },
                )
                FilterChip(
                    selected = currentFilter == SyncLogFilter.LOCAL,
                    onClick = { viewModel.applyFilter(SyncLogFilter.LOCAL) },
                    label = { Text("本地") },
                )
                FilterChip(
                    selected = currentFilter == SyncLogFilter.FAILED,
                    onClick = { viewModel.applyFilter(SyncLogFilter.FAILED) },
                    label = { Text("失败") },
                )
            }

            if (groupedLogs.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "暂无同步日志",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    groupedLogs.forEach { (date, logs) ->
                        item {
                            Text(
                                text = date,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }

                        items(logs) { entry ->
                            SyncLogEntryCard(entry = entry)
                        }
                    }

                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SyncLogEntryCard(
    entry: SyncLogEntry,
    modifier: Modifier = Modifier,
) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            // 状态图标
            Icon(
                imageVector = when (entry.result) {
                    SyncResult.SUCCESS -> Icons.Outlined.CheckCircle
                    SyncResult.FAILED -> Icons.Outlined.Error
                    SyncResult.CANCELLED -> Icons.Outlined.Cancel
                },
                contentDescription = null,
                tint = when (entry.result) {
                    SyncResult.SUCCESS -> MaterialTheme.colorScheme.primary
                    SyncResult.FAILED -> MaterialTheme.colorScheme.error
                    SyncResult.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = timeFormat.format(Date(entry.timestamp)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = when (entry.syncType) {
                            SyncLogFilter.CLOUD -> "云端"
                            SyncLogFilter.LOCAL -> "本地"
                            else -> "未知"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = formatDuration(entry.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${entry.requestCount} 次请求",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatSize(entry.transferSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (entry.errorMessage != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = entry.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    return when {
        seconds < 60 -> "${seconds}秒"
        seconds < 3600 -> "${seconds / 60}分${seconds % 60}秒"
        else -> "${seconds / 3600}时${(seconds % 3600) / 60}分"
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))}MB"
    }
}
