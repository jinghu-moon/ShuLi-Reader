# 阅读界面配置扩展设计文档

> 版本：v1.0  
> 创建日期：2026-06-10  
> 作者：Qoder AI

## 1. 现状盘点

### 1.1 现有配置架构

当前阅读界面已实现 **50+ 个配置项**，分布在四个 Tab 中：

#### 排版 Tab (Typography)
- 字号 (fontSize): 12-32sp
- 行距 (lineSpacing): 1.0-3.0
- 段距 (paragraphSpacing): 0-2.0
- 缩进 (indent): 0-10em
- 缩进单位 (indentUnit): 字符/像素
- 字距 (letterSpacing): -0.1-0.5em
- 上下边距 (marginVertical): 0-80dp
- 左右边距 (marginHorizontal): 0-60dp
- 页面最大宽度 (maxPageWidth): 0-1200dp

#### 字体 Tab (Font)
- 翻页动画 (pageAnimType): 覆盖/滑动/仿真/淡入淡出
- 字体 (readingFont): HarmonyOS Sans SC / 系统字体 / 自定义
- 字重 (fontWeight): 细/常规/中等/粗
- 对齐 (textAlign): 左对齐/两端对齐
- 简繁转换 (chineseConvert): 无/简体/繁体
- 中文分行 (useZhLayout): 标点避头尾
- 盘古空格 (usePanguSpacing): 中英自动加空格
- 底部对齐 (bottomJustify): 均匀分布行间距
- 去空行 (removeEmptyLines)
- 清理章节标题 (cleanChapterTitle)
- EPUB 覆盖样式 (epubOverrideStyle)

#### 页面 Tab (Page)
- 页眉开关 (header.visibility)
- 页脚开关 (footer.visibility)
- 进度条开关 (showProgress)
- 进度样式 (progressStyle): 章节分数/章节百分比/页码/全书分数/全书百分比
- 页眉脚透明度 (headerFooterAlpha): 0.1-1.0
- 自动夜间模式 (autoNightMode)
- 页眉脚自定义 (6 个 Slot): 书名/章节/进度/时间/电池/自定义
- 页眉脚分割线 (showHeaderLine, showFooterLine)
- 页眉脚字号比 (headerFontSizeRatio, footerFontSizeRatio)
- 标题对齐/字号/上下间距 (titleStyle)
- 保持亮屏 (keepScreenOn)
- 音量键翻页 (volumeKeyTurnPage)
- 边缘翻页 + 宽度 (edgeTurnPage, edgeWidthPercent)
- 预设管理 (ReaderPresetEntity)

#### 交互 Tab (Interaction)
- 亮度 (brightness): -1-1.0
- 翻页模式 (pageAnimType)
- 音量键翻页 (volumeKeyTurnPage)
- 保持亮屏 (keepScreenOn)
- 沉浸模式 (immersiveMode)
- 边缘翻页 + 宽度 (edgeTurnPage, edgeWidthPercent)
- 左侧触控热区比例 (leftZoneRatio): 0.2-0.5
- 自动翻页 + 间隔 (autoPageTurn, autoPageTurnInterval): 5-60秒

### 1.2 核心渲染管线

```
ReaderPreferences (DataStore)
    ↓ UserPreferences
ReaderViewModel.layoutConfigFor()
    ↓ ReaderLayoutConfig (px 单位)
Paginator.paginateChapter()
    ↓ TextPage[]
ReaderPageRenderer.render()
    ↓ Canvas
三层 Recorder 架构 (shell/content/overlay)
    ↓
ReaderCanvasView 合成
```

**性能基线：**
- 单页渲染：≤ 8ms (中端设备)
- 翻页动画：60fps 稳定
- 首屏显示：≤ 300ms

### 1.3 现有功能盲区

通过对比 KOReader、Moon+ Reader、多看阅读、微信读书等竞品，识别以下功能空白：

1. **护眼功能**：色温调节、蓝光过滤、护眼提醒
2. **高级排版**：断字连字、词间距、独立边距、段间分隔线
3. **阅读辅助**：Bionic Reading、竖排阅读、双页模式、阅读聚焦线
4. **交互定制**：手势绑定、双击操作、振动反馈、方向锁定
5. **TTS 朗读**：完整 TTS 引擎（已有目录预留）
6. **内容处理**：正则替换、广告过滤

---

## 2. 功能设计方案

### 2.1 护眼功能 (Eye Care)

#### 2.1.1 色温调节 (Color Temperature)

**方案类型：增量**  
**复杂度：低**  
**性能影响：可忽略**

**设计：**
```kotlin
// UserPreferences 新增字段
val colorTemperature: Flow<Float> = dataStore.data
    .map { it[COLOR_TEMPERATURE] ?: 6500f }

// ReaderCanvasView 新增覆盖层
if (colorTemperature < 6500f) {
    val alpha = ((6500f - colorTemperature) / 3500f * 128).toInt()
    canvas.drawColor(Color.argb(alpha, 255, 147, 41), PorterDuff.Mode.SRC_ATOP)
}
```

