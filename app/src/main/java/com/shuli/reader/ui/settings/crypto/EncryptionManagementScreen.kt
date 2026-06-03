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
    val strings = LocalAppStrings.current
    var showVerifyDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("加密管理", fontWeight = FontWeight.Bold) },
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
                        title = "请牢记加密密码",
                        message = "加密密码无法找回。如果忘记密码，加密的同步数据将无法恢复。",
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
                                Text("验证密码")
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showChangePasswordDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("更换密码")
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
                                text = "端到端加密未启用",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "启用后，同步数据将在设备端加密后上传，服务器无法读取内容。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { showChangePasswordDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("启用加密")
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
            onDismiss = { showChangePasswordDialog = false },
            onConfirm = { /* TODO: implement password change */ },
        )
    }
}

@Composable
private fun EncryptionStatusCard(
    info: EncryptionInfo,
    modifier: Modifier = Modifier,
) {
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
                    text = if (info.isEnabled) "加密已启用" else "加密未启用",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (info.isEnabled) "端到端加密保护您的同步数据" else "数据以明文方式同步",
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
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "算法详情",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            DetailRow(label = "加密算法", value = info.algorithm.ifEmpty { "AES-256-GCM" })
            DetailRow(label = "KDF 迭代次数", value = info.kdfIterations.toString())
            DetailRow(label = "密钥版本", value = info.keyVersion.toString())
            if (info.createdAt > 0) {
                DetailRow(label = "创建时间", value = dateFormat.format(Date(info.createdAt)))
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
    var password by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("验证密码") },
        text = {
            Column {
                Text("输入加密密码以验证正确性", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("加密密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (verifyResult != null) {
                    Spacer(Modifier.height(8.dp))
                    val (text, color) = when (verifyResult) {
                        PasswordVerifyResult.SUCCESS -> "验证成功" to MaterialTheme.colorScheme.primary
                        PasswordVerifyResult.WRONG_PASSWORD -> "密码错误" to MaterialTheme.colorScheme.error
                        PasswordVerifyResult.NO_ENCRYPTION -> "未启用加密" to MaterialTheme.colorScheme.onSurfaceVariant
                        PasswordVerifyResult.ERROR -> "验证出错" to MaterialTheme.colorScheme.error
                        null -> "" to MaterialTheme.colorScheme.onSurfaceVariant
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
                Text("验证")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val passwordsMatch = newPassword == confirmPassword

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更换密码") },
        text = {
            Column {
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("新密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("确认新密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = newPassword.isNotEmpty() && !passwordsMatch,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (newPassword.isNotEmpty() && !passwordsMatch) {
                    Text(
                        text = "密码不一致",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(newPassword) },
                enabled = newPassword.isNotEmpty() && passwordsMatch,
            ) {
                Text("确认更换")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
