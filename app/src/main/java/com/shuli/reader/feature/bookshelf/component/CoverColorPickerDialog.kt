package com.shuli.reader.feature.bookshelf.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings

/**
 * 自定义封面色盘选择对话框。
 *
 * 5×4 网格展示 20 组墨土色盘，选中色块加 ✓ 标记；
 * 顶部"恢复自动"项把 customCoverPaletteIndex 重置为 null，回到 hash 散列模式。
 */
@Composable
fun CoverColorPickerDialog(
    currentIndex: Int?,
    onSelected: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val paletteIndices = remember { (0 until MorandiPalettes.size).toList() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.reader.customizeCover, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Surface(
                    onClick = { onSelected(null) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (currentIndex == null) MaterialTheme.colorScheme.surfaceVariant
                        else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(
                            text = strings.reader.resetCoverColor,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(items = paletteIndices, key = { it }) { index ->
                        val palette = MorandiPalettes[index]
                        val isSelected = currentIndex == index
                        val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        val interactionSource = remember { MutableInteractionSource() }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(palette))
                                .border(
                                    BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
                                    CircleShape,
                                )
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { onSelected(index) },
                                ),
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.common.cancel)
            }
        },
    )
}
