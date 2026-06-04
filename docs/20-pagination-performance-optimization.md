# 20. 分页器性能优化方案 v4（终版）

> 日期：2026-06-03
> 状态：可实施
> 允许破坏性重构

---

## 1. 问题背景

分页器 `Paginator.calculateLine()` 在构建每行数据时存在逐字符对象分配和重复遍历问题。

以 5000 字章节为例（约 120 行、每行 ~40 字符）：

| 指标 | 当前 | 说明 |
|------|------|------|
| String 分配 | ~5000 | `content[i].toString()` 每字符一次 |
| CharColumn 对象 | ~5000 | `data class CharColumn(String, Float)` |
| substring 分配 | ~120 | 每行一次 `content.substring()` |
| widthWindow 遍历 | 3 遍/行 | 判定 charCount + 构建 CharColumn + 累计 measuredWidth |

总计约 **10000+ 堆对象分配/章节**，GC 压力显著。

---

## 2. 核心设计变更

### 2.1 从对象模型改为区间模型

**之前：**

```
TextChapter
 └─ content: String
 └─ pages
      └─ TextPage
           └─ TextLine
               ├─ text: String          ← substring 分配
               └─ charColumns: List<CharColumn>  ← 每字符一个对象
                   └─ CharColumn(charData: String, charWidth: Float)
```

**之后：**

```
TextChapter
 ├─ content: String               ← 唯一文本存储，所有 Page/Line 引用此区间
 └─ pages
      └─ TextPage
           ├─ startCharOffset: Int   ← 可推导字段（见 §3.3）
           ├─ endCharOffset: Int     ← 可推导字段（见 §3.3）
           └─ TextLine
               ├─ start: Int          ← 字符区间 [start, end)，相对于 content
               ├─ end: Int
               ├─ measuredWidth: Float ← 分页时预计算（见 §4.2 口径定义）
               └─ charWidths: FloatArray? ← 渲染辅助缓存（见 §3.2）
```

**关键决策：原文存储在 `TextChapter.content`，不放在 `TextPage`。**

理由：
- 3MB 章节同时缓存上一页/当前页/下一页时，语义上 Page 不应持有大文本
- 渲染器通过 `PageRenderContext` 统一获取 `content`（见 §5），职责清晰
- 未来缓存策略（Page 淘汰/重建）不受大文本引用干扰

### 2.2 渲染路径

**之前：**

```kotlin
// 每字符 drawText(String) — 需要 CharColumn.charData
for (col in line.charColumns) {
    drawText(col.charData, currentX, y, textPaint)
    currentX += col.charWidth + spacingPerChar
}
```

**之后：**

```kotlin
// 直接从 content 绘制，零分配
canvas.drawText(content, line.start, line.end, x, y, textPaint)

// 两端对齐：逐字符定位，用 CharSequence 重载
val charCount = line.end - line.start
for (i in 0 until charCount) {
    canvas.drawText(content, line.start + i, line.start + i + 1, currentX, y, textPaint)
    if (i < charCount - 1) {
        currentX += widths[i] + letterSpacingPx + justifySpacing
    }
}
```

关键 API（Web 搜索确认）：
- `Canvas.drawText(CharSequence, start, end, x, y, paint)` — **无 String 分配**
- `Paint.getTextWidths(String, start, end, float[])` — 批量填充，单次 JNI

---

## 3. 数据模型变更

### 3.1 删除 CharColumn

```kotlin
// 删除
data class CharColumn(val charData: String, val charWidth: Float)
```

### 3.2 TextLine 改为区间模型

```kotlin
data class TextLine(
    /** 行在 content 中的起始偏移（含） */
    val start: Int,
    /** 行在 content 中的结束偏移（不含） */
    val end: Int,
    /**
     * 文本实际占用宽度（分页时缓存）。
     *
     * 口径定义：measuredWidth = Σ(charWidth[i]) + letterSpacingPx × (charCount - 1)
     * - 包含：字符原始宽度 + 基础字距
     * - 不包含：两端对齐额外拉伸量
     *
     * 高亮、命中测试、分页回算均使用此口径。
     */
    val measuredWidth: Float = 0f,
    /** 是否为段落末行 */
    val isParagraphEnd: Boolean = false,
    /** 段落首行缩进宽度（像素） */
    val startXOffset: Float = 0f,
    /** 行在页面中的 Y 坐标 */
    val baseline: Float = 0f,
    val top: Float = 0f,
    val bottom: Float = 0f,
    /**
     * 字符宽度数组（不含字距），仅两端对齐模式且非段落末行时有值。
     * 长度 = end - start，每个元素为对应字符的原始宽度。
     *
     * 性质：渲染辅助缓存，不是内容模型。
     * - 从分页阶段的 widthWindow 复制而来，与 widthWindow 完全解耦
     * - 渲染时用于逐字符定位，不参与持久化、序列化、equals/hashCode
     * - 未来若引入 codePoint 遍历，此数组将随之重构
     */
    val charWidths: FloatArray? = null,
) {
    @Transient var canvasRecorder: CanvasRecorder? = null
    // ... invalidateSelf / recycleRecorder 不变
}
```

