package com.shuli.reader.feature.reader.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ── Design Tokens (墨土 MoTu 暖灰棕) ──
private val InkT50 = Color(0xFFF6F4F0)
private val InkT100 = Color(0xFFEAE5DC)
private val InkT200 = Color(0xFFD4CCC0)
private val InkT300 = Color(0xFFB9AFA0)
private val InkT400 = Color(0xFF9C9082)
private val InkT600 = Color(0xFF5E5346)
private val InkT700 = Color(0xFF453B2E)
private val InkT800 = Color(0xFF2C231A)
private val InkT900 = Color(0xFF1A130B)
private val WarningMain = Color(0xFF9A6500)
private val WarningBg = Color(0xFFF5ECD8)
private val HighlightOtherBg = Color(0x99F5ECD8) // rgba(245,236,216,0.6)
private val GlassBg = Color(0xD9F6F4F0) // rgba(246,244,240,0.85)

/**
 * 沉浸式阅读编辑器 (V2)
 *
 * 参照 edit-interface-demo.html 重构：
 * - 毛玻璃悬浮工具栏（替代 TopAppBar）
 * - 全屏文本编辑区
 * - 查找匹配高亮（当前焦点 + 其他匹配）
 * - 退出保护对话框
 * - 编辑记录底部面板
 * - 查找历史下拉菜单
 */
