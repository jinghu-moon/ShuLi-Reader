# 35 - 阅读界面页面盒子模型（Page Box Model）设计方案

> 状态：设计完成，待实施
> 创建日期：2026-06-17
> 修订日期：2026-06-17（综合四份深度审查修订）
> 替代：全局共享边距（marginTop/Bottom/Left/Right + headerMarginTop + footerMarginBottom）

---

## 1. 设计目标

将页眉、页脚、正文、标题从"共享全局边距的扁平布局"重构为"四个独立盒子，各自持有四向边距"的盒子模型。

### 当前问题

| 问题 | 位置 | 说明 |
|------|------|------|
| 全局边距共享 | `ReaderLayoutConfig.marginTop/Bottom/Left/Right` | header/footer 只能通过 `headerMarginTop` / `footerMarginBottom` 微调，无法独立控制左右边距 |
| 硬编码组合逻辑 | `Paginator.paginatePage` | `startY = marginTop + headerHeight + titleAreaHeight` 等散落的计算 |
| 冗余字段 | `TextPage` | 携带 `topContentY`、`marginHorizontal`、`headerMarginTop`、`footerMarginBottom` 等散落字段 |
| 扩展性差 | 多处 | 新增元素的独立边距需要改多处代码 |
| 首页标题污染后续页 | `paginateChapter` | 标题区域高度仅首页有效，但 layout 共享给所有页面，后续页白白损失正文高度 |
| 渲染期对象分配 | `drawChapterTitle` | 每帧创建 `StaticLayout`，导致 GC 抖动和掉帧 |

### 目标架构

```
BoxInsetsDp（用户设置，dp 单位）
  └─ toPx(density) → BoxInsetsPx
       │
       ▼
BoxSpec（统一盒子规格，px 单位）
  ├── insets: BoxInsetsPx
  ├── innerHeight: Float（内容高度，不含 insets）
  ├── placement: TOP_DOWN | BOTTOM_UP | FILL
  └── visible: Boolean
       │
       ▼
PageLayoutCalculator.calculate(header, title, body, footer)
       │
       ▼
PageLayout
  ├── header: BoxBounds?   (TOP_DOWN)
  ├── title:  BoxBounds?   (TOP_DOWN，仅首页)
  ├── body:   BoxBounds    (FILL)
  └── footer: BoxBounds?   (BOTTOM_UP)
```

每个盒子用同一个 `BoxSpec` 描述，`PageLayoutCalculator` 通过 `placement` 类型统一处理，不再对每个盒子写独立的 if-else 分支。

**首页标题处理**：`PageLayout` 按页生成（非按章共享）。首页 layout 包含 title box，后续页 layout 的 title=null，body 自动扩展到标题区域，不浪费空间。

---

## 2. 新增数据模型

### 2.1 BoxInsetsDp / BoxInsetsPx — 类型安全的边距

> 文件：`core/reader/model/BoxInsets.kt`

```kotlin
/**
 * 盒子四向边距（单位：dp）。
 * 用于 ReaderPreferences 存储用户设置。
 * 通过 toPx() 转换为 BoxInsetsPx 后参与布局计算。
 *
 * 与 BoxInsetsPx 是不同类型，编译器防止 dp/px 混用导致的静默 bug。
 * 使用 data class（非 @JvmInline value class），因为 4 个 Float 需要 128 位，
 * 无法 packed 进 64 位 Long。
 */
@Serializable
data class BoxInsetsDp(
    val top: Float = 0f,
    val bottom: Float = 0f,
    val left: Float = 0f,
    val right: Float = 0f,
) {
    fun toPx(density: Float) = BoxInsetsPx(
        top = top * density,
        bottom = bottom * density,
        left = left * density,
        right = right * density,
    )

    companion object {
        val ZERO = BoxInsetsDp()
    }
}

/**
 * 盒子四向边距（单位：px）。
 * 用于 PageLayoutCalculator 参与布局计算。
 * 由 BoxInsetsDp.toPx(density) 产生。
 */
data class BoxInsetsPx(
    val top: Float = 0f,
    val bottom: Float = 0f,
    val left: Float = 0f,
    val right: Float = 0f,
) {
    companion object {
        val ZERO = BoxInsetsPx()
    }
}
```

> **设计决策**：
> - 用两个独立类型（`BoxInsetsDp` / `BoxInsetsPx`）而非 typealias，编译器会阻止 `dp * density` 遗漏或重复的错误。
> - `BoxInsetsDp` 标记 `@Serializable`，kotlinx.serialization 直接支持，`ReaderPreferences` 序列化时自动处理。
> - 不使用 `@JvmInline value class`——4 个 Float 需要 128 位，无法 packed 进 64 位 Long。

### 2.2 BoxSpec — 盒子规格（统一盒子描述）

> 文件：`core/reader/model/BoxSpec.kt`

```kotlin
/**
 * 描述一个布局盒子的规格（输入参数）。
 *
 * 每个盒子（页眉、标题、正文、页脚）都用同一个 BoxSpec 描述，
 * 由 PageLayoutCalculator 统一计算其 BoxBounds。
 *
 * @param insets      四向边距（px）
 * @param innerHeight 内容区高度（px），不含 insets。FILL 模式下忽略。
 *                    注意：这是内容本身的高度，盒子总高度 = innerHeight + insets.top + insets.bottom
 * @param placement   放置方式：TOP_DOWN / BOTTOM_UP / FILL
 * @param visible     是否参与布局。false 时 PageLayout 中对应 BoxBounds 为 null，
 *                    且不占用垂直空间（cursorY 不推进）。
 */
data class BoxSpec(
    val insets: BoxInsetsPx = BoxInsetsPx.ZERO,
    val innerHeight: Float = 0f,
    val placement: Placement = Placement.TOP_DOWN,
    val visible: Boolean = true,
) {
    /** 盒子放置方式 */
    enum class Placement {
        /** 从上往下放置（页眉、标题） */
        TOP_DOWN,
        /** 从底部往上放置（页脚） */
        BOTTOM_UP,
        /** 填充剩余空间（正文） */
        FILL,
    }
}
```

> **命名**：`innerHeight`（原 `contentHeight`）明确表示"内容区高度，不含 insets"，避免与"盒子总高度"混淆。
>
> **visible 语义**：`visible = false` 等同于"此盒子不存在"——不占空间、不产出 BoxBounds。`visible = true, innerHeight = 0f` 的盒子会占据 insets 空间但内容为零（如仅显示背景的空盒子）。

### 2.3 BoxBounds — 盒子计算结果

> 文件：`core/reader/model/BoxSpec.kt`（与 BoxSpec 同文件）

```kotlin
/**
 * 单个盒子的计算结果：屏幕坐标（px）+ 内容区域。
 * 由 PageLayoutCalculator 一次性计算，后续只读。
 *
 * width/height 在构造时计算，避免每次访问的减法开销。
 * 不提供 toRectF()——渲染层应使用 canvas.drawRect(l, t, r, b, paint) 四参数重载，
 * 避免在渲染路径上堆分配 RectF 对象。
 */
data class BoxBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float = right - left
    val height: Float = bottom - top
}
```

> **移除 toRectF()**：渲染路径（onDraw 等价路径）禁止堆分配。需要 RectF 时，使用 ReaderPageRenderer 中预分配的 `reusableRectF` 字段，或直接用 `canvas.drawRect(l, t, r, b, paint)` 四参数重载。

### 2.4 PageLayout — 单页布局结果

> 文件：`core/reader/model/BoxSpec.kt`（与 BoxSpec 同文件）

