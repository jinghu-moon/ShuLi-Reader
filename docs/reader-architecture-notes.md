# 阅读界面架构研读笔记

> 研读时间：2026-05-24  
> 范围：`feature/reader` UI 层 + `core/reader` 渲染/分页/动画底层  
> 性质：只读分析，作为后续协助开发/排障的索引

---

## 1. 整体分层

```
┌────────────────────────────────────────────────────────────────┐
│ MainActivity (路由 ActiveScreen.Reader → ReaderScreen)         │
│   @app/src/main/java/com/shuli/reader/MainActivity.kt:151-166  │
└────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────┐
│ feature/reader  (Compose UI + ViewModel)                       │
│  • ReaderScreen.kt            : Scaffold + AndroidView         │
│    悬浮工具栏 / 亮度 / 快捷设置 / 选区动作条 / 目录浮层        │
│  • ReaderViewModel.kt         : UI State + 业务编排            │
│  • component/DirectoryDialog  : 目录/书签/笔记 三 Tab          │
└────────────────────────────────────────────────────────────────┘
                              │ AndroidView 桥接
                              ▼
┌────────────────────────────────────────────────────────────────┐
│ core/reader  (传统 Android View + 自绘)                        │
│  • ReaderCanvasView         : 自定义 View，承载 Bitmap & 手势  │
│  • ReaderPageRenderer       : 单页绘制（文本/高亮/页眉/进度）  │
│  • Paginator + TextMeasurer : 分页算法（纯 Kotlin）            │
│  • animation/*PageDelegate  : 翻页动画委托（5 种）             │
│  • model/                   : TextChapter/TextPage/TextLine/   │
│                                SelectionRange/ReaderLayoutConfig│
│  • cache/                   : LruCache + CacheManager (未接线) │
│  • PageBuffer/ChapterProvider/ReadingStateManager (未接线)     │
└────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────┐
│ 数据层 : BookRepository / BookmarkDao / NoteDao /              │
│         UserPreferences / TtsController / Parser               │
└────────────────────────────────────────────────────────────────┘
```

关键架构判断：

- **UI 用 Compose、渲染用传统 View+Canvas**：通过 `AndroidView`(`@app/src/main/java/com/shuli/reader/feature/reader/ReaderScreen.kt:212-244`) 桥接。Bitmap 预渲染避免动画期间重新排版，是性能关键。
- **分页是 ViewModel 侧的纯 Kotlin 运算**：`SimpleTextMeasurer` 不依赖 Android Paint，可在任何线程跑（`Dispatchers.Default`）。这导致 **Canvas 渲染时的真实字宽与分页用的估算字宽并不严格一致**，是潜在的"末字溢出"风险源。
- **缓存类已写但未接入主流程**：`core/reader/cache/`、`PageBuffer`、`ChapterProvider`、`ReadingStateManager` 当前未在 `ReaderViewModel` / `ReaderCanvasView` 中使用。属于半成品脚手架。

---

## 2. ReaderUiState 与 Overlay 状态机

定义：`@app/src/main/java/com/shuli/reader/feature/reader/ReaderViewModel.kt:50-90`

```
OverlayPanel = NONE | DIRECTORY | BRIGHTNESS | QUICK_SETTINGS
```

- `showToolbar` 与 `overlayPanel` 解耦但联动：点击中心唤起工具栏会同时把 `overlayPanel` 清为 `NONE`(`@app/src/main/java/com/shuli/reader/feature/reader/ReaderViewModel.kt:460-470`)。
- `showDirectory / showQuickSettings / showBrightness` 是派生属性，互斥（`toggleOverlay` 内同 panel 再点关闭，不同 panel 直接覆盖）(`@app/src/main/java/com/shuli/reader/feature/reader/ReaderViewModel.kt:504-509`)。
- `themeColors` 为派生：从 `readerPreferences.backgroundColor` → `toReaderColorScheme()` → `toCanvasThemeColors()`。

