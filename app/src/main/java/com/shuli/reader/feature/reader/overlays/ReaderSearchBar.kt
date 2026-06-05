package com.shuli.reader.feature.reader.overlays

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.ui.theme.LocalReaderColorScheme
import com.shuli.reader.ui.theme.ReaderDimens

/**
 * 阅读器搜索输入栏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSearchBar(
    onSearch: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(
        color = readerColors.surface.copy(alpha = 0.95f),
        contentColor = readerColors.textPrimary,
        tonalElevation = ReaderDimens.ElevationMedium,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = strings.backIconDesc,
                    tint = readerColors.textPrimary,
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(strings.search, color = readerColors.textTertiary)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = readerColors.textPrimary,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = null,
                                tint = readerColors.textTertiary,
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
            IconButton(onClick = { onSearch(query) }) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = strings.search,
                    tint = readerColors.textPrimary,
                )
            }
        }
    }
}
