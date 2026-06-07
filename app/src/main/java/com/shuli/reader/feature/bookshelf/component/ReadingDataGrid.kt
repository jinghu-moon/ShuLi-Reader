package com.shuli.reader.feature.bookshelf.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ReadingDataGrid(
    totalDuration: String,
    readingDays: Int,
    readingProgress: Float,
    modifier: Modifier = Modifier,
) {
    val strings = com.shuli.reader.core.i18n.LocalAppStrings.current

    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = strings.bookshelf.readingData,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DataCard(
                value = totalDuration.ifEmpty { "0m" },
                label = strings.bookshelf.totalDuration,
                modifier = Modifier.weight(1f),
            )
            DataCard(
                value = "${readingDays}${strings.bookshelf.daysUnit}",
                label = strings.bookshelf.readingDays,
                modifier = Modifier.weight(1f),
            )
            DataCard(
                value = "${(readingProgress * 100).toInt()}%",
                label = strings.bookshelf.bookProgressLabel,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun DataCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