**UI 交互：**
- 色温滑块：3000K-6500K
- 实时预览（不触发 reflow）
- 与亮度滑块并列显示

**实现要点：**
- 使用 `drawColor()` + `SRC_ATOP` 模式，仅影响已绘制像素
- 不修改 ReaderPreferences，避免触发 reflow
- 渲染开销：单次 drawColor 调用，< 0.1ms

---

#### 2.1.2 蓝光过滤 (Blue Light Filter)

**方案类型：增量**  
**复杂度：低**  
**性能影响：可忽略**

**设计：**
```kotlin
// 复用色温机制，但使用固定值
val blueLightFilter: Flow<Boolean> = dataStore.data
    .map { it[BLUE_LIGHT_FILTER] ?: false }

// 开启时强制色温 = 3400K
val effectiveTemp = if (blueLightFilter) 3400f else colorTemperature
```

**UI 交互：**
- 开关按钮（快捷面板）
- 开启时色温锁定为 3400K（护眼模式标准值）
- 与色温滑块互斥（开启蓝光过滤时禁用色温滑块）

**实现要点：**
- 复用色温渲染逻辑，零额外代码
- 3400K 对应 RGB(255, 147, 41)，alpha = 128

---

#### 2.1.3 护眼提醒 (Eye Care Reminder)

**方案类型：增量**  
**复杂度：中**  
**性能影响：无**

**设计：**
```kotlin
// UserPreferences 新增字段
val eyeCareReminderInterval: Flow<Int> = dataStore.data
    .map { it[EYE_CARE_REMINDER_INTERVAL] ?: 0 }  // 分钟，0 = 禁用

// ReaderViewModel 新增计时器
private var readingStartTime = System.currentTimeMillis()
private val eyeCareJob = viewModelScope.launch {
    val interval = eyeCareReminderInterval.first()
    if (interval > 0) {
        delay(interval * 60_000L)
        _eyeCareReminderVisible.value = true
    }
}
```

**UI 交互：**
- 设置项：提醒间隔（15/30/45/60 分钟，禁用）
- 提醒弹窗：半透明遮罩 + "休息一下"按钮
- 点击"休息"：暂停计时器，显示 20-20-20 规则提示

**实现要点：**
- 使用 `viewModelScope.launch` + `delay`，轻量级
- 翻页/设置操作时重置计时器
- 弹窗使用 `Dialog` Composable，不影响渲染管线

---

### 2.2 高级排版 (Advanced Typography)

#### 2.2.1 断字连字 (Hyphenation)

**方案类型：重构**  
**复杂度：高**  
**性能影响：中（需评估）**

**设计：**
```kotlin
// UserPreferences 新增字段
val hyphenation: Flow<Boolean> = dataStore.data
    .map { it[HYPHENATION] ?: false }

// Paginator 改造
private fun calculateLine(...): LineResult {
    // 现有逻辑...
    
    // 新增：断字处理
    if (hyphenation && isEnglishWord(currentWord)) {
        val breakPoints = findHyphenationPoints(currentWord)
        if (breakPoints.isNotEmpty() && canFitWithHyphen(breakPoints)) {
            // 插入连字符，分割单词
        }
    }
}

// 断字算法
private fun findHyphenationPoints(word: String): List<Int> {
    // Liang's algorithm (TeX 断字算法)
    // 需要加载断字模式表（约 50KB，按语言）
    return HyphenationEngine.hyphenate(word, locale)
}
```

**UI 交互：**
- 开关按钮（字体 Tab）
- 仅对英文文本生效（中文无需断字）
- 开启后触发 reflow

**实现要点：**
- 集成 Liang 断字算法（MIT 许可，已有 Kotlin 实现）
- 断字模式表按语言加载（英语/法语/德语等）
- 性能评估：单页断字计算 < 2ms，可接受
- **重构理由**：需改造 `Paginator.calculateLine()`，但收益明显（英文排版质量提升）

---

#### 2.2.2 词间距 (Word Spacing)

**方案类型：增量**  
**复杂度：低**  
**性能影响：可忽略**

**设计：**
```kotlin
// UserPreferences 新增字段
val wordSpacing: Flow<Float> = dataStore.data
    .map { it[WORD_SPACING] ?: 0f }  // em 单位，范围 -0.2 ~ 0.5

// Paginator 改造（在 calculateLine 中应用）
val spaceWidth = measureSpaceWidth(textPaint) + wordSpacing * fontSize
```

**UI 交互：**
- 滑块：-0.2em ~ 0.5em
- 负值紧凑，正值宽松
- 调整后触发 reflow

**实现要点：**
- 仅修改空格字符宽度，不影响其他字符
- 与 `letterSpacing`（字距）独立
- 渲染时无需额外逻辑（空格宽度已在分页时确定）

---

#### 2.2.3 独立边距 (Independent Margins)

**方案类型：重构**  
**复杂度：中**  
**性能影响：无**