```kotlin
/**
 * 单页布局：四个盒子的最终位置。
 * 按页生成（非按章共享），首页包含 title box，后续页 title=null。
 *
 * @param header 页眉盒子，null = 隐藏
 * @param title  标题盒子，null = 非首页或隐藏
 * @param body   正文盒子，始终存在（分页区域）
 * @param footer 页脚盒子，null = 隐藏
 * @param pageWidth  页面宽度（px）
 * @param pageHeight 页面高度（px）
 */
data class PageLayout(
    val header: BoxBounds?,
    val title: BoxBounds?,
    val body: BoxBounds,
    val footer: BoxBounds?,
    val pageWidth: Float,
    val pageHeight: Float,
)
```

> **按页生成**：`paginateChapter` 中，首页和后续页分别计算不同的 `PageLayout`。首页 layout 的 title box 占据空间，后续页 layout 的 title=null，body 自动向上扩展。

---

## 3. PageLayoutCalculator — 核心布局引擎

> 文件：`core/reader/engine/PageLayoutCalculator.kt`

### 3.1 calculate — 布局计算（统一盒子循环）

```kotlin
object PageLayoutCalculator {

    /**
     * 统一布局计算：接收 4 个 BoxSpec，返回 PageLayout。
     *
     * 内部按 placement 类型处理：
     * 1. TOP_DOWN 盒子（header、title）：从上往下依次放置，共享 cursorY
     * 2. BOTTOM_UP 盒子（footer）：从底部向上放置
     * 3. FILL 盒子（body）：填充 TOP_DOWN 下方到 BOTTOM_UP 上方的剩余空间
     *
     * 无 List 分配——header/title 直接计算，避免 listOf + map 的对象分配和索引魔法。
     */
    fun calculate(
        pageSize: PageSize,
        header: BoxSpec,
        title: BoxSpec,
        body: BoxSpec,
        footer: BoxSpec,
    ): PageLayout {
        val pageWidth = pageSize.width.toFloat()
        val pageHeight = pageSize.height.toFloat()
        var cursorY = 0f

        // ── 第一轮：TOP_DOWN 盒子 ──
        val headerBounds = placeTopDown(header, pageWidth, cursorY)
        if (headerBounds != null) cursorY = headerBounds.bottom + header.insets.bottom

        val titleBounds = placeTopDown(title, pageWidth, cursorY)
        if (titleBounds != null) cursorY = titleBounds.bottom + title.insets.bottom

        // ── 第二轮：BOTTOM_UP 盒子（footer） ──
        //
        // footer.insets 含义：
        //   .bottom = 页脚内容区底部到页面底部的距离
        //   .top    = 页脚内容区顶部到 body 底部的间距（即 body 与 footer 之间的留白）
        //   .left/.right = 页脚内容区的水平内边距
        //
        val footerBounds = if (footer.visible && footer.placement == BoxSpec.Placement.BOTTOM_UP) {
            val contentH = footer.innerHeight.coerceAtLeast(0f)
            val bottom = pageHeight - footer.insets.bottom
            val top = (bottom - contentH).coerceAtLeast(0f)
            BoxBounds(footer.insets.left, top, pageWidth - footer.insets.right, bottom)
        } else null

        // ── 第三轮：FILL 盒子（body = 剩余空间） ──
        //
        // body 底部 = footer 内容区顶部 - footer.insets.top（body 与 footer 的间距）- body.insets.bottom
        // 若 footer 不存在，body 底部 = 页面底部 - body.insets.bottom
        //
        val bodyTop = cursorY + body.insets.top
        val bodyBottom = (footerBounds?.let { it.top - footer.insets.top } ?: pageHeight) - body.insets.bottom

        val bodyBounds = BoxBounds(
            left = body.insets.left,
            top = bodyTop,
            right = pageWidth - body.insets.right,
            bottom = bodyBottom.coerceAtLeast(bodyTop), // 防止 footer 过大导致 body 为负
        )

        return PageLayout(
            header = headerBounds,
            title = titleBounds,
            body = bodyBounds,
            footer = footerBounds,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
        )
    }

    /** 计算 TOP_DOWN 盒子的 BoxBounds，不可见时返回 null */
    private fun placeTopDown(spec: BoxSpec, pageWidth: Float, cursorY: Float): BoxBounds? {
        if (!spec.visible || spec.placement != BoxSpec.Placement.TOP_DOWN) return null
        val top = cursorY + spec.insets.top
        val bottom = top + spec.innerHeight.coerceAtLeast(0f)
        return BoxBounds(spec.insets.left, top, pageWidth - spec.insets.right, bottom)
    }

    /**
     * 仅计算 body 宽度（px）。
     * 供 Paginator 在计算标题高度前使用，避免与 calculate() 内部逻辑重复。
     */
    fun bodyWidth(pageSize: PageSize, bodyInsets: BoxInsetsPx): Float {
        return pageSize.width.toFloat() - bodyInsets.left - bodyInsets.right
    }
}
```

> **无 List 分配**：header/title 直接用 `placeTopDown()` 计算，避免 `listOf + map` 的对象分配和 `topDownBounds[0]`/`[1]` 的索引硬编码。
>
> **bodyWidth 工具方法**：提取公共的 body 宽度计算逻辑，`paginateChapter` 中预计算标题宽度时使用，避免与 `calculate()` 内部逻辑重复。

---

## 4. ReaderLayoutConfig 改造

> 文件：`core/reader/model/TextModels.kt`（第 16-36 行）

### 4.1 After（盒子模型）

```kotlin
data class ReaderLayoutConfig(
    val pageSize: PageSize,
    val textSize: Float,
    val lineHeight: Float,
    val paragraphSpacing: Float,
    val indent: Float,
    val density: Float = 3f,
    val letterSpacingPx: Float = 0f,
    val titleStyle: TitleStyleConfig = TitleStyleConfig(),
    val useZhLayout: Boolean = false,
    val bottomJustify: Boolean = false,

    // ── 盒子边距（px，由 BoxInsetsDp.toPx(density) 产生） ──
    val headerInsets: BoxInsetsPx = BoxInsetsPx.ZERO,
    val titleInsets: BoxInsetsPx = BoxInsetsPx.ZERO,
    val bodyInsets: BoxInsetsPx = BoxInsetsPx.ZERO,
    val footerInsets: BoxInsetsPx = BoxInsetsPx.ZERO,

    // ── 字体信息（供 Paginator 的 buildTitleLayout 精确度量） ──
    // Paginator 不持有渲染层的 TextPaint，但需要字体信息来精确计算标题高度。
    // titleTypeface 从 Renderer 的字体源传入，确保分页与渲染使用完全相同的字体度量。
    val titleTypeface: Typeface = Typeface.DEFAULT,
    val titleIsFakeBold: Boolean = true,
)
```

### 4.2 toLayoutConfig 改造

> 文件：`core/data/ReaderPreferences.kt`（第 440-471 行）

```kotlin
fun ReaderPreferences.toLayoutConfig(
    pageSize: PageSize,
    density: Float,
): ReaderLayoutConfig {
    val textSizePx = fontSize * density
    val indentPx = when (indentUnit) {
        IndentUnit.CHARACTER -> indent * textSizePx
        IndentUnit.PIXEL -> indent * density
    }

    return ReaderLayoutConfig(
        pageSize = pageSize,
        textSize = textSizePx,
        lineHeight = lineSpacing,
        paragraphSpacing = paragraphSpacing * textSizePx,
        indent = indentPx,
        density = density,
        letterSpacingPx = letterSpacing * textSizePx,
        titleStyle = titleStyle,
        useZhLayout = useZhLayout,
        bottomJustify = bottomJustify,
        // ── 盒子边距（BoxInsetsDp → BoxInsetsPx） ──
        headerInsets = headerBox.toPx(density),
        titleInsets = titleBox.toPx(density),
        bodyInsets = bodyBox.toPx(density),
        footerInsets = footerBox.toPx(density),
        // ── 字体（供 Paginator 的 buildTitleLayout 精确度量） ──
        // 通过 FontManager 或 Typeface.create() 获取当前阅读字体
        titleTypeface = resolveTitleTypeface(readingFont),
        titleIsFakeBold = fontWeight == ReaderFontWeight.BOLD,
    )
}
```

