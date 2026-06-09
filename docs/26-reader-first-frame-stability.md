# 26 - 阅读器首帧稳定性专项

## 1. 背景

当前阅读页已经具备分页、Canvas 渲染、页眉页脚、TTS、选区、笔记、缓存和多种阅读设置。但近期暴露出一类共同问题：

- 打开小说首帧使用默认 Canvas 状态，随后才应用持久化阅读设置，出现视觉跳变。
- 文本对齐、边距、主题等设置在退出重进时可能先显示旧状态，再刷新到正确状态。
- Compose 副作用、ViewModel 状态、Canvas 画笔、分页缓存、行级 recorder 之间存在时序缝隙。

这些问题不是单个设置项的 bug，而是渲染状态更新路径分散导致的系统性问题。

## 2. 项目阶段与约束

### 2.1 当前阶段

- 阶段：快速开发期（pre-release）
- 发布状态：尚未发布，无外部用户
- 破坏性改动：允许
- 重构：鼓励

### 2.2 设计取向

因此本专项不以“最小补丁”和“兼容旧路径”为目标，而以建立稳定、可测、可扩展的阅读渲染管线为目标。

允许进行以下破坏性调整：

- 重命名或删除不再合理的 Canvas setter。
- 调整 ReaderViewModel 与 ReaderCanvasView 的交互边界。
- 重建缓存 key 与布局 hash。
- 改造 ReaderCanvasEffects，使其只负责真正的生命周期副作用。
- 引入新的渲染状态模型与 diff 机制。

## 3. 问题诊断

### 3.1 当前问题形态

当前渲染状态散落在多个入口：

- `ReaderScreen` 中的 `AndroidView.factory/update`
- `ReaderCanvasEffects` 中的多个 `LaunchedEffect`
- `ReaderSettingsManager` 中的设置更新
- `ReaderPreferenceMonitor` 中的 DataStore 监听
- `ReaderCanvasView` 的多个 `setXxx` / `updatePaintSnapshot`
- `CanvasVisualParamsManager` 的内部更新
- `ChapterPaginationCoordinator` 的分页与缓存

这些入口各自更新一部分状态，导致无法保证以下顺序：

```text
读取持久化偏好
-> 解析 ResolvedReaderSettings
-> 构建 ReaderLayoutInput
-> 分页当前页
-> 创建 Canvas
-> 应用真实渲染状态
-> 设置当前页
-> 首次录制当前页
-> 首帧绘制
```

一旦 Canvas 先 `setPage()`，再异步应用 `textAlign`、主题、字号、页眉脚等设置，就会出现首帧闪动。

### 3.2 根因

根因不是 DataStore 未保存，也不是单个 Canvas 方法失效，而是：

1. 缺少统一的渲染状态快照。
2. 缺少状态 diff 与失效策略。
3. 缺少首帧同步应用流程。
4. 布局、内容、壳层、覆盖层失效边界不清。
5. 缓存 key 未完整覆盖所有影响布局或录制结果的输入。

### 3.3 当前性能基线

重构前必须采集当前基线，否则无法衡量改善幅度。建议在 Debug build、代表机型（如 Pixel 7 / Redmi Note 12）上采集：

| 指标 | 采集方法 | 目标 |
| --- | --- | --- |
| 首帧 Canvas 创建到所有 setter 完成 | `applyInitialReaderCanvasState` 前后打点 | 记录当前值 |
| 首帧 `initialSync.await()` 到首帧绘制 | `openBook` 开始到 `onDraw` 首次执行 | 记录当前值 |
| TTS 每句高亮 content 重录耗时 | `setTtsActiveRange` 到 `submitRenderTask` 完成 | 记录当前值 |
| 切换 JUSTIFY 对齐到渲染完成 | `setTextAlign` 到 `onDraw` | 记录当前值 |
| 横竖屏切换缓存命中率 | `CacheManager.stats()` 前后对比 | 记录当前值 |
| 切换预设到渲染完成 | `applyPreferencesFromPreset` 开始到 `onDraw` | 记录当前值 |

基线数据采集后写入本章节，验收时逐项对比。如果某项指标在重构后反而劣化，需要定位原因。

## 4. 目标

### 4.1 功能目标

1. 打开任意书籍时，首帧必须使用真实持久化偏好，不允许先显示默认状态再刷新。
2. 修改阅读设置时，只触发必要级别的更新：重绘、重录、重排或重建缓存。
3. 退出阅读页再打开，字号、字体、边距、对齐、主题、页眉脚首帧即正确。
4. 翻页时 next/prev 页面不得复用旧 Paint、旧主题或旧 recorder 状态。
5. TTS、选区、笔记变化不得强制重录正文。

### 4.2 工程目标

1. 渲染状态只有一个主入口。
2. Canvas 不再依赖默认值推断真实状态。
3. 副作用职责收敛：生命周期副作用与渲染状态同步分离。
4. 缓存 key 与布局 hash 可解释、可测试。
5. 新增单元测试覆盖 diff 策略和首帧调用顺序。

## 5. 非目标

本专项不直接实现以下功能：

- 新的阅读设置项。
- 新的翻页动画。
- PDF 支持。
- AI 总结或推荐。
- 大范围 UI 视觉重设计。

但本专项应为上述能力提供更稳定的渲染基础。

## 6. 总体方案

建立统一渲染管线：

```text
ResolvedReaderSettings
    |
    v
ReaderLayoutInput
    |
    v
ReaderPageState + ReaderOverlayState + SessionState
    |
    v
ReaderRenderSnapshot
    ├── PageSnapshot
    ├── LayoutSnapshot
    ├── VisualSnapshot
    ├── ShellSnapshot
    └── OverlaySnapshot
    |
    v
ReaderRenderDiff
    └── Set<InvalidationScope>
    |
    v
ReaderRenderOrchestrator
    └── owns currentSnapshot
    |
    v
ReaderCanvasView.applySnapshot(snapshot, diff)
    |
    +--> Layout Layer
    +--> Shell Layer
    +--> Content Layer
    +--> Overlay Layer
```

核心原则：

- 任何进入分页和渲染的设置，必须先合并为 `ResolvedReaderSettings`。
- 分页只消费 `ReaderLayoutInput`，不依赖 Canvas 的 `Paint` 或 View 生命周期。
- Compose 只负责把当前状态交给 Canvas，不直接分散调用多个 Canvas setter。
- Canvas 每次接收完整不可变 snapshot，而不是局部 setter。
- diff 输出失效范围集合，而不是调用方手动猜测 invalidate 级别。
- 首帧渲染前必须同步应用 snapshot。

## 7. ReaderRenderSnapshot

新增渲染快照模型，建议放置：

```text
app/src/main/java/com/shuli/reader/feature/reader/render/ReaderRenderSnapshot.kt
```

`ReaderRenderSnapshot` 不应做成一个“大而全”的扁平 data class。建议采用“主快照 + 子快照”的结构：主快照保证一次 apply 的完整性，子快照保证 diff、缓存、局部失效可以按层计算。

```kotlin
data class ReaderRenderSnapshot(
    val generation: Long,
    val page: PageSnapshot,
    val layout: LayoutSnapshot,
    val visual: VisualSnapshot,
    val shell: ShellSnapshot,
    val overlay: OverlaySnapshot,
)
```

建议子快照：

```kotlin
data class PageSnapshot(
    val bookId: Long,
    val chapterIndex: Int,
    val pageIndex: Int,
    val anchorByteOffset: Long,
    val currentPage: TextPage?,
    val nextPage: TextPage?,
    val prevPage: TextPage?,
    val contentVersion: Int,
    val pageRenderMode: PageRenderMode,
    val pageAnimType: PageDelegateFactory.PageAnimType,
    val canTurnPrev: Boolean,
    val canTurnNext: Boolean,
)

data class LayoutSnapshot(
    val input: ReaderLayoutInput,
    val layoutKey: LayoutKey,
)

data class VisualSnapshot(
    val themeColors: ThemeColors,
    val textAlign: ReaderTextAlign,
    val titleStyle: TitleStyleConfig,
    val contentKey: RenderKey,
)

data class ShellSnapshot(
    val headerSlots: SlotResolution,
    val footerSlots: SlotResolution,
    val batteryLevel: Int,
    val showProgress: Boolean,
    val headerFooterAlpha: Float,
    val shellKey: RenderKey,
)
```

#### 7.0.1 headerSlots / footerSlots 数据流

当前代码中 `ReaderCanvasView.onPageChangedSlots` 回调形成了 Canvas → ViewModel → Canvas 的反向数据流，与单向 snapshot 管线冲突：

```text
// 旧路径（应删除）
翻页 → onPageChanged → onPageChangedSlots 回调
     → viewModel.readerProgressResolver.resolveHeaderAndFooterSlots()
     → 命令式写入 Canvas
```

重构后 `headerSlots` / `footerSlots` 必须由 ViewModel 主动计算并写入 `pageState`，snapshot factory 从 `pageState` 直接读取：

```text
// 新路径（单向）
翻页 → ViewModel.handlePageDirection()
     → viewModel 计算新的 headerSlots / footerSlots
     → 写入 _pageState（ShellSnapshot 部分）
     → 下次 AndroidView.update 时 SnapshotFactory 读取
     → 构建 ShellSnapshot → diff → applySnapshot
```

实施时：
- 删除 `ReaderCanvasView.onPageChangedSlots` 回调。
- `ReaderScreen.factory` 中不再设置 `onPageChangedSlots`。
- ViewModel 的 `handlePageDirection()` 在翻页后同步计算 slots 并更新 `pageState`。

```kotlin
data class OverlaySnapshot(
    val selectedRange: SelectionRange?,
    val ttsActiveRange: SelectionRange?,
    val noteRanges: List<Pair<SelectionRange, String?>>,
    val overlayKey: OverlayKey,
)
```

注意：

- 主 snapshot 必须完整，Canvas 不再需要从多个 setter 补状态。
- 子 snapshot 必须不可变，避免 `RenderContext` 这类共享 var 在录制过程中被改写。
- snapshot 应由 `ReaderRenderSnapshotFactory` 构造，不由 `ReaderScreen` 手写拼装。
- `generation` 用于丢弃过期异步结果，避免翻页或设置变更后旧分页结果回写。
- `anchorByteOffset` 用于横竖屏、字号、边距变化后的阅读位置锚定，不能只保留旧 `pageIndex`。
- `layoutVersion` 必须进入 `ReaderLayoutInput` 和 `LayoutKey`，用于分页算法升级时废弃旧缓存。
- `contentVersion` 替代了旧的 `chapterContent: CharSequence`。章节文本可能 50KB+，`CharSequence.equals()` 是 O(n) 字符比较，每次 diff 都会触发。改为 `Int` 版本号后 diff 比较为 O(1)。版本号在章节加载或简繁转换时递增。

