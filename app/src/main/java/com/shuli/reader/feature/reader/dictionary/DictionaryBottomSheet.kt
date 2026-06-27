package com.shuli.reader.feature.reader.dictionary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontStyle
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DictionaryContent(
    uiState: ReaderUiState,
    onLookup: (String) -> Unit,
    onAddToWordBook: (String) -> Unit,
    onCopyDefinition: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
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
            // 空状态（带前缀建议）
            EmptyState(
                word = word,
                onLookup = onLookup,
                onCopy = { onCopyDefinition(word) },
                suggestions = uiState.dictionarySuggestions,
            )
        } else if (results.size == 1) {
            // 单词典直接显示
            DefinitionCard(
                entry = results.first(),
                word = word,
                onAddToWordBook = { onAddToWordBook(word) },
                onCopyDefinition = { onCopyDefinition(results.first().definition) },
                onLookup = onLookup,
            )
        } else {
            // 多词典 Tab 切换
            val pagerState = rememberPagerState(pageCount = { results.size })

            // 使用 LazyColumn 实现 stickyHeader
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 词典徽章粘性吸顶
                stickyHeader {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ScrollableTabRow(
                            selectedTabIndex = pagerState.currentPage,
                            edgePadding = 0.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            results.forEachIndexed { index, entry ->
                                Tab(
                                    selected = pagerState.currentPage == index,
                                    onClick = {
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    },
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
                    }
                }

                // 释义内容
                item {
                    val entry = results[pagerState.currentPage]
                    DefinitionCard(
                        entry = entry,
                        word = word,
                        onAddToWordBook = { onAddToWordBook(word) },
                        onCopyDefinition = { onCopyDefinition(entry.definition) },
                        onLookup = onLookup,
                        contextSentence = contextSentence,
                    )
                }
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
    var isEditing by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // 进入编辑态时自动获取焦点
    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
        }
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isEditing = true },
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
                onValueChange = {
                    text = it
                    // 输入时实时搜索
                    if (it.isNotBlank()) {
                        onSearch(it)
                    }
                },
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                readOnly = !isEditing,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    onSearch(text)
                    isEditing = false
                }),
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
                onClick = {
                    if (isEditing) {
                        onSearch(text)
                        isEditing = false
                    } else {
                        isEditing = true
                    }
                },
                enabled = text.isNotBlank() || !isEditing,
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
    contextSentence: String = "",
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
            // 词头（自适应字号）
            val titleFontSize = when {
                word.length <= 4 -> MaterialTheme.typography.headlineSmall.fontSize
                word.length <= 6 -> MaterialTheme.typography.titleLarge.fontSize
                word.length <= 10 -> MaterialTheme.typography.titleMedium.fontSize
                else -> MaterialTheme.typography.titleSmall.fontSize
            }

            Text(
                text = word,
                fontSize = titleFontSize,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            // 上下文句子
            if (contextSentence.isNotBlank()) {
                Text(
                    text = "来自：「${contextSentence.take(50)}」",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(top = 4.dp),
                )
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
    onCopy: ((String) -> Unit)? = null,
    suggestions: List<String> = emptyList(),
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

        // 前缀匹配建议
        if (suggestions.isNotEmpty()) {
            Text(
                text = "你是不是要找：",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            suggestions.take(3).forEach { suggestion ->
                TextButton(onClick = { onLookup(suggestion) }) {
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 操作按钮
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 复制该词按钮
            if (onCopy != null) {
                OutlinedButton(onClick = { onCopy(word) }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("复制该词")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 引导文案
        Text(
            text = "可在「设置 → 词典管理」中导入更多词库",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