**设计：**
```kotlin
// 废弃现有字段
// val marginVertical: Flow<Float>
// val marginHorizontal: Flow<Float>

// 新增四个独立字段
val marginTop: Flow<Float> = dataStore.data.map { it[MARGIN_TOP] ?: 30f }
val marginBottom: Flow<Float> = dataStore.data.map { it[MARGIN_BOTTOM] ?: 30f }
val marginLeft: Flow<Float> = dataStore.data.map { it[MARGIN_LEFT] ?: 20f }
val marginRight: Flow<Float> = dataStore.data.map { it[MARGIN_RIGHT] ?: 20f }

// ReaderLayoutConfig 更新
data class ReaderLayoutConfig(
    val marginTop: Float,
    val marginBottom: Float,
    val marginLeft: Float,
    val marginRight: Float,
    // ... 其他字段
)

// Paginator 适配
val contentWidth = pageWidth - marginLeft - marginRight
val contentHeight = pageHeight - marginTop - marginBottom
```

**UI 交互：**
- 四个独立滑块（排版 Tab）
- 保留"同步边距"开关（开启时联动上下/左右）
- 调整后触发 reflow

**实现要点：**
- **重构理由**：现有 `marginVertical` / `marginHorizontal` 不够灵活
- 数据迁移：旧字段值同时赋给 top/bottom 或 left/right
- Paginator 改动量：约 20 处坐标计算
- 收益：支持非对称边距（如左侧留白做笔记）

---

#### 2.2.4 段间分隔线 (Paragraph Divider)

**方案类型：增量**  
**复杂度：低**  
**性能影响：低**

**设计：**
```kotlin
// UserPreferences 新增字段
val paragraphDivider: Flow<Boolean> = dataStore.data
    .map { it[PARAGRAPH_DIVIDER] ?: false }

// ReaderPageRenderer 改造
private fun drawParagraphDivider(canvas: Canvas, y: Float) {
    if (!paragraphDivider) return
    
    dividerPaint.color = Color.argb(64, 0, 0, 0)  // 半透明灰
    dividerPaint.strokeWidth = 1f
    
    val dividerY = y + paragraphSpacing / 2
    canvas.drawLine(
        marginLeft + indent,
        dividerY,
        pageWidth - marginRight - indent,
        dividerY,
        dividerPaint
    )
}

// 在 renderContent 中调用
lines.forEachIndexed { index, line ->
    // 绘制文本...
    if (line.isParagraphEnd && index < lines.size - 1) {
        drawParagraphDivider(canvas, line.baseline)
    }
}
```

**UI 交互：**
- 开关按钮（排版 Tab）
- 开启后触发 reflow（需重新计算段距空间）

**实现要点：**
- 分隔线绘制在 `contentRecorder` 层
- 性能开销：每段一次 `drawLine`，< 0.5ms
- 与 `paragraphSpacing` 配合：分隔线居中于段距空间

---

### 2.3 阅读辅助 (Reading Assistance)

#### 2.3.1 Bionic Reading

**方案类型：增量**  
**复杂度：中**  
**性能影响：中（需评估）**

**设计：**
```kotlin
// UserPreferences 新增字段
val bionicReading: Flow<Boolean> = dataStore.data
    .map { it[BIONIC_READING] ?: false }

// ReaderPageRenderer 改造
private fun drawBionicText(canvas: Canvas, line: TextLine, x: Float, y: Float) {
    if (!bionicReading) {
        // 常规绘制
        canvas.drawText(pageContent, line.startOffset, line.endOffset, x, y, textPaint)
        return
    }
    
    // Bionic 模式：加粗每个单词的前半部分
    var currentX = x
    val words = extractWords(line)
    
    words.forEach { word ->
        val boldLength = (word.text.length * 0.5f).toInt().coerceAtLeast(1)
        
        // 绘制加粗部分
        textPaint.typeface = Typeface.create(readingFont, Typeface.BOLD)
        canvas.drawText(word.text, 0, boldLength, currentX, y, textPaint)
        currentX += textPaint.measureText(word.text, 0, boldLength)
        
        // 绘制常规部分
        textPaint.typeface = Typeface.create(readingFont, Typeface.NORMAL)
        canvas.drawText(word.text, boldLength, word.text.length, currentX, y, textPaint)
        currentX += textPaint.measureText(word.text, boldLength, word.text.length)
    }
}
```

**UI 交互：**
- 开关按钮（字体 Tab）
- 仅对英文文本生效
- 开启后触发 reflow（需重新计算字宽）

**实现要点：**
- 单词提取：按空格/标点分割
- 加粗比例：50%（可调，但初期固定）
- **性能风险**：每个单词 2 次 drawText + 2 次 measureText，约 2x 渲染时间
- **优化方案**：预计算加粗宽度，缓存到 TextLine（需扩展数据模型）
- 建议：先在高端设备测试，再决定是否默认开启

---

#### 2.3.2 竖排阅读 (Vertical Text)

**方案类型：重构**  
**复杂度：极高**  
**性能影响：高（需全面评估）**

