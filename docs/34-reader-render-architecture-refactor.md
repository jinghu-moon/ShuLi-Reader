# 34 - 阅读界面渲染架构根治重构方案

> 编写时间：2026-06-16
> 更新时间：2026-06-16（v6，融入代码审读确认：Phase 1 全部待实施、补充 4 个遗漏风险、调整 Phase 2 为模型纯化+壳层迁移并行）
> 前置文档：[33-canvas-recorder-architecture-analysis.md](33-canvas-recorder-architecture-analysis.md)
> 状态：待实施

---

## 0. 背景

docs/33 已完成 PR-1（删除 PicturePool）、PR-2（RenderNode 不再后台 flush）、PR-3a（禁止 recycle 复活）。但阅读界面在快速翻页、调整设置时仍偶发闪退。

本文从根因出发，诊断当前渲染架构的 **5 个系统性缺陷**，给出 **5 阶段分阶段可交付** 的重构方案。

### 已实施的修复（来自 docs/33）

- ✅ PR-1：删除 PicturePool（每实例自持 Picture）
- ✅ PR-2：RenderNode 不再后台 flush（UI 线程 lazy flush）
- ✅ PR-3a：禁止 recycle 复活（recycled 后返回 dummy Canvas）
- ✅ PR-4：PageBitmapCache generation token（过期任务丢弃）
- ✅ ReaderRenderOrchestrator + snapshot/diff 基础设施
- ✅ RenderApplierTarget 接口解耦
- ✅ InvalidationScope 枚举 + scope 级失效

### 待实施的 Phase 1 止血措施（全部 ❌）

| Step | 问题 | 状态 | 确认方式 |
|------|------|------|---------|
| 1.1 | RenderNodePool 跨实例共享 | ❌ `RenderNodePool.kt` 仍存在 | 文件存在 |
| 1.2 | dummyPicture 卡 recording 状态 | ❌ `endRecording()` recycled 路径不结束 dummy | `CanvasRecorderLocked.kt:63` |
| 1.3 | recordComposite 无子 recorder 防御 | ❌ 未检查 `isRecycled` | `TextModels.kt:188-195` |
| 1.4 | idle 路径缺少 overlay 绘制 | ❌ `onDraw()` 只画 shell+canvas | `ReaderCanvasView.kt:767-770` |
| 1.5 | StateFlow CAS 竞态 | ❌ 全线 `uiState.value = state.copy(...)` | `ReaderNavigationCoordinator.kt` 多处 |
| 1.6 | reflow 无防抖 | ❌ 无 delay | `ChapterPaginationCoordinator.kt` |
| 1.7 | 任务提交风暴 | ❌ 每个 scope 独立 submitRenderTask | `ReaderCanvasStateApplier.kt:28,50,54,58,62` |
| 1.8 | VisualParamsManager 偷提交任务 | ❌ `onSubmitRenderTask()` 绕过 applier | `CanvasVisualParamsManager.kt` 多处 |

### 正面设计（保留不动）

- Snapshot/Diff 体系（Orchestrator + SnapshotFactory + DiffCalculator）
- RenderApplierTarget 接口（可测试）
- CanvasRecorderLocked 终态+锁（方向正确，有小 bug）
- InvalidationScope 粒度划分
- 跨章检测防御（setPageInternal + doRecordPage）

---

## Part A：架构缺陷诊断

当前渲染架构的 5 个系统性缺陷不是孤立的 bug，而是同一组设计错误在不同层面的症状。

### 缺陷 A1：模型层与渲染资源生命周期耦合

**症状：** TextPage 在构造时创建 4 个 CanvasRecorder（`TextModels.kt:163-182`）。TextLine 也持有 `canvasRecorder`（`TextModels.kt:90`）。

**根因：** `TextPage` 和 `TextLine` 承担了两个不应共存的角色：

- **分页模型**：不可变数据（startCharOffset, endCharOffset, lines），由 Paginator 产出，被 CacheManager 缓存
- **渲染状态容器**：可变 UI 资源（recorder），有创建/失效/回收生命周期

后果：

1. **缓存驱逐不回收资源**：`LruCache.trimToSize()` 驱逐 TextPage 时仅丢弃引用，TextPage 上的 4 个 recorder 和所有 TextLine 的 recorder 无人回收（`LruCache.kt:82-89`，`CacheManager.kt:109-121`）。这些 recorder 持有 RenderNode/Picture native 资源，直到 GC 时才释放。
2. **同一 TextPage 被多方引用**：`uiState.currentPage`、`CacheManager`、`ReaderCanvasView` 同时持有同一 TextPage。`ReaderCanvasView.setPageInternal` 回收"旧页面"的 recorder 时，CacheManager 仍持有该页面 → 下次缓存命中取出的页面 recorder 已 disposed。
3. **章节缓存间接持有图形资源**：CacheManager 存 TextChapter → TextChapter 持有 `List<TextPage>` → 每个 TextPage 持有 4 个 recorder + N 个 TextLine recorder。这意味着缓存大小不仅受限于条目数，还隐式持有大量 native 资源。
4. **CacheManager 不知道 value 的资源语义**：`CacheManager.clearBook()` 和 `LruCache.clear()` 只清空 Map，不调用任何回调释放 native 资源。这进一步支持"模型纯化优先"——不要长期依赖回调释放 native 资源。

**证据链：**

```
Paginator.paginateChapter()  →  new TextPage(...)  [创建 4 个 recorder + N 个 TextLine recorder]
    ↓
CacheManager.putChapter()    →  LruCache.put()      [缓存 TextChapter，不感知 recorder]
    ↓
reflow → cacheManager.clearBook()  →  LruCache.clear()  [驱逐，不回收 recorder]
    ↓
新 TextPage 创建             →  旧 TextPage 的 recorder 泄漏
```

### 缺陷 A2：共享可变状态跨线程传递，无隔离契约

**症状：** 后台线程 `doRecordPage()` 接收并绘制主线程可变对象。

**根因：** 渲染管线中共享可变状态从主线程传递到后台线程，无任何隔离：

| 共享对象 | 主线程写入 | 后台线程读取 | 竞态后果 |
|----------|-----------|-------------|---------|
| `textPaint` / `backgroundPaint` / `selectionPaint` | `setTheme()` 改 color | `doRecordPage()` 用 paint 绘制 | Paint native 层 SIGSEGV |
| **ReaderPageRenderer 内部 Paint** | `updatePaintSnapshot()` 改 textSize/typeface | `renderContent()` / `renderShell()` 使用内部 titlePaint/dividerPaint/batteryStrokePaint/batteryFillPaint | 同上 |
| `RenderContext`（普通类，无锁） | `applySnapshot` 写 headerSlots/noteRanges | `doRecordPage` 读取 | 读到半写状态 |
| `noteRanges: List<Pair<SelectionRange, Paint>>` | 替换 List 引用 | 遍历旧 List 中的 Paint | Paint 并发读写 |
| `chapterContentsByIndex: MutableMap` | `applySnapshot` putAll | `snapshotChapterContents()` toMutableMap | ConcurrentModificationException |

**关键发现：** 仅冻结 `PageBitmapCache.submitRenderTask()` 入参的 3 个 Paint 不够。`ReaderPageRenderer` 内部还持有 `titlePaint`、`dividerPaint`、`batteryStrokePaint`、`batteryFillPaint` 等可变 Paint 对象（`ReaderPageRenderer.kt:33-53`），`renderShell()` 和 `renderContent()` 都直接读取这些内部 Paint。后台线程调用 `pageRenderer.renderShell()` 时，主线程可能正在通过 `visualParams.updatePaintSnapshot()` 修改同一个 renderer 的 textPaint.textSize。

PR-4 的 generation token 解决了"过期任务不执行"，但不解决"正在执行的任务中读取被主线程修改的共享对象"。

**本质：** 后台录制路径使用了 `ReaderPageRenderer` 的**有状态实例方法**。这不是加几个 Paint 副本能解决的问题——需要让后台路径使用提交时冻结的 StatelessReaderPageRenderer。