**BackHandler 优先级**（`@app/src/main/java/com/shuli/reader/feature/reader/ReaderScreen.kt:131-145`）：
`selectedRange` → `DIRECTORY` → `BRIGHTNESS` → `QUICK_SETTINGS` → `showToolbar` → 透传到外层退到书架。

**工具栏自动隐藏**：5000ms（`TOOLBAR_AUTO_HIDE_DELAY_MS`，`@app/src/main/java/com/shuli/reader/feature/reader/ReaderViewModel.kt:475-481`），但 **只在 `toggleToolbar` 显示时启动一次**——再次交互/翻页 **不会重置计时**。

---

## 3. 打开书籍全链路时序

入口：`openBook(bookId)` `@app/src/main/java/com/shuli/reader/feature/reader/ReaderViewModel.kt:187-246`

```
ReaderScreen LaunchedEffect(bookId)
  └─► ViewModel.openBook(bookId)
        ├─ repository.getBookById(bookId).first()        [IO]
        ├─ repository.parseBookContent(File(filePath))   [IO] → 缓存为 loadedBookContent
        ├─ paginateChapter(content, chapterIndex)        [Default]
        │     └─ repository.getChapterText(...)          [IO]   ← 注意：嵌套 first() 查 book
        │     └─ paginator.paginateChapter(...)
        ├─ 计算 pageIndex (按 durChapterPos)
        ├─ _uiState ← {bookTitle, chapter, page, indices, totalPages...}
        ├─ repository.updateLastReadTime(bookId)         [IO]
        ├─ loadBookmarks() / loadNotes()                 [collect flow]
        └─ 异常 → uiState.error
```

注意点：

- `paginateChapter` 在 `bookId != 0L && repository != null` 时会**再次**调用 `repository.getBookById(bookId).first()` 查 `filePath`(`@app/src/main/java/com/shuli/reader/feature/reader/ReaderViewModel.kt:956-961`)，每次切章/重排都重复一次 IO，**有优化空间**（可缓存 filePath 或 book 引用）。
- `loadBookmarks/loadNotes` 用的是 `collect`，意味着每次进入会再开一个收集协程；如果多次 `openBook`，**已有的收集协程不会被取消**，将与 ViewModel 一起退出。属于潜在的协程泄漏点。
- `normalizedChapters()` 兜底：无章节时把整段视为单一 "Full Text" 章节(`@app/src/main/java/com/shuli/reader/feature/reader/ReaderViewModel.kt:1015-1022`)。

---

## 4. 分页算法 (Paginator)

文件：`@app/src/main/java/com/shuli/reader/core/reader/Paginator.kt:1-247`

**输入**：章节文本 + `ReaderLayoutConfig`（pageSize、textSize、lineHeight、paragraphSpacing、margin*、indent、density）  
**输出**：`TextChapter`，包含若干 `TextPage`，每页若干 `TextLine`。

**单页填充循环**（`paginatePage`）核心逻辑：

1. `lineHeight = textSize * config.lineHeight`，`availableWidth = pageWidth - 2*marginH`。
2. `maxAvailableY = pageHeight - marginV - footerHeight(24*density)`，起始 `currentY = marginV + headerHeight`。
3. 每次循环：
   - 段落起始：跳过 `' ' / '\t' / U+3000 / '\r'` 等空白字符，作为 `skippedSpaces`。
   - 计算首行缩进 `indentWidth = indent * textSize`（防止把整行吃掉时退化为 0）。
   - 调 `calculateLine(...)` 用 `TextMeasurer.measureCharWidth` 累加字宽，到达 `availableWidth` 时停止。
   - **禁则处理**：若下一字符是中文标点（`、。，；：？！）】》」』…`），把该标点拽回上一行，避免行首出现禁则字符 (`@app/src/main/java/com/shuli/reader/core/reader/Paginator.kt:14-30, 227-229`)。
   - 命中 `\n` 时 `isParagraphEnd = true`，并增加 `paragraphSpacing` 垂直留白。
4. 边界保护：若一行都填不下（如尺寸过小）会**强制塞入一行**，避免死循环(`@app/src/main/java/com/shuli/reader/core/reader/Paginator.kt:132-167`)。