**设计：**
```kotlin
// UserPreferences 新增字段
val verticalText: Flow<Boolean> = dataStore.data
    .map { it[VERTICAL_TEXT] ?: false }

// Paginator 全面重构
class VerticalPaginator {
    fun paginateChapter(...): List<TextPage> {
        // 列优先布局：从右向左，从上向下
        val columns = mutableListOf<TextColumn>()
        var currentColumn = 0
        var currentY = 0f
        
        while (hasMoreContent) {
            val column = paginateColumn(currentColumn, currentY)
            columns.add(column)
            
            currentY += columnHeight
            if (currentY > contentHeight) {
                currentColumn++
                currentY = 0f
            }
        }
        
        return columns.chunked(columnsPerPage).map { TextPage(it) }
    }
}

// TextPage 数据模型扩展
data class TextPage(
    val columns: List<TextColumn>,  // 新增列数据
    val isVertical: Boolean,
    // ...
)

data class TextColumn(
    val x: Float,
    val lines: List<TextLine>,
    // ...
)

// ReaderPageRenderer 适配
private fun renderVerticalPage(canvas: Canvas, page: TextPage) {
    page.columns.forEach { column ->
        column.lines.forEach { line ->
            // 竖排文字绘制：每个字符旋转 90°
            canvas.save()
            canvas.rotate(90f, line.x, line.y)
            canvas.drawText(...)
            canvas.restore()
        }
    }
}
```

**UI 交互：**
- 开关按钮（字体 Tab，高级选项）
- 开启后触发完整 reflow
- 翻页方向：从右向左（模拟古籍）

**实现要点：**
- **重构理由**：现有 Paginator 完全基于行优先布局，无法增量改造
- 需新增 `VerticalPaginator` 类（约 800 行）
- TextPage 数据模型扩展：新增列概念
- 标点旋转：全角标点需旋转 90°（如「」→「」）
- 性能预估：渲染时间 2-3x（字符旋转 + 列布局）
- **建议**：作为实验性功能，默认隐藏

---

#### 2.3.3 双页模式 (Dual Page)

**方案类型：增量**  
**复杂度：中**  
**性能影响：中**

**设计：**
```kotlin
// UserPreferences 新增字段
val dualPageMode: Flow<Boolean> = dataStore.data
    .map { it[DUAL_PAGE_MODE] ?: false }

// ReaderViewModel 改造
private fun getPagePair(currentIndex: Int): Pair<TextPage?, TextPage?> {
    val leftPage = if (currentIndex % 2 == 0) pages[currentIndex] else pages[currentIndex - 1]
    val rightPage = if (currentIndex % 2 == 0) pages.getOrNull(currentIndex + 1) else pages[currentIndex]
    return leftPage to rightPage
}

// ReaderScreen 布局改造
Row(modifier = Modifier.fillMaxSize()) {
    if (dualPageMode && isLandscape) {
        // 双页布局
        ReaderCanvasView(
            page = leftPage,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(1.dp).background(Color.Gray))  // 中缝
        ReaderCanvasView(
            page = rightPage,
            modifier = Modifier.weight(1f)
        )
    } else {
        // 单页布局
        ReaderCanvasView(page = currentPage)
    }
}
```

**UI 交互：**
- 开关按钮（页面 Tab）
- 仅横屏时生效（竖屏强制单页）
- 翻页时同时翻两页

**实现要点：**
- 两个独立的 `ReaderCanvasView`，各自维护 Recorder
- 中缝装饰：1dp 灰线 + 可选阴影
- 性能：2x 渲染时间，但两个 View 并行绘制，实际开销 < 1.5x
- 横屏检测：`LocalConfiguration.current.orientation`

---

#### 2.3.4 阅读聚焦线 (Focus Line)

**方案类型：增量**  
**复杂度：低**  
**性能影响：低**

**设计：**
```kotlin
// UserPreferences 新增字段
val focusLine: Flow<Boolean> = dataStore.data
    .map { it[FOCUS_LINE] ?: false }

// ReaderCanvasView 改造
private var currentLineIndex = 0  // 当前阅读行索引

// 基于阅读进度更新
fun updateFocusLine(readingProgress: Float) {
    val page = currentPage ?: return
    currentLineIndex = (readingProgress * page.lines.size).toInt()
    overlayRecorder.invalidate()  // 触发覆盖层重绘
}

// 绘制聚焦线
private fun drawFocusLine(canvas: Canvas) {
    if (!focusLine) return
    
    val line = currentPage?.lines?.getOrNull(currentLineIndex) ?: return
    val y = line.baseline + line.height / 2
    
    focusLinePaint.color = Color.argb(32, 0, 122, 255)  // 半透明蓝
    focusLinePaint.strokeWidth = 2f
    
    canvas.drawLine(0f, y, pageWidth.toFloat(), y, focusLinePaint)
}
```

**UI 交互：**
- 开关按钮（交互 Tab）
- 聚焦线随阅读进度自动移动
- 颜色/透明度可调（高级选项）