注意：`text: String` 字段已删除。渲染时通过 `PageRenderContext.content` + `start/end` 访问。

### 3.3 TextPage 结构

```kotlin
class TextPage(
    /**
     * 页在 content 中的起止偏移。
     * 技术上可从 lines.first().start / lines.last().end 推导，
     * 保留为快速定位字段（进度跳转、选区计算），
     * 不参与核心渲染逻辑。
     */
    val startCharOffset: Int,
    val endCharOffset: Int,
    val lines: List<TextLine>,
    // ... 其余字段不变
    // 注意：无 sourceText 字段，由 PageRenderContext.content 提供
)
```

### 3.4 PageRenderContext（新增）

渲染器需要 `content` + `page` + `paint` + `metrics` 的统一来源。
不靠"外部顺手传一下"，显式定义上下文对象：

```kotlin
/**
 * 页面渲染上下文，封装渲染所需的全部依赖。
 *
 * 职责：确保 ReaderPageRenderer、选区绘制、TTS 高亮
 * 使用同一份 content 引用和同一套 paint/metrics，避免数据来源不一致。
 */
class PageRenderContext(
    /** 章节原始文本，所有 TextLine 的 [start, end) 均相对于此 */
    val content: CharSequence,
    /** 当前渲染的页面 */
    val page: TextPage,
    /** 文本画笔 */
    val textPaint: Paint,
    /** 字距（像素），分页与渲染必须一致 */
    val letterSpacingPx: Float,
    /** 可用内容宽度（减去左右 margin） */
    val availableWidth: Float,
)
```

使用方式：

```kotlin
// ReaderCanvasView / ReaderPageRenderer 统一通过 context 访问
fun render(ctx: PageRenderContext) {
    for (line in ctx.page.lines) {
        // 普通绘制
        canvas.drawText(ctx.content, line.start, line.end, x, y, ctx.textPaint)
        // 两端对齐
        drawTextJustified(line, x, y, ctx)
    }
}
```

---

## 4. Paginator 变更

### 4.1 calculateLine 合并遍历

**之前（3 遍）：**

```kotlin
// 第 1 遍：判定 charCount
for (i in 0 until lineEnd) {
    val width = widthWindow[startOffset + i]
    if (currentWidth + width + spacing > availableWidth) break
    currentWidth += width + spacing; charCount++
}

// 第 2 遍：构建 CharColumn（逐字符 toString + new CharColumn）
val charColumns = ArrayList<CharColumn>(charCount)
for (i in 0 until charCount) {
    charColumns.add(CharColumn(content[startOffset + i].toString(), widthWindow[startOffset + i]))
}

// 第 3 遍：重新累计 measuredWidth
for (i in 0 until charCount) w += widthWindow[startOffset + i]
```

**之后（1 遍 + 收尾）：**

```kotlin
// 唯一遍历：判定 charCount + 累计 measuredWidth
var currentWidth = 0f
var charCount = 0

for (i in 0 until lineEnd) {
    val width = widthWindow[startOffset + i]
    val spacing = if (charCount > 0) letterSpacingPx else 0f
    if (currentWidth + width + spacing > availableWidth) break
    currentWidth += width + spacing
    charCount++
}

val measuredWidth = currentWidth  // 已在循环中累计，无需额外遍历

// charWidths 在 charCount 确定后分配，避免 lineEnd != charCount 的浪费
val charWidths = if (needJustify && charCount > 0) {
    FloatArray(charCount).also { arr ->
        for (i in 0 until charCount) arr[i] = widthWindow[startOffset + i]
    }
} else null
```