### 缺陷 A3：recorder 所有权模糊，多方操作无协调

**症状：** recorder 的创建、使用、失效、回收分散在 4 个不同的类中。

**当前 recorder 生命周期参与方：**

| 参与方 | 操作 | 感知范围 |
|--------|------|---------|
| `TextPage`（构造函数） | 创建 4 个 recorder | 不知道谁会用、何时回收 |
| `TextLine`（懒初始化） | 创建 canvasRecorder | 不知道页面何时回收 |
| `PageBitmapCache.doRecordPage()` | 使用 recorder 录制 | 不知道 recorder 是否已回收 |
| `ReaderCanvasView.setPageInternal()` | 回收旧页面 recorder | 不知道 CacheManager 是否还引用 |
| `ReaderCanvasView.onDetachedFromWindow()` | 回收当前三页 recorder | 不知道章节缓存中的页面 |
| `TextPage.invalidateAll()` | 标记 dirty | 不知道后台是否正在录制 |
| `TextPage.recordComposite()` | 使用子 recorder 叠加 | 不知道子 recorder 是否已回收 |
| `ReaderPageRenderer.drawLineWithRecorder()` | 懒创建行级 recorder | 不知道页面生命周期 |

没有统一的 **owner** 来协调"谁有权创建、谁有权回收、谁有权使用"。

### 缺陷 A4：StateFlow 巨型状态 + 非原子复合更新

**症状：** `ReaderUiState` 是 50+ 字段的 data class，多个写入者并发修改。

**根因：**

1. **读-改-写不是原子的：** `nextPage()` 执行 `state.copy(pageIndex = state.pageIndex + 1, currentPage = ...)`。这是两步操作：读 `.value` → 写 `.value = copy(...)`。中间窗口如果有另一个写入者（如 `paginateChapterStreaming` 的 `onPageReady`），新写入会覆盖前一个。

2. **关联字段不保证一致：** `currentPage` 和 `pageIndex` 必须指向同一页面。但 `nextPage()` 和 `onPageReady()` 分别写入这两个字段，交错时 `currentPage` 可能是第 N+1 页而 `pageIndex` 仍是 N。

3. **`currentPage` 可为 null 但下游未处理：** reflow 时 `clearBook` 清空页面列表，`currentPage` 短暂为 null。`onDraw()` 中 `currentPage == null` 时只画背景色，如果此时翻页动画正在进行，delegate 会访问 null page。

### 缺陷 A5：绕过 snapshot 系统的直接操作后门 + 任务风暴

**症状：** `ReaderCanvasView` 中有多个方法绕过 `applySnapshot` 直接操作 recorder；`ReaderCanvasStateApplier` 对每个 scope 都调用 `submitRenderTask()`，产生任务风暴。

**5a. 绕过路径：**

| 绕过路径 | 位置 | 问题 |
|----------|------|------|
| `setPageInternal` 直接 recycle recorder | `ReaderCanvasView.kt:330-338` | Orchestrator 不知道 recorder 被回收 |
| `startLayoutCrossfade` 直接 draw recorder | `ReaderCanvasView.kt:628-660` | 在 recorder 可能已被后台修改时绘制 |
| `fillPage` 直接交换 page 引用 | `ReaderCanvasView.kt:590-626` | 绕过 snapshot 的 page diff |
| `setBatteryLevel` 直接 invalidate + submitRenderTask | `ReaderCanvasView.kt:184-192` | 不走 snapshot diff |
| `setTheme` 直接 invalidateAllRecorders | `ReaderCanvasView.kt:570-579` | 绕过 snapshot 的 CONTENT/SHELL scope |

**5b. CanvasVisualParamsManager 偷偷提交任务：**

`CanvasVisualParamsManager` 的多个 setter（`updateHeaderFooter()`、`setTheme()`、`setTextAlign()`、`setFontFamily()`、`updateRenderProperty()`）内部调用 `onSubmitRenderTask()`。这些调用发生在 `applySnapshotInternal()` 过程中（`ReaderCanvasView.kt:454` 附近），绕过了 diff/applier 的统一调度。结果是：一次 snapshot apply 可能触发多次 submitRenderTask——VisualParamsManager 先提交一次，applier 再提交一次。

**5c. ReaderCanvasStateApplier 任务风暴：**

`ReaderCanvasStateApplier.apply()` 对每个 scope 都调用 `target.submitRenderTask()`（`ReaderCanvasStateApplier.kt:28,50,54,58,62`）。REFLOW 展开为 PAGE+CONTENT+SHELL+OVERLAY 时，加上 REFLOW 自身的一次，共提交 5 次后台任务。generation 只能丢弃过期任务，不能避免任务风暴——每次 submit 都向 renderThread 的队列追加一个任务。

**5d. idle 路径缺少 overlay 绘制（实现 bug）：**

`ReaderCanvasView.onDraw()` 静止路径（`ReaderCanvasView.kt:767-769`）只画 `shellRecorder` 和 `canvasRecorder`，没有画 `overlayRecorder`。动画路径使用 `recordComposite` 时才叠加 overlay。这意味着在静止状态下，选区高亮和笔记高亮不会被绘制——它们只在翻页动画期间可见。既然架构设计了 overlay 层，idle 路径应绘制 overlay。

---

## Part B：分阶段重构方案

每个 Phase 独立可交付。推荐顺序：**止血 → 模型纯化 → 线程隔离 → 统一入口**。

### Phase 1：止血 — 消除当前崩溃向量 + 实现 bug

**目标：** 在不改变架构的前提下，修复已确认的崩溃点和实现 bug。

**预期效果：** 快速翻页和设置调整不再闪退；选区/笔记高亮在静止状态可见。

**涉及文件：** ~12 个，改动量约 400 行。

| Step | 问题 | 改动 | 文件 |
|------|------|------|------|
| 1.1 | RenderNodePool 跨实例共享 | 删除 `renderNodePool`，每实例自持 RenderNode | `CanvasRecorderApi29Impl.kt`，删除 `RenderNodePool.kt` |
| 1.2 | dummyPicture 卡 recording 状态 | `endRecording()` recycled 路径中结束 dummy 录制 | `CanvasRecorderLocked.kt` |
| 1.3 | recordComposite 绘制已回收子 recorder | 检查子 recorder 是否已回收 | `TextModels.kt` |
| 1.4 | idle 路径缺少 overlay | `onDraw()` 静止路径增加 `overlayRecorder.draw(canvas)` | `ReaderCanvasView.kt` |
| 1.5 | StateFlow CAS 竞态 | 改用 `MutableStateFlow.update {}` 原子操作 | `ReaderNavigationCoordinator.kt`、`ChapterPaginationCoordinator.kt` |
| 1.6 | reflow 无防抖 | `reflowCurrentChapter` 增加 150ms debounce | `ChapterPaginationCoordinator.kt` |
| 1.7 | 任务提交风暴 | `ReaderCanvasStateApplier` 先收集 dirty scopes，最后只提交一次 | `ReaderCanvasStateApplier.kt`、`RenderApplierTarget.kt` |
| 1.8 | VisualParamsManager 偷提交任务 | 改为纯参数应用器：只改 Paint/RenderContext，不提交、不失效 | `CanvasVisualParamsManager.kt` |

**实施细节：**

#### 1.1 删除 RenderNodePool

与 PR-1 对 PicturePool 的处理一致。RenderNode 是 UI 线程图形宿主，跨实例共享引入并发访问 display list 的风险。

```kotlin
// CanvasRecorderApi29Impl.kt
// Before: companion object { private val renderNodePool = ... }
// After: init() 中直接 renderNode = RenderNode("CanvasRecorder")
// recycle() 中直接 renderNode = null
```

#### 1.2 修复 dummyPicture 状态泄漏

当前 `CanvasRecorderLocked` 在 `recycled=true` 时仍允许 `beginRecording()` 返回 dummy Canvas（PR-3a 的设计：避免后台线程因 TOCTOU 竞态崩溃），但 `endRecording()` 在 recycled 路径直接 return，不调用 `dummyPicture.endRecording()`。

修复：在 `recycle()` 路径统一处理 dummy 收尾，不引入额外状态标志：

