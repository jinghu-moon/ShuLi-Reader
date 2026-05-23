# 08 - Legado 阅读器架构分析

> 基于 `legado-with-MD3-main` 项目的深度分析，为书里阅读器提供实现参考。

## 架构概览

```
ReadView (阅读器主视图)
├── PageView (prevPage) ── 上一页
├── PageView (curPage)  ── 当前页
├── PageView (nextPage) ── 下一页
├── PageDelegate        ── 翻页动画控制器
└── TextPageFactory     ── 页面工厂

PageView (单页视图)
├── ContentTextView     ── 文本绘制视图
├── Header/Footer       ── 页眉页脚（时间、电量、进度）
└── TextPage            ── 页面数据模型

ChapterProvider (分页引擎)
├── TextChapter         ── 章节数据
├── TextPage            ── 页面数据
├── TextLine            ── 行数据
└── TextColumn          ── 字符列数据
```

## 核心数据模型

### TextPage（页面模型）

```kotlin
data class TextPage(
    var index: Int = 0,
    var text: String,                         // 页面文本
    var title: String,                        // 章节标题
    private val textLines: ArrayList<TextLine>, // 行列表
    var chapterSize: Int,                     // 章节总数
    var chapterIndex: Int,                    // 当前章节索引
    var height: Float,                        // 页面高度
    var leftLineSize: Int,                    // 双页模式左列行数
    var renderHeight: Int                     // 渲染高度
)
```

**职责**：
- 存储单页的文本内容和排版信息
- 管理行列表，提供行级访问接口
- 计算阅读进度百分比
- 处理文本绘制和缓存

### TextLine（行模型）

```kotlin
data class TextLine(
    var text: String,
    var lineTop: Float,           // 行顶部位置
    var lineBase: Float,          // 行基线位置
    var lineBottom: Float,        // 行底部位置
    var isTitle: Boolean,         // 是否标题行
    var isParagraphEnd: Boolean,  // 是否段落结束
    val columns: ArrayList<BaseColumn> // 字符列
)
```

**职责**：
- 存储单行文本的精确位置信息
- 管理字符列，支持字符级定位
- 处理行内绘制（文本、高亮、下划线）

### TextColumn（字符列模型）

```kotlin
// 基类
abstract class BaseColumn {
    abstract val start: Float
    abstract val end: Float
    abstract val charData: String
}

// 文本列
data class TextColumn(
    override val start: Float,    // 起始 x 坐标
    override val end: Float,      // 结束 x 坐标
    override val charData: String // 字符内容
) : BaseColumn()

// 图片列
data class ImageColumn(
    override val start: Float,
    override val end: Float,
    val src: String               // 图片路径
) : BaseColumn()
```

**职责**：
- 存储单个字符/元素的精确坐标
- 支持文本选择和点击定位
- 区分文本列和图片列

### TextChapter（章节模型）

```kotlin
data class TextChapter(
    val bookChapter: BookChapter,
    val index: Int,
    val title: String,
    val chapterSize: Int,
    val pages: ArrayList<TextPage>,
    var isCompleted: Boolean = false
)
```

**职责**：
- 存储章节的所有页面
- 管理章节加载状态
- 提供页面访问接口

---

## 分页引擎（ChapterProvider）

### 核心职责

将章节文本按照屏幕尺寸分割成多个页面，为每个字符计算精确坐标。

### 核心参数

```kotlin
object ChapterProvider {
    // 屏幕参数
    var viewWidth = 0
    var viewHeight = 0
    var paddingLeft = 0
    var paddingTop = 0
    var paddingRight = 0
    var paddingBottom = 0
    var visibleWidth = 0      // 可见宽度 = viewWidth - paddingLeft - paddingRight
    var visibleHeight = 0     // 可见高度 = viewHeight - paddingTop - paddingBottom

    // 文本参数
    var lineSpacingExtra = 0f     // 行间距
    var paragraphSpacing = 0      // 段落间距
    var indentCharWidth = 0f      // 缩进宽度
    var contentPaintTextHeight = 0f // 文本高度

    // 画笔
    var titlePaint: TextPaint     // 标题画笔
    var contentPaint: TextPaint   // 内容画笔
}
```

### 分页流程