**实现要点：**
- 绘制在 `overlayRecorder` 层，不影响内容层
- 阅读进度来源：`ReadingProgressRepository`
- 性能开销：单次 drawLine，< 0.1ms

---

### 2.4 交互定制 (Interaction Customization)

#### 2.4.1 手势绑定 (Gesture Binding)

**方案类型：重构**  
**复杂度：高**  
**性能影响：无**

**设计：**
```kotlin
// 新增手势配置
enum class GestureAction {
    NONE,
    TOGGLE_TOOLBAR,
    NEXT_PAGE,
    PREV_PAGE,
    BRIGHTNESS_UP,
    BRIGHTNESS_DOWN,
    VOLUME_UP,
    VOLUME_DOWN,
    SCREENSHOT,
    BOOKMARK
}

data class GestureConfig(
    val doubleTap: GestureAction = GestureAction.TOGGLE_TOOLBAR,
    val swipeUp: GestureAction = GestureAction.BRIGHTNESS_UP,
    val swipeDown: GestureAction = GestureAction.BRIGHTNESS_DOWN,
    val swipeLeft: GestureAction = GestureAction.NEXT_PAGE,
    val swipeRight: GestureAction = GestureAction.PREV_PAGE,
    val longPress: GestureAction = GestureAction.BOOKMARK
)

// UserPreferences 新增字段
val gestureConfig: Flow<GestureConfig> = dataStore.data
    .map { prefs ->
        GestureConfig(
            doubleTap = prefs[DOUBLE_TAP_ACTION]?.let { GestureAction.valueOf(it) } ?: GestureAction.TOGGLE_TOOLBAR,
            // ...
        )
    }

// ReaderCanvasView 手势处理
private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
    override fun onDoubleTap(e: MotionEvent): Boolean {
        executeAction(gestureConfig.doubleTap)
        return true
    }
    
    override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        val dx = e2.x - e1.x
        val dy = e2.y - e1.y
        
        when {
            abs(dx) > abs(dy) -> {
                if (dx > 0) executeAction(gestureConfig.swipeRight)
                else executeAction(gestureConfig.swipeLeft)
            }
            else -> {
                if (dy > 0) executeAction(gestureConfig.swipeDown)
                else executeAction(gestureConfig.swipeUp)
            }
        }
        return true
    }
    
    override fun onLongPress(e: MotionEvent) {
        executeAction(gestureConfig.longPress)
    }
})

private fun executeAction(action: GestureAction) {
    when (action) {
        GestureAction.TOGGLE_TOOLBAR -> toggleToolbar()
        GestureAction.NEXT_PAGE -> nextPage()
        GestureAction.BRIGHTNESS_UP -> adjustBrightness(0.1f)
        // ...
    }
}
```

**UI 交互：**
- 交互 Tab 新增"手势设置"分组
- 每个手势独立下拉选择（6 个手势 × 10 个动作）
- 预览模式：点击手势显示动画示意

**实现要点：**
- **重构理由**：现有 `TouchZoneCalculator` 基于 3x3 网格，无法表达滑动/双击
- 新增 `GestureDetector`，替代部分 `onTouchEvent` 逻辑
- 与翻页动画冲突处理：滑动翻页优先于手势绑定
- 性能：手势检测在主线程，但计算量极小

---

#### 2.4.2 振动反馈 (Haptic Feedback)

**方案类型：增量**  
**复杂度：低**  
**性能影响：无**

**设计：**
```kotlin
// UserPreferences 新增字段
val hapticFeedback: Flow<Boolean> = dataStore.data
    .map { it[HAPTIC_FEEDBACK] ?: true }

// ReaderViewModel 新增方法
private val vibrator = context.getSystemService<Vibrator>()

fun triggerHaptic(duration: Long = 10) {
    if (hapticFeedback.value) {
        vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

// 翻页时触发
fun nextPage() {
    triggerHaptic()
    pageDelegate.nextPage()
}
```

**UI 交互：**
- 开关按钮（交互 Tab）
- 振动强度可调（高级选项）

**实现要点：**
- 使用 `Vibrator` 系统服务
- 默认 10ms 短振动（不干扰阅读）
- 权限：需在 AndroidManifest 声明 `VIBRATE`

---

#### 2.4.3 方向锁定 (Orientation Lock)

**方案类型：增量**  
**复杂度：低**  
**性能影响：无**

**设计：**
```kotlin
// UserPreferences 新增字段
val orientationLock: Flow<Boolean> = dataStore.data
    .map { it[ORIENTATION_LOCK] ?: false }

// MainActivity 适配
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    lifecycleScope.launch {
        userPreferences.orientationLock.collect { locked ->
            requestedOrientation = if (locked) {
                // 锁定为当前方向
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            } else {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
}
```

**UI 交互：**
- 快捷面板图标（锁定/解锁）
- 状态持久化

**实现要点：**
- 使用 `Activity.requestedOrientation` API
- 锁定为当前方向（非固定竖屏/横屏）
- 与系统"自动旋转"设置独立

---

### 2.5 TTS 朗读 (Text-to-Speech)

