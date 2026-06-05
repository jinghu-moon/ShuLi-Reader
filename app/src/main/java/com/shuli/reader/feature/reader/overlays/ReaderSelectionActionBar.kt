package com.shuli.reader.feature.reader.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Note
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.ui.testing.UiTestTags
import com.shuli.reader.ui.theme.LocalReaderColorScheme
import com.shuli.reader.ui.theme.ReaderDimens

/**
 * 选区操作栏：复制、添加书签、添加笔记。
 */
@Composable
fun ReaderSelectionActionBar(
    onCopy: () -> Unit,
    onBookmark: () -> Unit,
    onNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current

    val actionButtonColors = ButtonDefaults.filledTonalButtonColors(
        containerColor = readerColors.divider,
        contentColor = readerColors.textPrimary,
    )

    Surface(
        color = readerColors.surface,
        contentColor = readerColors.textPrimary,
        tonalElevation = ReaderDimens.ElevationMedium,
        shadowElevation = ReaderDimens.ElevationMedium + 2.dp,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .wrapContentWidth()
            .testTag(UiTestTags.READER_SELECTION_ACTION_BAR),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ReaderDimens.PaddingSmall),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = ReaderDimens.PaddingMedium - 4.dp, vertical = ReaderDimens.PaddingSmall),
        ) {
            FilledTonalButton(
                onClick = onCopy,
                colors = actionButtonColors,
                modifier = Modifier.testTag(UiTestTags.READER_COPY_SELECTION_BUTTON),
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                Text(strings.copySelection)
            }
            FilledTonalButton(
                onClick = onBookmark,
                colors = actionButtonColors,
                modifier = Modifier.testTag(UiTestTags.READER_BOOKMARK_SELECTION_BUTTON),
            ) {
                Icon(Icons.Outlined.Bookmark, contentDescription = null)
                Text(strings.addBookmarkAction)
            }
            FilledTonalButton(
                onClick = onNote,
                colors = actionButtonColors,
                modifier = Modifier.testTag(UiTestTags.READER_NOTE_SELECTION_BUTTON),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Note, contentDescription = null)
                Text(strings.addNoteAction)
            }
        }
    }
}