```
┌─────────────────────────────────────────────────────────┐
│                    分页流程                              │
├─────────────────────────────────────────────────────────┤
│  1. 计算标题区域高度                                      │
│     └─ 标题行数 × 文本高度 + 标题上下间距                  │
│                                                         │
│  2. 初始化第一个 TextPage                                 │
│     └─ 设置页面起始位置                                   │
│                                                         │
│  3. 遍历每一段文本内容                                    │
│     ├─ 使用 StaticLayout 计算行数                        │
│     └─ 或使用 ZhLayout（中文排版优化）                    │
│                                                         │
│  4. 逐行处理                                            │
│     ├─ 计算行高: durY += textHeight * lineSpacingExtra  │
│     ├─ 判断是否换页: durY + textHeight > visibleHeight  │
│     │   ├─ 是 → 创建新 TextPage，重置 durY = 0          │
│     │   └─ 否 → 继续添加行                              │
│     └─ 为每个字符计算 (x, y) 坐标                        │
│                                                         │
│  5. 完成章节分页                                         │
│     └─ 设置页面索引、章节信息                             │
└─────────────────────────────────────────────────────────┘
```

### 核心分页方法

```kotlin
fun getTextChapterAsync(
    scope: CoroutineScope,
    book: Book,
    bookChapter: BookChapter,
    displayTitle: String,
    bookContent: BookContent,
    chapterSize: Int,
): TextChapter {
    val textChapter = TextChapter(bookChapter, ...)
    textChapter.createLayout(scope, book, bookContent)
    return textChapter
}

// 分页核心逻辑（简化版）
private fun setTypeText(
    x: Int,
    y: Float,
    text: String,
    textPages: ArrayList<TextPage>,
    textPaint: TextPaint,
    textHeight: Float,
): Pair<Int, Float> {
    var absStartX = x
    var durY = y

    // 使用 StaticLayout 计算行数
    val layout = StaticLayout(text, textPaint, visibleWidth, ...)

    for (lineIndex in 0 until layout.lineCount) {
        // 判断是否需要换页
        if (durY + textHeight > visibleHeight) {
            // 当前页面结束
            textPage.text = stringBuilder.toString()
            textPages.add(TextPage())  // 创建新页面
            durY = 0f
        }

        // 创建 TextLine
        val textLine = TextLine()
        textLine.text = layout.getLineText(lineIndex)

        // 计算字符坐标
        for (charIndex in textLine.text.indices) {
            val charWidth = measureCharWidth(char)
            textLine.addColumn(TextColumn(start = x, end = x + charWidth, char))
            x += charWidth
        }

        // 设置行位置
        textLine.upTopBottom(durY, textHeight, fontMetrics)
        textPage.addLine(textLine)
        durY += textHeight * lineSpacingExtra
    }

    return Pair(absStartX, durY)
}
```

### 字符坐标计算

```kotlin
// 为每个字符计算精确坐标
private fun addCharsToLine(
    absStartX: Int,
    textLine: TextLine,
    words: List<String>,
    widths: List<Float>,
    srcList: LinkedList<String>?
) {
    var x = absStartX.toFloat()
    for (i in words.indices) {
        val word = words[i]
        val width = widths[i]
        textLine.addColumn(TextColumn(start = x, end = x + width, word))
        x += width
    }
}
```

---

## 翻页动画系统

### PageDelegate 基类

```kotlin
abstract class PageDelegate(protected val readView: ReadView) {
    // 页面引用
    protected val nextPage: PageView get() = readView.nextPage
    protected val curPage: PageView get() = readView.curPage
    protected val prevPage: PageView get() = readView.prevPage

    // 屏幕尺寸
    protected var viewWidth: Int = readView.width
    protected var viewHeight: Int = readView.height

    // 动画控制器
    protected val scroller: Scroller = Scroller(context, LinearInterpolator())

    // 状态
    var isMoved = false          // 是否移动中
    var mDirection = PageDirection.NONE  // 翻页方向
    var isRunning = false        // 动画是否运行中

    // 核心方法
    abstract fun onTouch(event: MotionEvent)     // 触摸事件处理
    abstract fun onDraw(canvas: Canvas)          // 绘制动画帧
    abstract fun nextPageByAnim(speed: Int)      // 下一页动画
    abstract fun prevPageByAnim(speed: Int)      // 上一页动画
    abstract fun abortAnim()                     // 中断动画

    // 动画控制
    protected fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int, duration: Int) {
        scroller.startScroll(startX, startY, dx, dy, duration)
        isRunning = true
        readView.invalidate()
    }

    open fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            readView.setTouchPoint(scroller.currX.toFloat(), scroller.currY.toFloat())
        } else if (isStarted) {
            onAnimStop()
            stopScroll()
        }
    }
}
```