#### 7.1 Snapshot 构建子快照缓存

`ReaderRenderSnapshotFactory.build()` 或等价的 `buildReaderRenderSnapshot()` 在每次渲染事务中执行。如果 TTS 高亮跳动导致 `overlayState` 变化，此时只有 `OverlaySnapshot` 需要重建，其余子快照应复用上次结果。

```kotlin
class ReaderRenderSnapshotFactory {
    private var lastPage: PageSnapshot? = null
    private var lastLayout: LayoutSnapshot? = null
    private var lastVisual: VisualSnapshot? = null
    private var lastShell: ShellSnapshot? = null
    private var lastOverlay: OverlaySnapshot? = null

    fun build(
        pageState: ReaderPageState,
        settings: ResolvedReaderSettings,
        overlayState: ReaderOverlayState,
        generation: Long,
    ): ReaderRenderSnapshot {
        // 每个子快照先构建 candidate，与缓存做 equals 比较（data class 自带）
        // 相等则复用缓存实例，避免不必要的对象创建和 GC 压力
        val page = buildPageSnapshot(pageState, generation).also { lastPage = it }

        val layoutCandidate = buildLayoutSnapshot(settings)
        val layout = if (layoutCandidate == lastLayout) lastLayout!! else layoutCandidate.also { lastLayout = it }

        val visualCandidate = buildVisualSnapshot(settings)
        val visual = if (visualCandidate == lastVisual) lastVisual!! else visualCandidate.also { lastVisual = it }

        val shell = buildShellSnapshot(settings, pageState).also { lastShell = it }
        val overlay = buildOverlaySnapshot(overlayState).also { lastOverlay = it }

        return ReaderRenderSnapshot(
            generation = generation,
            page = page,
            layout = layout,
            visual = visual,
            shell = shell,
            overlay = overlay,
        )
    }
}
```

注意：变更检测在 Factory 内部通过 data class `equals()` 完成，不在 `ResolvedReaderSettings` 中添加 `layoutChanged` / `visualChanged` 等标志。这样保持 Settings 纯净，不混入变更追踪职责。

这样 TTS 高频变化时只重建 `OverlaySnapshot`（2 个字段），不会重新构造 layout/visual/page 子快照，减少 GC 压力。

## 8. ReaderRenderDiff

新增 diff 模型：

```text
app/src/main/java/com/shuli/reader/feature/reader/render/ReaderRenderDiff.kt
```

`ReaderRenderDiff` 不应使用一组扁平 boolean。建议使用失效集合表达语义，由执行器统一处理包含关系和执行顺序。

```kotlin
enum class InvalidationScope(
    /** applier 按此值升序执行 */
    val order: Int,
    /** REFLOW 发生时是否自动隐含此 scope */
    val impliedByReflow: Boolean,
) {
    PAGE_DELEGATE(0, false),
    REFLOW(1, false),
    PAGE(2, true),
    CONTENT(3, true),
    SHELL(4, true),
    OVERLAY(5, true);

    companion object {
        /** REFLOW 隐含的 scope 集合，预计算避免每次遍历 */
        val REFLOW_IMPLIED: Set<InvalidationScope> = entries.filter { it.impliedByReflow }.toSet()
    }
}

data class ReaderRenderDiff(
    val scopes: Set<InvalidationScope>,
)
```

使用 enum 而非 sealed class 的理由：

- 6 个 scope 已经覆盖核心场景（重排/页面/内容/壳层/覆盖层/翻页委托），后续新增概率很低。
- enum 构造参数自带元数据，效果与 sealed class 相同，但零反射开销。
- `EnumSet` 天然高效（位向量实现），比 `Set<sealed class>` 更轻量。
- `REFLOW_IMPLIED` 在 companion object 中预计算，REFLOW 展开时直接 `+` 即可。

applier 按 `scope.order` 排序执行，REFLOW 展开时使用预计算的 `REFLOW_IMPLIED`：

```kotlin
// applier 内部
val effectiveScopes = if (REFLOW in diff.scopes) {
    diff.scopes + InvalidationScope.REFLOW_IMPLIED
} else {
    diff.scopes
}

effectiveScopes.sortedBy { it.order }.forEach { scope ->
    when (scope) {
        PAGE_DELEGATE -> rebuildPageDelegate(snapshot)
        REFLOW -> triggerReflow(snapshot)
        PAGE -> updatePages(snapshot)
        CONTENT -> invalidateContent(snapshot)
        SHELL -> invalidateShell(snapshot)
        OVERLAY -> invalidateOverlay(snapshot)
    }
}
```

`InvalidationReason` 不进入 diff 数据模型。pre-release 阶段原因信息仅用于调试日志，由 `DiffCalculator` 内部打印，不增加 diff 的构造和测试负担：

```kotlin
enum class InvalidationReason {
    VIEWPORT_CHANGED,
    FONT_CHANGED,
    FONT_SIZE_CHANGED,
    FONT_WEIGHT_CHANGED,
    LAYOUT_SPACING_CHANGED,
    MARGIN_CHANGED,
    TEXT_CONTENT_CHANGED,
    TEXT_ALIGN_CHANGED,
    THEME_CHANGED,
    SHELL_CHANGED,
    BATTERY_CHANGED,
    SELECTION_CHANGED,
    TTS_CHANGED,
    NOTES_CHANGED,
    PAGE_CHANGED,
    ANIMATION_CHANGED,
}

// DiffCalculator 内部
private fun log(reason: InvalidationReason, scope: InvalidationScope) {
    if (BuildConfig.DEBUG) Log.d("RenderDiff", "$reason -> $scope")
}
```

后续如需性能分析或遥测，可将 reason 重新加入 diff 模型。

#### 8.1 空页面 diff 语义

首帧或章节切换时 `PageSnapshot.currentPage` 可能为 null（分页尚未完成）。diff 必须覆盖这些边界情况：

| 旧状态 | 新状态 | diff 输出 |
| --- | --- | --- |
| `currentPage == null` | `currentPage != null` | `PAGE + CONTENT + SHELL` |
| `currentPage != null` | `currentPage == null` | 不触发 invalidation（瞬态，applier 显示 lastKnownSnapshot） |
| `chapterIndex` 变化 | 任意 | `REFLOW`（新章节可能需要全量分页） |
| `currentPage` 引用相同但 `pageIndex` 变化 | — | `PAGE` |

规则：

- diff 应由旧 snapshot 与新 snapshot 计算，不由调用点手动判断。
- `REFLOW` 隐含 `PAGE + CONTENT + SHELL + OVERLAY`，由 applier 展开，不在 diff 中重复表达。
- `PAGE` 表示 current/next/prev 或章节内容变化。
- `CONTENT` 表示正文 recorder 需要重录。
- `SHELL` 表示背景、页眉、页脚、进度、电池等壳层需要重录。
- `OVERLAY` 表示选区、TTS、笔记等覆盖层需要重录。
- `PAGE_DELEGATE` 表示翻页动画委托需要重建。

## 9. 状态分类

### 9.1 需要重新分页的输入

以下变化应触发 reflow，并进入章节缓存 key / layout hash：

| 输入 | 原因 |
| --- | --- |
| `fontSize` | 改变测量宽度和行高 |
| `readingFont` | 改变字形宽度 |
| `fontWeight` | 可能改变测量结果 |
| `letterSpacing` | 改变行宽 |
| `lineSpacing` | 改变行高 |
| `paragraphSpacing` | 改变分页 |
| `marginHorizontal` | 改变可用宽度 |
| `marginVertical` | 改变可用高度 |
| `indent` | 改变首行宽度 |
| `titleStyle` | 改变首页标题占位 |
| `header.visibility` | 改变正文区域高度 |
| `footer.visibility` | 改变正文区域高度 |
| `chineseConvert` | 改变正文内容 |
| `usePanguSpacing` | 改变正文内容和宽度 |
| `useZhLayout` | 改变分行规则 |
| `bottomJustify` | 改变行 Y 坐标 |
| viewport size | 改变页面尺寸 |
| density | 改变 px 单位 |

### 9.2 只需要重录正文的输入

| 输入 | 原因 |
| --- | --- |
| `textAlign` | 改变绘制方式，不一定改变分页 |
| 正文颜色 | 改变正文视觉 |
| 标题颜色 | 改变标题视觉 |

注：为避免行级 recorder 复用造成视觉污染，可以将 `textAlign` 纳入 recorder 失效版本，或保守纳入缓存 key。

### 9.3 只需要重录壳层的输入

| 输入 | 原因 |
| --- | --- |
| header slots | 页眉内容变化 |
| footer slots | 页脚内容变化 |
| header/footer alpha | 透明度变化 |
| showProgress | 进度条显示变化 |
| batteryLevel | 电池显示变化 |
| time/date slot | 时间显示变化 |
| showHeaderLine | 页眉线变化 |
| showFooterLine | 页脚线变化 |
| backgroundColor | 背景变化 |

### 9.4 只需要重录覆盖层的输入

| 输入 | 原因 |
| --- | --- |
| selectedRange | 选区变化 |
| ttsActiveRange | TTS 高亮变化 |
| noteRanges | 笔记高亮变化 |

## 10. 分层渲染

建议将当前 recorder 拆为：

```text
TextPage
├── shellRecorder      // 背景、页眉、页脚、进度、电池
├── contentRecorder    // 正文、标题
├── overlayRecorder    // 选区、TTS、笔记
└── compositeRecorder  // 翻页动画需要时合成
```

当前已有 `shellRecorder`、`canvasRecorder`、`compositeRecorder`，建议：

- 将 `canvasRecorder` 重命名为 `contentRecorder`。
- 新增 `overlayRecorder`。
- TTS、选区、笔记不再污染正文 recorder。

破坏性重命名是允许的，且有助于降低后续维护成本。

## 11. 分时间渲染

### 11.1 T0：首帧同步阶段

