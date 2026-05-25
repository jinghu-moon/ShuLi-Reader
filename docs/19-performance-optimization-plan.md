# 19. 阅读界面性能优化方案

> 对比项目：`ShuLi-Reader` vs `legado-with-MD3-main`（以下简称 Legado）
> 日期：2025-05-25

---

## 目录

1. [P0 — 两端对齐逐字符绘制](#p0-1)
2. [P0 — AndroidView update 块全量执行](#p0-2)
3. [P1 — drawBattery 每帧创建 Paint 对象](#p1-1)
4. [P1 — 高亮循环重复 measureText](#p1-2)
5. [P1 — setFontFamily 先加载字体再判断变化](#p1-3)
6. [P1 — ViewModel combine 30+ flows 过度触发 reflow](#p1-4)
7. [P2 — themeColors 派生属性无缓存](#p2-1)
8. [P2 — recordPage / recordPageOffMain 重复代码](#p2-2)
9. [P2 — QuickSettingsSheet 40+ lambda 回调爆炸](#p2-3)
10. [P2 — ReaderSliderRow 纯透传包装](#p2-4)
11. [P2 — DisplayPanel 主题色块循环转换](#p2-5)
12. [P3 — setter 样板代码](#p3-1)
13. [P3 — isAnimationDisabled 每次查系统设置](#p3-2)
14. [实施优先级与排期建议](#schedule)

---

<a id="p0-1"></a>
## 1. [P0] 两端对齐逐字符绘制

### 当前实现 (ShuLi-Reader)

`ReaderPageRenderer.drawTextJustified()` 对每一行的每个字符：
1. 调用 `char.toString()` 创建临时 String
2. 调用 `textPaint.measureText(char.toString())` (JNI)
3. 调用 `canvas.drawText(char.toString(), ...)` (JNI)

一行 40 个字 = **80 次 JNI 调用 + 40 个临时 String 分配**。

```kotlin
// ReaderPageRenderer.kt:278-281
for (char in text) {
    drawText(char.toString(), currentX, y, textPaint)
    currentX += textPaint.measureText(char.toString()) + spacingPerChar
}
```

### Legado 实现

Legado 在 **排版阶段**（`TextChapterLayout.measureTextSplit`）就使用 `paint.getTextWidths(text, widthsArray)` **一次性** 获取所有字符宽度，并将每个字符的 `start`/`end` 坐标存储到 `TextColumn` 对象中。绘制时只需 `drawText(charData, column.start, ...)` 即可，**无需重新测量**。

此外 Legado 还有 `TextMeasure` 缓存类，按 codePoint 缓存字符宽度（中文统一宽度/ASCII 查表），进一步减少 `measureText` 调用。

```kotlin
// Legado TextChapterLayout.kt:1279-1301
private fun measureTextSplit(text: String, widthsArray: FloatArray, start: Int = 0): Pair<...> {
    // 使用 paint.getTextWidths() 一次性获取所有宽度
    // 按 grapheme cluster 聚合，返回 (words, widths)
}
```

### 改进方案：借鉴 Legado，在排版阶段预计算坐标

**策略**：重构，不仅仅借鉴

1. **TextLine 增加 `columns: List<CharColumn>` 字段**，每个 `CharColumn` 存储 `(charData: String, startX: Float, endX: Float)`。
2. **Paginator 排版阶段**使用 `paint.getTextWidths()` 一次性获取宽度数组，计算两端对齐后各字符的 x 坐标，写入 `CharColumn`。
3. **ReaderPageRenderer 绘制阶段**直接遍历 `columns`，调用 `canvas.drawText(col.charData, col.startX, baseline, paint)`。
4. **彻底删除** `drawTextJustified()` 中的 `measureText` 逻辑。

```kotlin
// 新增数据类
data class CharColumn(
    val charData: String,   // 单个 grapheme cluster
    val startX: Float,      // 相对于行左侧的起始 x
    val endX: Float,        // 结束 x
)

// TextLine 增加字段
data class TextLine(
    ...
    val charColumns: List<CharColumn> = emptyList(), // 两端对齐时填充
)
```

**预期收益**：
- 绘制阶段 JNI 调用数：从 `2N`（N=字符数）降到 `N`（仅 drawText）
- 消除 `N` 个临时 String 分配
- `measureText` 从每帧调用降为分页时一次

---

<a id="p0-2"></a>
## 2. [P0] AndroidView update 块全量执行

### 当前实现 (ShuLi-Reader)

`ReaderScreen.kt` 的 `AndroidView.update` lambda 在 **任何 uiState 字段变化** 时全量执行 20+ setter 调用：

```kotlin
// ReaderScreen.kt:264-288
update = { view ->
    view.setThemeColors(uiState.themeColors)       // 每次创建 2 个中间对象
    view.setTextSizePx(prefs.fontSize * density)
    view.setFontFamily(prefs.readingFont)           // 每次 ResourcesCompat.getFont()
    view.setLetterSpacing(prefs.letterSpacing)
    view.setFakeBoldText(...)
    view.setTextAlign(prefs.textAlign)
    view.setPageDelegate(viewModel.pageDelegate)    // 每次查系统设置
    view.updateHeaderFooter(...)                     // 每次调用 resolveHeaderSlots()
    view.setPage(page, nextPage, prevPage, ...)
    view.setBatteryLevel(batteryLevel)
    // ...
}
```

翻页时 `pageIndex` 变化导致 uiState 更新，但字体/主题等根本没变，却全部重新调用。

### Legado 实现

Legado 使用传统 View 体系（FrameLayout + 子 View），不经过 Compose 桥接。配置变更通过 EventBus 细粒度分发，`ReadView.upStyle()` / `upBg()` / `upTime()` / `upBattery()` 各自独立，**只在对应配置变化时调用**。

### 改进方案：拆分 update 为分层更新

**策略**：重构 ShuLi-Reader 的 Compose-AndroidView 桥接层

1. **将 ReaderCanvasView 的属性分为三层**：
   - **高频层**（每次翻页）：`page`, `nextPage`, `prevPage`, `batteryLevel`, `ttsActiveRange`, `selectedRange`
   - **中频层**（设置面板操作）：`theme`, `fontSize`, `fontFamily`, `letterSpacing`, `fontWeight`, `textAlign`, `headerFooter`, `pageDelegate`, `titleStyle`
   - **低频层**（几乎不变）：`edgeTurnPage`

2. **使用 `derivedStateOf` + `LaunchedEffect`** 分离观察：

```kotlin
// 高频：翻页数据
val pageData = remember { derivedStateOf { 
    PageUpdateBundle(uiState.currentPage, uiState.currentChapter, uiState.pageIndex, ...) 
}}
LaunchedEffect(pageData.value) { view.setPage(...) }

// 中频：外观配置（snapshotFlow + distinctUntilChanged）
val styleFingerprint = remember { derivedStateOf { prefs.styleHash() }}
LaunchedEffect(styleFingerprint.value) {
    view.setThemeColors(...)
    view.setTextSizePx(...)
    // ...
}
```

3. **或者引入 `ReaderCanvasView.applyConfig(config: ReaderViewConfig)`** 批量方法，内部一次性 diff 并更新：

```kotlin
data class ReaderViewConfig(
    val themeColors: ThemeColors,
    val textSizePx: Float,
    val fontKey: String,
    ...
)

fun applyConfig(config: ReaderViewConfig) {
    if (currentConfig == config) return
    // diff 并只更新变化字段
    if (currentConfig.fontKey != config.fontKey) { ... }
    // 最后统一 invalidateAllRecorders + submitRenderTask + invalidate
}
```

**预期收益**：
- 翻页时避免 `setFontFamily` / `setThemeColors` / `setPageDelegate` 等无用调用
- 减少中间对象分配（`ThemeColors`、`SlotResolution`）
- `submitRenderTask()` 和 `invalidate()` 从 N 次降为 1 次

---

<a id="p1-1"></a>
## 3. [P1] drawBattery 每帧创建 Paint 对象

### 当前实现 (ShuLi-Reader)

```kotlin
// ReaderPageRenderer.kt:301-330
val strokePaint = Paint().apply { ... }  // new Paint #1
val capPaint = Paint().apply { ... }     // new Paint #2
val fillPaint = Paint().apply { ... }    // new Paint #3
```

每次录制页面时创建 3 个 `Paint` 对象。`Paint` 构造涉及 native 分配。

### Legado 实现

Legado 使用 `PaintPool`（对象池）复用 Paint 实例：

```kotlin
val paint = PaintPool.obtain()
// ... use paint ...
PaintPool.recycle(paint)
```

### 改进方案：提为类成员

**策略**：直接重构，比 PaintPool 更简单

```kotlin
class ReaderPageRenderer(...) {
    // 电池绘制专用画笔（预分配，绘制前更新 color/alpha）
    private val batteryStrokePaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val batteryFillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private fun drawBattery(...) {
        batteryStrokePaint.color = footerPaint.color
        batteryStrokePaint.alpha = 102
        batteryStrokePaint.strokeWidth = 1f * density
        // ... use batteryStrokePaint ...

        batteryFillPaint.color = footerPaint.color
        batteryFillPaint.alpha = 102
        // ... use batteryFillPaint for both cap and fill ...
    }
}
```

注意 `capPaint` 和 `fillPaint` 属性完全相同，合并为一个。

**预期收益**：消除每次录制 3 个 Paint 的 native 分配

---

<a id="p1-2"></a>
## 4. [P1] 高亮循环重复 measureText

### 当前实现 (ShuLi-Reader)

```kotlin
// ReaderPageRenderer.kt:104-117
page.lines.forEach { line ->
    val textWidth = textPaint.measureText(line.text)  // 每行一次 JNI
    // ... draw highlight rect ...
}
```

同一行的 `measureText` 还会在 `drawTextJustified` 中再次调用。

### Legado 实现

Legado 的 `TextLine` 在排版阶段就通过 `columns` 列表记录了行首到行尾的坐标范围，绘制高亮时直接用 `lineStart` / `lineEnd` 坐标，**不调用 measureText**。

### 改进方案：TextLine 缓存 measuredWidth

**策略**：结合 P0 的 CharColumn 重构

若已实施 P0（CharColumn 方案），`TextLine` 的文本宽度可直接从 `charColumns.last().endX - charColumns.first().startX` 得到。

若暂不实施 P0，可在 `TextLine` 增加 `val measuredWidth: Float` 字段，在 `Paginator` 分页时计算并存储：

```kotlin
data class TextLine(
    ...
    val measuredWidth: Float = 0f,  // 在 Paginator 分页时缓存
)
```

**预期收益**：每页高亮绘制减少 N 次 `measureText` JNI 调用（N = 行数，约 15-25）

---

<a id="p1-3"></a>
## 5. [P1] setFontFamily 先加载字体再判断变化

### 当前实现 (ShuLi-Reader)

```kotlin
// ReaderCanvasView.kt:457-476
fun setFontFamily(fontKey: String) {
    val typeface = when (fontKey) {
        "system" -> Typeface.DEFAULT
        "lxgw" -> ResourcesCompat.getFont(context, R.font.lxgw_wenkai_regular)  // IO!
        else -> ResourcesCompat.getFont(context, R.font.harmonyos_sanssc_regular)
    }
    if (textPaint.typeface == typeface) return  // 检查在加载之后
}
```

`ResourcesCompat.getFont()` 内部有缓存，但仍涉及 HashMap lookup + 同步锁。在 `update` 块中每次 recomposition 都调用。

### Legado 实现

Legado 在 `ChapterProvider.upStyle()` 中集中加载字体，仅在配置变化时触发一次。字体引用存储在 `ChapterProvider.typeface` 静态字段中。

### 改进方案：先比较 fontKey

```kotlin
private var currentFontKey: String = "system"

fun setFontFamily(fontKey: String) {
    if (fontKey == currentFontKey) return  // 快速跳过
    currentFontKey = fontKey
    val typeface = when (fontKey) { ... }
    if (textPaint.typeface == typeface) return
    textPaint.typeface = typeface
    invalidateAllRecorders()
    submitRenderTask()
    invalidate()
}
```

**预期收益**：翻页等高频场景避免 font 查找开销

---

<a id="p1-4"></a>
## 6. [P1] ViewModel combine 30+ flows 过度触发 reflow

### 当前实现 (ShuLi-Reader)

```kotlin
// ReaderViewModel.kt:230-310
combine(
    prefs.defaultFontSize,       // flows[0]
    prefs.defaultLineSpacing,    // flows[1]
    ...30+ flows...
) { flows ->
    ReaderPreferences(
        fontSize = flows[0] as Float,  // 数组下标 + 强转
        ...
    )
}.collectLatest { preferences ->
    reflowCurrentChapter(preferences)  // 任何属性变化都触发 reflow!
}
```

**问题**：
1. **脆弱**：增删字段需同步修改数组下标，极易出错
2. **过度触发**：改变 `brightness`（纯 UI 属性）/ `keepScreenOn` / `volumeKeyTurnPage` 也触发 `reflowCurrentChapter`（重新分页），实际只有排版参数（fontSize, lineSpacing, margin, indent, useZhLayout 等）变化才需要 reflow

### Legado 实现

Legado 使用 EventBus 分发配置变更，按编号分类处理：
- `upConfig(1)` → 字体大小变化 → reflow
- `upConfig(2)` → 主题变化 → 仅重绘
- `upConfig(5)` → 布局尺寸 → reflow
- 等等

细粒度控制，不会因无关属性变化触发 reflow。

### 改进方案：按影响范围分组 flows

**策略**：重构

将 30+ flows 按影响范围分为三组：

```kotlin
// Group A: 排版参数 → 触发 reflow
val layoutFlows = combine(
    prefs.defaultFontSize,
    prefs.defaultLineSpacing,
    prefs.defaultParagraphSpacing,
    prefs.defaultIndent,
    prefs.marginHorizontal,
    prefs.marginVertical,
    prefs.letterSpacing,
    prefs.useZhLayout,
    prefs.titleAlign,
    prefs.titleSizeOffset,
    prefs.titleMarginTop,
    prefs.titleMarginBottom,
) { ... -> LayoutPreferences(...) }

// Group B: 外观参数 → 仅触发重绘
val visualFlows = combine(
    prefs.readingFont,
    prefs.fontWeight,
    prefs.textAlign,
    prefs.defaultPageAnim,
    prefs.headerVisibility, prefs.headerLeft, prefs.headerCenter, prefs.headerRight,
    prefs.footerVisibility, prefs.footerLeft, prefs.footerCenter, prefs.footerRight,
    prefs.headerFooterAlpha,
    prefs.showProgress,
) { ... -> VisualPreferences(...) }

// Group C: 行为参数 → 仅更新标志位
val behaviorFlows = combine(
    prefs.brightness,
    prefs.keepScreenOn,
    prefs.volumeKeyTurnPage,
    prefs.edgeTurnPage,
    prefs.chineseConvert,
) { ... -> BehaviorPreferences(...) }
```

分别 collect：
- Group A → `reflowCurrentChapter()`
- Group B → `_uiState.update { ... }` (Compose 重绘)
- Group C → 仅更新 Window 属性 / 标志位

同时将 `flows[N] as Float` 替换为具名参数，消除数组下标魔数。

**预期收益**：
- 调节亮度时不再触发 reflow（当前每滑动一次亮度条都重排整章）
- 切换音量翻页/屏幕常亮不再触发 reflow
- 代码可维护性大幅提升

---

<a id="p2-1"></a>
## 7. [P2] themeColors 派生属性无缓存

### 当前实现

```kotlin
// ReaderViewModel.kt:117-121
val themeColors: ThemeColors
    get() = readerPreferences.backgroundColor
        .toReaderColorScheme()
        .toCanvasThemeColors()
```

每次访问创建 `ReaderColorScheme` + `ThemeColors` 中间对象。在 `update` 块中每帧访问。

### 改进方案

改为 `data class` 字段，在 `_uiState.copy()` 时计算一次：

```kotlin
data class ReaderUiState(
    ...
    val themeColors: ThemeColors = ReaderTheme.PAPER
        .toReaderColorScheme()
        .toCanvasThemeColors(),
)

// 更新时
_uiState.value = _uiState.value.copy(
    readerPreferences = preferences,
    themeColors = preferences.backgroundColor.toReaderColorScheme().toCanvasThemeColors(),
)
```

**预期收益**：消除每帧 2 个中间对象分配

---

<a id="p2-2"></a>
## 8. [P2] recordPage / recordPageOffMain 重复代码

### 当前实现

```kotlin
// ReaderCanvasView.kt:130-175
private fun recordPage(page: TextPage) {
    page.canvasRecorder.recordIfNeeded(w, h) {
        pageRenderer.render(/* 13 个参数 */)
    }
}

private fun recordPageOffMain(page: TextPage, w: Int, h: Int): Boolean {
    return page.canvasRecorder.recordIfNeeded(w, h) {
        pageRenderer.render(/* 同样 13 个参数 */)
    }
}
```

### 改进方案

提取公共方法：

```kotlin
private fun doRecordPage(page: TextPage, w: Int, h: Int): Boolean {
    return page.canvasRecorder.recordIfNeeded(w, h) {
        pageRenderer.render(
            canvas = this,
            page = page,
            headerSlots = renderContext.headerSlots,
            footerSlots = renderContext.footerSlots,
            showProgress = renderContext.showProgress,
            headerAlpha = renderContext.headerAlpha,
            footerAlpha = renderContext.footerAlpha,
            batteryLevel = renderContext.batteryLevel,
            ttsActiveRange = renderContext.ttsActiveRange,
            selectedRange = renderContext.selectedRange,
            ttsHighlightPaint = ttsHighlightPaint,
            selectionPaint = selectionPaint,
            backgroundPaint = backgroundPaint,
        )
    }
}

private fun recordPage(page: TextPage) {
    val w = width; val h = height
    if (w <= 0 || h <= 0) return
    doRecordPage(page, w, h)
}

private fun recordPageOffMain(page: TextPage, w: Int, h: Int): Boolean {
    return doRecordPage(page, w, h)
}
```

---

<a id="p2-3"></a>
## 9. [P2] QuickSettingsSheet 40+ lambda 回调爆炸

### 当前实现

`QuickSettingsSheet` 接收 40+ callback 参数，每次 recomposition 重新创建。传递链 `ReaderScreen → QuickSettingsSheet → Panel` 层层转发。

### Legado 实现

Legado 使用 Activity 作为 `CallBack` 接口实现者，子组件通过 `activity as CallBack` 获取引用，回调数极少。

### 改进方案

定义 `interface QuickSettingsActions`，由 ViewModel 实现或作为 stable 对象传递：

```kotlin
@Stable
class QuickSettingsActions(
    val onFontSizeChange: (Float) -> Unit,
    val onLineSpacingChange: (Float) -> Unit,
    val onThemeChange: (ReaderTheme) -> Unit,
    // ...所有回调
)

// ReaderScreen 中创建一次
val actions = remember(viewModel) {
    QuickSettingsActions(
        onFontSizeChange = viewModel::setFontSize,
        onLineSpacingChange = viewModel::setLineSpacing,
        ...
    )
}

// QuickSettingsSheet 仅接收一个参数
@Composable
fun QuickSettingsSheet(
    prefs: ReaderPreferences,
    actions: QuickSettingsActions,
)
```

**预期收益**：
- 减少 recomposition 时 lambda 重建
- 函数签名简洁，维护性提升
- Compose 可将 `@Stable` 对象视为不变，跳过不必要的重组

---

<a id="p2-4"></a>
## 10. [P2] ReaderSliderRow 纯透传包装

### 当前实现

```kotlin
// QuickSettingsControls.kt:52-77
@Composable
fun ReaderSliderRow(...) {
    ReaderValueSlider(label, value, ...)  // 原封不动转发
}
```

### 改进方案

删除 `ReaderSliderRow`，调用方直接使用 `ReaderValueSlider`。或反过来：统一命名为 `ReaderSliderRow`，删除内层 `ReaderValueSlider`。

---

<a id="p2-5"></a>
## 11. [P2] DisplayPanel 主题色块循环转换

### 当前实现

```kotlin
// QuickSettingsSheet.kt ~L471
ReaderTheme.entries.forEach { theme ->
    val themeColors = theme.toReaderColorScheme().toCanvasThemeColors()  // 每次 recomposition!
}
```

### 改进方案

```kotlin
val themeColorMap = remember {
    ReaderTheme.entries.associateWith { 
        it.toReaderColorScheme().toCanvasThemeColors()
    }
}
ReaderTheme.entries.forEach { theme ->
    val themeColors = themeColorMap[theme]!!
}
```

---

<a id="p3-1"></a>
## 12. [P3] setter 样板代码

### 当前实现

`setTextSizePx`、`setLetterSpacing`、`setFakeBoldText`、`setTextAlign`、`setFontFamily`、`setTheme` 都遵循相同的 `if (same) return → update → invalidateAllRecorders() → submitRenderTask() → invalidate()` 模式。

### 改进方案

若实施 P0-2 的 `applyConfig` 批量方法，这些 setter 将被替代。否则可提取辅助函数：

```kotlin
private inline fun updateRenderProperty(changed: Boolean, apply: () -> Unit) {
    if (!changed) return
    apply()
    invalidateAllRecorders()
    submitRenderTask()
    invalidate()
}
```

---

<a id="p3-2"></a>
## 13. [P3] isAnimationDisabled 每次查系统设置

### 当前实现

```kotlin
// ReaderCanvasView.kt:274-284
private fun isAnimationDisabled(): Boolean {
    return Settings.Global.getFloat(contentResolver, ANIMATOR_DURATION_SCALE, 1.0f) == 0f
}
```

每次 `setPageDelegate` 调用（每次 recomposition）都查询 ContentProvider。

### 改进方案

缓存结果，仅在 `onAttachedToWindow` / `onResume` 时刷新：

```kotlin
private var animationDisabledCache: Boolean? = null

private fun isAnimationDisabled(): Boolean {
    return animationDisabledCache ?: run {
        val disabled = try {
            Settings.Global.getFloat(...) == 0f
        } catch (_: Exception) { false }
        animationDisabledCache = disabled
        disabled
    }
}

override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    animationDisabledCache = null  // 重新检测
}
```

---

<a id="schedule"></a>
## 14. 实施优先级与排期建议

| 编号 | 优先级 | 改动范围 | 预估工作量 | 依赖 |
|------|--------|---------|-----------|------|
| #1 P0 两端对齐 | **P0** | Paginator + TextLine + TextModels + ReaderPageRenderer | 大（涉及数据模型变更） | 无 |
| #2 P0 update 拆分 | **P0** | ReaderScreen + ReaderCanvasView | 中 | 无 |
| #6 P1 combine 分组 | **P1** | ReaderViewModel | 中 | 无 |
| #3 P1 Paint 复用 | **P1** | ReaderPageRenderer | 小 | 无 |
| #4 P1 measureText 缓存 | **P1** | TextLine + Paginator + ReaderPageRenderer | 小（若 #1 已做则自动解决） | #1 |
| #5 P1 fontKey 短路 | **P1** | ReaderCanvasView | 小 | 无 |
| #7 P2 themeColors 缓存 | **P2** | ReaderViewModel | 小 | 无 |
| #8 P2 recordPage 去重 | **P2** | ReaderCanvasView | 小 | 无 |
| #9 P2 回调接口化 | **P2** | ReaderScreen + QuickSettingsSheet + QuickSettingsControls | 中 | 无 |
| #10 P2 删除透传 | **P2** | QuickSettingsControls | 小 | 无 |
| #11 P2 主题色缓存 | **P2** | QuickSettingsSheet | 小 | 无 |
| #12 P3 setter 样板 | **P3** | ReaderCanvasView | 小 | #2 |
| #13 P3 动画检测缓存 | **P3** | ReaderCanvasView | 小 | 无 |

### 建议实施顺序

1. **Phase 1**（立即）：#3, #5, #7, #8, #11, #13 — 小改动，无风险
2. **Phase 2**（短期）：#2, #6 — 架构改进，显著减少无效工作
3. **Phase 3**（中期）：#1, #4 — 数据模型变更，需配合回归测试
4. **Phase 4**（长期）：#9, #10, #12 — 代码质量改进

### 对比总结

| 维度 | ShuLi-Reader | Legado | 结论 |
|------|-------------|--------|------|
| 文本测量 | 渲染时逐字符 measureText | 排版时 getTextWidths 一次性 + TextMeasure 缓存 | **借鉴 Legado** |
| Paint 管理 | 每帧 new Paint() | PaintPool 对象池 | **重构**（预分配成员更简单） |
| 配置分发 | 30+ flows combine → 全量 reflow | EventBus 细粒度分发 | **重构**（分组 combine） |
| View 更新 | Compose AndroidView update 全量 | 传统 View 细粒度 upXxx() | **重构**（拆分 update 层） |
| 页面缓存 | CanvasRecorder（与 Legado 同源） | CanvasRecorder + optimizeRender 开关 | **持平** |
| 回调接口 | 40+ lambda 参数 | Activity CallBack 接口 | **重构**（@Stable 接口对象） |