### 翻页动画实现类

| 类名 | 效果 | 实现方式 |
|------|------|----------|
| `HorizontalPageDelegate` | 水平平移 | 平移三个 PageView |
| `SimulationPageDelegate` | 仿真翻页 | 贝塞尔曲线 + 阴影渐变 |
| `ScrollPageDelegate` | 垂直滚动 | 连续滚动 ContentTextView |
| `CoverPageDelegate` | 覆盖翻页 | 上层页面覆盖下层 |
| `FadePageDelegate` | 淡入淡出 | 透明度渐变 |
| `SlidePageDelegate` | 滑动翻页 | 跟随手指滑动 |
| `NoAnimPageDelegate` | 无动画 | 直接切换 |

### 仿真翻页算法（SimulationPageDelegate）

```kotlin
class SimulationPageDelegate(readView: ReadView) : HorizontalPageDelegate(readView) {
    // 触摸点
    private var mTouchX = 0.1f
    private var mTouchY = 0.1f

    // 拖拽点对应的页脚
    private var mCornerX = 1
    private var mCornerY = 1

    // 贝塞尔曲线控制点
    private val mBezierStart1 = PointF()    // 起始点
    private val mBezierControl1 = PointF()  // 控制点
    private val mBezierVertex1 = PointF()   // 顶点
    private val mBezierEnd1 = PointF()      // 结束点

    // 第二条贝塞尔曲线
    private val mBezierStart2 = PointF()
    private val mBezierControl2 = PointF()
    private val mBezierVertex2 = PointF()
    private val mBezierEnd2 = PointF()

    // 阴影
    private var mBackShadowColors: IntArray   // 背面阴影
    private var mFrontShadowColors: IntArray  // 前面阴影

    // 计算贝塞尔曲线端点
    private fun calcBezierEndPoint(touchX: Float, touchY: Float) {
        // 根据触摸点计算翻页路径
        // mCornerX/mCornerY 决定翻页方向（左上/右上/左下/右下）
    }

    override fun onDraw(canvas: Canvas) {
        // 1. 绘制当前页（被翻起的部分）
        // 2. 绘制下一页（露出的部分）
        // 3. 绘制翻页背面
        // 4. 绘制阴影效果
    }
}
```

**贝塞尔曲线计算原理**：

```
        mBezierStart1
              \
               \    mBezierControl1
                \   /
                 \ /
                  X mBezierVertex1
                 / \
                /   \
               /     \
    mBezierEnd1       mBezierStart2
                            \
                             \    mBezierControl2
                              \   /
                               \ /
                                X mBezierVertex2
                               / \
                              /   \
                             /     \
                        mBezierEnd2
```

---

## 文本绘制系统

### ContentTextView

```kotlin
class ContentTextView(context: Context) : View(context) {
    var textPage: TextPage? = null
    var isMainView = false
    var selectAble = true  // 是否可选择

    // 文本选择状态
    var selectStart: TextPos? = null
    var selectEnd: TextPos? = null

    override fun onDraw(canvas: Canvas) {
        textPage?.draw(this, canvas, relativeOffset)
    }

    // 绘制页面
    fun drawPage(view: ContentTextView, canvas: Canvas) {
        for (line in lines) {
            canvas.withTranslation(0f, line.lineTop) {
                line.draw(view, this)
            }
        }
    }

    // 点击处理
    fun click(x: Float, y: Float): Boolean {
        // 根据坐标查找字符位置
        // 触发链接、图片等交互
    }

    // 长按处理
    fun longPress(x: Float, y: Float, select: (TextPos) -> Unit) {
        // 开始文本选择
    }
}
```

### TextLine 绘制