```kotlin
// CanvasRecorderLocked.kt
override fun recycle() {
    val l = lock ?: return
    l.lock()
    try {
        if (!recycled) {
            recycled = true
            try { delegate.endRecording() } catch (_: IllegalStateException) {}
            delegate.recycle()
        }
        // 统一结束 dummy 录制（如果有）
        try { dummyPicture.endRecording() } catch (_: IllegalStateException) {}
    } finally {
        if (l.isHeldByCurrentThread) l.unlock()
    }
}

override fun endRecording() {
    if (recycled) {
        // recycled 路径：结束 dummy 录制
        try { dummyPicture.endRecording() } catch (_: IllegalStateException) {}
        return
    }
    // ... existing logic
}
```

比引入 `@Volatile isDummyRecording` 更 KISS：`endRecording()` 的 `IllegalStateException` catch 天然处理"未在录制"的情况。

#### 1.3 recordComposite 子 recorder 防御

```kotlin
// TextModels.kt
fun recordComposite(width: Int, height: Int) {
    if (!compositeRecorder.needRecord()) return
    if (shellRecorder.isRecycled() || canvasRecorder.isRecycled() || overlayRecorder.isRecycled()) {
        compositeRecorder.invalidate()
        return
    }
    compositeRecorder.record(width, height) {
        shellRecorder.draw(this)
        canvasRecorder.draw(this)
        overlayRecorder.draw(this)
    }
}
```

#### 1.4 idle 路径增加 overlay 绘制

```kotlin
// ReaderCanvasView.kt onDraw()
} else {
    current.shellRecorder.draw(canvas)
    current.canvasRecorder.draw(canvas)
    current.overlayRecorder.draw(canvas)  // 新增
}
```

#### 1.5 StateFlow 原子更新

将所有 `_uiState.value = state.copy(...)` 改为 `_uiState.update { state -> state.copy(...) }`。`update` 内部使用 CAS 循环保证原子性。

涉及文件：`ReaderNavigationCoordinator.kt`（nextPage/prevPage/jumpToPage/selectText/clearTextSelection）、`ChapterPaginationCoordinator.kt`（onPageReady/onLayoutCompleted）。

#### 1.6 reflow 防抖

```kotlin
// ChapterPaginationCoordinator.kt
fun reflowCurrentChapter(preferences: ReaderPreferences) {
    // ...
    reflowJob?.cancel()
    reflowJob = scope.launch {
        delay(150)  // debounce
        // ... existing reflow logic
    }
}
```

#### 1.7 任务提交合并

`ReaderCanvasStateApplier.apply()` 改为先执行所有 invalidate 操作，最后统一提交一次：

```kotlin
// ReaderCanvasStateApplier.kt
fun apply(target: RenderApplierTarget, snapshot: ReaderRenderSnapshot, diff: ReaderRenderDiff) {
    if (diff.scopes.isEmpty()) return

    if (InvalidationScope.REFLOW in diff.scopes) {
        target.invalidateAllPages()
    }

    val expanded = if (InvalidationScope.REFLOW in diff.scopes) {
        (diff.scopes + InvalidationScope.REFLOW_IMPLIED) - InvalidationScope.REFLOW
    } else {
        diff.scopes
    }

    expanded.sortedBy { it.order }.forEach { scope ->
        when (scope) {
            InvalidationScope.PAGE_DELEGATE -> target.rebuildPageDelegate()
            InvalidationScope.PAGE -> {
                val page = snapshot.page.currentPage ?: return@forEach
                target.setPage(page, snapshot.page.nextPage, snapshot.page.prevPage, snapshot.page.pageRenderMode)
            }
            InvalidationScope.CONTENT -> target.invalidateContentOnly()
            InvalidationScope.SHELL -> target.invalidateShellOnly()
            InvalidationScope.OVERLAY -> target.invalidateOverlayOnly()
            else -> { /* VIEW_INVALIDATE / NONE / REFLOW */ }
        }
    }

    // 统一提交一次（而非每个 scope 各提交一次）
    target.submitRenderTask()
}
```

#### 1.8 CanvasVisualParamsManager 纯参数化

移除 `onSubmitRenderTask` 回调和所有内部调用。`onPagesInvalidate` 和 `onInvalidate` 保留——它们只标记 dirty 和触发 View.invalidate()，不提交后台任务。是否提交后台任务由 `applySnapshotInternal` 统一决定：

```kotlin
// CanvasVisualParamsManager.kt
// 删除: private val onSubmitRenderTask: () -> Unit
// 删除: 所有 onSubmitRenderTask() 调用
// 保留: onInvalidate()（触发 View.invalidate()）
// 保留: onPagesInvalidate()（标记 recorder dirty）

// ReaderCanvasView.applySnapshotInternal() 末尾已有:
//   com.shuli.reader.feature.reader.render.ReaderCanvasStateApplier().apply(this, snapshot, diff)
//   invalidate()
// submitRenderTask() 由 StateApplier 统一调用（Step 1.7）
```

---

### Phase 2：模型纯化 — recorder 生命周期统一所有权

**目标：** 解决 A1（模型与渲染资源耦合）和 A3（所有权模糊）。

**核心思想：** 引入 `PageRenderStateStore` 作为 recorder 的唯一 owner。TextPage 和 TextLine 不再持有任何 recorder。

**架构变化：**

```
Before:
  TextPage (分页模型 + 4 个 recorder)
  TextLine (分页行模型 + 1 个 recorder)
  ├── 被 CacheManager 缓存（recorder 随之缓存）
  ├── 被 ReaderCanvasView 引用（recorder 被直接使用/回收）
  └── 被 PageBitmapCache 后台录制（recorder 被跨线程访问）

After:
  TextPage (纯分页模型，无 recorder)
  TextLine (纯分页行模型，无 recorder)
  ├── 被 CacheManager 缓存（安全，无 native 资源）
  └── 被 ReaderCanvasView 引用（安全，不可变数据）

  PageRenderStateStore (recorder 唯一 owner)
  ├── 以 PageKey 为索引管理页级 recorder（shell/content/overlay/composite）
  ├── 以 LineKey 为索引管理行级 recorder
  ├── 只有 UI 线程可操作
  ├── 知道哪些 page 当前活跃（current/next/prev）
  └── 统一处理创建、失效、回收
```

**实施步骤：**

1. **新增 `PageKey`** — 标识页面的不可变 key：

```kotlin
data class PageKey(
    val bookId: Long,
    val chapterIndex: Int,
    val pageIndex: Int,
    val startCharOffset: Int,
    val endCharOffset: Int,
    val layoutKey: LayoutKey,  // 复用 ReaderRenderKeys.kt 已有的 LayoutKey
)
```

`layoutKey` 直接复用 `ReaderRenderKeys.kt` 中 `ReaderLayoutHasher.hash(input)` 产出的 `LayoutKey`，而非重新发明 hash。`LayoutKey` 已包含所有影响分页的参数（fontSize、lineSpacing、margins、indent、pageSize 等），且不包含 overlay/chrome 级参数（主题色、页眉文字内容），符合"分页 key 不应因视觉变化而改变"的语义。

2. **新增 `LineKey`** — 标识行的不可变 key：

```kotlin
data class LineKey(
    val pageKey: PageKey,
    val lineIndex: Int,
)
```

3. **新增 `PageRenderState`** — 持有页级 recorder：

```kotlin
class PageRenderState(val key: PageKey) {
    val shell: CanvasRecorder = CanvasRecorderFactory.create(locked = true)
    val content: CanvasRecorder = CanvasRecorderFactory.create(locked = true)
    val overlay: CanvasRecorder = CanvasRecorderFactory.create(locked = true)
    val composite: CanvasRecorder = CanvasRecorderFactory.create(locked = true)
    var generation: Long = -1L
    fun recycle() { shell.recycle(); content.recycle(); overlay.recycle(); composite.recycle() }
    fun invalidateAll() { shell.invalidate(); content.invalidate(); overlay.invalidate(); composite.invalidate() }
}
```

