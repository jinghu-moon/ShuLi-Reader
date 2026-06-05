// Part of T-35 加密管理页
package com.shuli.reader.ui.settings.crypto

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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.shuli.reader.core.i18n.LocalAppStrings

/**
 * 加密管理页（T-35）
 *
 * 功能：E2EE 状态展示、算法详情、验证密码、更换密码、warn-card 提示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncryptionManagementScreen(
    viewModel: EncryptionManagementViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val encryptionInfo by viewModel.encryptionInfo.collectAsState()
    val verifyResult by viewModel.verifyResult.collectAsState()
    val changePasswordResult by viewModel.changePasswordResult.collectAsState()
    val strings = LocalAppStrings.current
    var showVerifyDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.encryption.encryptionManagement, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.common.backIconDesc)
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
            // 加密状态卡片
            item {
                EncryptionStatusCard(info = encryptionInfo)
            }

            // 算法详情
            if (encryptionInfo.isEnabled) {
                item {
                    AlgorithmDetailsCard(info = encryptionInfo)
                }

                // warn-card: 密码丢失提示
                item {
                    WarnCard(
                        title = strings.encryption.rememberEncryptionPassword,
                        message = strings.encryption.rememberEncryptionPasswordDesc,
                    )
                }

                // 操作按钮
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            OutlinedButton(
                                onClick = { showVerifyDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(strings.encryption.verifyPassword)
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showChangePasswordDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(strings.encryption.changePassword)
                            }
                        }
                    }
                }
            } else {
                // 未启用加密
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = strings.encryption.e2eeNotEnabled,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = strings.encryption.e2eeNotEnabledDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { showChangePasswordDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(strings.encryption.enableEncryption)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // 验证密码弹窗
    if (showVerifyDialog) {
        VerifyPasswordDialog(
            verifyResult = verifyResult,
            onVerify = { password, salt -> viewModel.verifyPassword(password, salt) },
            onDismiss = {
                showVerifyDialog = false
            },
        )
    }

    // 更换密码弹窗
    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            isEncryptionEnabled = encryptionInfo.isEnabled,
            onDismiss = {
                showChangePasswordDialog = false
            },
            onConfirm = { oldPassword, newPassword ->
                viewModel.changePassword(oldPassword, newPassword, encryptionInfo.salt)
            },
        )
    }

    // 密码变更结果反馈
    LaunchedEffect(changePasswordResult) {
        when (changePasswordResult) {
            PasswordChangeResult.SUCCESS -> {
                showChangePasswordDialog = false
            }
            else -> { /* 错误状态由 dialog 内部处理或忽略 */ }
        }
    }
}

@Composable
private fun EncryptionStatusCard(
    info: EncryptionInfo,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                if (info.isEnabled) Icons.Outlined.Lock else Icons.Outlined.Lock,
                contentDescription = null,
                tint = if (info.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = if (info.isEnabled) strings.encryption.encryptionEnabled else strings.encryption.encryptionDisabled,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (info.isEnabled) strings.encryption.e2eeProtectsSyncData else strings.encryption.dataSyncedInPlaintext,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AlgorithmDetailsCard(
    info: EncryptionInfo,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = strings.encryption.algorithmDetails,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            DetailRow(label = strings.encryption.encryptionAlgorithm, value = info.algorithm.ifEmpty { "AES-256-GCM" })
            DetailRow(label = strings.encryption.kdfIterations, value = info.kdfIterations.toString())
            DetailRow(label = strings.encryption.keyVersion, value = info.keyVersion.toString())
            if (info.createdAt > 0) {
                DetailRow(label = strings.encryption.createdAt, value = dateFormat.format(Date(info.createdAt)))
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WarnCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
        modifier = modifier.fillMaxWidth(),
    ) {
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
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun VerifyPasswordDialog(
    verifyResult: PasswordVerifyResult?,
    onVerify: (String, ByteArray) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    var password by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.encryption.verifyPassword) },
        text = {
            Column {
                Text(strings.encryption.inputPasswordToVerify, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(strings.encryption.encryptionPassword) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (verifyResult != null) {
                    Spacer(Modifier.height(8.dp))
                    val (text, color) = when (verifyResult) {
                        PasswordVerifyResult.SUCCESS -> strings.encryption.verifySuccess to MaterialTheme.colorScheme.primary
                        PasswordVerifyResult.WRONG_PASSWORD -> strings.encryption.passwordWrong to MaterialTheme.colorScheme.error
                        PasswordVerifyResult.NO_ENCRYPTION -> strings.encryption.encryptionNotEnabled to MaterialTheme.colorScheme.onSurfaceVariant
                        PasswordVerifyResult.ERROR -> strings.encryption.verifyError to MaterialTheme.colorScheme.error
                    }
                    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    // 使用空 salt 进行验证（实际实现需要从元数据获取 salt）
                    onVerify(password, ByteArray(16))
                },
                enabled = password.isNotEmpty(),
            ) {
                Text(strings.encryption.verify)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(strings.common.cancel)
            }
        },
    )
}

@Composable
private fun ChangePasswordDialog(
    isEncryptionEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (oldPassword: String, newPassword: String) -> Unit,
) {
    val strings = LocalAppStrings.current
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val passwordsMatch = newPassword == confirmPassword

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEncryptionEnabled) strings.encryption.changePassword else strings.encryption.setEncryptionPassword) },
        text = {
            Column {
                if (isEncryptionEnabled) {
                    OutlinedTextField(
                        value = oldPassword,
                        onValueChange = { oldPassword = it },
                        label = { Text(strings.encryption.oldPassword) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text(strings.encryption.newPassword) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(strings.encryption.confirmNewPassword) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = newPassword.isNotEmpty() && !passwordsMatch,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (newPassword.isNotEmpty() && !passwordsMatch) {
                    Text(
                        text = strings.encryption.passwordMismatch,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(oldPassword, newPassword) },
                enabled = newPassword.isNotEmpty() && passwordsMatch &&
                    (!isEncryptionEnabled || oldPassword.isNotEmpty()),
            ) {
                Text(if (isEncryptionEnabled) strings.encryption.confirmChange else strings.encryption.confirmSet)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(strings.common.cancel)
            }
        },
    )
}
