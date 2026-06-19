# 36 - Page Box Model 实施任务清单

> 关联设计文档：`docs/35-page-box-model-design.md`
> 创建日期：2026-06-17

---

## 总览

| Phase | 名称 | 任务数 | 前置依赖 |
|-------|------|--------|---------|
| 1 | 数据模型 + 布局引擎 | 8 | 无 |
| 2 | Paginator + TextPage 迁移 | 10 | Phase 1 |
| 3 | 渲染器迁移 | 8 | Phase 2 |
| 4 | 偏好设置 + 设置界面 | 14 | Phase 1 |
| 5 | 系统集成 + 清理 + 测试 | 7 | Phase 3 + 4 |

Phase 1/4 可并行开发（无交叉依赖）。Phase 2 依赖 Phase 1。Phase 3 依赖 Phase 2。Phase 5 收尾。

---

## Phase 1: 数据模型 + 布局引擎

### T1.1 新增 BoxInsetsDp + BoxInsetsPx

- **文件**：`core/reader/model/BoxInsets.kt`（新增）
- **内容**：
  - `@Serializable data class BoxInsetsDp(top, bottom, left, right)` + `toPx(density)` + `companion object { val ZERO }`
  - `data class BoxInsetsPx(top, bottom, left, right)` + `companion object { val ZERO }`
- **验收**：编译通过，`BoxInsetsDp(1f,2f,3f,4f).toPx(2f) == BoxInsetsPx(2f,4f,6f,8f)`

### T1.2 新增 BoxSpec + BoxBounds + PageLayout

- **文件**：`core/reader/model/BoxSpec.kt`（新增）
- **内容**：
  - `data class BoxSpec(insets, innerHeight, placement, visible)` + `enum class Placement { TOP_DOWN, BOTTOM_UP, FILL }`
  - `data class BoxBounds(left, top, right, bottom)` + 构造时计算 `width`/`height`（非 `get()`），无 `toRectF()`
  - `data class PageLayout(header?, title?, body, footer?, pageWidth, pageHeight)`
- **验收**：编译通过，`BoxBounds(0f,0f,100f,50f).width == 100f`，`BoxBounds(0f,0f,100f,50f).height == 50f`

### T1.3 新增 PageLayoutCalculator

- **文件**：`core/reader/engine/PageLayoutCalculator.kt`（新增）
- **内容**：
  - `object PageLayoutCalculator`
  - `fun calculate(pageSize, header, title, body, footer): PageLayout`
    - 内部调用 `placeTopDown()` 处理 header/title
    - footer 从底部向上放置
    - body 填充剩余空间，`bodyBottom = (footerBounds?.let { it.top - footer.insets.top } ?: pageHeight) - body.insets.bottom`
    - `bodyBottom.coerceAtLeast(bodyTop)` 防止 footer 过大
  - `private fun placeTopDown(spec, pageWidth, cursorY): BoxBounds?`
  - `fun bodyWidth(pageSize, bodyInsets): Float`
- **验收**：
  - 全部可见时：header.top > 0, title.top > header.bottom, body.top > title.bottom, footer.bottom == pageHeight
  - title 不可见时：body.top 紧接 header 之下
  - 全部不可见时：body 占满页面减去 bodyInsets
  - footer insets.top 正确参与 body 底部计算

### T1.4 ReaderLayoutConfig 改造

- **文件**：`core/reader/model/TextModels.kt`
- **改动**：
  - 移除：`marginTop`, `marginBottom`, `marginLeft`, `marginRight`, `headerMarginTop`, `footerMarginBottom`, `marginHorizontal`(get), `marginVertical`(get)
  - 新增：`headerInsets: BoxInsetsPx`, `titleInsets: BoxInsetsPx`, `bodyInsets: BoxInsetsPx`, `footerInsets: BoxInsetsPx`
  - 新增：`titleTypeface: Typeface = Typeface.DEFAULT`, `titleIsFakeBold: Boolean = true`