> **关键变化**：旧的 `marginTop ?: marginVertical` nullable fallback 模式被移除。`BoxInsetsDp.toPx(density)` 一次性完成 dp→px 转换，类型系统保证不会遗漏或重复乘以 density。

---

## 5. Paginator 改造

> 文件：`core/reader/engine/Paginator.kt`

### 5.1 首页 vs 后续页：按页生成 PageLayout

**核心设计变更**：`PageLayout` 不再按章共享，而是按页生成。首页 layout 包含 title box，后续页 layout 的 title=null，body 自动向上扩展。

```kotlin
fun paginateChapter(
    chapterIndex: Int,
    title: String,
    content: String,
    config: ReaderLayoutConfig,
    showHeader: Boolean = true,
    showFooter: Boolean = true,
): TextChapter {
    val widthWindow = WidthWindow(content, config.textSize, textMeasurer)
    val pages = mutableListOf<TextPage>()
    var currentOffset = 0
    var pageIndex = 0

    // ── 预计算标题 StaticLayout（首页独有） ──
    val bodyWidth = PageLayoutCalculator.bodyWidth(config.pageSize, config.bodyInsets)
    val titleResult = buildTitleLayout(config, title, bodyWidth) // Pair<StaticLayout?, Float>

    val headerHeight = HEADER_CONTENT_HEIGHT_DP * config.density
    val footerHeight = FOOTER_CONTENT_HEIGHT_DP * config.density

    while (currentOffset < content.length) {
        val isFirstPage = pageIndex == 0

        // ── 按页生成 layout（首页有 title，后续页无 title） ──
        val layout = PageLayoutCalculator.calculate(
            pageSize = config.pageSize,
            header = BoxSpec(
                insets = config.headerInsets,
                innerHeight = headerHeight,
                placement = BoxSpec.Placement.TOP_DOWN,
                visible = showHeader,
            ),
            title = BoxSpec(
                insets = config.titleInsets,
                innerHeight = if (isFirstPage) titleResult.second else 0f,
                placement = BoxSpec.Placement.TOP_DOWN,
                visible = isFirstPage && titleResult.first != null,
            ),
            body = BoxSpec(
                insets = config.bodyInsets,
                placement = BoxSpec.Placement.FILL,
                visible = true,
            ),
            footer = BoxSpec(
                insets = config.footerInsets,
                innerHeight = footerHeight,
                placement = BoxSpec.Placement.BOTTOM_UP,
                visible = showFooter,
            ),
        )

        val page = paginatePage(
            chapterIndex = chapterIndex,
            pageIndex = pageIndex,
            content = content,
            startOffset = currentOffset,
            widthWindow = widthWindow,
            config = config,
            layout = layout,
            titleLayout = if (isFirstPage) titleResult.first else null,
            chapterTitle = title,
        )
        pages.add(page)
        currentOffset = page.endCharOffset
        pageIndex++
    }

    return TextChapter(
        chapterIndex = chapterIndex,
        title = title,
        content = content,
        pages = pages,
    )
}
```

### 5.2 paginatePage — 保证至少一行

```kotlin
private fun paginatePage(
    chapterIndex: Int,
    pageIndex: Int,
    content: String,
    startOffset: Int,
    widthWindow: WidthWindow,
    config: ReaderLayoutConfig,
    layout: PageLayout,
    titleLayout: StaticLayout?,  // 预计算的标题布局，null = 无标题
    chapterTitle: String = "",
): TextPage {
    val body = layout.body
    val availableWidth = body.width
    val lineHeight = textMeasurer.measureTextHeight(config.textSize, config.lineHeight)
    var currentY = body.top
    val maxY = body.bottom
    val indentWidth = config.indent

    // ── 防死循环：保证至少排版一行 ──
    // 即使 body 高度 < lineHeight（极端边距），也强制排版一行，防止 endCharOffset == startOffset
    var mustPlaceAtLeastOneLine = true

    while ((currentY + lineHeight <= maxY + EPSILON || mustPlaceAtLeastOneLine) && currentOffset < content.length) {
        mustPlaceAtLeastOneLine = false
        // ... 原有逐行排版逻辑 ...
        // currentY += lineHeight
        // currentOffset = lineEndOffset
    }

    return TextPage(
        startCharOffset = startOffset,
        endCharOffset = currentOffset,
        chapterIndex = chapterIndex,
        pageIndex = pageIndex,
        lines = lines,
        layout = layout,
        titleLayout = titleLayout,  // 预计算的 StaticLayout，渲染期零分配
        columns = columns,
        density = config.density,
        chapterContentLength = content.length,
        chapterTitle = chapterTitle,
    )
}

companion object {
    /** 浮点精度容差，防止 maxY == currentY + lineHeight 时因精度丢失跳过最后一行 */
    private const val EPSILON = 0.5f

    /** 页眉内容高度常量（dp），避免魔法数字散落 */
    internal const val HEADER_CONTENT_HEIGHT_DP = 24f

    /** 页脚内容高度常量（dp） */
    internal const val FOOTER_CONTENT_HEIGHT_DP = 24f
}
```

> **防死循环**：`mustPlaceAtLeastOneLine` 标志保证即使 `body.height < lineHeight`（用户将边距调到极大），也至少排版一行文本。这打破 `startOffset == endCharOffset` 的死循环条件。
>
> **浮点精度**：`maxY + EPSILON`（0.5px 容差）防止 `Float` 精度丢失导致最后一行被跳过。
>
> **常量提取**：`HEADER_CONTENT_HEIGHT_DP` / `FOOTER_CONTENT_HEIGHT_DP` 替代散落的 `24f` 魔法数字。

### 5.3 buildTitleLayout — StaticLayout 精确预算 + 缓存

```kotlin
/**
 * 预计算标题的 StaticLayout 和总高度。
 * 返回 Pair<StaticLayout?, totalHeight>。
 * - StaticLayout 在分页阶段创建一次，渲染期直接 draw()，零分配。
 * - 高度由 StaticLayout 精确计算，消除旧的 1.05f 经验系数和行数估算误差。
 * - TextPaint 的字体从 config.titleTypeface 获取，确保与渲染期完全一致的度量。
 */
private fun buildTitleLayout(
    config: ReaderLayoutConfig,
    chapterTitle: String,
    availableWidth: Float,
): Pair<StaticLayout?, Float> {
    val ts = config.titleStyle
    if (ts.align == TitleAlign.HIDDEN || chapterTitle.isBlank()) return null to 0f

    val d = config.density
    val titleTextSize = config.textSize + ts.sizeOffsetSp * d
    val paint = TextPaint().apply {
        textSize = titleTextSize
        typeface = config.titleTypeface
        isFakeBoldText = config.titleIsFakeBold
    }

    val layoutAlign = when (ts.align) {
        TitleAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
        TitleAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
        TitleAlign.HIDDEN -> return null to 0f
    }

    val w = availableWidth.toInt().coerceAtLeast(1)
    val layout = StaticLayout.Builder.obtain(
        chapterTitle, 0, chapterTitle.length, paint, w
    ).setAlignment(layoutAlign).setIncludePad(false).build()

    val totalHeight = ts.marginTopDp * d + layout.height + ts.marginBottomDp * d
    return layout to totalHeight
}
```

> **精度提升**：旧方案用 `measureTextWidth * 1.05f / availableWidth` 估算行数，对不同字体/标点挤压不可靠。新方案直接用 `StaticLayout.height` 精确预算，与渲染期完全一致。

---

## 6. TextPage 改造

> 文件：`core/reader/model/TextModels.kt`（第 123-158 行）

### 6.1 After（盒子模型）

