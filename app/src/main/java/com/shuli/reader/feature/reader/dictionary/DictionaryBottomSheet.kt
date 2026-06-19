package com.shuli.reader.feature.reader.dictionary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shuli.reader.core.dictionary.model.DictEntry
import com.shuli.reader.core.dictionary.render.DictMdxRenderer
import com.shuli.reader.core.dictionary.render.DictRenderer
import com.shuli.reader.feature.reader.screen.ReaderUiState

/**
 * 查词结果 BottomSheet
 *
 * 使用 M3 ModalBottomSheet + 自定义 detents（36%/55%/90%）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryBottomSheet(
    uiState: ReaderUiState,
    onDismiss: () -> Unit,
    onLookup: (String) -> Unit,
    onAddToWordBook: (String) -> Unit,
    onCopyDefinition: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    ) {
        DictionaryContent(
            uiState = uiState,
            onLookup = onLookup,
            onAddToWordBook = onAddToWordBook,
            onCopyDefinition = onCopyDefinition,
        )
    }
}

@Composable
private fun DictionaryContent(
    uiState: ReaderUiState,
    onLookup: (String) -> Unit,
    onAddToWordBook: (String) -> Unit,
    onCopyDefinition: (String) -> Unit,
) {
    val word = uiState.currentLookupWord
    val results = uiState.dictionaryResults
    val contextSentence = uiState.dictionaryContextSentence

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding(),
    ) {
        // 搜索框
        DictionarySearchBar(
            initialWord = word,
            onSearch = onLookup,
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (results.isEmpty()) {
            // 空状态
            EmptyState(word = word, onLookup = onLookup)
        } else {
            // 词典标签页
            var selectedDictIndex by remember { mutableIntStateOf(0) }

            if (results.size > 1) {
                ScrollableTabRow(
                    selectedTabIndex = selectedDictIndex,
                    edgePadding = 0.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    results.forEachIndexed { index, entry ->
                        Tab(
                            selected = selectedDictIndex == index,
                            onClick = { selectedDictIndex = index },
                            text = {
                                Text(
                                    text = entry.dictName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 释义列表
            val currentEntry = results.getOrNull(selectedDictIndex)
            if (currentEntry != null) {
                DefinitionCard(
                    entry = currentEntry,
                    word = word,
                    onAddToWordBook = { onAddToWordBook(word) },
                    onCopyDefinition = { onCopyDefinition(currentEntry.definition) },
                    onLookup = onLookup,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DictionarySearchBar(
    initialWord: String,
    onSearch: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialWord) }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            if (text.isNotEmpty()) {
                IconButton(onClick = { text = ""; onSearch("") }) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = "清除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(
                onClick = { onSearch(text) },
                enabled = text.isNotBlank(),
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "搜索",
                    tint = if (text.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun DefinitionCard(
    entry: DictEntry,
    word: String,
    onAddToWordBook: () -> Unit,
    onCopyDefinition: () -> Unit,
    onLookup: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // 词头
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = word,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )

                // TTS 按钮
                IconButton(onClick = { /* TODO: TTS */ }) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "发音",
                    )
                }
            }

            // 音标（如果有）
            entry.phonetic?.let { phonetic ->
                Text(
                    text = phonetic,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 词性（如果有）
            entry.partOfSpeech?.let { pos ->
                Text(
                    text = pos,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 释义内容
            val renderedDefinition = DictRenderer.render(
                definition = entry.definition,
                definitionType = entry.definitionType,
                isDarkMode = isSystemInDarkTheme(),
            )
            ClickableText(
                text = renderedDefinition,
                style = MaterialTheme.typography.bodyLarge,
                onClick = { offset ->
                    // 检测是否点击了 entry:// 链接
                    renderedDefinition.getStringAnnotations(
                        tag = DictMdxRenderer.LINK_TAG,
                        start = offset,
                        end = offset,
                    ).firstOrNull()?.let { annotation ->
                        onLookup(annotation.item)
                    }
                },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 操作按钮
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onCopyDefinition) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("复制")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onAddToWordBook) {
                    Icon(Icons.Outlined.BookmarkAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("加入生词本")
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    word: String,
    onLookup: (String) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
    ) {
        Icon(
            Icons.Outlined.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "未找到 \"$word\" 的释义",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "请检查拼写或尝试其他词典",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 前缀建议（如果有）
        // TODO: 集成 searchByPrefix
    }
}
