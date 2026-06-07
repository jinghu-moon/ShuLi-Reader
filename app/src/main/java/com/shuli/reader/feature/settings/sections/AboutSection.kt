package com.shuli.reader.feature.settings.sections

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.settings.components.SettingsClickItem
import com.shuli.reader.feature.settings.components.SettingsSectionHeader

@Composable
internal fun AboutSection(
    onShowLicenseDialog: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    SettingsSectionHeader(title = strings.settings.aboutLabel, icon = Icons.Outlined.Info)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        modifier = Modifier.padding(bottom = 24.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            // 开发者卡片
            ListItem(
                headlineContent = { Text(strings.settings.developerLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text("jinghu-moon", style = MaterialTheme.typography.bodySmall) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            // 版本号
            ListItem(
                headlineContent = { Text(strings.settings.versionLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text("v1.0.0 (Build 20260523)", style = MaterialTheme.typography.bodySmall) },
                trailingContent = {
                    OutlinedButton(onClick = {
                        Toast.makeText(context, strings.reader.alreadyLatestVersion, Toast.LENGTH_SHORT).show()
                    }) {
                        Text(strings.settings.checkUpdate, style = MaterialTheme.typography.labelMedium)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            // 项目地址
            SettingsClickItem(
                title = "GitHub",
                subtitle = "https://github.com/jinghu-moon/ShuLi-Reader",
                onClick = { uriHandler.openUri("https://github.com/jinghu-moon/ShuLi-Reader") }
            )

            // 开源许可证 (AGPL-3.0)
            SettingsClickItem(
                title = strings.settings.licenseLabel,
                subtitle = "AGPL-3.0 License",
                onClick = onShowLicenseDialog,
            )
        }
    }
}