必须在首帧前同步完成：

1. 读取 `ReaderDefaults` 与当前书 `BookReaderPrefs`。
2. 解析 `ResolvedReaderSettings`。
3. 构建 `ReaderLayoutInput`。
4. 使用独立 `TextMeasurer` 分页当前章节当前页。
5. 创建 `ReaderCanvasView`。
6. 构建 `ReaderRenderSnapshot`。
7. 计算 initial diff。
8. `applySnapshot(snapshot, diff)`。
9. 当前页必要层录制。
10. 绘制首帧。

该阶段只做当前页必须工作，不等待全书页数、相邻章节预加载、统计计算。

关键约束：

- `Paginator` 不得依赖 `ReaderCanvasView.textPaint`。
- 首帧 snapshot 必须已经使用最终 `ResolvedReaderSettings`。
- `ReaderCanvasView` 创建前允许完成设置解析、文本测量器构造和当前页分页。
- `applySnapshot` 内部可以调用旧 setter 作为迁移兼容，但外部不再允许直接调用 setter。

### 11.1.1 T0 fallback

首帧同步不能无限追求完整，否则会把”首帧闪动”改成”首帧卡顿”。T0 预算应按场景分别定义：

| 场景 | T0 预算 | 瓶颈 |
| --- | --- | --- |
| 冷启动打开书 | 200ms | DataStore `first()` 本身需要 50-100ms + 章节解析 |
| 热切换章节 | 100ms | 分页（缓存命中则 < 10ms） |
| 设置变更 reflow | 100ms | 当前页重新分页 |

如果 T0 超过预算：

```text
1. 优先显示 lastKnownSnapshot。
2. 如果没有 lastKnownSnapshot，显示稳定空白页或骨架页。
3. 后台继续分页和构建 snapshot。
4. 新 snapshot ready 后按 generation 校验并替换。
```

禁止使用默认 `ReaderPreferences()` 先画一帧再覆盖。fallback 可以是空白或旧页，但不能是错误设置状态。

#### 11.1.1.1 lastKnownSnapshot 来源与存储

`lastKnownSnapshot` 不应只在内存中保留，需要定义其来源和生命周期：

| 场景 | lastKnownSnapshot 来源 | fallback 行为 |
| --- | --- | --- |
| 冷启动（App 刚打开） | 无 | 显示骨架页（纯色背景 + 书名居中） |
| 热启动（从书架重新进入同一本书） | 内存中仍持有上次 snapshot | 显示旧页 |
| 切换书籍 | 无当前书的 snapshot | 骨架页 |
| 进程被杀后恢复 | 持久化摘要（见下） | 显示持久化的主题色 + 最后页码 |

退出阅读页时，将当前 snapshot 的轻量摘要持久化到 `BookCacheStore`：

```kotlin
data class SnapshotDigest(
    val bookId: Long,
    val themeColors: ThemeColors,
    val chapterIndex: Int,
    val pageIndex: Int,
    val anchorByteOffset: Long,
    val savedAt: Long,
)
```

持久化时机：

- 不能只在 `ON_DESTROY` 写入（后台杀进程时不会执行）。
- 每次稳定翻页后，延迟 1s 防抖写入（避免快速翻页时频繁 I/O）。
- 与 `ReadingProgressRepository.saveReadingProgress()` 合并为一次 Room 写入，减少 I/O 次数。
- 使用 Room 而非文件存储，避免文件 I/O 异常。

下次打开同一本书时，如果 T0 超预算，先按 `SnapshotDigest` 渲染骨架页（正确的主题色 + 章节标题），后台分页完成后替换为真实页面。这避免了"先用默认主题画一帧再覆盖"的问题。

### 11.2 T1：当前页稳定阶段

首帧后立即确认：

- current page content recorder 完成。
- shell recorder 完成。
- overlay recorder 按需完成。

如有旧页面快照，可执行 crossfade，但不能出现默认状态闪动。

### 11.3 T2：后台预渲染阶段

后台处理：

- next page 录制。
- prev page 录制。
- 相邻章节预加载。
- 页数缓存读取。

### 11.4 T3：空闲持久化阶段

延迟处理：

- 页数持久化。
- 阅读时长统计。
- 字数统计。
- 低优先级缓存清理。

### 11.5 T4：设置变更阶段

设置变化时：

1. 生成新的 `ResolvedReaderSettings`。
2. 构建新的 `ReaderLayoutInput`。
3. 生成新 snapshot。
4. 计算 diff。
5. `REFLOW`：保留旧页面快照，后台分页，新页 ready 后按 generation 替换。
6. `CONTENT`：只重录正文。
7. `SHELL`：只重录壳层。
8. `OVERLAY`：只重录覆盖层。
9. `PAGE_DELEGATE`：重建翻页委托。

### 11.6 RenderOrchestrator

`ReaderRenderOrchestrator` 应成为唯一 Snapshot Owner。Canvas 只做 renderer，不做 store。

推荐模型：

```kotlin
class ReaderRenderOrchestrator(
    private val snapshotFactory: ReaderRenderSnapshotFactory,
    private val diffCalculator: ReaderRenderDiffCalculator,
    private val applier: ReaderCanvasStateApplier,
) {
    private var currentSnapshot: ReaderRenderSnapshot? = null
    private var generation: Long = 0L

    /** 同步场景：自动递增 generation 并立即 apply */
    fun apply(canvas: ReaderCanvasView, input: ReaderRenderInput) {
        val gen = ++generation
        val nextSnapshot = snapshotFactory.build(input, generation = gen)
        val diff = diffCalculator.diff(currentSnapshot, nextSnapshot)
        applier.apply(canvas, nextSnapshot, diff)
        currentSnapshot = nextSnapshot
    }

    /** 异步场景：调用方先 reserve generation，后台任务完成后用同一 gen 提交 */
    fun reserveGeneration(): Long = ++generation

    fun applyAsync(
        canvas: ReaderCanvasView,
        input: ReaderRenderInput,
        generation: Long,
    ) {
        if (generation != this.generation) return  // 已过期
        val nextSnapshot = snapshotFactory.build(input, generation = generation)
        val diff = diffCalculator.diff(currentSnapshot, nextSnapshot)
        applier.apply(canvas, nextSnapshot, diff)
        currentSnapshot = nextSnapshot
    }

    fun isCurrent(value: Long): Boolean = value == generation
}
```

使用显式双入口而非默认参数，消除"传不传 generation"的隐式约定：

```kotlin
// 同步：翻页、设置变更、主题切换
orchestrator.apply(canvas, input)

// 异步：后台 reflow
val gen = orchestrator.reserveGeneration()
viewModelScope.launch(Dispatchers.Default) {
    val newChapter = paginator.paginate(layoutInput)
    withContext(Dispatchers.Main) {
        orchestrator.applyAsync(canvas, newInput, gen)
    }
}
```

所有 snapshot 状态只存在于 Orchestrator：

```text
ReaderRenderOrchestrator.currentSnapshot
    ↓
ReaderCanvasStateApplier.apply(...)
    ↓
ReaderCanvasView.render(snapshot)
```

`ReaderCanvasView` 可以缓存 recorder、bitmap、delegate 等渲染资源，但不能成为 snapshot 状态源。

**关键约束：Orchestrator 不持有 canvas 引用。** 每次 `apply()` / `applyAsync()` / `applyWithFallback()` 必须由调用方传入当前存活的 canvas view。原因：Compose `AndroidView` 在配置变更（横竖屏切换、暗色模式切换）时可能重建 view，Orchestrator 如果缓存旧引用会操作已 detach 的 view。

#### 11.6.1 generation：异步结果校验

`generation` 用于丢弃过期异步结果。异步场景使用 `reserveGeneration()` + `applyAsync()` 双步骤：

```kotlin
val gen = orchestrator.reserveGeneration()

viewModelScope.launch(Dispatchers.Default) {
    val newChapter = paginator.paginate(layoutInput)
    if (!orchestrator.isCurrent(gen)) return@launch  // 已过期，丢弃

    withContext(Dispatchers.Main) {
        orchestrator.applyAsync(canvas, input.copy(pageState = newPageState), gen)
    }
}
```

规则：

- 同步场景（翻页、设置变更）使用 `apply()`，自动递增 generation。
- 异步场景（后台 reflow、预渲染）使用 `reserveGeneration()` + `applyAsync()`。
- 后台分页、预渲染、缓存读取完成时必须校验 generation。
- 过期结果直接丢弃。
- `AndroidView.update` 只负责把当前 view 交给 Orchestrator，不持有 `lastSnapshot`。

#### 11.6.2 与 ViewModel 的关系

ViewModel 负责业务状态，Orchestrator 负责渲染事务：

```text
ReaderIntent
    ↓
ReaderViewModel updates page/settings/overlay state
    ↓
ReaderRenderInput
    ↓
ReaderRenderOrchestrator
    ↓
ReaderCanvasView
```

这样可以避免：

```text
Canvas 当前状态
Orchestrator 当前状态
Compose remember lastSnapshot
```

三套状态并存。

#### 11.6.3 T0 fallback 与 Orchestrator 集成

T0 预算检查（§11.1.1）应集中在 Orchestrator 中，而非分散在 ViewModel 的各个 openBook / openChapter 方法中。

```kotlin
fun applyWithFallback(
    canvas: ReaderCanvasView,
    input: ReaderRenderInput,
    fallback: ReaderRenderInput?,
    budgetMs: Long = 100,
) {
    val gen = ++generation
    val startTime = SystemClock.elapsedRealtime()

    val nextSnapshot = snapshotFactory.build(input, generation = gen)
    val elapsed = SystemClock.elapsedRealtime() - startTime

    // 超预算且有 fallback：先用 fallback 渲染骨架页，后台继续构建真实 snapshot
    if (elapsed > budgetMs && fallback != null) {
        val fallbackSnapshot = snapshotFactory.build(fallback, generation = gen)
        val diff = diffCalculator.diff(currentSnapshot, fallbackSnapshot)
        applier.apply(canvas, fallbackSnapshot, diff)
        currentSnapshot = fallbackSnapshot
        // 调用方应通过 reserveGeneration() + applyAsync() 在后台完成后替换
        return
    }

    val diff = diffCalculator.diff(currentSnapshot, nextSnapshot)
    applier.apply(canvas, nextSnapshot, diff)
    currentSnapshot = nextSnapshot
}
```

