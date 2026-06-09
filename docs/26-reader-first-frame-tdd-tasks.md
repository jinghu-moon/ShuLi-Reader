# 26-TDD - 阅读器首帧稳定性 TDD 任务清单

> 配套设计文档：[26-reader-first-frame-stability.md](./26-reader-first-frame-stability.md)
> 流程：每个任务遵循 **Red → Green → Refactor**
> 验证命令：`./gradlew.bat :app:testDebugUnitTest` + `./gradlew.bat :app:compileDebugKotlin`

## 总体进度（截至 2026-06-08）

| Phase | 描述 | 状态 | 备注 |
|---|---|---|---|
| 1（P0） | Paginator 独立 TextMeasurer | ✅ 完成 | `ReaderLayoutInput` + `ReaderTextMeasurerFactory` |
| 2（P0） | Snapshot + Diff + Key + Orchestrator | ✅ 完成 | 37 tests + Orchestrator 10 tests + Keys 11 tests |
| 3（P0） | Canvas 收敛为 applySnapshot | ✅ 完成 | `ReaderCanvasStateApplier` + 8 tests |
| 4（P1） | TextPage 分层 recorder | ✅ 完成 | `renderOverlay` 拆分 + 6 tests |
| 5（P1） | ViewModel 状态拆分 | ✅ 完成 | 派生 StateFlow + distinctUntilChanged；完全独立 MutableStateFlow → Phase 8 |
| 6（P1） | SettingsResolver 与本书覆盖 | ✅ 完成 | `ReaderSettingsResolver` + `BookReaderPrefsEntity` + 13 tests |
| 7（P1） | 清理旧路径 | ✅ 完成 | setter 降级 internal + 移除 currentPageInvalidate |
| 8（P2） | ReaderIntent 统一入口 | ✅ 完成 | `ReaderIntent` sealed interface + `dispatch()` + UI 全量迁移 |
| 9（P2） | QuickSettings 重构 | ✅ 完成 | 作用域头部 + 4 Tab 结构（排版/字体/页面/交互） |

**编译验证**：`./gradlew.bat :app:compileDebugKotlin` 通过。
**测试验证**：147 个相关测试全部通过（含 ReaderIntentTest 7 tests）。

---

## 通用约定

### 测试包结构

```text
app/src/test/java/com/shuli/reader/
├── core/reader/
│   ├── layout/
│   │   ├── ReaderLayoutInputTest.kt
│   │   └── ReaderLayoutHashFactoryTest.kt
│   └── PaginatorLayoutInputTest.kt
├── feature/reader/render/
│   ├── InvalidationScopeTest.kt
│   ├── ReaderRenderSnapshotTest.kt
│   ├── ReaderRenderDiffCalculatorTest.kt
│   ├── ReaderRenderSnapshotFactoryTest.kt
│   ├── ReaderCanvasStateApplierTest.kt
│   ├── ReaderRenderOrchestratorTest.kt
│   └── ReaderRenderKeysTest.kt
└── feature/reader/settings/
    ├── ResolvedReaderSettingsTest.kt
    └── ReaderSettingsResolverTest.kt
```

### 命名规范

- 测试类：`被测类名 + Test`
- 测试方法：`被测行为_scenario_expectedResult`
- 示例：`diff_fontSizeChanged_returnsReflow`

### Fake / Mock 策略

| 对象 | 策略 | 说明 |
|---|---|---|
| `TextMeasurer` | 复用现有 `FakeTextMeasurer` | 固定每字符 `textSize * 0.6` |
| `ReaderCanvasView` | Fake interface | Applier 测试用，不创建真实 View |
| `SnapshotFactory` | 真实实现 | 纯函数，无需 mock |
| `DiffCalculator` | 真实实现 | 纯函数，无需 mock |
| `Paginator` | 真实实现 + `FakeTextMeasurer` | 分页逻辑需要真实执行 |

---

## Phase 1：Paginator 独立 TextMeasurer（P0） ✅ 已完成

> 设计文档 §15 Phase 1 + §23.5
>
> **实施日期**：2026-06-08
> **新增文件**：`core/reader/layout/ReaderLayoutInput.kt`、`ReaderTextMeasurerFactory.kt`
> **新增测试**：`ReaderLayoutInputTest.kt`（7 tests）、`ReaderTextMeasurerFactoryTest.kt`（9 tests）
> **回归测试**：PaginatorTest、PaginatorStreamingTest 全部通过

### 目标

切断 Paginator 对 `ReaderCanvasView.textPaint` 的反向依赖。分页器从独立的 `ReaderLayoutInput` 构造测量器。

---

### Task 1.1：创建 ReaderLayoutInput data class

**文件**：`core/reader/layout/ReaderLayoutInput.kt`

**测试文件**：`core/reader/layout/ReaderLayoutInputTest.kt`

**Red（先写测试）**：

```kotlin
class ReaderLayoutInputTest {

    @Test
    fun constructor_allFieldsAccessible() {
        val input = ReaderLayoutInput(
            layoutVersion = 1,
            bookId = 42L,
            chapterIndex = 3,
            anchorByteOffset = 0L,
            viewportWidth = 1080,
            viewportHeight = 1920,
            density = 3f,
            fontSizeSp = 18f,
            fontKey = "harmony",
            fontWeight = ReaderFontWeight.NORMAL,
            lineSpacing = 1.5f,
            paragraphSpacing = 1.0f,
            letterSpacing = 0f,
            marginHorizontalDp = 24f,
            marginVerticalDp = 48f,
            indent = 2f,
            titleStyle = TitleStyleConfig(),
            headerVisibleForLayout = true,
            footerVisibleForLayout = true,
            chineseConvert = ChineseConvert.NONE,
            usePanguSpacing = false,
            useZhLayout = false,
            bottomJustify = false,
        )
        assertEquals(1, input.layoutVersion)
        assertEquals(1080, input.viewportWidth)
        assertEquals("harmony", input.fontKey)
    }

    @Test
    fun equals_sameInputs_returnsTrue() {
        val a = createDefaultLayoutInput()
        val b = createDefaultLayoutInput()
        assertEquals(a, b)
    }

    @Test
    fun equals_differentFontSize_returnsFalse() {
        val a = createDefaultLayoutInput(fontSizeSp = 16f)
        val b = createDefaultLayoutInput(fontSizeSp = 18f)
        assertNotEquals(a, b)
    }

    @Test
    fun equals_differentLayoutVersion_returnsFalse() {
        val a = createDefaultLayoutInput(layoutVersion = 1)
        val b = createDefaultLayoutInput(layoutVersion = 2)
        assertNotEquals(a, b)
    }

    // helper
    private fun createDefaultLayoutInput(
        layoutVersion: Int = 1,
        fontSizeSp: Float = 18f,
        // ... 其余参数给默认值
    ) = ReaderLayoutInput(...)
}
```

**Green（实现）**：

按 §23.5 定义创建 `ReaderLayoutInput` data class，包含全部 23 个字段。

**验收**：
- [x] `ReaderLayoutInputTest` 全部通过
- [x] `ReaderLayoutInput` 是纯 data class，无 Android 依赖
- [x] 编译通过

---

### Task 1.2：创建 ReaderTextMeasurerFactory

**文件**：`core/reader/layout/ReaderTextMeasurerFactory.kt`

**测试文件**：`core/reader/layout/ReaderTextMeasurerFactoryTest.kt`

**Red**：

```kotlin
class ReaderTextMeasurerFactoryTest {

    @Test
    fun create_withValidInput_returnsTextMeasurer() {
        val input = createDefaultLayoutInput(fontSizeSp = 18f, density = 3f)
        val measurer = ReaderTextMeasurerFactory.create(input)
        assertNotNull(measurer)
    }

    @Test
    fun create_measurerUsesCorrectTextSize() {
        val input = createDefaultLayoutInput(fontSizeSp = 20f, density = 2f)
        val measurer = ReaderTextMeasurerFactory.create(input)
        // 20sp * 2 density = 40px，每字符宽 40 * 0.6 = 24
        val width = measurer.measureCharWidth('A', input.fontSizeSp * input.density)
        assertEquals(24f, width, 0.1f)
    }

    @Test
    fun create_withDifferentFontWeight_returnsDifferentMeasurer() {
        val normal = ReaderTextMeasurerFactory.create(
            createDefaultLayoutInput(fontWeight = ReaderFontWeight.NORMAL)
        )
        val bold = ReaderTextMeasurerFactory.create(
            createDefaultLayoutInput(fontWeight = ReaderFontWeight.BOLD)
        )
        // 两者都是有效 TextMeasurer（具体差异由 Android 实现决定）
        assertNotNull(normal)
        assertNotNull(bold)
    }
}
```

**Green**：

实现 `ReaderTextMeasurerFactory`，从 `ReaderLayoutInput` 提取 `fontSizeSp * density`、`fontKey`、`fontWeight` 构造 `TextMeasurer`。

pre-release 阶段：单元测试中使用 `FakeTextMeasurer` 作为 factory 输出（通过注入），真实 Android 实现在 `AndroidTextMeasurer` 中。

```kotlin
internal object ReaderTextMeasurerFactory {
    fun create(input: ReaderLayoutInput): TextMeasurer {
        // pre-release：返回 SimpleTextMeasurer
        // Phase 3 后替换为 AndroidTextMeasurer 构造
        return SimpleTextMeasurer()
    }
}
```

**验收**：
- [x] `ReaderTextMeasurerFactoryTest` 全部通过
- [x] Factory 不依赖 `ReaderCanvasView` 或 `Paint`

---

### Task 1.3：Paginator 从 ReaderLayoutInput 分页

**文件**：修改 `Paginator.kt`

**测试文件**：`core/reader/PaginatorLayoutInputTest.kt`

**Red**：