```kotlin
data class TextLine(...) {
    fun draw(view: ContentTextView, canvas: Canvas) {
        // 1. 绘制背景（高亮、搜索结果）
        drawBackground(canvas)

        // 2. 绘制文本
        for (column in columns) {
            when (column) {
                is TextColumn -> drawText(canvas, column)
                is ImageColumn -> drawImage(canvas, column)
            }
        }

        // 3. 绘制下划线（书签标记）
        if (isBookmark) {
            drawUnderline(canvas)
        }
    }

    private fun drawText(canvas: Canvas, column: TextColumn) {
        canvas.drawText(column.charData, column.start, lineBase, textPaint)
    }
}
```

---

## 页面工厂（TextPageFactory）

```kotlin
class TextPageFactory(private val readView: ReadView) : DataSource {
    // 页面方向
    enum class Direction {
        NEXT, PREV, CURRENT
    }

    // 获取指定方向的页面
    fun getPage(direction: Direction): TextPage {
        return when (direction) {
            Direction.NEXT -> nextPage
            Direction.PREV -> prevPage
            Direction.CURRENT -> curPage
        }
    }

    // 移动到下一页
    fun moveToNext(): Boolean {
        // 1. 检查是否有下一页
        // 2. 更新页面索引
        // 3. 预加载下一页
        return true
    }

    // 移动到上一页
    fun moveToPrev(): Boolean {
        // 1. 检查是否有上一页
        // 2. 更新页面索引
        // 3. 预加载上一页
        return true
    }
}
```

---

## 页面预加载机制

### 三层页面结构

```
┌─────────────────────────────────────────────────────────┐
│                    页面预加载                            │
├─────────────────────────────────────────────────────────┤
│                                                         │
│   prevPage          curPage           nextPage          │
│   ┌─────┐          ┌─────┐          ┌─────┐            │
│   │     │          │     │          │     │            │
│   │  -1 │          │  0  │          │  +1 │            │
│   │     │          │     │          │     │            │
│   └─────┘          └─────┘          └─────┘            │
│                                                         │
│   翻页时：                                              │
│   - 向前翻：prevPage → curPage, 加载新的 prevPage      │
│   - 向后翻：nextPage → curPage, 加载新的 nextPage      │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 预加载流程

```kotlin
fun upContent(relativePosition: Int = 0) {
    when (relativePosition) {
        -1 -> {
            // 向前翻页
            val temp = nextPage
            nextPage = curPage
            curPage = prevPage
            prevPage = temp
            // 预加载上一页
            loadPrevPage()
        }
        0 -> {
            // 当前页更新
        }
        1 -> {
            // 向后翻页
            val temp = prevPage
            prevPage = curPage
            curPage = nextPage
            nextPage = temp
            // 预加载下一页
            loadNextPage()
        }
    }
}
```

---

## 触摸事件处理

### ReadView 触摸事件流程

```kotlin
override fun onTouchEvent(event: MotionEvent): Boolean {
    when (event.action) {
        MotionEvent.ACTION_DOWN -> {
            // 1. 记录起始点
            setStartPoint(event.x, event.y)
            // 2. 启动长按检测
            postDelayed(longPressRunnable, longPressTimeout)
            // 3. 通知 PageDelegate
            pageDelegate?.onTouch(event)
        }

        MotionEvent.ACTION_MOVE -> {
            // 1. 判断是否移动
            val isMove = abs(startX - event.x) > slopSquare
            // 2. 如果移动，取消长按检测
            if (isMove) removeCallbacks(longPressRunnable)
            // 3. 通知 PageDelegate
            pageDelegate?.onTouch(event)
        }

        MotionEvent.ACTION_UP -> {
            // 1. 取消长按检测
            removeCallbacks(longPressRunnable)
            // 2. 判断是点击还是滑动
            if (!isMove && !longPressed) {
                onSingleTapUp()  // 点击处理
            } else {
                pageDelegate?.onTouch(event)  // 滑动处理
            }
        }
    }
    return true
}
```

### 点击区域划分

```
┌─────────────────────────────────────────────────────────┐
│                    点击区域划分                          │
├─────────────────────────────────────────────────────────┤
│                                                         │
│   ┌─────────┬─────────┬─────────┐                      │
│   │   TL    │   TC    │   TR    │  ← 上部 1/3          │
│   │  上一页 │  菜单   │  下一页 │                      │
│   ├─────────┼─────────┼─────────┤                      │
│   │   ML    │   MC    │   MR    │  ← 中部 1/3          │
│   │  上一页 │  隐藏   │  下一页 │                      │
│   ├─────────┼─────────┼─────────┤                      │
│   │   BL    │   BC    │   BR    │  ← 下部 1/3          │
│   │  上一页 │  进度   │  下一页 │                      │
│   └─────────┴─────────┴─────────┘                      │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## 关键设计模式