调用方式：

```kotlin
// ViewModel.openBook()
val fallbackInput = loadSnapshotDigest(bookId)?.toRenderInput()  // 持久化摘要
orchestrator.applyWithFallback(canvas, realInput, fallbackInput, budgetMs = 200)
```

这样 fallback 逻辑只存在于 Orchestrator 一处，ViewModel 只负责提供 real input 和 fallback input。

### 11.7 翻页与 reflow 锚定

横竖屏、字号、边距、字体变化后，旧 `pageIndex` 不再可靠。reflow 应使用字节偏移或字符偏移锚定当前位置。

建议：

```text
PageState
├── chapterIndex
├── pageIndex
└── anchorByteOffset
```

reflow 完成后通过 `anchorByteOffset` 定位新页，而不是直接复用旧 `pageIndex`。

## 12. 推荐文件结构

```text
feature/reader/render/
├── ReaderRenderSnapshot.kt
├── ReaderRenderDiff.kt
├── ReaderRenderSnapshotFactory.kt
├── ReaderRenderDiffCalculator.kt
├── InvalidationScope.kt
├── ReaderRenderKeys.kt
├── ReaderRenderOrchestrator.kt
└── ReaderCanvasStateApplier.kt

core/reader/
├── ReaderCanvasView.kt
├── ReaderPageRenderer.kt
├── CanvasVisualParamsManager.kt
├── RenderContext.kt
├── layout/
│   ├── ReaderLayoutInput.kt
│   ├── ReaderLayoutHashFactory.kt
│   └── ReaderTextMeasurerFactory.kt
└── canvas/
    └── PageBitmapCache.kt
```

职责建议：

- `ReaderRenderSnapshotFactory`：从分层 state 与 `ResolvedReaderSettings` 构造 snapshot。
- `ReaderRenderDiffCalculator`：纯函数，适合单元测试。
- `ReaderCanvasStateApplier`：执行 snapshot 到 Canvas 的原子应用。
- `ReaderRenderOrchestrator`：唯一 snapshot owner，串行化渲染事务，管理 `currentSnapshot` 与 generation。
- `ReaderCanvasView`：持有 View 与 recorder，不理解 DataStore / ViewModel。
- `ReaderTextMeasurerFactory`：根据 `ReaderLayoutInput` 创建分页测量器，不依赖 Canvas Paint。
- `RenderContext`：改为不可变 data class，录制时传入快照。渐进路线：Phase 1-2 在 `submitRenderTask` 入口做浅拷贝快照（`renderContext.toSnapshot()`）；Phase 3 随 `applySnapshot` 管线落地后，将 `RenderContext` 全面替换为不可变 Snapshot 传入。

### 12.1 轻量实现规则

为避免过度设计，文件结构中的 `Factory` / `Calculator` / `Applier` 是职责边界，不强制每个边界都实现为独立 class。

建议：

- 如果只有一个实现、没有策略切换、没有依赖注入需求，优先使用 `internal fun` 或 `object`。
- 如果需要持有状态，例如 `ReaderRenderOrchestrator.currentSnapshot`，使用 class。
- 如果需要单元测试复杂分支，例如 diff 规则，可以使用独立 class 或 object。
- 文件名可以表达职责，但代码实现应保持 KISS，不为架构图创建空壳类。

示例：

```kotlin
internal object ReaderLayoutHasher {
    fun hash(input: ReaderLayoutInput): LayoutKey = ...
}

internal fun buildReaderRenderSnapshot(input: ReaderRenderInput): ReaderRenderSnapshot = ...
```

## 13. ReaderCanvasEffects 收敛

`ReaderCanvasEffects` 应保留：

- 亮度
- 屏幕常亮
- 生命周期暂停/恢复
- 电量广播采集

应移出：

- Paint 更新
- textAlign 更新
- theme 更新
- titleStyle 更新
- header/footer slots 更新
- pageDelegate 更新
- note/tts/selection recorder 失效

这些应进入 snapshot + diff 管线。

迁移完成后，`ReaderCanvasEffects` 不应再调用任何 `ReaderCanvasView` 渲染 setter。它只能采集运行时信号并把信号送回 ViewModel，例如电量、生命周期、屏幕常亮、亮度。

## 14. 缓存策略调整

### 14.1 分层缓存 key

缓存 key 不应继续手动维护一个大字段列表。建议拆为三类：

```text
LayoutKey   // 影响分页
RenderKey   // 影响正文/壳层录制
OverlayKey  // 影响覆盖层录制
```

### 14.1.1 LayoutKey

`LayoutKey` 应从 `ReaderLayoutInput` 自动派生。任何影响分页的字段必须先进入 `ReaderLayoutInput`，再自然进入 key。

建议包含：

- layoutVersion
- bookId
- chapterIndex
- viewport width / height
- density
- fontSize
- readingFont
- fontWeight
- letterSpacing
- lineSpacing
- paragraphSpacing
- marginHorizontal
- marginVertical
- indent
- titleStyle
- header visibility for layout
- footer visibility for layout
- chineseConvert
- usePanguSpacing
- useZhLayout
- bottomJustify

建议：

```kotlin
data class LayoutKey(
    val layoutVersion: Int,
    val inputHash: String,
)
```

`layoutVersion` 应由分页算法版本常量提供，例如：

```kotlin
const val LAYOUT_VERSION = 5
```

当标点避头尾、缩进规则、中文/日文换行、标题占位等分页算法升级时，提高 `LAYOUT_VERSION` 即可一次性废弃旧分页缓存。

`inputHash` 可由 `ReaderLayoutInput` 的稳定序列化内容计算，不建议直接使用默认 `hashCode()` 作为持久化 key。

### 14.1.2 RenderKey

`RenderKey` 只覆盖不改变分页、但会改变正文或壳层视觉的输入：

- textAlign
- textColor
- titleColor
- backgroundColor
- headerColor
- footerColor
- progressColor
- header/footer alpha
- showHeaderLine
- showFooterLine
- showProgress
- header/footer slot 样式

### 14.1.3 OverlayKey

`OverlayKey` 覆盖高频覆盖层输入：

- selectedRange
- ttsActiveRange
- noteRanges

这些变化不应污染 `LayoutKey` 和 `RenderKey`，否则会降低缓存命中率并导致正文重录。

### 14.2 Recorder 版本

建议为 recorder 增加版本字段或版本 key：

```text
shellVersion
contentVersion
overlayVersion
```

当对应版本变化时，recorder 自动失效。

这样可以避免手动遍历所有页面调用 `invalidateXxx()` 的遗漏。

版本来源：

- `contentVersion` 由 `LayoutKey + RenderKey.contentPart` 决定。
- `shellVersion` 由 `RenderKey.shellPart + ShellSnapshot` 决定。
- `overlayVersion` 由 `OverlayKey` 决定。

## 15. 实施计划

### Phase 1：Paginator 独立 TextMeasurer

目标：切断分页对 Canvas Paint 的反向依赖。

1. 新增 `ReaderLayoutInput`。
2. 新增 `layoutVersion`，并纳入 `ReaderLayoutInput`。
3. 新增 `ReaderTextMeasurerFactory`。
4. `Paginator` 从 `ReaderLayoutInput` 构造测量器，不再等待 `ReaderCanvasView.textPaint`。
5. 删除或废弃 `ReaderViewModel.syncTextMeasurerPaint(canvas.textPaint)`。
6. 单元测试覆盖不同字体、字重、字号、密度下的测量输入构造。

### Phase 2：Snapshot、Diff、LayoutKey

目标：先建立纯数据模型，保证可以独立测试。

1. 新增 `ReaderRenderSnapshot` 及子快照。
2. 新增 `InvalidationScope`、`ReaderRenderDiff`。`InvalidationReason` 仅作为 `DiffCalculator` 内部调试枚举，不进入 diff 数据模型。
3. 新增 `ReaderRenderDiffCalculator`。
4. 新增 `LayoutKey`、`RenderKey`、`OverlayKey`。
5. `ChapterCacheKey` 改为从 `ReaderLayoutInput` / `LayoutKey` 派生。
6. `LayoutKey` 必须包含 `layoutVersion`。
7. 新增 `ReaderRenderOrchestrator`，作为唯一 `currentSnapshot` owner。
8. 单元测试覆盖：
   - 字号变化 -> `REFLOW`
   - 字体变化 -> `REFLOW`
   - 字重变化 -> `REFLOW`
   - textAlign 变化 -> `CONTENT`
   - 主题变化 -> `CONTENT + SHELL`
   - 电量变化 -> `SHELL`
   - TTS range 变化 -> `OVERLAY`
   - 当前页变化 -> `PAGE`
   - 翻页动画变化 -> `PAGE_DELEGATE`

### Phase 3：Canvas 收敛为 applySnapshot

目标：从“3 条路径 × 30 个 setter”变为“1 个 Orchestrator × 1 个 applySnapshot”。

1. 新增 `ReaderCanvasView.applySnapshot(snapshot, diff)`。
2. 新增 `ReaderCanvasStateApplier`，内部按固定顺序调用旧 setter。
3. `ReaderScreen.factory` 只创建 Canvas 和绑定回调，不再应用视觉状态。
4. `ReaderScreen.update` 只把 Canvas view 与当前 render input 交给 Orchestrator。
5. `ReaderScreen.update` 不再直接 `setPage()`，改由 snapshot 驱动。
6. `ReaderCanvasEffects` 不再调用渲染 setter。
7. 将旧 setter 降级为 `internal` 或 `private`，只允许 applier 使用。

### Phase 4：TextPage 分层 recorder

目标：TTS、选区、笔记变化不再重录正文。

1. `canvasRecorder` 重命名为 `contentRecorder`。
2. 新增 `overlayRecorder`。
3. `ReaderPageRenderer` 拆为 `renderContent()`、`renderShell()`、`renderOverlay()`。
4. composite recorder 合成 shell + content + overlay。
5. `setTtsActiveRange`、选区、笔记变化只触发 overlay invalidation。

### Phase 5：ViewModel 状态拆分

目标：减少不必要 recomposition，并让 AndroidView 只观察渲染相关状态。

1. 将 `ReaderUiState` 拆为：
   - `ReaderPageState`
   - `ResolvedReaderSettings`
   - `ReaderOverlayState`
   - `ReaderSearchState`
   - `ReaderBookmarkState`
