# 33 - CanvasRecorder 架构缺陷分析与治本方案

> 编写时间：2026-06-15
> 更新时间：2026-06-15
> 范围：`core/recorder/`、`core/reader/engine/cache/PageBitmapCache`、`core/reader/model/TextPage`
> 问题现象：快速切换页眉页脚可见性、边距或章节内容时，偶发闪退或短暂绘制旧内容。

---

## 0. 结论

当前问题不能只按“加锁”处理。`CanvasRecorderLocked` 只能串行化同一个 recorder 实例，无法修复跨 recorder 共享对象池、后台任务过期、以及 UI 线程图形宿主对象跨线程使用的问题。

治本方向：

1. 先止血：移除 `PicturePool`，禁止 `recycle()` 后复活，统一改为终态 `dispose()`。
2. 再收边界：`RenderNode` 只允许在 UI 线程创建、录制、绘制；后台线程只允许生成线程无关的录制结果。
3. 最后拆模型：`TextPage` 只保留分页模型，所有 recorder/cache state 移入独立的 `PageRenderState`。

---

## 1. 官方资料结论

### 1.1 Picture 是强状态录制对象

`Picture.beginRecording(width, height)` 和 `Picture.endRecording()` 必须成对出现。AOSP `Picture.java` 内部维护 `mRecording` 状态，重复 begin 会抛出：

```java
throw new IllegalStateException("Picture already recording, must call #endRecording()");
```

结论：`Picture` 不适合做进程级全局对象池。对象池会把未正确收尾的 recording 状态传播到另一个 recorder 实例，导致“锁住 A，污染 B”的跨实例故障。

参考：
- Android `Picture` API: <https://developer.android.com/reference/android/graphics/Picture>
- AOSP `Picture.java`: <https://android.googlesource.com/platform/frameworks/base/+/android-9.0.0_r8/graphics/java/android/graphics/Picture.java>

### 1.2 RenderNode 是 UI 线程图形宿主

Android 官方 `RenderNode` 文档明确指出：`RenderNode` 不是线程安全对象；在自定义 `View` 中使用时，必须只在 UI 线程使用。

当前 `CanvasRecorderApi29Impl` 在 `PageBitmapCache` 后台线程中调用 `RenderNode.beginRecording()` / `endRecording()`，随后在 `ReaderCanvasView.onDraw()` UI 线程 `drawRenderNode()`。这越过了 `RenderNode` 的线程边界。`ReentrantLock` 只能避免同时访问，不能把非线程安全对象变成可跨线程对象。

结论：`RenderNode` 不能作为后台录制容器。它只能作为 UI 线程 display-list cache。

参考：
- Android `RenderNode` API: <https://developer.android.com/reference/kotlin/android/graphics/RenderNode>

---

## 2. 当前实现快照

### 2.1 类关系

```
TextPage
├── canvasRecorder: CanvasRecorderLocked(CanvasRecorderApi29Impl)
├── contentRecorder: canvasRecorder alias
├── shellRecorder: CanvasRecorderLocked(CanvasRecorderApi29Impl)
├── overlayRecorder: CanvasRecorderLocked(CanvasRecorderApi29Impl)
└── compositeRecorder: CanvasRecorderLocked(CanvasRecorderApi29Impl)

PageBitmapCache
└── renderThread: SingleThreadExecutor("ShuLi-PageRender")
    └── doRecordPage()
        ├── page.shellRecorder.recordIfNeeded(...)
        ├── page.canvasRecorder.recordIfNeeded(...)
        └── page.overlayRecorder.recordIfNeeded(...)
```

### 2.2 已有保护

当前代码已有一些防御，不应在方案中重复设计：

- `TextPage` 已经是普通 `class`，不是 `data class`；`equals/hashCode` 使用 identity。
- `ReaderRenderOrchestrator` 已经有 `generation`，用于渲染事务过期校验。
- `PageBitmapCache.doRecordPage()` 已有 chapter/range 校验，防止错章 content 录入当前 page。
- `CanvasRecorderLocked.endRecording()` 当前不再因 `recycled` 直接跳过底层 `endRecording()`。
- `CanvasRecorderApi29Impl.flushRenderNode()` 当前已经对 `renderNode` / `picture` 做 null 早退。