**charWidths 分配优化**：先确定 `charCount`，再分配精确大小的 `FloatArray`。
`lineEnd` 可能是 120，`charCount` 只有 37，避免 3 倍内存浪费。

### 4.2 letterSpacing 一致性

**口径定义（贯穿分页 + 渲染）：**

```
measuredWidth = Σ(charWidth[i]) + letterSpacingPx × (charCount - 1)
                ─────────────────   ─────────────────────────────────
                字符原始宽度之和         基础字距（字符间）
```

- `measuredWidth` **包含**基础字距
- `measuredWidth` **不包含**两端对齐额外拉伸量
- `charWidths[i]` **不包含**字距（纯字符原始宽度）

**分页阶段**（§4.1）：`currentWidth` 在循环中累加 `width + spacing`，自然包含字距。✓

**渲染阶段**：

```kotlin
val extraSpace = availableWidth - line.measuredWidth   // measuredWidth 含字距
val justifySpacing = if (charCount > 1) extraSpace / (charCount - 1) else 0f
var currentX = x

for (i in 0 until charCount) {
    drawText(content, line.start + i, line.start + i + 1, currentX, y, textPaint)
    if (i < charCount - 1) {
        // charWidths[i]（纯宽度）+ letterSpacingPx（基础字距）+ justifySpacing（对齐拉伸）
        currentX += line.charWidths[i] + letterSpacingPx + justifySpacing
    }
}
```

验证：`Σ(charWidths[i] + letterSpacingPx + justifySpacing)` + `charWidths[last]`
= `measuredWidth + extraSpace = availableWidth` ✓

### 4.3 LineResult 简化

```kotlin
private data class LineResult(
    val isParagraphEnd: Boolean,
    val consumedChars: Int,
    val charWidths: FloatArray?,
    val measuredWidth: Float,
)
```

`text: String` 字段删除。TextLine 也不再需要 text。

### 4.4 paginatePage 产出 TextLine

```kotlin
val line = TextLine(
    start = currentOffset,
    end = currentOffset + skippedSpaces + lineResult.consumedChars,
    measuredWidth = lineResult.measuredWidth,
    isParagraphEnd = lineResult.isParagraphEnd,
    startXOffset = if (isParagraphStart) indentWidth else 0f,
    baseline = currentY + lineHeight * 0.8f,
    top = currentY,
    bottom = currentY + lineHeight,
    charWidths = lineResult.charWidths,
)
```

---

## 5. ReaderPageRenderer 变更

### 5.1 渲染入口改为 PageRenderContext

```kotlin
// 之前：渲染器持有 textPaint，外部传 page + content 散落参数
fun render(canvas: Canvas, page: TextPage, content: CharSequence, ...)

// 之后：统一通过 PageRenderContext
fun render(canvas: Canvas, ctx: PageRenderContext) {
    for (line in ctx.page.lines) {
        canvas.drawText(ctx.content, line.start, line.end, x, y, ctx.textPaint)
    }
}
```

选区绘制、TTS 高亮同样从 `ctx.content` + `line.start/end` 获取文本，保证数据来源一致。

### 5.2 普通绘制

```kotlin
// 之前
drawText(line.text, startX, relativeBaseline, textPaint)

// 之后
drawText(ctx.content, line.start, line.end, startX, relativeBaseline, ctx.textPaint)
```

### 5.3 两端对齐绘制

```kotlin
private fun Canvas.drawTextJustified(line: TextLine, x: Float, y: Float, ctx: PageRenderContext) {
    val extraSpace = ctx.availableWidth - line.measuredWidth
    if (extraSpace <= 0f || line.charWidths == null) {
        drawText(ctx.content, line.start, line.end, x, y, ctx.textPaint)
        return
    }

    val charCount = line.end - line.start
    val justifySpacing = if (charCount > 1) extraSpace / (charCount - 1) else 0f
    var currentX = x

    for (i in 0 until charCount) {
        drawText(ctx.content, line.start + i, line.start + i + 1, currentX, y, ctx.textPaint)
        if (i < charCount - 1) {
            currentX += line.charWidths[i] + ctx.letterSpacingPx + justifySpacing
        }
    }
}
```

### 5.4 高亮绘制

```kotlin
// 之前：每行 measureText JNI 调用
val textWidth = textPaint.measureText(line.text)

// 之后：直接用预计算的 measuredWidth
val textWidth = line.measuredWidth
```

---

## 6. Unicode 处理策略

### 6.1 当前限制