2. 新增 `ReaderIntent` 与 `ReaderViewModel.dispatch(intent)`。
3. `AndroidView.update` 只依赖 page/render snapshot。
4. Toolbar、搜索、目录、预设变化不再触发 `setPage()`。

### Phase 6：SettingsResolver 与本书覆盖

目标：让设置作用域成为一等公民。

1. 复用现有 `UserPreferences` 作为全局默认来源。
2. 新增 `BookReaderPrefs` Room 表，保存当前书稀疏覆盖。
3. 新增 `ReaderSettingsResolver`，输出 `ResolvedReaderSettings`。
4. QuickSettings 默认写入当前书覆盖。
5. 全局设置页只修改全局默认。

### Phase 7：清理旧路径

目标：删除会制造竞态的旧入口。

1. 删除 `applyInitialReaderCanvasState`。
2. 清理 `ReaderCanvasEffects` 中的视觉同步副作用。
3. 合并或替换 `ReaderPreferenceMonitor` 的多组 combine。
4. 删除重复 invalidation。
5. 更新文档、测试和调试日志。

阶段 1 到 3 是核心转变，完成后系统必须达到“唯一设置来源、唯一 Canvas 输入、独立分页测量”的状态。阶段 4 到 7 是在新架构上的性能优化和旧路径清理。

## 16. 验收标准

### 16.1 功能验收

- 打开书籍首帧不出现边距、对齐、主题、字号闪动。
- 保存 `JUSTIFY` 后退出重进，首帧即两端对齐。
- 保存自定义字体后退出重进，首帧即使用正确字体。
- 切换主题后当前页、next 页、prev 页均无旧主题残留。
- TTS 高亮跳动时正文不重录。
- 选区变化时正文不重录。
- 横竖屏切换后不复用旧尺寸页面缓存。

### 16.2 测试验收

建议新增测试：

```text
app/src/test/java/com/shuli/reader/feature/reader/render/
├── ReaderRenderDiffCalculatorTest.kt
├── ReaderRenderSnapshotFactoryTest.kt
└── ReaderCanvasStateApplierTest.kt
```

重点测试：

- `applySnapshot` 必须是 Canvas 渲染状态唯一入口。
- `ReaderRenderOrchestrator` 必须是唯一 `currentSnapshot` owner。
- `ReaderCanvasView` 不得持有 `currentSnapshot` 并作为状态源。
- 首帧 snapshot 构建必须早于 Canvas 状态应用。
- `textAlign/theme` 更新不能触发 `REFLOW`。
- overlay 变化不能触发 content recorder 失效。
- `REFLOW` 必须隐含页面、正文、壳层、覆盖层刷新。
- generation 过期的分页结果不能回写 Canvas。
- 横竖屏 reflow 后应按 `anchorByteOffset` 定位新页。

建议保留现有编译验证：

```powershell
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat :app:testDebugUnitTest
```

### 16.3 性能验收

- 首帧当前页同步录制，不等待 next/prev。
- next/prev 预渲染不得阻塞主线程。
- 仅 overlay 变化不得重录 content。
- 仅 shell 变化不得重录 content。
- reflow 期间保留旧页快照，新页 ready 后替换。

## 17. 风险与处理

| 风险 | 处理 |
| --- | --- |
| 重构范围较大 | 按依赖顺序推进，阶段 1 到 3 完成前不做 UI 大改 |
| 缓存 key 变严格导致命中率下降 | 分为 `LayoutKey` / `RenderKey` / `OverlayKey`，避免 overlay 污染分页缓存 |
| overlay 拆分引入合成问题 | 先保留 composite recorder，新增测试覆盖合成顺序 |
| 设置项分类错误 | 用 diff 单元测试固化分类 |
| 首帧同步录制增加主线程压力 | 只同步当前页，超过 T0 预算则显示 lastKnownSnapshot 或稳定空白页 |
| 后台分页结果过期 | 使用 `generation` 校验，过期结果直接丢弃 |
| 不可变 RenderContext 改造影响面大 | 先在录制入口传不可变副本，再逐步删除共享 var |

## 18. 结论

阅读器首帧稳定性应作为一次管线重构，而不是继续追加局部补丁。

推荐最终形态：

```text
ResolvedReaderSettings
-> ReaderLayoutInput
-> ReaderPageState / ReaderOverlayState / SessionState
-> ReaderRenderSnapshot(layout / visual / shell / overlay)
-> ReaderRenderDiff(Set<InvalidationScope>)
-> ReaderCanvasView.applySnapshot()
-> shell / content / overlay recorder
-> RenderTransaction 分时间执行
```

在当前 pre-release 阶段，破坏性重构成本可控，收益明确：可以系统性解决首帧闪动、旧缓存污染、设置应用顺序不稳定、分页依赖 Canvas Paint、overlay 重录正文等问题，并为后续滚动模式、双页模式、增强 TTS、笔记高亮提供稳定基础。

## 19. 阅读设置作用域模型

> **文档拆分提示**：§19-21（设置作用域模型、补齐设置项、QuickSettings 布局）属于 UI/UX 设计范畴，与渲染管线是不同关注点。建议将这三节拆为独立文档 `27-reader-settings-scope.md`，以便分别排期和验收。当前保留在本文档中仅为保持方案完整性。

首帧稳定性专项不应只处理 Canvas 渲染时序，还应同步明确”阅读页设置”和”软件整体设置”的关系。否则设置来源不清，会继续导致首帧状态、缓存 key、持久化策略难以统一。

### 19.1 推荐模型

建议将阅读设置拆成三层：

```text
ReaderDefaults           // 全局阅读默认值
    -> BookReaderPrefs   // 单本书稀疏覆盖设置
        -> SessionState  // 当前会话临时状态
            -> ResolvedReaderSettings
```

### 19.2 三层职责

| 层级 | 持久化 | 作用范围 | 示例 |
| --- | --- | --- | --- |
| `ReaderDefaults` | 是 | 新书、未单独配置的书 | 默认字号、默认行距、默认主题、默认翻页方式 |
| `BookReaderPrefs` | 是 | 当前书 | 只保存某本书相对全局默认的差异 |
| `SessionState` | 按需 | 当前阅读会话 | 当前打开的设置 Tab、TTS 播放状态、临时亮度 |
| `ResolvedReaderSettings` | 否 | 分页与渲染输入 | 合并全局、本书覆盖、会话状态后的最终设置 |

### 19.2.1 BookReaderPrefs 可空字段覆盖

`BookReaderPrefs` 不建议使用 `Map<ReaderSettingKey, ReaderSettingValue>` 做稀疏覆盖。`Map` 在 Room 中需要 TypeConverter + JSON 序列化，查询和迁移不友好。

建议使用 Room 可空字段：

```kotlin
@Entity(tableName = “book_reader_prefs”)
data class BookReaderPrefsEntity(
    @PrimaryKey val bookId: Long,
    val fontSize: Float? = null,           // null = 跟随全局默认
    val lineSpacing: Float? = null,
    val paragraphSpacing: Float? = null,
    val textAlign: String? = null,
    val readingFont: String? = null,
    val fontWeight: String? = null,
    val marginHorizontal: Float? = null,
    val marginVertical: Float? = null,
    val letterSpacing: Float? = null,
    val indent: Float? = null,
    val backgroundColor: Int? = null,      // ReaderTheme ordinal
    val pageAnimType: String? = null,
    val headerVisibility: String? = null,
    val footerVisibility: String? = null,
    val chineseConvert: String? = null,
    val bottomJustify: Boolean? = null,
    // ... 其他设置项均可空
)
```

语义：

- `null` 表示”未覆盖，跟随全局默认”。
- 用户只改当前书字号时，只写入 `fontSize` 一项，其余保持 null。
- “恢复全局默认” = 将所有字段设为 null，或直接删除该行。
- 全局默认变更后，null 字段自然跟随新默认。
- 非 null 字段保持当前书独立设置。

优势：

- 类型安全，IDE 可导航。
- Room 原生支持，无需 TypeConverter。
- `null` 语义天然表达”未覆盖”。
- 查询、迁移、调试都方便。

### 19.3 软件整体设置页

软件整体设置页应管理“默认规则”，不直接代表当前正在阅读的书：

- App 外观：语言、应用主题、应用字体。
- 书库设置：导入、去重、封面、排序。
- 同步与备份：WebDAV、自动备份、加密。
- 阅读默认值：`ReaderDefaults`。

修改软件设置页中的阅读设置时，默认只影响未来新书或没有单独配置的书。

如果需要覆盖所有已有书籍，提供明确的破坏性批量操作：

```text
将当前默认阅读设置应用到所有书籍
```

该操作必须清晰提示会覆盖每本书的独立阅读配置。

### 19.4 阅读页快捷设置

阅读页快捷设置默认修改“当前书设置”：

- 用户在阅读时调整字号、边距、对齐，通常是针对当前书源。
- 这些设置应写入 `BookReaderPrefs`。
- 面板顶部应显示当前作用域，例如：

```text
当前作用域：本书
[恢复全局默认] [保存为默认] [保存为预设]
```

### 19.5 阅读预设

阅读预设是可复用配置包：

```text
ReaderPreset
├── name
├── readerPreferences
└── createdAt / updatedAt
```

预设支持三类操作：

- 应用到当前书。
- 保存当前书设置为预设。
- 设为全局默认。

## 20. 建议补齐的阅读设置项

当前阅读设置已经覆盖字号、行距、边距、段距、缩进、字距、字体、字重、对齐、简繁、页眉页脚、进度条、TTS、预设等核心能力。后续建议补齐以下项目。

### 20.1 P0：应随本专项一起补齐

| 设置项 | 建议位置 | 说明 |
| --- | --- | --- |
| 亮度 | 阅读页快捷设置顶部或交互 Tab | 支持跟随系统 / 手动亮度。当前侧边亮度条不够可发现 |
| 沉浸模式 | 交互 Tab | 控制状态栏、导航栏、页眉页脚联动显示 |
| 设置作用域 | 面板顶部固定区 | 显示“本书 / 全局默认”，并提供保存为默认、恢复默认 |
| 触控热区 | 交互 Tab | 左/中/右点击区比例，是否启用边缘翻页 |
| 翻页方向/模式统一 | 交互 Tab | 将横向翻页、滚动、连续滚动等入口统一 |
| 当前书恢复默认 | 面板顶部或预设区 | 删除当前书覆盖，回到 `ReaderDefaults` |