**字宽测量** (`SimpleTextMeasurer` `@app/src/main/java/com/shuli/reader/core/reader/SimpleTextMeasurer.kt`)：

- ASCII < 128 → `textSize * 0.56`
- 其他 → `textSize`（按全角等宽处理）

> 这是个粗略模型，**对中英混排或西文字体宽度不准**，会和真实 `Paint.measureText` 有出入，可能造成 Canvas 真实测量后**最后一两个字符被裁切**。

**单位换算（混乱点警示）**：

- `READER_TEXT_PX_PER_SP = 3f`（`@app/src/main/java/com/shuli/reader/feature/reader/ReaderScreen.kt:590`）用于 Canvas 的 `setTextSizePx(fontSize * 3)`。
- `PX_PER_DP = 3f`（`@app/src/main/java/com/shuli/reader/feature/reader/ReaderViewModel.kt:108`）用于 `layoutConfigFor` 里把 dp 换算成 px。
- `PageSize` 固定 `1080 x 1920`，**忽略真实屏幕尺寸**。重排只在偏好变化时触发，**屏幕方向变化 / 实际 View 尺寸变化不会触发重新分页**——`onSizeChanged` 只重渲染当前页 Bitmap。这是已识别的核心**风险点**。

---

## 5. 渲染管线 (ReaderCanvasView + ReaderPageRenderer)

### 5.1 Bitmap 三页缓冲

`ReaderCanvasView` 维护 `currentBitmap / nextBitmap / prevBitmap`，在 `setPage` 时调用 `preRenderAllBitmaps()` 一次性预渲染（`@app/src/main/java/com/shuli/reader/core/reader/ReaderCanvasView.kt:157-167, 354-372`）。

**触发重渲染**：
- `setPage` 中页面引用变化 → 全量预渲染
- `setTextSizePx / setFontFamily / setTheme` → 全量预渲染
- `setHeaderText / setFooterText / setShowProgress / setBatteryLevel / clearSelection / setTtsActiveRange` → **只**重绘当前页（`updateCurrentBitmapHeaderFooter`）以保留 next/prev，避免动画抖动

**生命周期**：
- `onSizeChanged` → `releaseBitmaps()` + `post { preRenderAllBitmaps() }`
- `onDetachedFromWindow` → 释放
- 首帧兜底：`onDraw` 检测 `currentBitmap == null` 时再补一次预渲染(`@app/src/main/java/com/shuli/reader/core/reader/ReaderCanvasView.kt:432-435`)

### 5.2 单页绘制顺序（ReaderPageRenderer.render）

`@app/src/main/java/com/shuli/reader/core/reader/ReaderPageRenderer.kt:21-103`

1. 背景填充
2. 行级高亮（先 TTS 高亮，再用户选区，颜色都从 `progressColor` 派生 alpha；圆角 6px）
3. 正文 `canvas.drawText(line.text, startX, baseline, textPaint)`，逐行
4. 页眉：40% 透明度，basline = `48dp + 24dp*0.6`
5. 页脚文本：40% 透明度（左侧画文本，右侧画电池）
6. 自绘电池图标 + `xx%` 文本
7. 底部进度条（`startCharOffset / endCharOffset` 估算的"页内进度"，**注意这其实是错误的进度公式**——它对当前页的进度，不是章节进度。见第 11 节。）

### 5.3 手势

`GestureDetector.SimpleOnGestureListener` (`@app/src/main/java/com/shuli/reader/core/reader/ReaderCanvasView.kt:119-152`)：

- `onLongPress` → `selectLineAt(x, y)`：定位行 → 构造 `SelectionRange` 覆盖整行 → `onTextSelected` 回调
- `onSingleTapUp`：3x3 网格判断
  - 中心区(1/3~2/3 横纵)：`onCenterClicked` → ViewModel.toggleToolbar
  - 左 1/3：`startPrev` 或回退到 `Direction.PREV`
  - 右 1/3：`startNext`
