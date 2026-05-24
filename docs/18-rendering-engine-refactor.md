# 18 · 渲染引擎重构 · 取经 Legado 的高性能渲染与跳转

> 文档版本：v1.0  
> 编制时间：2026-05-24  
> 协议声明：本项目与参考项目 [legado](https://github.com/gedoor/legado) 同为 **GPL-3.0**，本文档涉及的代码移植与改写在协议范围内进行。  
> 关联文档：
> - `@d:/100_Projects/110_Daily/ShuLi-Reader/docs/reader-architecture-notes.md`（架构基线）
> - `@d:/100_Projects/110_Daily/ShuLi-Reader/docs/17-reader-settings-redesign-tasks.md`（设置面板重构）
> - `@d:/100_Projects/110_Daily/ShuLi-Reader/docs/08-legado-reader-analysis.md`（早期 Legado 分析）

---

## 0. 总览

### 0.1 背景

当前 `ReaderCanvasView` 采用 `Bitmap.ARGB_8888` 三槽缓存（CURRENT/NEXT/PREV）。在 1080×2400 设备上每页约 10 MB，三页共 30 MB 内存占用；选区/页眉/电量任一变化都触发整页重画；分页同步阻塞主线程。为了实现"高性能、顺序阅读、快速跳转、不抖动"的目标，本次重构以 **Legado** 的渲染管线为蓝本进行改造。

### 0.2 核心改造点

| # | 改造 | 收益 | ROI |
|---|---|---|---|
| **B-1** | `Bitmap` 缓存 → `CanvasRecorder`（RenderNode/Picture） | 内存 -99%，重绘 < 1ms | ★★★ |
| **B-2** | 抽象 `TextPageFactory` 统一翻页/跳转 | 跨章透明、跳转 O(1) | ★★★ |
| **B-3** | 后台 `renderThread` 预渲染 | 主线程无卡顿 | ★★ |
| **B-4** | 流式分页 + `TextChapter.isCompleted` | 首页秒开 | ★★ |
| **B-5** | per-Line `CanvasRecorder` | 选区动画 60 fps 稳定 | ★ |

> 三子 View 架构（`prev/cur/next` 三个真实 View）属于"底层重写"，本次**不做**，保留单 `ReaderCanvasView` + 多页 `CanvasRecorder` 的融合方案。

### 0.3 最终架构示意

```
ReaderViewModel
├─ uiState: StateFlow<ReaderUiState>
│   ├─ pageIndex, currentChapter, currentPage
│   └─ pageRenderMode: SEQUENTIAL | JUMP | SCRUBBING
├─ pageFactory: TextPageFactory       ← 新增（B-2）
└─ chapterCache: TextChapter（含 isCompleted + 流式分页）  ← B-4

ReaderCanvasView
├─ pageFactory：通过 ViewModel 暴露
├─ renderThread (Executor)            ← 新增（B-3）
├─ submitRenderTask()                 ← 后台触发
└─ onDraw → 各 TextPage.canvasRecorder.draw(canvas)

TextPage
├─ canvasRecorder: CanvasRecorder     ← 新增（B-1）
├─ lines: List<TextLine>              ← B-5 内 line 各自 recorder
├─ render(view): Boolean              ← record 命令
└─ invalidate() / invalidateAll()

PageDelegate
└─ onDraw 接收 (currentRecorder, targetRecorder) 替代 (currentBitmap, targetBitmap)
```

### 0.4 阶段划分

| 阶段 | 内容 | 工时 | 风险 |
|---|---|---|---|
| **阶段 0** | 移植 `core/canvasrecorder/*` 工具类（8 文件） | 0.5 天 | 低 |
| **阶段 1** | `TextPage` 接入 `CanvasRecorder`，删除 `Bitmap` 三槽 | 1 天 | 中 |
| **阶段 2** | `PageDelegate.onDraw` 适配新接口 | 1 天 | 中 |
| **阶段 3** | 后台 `renderThread` 预渲染 | 0.5 天 | 中 |
| **阶段 4** | `TextPageFactory` + `DataSource` 抽象 | 1 天 | 中 |
| **阶段 5** | 进度条 Slider + `pageRenderMode` 枚举 | 0.5 天 | 低 |
| **阶段 6** | 流式分页（`TextChapter.isCompleted`） | 1.5 天 | 高 |
| **阶段 7** | per-Line `CanvasRecorder`（可选） | 1 天 | 中 |
| **阶段 8** | 测试与基准 | 1 天 | — |

合计 **约 7 天**，每阶段独立提交、可单独回滚。

---

## 1. 阶段 0 · 移植 CanvasRecorder 工具类

### 1.1 目标

把 Legado 的 `io.legado.app.utils.canvasrecorder` 整套搬进 ShuLi。这是**纯工具类**（无业务依赖），可直接拷贝改包名。

### 1.2 涉及文件

| Legado 源路径 | 目标路径（ShuLi） |
|---|---|
| `utils/canvasrecorder/CanvasRecorder.kt` | `core/canvasrecorder/CanvasRecorder.kt` |
| `utils/canvasrecorder/BaseCanvasRecorder.kt` | `core/canvasrecorder/BaseCanvasRecorder.kt` |
| `utils/canvasrecorder/CanvasRecorderImpl.kt` | `core/canvasrecorder/CanvasRecorderImpl.kt` |
| `utils/canvasrecorder/CanvasRecorderApi23Impl.kt` | `core/canvasrecorder/CanvasRecorderApi23Impl.kt` |
| `utils/canvasrecorder/CanvasRecorderApi29Impl.kt` | `core/canvasrecorder/CanvasRecorderApi29Impl.kt` |
| `utils/canvasrecorder/CanvasRecorderLocked.kt` | `core/canvasrecorder/CanvasRecorderLocked.kt` |
| `utils/canvasrecorder/CanvasRecorderFactory.kt` | `core/canvasrecorder/CanvasRecorderFactory.kt` |
| `utils/canvasrecorder/CanvasRecorderExtensions.kt` | `core/canvasrecorder/CanvasRecorderExtensions.kt` |
| `utils/canvasrecorder/pools/RenderNodePool.kt` | `core/canvasrecorder/pools/RenderNodePool.kt` |
| `utils/canvasrecorder/pools/PicturePool.kt` | `core/canvasrecorder/pools/PicturePool.kt` |
| `utils/canvasrecorder/pools/CanvasPool.kt` | `core/canvasrecorder/pools/CanvasPool.kt` |
| `utils/objectpool/synchronized.kt`（被池子用到） | `core/canvasrecorder/internal/Synchronized.kt` |

合计 **12 个文件**。

### 1.3 改写要点

1. 包名替换：`io.legado.app.utils` → `com.shuli.reader.core`
2. 删除 `import io.legado.app.help.config.AppConfig` 依赖：原代码用 `AppConfig.optimizeRender` 控制开关，改为读 ShuLi 自身的开关。建议放在 `BuildConfig.OPTIMIZE_RENDER` 或新增 `ReaderPreferences.optimizeRender = true`（默认开）。
3. `Logger`：Legado 用自家日志，改 ShuLi 现有日志工具或 `android.util.Log`。
4. **保留 GPL-3.0 头注释**：每个移植文件顶部加：

```kotlin
/*
 * Adapted from legado (https://github.com/gedoor/legado)
 * Copyright (C) gedoor, licensed under GPL-3.0.
 *
 * This file is part of ShuLi-Reader, also licensed under GPL-3.0.
 */
```

### 1.4 新增配置项

```kotlin
// core/data/ReaderPreferences.kt
data class ReaderPreferences(
    ...
    val optimizeRender: Boolean = true,   // 渲染优化总开关，调试时可关
)
```

### 1.5 验收

- [ ] 12 个文件编译通过，无 Lint 错
- [ ] 单测：`CanvasRecorderImpl` / `CanvasRecorderApi29Impl` 各一个 record→draw 闭环测试
- [ ] 在临时 demo Activity 中测试：用 RenderNode 在 `View.onDraw` 中绘制 1000 个 `drawText`，对比直接绘制的耗时（应有数量级差异）

---

## 2. 阶段 1 · TextPage 接入 CanvasRecorder

### 2.1 目标

替换 `ReaderCanvasView` 内的 `currentBitmap/nextBitmap/prevBitmap` 三槽方案，改为每个 `TextPage` 自带 `CanvasRecorder`。

### 2.2 数据模型变更

**`core/reader/model/TextPage.kt`**：

```kotlin
data class TextPage(
    val pageIndex: Int,
    val chapterIndex: Int,
    val lines: List<TextLine>,
    val startCharOffset: Int,
    val endCharOffset: Int,
    val chapterContentLength: Int,
    val marginHorizontal: Float,
    ...
) {
    /** 渲染缓存，每页一份，跨章节共享池资源。Transient：序列化时不参与。 */
    @Transient
    val canvasRecorder: CanvasRecorder = CanvasRecorderFactory.create(locked = true)

    /** 标记 recorder 失效，下次绘制时会重录。 */
    fun invalidate() = canvasRecorder.invalidate()

    /** 释放 recorder 对应的 RenderNode/Picture 回池。 */
    fun recycleRecorders() = canvasRecorder.recycle()
}
```

**注意**：
- `TextPage` 当前是 `data class`，加非 `val` 字段会破坏 `equals/hashCode/copy` 语义。建议把 `data class` 改成 `class`，或把 `canvasRecorder` 包成单独的 holder 通过 `WeakHashMap<TextPage, CanvasRecorder>` 关联。
- 推荐前者（直接改类）：`equals` 用 `===` 比较即可，`copy` 不再需要（pages 在分页阶段一次性生成，不会被 copy）。

### 2.3 渲染入口重写

**`core/reader/ReaderCanvasView.kt`** 大改：

```kotlin
class ReaderCanvasView(...) : View(...) {

    // 删除以下字段
    // private var currentBitmap: Bitmap?      ← 删
    // private var nextBitmap: Bitmap?         ← 删
    // private var prevBitmap: Bitmap?         ← 删

    // 删除以下方法
    // private fun preRenderAllBitmaps()       ← 删
    // private fun updateCurrentBitmapHeaderFooter()  ← 删
    // private fun releaseBitmaps()            ← 删

    // 保留页面引用
    private var currentPage: TextPage? = null
    private var nextPage: TextPage? = null
    private var prevPage: TextPage? = null

    // 新增：渲染参数（为了让 record 函数能拿到一切上下文）
    private val renderContext = RenderContext()

    private inner class RenderContext {
        var headerText: String = ""
        var footerText: String = ""
        var showProgress: Boolean = true
        var batteryLevel: Int = 100
        var ttsActiveRange: SelectionRange? = null
        var selectedRange: SelectionRange? = null
    }

    /** 录制单页：触发 recorder.record */
    private fun recordPage(page: TextPage) {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        page.canvasRecorder.recordIfNeeded(w, h) {
            pageRenderer.render(
                canvas = this,
                page = page,
                headerText = renderContext.headerText,
                footerText = renderContext.footerText,
                showProgress = renderContext.showProgress,
                batteryLevel = renderContext.batteryLevel,
                ttsActiveRange = renderContext.ttsActiveRange,
                selectedRange = renderContext.selectedRange,
                ttsHighlightPaint = ttsHighlightPaint,
                selectionPaint = selectionPaint,
                backgroundPaint = backgroundPaint,
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = currentPage ?: return
        recordPage(current)              // 命中缓存则直接 draw，不会重录

        val delegate = pageDelegate
        if (delegate != null) {
            val isPrevDirection = when (delegate.state) {
                PageDelegate.State.DRAGGING -> delegate.isDraggingBackward()
                PageDelegate.State.ANIMATING -> delegate.direction == PageDelegate.Direction.PREV
                else -> false
            }
            val target = if (isPrevDirection) prevPage else nextPage
            target?.let { recordPage(it) }
            // 委托接收 CanvasRecorder 而不是 Bitmap
            delegate.onDraw(canvas, current.canvasRecorder, target?.canvasRecorder ?: current.canvasRecorder)
        } else {
            current.canvasRecorder.draw(canvas)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        currentPage?.recycleRecorders()
        nextPage?.recycleRecorders()
        prevPage?.recycleRecorders()
    }
}
```

**关键点**：
- 删除 `Bitmap` 字段后，`onSizeChanged` 不再需要 `releaseBitmaps + post preRenderAllBitmaps`，只要 `currentPage?.invalidate()` 让下次 onDraw 重录即可。
- `setHeaderText / setFooterText / setBatteryLevel / setTtsActiveRange / clearSelection` 等改为：
  ```kotlin
  fun setHeaderText(text: String) {
      if (renderContext.headerText == text) return
      renderContext.headerText = text
      currentPage?.invalidate()
      invalidate()
  }
  ```
  > **注意**：暂时只 invalidate `currentPage`，因为 next/prev 的 header/footer 也会用同一文本（除非有跨章场景）。这部分语义需 review。

### 2.4 PageDelegate 接口签名变更

**`core/reader/animation/PageDelegate.kt`** 接口：

```kotlin
interface PageDelegate {
    enum class State { IDLE, DRAGGING, ANIMATING }
    enum class Direction { NONE, NEXT, PREV }

    val state: State
    val direction: Direction

    fun setCallback(callback: Callback?)
    fun setViewSize(width: Int, height: Int)
    fun startNext()
    fun startPrev()
    fun onTouch(event: MotionEvent): Boolean
    fun abort()
    fun isDraggingBackward(): Boolean

    /**
     * 绘制翻页动画。
     * @param current 当前页 recorder（必须已 record 过）
     * @param target 目标页 recorder（next 或 prev，按 direction 决定）
     */
    fun onDraw(canvas: Canvas, current: CanvasRecorder, target: CanvasRecorder)

    interface Callback {
        fun onPageChanged(direction: Direction)
        fun invalidate()
    }
}
```

### 2.5 删除文件

- `core/reader/PageBuffer.kt` — 死代码（参数签名与 `ReaderPageRenderer.render` 已不兼容）。

### 2.6 验收

- [ ] 内存峰值 ≤ 5 MB（当前 30 MB）
- [ ] 进入阅读 / 翻页 / 章节切换无白屏闪烁
- [ ] 选区切换不再"瞬白" → 黑（recorder 命中缓存）
- [ ] `pageRenderer.render` 调用次数（埋点统计）：选区切换从 N 次 → 1 次

---

## 3. 阶段 2 · PageDelegate 适配 CanvasRecorder

### 3.1 5 个 PageDelegate 改造

| 委托 | 改造点 |
|---|---|
| `HorizontalPageDelegate` | `Bitmap` → `CanvasRecorder.draw(canvas)`，平移由 `Canvas.translate` 实现 |
| `CoverPageDelegate` | 同上 + 阴影绘制保留（`drawRect + LinearGradient`） |
| `SimulationPageDelegate` | **复杂**：原代码用 `BitmapShader` 给翻起的页面贴图。RenderNode 不能直接做 BitmapShader。**降级方案**：`SimulationPageDelegate` 内部只在录制阶段调用一次 `canvasRecorder.draw` 到一张临时 Bitmap，再用 BitmapShader。或者放弃仿真模式的"贴图"，改用纯曲线 + 阴影模拟（视觉略简但仍美观） |
| `ScrollPageDelegate` | 滚动只用 current，简单 `canvas.translate(0, scrollOffset); current.draw(canvas)` |
| `NoAnimPageDelegate` | 直接 `current.draw(canvas)` |
| `FadePageDelegate`（如有） | `Canvas.saveLayerAlpha + draw` 完成淡入淡出 |

### 3.2 SimulationPageDelegate 处理建议

仿真翻页是 ShuLi 的主打动画，必须保留视觉。两个方案：

**方案 A（保留 Bitmap，但延迟创建）**：
```kotlin
override fun onDraw(canvas: Canvas, current: CanvasRecorder, target: CanvasRecorder) {
    // 仿真模式动画期间需要 BitmapShader：将 recorder 渲染到临时 Bitmap
    if (currentBitmap == null || currentBitmap.width != width || currentBitmap.height != height) {
        currentBitmap?.recycle()
        targetBitmap?.recycle()
        currentBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        targetBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }
    current.draw(Canvas(currentBitmap!!))   // 仅动画期间转换一次
    target.draw(Canvas(targetBitmap!!))
    drawSimulationCurl(canvas, currentBitmap!!, targetBitmap!!)
}

override fun abort() {
    super.abort()
    currentBitmap?.recycle(); currentBitmap = null
    targetBitmap?.recycle(); targetBitmap = null
}
```

仿真模式仍占 20 MB（动画期间），但**仅动画进行中**，结束立即释放。

**方案 B（纯曲线模拟）**：抛弃 BitmapShader，仅画曲线 + 阴影。视觉略简，但全程零 Bitmap。建议先实现 A，UX 验收后再决定是否切 B。

### 3.3 验收

- [ ] 5 种动画切换均流畅
- [ ] 仿真模式动画期间内存峰值 ≤ 25 MB（含临时 Bitmap），结束后回落
- [ ] 滚动模式无掉帧

---

## 4. 阶段 3 · 后台 renderThread 预渲染

### 4.1 目标

把 record 操作搬到后台线程，主线程仅 `draw(canvas)`。

### 4.2 实现

**`core/reader/ReaderCanvasView.kt`**：

```kotlin
companion object {
    private val renderThread by lazy {
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "ShuLi-PageRender").apply { priority = Thread.NORM_PRIORITY - 1 }
        }
    }
}

private val renderRunnable = Runnable {
    val w = width
    val h = height
    if (w <= 0 || h <= 0) return@Runnable
    val cur = currentPage
    val nxt = nextPage
    val prv = prevPage
    var dirty = false
    cur?.let { if (recordPageOffMain(it, w, h)) dirty = true }
    nxt?.let { if (recordPageOffMain(it, w, h)) dirty = true }
    prv?.let { if (recordPageOffMain(it, w, h)) dirty = true }
    if (dirty) postInvalidate()
}

/** 后台线程录制页面，返回是否实际产生录制。CanvasRecorderLocked 内部自带锁。 */
private fun recordPageOffMain(page: TextPage, w: Int, h: Int): Boolean {
    return page.canvasRecorder.recordIfNeeded(w, h) {
        pageRenderer.render(this, page, ... /* 同 recordPage */)
    }
}

fun submitRenderTask() {
    renderThread.submit(renderRunnable)
}

// 在合适时机调用 submitRenderTask：
// - setPage(...)：数据变更后
// - setTextSizePx / setFontFamily / setTheme：样式变更后
// - 旋转/onSizeChanged：尺寸变更后
```

### 4.3 主线程兜底

```kotlin
override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val current = currentPage ?: return
    // 兜底：若后台尚未录制完成（首帧），主线程同步录制
    if (current.canvasRecorder.needRecord()) {
        recordPage(current)
    }
    current.canvasRecorder.draw(canvas)
    // 动画情况同 2.3，但 target 也走兜底
}
```

### 4.4 线程安全

`CanvasRecorderFactory.create(locked = true)` 已用 `CanvasRecorderLocked` 包装：内部 `synchronized` 保护 `beginRecording / endRecording / draw` 互斥。**无需额外锁**。

### 4.5 验收

- [ ] 切章 / 改字号 / 旋转屏幕主线程**不卡顿**（用 Systrace / Profile 确认）
- [ ] 首帧偶发同步录制不超过 16 ms（可接受）
- [ ] 无并发 crash（`record` 与 `draw` 互斥）

---

## 5. 阶段 4 · TextPageFactory + DataSource 抽象

### 5.1 目标

把翻页/跳转逻辑从 `ReaderViewModel` 与 `ReaderCanvasView` 中抽出，集中到 `TextPageFactory`。

### 5.2 接口定义

**`core/reader/PageFactory.kt`**：

```kotlin
interface DataSource {
    val pageIndex: Int
    val currentChapter: TextChapter?
    val nextChapter: TextChapter?
    val prevChapter: TextChapter?
    val isScroll: Boolean
    fun hasNextChapter(): Boolean
    fun hasPrevChapter(): Boolean
    /** 通知 View 层更新内容，relativePosition: -1=prev, 0=cur, 1=next */
    fun upContent(relativePosition: Int = 0, resetPageOffset: Boolean = true)
}

abstract class PageFactory<T>(protected val dataSource: DataSource) {
    abstract fun hasPrev(): Boolean
    abstract fun hasNext(): Boolean
    abstract fun moveToPrev(upContent: Boolean): Boolean
    abstract fun moveToNext(upContent: Boolean): Boolean
    abstract fun moveToFirst()
    abstract fun moveToLast()
    abstract val curPage: T
    abstract val nextPage: T
    abstract val prevPage: T
}

class TextPageFactory(dataSource: DataSource) : PageFactory<TextPage>(dataSource) {
    // 完全照搬 Legado 实现，仅替换 ReadBook 调用为 ShuLi 等价物
}
```

### 5.3 ReaderViewModel 实现 DataSource

```kotlin
class ReaderViewModel(...) : ViewModel(), DataSource {
    override val pageIndex: Int get() = _uiState.value.pageIndex
    override val currentChapter: TextChapter? get() = _uiState.value.currentChapter
    override val nextChapter: TextChapter? get() = chapterCache[uiState.value.chapterIndex + 1]
    override val prevChapter: TextChapter? get() = chapterCache[uiState.value.chapterIndex - 1]
    override val isScroll: Boolean get() = _uiState.value.readerPreferences.pageAnimType == PageAnimType.SCROLL
    override fun hasNextChapter() = uiState.value.chapterIndex < uiState.value.totalChapters - 1
    override fun hasPrevChapter() = uiState.value.chapterIndex > 0
    override fun upContent(relativePosition: Int, resetPageOffset: Boolean) { /* 触发 UI 重组 */ }

    val pageFactory = TextPageFactory(this)

    fun nextPage() {
        if (pageFactory.moveToNext(upContent = true)) {
            saveReadingProgress(immediate = false)
        }
    }

    fun prevPage() {
        if (pageFactory.moveToPrev(upContent = true)) {
            saveReadingProgress(immediate = false)
        }
    }
}
```

### 5.4 跳转统一入口

```kotlin
fun jumpToPage(pageIndex: Int) {
    val chapter = currentChapter ?: return
    val safe = pageIndex.coerceIn(0, chapter.pages.lastIndex)
    if (safe == this.pageIndex) return
    _uiState.update {
        it.copy(
            pageIndex = safe,
            currentPage = chapter.pages[safe],
            pageRenderMode = PageRenderMode.JUMP,
            selectedRange = null,
            ttsActiveRange = null,
        )
    }
    saveReadingProgress(immediate = true)
    upContent(relativePosition = 0, resetPageOffset = true)
    // 一帧后回到 SEQUENTIAL，让 View 自然预热邻页
    viewModelScope.launch {
        delay(16)
        _uiState.update { it.copy(pageRenderMode = PageRenderMode.SEQUENTIAL) }
    }
}

fun jumpToChapterPosition(chapterIndex: Int, charOffset: Int) {
    if (currentChapter?.chapterIndex == chapterIndex) {
        val pi = currentChapter!!.getPageIndexByCharIndex(charOffset)
        jumpToPage(pi)
    } else {
        openChapter(chapterIndex, targetCharOffset = charOffset)
    }
}
```

### 5.5 替换调用方

| 现有调用 | 改为 |
|---|---|
| `viewModel.nextPage()` 直接改 pageIndex | `pageFactory.moveToNext(true)` |
| `goToBookmark` 中 `navigateToChapterPosition(...)` | `jumpToChapterPosition(...)` |
| `goToNote` 同上 | 同上 |
| `navigateToSearchResult` 同上 | 同上 |

### 5.6 验收

- [ ] 章末点击右侧 → 自动跨入下一章首页
- [ ] 章首点击左侧 → 自动跨入上一章末页
- [ ] 目录/书签/搜索/笔记跳转使用同一 `jumpToChapterPosition` 入口
- [ ] `pageFactory` 单测可独立运行（用 fake DataSource）

---

## 6. 阶段 5 · 进度条 Slider + pageRenderMode

### 6.1 数据模型

```kotlin
enum class PageRenderMode {
    SEQUENTIAL,   // 顺序阅读：渲染 prev/cur/next
    JUMP,         // 跳转：仅 cur，禁用动画
    SCRUBBING,    // 拖动进度条：仅 cur，节流，禁用动画
}

data class ReaderUiState(
    ...
    val pageRenderMode: PageRenderMode = PageRenderMode.SEQUENTIAL,
)
```

### 6.2 ViewModel scrub 接口（Channel 节流）

```kotlin
private val scrubChannel = Channel<Int>(Channel.CONFLATED)

init {
    viewModelScope.launch {
        scrubChannel.consumeAsFlow()
            .sample(80.milliseconds)
            .collect { pageIndex -> emitScrubFrame(pageIndex) }
    }
}

fun startPageScrub() {
    _uiState.update { it.copy(pageRenderMode = PageRenderMode.SCRUBBING) }
}

fun scrubToPage(pageIndex: Int) {
    val chapter = uiState.value.currentChapter ?: return
    val safe = pageIndex.coerceIn(0, chapter.pages.lastIndex)
    // 1. 立即更新 pageIndex（页脚数字跟手）
    _uiState.update { it.copy(pageIndex = safe) }
    // 2. 把"换页"扔进节流 channel
    scrubChannel.trySend(safe)
}

private fun emitScrubFrame(pageIndex: Int) {
    val chapter = uiState.value.currentChapter ?: return
    _uiState.update { it.copy(currentPage = chapter.pages.getOrNull(pageIndex)) }
}

fun commitPageScrub() {
    val state = uiState.value
    val pi = state.pageIndex
    val chapter = state.currentChapter ?: return
    _uiState.update {
        it.copy(
            currentPage = chapter.pages.getOrNull(pi),
            pageRenderMode = PageRenderMode.SEQUENTIAL,
        )
    }
    saveReadingProgress(immediate = true)
}
```

### 6.3 Compose UI

`ReaderScreen` 底部工具栏插入：

```kotlin
if (uiState.totalPages > 1) {
    var isScrubbing by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${uiState.pageIndex + 1}", Modifier.width(32.dp))
        Slider(
            value = uiState.pageIndex.toFloat(),
            onValueChange = { v ->
                val p = v.roundToInt()
                if (!isScrubbing) {
                    viewModel.startPageScrub()
                    isScrubbing = true
                }
                viewModel.scrubToPage(p)
            },
            onValueChangeFinished = {
                viewModel.commitPageScrub()
                isScrubbing = false
            },
            valueRange = 0f..(uiState.totalPages - 1).coerceAtLeast(1).toFloat(),
            modifier = Modifier.weight(1f),
        )
        Text("${uiState.totalPages}", Modifier.width(32.dp), textAlign = TextAlign.End)
    }
}
```

### 6.4 ReaderCanvasView 响应模式

```kotlin
fun setPage(page: TextPage, next: TextPage?, prev: TextPage?, mode: PageRenderMode) {
    currentPage = page
    when (mode) {
        PageRenderMode.SEQUENTIAL -> { nextPage = next; prevPage = prev }
        PageRenderMode.JUMP, PageRenderMode.SCRUBBING -> {
            nextPage = null; prevPage = null
            pageDelegate?.abort()    // 取消进行中的动画
        }
    }
    submitRenderTask()
    invalidate()
}
```

Compose `update` 块：

```kotlin
update = { view ->
    view.setPage(
        page = uiState.currentPage ?: return@AndroidView,
        next = uiState.currentChapter?.pages?.getOrNull(uiState.pageIndex + 1),
        prev = uiState.currentChapter?.pages?.getOrNull(uiState.pageIndex - 1),
        mode = uiState.pageRenderMode,
    )
}
```

### 6.5 验收

- [ ] 拖动 1000 页 Slider 不卡（80ms 节流，最多 ~12 帧）
- [ ] 页脚 "P / N" 数字实时跟手
- [ ] 松手后 80ms 内显示目标页内容
- [ ] 跳转后立即翻页：第一次可能慢 1 帧（NEXT 还在录制），后续正常

---

## 7. 阶段 6 · 流式分页

### 7.1 目标

把 `Paginator.paginateChapter` 的 suspend 全量函数改成 Flow 流式输出，章节首页先生成、其余后台继续。

### 7.2 数据模型

**`core/reader/model/TextChapter.kt`**：

```kotlin
class TextChapter(
    val chapterIndex: Int,
    val title: String,
    val totalLength: Int,
) {
    private val _pages = mutableListOf<TextPage>()
    val pages: List<TextPage> get() = _pages

    @Volatile var isCompleted = false
        private set

    var layoutListener: LayoutListener? = null

    interface LayoutListener {
        fun onPageReady(index: Int, page: TextPage)
        fun onLayoutCompleted()
    }

    fun addPage(page: TextPage) {
        synchronized(_pages) { _pages.add(page) }
        layoutListener?.onPageReady(page.pageIndex, page)
    }

    fun markCompleted() {
        isCompleted = true
        layoutListener?.onLayoutCompleted()
    }

    fun getPage(index: Int): TextPage? = synchronized(_pages) { _pages.getOrNull(index) }

    fun getPageIndexByCharIndex(charIndex: Int): Int {
        val list = synchronized(_pages) { _pages.toList() }
        return list.indexOfFirst { charIndex < it.endCharOffset }.coerceAtLeast(0)
    }

    val lastIndex: Int get() = synchronized(_pages) { _pages.lastIndex }
    val pageSize: Int get() = synchronized(_pages) { _pages.size }
}
```

### 7.3 Paginator 改造

```kotlin
class Paginator(...) {

    /**
     * 流式分页。每生成一页 emit 一次。
     * 调用方应在背景 Dispatcher 里 collect。
     */
    fun paginateStreaming(
        chapter: TextChapter,
        text: String,
        config: ReaderLayoutConfig,
    ): Flow<TextPage> = flow {
        var currentY = config.headerHeight
        var startCharOffset = 0
        var lines = mutableListOf<TextLine>()
        var pageIndex = 0
        for (paragraph in splitParagraphs(text)) {
            for (line in calculateLines(paragraph, currentY, ...)) {
                if (currentY + line.height > config.maxAvailableY) {
                    val page = buildPage(pageIndex++, startCharOffset, lines, ...)
                    chapter.addPage(page)   // 同步加入章节
                    emit(page)
                    lines = mutableListOf()
                    currentY = config.headerHeight
                    startCharOffset = line.startCharOffset
                }
                lines.add(line)
                currentY += line.height
            }
        }
        if (lines.isNotEmpty()) {
            val page = buildPage(pageIndex, startCharOffset, lines, ...)
            chapter.addPage(page)
            emit(page)
        }
    }
}
```

### 7.4 ViewModel 调度

```kotlin
private fun loadChapter(chapterIndex: Int, targetCharOffset: Int = 0) {
    val chapter = TextChapter(chapterIndex, title, content.length)
    chapter.layoutListener = object : TextChapter.LayoutListener {
        override fun onPageReady(index: Int, page: TextPage) {
            val current = uiState.value.currentChapter
            if (current?.chapterIndex != chapterIndex) return
            // 第一页就绪：立即显示
            if (index == 0 && uiState.value.currentPage == null) {
                _uiState.update { it.copy(currentPage = page, pageIndex = 0) }
            }
            // 目标 charOffset 落在此页：跳转
            if (page.startCharOffset <= targetCharOffset && targetCharOffset < page.endCharOffset) {
                _uiState.update { it.copy(currentPage = page, pageIndex = index) }
            }
        }
        override fun onLayoutCompleted() {
            _uiState.update { it.copy(totalPages = chapter.pageSize, isLoading = false) }
        }
    }
    _uiState.update { it.copy(currentChapter = chapter, isLoading = true) }

    viewModelScope.launch(Dispatchers.Default) {
        paginator.paginateStreaming(chapter, content, config).collect()
        chapter.markCompleted()
    }
}
```

### 7.5 PageFactory 适配 isCompleted

```kotlin
override fun hasNext(): Boolean = with(dataSource) {
    val ch = currentChapter ?: return false
    if (ch.isCompleted) {
        return pageIndex < ch.lastIndex || hasNextChapter()
    } else {
        // 未完成时，仅当当前 pageIndex < 已生成最后一页时才允许往后
        return pageIndex < ch.lastIndex
    }
}
```

### 7.6 验收

- [ ] 10 万字章节首页 < 100 ms 显示（当前 ~500 ms）
- [ ] 流式分页期间，正向翻页不卡顿
- [ ] 进度条 Slider 在分页未完成时只能拖到已生成的最后一页
- [ ] 分页完成后总页数自动更新

---

## 8. 阶段 7 · per-Line CanvasRecorder（可选）

### 8.1 目标

选区移动 / TTS 高亮变化时，仅重画受影响的行，而非整页。

### 8.2 数据模型

```kotlin
class TextLine(
    val text: String,
    val top: Float,
    val bottom: Float,
    ...
) {
    @Transient
    var canvasRecorder: CanvasRecorder? = null

    fun invalidateSelf() {
        canvasRecorder?.invalidate()
    }

    fun recycleRecorder() {
        canvasRecorder?.recycle()
        canvasRecorder = null
    }
}
```

### 8.3 ReaderPageRenderer 改造

```kotlin
fun render(canvas: Canvas, page: TextPage, ...) {
    drawBackground(canvas)
    drawHeader(canvas, ...)
    drawFooter(canvas, ...)
    for (line in page.lines) {
        val recorder = line.canvasRecorder
            ?: CanvasRecorderFactory.create().also { line.canvasRecorder = it }
        recorder.recordIfNeeded(canvas.width, (line.bottom - line.top).toInt()) {
            drawSingleLine(this, line, page, ...)
        }
        canvas.withTranslation(0f, line.top) {
            recorder.draw(this)
        }
    }
    drawProgress(canvas, ...)
}
```

### 8.4 选区/TTS 失效逻辑

```kotlin
// ViewModel 改 selectedRange 时
fun selectText(range: SelectionRange) {
    val oldRange = uiState.value.selectedRange
    _uiState.update { it.copy(selectedRange = range) }
    // 仅 invalidate 受影响行
    val page = uiState.value.currentPage ?: return
    page.lines.forEachIndexed { idx, line ->
        val affected = line.intersects(range) || (oldRange != null && line.intersects(oldRange))
        if (affected) line.invalidateSelf()
    }
    // 整页 recorder 也要 invalidate（因为 forEach 重录可能改变 line 顺序）
    page.invalidate()
}
```

### 8.5 验收

- [ ] 长按选区动画 60 fps（之前 ~30 fps）
- [ ] TTS 高亮跳行无明显卡顿

---

## 9. 阶段 8 · 测试与基准

### 9.1 单测

| 模块 | 测试点 |
|---|---|
| `CanvasRecorderImpl` | record 后 draw 输出正确 |
| `CanvasRecorderApi29Impl` | 使用 RenderNode 路径，draw 在硬件画布上无错 |
| `RenderNodePool` | obtain/recycle 闭环，引用计数正确 |
| `TextPageFactory` | 跨章 moveToNext/moveToPrev 边界正确（章首/章末/未完成章） |
| `TextChapter` | isCompleted 状态机；并发 addPage 安全 |
| `Paginator.paginateStreaming` | emit 顺序、startCharOffset/endCharOffset 单调 |

### 9.2 UI 测

参考 `@d:/100_Projects/110_Daily/ShuLi-Reader/docs/15-visual-regression-plan.md`，新增：

- 长章节流式加载首页 < 100ms（计时断言）
- 进度条拖动 1000 页帧率稳定
- 5 种翻页动画切换无 crash
- 旋转屏幕后 recorder 自动重录

### 9.3 基准（macrobenchmark）

参考 `@d:/100_Projects/110_Daily/ShuLi-Reader/docs/16-benchmark-execution-guide.md`：

| 指标 | 基线（重构前） | 目标（重构后） |
|---|---|---|
| 单页内存 | 10 MB | < 0.1 MB（display list） |
| 3 页总内存 | 30 MB | < 1 MB |
| 章节首页耗时 | 500 ms | < 100 ms |
| 翻页帧率（90 Hz 设备） | ~80 fps | 90 fps 稳定 |
| 选区切换主线程耗时 | ~12 ms | < 1 ms |
| Slider 1000 页拖动 | 卡顿明显 | < 12 次重录，跟手 |

---

## 10. 文件改动清单

### 新建（19 文件）

| 路径 | 用途 |
|---|---|
| `core/canvasrecorder/CanvasRecorder.kt` | 接口（移植） |
| `core/canvasrecorder/BaseCanvasRecorder.kt` | 基类（移植） |
| `core/canvasrecorder/CanvasRecorderImpl.kt` | 兜底实现（移植） |
| `core/canvasrecorder/CanvasRecorderApi23Impl.kt` | Picture 实现（移植） |
| `core/canvasrecorder/CanvasRecorderApi29Impl.kt` | RenderNode 实现（移植） |
| `core/canvasrecorder/CanvasRecorderLocked.kt` | 加锁包装（移植） |
| `core/canvasrecorder/CanvasRecorderFactory.kt` | 工厂（移植） |
| `core/canvasrecorder/CanvasRecorderExtensions.kt` | 扩展（移植） |
| `core/canvasrecorder/pools/RenderNodePool.kt` | RenderNode 对象池（移植） |
| `core/canvasrecorder/pools/PicturePool.kt` | Picture 对象池（移植） |
| `core/canvasrecorder/pools/CanvasPool.kt` | Canvas 对象池（移植） |
| `core/canvasrecorder/internal/Synchronized.kt` | 池子加锁工具（移植） |
| `core/reader/PageFactory.kt` | DataSource + PageFactory 抽象 |
| `core/reader/TextPageFactory.kt` | TextPage 工厂实现 |
| `core/reader/RenderContext.kt` | 渲染参数容器 |
| `feature/reader/component/PageScrubBar.kt` | 进度条 Slider 组件 |
| `core/reader/model/PageRenderMode.kt` | 渲染模式枚举 |
| 单测：`CanvasRecorderTest.kt` | 录制/回放闭环 |
| 单测：`TextPageFactoryTest.kt` | 跨章/边界 |

### 修改（核心 8 文件）

| 路径 | 改动概要 |
|---|---|
| `core/reader/model/TextPage.kt` | data class → class，新增 `canvasRecorder` 字段 |
| `core/reader/model/TextLine.kt` | 新增 `canvasRecorder?` 字段（阶段 7） |
| `core/reader/model/TextChapter.kt` | 新增 `isCompleted`、`LayoutListener`、并发安全 addPage |
| `core/reader/Paginator.kt` | 新增 `paginateStreaming` Flow API |
| `core/reader/ReaderCanvasView.kt` | 删除 Bitmap 字段；recorder 化；renderThread；模式响应 |
| `core/reader/ReaderPageRenderer.kt` | 拆分 drawSingleLine（阶段 7） |
| `core/reader/animation/PageDelegate.kt` | onDraw 签名改为接收 CanvasRecorder |
| `core/reader/animation/*PageDelegate.kt` | 5 个委托适配新签名 |
| `feature/reader/ReaderViewModel.kt` | 实现 DataSource；引入 pageFactory；jumpToPage；scrub |
| `feature/reader/ReaderScreen.kt` | update 块按 pageRenderMode 分流；底部加 PageScrubBar |
| `core/data/ReaderPreferences.kt` | 新增 `optimizeRender: Boolean = true` |

### 删除

| 路径 | 原因 |
|---|---|
| `core/reader/PageBuffer.kt` | 死代码（参数已不兼容 ReaderPageRenderer） |

---

## 11. 风险与回滚

| 风险 | 等级 | 缓解 |
|---|---|---|
| RenderNode 在 API < 24 不可用 | 低 | `CanvasRecorderImpl` 兜底（无缓存，等价当前每帧重画） |
| TextPage 改 class 破坏 data class 语义 | 中 | 全工程搜 `.copy(` 调用确认无影响；`equals` 用 `===` |
| SimulationPageDelegate 失去 BitmapShader 贴图 | 中 | 方案 A 临时 Bitmap 兜底；动画结束立即回收 |
| 流式分页破坏 totalPages 计算 | 中 | UI 用"已知页数"显示；isCompleted 后再 emit 真实总数 |
| 后台线程录制并发 crash | 低 | `CanvasRecorderLocked` 已 synchronized |
| GPL-3.0 协议合规 | 低 | 协议一致；保留每个移植文件的来源注释 |

**回滚策略**：每阶段独立 PR，可单独 revert。`optimizeRender` 配置开关支持运行时降级到老路径（每帧重画，无缓存），调试方便。

---

## 12. 与设置面板重构（doc 17）的协作

| 项 | 与本文档关系 |
|---|---|
| 字距 / 字重 / 对齐方式（doc 17 阶段三） | 本重构后，参数改变只需 `currentPage.invalidate()` + `submitRenderTask`，自动后台重录，无主线程卡顿 |
| 页眉脚三槽位（doc 17 阶段五） | per-Line recorder 落地后，仅页眉/页脚 line invalidate，不影响正文层 |
| 主题切换 | recorder 失效一次重录即可，30MB Bitmap 切换瞬间可见的"白闪"消失 |
| 屏幕亮度 | 与渲染层无关，独立 |
| 翻页动画 | 5 委托接口签名变更，需配合本重构同步改 |

**实施顺序建议**：先完成本文档**阶段 0~3**（recorder 化 + 后台线程），再开始 doc 17 设置面板的复杂数据项（字距/字重）。否则字距改动后整页 Bitmap 重建会让用户看到强烈闪烁。

---

## 13. 实施节奏建议

```
Week 1
  Day 1: 阶段 0  (CanvasRecorder 移植 + 单测)
  Day 2: 阶段 1  (TextPage 接入)
  Day 3: 阶段 2  (PageDelegate 适配)
  Day 4: 阶段 3  (renderThread)
  Day 5: 自测 + 修 Bug + 阶段 1-3 PR 提交

Week 2
  Day 1: 阶段 4  (TextPageFactory)
  Day 2: 阶段 5  (Slider + scrub)
  Day 3-4: 阶段 6  (流式分页)
  Day 5: 阶段 7  (per-Line recorder，可选)

Week 3
  Day 1: 阶段 8  (测试 + 基准)
  Day 2: 设置面板对接（doc 17 启动）
```

---

## 14. 验收清单（用户视角）

- [ ] 阅读时翻页流畅，90/120 Hz 设备帧率达标
- [ ] 章节切换不再有"白屏闪烁"
- [ ] 长按选区，文字背景平滑变色，无整页闪烁
- [ ] TTS 高亮逐句切换流畅
- [ ] 字号/字距/字体调节后，无可见 Bitmap 重建
- [ ] 主题切换瞬间生效，无白闪
- [ ] 拖动底部进度条 1000 页：页脚数字跟手，松手后立即显示目标页
- [ ] 目录/书签/搜索/笔记跳转：均无延迟（同章 < 100 ms，跨章 < 500 ms）
- [ ] 大章节（10 万字+）首页秒开
- [ ] 内存监控（Profiler）：阅读 1 小时无明显增长，峰值 < 30 MB

---

## 15. 附录 · 与 Legado 的相似与差异

| 点 | Legado | ShuLi（重构后） |
|---|---|---|
| 渲染缓存 | RenderNode/Picture | 同 |
| 后台线程 | `Executors.newSingleThreadExecutor` | 同 |
| 三个真实子 View | ✅ FrameLayout + 3 PageView | ❌ 仍用单 View（保留架构简洁） |
| 流式分页 | Flow + LayoutProgressListener | 同 |
| TextPageFactory 抽象 | ✅ | 同 |
| per-Line recorder | ✅ | ✅（阶段 7） |
| 翻页动画 | 6 种 | 5 种（暂不引 Fade） |
| AutoPager 自动翻页 | ✅ | 暂不实现 |
| 字粒度选区 | ✅（TextPos） | 仍按行（独立工作 R8） |

ShuLi 不引入"三子 View"是基于成本考虑：Legado 的方案需重写整套手势/动画/选区代码（数千行），收益主要在仿真翻页 BitmapShader 的天然支持。本次保留 ShuLi 单 View + 多 recorder 的融合方案，仿真翻页用动画期间临时 Bitmap 处理（仅 ~20 MB 暂时占用），其他场景与 Legado 等价。

---

## 16. 协议合规

- ShuLi-Reader 与 Legado **同为 GPL-3.0**，移植代码合规。
- 每个移植文件保留来源注释（见阶段 0 第 1.3 节模板）。
- 项目根 `LICENSE` 与 `README.md` 内补充"基于 Legado（GPL-3.0）的渲染工具类"说明。
- 后续若 Legado 修复关键 bug，建议保持 patch 同步，避免分叉。