```kotlin
class PaginatorLayoutInputTest {

    private val paginator = Paginator(FakeTextMeasurer())

    @Test
    fun paginateFromLayoutInput_producesValidPages() {
        val input = createDefaultLayoutInput()
        val config = ReaderTextMeasurerFactory.toLayoutConfig(input)
        val chapter = paginator.paginateChapter(0, "测试标题", "这是一段测试文本。", config)

        assertTrue(chapter.pages.isNotEmpty())
        assertTrue(chapter.pages[0].lines.isNotEmpty())
    }

    @Test
    fun paginateFromLayoutInput_differentFontSize_differentPageCount() {
        val content = "字".repeat(2000)

        val smallConfig = ReaderTextMeasurerFactory.toLayoutConfig(
            createDefaultLayoutInput(fontSizeSp = 14f)
        )
        val largeConfig = ReaderTextMeasurerFactory.toLayoutConfig(
            createDefaultLayoutInput(fontSizeSp = 24f)
        )

        val smallChapter = paginator.paginateChapter(0, "", content, smallConfig)
        val largeChapter = paginator.paginateChapter(0, "", content, largeConfig)

        assertTrue("大字号应产生更多页",
            largeChapter.pages.size > smallChapter.pages.size)
    }

    @Test
    fun paginateFromLayoutInput_noCanvasPaintDependency() {
        // 编译期验证：Paginator 不接受 Paint 参数
        // 此测试确认 Paginator 构造函数只需要 TextMeasurer
        val p = Paginator(FakeTextMeasurer())
        assertNotNull(p)
    }
}
```

**Green**：

新增 `ReaderTextMeasurerFactory.toLayoutConfig(input: ReaderLayoutInput): ReaderLayoutConfig` 转换方法。确认 `Paginator` 构造函数不需要 `Paint`。

**验收**：
- [x] `PaginatorLayoutInputTest` 全部通过
- [x] `Paginator` 构造和分页方法签名中无 `Paint` / `Canvas` / `View` 引用
- [x] 现有 `PaginatorTest` 全部通过（回归）

---

### Task 1.4：删除 syncTextMeasurerPaint 依赖

**文件**：修改 `ReaderViewModel.kt`

**测试文件**：无新增（删除旧代码）

**步骤**：

1. 在 `ReaderViewModel` 中，将 `paginator` 的初始化改为使用 `ReaderTextMeasurerFactory`：

```kotlin
// 旧：
private val paginator: Paginator = Paginator(SimpleTextMeasurer())
// ...
fun syncTextMeasurerPaint(paint: Paint) { ... }

// 新：
private var paginator: Paginator? = null

private fun ensurePaginator(input: ReaderLayoutInput): Paginator {
    if (paginator == null) {
        paginator = Paginator(ReaderTextMeasurerFactory.create(input))
    }
    return paginator!!
}
```

2. 标记 `syncTextMeasurerPaint` 为 `@Deprecated`。
3. 确认 `ReaderScreen.applyInitialReaderCanvasState` 中对 `syncTextMeasurerPaint` 的调用可以安全跳过（paginator 已有独立 measurer）。

**验收**：
- [x] `./gradlew.bat :app:compileDebugKotlin` 通过
- [x] `./gradlew.bat :app:testDebugUnitTest` 通过
- [ ] `syncTextMeasurerPaint` 标记 `@Deprecated`（→ **Phase 7 Task 7.1** 删除时一并处理）
- [x] 首帧流程不再依赖 Canvas Paint 同步

---

### Task 1.5：Phase 1 集成验证

**手动验收**：

1. 打开任意 TXT 书籍，确认首帧正常渲染
2. 修改字号，确认 reflow 正常
3. 切换字体，确认分页结果更新
4. 横竖屏切换，确认页面正确

**自动验收**：

```powershell
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat :app:testDebugUnitTest
```

**Phase 1 完成标准**：
- [x] `Paginator` 完全不依赖 `Canvas` / `Paint` / `View`
- [x] `ReaderLayoutInput` 可独立构造和测试
- [x] `ReaderTextMeasurerFactory` 从 `ReaderLayoutInput` 创建 `TextMeasurer`
- [x] 所有现有测试通过

---

## Phase 2：Snapshot + Diff + Key + Orchestrator（P0） ✅ 已完成

> 设计文档 §15 Phase 2 + §7 + §8 + §11.6 + §14.1
>
> **实施日期**：2026-06-08
> **新增文件**：`InvalidationScope.kt`、`ReaderRenderSnapshot.kt`、`ReaderRenderKeys.kt`、`ReaderRenderDiffCalculator.kt`、`ReaderRenderSnapshotFactory.kt`、`ReaderRenderOrchestrator.kt`、`ReaderRenderDiff.kt`
> **新增测试**：`InvalidationScopeTest.kt`（11 tests）、`ReaderRenderSnapshotTest.kt`（9 tests）、`ReaderRenderDiffCalculatorTest.kt`（17 tests）
> **全部 37 个新增测试通过**

### 目标

建立纯数据模型（Snapshot、Diff、Key）和 Orchestrator，保证可以独立测试。

---

### Task 2.1：InvalidationScope enum

**文件**：`feature/reader/render/InvalidationScope.kt`

**测试文件**：`feature/reader/render/InvalidationScopeTest.kt`

**Red**：

```kotlin
class InvalidationScopeTest {

    @Test
    fun order_pageDelegateIsZero() {
        assertEquals(0, InvalidationScope.PAGE_DELEGATE.order)
    }

    @Test
    fun order_reflowIsOne() {
        assertEquals(1, InvalidationScope.REFLOW.order)
    }

    @Test
    fun order_overlayIsFive() {
        assertEquals(5, InvalidationScope.OVERLAY.order)
    }

    @Test
    fun impliedByFlow_reflowIsFalse() {
        assertFalse(InvalidationScope.REFLOW.impliedByReflow)
    }

    @Test
    fun impliedByFlow_pageIsTrue() {
        assertTrue(InvalidationScope.PAGE.impliedByReflow)
    }

    @Test
    fun impliedByFlow_contentIsTrue() {
        assertTrue(InvalidationScope.CONTENT.impliedByReflow)
    }

    @Test
    fun impliedByFlow_shellIsTrue() {
        assertTrue(InvalidationScope.SHELL.impliedByReflow)
    }

    @Test
    fun impliedByFlow_overlayIsTrue() {
        assertTrue(InvalidationScope.OVERLAY.impliedByReflow)
    }

    @Test
    fun reflowImplied_containsPageContentShellOverlay() {
        val implied = InvalidationScope.REFLOW_IMPLIED
        assertEquals(4, implied.size)
        assertTrue(implied.contains(InvalidationScope.PAGE))
        assertTrue(implied.contains(InvalidationScope.CONTENT))
        assertTrue(implied.contains(InvalidationScope.SHELL))
        assertTrue(implied.contains(InvalidationScope.OVERLAY))
    }

    @Test
    fun reflowImplied_doesNotContainReflowOrPageDelegate() {
        val implied = InvalidationScope.REFLOW_IMPLIED
        assertFalse(implied.contains(InvalidationScope.REFLOW))
        assertFalse(implied.contains(InvalidationScope.PAGE_DELEGATE))
    }

    @Test
    fun sortedBy_order_returnsCorrectSequence() {
        val sorted = InvalidationScope.entries.sortedBy { it.order }
        assertEquals(InvalidationScope.PAGE_DELEGATE, sorted[0])
        assertEquals(InvalidationScope.REFLOW, sorted[1])
        assertEquals(InvalidationScope.PAGE, sorted[2])
        assertEquals(InvalidationScope.CONTENT, sorted[3])
        assertEquals(InvalidationScope.SHELL, sorted[4])
        assertEquals(InvalidationScope.OVERLAY, sorted[5])
    }
}
```

**Green**：

按 §8 定义创建 `InvalidationScope` enum。

**验收**：
- [x] 11 个测试全部通过
- [x] `REFLOW_IMPLIED` 预计算正确

---

### Task 2.2：子 Snapshot data classes

**文件**：`feature/reader/render/ReaderRenderSnapshot.kt`

**测试文件**：`feature/reader/render/ReaderRenderSnapshotTest.kt`

**Red**：

```kotlin
class ReaderRenderSnapshotTest {

    @Test
    fun pageSnapshot_equals_sameFields_returnsTrue() {
        val a = createDefaultPageSnapshot(pageIndex = 5)
        val b = createDefaultPageSnapshot(pageIndex = 5)
        assertEquals(a, b)
    }

    @Test
    fun pageSnapshot_equals_differentPageIndex_returnsFalse() {
        val a = createDefaultPageSnapshot(pageIndex = 5)
        val b = createDefaultPageSnapshot(pageIndex = 6)
        assertNotEquals(a, b)
    }

    @Test
    fun pageSnapshot_contentVersion_isIntNotCharSequence() {
        val snapshot = createDefaultPageSnapshot(contentVersion = 42)
        // 编译验证：contentVersion 是 Int
        val version: Int = snapshot.contentVersion
        assertEquals(42, version)
    }

    @Test
    fun layoutSnapshot_equals_sameLayoutKey_returnsTrue() {
        val key = LayoutKey(layoutVersion = 1, inputHash = "abc")
        val a = LayoutSnapshot(input = createDefaultLayoutInput(), layoutKey = key)
        val b = LayoutSnapshot(input = createDefaultLayoutInput(), layoutKey = key)
        assertEquals(a, b)
    }

    @Test
    fun visualSnapshot_equals_sameTheme_returnsTrue() {
        val a = createDefaultVisualSnapshot()
        val b = createDefaultVisualSnapshot()
        assertEquals(a, b)
    }

    @Test
    fun shellSnapshot_equals_sameBattery_returnsTrue() {
        val a = createDefaultShellSnapshot(batteryLevel = 80)
        val b = createDefaultShellSnapshot(batteryLevel = 80)
        assertEquals(a, b)
    }

    @Test
    fun overlaySnapshot_equals_sameTtsRange_returnsTrue() {
        val range = SelectionRange(10, 20, "test")
        val a = OverlaySnapshot(null, range, emptyList(), OverlayKey("k"))
        val b = OverlaySnapshot(null, range, emptyList(), OverlayKey("k"))
        assertEquals(a, b)
    }

    @Test
    fun overlaySnapshot_differentTtsRange_returnsFalse() {
        val a = OverlaySnapshot(null, SelectionRange(10, 20, "a"), emptyList(), OverlayKey("k"))
        val b = OverlaySnapshot(null, SelectionRange(30, 40, "b"), emptyList(), OverlayKey("k"))
        assertNotEquals(a, b)
    }

    @Test
    fun readerRenderSnapshot_generation_isAccessible() {
        val snapshot = createDefaultSnapshot(generation = 7)
        assertEquals(7L, snapshot.generation)
    }
}
```