#### 2.5.1 TTS 引擎集成

**方案类型：新增模块**  
**复杂度：高**  
**性能影响：低（后台线程）**

**设计：**
```kotlin
// 新增 core/tts/ 模块
class TtsManager(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    
    suspend fun initialize() {
        tts = suspendCoroutine { cont ->
            TextToSpeech(context) { status ->
                isInitialized = (status == TextToSpeech.SUCCESS)
                cont.resume(tts)
            }
        }
    }
    
    fun speak(text: String, utteranceId: String) {
        if (!isInitialized) return
        
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        
        tts?.speak(text, TextToSpeech.QUEUE_ADD, params, utteranceId)
    }
    
    fun setSpeed(speed: Float) {
        tts?.setSpeechRate(speed)
    }
    
    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch)
    }
    
    fun stop() {
        tts?.stop()
    }
    
    fun shutdown() {
        tts?.shutdown()
    }
}

// ReaderViewModel 集成
private val ttsManager = TtsManager(context)
private val ttsJob = viewModelScope.launch {
    ttsManager.initialize()
}

fun startTts() {
    val page = currentPage ?: return
    val text = page.lines.joinToString("\n") { line ->
        pageContent.substring(line.startOffset, line.endOffset)
    }
    ttsManager.speak(text, "page_${page.index}")
}

fun stopTts() {
    ttsManager.stop()
}
```

**UI 交互：**
- 阅读界面底部新增 TTS 控制栏（可选显示）
- 播放/暂停/停止按钮
- 语速滑块：0.5x - 3.0x
- 音调滑块：0.5x - 2.0x
- 语音选择（系统 TTS 引擎提供的语音列表）

**实现要点：**
- 使用 Android 内置 `TextToSpeech` API（无需第三方库）
- TTS 在后台线程运行，不阻塞 UI
- 语音选择：通过 `tts.voices` 获取可用语音列表
- 自动翻页：TTS 完成当前页后自动翻到下一页（可选）

---

#### 2.5.2 TTS 高亮同步 (TTS Highlight)

**方案类型：增量**  
**复杂度：中**  
**性能影响：低**

**设计：**
```kotlin
// TtsManager 扩展
class TtsManager {
    private var currentUtteranceId: String? = null
    
    fun setUtteranceProgressListener(listener: UtteranceProgressListener) {
        tts?.setOnUtteranceProgressListener(listener)
    }
}

// ReaderViewModel 集成
private val ttsHighlightJob = viewModelScope.launch {
    ttsManager.setUtteranceProgressListener(object : UtteranceProgressListener() {
        override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
            // 更新高亮范围
            _ttsHighlightRange.value = start..end
        }
        
        override fun onDone(utteranceId: String) {
            // 清除高亮，翻页
            _ttsHighlightRange.value = null
            if (autoNextPage) nextPage()
        }
    })
}

// ReaderPageRenderer 适配
private fun drawTtsHighlight(canvas: Canvas, line: TextLine) {
    val highlightRange = ttsHighlightRange.value ?: return
    
    if (line.startOffset <= highlightRange.last && line.endOffset >= highlightRange.first) {
        highlightPaint.color = Color.argb(64, 255, 235, 59)  // 半透明黄
        canvas.drawRect(
            line.x, line.y,
            line.x + line.width, line.y + line.height,
            highlightPaint
        )
    }
}
```

**UI 交互：**
- TTS 播放时自动高亮当前朗读文本
- 高亮颜色：半透明黄（可调）

**实现要点：**
- 使用 `UtteranceProgressListener.onRangeStart` 获取当前朗读位置
- 高亮绘制在 `overlayRecorder` 层
- 性能开销：单次 drawRect，< 0.1ms

---

### 2.6 内容处理 (Content Processing)

#### 2.6.1 正则替换 (Regex Replacement)

**方案类型：增量**  
**复杂度：中**  
**性能影响：中（需评估）**

**设计：**
```kotlin
// 新增数据模型
data class RegexRule(
    val pattern: String,
    val replacement: String,
    val enabled: Boolean = true
)

// UserPreferences 新增字段
val regexRules: Flow<List<RegexRule>> = dataStore.data
    .map { prefs ->
        prefs[REGEX_RULES]?.let { json ->
            Json.decodeFromString<List<RegexRule>>(json)
        } ?: emptyList()
    }

// TextPreprocessor 新增模块
class TextPreprocessor {
    fun applyRegexRules(text: String, rules: List<RegexRule>): String {
        var result = text
        rules.filter { it.enabled }.forEach { rule ->
            try {
                val regex = Regex(rule.pattern)
                result = regex.replace(result, rule.replacement)
            } catch (e: Exception) {
                // 正则语法错误，跳过
            }
        }
        return result
    }
}

// Paginator 集成
fun preprocessChapter(content: String): String {
    var processed = content
    processed = textPreprocessor.removeAds(processed)
    processed = textPreprocessor.applyRegexRules(processed, regexRules.value)
    return processed
}
```