### 20.2 P1：建议纳入下一轮

| 设置项 | 建议位置 | 说明 |
| --- | --- | --- |
| 页面最大宽度 | 排版 Tab | 平板、横屏时限制正文宽度，避免行过长 |
| 主题自定义 | 页面 Tab | 背景色、文字色、强调色，不只固定色块 |
| 文本净化 | 字体/文本 Tab 或二级页 | 去多余空行、章节标题清理、替换规则 |
| 中文排版细项 | 字体/文本 Tab | 标点避头尾、英文数字间距、段首缩进单位 |
| 进度显示样式 | 页面 Tab | 章节进度、全书进度、页码、百分比、进度条位置 |
| 页眉页脚字号/位置 | 页面二级页 | 当前已有部分能力，建议独立管理 |

### 20.3 P2：低频增强

| 设置项 | 建议位置 | 说明 |
| --- | --- | --- |
| 背景纹理/图片 | 页面高级设置 | 需要处理可读性和性能 |
| 自动夜间模式 | 页面高级设置 | 按时间或系统暗色切换 |
| 自动翻页 | 交互高级设置 | 与 TTS、阅读速度联动 |
| 双页模式 | 排版高级设置 | 平板/横屏优先 |
| EPUB 原样式/覆盖样式 | 文本高级设置 | EPUB 需要区别处理 |

## 21. QuickSettings 信息架构与布局建议

### 21.1 当前布局问题

当前 QuickSettings 采用：

```text
阅读主题
排版 / 样式 / 设置
```

主要问题：

1. `设置` 过于宽泛，包含页眉页脚、标题、行为、TTS、预设，信息密度过高。
2. `样式` 中混合了翻页、字体、字体导入、对齐、简繁、中文排版，分类边界不清。
3. Sheet 高度固定约 45%，设置项较多时滚动成本高。
4. 高频设置和低频管理操作混在一起，例如字体导入、预设管理、页眉脚自定义。
5. 面板没有明确显示当前设置作用域，用户无法判断修改会影响当前书还是全局默认。

### 21.2 推荐 Tab 结构

建议改为 4 个 Tab：

```text
排版 / 字体 / 页面 / 交互
```

#### 排版

放影响正文版面密度的设置：

- 字号
- 行距
- 段距
- 左右边距
- 上下边距
- 首行缩进
- 字距
- 页面最大宽度

#### 字体

放文字形态和文本处理：

- 阅读字体
- 字重
- 对齐
- 简繁
- 中文分行
- 盘古间距
- 文本净化入口

字体导入、删除字体建议移入二级“字体管理”页，不放在快捷面板主列表。

#### 页面

放页面元素与主题：

- 阅读主题
- 背景色 / 文字色
- 页眉
- 页脚
- 进度条
- 章节标题
- 页眉脚透明度
- 页眉脚自定义入口

页眉脚 slot 自定义属于复杂配置，应使用二级页或全屏 Sheet。

#### 交互

放行为设置：

- 翻页模式
- 翻页方向
- 连续滚动
- 触控热区
- 音量键翻页
- 保持亮屏
- 沉浸模式
- TTS 简要控制

TTS 的语速、音调可以放在折叠区；TTS 引擎、朗读偏好等低频项放全局设置页。

### 21.3 Sheet 布局建议

建议结构：

```text
DragHandle
作用域行：本书设置 / 全局默认
主题色块 + 亮度快捷入口
Tabs：排版 / 字体 / 页面 / 交互
TabContent
底部操作：恢复默认 / 保存为默认 / 保存为预设
```

### 21.4 高度与交互

- 默认高度建议从 `45%` 提升到 `60%`。
- 支持拖拽展开到接近全屏。
- 高频设置保留在一级。
- 低频设置使用折叠区或二级页。
- Slider 行应固定右侧数值宽度，避免数值变化导致布局跳动。
- `+` / `-` 应使用固定尺寸按钮，点击区域不小于 40dp。

### 21.5 控件排版建议

Slider 行建议统一为：

```text
标签          当前值
[-]  =========o======  [+]
```

或更紧凑：

```text
标签    [-] ===o==== [+]   16sp
```

要求：

- 标签列宽稳定。
- 数值列宽稳定。
- Slider 不因数值变化改变宽度。
- 长标签允许换行，但不挤压按钮。
- 高级项使用折叠区，避免主面板过长。

### 21.6 全局设置页布局建议

软件整体设置页中的阅读设置不应复制 QuickSettings 的全部内容。建议只保留：

- 默认阅读主题。
- 默认排版。
- 默认字体。
- 默认翻页行为。
- 阅读预设管理。
- 字体管理。
- 文本净化规则管理。

同时提供说明：

```text
这些设置作为新书和未单独配置书籍的默认阅读设置。
```

如用户需要影响所有已有书籍，使用明确的批量操作。

## 22. 对本专项实施计划的补充

基于设置作用域、独立分页测量器与 snapshot 管线，原实施计划应调整为：

1. 先让 `Paginator` 脱离 `ReaderCanvasView.textPaint`，建立 `ReaderLayoutInput`。
2. 给 `ReaderLayoutInput` 增加 `layoutVersion`，并进入 `LayoutKey`。
3. 建立 `ReaderRenderSnapshot` 子快照、`ReaderRenderDiff` 失效集合、分层 key、`ReaderRenderOrchestrator`（一起落地，避免中间态）。
4. 改造 `ReaderCanvasView.applySnapshot()`，使 Canvas 成为纯 renderer。
5. 拆分 `TextPage` recorder 为 shell / content / overlay。
6. 拆分 ViewModel StateFlow，减少 God Object 引发的 recomposition。
7. 建立 `ReaderDefaults` / 稀疏 `BookReaderPrefs` / `ResolvedReaderSettings`。
8. 清理 `ReaderCanvasEffects`、`applyInitialReaderCanvasState`、`ReaderPreferenceMonitor` 旧路径。
9. 新增 `ReaderIntent`，统一 UI、快捷键、TTS、自动翻页等入口。
10. 重构 QuickSettings UI 与全局设置页入口。

原因：

- 如果分页继续依赖 Canvas Paint，首帧链路仍然被 View 创建时序卡住。
- 如果 Canvas 仍有 30+ public setter，任何 UI 或 effect 都可能绕过 snapshot 管线。
- 如果 Orchestrator 不拥有 snapshot，Canvas / Compose / Orchestrator 会形成多状态源。
- 如果 LayoutKey 没有版本号，分页算法升级后旧缓存无法一次性废弃。
- 如果先补 UI，而没有作用域模型，会继续产生“当前书还是全局”的歧义。
- 当前 pre-release 阶段允许破坏性改动，应优先一次性理清数据模型。

## 23. 重构后的架构

本专项完成后，阅读器不应再是“Compose 多个 `LaunchedEffect` + ViewModel 多个 manager + Canvas 多个 setter”拼接出来的渲染链路，而应收敛为一条明确的单向数据流：

```text
DataStore / Database
    |
    v
ReaderSettingsResolver
    |
    v
ResolvedReaderSettings
    |
    v
ReaderViewModel
    |
    v
ReaderPageState + ResolvedReaderSettings + ReaderOverlayState
    |
    v
ReaderRenderSnapshotFactory
    |
    v
ReaderRenderSnapshot
    ├── PageSnapshot
    ├── LayoutSnapshot
    ├── VisualSnapshot
    ├── ShellSnapshot
    └── OverlaySnapshot
    |
    v
ReaderRenderDiffCalculator
    |
    v
Set<InvalidationScope>
    |
    v
ReaderRenderOrchestrator
    └── owns currentSnapshot
    |
    v
ReaderCanvasStateApplier
    |
    v
ReaderCanvasView
    |
    +--> PageDelegate
    +--> PageBitmapCache
    +--> ReaderPageRenderer
    +--> Layer Recorders
```

### 23.1 架构目标

重构后的架构目标是：

1. 设置来源唯一：任何阅读设置都先解析为 `ResolvedReaderSettings`，再进入分页和渲染。
2. 分页输入唯一：`Paginator` 只消费 `ReaderLayoutInput` 和独立 `TextMeasurer`，不依赖 Canvas Paint。
3. Canvas 输入唯一：`ReaderCanvasView` 只接收完整 `ReaderRenderSnapshot`，不再由外部零散调用 setter。
4. 失效范围可计算：所有重排、重录、刷新都由 `ReaderRenderDiffCalculator` 输出的 `Set<InvalidationScope>` 决定。
5. 首帧同步稳定：打开阅读页时，首帧前必须完成设置解析、当前页分页、snapshot 构建和 Canvas 原子应用。
6. 异步任务分级：当前页首帧优先，相邻页、页数持久化、预加载、统计延后。

### 23.2 分层结构

推荐重构为 5 层。Settings、Pagination、Content 本质上都是阅读器业务规则，合并为 Domain Layer 更容易理解和导航。

```text
UI Layer
    ReaderScreen
    ReaderOverlayPanels
    QuickSettingsSheet

Application Layer
    ReaderViewModel
    ReaderIntent / ReaderAction
    ReaderSessionController

Domain Layer
    ReaderSettingsRepository
    ReaderSettingsResolver
    ReaderDefaults
    BookReaderPrefs
    ResolvedReaderSettings
    ReaderLayoutInput
    ReaderLayoutHashFactory
    ReaderTextMeasurerFactory
    ChapterPaginationCoordinator
    Paginator

Render Orchestration Layer
    ReaderRenderOrchestrator
    ReaderRenderSnapshotFactory
    ReaderRenderDiffCalculator
    ReaderCanvasStateApplier

Canvas Engine Layer
    ReaderCanvasView
    CanvasVisualParamsManager
    PageBitmapCache
    ReaderPageRenderer
    PageDelegate
    TextPage Layer Recorders
```

依赖方向必须保持单向：

```text
UI -> Application -> Domain -> Render Orchestration -> Canvas Engine
```

反向依赖应删除：

- `ReaderCanvasView` 不依赖 `ViewModel`、`DataStore`、`ReaderScreen`。
- `Paginator` 不依赖 `ReaderCanvasView.textPaint`。
- `QuickSettingsSheet` 不直接理解 DataStore 保存细节。
- `ReaderCanvasEffects` 不再负责视觉参数同步。

