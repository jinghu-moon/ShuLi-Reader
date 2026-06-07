package com.shuli.reader.feature.bookshelf.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.database.dao.TagWithCount
import com.shuli.reader.core.i18n.LocalAppStrings

@Composable
fun TagInputField(
    suggestions: List<TagWithCount>,
    onTagSubmit: (String) -> Unit,
    onSuggestionClick: (TagWithCount) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    var inputText by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = inputText,
            onValueChange = { value ->
                inputText = value
                showSuggestions = value.isNotEmpty()
            },
            label = { Text(strings.bookshelf.searchTagHint) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (inputText.isNotBlank()) {
                        onTagSubmit(inputText.trim())
                        inputText = ""
                        showSuggestions = false
                    }
                },
            ),
        )

        if (showSuggestions && suggestions.isNotEmpty()) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { showSuggestions = false },
                modifier = Modifier.fillMaxWidth(0.9f),
            ) {
                suggestions.forEach { suggestion ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = suggestion.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = strings.bookshelf.tagCountLabel(suggestion.usageCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            onSuggestionClick(suggestion)
                            inputText = ""
                            showSuggestions = false
                        },
                    )
                }
            }
        }

        if (inputText.isNotBlank()) {
            Text(
                text = strings.bookshelf.addTag,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        onTagSubmit(inputText.trim())
                        inputText = ""
                        showSuggestions = false
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