### 2.3 仍然存在的边界错误

| 问题 | 当前代码 | 风险 |
|------|----------|------|
| `Picture` 全局池化 | `CanvasRecorderApi23Impl` / `Api29Impl` companion object 持有 `picturePool` | recording 状态跨 recorder 污染 |
| `RenderNode` 后台线程录制 | `PageBitmapCache` 后台线程触发 `RenderNode.beginRecording()` | 违反官方线程边界 |
| `recycle()` 可复活 | `CanvasRecorderLocked.beginRecording()` 会把 `recycled=false` | 生命周期不是终态，状态机复杂 |
| recorder 挂在 model 上 | `TextPage` / `TextLine` 直接持有 recorder | 分页模型与 UI 缓存生命周期耦合 |
| 后台任务不可取消 | `PageBitmapCache` 单线程 executor 只排队，不校验任务 token | 旧任务可能录入已过期页面 |

---

## 3. 缺陷分析

### 3.1 缺陷一：PicturePool 导致跨实例状态污染

**严重度：P0**

`CanvasRecorderLocked` 的锁粒度是单实例：

```
Recorder A lock 保护 A.delegate
Recorder B lock 保护 B.delegate
PicturePool 是 A/B 共享的全局资源
```

因此只要一个 `Picture` 在未完成 `endRecording()` 的情况下进入池，另一个 recorder 就可能取到脏对象。后续 `beginRecording()` 直接触发 `Picture already recording`。

当前代码在 `beginRecording()` 中尝试先 `pic.endRecording()`，失败后再从池里取新对象。这是防御性补丁，但它仍然把疑似异常的 `Picture` 放回同一个池，不能从根上切断污染。

### 3.2 缺陷二：RenderNode 跨线程使用

**严重度：P0**

`PageBitmapCache` 的设计目标是后台预渲染三页。但 `CanvasRecorderApi29Impl.endRecording()` 会调用 `flushRenderNode()`，而 `flushRenderNode()` 会执行：

```kotlin
val rc = renderNode.beginRecording()
rc.drawPicture(picture)
renderNode.endRecording()
```

当这条链路由 `ShuLi-PageRender` 执行时，`RenderNode` 录制发生在后台线程；随后 UI 线程在 `draw()` 中消费同一个 `RenderNode`。这违反 `RenderNode` 的线程约束。

这也是“加锁无效”的第二个原因：锁只能串行化访问，不能改变 Android 图形对象的合法线程。

### 3.3 缺陷三：recycle 语义不是终态

**严重度：P1**

当前 `CanvasRecorder.recycle()` 同时承担三件事：

1. 结束可能进行中的录制。
2. 释放底层图形资源。
3. 标记后续 `draw()` 跳过。

但 `CanvasRecorderLocked.beginRecording()` 又允许从 recycled 状态“复活”。这让 recorder 生命周期变成：

```
IDLE -> RECORDING -> IDLE -> RECYCLED -> RECORDING
```

`RECYCLED -> RECORDING` 是危险边。资源释放后的对象被复用，会迫使实现层写大量“如果为空就重建、如果异常就吞掉”的补丁。KISS 角度上，释放后终态更简单：

```
NEW -> RECORDING -> READY -> DISPOSED
DISPOSED 后任何 record/draw 都是 no-op 或明确失败
```

### 3.4 缺陷四：过期后台任务缺少事务令牌

**严重度：P1**

项目已有 `ReaderRenderOrchestrator.generation`，但 `PageBitmapCache.submitRenderTask()` 没有携带和校验 generation。快速切换设置时，旧任务仍可能在后台队列中执行。

现有 chapter/range 校验能防止“错章内容录入”，但不能判断“这个任务是否仍属于当前渲染事务”。

### 3.5 缺陷五：TextPage 持有渲染缓存

**严重度：P2**

`TextPage` 是分页结果，但当前还持有：

- page-level recorder
- line-level recorder
- shell/content/overlay/composite 缓存状态

这导致模型生命周期、缓存生命周期、View 生命周期纠缠。结果是：

- 章节缓存可能间接持有图形资源。
- 旧页面回收需要 View 根据引用身份判断。
- `TextLine.copy()`、缓存复用、reflow 都容易和 transient recorder 产生隐性耦合。

### 3.6 附：代码审查额外发现

