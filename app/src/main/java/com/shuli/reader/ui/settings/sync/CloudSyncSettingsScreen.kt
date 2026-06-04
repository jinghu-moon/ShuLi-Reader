// Part of T-34 云端同步设置子页
package com.shuli.reader.ui.settings.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.shuli.reader.feature.settings.SettingsClickItem
import com.shuli.reader.feature.settings.SettingsSwitchItem
import com.shuli.reader.core.i18n.LocalAppStrings

/**
 * 云端同步配置页（T-34）
 *
 * 功能：服务商选择、账号输入、测试连接、同步内容复选、Wi-Fi限制、自动同步、
 *       加密管理入口、查看日志、已同步设备入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncSettingsScreen(
    viewModel: CloudSyncSettingsViewModel,
    onBackClick: () -> Unit,
    onNavigateToEncryption: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTesting by viewModel.isTesting.collectAsState()
    val strings = LocalAppStrings.current
    val coroutineScope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<ConnectionTestResult?>(null) }
    var syncBookmarks by remember { mutableStateOf(true) }
    var syncNotes by remember { mutableStateOf(true) }
    var syncProgress by remember { mutableStateOf(true) }
    var wifiOnly by remember { mutableStateOf(false) }
    var autoSync by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.connectionTestResult.collect { result ->
            testResult = result
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.cloudSyncConfig, fontWeight = FontWeight.Bold) },
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
            // WebDAV 服务器配置
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = strings.webdavServer,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text(strings.serverAddress) },
                            placeholder = { Text("https://dav.example.com/dav/") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text(strings.webdavUser) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(strings.webdavPassword) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(16.dp))

                        // 测试连接结果
                        if (testResult != null) {
                            ConnectionTestResultBanner(result = testResult!!)
                            Spacer(Modifier.height(8.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.testConnection(url, username, password) },
                                enabled = !isTesting && url.isNotBlank() && username.isNotBlank(),
                                modifier = Modifier.weight(1f),
                            ) {
                                if (isTesting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(strings.testConnection)
                                }
                            }
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        viewModel.saveSyncSettings(url, username, password)
                                    }
                                },
                                enabled = url.isNotBlank() && username.isNotBlank(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(strings.save)
                            }
                        }
                    }
                }
            }

            // 同步内容
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                    Column {
                        Text(
                            text = strings.syncContent,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
                        )
                        SettingsSwitchItem(title = strings.bookmarksTab, checked = syncBookmarks, onCheckedChange = { syncBookmarks = it })
                        SettingsSwitchItem(title = strings.notesTab, checked = syncNotes, onCheckedChange = { syncNotes = it })
                        SettingsSwitchItem(title = strings.sortReadingProgress, checked = syncProgress, onCheckedChange = { syncProgress = it })
                    }
                }
            }

            // 同步偏好
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                    Column {
                        SettingsSwitchItem(title = strings.wifiOnly, checked = wifiOnly, onCheckedChange = { wifiOnly = it })
                        SettingsSwitchItem(title = strings.autoSync, checked = autoSync, onCheckedChange = { autoSync = it })
                    }
                }
            }

            // 其他入口
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                    Column {
                        SettingsClickItem(
                            title = strings.encryptionManagement,
                            subtitle = strings.e2eeSettings,
                            onClick = onNavigateToEncryption,
                        )
                        SettingsClickItem(
                            title = strings.syncedDevices,
                            subtitle = strings.manageRegisteredDevices,
                            onClick = onNavigateToDevices,
                        )
                        SettingsClickItem(
                            title = strings.syncLog,
                            subtitle = strings.viewSyncHistory,
                            onClick = onNavigateToLogs,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ConnectionTestResultBanner(
    result: ConnectionTestResult,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val (message, icon, color) = when (result) {
        ConnectionTestResult.SUCCESS -> Triple(strings.connectionSuccess, Icons.Outlined.CheckCircle, MaterialTheme.colorScheme.primary)
        ConnectionTestResult.AUTH_FAILED -> Triple(strings.authFailedCheckUserPassword, Icons.Outlined.Error, MaterialTheme.colorScheme.error)
        ConnectionTestResult.NETWORK_ERROR -> Triple(strings.networkErrorCheckAddress, Icons.Outlined.Error, MaterialTheme.colorScheme.error)
        ConnectionTestResult.UNKNOWN_ERROR -> Triple(strings.unknownErrorRetryLater, Icons.Outlined.Error, MaterialTheme.colorScheme.error)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.padding(8.dp))
            Text(text = message, style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}