4. **新增 `PageRenderStateStore`** — recorder 生命周期管理者：

```kotlin
class PageRenderStateStore {
    private val pageStates = mutableMapOf<PageKey, PageRenderState>()
    private val lineStates = mutableMapOf<LineKey, CanvasRecorder>()

    fun getPageState(key: PageKey): PageRenderState
    fun getLineRecorder(key: LineKey): CanvasRecorder
    fun recycleUnused(activePageKeys: Set<PageKey>)
    fun clear()
}
```

5. **TextPage 去除 recorder 字段** — `canvasRecorder`/`shellRecorder`/`overlayRecorder`/`compositeRecorder` 全部移出。TextPage 变为纯不可变数据类。

6. **TextLine 去除 recorder 字段** — `canvasRecorder` 移出（`TextModels.kt:90`）。`ReaderPageRenderer.drawLineWithRecorder()` 改为从 store 获取行 recorder。

7. **ReaderCanvasView** — 不再直接操作 page 的 recorder：
   ```kotlin
   val renderState = store.getPageState(page.toKey())
   // 使用 renderState.shell / renderState.content 等
   ```

8. **PageBitmapCache** — 从 store 获取 recorder：
   ```kotlin
   fun doRecordPage(
       page: TextPage,
       renderState: PageRenderState,  // 由调用方从 store 获取
       lineRecorders: Map<LineKey, CanvasRecorder>,  // 行级 recorder
       ...
   )
   ```

**关键文件：**
- `core/reader/model/TextModels.kt` — 删除 TextPage 和 TextLine 的 recorder 字段
- 新增 `core/reader/engine/cache/PageRenderStateStore.kt`
- `core/reader/engine/ReaderCanvasView.kt` — 改用 store
- `core/reader/engine/cache/PageBitmapCache.kt` — 改用 store
- `core/reader/engine/ReaderPageRenderer.kt` — `drawLineWithRecorder()` 改用 store

**依赖：** 依赖 Phase 1 完成。

**预期效果：** CacheManager 驱逐不再泄漏 native 资源；recorder 生命周期由单一 owner 管理；TOCTOU 竞态窗口大幅收窄。

---

### Phase 3：线程隔离 — StatelessRenderer + 不可变快照

**目标：** 解决 A2（共享可变状态跨线程）。

**核心思想：** 后台线程只使用提交时冻结的不可变数据，不持有任何主线程可变引用。

**实施步骤：**

1. **新增 `StatelessReaderPageRenderer`** — 后台录制专用的无状态渲染器：

```kotlin
class StatelessReaderPageRenderer(
    private val paints: PaintSnapshot,  // 冻结副本
) {
    fun renderShell(canvas: Canvas, page: TextPage, shellSnapshot: ShellRenderSnapshot)
    fun renderContent(canvas: Canvas, page: TextPage, content: CharSequence, contentSnapshot: ContentRenderSnapshot)
    fun renderOverlay(canvas: Canvas, page: TextPage, overlaySnapshot: OverlayRenderSnapshot)
}

data class PaintSnapshot(
    val text: Paint,       // Paint(srcTextPaint) 冻结副本
    val header: Paint,
    val footer: Paint,
    val progress: Paint,
    val background: Paint,
    val selection: Paint,
    val title: Paint,
    val divider: Paint,
    val batteryStroke: Paint,
    val batteryFill: Paint,
)
```

`StatelessReaderPageRenderer` 与 `ReaderPageRenderer` 的区别：前者所有 Paint 来自不可变参数，不持有任何可变状态；后者持有 textPaint/headerPaint 等可变实例字段。

2. **`RenderContext` → `RenderContextSnapshot`** — 改为 `data class`，所有字段 `val`：

```kotlin
data class RenderContextSnapshot(
    val headerSlots: SlotResolution,
    val footerSlots: SlotResolution,
    val showProgress: Boolean,
    val headerAlpha: Float,
    val footerAlpha: Float,
    val batteryLevel: Int,
    val selectedRange: SelectionRange?,
    val noteRanges: List<Pair<SelectionRange, Int>>,  // Paint 改为 color int，避免跨线程
    val showHeaderLine: Boolean,
    val showFooterLine: Boolean,
    val generation: Long,
)
```

3. **`PageBitmapCache.submitRenderTask()` 签名重构：**

```kotlin
data class RenderTaskToken(val generation: Long, val pageKeys: List<PageKey>)

fun submitRenderTask(
    token: RenderTaskToken,
    renderContext: RenderContextSnapshot,
    paints: PaintSnapshot,       // 主线程创建，不可跨提交复用
    pages: List<PageRecordTask>,
    postInvalidate: () -> Unit,
) {
    // PaintSnapshot 必须在此处（主线程、提交前）创建，不得缓存在字段中
    // 每次 submitRenderTask 都创建新快照，确保后台线程永远不读取主线程可变引用
    renderThread.execute {
        val renderer = StatelessReaderPageRenderer(paints)
        // ... 录制逻辑
    }
}
```

**关键约束：** PaintSnapshot 必须在 `submitRenderTask` 调用入口处（主线程）创建，不能缓存在 `ReaderCanvasView` 或 `CanvasVisualParamsManager` 的字段中跨提交复用。如果缓存复用，A2 的竞态窗口就没有真正关闭——后台线程仍可能读到主线程已修改的 Paint 值。

4. **`chapterContentsByIndex` 不可变化** — 每次 `applySnapshot` 创建新 `Map`，传不可变引用给后台

5. **移除 `ReaderCanvasView` 中的 `RenderContext` 可变实例** — 改为每次 `applySnapshot` 创建新快照

**关键文件：**
- 新增 `core/reader/engine/StatelessReaderPageRenderer.kt`
- `core/reader/engine/RenderContext.kt` — 改为不可变 data class
- `core/reader/engine/cache/PageBitmapCache.kt` — 签名重构 + 使用 StatelessRenderer
- `core/reader/engine/ReaderCanvasView.kt` — 快照化提交

**依赖：** 可与 Phase 2 并行（修改不同关注点）。

**预期效果：** 后台线程只读不可变数据 + 无状态渲染器，彻底消除 Paint SIGSEGV 和 ConcurrentModificationException。

---

### Phase 4：统一入口 — 消除 snapshot 后门 + 拆状态

**目标：** 解决 A4（巨型状态）和 A5（绕过 snapshot 的操作）。

**核心思想：** 所有渲染状态变化必须经过 `ReaderRenderOrchestrator.apply()`，消除直接操作 recorder 的后门。

**实施步骤：**

1. **`setBatteryLevel` / `setColorTemperature` / `setTheme` 改为 intent** — 通过 `ReaderIntent` → ViewModel → uiState → snapshot → apply 链路：
   ```kotlin
   // Before: ReaderCanvasView.setBatteryLevel() 直接 invalidate + submitRenderTask
   // After: dispatch(ReaderIntent.UpdateBattery(level)) → ViewModel → uiState → snapshot
   ```

2. **`fillPage` 纳入 snapshot** — 翻页动画的 page 交换由 NavigationCoordinator 更新 uiState → snapshot diff 检测 PAGE scope → applier 调用 `setPage`。`ReaderCanvasView.fillPage` 仅作为 PageDelegate callback 的桥接，不直接交换 page 引用。

3. **`startLayoutCrossfade` 保留为 CanvasView 内部动画** — 但改为使用 snapshot 的 page 引用而非直接访问 page 的 recorder。

