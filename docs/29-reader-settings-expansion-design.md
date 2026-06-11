# 阅读界面配置扩展设计文档

> 版本：v5.1（审核报告修正）  
> 创建日期：2026-06-10  
> 修订日期：2026-06-11  
> 前置文档：`docs/17-reader-settings-redesign-tasks.md`、`docs/20-quick-settings-redesign.md`、`docs/18-rendering-engine-refactor.md`  
> 交互原型：`docs/prototypes/settings-panel-v5.html`（墨土配色 · 双层渐进式展开 · 3-Tab 卡片化分组）  
> TDD 任务清单：`docs/30-reader-settings-expansion-tdd-tasks.md`

---

## 0. 审查修订摘要

v1.0 版本存在以下架构级缺陷，v2.0 逐一修正：

| 缺陷 | 修正 |
|:---|:---|
| 设置存储路径绕过三层体系 | 所有新设置统一走 `ReaderPreferences` + `BookReaderPrefsOverrides` |
| 缺少 `InvalidationScope` 分类 | 每个设置明确标注 Scope 并接入 Snapshot/Key 体系 |
| 快照/diff 系统未适配 | 新增字段同步修改 Snapshot、Factory、DiffCalculator、Keys |
| Bionic Reading 逐词 drawText 致 60× JNI 开销 | 改为分页阶段预计算 `bionicSegments` |
| 手势绑定新建系统导致两套并存 | 扩展现有 `CanvasTouchHandler.Callbacks` 为 action-based |
| 独立边距废弃旧字段破坏向后兼容 | 保留旧字段做 fallback，新增 nullable 字段 |
| 双页模式两个 View 致双倍内存 | 改为单 View 双区域 clipRect 方案 |
| 竖排阅读未利用现有 `TextColumn` | 复用 `TextPage.columns` 字段 |

v3.0 辩证吸收外部架构建议，新增以下改进：

| 改进 | 来源 | 要点 |
|:---|:---|:---|
| 四层重组模型 | 外部建议 §1.2 | 在存储三层体系之上，ViewModel 层按重组代价拆分为 OverlayPrefs / ChromePrefs / StylePrefs / LayoutPrefs 四个独立 StateFlow，精确控制 Compose recomposition 范围 |
| 统一设置注册表 | 外部建议核心洞察 | `ReaderSettingRegistry` 作为所有设置元数据的唯一真相源，新增设置从改 8 处文件降为改 3 处，消除分散定义的遗漏风险 |
| PorterDuff.Mode.MULTIPLY | 外部建议 §3.1 | 色温叠加改用 MULTIPLY 混合模式，保持纯黑文字不变色（0 × anything = 0），对 OLED 主题尤为关键 |
| 蓝光 = 色温预设 | 外部建议 §3.1 延伸 | 蓝光过滤不再是独立 Boolean 开关，而是 `colorTemperature = 3400K` 的一键快捷预设，消除两套并行逻辑的状态矛盾 |
| Overlay 路径收口 | 外部建议 §3.1 延伸 | 色温/聚焦线统一走 `onDraw()` 最顶层叠加，不进任何 recorder 缓存层 |
| 实际阅读时间计时 | 外部建议 §4.3 | 护眼提醒改为检测翻页活动后才计时，而非简单 `delay()`，避免用户长时间停留在同一页也收到提醒 |
| marginBottom + WindowInsets 分离 | 外部建议 §7.2 | 用户配置边距和系统导航栏 inset 分开叠加，横竖屏切换时 inset 变化触发 Tier 3 reflow |
| 背景纹理 BitmapShader | 外部建议 §3.2 | 在 `renderShell()` 中使用 `android.graphics.BitmapShader` + `TileMode.REPEAT`，shader 仅在纹理变更时重建 |
| 设置面板实时预览区 | 外部建议 §5.4 | 设置面板内嵌 2-3 行小型渲染预览，用独立 previewState 驱动，不影响真实阅读页 |
| 预设范围收敛 | 外部建议 §5.3 | 预设仅纳入 Style + Layout + Chrome 三类设置，Overlay/行为/TTS 排除，避免预设变成第二个 Settings 面板 |

**二次审核修正（v3.1，基于代码验证）：**

| 修正 | 问题 | 影响 |
|:---|:---|:---|
| onDraw 双路径发现 | 静止态不绘制 overlayRecorder，色温/聚焦线必须在 if/else 之后注入 | §1.4.1 重写，聚焦线/色温 Scope 从 OVERLAY 改为 VIEW_INVALIDATE |
| BookPrefs 实为死代码 | `feature.reader.settings.BookReaderPrefsEntity` 未注册到 Database，是废弃设计 | §1.7.1 修正为删除死代码，非统一路径 |
| 实际触点数 8-10 | 原文档低估为 4-6，遗漏 ReaderLayoutInput/Resolver/Manager | §1.7.1/§1.7.4 修正 |
| Registry 默认值驱动 | ReaderPreferences 默认值应直接从 Registry 读取，彻底消除同步点 | §1.7.3 修正 |
| Bionic 性能修正 | 实际 JNI ~40 次/行（1200 次/页），原文档估算偏乐观 | §2.3.1 修正，增加 FakeBold + measureText 消除降级策略 |
| 竖排渲染修正 | 逐字符 drawText 900 JNI/页 → StaticLayout + rotate 30 JNI/页 | §2.3.2 修正 |
| 断字算法简化 | 优先 `BreakIterator.getLineInstance()`，不自建 Liang 模式表 | §2.2.4 修正 |
| PaginationStrategy 提取 | Phase 0 提前提取接口，竖排实现推迟到 Phase 4 | Phase 0 新增 |
| LayoutHasher 一次预留 | 一次性预留所有新字段，避免多次 bump LAYOUT_ALGORITHM_VERSION | Phase 0 新增 |
| §1.3 适配分类 | REFLOW 类只改 LayoutInput+LayoutKey，渲染类改 Snapshot+Keys | §1.3 重写 |

**深度自审修正（v4.0，早期重构避免后续代价）：**

| 问题 | 严重性 | Phase 0 修正 |
|:---|:---|:---|
| PaginationStrategy 接口绑死横排 `ReaderLayoutConfig` | P0 | 接口改为接受 `ReaderPreferences` + `PageSize` + `density`，各策略自行解析 |
| BionicMarkerProcessor 插入控制字符，与分页预计算方案矛盾 | P1 | 废弃标记方案，统一为 Paginator 内直接计算 `bionicSegments` |
| Preview 用独立 Paginator/Renderer 实例，会与真实渲染逐步分化 | P1 | Preview 复用真实 Paginator/Renderer，仅缩小 `pageSize` |
| GestureConfig 序列化为 JSON String，丧失类型安全 | P1 | 改为 `@Serializable data class GestureConfig` |
| BookReaderPrefsOverrides 反序列化容错不足，一个字段异常导致整书配置丢失 | P2 | 添加 `Json { ignoreUnknownKeys; coerceInputValues }`，细化异常捕获 |
| 四层 StateFlow 分组硬编码会导致字段迁移时双重重组 | P2 | 分组由 `Registry.recompositionTier` 驱动（已设计，确认实现时不硬编码） |
| TextProcessingPipeline 的 `order: Int` 无重复约束 | P3 | 构造时断言 order 无重复，或改用枚举 |
| `InvalidationScope.OVERLAY` 命名对色温/聚焦线有误导 | P3 | 新增 `VIEW_INVALIDATE` scope，语义为"仅 View.invalidate()，不进 recorder" |

**设置面板布局重构（v5.0，解决 50+ 设置信息过载）：**

| 改进 | 要点 | 对接章节 |
|:---|:---|:---|
| 双层渐进式展开 | `ModalBottomSheet` → `BottomSheetScaffold`，Peek 态（高频操作）+ Expanded 态（完整设置） | §4.1 |
| 3-Tab 重组 | 4-Tab → 3-Tab（排版与字体 / 外观与显示 / 行为与手势），Tab → UiGroup 映射提取为配置 | §4.1 |
| 卡片化分组 | 平铺列表 → Card 分组，由 Registry `UiGroup` 驱动生成 | §4.2 |
| Peek 态快捷操作 | A-/A+ 字号步进器 + 主题色块秒切 + ScopeHeader 常驻 | §4.3 |
| 页眉页脚线框图 | 6 个下拉框 → 微型 Wireframe + 原地气泡选择 | §4.5 |
| 空间映射边距控件 | 2 个滑块 → 微型屏幕缩略图 + 四边步进器 | §4.5 |
| 预览区改进 | 当前页首行截取 + 硬编码 fallback，仅 Expanded 态显示 | §4.4 |
| Slider + Stepper | 连续值控件两端加步进图标，粗调 + 精调结合 | §4.6 |
| Spring 动画 | Tab 切换 / 面板展开改用 `spring()` 弹性动画 | §4.7 |
| 背景 Scrim | 第二层展开时半透明蒙层（非 blur，API 31+ 限制） | §4.7 |

**审核报告修正（v5.1，辩证吸收外部审核 16 项建议）：**

| 优先级 | 问题 | 修正 |
|:---|:---|:---|
| P0 | `VIEW_INVALIDATE`/`NONE` 在 §1.2 表格中读起来像已存在的 scope，实际枚举仅 6 值 | §1.2 表格上方新增"待实现标注"，明确区分现有/新增 scope |
| P0 | `ReaderSettingsResolver` 死代码断言缺少验证证据 | §1.7.1 补充 grep 验证结果和删除前 @Deprecated 过渡策略 |
| P0 | §3.1 `gestureConfig: String` 与 v4.0 "改为 @Serializable data class" 修正矛盾 | §3.1 改为 `gestureConfig: GestureConfig = GestureConfig()`；`textReplaceRules` 同步改为 `List<RegexRule>` |
| P1 | §2.3.2 `PaginationStrategy` 接口签名与 v4.0/Phase 0a 不一致（仍使用 `ReaderLayoutConfig`） | §2.3.2 统一为 v4.0 签名 `paginate(prefs, pageSize, density)` |
| P1 | §1.7.2 Registry 示例 `fontSize` 默认值 `18f` 与代码 `ReaderPreferences.kt:14` 的 `16f` 不一致 | §1.7.2 统一为 `16f` |
| P1 | §1.4.1 `onDraw()` 行号引用缺少版本锚点 | 补充 commit hash `c0969b1` |
| P1 | §2.1.3 护眼提醒 `break` 退出后未重启 checker | 补充 `dismissEyeCareReminder()` 重启逻辑 |
| P1 | §2.3.3 双页 `renderPageRegion()` 直接绘制三层 recorder，与 §1.4.1 静止路径模式矛盾 | 修正为遵循现有 onDraw 双路径模式 |
| P2 | §4.1 `sheetState.targetValue` 直接 `when` 切换导致拖拽中途内容突变 | 补充 `sheetProgress` 渐变方案说明 |
| P2 | §4.9 预设排除列表遗漏 `pageAnimType` | 补充排除项及理由 |
| P2 | §1.7.2 `SettingDefinition<T>` star-projection 的类型擦除问题未说明 | 补充类型安全封装说明 |
| P2 | §2.2.4 `BreakIterator.getLineInstance()` 返回行断点而非音节断点，断字效果存疑 | 补充局限性说明和 Phase 4 决策点 |
| P2 | Phase 0b 与 Phase 1 并行策略不够细化 | 细化为"逻辑层并行 → UI 集成"两阶段 |
| 结构 | 2385 行文档缺少术语速查 | 新增 §0.1 术语速查表 |
| 结构 | 缺少迁移/回滚策略 | 新增 §8 迁移与回滚策略 |
| 结构 | 缺少可测试性设计 | 新增 §9 测试策略 |

---

### 0.1 术语速查表

| 术语 | 含义 | 首次出现 |
|:---|:---|:---|
| 三层存储体系 | Tier 1 全局 DataStore / Tier 2 每书 JSON / Tier 3 内存合并态 | §1.1 |
| 四层重组模型 | OverlayPrefs / ChromePrefs / StylePrefs / LayoutPrefs 四个 StateFlow | §1.6 |
| InvalidationScope | 设置变更触发的 Canvas 失效级别（REFLOW/CONTENT/SHELL/OVERLAY/VIEW_INVALIDATE 等） | §1.2 |
| ReaderSettingRegistry | 运行时注册表，所有设置元数据的唯一真相源 | §1.7 |
| SettingDefinition | Registry 中单个设置的完整元数据（默认值/持久化层/Scope/UI 分组/预设策略/预览策略） | §1.7.2 |
| UiGroup | 设置面板 12 个卡片分组枚举（FONT_BASICS/THEME/GESTURE 等） | §4.3 |
| PaginationStrategy | 分页策略接口，横排/竖排各自实现 | §2.3.2 |
| TextProcessingPipeline | 统一文本处理管道（ChineseConverter → PanguSpacing → AdFilter → RegexReplacer） | §2.6.1 |
| VIEW_INVALIDATE | 新增 Scope：仅 `View.invalidate()`，不进任何 recorder（色温、聚焦线） | §1.2 |
| Peek/Expanded 两态 | BottomSheetScaffold 的部分展开/完全展开两种状态 | §4.1 |
| 预设快照 | 仅序列化 `includeInPreset = true` 的设置字段子集 | §4.9 |

---

## 1. 现有架构全景

### 1.1 设置存储三层体系

```
┌────────────────────┬─────────────────────────────────────────────────┬──────────┐
│         层         │                      存储                       │ 生命周期 │
├────────────────────┼─────────────────────────────────────────────────┼──────────┤
│ Tier 1: 全局默认   │ UserPreferences (DataStore)                     │ 永久     │
├────────────────────┼─────────────────────────────────────────────────┼──────────┤
│ Tier 2: 每书覆盖   │ BookReaderPrefsOverrides (Room JSON)            │ 随书     │
├────────────────────┼─────────────────────────────────────────────────┼──────────┤
│ Tier 3: 内存合并态 │ ReaderPreferences (data class in ReaderUiState) │ 会话     │
└────────────────────┴─────────────────────────────────────────────────┴──────────┘
```

**设置变更正确路径：**

```
UI 操作
  → ReaderIntent.UpdateSetting(key, value)
  → ReaderViewModel.dispatch()
  → ReaderSettingsManager.setXxx()
  → 更新 ReaderUiState.readerPreferences (Tier 3)
  → 持久化到 DataStore (Tier 1) 或 BookReaderPrefsOverrides (Tier 2)
```

**关键约束：** 所有新设置必须同时支持三层——在 `ReaderPreferences` 中声明默认值，在 `BookReaderPrefsOverrides` 中声明 nullable 覆盖字段，在 `UserPreferences` 中声明 DataStore key。

### 1.2 渲染失效分类 (InvalidationScope)

每个设置变更必须归入以下 Scope 之一，决定触发哪种级别的重绘/重排：

> **⚠️ Scope 状态说明（v5.1 补充）：** 下表中 `VIEW_INVALIDATE` 和无（`NONE`）为本次扩展**新增**，需在 Phase 0 实现。现有 `InvalidationScope` 枚举（`InvalidationScope.kt`）仅包含 6 个值：`PAGE_DELEGATE`、`REFLOW`、`PAGE`、`CONTENT`、`SHELL`、`OVERLAY`。Phase 0 需新增 `VIEW_INVALIDATE`（语义：仅 `View.invalidate()`，不进 recorder）和 `NONE`（语义：不影响 Canvas）。

| Scope | 含义 | 开销 | 触发方式 |
|:---|:---|:---|:---|
| `REFLOW` | 重新分页 | 最高 | `ReaderLayoutHasher.hash()` 变化 → `Paginator` 重跑 |
| `CONTENT` | 重绘正文 | 中 | `RenderKey` 变化 → `contentRecorder.invalidate()` |
| `SHELL` | 重绘页眉页脚 | 低 | `ShellKey` 变化 → `shellRecorder.invalidate()` |
| `OVERLAY` | 重绘选区/高亮 | 最低 | `OverlayKey` 变化 → `overlayRecorder.invalidate()` |
| `VIEW_INVALIDATE` | 仅 View.invalidate() | 零 | 不进任何 recorder，直接在 onDraw() 顶层绘制（色温、聚焦线） |
| `PAGE_DELEGATE` | 重建翻页代理 | 低 | `pageAnimType` 变化 → `PageDelegateFactory.create()` |
| 无 | 行为标志 | 零 | 不影响 Canvas |