- **依赖**：T1.1
- **验收**：编译通过（预期大量编译错误，由后续任务修复）

### T1.5 ReaderPreferences 字段改造

- **文件**：`core/data/ReaderPreferences.kt`
- **改动**：
  - 移除：`marginHorizontal`, `marginVertical`, `marginTop?`, `marginBottom?`, `marginLeft?`, `marginRight?`
  - 新增：`bodyBox: BoxInsetsDp = BoxInsetsDp(48f,48f,24f,24f)`, `headerBox: BoxInsetsDp = BoxInsetsDp(16f,0f,24f,24f)`, `footerBox: BoxInsetsDp = BoxInsetsDp(0f,16f,24f,24f)`, `titleBox: BoxInsetsDp = BoxInsetsDp(9f,10f,24f,24f)`
- **依赖**：T1.1
- **验收**：编译通过（预期大量编译错误，由后续任务修复）

### T1.6 toLayoutConfig 改造

- **文件**：`core/data/ReaderPreferences.kt`
- **改动**：
  - `toLayoutConfig()` 内部改用 `headerBox.toPx(density)` 等替代旧的 `marginTop ?: marginVertical` 模式
  - 新增 `titleTypeface = resolveTitleTypeface(readingFont)` 和 `titleIsFakeBold = fontWeight == ReaderFontWeight.BOLD`
  - 新增 `resolveTitleTypeface()` 辅助函数（从 FontManager 获取当前阅读字体的 Typeface）
- **依赖**：T1.4, T1.5
- **验收**：`toLayoutConfig()` 返回的 `ReaderLayoutConfig` 的 `bodyInsets` 等字段值正确（dp × density）

### T1.7 HeaderConfig / FooterConfig 移除 margin 字段

- **文件**：`core/reader/model/HeaderFooterModels.kt`
- **改动**：
  - `HeaderConfig` 移除 `marginTop: Float` 字段
  - `FooterConfig` 移除 `marginBottom: Float` 字段
- **依赖**：无
- **验收**：编译通过

### T1.8 修复 Phase 1 编译错误

- **文件**：多个
- **内容**：修复因 T1.4/T1.5/T1.7 导致的所有编译错误（引用旧 margin 字段的地方）
- **依赖**：T1.4, T1.5, T1.7
- **验收**：`./gradlew compileDebugKotlin` 通过

---

## Phase 2: Paginator + TextPage 迁移

### T2.1 TextPage 改造

- **文件**：`core/reader/model/TextModels.kt`
- **改动**：
  - 移除：`pageSize`, `marginHorizontal`, `topContentY`, `headerMarginTop`, `footerMarginBottom`
  - 新增：`val layout: PageLayout`
  - 新增：`val titleLayout: StaticLayout?`
  - 保留 `class`（非 data class），`equals`/`hashCode` 基于 `System.identityHashCode`
- **依赖**：T1.2
- **验收**：编译通过（预期大量编译错误）

### T2.2 提取常量

- **文件**：`core/reader/engine/Paginator.kt`
- **改动**：
  - 新增 `companion object` 中的常量：
    - `internal const val HEADER_CONTENT_HEIGHT_DP = 24f`
    - `internal const val FOOTER_CONTENT_HEIGHT_DP = 24f`
    - `private const val EPSILON = 0.5f`
  - 将散落的 `24f * config.density` 替换为 `HEADER_CONTENT_HEIGHT_DP * config.density`
- **依赖**：无
- **验收**：所有 `24f` 魔法数字被常量替代

### T2.3 新增 buildTitleLayout

- **文件**：`core/reader/engine/Paginator.kt`
- **改动**：
  - 新增 `private fun buildTitleLayout(config, chapterTitle, availableWidth): Pair<StaticLayout?, Float>`
  - 使用 `TextPaint` 设置 `textSize`、`typeface = config.titleTypeface`、`isFakeBoldText = config.titleIsFakeBold`
  - 使用 `StaticLayout.Builder.obtain()` 构建
  - 返回 `Pair(layout, totalHeight)`，其中 `totalHeight = titleStyle.marginTopDp * d + layout.height + titleStyle.marginBottomDp * d`
  - 移除旧的 `calcTitleAreaHeight` 方法