```kotlin
class TextPage(
    val startCharOffset: Int,
    val endCharOffset: Int,
    val chapterIndex: Int,
    val pageIndex: Int,
    val lines: List<TextLine>,
    val layout: PageLayout,           // 四个盒子的位置（替代旧的 5 个散落字段）
    val titleLayout: StaticLayout?,   // 预计算的标题布局（首页非 null，后续页 null）
    val columns: List<TextColumn> = emptyList(),
    val density: Float = 3f,
    val chapterContentLength: Int = 0,
    val chapterTitle: String = "",
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}
```

> **titleLayout**：在分页阶段（`buildTitleLayout`）创建并缓存，渲染期直接 `layout.draw(canvas)`，零对象分配。

### 6.2 旧字段迁移映射

| 旧字段 | 新访问方式 | 说明 |
|--------|-----------|------|
| `page.pageSize.width` | `page.layout.pageWidth` | Float 而非 Int |
| `page.pageSize.height` | `page.layout.pageHeight` | Float 而非 Int |
| `page.marginHorizontal` | `page.layout.body.left` / `page.layout.body.right` | 左右边距独立 |
| `page.topContentY` | `page.layout.body.top` | 正文区域顶部 |
| `page.headerMarginTop` | `page.layout.header?.top` | 页眉盒子顶部 |
| `page.footerMarginBottom` | `page.layout.footer?.bottom` | 页脚盒子底部 |

> **TextPage 保持 class（非 data class）**：当前使用引用比较（`equals`/`hashCode` 基于 `System.identityHashCode`），避免大 `lines` 列表的深度比较开销。

---

## 7. ReaderPageRenderer 改造

> 文件：`core/reader/engine/ReaderPageRenderer.kt`

### 7.1 renderShell — 使用 BoxBounds 定位

```kotlin
fun renderShell(
    canvas: Canvas,
    page: TextPage,
    headerSlots: SlotResolution,
    footerSlots: SlotResolution,
    showProgress: Boolean,
    headerAlpha: Float = 0.4f,
    footerAlpha: Float = 0.4f,
    batteryLevel: Int = 100,
    backgroundPaint: Paint? = null,
    showHeaderLine: Boolean = false,
    showFooterLine: Boolean = false,
) {
    val layout = page.layout

    // 1. 背景（全屏）—— 使用四参数重载，避免 RectF 分配
    if (backgroundPaint != null) {
        canvas.drawRect(0f, 0f, layout.pageWidth, layout.pageHeight, backgroundPaint)
    }

    val density = page.density

    // 2. 页眉（在 header box 内绘制）
    layout.header?.let { box ->
        val baseline = box.top + box.height * 0.6f
        drawHeaderFooter(canvas, headerSlots, headerPaint, headerAlpha, baseline, box, batteryLevel, density)

        if (showHeaderLine) {
            val lineY = (baseline + 4f * density).roundToInt().toFloat() // 像素对齐
            dividerPaint.color = headerPaint.color
            dividerPaint.alpha = (headerAlpha * 255 * 0.5f).toInt()
            canvas.drawLine(box.left, lineY, box.right, lineY, dividerPaint)
        }
    }

    // 3. 页脚（在 footer box 内绘制）
    layout.footer?.let { box ->
        val baseline = box.bottom - box.height * 0.4f
        drawHeaderFooter(canvas, footerSlots, footerPaint, footerAlpha, baseline, box, batteryLevel, density)

        if (showFooterLine) {
            val lineY = (baseline - footerPaint.textSize * 0.6f).roundToInt().toFloat() // 像素对齐
            dividerPaint.color = footerPaint.color
            dividerPaint.alpha = (footerAlpha * 255 * 0.5f).toInt()
            canvas.drawLine(box.left, lineY, box.right, lineY, dividerPaint)
        }
    }

    // 4. 进度条
    if (showProgress) {
        val progress = if (page.chapterContentLength > 0) {
            (page.startCharOffset.toFloat() / page.chapterContentLength).coerceIn(0f, 1f)
        } else 0f
        val progressWidth = layout.pageWidth * progress
        // 使用四参数重载，避免 RectF 分配
        canvas.drawRect(0f, layout.pageHeight - 3f * density, progressWidth, layout.pageHeight, progressPaint)
    }
}
```

> **像素对齐**：分割线的 Y 坐标使用 `.roundToInt().toFloat()` 确保 1px 线条不发虚（亚像素抗锯齿问题）。
>
> **零 RectF 分配**：所有 `drawRect` 调用使用四参数重载 `(left, top, right, bottom, paint)`，不创建 `RectF` 对象。

### 7.2 renderContent — 使用预计算的 titleLayout

```kotlin
fun renderContent(
    canvas: Canvas,
    ctx: PageRenderContext,
) {
    val page = ctx.page

    // 正文行（line.top / line.bottom 已经是绝对坐标）
    for ((lineIndex, line) in page.lines.withIndex()) {
        drawLineWithRecorder(canvas, line, lineIndex, ctx)
    }

    // 标题（使用分页阶段预计算的 StaticLayout，零分配）
    page.titleLayout?.let { titleLayout ->
        val titleBox = page.layout.title ?: return@let
        drawChapterTitle(canvas, titleLayout, titleBox, page.density)
    }
}
```

### 7.3 drawChapterTitle — 零分配绘制

```kotlin
/**
 * 使用预计算的 StaticLayout 绘制章节标题。
 * 不再在渲染期创建 StaticLayout——titleLayout 在分页阶段已缓存在 TextPage 中。
 *
 * @param titleLayout 分页阶段预计算的 StaticLayout
 * @param titleBox 标题盒子边界
 */
private fun drawChapterTitle(
    canvas: Canvas,
    titleLayout: StaticLayout,
    titleBox: BoxBounds,
    density: Float,
) {
    val marginTop = titleStyle.marginTopDp * density
    val titleTop = titleBox.top + marginTop

    canvas.save()
    canvas.translate(titleBox.left, titleTop)
    titleLayout.draw(canvas)
    canvas.restore()
}
```

> **零分配**：`StaticLayout` 在 `buildTitleLayout()` 中创建一次（分页阶段），渲染期只做 `translate + draw`，无任何对象分配。
>
> **移除冗余判断**：旧代码的 `if (page.pageIndex != 0) return` 已被 `page.titleLayout?.let` 的 null 检查替代——非首页 `titleLayout` 为 null，不会进入绘制。

### 7.4 drawHeaderFooter — 接受 BoxBounds

```kotlin
/**
 * 在指定的 BoxBounds 内绘制左/中/右三个槽位。
 * 使用 box.left / box.right 作为水平边界，不再依赖 page.marginHorizontal。
 */
private fun drawHeaderFooter(
    canvas: Canvas,
    slots: SlotResolution,
    paint: Paint,
    alpha: Float,
    baseline: Float,
    box: BoxBounds,
    batteryLevel: Int,
    density: Float,
) {
    if (slots.isEmpty()) return

    val oldAlpha = paint.alpha
    paint.alpha = (alpha * 255).toInt()

    fun drawSlot(text: String, content: SlotContent, x: Float, align: Paint.Align) {
        if (text.isEmpty()) return
        if (content == SlotContent.BATTERY) {
            drawBatteryAt(canvas, x, baseline, align, batteryLevel, density, paint)
        } else {
            paint.textAlign = align
            canvas.drawText(text, x, baseline, paint)
        }
    }

    drawSlot(slots.left, slots.leftContent, box.left, Paint.Align.LEFT)
    drawSlot(slots.center, slots.centerContent, (box.left + box.right) / 2f, Paint.Align.CENTER)
    drawSlot(slots.right, slots.rightContent, box.right, Paint.Align.RIGHT)

    paint.alpha = oldAlpha
    paint.textAlign = Paint.Align.LEFT
}
```

### 7.5 PageRenderContext — availableWidth 来源变化

```kotlin
class PageRenderContext(
    val content: CharSequence,
    val page: TextPage,
    val textPaint: Paint,
    val letterSpacingPx: Float,
    val availableWidth: Float,       // 来自 page.layout.body.width
    val renderStateStore: PageRenderStateStore,
)
```