4. **ReaderUiState 拆分** — 将 50+ 字段拆为 4 个独立 StateFlow：

   | StateFlow | 字段 | 变化频率 | 观察者 |
   |-----------|------|---------|--------|
   | `PageState` | currentPage/pageIndex/chapterIndex/totalPages | 高频（翻页） | AndroidView.update |
   | `LayoutState` | fontSize/margin/indent/lineSpacing 等排版参数 | 中频（设置变更） | AndroidView.update |
   | `ThemeState` | theme/colors/colorTemperature | 低频 | AndroidView.update |
   | `ChromeState` | toolbar/search/directory/overlay 可见性 | 中频 | Compose UI（不触发 Canvas 重绘） |

   拆分后 `AndroidView.update` 只观察 `PageState` + `LayoutState` + `ThemeState`，工具栏/搜索等 UI 变化不再触发 Canvas 更新。

   **跨 StateFlow 一致性：** 拆分后有些操作需要同时更新多个 StateFlow（如换字体同时影响 LayoutState 的 fontKey 和 ThemeState 的渲染参数）。下游用 `combine(layoutState, themeState)` 消费时，可能在两次 emit 之间看到半写状态（LayoutState 新、ThemeState 旧）。多数场景下短暂不一致可接受（两次 emit 间隔极短，同一 coroutine 上下文），但需在设计时枚举所有跨 StateFlow 的联动操作。对于必须原子的场景（如 reflow 触发），可通过中间 `SnapshotState` 聚合后再 emit。

**关键文件：**
- `feature/reader/screen/ReaderCanvasView.kt` — 删除后门方法
- `feature/reader/screen/ReaderScreen.kt` — 拆分观察
- `feature/reader/screen/ReaderUiState.kt` — 拆分为 4 个子状态
- `feature/reader/screen/ReaderViewModel.kt` — 暴露 4 个 StateFlow

**依赖：** 依赖 Phase 2 + Phase 3。

**预期效果：** Orchestrator 成为唯一渲染入口；StateFlow 拆分减少不必要的 recomposition 和 update 调用。

---

### Phase 5：key 驱动失效 — 消除手动 invalidate

**目标：** 从 dirty flag 模型过渡到 render key diff 模型，消除"忘记 invalidate"和"过度 invalidate"两类 bug。

**核心思想：** 每个 recorder layer 关联一个 `RenderKey`（不可变 data class）。只有 key 变化才重录，不再需要手动调用 `invalidate()`/`invalidateAll()`/`invalidateShell()`。

**实施步骤：**

1. **`PageRenderState` 的每个 layer 持有 `RenderKey`**：
   ```kotlin
   class LayerState(val recorder: CanvasRecorder, var key: RenderKey) {
       fun updateKey(newKey: RenderKey): Boolean {
           if (key == newKey) return false
           key = newKey
           recorder.invalidate()
           return true
       }
   }
   ```

2. **`applySnapshot` 时比较 key** — 新 key == 旧 key → 跳过；新 key != 旧 key → 标记 dirty 并重录

3. **删除 `TextPage.invalidate*()` 系列方法** — 失效判断完全由 key diff 驱动

4. **标记 `InvalidationScope` 为 `@Deprecated`** — Phase 5 完成后留观一个迭代，确认 overlay 事件（电量、时间、进度更新）都已通过 key 正确触发重录后再彻底删除。有些外部事件驱动的失效（屏幕旋转、系统字体变化）不一定自然对应一个 key 变化，需确认兜底路径。

**已有基础：** `ReaderRenderKeys.kt` 中的 `RenderKey`/`ShellSnapshot`/`OverlayKey` 已经存在，只是尚未用于 recorder 层级的精确失效判断。

**关键文件：**
- `PageRenderStateStore`（Phase 2 产物）— 新增 key diff 逻辑
- `TextModels.kt` — 删除 `invalidate*()` 方法
- `InvalidationScope.kt` — 删除文件
- `ReaderCanvasStateApplier.kt` — 简化为 key diff 驱动

**依赖：** 依赖 Phase 2 + Phase 4。

---

## 实施注意事项（深度代码审读发现）

以下是在深度审读全部相关文件后发现的实施细节，如果忽略可能导致重构引入新问题。

### N1：行级 recorder 懒创建必须迁入 PageRenderStateStore

`ReaderPageRenderer.drawLineWithRecorder()`（`ReaderPageRenderer.kt:334-335`）在首次绘制行时懒创建 `TextLine.canvasRecorder`：

```kotlin
val recorder = line.canvasRecorder
    ?: CanvasRecorderFactory.create().also { line.canvasRecorder = it }
```

Phase 2 移除 `TextLine.canvasRecorder` 后，此处必须改为从 `PageRenderStateStore` 获取行 recorder：

```kotlin
val lineKey = LineKey(pageKey, lineIndex)
val recorder = store.getLineRecorder(lineKey)  // store 负责懒创建
```

如果遗漏此处，行绘制会编译失败。同时 `drawLineWithRecorder()` 的签名需要接收 `PageKey` 和 `lineIndex` 参数。

### N2：startLayoutCrossfade 使用 canvasRecorder，非 compositeRecorder

`ReaderCanvasView.startLayoutCrossfade()`（`ReaderCanvasView.kt:636`）在排版变化时将当前页录制到 Bitmap：

```kotlin
cur.canvasRecorder.draw(captureCanvas)
```

Phase 2 将 recorder 从 TextPage 移出后，此处需要改为：

```kotlin
val pageKey = cur.toPageKey(currentLayoutKey)
val renderState = store.getPageState(pageKey)
renderState.content.draw(captureCanvas)
```

注意：crossfade 只需要 content 层（正文），不需要 shell/overlay。这决定了 Bitmap 捕获的是 content recorder 而非 composite。

### N3：翻页动画 abort() 的异步竞态

所有 `PageDelegate.abort()` 实现中，如果动画在 committed 状态下被中断，会同步调用 `callback?.onPageChanged(prevDirection)`（如 `HorizontalPageDelegate.kt:158-159`）。

`ReaderCanvasView` 的 `onPageChanged` 回调直接调用 `fillPage()`（同步交换 page 引用），但同时通过 ViewModel dispatch 触发异步的 `nextPage()`/`prevPage()`。这意味着：

1. `fillPage()` 同步更新 `currentPage`/`nextPage`/`prevPage`
2. ViewModel 异步更新 `uiState.currentPage`/`pageIndex`
3. 下一次 `applySnapshot` 时，snapshot 的 page 引用可能与 `fillPage` 的结果不一致

Phase 4 计划将 `fillPage` 纳入 snapshot，但过渡期需要确保 `fillPage` 不会在 `abort()` 路径中产生与 snapshot 矛盾的 page 状态。建议在 Phase 4 实施前，`abort()` 路径的 `onPageChanged` 只通过 ViewModel dispatch 触发，不再直接调用 `fillPage()`。

### N4：isReflowing 标志的竞态窗口

`ReaderSettingsManager.updatePrefs()` 在 `reflow=true` 时设置 `isReflowing = true`（`ReaderSettingsManager.kt:648`）。`ChapterPaginationCoordinator.onLayoutCompleted()` 设置 `isReflowing = false`。

如果用户在 reflow 期间快速切换设置（如连续切换页眉可见性），每次 `updatePrefs` 都会设 `isReflowing = true`，但只有最后一次 reflow 的 `onLayoutCompleted` 会设 `false`。中间被取消的 reflow 不会触发 `onLayoutCompleted`，所以 `isReflowing` 可能卡在 `true`。

Phase 1.6 的 reflow 防抖（150ms delay）会缓解此问题，但不能根治。建议在 `reflowJob?.cancel()` 后也重置 `isReflowing = false`：

```kotlin
fun reflowCurrentChapter(preferences: ReaderPreferences) {
    reflowJob?.cancel()
    // 取消旧 reflow 时重置 isReflowing，避免卡住
    uiState.value = uiState.value.copy(isReflowing = false)
    reflowJob = scope.launch {
        delay(150)
        uiState.value = uiState.value.copy(isReflowing = true)
        // ... existing reflow logic
    }
}
```

### N5：ReaderSettingsManager.updatePrefs 的 CAS 竞态

`updatePrefs()` 执行 read-modify-write（`ReaderSettingsManager.kt:645-649`）：

```kotlin
val updated = transform(uiState.value.readerPreferences)
uiState.value = uiState.value.copy(readerPreferences = updated, ...)
```

这与 Phase 1 Step 1.5 修复的 `nextPage()` CAS 竞态是同一类问题。虽然设置变更通常来自主线程的 UI 交互（串行），但 `ReaderPreferenceMonitor` 可能从 DataStore flow 并发写入。建议 `updatePrefs()` 也改用 `_uiState.update {}`。