| 发现 | 说明 |
|------|------|
| `TextLine.canvasRecorder` 是僵尸代码 | 该字段存在且参与 `invalidate()` / `recycle()` 遍历，但从未被创建或用于实际绘制。是模型与缓存耦合的后遗症。PR-5（模型拆分）时应一并清理。 |
| `contentRecorder` 是 `canvasRecorder` 的 alias | `TextPage` L170：`val contentRecorder: CanvasRecorder = canvasRecorder`。代码中两个名字并存容易造成"有 5 个独立 recorder"的错觉，实际只有 4 个独立实例（shell / canvas / overlay + alias）。 |
| `CanvasRecorderLocked.recycle()` 保留锁不置 null | 注释 L91："不再置 lock = null：后续录制调用仍需锁来序列化，否则会和 render 线程竞速"。legado 原版在 recycle 时丢弃锁，ShuLi 保留锁以支持复活。这恰好印证了"recycle 可复活"问题的严重性：复活机制已经迫使代码做了一系列补丁式调整。 |

---

## 4. 治本架构

### 4.1 线程边界

```
后台线程 ShuLi-PageRender
└── 允许：
    ├── 校验 generation / pageKey / layerKey
    ├── 生成 Picture 或软件 Bitmap fallback
    └── 标记 UI 线程需要刷新

UI 线程 ReaderCanvasView.onDraw
└── 允许：
    ├── 创建 / 更新 RenderNode
    ├── drawRenderNode()
    ├── drawPicture()
    └── drawBitmap()
```

`RenderNode` 不再出现在后台线程调用栈中。

### 4.2 分层缓存

```
PageRenderState
├── pageKey: PageKey
├── generation: Long
├── shell: LayerState
├── content: LayerState
├── overlay: LayerState
└── composite: LayerState

LayerState
├── key: RenderKey
├── picture: Picture?              // 后台可录制
├── renderNode: RenderNode?        // UI 线程专属
├── renderNodeDirty: Boolean
└── disposed: Boolean
```

`Picture` 是跨线程录制结果；`RenderNode` 是 UI 线程加速缓存。二者不是同一个生命周期。

### 4.3 模型边界

```
TextPage
├── startCharOffset
├── endCharOffset
├── chapterIndex
├── pageIndex
├── pageSize
├── lines
└── columns

PageRenderStateStore
├── getOrCreate(pageKey)
├── invalidate(pageKey, layer)
├── disposeUnused(activePageKeys)
└── clear()
```

`TextPage` 不再持有任何 `CanvasRecorder`。`TextLine.canvasRecorder` 也应移出模型，改由 `PageRenderState.lineStates` 管理。

---

## 5. 分阶段实施

### 5.1 P0：止血

目标：先消除当前崩溃和最明显的线程越界。

任务：

1. 删除 `PicturePool`，`CanvasRecorderApi23Impl` / `CanvasRecorderApi29Impl` 每个实例自持 `Picture`。
2. `CanvasRecorderApi29Impl` 不在后台线程 flush `RenderNode`。
3. `CanvasRecorder.recycle()` 改名或收敛为 `dispose()`，释放后不再复活。
4. `CanvasRecorderLocked.beginRecording()` 不再把 `recycled=false` 当作复活入口。
5. `CanvasRecorderExtensions.record()` 成为主要录制入口，逐步收窄公开 `beginRecording()` / `endRecording()` 使用范围。

建议最小改动：

```kotlin
class CanvasRecorderApi29Impl : BaseCanvasRecorder() {
    private var picture: Picture? = null
    private var renderNode: RenderNode? = null
    private var renderNodeDirty = true

    override fun beginRecording(width: Int, height: Int): Canvas {
        checkNotDisposed()
        val pic = Picture()
        picture = pic
        renderNodeDirty = true
        return pic.beginRecording(width, height)
    }

    override fun endRecording() {
        picture?.endRecording()
        markClean()
    }

    override fun draw(canvas: Canvas) {
        val pic = picture ?: return
        if (canvas.isHardwareAccelerated) {
            val node = renderNode ?: RenderNode("CanvasRecorder").also { renderNode = it }
            if (renderNodeDirty) {
                val rc = node.beginRecording()
                rc.drawPicture(pic)
                node.endRecording()
                renderNodeDirty = false
            }
            canvas.drawRenderNode(node)
        } else {
            canvas.drawPicture(pic)
        }
    }
}
```