- `onTouchEvent` 顶层路由：
  - 选区中：吞掉所有事件
  - 中央 1/3 横向：交给 `gestureDetector`
  - 左右：转交 `pageDelegate.onTouch`
  - UP/CANCEL 时若 delegate 在 DRAGGING/ANIMATING，会强制走 delegate（避免动画期间松手不结束）

> 选区目前**只支持整行选择**，不支持自由起止点。

---

## 6. 翻页动画体系

接口：`PageDelegate` `@app/src/main/java/com/shuli/reader/core/reader/animation/PageDelegate.kt`

- 状态机 `IDLE → DRAGGING → ANIMATING → IDLE`
- `Direction = NEXT | PREV | NONE`
- `Callback.onPageChanged(direction)` → 由 `ReaderCanvasView` 转发到 `ViewModel::handlePageDirection` → `nextPage()/prevPage()`

工厂：`PageDelegateFactory.create(type)` `@app/src/main/java/com/shuli/reader/core/reader/animation/PageDelegateFactory.kt`

五种类型：

| 类型 | 文件 | 特征 |
|---|---|---|
| NONE | `NoAnimPageDelegate.kt` | 立即翻页，无动画 |
| COVER | `CoverPageDelegate.kt` | 当前页覆盖滑动，下页静止 |
| HORIZONTAL | `HorizontalPageDelegate.kt` | 两页一起平移（书架式） |
| SIMULATION | `SimulationPageDelegate.kt` | 贝塞尔卷页 + 阴影 + 背面 (`LONG_MS=500ms`) |
| SCROLL | `ScrollPageDelegate.kt` | 垂直滚动 + 惯性，超过 ±screenHeight 阈值翻页 |

**Motion Tokens** (`ReaderMotionTokens.kt`)：`MICRO=100 / SHORT=200 / MEDIUM=300 / LONG=500 / FRAME=16ms`。

**关键设计与隐患**：

- **每次动画启一个 `Thread { while + sleep(16) }`**（HORIZONTAL/COVER/SIMULATION/SCROLL 都是），通过 `Handler(Looper.getMainLooper()).post { invalidate() }` 触发重绘。这是非常老派的实现：
  - 没有用 `ValueAnimator` / `Choreographer`，无法适配显示刷新率（90/120Hz）。
  - 没有 `InterruptedException` 之外的中断手段；`abort()` 只置位 `isAnimating=false`，**线程可能仍跑完最后一帧**。
  - 多次快速翻页可能堆叠多个动画线程（虽然 `abort()` 会让旧线程退出循环）。
- **回弹机制（HORIZONTAL/COVER/SIMULATION）**：拖拽距离 < `screenWidth/3` → `isCancel=true`，方向反转执行动画，但 `shouldNotify=false`，不会真正翻页。
- **`SCROLL` 不发 `Direction.PREV` 也不发普通翻页**——只有越过 `±screenHeight` 才发，且重置 `scrollOffset = 0`，意味着滚动模式与 `ViewModel.nextPage()` 单页模型并不自洽（连续滚动场景下进度回退到 0）。
- **`isAnimationDisabled()`**（`@app/src/main/java/com/shuli/reader/core/reader/ReaderCanvasView.kt:169-201`）：检测系统"关闭动画"后强制改用 `NoAnimPageDelegate`，对辅助功能友好。
- `onDraw` 根据 `delegate.state` + `isDraggingBackward()` 决定用 prev 还是 next bitmap，让回弹/反向拖拽时也能正确显示反向那页(`@app/src/main/java/com/shuli/reader/core/reader/ReaderCanvasView.kt:438-453`)。

---

## 7. 偏好变更 → 重排流程

订阅 (`ReaderViewModel.init`，`@app/src/main/java/com/shuli/reader/feature/reader/ReaderViewModel.kt:145-181`)：

```
combine 9 个 Flow:
  fontSize, lineSpacing, paragraphSpacing, indent,
  pageAnim, brightness, marginH, marginV, readingFont
↓ collectLatest
合成 ReaderPreferences → 更新 _uiState + 重建 pageDelegate
↓
reflowCurrentChapter(preferences):
  • 用 charOffset = currentPage.startCharOffset 锚定位置
  • Dispatchers.Default 重新分页当前章节
  • 用 getPageIndexByCharIndex 找回当前页
  • 更新 currentChapter / currentPage / pageIndex / totalPages
```