- **依赖**：T1.4（需要 `titleTypeface` 字段）
- **验收**：
  - 隐藏标题时返回 `null to 0f`
  - 单行标题的 `totalHeight` 与 `drawChapterTitle` 渲染的实际高度一致
  - 多行标题的行数与渲染结果一致

### T2.4 paginateChapter 改造

- **文件**：`core/reader/engine/Paginator.kt`
- **改动**：
  - `PageLayout` 不再按章共享，改为按页生成（while 循环内每页调用 `PageLayoutCalculator.calculate()`）
  - 首页：`title.visible = isFirstPage && titleResult.first != null`, `title.innerHeight = titleResult.second`
  - 后续页：`title.visible = false`, `title.innerHeight = 0f`
  - 使用 `PageLayoutCalculator.bodyWidth()` 预计算标题可用宽度
  - 传递 `titleLayout = if (isFirstPage) titleResult.first else null` 给 `paginatePage`
- **依赖**：T1.3, T2.2, T2.3
- **验收**：
  - 首页的 `TextPage.layout.title != null`
  - 后续页的 `TextPage.layout.title == null`
  - 后续页的 `body.top` 紧接 header 之下（无标题区域浪费）

### T2.5 paginatePage 改造

- **文件**：`core/reader/engine/Paginator.kt`
- **改动**：
  - 签名新增 `layout: PageLayout` 和 `titleLayout: StaticLayout?` 参数
  - 移除旧的 `startY`/`maxY`/`availableWidth` 计算逻辑
  - 改用 `val body = layout.body`, `val availableWidth = body.width`, `var currentY = body.top`, `val maxY = body.bottom`
  - 新增 `var mustPlaceAtLeastOneLine = true` 防死循环
  - while 条件改为 `(currentY + lineHeight <= maxY + EPSILON || mustPlaceAtLeastOneLine) && currentOffset < content.length`
  - 构造 `TextPage` 时传入 `layout` 和 `titleLayout`
- **依赖**：T2.1, T2.4
- **验收**：
  - 正常内容分页结果与旧方案一致（行数、字符偏移）
  - 极端边距（body.height < lineHeight）不导致死循环，至少排版一行

### T2.6 更新 TextPage 构造点

- **文件**：搜索所有 `TextPage(` 调用点
- **改动**：
  - 所有构造 `TextPage` 的地方需要传入 `layout` 和 `titleLayout` 参数
  - 移除旧的 `pageSize`, `marginHorizontal`, `topContentY`, `headerMarginTop`, `footerMarginBottom` 参数
- **依赖**：T2.1
- **验收**：编译通过

### T2.7 更新 TextPage 字段访问方

- **文件**：搜索所有 `page.pageSize`, `page.marginHorizontal`, `page.topContentY`, `page.headerMarginTop`, `page.footerMarginBottom` 访问
- **改动**：
  - `page.pageSize.width` → `page.layout.pageWidth`
  - `page.pageSize.height` → `page.layout.pageHeight`
  - `page.marginHorizontal` → `page.layout.body.left`（或 `body.right`，视上下文）
  - `page.topContentY` → `page.layout.body.top`
  - `page.headerMarginTop` → `page.layout.header?.top ?: 0f`
  - `page.footerMarginBottom` → `page.layout.footer?.bottom ?: page.layout.pageHeight`
- **依赖**：T2.1
- **验收**：编译通过

### T2.8 更新 ReaderLayoutConfig 字段访问方