说明：上面是方向示意。实际实现需确保 `RenderNode` 的创建和 flush 只发生在 UI 线程。

### 5.2 P1：收边界

目标：把过期任务、线程规则、生命周期规则变成显式契约。

任务：

1. `PageBitmapCache.submitRenderTask()` 增加 `generation` 参数。
2. 任务开始前、每层录制前、任务结束 postInvalidate 前都校验 generation。
3. 复用现有 `ReaderRenderOrchestrator.generation`，不要在 `TextPage` / recorder 上再引入第二套 generation。
4. 新增 `PageRenderTaskToken`：

```kotlin
data class PageRenderTaskToken(
    val generation: Long,
    val pageKey: PageKey,
    val layerKey: RenderKey,
    val width: Int,
    val height: Int,
)
```

5. `CanvasRecorderFactory.create(locked = false)` 改成显式角色：

```kotlin
enum class RecorderThreadPolicy {
    UiOnly,
    BackgroundPictureOnly,
    SoftwareBitmap
}
```

布尔参数会隐藏线程语义，应逐步淘汰。

### 5.3 P2：模型拆分

目标：让分页模型、渲染缓存、View 生命周期三者解耦。

任务：

1. 新增 `PageKey`，至少包含：
   - `bookId`
   - `chapterIndex`
   - `pageIndex`
   - `startCharOffset`
   - `endCharOffset`
   - `layoutKey`

2. 新增 `PageRenderState`，接管 `TextPage` 当前的四个 recorder：
   - shell
   - content
   - overlay
   - composite

3. `ReaderCanvasView` 不再直接回收 `TextPage.recycleRecorders()`，改为：

```kotlin
renderStateStore.disposeUnused(activePageKeys)
```

4. `TextLine.canvasRecorder` 移入 `LineRenderState`，避免 line model 持有图形资源。

### 5.4 P3：按 key 驱动失效

目标：从 dirty flag 过渡到 render key diff。

当前：

```kotlin
page.shellRecorder.invalidate()
page.canvasRecorder.invalidate()
page.overlayRecorder.invalidate()
```

目标：

```kotlin
state.shell.updateKey(shellKey)
state.content.updateKey(contentKey)
state.overlay.updateKey(overlayKey)
```

只有 key 变化才重录。这样能减少“人为忘记 invalidate”或“误 invalidate 导致后台任务风暴”的概率。

---

## 6. 推荐 PR 顺序

| PR | 内容 | 目标 |
|----|------|------|
| PR-1 | 删除 `PicturePool`，每实例自持 `Picture` | 立即消除 recording 状态跨实例污染 |
| PR-2 | `Api29` 后台不 flush `RenderNode`，UI 线程 lazy flush | 修复官方线程边界问题 |
| PR-3a | 禁止 `beginRecording()` 中的 `recycled = false` 复活（一行改动） | 立即降低后续重构风险 |
| PR-3b | 完整 `dispose()` 终态语义，重命名 `recycle()` → `dispose()` | 简化生命周期 |
| PR-4 | `PageBitmapCache` 接入 generation token | 丢弃过期后台任务 |
| PR-5 | 抽 `PageRenderStateStore`，同时清理 `TextLine.canvasRecorder` 僵尸字段 | recorder 从 `TextPage` 脱离 |
| PR-6 | render key 驱动分层失效 | 收敛 dirty flag |

**PR-3a 最小改动示意：**

```kotlin
// CanvasRecorderLocked.beginRecording()
override fun beginRecording(width: Int, height: Int): Canvas {
    check(!recycled) { "Cannot record: recorder is disposed" }
    lock.lock()
    // 删除：recycled = false  ← 这行是复活入口
    return delegate.beginRecording(width, height)
}
```

**PR-4 极简实现示意：**

```kotlin
// PageBitmapCache.submitRenderTask()
fun submitRenderTask(..., generation: Long = orchestrator.generation) {
    renderThread.execute {
        if (generation != orchestrator.generation) return@execute  // 一行校验
        // ... existing recording logic
    }
}
```

**PR 依赖关系：**

