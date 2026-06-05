// Part of T-37 设备管理页
package com.shuli.reader.ui.devices

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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.shuli.reader.core.i18n.LocalAppStrings

/**
 * 设备管理页（T-37）
 *
 * 功能：设备列表展示、本机标记、移除确认弹窗。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceManagementScreen(
    viewModel: DeviceManagementViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val devices by viewModel.devices.collectAsState()
    val strings = LocalAppStrings.current
    var showRemoveDialog by remember { mutableStateOf<DeviceUiItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.sync.syncedDevices, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.common.backIconDesc)
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        if (devices.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = strings.sync.noSyncedDevices,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(Modifier.height(8.dp)) }

                items(devices, key = { it.deviceId }) { device ->
                    DeviceCard(
                        device = device,
                        onRemove = { showRemoveDialog = device },
                    )
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    // 移除确认弹窗
    showRemoveDialog?.let { device ->
        RemoveDeviceDialog(
            deviceName = device.model.ifBlank { strings.sync.deviceFallbackName(device.deviceId.take(6)) },
            onConfirm = {
                viewModel.removeDevice(device.deviceId)
                showRemoveDialog = null
            },
            onDismiss = { showRemoveDialog = null },
        )
    }
}

@Composable
private fun DeviceCard(
    device: DeviceUiItem,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                Icons.Outlined.PhoneAndroid,
                contentDescription = null,
                tint = if (device.isSelf) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.model.ifBlank { strings.sync.unknownDevice },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (device.isSelf) {
                        Spacer(Modifier.width(8.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))) {
                            Text(
                                text = strings.sync.thisDevice,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                if (device.manufacturer.isNotBlank()) {
                    Text(
                        text = device.manufacturer,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${strings.sync.lastSyncLabel}: ${dateFormat.format(Date(device.lastSyncAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (device.appVersion.isNotBlank()) {
                    Text(
                        text = "${strings.sync.appVersionLabel}: ${device.appVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!device.isSelf) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = strings.sync.removeDevice,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoveDeviceDialog(
    deviceName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.sync.removeDevice) },
        text = {
            Text(strings.sync.removeDeviceConfirm(deviceName))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(strings.sync.remove, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.common.cancel)
            }
        },
    )
}