**新设置接入清单：**

| 新增设置 | InvalidationScope | 需修改的 Key/Hasher |
|:---|:---|:---|
| 色温（含蓝光预设） | VIEW_INVALIDATE（onDraw 顶层叠加，不进 recorder） | — |
| 护眼提醒 | 无 | — |
| 词间距 | REFLOW | `LayoutKey` 新增 `wordSpacing` → `ReaderLayoutHasher.hash()` |
| 段间分隔线 | REFLOW + CONTENT | `LayoutKey` + `RenderKey` |
| 独立边距 | REFLOW | `LayoutKey` 四边距替换双边距 |
| Bionic Reading | REFLOW + CONTENT | `LayoutKey` + `RenderKey` |
| 竖排阅读 | REFLOW | `LayoutKey` 新增 `vertical` flag |
| 双页模式 | REFLOW + PAGE_DELEGATE | `LayoutKey` + `PageDelegate` 重建 |
| 聚焦线 | VIEW_INVALIDATE（onDraw 末尾直接 drawLine） | — |
| 手势绑定 | 无 | — |
| 振动反馈 | 无 | — |
| 方向锁定 | 无 | — |
| TTS 高亮 | OVERLAY | `OverlayKey` |
| 正则替换/广告过滤 | REFLOW | 文本内容变化 → `LayoutKey` 隐式变化 |
| 翻页速度 | PAGE_DELEGATE | `PageDelegateFactory` 参数 |
| 标题字体 | CONTENT | `RenderKey` 新增 `titleFont` |

### 1.3 快照/diff 系统适配要求

新设置按 Scope 不同，需修改的文件集合也不同（v3.0 二次审核补充）：

**REFLOW 类设置**（影响分页结果）只需修改分页链路：

```
ReaderLayoutInput         ← 新增字段（core.reader.layout 包）
ReaderRenderKeys.LayoutKey ← 参与 hash 计算
LAYOUT_ALGORITHM_VERSION  ← Phase 0 一次性 bump
```

**CONTENT / SHELL / OVERLAY 类设置**（影响渲染但不影响分页）需修改渲染链路：

```
ReaderSettingsSnapshot      ← 新增字段（渲染管线的设置入口）
ReaderRenderSnapshotFactory  ← 构建对应的 Snapshot 子对象
ReaderRenderDiffCalculator  ← 检测变更并生成正确的 InvalidationScope
ReaderRenderKeys            ← 更新 RenderKey / ShellKey / OverlayKey
```

**REFLOW + CONTENT 类设置**（同时影响分页和渲染，如 Bionic Reading）需修改两条链路。

**VIEW_INVALIDATE 类设置**（不走 recorder，如色温/聚焦线）只需在 `ReaderCanvasView.onDraw()` 中注入绘制逻辑，不修改任何 Snapshot/Key 文件，仅触发 `View.invalidate()`。

### 1.4 渲染管线数据流

```
ReaderPreferences (Tier 3 内存合并态)
    ↓ ReaderViewModel.layoutConfigFor()
ReaderLayoutConfig (px 级绝对值)
    ↓ Paginator.paginateChapter() / paginateStreaming()
TextChapter → TextPage → TextLine[]
    ↓
ReaderPageRenderer.render()
    ├─ shellRecorder   — 背景、页眉、页脚、进度条、电池
    ├─ contentRecorder — 正文文本、标题
    │   └─ TextLine.canvasRecorder — 行级缓存
    ├─ overlayRecorder — 选区、TTS 高亮
    ├─ VIEW_INVALIDATE — 色温滤镜、聚焦线（onDraw 顶层叠加，不进 recorder）
    └─ compositeRecorder — 翻页动画合成
    ↓
ReaderCanvasView.onDraw()
    ├─ 三层 recorder 按序绘制
    └─ 最顶层 drawColor()（色温/蓝光，不污染 recorder 缓存）
```

#### 1.4.1 Overlay 统一绘制路径（v3.0 新增，v3.0 二次审核修正）

**实际 `onDraw()` 双路径结构（ReaderCanvasView.kt:566，基于 commit `c0969b1`）：**

```kotlin
override fun onDraw(canvas: Canvas) {
    // 按需录制（needRecord 检查）
    if (current.canvasRecorder.needRecord() || current.shellRecorder.needRecord()) {
        pageBitmapCache.recordPage(current, ...)
    }

    val delegate = pageDelegate
    if (delegate != null && delegate.state != PageDelegate.State.IDLE) {
        // ── 动画路径：compositeRecorder 合并三层 → delegate 绘制 ──
        target?.recordComposite(width, height)  // shell + canvas + overlay
        current.recordComposite(width, height)  // shell + canvas + overlay
        delegate.onDraw(canvas, current.compositeRecorder, target?.compositeRecorder)
    } else {
        // ── 静止路径：仅绘制壳层 + 内容层，⚠️ overlayRecorder 不单独绘制！ ──
        current.shellRecorder.draw(canvas)
        current.canvasRecorder.draw(canvas)
    }

    // crossfade overlay...
}
```

**关键发现：** `overlayRecorder` 仅在动画路径通过 `compositeRecorder` 间接绘制，静止路径下完全不可见。这意味着：

1. **色温/聚焦线不能依赖 overlayRecorder** — 静止态下不显示
2. 必须在 **if/else 之后** 注入顶层绘制，两条路径都能覆盖

**修正后的注入方案：**

```kotlin
override fun onDraw(canvas: Canvas) {
    // ... 现有 if/else 双路径（不动） ...

    // ── 新增：无缓存顶层叠加（两条路径之后，crossfade 之前） ──
    drawColorTemperatureOverlay(canvas)  // MULTIPLY 模式
    drawFocusLineOverlay(canvas)         // 直接 drawLine，不走 recorder
}
```

**修正后的 Scope 归属：**

| 设置 | 绘制方式 | 实际 Scope | 理由 |
|:---|:---|:---|:---|
| 色温 | `drawRect` + `MULTIPLY` | **VIEW_INVALIDATE** | 不走 recorder，onDraw 末尾直接绘制，仅 View.invalidate() |
| 聚焦线 | `drawLine` | **VIEW_INVALIDATE** | 同上，静止态 overlayRecorder 不可见 |
| TTS 高亮 | overlayRecorder | OVERLAY | 需要行级坐标，走 `recordComposite()` 路径 |
| 选区高亮 | overlayRecorder | OVERLAY | 同上 |
| 背景纹理 | shellRecorder | SHELL | 低频变化，壳层绘制在两条路径中都存在 |

### 1.5 现有配置清单（50+ 字段）

| 分类 | 字段 | 类型 | Scope |
|:---|:---|:---|:---|
| 排版 | `fontSize`, `lineSpacing`, `paragraphSpacing`, `indent`, `indentUnit`, `marginHorizontal`, `marginVertical`, `maxPageWidth`, `letterSpacing`, `removeEmptyLines`, `bottomJustify` | Float/Enum/Boolean | REFLOW |
| 字体 | `readingFont`, `fontWeight`, `textAlign`, `chineseConvert`, `useZhLayout`, `usePanguSpacing`, `cleanChapterTitle`, `epubOverrideStyle` | String/Enum/Boolean | REFLOW/CONTENT |
| 页眉脚 | `header`, `footer`, `headerFooterAlpha`, `showHeaderLine`, `showFooterLine`, `headerFontSizeRatio`, `footerFontSizeRatio`, `titleStyle`, `showProgress`, `progressStyle` | Config/Float/Enum | SHELL |
| 主题 | `backgroundColor`, `customBackgroundColor`, `customTextColor`, `customAccentColor`, `autoNightMode` | Enum/Int/Boolean | CONTENT |
| 交互 | `brightness`, `pageAnimType`, `volumeKeyTurnPage`, `edgeTurnPage`, `edgeWidthPercent`, `leftZoneRatio`, `immersiveMode`, `keepScreenOn`, `autoPageTurn`, `autoPageTurnInterval` | Float/Enum/Boolean | 无/PAGE_DELEGATE |
| TTS | `ttsSpeed`, `ttsPitch` | Float | 无 |

### 1.6 Compose 重组四层模型（v3.0 新增）

存储三层体系（§1.1）控制**数据在哪一层持久化**，InvalidationScope（§1.2）控制**Canvas 哪一层 recorder 失效**。但 Compose UI 层还有一个正交的优化维度：**哪些 StateFlow 订阅者需要 recompose**。

如果所有设置都塞进一个 `ReaderPreferences` data class 并通过单个 `StateFlow<ReaderPreferences>` 传递，那么改蓝光强度也会触发依赖 `fontSize` 的 Composable 重组——这是浪费。

**四层拆分方案：** ViewModel 将 `ReaderPreferences` 按重组代价拆分为四个独立 `StateFlow`，Composable 只订阅它需要的那一层：

```
┌─ Tier 0: OverlayPrefs ───────────────────────────────────────────┐
│  colorTemperature（含蓝光预设 3400K）, brightness, focusLine       │
│  → 只影响最外层 Canvas drawRect，零重组，零重分页                  │
│  → StateFlow 变化时仅 ReaderCanvasView.invalidate()               │
└───────────────────────────────────────────────────────────────────┘
┌─ Tier 1: ChromePrefs ────────────────────────────────────────────┐
│  header/footer slots, progress style, headerFooterAlpha           │
│  → 只让页眉页脚 Composable 重组，正文不动                         │
│  → StateFlow 变化 → SHELL scope                                  │
└───────────────────────────────────────────────────────────────────┘
┌─ Tier 2: StylePrefs ─────────────────────────────────────────────┐
│  readingFont, fontWeight, textAlign, bionicReading, textStroke    │
│  → TextStyle 变化，触发 contentRecorder 重绘但不需要重分页        │
│  → StateFlow 变化 → CONTENT scope                                │
└───────────────────────────────────────────────────────────────────┘
┌─ Tier 3: LayoutPrefs ────────────────────────────────────────────┐
│  fontSize, lineHeight, margins, indent, wordSpacing, hyphenation  │
│  → 改变分页结果，必须重跑 Paginator                               │
│  → StateFlow 变化 → REFLOW scope                                 │
└───────────────────────────────────────────────────────────────────┘
```

**ViewModel 实现：**

```kotlin
// ReaderViewModel
val overlayPrefs: StateFlow<OverlayPrefs> = uiState
    .map { it.readerPreferences.toOverlayPrefs() }
    .distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.Eagerly, OverlayPrefs())

val chromePrefs: StateFlow<ChromePrefs> = uiState
    .map { it.readerPreferences.toChromePrefs() }
    .distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.Eagerly, ChromePrefs())

val stylePrefs: StateFlow<StylePrefs> = uiState
    .map { it.readerPreferences.toStylePrefs() }
    .distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.Eagerly, StylePrefs())

val layoutPrefs: StateFlow<LayoutPrefs> = uiState
    .map { it.readerPreferences.toLayoutPrefs() }
    .distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.Eagerly, LayoutPrefs())
```

**重组代价矩阵：**

```
配置项            触发层   重分页  重渲染  UI 重组
字号/行距          Tier 3    ✓      ✓      ✓
字体/字重          Tier 2    ✗      ✓      ✓
页眉 Slot          Tier 1    ✗      ✗      局部
蓝光滤镜           Tier 0    ✗      ✗      ✗（仅 invalidate）
翻页振动           交互层    ✗      ✗      ✗（仅 side-effect）
```

**关键约束：** 四层模型不替代存储三层体系或 InvalidationScope，三者正交互补：
- 存储三层 → 数据持久化粒度
- 重组四层 → Compose recomposition 粒度
- InvalidationScope → Canvas recorder 失效粒度

### 1.7 统一设置注册表（v3.0 新增）

#### 1.7.1 问题：同步点过多

当前方案中，每新增一个渲染类设置需要手工修改 **8–10 个文件**（v3.0 二次审核修正，原文档低估为 4-6）：

```
 1. ReaderPreferences.kt          ← Tier 3 默认值
 2. BookReaderPrefsOverrides.kt    ← Tier 2 nullable 覆盖（或 settings/BookReaderPrefsEntity.kt 列路径）
 3. UserPreferences.kt             ← Tier 1 DataStore key + Flow + setter
 4. ReaderLayoutInput.kt           ← 分页参数（如影响分页）
 5. ReaderSettingsSnapshot.kt      ← 渲染管线入口（如影响渲染）
 6. ReaderRenderKeys.kt            ← LayoutKey hash / RenderKey / OverlayKey / ShellKey
 7. ReaderIntent.kt                ← ReaderSettingKey 枚举值
 8. ReaderSettingsManager.kt       ← setXxx() 方法
 9. ReaderSettingsResolver.kt      ← merge 逻辑（全局 + 本书 + 会话合并）
10. UI 面板                         ← Tab/Section 归属
```

⚠️ **BookReaderPrefsEntity 死代码问题（v4.0 修正）：** 代码中存在两个同名类：
- `core.database.entity.BookReaderPrefsEntity` — **活代码**：`configJson` JSON blob，已注册到 `ShuLiDatabase`，`BookReaderPrefsDao` 使用
- `feature.reader.settings.BookReaderPrefsEntity` — **死代码**：47 个独立 nullable 列，**未注册到 Database**，从未被生产代码调用

`ReaderSettingsResolver`（引用列路径实体）也是死代码——`resolve()` 在生产代码中零调用。这是早期设计的残留，不是"双路径"。

**死代码验证证据（v5.1 补充）：**
- `grep -rn "ReaderSettingsResolver" app/src/main/` → 仅在 `ReaderSettingsResolver.kt`（定义）、`BookReaderPrefsEntity.kt`（KDoc 注释引用）、`ResolvedReaderSettings.kt`（返回类型）、`ReaderSessionState.kt`（KDoc 注释引用）中出现
- `grep -rn "ReaderSettingsResolver.resolve" app/src/main/` → **零结果**（`.resolve()` 无生产调用方）
- `ResolvedReaderSettings` 类型仅在 Resolver 包内部自引用 + 测试文件 `ResolvedReaderSettingsTest.kt` 中使用
- `ReaderSettingsManager.kt` 中的 `resolved` 变量名是命名巧合——它使用 JSON blob 路径解析，不经过 Resolver

**安全删除策略：** Phase 0 先对 `ReaderSettingsResolver`、`ResolvedReaderSettings`、`feature.reader.settings.BookReaderPrefsEntity` 标记 `@Deprecated(level = DeprecationLevel.ERROR)`，跑完整构建 + 全量测试确认无编译错误后，再物理删除。

**Phase 0 处理：删除死代码，Registry 对接 JSON blob 路径。** 不要复活列路径设计。

这种分散定义 + 双路径的模式会导致：
- 新增设置的"遗漏修改"风险（忘记某个文件 → 编译通过但运行时异常）
- 代码审查时无法一眼看清单个设置的完整元数据
- 预设系统、UI 分组、Scope 检测之间的隐式耦合
- 两套 BookPrefs 持久化路径可能不同步

#### 1.7.2 方案：运行时注册表

**拒绝编译期代码生成**（KSP/kapt 增加构建复杂度和调试难度），改用运行时注册表模式。核心思想：**每个设置只定义一次元数据，所有消费方从注册表查询**。