@Composable
fun ChapterEditorScreen(
    chapterTitle: String,
    chapterText: String,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
) {
    // ── 文本编辑状态 ──
    var textFieldValue by remember(chapterText) {
        mutableStateOf(TextFieldValue(text = chapterText))
    }
    val hasChanges = textFieldValue.text != chapterText

    // ── 撤销/重做栈 ──
    val undoStack = remember { ArrayDeque<TextFieldValue>() }
    val redoStack = remember { ArrayDeque<TextFieldValue>() }
    var lastSnapshotText by remember(chapterText) { mutableStateOf(chapterText) }

    // ── 查找/替换状态 ──
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var showReplace by remember { mutableStateOf(false) }
    var isRegex by remember { mutableStateOf(false) }
    var isCaseSensitive by remember { mutableStateOf(false) }
    var showSearchHistory by remember { mutableStateOf(false) }

    // ── 查找匹配 ──
    data class FindMatch(val start: Int, val end: Int)
    var matches by remember { mutableStateOf<List<FindMatch>>(emptyList()) }
    var currentMatchIndex by remember { mutableIntStateOf(-1) }

    // ── 编辑记录 ──
    data class EditRecord(val originalText: String, val newText: String, val timestamp: Long)
    val editRecords = remember { mutableStateListOf<EditRecord>() }
    var showHistory by remember { mutableStateOf(false) }

    // ── 查找历史 ──
    val searchHistory = remember { mutableStateListOf<String>() }

    // ── 退出保护 ──
    var showExitDialog by remember { mutableStateOf(false) }

    // ── 查找逻辑 ──
    fun performFind() {
        if (findText.isEmpty()) {
            matches = emptyList()
            currentMatchIndex = -1
            return
        }
        // 添加到搜索历史
        if (findText !in searchHistory) {
            searchHistory.add(0, findText)
            if (searchHistory.size > 20) searchHistory.removeAt(searchHistory.lastIndex)
        }
        val text = textFieldValue.text
        val results = mutableListOf<FindMatch>()
        try {
            if (isRegex) {
                val regex = if (isCaseSensitive) Regex(findText) else Regex(findText, RegexOption.IGNORE_CASE)
                regex.findAll(text).forEach { match ->
                    results.add(FindMatch(match.range.first, match.range.last + 1))
                }
            } else {
                var start = 0
                while (true) {
                    val idx = if (isCaseSensitive) text.indexOf(findText, start)
                    else text.indexOf(findText, start, ignoreCase = true)
                    if (idx < 0) break
                    results.add(FindMatch(idx, idx + findText.length))
                    start = idx + 1
                }
            }
        } catch (_: Exception) { /* regex error */ }
        matches = results
        currentMatchIndex = if (results.isNotEmpty()) 0 else -1
    }

    LaunchedEffect(findText, isRegex, isCaseSensitive, textFieldValue.text) {
        performFind()
    }

    // ── 替换逻辑 ──
    fun replaceCurrent() {
        if (currentMatchIndex < 0 || currentMatchIndex >= matches.size) return
        val match = matches[currentMatchIndex]
        val oldText = textFieldValue.text
        val newText = oldText.substring(0, match.start) + replaceText + oldText.substring(match.end)
        editRecords.add(EditRecord(
            originalText = oldText.substring(match.start, match.end),
            newText = replaceText,
            timestamp = System.currentTimeMillis(),
        ))
        undoStack.addLast(textFieldValue)
        redoStack.clear()
        textFieldValue = TextFieldValue(text = newText, selection = TextRange(match.start + replaceText.length))
        lastSnapshotText = newText
    }

    fun replaceAll() {
        if (matches.isEmpty()) return
        val oldText = textFieldValue.text
        var result = oldText
        for (match in matches.sortedByDescending { it.start }) {
            result = result.substring(0, match.start) + replaceText + result.substring(match.end)
        }
        editRecords.add(EditRecord(
            originalText = "(${matches.size} 处) $findText",
            newText = replaceText,
            timestamp = System.currentTimeMillis(),
        ))
        undoStack.addLast(textFieldValue)
        redoStack.clear()
        textFieldValue = TextFieldValue(text = result)
        lastSnapshotText = result
        matches = emptyList()
        currentMatchIndex = -1
    }

    // ── 撤销/重做 ──
    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(textFieldValue)
        textFieldValue = prev
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(textFieldValue)
        textFieldValue = next
    }

    fun onTextChanged(newValue: TextFieldValue) {
        if (newValue.text != textFieldValue.text && newValue.text != lastSnapshotText) {
            undoStack.addLast(textFieldValue)
            redoStack.clear()
            while (undoStack.size > 100) undoStack.removeFirst()
            lastSnapshotText = newValue.text
        }
        textFieldValue = newValue
    }

    fun navigateMatch(direction: Int) {
        if (matches.isEmpty()) return
        val newIndex = (currentMatchIndex + direction + matches.size) % matches.size
        currentMatchIndex = newIndex
        val match = matches[newIndex]
        textFieldValue = textFieldValue.copy(selection = TextRange(match.start, match.end))
    }

    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    // 匹配导航时自动滚动
    LaunchedEffect(currentMatchIndex, matches) {
        if (currentMatchIndex < 0 || currentMatchIndex >= matches.size) return@LaunchedEffect
        val match = matches[currentMatchIndex]
        val textBefore = textFieldValue.text.substring(0, match.start.coerceAtMost(textFieldValue.text.length))
        val estimatedLine = textBefore.count { it == '\n' }
        val lineHeightPx = with(density) { 24.sp.toPx() }
        val viewportHeight = scrollState.viewportSize
        val targetScroll = (estimatedLine * lineHeightPx - viewportHeight / 2f).toInt().coerceAtLeast(0)
        scrollState.animateScrollTo(targetScroll.coerceIn(0, scrollState.maxValue))
    }

    // ── 构建带高亮的文本 ──
    val highlightedText = remember(textFieldValue.text, matches, currentMatchIndex) {
        buildAnnotatedString {
            val text = textFieldValue.text
            if (matches.isEmpty()) {
                append(text)
                return@buildAnnotatedString
            }
            val sortedMatches = matches.sortedBy { it.start }
            var lastEnd = 0
            for ((index, match) in sortedMatches.withIndex()) {
                if (match.start > lastEnd) {
                    append(text.substring(lastEnd, match.start))
                }
                val isCurrent = index == currentMatchIndex
                if (isCurrent) {
                    withStyle(SpanStyle(
                        color = WarningMain,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        background = WarningBg,
                    )) {
                        append(text.substring(match.start, match.end))
                    }
                } else {
                    withStyle(SpanStyle(background = HighlightOtherBg)) {
                        append(text.substring(match.start, match.end))
                    }
                }
                lastEnd = match.end
            }
            if (lastEnd < text.length) {
                append(text.substring(lastEnd))
            }
        }
    }

    // ── 主布局 ──
    Box(modifier = Modifier.fillMaxSize().background(InkT50)) {
        // ── 正文编辑区 ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                .padding(top = 90.dp, bottom = 60.dp),
        ) {
            BasicTextField(
                value = if (matches.isNotEmpty()) {
                    // 用高亮文本替换显示（只读显示，编辑时切回纯文本）
                    textFieldValue.copy(text = highlightedText.text)
                } else {
                    textFieldValue
                },
                onValueChange = { onTextChanged(it) },
                textStyle = TextStyle(
                    fontSize = 17.sp,
                    lineHeight = 30.4.sp, // 17 * 1.8
                    color = InkT900,
                    letterSpacing = 0.5.sp,
                ),
                cursorBrush = SolidColor(InkT700),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ── 模拟顶栏（章节标题） ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(InkT50)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(
                onClick = {
                    if (hasChanges) showExitDialog = true
                    else onBack()
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = InkT900)
            }
            Text(
                text = chapterTitle,
                fontSize = 16.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = InkT600,
                maxLines = 1,
            )
        }

        // ── 悬浮工具栏（毛玻璃） ──
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .offset(y = 60.dp),
            shape = RoundedCornerShape(16.dp),
            color = GlassBg,
            shadowElevation = 8.dp,
            tonalElevation = 0.dp,
        ) {
            Column {
                // 查找行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // 输入框组
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(InkT100)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = InkT400, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        BasicTextField(
                            value = findText,
                            onValueChange = { findText = it; showSearchHistory = it.isEmpty() && searchHistory.isNotEmpty() },
                            textStyle = TextStyle(fontSize = 14.sp, color = InkT900),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { performFind() }),
                        )
                        // 正则/大小写 toggle
                        ToggleChip(label = ".*", active = isRegex) { isRegex = !isRegex }
                        ToggleChip(label = "Aa", active = isCaseSensitive) { isCaseSensitive = !isCaseSensitive }
                    }

                    // 匹配计数胶囊
                    if (matches.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = InkT100,
                            modifier = Modifier.border(1.dp, InkT200, RoundedCornerShape(6.dp)),
                        ) {
                            Text(
                                "${currentMatchIndex + 1}/${matches.size}",
                                fontSize = 12.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                color = InkT900,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }

                    // 导航按钮组
                    IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "上一个", matches.isNotEmpty()) { navigateMatch(-1) }
                    IconBtn(Icons.AutoMirrored.Filled.ArrowForward, "下一个", matches.isNotEmpty()) { navigateMatch(1) }
                    Box(Modifier.width(1.dp).height(16.dp).background(InkT200))
                    IconBtn(Icons.Filled.History, "编辑记录", true) { showHistory = !showHistory }
                    IconBtn(
                        Icons.Filled.FindReplace, "替换",
                        true,
                        tintOverride = if (showReplace) InkT700 else null,
                    ) { showReplace = !showReplace }
                    IconBtn(Icons.Filled.Close, "关闭") { findText = ""; matches = emptyList() }
                }

                // 替换行（可展开）
                AnimatedVisibility(
                    visible = showReplace,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(InkT100)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.FindReplace, contentDescription = null, tint = InkT400, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            BasicTextField(
                                value = replaceText,
                                onValueChange = { replaceText = it },
                                textStyle = TextStyle(fontSize = 14.sp, color = InkT900),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        OutlinedButton(
                            onClick = { replaceCurrent() },
                            enabled = matches.isNotEmpty(),
                            modifier = Modifier.height(32.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                        ) { Text("替换", fontSize = 13.sp) }
                        Button(
                            onClick = { replaceAll() },
                            enabled = matches.isNotEmpty(),
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = InkT700,
                                contentColor = Color.White,
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                        ) { Text("全部替换", fontSize = 13.sp) }
                    }
                }
            }
        }

        // ── 查找历史下拉菜单 ──
        AnimatedVisibility(
            visible = showSearchHistory && searchHistory.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .offset(y = 112.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = InkT50,
                shadowElevation = 12.dp,
                modifier = Modifier.border(1.dp, InkT200, RoundedCornerShape(12.dp)),
            ) {
                Column {
                    searchHistory.take(5).forEach { term ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    findText = term
                                    showSearchHistory = false
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Filled.History, contentDescription = null, tint = InkT400, modifier = Modifier.size(16.dp))
                            Text(term, fontSize = 14.sp, color = InkT900, modifier = Modifier.weight(1f))
                        }
                    }
                    androidx.compose.material3.HorizontalDivider(color = InkT200)
                    Text(
                        "清空搜索历史",
                        fontSize = 12.sp,
                        color = InkT600,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { searchHistory.clear(); showSearchHistory = false }
                            .padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }

        // ── 编辑记录底部面板 ──
        if (showHistory) {
            // 遮罩层
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x660C0804)) // rgba(12,8,4,0.4)
                    .clickable { showHistory = false },
            )
            // 面板
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(600.dp),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = InkT50,
                shadowElevation = 8.dp,
            ) {
                Column {
                    // 头部
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.History, contentDescription = null, tint = InkT900, modifier = Modifier.size(20.dp))
                            Text("编辑记录", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = InkT900)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = InkT100,
                            ) {
                                Text(
                                    "${editRecords.size}",
                                    fontSize = 12.sp,
                                    color = InkT600,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        IconButton(onClick = { showHistory = false }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "关闭", tint = InkT600)
                        }
                    }
                    androidx.compose.material3.HorizontalDivider(color = InkT200)
                    // 编辑记录列表
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        editRecords.reversed().forEachIndexed { index, record ->
                            DiffCard(
                                originalText = record.originalText,
                                newText = record.newText,
                                onUndo = {
                                    editRecords.removeAt(editRecords.size - 1 - index)
                                    undo()
                                },
                            )
                        }
                    }
                    // 底部操作栏
                    androidx.compose.material3.HorizontalDivider(color = InkT200)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                editRecords.clear()
                                undoStack.clear()
                                redoStack.clear()
                                textFieldValue = TextFieldValue(text = chapterText)
                                lastSnapshotText = chapterText
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("全部撤销", fontSize = 15.sp) }
                        Button(
                            onClick = { onSave(textFieldValue.text) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = InkT700, contentColor = Color.White),
                        ) { Text("保存修改", fontSize = 15.sp) }
                    }
                }
            }
        }

        // ── 退出保护对话框 ──
        if (showExitDialog) {
            Dialog(
                onDismissRequest = { showExitDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = InkT50,
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .width(300.dp)
                        .border(1.dp, InkT200, RoundedCornerShape(16.dp)),
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("退出编辑器", fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = InkT900)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "当前文件有 ${editRecords.size} 处未保存的文本编辑修改，直接退出将会丢失这些修改。",
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = InkT600,
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showExitDialog = false
                                    onBack()
                                },
                                shape = RoundedCornerShape(8.dp),
                            ) { Text("放弃修改", fontSize = 14.sp) }
                            Button(
                                onClick = {
                                    showExitDialog = false
                                    onSave(textFieldValue.text)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = InkT700, contentColor = Color.White),
                            ) { Text("保存并退出", fontSize = 14.sp) }
                        }
                    }
                }
            }
        }
    }
}