### 1. 虚拟化分页

```kotlin
// 只计算当前章节的页面，不预加载所有章节
fun loadChapter(index: Int) {
    scope.launch(Dispatchers.IO) {
        val chapter = ChapterProvider.getTextChapterAsync(...)
        withContext(Dispatchers.Main) {
            currentChapter = chapter
            updatePage()
        }
    }
}
```

### 2. LRU 缓存

```kotlin
// 缓存最近访问的章节
val chapterCache = LruCache<Int, TextChapter>(maxSize = 5)

fun getChapter(index: Int): TextChapter {
    return chapterCache.get(index) ?: run {
        val chapter = loadChapter(index)
        chapterCache.put(index, chapter)
        chapter
    }
}
```

### 3. Canvas 直接绘制

```kotlin
// 不使用 TextView，直接绘制文本
fun drawText(canvas: Canvas, text: String, x: Float, y: Float) {
    canvas.drawText(text, x, y, contentPaint)
}
```

### 4. 异步分页

```kotlin
// 使用协程在 IO 线程分页
fun paginateAsync(text: String) = scope.launch(Dispatchers.IO) {
    val pages = ChapterProvider.paginate(text)
    withContext(Dispatchers.Main) {
        updatePages(pages)
    }
}
```

---

## 性能优化策略

### 1. 避免频繁 GC

```kotlin
// 复用对象
private val textLinePool = ObjectPool<TextLine>(maxSize = 100)

fun obtainTextLine(): TextLine {
    return textLinePool.obtain() ?: TextLine()
}

fun recycleTextLine(line: TextLine) {
    line.reset()
    textLinePool.recycle(line)
}
```

### 2. 减少绘制调用

```kotlin
// 使用 CanvasRecorder 缓存绘制结果
var canvasRecorder = CanvasRecorderFactory.create(true)

fun render(view: ContentTextView): Boolean {
    return canvasRecorder.recordIfNeeded(view.width, renderHeight) {
        drawPage(view, this)
    }
}
```

### 3. 增量更新

```kotlin
// 只更新变化的行
fun updateLine(index: Int, newLine: TextLine) {
    textLines[index] = newLine
    invalidate()  // 只重绘当前页
}
```

---

## 对书里阅读器的借鉴建议

### 1. 保持单模块结构

Legado 虽然设计上是多模块，但阅读器核心代码集中在 `ui/book/read/page/` 包下，内聚性很高。建议书里保持单模块，减少复杂度。

### 2. Canvas 直接绘制

比 Compose Canvas 性能更好，适合长文本渲染。建议核心阅读视图使用传统 View + Canvas。

### 3. 三层页面预加载

避免翻页时的白屏闪烁，提供流畅的翻页体验。

### 4. 字符级坐标计算

为文本选择、书签定位提供基础，是高级功能的前提。

### 5. PageDelegate 模式

将翻页动画与页面内容解耦，便于扩展新动画，符合开闭原则。

### 6. 异步分页

使用协程在 IO 线程分页，避免阻塞主线程，保证 UI 流畅。

---

## 文档索引更新

| 序号 | 文档 | 内容 |
|------|------|------|
| 00 | 项目概述 | 项目基本信息 |
| 01 | 需求分析 | 功能需求与优先级 |
| 02 | 技术架构 | 整体架构设计 |
| 03 | 组件选型 | 第三方库选型 |
| 04 | UI设计系统 | 视觉规范与组件库 |
| 05 | 核心模块设计 | 关键模块详细设计 |
| 06 | 性能优化策略 | 性能优化方案 |
| 07 | 项目结构 | 代码目录结构 |
| **08** | **Legado 阅读器分析** | **参考项目架构分析** |