```kotlin
/**
 * 设置元数据定义——每个设置的所有属性集中在此。
 * 新增设置只需在此处添加一条记录。
 */
data class SettingDefinition<T>(
    val key: String,                    // DataStore key（Tier 1）
    val defaultValue: T,                // 默认值（Tier 3）
    val storageTier: StorageTier,       // 持久化层：GLOBAL / PER_BOOK / BOTH
    val scope: InvalidationScope,       // 渲染失效分类
    val recompositionTier: Int,         // 重组层：0=Overlay / 1=Chrome / 2=Style / 3=Layout
    val uiGroup: UiGroup,               // UI 分组
    val includeInPreset: Boolean,       // 是否纳入预设快照
    val previewStrategy: PreviewStrategy // 预览区行为：LIVE / ON_APPLY / NONE
)

enum class StorageTier { GLOBAL, PER_BOOK, BOTH }
// UiGroup 枚举定义见 §4.3（v5.0 版本，12 个分组）
// FONT_BASICS, TEXT_LAYOUT, TEXT_STYLE, ADVANCED_READING,
// THEME, PAGE_CHROME, DISPLAY_MODE, VISUAL_AIDS,
// PAGE_TURN, GESTURE, EYE_CARE, GENERAL
enum class PreviewStrategy { LIVE, ON_APPLY, NONE }

/**
 * 类型安全说明（v5.1 补充）：
 * SettingDefinition<T> 使用泛型，但 Registry.all 为 List<SettingDefinition<*>>（star-projection）。
 * 消费方 getValue(def, prefs) 和 valueChanged(def, old, new) 需要运行时类型信息。
 * 解决方案：Registry 内部封装类型安全的 accessor：
 *   fun <T> getValue(def: SettingDefinition<T>, prefs: ReaderPreferences): T
 *   fun <T> setValue(def: SettingDefinition<T>, prefs: ReaderPreferences, value: T): ReaderPreferences
 * 使用 @Suppress("UNCHECKED_CAST") 在 Registry 内部统一处理，消费方无需关心类型转换。
 * 备选方案：如 star-projection 带来的 unchecked cast 过多，可改为 sealed class SettingDefinition
 * 的子类层次（FloatSetting / BooleanSetting / EnumSetting 等），以编译器安全换取更多样板代码。
 * Phase 0 实现时根据实际 cast 数量决定。
 */

/**
 * 全局注册表——所有设置的唯一真相源。
 */
object ReaderSettingRegistry {

    val all: List<SettingDefinition<*>> = buildList {
        // ── Overlay 层（recompositionTier = 0）──
        add(SettingDefinition(
            key = "color_temperature",
            defaultValue = 6500f,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.VIEW_INVALIDATE, // 不进 recorder，onDraw 顶层叠加
            recompositionTier = 0,
            uiGroup = UiGroup.EYE_CARE,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.LIVE
        ))
        // blueLightFilter 不再是独立设置——它是 colorTemperature 的快捷预设（见 §2.1.2）
        add(SettingDefinition(
            key = "focus_line",
            defaultValue = false,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.VIEW_INVALIDATE,
            recompositionTier = 0,
            uiGroup = UiGroup.VISUAL_AIDS,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.LIVE
        ))

        // ── Chrome 层（recompositionTier = 1）──
        add(SettingDefinition(
            key = "progress_style",
            defaultValue = ProgressStyle.CHAPTER_FRACTION,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.SHELL,
            recompositionTier = 1,
            uiGroup = UiGroup.PAGE_CHROME,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.ON_APPLY
        ))

        // ── Style 层（recompositionTier = 2）──
        add(SettingDefinition(
            key = "reading_font",
            defaultValue = "harmony",
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.CONTENT,
            recompositionTier = 2,
            uiGroup = UiGroup.FONT_BASICS,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.LIVE
        ))
        add(SettingDefinition(
            key = "title_font",
            defaultValue = "",
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.CONTENT,
            recompositionTier = 2,
            uiGroup = UiGroup.FONT_BASICS,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.LIVE
        ))

        // ── Layout 层（recompositionTier = 3）──
        add(SettingDefinition(
            key = "font_size",
            defaultValue = 16f,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.REFLOW,
            recompositionTier = 3,
            uiGroup = UiGroup.FONT_BASICS,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.LIVE
        ))
        add(SettingDefinition(
            key = "word_spacing",
            defaultValue = 0f,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.REFLOW,
            recompositionTier = 3,
            uiGroup = UiGroup.TEXT_LAYOUT,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.LIVE
        ))

        // ── 行为层（recompositionTier = ∞，不影响 Canvas）──
        add(SettingDefinition(
            key = "haptic_feedback",
            defaultValue = false,
            storageTier = StorageTier.GLOBAL,
            scope = InvalidationScope.NONE,
            recompositionTier = -1, // 不参与重组
            uiGroup = UiGroup.GESTURE,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.NONE
        ))

        // ... 其余设置同理逐条注册
    }

    // ── 查询 API ──

    /** 按重组层过滤（驱动四层 StateFlow 拆分） */
    fun byRecompositionTier(tier: Int): List<SettingDefinition<*>> =
        all.filter { it.recompositionTier == tier }

    /** 按 UI 分组过滤（驱动设置面板生成） */
    fun byUiGroup(group: UiGroup): List<SettingDefinition<*>> =
        all.filter { it.uiGroup == group }

    /** 预设白名单（驱动预设快照字段） */
    fun presetFields(): List<SettingDefinition<*>> =
        all.filter { it.includeInPreset }

    /** 按 Scope 过滤（驱动 DiffCalculator） */
    fun byScope(scope: InvalidationScope): List<SettingDefinition<*>> =
        all.filter { it.scope == scope }

    /** 按持久化层过滤（驱动 DataStore / Overrides 字段生成） */
    fun byStorageTier(tier: StorageTier): List<SettingDefinition<*>> =
        all.filter { it.storageTier == tier || it.storageTier == StorageTier.BOTH }
}
```

#### 1.7.3 消费方如何使用注册表

```kotlin
// 1. ReaderPreferences 构造：默认值从 Registry 读取，彻底消除同步点
//    （v3.0 二次审核改进：原文档要求手工同步，现改为 Registry 驱动）
data class ReaderPreferences(
    val colorTemperature: Float = ReaderSettingRegistry.getDefault("color_temperature"),
    val focusLine: Boolean = ReaderSettingRegistry.getDefault("focus_line"),
    // ... 所有字段默认值均从 Registry 获取
)

// 2. ViewModel 四层 StateFlow：按 recompositionTier 分组
val overlayPrefs: StateFlow<OverlayPrefs> = uiState
    .map { state ->
        OverlayPrefs(
            colorTemperature = state.readerPreferences.colorTemperature,
            focusLine = state.readerPreferences.focusLine,
            // 仅包含 recompositionTier == 0 的字段
        )
    }
    .distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.Eagerly, OverlayPrefs())

// 3. DiffCalculator：按 Scope 检测变更
fun calculateDiff(old: ReaderSettingsSnapshot, new: ReaderSettingsSnapshot): Set<InvalidationScope> {
    val scopes = mutableSetOf<InvalidationScope>()
    for (def in ReaderSettingRegistry.all) {
        if (def.scope != InvalidationScope.NONE && def.scope != InvalidationScope.VIEW_INVALIDATE && valueChanged(def, old, new)) {
            scopes.add(def.scope)
        }
    }
    return scopes
}

// 4. 预设快照：仅序列化 includeInPreset == true 的字段
fun createPresetSnapshot(prefs: ReaderPreferences): PresetSnapshot {
    val fields = ReaderSettingRegistry.presetFields()
    return PresetSnapshot(fields.associate { it.key to getValue(it, prefs) })
}

// 5. UI 面板：按 UiGroup 自动生成设置项
@Composable
fun SettingsPanel(group: UiGroup) {
    val settings = remember(group) { ReaderSettingRegistry.byUiGroup(group) }
    settings.forEach { def ->
        SettingControl(def) // 根据 def.defaultValue 的类型自动选择 Slider/Switch/Picker
    }
}
```

#### 1.7.4 新增设置的完整流程（对比改进前后）

**改进前（8-10 处手工修改）：**
```
 1. ReaderPreferences.kt          — 添加字段 + 默认值
 2. BookReaderPrefsOverrides.kt    — 添加 nullable 字段（JSON blob 路径，活代码）
 3. UserPreferences.kt             — 添加 DataStore key + Flow + setter
 4. ReaderLayoutInput.kt           — 添加分页参数字段（如影响分页）
 5. ReaderSettingsSnapshot.kt      — 添加渲染快照字段（如影响渲染）
 6. ReaderRenderKeys.kt            — 添加到对应 Key hash
 7. ReaderIntent.kt                — 添加 ReaderSettingKey 枚举值
 8. ReaderSettingsManager.kt       — 添加 setXxx() 方法
 9. UI 面板                         — 添加到对应 Tab/Section
```

**改进后（1 处定义 + 3 处手工同步）：**
```
1. ReaderSettingRegistry.kt       — 添加 SettingDefinition（唯一定义点）
2. ReaderPreferences.kt           — 添加字段（默认值从 Registry 读取）
3. BookReaderPrefsOverrides.kt    — 添加 nullable 字段（JSON blob 路径）
4. UI 面板                         — 添加控件（或从 Registry 自动生成）
```

其余文件（UserPreferences、ReaderLayoutInput、Snapshot、Keys、Intent、SettingsManager）全部从 Registry 查询驱动，无需手工修改。

**Phase 0 前提：** 删除死代码 `feature.reader.settings.BookReaderPrefsEntity`（47 列实体，未注册到 Database）和未使用的 `ReaderSettingsResolver`。Registry 对接 JSON blob 路径（`BookReaderPrefsOverrides` + `configJson`）。

---

## 2. 新增功能设计

### 设计原则

1. **Registry 驱动**：每个设置的元数据（默认值/持久化层/Scope/UI 分组/预设策略/预览策略）在 `ReaderSettingRegistry` 中**只定义一次**，所有消费方（StateFlow 拆分/DiffCalculator/预设快照/UI 面板）从 Registry 查询
2. **三层体系一致性**：所有新设置同时声明在 `ReaderPreferences`（默认值）、`BookReaderPrefsOverrides`（nullable 覆盖）、`UserPreferences`（DataStore key），三处字段与 Registry 保持对齐
3. **Scope 驱动**：每个设置标注 `InvalidationScope`，接入对应的 Key/Hasher；Overlay 类视觉设置走 `onDraw()` 最顶层叠加，不进 recorder 缓存
4. **性能优先**：新设置不得突破性能预算（单页渲染 ≤ 10ms，翻页 60fps）
5. **向后兼容**：不废弃旧字段，新增字段使用 nullable + fallback
6. **预设收敛**：预设仅记录 Style + Layout + Chrome 三类设置，Overlay/行为/TTS 排除
7. **蓝光不独立**：蓝光过滤是色温的命名预设（3400K），不是并行 Boolean 开关

---

### 2.1 护眼功能

#### 2.1.1 色温调节

| 项 | 值 |
|:---|:---|
| InvalidationScope | VIEW_INVALIDATE（不影响文本布局，不进 recorder，仅 View.invalidate()） |
| 存储层 | Tier 1: `KEY_COLOR_TEMPERATURE = "color_temperature"` (Float, 默认 6500f)；Tier 2: `BookReaderPrefsOverrides.colorTemperature: Float?`；Tier 3: `ReaderPreferences.colorTemperature: Float = 6500f` |
| Intent 路径 | `ReaderIntent.UpdateSetting(COLOR_TEMPERATURE, FloatValue)` → `ReaderSettingsManager.setColorTemperature()` |

**渲染方案：**

在 `ReaderCanvasView.onDraw()` 中，所有 recorder 绘制完成后，在最顶层叠加色温层。这样**完全不污染三层 recorder 的缓存逻辑**，色温变化只需 `invalidate()` 整个 View，不触发任何 recorder 重录。

```kotlin
// ReaderCanvasView.onDraw() — 色温注入在双路径之后（VIEW_INVALIDATE scope）
override fun onDraw(canvas: Canvas) {
    // ... 省略 needRecord 检查 ...

    val delegate = pageDelegate
    if (delegate != null && delegate.state != PageDelegate.State.IDLE) {
        // 动画路径：compositeRecorder（shell + canvas + overlay）
        target?.recordComposite(width, height)
        current.recordComposite(width, height)
        delegate.onDraw(canvas, current.compositeRecorder, target?.compositeRecorder)
    } else {
        // 静止路径：仅 shell + canvas（overlayRecorder 不单独绘制）
        current.shellRecorder.draw(canvas)
        current.canvasRecorder.draw(canvas)
    }

    // ── VIEW_INVALIDATE：色温滤镜（不进入任何 recorder 缓存）──
    // 在双路径之后注入，确保无论动画/静止都生效。
    // 使用 MULTIPLY 混合模式：纯黑文字保持不变（0 × anything = 0），
    // 仅浅色区域（纸张背景）被暖色偏移。
    val temp = currentPrefs.colorTemperature
    if (temp < 6500f) {
        val rgb = colorTemperatureToRgb(temp)
        colorTempPaint.color = Color.rgb(rgb.first, rgb.second, rgb.third)
        colorTempPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), colorTempPaint)
        colorTempPaint.xfermode = null
    }
}
```

**色温 → RGB 转换公式（Tanner Helland 近似）：**

```kotlin
fun colorTemperatureToRgb(kelvin: Float): Triple<Int, Int, Int> {
    val temp = kelvin / 100f
    val r = if (temp <= 66) 255
            else (329.698727446 * (temp - 60).pow(-0.1332047592)).toInt().coerceIn(0, 255)
    val g = if (temp <= 66) (99.4708025861 * ln(temp) - 161.1195681661).toInt().coerceIn(0, 255)
            else (288.1221695283 * (temp - 60).pow(-0.0755148492)).toInt().coerceIn(0, 255)
    val b = if (temp >= 66) 255
            else if (temp <= 19) 0
            else (138.5177312231 * ln(temp - 10) - 305.0447927307).toInt().coerceIn(0, 255)
    return Triple(r, g, b)
}
```

**UI 交互：** 色温滑块 3000K–6500K，与亮度滑块并列。

#### 2.1.2 蓝光过滤（v3.0 重构：色温快捷预设）

| 项 | 值 |
|:---|:---|
| InvalidationScope | 无独立 Scope（复用色温的 VIEW_INVALIDATE 路径） |
| 存储层 | **不再是独立设置**。蓝光过滤是 `colorTemperature` 的一键快捷预设 |

**v2.0 方案的问题：** 蓝光过滤（`blueLightFilter: Boolean`）和色温（`colorTemperature: Float`）是两套并行逻辑——一个 Boolean 开关 + 一个 Float 滑块，UI 上互斥但代码上各自独立。这导致：
- 用户开启蓝光后手动调色温，两者状态矛盾
- 预设快照需要同时记录两个字段
- Registry 中多一条冗余定义

**v3.0 方案：蓝光 = 色温的命名预设**

```kotlin
// 蓝光过滤不再是独立的 SettingDefinition
// 它是一个预设值：colorTemperature = 3400K

// 快捷开关的实现：
fun toggleBlueLightFilter(currentTemp: Float): Float {
    return if (currentTemp == BLUE_LIGHT_TEMP) {
        // 关闭蓝光：恢复上次手动设置的色温
        lastManualColorTemperature
    } else {
        // 开启蓝光：记住当前色温，切换到蓝光预设
        lastManualColorTemperature = currentTemp
        BLUE_LIGHT_TEMP
    }
}

companion object {
    const val BLUE_LIGHT_TEMP = 3400f
}
```

**UI 交互：**
- 快捷面板上的"蓝光"按钮 = 一键切换色温到 3400K / 恢复上次值
- 色温滑块始终显示当前实际色温值（包括蓝光模式下显示 3400K）
- 蓝光模式下色温滑块**不锁定**——用户仍可拖动（拖走后自动退出蓝光模式）
- 不再有"互斥禁用"的 UI 状态，简化交互逻辑

**好处：**
- Registry 中少一条 `SettingDefinition`
- 预设快照只需记录 `colorTemperature`，不需要额外的 `blueLightFilter` 字段
- DiffCalculator 只需处理一个字段变更
- 消除两套逻辑的状态矛盾风险

#### 2.1.3 护眼提醒

| 项 | 值 |
|:---|:---|
| InvalidationScope | 无（纯 UI 弹窗） |
| 存储层 | 三层体系，Int 类型（分钟），默认 0（禁用） |

**方案：** 跟踪"实际阅读时间"而非"App 打开时间"——只在检测到翻页活动时计时，用户长时间停留在同一页不累积时间。