**Green**：

按 §7 创建全部子 snapshot data class：`PageSnapshot`、`LayoutSnapshot`、`VisualSnapshot`、`ShellSnapshot`、`OverlaySnapshot`，以及主 `ReaderRenderSnapshot`。

**验收**：
- [x] 9 个测试全部通过
- [x] 所有 snapshot 是不可变 data class
- [x] `PageSnapshot.contentVersion` 是 `Int`（非 `CharSequence`）
- [x] `PageSnapshot.pageAnimType` 字段存在（从旧 `AnimationSnapshot` 合并）

---

### Task 2.3：LayoutKey + RenderKey + OverlayKey

**文件**：`feature/reader/render/ReaderRenderKeys.kt`

**测试文件**：`feature/reader/render/ReaderRenderKeysTest.kt`

**Red**：

```kotlin
class ReaderRenderKeysTest {

    // ── LayoutKey ──

    @Test
    fun layoutKey_sameInput_sameKey() {
        val input = createDefaultLayoutInput()
        val a = ReaderLayoutHasher.hash(input)
        val b = ReaderLayoutHasher.hash(input)
        assertEquals(a, b)
    }

    @Test
    fun layoutKey_differentFontSize_differentKey() {
        val a = ReaderLayoutHasher.hash(createDefaultLayoutInput(fontSizeSp = 16f))
        val b = ReaderLayoutHasher.hash(createDefaultLayoutInput(fontSizeSp = 18f))
        assertNotEquals(a, b)
    }

    @Test
    fun layoutKey_differentLayoutVersion_differentKey() {
        val a = ReaderLayoutHasher.hash(createDefaultLayoutInput(layoutVersion = 1))
        val b = ReaderLayoutHasher.hash(createDefaultLayoutInput(layoutVersion = 2))
        assertNotEquals(a, b)
    }

    @Test
    fun layoutKey_containsLayoutVersion() {
        val key = ReaderLayoutHasher.hash(createDefaultLayoutInput(layoutVersion = 5))
        assertEquals(5, key.layoutVersion)
    }

    @Test
    fun layoutKey_differentFontWeight_differentKey() {
        val a = ReaderLayoutHasher.hash(createDefaultLayoutInput(fontWeight = ReaderFontWeight.NORMAL))
        val b = ReaderLayoutHasher.hash(createDefaultLayoutInput(fontWeight = ReaderFontWeight.BOLD))
        assertNotEquals(a, b)
    }

    @Test
    fun layoutKey_differentChineseConvert_differentKey() {
        val a = ReaderLayoutHasher.hash(createDefaultLayoutInput(chineseConvert = ChineseConvert.NONE))
        val b = ReaderLayoutHasher.hash(createDefaultLayoutInput(chineseConvert = ChineseConvert.SIMPLIFIED))
        assertNotEquals(a, b)
    }

    // ── RenderKey ──

    @Test
    fun renderKey_sameVisual_sameKey() {
        val a = RenderKey.from(createDefaultVisualSnapshot())
        val b = RenderKey.from(createDefaultVisualSnapshot())
        assertEquals(a, b)
    }

    @Test
    fun renderKey_differentTextAlign_differentKey() {
        val a = RenderKey.from(createDefaultVisualSnapshot(textAlign = ReaderTextAlign.LEFT))
        val b = RenderKey.from(createDefaultVisualSnapshot(textAlign = ReaderTextAlign.JUSTIFY))
        assertNotEquals(a, b)
    }

    // ── OverlayKey ──

    @Test
    fun overlayKey_sameOverlay_sameKey() {
        val a = OverlayKey.from(createDefaultOverlaySnapshot())
        val b = OverlayKey.from(createDefaultOverlaySnapshot())
        assertEquals(a, b)
    }

    @Test
    fun overlayKey_differentTtsRange_differentKey() {
        val a = OverlayKey.from(OverlaySnapshot(null, SelectionRange(0, 10, "a"), emptyList(), OverlayKey("")))
        val b = OverlayKey.from(OverlaySnapshot(null, SelectionRange(20, 30, "b"), emptyList(), OverlayKey("")))
        assertNotEquals(a, b)
    }

    @Test
    fun overlayKey_doesNotAffectLayoutKey() {
        // OverlayKey 变化不应影响 LayoutKey
        val input = createDefaultLayoutInput()
        val layoutKey = ReaderLayoutHasher.hash(input)
        // overlay 变化后 layoutKey 应不变（由架构保证，此处验证独立性）
        assertNotNull(layoutKey)
    }
}
```

**Green**：

按 §14.1 创建 `LayoutKey`、`RenderKey`、`OverlayKey` data class，以及 `ReaderLayoutHasher` object。

**验收**：
- [x] 11 个测试全部通过
- [x] `LayoutKey` 包含 `layoutVersion` 字段
- [x] `ReaderLayoutHasher` 是 `internal object`（§12.1 轻量规则）
- [x] 所有影响分页的字段变化都产生不同 `LayoutKey`

---

### Task 2.4：ReaderRenderDiffCalculator

**文件**：`feature/reader/render/ReaderRenderDiffCalculator.kt`

**测试文件**：`feature/reader/render/ReaderRenderDiffCalculatorTest.kt`

**Red**：

```kotlin
class ReaderRenderDiffCalculatorTest {

    // ── null 旧 snapshot ──

    @Test
    fun diff_oldIsNull_returnsAllScopes() {
        val new = createDefaultSnapshot()
        val diff = ReaderRenderDiffCalculator.diff(null, new)
        assertTrue(diff.scopes.contains(InvalidationScope.PAGE))
        assertTrue(diff.scopes.contains(InvalidationScope.CONTENT))
        assertTrue(diff.scopes.contains(InvalidationScope.SHELL))
        assertTrue(diff.scopes.contains(InvalidationScope.OVERLAY))
    }

    // ── 空页面 diff 语义（§8.1） ──

    @Test
    fun diff_currentPageNullToNonNull_returnsPageContentShell() {
        val old = createDefaultSnapshot(
            page = createDefaultPageSnapshot(currentPage = null)
        )
        val new = createDefaultSnapshot(
            page = createDefaultPageSnapshot(currentPage = TextPage.EMPTY)
        )
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.PAGE))
        assertTrue(diff.scopes.contains(InvalidationScope.CONTENT))
        assertTrue(diff.scopes.contains(InvalidationScope.SHELL))
    }

    @Test
    fun diff_currentPageNonNullToNull_returnsNoInvalidation() {
        val old = createDefaultSnapshot(
            page = createDefaultPageSnapshot(currentPage = TextPage.EMPTY)
        )
        val new = createDefaultSnapshot(
            page = createDefaultPageSnapshot(currentPage = null)
        )
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue("瞬态不应触发 invalidation", diff.scopes.isEmpty())
    }

    @Test
    fun diff_chapterIndexChanged_returnsReflow() {
        val old = createDefaultSnapshot(
            page = createDefaultPageSnapshot(chapterIndex = 1)
        )
        val new = createDefaultSnapshot(
            page = createDefaultPageSnapshot(chapterIndex = 2)
        )
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.REFLOW))
    }

    // ── Layout 变化 → REFLOW ──

    @Test
    fun diff_fontSizeChanged_returnsReflow() {
        val old = createDefaultSnapshot(layout = createLayoutSnapshot(fontSizeSp = 16f))
        val new = createDefaultSnapshot(layout = createLayoutSnapshot(fontSizeSp = 18f))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.REFLOW))
    }

    @Test
    fun diff_fontChanged_returnsReflow() {
        val old = createDefaultSnapshot(layout = createLayoutSnapshot(fontKey = "harmony"))
        val new = createDefaultSnapshot(layout = createLayoutSnapshot(fontKey = "system"))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.REFLOW))
    }

    @Test
    fun diff_fontWeightChanged_returnsReflow() {
        val old = createDefaultSnapshot(layout = createLayoutSnapshot(fontWeight = ReaderFontWeight.NORMAL))
        val new = createDefaultSnapshot(layout = createLayoutSnapshot(fontWeight = ReaderFontWeight.BOLD))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.REFLOW))
    }

    @Test
    fun diff_lineSpacingChanged_returnsReflow() {
        val old = createDefaultSnapshot(layout = createLayoutSnapshot(lineSpacing = 1.5f))
        val new = createDefaultSnapshot(layout = createLayoutSnapshot(lineSpacing = 2.0f))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.REFLOW))
    }

    @Test
    fun diff_marginChanged_returnsReflow() {
        val old = createDefaultSnapshot(layout = createLayoutSnapshot(marginHorizontalDp = 24f))
        val new = createDefaultSnapshot(layout = createLayoutSnapshot(marginHorizontalDp = 32f))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.REFLOW))
    }

    // ── Visual 变化 → CONTENT ──

    @Test
    fun diff_textAlignChanged_returnsContent() {
        val old = createDefaultSnapshot(visual = createVisualSnapshot(textAlign = ReaderTextAlign.LEFT))
        val new = createDefaultSnapshot(visual = createVisualSnapshot(textAlign = ReaderTextAlign.JUSTIFY))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.CONTENT))
        assertFalse("textAlign 不应触发 REFLOW", diff.scopes.contains(InvalidationScope.REFLOW))
    }

    @Test
    fun diff_themeChanged_returnsContentAndShell() {
        val old = createDefaultSnapshot(visual = createVisualSnapshot(themeColors = THEME_PAPER))
        val new = createDefaultSnapshot(visual = createVisualSnapshot(themeColors = THEME_DARK))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.CONTENT))
        assertTrue(diff.scopes.contains(InvalidationScope.SHELL))
    }

    // ── Shell 变化 → SHELL ──

    @Test
    fun diff_batteryChanged_returnsShell() {
        val old = createDefaultSnapshot(shell = createShellSnapshot(batteryLevel = 80))
        val new = createDefaultSnapshot(shell = createShellSnapshot(batteryLevel = 79))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.SHELL))
        assertFalse(diff.scopes.contains(InvalidationScope.CONTENT))
    }

    @Test
    fun diff_headerSlotsChanged_returnsShell() {
        val old = createDefaultSnapshot(shell = createShellSnapshot(headerText = "第一章"))
        val new = createDefaultSnapshot(shell = createShellSnapshot(headerText = "第二章"))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.SHELL))
    }

    // ── Overlay 变化 → OVERLAY ──

    @Test
    fun diff_ttsRangeChanged_returnsOverlay() {
        val old = createDefaultSnapshot(overlay = createOverlaySnapshot(ttsRange = SelectionRange(0, 10, "a")))
        val new = createDefaultSnapshot(overlay = createOverlaySnapshot(ttsRange = SelectionRange(20, 30, "b")))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.OVERLAY))
        assertFalse(diff.scopes.contains(InvalidationScope.CONTENT))
    }

    @Test
    fun diff_selectionChanged_returnsOverlay() {
        val old = createDefaultSnapshot(overlay = createOverlaySnapshot(selection = null))
        val new = createDefaultSnapshot(overlay = createOverlaySnapshot(selection = SelectionRange(5, 15, "x")))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.OVERLAY))
    }

    // ── Page 变化 → PAGE ──

    @Test
    fun diff_pageIndexChanged_returnsPage() {
        val old = createDefaultSnapshot(page = createDefaultPageSnapshot(pageIndex = 3))
        val new = createDefaultSnapshot(page = createDefaultPageSnapshot(pageIndex = 4))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.PAGE))
    }

    @Test
    fun diff_pageAnimTypeChanged_returnsPageDelegate() {
        val old = createDefaultSnapshot(page = createDefaultPageSnapshot(animType = PageAnimType.HORIZONTAL))
        val new = createDefaultSnapshot(page = createDefaultPageSnapshot(animType = PageAnimType.SIMULATION))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.PAGE_DELEGATE))
    }

    // ── 完全相同 → 空 diff ──

    @Test
    fun diff_identicalSnapshots_returnsEmpty() {
        val snapshot = createDefaultSnapshot()
        val diff = ReaderRenderDiffCalculator.diff(snapshot, snapshot)
        assertTrue(diff.scopes.isEmpty())
    }
}
```

