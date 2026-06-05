package com.shuli.reader.feature.bookshelf.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.bookshelf.model.BookItem
import com.shuli.reader.feature.bookshelf.model.FileType

/**
 * 书籍信息底部弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookInfoBottomSheet(
    book: BookItem?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (book == null) return

    val strings = LocalAppStrings.current
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            // 标题
            Text(
                text = strings.bookshelf.bookInfo,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // 信息列表
            InfoItem(label = strings.bookshelf.bookTitleLabel, value = book.title)
            InfoItem(label = strings.bookshelf.bookAuthorLabel, value = book.author ?: strings.bookshelf.unknownAuthor)
            InfoItem(label = strings.bookshelf.bookFormatLabel, value = if (book.fileType == FileType.TXT) "TXT" else "EPUB")
            InfoItem(label = strings.bookshelf.bookSizeLabel, value = book.fileSize)
            InfoItem(label = strings.bookshelf.bookProgressLabel, value = "${(book.readingProgress * 100).toInt()}%")
            InfoItem(label = strings.bookshelf.readingDurationLabel, value = book.readingDuration.ifEmpty { strings.bookshelf.notReadYet })
            InfoItem(label = strings.bookshelf.filePathLabel, value = book.filePath)

            // 底部间距
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InfoItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