```kotlin
// ReaderViewModel
private var accumulatedReadingMs = 0L
private var lastPageTurnTimestamp = 0L
private var eyeCareCheckJob: Job? = null

fun onReaderOpened() {
    val interval = uiState.value.readerPreferences.eyeCareReminderInterval
    if (interval > 0) {
        lastPageTurnTimestamp = System.currentTimeMillis()
        accumulatedReadingMs = 0L
        startEyeCareChecker(interval)
    }
}

/** 翻页时调用：累加阅读时间，重置计时起点 */
fun onPageTurned() {
    val now = System.currentTimeMillis()
    val elapsed = now - lastPageTurnTimestamp
    // 仅当两次翻页间隔 < 5 分钟时才累加（排除用户放下手机的情况）
    if (elapsed < 5 * 60_000L) {
        accumulatedReadingMs += elapsed
    }
    lastPageTurnTimestamp = now
}

private fun startEyeCareChecker(intervalMinutes: Int) {
    eyeCareCheckJob?.cancel()
    eyeCareCheckJob = viewModelScope.launch {
        while (true) {
            delay(30_000L)  // 每 30 秒检查一次
            // 加上自上次翻页以来的时间
            val now = System.currentTimeMillis()
            val sinceLastTurn = (now - lastPageTurnTimestamp).coerceAtMost(5 * 60_000L)
            val totalReading = accumulatedReadingMs + sinceLastTurn
            if (totalReading >= intervalMinutes * 60_000L) {
                _eyeCareReminderVisible.value = true
                // 用户关闭提醒后重置
                accumulatedReadingMs = 0L
                lastPageTurnTimestamp = System.currentTimeMillis()
                break
            }
        }
    }
}

// 关闭阅读器时清理
override fun onCleared() {
    eyeCareCheckJob?.cancel()
    super.onCleared()
}
```

**集成点：** 在 `ReaderNavigationCoordinator.nextPage()` / `prevPage()` / `openChapter()` 中调用 `viewModel.onPageTurned()`。

**提醒关闭后重启 checker（v5.1 补充）：**

```kotlin
fun dismissEyeCareReminder() {
    _eyeCareReminderVisible.value = false
    // 重置计时并重新启动 checker，否则用户点"继续"后不会再收到提醒
    accumulatedReadingMs = 0L
    lastPageTurnTimestamp = System.currentTimeMillis()
    val interval = uiState.value.readerPreferences.eyeCareReminderInterval
    if (interval > 0) {
        startEyeCareChecker(interval)
    }
}
```

UI：提醒弹窗使用 Compose `AlertDialog`，包含"休息 20 秒"（黑屏倒计时）和"继续"按钮。弹窗显示已读时长统计（"本次已阅读 N 分钟"）。

---

### 2.2 高级排版

#### 2.2.1 词间距

| 项 | 值 |
|:---|:---|
| InvalidationScope | REFLOW |
| 存储层 | 三层体系，Float 类型，默认 0f，范围 -0.2em ~ 0.5em |
| Key 接入 | `LayoutKey` 新增 `wordSpacing: Float` → `ReaderLayoutHasher.hash()` |

**Paginator 改造：**

```kotlin
// ReaderLayoutConfig 新增
val wordSpacingPx: Float = 0f  // density 转换后绝对值

// Paginator.calculateLine() — 在现有 for 循环中追加
for (i in 0 until lineEnd) {
    val width = widthWindow[startOffset + i]
    val spacing = if (charCount > 0) letterSpacingPx else 0f
+   val wordSpace = if (content[startOffset + i] == ' ') wordSpacingPx else 0f
-   if (currentWidth + width + spacing > availableWidth) break
+   if (currentWidth + width + spacing + wordSpace > availableWidth) break
-   currentWidth += width + spacing
+   currentWidth += width + spacing + wordSpace
    charCount++
}
```

- `WidthWindow` 不需改动（缓存字符原始宽度，词间距是额外追加）
- `charWidths` 数组不含词间距（选区坐标不受影响）
- 仅影响空格字符，中文字符间无空格故自动跳过

#### 2.2.2 段间分隔线

| 项 | 值 |
|:---|:---|
| InvalidationScope | REFLOW（占用额外行间距空间）+ CONTENT（绘制分隔线） |
| 存储层 | 三层体系，Boolean 类型，默认 false |
| Key 接入 | `LayoutKey` 新增 `paragraphDivider: Boolean` + `RenderKey` 新增 `paragraphDivider` |

**分页阶段：**

```kotlin
// ReaderLayoutConfig 新增
val paragraphDividerHeight: Float = 0f  // 4dp → px（false 时为 0）

// Paginator.paginatePage() — 段落末行后追加
if (lineResult.isParagraphEnd && config.paragraphDividerHeight > 0f) {
    currentY += config.paragraphDividerHeight
}
```

**渲染阶段：**

```kotlin
// ReaderPageRenderer.renderContent() — 段落末行后绘制
if (showParagraphDivider && line.isParagraphEnd && index < lines.size - 1) {
    val midY = line.bottom + config.paragraphDividerHeight / 2
    dividerPaint.color = textColor.copy(alpha = 0.15f)
    canvas.drawLine(marginLeft + indentWidth, midY, width - marginRight, midY, dividerPaint)
}
```

- 分隔线颜色自动从 `textColor` 派生（15% 不透明度），无需新增配置
- `bottomJustify` 逻辑不受影响（分隔线高度已计入 `currentY`，底部对齐基于最终 `currentY`）

#### 2.2.3 独立边距

| 项 | 值 |
|:---|:---|
| InvalidationScope | REFLOW |
| 存储层 | 三层体系，4 个 nullable Float，默认 null |
| 向后兼容 | **保留旧字段做 fallback，不废弃** |

**数据模型：**

```kotlin
// ReaderPreferences — 新增 nullable 字段 + 保留旧字段
data class ReaderPreferences(
    // 保留旧字段（作为默认值来源）
    val marginVertical: Float = 48f,
    val marginHorizontal: Float = 24f,
    // 新增独立边距（null = 使用旧字段值）
    val marginTop: Float? = null,
    val marginBottom: Float? = null,
    val marginLeft: Float? = null,
    val marginRight: Float? = null,
)
```

**解析逻辑（在 `ReaderViewModel.layoutConfigFor()` 中）：**

```kotlin
val top = prefs.marginTop ?: prefs.marginVertical
val bottom = prefs.marginBottom ?: prefs.marginVertical
val left = prefs.marginLeft ?: prefs.marginHorizontal
val right = prefs.marginRight ?: prefs.marginHorizontal

ReaderLayoutConfig(
    marginTop = top * density,
    marginBottom = bottom * density,
    marginLeft = left * density,
    marginRight = right * density,
    // ...
)
```

**ReaderLayoutConfig 改造：**

```kotlin
data class ReaderLayoutConfig(
    // 替换 marginVertical / marginHorizontal 为四边独立值
    val marginTop: Float,
    val marginBottom: Float,
    val marginLeft: Float,
    val marginRight: Float,
    // ...
)
```

**Paginator 适配（约 10 处修改）：**

```kotlin
// 现有
val availableWidth = config.pageSize.width - config.marginHorizontal * 2
val startY = config.marginVertical + headerHeight + titleAreaHeight
val maxAvailableY = config.pageSize.height - config.marginVertical - footerHeight

// 改为
val availableWidth = config.pageSize.width - config.marginLeft - config.marginRight
val startY = config.marginTop + headerHeight + titleAreaHeight
val maxAvailableY = config.pageSize.height - config.marginBottom - footerHeight
```

**向后兼容保证：**
- 旧预设加载时 `marginTop` 等为 null → 自动 fallback 到 `marginVertical`
- `BookReaderPrefsOverrides` 中 4 个新字段为 nullable → 旧 JSON 反序列化无报错
- `ReaderLayoutHasher.hash()` 使用解析后的实际值（top/bottom/left/right），确保旧预设和新预设不会碰撞缓存

**UI：** 排版 Tab 新增"边距"折叠区，4 个独立滑块 + "同步上下/左右"开关。

**WindowInsets 分离（v3.0 新增）：**

用户配置的 `marginBottom` 和系统导航栏 inset 必须分开叠加，否则最后一行正文会被手势导航条遮住：

```kotlin
// ReaderViewModel.layoutConfigFor()
val userMarginBottom = (prefs.marginBottom ?: prefs.marginVertical) * density

// 系统 inset 独立获取（横竖屏切换、三键/手势导航切换时自动更新）
val systemBottomInset = WindowInsetsCompat.toWindowInsetsCompat(view.rootWindowInsets)
    .getInsets(WindowInsetsCompat.Type.navigationBars()).bottom.toFloat()

ReaderLayoutConfig(
    marginTop = (prefs.marginTop ?: prefs.marginVertical) * density,
    marginBottom = userMarginBottom + systemBottomInset,  // 用户配置 + 系统 inset
    marginLeft = (prefs.marginLeft ?: prefs.marginHorizontal) * density,
    marginRight = (prefs.marginRight ?: prefs.marginHorizontal) * density,
    // ...
)
```

**关键点：**
- `systemBottomInset` 变化时（横竖屏切换、导航模式切换）触发 Tier 3 reflow
- `ReaderCanvasView` 通过 `ViewCompat.setOnApplyWindowInsetsListener` 监听 inset 变化
- inset 变化 → `layoutConfigFor()` 重新计算 → `ReaderLayoutHasher.hash()` 变化 → Paginator 重跑
- 同理，`marginLeft` / `marginRight` 在横屏手势导航模式下也需叠加侧边 inset

#### 2.2.4 断字连字

| 项 | 值 |
|:---|:---|
| InvalidationScope | REFLOW |
| 存储层 | 三层体系，Enum `Hyphenation { NONE, AUTO, ENGLISH_ONLY }`，默认 NONE |
| 外部依赖 | Android 内置 `android.icu.text.BreakIterator`（API 24+），无需额外 JAR |
| Key 接入 | `LayoutKey` 新增 `hyphenation: Int` |

**断字算法策略（v3.0 二次审核修正）：**

优先使用 Android 内置 `BreakIterator.getLineInstance()` 获取断字机会点，**不自建 Liang 算法模式表**：

```kotlin
object HyphenationEngine {
    private val breakIterator = BreakIterator.getLineInstance()

    fun findBreakPoints(word: String): List<Int> {
        breakIterator.setText(word)
        val points = mutableListOf<Int>()
        var boundary = breakIterator.first()
        while (boundary != BreakIterator.DONE) {
            if (boundary in 2 until word.length - 1) {  // 至少保留 2 字符在前/1 字符在后
                points.add(boundary)
            }
            boundary = breakIterator.next()
        }
        return points
    }
}
```

**选择理由：**
- `BreakIterator.getLineInstance()` 在 API 24+ 可用（项目 minSdk 26 满足）
- 内置 ICU 数据覆盖主流语言（英/法/德/西/意等），无需 ship 50KB JSON 模式表
- 零外部依赖，无 `assets/` 目录膨胀
- 如实际效果不佳，再考虑引入 Liang 算法作为增强（通过 `HyphenationPatterns` JSON 覆盖）

**⚠️ 已知局限性（v5.1 补充）：** `BreakIterator.getLineInstance()` 返回的是**行断点**（可以在此处换行的位置），而非**音节断点**（连字符插入位置）。对英文单词 "international"，`getLineInstance()` 在单词边界返回断点，不会在 "in-ter-na-tion-al" 音节处返回。这意味着：
- 仅用 `getLineInstance()` 的断字效果有限——它更适合 word-wrap 而非 hyphenation
- 真正的连字符断字需要 Android `Hyphenator`（API 33+，项目 minSdk 26 不满足）或 ICU `BreakIterator` 配合音节字典数据

**Phase 4 决策点：**
1. 先实现 `getLineInstance()` 方案，验证对长单词的实际断字效果
2. 如果效果不佳（大量单词无法断字），引入轻量 Liang 模式表（仅英文，约 15KB）作为 `HyphenationEngine` 的增强后端
3. API 33+ 设备上优先使用系统 `Hyphenator`，低版本 fallback 到 Liang 模式表

**v4.0 确认：** `BreakIterator.getLineInstance()` 优先级高于自建 Liang 模式表。Phase 4 实现时先验证 BreakIterator 效果，仅在 ICU 数据覆盖不足时才引入 Liang 算法。

**Paginator 集成（在 WidthWindow block 计算中）：**

```kotlin
// calculateLine() — 行尾溢出时尝试断字
if (hyphenation != NONE && currentWidth > availableWidth && charCount > 0) {
    val lastWordStart = findLastWordStart(content, startOffset, charCount)
    if (lastWordStart >= 0 && isLatinWord(content, lastWordStart, charCount)) {
        val word = content.substring(startOffset + lastWordStart, startOffset + charCount)
        val breakPoints = HyphenationEngine.hyphenate(word, locale)
        for (bp in breakPoints.reversed()) {
            val prefixWidth = measureRange(widthWindow, startOffset + lastWordStart, bp)
            if (prefixWidth + hyphenWidth <= availableWidth) {
                charCount = lastWordStart + bp
                appendHyphen = true
                break
            }
        }
    }
}
```

- 中文字符不触发断字（CJK Unicode 范围过滤）
- 断字结果缓存在 `TextLine.hyphenationPoints: IntArray?` 字段中

---

### 2.3 阅读辅助

#### 2.3.1 Bionic Reading

| 项 | 值 |
|:---|:---|
| InvalidationScope | REFLOW + CONTENT |
| 存储层 | 三层体系，Boolean 类型，默认 false |
| Key 接入 | `LayoutKey` + `RenderKey` |
| ⚠️ 性能关键 | **必须在分页阶段预计算，禁止渲染阶段逐词处理** |

**v1.0 方案的致命缺陷：**

v1.0 在渲染阶段逐词调用 `drawText`，每行 30 个英文单词 = 60 次 JNI 调用（2 drawText + 2 measureText × 30 词），一页 30 行 = **1800 次 JNI 调用**。现有方案每行仅 1 次 `drawText`，一页 30 行 = 30 次。性能退化 **60 倍**，单页渲染从 8ms 推高到 50ms+，直接跌破 60fps 预算。

**v2.0 方案：分页阶段预计算 + 段合并渲染**

```kotlin
// TextLine 新增字段
data class TextLine(
    // ... 现有字段
    /** Bionic Reading 段落标记，null = 未启用 */
    val bionicSegments: List<BionicSegment>? = null,
)

data class BionicSegment(
    val startOffset: Int,  // 相对于 TextLine.startCharOffset
    val endOffset: Int,
    val isBold: Boolean,
)
```

**分页阶段预计算（BreakIterator + CJK 排除，v3.0 规范实现）：**

```kotlin
// renderContent() — 替代现有单次 drawText
if (line.bionicSegments != null) {
    var x = lineX
    for (seg in line.bionicSegments) {
        textPaint.typeface = if (seg.isBold) boldTypeface else normalTypeface
        val absStart = line.startCharOffset + seg.startOffset
        val absEnd = line.startCharOffset + seg.endOffset
        canvas.drawText(content, absStart, absEnd, x, line.baseline, textPaint)
        x += textPaint.measureText(content, absStart, absEnd)
    }
} else {
    // 现有路径：单次 drawText
    canvas.drawText(content, line.startCharOffset, line.endCharOffset, lineX, line.baseline, textPaint)
}
```

**性能分析（v3.0 二次审核修正）：**
- 分页阶段：每行一次线性扫描 O(n)，n = 字符数，约 0.1ms
- 渲染阶段：段合并后一行 15 个英文单词 ≈ 20 个 segment（粗/非粗交替，相邻非粗段合并）
- 实际 JNI 调用：约 20 drawText + 20 measureText = **40 次/行**
- 一页 30 行 = **1200 次 JNI 调用**（基线 30 次的 40 倍）
- 原始文档估算"30-40 次/行"偏乐观，实际更接近 40 次/行

**降级策略（按优先级）：**