写偏好：每个 setter 同时 (1) 立刻改 `_uiState`、(2) 调 `reflowCurrentChapter`（仅排版相关）、(3) `viewModelScope.launch { userPreferences?.setXxx() }` 落盘。**没有 debounce**，连续调步进按钮时会触发多次重排。`collectLatest` 能取消上一次重排，所以最终一致性 OK，但中间会有可见的多次重新分页。

**单位陷阱**：`fontSize` 传给 `Paginator` 时乘以 `PX_PER_DP=3`，传给 Canvas 时乘以 `READER_TEXT_PX_PER_SP=3`。两者数值相同纯属巧合（density 假设 3）。**真实设备 density ≠ 3 时会失配**。

---

## 8. 选区 / 书签 / 笔记 协作链路

```
[长按]
ReaderCanvasView.onLongPress
  └─► selectLineAt(x,y) 构造整行 SelectionRange
        ├─ selectedRange = range
        ├─ updateCurrentBitmapHeaderFooter() 重绘当前页带高亮
        └─ onTextSelected?.invoke(range)
              └─► ViewModel.selectText(range) 写入 uiState.selectedRange
                    └─► ReaderScreen 显示 ReaderSelectionActionBar
                          ├─ 复制：clipboard.setText + clearTextSelection
                          ├─ 添加书签：addBookmarkFromSelection
                          │     └─ BookmarkDao.insertBookmark(entity) → loadBookmarks 重新订阅
                          └─ 添加笔记：addNoteFromSelection (selectedText 当 content)
```

**注意**：
- `addBookmark(selectedText)` 同时落 `pageIndex/position/chapterIndex/chapterPos/chapterName` 等 5 个位置字段，冗余但便于跨设备/重排后定位。
- `goToBookmark / goToNote` 都走 `navigateToChapterPosition`，能在当前章节内时直接换页，跨章节才调 `openChapter`(`@app/src/main/java/com/shuli/reader/feature/reader/ReaderViewModel.kt:811-826`)。
- 选区添加完书签/笔记后 **不会自动关闭工具栏**（因为 toolbar 在选区出现时通常已隐藏，且 selection bar 独立显示）。

---

## 9. TTS 朗读

`@app/src/main/java/com/shuli/reader/feature/reader/ReaderViewModel.kt:559-873`

模型：
1. `startTts(config)`：拿当前页所有 `TextLine`，逐行用 `toSentenceRanges` 拆句（标点：`. ! ? ; 。！？；`），生成 `List<SelectionRange>`。
2. `speakCurrentTtsSentence`：把当前 `SelectionRange.selectedText` 喂给 `TtsController.play`；若 `config.highlightSentence` 把 `ttsActiveRange` 写回 uiState 让 Canvas 高亮该句。
3. `handleTtsUtteranceCompleted` 回调：
   - 还有下一句 → 播放下一句
   - 当页念完 + `autoPage=true` → `nextPage()`；若 `pageIndex` 真的变了 → 重新拆句并继续；否则 → 复位为 `READY`/`IDLE`，清掉 `ttsActiveRange`
4. 生命周期：`Lifecycle.ON_STOP` → `pauseTtsOnBackground`；`onDispose` → `releaseReaderResources`（关动画+TTS）。

**已识别问题**：
- 只在当前页范围内分句，**句子跨页时被强制截断**（句号在下一页 → 上半句被独立朗读）。
- `sentenceRangeOrNull` 用 `indexOf(selectedText, start)` 复原 trim 后位置，遇到当前页有重复短语时会取错位（极小概率）。

---

## 10. 搜索

`searchInCurrentBook(query)` → `BookRepository.searchInBook` → `List<SearchResult>(chapterIndex, charOffset, ...)`