// ── 子组件 ──

@Composable
private fun ToggleChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) InkT200 else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = if (active) InkT700 else InkT600,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
    }
}

@Composable
private fun IconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    enabled: Boolean = true,
    tintOverride: Color? = null,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(28.dp),
    ) {
        Icon(
            icon,
            contentDescription = desc,
            tint = tintOverride ?: if (enabled) InkT600 else InkT300,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun DiffCard(
    originalText: String,
    newText: String,
    onUndo: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, InkT200),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Original line
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("-", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color(0xFF9B3525), modifier = Modifier.width(14.dp))
                    Text(
                        buildAnnotatedString {
                            append("…")
                            withStyle(SpanStyle(
                                background = Color(0xFFF5E5E2),
                                color = Color(0xFF9B3525),
                                textDecoration = TextDecoration.LineThrough,
                            )) { append(originalText) }
                            append("…")
                        },
                        fontSize = 13.sp,
                        lineHeight = 20.8.sp,
                        color = InkT600,
                    )
                }
                // Modified line
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("+", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color(0xFF2D7A52), modifier = Modifier.width(14.dp))
                    Text(
                        buildAnnotatedString {
                            append("…")
                            withStyle(SpanStyle(
                                background = Color(0xFFE8F4EE),
                                color = Color(0xFF2D7A52),
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            )) { append(newText) }
                            append("…")
                        },
                        fontSize = 13.sp,
                        lineHeight = 20.8.sp,
                        color = InkT900,
                    )
                }
            }
            IconButton(
                onClick = onUndo,
                modifier = Modifier.size(28.dp).border(1.dp, InkT200, RoundedCornerShape(6.dp)),
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "撤销", tint = InkT600, modifier = Modifier.size(16.dp))
            }
        }
    }
}