1. **FakeBold 优化**：粗体段使用 `textPaint.isFakeBoldText = true` 替代切换 Typeface，省去 Typeface 查找开销。粗/非粗段共享同一 Paint 实例，仅切换 `isFakeBoldText` 标志
2. **measureText 消除**：预计算 segment 宽度并缓存到 `TextLine.bionicSegmentWidths: FloatArray`，渲染时直接使用缓存值，省去 measureText JNI 调用。优化后 JNI 调用降至 **20 drawText/行**
3. **自动关闭**：如优化后单页渲染仍 > 15ms，自动禁用 Bionic Reading 并 Toast 提示

**优化后预估：** 8ms（基线）+ 6ms（Bionic，20 drawText/行 × 30 行）= **14ms**，接近 60fps 预算边界。建议在 Phase 4 实现时先做 benchmark 验证。

**CJK 排除规则：** `calculateBionicSegments()` 已内置 CJK 检测（见上方规范实现）——中文/日文/韩文表意文字不做加粗分割，仅对拉丁/西里尔文字生效。`BreakIterator` 实例在 Paginator 构造时创建一次并复用。

#### 2.3.2 竖排阅读

| 项 | 值 |
|:---|:---|
| InvalidationScope | REFLOW（完全不同的分页算法） |
| 存储层 | 三层体系，Boolean 类型，默认 false |
| Key 接入 | `LayoutKey` 新增 `vertical: Boolean` |
| 现有基础设施 | **复用 `TextPage.columns` 和 `TextColumn`（已存在但未使用）** |

**Paginator 策略模式重构：**

```kotlin
// 新增分页策略接口（v4.0 签名：不绑死 ReaderLayoutConfig，各策略自行从 prefs 解析）
interface PaginationStrategy {
    fun paginate(
        content: String,
        prefs: ReaderPreferences,
        pageSize: PageSize,
        density: Float,
    ): TextChapter
}

// 现有逻辑提取为 HorizontalStrategy
class HorizontalPaginationStrategy(private val textMeasurer: TextMeasurer) : PaginationStrategy {
    override fun paginate(content: String, prefs: ReaderPreferences, pageSize: PageSize, density: Float): TextChapter {
        val config = prefs.toLayoutConfig(pageSize, density)  // 内部解析
        return paginateChapter(content, config)  // 现有逻辑
    }
}

// 新增 VerticalStrategy
class VerticalPaginationStrategy(private val textMeasurer: TextMeasurer) : PaginationStrategy {
    override fun paginate(content: String, prefs: ReaderPreferences, pageSize: PageSize, density: Float): TextChapter {
        // 列优先布局：从右向左，从上向下
        // 列宽 = pageSize.height - marginTop - marginBottom（自行从 prefs 解析）
        // 行高 = fontSize * lineHeight
        // 每列行数 = (pageSize.width - marginLeft - marginRight) / 行高
        // TextPage.columns 填充 TextColumn 列表
        TODO()
    }
}

// Paginator 委托给策略
class Paginator(var textMeasurer: TextMeasurer) {
    var strategy: PaginationStrategy = HorizontalPaginationStrategy(textMeasurer)

    fun paginateChapter(...) = strategy.paginate(...)
}
```

**TextPage 复用现有 columns 字段：**

```kotlin
// TextPage 已有 columns: List<TextColumn> = emptyList()
// TextColumn 已定义 startCharOffset, endCharOffset, startLine, endLine

// 竖排模式：columns 非空，渲染器据此切换坐标系
// 横排模式：columns 为空（现有行为不变）
```

**渲染器适配（v3.0 二次审核修正）：**

原始方案逐字符 `drawText` 会导致每行 30 字 = 30 次 JNI，一页 30 行 = **900 次 JNI**（基线的 30 倍）。修正为 **`StaticLayout` + `canvas.rotate(90°)` 整行绘制**：

```kotlin
// ReaderPageRenderer.renderContent()
if (page.columns.isNotEmpty()) {
    // 竖排模式：按列绘制
    for ((columnIndex, column) in page.columns.withIndex()) {
        val columnX = pageWidth - marginRight - (columnIndex + 1) * columnWidth

        canvas.save()
        canvas.translate(columnX, marginTop)
        canvas.rotate(90f)  // 坐标系旋转：X→下行，Y→左行

        for (lineIndex in column.startLine..column.endLine) {
            val line = page.lines[lineIndex]
            val text = content.substring(line.startCharOffset, line.endCharOffset)

            if (containsCjkPunctuation(text)) {
                // CJK 标点需要逐字符旋转（「」等需旋转 90°）
                drawVerticalLineWithPunctuationRotation(canvas, text, line)
            } else {
                // 纯文本：StaticLayout 整行绘制，1 次 JNI
                val layout = StaticLayout.Builder
                    .obtain(text, 0, text.length, textPaint, columnHeight.toInt())
                    .build()
                layout.draw(canvas)
            }
            canvas.translate(lineHeight, 0f)  // 下一"行"（竖排中实际是左移一列内行位）
        }
        canvas.restore()
    }
} else {
    // 现有横排逻辑
}
```

**性能对比：**

| 方案 | JNI 调用/页 | 预估耗时 |
|:---|:---|:---|
| 逐字符 drawText（v2.0 原始方案） | ~900 | ~25ms ❌ |
| StaticLayout + rotate（v3.0 修正） | ~30 | ~10ms ✅ |
| StaticLayout + 标点逐字符（混合方案） | ~60 | ~12ms ✅ |

**CJK 标点旋转** 仅在检测到 `「」『』（）【】` 等标点时触发逐字符路径，纯文本行走 StaticLayout 快速路径。预渲染标点 bitmap 缓存可进一步优化（§7 风险表已提及）。

**建议分期：**
1. Phase 4a — `VerticalPaginationStrategy` + `TextPage.columns` 填充
2. Phase 4b — 渲染器竖排坐标系适配
3. Phase 4c — 标点旋转 + CJK 优化
4. Phase 4d — 翻页方向适配（从右向左）

#### 2.3.3 双页模式

| 项 | 值 |
|:---|:---|
| InvalidationScope | REFLOW + PAGE_DELEGATE |
| 存储层 | 三层体系，Enum `DualPageMode { AUTO, SINGLE, DUAL }`，默认 AUTO |
| Key 接入 | `LayoutKey` 新增 `dualPage: Boolean` |
| ⚠️ 内存关键 | **单 View 双区域方案，禁止两个 ReaderCanvasView** |

**v1.0 方案的内存问题：**

v1.0 提议两个 `ReaderCanvasView` 并排。每个 View 持有独立的 Paint 对象组（textPaint/headerPaint/footerPaint/progressPaint/dividerPaint/batteryStrokePaint/batteryFillPaint）、触摸处理器、PageDelegate、PageBitmapCache。两份 = **双倍内存开销**。

**v2.0 方案：单 View + clipRect 双区域**

```kotlin
// ReaderCanvasView.onDraw() — 单 View 内部双区域
override fun onDraw(canvas: Canvas) {
    val prefs = currentPrefs
    val isDual = prefs.dualPageMode == DualPageMode.DUAL ||
        (prefs.dualPageMode == DualPageMode.AUTO && isLandscape && width > 800 * density)

    if (isDual) {
        val halfWidth = width / 2

        // 左页
        canvas.save()
        canvas.clipRect(0, 0, halfWidth, height)
        renderPageRegion(canvas, leftPage, 0f, halfWidth.toFloat())
        canvas.restore()

        // 右页
        canvas.save()
        canvas.clipRect(halfWidth, 0, width, height)
        renderPageRegion(canvas, rightPage, halfWidth.toFloat(), width.toFloat())
        canvas.restore()

        // 中缝装饰
        dividerPaint.color = Color.GRAY.copy(alpha = 0.3f)
        canvas.drawLine(halfWidth.toFloat(), 0f, halfWidth.toFloat(), height.toFloat(), dividerPaint)
    } else {
        renderPageRegion(canvas, currentPage, 0f, width.toFloat())
    }
}

private fun renderPageRegion(canvas: Canvas, page: TextPage?, left: Float, right: Float) {
    if (page == null) return
    canvas.save()
    canvas.translate(left, 0f)
    // v5.1 修正：遵循现有 onDraw 双路径模式（§1.4.1），
    // 静止态仅绘制 shell + canvas，overlayRecorder 不单独绘制。
    // 双页模式走静止路径（PageDelegate 不感知双页），
    // 如需 overlay（TTS 高亮/选区），走 compositeRecorder 路径。
    page.shellRecorder.draw(canvas)
    page.canvasRecorder.draw(canvas)
    canvas.restore()
}
```

**翻页适配：**

```kotlin
// 双页模式下一次翻两页
fun nextPage() {
    if (isDualPageMode) {
        currentPageIndex += 2
    } else {
        currentPageIndex += 1
    }
}
```

- PageDelegate 不感知双页（它只接收 CanvasRecorder，双页合成在 View 层）
- 共享所有 Paint 对象、触摸处理器、主题系统
- 内存开销：仅多一份 `TextPage` 引用（可忽略）

#### 2.3.4 阅读聚焦线

| 项 | 值 |
|:---|:---|
| InvalidationScope | **VIEW_INVALIDATE**（onDraw 末尾直接 drawLine，不进 recorder） |
| 存储层 | 三层体系，Boolean 类型，默认 false |
| Key 接入 | —（不走 recorder，仅 View.invalidate()） |

**方案（v5.1 修正：原 OVERLAY scope 与 §1.4.1 矛盾）：**

聚焦线必须在 `ReaderCanvasView.onDraw()` 的 if/else 双路径之后注入，与色温同一位置。原因：静止态下 `overlayRecorder` 不单独绘制（§1.4.1 已验证），如果走 overlayRecorder 则聚焦线在静止态不可见。

```kotlin
// ReaderCanvasView.onDraw() — 在双路径之后，与色温滤镜同一位置
// ... 省略 if/else 双路径 ...

// VIEW_INVALIDATE：聚焦线（不进入任何 recorder 缓存）
if (focusLine && currentReadingLine != null) {
    val line = currentReadingLine!!
    val y = line.baseline
    focusLinePaint.color = accentColor.copy(alpha = 0.2f)
    focusLinePaint.strokeWidth = 2f * density
    canvas.drawLine(marginLeft, y, width - marginRight, y, focusLinePaint)
}
```

- 绘制在 onDraw() 最顶层（与色温同层），不走 overlayRecorder
- "当前阅读行"基于 `ReadingStateManager` 的字符偏移 → `TextPage.lines` 查找
- 变化时仅触发 `View.invalidate()`，不触发任何 recorder 重录

#### 2.3.5 背景纹理（v3.0 新增）

| 项 | 值 |
|:---|:---|
| InvalidationScope | SHELL（背景属于壳层） |
| 存储层 | 三层体系，`backgroundTexture: String?`（内置纹理名或自定义 URI），默认 null（纯色） |
| Key 接入 | `ShellKey` 新增 `texture: String?` |

**渲染方案：** 在 `ReaderPageRenderer.renderShell()` 中使用 `android.graphics.BitmapShader` 绘制纹理背景。**注意：** `Modifier.drawWithCache` 是 Compose API，不适用于 `ReaderCanvasView`（AndroidView + `android.graphics.Canvas`），必须在 `renderShell()` 中使用原生 `BitmapShader`。

```kotlin
// ReaderPageRenderer 新增成员
private var textureShader: BitmapShader? = null
private var texturePaint: Paint = Paint().apply { isAntiAlias = true }
private var currentTextureKey: String? = null

// renderShell() — 在绘制纯色背景之后
fun renderShell(canvas: Canvas, ctx: PageRenderContext, ...) {
    // 1. 纯色背景（现有逻辑）
    canvas.drawRect(0f, 0f, width, height, backgroundPaint)

    // 2. 纹理叠加（shellRecorder 层，低频变化）
    val textureKey = ctx.backgroundTexture
    if (textureKey != null) {
        // 纹理资源变化时重建 shader
        if (textureKey != currentTextureKey) {
            val bitmap = loadTextureBitmap(ctx.appContext, textureKey)
            textureShader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            texturePaint.shader = textureShader
            texturePaint.alpha = (ctx.textureAlpha * 255).toInt()  // 默认 0.12
            currentTextureKey = textureKey
        }
        canvas.drawRect(0f, 0f, width, height, texturePaint)
    }

    // 3. 页眉、页脚、进度条、电池（现有逻辑）
    ...
}

private fun loadTextureBitmap(context: Context, key: String): Bitmap {
    return if (key.startsWith("builtin:")) {
        // 内置纹理：assets/textures/{name}.png（低分辨率，约 128×128）
        val name = key.removePrefix("builtin:")
        context.assets.open("textures/$name.png").use { BitmapFactory.decodeStream(it) }
    } else {
        // 自定义图片：经 ContentResolver 加载
        context.contentResolver.openInputStream(Uri.parse(key))?.use {
            BitmapFactory.decodeStream(it)
        } ?: createFallbackTexture()
    }
}
```

**内置纹理预设：** kraft 纸 / 仿麻 / 米格 / 素白（4 种，以低分辨率 PNG 存放在 `assets/textures/`）

**UI：** 页面 Tab → 显示模式 → 纹理选择器（网格预览 + "自定义"入口）

**性能：**
- `BitmapShader` 在 GPU 上执行纹理平铺，CPU 开销接近零
- shader 和 paint 仅在 `textureKey` 变化时重建（`currentTextureKey` 比对）
- 纹理 alpha 默认 0.12（12% 不透明度），不影响正文可读性
- 纹理变化 → `shellRecorder.invalidate()` → SHELL scope，不触发 reflow

---

### 2.4 交互与行为

#### 2.4.1 手势绑定

| 项 | 值 |
|:---|:---|
| InvalidationScope | 无（纯行为配置） |
| 存储层 | 三层体系，`GestureConfig` 为 `@Serializable data class`，作为 `BookReaderPrefsOverrides.gestureConfig: GestureConfig?` 字段序列化存储 |
| ⚠️ 架构关键 | **扩展现有 CanvasTouchHandler，禁止新建手势系统** |

**v1.0 方案的架构问题：**

v1.0 提议新建 `GestureDetector` + `GestureConfig` 系统。但现有 `CanvasTouchHandler` 已具备：
- 3×3 触摸区域网格（`TouchZoneCalculator`）
- 边缘检测 + 边缘点击翻页
- `GestureDetector` 长按/单点检测
- 文本选区手势拦截

两套手势系统并存会导致维护成本翻倍和冲突风险。

**v2.0 方案：扩展 CanvasTouchHandler.Callbacks**

```kotlin
// 新增动作枚举
enum class GestureAction {
    NONE,
    PREV_PAGE,
    NEXT_PAGE,
    TOGGLE_TOOLBAR,
    TOGGLE_DIRECTORY,
    ADD_BOOKMARK,
    TOGGLE_THEME,
    TOGGLE_IMMERSIVE,
    SCROLL_UP,
    SCROLL_DOWN,
}

// 新增配置
@Serializable
data class GestureConfig(
    val topLeft: GestureAction = GestureAction.PREV_PAGE,
    val topCenter: GestureAction = GestureAction.TOGGLE_TOOLBAR,
    val topRight: GestureAction = GestureAction.NEXT_PAGE,
    val middleLeft: GestureAction = GestureAction.PREV_PAGE,
    val middleCenter: GestureAction = GestureAction.TOGGLE_TOOLBAR,
    val middleRight: GestureAction = GestureAction.NEXT_PAGE,
    val bottomLeft: GestureAction = GestureAction.PREV_PAGE,
    val bottomCenter: GestureAction = GestureAction.TOGGLE_TOOLBAR,
    val bottomRight: GestureAction = GestureAction.NEXT_PAGE,
    val doubleTap: GestureAction = GestureAction.NONE,
    val longPress: GestureAction = GestureAction.NONE,
    val swipeUp: GestureAction = GestureAction.NONE,
    val swipeDown: GestureAction = GestureAction.NONE,
)

// 现有 Callbacks 接口扩展
interface Callbacks {
    // 旧接口（保留兼容）
    fun onCenterClicked()
    fun onLongPress(x: Float, y: Float)

    // 新接口：action-based
    fun onAction(action: GestureAction, x: Float = 0f, y: Float = 0f)
}

// CanvasTouchHandler 内部
override fun onSingleTapUp(event: MotionEvent): Boolean {
    val zone = touchZoneCalculator.calculate(event.x, event.y, w, h)
    val action = gestureConfig.getAction(zone)  // 从配置读取
    cb.onAction(action, event.x, event.y)
    return true
}
```