- **文件**：搜索所有 `config.marginTop`, `config.marginBottom`, `config.marginLeft`, `config.marginRight`, `config.headerMarginTop`, `config.footerMarginBottom`, `config.marginHorizontal`, `config.marginVertical` 访问
- **改动**：
  - `config.marginLeft` / `config.marginRight` → `config.bodyInsets.left` / `config.bodyInsets.right`
  - `config.marginTop` / `config.marginBottom` → `config.bodyInsets.top` / `config.bodyInsets.bottom`
  - `config.headerMarginTop` → `config.headerInsets.top`
  - `config.footerMarginBottom` → `config.footerInsets.bottom`
  - `config.marginHorizontal` → 移除（使用 `config.bodyInsets.left`）
  - `config.marginVertical` → 移除（使用 `config.bodyInsets.top`）
- **依赖**：T1.4
- **验收**：编译通过

### T2.9 更新 VerticalPaginationStrategy

- **文件**：`core/reader/engine/VerticalPaginationStrategy.kt`
- **改动**：适配新的 `ReaderLayoutConfig` 和 `TextPage` 字段
- **依赖**：T2.1, T2.8
- **验收**：编译通过

### T2.10 Phase 2 编译验证

- **内容**：`./gradlew compileDebugKotlin` 全量编译通过
- **依赖**：T2.1 - T2.9
- **验收**：零编译错误

---

## Phase 3: 渲染器迁移

### T3.1 renderShell 改造

- **文件**：`core/reader/engine/ReaderPageRenderer.kt`
- **改动**：
  - 背景：`canvas.drawRect(0f, 0f, layout.pageWidth, layout.pageHeight, backgroundPaint)`（四参数重载）
  - 页眉：`layout.header?.let { box -> ... }`，baseline = `box.top + box.height * 0.6f`
  - 页脚：`layout.footer?.let { box -> ... }`，baseline = `box.bottom - box.height * 0.4f`
  - 分割线：Y 坐标使用 `.roundToInt().toFloat()` 像素对齐
  - 进度条：`canvas.drawRect(0f, layout.pageHeight - 3f*density, progressWidth, layout.pageHeight, progressPaint)`
  - 移除所有 `page.marginHorizontal`, `page.headerMarginTop`, `page.footerMarginBottom` 引用
- **依赖**：T2.1, T2.7
- **验收**：页眉/页脚/进度条渲染位置与旧方案一致

### T3.2 drawHeaderFooter 改造

- **文件**：`core/reader/engine/ReaderPageRenderer.kt`
- **改动**：
  - 签名从 `(canvas, slots, paint, alpha, baseline, page: TextPage, batteryLevel, density)` 改为 `(canvas, slots, paint, alpha, baseline, box: BoxBounds, batteryLevel, density)`
  - 水平定位：`box.left` / `(box.left + box.right) / 2f` / `box.right`
  - 移除 `page.marginHorizontal` 引用
- **依赖**：T3.1
- **验收**：页眉页脚三槽位绘制位置正确

### T3.3 drawChapterTitle 改造

- **文件**：`core/reader/engine/ReaderPageRenderer.kt`
- **改动**：
  - 签名从 `(canvas, page: TextPage, density)` 改为 `(canvas, titleLayout: StaticLayout, titleBox: BoxBounds, density)`
  - 移除 `StaticLayout.Builder.obtain()` 调用（使用预计算的 `titleLayout`）
  - 移除 `TextPaint` 创建
  - 移除 `if (page.pageIndex != 0) return` 冗余判断
  - 垂直定位：`titleBox.top + titleStyle.marginTopDp * density`
  - 水平定位：`titleBox.left`
- **依赖**：T3.1
- **验收**：标题渲染位置与旧方案一致，无对象分配

### T3.4 renderContent 改造

- **文件**：`core/reader/engine/ReaderPageRenderer.kt`
- **改动**：
  - 标题绘制改为 `page.titleLayout?.let { titleLayout -> page.layout.title?.let { titleBox -> drawChapterTitle(canvas, titleLayout, titleBox, page.density) } }`
  - 正文行绘制逻辑不变