### N6：noteRanges 的 Paint 跨线程（Phase 3 补充）

`CanvasVisualParamsManager.setNoteRanges()`（`CanvasVisualParamsManager.kt:210-227`）创建 Paint 对象并存入 `renderContext.noteRanges: List<Pair<SelectionRange, Paint>>`。后台线程 `renderOverlay()` 遍历这个列表并使用 Paint 绘制。

Phase 3 的 `RenderContextSnapshot` 已将 `noteRanges` 改为 `List<Pair<SelectionRange, Int>>`（Paint → color int）。实施时需同步修改 `ReaderPageRenderer.renderOverlay()` 的签名，接收 color int 而非 Paint：

```kotlin
// Before
fun renderOverlay(canvas: Canvas, page: TextPage, noteRanges: List<Pair<SelectionRange, Paint>>, ...)

// After (StatelessReaderPageRenderer)
fun renderOverlay(canvas: Canvas, page: TextPage, noteRanges: List<Pair<SelectionRange, Int>>, ...) {
    // 内部创建临时 Paint 或使用预分配的 notePaint
}
```

### N7：翻页动画 delegate 的 recorder 生命周期

所有动画 delegate（`HorizontalPageDelegate`、`CoverPageDelegate`、`SimulationPageDelegate`）的 `onDraw()` 接收 `current: CanvasRecorder` 和 `target: CanvasRecorder`。这些是 composite recorder。

在 `ReaderCanvasView.onDraw()` 中，这些 composite recorder 来自：

```kotlin
current.recordComposite(width, height)
delegate.onDraw(canvas, current.compositeRecorder, target.compositeRecorder)
```

Phase 2 后，composite recorder 不再属于 TextPage，而是属于 `PageRenderState`。`onDraw` 需要改为：

```kotlin
val currentKey = current.toPageKey(layoutKey)
val targetKey = target?.toPageKey(layoutKey)
val currentState = store.getPageState(currentKey)
val targetState = targetKey?.let { store.getPageState(it) }

// recordComposite 需要在 store 层面实现
store.recordComposite(currentKey, width, height)
targetKey?.let { store.recordComposite(it, width, height) }

delegate.onDraw(canvas, currentState.composite, targetState?.composite ?: currentState.composite)
```

`PageRenderStateStore.recycleUnused()` 的 activeKeys 必须包含动画 delegate 正在使用的 target page key。

### N8：PageRenderContext 持有 mutable Paint 引用

`PageRenderContext`（`PageRenderContext.kt:12-23`）持有 `textPaint: Paint`，与 `ReaderCanvasView` 共享同一实例。`PageBitmapCache.recordPage()`（主线程路径）和 `recordPageOffMain()`（后台路径）都创建 `PageRenderContext` 并传入 textPaint。后台路径构成跨线程共享可变 Paint 引用。

Phase 3 的 `StatelessReaderPageRenderer` + `PaintSnapshot` 将彻底解决此问题。过渡期（Phase 1）可通过在 `submitRenderTask` 入口处创建 Paint 副本来缓解。

### N9：chapterContentsByIndex MutableMap 非同步读写

`ReaderCanvasView.chapterContentsByIndex`（`mutableMapOf<Int, CharSequence>()`）在主线程 `setPageInternal`/`applySnapshot` 中写入，在 `submitRenderTask` 的 `snapshotChapterContents()` 中通过 `toMutableMap()` 读取。`toMutableMap()` 遍历原 Map 时如果主线程并发写入，可能 `ConcurrentModificationException`。

修复方案：`applySnapshot` 中每次创建新的 `Map`（不可变引用替换），而非 `putAll` 到现有 MutableMap。

### N10：动画期间后台录制与 crossfade 的锁竞争

`startLayoutCrossfade` 在主线程绘制旧页面的 `canvasRecorder`（`ReaderCanvasView.kt:636`），同时后台渲染线程可能在同一 recorder 上执行 `recordIfNeeded`。`CanvasRecorderLocked` 的 `ReentrantLock` 保证不会崩溃，但会产生锁竞争导致帧率下降。

建议约定：**动画期间，后台对该 page 的录制任务应视为"for display only"而非"for storage"**。具体做法：`startLayoutCrossfade` 开始时将旧页面的 recorder 标记为"animation locked"，后台任务跳过该 recorder 的录制，动画结束后再正式录制。Phase 2 的 `PageRenderStateStore` 可以实现此语义。

### N11：RenderNodePool 删除的影响评估

删除 `RenderNodePool` 后，每个 `CanvasRecorderApi29Impl` 实例自持一个 `RenderNode`。低端设备上 50+ TextPage × 4 recorder = 200+ RenderNode 实例。`RenderNode` 本身是轻量 native 对象（名称字符串 + display list），200 个实例的内存开销远小于 native 资源泄漏的风险。GC 比泄漏好。确认此改动可接受。

---

## Phase 依赖关系与推荐顺序

### 推荐方案

基于代码审读确认和 Legado 架构分析的洞察，**Phase 2 同时执行模型纯化和壳层 Compose 化**。两者不冲突：模型纯化把 recorder 移出 TextPage，壳层 View 化完全去掉 shellRecorder，两者叠加后 TextPage 只剩 contentRecorder + overlayRecorder，复杂度减半。

```
Phase 1（止血 + bug 修复）──→ Phase 2（模型纯化 + 壳层 Compose 化 并行）──→ Phase 3（线程隔离）──→ Phase 4（统一入口）──→ Phase 5（key 驱动）
```

| Phase | 内容 | 风险降低 | 代码量 | 建议时机 |
|-------|------|---------|--------|---------|
| **Phase 1** | 止血：8 个 Step（见上方待实施表） | 高（消除崩溃 + 修复 overlay bug） | ~400 行 | 立即 |
| **Phase 2** | 模型纯化（PageRenderStateStore）+ 壳层迁移到 Compose | 高（根治资源泄漏 + 结构性消除 A2 一半风险面） | ~800 行 | Phase 1 后 |
| **Phase 3** | 线程隔离（StatelessRenderer + 不可变快照，仅 content+overlay） | 高（根治线程竞态） | ~400 行 | Phase 2 后 |
| **Phase 4** | 统一入口（消除后门）+ StateFlow 拆分 | 中 | ~400 行 | Phase 3 后 |
| **Phase 5** | key 驱动失效 | 低 | ~200 行 | Phase 4 后 |

**Phase 2 双重改造的关键收益：**
- TextPage 从 4 个 recorder 减为 2 个（content + overlay），资源开销减半
- shellRecorder 和 compositeRecorder 彻底消失（壳层走 Compose，不再需要 composite 叠加）
- `renderShell()` 从后台线程完全移除 → headerPaint/footerPaint/progressPaint/dividerPaint 不再是跨线程共享状态
- `PageBitmapCache.doRecordPage()` 简化为只录 content + overlay → 快照参数减少
- `PageRenderState` 只需 2 个 recorder → Phase 3 的 `PaintSnapshot` 只需 textPaint + selectionPaint
- 页眉/页脚/电量/时间变化不再触发 Canvas 重录 → 性能提升

### Phase 1 实施优先级

按收益/成本比排序：

**🔴 P0 — 高收益/低成本（立即实施）：**

| Step | 内容 | 预期工时 |
|------|------|---------|
| 1.4 | idle 路径加 `overlayRecorder.draw(canvas)` | 1 行 |
| 1.2 | dummyPicture 收尾 | ~10 行 |
| 1.3 | recordComposite 子 recorder 防御 | ~5 行 |
| 1.5 | StateFlow `update {}` 原子更新 | ~10 处替换 |

**🟡 P1 — 改造型（速做）：**

| Step | 内容 | 预期工时 |
|------|------|---------|
| 1.1 | 删除 RenderNodePool | ~30 行 |
| 1.6 | reflow 防抖 | ~5 行 |
| 1.7+1.8 | 任务提交合并 + VisualParamsManager 去提交 | ~60 行 |

---

## 未充分覆盖的风险

### 翻页动画期间的并发录制