**冲突解决优先级：**

```
翻页动画拖拽 > 文本选区 > 自定义手势 > 默认热区行为
```

- `PageDelegate.onTouch()` 返回 true 时，自定义手势不触发
- 文本选区激活时，长按不触发自定义 `longPress` 动作

#### 2.4.2 翻页振动反馈

| 项 | 值 |
|:---|:---|
| InvalidationScope | 无 |
| 存储层 | 三层体系，Boolean 类型，默认 false |

```kotlin
// ReaderCanvasView — 翻页完成时
fun onPageChanged() {
    if (currentPrefs.hapticFeedback) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}
```

#### 2.4.3 屏幕方向锁定

| 项 | 值 |
|:---|:---|
| InvalidationScope | 无 |
| 存储层 | 三层体系，Enum `OrientationLock { SYSTEM, PORTRAIT, LANDSCAPE }`，默认 SYSTEM |

```kotlin
// ReaderScreen
LaunchedEffect(orientationLock) {
    activity.requestedOrientation = when (orientationLock) {
        OrientationLock.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        OrientationLock.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        OrientationLock.SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}
```

退出阅读页时在 `DisposableEffect.onDispose` 中恢复 `SYSTEM`。

#### 2.4.4 翻页动画速度

| 项 | 值 |
|:---|:---|
| InvalidationScope | PAGE_DELEGATE |
| 存储层 | 三层体系，Enum `PageAnimSpeed { FAST(100), NORMAL(250), SLOW(400) }`，默认 NORMAL |

```kotlin
// PageDelegateFactory.create() — 传入 speed.durationMs
HorizontalPageDelegate(scrollDuration = speed.durationMs)
CoverPageDelegate(animDuration = speed.durationMs)
SimulationPageDelegate(duration = speed.durationMs)
```

所有 5 种 PageDelegate 实现已有 `duration` 参数，仅需从 `ReaderPreferences` 桥接。

**AnimationSpec 缓存（v3.0 新增）：** `PageAnimSpec` 对象必须在 `PageDelegate` 构造时创建一次并持有，不能在每帧动画回调中重建。`PageDelegateFactory.create()` 内部根据 `speed` 选择对应的 spec：

```kotlin
// PageDelegateFactory.create()
private fun createAnimSpec(speed: PageAnimSpeed): AnimSpec = when (speed) {
    PageAnimSpeed.FAST   -> AnimSpec(durationMs = 100, easing = LinearOutSlowIn)
    PageAnimSpeed.NORMAL -> AnimSpec(durationMs = 250, easing = FastOutSlowIn)
    PageAnimSpeed.SLOW   -> AnimSpec(durationMs = 400, easing = StandardDecelerate)
}
```

`InvalidationScope = PAGE_DELEGATE` 确保速度变化时重建 `PageDelegate`（而非复用旧的 spec），新 spec 随 Delegate 一起创建，生命周期一致。

---

### 2.5 TTS 朗读

#### 2.5.1 TTS 引擎集成

| 项 | 值 |
|:---|:---|
| InvalidationScope | 无（后台线程运行） |
| 新增模块 | `core/tts/TtsManager.kt` + `feature/reader/TtsController.kt` |
| 存储层 | `ttsSpeed` / `ttsPitch` 已存在于 ReaderPreferences；新增 `ttsVoice: String`、`ttsAutoPage: Boolean`、`ttsTimer: Int` |

**TtsManager 错误处理：**

```kotlin
class TtsManager(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var initError: String? = null

    suspend fun initialize() = suspendCoroutine { cont ->
        tts = TextToSpeech(context) { status ->
            when (status) {
                TextToSpeech.SUCCESS -> {
                    isInitialized = true
                    tts?.setOnUtteranceProgressListener(utteranceListener)
                    cont.resume(Result.success(Unit))
                }
                TextToSpeech.ERROR -> {
                    initError = "TTS engine initialization failed"
                    cont.resume(Result.failure(RuntimeException(initError)))
                }
            }
        }
    }

    fun speak(text: String, utteranceId: String): Boolean {
        if (!isInitialized) {
            Log.w("TtsManager", "Engine not initialized: $initError")
            return false
        }
        val result = tts?.speak(text, TextToSpeech.QUEUE_ADD, Bundle(), utteranceId)
        return result == TextToSpeech.SUCCESS
    }

    // onError 回调处理
    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) { /* 更新 UI 状态 */ }
        override fun onDone(utteranceId: String) { /* 自动翻页或停止 */ }
        override fun onError(utteranceId: String, errorCode: Int) {
            Log.e("TtsManager", "TTS error $errorCode for utterance $utteranceId")
            // 尝试恢复：跳到下一段
            onErrorRecovery(utteranceId)
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
```

**TTS 高亮同步（OVERLAY Scope）：**

```kotlin
// TtsController → overlayRecorder
private fun updateHighlight(startOffset: Int, endOffset: Int) {
    val range = SelectionRange(startOffset, endOffset, SelectionType.TTS)
    uiState.value.currentPage?.let { page ->
        page.lines.forEach { line ->
            if (line.intersects(range)) {
                line.invalidateSelf()  // 仅失效受影响的行
            }
        }
        page.invalidateOverlay()
    }
}
```

- 高亮使用 `overlayRecorder`（独立于正文渲染），TTS 播放时不影响 `contentRecorder`
- 句子分割使用 `BreakIterator.getSentenceInstance()`

---

### 2.6 内容处理

#### 2.6.1 统一文本处理管道

| 项 | 值 |
|:---|:---|
| InvalidationScope | REFLOW（文本内容变化 → 重新分页） |
| 架构 | `TextProcessor` 接口 + 管道链式执行 |

**当前文本处理管道：**

```
原文 → ChineseConverter → PanguSpacing → Paginator
```

**v2.0 统一管道：**

```
原文 → [ChineseConverter, PanguSpacing, AdFilter, RegexReplacer, BionicMarker] → Paginator
```

```kotlin
// 统一接口
interface TextProcessor {
    /** 处理顺序，数值越小越先执行 */
    val order: Int
    fun process(text: String, context: ProcessingContext): String
}

data class ProcessingContext(
    val adFiltering: Boolean,
    val regexRules: List<RegexRule>,
    val locale: String,
    // bionicReading 不在此处——Bionic 段在 Paginator 分页阶段直接计算（§2.3.1），
    // 不插入控制字符到文本中，避免字符偏移/选区/宽度计算错乱。
)

// 管道管理器
class TextProcessingPipeline(processors: List<TextProcessor>) {
    private val sorted = processors.sortedBy { it.order }

    init {
        // v4.0：断言 order 无重复，防止同优先级 processor 执行顺序不确定
        val duplicates = processors.groupBy { it.order }.filter { it.value.size > 1 }
        require(duplicates.isEmpty()) {
            "Duplicate processor orders: ${duplicates.keys}. Each processor must have a unique order."
        }
    }

    fun process(text: String, context: ProcessingContext): String =
        sorted.fold(text) { acc, proc -> proc.process(acc, context) }
}

// 各处理器
class ChineseConvertProcessor : TextProcessor {
    override val order = 100
    override fun process(text: String, ctx: ProcessingContext) = ChineseConverter.convert(text, ctx.chineseConvert)
}

class PanguSpacingProcessor : TextProcessor {
    override val order = 200
    override fun process(text: String, ctx: ProcessingContext) =
        if (ctx.usePanguSpacing) PanguSpacing.addSpaces(text) else text
}

class AdFilterProcessor : TextProcessor {
    override val order = 300
    override fun process(text: String, ctx: ProcessingContext): String {
        if (!ctx.adFiltering) return text
        var result = text
        AD_PATTERNS.forEach { result = it.replace(result, "") }
        return result
    }

    companion object {
        private val AD_PATTERNS = listOf(
            Regex("点击.*下载"),
            Regex("扫码关注.*"),
            Regex("关注公众号.*"),
            Regex("www\\.[a-zA-Z0-9.-]+\\.(com|net|org|cn)"),
            Regex("https?://\\S+"),
        )
    }
}

class RegexReplaceProcessor(private val cache: RegexRuleCache) : TextProcessor {
    override val order = 400
    override fun process(text: String, ctx: ProcessingContext) = cache.apply(text)
}

// BionicMarkerProcessor 已废弃（v4.0）
// 原方案在文本中插入控制字符标记粗体段，与分页预计算方案矛盾。
// 统一为 Paginator.calculateBionicSegments() 在分页阶段直接计算。
}
```

**正则编译缓存：**

```kotlin
class RegexRuleCache(rules: List<RegexRule>) {
    // 预编译，只在规则列表变化时重建
    private val compiled: List<Pair<Regex, String>> = rules
        .filter { it.enabled }
        .mapNotNull { rule ->
            try {
                Regex(rule.pattern, RegexOption.MULTILINE) to rule.replacement
            } catch (e: PatternSyntaxException) {
                Log.w("RegexCache", "Invalid pattern: ${rule.pattern}", e)
                null
            }
        }

    fun apply(text: String): String {
        var result = text
        compiled.forEach { (regex, replacement) ->
            result = regex.replace(result, replacement)
        }
        return result
    }
}
```

- 在 `ChapterPaginationCoordinator` 中，规则列表变化时重建 cache
- 正则编译是昂贵操作（`Pattern.compile()`），预编译避免每章重复
- 安全性：`Regex` 构造时 `try-catch` 捕获语法错误，跳过无效规则

**管道集成点：**

```kotlin
// ChapterPaginationCoordinator
private val textPipeline = TextProcessingPipeline(listOf(
    ChineseConvertProcessor(),
    PanguSpacingProcessor(),
    AdFilterProcessor(),
    RegexReplaceProcessor(regexRuleCache),
    // Bionic Reading 不走文本管道，由 Paginator.calculateBionicSegments() 在分页阶段处理
))

fun paginateChapter(content: String, prefs: ReaderPreferences): TextChapter {
    val context = ProcessingContext(
        adFiltering = prefs.adFiltering,
        regexRules = prefs.textReplaceRules,
        locale = prefs.locale,
    )
    val processed = textPipeline.process(content, context)
    return paginator.paginateChapter(chapterIndex, title, processed, layoutConfig)
}
```

---

## 3. 预设系统兼容

### 3.1 ReaderPreferences 新字段默认值

所有新字段必须声明默认值，确保 `kotlinx.serialization` 反序列化旧预设时自动填充：

```kotlin
@Serializable
data class ReaderPreferences(
    // ... 现有 50+ 字段（已有默认值）
    // 新增字段（blueLightFilter 不在此处——它是 colorTemperature 的快捷预设，见 §2.1.2）
    val colorTemperature: Float = 6500f,
    val eyeCareReminderInterval: Int = 0,
    val wordSpacing: Float = 0f,
    val paragraphDivider: Boolean = false,
    val marginTop: Float? = null,
    val marginBottom: Float? = null,
    val marginLeft: Float? = null,
    val marginRight: Float? = null,
    val bionicReading: Boolean = false,
    val verticalText: Boolean = false,
    val dualPageMode: DualPageMode = DualPageMode.AUTO,
    val focusLine: Boolean = false,
    val gestureConfig: GestureConfig = GestureConfig(),
    val hapticFeedback: Boolean = false,
    val orientationLock: OrientationLock = OrientationLock.SYSTEM,
    val pageAnimSpeed: PageAnimSpeed = PageAnimSpeed.NORMAL,
    val adFiltering: Boolean = false,
    val textReplaceRules: List<RegexRule> = emptyList(),
    val ttsVoice: String = "",
    val ttsAutoPage: Boolean = true,
    val ttsTimer: Int = 0,
)
```

### 3.2 BookReaderPrefsOverrides 扩展

```kotlin
@Serializable
data class BookReaderPrefsOverrides(
    // ... 现有字段（nullable）
    val colorTemperature: Float? = null,
    // blueLightFilter 不在此处——它是 colorTemperature 的快捷预设，见 §2.1.2
    val wordSpacing: Float? = null,
    val paragraphDivider: Boolean? = null,
    val marginTop: Float? = null,
    val marginBottom: Float? = null,
    val marginLeft: Float? = null,
    val marginRight: Float? = null,
    val bionicReading: Boolean? = null,
    // ... 其余新字段同理
)
```

`ReaderSettingsManager.copyGlobalToBook()` 时，仅将非 null 的覆盖字段写入 `BookReaderPrefsEntity`。

---

## 4. 设置面板布局重构（v5.0）

### 4.0 设计目标

现有 `ModalBottomSheet` + 4-Tab 平铺列表在 50+ 设置下面临三个核心问题：

1. **信息过载** — 所有设置平铺在同一 Tab 内，用户需大量滚动
2. **高频操作深埋** — 字号、主题等最常用设置不在默认视图
3. **缺乏空间映射** — 边距用 2 个滑块，页眉页脚用 6 个下拉框，与阅读页视觉脱节

v5.0 通过**双层渐进式展开 + 3-Tab 重组 + 卡片化分组 + 空间映射控件**系统性解决。

### 4.1 双层渐进式展开

**ModalBottomSheet → BottomSheetScaffold 迁移：**

```kotlin
// 当前：ModalBottomSheet，skipPartiallyExpanded = true，只能全展开或关闭
ModalBottomSheet(onDismissRequest = { /* ... */ }) { QuickSettingsContent() }

// 目标：BottomSheetScaffold，支持 Peek → Expanded 两态渐进
@Composable
fun ReaderQuickSettingsScaffold(
    sheetState: SheetState = rememberBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
    ),
    scaffoldState: BottomSheetScaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = sheetState,
    ),
) {
    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = peekHeight,           // Peek 态高度
        sheetDragHandle = { DragHandle() },     // 顶部拖拽条
        sheetContent = {
            // v5.1 修正：避免用 when(targetValue) 硬切换导致拖拽中途内容突变。
            // 改用 sheetProgress（0..1 浮点值）控制渐变：
            //   - PeekContent 始终渲染
            //   - ExpandedContent 通过 AnimatedVisibility 配合 sheet offset 渐入
            // 参考 BottomSheetScaffold 源码中 sheetState.requireOffset() 获取连续进度值。
            PeekContent()   // 高频操作（始终可见）
            AnimatedVisibility(
                visible = sheetState.targetValue == SheetValue.Expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                ExpandedContent() // 完整设置（渐变显示）
            }
        },
    )
}
```

**Peek 态（约 30% 屏高）— 高频操作区：**
- `ScopeHeader`（全局/本书 切换 + 重置）— 常驻
- `FontSizeStepper`（A-/A+ 步进器 + 当前值显示）— 最高频操作
- `ThemeColorRow`（主题色块秒切）— 第二高频
- 一行快捷 Chip（暗色模式 / 护眼 / 横屏锁定）

**Expanded 态（约 80% 屏高）— 完整设置：**
- 3-Tab TabRow（排版与字体 / 外观与显示 / 行为与手势）
- 每个 Tab 内为卡片化分组的设置项
- 顶部嵌入实时预览区（仅 Expanded 态可见）

### 4.2 3-Tab 重组

现有 4-Tab（排版 / 字体 / 页面 / 交互）改为 3-Tab，Tab → UiGroup 映射提取为配置：