- **依赖**：T3.3
- **验收**：标题和正文渲染正确

### T3.5 PageRenderContext 改造

- **文件**：`core/reader/engine/PageRenderContext.kt`
- **改动**：
  - `availableWidth` 的来源改为 `page.layout.body.width`（在构造时计算）
  - 或在调用方传入 `ctx.page.layout.body.width`
- **依赖**：T2.1
- **验收**：`availableWidth` 值正确

### T3.6 StatelessReaderPageRenderer 适配

- **文件**：`core/reader/engine/StatelessReaderPageRenderer.kt`
- **改动**：适配 `ReaderPageRenderer` 的新签名（`drawHeaderFooter` 接受 `BoxBounds`，`drawChapterTitle` 接受 `StaticLayout + BoxBounds`）
- **依赖**：T3.1 - T3.4
- **验收**：编译通过

### T3.7 SimulationPageDelegate 适配

- **文件**：`core/reader/engine/animation/SimulationPageDelegate.kt`
- **改动**：适配 `TextPage` 新字段（`layout` 替代旧字段）
- **依赖**：T2.1
- **验收**：编译通过

### T3.8 Phase 3 编译验证

- **内容**：`./gradlew compileDebugKotlin` 全量编译通过
- **依赖**：T3.1 - T3.7
- **验收**：零编译错误

---

## Phase 4: 偏好设置 + 设置界面

> Phase 4 与 Phase 2/3 可并行开发（无交叉依赖，仅 Phase 1 完成即可开始）

### T4.1 ReaderSettingRegistry 注册新 key

- **文件**：`feature/reader/settings/ReaderSettingRegistry.kt`
- **改动**：
  - 移除 6 个旧 key：`margin_horizontal`, `margin_vertical`, `margin_top`, `margin_bottom`, `margin_left`, `margin_right`
  - 新增 4 个新 key：`body_box`, `header_box`, `footer_box`, `title_box`
  - `defaultValue` 为 `BoxInsetsDp` 实例
  - `scope = InvalidationScope.REFLOW`, `recompositionTier = 3`, `includeInPreset = true`
- **依赖**：T1.1
- **验收**：`ReaderSettingRegistry.all` 包含 4 个新 key，不包含 6 个旧 key

### T4.2 ReaderSettingKey 枚举改造

- **文件**：`feature/reader/screen/ReaderIntent.kt`
- **改动**：
  - 移除：`MARGIN_HORIZONTAL`, `MARGIN_VERTICAL`, `MARGIN_TOP`, `MARGIN_BOTTOM`, `MARGIN_LEFT`, `MARGIN_RIGHT`
  - 新增：`BODY_BOX`, `HEADER_BOX`, `FOOTER_BOX`, `TITLE_BOX`
- **依赖**：无
- **验收**：编译通过

### T4.3 ReaderSettingValue 新增 BoxInsetsDpVal

- **文件**：`feature/reader/screen/ReaderIntent.kt`
- **改动**：
  - 在 `sealed interface ReaderSettingValue` 中新增 `data class BoxInsetsDpVal(val value: BoxInsetsDp) : ReaderSettingValue`
- **依赖**：T1.1
- **验收**：编译通过

### T4.4 ReaderSettingsManager 改造

- **文件**：`feature/reader/settings/ReaderSettingsManager.kt`
- **改动**：
  - 移除旧的 `setMarginHorizontal`, `setMarginVertical` 方法（如果存在）
  - 移除 `setHeaderMarginTop`, `setFooterMarginBottom` 中引用旧字段的逻辑
  - 新增 4 个 setter：`setBodyBox(insets)`, `setHeaderBox(insets)`, `setFooterBox(insets)`, `setTitleBox(insets)`
  - 新增 `batchUpdateBoxInsets(body?, header?, footer?, title?)` 批量更新方法
- **依赖**：T1.5
- **验收**：编译通过