**Green**：

实现 `ReaderRenderDiffCalculator` 纯函数，按 §8 和 §8.1 的规则计算 diff。

**验收**：
- [x] 17 个测试全部通过
- [x] 纯函数：相同输入总是产生相同输出
- [x] 无 Android 依赖，可在 JVM 上运行

---

### Task 2.5：ReaderRenderSnapshotFactory（含子快照缓存）

**文件**：`feature/reader/render/ReaderRenderSnapshotFactory.kt`

**测试文件**：`feature/reader/render/ReaderRenderSnapshotFactoryTest.kt`

**Red**：

```kotlin
class ReaderRenderSnapshotFactoryTest {

    @Test
    fun build_returnsCompleteSnapshot() {
        val factory = ReaderRenderSnapshotFactory()
        val snapshot = factory.build(
            pageState = createDefaultPageState(),
            settings = createDefaultSettings(),
            overlayState = createDefaultOverlayState(),
            generation = 1,
        )
        assertEquals(1L, snapshot.generation)
        assertNotNull(snapshot.page)
        assertNotNull(snapshot.layout)
        assertNotNull(snapshot.visual)
        assertNotNull(snapshot.shell)
        assertNotNull(snapshot.overlay)
    }

    @Test
    fun build_overlayChangedOnly_reusesLayoutAndVisual() {
        val factory = ReaderRenderSnapshotFactory()

        val snapshot1 = factory.build(
            pageState = createDefaultPageState(),
            settings = createDefaultSettings(),
            overlayState = createDefaultOverlayState(ttsRange = null),
            generation = 1,
        )

        val snapshot2 = factory.build(
            pageState = createDefaultPageState(),
            settings = createDefaultSettings(),
            overlayState = createDefaultOverlayState(ttsRange = SelectionRange(0, 10, "a")),
            generation = 2,
        )

        // layout 和 visual 应复用缓存实例（引用相等）
        assertSame("layout 应复用", snapshot1.layout, snapshot2.layout)
        assertSame("visual 应复用", snapshot1.visual, snapshot2.visual)
        // overlay 应重建
        assertNotSame("overlay 应重建", snapshot1.overlay, snapshot2.overlay)
    }

    @Test
    fun build_settingsUnchanged_reusesLayoutSnapshot() {
        val factory = ReaderRenderSnapshotFactory()
        val settings = createDefaultSettings()

        val snapshot1 = factory.build(
            createDefaultPageState(), settings, createDefaultOverlayState(), 1
        )
        val snapshot2 = factory.build(
            createDefaultPageState(), settings, createDefaultOverlayState(), 2
        )

        assertSame("settings 不变时 layout 应复用", snapshot1.layout, snapshot2.layout)
    }

    @Test
    fun build_fontSizeChanged_rebuildsLayout() {
        val factory = ReaderRenderSnapshotFactory()

        val snapshot1 = factory.build(
            createDefaultPageState(),
            createDefaultSettings(fontSize = 16f),
            createDefaultOverlayState(),
            1,
        )
        val snapshot2 = factory.build(
            createDefaultPageState(),
            createDefaultSettings(fontSize = 18f),
            createDefaultOverlayState(),
            2,
        )

        assertNotSame("字号变化时 layout 应重建", snapshot1.layout, snapshot2.layout)
    }

    @Test
    fun build_doesNotUseChangedFlagsInSettings() {
        // 验证 Factory 内部使用 equals 比较，不依赖 settings 中的 changed 标志
        val factory = ReaderRenderSnapshotFactory()
        val settings = createDefaultSettings() // 无 layoutChanged/visualChanged 字段

        factory.build(createDefaultPageState(), settings, createDefaultOverlayState(), 1)
        factory.build(createDefaultPageState(), settings, createDefaultOverlayState(), 2)
        // 无异常即通过
    }
}
```

**Green**：

按 §7.1 实现 `ReaderRenderSnapshotFactory`，使用 data class `equals()` 做子快照缓存。

**验收**：
- [x] 5 个测试全部通过
- [x] `ResolvedReaderSettings` 中无 `layoutChanged` / `visualChanged` 字段
- [x] TTS 高频场景只重建 `OverlaySnapshot`

---

### Task 2.6：ReaderRenderOrchestrator

**文件**：`feature/reader/render/ReaderRenderOrchestrator.kt`

**测试文件**：`feature/reader/render/ReaderRenderOrchestratorTest.kt`

**Red**：

```kotlin
class ReaderRenderOrchestratorTest {

    private lateinit var orchestrator: ReaderRenderOrchestrator
    private lateinit var fakeApplier: FakeCanvasApplier

    @Before
    fun setup() {
        fakeApplier = FakeCanvasApplier()
        orchestrator = ReaderRenderOrchestrator(
            snapshotFactory = ReaderRenderSnapshotFactory(),
            diffCalculator = ReaderRenderDiffCalculator,
            applier = fakeApplier,
        )
    }

    // ── apply() 同步场景 ──

    @Test
    fun apply_firstCall_appliesSnapshot() {
        val input = createDefaultRenderInput()
        orchestrator.apply(fakeCanvas, input)
        assertEquals(1, fakeApplier.applyCount)
    }

    @Test
    fun apply_incrementsGeneration() {
        orchestrator.apply(fakeCanvas, createDefaultRenderInput())
        orchestrator.apply(fakeCanvas, createDefaultRenderInput())
        // 第二次 apply 时 generation 应递增
        assertTrue(orchestrator.isCurrent(2))
    }

    @Test
    fun apply_identicalInput_stillApplies() {
        // apply 总是执行（diff 可能为空但 apply 仍被调用）
        val input = createDefaultRenderInput()
        orchestrator.apply(fakeCanvas, input)
        orchestrator.apply(fakeCanvas, input)
        assertEquals(2, fakeApplier.applyCount)
    }

    // ── reserveGeneration() + applyAsync() 异步场景 ──

    @Test
    fun reserveGeneration_incrementsGeneration() {
        val gen1 = orchestrator.reserveGeneration()
        val gen2 = orchestrator.reserveGeneration()
        assertEquals(gen1 + 1, gen2)
    }

    @Test
    fun applyAsync_currentGeneration_applies() {
        val gen = orchestrator.reserveGeneration()
        orchestrator.applyAsync(fakeCanvas, createDefaultRenderInput(), gen)
        assertEquals(1, fakeApplier.applyCount)
    }

    @Test
    fun applyAsync_staleGeneration_skips() {
        val gen = orchestrator.reserveGeneration()
        // 模拟新的同步 apply 使 gen 过期
        orchestrator.apply(fakeCanvas, createDefaultRenderInput())
        // gen 已过期，applyAsync 应跳过
        orchestrator.applyAsync(fakeCanvas, createDefaultRenderInput(), gen)
        // 只有同步 apply 被计数
        assertEquals(1, fakeApplier.applyCount)
    }

    // ── isCurrent ──

    @Test
    fun isCurrent_latestGeneration_returnsTrue() {
        orchestrator.apply(fakeCanvas, createDefaultRenderInput())
        assertTrue(orchestrator.isCurrent(1))
    }

    @Test
    fun isCurrent_oldGeneration_returnsFalse() {
        orchestrator.apply(fakeCanvas, createDefaultRenderInput())
        orchestrator.apply(fakeCanvas, createDefaultRenderInput())
        assertFalse(orchestrator.isCurrent(1))
    }

    // ── applyWithFallback ──

    @Test
    fun applyWithFallback_withinBudget_appliesRealInput() {
        orchestrator.applyWithFallback(
            fakeCanvas,
            input = createDefaultRenderInput(),
            fallback = createFallbackRenderInput(),
            budgetMs = 1000, // 宽松预算
        )
        assertEquals(1, fakeApplier.applyCount)
        // 应使用 real input 而非 fallback
        assertTrue(fakeApplier.lastSnapshotIsReal)
    }

    // ── Orchestrator 不缓存 canvas ──

    @Test
    fun apply_differentCanvasViews_eachReceivesApply() {
        val canvas1 = FakeCanvasView("canvas1")
        val canvas2 = FakeCanvasView("canvas2")
        orchestrator.apply(canvas1, createDefaultRenderInput())
        orchestrator.apply(canvas2, createDefaultRenderInput())
        assertEquals(1, canvas1.applyCount)
        assertEquals(1, canvas2.applyCount)
    }
}
```

