// Part of T-39 导出底部弹窗
package com.shuli.reader.ui.export

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.sync.export.ExportOptions

/**
 * 导出底部弹窗（T-39）
 *
 * 功能：导出内容复选、加密开关、密码输入（视觉区隔）、
 *       异步大小预估、warn-card 提示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    onDismiss: () -> Unit,
    onExport: (ExportOptions) -> Unit,
    estimatedSize: String? = null,
    isEstimating: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val sheetState = rememberModalBottomSheetState()
    var includeBookFiles by remember { mutableStateOf(true) }
    var includeBookmarks by remember { mutableStateOf(true) }
    var includeNotes by remember { mutableStateOf(true) }
    var includeProgress by remember { mutableStateOf(true) }
    var includeConfig by remember { mutableStateOf(true) }
    var useEncryption by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    val passwordsMatch = !useEncryption || password == confirmPassword
    val canExport = !useEncryption || (password.isNotEmpty() && passwordsMatch)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = strings.exportData,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))

            // 导出内容选择
            Text(
                text = strings.selectExportContent,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))

            ExportOptionCheckbox(text = strings.bookFiles, checked = includeBookFiles, onCheckedChange = { includeBookFiles = it })
            ExportOptionCheckbox(text = strings.bookmarks, checked = includeBookmarks, onCheckedChange = { includeBookmarks = it })
            ExportOptionCheckbox(text = strings.notes, checked = includeNotes, onCheckedChange = { includeNotes = it })
            ExportOptionCheckbox(text = strings.readingProgressExport, checked = includeProgress, onCheckedChange = { includeProgress = it })
            ExportOptionCheckbox(text = strings.readerConfig, checked = includeConfig, onCheckedChange = { includeConfig = it })

            Spacer(Modifier.height(16.dp))

            // 预估大小
            if (isEstimating) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.estimatingSize, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (estimatedSize != null) {
                Text(
                    text = strings.estimatedSize(estimatedSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            // 加密选项
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = strings.encryptedExport,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = useEncryption,
                    onCheckedChange = { useEncryption = it },
                )
            }

            if (useEncryption) {
                Spacer(Modifier.height(8.dp))

                // warn-card: 密码丢失提示
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = strings.rememberExportPassword,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(strings.password) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(strings.passwordConfirm) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = useEncryption && password.isNotEmpty() && !passwordsMatch,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (useEncryption && password.isNotEmpty() && !passwordsMatch) {
                    Text(
                        text = strings.passwordMismatch,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // 导出按钮
            Button(
                onClick = {
                    val options = ExportOptions(
                        includeBookFiles = includeBookFiles,
                        includeBookmarks = includeBookmarks,
                        includeNotes = includeNotes,
                        includeProgress = includeProgress,
                        includeConfig = includeConfig,
                        encryptionPassword = if (useEncryption) password else null,
                    )
                    onExport(options)
                },
                enabled = canExport,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(strings.export)
            }
        }
    }
}

@Composable
private fun ExportOptionCheckbox(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