`startLayoutCrossfade`（`ReaderCanvasView.kt:628-660`）在动画期间持有旧页面的 `canvasRecorder` 引用，将其 draw 到 Bitmap。动画持续 200ms，期间旧页面的 recorder 不能被回收。

Phase 2 引入 `PageRenderStateStore.recycleUnused(activePageKeys)` 时，**必须把动画帧持有的 pageKey 也纳入"活跃"集合**。具体做法：

```kotlin
class PageRenderStateStore {
    fun recycleUnused(activePageKeys: Set<PageKey>) {
        // activePageKeys 必须包含：
        // 1. 当前 current/next/prev 三页
        // 2. crossfade 动画中的旧页面（动画结束前）
        // 3. 翻页动画 delegate 持有的 target 页面
        val keysToRemove = pageStates.keys - activePageKeys
        keysToRemove.forEach { key ->
            pageStates.remove(key)?.recycle()
        }
    }
}
```

如果遗漏了动画帧持有的 pageKey，动画中途 disposeUnused 会让旧帧录制器被清理，产生新的闪烁或崩溃。`ReaderCanvasView` 需要在动画开始/结束时通知 store 更新活跃集合。

### StrictMode 应立即开启

两份文档的验证方案都依赖手工测试。Android `StrictMode.ThreadPolicy` 可以在开发构建中自动检测线程违规，建议在 Debug build 中立即开启：

```kotlin
// Application.onCreate() 或 ReaderActivity.onCreate()
if (BuildConfig.DEBUG) {
    StrictMode.setThreadPolicy(
        StrictMode.ThreadPolicy.Builder()
            .detectCustomSlowCalls()
            .detectDiskReads()
            .detectDiskWrites()
            .penaltyLog()
            .build()
    )
}
```

Phase 3 完成后可通过 StrictMode 自动验证后台线程是否干净（不接触主线程对象）。

---

## 验证方案

每个 Phase 交付后执行：

1. **Phase 1：** 快速连续切换页眉页脚可见性 20 次 + 快速拖拽字号滑块后立即翻页 + 仿真翻页动画中切换主题 + 跨章翻页 + 静止状态选区高亮可见
2. **Phase 2：** 反复进出阅读页 50 次 + 低内存模式下阅读 + Android Profiler 确认无 recorder native 资源泄漏 + TextLine recorder 一并验证
3. **Phase 3：** StrictMode 线程违规检测（自动验证后台线程是否干净）+ 后台录制中修改所有设置项 + StatelessReaderPageRenderer 单元测试
4. **Phase 4：** 审计所有渲染路径确认经 Orchestrator + 确认无 recorder 直接操作后门 + AndroidView.update 不被 ChromeState 触发
5. **Phase 5：** 删除所有 `invalidate*()` 调用后渲染正确 + 性能不退化

---

## 原则应用

| 原则 | 应用 |
|------|------|
| SRP | TextPage/TextLine 只负责分页模型；PageRenderStateStore 只负责渲染缓存；CanvasVisualParamsManager 只负责参数应用 |
| DIP | PageBitmapCache 依赖 RenderTaskToken / PaintSnapshot / StatelessRenderer，不直接依赖具体图形宿主 |
| KISS | 删除所有对象池和 recycle 复活逻辑，用 GC 管理轻量 native 对象 |
| DRY | shell/content/overlay/composite 统一由 PageRenderState 管理，失效由 key diff 驱动 |
| 线程安全 | 后台线程只读不可变快照 + 无状态渲染器；主线程是 recorder 唯一写入者 |

---

## Legado 架构对比分析

对 `refer/legado-with-MD3-3.26.13-beta.26` 进行了深度审读。Legado 是 ShuLi 的上游参考项目（`CanvasRecorderLocked` 等文件标注了 "Adapted from legado"），两者在渲染管线设计上有显著差异。

### 核心架构差异

| 维度 | Legado | ShuLi | 影响 |
|------|--------|-------|------|
| **壳层渲染** | Android View（`PageView` XML 布局 + `BatteryView`） | Canvas 统一管线（`renderShell()`） | ShuLi 壳层参与 Canvas 录制，增加共享可变状态 |
| **内容渲染** | Canvas + `CanvasRecorder`（仅文本） | Canvas + `CanvasRecorder`（文本 + 标题） | 相似 |
| **后台渲染** | `TextPageRender` 单线程 executor | `ShuLi-PageRender` 单线程 executor | 相似，但 legado 后台只录 content 层 |
| **状态管理** | `ReadBook` 全局单例 + EventBus | `ReaderViewModel` + `MutableStateFlow` | Legado 简单但测试性差；ShuLi 响应式但复杂 |
| **翻页动画** | `Scroller`（Android 框架） | `ValueAnimator`（自定义） | Legado 由框架驱动时序，更稳定 |
| **TextPage** | `data class` + `canvasRecorder` | `class` + 4 个 recorder | ShuLi 每页 4× 资源开销 |
| **章节加载同步** | `Mutex`（kotlinx.coroutines） | `generation` token | Legado 粗粒度但简单；ShuLi 细粒度但易遗漏 |

### 关键发现：Legado 的壳层分离

Legado 最重要的架构决策是**壳层与内容层分离渲染**：

- **壳层（页眉/页脚/电量/时间/进度）**：使用 Android View（`BatteryView`、`TextView`），由 Android 框架管理生命周期和绘制。`PageView.upStyle()` 直接设置 View 属性，不涉及 Canvas 录制。
- **内容层（正文文本）**：使用 `ContentTextView.onDraw()` → `TextPage.draw()` → `canvasRecorder.recordIfNeeded()`。后台线程 `preRenderPage()` 也只录制 content 层。

这意味着 Legado 的后台线程**不接触** headerPaint/footerPaint/progressPaint/dividerPaint 等壳层 Paint，因为壳层根本不走 Canvas 录制。这从结构上消除了 A2 缺陷的一半风险面。

ShuLi 将壳层（`renderShell`）、内容（`renderContent`）、覆盖层（`renderOverlay`）全部走 Canvas 录制管线，虽然架构更统一，但代价是所有 Paint 和 RenderContext 都成为跨线程共享状态。

### 关键发现：Legado 也有后台渲染线程

`ContentTextView` 的 companion object 持有 `renderThread`（`Executors.newSingleThreadExecutor`），`submitRenderTask()` 提交 `preRenderPage()` 到后台线程。后台线程调用 `textPage.render(view)` → `canvasRecorder.recordIfNeeded()`。

Legado 同样面临 A2 的线程安全问题，但范围更小：
- `ChapterProvider.contentPaint` 是共享 Paint，后台线程的 `drawText` 使用它
- 但壳层 Paint（header/footer/divider/battery）不在后台线程使用

### 可借鉴的设计

#### 1. 壳层 View 化（推荐，已纳入 Phase 2）

将页眉/页脚/电量/进度条从 Canvas 录制迁移到 Android View（或 Compose），与 Legado 的方案一致：

```
Before:
  ReaderCanvasView.onDraw()
  ├── shellRecorder.draw()  ← Canvas 录制
  ├── canvasRecorder.draw()  ← Canvas 录制
  └── overlayRecorder.draw() ← Canvas 录制

After:
  ReaderScreen (Compose)
  ├── HeaderFooterBar (Compose)  ← 框架管理
  ├── ReaderCanvasView
  │   ├── contentRecorder.draw()  ← Canvas 录制
  │   └── overlayRecorder.draw()  ← Canvas 录制
  └── ProgressBar (Compose)  ← 框架管理
```

好处：
- 壳层变化（电量、时间、页眉文字）不再触发 Canvas 重录
- 壳层 Paint 从后台线程完全移除，A2 风险面缩小一半
- `ReaderCanvasStateApplier` 不再需要 SHELL scope

#### 2. Scroller 替代 ValueAnimator（可选优化）

Legado 的翻页动画使用 `Scroller`（`computeScroll()` + `scroller.computeScrollOffset()`），由 Android 框架驱动时序。ShuLi 使用 `ValueAnimator`，需要手动管理 `isAborting`、`onAnimationEnd` 竞态。