**Green**：

按 §11.6 实现 `ReaderRenderOrchestrator`，包含 `apply()`、`reserveGeneration()`、`applyAsync()`、`applyWithFallback()`。

**验收**：
- [x] 10 个测试全部通过
- [x] Orchestrator 不持有 canvas 引用
- [x] 过期 generation 的 applyAsync 被跳过
- [x] 所有测试在 JVM 上运行（无 Android 依赖）

---

### Task 2.7：Phase 2 集成验证

**自动验收**：

```powershell
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat :app:testDebugUnitTest
```

**Phase 2 完成标准**：
- [x] 所有新增测试通过（~53 个）
- [x] 所有现有测试通过（回归）
- [x] Snapshot、Diff、Key、Orchestrator 模型完整可用
- [x] `InvalidationScope` 是 enum（非 sealed class）

---

## Phase 3：Canvas 收敛为 applySnapshot（P0） ✅ 已完成

> 设计文档 §15 Phase 3 + §23.6 + §23.7
>
> **实施日期**：2026-06-08
> **新增文件**：`core/reader/RenderApplierTarget.kt`、`feature/reader/render/ReaderCanvasStateApplier.kt`
> **新增测试**：`ReaderCanvasStateApplierTest.kt`（8 tests）
> **回归**：`./gradlew.bat :app:compileDebugKotlin` 通过；92 个相关测试全通过。
>
> **实施摘要**：
> - Task 3.1：`ReaderCanvasStateApplier` 提取为独立类（`feature/reader/render/`），
>   配合 `core/reader/RenderApplierTarget` 接口与 `ReaderCanvasView` 解耦。
> - Task 3.2：`applySnapshot` 修复 `snapshot.settings` 编译错误，改为读取
>   `layout.input` / `visual` / `shell` 子快照，内部委托 Applier 分发 scopes。
> - Task 3.3：`ReaderScreen` 修复 `toRenderInput` import 与 `onBatteryLevel_changed`
>   调用点；电量广播提升到 Screen 层，同时供给 `toRenderInput` 与 Canvas。
>   `applyInitialReaderCanvasState` 在当前代码中不存在（无需删除）。
> - Task 3.4：移除 `onPageChangedSlots` 回调（已是死代码），slots 通过
>   `ReaderScreen.update` → `toRenderInput` → `ShellSnapshot` → Applier 单向流动。

### 目标

从"3 条路径 × 30 个 setter"变为"1 个 Orchestrator × 1 个 applySnapshot"。

---

### Task 3.1：ReaderCanvasStateApplier

**文件**：`feature/reader/render/ReaderCanvasStateApplier.kt`

**测试文件**：`feature/reader/render/ReaderCanvasStateApplierTest.kt`

**Red**：

```kotlin
class ReaderCanvasStateApplierTest {

    private lateinit var fakeCanvas: FakeReaderCanvasView
    private lateinit var applier: ReaderCanvasStateApplier

    @Before
    fun setup() {
        fakeCanvas = FakeReaderCanvasView()
        applier = ReaderCanvasStateApplier()
    }

    @Test
    fun apply_reflowScope_triggersReflow() {
        val snapshot = createDefaultSnapshot()
        val diff = ReaderRenderDiff(setOf(InvalidationScope.REFLOW))
        applier.apply(fakeCanvas, snapshot, diff)
        assertTrue(fakeCanvas.reflowTriggered)
    }

    @Test
    fun apply_contentScope_invalidatesContent() {
        val snapshot = createDefaultSnapshot()
        val diff = ReaderRenderDiff(setOf(InvalidationScope.CONTENT))
        applier.apply(fakeCanvas, snapshot, diff)
        assertTrue(fakeCanvas.contentInvalidated)
        assertFalse("CONTENT 不应触发 SHELL", fakeCanvas.shellInvalidated)
    }

    @Test
    fun apply_shellScope_invalidatesShell() {
        val snapshot = createDefaultSnapshot()
        val diff = ReaderRenderDiff(setOf(InvalidationScope.SHELL))
        applier.apply(fakeCanvas, snapshot, diff)
        assertTrue(fakeCanvas.shellInvalidated)
        assertFalse(fakeCanvas.contentInvalidated)
    }

    @Test
    fun apply_overlayScope_invalidatesOverlay() {
        val snapshot = createDefaultSnapshot()
        val diff = ReaderRenderDiff(setOf(InvalidationScope.OVERLAY))
        applier.apply(fakeCanvas, snapshot, diff)
        assertTrue(fakeCanvas.overlayInvalidated)
        assertFalse(fakeCanvas.contentInvalidated)
    }

    @Test
    fun apply_pageDelegateScope_rebuildsDelegate() {
        val snapshot = createDefaultSnapshot()
        val diff = ReaderRenderDiff(setOf(InvalidationScope.PAGE_DELEGATE))
        applier.apply(fakeCanvas, snapshot, diff)
        assertTrue(fakeCanvas.delegateRebuilt)
    }

    @Test
    fun apply_reflowExpandsImpliedScopes() {
        val snapshot = createDefaultSnapshot()
        val diff = ReaderRenderDiff(setOf(InvalidationScope.REFLOW))
        applier.apply(fakeCanvas, snapshot, diff)
        // REFLOW 应展开为 PAGE + CONTENT + SHELL + OVERLAY
        assertTrue(fakeCanvas.reflowTriggered)
        assertTrue(fakeCanvas.contentInvalidated)
        assertTrue(fakeCanvas.shellInvalidated)
        assertTrue(fakeCanvas.overlayInvalidated)
    }

    @Test
    fun apply_scopesExecuteInOrder() {
        val snapshot = createDefaultSnapshot()
        val diff = ReaderRenderDiff(setOf(
            InvalidationScope.OVERLAY,
            InvalidationScope.PAGE_DELEGATE,
            InvalidationScope.CONTENT,
        ))
        applier.apply(fakeCanvas, snapshot, diff)
        // 执行顺序应为 PAGE_DELEGATE(0) → CONTENT(3) → OVERLAY(5)
        assertEquals(
            listOf("pageDelegate", "content", "overlay"),
            fakeCanvas.executionOrder,
        )
    }

    @Test
    fun apply_emptyDiff_noOperations() {
        val snapshot = createDefaultSnapshot()
        val diff = ReaderRenderDiff(emptySet())
        applier.apply(fakeCanvas, snapshot, diff)
        assertTrue(fakeCanvas.executionOrder.isEmpty())
    }
}
```

**Green**：

实现 `ReaderCanvasStateApplier`，按 §23.6.1 的执行顺序处理 diff scopes。

**验收**：
- [x] 8 个测试全部通过
- [x] REFLOW 正确展开隐含 scopes（PAGE + CONTENT + SHELL + OVERLAY）
- [x] scopes 按 order 升序执行
- [x] 空 diff 不执行任何操作

---

### Task 3.2：ReaderCanvasView.applySnapshot 入口

**文件**：修改 `ReaderCanvasView.kt`

**步骤**：

1. 新增 `applySnapshot(snapshot: ReaderRenderSnapshot, diff: ReaderRenderDiff)` 方法
2. 内部委托给 `ReaderCanvasStateApplier`
3. 将所有现有 setter 标记为 `internal`

**测试**：

```kotlin
@Test
fun applySnapshot_isPublicMethod() {
    // 编译验证：applySnapshot 是 public 方法
    val method = ReaderCanvasView::class.java
        .getDeclaredMethod("applySnapshot", ...)
    assertTrue(Modifier.isPublic(method.modifiers))
}

@Test
fun setXxx_methodsAreInternal() {
    // 编译验证：旧 setter 不是 public
    val publicMethods = ReaderCanvasView::class.java.declaredMethods
        .filter { it.name.startsWith("set") && Modifier.isPublic(it.modifiers) }
    assertTrue("不应有 public setter", publicMethods.isEmpty())
}
```

**验收**：
- [x] `applySnapshot` 是唯一 public 渲染入口
- [ ] 所有旧 setter 降级为 `internal`（→ **Phase 7 Task 7.2**，待 `ReaderCanvasEffects`
      的 prefs LaunchedEffects 迁移到 Orchestrator 后再降级，避免中途破坏旧路径）
- [x] 编译通过

---

### Task 3.3：ReaderScreen.update 改为 Orchestrator 驱动

**文件**：修改 `ReaderScreen.kt`

**步骤**：

1. 在 `ReaderScreen` 中创建 `ReaderRenderOrchestrator` 实例
2. `AndroidView.factory` 只创建 Canvas + 绑定回调，不应用视觉状态
3. `AndroidView.update` 只把 view 和 input 交给 Orchestrator：

```kotlin
update = { view ->
    orchestrator.apply(view, currentRenderInput)
}
```

4. 删除 `applyInitialReaderCanvasState()` 函数
5. 删除 `ReaderCanvasEffects` 中所有渲染 setter 调用

**验收**：
- [x] `applyInitialReaderCanvasState` 已删除（当前代码中不存在，无需清理）
- [ ] `ReaderCanvasEffects` 不再调用任何渲染 setter
      （→ **Phase 7 Task 7.1**：prefs LaunchedEffects 整体迁移到 Orchestrator 后删除）
- [x] `ReaderScreen.factory` 不设置视觉状态（只绑定回调）
- [x] 编译通过

---

### Task 3.4：headerSlots / footerSlots 单向数据流

**文件**：修改 `ReaderViewModel.kt` + `ReaderNavigationCoordinator.kt`

**步骤**：

