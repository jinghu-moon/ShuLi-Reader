// Part of T-39 导出对话框
package com.shuli.reader.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.sync.backup.ExportOptions

/**
 * 导出对话框。
 *
 * 允许用户配置导出选项并触发导出操作。
 */
@Composable
fun ExportDialog(
    onDismiss: () -> Unit,
    onExport: (ExportOptions) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.sync.exportData) },
        text = {
            Column {
                Text(
                    text = strings.sync.selectExportContent,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))

                ExportOptionCheckbox(
                    text = strings.sync.bookFiles,
                    checked = includeBookFiles,
                    onCheckedChange = { includeBookFiles = it },
                )
                ExportOptionCheckbox(
                    text = strings.sync.bookmarks,
                    checked = includeBookmarks,
                    onCheckedChange = { includeBookmarks = it },
                )
                ExportOptionCheckbox(
                    text = strings.sync.notes,
                    checked = includeNotes,
                    onCheckedChange = { includeNotes = it },
                )
                ExportOptionCheckbox(
                    text = strings.sync.readingProgressExport,
                    checked = includeProgress,
                    onCheckedChange = { includeProgress = it },
                )
                ExportOptionCheckbox(
                    text = strings.sync.readerConfig,
                    checked = includeConfig,
                    onCheckedChange = { includeConfig = it },
                )

                Spacer(Modifier.height(16.dp))

                // 加密选项
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = strings.sync.encryptedExport,
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
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(strings.sync.password) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text(strings.sync.passwordConfirm) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = useEncryption && password.isNotEmpty() && !passwordsMatch,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (useEncryption && password.isNotEmpty() && !passwordsMatch) {
                        Text(
                            text = strings.encryption.passwordMismatch,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
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
            ) {
                Text(strings.sync.export)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.common.cancel)
            }
        },
        modifier = modifier,
    )
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