ViewModel 维护 `searchResults / currentSearchResultIndex`，通过 `goToNext/PreviousSearchResult` 循环切换，最终都走 `navigateToChapterPosition(chapterIndex, charOffset)`。

UI 侧：`ReaderSearchControls` 在 TopAppBar 的 `actions` 槽内渲染，**仅在已有结果时显示计数与上下页按钮**(`@app/src/main/java/com/shuli/reader/feature/reader/ReaderScreen.kt:660`)，**没有暴露输入框 UI**——`searchInCurrentBook` 当前只能被外部代码调用，**搜索入口尚未在阅读界面接出来**，这是个明显的 UI 缺口。

---

## 11. 已识别的潜在问题 / 优化点（仅记录）

- **R1 进度公式可能错误**：`ReaderPageRenderer:94-98` 用 `startCharOffset / endCharOffset` 计算"进度"，分母是页结束位置，**永远 < 1 且每页都重置**，不代表章节阅读进度。期望应使用 `(startCharOffset + 1) / content.length` 或 `TextChapter.readProgress`。
- **R2 PageSize 固定 1080×1920**：`ReaderViewModel:106-107`、`layoutConfigFor`。真实屏幕尺寸/横竖屏切换不触发重新分页；`SimpleTextMeasurer` 也不是按 px 真实测量。
- **R3 density 写死**：`ReaderViewModel:135` 默认 3f，`setDensity` 已经实现但布局参数中字号换算用了独立的 `PX_PER_DP=3`，未使用 `density`。
- **R4 翻页动画用裸 Thread**：建议改 `Choreographer` / `ValueAnimator` 以支持高刷与正确中断。
- **R5 工具栏自动隐藏不会因用户活动重置**：长时间阅读期间用户翻页不影响 5s 计时器（实际只在第一次显示时启动一次，无问题；但若工具栏出现时用户继续操作 UI 例如拖滑块，超过 5s 仍会被关闭）。
- **R6 重复 IO**：`paginateChapter` 每次都 `repository.getBookById(...).first()`；`reflowCurrentChapter` 与 `setFontSize/setLineSpacing/...` 在连续滑动步进时频繁触发，没有节流。
- **R7 ChapterProvider / PageBuffer / CacheManager / ReadingStateManager 未接入**：留有半成品，注意改动前确认是否需要补完。
- **R8 选区不可调整端点**：长按只能整行选区，且只在当前页内。
- **R9 搜索入口缺失**：UI 上没有搜索输入框，`searchInCurrentBook` 是死代码（至少在 ReaderScreen 里）。
- **R10 TTS 跨页/跨行句子被切断**：句子完整性问题。
- **R11 `loadBookmarks/loadNotes` 每次 openBook 都会 `collect`**，未取消上次收集，多次切书可能堆 Job。
- **R12 进度回滚条件**：`ScrollPageDelegate.checkChapterBoundary` 跨阈值后把 `scrollOffset` 直接置 0，与 ViewModel 单页模型不耦合，滚动模式实际体验未完整。

---

## 12. 关键文件速查表

| 关注点 | 路径 |
|---|---|
| 阅读界面入口 | `@app/src/main/java/com/shuli/reader/feature/reader/ReaderScreen.kt` |
| UI 状态 + 业务编排 | `@app/src/main/java/com/shuli/reader/feature/reader/ReaderViewModel.kt` |
| 目录/书签/笔记 BottomSheet | `@app/src/main/java/com/shuli/reader/feature/reader/component/DirectoryDialog.kt` |
| Canvas View | `@app/src/main/java/com/shuli/reader/core/reader/ReaderCanvasView.kt` |
| 单页渲染 | `@app/src/main/java/com/shuli/reader/core/reader/ReaderPageRenderer.kt` |
| 分页 | `@app/src/main/java/com/shuli/reader/core/reader/Paginator.kt` |
| 字宽测量 | `@app/src/main/java/com/shuli/reader/core/reader/SimpleTextMeasurer.kt` |
| 翻页动画接口 | `@app/src/main/java/com/shuli/reader/core/reader/animation/PageDelegate.kt` |
| 翻页动画工厂 | `@app/src/main/java/com/shuli/reader/core/reader/animation/PageDelegateFactory.kt` |
| 翻页动画实现 | `@app/src/main/java/com/shuli/reader/core/reader/animation/` (5 个 *PageDelegate.kt) |
| 文本模型 | `@app/src/main/java/com/shuli/reader/core/reader/model/TextModels.kt` |
| 选区值对象 | `@app/src/main/java/com/shuli/reader/core/reader/model/SelectionRange.kt` |
| 触控热区（未接 Canvas） | `@app/src/main/java/com/shuli/reader/core/reader/TouchZone.kt` |
| 缓存（未接） | `@app/src/main/java/com/shuli/reader/core/reader/cache/CacheManager.kt`、`LruCache.kt` |
| 章节预加载（未接） | `@app/src/main/java/com/shuli/reader/core/reader/ChapterProvider.kt` |
| 进度+时长（未接） | `@app/src/main/java/com/shuli/reader/core/reader/ReadingStateManager.kt` |