---

## 8. 渲染层与 recorder 的映射

保持现有 3 层 recorder 架构不变：

| Recorder 层 | 包含的盒子 | 失效条件 |
|-------------|-----------|---------|
| **shell** | header box + footer box + 背景 + 进度条 | 时间/电量/主题变化 |
| **content** | body box（正文 + 逐行 recorder）+ title box | 字号/排版变化 |
| **overlay** | 选区高亮 + 笔记高亮 | 用户交互 |

盒子模型只改变了"每个盒子在哪里绘制"，不改变"哪些盒子属于哪个 recorder 层"。

---

## 9. 系统 WindowInsets 整合

> 文件：`core/reader/model/BoxInsets.kt`（扩展函数）

### 9.1 安全区合并

在全面屏/刘海屏的沉浸式模式下，固定的用户边距可能被状态栏或导航栏遮挡。需要将系统安全区与用户边距合并：

```kotlin
/**
 * 将系统安全区合并到盒子边距中。
 * 实际边距 = max(用户设置边距, 系统安全区)。
 *
 * @param insets 系统安全区（px），来自 WindowInsetsCompat.getInsets(Type.systemBars())
 *               返回类型为 androidx.core.graphics.Insets（非 android.graphics.Rect）
 */
fun BoxInsetsPx.withSystemInsets(insets: androidx.core.graphics.Insets): BoxInsetsPx = BoxInsetsPx(
    top = maxOf(this.top, insets.top.toFloat()),
    bottom = maxOf(this.bottom, insets.bottom.toFloat()),
    left = maxOf(this.left, insets.left.toFloat()),
    right = maxOf(this.right, insets.right.toFloat()),
)
```

> **类型修正**：`WindowInsetsCompat.getInsets()` 返回 `androidx.core.graphics.Insets`，非 `android.graphics.Rect`。直接接受 `Insets` 类型，避免不必要的转换。

### 9.2 数据流

WindowInsets 只能在 Composable/View 层获取（通过 `LocalWindowInsets` 或 `View.setOnApplyWindowInsetsListener`），不能直接在 ViewModel 中读取。数据流如下：

```
Compose/View 层（获取 WindowInsets）
  → ReaderScreenState.systemInsets: Insets
    → Paginator 入口（合并到 config 的 BoxInsetsPx）
      → PageLayoutCalculator.calculate()
```

### 9.3 合并范围

header、footer、**body** 都需要合并安全区——横屏刘海会遮挡正文左右区域：

```kotlin
// 在 Paginator 入口处（Compose 层传入 systemInsets）
val systemInsets = ... // 从 ReaderScreenState 获取

// 仅在沉浸式模式下合并
val mergedHeaderInsets = if (isImmersive) {
    config.headerInsets.withSystemInsets(systemInsets)
} else config.headerInsets

val mergedFooterInsets = if (isImmersive) {
    config.footerInsets.withSystemInsets(systemInsets)
} else config.footerInsets

val mergedBodyInsets = if (isImmersive) {
    // body 仅需要左右安全区（横屏刘海）
    config.bodyInsets.withSystemInsets(
        Insets.of(systemInsets.left, 0, systemInsets.right, 0)
    )
} else config.bodyInsets
```

> **注意**：仅在沉浸式模式启用时合并。非沉浸式模式下系统已自动处理安全区，不需要额外偏移。

---

## 10. ReaderPreferences 改造

> 文件：`core/data/ReaderPreferences.kt`

### 10.1 字段变更（4 个 BoxInsetsDp 替代 16 个 Float）

```kotlin
@Serializable
data class ReaderPreferences(
    // ... 其他字段不变 ...

    // ── 移除（不再需要全局边距） ──
    // val marginHorizontal: Float,   ← 移除
    // val marginVertical: Float,     ← 移除
    // val marginTop: Float?,         ← 移除
    // val marginBottom: Float?,      ← 移除
    // val marginLeft: Float?,        ← 移除
    // val marginRight: Float?,       ← 移除

    // ── HeaderConfig / FooterConfig 中的 marginTop / marginBottom 移入盒子边距 ──
    // val header: HeaderConfig,      ← marginTop 字段移除，其余保留
    // val footer: FooterConfig,      ← marginBottom 字段移除，其余保留

    // ── 盒子边距（dp，非 nullable，有合理默认值） ──
    val bodyBox: BoxInsetsDp = BoxInsetsDp(top = 48f, bottom = 48f, left = 24f, right = 24f),
    val headerBox: BoxInsetsDp = BoxInsetsDp(top = 16f, bottom = 0f, left = 24f, right = 24f),
    val footerBox: BoxInsetsDp = BoxInsetsDp(top = 0f, bottom = 16f, left = 24f, right = 24f),
    val titleBox: BoxInsetsDp = BoxInsetsDp(top = 9f, bottom = 10f, left = 24f, right = 24f),
)
```

> **从 16 个字段收敛为 4 个**：`BoxInsetsDp` 是 `@Serializable data class`，kotlinx.serialization 直接支持。DataStore 序列化体积更小，代码更清晰。
>
> **默认值说明**：
> - `bodyBox`：48f/48f/24f/24f 对应旧的 `marginVertical=48` / `marginHorizontal=24`
> - `headerBox`：16f/0f/24f/24f 对应旧的 `headerMarginTop=16`
> - `footerBox`：0f/16f/24f/24f 对应旧的 `footerMarginBottom=16`
> - `titleBox`：9f/10f/24f/24f 对应旧的 `titleStyle.marginTopDp=9` / `marginBottomDp=10`

### 10.2 HeaderConfig / FooterConfig 改造

> 文件：`core/reader/model/HeaderFooterModels.kt`

```kotlin
@Serializable
data class HeaderConfig(
    val visibility: HeaderVisibility = HeaderVisibility.HIDE_WHEN_STATUS_BAR,
    val left: SlotContent = SlotContent.CHAPTER_TITLE,
    val center: SlotContent = SlotContent.NONE,
    val right: SlotContent = SlotContent.NONE,
    // val marginTop: Float = 48f,   ← 移除，由 headerBox.top 替代
)

@Serializable
data class FooterConfig(
    val visibility: HeaderVisibility = HeaderVisibility.ALWAYS_SHOW,
    val left: SlotContent = SlotContent.BOOK_PROGRESS_PERCENT,
    val center: SlotContent = SlotContent.CHAPTER_PROGRESS_FRACTION,
    val right: SlotContent = SlotContent.TIME,
    // val marginBottom: Float = 48f, ← 移除，由 footerBox.bottom 替代
)
```

---

## 11. 设置界面改造

### 11.1 预设 + 高级编辑（分层设计）

> 设计原则：默认展示 2-4 个常用预设，高级区展开四个盒子的独立编辑。避免 16 个滑块直出劝退用户。

```kotlin
SettingsCard(title = strings.marginCardTitle) {
    // ── 预设区（默认展示） ──
    MarginPresetRow(
        selected = currentPreset,
        onSelect = { onPresetSelected(it) },
        presets = listOf(
            MarginPreset(strings.marginPresetCompact, BoxInsetsDp(32f, 32f, 16f, 16f), ...),
            MarginPreset(strings.marginPresetStandard, BoxInsetsDp(48f, 48f, 24f, 24f), ...),
            MarginPreset(strings.marginPresetRelaxed, BoxInsetsDp(64f, 64f, 32f, 32f), ...),
        ),
    )

    // ── 高级编辑（可折叠，默认收起） ──
    CollapsibleSection(title = strings.marginAdvancedTitle) {
        BoxMarginSection(title = strings.bodyBoxLabel, insets = prefs.bodyBox, ...)
        BoxMarginSection(title = strings.headerBoxLabel, insets = prefs.headerBox, collapsible = true, ...)
        BoxMarginSection(title = strings.footerBoxLabel, insets = prefs.footerBox, collapsible = true, ...)
        BoxMarginSection(title = strings.titleBoxLabel, insets = prefs.titleBox, collapsible = true, ...)
    }
}
```