**UI 交互：**
- 新增"内容处理"设置页（独立于四个 Tab）
- 规则列表：添加/编辑/删除/排序
- 每条规则：正则表达式 + 替换文本 + 启用开关
- 预览模式：输入示例文本，显示替换结果

**实现要点：**
- 正则编译缓存：避免每次分页重新编译
- 性能评估：10 条规则 × 100KB 文本 ≈ 50ms（可接受）
- 安全性：限制正则复杂度（禁止回溯爆炸）
- 存储：规则列表序列化为 JSON，存入 DataStore

---

#### 2.6.2 广告过滤 (Ad Filtering)

**方案类型：增量**  
**复杂度：低**  
**性能影响：低**

**设计：**
```kotlin
// UserPreferences 新增字段
val adFiltering: Flow<Boolean> = dataStore.data
    .map { it[AD_FILTERING] ?: false }

// TextPreprocessor 新增模块
class TextPreprocessor {
    private val adPatterns = listOf(
        Regex("点击.*下载"),
        Regex("扫码关注.*"),
        Regex("关注公众号.*"),
        Regex("加入.*群"),
        Regex("www\\..*\\.com"),
        Regex("http[s]?://.*"),
        // ... 更多模式
    )
    
    fun removeAds(text: String): String {
        if (!adFiltering) return text
        
        var result = text
        adPatterns.forEach { pattern ->
            result = pattern.replace(result, "")
        }
        return result
    }
}
```

**UI 交互：**
- 开关按钮（内容处理页）
- 预设规则（不可编辑）
- 统计：显示已过滤的广告数量

**实现要点：**
- 预设 20-30 条常见广告模式
- 性能：正则匹配 < 20ms（100KB 文本）
- 误杀风险：部分正则可能误删正常文本，提供"撤销"按钮

---

## 3. 实施路线图

### Phase 1：低风险增量 (2 周)
- [ ] 2.1.1 色温调节
- [ ] 2.1.2 蓝光过滤
- [ ] 2.1.3 护眼提醒
- [ ] 2.2.2 词间距
- [ ] 2.2.4 段间分隔线
- [ ] 2.3.4 阅读聚焦线
- [ ] 2.4.2 振动反馈
- [ ] 2.4.3 方向锁定

**验收标准：**
- 所有功能在 60fps 下稳定运行
- 无 reflow 触发（除词间距/段间分隔线）
- 单元测试覆盖率 ≥ 80%

### Phase 2：中等复杂度增量 (3 周)
- [ ] 2.3.1 Bionic Reading
- [ ] 2.3.3 双页模式
- [ ] 2.6.2 广告过滤
- [ ] 2.5.1 TTS 引擎集成（基础）
- [ ] 2.5.2 TTS 高亮同步

**验收标准：**
- Bionic Reading 渲染时间 < 15ms（中端设备）
- 双页模式横屏切换流畅，无闪烁
- TTS 播放延迟 < 500ms

### Phase 3：重构与高级功能 (4 周)
- [ ] 2.2.1 断字连字
- [ ] 2.2.3 独立边距
- [ ] 2.4.1 手势绑定
- [ ] 2.6.1 正则替换

**验收标准：**
- 断字连字英文排版质量提升（主观评估）
- 独立边距数据迁移无丢失
- 手势绑定响应延迟 < 100ms

### Phase 4：实验性功能 (持续)
- [ ] 2.3.2 竖排阅读

**验收标准：**
- 竖排渲染正确（中日韩文）
- 性能可接受（渲染时间 < 30ms）
- 作为实验性功能，默认隐藏

---

## 4. 性能保障策略

### 4.1 性能预算

| 操作 | 预算 | 当前基线 |
|---|---|---|
| 单页渲染 | ≤ 10ms | 8ms |
| 翻页动画 | 60fps | 60fps |
| Reflow (单页) | ≤ 50ms | 30ms |
| 首屏显示 | ≤ 300ms | 280ms |

### 4.2 性能测试

**自动化基准测试：**
```kotlin
@RunWith(AndroidJUnit4::class)
class RenderingBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()
    
    @Test
    fun renderPage() {
        val page = createTestPage()
        val renderer = ReaderPageRenderer(...)
        
        benchmarkRule.measureRepeated {
            renderer.render(canvas, page)
        }
    }
}
```

**手动测试清单：**
- [ ] 连续翻页 100 次，无卡顿
- [ ] 开启所有功能，渲染时间 < 15ms
- [ ] 低端设备（2GB RAM）稳定运行

### 4.3 性能优化手段

1. **延迟初始化**：TTS 引擎、断字模式表按需加载
2. **缓存复用**：正则编译结果、断字计算结果
3. **异步处理**：内容预处理在后台线程
4. **降级策略**：性能不足时自动禁用高开销功能（如 Bionic Reading）

---

## 5. 数据迁移策略

### 5.1 UserPreferences 迁移

**新增字段：** 无需迁移，使用默认值