### 23.3 设置层

设置层负责把“全局默认 + 当前书覆盖 + 会话状态”合并为最终设置。

建议模型：

```text
ReaderDefaults
    全局默认阅读设置，来自软件整体设置页

BookReaderPrefs
    单本书稀疏覆盖设置，来自阅读页快捷设置

SessionState
    临时会话状态，例如亮度临时值、设置面板状态、TTS 当前状态

ResolvedReaderSettings
    进入分页和渲染前的唯一最终设置
```

建议接口：

```kotlin
interface ReaderSettingsRepository {
    fun observeDefaults(): Flow<ReaderDefaults>
    fun observeBookPrefs(bookId: Long): Flow<BookReaderPrefs?>
    suspend fun updateDefaults(transform: (ReaderDefaults) -> ReaderDefaults)
    suspend fun updateBookPrefs(bookId: Long, transform: (BookReaderPrefs?) -> BookReaderPrefs?)
    suspend fun clearBookPrefs(bookId: Long)
}

class ReaderSettingsResolver {
    fun resolve(
        defaults: ReaderDefaults,
        bookPrefs: BookReaderPrefs?,
        sessionState: ReaderSessionState,
    ): ResolvedReaderSettings
}
```

`ReaderPreferences` 可以继续作为兼容模型短期保留，但重构后不应同时承担“全局默认”“本书覆盖”“最终设置”三个角色。

`BookReaderPrefs` 必须保持稀疏覆盖语义：

```text
ReaderDefaults.fontSize = 18
BookReaderPrefs.overrides = { textAlign = JUSTIFY }
ResolvedReaderSettings.fontSize = 18
ResolvedReaderSettings.textAlign = JUSTIFY
```

当前书没有覆盖的字段应继续跟随全局默认。

### 23.4 ViewModel 层

`ReaderViewModel` 重构后应从“直接协调所有细节”变为“状态机 + use case 编排器”。

推荐职责：

- 接收 UI intent，例如打开书籍、翻页、修改设置、选择文本、启动 TTS。
- 维护分层 StateFlow，而不是单体 God Object。
- 订阅 `ResolvedReaderSettings`。
- 触发分页、快照构建和持久化。
- 对外暴露只读 StateFlow。

推荐状态拆分：

```kotlin
class ReaderViewModel {
    val pageState: StateFlow<ReaderPageState>
    val preferences: StateFlow<ResolvedReaderSettings>
    val overlayState: StateFlow<ReaderOverlayState>
    val searchState: StateFlow<ReaderSearchState>
    val bookmarkState: StateFlow<ReaderBookmarkState>
}
```

拆分原则：

- `pageState`：章节、页码、current/next/prev 页面，中频变化。
- `preferences`：最终阅读设置，低频变化。
- `overlayState`：选区、TTS、笔记，高频变化。
- `searchState` / `bookmarkState`：按需收集，不参与 Canvas 首帧。
- `ReaderScreen.AndroidView.update` 只观察渲染相关 state，不因 toolbar、搜索、预设列表变化触发页面设置。

不推荐继续保留的职责：

- 从 Canvas 反向同步 `Paint` 给分页器。
- 逐个 setter 应用阅读设置。
- 同时处理 DataStore 字段转换、本书设置覆盖、预设应用和 Canvas 刷新。

当前 `ReaderSettingsManager`、`ReaderPreferenceMonitor`、`ReaderPresetManager` 可以保留名称，但职责应重新收敛：

| 当前模块 | 重构后建议 |
| --- | --- |
| `ReaderSettingsManager` | 只处理当前书设置修改 intent，写入 `BookReaderPrefs` |
| `ReaderPreferenceMonitor` | 替换为 `ReaderSettingsResolver` 的 Flow 订阅 |
| `ReaderPresetManager` | 只处理预设 CRUD 与“应用到当前书/设为默认” |
| `ChapterPaginationCoordinator` | 只消费 `ReaderLayoutInput`，不直接读取散落偏好字段 |

#### 23.4.1 ReaderIntent

UI、快捷键、蓝牙翻页器、TTS、自动翻页不应各自直接调用 ViewModel 的不同方法。建议新增统一入口：

```kotlin
sealed interface ReaderIntent {
    // 书籍与导航
    data class OpenBook(val bookId: Long) : ReaderIntent
    data class TurnPage(val direction: PageDirection) : ReaderIntent
    data class JumpToPosition(val chapterIndex: Int, val byteOffset: Long) : ReaderIntent

    // 设置
    data class UpdateSetting(val key: ReaderSettingKey, val value: ReaderSettingValue) : ReaderIntent
    data object RestoreBookDefaults : ReaderIntent

    // TTS
    data object StartTts : ReaderIntent
    data object StopTts : ReaderIntent

    // 选区
    data class SelectText(val range: SelectionRange) : ReaderIntent
    data object ClearSelection : ReaderIntent

    // 搜索
    data class Search(val query: String) : ReaderIntent
    data object ClearSearch : ReaderIntent
    data class JumpToSearchResult(val index: Int) : ReaderIntent

    // 书签 / 笔记
    data object AddBookmark : ReaderIntent
    data class AddNote(val content: String) : ReaderIntent
    data class DeleteNote(val noteId: Long) : ReaderIntent

    // UI 面板切换
    data object ToggleDirectory : ReaderIntent
    data object ToggleQuickSettings : ReaderIntent
    data object ToggleToolbar : ReaderIntent
}
```

数据流：

```text
UI / shortcut / TTS / auto page
    ↓
ReaderIntent
    ↓
ReaderViewModel.dispatch(intent)
    ↓
page/settings/overlay state
    ↓
ReaderRenderOrchestrator
```

这样后续新增输入设备或自动行为时，不会绕过设置解析、分页锚定、snapshot 和 diff 管线。

### 23.5 分页层

分页层应消费稳定的布局输入，而不是消费完整 UI 状态。

建议模型：

```kotlin
data class ReaderLayoutInput(
    val layoutVersion: Int,
    val bookId: Long,
    val chapterIndex: Int,
    val anchorByteOffset: Long,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val density: Float,
    val fontSizeSp: Float,
    val fontKey: String,
    val fontWeight: ReaderFontWeight,
    val lineSpacing: Float,
    val paragraphSpacing: Float,
    val letterSpacing: Float,
    val marginHorizontalDp: Float,
    val marginVerticalDp: Float,
    val indent: Float,
    val titleStyle: TitleStyleConfig,
    val headerVisibleForLayout: Boolean,
    val footerVisibleForLayout: Boolean,
    val chineseConvert: ChineseConvert,
    val usePanguSpacing: Boolean,
    val useZhLayout: Boolean,
    val bottomJustify: Boolean,
)
```

关键点：

- `ReaderLayoutHashFactory` 只基于 `ReaderLayoutInput` 计算 hash。
- `layoutVersion` 必须进入 `ReaderLayoutInput`，用于分页算法升级时废弃旧缓存。
- `ReaderTextMeasurerFactory` 根据 `ReaderLayoutInput` 构造分页测量器。
- `Paginator` 和 `ReaderPageRenderer` 使用同源文字规格，避免分页和绘制使用两套 Paint。
- `Paginator` 不得从 `ReaderCanvasView.textPaint` 同步测量器。
- `textAlign` 如果不改变分页，可以不进入 `ReaderLayoutInput`，但必须进入 content recorder 版本。
- 页眉页脚可见性如果影响正文区域高度，必须进入 layout hash。
- reflow 后使用 `anchorByteOffset` 重新定位页面，不直接复用旧 `pageIndex`。

### 23.6 渲染编排层

渲染编排层是首帧稳定性的核心。

推荐文件：

```text
app/src/main/java/com/shuli/reader/feature/reader/render/
├── ReaderRenderSnapshot.kt
├── ReaderRenderSnapshotFactory.kt
├── ReaderRenderDiff.kt
├── ReaderRenderDiffCalculator.kt
├── InvalidationScope.kt
├── ReaderRenderKeys.kt
├── ReaderRenderOrchestrator.kt
└── ReaderCanvasStateApplier.kt
```

职责：

| 模块 | 职责 |
| --- | --- |
| `ReaderRenderOrchestrator` | 唯一 snapshot owner，串行化渲染事务，管理 `currentSnapshot` 与 generation |
| `ReaderRenderSnapshotFactory` | 从分层 state 与 `ResolvedReaderSettings` 构建完整 snapshot |
| `ReaderRenderDiffCalculator` | 比较新旧子快照，输出 `Set<InvalidationScope>` |
| `ReaderCanvasStateApplier` | 将 snapshot 原子应用到 `ReaderCanvasView` |
| `ReaderRenderKeys` | 定义 `LayoutKey`、`RenderKey`、`OverlayKey` |

pre-release 阶段应引入独立 `ReaderRenderOrchestrator`。它是唯一 snapshot owner，`AndroidView.update` 只负责把 Canvas view 与当前 render input 交给 Orchestrator，不持有 `lastSnapshot`，也不直接计算 diff。

`ReaderCanvasStateApplier` 是旧 setter 链的替代品，但它不是 UI 层入口。调用边界应为：

```text
ReaderScreen / ViewModel
    -> ReaderRenderOrchestrator
        -> ReaderCanvasStateApplier
            -> ReaderCanvasView.applySnapshot(snapshot, diff)
```

`ReaderScreen`、`ReaderCanvasEffects`、ViewModel 不应直接调用：

```text
canvasView.applySnapshot(...)
canvasStateApplier.apply(...)
```

不再允许 `ReaderScreen` 或 `ReaderCanvasEffects` 直接散落调用：

```text
setTextAlign
setThemeColors
setTitleStyle
setHeaderSlots
setFooterSlots
updatePaintSnapshot
setPageDelegate
setPage
```

这些调用应集中在 `ReaderCanvasStateApplier` 内部，并按固定顺序执行。

#### 23.6.1 InvalidationScope 执行顺序

`InvalidationScope` 是 enum，每个值自带 `order` 和 `impliedByReflow` 元数据。applier 不维护硬编码执行列表，而是按 `order` 升序遍历 `diff.scopes`（含 REFLOW 展开后的 effectiveScopes）。

```kotlin
effectiveScopes.sortedBy { it.order }.forEach { scope -> ... }
```