### 11.2 新增 BoxMarginSection 可复用组件

> 文件：`feature/reader/settings/panel/controls/BoxMarginSection.kt`

```kotlin
/**
 * 盒子边距设置区段：可折叠标题 + 4 个 InkStepperSlider（上/下/左/右）+ 可选同步开关。
 *
 * 无状态组件：只接收 value 和 onValueChange，不在内部藏业务状态。
 * 同步行为由外层统一管理（避免连续两次 onValueChange 触发两次 REFLOW）。
 *
 * 拖拽行为：Slider 的 onValueChange 仅更新本地 UI 状态，
 * onValueChangeFinished 才触发实际的 Setting 变更和排版，避免拖拽过程中频繁 reflow。
 */
@Composable
fun BoxMarginSection(
    title: String,
    insets: BoxInsetsDp,         // 使用 BoxInsetsDp 而非 4 个独立 Float
    onInsetsChange: (BoxInsetsDp) -> Unit,
    syncLabel: String,
    topLabel: String,
    bottomLabel: String,
    leftLabel: String,
    rightLabel: String,
    collapsible: Boolean = false,
    initiallyExpanded: Boolean = !collapsible,
    modifier: Modifier = Modifier,
) {
    // 本地 UI 状态（拖拽中不触发 REFLOW）
    var localTop by remember(insets.top) { mutableFloatStateOf(insets.top) }
    var localBottom by remember(insets.bottom) { mutableFloatStateOf(insets.bottom) }
    var localLeft by remember(insets.left) { mutableFloatStateOf(insets.left) }
    var localRight by remember(insets.right) { mutableFloatStateOf(insets.right) }

    // 可折叠分组标题
    // 内部 4 个 InkStepperSlider：
    //   上边距 (0..96dp, step=4)
    //   下边距 (0..96dp, step=4)
    //   左边距 (0..96dp, step=4)
    //   右边距 (0..96dp, step=4)
    //
    // 每个 Slider 的 onValueChange → 更新 localXxx
    // 每个 Slider 的 onValueChangeFinished → onInsetsChange(BoxInsetsDp(localTop, ...))
}
```

> **无状态组件**：只接收 `insets` 和 `onInsetsChange`，不在内部藏业务状态。同步上下/左右的行为由外层管理。
>
> **拖拽防抖**：`onValueChange` 仅更新本地 UI 状态，`onValueChangeFinished` 才触发 Setting 变更，避免拖拽过程中频繁触发 reflow。

### 11.3 MarginPreset — 预设组件

```kotlin
data class MarginPreset(
    val label: String,
    val bodyBox: BoxInsetsDp,
    val headerBox: BoxInsetsDp,
    val footerBox: BoxInsetsDp,
    val titleBox: BoxInsetsDp,
)

@Composable
fun MarginPresetRow(
    selected: MarginPreset?,
    onSelect: (MarginPreset) -> Unit,
    presets: List<MarginPreset>,
) {
    // 横向滚动的 Chip/Button 行
    // 点击预设 → 调用 onSelect → 外层批量更新 4 个 BoxInsetsDp
}
```

### 11.4 预设与同步开关的交互规则

预设（`MarginPreset`）和同步开关（sync）是两个独立机制，交互规则如下：

| 场景 | 行为 |
|------|------|
| 用户选择预设 | 预设直接设置 4 个盒子的 BoxInsetsDp（通过 `batchUpdateBoxInsets`），同步开关状态不变 |
| 用户选择预设后手动改某盒子的某边距 | 同步开关若开启，仅影响当前正在编辑的盒子内部（如同步 bodyBox 的上下），不影响其他盒子 |
| 同步开关的作用域 | **单盒子内**：同步 bodyBox.top/bottom 或 bodyBox.left/right。**不跨盒子**：不会同步 bodyBox.top 和 headerBox.top |
| 同步开关开启瞬间 | 将当前盒子的 top 值同步到 bottom，left 值同步到 right（沿用现有行为） |
| 预设是否重置同步开关 | **不重置**。预设改变数值，同步开关保持用户选择 |

> **设计原则**：预设是"批量设值"，同步是"联动编辑"。两者正交，互不影响。

---

## 12. 偏好设置改造

### 12.1 ReaderSettingRegistry — 注册新 key

> 文件：`feature/reader/settings/ReaderSettingRegistry.kt`

```kotlin
// ── 盒子边距（替代旧的 margin_horizontal / margin_vertical / margin_top/bottom/left/right） ──
// 使用 BoxInsetsDp 的 4 个组合 key，而非 16 个独立 key
add(SettingDefinition(key = "body_box",    defaultValue = BoxInsetsDp(48f, 48f, 24f, 24f), storageTier = StorageTier.BOTH, scope = InvalidationScope.REFLOW, recompositionTier = 3, uiGroup = UiGroup.TEXT_LAYOUT, includeInPreset = true, previewStrategy = PreviewStrategy.LIVE))
add(SettingDefinition(key = "header_box",  defaultValue = BoxInsetsDp(16f, 0f, 24f, 24f), /* ...同上... */))
add(SettingDefinition(key = "footer_box",  defaultValue = BoxInsetsDp(0f, 16f, 24f, 24f), /* ...同上... */))
add(SettingDefinition(key = "title_box",   defaultValue = BoxInsetsDp(9f, 10f, 24f, 24f), /* ...同上... */))
```

> **SettingDefinition 值类型扩展**：
> 现有 `SettingDefinition<T>` 是泛型，已支持 `Float`、`Float?`、`Boolean`、`ReaderTheme` 等类型。
> `BoxInsetsDp` 作为 `@Serializable data class`，可直接作为 `T` 传入。
> 需要确认的改造点：
> 1. `ReaderSettingRegistry` 的反序列化逻辑（DataStore/Room 读取时需正确反序列化 `BoxInsetsDp`）
> 2. 预设系统（`includeInPreset = true`）的序列化/反序列化
> 3. `ReaderPreferences` 的 `@Serializable` 注解确保 kotlinx.serialization 能处理嵌套的 `BoxInsetsDp`

### 12.2 移除旧 key

```kotlin
// 移除以下 SettingDefinition：
// "margin_horizontal", "margin_vertical"
// "margin_top", "margin_bottom", "margin_left", "margin_right"
```

### 12.3 ReaderIntent.kt — ReaderSettingKey 枚举扩展

> 文件：`feature/reader/screen/ReaderIntent.kt`（第 119-146 行）

```kotlin
enum class ReaderSettingKey {
    // ... 其他 key 不变 ...

    // ── 移除 ──
    // MARGIN_HORIZONTAL, MARGIN_VERTICAL,
    // MARGIN_TOP, MARGIN_BOTTOM, MARGIN_LEFT, MARGIN_RIGHT,

    // ── 新增盒子边距（4 个 key，每个携带完整 BoxInsetsDp） ──
    BODY_BOX, HEADER_BOX, FOOTER_BOX, TITLE_BOX,
}
```

### 12.4 ReaderSettingsManager — 批量更新 API

```kotlin
// ── 单个盒子边距更新 ──
fun setBodyBox(insets: BoxInsetsDp) {
    updatePrefsGeneric({ it.copy(bodyBox = insets) }, reflow = true)
}

fun setHeaderBox(insets: BoxInsetsDp) {
    updatePrefsGeneric({ it.copy(headerBox = insets) }, reflow = true)
}

fun setFooterBox(insets: BoxInsetsDp) {
    updatePrefsGeneric({ it.copy(footerBox = insets) }, reflow = true)
}

fun setTitleBox(insets: BoxInsetsDp) {
    updatePrefsGeneric({ it.copy(titleBox = insets) }, reflow = true)
}

// ── 批量更新（预设切换、同步边距等场景） ──
/**
 * 批量更新多个盒子边距，只触发一次 reflow。
 * 避免连续调用多个 setXxxBox() 导致触发多次不必要的全局重新分页。
 */
fun batchUpdateBoxInsets(
    body: BoxInsetsDp? = null,
    header: BoxInsetsDp? = null,
    footer: BoxInsetsDp? = null,
    title: BoxInsetsDp? = null,
) {
    updatePrefsGeneric(
        transform = { prefs ->
            prefs.copy(
                bodyBox = body ?: prefs.bodyBox,
                headerBox = header ?: prefs.headerBox,
                footerBox = footer ?: prefs.footerBox,
                titleBox = title ?: prefs.titleBox,
            )
        },
        reflow = true,
    )
}
```

