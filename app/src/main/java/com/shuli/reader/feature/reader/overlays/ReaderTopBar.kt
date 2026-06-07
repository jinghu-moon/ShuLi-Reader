package com.shuli.reader.feature.reader.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.core.reader.animation.ReaderMotionTokens
import com.shuli.reader.feature.reader.ReaderUiState
import com.shuli.reader.ui.testing.UiTestTags
import com.shuli.reader.ui.theme.LocalReaderColorScheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderTopBar(
    uiState: ReaderUiState,
    bookId: Long,
    onBackClick: () -> Unit,
    onToggleSearch: () -> Unit,
    onPreviousSearchResult: () -> Unit,
    onNextSearchResult: () -> Unit,
    onShowBookInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current

    AnimatedVisibility(
        visible = uiState.showToolbar && !uiState.showSearch,
        enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(ReaderMotionTokens.SHORT_MS.toInt())) + fadeIn(animationSpec = tween(ReaderMotionTokens.SHORT_MS.toInt())),
        exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(ReaderMotionTokens.SHORT_MS.toInt())) + fadeOut(animationSpec = tween(ReaderMotionTokens.SHORT_MS.toInt())),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(readerColors.surface.copy(alpha = 0.95f))
                .statusBarsPadding()
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.bookTitle.ifBlank { "${strings.common.appName} - #$bookId" },
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
                            contentDescription = strings.common.backIconDesc,
                            tint = readerColors.textPrimary
                        )
                    }
                },
                actions = {
                    ReaderSearchControls(
                        currentIndex = uiState.currentSearchResultIndex,
                        total = uiState.searchResults.size,
                        onPrevious = onPreviousSearchResult,
                        onNext = onNextSearchResult,
                    )
                    IconButton(onClick = onShowBookInfo) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = strings.bookshelf.bookInfo,
                            tint = readerColors.textPrimary,
                        )
                    }
                    IconButton(onClick = onToggleSearch) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = strings.common.search,
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
}

@Composable
private fun ReaderSearchControls(
    currentIndex: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current
    if (total <= 0 || currentIndex < 0) return

    Row(modifier = modifier) {
        IconButton(
            onClick = onPrevious,
            modifier = Modifier.testTag(UiTestTags.READER_SEARCH_PREV_BUTTON),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = strings.common.previousSearchResult,
                tint = readerColors.textPrimary,
            )
        }
        Text(
            text = "${currentIndex + 1}/$total",
            style = MaterialTheme.typography.labelMedium,
            color = readerColors.textSecondary,
            modifier = Modifier.testTag(UiTestTags.READER_SEARCH_RESULT_COUNTER),
        )
        IconButton(
            onClick = onNext,
            modifier = Modifier.testTag(UiTestTags.READER_SEARCH_NEXT_BUTTON),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = strings.common.nextSearchResult,
                tint = readerColors.textPrimary,
            )
        }
    }
}