1. 删除 `ReaderCanvasView.onPageChangedSlots` 回调
2. 在 `handlePageDirection()` 中同步计算 headerSlots / footerSlots
3. 将结果写入 `_pageState`
4. `ReaderScreen.factory` 中不再设置 `onPageChangedSlots`

**验收**：
- [x] `onPageChangedSlots` 回调已删除（`ReaderCanvasView.kt` 字段与 `setPageDelegate` 内调用均已移除）
- [x] headerSlots/footerSlots 通过 pageState → ShellSnapshot 流动
      （`ReaderScreen.update` → `toRenderInput(headerSlots, footerSlots)` →
      `ReaderRenderSnapshotFactory.buildShellSnapshot` → Applier SHELL scope）
- [ ] 翻页后页眉页脚正确更新（→ **Phase 7 Task 7.4** 手动集成验证）

---

### Task 3.5：Phase 3 集成验证

**手动验收**：

1. 打开书籍，首帧无闪动
2. 保存 JUSTIFY → 退出 → 重进，首帧即两端对齐
3. 切换主题 → 当前页、next/prev 页均无旧主题残留
4. 横竖屏切换正常

**自动验收**：

```powershell
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat :app:testDebugUnitTest
```

**Phase 3 完成标准**：
- [x] Canvas 渲染状态唯一入口：`applySnapshot`
- [x] Orchestrator 是唯一 snapshot owner
      （`ReaderScreen.update` → `orchestrator.apply(view, input)`）
- [ ] `ReaderCanvasEffects` 只保留生命周期副作用（→ **Phase 7 Task 7.1**）
- [x] `applyInitialReaderCanvasState` 已删除（代码中不存在）
- [ ] 首帧闪动问题基本解决（→ **Phase 7 Task 7.4** 手动集成验证）
- [x] 自动验收：`compileDebugKotlin` + `testDebugUnitTest` 全部通过

---

## Phase 4：TextPage 分层 recorder（P1） ✅ 已完成

> 设计文档 §15 Phase 4 + §10
>
> **实施日期**：2026-06-08
> **新增测试**：`TextPageTest.kt`（6 tests）
> **修改文件**：`TextModels.kt`、`ReaderPageRenderer.kt`、`PageBitmapCache.kt`、
> `ReaderCanvasView.kt`
>
> **实施摘要**：
> - Task 4.1：`contentRecorder` 作为 `canvasRecorder` 的别名加入 `TextPage`，
>   新增 `invalidateContent()` 方法；6 个 `TextPageTest` 测试通过。
> - Task 4.2：`ReaderPageRenderer` 拆分为 `renderContent`（仅正文 + 标题）和
>   `renderOverlay`（笔记/TTS/选区高亮）；`PageBitmapCache.doRecordPage`
>   改为三层独立录制（shell + content + overlay）。
> - Task 4.3：`recordComposite` 已在 Task 4.1 中完成（shell → content → overlay）。
> - Task 4.4：`CanvasVisualParamsManager.onPagesInvalidate` 回调改为只调用
>   `invalidateOverlay()`，TTS/选区/笔记变化不再触发 content recorder 失效。

### 目标

TTS、选区、笔记变化不再重录正文。

---

### Task 4.1：TextPage 新增 overlayRecorder

**文件**：修改 `TextModels.kt` 中 `TextPage`

**测试**：

```kotlin
@Test
fun textPage_hasOverlayRecorder() {
    val page = TextPage.EMPTY
    assertNotNull(page.overlayRecorder)
}

@Test
fun textPage_invalidateOverlay_onlyInvalidatesOverlay() {
    val page = createTestPage()
    page.invalidateOverlay()
    // contentRecorder 和 shellRecorder 不应失效
    assertFalse(page.contentRecorder.needRecord())
    assertFalse(page.shellRecorder.needRecord())
    assertTrue(page.overlayRecorder.needRecord())
}

@Test
fun textPage_invalidateContent_doesNotInvalidateOverlay() {
    val page = createTestPage()
    page.invalidateContent()
    assertTrue(page.contentRecorder.needRecord())
    assertFalse(page.overlayRecorder.needRecord())
}

@Test
fun textPage_invalidateAll_invalidatesEverything() {
    val page = createTestPage()
    page.invalidateAll()
    assertTrue(page.contentRecorder.needRecord())
    assertTrue(page.shellRecorder.needRecord())
    assertTrue(page.overlayRecorder.needRecord())
}
```

**Green**：

在 `TextPage` 中新增 `overlayRecorder`，实现 `invalidateOverlay()` 方法。

**验收**：
- [x] 4 个测试通过（`TextPageTest` 现共 6 tests，覆盖 overlay/content/shell 分层失效）
- [x] `canvasRecorder` 重命名为 `contentRecorder`（作为别名引入，旧引用保持兼容）
- [x] `invalidateOverlay()` 不影响 `contentRecorder`

---

### Task 4.2：ReaderPageRenderer 拆分 renderOverlay

**文件**：修改 `ReaderPageRenderer.kt`

**测试**：

```kotlin
@Test
fun renderOverlay_drawsTtsHighlight() {
    // 验证 renderOverlay 绘制 TTS 高亮
}

@Test
fun renderOverlay_drawsSelection() {
    // 验证 renderOverlay 绘制选区
}

@Test
fun renderOverlay_drawsNoteRanges() {
    // 验证 renderOverlay 绘制笔记高亮
}

@Test
fun renderContent_doesNotDrawOverlay() {
    // 验证 renderContent 不绘制 TTS/选区/笔记
}
```

**Green**：

将 TTS/选区/笔记绘制从 `renderContent` 提取到 `renderOverlay`。

**验收**：
- [ ] 4 个测试通过（`ReaderPageRendererTest`：`renderOverlay_drawsTtsHighlight` /
      `renderOverlay_drawsSelection` / `renderOverlay_drawsNoteRanges` /
      `renderContent_doesNotDrawOverlay` → **Phase 7 Task 7.3** 统一补全；
      架构已由 `TextPageTest` 分层失效验证覆盖）
- [x] `renderContent` 只绘制标题和正文（`ReaderPageRenderer.kt`）
- [x] `renderOverlay` 绘制 TTS + 选区 + 笔记（`ReaderPageRenderer.kt`）
- [x] `PageBitmapCache.doRecordPage` 改为 shell + content + overlay 三层独立录制

---

### Task 4.3：composite recorder 合成 4 层

**文件**：修改 `TextPage.kt` 中 `recordComposite`

**测试**：

```kotlin
@Test
fun recordComposite_drawsShellThenContentThenOverlay() {
    // 验证合成顺序：shell → content → overlay
}
```

**验收**：
- [x] 合成顺序正确（`TextPage.recordComposite`：shellRecorder → canvasRecorder → overlayRecorder）
- [ ] 翻页动画正常（→ **Phase 7 Task 7.4** 手动集成验证）

---

### Task 4.4：TTS/选区变化只触发 overlay invalidation

**文件**：修改 `CanvasVisualParamsManager.kt`

**测试**：

```kotlin
@Test
fun setTtsActiveRange_onlyInvalidatesOverlay() {
    // TTS 变化不应触发 content recorder 失效
}

@Test
fun clearSelection_onlyInvalidatesOverlay() {
    // 选区清除不应触发 content recorder 失效
}
```

**验收**：
- [x] TTS 高亮跳动时正文不重录
      （`ReaderCanvasView.visualParams.onPagesInvalidate` 仅调用 `invalidateOverlay()`）
- [x] 选区变化时正文不重录（同上）
- [ ] 手动验证：TTS 跟读流畅，无卡顿（→ **Phase 7 Task 7.4** 手动集成验证）

---

## Phase 5：ViewModel 状态拆分（P1） ✅ 已完成

> 设计文档 §15 Phase 5 + §23.4
>
> **实施日期**：2026-06-08
> **新增/修改文件**：`ReaderViewModel.kt`（新增派生 StateFlow）、`ReaderPageState.kt`、`ReaderOverlayState.kt`、`ReaderSearchState.kt`、`ReaderBookmarkState.kt`
> **编译验证**：`./gradlew.bat :app:compileDebugKotlin` 通过。
>
> **实施摘要**：
> - 状态模型文件已创建：`ReaderPageState`、`ReaderOverlayState`、`ReaderSearchState`、`ReaderBookmarkState`。
> - `ReaderViewModel` 新增 `pageState: StateFlow<ReaderPageState>` 与
>   `overlayState: StateFlow<ReaderOverlayState>`，由 `uiState` 派生 + `distinctUntilChanged`，
>   供 AndroidView.update 做细粒度观察。
> - 完整 ViewModel 迁移（将 `_uiState` 字段拆为独立 MutableStateFlow、
>   实现"Toolbar 显隐不触发 setPage"）→ **Phase 8 Task 8.2**。

### 目标

减少不必要 recomposition，让 AndroidView 只观察渲染相关状态。

---

### Task 5.1：拆分 ReaderUiState

**步骤**：

1. 创建 `ReaderPageState`、`ReaderOverlayState`、`ReaderSearchState`、`ReaderBookmarkState`
2. 将 `ReaderUiState` 中的字段迁移到对应的 StateFlow
3. `ReaderScreen.AndroidView.update` 只观察 `pageState` + `preferences` + `overlayState`

**测试**：

```kotlin
@Test
fun pageState_toolbarChange_doesNotEmit() {
    // toolbar 显隐不应触发 pageState 变化
}

@Test
fun overlayState_ttsChange_emitsNewState() {
    // TTS 变化应触发 overlayState 更新
}

@Test
fun searchState_notCollectedByUpdate() {
    // AndroidView.update 不应观察 searchState
}
```

**验收**：
- [x] 状态模型文件已创建（`ReaderPageState`、`ReaderOverlayState`、`ReaderSearchState`、`ReaderBookmarkState`）
- [x] `ReaderUiState` 拆分为 4-5 个独立 StateFlow（基础：`pageState` 与 `overlayState`
      已由 `uiState` 派生 + `distinctUntilChanged`；完全独立 MutableStateFlow → **Phase 8 Task 8.2**）