### T4.5 ReaderViewModel.dispatchSetting 改造

- **文件**：`feature/reader/screen/ReaderViewModel.kt`
- **改动**：
  - 移除旧的 `MARGIN_HORIZONTAL`, `MARGIN_VERTICAL`, `MARGIN_TOP/BOTTOM/LEFT/RIGHT` 分支
  - 新增 4 个分支：`BODY_BOX`, `HEADER_BOX`, `FOOTER_BOX`, `TITLE_BOX`，调用对应的 `s.setXxxBox()`
  - 移除旧的 `HEADER_MARGIN_TOP`, `FOOTER_MARGIN_BOTTOM` 分支中引用 `HeaderConfig.marginTop` / `FooterConfig.marginBottom` 的逻辑
- **依赖**：T4.2, T4.4
- **验收**：编译通过

### T4.6 新增 BoxMarginSection 组件

- **文件**：`feature/reader/settings/panel/controls/BoxMarginSection.kt`（新增）
- **内容**：
  - `@Composable fun BoxMarginSection(title, insets: BoxInsetsDp, onInsetsChange, syncLabel, topLabel, bottomLabel, leftLabel, rightLabel, collapsible, initiallyExpanded, modifier)`
  - 无状态组件：只接收 `insets` 和 `onInsetsChange`
  - 本地 UI 状态：`var localTop/Bottom/Left/Right`（拖拽中不触发 REFLOW）
  - 4 个 `InkStepperSlider`：范围 0..96dp，步进 4
  - 可选同步开关（syncLabel）
  - `onValueChange` → 更新本地状态
  - `onValueChangeFinished` → `onInsetsChange(BoxInsetsDp(localTop, localBottom, localLeft, localRight))`
  - 可折叠标题（collapsible 参数）
- **依赖**：T1.1
- **验收**：组件可独立预览，拖拽不卡顿

### T4.7 新增 MarginPresetRow 组件

- **文件**：`feature/reader/settings/panel/controls/MarginPresetRow.kt`（新增）
- **内容**：
  - `data class MarginPreset(label, bodyBox, headerBox, footerBox, titleBox)`
  - `@Composable fun MarginPresetRow(selected?, onSelect, presets)`
  - 横向滚动的 Chip/Button 行
  - 3 个默认预设：紧凑/标准/舒展
- **依赖**：T1.1
- **验收**：组件可独立预览

### T4.8 TypeAndFontTab 改造

- **文件**：`feature/reader/settings/panel/tabs/TypeAndFontTab.kt`
- **改动**：
  - 移除旧的边距区段（4 个 `InkStepperSlider` + `SwitchRow` 同步开关 + `marginSync` 状态）
  - 新增预设区（`MarginPresetRow`）
  - 新增高级编辑区（`CollapsibleSection` 包裹 4 个 `BoxMarginSection`）
  - 预设选择 → `batchUpdateBoxInsets` 批量更新
  - 单个盒子边距变更 → `onSettingChanged("body_box", BoxInsetsDp(...))`
- **依赖**：T4.6, T4.7
- **验收**：设置界面可正常展示和操作

### T4.9 i18n 字符串新增

- **文件**：`core/i18n/ReaderStrings.kt`（interface）+ `core/i18n/ReaderStringsImpl.kt`（3 个实现）
- **新增字段**：
  - `marginCardTitle`："边距" / "Margins"
  - `bodyBoxLabel`："正文" / "Body"
  - `headerBoxLabel`："页眉" / "Header"
  - `footerBoxLabel`："页脚" / "Footer"
  - `titleBoxLabel`："标题" / "Title"
  - `boxMarginTop`："上边距" / "Top"
  - `boxMarginBottom`："下边距" / "Bottom"
  - `boxMarginLeft`："左边距" / "Left"
  - `boxMarginRight`："右边距" / "Right"
  - `marginAdvancedTitle`："高级边距" / "Advanced"
  - `marginPresetCompact`："紧凑" / "Compact"
  - `marginPresetStandard`："标准" / "Standard"
  - `marginPresetRelaxed`："舒展" / "Relaxed"
  - `syncMarginsLabel`："同步上下 / 左右" / "Sync top/bottom · left/right"