```
PR-1 (PicturePool)  ──┐
                      ├──→ PR-3a (禁止复活) ──→ PR-3b (dispose 终态)
PR-2 (RenderNode)   ──┘         │
                                ↓
                          PR-4 (generation) ──→ PR-5 (模型拆分) ──→ PR-6 (key 驱动)
```

PR-1 和 PR-2 可并行开发，修改不同代码路径。PR-3a 紧随其后（一行改动）。PR-4 可与 PR-3b 并行。

---

## 7. 触发场景

### 7.1 崩溃机制

所有闪退都发生在同一个路径：

```
ShuLi-PageRender 线程:
PageBitmapCache.submitRenderTask()
  → renderThread.execute { ... }
    → doRecordPage()
      → page.shellRecorder.beginRecording()  // CanvasRecorderLocked
        → delegate.beginRecording()          // CanvasRecorderApi29Impl
          → init()                           // 从池中 obtain()
          → pic.beginRecording()             // 💥 Picture already recording
```

**触发条件三要素：**

1. **recycle 发生** — 旧 TextPage 的 recorder 被回收，Picture 放回池中
2. **复用发生** — 新 TextPage 的 recorder 从池中拿到"脏" Picture
3. **录制发生** — 后台渲染线程调用 beginRecording()

### 7.2 触发操作分类

#### A. 翻页操作

翻页时 `ReaderCanvasView.setPageInternal()` 会回收不再引用的旧页面 recorder：

```kotlin
// ReaderCanvasView.kt:324-338
if (changed) {
    val oldCurrent = currentPage
    val newPages = setOfNotNull<Any>(page, next, prev)
    if (oldCurrent != null && oldCurrent !== page && oldCurrent !in newPages) {
        oldCurrent.recycleRecorders()  // ← 回收旧页面的 recorder
    }
    // ... nextPage, prevPage 同理
}
```

| 操作 | 触发链路 | 风险 |
|------|----------|------|
| 正常翻页（滑动/音量键） | `turnPage()` → `setPageInternal()` → `recycleRecorders()` | 中 |
| 跨章翻页 | 同上 + `chapterSwitched` 强制 invalidate | **高** |
| 快速连续翻页 | 多次 `setPageInternal()` 叠加 | **高** |
| 翻页动画中切回书架 | 动画未完成就 `releaseReaderResources()` | 中 |

**跨章翻页风险更高**的原因：跨章时不仅回收旧页面 recorder，还会强制 invalidate 新页面 recorder（第 343-346 行），增加了一次 `beginRecording()/endRecording()` 调用。

#### B. 设置调整操作

任何触发 `reflow = true` 的设置变化都会导致 TextPage 重建：

```kotlin
// ReaderSettingsManager.kt 中 reflow = true 的设置：
updatePrefs(..., reflow = true)  →  reflowCurrentChapter()
  → cacheManager.clearBook()    →  全部页面重建
    → 旧 TextPage 的 recorder 被 recycle
    → 新 TextPage 的 recorder 从池中 obtain
```

| 设置项 | 操作 | 风险 |
|--------|------|------|
| 字号 | 拖拽滑块（连续触发 reflow） | **高** |
| 行间距 | 拖拽滑块 | **高** |
| 段间距 | 拖拽滑块 | **高** |
| 缩进 | 拖拽滑块 / 切换单位 | 中 |
| 左右边距 | 拖拽滑块 | 中 |
| 上下边距 | 拖拽滑块 | 中 |
| 字间距 | 拖拽滑块 | 中 |
| 页眉可见性 | 下拉菜单切换 | **高** |
| 页脚可见性 | 下拉菜单切换 | **高** |
| 章节标题对齐 | 切换选项 | 中 |
| 标题大小偏移 | 切换选项 | 中 |
| 标题上下边距 | 切换选项 | 中 |
| 中文转换 | 切换选项 | 中 |
| 中文排版优化 | 切换开关 | 中 |
| 盘古空格 | 切换开关 | 中 |
| 底部对齐 | 切换开关 | 中 |
| 最大页面宽度 | 调整 | 中 |
| 移除空行 | 切换开关 | 中 |
| 精简标题 | 切换开关 | 中 |
| 保留原缩进 | 切换开关 | 中 |
| EPUB 覆盖样式 | 切换开关 | 中 |
| 翻页动画类型 | 切换选项 | 低 |
| 翻页动画速度 | 切换选项 | 低 |