顺序约束原因（已编码在 `order` 值中）：

- `PAGE_DELEGATE`（order=0）必须在 `PAGE` 之前：新的 PageDelegate 需要知道页面尺寸才能正确执行翻页动画。
- `REFLOW`（order=1）必须在 `PAGE` 之前：REFLOW 产出的新 TextPage 是 PAGE 的输入。
- `CONTENT`（order=3）必须在 `SHELL`（order=4）之前：shell 绘制时可能参考 content 区域的边界。
- `OVERLAY`（order=5）必须在最后：overlay 叠加在 content 和 shell 之上。

`REFLOW` 的隐含展开使用预计算的 `REFLOW_IMPLIED`：

```kotlin
val effectiveScopes = if (REFLOW in diff.scopes) {
    diff.scopes + InvalidationScope.REFLOW_IMPLIED
} else {
    diff.scopes
}
```

新增 scope 时只需定义 order 和 impliedByReflow，`REFLOW_IMPLIED` 自动更新，applier 核心逻辑不变。

执行顺序建议：

```text
1. 校验 generation（异步分页、预渲染、缓存读取场景）。
2. 展开 REFLOW 的隐含 scope（按 impliedByReflow 元数据）。
3. 按 order 升序依次执行各 scope。
4. submitRenderTask。
```

### 23.7 Canvas 引擎层

`ReaderCanvasView` 重构后的职责：

- 接收 Orchestrator 下发的 snapshot 并执行渲染。
- 持有 `PageDelegate`、`PageBitmapCache`、recorder 和 touch handler。
- 执行绘制、翻页动画、触摸命中、选区映射。
- 提供一个原子应用入口。
- 将现有 30+ setter 降级为 `internal` / `private`，只供 `ReaderCanvasStateApplier` 使用。

不应承担：

- 读取或监听用户设置。
- 推导设置作用域。
- 判断哪些偏好需要重新分页。
- 管理预设、DataStore、数据库。
- 被 `ReaderScreen`、`ReaderCanvasEffects` 或 ViewModel 任意局部修改。
- 持有 `currentSnapshot` 并充当状态源。

建议内部层级：

```text
ReaderCanvasView
├── CanvasVisualParamsManager
├── CanvasTouchHandler
├── CanvasTextSelection
├── PageBitmapCache
├── ReaderPageRenderer
└── PageDelegate
```

页面 recorder 建议固定为：

```text
TextPage
├── shellRecorder
├── contentRecorder
├── overlayRecorder
└── compositeRecorder
```

其中：

- shell：背景、页眉、页脚、进度、电池。
- content：标题、正文。
- overlay：选区、TTS、笔记。
- composite：翻页动画或特殊合成需要时使用。

### 23.8 首帧打开数据流

打开书籍的 T0 流程应调整为：

```text
ReaderScreen(bookId)
    |
    v
ReaderViewModel.openBook(bookId)
    |
    v
load ReaderDefaults + BookReaderPrefs
    |
    v
resolve ResolvedReaderSettings
    |
    v
load book metadata + current chapter text
    |
    v
build ReaderLayoutInput
    |
    v
build TextMeasurer from ReaderLayoutInput
    |
    v
paginate current chapter current page
    |
    v
build ReaderRenderSnapshot
    |
    v
ReaderCanvasView.applySnapshot(snapshot, initialDiff)
    |
    v
draw first frame
```

首帧前禁止等待：

- 相邻章节预加载。
- 全书页数统计。
- 页数持久化。
- 低优先级缓存清理。
- 复杂字体管理 UI 数据。
- TTS 引擎初始化。

这些任务全部进入 T2/T3。

如果当前页分页超过 T0 预算，必须走 fallback：

```text
lastKnownSnapshot
    or
stable blank/skeleton page
```

禁止先用默认偏好绘制一帧再覆盖。

### 23.9 设置变更数据流

阅读页快捷设置修改当前书：

```text
QuickSettingsSheet
    |
    v
ReaderIntent.UpdateBookReaderSetting
    |
    v
ReaderSettingsRepository.updateBookPrefs(bookId)
    |
    v
ReaderSettingsResolver.resolve(...)
    |
    v
new ResolvedReaderSettings
    |
    v
ReaderRenderSnapshotFactory
    |
    v
ReaderRenderDiffCalculator
    |
    +--> REFLOW: reflow current chapter, then apply new page snapshot
    +--> CONTENT: invalidate content recorder
    +--> SHELL: invalidate shell recorder
    +--> OVERLAY: invalidate overlay recorder
    +--> PAGE_DELEGATE: rebuild page delegate
```

软件整体设置页修改全局默认：

```text
SettingsScreen
    |
    v
ReaderSettingsRepository.updateDefaults
    |
    v
Only affects new books or books without BookReaderPrefs
```

如果用户选择“应用到所有书籍”，应走明确的批量覆盖流程，并清理或重写所有 `BookReaderPrefs`。该操作属于破坏性批量操作，需要 UI 明确确认。

### 23.10 与当前代码的迁移关系

当前项目已有一些拆分基础，可以继续复用：

| 当前文件 | 迁移方向 |
| --- | --- |
| `ReaderCanvasView` | 保留 View、绘制、触摸职责，新增/收敛 `applySnapshot` |
| `CanvasVisualParamsManager` | 保留为 Canvas 内部视觉参数应用器 |
| `PageBitmapCache` | 扩展为 shell/content/overlay 分层缓存 |
| `ReaderPageRenderer` | 保留绘制逻辑，按 layer 拆分方法 |
| `ChapterPaginationCoordinator` | 改为消费 `ReaderLayoutInput` |
| `RenderContext` | 改为不可变 data class，录制时传入快照 |
| `ReaderCanvasEffects` | 只保留亮度、屏幕常亮、生命周期、电量采集 |
| `QuickSettingsSheet` | 改为纯 UI，发出 intent，不直接承担设置作用域判断 |

需要删除或替换的旧链路：

```text
ReaderScreen AndroidView.update
    -> 多个 setter

ReaderCanvasEffects
    -> 多个视觉状态 LaunchedEffect

ReaderViewModel.syncTextMeasurerPaint(canvas.textPaint)
    -> 分页依赖 Canvas Paint

RenderContext var shared by Canvas / VisualParams / Cache
    -> 录制期间可能被中途改写

ReaderPreferenceMonitor.syncFromDataStore()
    -> 首帧后异步覆盖默认 ReaderPreferences
```

替代链路：

```text
ResolvedReaderSettings
    -> ReaderLayoutInput
    -> ReaderTextMeasurer
    -> paginate
    -> ReaderRenderSnapshot
    -> ReaderRenderOrchestrator
    -> applySnapshot
```

### 23.11 推荐包结构

建议包结构调整为：

```text
app/src/main/java/com/shuli/reader/
├── core/
│   ├── data/
│   │   ├── ReaderPreferences.kt
│   │   ├── ReaderDefaults.kt
│   │   ├── BookReaderPrefs.kt
│   │   └── ResolvedReaderSettings.kt
│   └── reader/
│       ├── ReaderCanvasView.kt
│       ├── ReaderPageRenderer.kt
│       ├── CanvasVisualParamsManager.kt
│       ├── layout/
│       │   ├── ReaderLayoutInput.kt
│       │   ├── ReaderLayoutHashFactory.kt
│       │   └── ReaderTextMeasurerFactory.kt
│       └── canvas/
│           ├── PageBitmapCache.kt
│           ├── CanvasTouchHandler.kt
│           └── CanvasTextSelection.kt
└── feature/
    └── reader/
        ├── ReaderViewModel.kt
        ├── ReaderIntent.kt
        ├── ReaderPageState.kt
        ├── ReaderOverlayState.kt
        ├── ReaderSearchState.kt
        ├── ReaderBookmarkState.kt
        ├── settings/
        │   ├── ReaderSettingsRepository.kt
        │   ├── ReaderSettingsResolver.kt
        │   ├── ReaderSettingsActions.kt
        │   └── BookReaderPrefsDao.kt
        ├── pagination/
        │   └── ChapterPaginationCoordinator.kt
        └── render/
            ├── ReaderRenderSnapshot.kt
            ├── ReaderRenderSnapshotFactory.kt
            ├── ReaderRenderDiff.kt
            ├── ReaderRenderDiffCalculator.kt
            ├── InvalidationScope.kt
            ├── ReaderRenderKeys.kt
            ├── ReaderRenderOrchestrator.kt
            └── ReaderCanvasStateApplier.kt
```

如果迁移成本需要控制，可以先不移动 `ChapterPaginationCoordinator` 文件，只调整其输入模型；等首帧专项稳定后再移动包路径。

### 23.12 迁移顺序

建议按以下顺序实施，避免中途出现更严重的首帧闪动：

1. `Paginator` 独立 `TextMeasurer`，切断 Canvas Paint 反向依赖。
2. 建立 `ReaderLayoutInput`，加入 `layoutVersion`，让 `LayoutKey` 可废弃旧分页缓存。
3. 建立分层 snapshot、失效集合 diff、分层 key、`ReaderRenderOrchestrator`（唯一 snapshot owner）。Snapshot 模型与 Orchestrator 一起落地，避免"有 Snapshot 但没有 Owner"的中间态。
4. `ReaderCanvasView` 收敛为纯 renderer，setter 降级为内部实现细节。
5. 拆分 `TextPage` recorder 为 shell/content/overlay。
6. 拆分 ViewModel StateFlow，减少 God Object 引发的 recomposition。
7. 建立 `ReaderDefaults` / 稀疏 `BookReaderPrefs` / `ResolvedReaderSettings`。
8. 清理 `ReaderCanvasEffects`、`applyInitialReaderCanvasState`、`ReaderPreferenceMonitor` 旧路径。
9. 新增 `ReaderIntent`，统一 UI、快捷键、TTS、自动翻页等入口。
10. 重构 QuickSettings，使其只发送设置 intent，并明确作用域。

其中第 1 到第 4 步是 P0，必须先完成；第 5 到第 10 步可以分批推进，但每一步完成后都必须保持编译通过和首帧验收通过。

`ReaderIntent` 放在第 9 步而非更早，因为 Intent 模式需要同时改造 ViewModel 和 UI 层，与首帧稳定性核心问题（Paginator 独立、Snapshot 管线、Canvas 收敛）无直接依赖，不应阻塞 P0 改动。