- **依赖**：无
- **验收**：3 个语言实现（ZhHans/ZhHant/En）全部编译通过

### T4.10 SettingDefinition 支持 BoxInsetsDp 序列化

- **文件**：`feature/reader/settings/ReaderSettingRegistry.kt` 及相关序列化逻辑
- **改动**：
  - 确认 `SettingDefinition<T>` 的反序列化逻辑能处理 `BoxInsetsDp` 类型
  - 确认预设系统（`includeInPreset = true`）能正确序列化/反序列化 `BoxInsetsDp`
  - 确认 `ReaderPreferences` 的 `@Serializable` 能处理嵌套的 `BoxInsetsDp`
- **依赖**：T4.1
- **验收**：DataStore 读写 `BoxInsetsDp` 正确，预设保存/加载正确

### T4.11 PageCountPersistence 改造

- **文件**：`PageCountPersistence.kt`
- **改动**：
  - `computeLayoutHash` 使用 `Float.toBits()` 替代 `Float.hashCode()`
  - 包含 4 个 `BoxInsetsPx` 的 hash（使用 `hashBoxInsets` 辅助函数）
  - 移除旧的 `margin` 字段 hash
- **依赖**：T1.4
- **验收**：hash 值对相同 config 稳定，对不同 config 不同

### T4.12 更新 BookshelfViewModel

- **文件**：`feature/bookshelf/BookshelfViewModel.kt`
- **改动**：如果有引用旧 margin 字段的地方，适配新字段
- **依赖**：T1.5
- **验收**：编译通过

### T4.13 Phase 4 编译验证

- **内容**：`./gradlew compileDebugKotlin` 全量编译通过
- **依赖**：T4.1 - T4.12
- **验收**：零编译错误

### T4.14 ReaderSettingRegistry 反序列化兼容性验证

- **内容**：验证旧的 `margin_*` key 在新代码下不会导致崩溃（应被忽略或使用默认值）
- **依赖**：T4.10
- **验收**：启动不崩溃，旧 key 被忽略

---

## Phase 5: 系统集成 + 清理 + 测试

### T5.1 BoxInsetsPx.withSystemInsets

- **文件**：`core/reader/model/BoxInsets.kt`
- **改动**：
  - 新增扩展函数 `fun BoxInsetsPx.withSystemInsets(insets: androidx.core.graphics.Insets): BoxInsetsPx`
  - `maxOf(this.top, insets.top.toFloat())` 等
- **依赖**：T1.1
- **验收**：编译通过

### T5.2 沉浸式模式 WindowInsets 合并

- **文件**：Paginator 入口处（Compose 层传入 systemInsets）
- **改动**：
  - 在 `ReaderScreenState` 中新增 `systemInsets: Insets` 字段
  - Paginator 入口处根据 `isImmersive` 决定是否合并
  - header/footer 合并上下安全区
  - body 仅合并左右安全区（横屏刘海）
- **依赖**：T5.1
- **验收**：沉浸式模式下页眉不被状态栏遮挡

### T5.3 更新 Paginator 测试

- **文件**：测试文件
- **改动**：
  - 更新 `ReaderLayoutConfig` 构造（使用 `BoxInsetsPx`）
  - 更新 `TextPage` 字段断言（使用 `page.layout.body.top` 等）
  - 新增极端边距死循环测试：`bodyBox = BoxInsetsDp(200f, 200f, 0f, 0f)` 不导致无限循环
  - 新增首页标题测试：首页 `layout.title != null`，后续页 `layout.title == null`
  - 新增 `buildTitleLayout` 测试：单行/多行标题高度正确
- **依赖**：T2.5
- **验收**：所有测试通过