> **批量更新**：`batchUpdateBoxInsets` 在一次 `updatePrefsGeneric` 调用中更新所有盒子，只触发一次 reflow。预设切换和同步边距场景使用此 API。

### 12.5 ReaderViewModel — dispatchSetting 分发

```kotlin
private fun dispatchSetting(key: ReaderSettingKey, value: ReaderSettingValue) {
    val s = readerSettingsManager
    when (key) {
        // ... 其他 key 不变 ...

        // ── 盒子边距 ──
        ReaderSettingKey.BODY_BOX   -> s.setBodyBox((value as ReaderSettingValue.BoxInsetsDpVal).value)
        ReaderSettingKey.HEADER_BOX -> s.setHeaderBox((value as ReaderSettingValue.BoxInsetsDpVal).value)
        ReaderSettingKey.FOOTER_BOX -> s.setFooterBox((value as ReaderSettingValue.BoxInsetsDpVal).value)
        ReaderSettingKey.TITLE_BOX  -> s.setTitleBox((value as ReaderSettingValue.BoxInsetsDpVal).value)
    }
}
```

### 12.6 ReaderSettingValue 扩展

```kotlin
sealed interface ReaderSettingValue {
    // ... 其他类型不变 ...

    /** 盒子边距值 */
    data class BoxInsetsDpVal(val value: BoxInsetsDp) : ReaderSettingValue
}
```

### 12.7 i18n 字符串

> 文件：`core/i18n/ReaderStrings.kt`（interface）

```kotlin
// ── 盒子边距标签（新增） ──
val marginCardTitle: String          // "边距"
val bodyBoxLabel: String             // "正文"
val headerBoxLabel: String           // "页眉"
val footerBoxLabel: String           // "页脚"
val titleBoxLabel: String            // "标题"
val boxMarginTop: String             // "上边距"
val boxMarginBottom: String          // "下边距"
val boxMarginLeft: String            // "左边距"
val boxMarginRight: String           // "右边距"
val marginAdvancedTitle: String      // "高级边距"
val marginPresetCompact: String      // "紧凑"
val marginPresetStandard: String     // "标准"
val marginPresetRelaxed: String      // "舒展"
val syncMarginsLabel: String         // "同步上下 / 左右"
```

---

## 13. PageCountPersistence 改造

> 文件参考：`PageCountPersistence.kt`

`computeLayoutHash` 需要包含盒子边距，使用 `Float.toBits()` 确保精确 hash（避免 `Float.hashCode()` 的碰撞风险）：

```kotlin
fun computeLayoutHash(config: ReaderLayoutConfig): Int {
    var result = config.pageSize.hashCode()
    result = 31 * result + config.textSize.toBits()
    result = 31 * result + config.lineHeight.toBits()
    result = 31 * result + config.paragraphSpacing.toBits()
    result = 31 * result + config.indent.toBits()
    result = 31 * result + config.density.toBits()
    result = 31 * result + config.letterSpacingPx.toBits()
    result = 31 * result + config.useZhLayout.hashCode()
    result = 31 * result + config.bottomJustify.hashCode()
    // ── 盒子边距参与 hash（使用 toBits 精确比较） ──
    result = hashBoxInsets(result, config.headerInsets)
    result = hashBoxInsets(result, config.titleInsets)
    result = hashBoxInsets(result, config.bodyInsets)
    result = hashBoxInsets(result, config.footerInsets)
    return result
}

private fun hashBoxInsets(seed: Int, insets: BoxInsetsPx): Int {
    var result = seed
    result = 31 * result + insets.top.toBits()
    result = 31 * result + insets.bottom.toBits()
    result = 31 * result + insets.left.toBits()
    result = 31 * result + insets.right.toBits()
    return result
}
```

> **Float.toBits()**：比 `Float.hashCode()` 更精确——不同 Float 值不会碰撞。对于页面缓存 key 来说，低碰撞率至关重要。

---

## 14. 实施步骤

### Phase 1: 数据模型 + 布局引擎

| # | 任务 | 文件 |
|---|------|------|
| 1 | 新增 `BoxInsetsDp` + `BoxInsetsPx` 数据类 | `core/reader/model/BoxInsets.kt`（新增） |
| 2 | 新增 `BoxSpec` + `BoxBounds` + `PageLayout` 数据类 | `core/reader/model/BoxSpec.kt`（新增） |
| 3 | 新增 `PageLayoutCalculator`（含 `bodyWidth` 工具方法） | `core/reader/engine/PageLayoutCalculator.kt`（新增） |
| 4 | `ReaderLayoutConfig` 移除旧 margin 字段，替换为 4 个 `BoxInsetsPx` | `core/reader/model/TextModels.kt` |
| 5 | `toLayoutConfig()` 改用 `BoxInsetsDp.toPx(density)` | `core/data/ReaderPreferences.kt` |

### Phase 2: Paginator + TextPage 迁移

| # | 任务 | 文件 |
|---|------|------|
| 6 | `TextPage` 移除旧字段，新增 `layout: PageLayout` + `titleLayout: StaticLayout?` | `core/reader/model/TextModels.kt` |
| 7 | 新增 `buildTitleLayout()` — StaticLayout 预计算 + 缓存 | `core/reader/engine/Paginator.kt` |
| 8 | `paginatePage` 接受 `PageLayout` + `titleLayout`，保证至少一行 + 浮点容差 | `core/reader/engine/Paginator.kt` |
| 9 | `paginateChapter` 按页生成 layout（首页有 title，后续页无 title） | `core/reader/engine/Paginator.kt` |
| 10 | 提取 `HEADER_CONTENT_HEIGHT_DP` / `FOOTER_CONTENT_HEIGHT_DP` 常量 | `core/reader/engine/Paginator.kt` |
| 11 | 更新所有 `TextPage` 构造点和字段访问方 | 多个文件 |

### Phase 3: 渲染器迁移

| # | 任务 | 文件 |
|---|------|------|
| 12 | `renderShell` 使用 `layout.header`/`layout.footer` 定位 + 像素对齐 + 零 RectF 分配 | `core/reader/engine/ReaderPageRenderer.kt` |
| 13 | `renderContent` 使用预计算的 `page.titleLayout` 绘制标题 | `core/reader/engine/ReaderPageRenderer.kt` |
| 14 | `drawChapterTitle` 签名改为接受 `StaticLayout` + `BoxBounds`，移除冗余 pageIndex 判断 | `core/reader/engine/ReaderPageRenderer.kt` |
| 15 | `drawHeaderFooter` 签名改为接受 `BoxBounds` | `core/reader/engine/ReaderPageRenderer.kt` |
| 16 | `PageRenderContext.availableWidth` 改为 `page.layout.body.width` | `core/reader/engine/PageRenderContext.kt` |
| 17 | `StatelessReaderPageRenderer` 适配 | `core/reader/engine/StatelessReaderPageRenderer.kt` |

### Phase 4: 偏好设置 + 设置界面

