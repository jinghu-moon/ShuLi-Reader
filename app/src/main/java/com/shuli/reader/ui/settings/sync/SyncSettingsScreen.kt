// Part of T-33 同步设置主页
package com.shuli.reader.ui.settings.sync

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings

/**
 * 同步设置主页（T-33）
 *
 * 显示同步摘要卡片，提供跳转到各子页面的入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
    viewModel: SyncSummaryViewModel,
    onBackClick: () -> Unit,
    onNavigateToCloudSync: () -> Unit,
    onNavigateToEncryption: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.cloudSyncUiState.collectAsState()
    val strings = LocalAppStrings.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.syncAndBackup, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backIconDesc)
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 同步摘要卡片
            item {
                SyncSummaryCard(uiState = uiState, onSyncClick = {
                    viewModel.triggerManualSync(com.shuli.reader.sync.engine.SyncTarget.BOTH)
                })
            }

            // 云端同步配置
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                    Column {
                        SyncNavigationItem(
                            icon = Icons.Outlined.Cloud,
                            title = "云端同步配置",
                            subtitle = "WebDAV 服务器设置",
                            onClick = onNavigateToCloudSync,
                        )
                        SyncNavigationItem(
                            icon = Icons.Outlined.Lock,
                            title = "加密管理",
                            subtitle = "端到端加密状态",
                            onClick = onNavigateToEncryption,
                        )
                        SyncNavigationItem(
                            icon = Icons.Outlined.Devices,
                            title = "已同步设备",
                            subtitle = "管理已注册设备",
                            onClick = onNavigateToDevices,
                        )
                        SyncNavigationItem(
                            icon = Icons.Outlined.History,
                            title = "同步日志",
                            subtitle = "查看历史同步记录",
                            onClick = onNavigateToLogs,
                        )
                        SyncNavigationItem(
                            icon = Icons.Outlined.FileDownload,
                            title = "导出数据",
                            subtitle = "导出书签、笔记、进度",
                            onClick = onNavigateToExport,
                        )
                    }
                }
            }

            // 底部间距
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SyncSummaryCard(
    uiState: CloudSyncCardUiState,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Outlined.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "同步状态",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = uiState.statusText.ifEmpty { "就绪" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    androidx.compose.material3.TextButton(onClick = onSyncClick) {
                        Text("立即同步")
                    }
                }
            }

            if (uiState.lastSyncText.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = uiState.lastSyncText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 错误状态展示
            if (uiState.errorType != SyncErrorType.NONE) {
                Spacer(Modifier.height(8.dp))
                SyncErrorBanner(errorType = uiState.errorType)
            }
        }
    }
}

@Composable
private fun SyncErrorBanner(
    errorType: SyncErrorType,
    modifier: Modifier = Modifier,
) {
    val (message, color) = when (errorType) {
        SyncErrorType.AUTH_FAILED -> "认证失败，请检查账号密码" to MaterialTheme.colorScheme.error
        SyncErrorType.NETWORK_ERROR -> "网络连接失败" to MaterialTheme.colorScheme.error
        SyncErrorType.RATE_LIMITED -> "请求过于频繁，稍后重试" to MaterialTheme.colorScheme.tertiary
        SyncErrorType.CRYPTO_LOCKED -> "加密锁未解锁，请先验证密码" to MaterialTheme.colorScheme.tertiary
        SyncErrorType.UNKNOWN -> "发生未知错误" to MaterialTheme.colorScheme.error
        SyncErrorType.NONE -> return
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun SyncNavigationItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        },
        supportingContent = {
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable { onClick() },
    )
}