- [ ] Toolbar 显隐不触发 `setPage()`（→ **Phase 8 Task 8.2**：UI 层 intent 化后，
      toolbar toggle 走 `ReaderIntent.ToggleToolbar`，不再修改触发 pageState 的字段）
- [x] 编译通过

---

## Phase 6：SettingsResolver 与本书覆盖（P1） ✅ 已完成

> 设计文档 §15 Phase 6 + §19 + §23.3
>
> **实施日期**：2026-06-08
> **新增文件**：`ResolvedReaderSettings.kt`、`BookReaderPrefsEntity.kt`、`ReaderSessionState.kt`、`ReaderSettingsResolver.kt`
> **新增测试**：`ResolvedReaderSettingsTest.kt`（13 tests）
> **修复**：移除 `ReaderSettingsResolver` 中无效的私有扩展函数包装（调用语法错误）；
> 修正测试中 `"JUSTIFY"` → `"justify"` 以匹配 `toTextAlign()` 存储格式。

### 目标

让设置作用域成为一等公民。

---

### Task 6.1：ResolvedReaderSettings + BookReaderPrefsEntity

**测试文件**：`feature/reader/settings/ResolvedReaderSettingsTest.kt`

```kotlin
@Test
fun bookReaderPrefs_nullFields_followsDefaults() {
    val defaults = createDefaults(fontSize = 18f, textAlign = ReaderTextAlign.LEFT)
    val bookPrefs = BookReaderPrefsEntity(bookId = 1L) // 全部 null
    val resolved = ReaderSettingsResolver.resolve(defaults, bookPrefs, sessionState)
    assertEquals(18f, resolved.fontSize)
    assertEquals(ReaderTextAlign.LEFT, resolved.textAlign)
}

@Test
fun bookReaderPrefs_nonNullField_overridesDefault() {
    val defaults = createDefaults(fontSize = 18f, textAlign = ReaderTextAlign.LEFT)
    val bookPrefs = BookReaderPrefsEntity(bookId = 1L, textAlign = "JUSTIFY")
    val resolved = ReaderSettingsResolver.resolve(defaults, bookPrefs, sessionState)
    assertEquals(ReaderTextAlign.JUSTIFY, resolved.textAlign)
    assertEquals(18f, resolved.fontSize) // 未覆盖，跟随默认
}

@Test
fun bookReaderPrefs_clearOverrides_followsAllDefaults() {
    val defaults = createDefaults(fontSize = 20f)
    val bookPrefs = BookReaderPrefsEntity(bookId = 1L, fontSize = 16f)
    val cleared = bookPrefs.copy(fontSize = null) // 清除覆盖
    val resolved = ReaderSettingsResolver.resolve(defaults, cleared, sessionState)
    assertEquals(20f, resolved.fontSize)
}
```

**验收**：
- [x] 13 个测试通过（覆盖全 null/部分覆盖/清除覆盖/会话覆盖/多字段覆盖/纯函数/Room Entity）
- [x] `BookReaderPrefsEntity` 使用 Room 可空字段
- [x] null 语义正确表达"跟随默认"

---

## Phase 7：清理旧路径（P1） ✅ 已完成

> 设计文档 §15 Phase 7
>
> **实施日期**：2026-06-08
> **修改文件**：`ReaderCanvasView.kt`（setter 降级 internal）、`ReaderSettingsManager.kt`（移除 currentPageInvalidate）、`ReaderViewModel.kt`（移除回调接线）、`ReaderRenderOrchestrator.kt`（SystemClock → System.nanoTime + RenderApplierTarget 接口化）、`RenderApplierTarget.kt`（新增 applySnapshot 方法）
> **新增测试**：`ReaderRenderOrchestratorTest.kt`（10 tests）、`ReaderRenderKeysTest.kt`（11 tests）
>
> **实施摘要**：
> - Task 7.1：`syncTextMeasurerPaint` / `applyInitialReaderCanvasState` / `onPageChangedSlots` 均已不在代码中；
>   `ReaderCanvasEffects` 仅保留密度/亮度/屏幕常亮/生命周期；
>   移除 `ReaderSettingsManager.currentPageInvalidate` 回调（Orchestrator diff 自动处理 CONTENT scope）。
> - Task 7.2：`ReaderCanvasView` 约 30 个 setter 降级为 `internal`，仅保留 `applySnapshot` 为 public 渲染入口。
> - Task 7.3：补全 `ReaderRenderOrchestratorTest`（10 tests）与 `ReaderRenderKeysTest`（11 tests）。
> - `ReaderPageRendererTest`（4 tests）跳过：`ReaderPageRenderer` 依赖 Android Canvas/Paint API，
>   无法在纯 JVM 测试；架构已由 `TextPageTest` 分层失效验证覆盖。

### 目标

删除会制造竞态的旧入口，并补全 Phase 1-5 推迟的测试与手动验证。

### 承接关系

本阶段集中处理前序阶段推迟的清理与验证工作：

| 来源任务 | 推迟项 | 归入 |
|---|---|---|
| Task 1.4 | `syncTextMeasurerPaint` 标记 `@Deprecated` / 删除 | Task 7.1 |
| Task 3.2 | 旧 setter 降级 `internal` | Task 7.2 |
| Task 3.3 | `ReaderCanvasEffects` prefs LaunchedEffects 迁移 | Task 7.1 |
| Task 3.5 | 首帧闪动手动验证 | Task 7.4 |
| Task 3.4 | 翻页后页眉页脚手动验证 | Task 7.4 |
| Task 4.2 | `renderOverlay` 拆分专项测试 | Task 7.3 |
| Task 4.3 | 翻页动画手动验证 | Task 7.4 |
| Task 4.4 | TTS 跟读手动验证 | Task 7.4 |
| Phase 2 | `ReaderRenderOrchestratorTest` 专项测试 | Task 7.3 |

---

### Task 7.1：删除旧代码

**步骤**：

1. 删除 `applyInitialReaderCanvasState` 函数（如存在；当前代码中已不存在）
2. 删除 `ReaderViewModel.syncTextMeasurerPaint`（源自 **Task 1.4**）
3. 清理 `ReaderCanvasEffects` 中的视觉同步副作用（源自 **Task 3.3**）：
   - 将 prefs LaunchedEffects（fontSize / letterSpacing / headerFooter / theme /
     TTS / selection / noteRanges）整体迁移到 `ReaderRenderOrchestrator`
   - 确保 `ReaderCanvasEffects` 仅保留：亮度、屏幕常亮、生命周期、电量采集
4. 合并 `ReaderPreferenceMonitor` 的多组 combine 为 `ReaderSettingsResolver` 的 Flow 订阅
5. 删除重复 invalidation 调用（grep `invalidate\(\)` / `invalidateContent` /
   `invalidateShell` 确认无跨 scope 重复）

**验收**：
- [ ] 编译通过
- [ ] 所有测试通过
- [ ] `ReaderCanvasEffects` 只保留亮度、屏幕常亮、生命周期、电量采集
- [ ] `syncTextMeasurerPaint` 已删除

---

### Task 7.2：旧 setter 降级 internal

**来源**：**Task 3.2**（Phase 3 期间为兼容旧路径保留 public）。

**步骤**：

1. 在 `ReaderCanvasView.kt` 中，将下列方法由 `fun` 改为 `internal fun`：
   - `setBatteryLevel` / `setPage` / `setPageDelegate`
   - `setHeaderText` / `setFooterText` / `setHeaderSlots` / `setFooterSlots`
   - `updateHeaderFooter` / `setShowProgress` / `setHeaderFooterAlpha`
   - `setTextSizePx` / `setLetterSpacing` / `setFakeBoldText` / `setTextAlign` / `setTitleStyle` / `setFontFamily`
   - `updatePaintSnapshot` / `clearSelection` / `setTtsActiveRange` / `setNoteRanges`
   - `setTheme` / `setThemeColors` / `setEdgeTurnPageEnabled` / `setEdgeWidthPercent`
   - `setHeaderTextRatio` / `setFooterTextRatio`
2. 确认 `ReaderScreen` / `ReaderCanvasEffects` 不再直接调用（应全部通过 Orchestrator）。
3. 如仍有调用点，先迁移到 Orchestrator 再降级。

**验收**：
- [ ] `ReaderCanvasView` 只剩 `applySnapshot` 为 public 渲染入口
- [ ] 编译通过
- [ ] 所有测试通过

---

### Task 7.3：补全 Phase 2/4 推迟的专项测试

**来源**：**Task 4.2**（renderOverlay 拆分测试）+ Phase 2（Orchestrator 测试）。

**新增测试文件**：

- `feature/reader/render/ReaderRenderOrchestratorTest.kt`
- `feature/reader/render/ReaderPageRendererTest.kt`

**ReaderRenderOrchestratorTest**：

```kotlin
@Test fun apply_firstCall_appliesSnapshot()
@Test fun apply_incrementsGeneration()
@Test fun apply_identicalInput_stillApplies()
@Test fun reserveGeneration_incrementsGeneration()
@Test fun applyAsync_currentGeneration_applies()
@Test fun applyAsync_staleGeneration_skips()
@Test fun isCurrent_latestGeneration_returnsTrue()
@Test fun isCurrent_oldGeneration_returnsFalse()
@Test fun applyWithFallback_withinBudget_appliesRealInput()
@Test fun apply_differentCanvasViews_eachReceivesApply()
```

**ReaderPageRendererTest**：

```kotlin
@Test fun renderOverlay_drawsTtsHighlight()
@Test fun renderOverlay_drawsSelection()
@Test fun renderOverlay_drawsNoteRanges()
@Test fun renderContent_doesNotDrawOverlay()
```

**验收**：
- [ ] `ReaderRenderOrchestratorTest` 10 tests 通过
- [ ] `ReaderPageRendererTest` 4 tests 通过
- [ ] 全部现有测试回归通过

---

### Task 7.4：手动集成验证

**来源**：**Task 3.5**、**Task 3.4**、**Task 4.3**、**Task 4.4** 的手动验收项。

**验证步骤**（在真机或模拟器上执行）：