`drawText(content, i, i+1, ...)` 按 UTF-16 code unit 遍历。对于 surrogate pair（emoji、罕用汉字）会拆分导致渲染异常。

**这不是正确的字符边界模型，只是阶段性取舍。** 当前版本优先保证中文阅读器核心场景（BMP 内字符）的正确性和性能。

### 6.2 影响范围

| 场景 | 风险 |
|------|------|
| 纯中文 TXT | 无风险（BMP 内字符，无 surrogate） |
| 含 emoji 的 TXT/EPUB | 有风险（如 😊 是 surrogate pair） |
| 少数民族文字 / 组合字符 | 有风险 |

### 6.3 后续升级路径

后续版本可升级为 `Character.codePointAt()` 遍历模式：

```kotlin
// 未来升级方案（暂不实现）
var i = line.start
while (i < line.end) {
    val codePoint = Character.codePointAt(content, i)
    val cpCharCount = Character.charCount(codePoint)
    drawText(content, i, i + cpCharCount, currentX, y, textPaint)
    // ...
    i += cpCharCount
}
```

---

## 7. 收益总结

| 指标 | 优化前 | 优化后 |
|------|--------|--------|
| 逐字符 String 分配 | ~5000 | 0 |
| CharColumn 对象 | ~5000 | 0（已删除） |
| substring 分配 | ~120 | 0（区间模型） |
| widthWindow 遍历 | 3 遍/行 | 1 遍/行 |
| charWidths 内存 | — | 仅两端对齐行，精确 charCount 大小 |
| 每行额外内存 | ~200 字节 | 0 或 ~160 字节 |

**总计：消除 ~10000+ 堆对象分配/章节，零字符级堆对象，保留必要的 primitive 缓冲。**

---

## 8. 注意事项

1. **measuredWidth 口径**：`Σ(charWidth) + letterSpacing × (charCount-1)`，包含基础字距，不包含两端对齐拉伸。高亮、命中测试、分页回算均使用此口径。见 §4.2。

2. **charWidths 性质**：渲染辅助缓存，不是内容模型。从 widthWindow 复制而来，不参与持久化、序列化、equals/hashCode。见 §3.2。

3. **charWidths 精确分配**：`FloatArray(charCount)` 而非 `FloatArray(lineEnd)`，避免 2-3 倍内存浪费。见 §4.1。

4. **content 生命周期**：`TextChapter.content` 是唯一文本存储。TextPage/TextLine 通过偏移引用。TextChapter 存活期间 content 不会被 GC。

5. **charWidths 与 widthWindow 解耦**：charWidths 在分页时从 widthWindow 复制到独立 FloatArray，渲染时不依赖 widthWindow，无 LRU 淘汰风险。

6. **向下兼容**：删除 `CharColumn` 和 `TextLine.text` 是破坏性变更。所有消费方（ReaderPageRenderer、选区逻辑、TTS 高亮）需同步更新。

7. **UTF-16 限制**：当前按 UTF-16 code unit 遍历，不是正确的字符边界模型。中文 TXT/EPUB 核心场景安全，含 emoji 文本可能异常。见 §6。

---

## 9. 实施后验证

实施后应立即进行性能验证，确认热点是否转移：

1. **Memory Profiler**：确认章节分页时无 CharColumn/String 堆分配
2. **Perfetto**：确认分页耗时下降，识别新热点
3. **Macrobenchmark**：翻页帧率对比

删除 CharColumn 后，分页热点大概率转移到 `Paint.getTextWidths()` JNI 调用。此时 Kotlin 层已无明显优化空间，后续优化需在 Android Text Layout 原生层或缓存策略上着手。

---

## 10. 改动文件清单

| 文件 | 改动 |
|------|------|
| `model/TextModels.kt` | 删除 `CharColumn`，重构 `TextLine`（区间模型），`TextPage` 删除 sourceText |
| `PageRenderContext.kt` | 新建，封装 content + page + paint + letterSpacingPx + availableWidth |
| `Paginator.kt` | `calculateLine` 合并遍历 + 精确 charWidths 分配，`LineResult` 删除 text，产出区间式 TextLine |
| `ReaderPageRenderer.kt` | 渲染入口改为 `PageRenderContext`，`drawTextJustified` 改用 content + 索引绘制 |
| `ReaderCanvasView.kt` | 构造 `PageRenderContext`，适配 TextLine 新接口 |
