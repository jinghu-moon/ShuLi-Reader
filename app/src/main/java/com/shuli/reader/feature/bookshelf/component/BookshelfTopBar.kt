package com.shuli.reader.feature.bookshelf.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.ViewHeadline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.bookshelf.model.ViewMode
import com.shuli.reader.ui.testing.UiTestTags

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfTopBar(
    todayReadingTime: String,
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit,
    onSortClick: () -> Unit,
    isSearching: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onStatisticsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current

    if (isSearching) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = { 
                    onSearchActiveChange(false)
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.common.backIconDesc)
                }
            },
            title = {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { 
                        Text(
                            text = strings.common.searchPlaceholder, 
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        ) 
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = strings.common.clearIconDesc)
                            }
                        }
                    }
                )
            },
            modifier = modifier,
        )
    } else {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${strings.common.todayReading} $todayReadingTime",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = { onSearchActiveChange(true) },
                    modifier = Modifier.testTag(UiTestTags.BOOKSHELF_SEARCH_BUTTON),
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = strings.common.searchIconDesc)
                }
                IconButton(
                    onClick = onSortClick,
                    modifier = Modifier.testTag(UiTestTags.BOOKSHELF_SORT_BUTTON),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = strings.common.sortIconDesc)
                }
                var viewModeExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { viewModeExpanded = true },
                        modifier = Modifier.testTag(UiTestTags.BOOKSHELF_VIEW_MODE_BUTTON),
                    ) {
                        Icon(
                            imageVector = when (viewMode) {
                                ViewMode.GRID -> Icons.Outlined.GridView
                                ViewMode.LIST -> Icons.AutoMirrored.Outlined.ViewList
                                ViewMode.COMPACT_LIST -> Icons.Outlined.ViewHeadline
                            },
                            contentDescription = strings.common.viewModeIconDesc,
                        )
                    }
                    DropdownMenu(
                        expanded = viewModeExpanded,
                        onDismissRequest = { viewModeExpanded = false },
                    ) {
                        val modes = listOf(
                            ViewMode.GRID to Icons.Outlined.GridView,
                            ViewMode.LIST to Icons.AutoMirrored.Outlined.ViewList,
                            ViewMode.COMPACT_LIST to Icons.Outlined.ViewHeadline,
                        )
                        val labels = mapOf(
                            ViewMode.GRID to strings.common.viewModeGrid,
                            ViewMode.LIST to strings.common.viewModeList,
                            ViewMode.COMPACT_LIST to strings.common.viewModeCompact,
                        )
                        modes.forEach { (mode, icon) ->
                            val isSelected = viewMode == mode
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = labels[mode] ?: "",
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                onClick = {
                                    viewModeExpanded = false
                                    onViewModeChange(mode)
                                },
                            )
                        }
                    }
                }
                
                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag(UiTestTags.BOOKSHELF_MORE_BUTTON),
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = strings.common.moreIconDesc)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(strings.settings.readingStats) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ShowChart, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onStatisticsClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(strings.common.settings) },
                            leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onSettingsClick()
                            }
                        )
                    }
                }
            },
            modifier = modifier,
        )
    }
}