**废弃字段：**
```kotlin
// 废弃 marginVertical / marginHorizontal
// 迁移逻辑：
val oldVertical = prefs[MARGIN_VERTICAL]?.toFloatOrNull() ?: 30f
prefs.edit {
    putFloat(MARGIN_TOP, oldVertical)
    putFloat(MARGIN_BOTTOM, oldVertical)
    remove(MARGIN_VERTICAL)
}
```

### 5.2 ReaderPreferences 迁移

```kotlin
// ReaderPreferences 新增字段时使用默认值
data class ReaderPreferences(
    // ... 现有字段
    val colorTemperature: Float = 6500f,
    val blueLightFilter: Boolean = false,
    val eyeCareReminderInterval: Int = 0,
    // ...
)
```

### 5.3 预设系统兼容

**ReaderPresetEntity 扩展：**
```kotlin
data class ReaderPresetEntity(
    // ... 现有字段
    val colorTemperature: Float = 6500f,
    val gestureConfig: String = "{}",  // JSON
    // ...
)
```

旧预设加载时，缺失字段使用默认值（向后兼容）。

---

## 6. 测试策略

### 6.1 单元测试

**新增配置项测试：**
```kotlin
class UserPreferencesTest {
    @Test
    fun testColorTemperature() = runTest {
        val prefs = UserPreferences(context)
        
        prefs.setColorTemperature(4500f)
        assertEquals(4500f, prefs.colorTemperature.first())
        
        prefs.setColorTemperature(3000f)  // 边界值
        assertEquals(3000f, prefs.colorTemperature.first())
    }
}
```

**Paginator 测试：**
```kotlin
class PaginatorTest {
    @Test
    fun testHyphenation() {
        val paginator = Paginator(hyphenation = true)
        val page = paginator.paginate("internationalization", pageWidth = 100f)
        
        assertTrue(page.content.contains("in-ter-na-tion-al-i-za-tion"))
    }
}
```

### 6.2 集成测试

**TTS 集成测试：**
```kotlin
class TtsManagerTest {
    @Test
    fun testTtsLifecycle() = runTest {
        val manager = TtsManager(context)
        manager.initialize()
        
        assertTrue(manager.isInitialized)
        
        manager.speak("Hello", "test")
        delay(1000)
        
        manager.stop()
        manager.shutdown()
    }
}
```

### 6.3 UI 测试

**手势绑定测试：**
```kotlin
class GestureBindingTest {
    @Test
    fun testDoubleTap() {
        composeTestRule.onNodeWithTag("ReaderCanvas")
            .performGesture { doubleClick() }
        
        // 验证工具栏显示
        composeTestRule.onNodeWithTag("Toolbar").assertIsDisplayed()
    }
}
```

---

## 7. 风险与缓解

| 风险 | 概率 | 影响 | 缓解措施 |
|---|---|---|---|
| Bionic Reading 性能不足 | 中 | 高 | 预计算加粗宽度，缓存到 TextLine |
| 竖排阅读渲染错误 | 高 | 中 | 分阶段交付，先支持简单文本 |
| TTS 引擎兼容性 | 中 | 中 | 使用 Android 内置 API，避免第三方库 |
| 正则替换性能爆炸 | 低 | 高 | 限制正则复杂度，超时中断 |
| 手势绑定冲突 | 中 | 低 | 明确优先级（翻页 > 手势绑定） |

---

## 8. 附录

### 8.1 竞品功能对比

| 功能 | KOReader | Moon+ Reader | 多看阅读 | 微信读书 | ShuLi (规划) |
|---|---|---|---|---|---|
| 色温调节 | ✅ | ✅ | ❌ | ✅ | ✅ |
| 断字连字 | ✅ | ❌ | ❌ | ❌ | ✅ |
| Bionic Reading | ❌ | ❌ | ❌ | ❌ | ✅ |
| 竖排阅读 | ✅ | ❌ | ✅ | ❌ | ✅ |
| 双页模式 | ✅ | ✅ | ✅ | ✅ | ✅ |
| TTS 朗读 | ❌ | ✅ | ✅ | ✅ | ✅ |
| 手势绑定 | ✅ | ✅ | ❌ | ❌ | ✅ |
| 正则替换 | ✅ | ❌ | ❌ | ❌ | ✅ |

### 8.2 技术债清单

1. **Paginator 重构**：为竖排阅读预留扩展点
2. **TextLine 扩展**：支持列概念（竖排）
3. **手势系统统一**：合并 `TouchZoneCalculator` 与 `GestureDetector`
4. **TTS 抽象层**：为未来第三方 TTS 引擎预留接口

### 8.3 参考资料

- [Liang's Hyphenation Algorithm](https://en.wikipedia.org/wiki/Hyphenation_algorithm#Liang's_algorithm)
- [Android TextToSpeech API](https://developer.android.com/reference/android/speech/tts/TextToSpeech)
- [Bionic Reading Research](https://bionic-reading.com/)
- [KOReader Source Code](https://github.com/koreader/koreader)

---

**文档版本历史：**
- v1.0 (2026-06-10)：初始版本，覆盖 20 个新功能设计