```kotlin
enum class SettingsTab(val label: String, val groups: List<UiGroup>) {
    TYPE_AND_FONT("排版与字体", listOf(
        UiGroup.FONT_BASICS,      // 字号、字体、字重、标题字体
        UiGroup.TEXT_LAYOUT,      // 行距、段距、缩进、字间距、词间距
        UiGroup.TEXT_STYLE,       // 对齐、简繁转换、盘古之风
        UiGroup.ADVANCED_READING, // Bionic Reading、竖排阅读
    )),
    APPEARANCE("外观与显示", listOf(
        UiGroup.THEME,            // 主题色、自定义主题
        UiGroup.PAGE_CHROME,      // 页眉页脚、进度条、分割线
        UiGroup.DISPLAY_MODE,     // 双页模式、背景纹理、翻页动画
        UiGroup.VISUAL_AIDS,      // 聚焦线、色温、蓝光过滤
    )),
    BEHAVIOR("行为与手势", listOf(
        UiGroup.PAGE_TURN,        // 翻页动画、音量键、边缘翻页、自动翻页
        UiGroup.GESTURE,          // 手势绑定、振动反馈、左侧热区
        UiGroup.EYE_CARE,         // 护眼提醒、番茄阅读
        UiGroup.GENERAL,          // 沉浸模式、保持亮屏、方向锁定
    )),
}
```

**迁移策略：** 现有 `SharedComponents.kt` 中的 `TAB_LAYOUT=0, TAB_FONT=1, TAB_PAGE=2, TAB_INTERACTION=3` 改为 `SettingsTab` 枚举驱动。Tab 切换动画保持 `AnimatedContent` + `tween(200)`。

### 4.3 卡片化分组

平铺列表改为 Card 分组，由 Registry `UiGroup` 驱动生成：

```kotlin
enum class UiGroup {
    FONT_BASICS, TEXT_LAYOUT, TEXT_STYLE, ADVANCED_READING,
    THEME, PAGE_CHROME, DISPLAY_MODE, VISUAL_AIDS,
    PAGE_TURN, GESTURE, EYE_CARE, GENERAL,
}

data class SettingDefinition(
    val key: String,
    val defaultValue: Any,
    val storageTier: StorageTier,
    val scope: InvalidationScope,
    val recompositionTier: RecompositionTier,
    val uiGroup: UiGroup,          // 决定所属卡片
    val includeInPreset: Boolean,
    val previewStrategy: PreviewStrategy,
)
```

**SettingsCard 组件：**

```kotlin
@Composable
fun SettingsCard(
    title: String,
    settings: List<SettingDefinition>,
    currentPrefs: ReaderPreferences,
    onSettingChanged: (String, Any) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            settings.forEach { def ->
                SettingRow(definition = def, value = currentPrefs.getValue(def.key),
                    onChange = { onSettingChanged(def.key, it) })
            }
        }
    }
}
```

**卡片分组规则：** 每个 Tab 内的 `UiGroup` 渲染为一张 `SettingsCard`。高级卡片默认收起（`ExpandableSection`），通过 `AnimatedVisibility` 展开。

### 4.4 Peek 态快捷操作

**FontSizeStepper — 最高频操作：**

```kotlin
@Composable
fun FontSizeStepper(
    value: Int,
    range: IntRange = 12..32,
    step: Int = 1,
    onValueChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepperButton(icon = Icons.Filled.Remove, enabled = value > range.first,
            onClick = { onValueChange((value - step).coerceIn(range)) })
        Text("$value sp", modifier = Modifier.width(48.dp), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium)
        StepperButton(icon = Icons.Filled.Add, enabled = value < range.last,
            onClick = { onValueChange((value + step).coerceIn(range)) })
    }
}
```

**主题色块秒切：** 复用现有 `ThemeColorRow`，在 Peek 态常驻显示。用户无需进入 Expanded 态即可一键切换主题。

**快捷 Chip 行：** 暗色模式 / 护眼 / 横屏锁定三个高频开关，以 FilterChip 形式常驻 Peek 态底部。

### 4.5 预览区改进

**v4.0 已确定：** 复用真实 Paginator/Renderer 实例，仅缩小 pageSize。v5.0 进一步改进：

- **预览内容：** 从硬编码文本改为当前页首行截取（`currentChapter.firstNChars(120)`），保留 fallback 硬编码
- **显示时机：** 仅 Expanded 态可见（Peek 态空间有限，字号/主题切换不需要预览）
- **更新策略：** `PreviewStrategy.LIVE` 类设置（字号/行距/字重/边距）实时更新；`ON_APPLY` 类设置（字体/主题）点击应用后更新

```kotlin
@Composable
fun SettingsPreviewArea(
    currentPrefs: ReaderPreferences,
    previewPrefs: ReaderPreferences,
    currentChapterText: String?,
    paginator: Paginator,
    renderer: ReaderPageRenderer,
) {
    val text = currentChapterText?.take(120)
        ?: "天地玄黄，宇宙洪荒。The quick brown fox."
    val config = remember(previewPrefs) { previewPrefs.toPreviewLayoutConfig(density) }
    val page = remember(config) {
        paginator.paginateChapter(0, "", text, config, maxLines = 3)
    }
    Canvas(modifier = Modifier.fillMaxWidth().height(72.dp)
        .clip(RoundedCornerShape(8.dp)).background(config.backgroundColor)) {
        renderer.renderPreview(canvas, page)
    }
}
```

### 4.6 空间映射控件

#### 4.6.1 边距控件 — 微型屏幕缩略图 + 四边步进器

替代现有 2 个滑块（水平边距 + 垂直边距），提供空间直觉：

```kotlin
@Composable
fun VisualMarginControl(
    margins: MarginValues,  // start, end, top, bottom
    onMarginsChange: (MarginValues) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 顶部边距步进器
        MarginStepperRow(label = "上", value = margins.top, onChange = { onMarginsChange(margins.copy(top = it)) })
        Row {
            // 左侧边距步进器
            MarginStepperColumn(value = margins.start, onChange = { onMarginsChange(margins.copy(start = it)) })
            // 微型屏幕缩略图 — 显示边距比例
            Box(modifier = Modifier.size(80.dp).drawBehind {
                drawRect(Color.LightGray)
                drawRect(Color.White,
                    topLeft = Offset(margins.start.toPx(), margins.top.toPx()),
                    size = Size(size.width - margins.start.toPx() - margins.end.toPx(),
                               size.height - margins.top.toPx() - margins.bottom.toPx()))
            })
            // 右侧边距步进器
            MarginStepperColumn(value = margins.end, onChange = { onMarginsChange(margins.copy(end = it)) })
        }
        // 底部边距步进器
        MarginStepperRow(label = "下", value = margins.bottom, onChange = { onMarginsChange(margins.copy(bottom = it)) })
    }
}
```

#### 4.6.2 页眉页脚 — 微型 Wireframe + 原地气泡选择

替代现有 6 个下拉框，提供可视化预览：

```kotlin
@Composable
fun HeaderFooterWireframe(
    headerSlots: HeaderFooterSlots,
    footerSlots: HeaderFooterSlots,
    onSlotChange: (SlotPosition, SlotValue) -> Unit,
) {
    Column {
        // 页眉 wireframe
        WireframeRow(slots = headerSlots, label = "页眉",
            onSlotTapped = { pos -> showBubblePicker(pos, SlotTarget.HEADER) })
        Divider()
        // 页面主体示意
        Box(modifier = Modifier.fillMaxWidth().height(40.dp)
            .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(4.dp)))
        Divider()
        // 页脚 wireframe
        WireframeRow(slots = footerSlots, label = "页脚",
            onSlotTapped = { pos -> showBubblePicker(pos, SlotTarget.FOOTER) })
    }
}
```

**气泡选择器：** 点击 wireframe 中的 slot 后，原地弹出 `Popup` 气泡，列出可选值（书名/章节/页码/时间/百分比/空）。选择后立即更新 wireframe 预览。

### 4.7 复合控件：Slider + Stepper

连续值控件（行距、字间距、段间距、色温等）两端加步进图标，粗调 + 精调结合：

```kotlin
@Composable
fun StepperSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float = 0.1f,
    onValueChange: (Float) -> Unit,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onValueChange((value - step).coerceIn(range)) }) {
            Icon(Icons.Filled.Remove, contentDescription = "减小")
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onValueChange((value + step).coerceIn(range)) }) {
            Icon(Icons.Filled.Add, contentDescription = "增大")
        }
        Text("%.1f".format(value), modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
    }
}
```

**适用控件：** 行距 (1.0-3.0)、字间距 (0-0.5em)、段间距 (0-2.0em)、色温 (2000-6500K)、页眉页脚字号比 (0.5-1.5)、蓝光过滤强度 (0-100%)。

### 4.8 动画与视觉层次

**Spring 弹性动画：**

```kotlin
// Tab 切换
AnimatedContent(targetState = selectedTab,
    transitionSpec = { fadeIn(spring()) + slideInHorizontally(spring()) togetherWith
                       fadeOut(spring()) + slideOutHorizontally(spring()) })

// 面板展开（Peek → Expanded）
val sheetProgress by animateFloatAsState(
    targetValue = if (isExpanded) 1f else 0f,
    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
)
```

**背景 Scrim：** Expanded 态时显示半透明蒙层（API 31+ 可用 blur，低版本 fallback 为纯色半透明）：

```kotlin
// 非 blur 方案（兼容 API 26+）
Box(modifier = Modifier.fillMaxSize()
    .background(Color.Black.copy(alpha = 0.32f * sheetProgress))
    .clickable { collapseSheet() })
```

### 4.9 预设系统扩展（保留 v3.0 设计）

**预设范围严格收敛：** 预设只记录"影响文本呈现和页面结构"的设置，不记录"视觉效果"和"交互行为"。

```
纳入预设（includeInPreset = true）:
  Layout:  fontSize, lineHeight, margins, indent, wordSpacing, hyphenation, paragraphDivider
  Style:   readingFont, titleFont, fontWeight, textAlign, letterSpacing, bionicReading
  Chrome:  header/footer slots, progressStyle, headerFooterAlpha, showHeaderLine/FooterLine

排除出预设（includeInPreset = false）:
  Overlay:  colorTemperature, focusLine
  行为:     hapticFeedback, orientationLock, gestureConfig, volumeKeyTurnPage, autoPageTurn
  翻页:     pageAnimType, pageAnimSpeed（v5.1 补充：因设备性能差异和个人习惯，翻页动画类型/速度不纳入预设）
  TTS:      ttsSpeed, ttsPitch, ttsVoice, ttsAutoPage
  护眼:     eyeCareReminderInterval
  理由:     因人而异（色温偏好）、因设备而异（振动/方向/翻页动画性能）、全局行为（手势/自动翻页）
```

**预设快照生成：** 通过 Registry 自动过滤，无需手工维护白名单：

```kotlin
fun createPresetSnapshot(prefs: ReaderPreferences): PresetSnapshot {
    val fields = ReaderSettingRegistry.presetFields() // includeInPreset == true
    return PresetSnapshot(fields.associate { it.key to getValue(it, prefs) })
}
```

**功能扩展：**
- **从当前配置创建：** 一键保存当前阅读状态为命名预设
- **内置官方预设：** 极简阅读 / 护眼模式 / 精读模式 / 深夜模式（聚焦线和亮度不在预设内，描述中标注"建议配合使用"）
- **预设对比预览：** 选择两个预设后，并排显示关键参数 diff

---

## 5. 实施路线

### Phase 0a — 架构准备（2 周）

```
├─ ★ 统一设置注册表 ReaderSettingRegistry（§1.7）
│   ├─ 定义 SettingDefinition data class + StorageTier/UiGroup/PreviewStrategy 枚举
│   ├─ 将现有 50+ 设置逐条迁移到 Registry（每条含完整元数据）
│   ├─ DiffCalculator 改为从 Registry 查询 Scope
│   ├─ 预设快照改为从 Registry.presetFields() 生成
│   └─ 验证：新增一个测试设置只需改 Registry + ReaderPreferences + BookPrefs + UI 四处
│
├─ ★ 删除 BookReaderPrefsEntity 死代码（§1.7.1 v4.0 修正）
│   ├─ 删除 feature.reader.settings.BookReaderPrefsEntity（47 列，未注册到 Database，死代码）
│   ├─ 删除 ReaderSettingsResolver（依赖死代码 Entity，零生产调用方）
│   ├─ 保留 core.database.entity.BookReaderPrefsEntity（3 列 JSON blob，实际在用）
│   └─ 验证：ShuLiDatabase entities 列表中无冗余 Entity
│
├─ ReaderPreferences 四层 StateFlow 拆分（§1.6）：
│   OverlayPrefs / ChromePrefs / StylePrefs / LayoutPrefs
│   各 Composable 只订阅所需层级，精确控制 recomposition
│   （四层分组由 Registry.recompositionTier 驱动，不手工硬编码）
│   ReaderPreferences 默认值从 Registry.getDefault() 读取
│
├─ PaginationStrategy 接口提取（§2.3.2 审核建议，v4.0 修正）
│   ├─ 定义 PaginationStrategy 接口
│   │   接口签名：fun paginate(content: String, prefs: ReaderPreferences, pageSize: PageSize, density: Float): TextChapter
│   │   不绑死 ReaderLayoutConfig，各策略自行从 prefs 解析所需参数
│   ├─ 现有逻辑移入 HorizontalPaginationStrategy（纯重构，零行为变化）
│   └─ Paginator 委托给 strategy
│
├─ ReaderLayoutConfig 四边距拆分 + LayoutHasher 预留
│   ├─ marginVertical/marginHorizontal → marginTop/Bottom/Left/Right
│   │   （ReaderLayoutConfig 直接替换，ReaderPreferences 保留旧字段做 fallback）
│   ├─ LayoutHasher 一次性预留所有新字段（wordSpacing/paragraphDivider/
│   │   hyphenation/vertical/dualPage/bionicReading/四边距）
│   │   即使值为默认值也写入 hash，避免多次 bump LAYOUT_ALGORITHM_VERSION
│   └─ bump LAYOUT_ALGORITHM_VERSION = 2（一次到位）
│
├─ 定义 TextProcessor 接口 + TextProcessingPipeline
├─ 扩展 ReaderSettingsSnapshot 预留新字段位置
├─ 扩展 LayoutKey / RenderKey / OverlayKey / ShellKey 预留新字段
├─ 扩展 CanvasTouchHandler.Callbacks 为 action-based
├─ ReaderCanvasView 集成 WindowInsets 监听（§2.2.3）
│   inset 变化 → layoutConfigFor() 重算 → Tier 3 reflow
├─ 快速设置面板新增实时预览区基础设施（§4.2，v4.0 修正）：
│   复用真实 Paginator + ReaderPageRenderer 实例（注入），仅缩小 pageSize
├─ BookReaderPrefsOverrides 反序列化容错（v4.0 新增）：
│   Json { ignoreUnknownKeys = true; coerceInputValues = true }
│   细化异常捕获：单字段异常不导致整书配置丢失
├─ InvalidationScope 新增 VIEW_INVALIDATE（v4.0 新增）：
│   语义：仅 View.invalidate()，不进任何 recorder（色温、聚焦线）
├─ GestureConfig 存储路径确认（v4.0 新增）：
│   @Serializable data class，作为 BookReaderPrefsOverrides.gestureConfig 字段存储
│   不是独立的 JSON String 序列化
│
└─ 所有 Phase 1-4 的新设置加入 ReaderSettingKey / ReaderIntent / ReaderPreferenceMonitor
```

### Phase 0b — 设置面板布局重构（2 周，v5.0）

```
├─ ModalBottomSheet → BottomSheetScaffold 迁移（§4.1）
│   ├─ Peek 态（30% 屏高）+ Expanded 态（80% 屏高）
│   ├─ SheetState 管理：PartiallyExpanded / Expanded 两态切换
│   └─ DragHandle 组件 + 拖拽手势交互
├─ SettingsTab 枚举定义（§4.2）
│   ├─ TYPE_AND_FONT / APPEARANCE / BEHAVIOR 三 Tab
│   ├─ Tab → UiGroup 映射列表
│   └─ 替换现有 TAB_LAYOUT=0/1/2/3 硬编码常量
├─ UiGroup 枚举集成到 SettingDefinition（§4.3）
│   ├─ 12 个 UiGroup 值定义
│   ├─ SettingsCard 组件实现
│   └─ 现有 ExpandableSection 复用为高级卡片收起
├─ FontSizeStepper 组件（§4.4）
│   ├─ A-/A+ 步进器 + 当前值显示
│   └─ Peek 态常驻组件
├─ 预览区改进（§4.5）
│   ├─ 当前页首行截取 + fallback 硬编码
│   └─ 仅 Expanded 态可见
├─ VisualMarginControl 组件（§4.6.1）
│   ├─ 微型屏幕缩略图（drawBehind 边距可视化）
│   └─ 四边步进器（上/下/左/右独立控制）
├─ HeaderFooterWireframe 组件（§4.6.2）
│   ├─ 微型 Wireframe（页眉/页面主体/页脚三段式）
│   └─ 原地 Popup 气泡选择器
├─ StepperSlider 复合控件（§4.7）
│   ├─ Slider 两端 IconButton 步进
│   └─ 统一用于行距/字间距/色温等连续值
└─ Spring 动画 + Scrim 蒙层（§4.8）
    ├─ Tab 切换 AnimatedContent + spring()
    └─ Expanded 态背景半透明蒙层
```

