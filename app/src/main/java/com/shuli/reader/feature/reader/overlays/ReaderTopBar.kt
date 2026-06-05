package com.shuli.reader.feature.reader.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.ui.testing.UiTestTags
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 阅读器顶部工具栏。
 *
 * 职责：显示书名、返回按钮、搜索入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(
    bookTitle: String,
    bookId: Long,
    searchResultIndex: Int,
    searchResultCount: Int,
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    onPreviousSearchResult: () -> Unit,
    onNextSearchResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(readerColors.surface.copy(alpha = 0.95f))
            .statusBarsPadding()
    ) {
        TopAppBar(
            title = {
                Text(
                    text = bookTitle.ifBlank { "${strings.appName} - #$bookId" },
                    style = MaterialTheme.typography.titleMedium,
                    color = readerColors.textPrimary
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.testTag(UiTestTags.READER_BACK_BUTTON),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings.backIconDesc,
                        tint = readerColors.textPrimary
                    )
                }
            },
            actions = {
                ReaderSearchControlsInline(
                    currentIndex = searchResultIndex,
                    total = searchResultCount,
                    onPrevious = onPreviousSearchResult,
                    onNext = onNextSearchResult,
                )
                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = strings.search,
                        tint = readerColors.textPrimary,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = readerColors.textPrimary,
                navigationIconContentColor = readerColors.textPrimary,
                actionIconContentColor = readerColors.textPrimary,
            ),
        )
    }
}

@Composable
private fun ReaderSearchControlsInline(
    currentIndex: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    if (total <= 0 || currentIndex < 0) return

    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current

    androidx.compose.foundation.layout.Row {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = strings.previousSearchResult,
                tint = readerColors.textPrimary,
            )
        }
        Text(
            text = "${currentIndex + 1}/$total",
            style = MaterialTheme.typography.labelMedium,
            color = readerColors.textSecondary,
        )
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = strings.nextSearchResult,
                tint = readerColors.textPrimary,
            )
        }
    }
}