| # | 任务 | 文件 |
|---|------|------|
| 18 | `ReaderPreferences` 移除旧 margin 字段，新增 4 个 `BoxInsetsDp` 字段 | `core/data/ReaderPreferences.kt` |
| 19 | `HeaderConfig`/`FooterConfig` 移除 `marginTop`/`marginBottom` | `core/reader/model/HeaderFooterModels.kt` |
| 20 | `ReaderSettingRegistry` 注册 4 个新 key（BoxInsetsDp），移除 6 个旧 key | `feature/reader/settings/ReaderSettingRegistry.kt` |
| 21 | `ReaderSettingKey` 枚举新增 4 个值，移除 6 个旧值 | `feature/reader/screen/ReaderIntent.kt` |
| 22 | `ReaderSettingValue` 新增 `BoxInsetsDpVal` 类型 | `feature/reader/screen/ReaderIntent.kt` |
| 23 | `ReaderSettingsManager` 新增 4 个 setter + `batchUpdateBoxInsets` | `feature/reader/settings/ReaderSettingsManager.kt` |
| 24 | `ReaderViewModel.dispatchSetting` 新增 4 个分支 | `feature/reader/screen/ReaderViewModel.kt` |
| 25 | 新增 `BoxMarginSection` + `MarginPresetRow` Compose 组件 | `feature/reader/settings/panel/controls/`（新增） |
| 26 | `TypeAndFontTab` 替换旧边距区段为预设 + 高级编辑 | `feature/reader/settings/panel/tabs/TypeAndFontTab.kt` |
| 27 | i18n 字符串新增（3 个实现文件） | `core/i18n/ReaderStrings.kt` + `ReaderStringsImpl.kt` |
| 28 | `PageCountPersistence.computeLayoutHash` 用 `toBits()` + 包含盒子边距 | `PageCountPersistence.kt` |

### Phase 5: 系统集成 + 测试

| # | 任务 | 文件 |
|---|------|------|
| 29 | `BoxInsetsPx.withSystemInsets()` 安全区合并 | `core/reader/model/BoxInsets.kt` |
| 30 | 沉浸式模式下 header/footer 边距合并 WindowInsets | `ReaderViewModel.kt` 或 `Paginator` 入口 |
| 31 | 更新 Paginator 测试（含极端边距死循环测试） | 测试文件 |
| 32 | 更新渲染测试 | 测试文件 |
| 33 | 编译验证 + 单元测试 | CI |

---

## 15. 影响范围总览

| 文件 | 改动类型 | 说明 |
|------|---------|------|
| `core/reader/model/BoxInsets.kt` | **新增** | BoxInsetsDp + BoxInsetsPx + withSystemInsets |
| `core/reader/model/BoxSpec.kt` | **新增** | BoxSpec + BoxBounds + PageLayout |
| `core/reader/engine/PageLayoutCalculator.kt` | **新增** | 布局引擎（统一盒子循环 + bodyWidth） |
| `feature/reader/settings/panel/controls/BoxMarginSection.kt` | **新增** | 设置 UI 组件 |
| `feature/reader/settings/panel/controls/MarginPresetRow.kt` | **新增** | 预设选择组件 |
| `core/reader/model/TextModels.kt` | **重构** | TextPage 新增 layout+titleLayout；ReaderLayoutConfig 替换 margin 为 BoxInsetsPx |
| `core/reader/model/HeaderFooterModels.kt` | **重构** | HeaderConfig/FooterConfig 移除 marginTop/marginBottom |
| `core/reader/engine/Paginator.kt` | **重构** | 按页生成 layout + buildTitleLayout + 防死循环 + 常量提取 |
| `core/reader/engine/ReaderPageRenderer.kt` | **重构** | 零分配渲染 + 预计算 titleLayout + 像素对齐 |
| `core/reader/engine/StatelessReaderPageRenderer.kt` | **适配** | 跟随 ReaderPageRenderer 变化 |
| `core/reader/engine/PageRenderContext.kt` | **小改** | availableWidth 来源变化 |
| `core/data/ReaderPreferences.kt` | **重构** | 4 个 BoxInsetsDp 替代 16 个 Float，重写 toLayoutConfig |
| `feature/reader/settings/ReaderSettingRegistry.kt` | **重构** | 4 个新 key（BoxInsetsDp），移除 6 个旧 key |
| `feature/reader/screen/ReaderIntent.kt` | **重构** | ReaderSettingKey + ReaderSettingValue 变更 |
| `feature/reader/settings/ReaderSettingsManager.kt` | **重构** | 4 个 setter + batchUpdateBoxInsets |
| `feature/reader/screen/ReaderViewModel.kt` | **重构** | dispatchSetting 新增 4 个分支 |
| `feature/reader/settings/panel/tabs/TypeAndFontTab.kt` | **重构** | 预设 + 高级编辑 |
| `core/i18n/ReaderStrings.kt` | **扩展** | 新增 13 个字符串字段 |
| `core/i18n/ReaderStringsImpl.kt` | **扩展** | 3 个实现各新增 13 个字符串 |
| `PageCountPersistence.kt` | **小改** | computeLayoutHash 用 toBits + 包含盒子边距 |
| 测试文件 | **适配** | 适配新字段 + 极端边距测试 |
| `PageRenderStateStore.kt` | **无变化** | recorder 层级不变 |

---

## 16. 开发期策略

项目处于开发期，尚未发布。不需要向后兼容：

- **旧字段直接删除**：不保留 `@Deprecated` 注解
- **无数据迁移逻辑**：用户无历史数据，无需 DataStore 迁移
- **非 nullable 字段**：4 个 BoxInsetsDp 字段全部非 nullable，直接使用合理默认值
- **同步更新**：所有调用方同步更新，不留过渡期

### 移除清单

| 文件 | 移除内容 |
|------|---------|
| `ReaderLayoutConfig` | `marginTop/Bottom/Left/Right`、`headerMarginTop`、`footerMarginBottom`、`marginHorizontal`、`marginVertical` |
| `TextPage` | `topContentY`、`marginHorizontal`、`headerMarginTop`、`footerMarginBottom`、`pageSize` |
| `HeaderConfig` | `marginTop` |
| `FooterConfig` | `marginBottom` |
| `ReaderPreferences` | `marginHorizontal`、`marginVertical`、`marginTop?`、`marginBottom?`、`marginLeft?`、`marginRight?` |
| `ReaderSettingRegistry` | `margin_horizontal`、`margin_vertical`、`margin_top/bottom/left/right` |
| `ReaderSettingKey` | `MARGIN_HORIZONTAL`、`MARGIN_VERTICAL`、`MARGIN_TOP/BOTTOM/LEFT/RIGHT` |

---

## 17. 已知限制与未来扩展

### 17.1 双页模式（未支持）

当前设计仅支持单列布局。双页模式（宽屏横屏左右翻页）需要：

- `PageLayout` 引入 `PageSlot`（LEFT / RIGHT）概念
- 或将 `BoxSpec.Placement` 扩展为 `FILL_LEFT` / `FILL_RIGHT`
- 或引入独立的 `DualPageLayout` 类型

**预计影响**：`PageLayoutCalculator.calculate()` 的签名和内部逻辑、`TextPage` 的 layout 字段。

### 17.2 RTL（从右向左）布局（未支持）

当前 `BoxInsetsDp` 使用 `left/right` 命名。如果未来支持阿拉伯语/希伯来语：

- 用户界面层应使用 `start/end` 语义
- `toLayoutConfig()` 根据 `LayoutDirection` 将 `start` 映射为 `left`（LTR）或 `right`（RTL）
- `BoxBounds` 的坐标系不变（Canvas 坐标永远是 LTR）

### 17.3 盒子背景与边框（预留）

当前 `BoxSpec` 不包含背景/边框配置。未来可扩展：

```kotlin
data class BoxSpec(
    // ... 现有字段 ...
    // val background: Int? = null,     // 预留：盒子背景色
    // val border: BorderSpec? = null,  // 预留：盒子边框
)
```

渲染层可在 `renderShell` 中根据 `BoxSpec` 配置先绘制背景，再绘制文字。