`Scroller` 的优势：
- 与 View 的 `computeScroll()` 自然集成，不需要 `AnimatorListener`
- `abortAnim()` 只需 `scroller.abortAnimation()`，不会触发回调
- 无 `isAborting` 标志位，状态机更简单

但迁移成本较高，且 ShuLi 的 `SimulationPageDelegate`（贝塞尔曲线仿真翻页）不适合 Scroller。建议保留 ValueAnimator，但简化 abort 逻辑（参考 N3）。

#### 3. Mutex 用于章节加载同步（Phase 1 可选）

Legado 使用 `Mutex`（`prevChapterLoadingLock`、`curChapterLoadingLock`、`nextChapterLoadingLock`）保护章节加载，确保同一章节不会被并发加载。ShuLi 的 `ChapterPaginationCoordinator` 使用 `reflowJob?.cancel()` + 重启，但没有 Mutex 保护。

建议在 `reflowCurrentChapter` 中增加 Mutex：

```kotlin
private val reflowMutex = Mutex()

fun reflowCurrentChapter(preferences: ReaderPreferences) {
    reflowJob?.cancel()
    reflowJob = scope.launch {
        delay(150)
        reflowMutex.withLock {
            // ... existing reflow logic
        }
    }
}
```

### 不应借鉴的设计

#### ReadBook 全局单例

Legado 的 `ReadBook` 是 Kotlin `object`（全局单例），持有所有阅读状态。这简化了状态访问，但：
- 无法与 Compose 的 `collectAsState()` 集成
- 难以做作用域隔离（GLOBAL vs BOOK）
- 测试时需要手动重置全局状态

ShuLi 的 `ReaderViewModel` + `StateFlow` 是更好的选择。

#### EventBus 松耦合通知

Legado 使用 `postEvent(EventBus.UP_CONFIG)` 通知配置变化。这种方式：
- 类型不安全（事件是字符串/枚举）
- 难以追踪数据流
- 无法做 diff 驱动的精确失效

ShuLi 的 `ReaderRenderOrchestrator` + snapshot diff 是更优方案。

---

## 并发模型分析

### 当前并发执行路径

阅读界面**不是完全串行的**，这是设计选择——纯串行会导致 UI 卡顿。当前有 5 个并发执行路径：

| 路径 | 线程 | 职责 | 串行？ |
|------|------|------|--------|
| **UI 主线程** | Main | `dispatch()`、`applySnapshot()`、`onDraw()`、动画 delegate、触摸事件 | ✅ 串行 |
| **后台渲染** | `renderThread`（单线程 executor） | `PageBitmapCache.doRecordPage()` — 录制 shell/content/overlay | ❌ 与主线程并行 |
| **分页协程** | `Dispatchers.Default` | `Paginator.paginateStreaming()` — 流式分页 | ❌ 与主线程并行 |
| **IO 协程** | `Dispatchers.IO` | 章节文本加载、DataStore 读写、搜索索引 | ❌ 与主线程并行 |
| **DataStore flow** | 不确定 | `ReaderPreferenceMonitor` 监听偏好变化 → 写 `_uiState` | ❌ 可能与 UI 线程并发 |

### 重构后的并发模型

重构不改变"多线程并行"的架构——那是性能所需。重构改变的是**跨线程数据传递方式**：

```
Before（当前）:
  主线程 ──(可变 Paint 引用)──→ 后台渲染线程
  主线程 ──(可变 RenderContext)──→ 后台渲染线程
  分页协程 ──(uiState.value = copy)──→ 主线程
  UI 交互 ──(uiState.value = copy)──→ 主线程
  DataStore ──(uiState.value = copy)──→ 主线程

  问题：多个写入者非原子地修改 _uiState；后台线程读取主线程可变对象

After（重构后）:
  主线程 ──(不可变 PaintSnapshot)──→ 后台渲染线程     [Phase 3]
  主线程 ──(不可变 RenderContextSnapshot)──→ 后台渲染线程  [Phase 3]
  主线程 ──(StateFlow.update {})──→ 所有写入者         [Phase 1]
  主线程 ──(generation token)──→ 后台渲染任务过期校验   [已有 + Phase 3]
```

### 哪些操作是串行的

| 操作 | 重构前 | 重构后 | 说明 |
|------|--------|--------|------|
| `dispatch(ReaderIntent)` | ✅ 主线程串行 | ✅ 不变 | Compose UI 回调在主线程 |
| `applySnapshot()` | ✅ 主线程串行 | ✅ 不变 | `AndroidView.update` 在主线程 |
| `onDraw()` | ✅ 主线程串行 | ✅ 不变 | View 绘制在主线程 |
| `fillPage()` | ✅ 主线程串行 | ✅ 不变 | PageDelegate callback 在主线程 |
| `recycleUnused()` | ✅ 主线程串行 | ✅ 不变 | Store 操作在主线程 |
| `nextPage()` vs `onPageReady()` | ❌ CAS 竞态 | ✅ `_uiState.update {}` 原子 | Phase 1.5 |
| `updatePrefs()` vs PreferenceMonitor | ❌ CAS 竞态 | ✅ `_uiState.update {}` 原子 | Phase 1.5 + N5 |
| 后台录制 vs 主线程改 Paint | ❌ Paint SIGSEGV | ✅ PaintSnapshot 不可变 | Phase 3 |
| 后台录制 vs 主线程改 RenderContext | ❌ 半写状态 | ✅ RenderContextSnapshot 不可变 | Phase 3 |

### 翻页期间的并发场景分析

**场景：用户在 reflow 进行中翻页**

```
时间线:
  T0: 用户拖动字号滑块 → updatePrefs(fontSize=20) → reflowJob 启动（150ms 后）
  T1: 150ms 后 → cacheManager.clearBook() → 旧 TextPage 被驱逐
  T2: paginator.paginateStreaming() 开始（Dispatchers.Default）
  T3: 用户点击翻页 → dispatch(TurnPage) → nextPage()（主线程）
  T4: nextPage() 内 chapter.getPage(pageIndex+1) → 可能返回 null（分页未完成）
```

**重构后的处理：**
- T3 的 `nextPage()` 使用 `_uiState.update {}` 原子操作
- T4 如果 `getPage()` 返回 null，`nextPage()` 检查 `chapter` 是否为 null 并安全退出
- 分页协程的 `onPageReady` 也使用 `_uiState.update {}`，不会与 `nextPage()` 产生丢失更新
- reflow 防抖（Phase 1.6）确保快速拖拽滑块时只触发一次 clearBook

**场景：翻页动画中后台录制完成**

```
时间线:
  T0: 用户开始翻页拖拽 → delegate.state = DRAGGING
  T1: applySnapshot → invalidateAllPages → submitRenderTask（后台线程）
  T2: 后台线程开始录制 current/target 页的 shell/content/overlay
  T3: 用户松手 → delegate.startAnimation()
  T4: onDraw() → recordComposite() → delegate.onDraw(canvas, current.composite, target.composite)
```

**重构后的处理：**
- T1 的 `submitRenderTask` 携带 generation token
- T2 的后台录制使用 PaintSnapshot（不可变），主线程改主题不影响正在录制的任务
- T4 的 `recordComposite` 在主线程执行，调用 `CanvasRecorderLocked.draw()` 获取锁
- 如果 T2 的后台录制尚未完成（锁被持有），`recordIfNeeded` 会跳过录制，使用旧内容——不会崩溃，只是可能显示一帧旧内容
- `CanvasRecorderLocked` 的锁保证录制和绘制不会同时进行

### 结论

重构后的模型是**协作式并发**，不是完全串行：
- **主线程是 recorder 和 UI 状态的唯一写入者**——所有变更在这里发生
- **后台线程是纯读取者**——只消费不可变快照，不修改共享状态
- **StateFlow 原子更新**——多个写入者通过 CAS 循环保证不丢失更新
- **generation token**——过期后台任务被丢弃，不会污染当前帧

这个模型比完全串行更高效（后台录制不阻塞 UI），比当前的共享可变状态更安全（无竞态、无 SIGSEGV）。