### Phase 1 — 零 Reflow 增量（1 周）

所有设置 Scope = VIEW_INVALIDATE 或 无，**完全不触发 reflow**，风险最低。

```
├─ 色温调节（VIEW_INVALIDATE）
├─ 蓝光过滤（VIEW_INVALIDATE）
├─ 护眼提醒（无）
├─ 振动反馈（无）
├─ 方向锁定（无）
├─ 聚焦线（VIEW_INVALIDATE）
└─ 翻页动画速度（PAGE_DELEGATE）
```

### Phase 2 — Reflow 增量（2 周）

```
├─ 词间距（REFLOW，Paginator calculateLine 小改）
├─ 段间分隔线（REFLOW + CONTENT）
├─ 独立边距（REFLOW，向后兼容方案已就绪）
├─ 手势绑定（扩展现有 CanvasTouchHandler）
└─ 标题字体（CONTENT）
```

### Phase 3 — 中等复杂度（3 周）

```
├─ TTS 引擎集成 + 错误处理
├─ TTS 高亮同步（OVERLAY）
├─ TTS 自动翻页 + 定时停止
├─ 正则替换（TextProcessor 管道 + RegexRuleCache）
├─ 广告过滤（TextProcessor 管道）
├─ 背景纹理（SHELL，BitmapShader + 内置纹理资产）
└─ 双页模式（单 View 双区域 clipRect）
```

### Phase 4 — 高复杂度（4 周）

```
├─ Bionic Reading（分页阶段预计算 bionicSegments + CJK 排除 + BreakIterator）
├─ 断字连字（Liang 算法 + AtomicReference 懒加载 + WidthWindow 集成）
└─ 竖排阅读（VerticalPaginationStrategy + TextPage.columns + 标点旋转）
```

### 依赖关系

```
Phase 0a（架构准备，2 周）→ 所有后续 Phase 的前置条件
  ├─ 删除 BookPrefs 死代码 → Registry 的前提
  ├─ Registry → 四层 StateFlow 拆分（四层分组由 Registry.recompositionTier 驱动）
  ├─ Registry → 设置面板卡片化（UiGroup 枚举由 Registry 驱动）
  ├─ PaginationStrategy 接口 → Phase 4 竖排阅读
  ├─ LayoutHasher 一次性预留 → Phase 1-4 所有 REFLOW 类设置无需再 bump version
  ├─ WindowInsets 监听 → Phase 2 独立边距
  └─ 预览区基础设施 → Phase 0b 预览区改进
Phase 0b（设置面板 UI，2 周）→ Phase 1 起新面板布局生效
  ├─ BottomSheetScaffold → Peek/Expanded 两态
  ├─ SettingsTab 枚举 → Phase 1-4 所有新增设置的 Tab 归属
  ├─ VisualMarginControl → Phase 2 独立边距（四边步进器依赖四边距字段）
  └─ HeaderFooterWireframe → Phase 1 页眉页脚设置
Phase 2 独立边距 → Phase 3 双页模式
Phase 0a 文本管道 → Phase 3 正则替换/广告过滤 → Phase 4 Bionic Reading
Phase 2 词间距 → Phase 4 断字

总时间线：Phase 0a (2w) + Phase 0b (2w) + Phase 1 (1w) + Phase 2 (2w) + Phase 3 (3w) + Phase 4 (4w) = 14 周

**并行策略细化（v5.1 补充）：**
Phase 0b 可与 Phase 1 **逻辑层**并行，但 UI 集成需串行：
- Phase 0b 第 1 周：BottomSheetScaffold 骨架 + SettingsTab 枚举 + UiGroup 定义 → Phase 1 逻辑层可并行开发（ViewModel/Manager/Registry 侧新增设置）
- Phase 0b 第 2 周：卡片化分组 + 控件实现 → Phase 1 UI 侧在 Phase 0b 完成后集成（将 Phase 1 新增设置挂入新面板的对应 Tab/Card）
- 即：Phase 1 逻辑层与 Phase 0b 并行 → Phase 1 UI 层在 Phase 0b 之后。总工期可压缩至 13 周。
```

---

## 6. 性能保障

### 6.1 性能预算

| 操作 | 预算 | 当前基线 |
|:---|:---|:---|
| 单页渲染 | ≤ 10ms | 8ms |
| 翻页动画 | 60fps | 60fps |
| Reflow（单章） | ≤ 50ms | 30ms |
| 首屏显示 | ≤ 300ms | 280ms |
| 文本管道处理（100KB） | ≤ 20ms | — |

### 6.2 性能测试

```kotlin
@RunWith(AndroidJUnit4::class)
class NewFeatureBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()

    @Test fun renderWithColorTemperature() { /* ≤ 8.5ms */ }
    @Test fun renderWithBionicReading() { /* ≤ 12ms */ }
    @Test fun renderWithDualPage() { /* ≤ 14ms (两页) */ }
    @Test fun reflowWithWordSpacing() { /* ≤ 35ms */ }
    @Test fun reflowWithHyphenation() { /* ≤ 40ms */ }
    @Test fun textPipelineWithRegex() { /* ≤ 20ms (10 rules, 100KB) */ }
}
```

### 6.3 降级策略

| 功能 | 性能阈值 | 降级行为 |
|:---|:---|:---|
| Bionic Reading | 渲染 > 15ms | 自动关闭，Toast 提示 |
| 断字连字 | reflow > 50ms | 降级为不断字 |
| 正则替换 | 管道 > 30ms | 异步处理 + 骨架屏 |
| 双页模式 | 渲染 > 20ms | 自动切换为单页 |

---

## 7. 风险与缓解

| 风险 | 概率 | 影响 | 缓解措施 |
|:---|:---|:---|:---|
| Bionic Reading 段合并后 JNI 调用仍过多 | 中 | 高 | 预计算 segment 宽度缓存到 TextLine，渲染时跳过 measureText |
| 竖排 CJK 标点旋转性能 | 中 | 中 | 预渲染标点 bitmap 缓存，避免每帧 rotate + drawText |
| 正则替换回溯爆炸 | 低 | 高 | `Regex` 超时机制（Kotlin 2.0 `Regex` 支持 timeout） |
| 独立边距 LayoutKey 碰撞 | 低 | 中 | hash 使用解析后的实际四边距值，非原始 prefs 字段 |
| 手势绑定与翻页动画冲突 | 中 | 低 | 明确优先级：PageDelegate.onTouch > 选区 > 自定义手势 > 默认热区 |
| TTS 引擎不可用（设备无 TTS） | 中 | 中 | `initialize()` 失败时静默降级，隐藏 TTS UI 入口 |
| 断字模式表加载失败 | 低 | 低 | fallback 到 `BreakIterator` 系统 API |
| 旧预设加载时新字段缺失 | 已解决 | — | `@Serializable` + Kotlin 默认值自动填充 |

---

## 8. 迁移与回滚策略（v5.1 新增）

### 8.1 数据迁移

**DataStore（Tier 1）迁移：**
- 新增字段使用 `ReaderPreferences` 默认值，DataStore 读取时自动填充（`@Serializable` + Kotlin 默认值）
- 无需 DataStore migration——旧 protobuf/JSON 缺少新字段时自动使用默认值
- 如需数据转换（如 `gestureConfig: String` → `GestureConfig`），在 `UserPreferencesSerializer` 中添加 `transform()` 回调

**Room 数据库（Tier 2）迁移：**
- `BookReaderPrefsOverrides` 存储为 `BookReaderPrefsEntity.configJson` JSON blob
- `kotlinx.serialization` 配置 `Json { ignoreUnknownKeys = true; coerceInputValues = true }`（Phase 0 已规划）
- 旧 JSON 缺少新字段 → 反序列化时使用 `ReaderPreferences` 默认值
- 旧 JSON 包含已废弃字段 → `ignoreUnknownKeys` 静默忽略
- **无需 Room Migration**——JSON blob 内部变更不触发数据库 schema 变更

**Room 数据库 schema 变更（Phase 0 删除死代码后）：**
- 删除 `feature.reader.settings.BookReaderPrefsEntity` 不影响 `ShuLiDatabase`——该类未注册到 entities 列表
- 如果未来需要修改 `core.database.entity.BookReaderPrefsEntity`（如拆分 JSON 列），需要 bump `version = 25` 并编写 `Migration`

**预设文件迁移：**
- `ReaderPreset` 使用 `@Serializable` data class，新增字段自动使用默认值
- 旧预设文件加载时不会报错，缺失字段填充默认值

### 8.2 回滚策略

**Feature Flag 机制：**

```kotlin
object ReaderFeatureFlags {
    // 每个 Phase 对应一个 feature flag，默认开启
    val COLOR_TEMPERATURE_ENABLED = true   // Phase 1
    val WORD_SPACING_ENABLED = true        // Phase 2
    val TTS_ENABLED = true                // Phase 3
    val BIONIC_READING_ENABLED = true      // Phase 4
    // ...
}
```

- 每个新功能的 UI 入口和逻辑分支检查对应 flag
- 如某功能在生产环境出现问题，远程配置关闭 flag 即可禁用该功能
- Flag 关闭后：UI 入口隐藏、逻辑路径跳过、存储数据保留（不清除用户配置）

**分级回滚：**

| 级别 | 场景 | 操作 |
|:---|:---|:---|
| 单功能回滚 | 色温 MULTIPLY 在某些 GPU 上渲染异常 | 关闭 `COLOR_TEMPERATURE_ENABLED` flag，UI 隐藏色温控件 |
| Phase 级回滚 | Phase 1 整体不稳定 | 关闭 Phase 1 全部 flag，用户回到 v5.0 功能集 |
| 紧急回滚 | Registry 引入导致设置读取崩溃 | 通过 `ReaderSettingRegistry` 的 fallback 路径——Registry 初始化失败时使用硬编码默认值 |

**回滚后数据保留：**
- 用户已配置的新功能设置值保留在 DataStore / JSON 中
- 功能重新启用时自动恢复用户配置
- 不会因回滚丢失用户偏好数据

---

## 9. 测试策略（v5.1 新增）

### 9.1 单元测试

**Registry 驱动设置测试：**

```kotlin
class ReaderSettingRegistryTest {
    @Test fun `all settings have unique keys`() {
        val keys = ReaderSettingRegistry.all.map { it.key }
        assertThat(keys).containsNoDuplicates()
    }

    @Test fun `all settings have valid default values`() {
        ReaderSettingRegistry.all.forEach { def ->
            assertThat(def.defaultValue).isNotNull()
        }
    }

    @Test fun `preset fields exclude overlay and behavior settings`() {
        val presetKeys = ReaderSettingRegistry.presetFields().map { it.key }
        assertThat(presetKeys).doesNotContain("color_temperature", "haptic_feedback", "gesture_config")
    }

    @Test fun `adding new setting only requires Registry + Prefs + BookPrefs + UI`() {
        // 验证改进后的 4 处修改约束
        // 通过架构测试确保 UserPreferences/Snapshot/Keys/Intent/Manager 从 Registry 驱动
    }

    @Test fun `recompositionTier covers all settings`() {
        val tiered = ReaderSettingRegistry.all.filter { it.recompositionTier >= 0 }
        val untiered = ReaderSettingRegistry.all.filter { it.recompositionTier == -1 }
        assertThat(tiered.size + untiered.size).isEqualTo(ReaderSettingRegistry.all.size)
    }
}
```

**DiffCalculator Registry 驱动测试：**

```kotlin
class ReaderRenderDiffCalculatorRegistryTest {
    @Test fun `REFLOW scope setting triggers reflow invalidation`() {
        val old = createSnapshot(fontSize = 16f)
        val new = createSnapshot(fontSize = 20f)
        val scopes = calculateDiff(old, new)
        assertThat(scopes).contains(InvalidationScope.REFLOW)
    }

    @Test fun `VIEW_INVALIDATE scope setting does not trigger recorder invalidation`() {
        val old = createSnapshot(colorTemperature = 6500f)
        val new = createSnapshot(colorTemperature = 4000f)
        val scopes = calculateDiff(old, new)
        assertThat(scopes).isEmpty()  // VIEW_INVALIDATE 不进 recorder
    }
}
```

**文本管道测试：**

```kotlin
class TextProcessingPipelineTest {
    @Test fun `processor order uniqueness enforced`() {
        val pipeline = TextProcessingPipeline(listOf(
            object : TextProcessor { override val order = 100; override fun process(...) = "" },
            object : TextProcessor { override val order = 100; override fun process(...) = "" },
        ))
        // 应抛出 IllegalArgumentException
    }

    @Test fun `regex processor skips invalid patterns`() {
        val rules = listOf(RegexRule("[invalid", "", true))
        val cache = RegexRuleCache(rules)
        assertThat(cache.apply("test")).isEqualTo("test")
    }
}
```

### 9.2 Compose UI 测试

```kotlin
class QuickSettingsPanelTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test fun `Peek state shows font stepper and theme row`() {
        composeTestRule.setContent { ReaderQuickSettingsScaffold(/* PartiallyExpanded */) }
        composeTestRule.onNodeWithTag("FontSizeStepper").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ThemeColorRow").assertIsDisplayed()
        composeTestRule.onNodeWithTag("SettingsTabRow").assertDoesNotExist()
    }

    @Test fun `Expanded state shows tabs and cards`() {
        composeTestRule.setContent { ReaderQuickSettingsScaffold(/* Expanded */) }
        composeTestRule.onNodeWithTag("SettingsTabRow").assertIsDisplayed()
        composeTestRule.onNodeWithTag("SettingsCard_FONT_BASICS").assertIsDisplayed()
    }

    @Test fun `tab switching shows correct card groups`() {
        composeTestRule.setContent { ReaderQuickSettingsScaffold(/* Expanded */) }
        composeTestRule.onNodeWithText("外观与显示").performClick()
        composeTestRule.onNodeWithTag("SettingsCard_THEME").assertIsDisplayed()
        composeTestRule.onNodeWithTag("SettingsCard_FONT_BASICS").assertDoesNotExist()
    }
}
```

### 9.3 视觉回归测试

```kotlin
class ReaderCanvasViewScreenshotTest {
    @get:Rule val screenshotRule = ScreenshotTestRule()

    @Test fun `color temperature MULTIPLY overlay - warm`() {
        // 在 5 种主题（白天/夜间/羊皮纸/绿色/深色）下截图对比
        screenshotRule.compare("color_temp_4000K_day", "color_temp_4000K_night", ...)
    }

    @Test fun `bionic reading segments rendering`() {
        // 英文/中文/混合文本的 Bionic 渲染截图
        screenshotRule.compare("bionic_english", "bionic_chinese", "bionic_mixed")
    }
}
```

### 9.4 集成测试

```kotlin
class SettingsRoundTripTest {
    @Test fun `setting persists through DataStore round trip`() {
        // 设置色温 → 重新打开阅读器 → 验证色温值恢复
    }

    @Test fun `per-book override takes precedence over global`() {
        // 全局 fontSize=16 → 本书覆盖 fontSize=20 → 验证读取为 20
        // 清除本书覆盖 → 验证读取回退到 16
    }

    @Test fun `old preset loads with new fields defaulted`() {
        // 加载 v4.0 格式的预设 JSON（缺少 colorTemperature 等新字段）
        // 验证新字段使用默认值，不报错
    }
}
```