### T5.4 更新渲染测试

- **文件**：测试文件
- **改动**：适配 `ReaderPageRenderer` 新签名
- **依赖**：T3.1 - T3.4
- **验收**：所有测试通过

### T5.5 更新 PageLayoutCalculator 测试

- **文件**：测试文件（新增）
- **改动**：
  - 测试全部可见：header/title/body/footer 的 bounds 正确
  - 测试 title 不可见：body 向上扩展
  - 测试全部不可见：body 占满
  - 测试 footer insets.top 参与 body 底部计算
  - 测试极端 insets 不导致负坐标
  - 测试 `bodyWidth()` 工具方法
- **依赖**：T1.3
- **验收**：所有测试通过

### T5.6 全量编译 + 单元测试

- **内容**：
  - `./gradlew assembleDebug` 编译通过
  - `./gradlew testDebugUnitTest` 所有测试通过
- **依赖**：T5.1 - T5.5
- **验收**：零编译错误，零测试失败

### T5.7 手动验证

- **内容**：
  - 阅读界面正常显示（页眉/标题/正文/页脚位置正确）
  - 设置界面预设切换正常
  - 设置界面高级编辑（4 个盒子的 16 个滑块）正常
  - 同步开关正常
  - 沉浸式模式下页眉不被遮挡
  - 极端边距（全部调到最大）不崩溃
  - 首页标题正确显示，后续页无标题区域
- **依赖**：T5.6
- **验收**：全部通过

---

## 依赖关系图

```
Phase 1 (可立即开始)
  T1.1 ─┬─→ T1.4 ─→ T1.6 ─→ T1.8
        ├─→ T1.5 ─→ T1.6
        ├─→ T1.2 ─→ T2.1 ─→ T2.6, T2.7
        ├─→ T1.3 ─→ T2.4
        └─→ T1.7 ─→ T1.8

Phase 2 (依赖 Phase 1)
  T2.2 ─→ T2.4 ─→ T2.5 ─→ T2.10
  T2.3 ─→ T2.4
  T2.8 ─→ T2.9
  T2.1, T2.6, T2.7 ─→ T2.10

Phase 3 (依赖 Phase 2)
  T3.1 ─→ T3.2, T3.3 ─→ T3.4 ─→ T3.8
  T3.5, T3.6, T3.7 ─→ T3.8

Phase 4 (依赖 Phase 1，可与 Phase 2/3 并行)
  T1.1 ─→ T4.1, T4.3, T4.6, T4.7
  T4.1 ─→ T4.10, T4.14
  T4.2, T4.4 ─→ T4.5
  T4.6, T4.7 ─→ T4.8
  T4.9 (独立)
  T1.4 ─→ T4.11
  T1.5 ─→ T4.12

Phase 5 (依赖 Phase 3 + 4)
  T5.1 ─→ T5.2
  T2.5 ─→ T5.3
  T3.1 ─→ T5.4
  T1.3 ─→ T5.5
  T5.1 - T5.5 ─→ T5.6 ─→ T5.7
```

---

## 风险与注意事项

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| `SettingDefinition` 不能泛型化 `BoxInsetsDp` | T4.10 阻塞 | 需要先验证序列化机制，可能需要改 `SettingDefinition` 为 `Any` 或引入 sealed class |
| `buildTitleLayout` 的字体度量与渲染不一致 | 标题溢出/截断 | T2.3 使用 `config.titleTypeface` 确保一致 |
| 极端边距导致 body 高度为负 | 崩溃 | `bodyBottom.coerceAtLeast(bodyTop)` + `mustPlaceAtLeastOneLine` 双重保护 |
| 旧 margin 字段的引用遗漏 | 编译错误 | T1.8/T2.10 全量编译验证，搜索所有旧字段引用 |
| 沉浸式 WindowInsets 数据流不清晰 | 页眉被遮挡 | T5.2 明确 Compose 层 → ViewModel → Paginator 的数据流 |