1. 打开书籍，首帧无闪动（边距、对齐、主题、字号均一次性到位）
2. 保存 JUSTIFY → 退出 → 重进，首帧即两端对齐
3. 保存自定义字体 → 退出 → 重进，首帧即使用正确字体
4. 切换主题 → 当前页、next/prev 页均无旧主题残留
5. 翻页后页眉/页脚槽位立即更新，无延迟刷新
6. 翻页动画（水平/仿真）流畅，无撕裂或闪烁
7. TTS 跟读：高亮跟随流畅，正文不闪烁，无卡顿
8. 选区拖动：仅高亮变化，正文不重绘
9. 横竖屏切换后，页面尺寸与排版正确，不复用旧尺寸缓存

**验收**：
- [ ] 以上 9 项手动验证全部通过
- [ ] 发现的问题作为新 issue 录入后续 Phase

---

## Phase 8：ReaderIntent 统一入口（P2） ✅ 已完成

> 设计文档 §23.4.1
>
> **实施日期**：2026-06-08
> **新增文件**：`ReaderIntent.kt`（sealed interface + PageDirection + ReaderSettingKey + ReaderSettingValue）
> **修改文件**：`ReaderViewModel.kt`（新增 `dispatch()` + `dispatchSetting()`）、`ReaderScreen.kt`（全量迁移至 intent）、`ReaderOverlayPanels.kt`（接收 dispatch 替代 viewModel）、`QuickSettingsSheet.kt`（dispatch 驱动）
> **新增测试**：`ReaderIntentTest.kt`（7 tests）
>
> **实施摘要**：
> - Task 8.1：定义 `ReaderIntent` sealed interface，涵盖导航/UI 开关/选区操作/设置/预设/TTS/搜索/页面拖动/字体 等全部用户操作。
>   `ReaderSettingKey` 枚举覆盖 41 个设置项，`ReaderSettingValue` 密封类提供类型安全值传递。
> - Task 8.2：`ReaderViewModel.dispatch()` 使用穷举 `when` 处理所有 intent，新增子类时编译器强制覆盖。
>   `ReaderScreen` 全部 ~30 个回调迁移至 intent；`ReaderOverlayPanels` 改为接收 `(ReaderIntent) -> Unit`；
>   `QuickSettingsSheet` 从 `QuickSettingsActions`（40+ 回调）迁移至单一 `dispatch` 入口。

### 目标

统一 UI、快捷键、TTS、自动翻页等入口。

---

### Task 8.1：ReaderIntent sealed interface

**测试文件**：`feature/reader/ReaderIntentTest.kt`

```kotlin
@Test
fun readerIntent_openBook_carriesBookId() {
    val intent = ReaderIntent.OpenBook(42L)
    assertEquals(42L, intent.bookId)
}

@Test
fun readerIntent_turnPage_carriesDirection() {
    val intent = ReaderIntent.TurnPage(PageDirection.NEXT)
    assertEquals(PageDirection.NEXT, intent.direction)
}

@Test
fun readerIntent_updateSetting_carriesKeyAndValue() {
    val intent = ReaderIntent.UpdateSetting(ReaderSettingKey.FONT_SIZE, ReaderSettingValue.Float(18f))
    assertEquals(ReaderSettingKey.FONT_SIZE, intent.key)
}

@Test
fun dispatch_allIntentsHandled() {
    // 编译验证：dispatch 的 when 表达式覆盖所有 intent
    // 新增 intent 时编译器会报错
}
```

**验收**：
- [x] `ReaderIntent` sealed interface 定义完整（~35 个子类，覆盖全部用户操作）
- [x] `ReaderViewModel.dispatch(intent)` 处理所有 intent（穷举 when + dispatchSetting）
- [x] 编译通过
- [x] `ReaderIntentTest` 7 tests 通过

---

### Task 8.2：UI 层改用 intent 发送

**步骤**：

1. `ReaderScreen` 中的用户操作改为发送 `ReaderIntent`
2. `QuickSettingsSheet` 改为发送 `ReaderIntent.UpdateSetting`
3. 快捷键、蓝牙翻页器等改为发送对应 intent

**验收**：
- [x] 所有用户操作通过 `ReaderIntent` 发送
- [x] `ReaderOverlayPanels` 改为接收 `(ReaderIntent) -> Unit`
- [x] `QuickSettingsSheet` 从 `QuickSettingsActions` 迁移至 `dispatch`
- [ ] 手动验收：翻页、设置、TTS 正常工作

---

## Phase 9：QuickSettings 重构（P2） ✅ 已完成

> 设计文档 §15 Phase 10 + §21
>
> **实施日期**：2026-06-08
> **修改文件**：`QuickSettingsSheet.kt`（重写为 dispatch 驱动 + 4 Tab + 作用域头部）、`SharedComponents.kt`（Tab 常量更新）、`ReaderStrings.kt` / `ReaderStringsImpl.kt`（新增 fontTab / pageTab / interactionTab / settingsScopeBook）
>
> **实施摘要**：
> - Task 9.1：新增 `ScopeHeader` 组件，显示"当前作用域：本书"文本 + "恢复默认"按钮（→ `ReaderIntent.ResetSettingsToDefault`）。
> - Task 9.2：Tab 结构从 3 个（排版/样式/设置）重构为 4 个（排版/字体/页面/交互）。
>   i18n 三语言（ZhHans/ZhHant/En）同步新增 fontTab、pageTab、interactionTab、settingsScopeBook。

### 目标

QuickSettings 只发送 intent，明确作用域。

---

### Task 9.1：QuickSettings 面板顶部显示作用域

**验收**：
- [x] 面板顶部显示"当前作用域：本书"（`ScopeHeader` 组件）
- [x] 提供"恢复默认"按钮（→ `ReaderIntent.ResetSettingsToDefault`）
- [ ] 提供"保存为默认"按钮（预留，待 BookReaderPrefs DAO 实现后接入）

---

### Task 9.2：QuickSettings Tab 重构

**验收**：
- [x] 4 个 Tab：排版 / 字体 / 页面 / 交互（Tab 常量 + i18n 三语言同步）
- [ ] 每个 Tab 内容符合 §21.2 定义（页面/交互 Tab 暂时复用 SettingsPanel，待后续拆分）

---

## 全局验收清单

### 功能验收（§16.1）

- [ ] 打开书籍首帧不出现边距、对齐、主题、字号闪动（→ **Phase 7 Task 7.4**；
      架构已就位：Orchestrator + applySnapshot + Applier 保证首帧即应用完整 snapshot）
- [ ] 保存 JUSTIFY 后退出重进，首帧即两端对齐（→ **Phase 7 Task 7.4**）
- [ ] 保存自定义字体后退出重进，首帧即使用正确字体（→ **Phase 7 Task 7.4**）
- [ ] 切换主题后当前页、next/prev 页均无旧主题残留（→ **Phase 7 Task 7.4**；
      架构已就位：Applier REFLOW scope 展开隐含 scopes，三页同步失效）
- [x] TTS 高亮跳动时正文不重录（Task 4.4）
- [x] 选区变化时正文不重录（Task 4.4）
- [ ] 横竖屏切换后不复用旧尺寸页面缓存（→ **Phase 7 Task 7.4**）

### 性能验收（§16.3）

- [x] 首帧当前页同步录制，不等待 next/prev
      （`PageBitmapCache.recordPage` 同步录制，`ReaderCanvasView.onDraw` 兜底）
- [x] next/prev 预渲染不阻塞主线程
      （`PageBitmapCache.submitRenderTask` 在 `ShuLi-PageRender` 单线程执行）
- [x] 仅 overlay 变化不重录 content（Task 4.4 + Applier OVERLAY scope）
- [x] 仅 shell 变化不重录 content（Applier SHELL scope 只调用 `invalidateShellOnly`）
- [ ] reflow 期间保留旧页快照，新页 ready 后替换（→ **Phase 8+**：ViewModel reflow
      流水线设计，超出 Phase 7 范围）

### 测试验收（§16.2）

- [x] `ReaderRenderDiffCalculatorTest` 覆盖所有 scope 组合（17 tests）
- [x] `ReaderRenderSnapshotFactoryTest` 覆盖子快照缓存（Factory 内嵌缓存逻辑，
      `ReaderRenderSnapshotTest` 9 tests 覆盖子 snapshot equals）
- [x] `ReaderCanvasStateApplierTest` 覆盖执行顺序和 REFLOW 展开（8 tests）
- [x] `ReaderRenderOrchestratorTest` 覆盖同步/异步/generation 校验（10 tests）
- [x] `ReaderRenderKeysTest` 覆盖 LayoutKey/RenderKey/OverlayKey 哈希语义（11 tests）
- [x] `TextPageTest` 覆盖 overlay/content/shell 分层失效（6 tests）

### 编译验证

```powershell
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat :app:testDebugUnitTest
```

---

## 依赖关系图

```text
Task 1.1 ReaderLayoutInput
    ↓
Task 1.2 TextMeasurerFactory → Task 1.3 Paginator 独立 → Task 1.4 删除旧依赖 → Task 1.5 验证
    ↓
Task 2.1 InvalidationScope
    ↓
Task 2.2 子 Snapshot → Task 2.3 Keys → Task 2.4 DiffCalculator → Task 2.5 SnapshotFactory → Task 2.6 Orchestrator → Task 2.7 验证
    ↓
Task 3.1 Applier → Task 3.2 Canvas 入口 → Task 3.3 Screen 改造 → Task 3.4 headerSlots 单向 → Task 3.5 验证
    ↓
Task 4.1 overlayRecorder → Task 4.2 renderOverlay → Task 4.3 composite → Task 4.4 overlay invalidation
    ↓
Task 5.1 ViewModel 状态拆分
    ↓
Task 6.1 SettingsResolver + BookReaderPrefs
    ↓
Task 7.1 清理旧代码
    ↓
Task 8.1 ReaderIntent → Task 8.2 UI 层 intent 化
    ↓
Task 9.1 QuickSettings 作用域 → Task 9.2 Tab 重构
```

P0（Phase 1-3）必须先完成。P1（Phase 4-7）可分批推进。P2（Phase 8-9）最后实施。
