// Part of T-39/T-40 本地备份界面
package com.shuli.reader.ui.export

import android.net.Uri
import com.shuli.reader.sync.export.ExportOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings

/**
 * 本地备份界面
 *
 * 提供导出、导入和自动备份设置的统一入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalBackupScreen(
    onBackClick: () -> Unit,
    onExport: (ExportOptions) -> Unit,
    onImport: (Uri) -> Unit,
    isExporting: Boolean = false,
    isImporting: Boolean = false,
    exportResult: String? = null,
    importResult: String? = null,
    // 自动备份设置
    autoBackupEnabled: Boolean = false,
    backupOnAppStart: Boolean = false,
    backupOnAppExit: Boolean = false,
    backupIntervalHours: Int = 24,
    backupLocation: String = "",
    onAutoBackupEnabledChange: (Boolean) -> Unit = {},
    onBackupOnAppStartChange: (Boolean) -> Unit = {},
    onBackupOnAppExitChange: (Boolean) -> Unit = {},
    onBackupIntervalChange: (Int) -> Unit = {},
    onBackupLocationChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    var showExportSheet by remember { mutableStateOf(false) }

    // SAF 文件选择器：导入
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onImport(it) }
    }

    // SAF 目录选择器：备份路径
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // 持久化读写权限
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            onBackupLocationChange(it.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.syncMethodLocal, fontWeight = FontWeight.Bold) },
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
            // 说明卡片
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Archive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "将书签、笔记、阅读进度等数据备份到本地文件，或从备份文件恢复数据。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 备份路径设置
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "备份存储位置",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = if (backupLocation.isNotEmpty()) {
                                        // 从 URI 提取路径显示
                                        Uri.parse(backupLocation).lastPathSegment ?: backupLocation
                                    } else {
                                        "应用默认目录"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            OutlinedButton(onClick = { folderLauncher.launch(null) }) {
                                Text("选择")
                            }
                        }
                        if (backupLocation.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "已选择自定义备份目录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "备份将保存在应用私有目录，卸载应用时数据会被清除",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // 自动备份设置
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "自动备份",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "按计划自动备份数据",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = autoBackupEnabled,
                                onCheckedChange = onAutoBackupEnabledChange,
                            )
                        }

                        if (autoBackupEnabled) {
                            Spacer(Modifier.height(16.dp))

                            // 定时间隔选择
                            Text(
                                text = "备份频率",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(8.dp))

                            val intervals = listOf(6, 12, 24, 48, 72)
                            val labels = listOf("每 6 小时", "每 12 小时", "每天", "每 2 天", "每 3 天")
                            intervals.forEachIndexed { index, hours ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                ) {
                                    androidx.compose.material3.RadioButton(
                                        selected = backupIntervalHours == hours,
                                        onClick = { onBackupIntervalChange(hours) },
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = labels[index],
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                            androidx.compose.material3.HorizontalDivider()
                            Spacer(Modifier.height(16.dp))

                            // 启动时备份
                            SettingsSwitchItem(
                                title = "启动时备份",
                                subtitle = "每次打开应用时自动备份",
                                checked = backupOnAppStart,
                                onCheckedChange = onBackupOnAppStartChange,
                            )

                            // 关闭时备份
                            SettingsSwitchItem(
                                title = "关闭时备份",
                                subtitle = "每次关闭应用时自动备份",
                                checked = backupOnAppExit,
                                onCheckedChange = onBackupOnAppExitChange,
                            )

                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "自动备份不包含书籍文件，仅备份书签、笔记和进度数据。最多保留 5 个备份。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // 手动备份区域
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "手动备份",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(12.dp))

                        // 导出
                        BackupActionButton(
                            icon = Icons.Outlined.FileDownload,
                            title = "导出备份",
                            subtitle = "将数据导出为 ZIP 文件",
                            buttonText = "导出",
                            isLoading = isExporting,
                            resultText = exportResult,
                            onClick = { showExportSheet = true },
                        )

                        Spacer(Modifier.height(12.dp))

                        // 导入
                        BackupActionButton(
                            icon = Icons.Outlined.FileUpload,
                            title = "导入备份",
                            subtitle = "从 ZIP 文件恢复数据",
                            buttonText = "选择文件",
                            isLoading = isImporting,
                            resultText = importResult,
                            onClick = { importLauncher.launch("application/zip") },
                        )
                    }
                }
            }

            // 注意事项
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "注意事项",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "• 导入会合并现有数据，不会覆盖较新的本地数据\n• 加密备份需要输入正确的密码才能导入\n• 建议定期备份以防止数据丢失\n• 自定义目录需要授予应用读写权限",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            // 底部间距
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // 导出弹窗
    if (showExportSheet) {
        ExportBottomSheet(
            onDismiss = { showExportSheet = false },
            onExport = { options ->
                showExportSheet = false
                onExport(options)
            },
        )
    }
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun BackupActionButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    buttonText: String,
    isLoading: Boolean,
    resultText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (resultText != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = resultText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (resultText.contains("成功") || resultText.contains("完成"))
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error,
                )
            }
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            OutlinedButton(onClick = onClick) {
                Text(buttonText)
            }
        }
    }
}