**字号/行间距/页眉页脚可见性风险最高**的原因：用户倾向于快速连续拖拽或连续切换，每次操作都触发 reflow + recorder 回收，大幅增加命中"脏" Picture 的概率。

#### C. 其他操作

| 操作 | 触发链路 | 风险 |
|------|----------|------|
| 切换主题/字体 | `invalidateAllRecorders()` → 全部 recorder 失效 | 中 |
| 进入/退出沉浸模式 | 视口变化 → reflow | 低 |
| 屏幕旋转 | 视口变化 → reflow | 低 |
| 返回书架再进入 | `releaseReaderResources()` → 全部回收 | 低 |

### 7.3 间歇性原因

- 取决于 GC 时机和线程调度
- Picture 对象池容量 64，只有在池中恰好拿到"脏"对象时才崩溃
- 快速连续操作增加了命中概率
- 后台渲染线程 (`ShuLi-PageRender`) 与主线程的时序竞争

---

## 8. 测试验证

### 8.1 单元 / instrumentation 测试

1. `Picture` 不再池化：连续创建、record、dispose 多个 recorder，不共享 `Picture`。
2. `dispose()` 后：
   - `draw()` no-op；
   - `record()` 明确失败或 no-op；
   - 不允许复活。
3. `PageBitmapCache` generation 过期任务不会录制，也不会 `postInvalidate()`。
4. `CanvasRecorderApi29Impl` 的 `RenderNode` flush 只在 UI 线程触发。
5. `TextPage` 不再持有 recorder 后，章节缓存不再间接持有图形资源。

### 7.2 手工验证

1. 快速连续切换页眉页脚可见性 20 次。
2. 快速拖拽页眉上边距 / 页脚下边距滑块。
3. 设置切换后立即翻页。
4. 后台录制过程中返回书架再进入。
5. 低内存情况下反复进出阅读页。
6. 跨章翻页时不闪现上一章内容。

---

## 9. 原则应用

| 原则 | 应用 |
|------|------|
| KISS | 删除强状态对象池和 recycle 复活逻辑，减少 try-catch 式补丁 |
| YAGNI | 不为 `TextPage` / recorder 额外增加 generation，复用现有渲染事务 generation |
| DRY | shell/content/overlay/composite 统一走 `LayerState` |
| SRP | `TextPage` 只负责分页模型，`PageRenderState` 只负责渲染缓存 |
| DIP | `PageBitmapCache` 依赖 render task token / state store，不直接依赖具体图形宿主 |

---

## 10. 风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| PR-1 删除 PicturePool 后 GC 压力增大 | 低 | 录制频率低（翻页、设置变更），Picture 是轻量对象。压测 20 次快速切换应能验证 |
| PR-2 lazy RenderNode flush 导致首帧延迟 | 低 | `beginRecording` + `drawPicture` + `endRecording` 在 HW 加速 Canvas 上极快（display list 操作），用户无感 |
| PR-3a 禁止复活后旧代码调用 `beginRecording` 崩溃 | 中 | 需要审计所有 `beginRecording` 调用点，确保没有路径在 dispose 后调用。当前只有 `CanvasRecorderExtensions.record()` 是主要入口，收敛度已经很高 |
| PR-4 generation 校验导致页面"空白" | 低 | 过期任务被丢弃后，UI 线程的 `onDraw` 会触发 fallback 同步录制（`ReaderCanvasView.onDraw()` L710-722 已有此逻辑），用户最多看到一帧旧内容，不会空白 |

---

## 11. 不建议采用的方案

1. 继续加锁：无法修复跨实例对象池污染，也无法让 `RenderNode` 合法跨线程。
2. 给每个 recorder 加 generation：会和 `ReaderRenderOrchestrator.generation` 形成双版本源。
3. 让 `recycle()` 自动复活：生命周期复杂度高，后续仍需大量防御性代码。
4. 保留 `PicturePool` 但增强清理：无法证明池内对象一定脱离 recording 状态，收益小于风险。
5. 直接把 `TextPage` 改成不可变 data class 并继续挂 recorder：会制造 `copy()` 和 transient 资源的新问题。