---

## 13. ReaderViewModel 模块化拆分（2026-06-04 起）

`ReaderViewModel.kt` 在多个迭代中累积至 2808 行，承载 7 类不同职责。按"单一职责 + 可独立测试"原则拆出子模块。

### 13.1 已完成模块

| 模块 | 文件 | 行数 | 职责 |
|---|---|---|---|
| `TextSearchManager` | `feature/reader/search/TextSearchManager.kt` | 91 | 查询发起、结果列表、结果间跳转 |
| `ReadingProgressTracker` | `feature/reader/progress/ReadingProgressTracker.kt` | 227 | 全书进度计算、页眉/页脚槽位解析、页数持久化、布局哈希 |
| `LayoutConfigBuilder` | `feature/reader/progress/LayoutConfigBuilder.kt` | 36 | `ReaderPreferences → ReaderLayoutConfig` 共享工具 |
| `NormalizedChapters` | `feature/reader/progress/NormalizedChapters.kt` | 21 | `BookContent` 规范化为章节列表的共享扩展 |
| `ReaderPreferencesBridge` | `feature/reader/prefs/ReaderPreferencesBridge.kt` | 349 | 40+ 偏好 setter + `updatePrefs` 辅助 + 主题切换 + 字体操作 |

拆分后 `ReaderViewModel` 约 2336 行，剩余职责：UI state、章节导航、TTS、书签/笔记、工具栏、分页。

### 13.2 依赖注入模式

所有子模块使用**构造器注入 + 回调**，避免对 ViewModel 的反向依赖：

```kotlin
class TextSearchManager(
    private val bookRepository: BookRepository?,
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val scope: CoroutineScope,
    private val jumpTo: (chapterIndex: Int, byteOffset: Long) -> Unit,
)
```

**公共 API 保持不变**：ViewModel 保留同名公共方法作为 delegation，UI 调用方无需修改。

### 13.3 待拆分模块

| 模块 | 预估行数 | 暂缓理由 |
|---|---|---|
| `ChapterPaginationCoordinator` | ~450 | `paginateChapterStreaming` 有 3 层嵌套回调，与 ViewModel state 紧耦合 |
| `AppStrings.kt` 拆分为 7 子接口 | ~2077 → 7 文件 | 700 词条 × 3 实现，机械搬运；call sites 可用 delegation 兼容 |
| `QuickSettingsSheet.kt` 拆分为 5 面板 | ~1160 → 6 文件 | 面板间独立，低风险，但非紧迫 |
| `ReaderScreen.kt` 拆分为 4 UI 区域 | ~918 → 5 文件 | 手势处理与 ViewModel 耦合较深 |

### 13.4 拆分原则

1. **单文件 > 500 行** 才考虑拆；< 300 行不拆
2. 按"可独立测试的职责单元"拆，不按"行数平均"拆
3. 子模块通过 `MutableStateFlow<UiState>` 或回调注入，**不复制 state**
4. ViewModel 保留同名公共方法作为 delegation，避免破坏 call sites
5. 拆分完成后**必须编译通过**并**跑相关单测**
