package com.shuli.reader.feature.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsSectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun SettingsClickItem(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
fun SettingsSwitchItem(title: String, subtitle: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) },
        supportingContent = subtitle?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun SettingsButtonItem(title: String, subtitle: String, buttonText: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            OutlinedButton(onClick = onClick) {
                Text(buttonText, style = MaterialTheme.typography.labelMedium)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun ThemePreviewDots(primary: Color, background: Color, text: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(background, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(primary, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(text, CircleShape)
        )
    }
}
