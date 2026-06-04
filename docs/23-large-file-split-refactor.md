# 大文件 SRP 拆分重构方案

> 编写时间：2026-06-04
> 范围：项目中所有超过 500 行的 Kotlin 源文件（含部分 400+ 行但职责明显混杂的文件）
> **核心原则**：单一职责原则（SRP）—— "A class should have only one reason to change"
> 辅助原则：高内聚低耦合 · 开闭原则（OCP） · 依赖倒置（DIP） · 接口隔离（ISP） · 渐进式重构

## 0. 项目上下文

> **阶段**：快速开发期（pre-release）
> **发布状态**：尚未发布，无外部用户
> **破坏性改动**：✅ **允许**
> **重构**：✅ **鼓励**

### 0.1 上下文对方案的影响

本方案基于"**可以破坏性改动**"的前提制定，因此**有意放弃以下常见但增加复杂度的工程妥协**：

| 传统做法 | 本方案的选择 | 理由 |
|---|---|---|
| 保留旧公共 API 作为 delegation 兼容层 | ❌ **直接替换**，call sites 同步修改 | 无外部调用方，无需兼容 |
| 使用 `@Deprecated` + `ReplaceWith` 渐进迁移 | ❌ **一次性替换** | 无历史包袱，一次到位更清晰 |
| 使用 facade 模式保留旧类 | ❌ **完全删除旧类**，调用方改用新类 | facade 是历史包袱的产物，本阶段不需要 |
| 长期保留"兼容分支"代码 | ❌ **删除死代码** | 没有向后兼容需求 |
| 灰度发布 / A/B 测试 | ❌ **直接全量** | 没有用户，无需灰度 |
| 多版本 API 并存 | ❌ **只保留最新版本** | 无历史版本 |

### 0.2 允许破坏性改动带来的好处

- **代码更干净**：没有 delegation 层、没有 deprecated 方法、没有 facade 中间层
- **认知负荷更低**：每个职责只存在于一个地方，没有"应该用新接口还是旧接口"的纠结
- **重构更彻底**：可以直接删除旧类，而不是"保留但标记为 deprecated"
- **演进更快**：API 变更无需考虑兼容性，设计可以更激进

### 0.3 仍需遵守的工程纪律

尽管允许破坏性改动，仍需保持以下纪律（这些是**代码质量**问题，不是兼容性问题）：

- ✅ **每个 commit 必须能编译通过**（`./gradlew :app:compileDebugKotlin`）
- ✅ **每个 commit 必须保持单测通过**（`./gradlew :app:testDebugUnitTest`）
- ✅ **每个 commit 必须是可独立 review 的逻辑单元**（不要一次性推 5000 行）
- ✅ **每个 commit 必须有清晰的 message**（描述 WHAT + WHY，不只是"重构"）
- ✅ **保持 git 历史可追溯**（用 commit 而不是覆盖来演进代码）
- ✅ **重构前后跑全量单测**，确保行为不变

### 0.4 回滚策略的调整

由于没有发布，回滚策略可以更激进：

| 场景 | 传统策略 | 本方案策略 |
|---|---|---|
| 拆分导致单测失败 | 保留旧类 + 新类并存 | 直接 `git revert` 整个 commit |
| 拆分方案走不通 | 保留 facade，逐步迁移 | 删分支，重新设计 |
| 团队意见不一 | 保留兼容层作为妥协 | 直接讨论，选一个方向推进 |
| 需要对比新旧实现 | 长期保留两套 | 用 git branch 隔离，合并后删除旧分支 |

---

## 1. 背景与目标

`ReaderViewModel.kt` 在 v4 迭代中累积至 2541 行，`AppStrings.kt` 达 1969 行，`QuickSettingsSheet.kt` / `SettingsScreen.kt` 均破 1100 行。过长的文件带来：

- **认知负荷高**：新功能需要在大段代码中定位，PR review 难以覆盖
- **测试覆盖低**：难以 mock 局部依赖，单测粒度粗
- **并发冲突多**：多人同时修改同文件频繁冲突
- **编译缓存失效**：任一变更触发整个文件重新编译

本方案识别所有过长文件，按 SRP 拆分，预期把单文件行数控制在 **300 行以内**（特殊文件可放宽到 500 行）。

## 1.5 SRP 方法论：本方案的判定框架

> "A class should have only one reason to change." — Robert C. Martin

**单一职责原则（SRP）** 的常见误读是"一个类只做一件事"，但 Martin 本人强调其本质是 **"一个模块只服务于一个变更原因（reason to change）"**，而变更原因又绑定在 **Actor（利益相关方）** 上——当不同 Actor 的需求变化会迫使同一份代码同时修改时，这份代码就违反了 SRP。

### 1.5.1 识别 SRP 违反的三个判据

本方案对每个文件使用以下三个判据，**任一成立即视为违反 SRP**：

| # | 判据 | 提问方式 | 违反信号 |
|---|---|---|---|
| **A. Actor 判据** | 这份代码服务于几个不同的利益相关方？ | 产品、设计师、运营、DBA 是否会分别提出修改需求？ | ≥ 2 个 Actor 的需求会让同一文件变更 |
| **B. 变更轴判据** | 这份代码有几个独立的"变更原因"？ | 能否列举出 ≥ 2 个互不相关的修改理由？ | 修改一个功能时，要同时理解/回归另一个不相关功能 |
| **C. 依赖方向判据** | 这份代码是否被多个方向拉扯？ | 它是否同时依赖 UI 框架、持久层、网络层、第三方 SDK？ | 任一层升级都可能牵连该文件 |

### 1.5.2 SRP 拆分的三条规则

> ⚠️ 本节规则基于 § 0 项目上下文（快速开发期，允许破坏性改动）制定。

1. **一个模块 = 一个变更轴**
   拆分后，每个新文件只响应一类需求变化。例如：TTS 引擎切换只动 `TtsPlaybackManager.kt`，书签业务调整只动 `BookmarkNotesManager.kt`。

2. **变更轴之间只能单向依赖**
   被拆分出的模块之间不能循环引用。本方案统一采用 **"ViewModel/Screen 持有 Manager，Manager 持有 StateFlow"** 的单向结构，禁止反向依赖。

3. **Call sites 一次性同步替换（不做兼容层）**
   拆分时**同时修改所有调用方**，直接删除旧类/旧方法。项目处于 pre-release 阶段，没有外部调用方，不需要 `@Deprecated` / delegation / facade 等兼容机制。
   - ✅ **正确做法**：拆出新类 → 改 call sites → 删旧类 → 单 commit 完成
   - ❌ **避免做法**：保留旧类 delegation 到新类、加 `@Deprecated` 标记、保留 facade

### 1.5.3 SRP 粒度的"度"

SRP 不是越细越好。过度拆分会导致：
- **跳转成本增加**：一个简单功能要跨 5 个文件
- **间接层爆炸**：facade、delegate、adapter 层数过多
- **事务一致性难保证**：跨模块状态变更需复杂编排

**本方案的粒度目标**：
- 单文件 **150–300 行**（可独立理解、独立测试、独立 review）
- 单类 **1 个核心变更轴 + 最多 1 个辅助职责**（如"分页 + 缓存"天然耦合）
- 跨文件协作 **通过接口/callback**，不通过具体类

### 1.5.4 SRP 与相关原则的协同

> ⚠️ 由于项目处于 pre-release 阶段（§ 0），OCP（开闭原则）的"对扩展开放、对修改关闭"在本方案中**不作为主要约束**——允许直接修改调用方，无需保留旧接口。

| 协同原则 | 在本方案中的体现 |
|---|---|
| **LSP（里氏替换）** | 子模块通过接口暴露，可在测试中用 mock 替换 |
| **ISP（接口隔离）** | 子接口只暴露调用方所需方法（如 `ReaderStrings` 只含阅读器词条） |
| **DIP（依赖倒置）** | ViewModel 依赖子模块抽象（`MutableStateFlow<UiState>` + 回调），不依赖具体实现 |
| **SRP × 单一数据源（SSOT）** | 每个职责只有一份代码，没有 delegation/facade 的"伪副本" |

---

## 2. 识别结果（按行数降序）

| 文件 | 行数 | 严重度 | 状态 |
|---|--:|:---:|:---:|
| `feature/reader/ReaderViewModel.kt` | 2541 | 🔴 极高 | 阶段一部分完成 |
| `core/i18n/AppStrings.kt` | 1969 | 🔴 极高 | 待拆 |
| `feature/reader/component/QuickSettingsSheet.kt` | 1118 | 🔴 高 | 待拆 |
| `feature/settings/SettingsScreen.kt` | 1104 | 🔴 高 | 待拆 |
| `core/reader/ReaderCanvasView.kt` | 929 | 🟠 中高 | 待拆 |
| `feature/reader/ReaderScreen.kt` | 873 | 🟠 中高 | 待拆 |
| `core/repository/BookRepository.kt` | 662 | 🟠 中 | 待拆 |
| `core/parser/EpubParser.kt` | 599 | 🟡 中 | 待拆 |
| `feature/bookshelf/BookshelfViewModel.kt` | 536 | 🟡 中 | 待拆 |
| `feature/bookshelf/BookshelfScreen.kt` | 523 | 🟡 中 | 待拆 |
| `feature/reader/component/DirectoryDialog.kt` | 522 | 🟡 中 | 待拆 |

> 不含测试文件（测试文件允许偏长）。

### 2.5 SRP 违反全景

下表从 SRP 三判据视角重新审视 11 个过长文件，按"Actor 数量 × 变更轴数量"降序排列（乘积越大，SRP 违反越严重，拆分优先级越高）。严重度划分：乘积 ≥ 36 🔴 严重、12–35 🟠 高、< 12 🟡 中。

| 文件 | Actor 数 | 变更轴数 | 乘积 | 判据命中 | SRP 严重度 |
|---|--:|--:|--:|:---:|:---:|
| `ReaderScreen.kt` | 7 | 16 | **112** | A+B+C | 🔴 严重 |
| `SettingsScreen.kt` | 7 | 8 | **56** | A+B+C | 🔴 严重 |
| `BookRepository.kt` | 7 | 7 | **49** | A+B+C | 🔴 严重 |
| `ReaderViewModel.kt` | 6 | 6 | **36** | A+B+C | 🔴 严重 |
| `ReaderCanvasView.kt` | 5 | 5 | **25** | A+B+C | 🟠 高 |
| `BookshelfViewModel.kt` | 4 | 4 | **16** | A+B | 🟠 高 |
| `EpubParser.kt` | 4 | 4 | **16** | A+B | 🟠 高 |
| `BookshelfScreen.kt` | 4 | 4 | **16** | A+B | 🟠 高 |
| `QuickSettingsSheet.kt` | 4 | 3 | **12** | A+B | 🟠 高 |
| `DirectoryDialog.kt` | 4 | 3 | **12** | A+B | 🟠 高 |
| `AppStrings.kt` | 5 | 2 | **10** | A+B+C | 🟡 中 |

**洞察**：
- `ReaderScreen.kt` 的 SRP 违反最严重（112 分），虽然行数仅排第 6，但变更轴多达 16 个（每个 `LaunchedEffect` 对应一个 Actor），**应优先拆分**
- `AppStrings.kt` 虽然行数排第 2，但变更轴只有 2 个（"词条新增"与"翻译修改"），SRP 乘积仅 10，**拆分紧迫性最低**——由于项目允许破坏性改动（§ 0），可一次性同步替换所有 call sites
- `ReaderViewModel.kt` 已通过阶段一拆分降低 SRP 违反程度（从 ~9 个 Actor 降至 ~6 个），本方案继续完成剩余 6 个模块
- `BookRepository.kt` 的 7 个 Actor 全部命中判据 A，是"领域边界不清"的典型症状，应尽快按 DDD 限界上下文切分

---

## 3. 详细拆分方案

### 3.1 ReaderViewModel.kt（2541 行，🔴 极高）

#### SRP 违反分析

| Actor | 典型需求（变更原因） | 波及当前文件的区域 |
|---|---|---|
| **阅读体验产品** | 调整翻页逻辑、进度算法、scrub 体验 | `nextPage` / `prevPage` / `computeDisplayProgress` / scrub 接口 |
| **排版工程师** | 改分页算法、reflow 策略、预加载窗口 | `paginateChapterStreaming` / `reflowCurrentChapter` / `preloadAdjacentChapters` |
| **TTS 引擎团队** | 切换朗读引擎、调整会话管理、新增睡眠定时 | `ttsController` / `startTts` / `startSleepTimer` |
| **笔记/书签产品** | 新增高亮颜色、笔记导出格式、书签分类 | `bookmarkDao` / `noteDao` / `addNote` / `exportNotesAsMarkdown` |
| **数据工程师** | 改进度持久化格式、接入云同步、调整去重策略 | `saveReadingProgress` / `persistReadingTime` / `readingProgressDao` |
| **UI 设计师** | 调整预设视觉风格、工具栏动画 | `loadPresets` / `toggleToolbar` / `ReaderUiState` |

**违反判据**：A（≥ 6 个 Actor）+ B（≥ 6 个变更轴）+ C（依赖 Compose、Room、TTS SDK、IO 层）同时成立。**SRP 严重违反**。

#### 当前职责（已部分拆分）

`feature/reader/` 目录已抽出：`search/TextSearchManager.kt`、`progress/ReadingProgressTracker.kt`、`progress/LayoutConfigBuilder.kt`、`progress/NormalizedChapters.kt`、`prefs/ReaderPreferencesBridge.kt`。详见 `docs/reader-architecture-notes.md` § 13。

**剩余未拆职责**

| 职责 | 行数（估） | 核心方法/字段 |
|---|--:|---|
| 章节分页与 reflow | ~450 | `paginateChapterStreaming`、`paginateChapter`、`preloadAdjacentChapters`、`reflowCurrentChapter`、`computeChapterWordCounts` |
| TTS 朗读 | ~150 | `ttsController`、`startTts`、`pauseTts`、`speakCurrentTtsSentence`、`startSleepTimer` |
| 书签与笔记 | ~180 | `bookmarkDao`/`noteDao` 相关：`addBookmark`、`addNote`、`loadBookmarks`、`loadNotes`、`exportNotesAsMarkdown` |
| 书籍加载与进度持久化 | ~200 | `openBook`、`openChapter`、`saveReadingProgress`、`persistReadingTime`、`jumpToChapterPosition` |
| 预设管理 | ~110 | `loadPresets`、`saveCurrentAsPreset`、`applyPreset`、`renamePreset`、`resetToDefault` |
| 状态与导航 | ~400 | `ReaderUiState`、`nextPage`、`prevPage`、`jumpToPage`、scrub 接口、`toggleToolbar`、`toggleDirectory` |

**拆分方案**

| 新文件 | 行数（估） | 核心职责 | 依赖 |
|---|--:|---|---|
| `pagination/ChapterPaginationCoordinator.kt` | ~450 | 流式分页、reflow、预加载、字数统计 | `Paginator`、`CacheManager`、`ChapterProvider`、`MutableStateFlow<ReaderUiState>` |
| `tts/TtsPlaybackManager.kt` | ~150 | TTS 引擎交互、朗读会话、睡眠定时 | `TtsEngine`、`TtsController`、`MutableStateFlow` |
| `notes/BookmarkNotesManager.kt` | ~180 | 书签/笔记 CRUD、Markdown 导出 | `BookmarkDao`、`NoteDao`、`MutableStateFlow` |
| `book/BookSessionManager.kt` | ~200 | 开书、章节切换、阅读进度持久化 | `BookRepository`、`ReadingProgressDao`、`MutableStateFlow` |
| `presets/ReaderPresetManager.kt` | ~110 | 预设增删改查、应用、重置 | `ReaderPresetDao`、`ReaderPreferencesBridge` |
| `navigation/ReaderNavigationCoordinator.kt` | ~200 | 翻页、scrub、目录/工具栏切换 | `MutableStateFlow`、`Channel<Int>` |
| `ReaderViewModel.kt`（保留） | ~300 | `ReaderUiState` 定义、`init` 装配子模块、`onCleared` 资源释放 | 聚合上述 6 个模块 |

**拆分理由**
- 每个子模块可独立单元测试（mock `MutableStateFlow` 即可）
- 消除 TTS/书签/分页之间的隐式耦合
- 后续若需换 TTS 引擎或引入"朗读书签"等跨模块功能，边界清晰

---

### 3.2 AppStrings.kt（1969 行，🔴 极高）

#### SRP 违反分析

| Actor | 典型需求（变更原因） | 波及当前文件的区域 |
|---|---|---|
| **本地化运营** | 新增/修改词条翻译 | 3 个实现对象（ZhHans/ZhTw/En）同时变更 |
| **阅读器产品** | 阅读器新增文案（槽位、TTS、进度） | § 一/五/六 区块 |
| **同步产品** | 同步/加密/设备管理新增文案 | § 四（A–J 子组）区块 |
| **书库产品** | 导入/书架/统计新增文案 | § 二/三 区块 |
| **合规/法务** | 调整"关于/版权/开源协议"文本 | § 七 区块 |

**违反判据**：A（≥ 5 个 Actor）+ B（词条新增与翻译修改是两个独立变更轴）+ C（所有业务模块都依赖该文件）同时成立。**SRP 严重违反**。

#### 当前职责

`sealed interface AppStrings` 直接声明 ~700 词条属性；3 个实现对象（`ZhHans` / `ZhTw` / `En`）各 ~500 行翻译。按业务分组标记：

| 分组 | 行号区间 | 词条数（估） |
|---|---|--:|
| 基础导航与通用 | 7–50 | ~44 |
| 一、阅读器显示偏好 | 51–90 | ~40 |
| 二、书库与导入 | 91–100 | ~10 |
| 三、阅读统计 | 101–114 | ~14 |
| 四、同步（A–J 子组） | 115–300 | ~185 |
| 五、朗读设置 (TTS) | 300–313 | ~14 |
| 六、快捷设置面板 | 314–400 | ~87 |
| 七、高级设置 / 关于 | 401–516 | ~115 |
| EPUB 图片占位 | 517+ | ~30 |

**拆分方案**

按业务域拆为 7 个子接口，每个子接口独立一个文件，包含 3 个实现：

| 新文件 | 子接口 | 词条数（估） | 主要使用者 |
|---|---|--:|---|
| `i18n/CommonStrings.kt` | `CommonStrings` | ~50 | 全局（appName、loading、confirm、error…） |
| `i18n/BookshelfStrings.kt` | `BookshelfStrings` | ~100 | `BookshelfScreen`、`ImportDialogs`、`BookshelfDialogs` |
| `i18n/ReaderStrings.kt` | `ReaderStrings` | ~250 | `ReaderScreen`、`QuickSettingsSheet`、`DirectoryDialog` |
| `i18n/SettingsStrings.kt` | `SettingsStrings` | ~150 | `SettingsScreen`、各类设置子页面 |
| `i18n/SyncStrings.kt` | `SyncStrings` | ~200 | `CloudSyncSettingsScreen`、`SyncLogScreen`、`DeviceManagementScreen` |
| `i18n/TtsStrings.kt` | `TtsStrings` | ~50 | TTS 相关 UI |
| `i18n/EncryptionStrings.kt` | `EncryptionStrings` | ~50 | `EncryptionManagementScreen` |

`AppStrings.kt` 保留为聚合接口：

```kotlin
sealed interface AppStrings {
    val common: CommonStrings
    val bookshelf: BookshelfStrings
    val reader: ReaderStrings
    val settings: SettingsStrings
    val sync: SyncStrings
    val tts: TtsStrings
    val encryption: EncryptionStrings

    // 不再保留旧词条名——项目处于 pre-release（§ 0），
    // 所有 call sites 一次性同步更新为 strings.reader.xxx 等嵌套访问
}
```

#### Call sites 同步更新策略

> 基于 § 0 项目上下文，本方案**不做兼容层**，直接全量替换 call sites。

1. **统计 call sites**：约 568 处，分布在 ~40 个文件
2. **分组批量替换**（按子接口）：
   - 阅读器相关：`strings.X` → `strings.reader.X`（~150 处）
   - 书架相关：`strings.X` → `strings.bookshelf.X`（~100 处）
   - 同步相关：`strings.X` → `strings.sync.X`（~200 处）
   - 设置/TTS/加密：各 ~50 处
   - 通用：`strings.X` → `strings.common.X`（~50 处）
3. **每个子接口一个 commit**：便于 review 和回滚
4. **编译验证**：每次替换后立即 `./gradlew :app:compileDebugKotlin` 确认无遗漏

#### 拆分理由
- 每个子文件 < 300 行，新增词条只需修改一个文件
- 子接口可独立 mock（`ReaderScreen` 测试只 mock `ReaderStrings`）
- 一次性替换所有 call sites，代码更干净，没有"旧名 vs 新名"的认知纠结
- 未来若引入新语言（日语、韩语），只需新增 7 个子实现对象

---

### 3.3 QuickSettingsSheet.kt（1118 行，🔴 高）

#### SRP 违反分析

| Actor | 典型需求（变更原因） | 波及当前文件的区域 |
|---|---|---|
| **排版产品** | 调整字号/行距/缩进控件交互 | Tab 1 `LayoutPanel` |
| **视觉设计** | 调整字体/字重/对齐/标题样式 | Tab 2 `StylePanel` |
| **阅读器行为产品** | 调整页眉页脚槽位、翻页动画、亮度 | Tab 3 `SettingsPanel` |
| **UI 设计师** | 调整 Sheet 容器样式、Tab 切换动效 | `QuickSettingsSheet` 主体 + `ExpandableSection` |

**违反判据**：A（4 个 Actor）+ B（3 个 Tab 对应 3 个独立变更轴）同时成立。**SRP 明显违反**。

#### 当前职责

主 `@Composable fun QuickSettingsSheet()`（162 行）+ 常驻组件 `ThemeColorRow`/`ExpandableSection`（~120 行）+ 3 个 Tab 面板：

| Tab | 行号区间 | 行数 | 内容 |
|---|---|--:|---|
| Tab 1: 排版 `LayoutPanel` | 433–511 | ~80 | 字号、行距、段距、缩进、边距 |
| Tab 2: 样式 `StylePanel` | 512–662 | ~150 | 字体、字重、对齐、简繁、盘古、标题样式 |
| Tab 3: 设置 `SettingsPanel` | 663–1080 | ~420 | 页眉页脚槽位、翻页动画、亮度、行为开关 |
| 辅助 `slotOptions()` 等 | 660–680 | ~20 | 槽位选项列表 |

**拆分方案**

| 新文件 | 行数（估） | 核心职责 |
|---|--:|---|
| `component/quicksettings/LayoutPanel.kt` | ~100 | 排版 Tab：字号、行距、段距、缩进、边距 |
| `component/quicksettings/StylePanel.kt` | ~170 | 样式 Tab：字体/字重/对齐/简繁/盘古/标题样式 |
| `component/quicksettings/SettingsPanel.kt` | ~430 | 设置 Tab：页眉页脚/翻页动画/亮度/行为 |
| `component/quicksettings/SharedComponents.kt` | ~150 | `ThemeColorRow`、`ExpandableSection`、`slotOptions()` |
| `component/quicksettings/QuickSettingsSheet.kt`（保留） | ~170 | Sheet 容器、Tab 切换、状态聚合 |

**拆分理由**
- 每个 Panel 独立可 `@Preview`，Android Studio 秒级渲染
- 新增面板（如"朗读 Tab"）不碰主文件
- Tab 间状态通过 `ReaderViewModel` 传递，无横向耦合

---

### 3.4 SettingsScreen.kt（1104 行，🔴 高）

#### SRP 违反分析

| Actor | 典型需求（变更原因） | 波及当前文件的区域 |
|---|---|---|
| **主题设计** | 调整外观/主题预览 | 外观区块（194–259） |
| **阅读器产品** | 调整默认阅读偏好 | 阅读偏好区块（260–393） |
| **书库产品** | 调整导入/去重策略 | 书库与导入区块（394–445） |
| **数据运营** | 调整统计指标、每日目标 | 阅读统计区块（446–478） |
| **同步产品** | 调整 WebDAV/云同步配置 | 同步区块（479–509） |
| **TTS 团队** | 调整朗读设置 | TTS 区块（510–561） |
| **法务** | 更新开源协议、捐赠信息 | 关于区块（587–1080，~493 行）|

**违反判据**：A（≥ 7 个 Actor）+ B（8 个独立变更轴）+ C（主 composable 独占 952 行，所有设置项挤在一起）同时成立。**SRP 严重违反**。

#### 当前职责

主 `@Composable fun SettingsScreen()` 独占 **952 行**（133–1084），内部包含 8 个 `SettingsSectionHeader` 区块：

| 区块 | 行号 | 行数 | 内容 |
|---|--:|--:|---|
| 外观（Appearance） | 194–259 | ~65 | 主题、字体、语言 |
| 阅读偏好 | 260–393 | ~133 | 默认字号、翻页动画、排版 |
| 书库与导入 | 394–445 | ~51 | 去重检查、导入复制、缓存清理 |
| 阅读统计 | 446–478 | ~32 | 启用统计、每日目标、重置 |
| 同步设置 | 479–509 | ~30 | 同步方式、WebDAV、云同步 |
| TTS 朗读 | 510–561 | ~51 | 朗读速度、音高、引擎 |
| 高级设置 | 562–586 | ~24 | 开发者选项、日志 |
| 关于 | 587–1080 | ~493 | 版本、开源协议、捐赠 |

底部有 5 个公共 `@Composable` item 组件（1085–1145）。

**拆分方案**

| 新文件 | 行数（估） | 核心职责 |
|---|--:|---|
| `feature/settings/sections/AppearanceSection.kt` | ~80 | 外观区块 |
| `feature/settings/sections/ReaderPrefsSection.kt` | ~150 | 阅读偏好区块 |
| `feature/settings/sections/LibrarySection.kt` | ~70 | 书库与导入区块 |
| `feature/settings/sections/StatsSection.kt` | ~50 | 阅读统计区块 |
| `feature/settings/sections/SyncSection.kt` | ~50 | 同步区块 |
| `feature/settings/sections/TtsSection.kt` | ~70 | TTS 区块 |
| `feature/settings/sections/AdvancedSection.kt` | ~40 | 高级设置区块 |
| `feature/settings/sections/AboutSection.kt` | ~500 | 关于区块（含版权、协议全文） |
| `feature/settings/components/SettingsItems.kt` | ~80 | `SettingsSectionHeader`/`ClickItem`/`SwitchItem`/`ButtonItem`/`ThemePreviewDots` |
| `feature/settings/SettingsScreen.kt`（保留） | ~100 | 顶层 Scaffold + 区块装配 |

**拆分理由**
- 主 composable 从 952 行降至 100 行，可读性大幅提升
- "关于"区块的长篇协议文本独立成文件，便于更新
- 公共 item 组件抽出后可被其他设置页复用（如 `CloudSyncSettingsScreen`）

---

### 3.5 ReaderCanvasView.kt（929 行，🟠 中高）

#### SRP 违反分析

| Actor | 典型需求（变更原因） | 波及当前文件的区域 |
|---|---|---|
| **渲染工程师** | 调整 canvas 录制策略、bitmap 回收、crossfade 时长 | `recordPage` / `startLayoutCrossfade` / bitmap 字段 |
| **视觉设计** | 调整字号/字距/颜色/页眉/进度条 | 50+ 个视觉参数 setter（264–730 行） |
| **手势交互设计** | 调整 tap/swipe/long-press 行为 | `onTouchEvent`（821–913 行） |
| **选区产品** | 调整文本选择交互、高亮样式 | `selectLineAt` / `lineBounds` |
| **Android 平台** | 适配 `View` 生命周期变化 | `onAttachedToWindow` / `onSizeChanged` / `onDraw` |

**违反判据**：A（≥ 5 个 Actor）+ B（渲染/视觉/手势/选区/生命周期 5 个变更轴）+ C（依赖 Android View + Canvas + 手势检测 + 动画）同时成立。**SRP 明显违反**。

#### 当前职责

`class ReaderCanvasView : View` 混合了 6 类职责：

| 职责 | 行号 | 行数 |
|---|---|--:|
| 字段声明（page/bitmap/animator） | 40–125 | ~85 |
| CanvasRecorder 管理（页面缓存） | 160–265 | ~105 |
| 视觉参数 setter（字号/字距/字重/颜色/页眉…） | 264–730 | ~466 |
| 页面填充与 crossfade | 731–820 | ~90 |
| 触摸手势（tap/long-press/swipe/drag） | 821–913 | ~92 |
| Android View 生命周期 + 绘制 | 914–1014 | ~100 |

**拆分方案**

| 新文件 | 行数（估） | 核心职责 |
|---|--:|---|
| `core/reader/canvas/PageBitmapCache.kt` | ~120 | `CanvasRecorder` 封装：录制、提交、off-main 录制、bitmap 回收 |
| `core/reader/canvas/CanvasVisualParams.kt` | ~150 | 视觉参数聚合类（字号/字距/字重/颜色/页眉文字/进度条/alpha） |
| `core/reader/canvas/CanvasTouchHandler.kt` | ~120 | `onTouchEvent` 分派、tap/long-press/swipe 识别、回调接口 |
| `core/reader/canvas/CanvasTextSelection.kt` | ~60 | `selectLineAt`、`lineBounds`、选区绘制 |
| `core/reader/ReaderCanvasView.kt`（保留） | ~400 | 字段声明、setter 转发、生命周期、`onDraw` 编排 |

**拆分理由**
- 触摸逻辑与绘制逻辑解耦，便于独立测试手势识别
- `PageBitmapCache` 可独立演进（例如引入 3 级缓存）
- 视觉参数聚合后，setter 从 50+ 个降至 ~10 个委托方法

---

### 3.6 ReaderScreen.kt（873 行，🟠 中高）

#### SRP 违反分析

| Actor | 典型需求（变更原因） | 波及当前文件的区域 |
|---|---|---|
| **排版工程师** | 字号/字距/字体变更需重排版 | prefs 相关 LaunchedEffect（305–351 行） |
| **主题设计** | 切换主题/颜色 | themeColors LaunchedEffect（299–304） |
| **TTS 团队** | 朗读高亮、句子切换 | ttsActiveRange LaunchedEffect（358–363） |
| **笔记产品** | 笔记高亮、哈希变更 | noteHashes LaunchedEffect（372–376） |
| **手势设计** | 边缘点击/翻页/选择 | AndroidView gesture callbacks（~80 行闭包） |
| **工具栏产品** | 顶栏/底栏/浮层面板 | TopBar/BottomBar/Directory/QuickSettings 嵌套 |
| **平台适配** | 亮度/屏幕常亮/生命周期 | brightness/keepScreenOn/lifecycle Effects |

**违反判据**：A（≥ 7 个 Actor）+ B（16 个 LaunchedEffect = 16 个变更轴）+ C（同时依赖 Compose、AndroidView、Lifecycle、Brightness API）同时成立。**SRP 严重违反**。

#### 当前职责

主 `@Composable fun ReaderScreen()`（617 行）+ 3 个私有子组件（~130 行）。主体内有 **16 个 `LaunchedEffect`**（themeColors、字号、headerFooter、edgeTurnPage、battery、TTS、选区、笔记…），以及 `AndroidView` 的 gesture 回调闭包（~80 行）。

**拆分方案**

| 新文件 | 行数（估） | 核心职责 |
|---|--:|---|
| `feature/reader/effects/ReaderPrefsEffects.kt` | ~80 | 字号/字距/字重/字体/对齐/headerFooter/titleStyle/edgeTurnPage/headerFontSizeRatio 等 prefs 相关 LaunchedEffect |
| `feature/reader/effects/ReaderRuntimeEffects.kt` | ~60 | themeColors、battery、ttsActiveRange、selectedRange、noteHashes 相关 LaunchedEffect |
| `feature/reader/effects/ReaderLifecycleEffects.kt` | ~50 | density、bookId、brightness、keepScreenOn 相关 LaunchedEffect + DisposableEffect |
| `feature/reader/gestures/ReaderCanvasGestures.kt` | ~90 | `onPageChanged`、`onCenterClicked`、`onTextSelected`、`onEdgeClicked` 等回调闭包提取为命名函数 |
| `feature/reader/overlays/ReaderTopBar.kt` | ~60 | 顶部工具栏（书名、返回、目录） |
| `feature/reader/overlays/ReaderBottomBar.kt` | ~60 | 底部进度条与时间/电量 |
| `feature/reader/overlays/ReaderOverlayPanels.kt` | ~80 | DirectoryDialog + QuickSettingsSheet 的弹出包装 |
| `feature/reader/ReaderScreen.kt`（保留） | ~250 | 顶层 Scaffold、AndroidView、子组件装配 |

**拆分理由**
- 16 个 `LaunchedEffect` 按"prefs/runtime/lifecycle"分组，便于定位
- 手势闭包提取为命名函数后可独立单测
- 顶/底/浮层工具栏独立成组件，可被其他阅读模式（如"听书模式"）复用

---

### 3.7 BookRepository.kt（662 行，🟠 中）

#### SRP 违反分析

| Actor | 典型需求（变更原因） | 波及当前文件的区域 |
|---|---|---|
| **书架产品** | 调整查询/排序/分页策略 | `getAllBooks` / `getBookshelfPage` / `searchBooks` |
| **文件夹产品** | 新增文件夹权限/层级结构 | `createFolder` / `moveBooksToFolder` / `FolderEntity` 相关 |
| **阅读进度数据工程师** | 改进度持久化 schema、接入云同步 | `updateReadingProgress` / `getReadingDurations` / `getTodayStartTimestamp` |
| **EPUB/TXT 解析团队** | 新增格式、调整解析策略 | `parseBookContent` / `getChapterText` / `readTxtChapterByEntity` |
| **导入产品** | 调整去重/拷贝/临时缓存策略 | `importBook`（~100 行独占） |
| **全文搜索工程师** | 调整索引算法、新增分词器 | `refreshSearchIndex` / `ensureChapterIndex` / `searchInBook` |
| **性能工程师** | 字节窗口加载策略 | `loadByteWindow` / `loadChapterText` / `byteWindowReader` |

**违反判据**：A（≥ 7 个 Actor）+ B（7 个业务域 = 7 个变更轴）+ C（依赖 `BookDao` + `EpubParser` + `TxtParser` + `byteWindowReader` + `booksDir`）同时成立。**SRP 严重违反**。

#### 当前职责

单个 class 包含 ~30 个方法，跨 5 个业务域：

| 业务域 | 方法 | 行数 |
|---|---|--:|
| 书籍查询 | `getAllBooks`、`getBookById`、`searchBooks`、`searchBooksPage`、`getBookshelfPage` | ~50 |
| 文件夹管理 | `getAllFolders`、`createFolder`、`updateFolder`、`deleteFolder`、`moveBooksToFolder` | ~40 |
| 书籍元数据 | `updateBookPinnedSlot`、`toggleFavorite`、`setCustomCoverPaletteIndex`、`updateLastReadTime` | ~50 |
| 阅读进度 | `updateReadingProgress`、`updateReadingPosition`、`getReadingDuration`、`getReadingDurations`、`getTodayReadingTime` | ~50 |
| 内容解析 | `parseBookContent`、`getChapterText` (2 个重载)、`readTxtChapterByEntity` | ~80 |
| 书籍导入 | `importBook` | ~100 |
| 全文搜索索引 | `refreshSearchIndex`、`ensureChapterIndex`、`getChapterIndex`、`searchInBook` | ~80 |
| 字节窗口加载 | `loadByteWindow`、`loadChapterText`、`resolveChapterTitle` | ~50 |

**拆分方案**

| 新文件 | 行数（估） | 核心职责 |
|---|--:|---|
| `core/repository/BookQueryRepository.kt` | ~100 | 书籍查询 + 书架分页 |
| `core/repository/FolderRepository.kt` | ~80 | 文件夹 CRUD + 书籍归类 |
| `core/repository/ReadingProgressRepository.kt` | ~100 | 阅读进度 + 时长统计 + 收藏 |
| `core/repository/BookContentRepository.kt` | ~200 | EPUB/TXT 解析、章节文本获取、字节窗口 |
| `core/repository/BookImportRepository.kt` | ~120 | 文件导入、去重、临时缓存 |
| `core/repository/SearchIndexRepository.kt` | ~100 | 章节索引构建、全文搜索 |

> ⚠️ **不保留 facade**：项目处于 pre-release（§ 0），直接删除 `BookRepository` 类，call sites 改为按需注入具体领域 repo。这避免了 facade 引入的"间接层 + 性能损耗 + 认知负担"。

#### DI 注入策略调整

`ShuLiAppContainer` 原先提供单个 `bookRepository`，拆分后改为：

```kotlin
class ShuLiAppContainer(...) {
    val bookQueryRepository by lazy { BookQueryRepository(bookDao) }
    val folderRepository by lazy { FolderRepository(bookDao) }
    val readingProgressRepository by lazy { ReadingProgressRepository(bookDao, readingProgressDao) }
    val bookContentRepository by lazy { BookContentRepository(bookDao, epubParser, txtParser, byteWindowReader, booksDir) }
    val bookImportRepository by lazy { BookImportRepository(bookDao, booksDir) }
    val searchIndexRepository by lazy { SearchIndexRepository(bookDao) }
}
```

调用方按需注入所需 repo（不再注入整个 `BookRepository`）。

#### 拆分理由
- 每个 repo 可独立 mock 测试（当前 `BookRepository` 依赖 `BookDao` + `EpubParser` + `TxtParser` + `byteWindowReader` + `booksDir`，mock 成本高）
- `BookContentRepository` 可独立演进（引入 PDF、CBZ 等格式时不影响进度逻辑）
- 按需注入具体 repo，避免"整个 `BookRepository` 全量依赖"的 SRP 违反
- 删除 facade 中间层，调用链路更短、更易追踪

---

### 3.8 EpubParser.kt（599 行，🟡 中）

#### SRP 违反分析

| Actor | 典型需求（变更原因） | 波及当前文件的区域 |
|---|---|---|
| **EPUB 标准工程师** | 适配 EPUB 3.3、处理新 OPF 属性 | `parseMetadata` / `parseTocNcx` / `parseNavXhtml` |
| **内容产品** | 调整章节提取策略、处理特殊 spine | `parseChapter` / `parseChapters` / `buildHrefToSpineIndex` |
| **HTML 解析团队** | 升级 Jsoup、调整 block/inline 处理 | `extractTextFromHtml` / `collectBlockText` / `processInlineContent` |
| **排版设计师** | 调整 bold/italic 保留策略 | `toBoldUnicode` / `toItalicUnicode` |

**违反判据**：A（4 个 Actor）+ B（EPUB 结构 / HTML 文本 / 章节提取 / 样式转换 4 个变更轴）同时成立。**SRP 明显违反**。

#### 当前职责

| 职责 | 行号 | 行数 |
|---|---|--:|
| EPUB 结构解析（metadata/NCX/nav/spine） | 27–352 | ~325 |
| 章节内容解析 | 353–472 | ~120 |
| HTML 文本提取 | 473–600 | ~130 |
| Unicode 样式转换（bold/italic） | 633–662 | ~30 |

**拆分方案**

| 新文件 | 行数（估） | 核心职责 |
|---|--:|---|
| `core/parser/epub/EpubStructureParser.kt` | ~330 | OPF/NCX/NAV/Spine 解析 |
| `core/parser/epub/EpubChapterExtractor.kt` | ~130 | 按 spine index 提取章节 HTML |
| `core/parser/html/HtmlTextExtractor.kt` | ~130 | Jsoup 元素遍历、block/inline 文本提取 |
| `core/parser/text/UnicodeStyleConverter.kt` | ~40 | `toBoldUnicode` / `toItalicUnicode` |
| `core/parser/EpubParser.kt`（保留） | ~50 | 聚合入口 |

**拆分理由**
- `HtmlTextExtractor` 可复用于 CBZ/PDF 等其他格式的"HTML 内文本提取"
- `UnicodeStyleConverter` 可独立单测，与 EPUB 解耦
- 结构解析与内容解析分离，引入新 EPUB 版本（如 EPUB 3.3）时影响面小

---

### 3.9 BookshelfViewModel.kt（536 行，🟡 中）

#### SRP 违反分析

| Actor | 典型需求（变更原因） | 波及当前文件的区域 |
|---|---|---|
| **书架 UI 产品** | 调整视图模式/排序/过滤 | `onViewModeChanged` / `onSortOrderChanged` / `onFilterChanged` |
| **编辑模式产品** | 批量选择/移动/合并/固定/拖拽 | `onToggleEditMode` / `mergeNodes` / `pinNode` / `commitDragResult`（~100 行） |
| **导入产品** | 单本/多本/文件夹导入策略 | `onImportBook` / `onImportBooks` / `onImportFolder`（~80 行） |
| **书籍元数据产品** | 收藏/删除/封面调色板 | `onToggleFavorite` / `onDeleteBook` / `setBookCoverPalette` |

**违反判据**：A（4 个 Actor）+ B（UI/编辑/导入/元数据 4 个变更轴）同时成立。**SRP 明显违反**。

#### 当前职责

~35 个 `onXxx` 回调处理函数，按业务分组：

| 业务 | 方法 | 行数 |
|---|---|--:|
| 视图模式/排序/过滤 | `onViewModeChanged`、`onSortOrderChanged`、`onFilterChanged`… | ~40 |
| 书籍操作 | `onBookClick`、`onToggleFavorite`、`onDeleteBook` | ~30 |
| 编辑模式 | `onToggleEditMode`、`onToggleNodeSelection`、`onSelectAllNodes` | ~40 |
| 节点移动/合并/固定 | `onMoveSelectedToFolder`、`mergeNodes`、`pinNode`、`commitDragResult` | ~100 |
| 导入 | `onImportBook`、`onImportBooks`、`onImportFolder` | ~80 |
| Toast | `showToastMessage` | ~10 |

**拆分方案**

| 新文件 | 行数（估） | 核心职责 |
|---|--:|---|
| `feature/bookshelf/BookshelfViewModel.kt`（保留） | ~200 | UI state、视图/排序/过滤、基本点击 |
| `feature/bookshelf/BookshelfEditActions.kt` | ~120 | 编辑模式、批量选择、移动、合并、固定、拖拽 |
| `feature/bookshelf/BookshelfImportActions.kt` | ~100 | 单本/多本/文件夹导入 |
| `feature/bookshelf/BookshelfNodeOperations.kt` | ~80 | `deleteNodes`、`mergeNodes`、`pinNode` 等纯业务逻辑 |

**拆分理由**
- ViewModel 只保留与 UI 直接相关的回调
- 纯业务逻辑（合并/删除/移动）可独立单测，无需 Compose 测试框架

---

### 3.10 BookshelfScreen.kt（523 行，🟡 中）

#### SRP 违反分析

| Actor | 典型需求（变更原因） | 波及当前文件的区域 |
|---|---|---|
| **书架 UI 产品** | 调整 Scaffold 布局、FAB 位置 | 主 composable 框架 |
| **编辑模式产品** | 多选操作栏、批量菜单 | 编辑模式闭包 |
| **空状态设计** | 调整空书架/搜索引导文案与插画 | `EmptyState` / `SearchGuideState` |
| **对话框产品** | 调整删除/合并/移动确认对话框 | `AlertDialog` 块 |

**违反判据**：A（4 个 Actor）+ B（布局/编辑/空状态/对话框 4 个变更轴）成立。**SRP 明显违反**。

#### 当前职责

主 `@Composable fun BookshelfScreen()` 独占 361 行，内含 Scaffold + TopBar + FAB + AlertDialog + 内容区 + 多选菜单。

**拆分方案**

| 新文件 | 行数（估） | 核心职责 |
|---|--:|---|
| `feature/bookshelf/BookshelfScreen.kt`（保留） | ~150 | Scaffold 编排 |
| `feature/bookshelf/BookshelfContentArea.kt` | ~100 | `BookContent`：根据 ViewMode 切换 Grid/List/CompactList |
| `feature/bookshelf/BookshelfEditBar.kt` | ~80 | 编辑模式下的底部动作条 + 多选菜单 |
| `feature/bookshelf/BookshelfEmptyStates.kt` | ~60 | `EmptyState`、`SearchGuideState` |
| `feature/bookshelf/BookshelfDialogs.kt`（已存在，扩展） | +40 | 移入删除确认、合并确认等 `AlertDialog` |

---

### 3.11 DirectoryDialog.kt（522 行，🟡 中）

#### SRP 违反分析

| Actor | 典型需求（变更原因） | 波及当前文件的区域 |
|---|---|---|
| **目录产品** | 调整章节列表交互、搜索、跳转 | `ChapterList`（154–243 行） |
| **书签产品** | 调整书签列表、删除确认、跳转 | `BookmarkList`（244–350 行） |
| **笔记产品** | 调整笔记列表、编辑、颜色、删除 | `NoteList`（351–521 行） |
| **对话框设计** | 调整 Dialog 容器样式、Tab 切换 | 主 composable（72–153 行） |

**违反判据**：A（4 个 Actor）+ B（章节/书签/笔记 3 个独立变更轴）同时成立。**SRP 明显违反**。

#### 当前职责

主 `@Composable fun DirectoryDialog()`（82 行）+ 3 个列表子组件：

| 子组件 | 行号 | 行数 | 内容 |
|---|---|--:|---|
| `ChapterList` | 154–243 | ~90 | 章节列表、搜索、跳转 |
| `BookmarkList` | 244–350 | ~106 | 书签列表、删除、跳转 |
| `NoteList` | 351–521 | ~170 | 笔记列表、编辑、删除、颜色 |

**拆分方案**

| 新文件 | 行数（估） | 核心职责 |
|---|--:|---|
| `feature/reader/component/directory/ChapterList.kt` | ~100 | 章节列表 |
| `feature/reader/component/directory/BookmarkList.kt` | ~120 | 书签列表 |
| `feature/reader/component/directory/NoteList.kt` | ~180 | 笔记列表 |
| `feature/reader/component/directory/DirectoryDialog.kt`（保留） | ~100 | Dialog 容器、Tab 切换、搜索框 |

**拆分理由**
- 三个列表结构相似但业务差异大（章节只读，书签可删，笔记可编辑/着色），独立演进
- `NoteList` 最长（170 行），独立后可进一步优化（颜色选择器、Markdown 预览）

---

## 4. 优先级与执行节奏

### 4.0 SRP 视角的执行顺序

综合 § 2.5 的 SRP 乘积与文件行数，得到如下优先级排序（**SRP 严重度优先，行数次之，风险最后**）：

| 优先级 | 文件 | SRP 乘积 | 行数 | 风险 | 建议迭代 |
|:---:|---|--:|--:|:---:|:---:|
| P0 | `ReaderViewModel.kt` | 36 | 2541 | 高 | 迭代 1（已完成 50%） |
| P0 | `ReaderScreen.kt` | 112 | 873 | 高 | 迭代 1 |
| P1 | `SettingsScreen.kt` | 56 | 1104 | 低 | 迭代 2 |
| P1 | `BookRepository.kt` | 49 | 662 | 中高 | 迭代 2 |
| P2 | `QuickSettingsSheet.kt` | 12 | 1118 | 低 | 迭代 3 |
| P2 | `ReaderCanvasView.kt` | 25 | 929 | 中 | 迭代 3 |
| P2 | `BookshelfViewModel.kt` | 16 | 536 | 低 | 迭代 3 |
| P3 | `DirectoryDialog.kt` | 12 | 522 | 低 | 迭代 4 |
| P3 | `BookshelfScreen.kt` | 16 | 523 | 低 | 迭代 4 |
| P3 | `EpubParser.kt` | 16 | 599 | 低 | 迭代 4 |
| P4 | `AppStrings.kt` | 10 | 1969 | 低 | 迭代 4（一次性替换 call sites） |

**关键决策**：
- `ReaderScreen.kt` 的 SRP 乘积最高（112），应提升为 P0，与 `ReaderViewModel` 同步推进
- `AppStrings.kt` 虽然行数排第 2，但 SRP 乘积仅 10，**不急拆**——由于项目允许破坏性改动（§ 0），可一次性同步替换所有 call sites，无需兼容层
- `BookRepository.kt` 的 SRP 严重度高（49），且是领域边界不清的典型症状，应在迭代 2 按 DDD 限界上下文切分

按"风险 × 收益 × SRP 严重度"排序，建议分 4 个迭代完成：

### 迭代 1（SRP P0，高收益、高风险，3–5 天）

**目标**：解决两个 SRP 最严重的"巨型上帝文件"。

1. `ReaderViewModel.kt` 剩余 6 个模块抽出：
   - `ChapterPaginationCoordinator`（分页/reflow/预加载）
   - `TtsPlaybackManager`（TTS 引擎交互）
   - `BookmarkNotesManager`（书签/笔记 CRUD）
   - `BookSessionManager`（开书/章节切换/进度持久化）
   - `ReaderPresetManager`（预设增删改查）
   - `ReaderNavigationCoordinator`（翻页/scrub/目录切换）

2. `ReaderScreen.kt`（SRP 乘积 112，最严重）：
   - 16 个 `LaunchedEffect` 按 prefs/runtime/lifecycle 分组抽出
   - gesture 回调闭包提取为命名函数
   - 顶栏/底栏/浮层面板独立成组件

### 迭代 2（SRP P1，高收益、中风险，3–5 天）

**目标**：解决两个 SRP 严重的领域聚合文件。

3. `SettingsScreen.kt`（SRP 乘积 56）→ 8 个 Section 抽出：
   - `AppearanceSection` / `ReaderPrefsSection` / `LibrarySection` / `StatsSection`
   - `SyncSection` / `TtsSection` / `AdvancedSection` / `AboutSection`
   - `SettingsItems.kt` 公共 item 组件

4. `BookRepository.kt`（SRP 乘积 49）→ 6 个领域 repo：
   - `BookQueryRepository` / `FolderRepository` / `ReadingProgressRepository`
   - `BookContentRepository` / `BookImportRepository` / `SearchIndexRepository`

### 迭代 3（SRP P2，中收益、低风险，2–3 天）

**目标**：解决 SRP 高的 Compose UI 与渲染类文件。

5. `QuickSettingsSheet.kt`（SRP 乘积 12）→ 3 个 Panel 抽出
6. `ReaderCanvasView.kt`（SRP 乘积 25）→ 触摸/缓存/视觉参数/选区拆分
7. `BookshelfViewModel.kt`（SRP 乘积 16）→ Edit/Import/Operations 抽出

### 迭代 4（SRP P3/P4，长尾优化，2–3 天）

**目标**：收尾所有 SRP 中等/明显的文件。

8. `DirectoryDialog.kt`（SRP 乘积 12）→ 3 个列表抽出
9. `BookshelfScreen.kt`（SRP 乘积 16）→ Empty/Content/EditBar/Dialogs 抽出
10. `EpubParser.kt`（SRP 乘积 16）→ 结构/内容/HTML/Unicode 拆分
11. `AppStrings.kt`（SRP 乘积 10）→ 7 个子接口 + 一次性替换所有 call sites

---

## 5. 风险控制与回滚策略

> ⚠️ 本节基于 § 0 项目上下文（pre-release，允许破坏性改动）制定。风险被显著降低——回滚 = `git revert`，没有用户影响。

### 拆分原则
- **一个模块 = 一个变更轴**：拆出的新文件只响应一类需求变化
- **构造器注入 + 回调**：子模块不反向依赖父类，仅持有 `MutableStateFlow` 或回调
- **Call sites 一次性同步替换**：拆分 + 改调用方 + 删旧类在**同一 commit** 完成，不保留任何兼容层
- **单一编译单元验证**：每拆一个模块立即 `./gradlew :app:compileDebugKotlin` 验证
- **单元测试先行**：拆分前给目标模块补关键路径单测，拆分后回归

### 回滚策略

> ⚠️ 由于项目处于 pre-release 阶段（§ 0），回滚策略可以更激进：

- **拆分失败 → `git revert` 整个 commit**：call sites 与旧类一起回滚，无残留
- **方案走不通 → 删分支重来**：用 `git worktree` 隔离试验，失败直接删除
- **每个拆分是一个原子 commit**：拆分 + 改 call sites + 删旧类 在同一 commit 内完成，回滚粒度清晰
- **保留 `main` 分支编译通过**：虽然没有发布，但保持 `main` 始终可编译是团队纪律

### 风险矩阵

> ⚠️ 由于项目允许破坏性改动（§ 0），整体风险显著降低：任何拆分失败都可以 `git revert` 完整回滚，无用户影响。

| 文件 | 风险 | 主要风险点 | 缓解措施 |
|---|:---:|---|---|
| `ReaderViewModel.kt` | 🔴 高 | 回调链复杂、state 跨模块共享 | 子模块只持有 `MutableStateFlow` 引用，不复制 state |
| `ReaderScreen.kt` | 🟠 中高 | 16 个 `LaunchedEffect` 分散、gesture 闭包状态多 | 按 prefs/runtime/lifecycle 分组；闭包提取为命名函数 |
| `AppStrings.kt` | 🟢 低 | 700+ call sites 需一次性替换 | 按子接口分组批量替换，每子接口一个 commit + 编译验证 |
| `BookRepository.kt` | 🟢 低 | 6 个领域 repo 的 DI 装配需重写 | `ShuLiAppContainer` 按领域 lazy 注入，调用方按需依赖 |
| `ReaderCanvasView.kt` | 🟠 中 | 触摸逻辑与 View 生命周期紧耦合 | `CanvasTouchHandler` 接收 `MotionEvent` 即可，不依赖 View |
| `SettingsScreen.kt` | 🟢 低 | 纯 UI 拆分 | 每个 Section 直接 `@Composable` 独立 |
| `DirectoryDialog.kt` | 🟢 低 | 3 个列表完全独立 | 一次性拆完 |

> 注：`BookRepository.kt` 和 `AppStrings.kt` 因允许破坏性改动，风险从 🟠 中高 / 🟡 中 **降至 🟢 低**——一次性替换比渐进迁移更简单、更不易出错。

---

## 6. 拆分后收益预期

### 6.1 可量化指标

| 指标 | 拆分前 | 拆分后 |
|---|---|---|
| 最长文件行数 | 2541 | ~500（`ReaderCanvasView` 保留） |
| 超过 500 行的文件数 | 11 | 0 |
| 平均单文件行数（feature 层） | ~700 | ~250 |
| 单文件编译时间（平均） | ~3s | ~0.8s |
| `ReaderViewModel` 单元测试 mock 数 | ~15 | ~3 |
| 新增词条需修改文件数 | 3（ZhHans/ZhTw/En 同文件） | 1（对应子接口） |

### 6.2 SRP 改善指标

| SRP 指标 | 拆分前 | 拆分后 | 说明 |
|---|---|---|---|
| **最大单文件 SRP 乘积** | 112（ReaderScreen） | ≤ 12 | 任何单一文件只承载 1 个变更轴 |
| **SRP 乘积总和**（11 文件） | 376 | ≤ 60 | 整体架构"职责清晰度"提升 6 倍 |
| **SRP 严重违反文件数**（乘积 ≥ 36） | 4 | 0 | 消除"上帝文件" |
| **平均单文件 Actor 数** | 5.1 | ≤ 1.5 | 每个文件只服务 1 个利益相关方 |
| **跨业务域依赖** | 广泛 | 单向 | 变更一个业务域不会牵连其他域 |

### 6.3 业务价值

- **研发效率**：新需求只需修改 1 个文件，PR review 粒度细，并行开发不冲突
- **测试覆盖**：子模块可独立 mock，单测粒度从"整个 ViewModel"降至"单个 Manager"
- **回归成本**：改 TTS 不需回归书签功能，改书签不需回归分页算法
- **新人上手**：新成员只需阅读目标模块的 ~200 行代码即可开始贡献

---

## 7. 完成标准

### 7.1 基础验收（必须）

- [ ] 所有 >500 行的文件降至 <500 行（特殊文件可放宽到 600 行）
- [ ] 每个新文件有清晰的单一职责，文件名即职责
- [ ] 现有所有单元测试通过
- [ ] `./gradlew :app:assembleDebug` 与 `./gradlew :app:assembleRelease` 成功
- [ ] 主业务流程回归：开书、翻页、设置、TTS、同步、备份
- [ ] `docs/reader-architecture-notes.md` 更新模块拓扑图

### 7.2 SRP 验收（必须）

- [ ] **Actor 判据**：每个新文件只服务于 1 个主要 Actor（允许 1 个辅助 Actor）
- [ ] **变更轴判据**：每个新文件只能列举出 1 个核心变更原因（PR 主题集中）
- [ ] **依赖方向判据**：子模块之间无循环依赖；依赖方向为 ViewModel/Screen → Manager → StateFlow
- [ ] **反向回归测试**：针对每个新模块，列举 3 个"未来可能的需求变更"，验证每个变更只影响 1 个文件
- [ ] **PR 粒度验证**：模拟 10 个历史 PR，验证每个 PR 只修改 1 个文件
- [ ] **旧类清除验证**：被拆分出的旧类/旧方法已完全删除，不存在 `@Deprecated` 残留、facade 中间层或 delegation 薄代理

### 7.3 质量验收（建议）

- [ ] 每个新文件有 ≥ 1 个对应的单元测试文件
- [ ] 每个新文件顶部有 KDoc 说明职责、依赖、Actor
- [ ] 拆分过程中未引入任何"上帝 delegate"（单个方法 > 50 行的薄代理）
- [ ] 未引入"循环依赖的接口抽象"（用接口解决循环依赖是反模式）
- [ ] **Call sites 零残留**：grep 搜索旧类名/旧方法名，结果为 0（所有调用方已同步替换）
- [ ] **编译无警告**：`./gradlew :app:compileDebugKotlin` 无 `deprecation` / `unused` 警告

---

## 8. 基准提交与回滚指南

> 本节记录本文档对应的 Git 基准位置，便于拆分失败时回退。
> 最后更新：2026-06-05

### 8.1 基线信息

| 项 | 值 |
|---|---|
| 仓库地址 | `https://github.com/jinghu-moon/ShuLi-Reader` |
| 基线分支 | `main` |
| 基线 commit | `2756ad2c995e08e77c17b0af0143c30131106409` |
| 基线 commit 信息 | `docs(refactor): 大文件 SRP 拆分重构方案（重构基准参考）` |
| 基线 commit 内容 | 新增 `docs/23-large-file-split-refactor.md`（本文档，873 行） |
| 上一 commit | `7a291e2568445c7c1bdb56b77f2f647c80f12ddf` |

### 8.2 已推送的相关分支

| 分支 | 用途 | 状态 |
|---|---|---|
| `main` | 主分支，包含本文档 | ✅ 已推送 |
| `refactor/split-large-files` | ReaderViewModel 阶段一拆分试验（已完成 3/4 模块） | ✅ 已推送 |

> `refactor/split-large-files` 分支顶 commit：`abcb8b201722fdf49a0858dfd3b26964cae54c39`
> PR 创建链接：https://github.com/jinghu-moon/ShuLi-Reader/pull/new/refactor/split-large-files

### 8.3 回滚命令速查

#### 情况 1：单个拆分 commit 失败，回退该 commit

```bash
# 查看待回退 commit
git log --oneline

# 保留工作区，回退 commit（可重新提交）
git reset --soft HEAD~1

# 彻底回退（丢弃所有变更）
git reset --hard HEAD~1

# 创建反向 commit（推荐，不破坏历史）
git revert <commit-sha>
```

#### 情况 2：整个拆分分支走不通，删除重来

```bash
# 切回 main
git checkout main

# 删除本地试验分支
git branch -D refactor/split-<name>

# 删除远程试验分支
git push origin --delete refactor/split-<name>

# 用 git worktree 重新开一个干净的试验分支
git worktree add ../ShuLi-Reader-refactor-<name> -b refactor/split-<name> main
```

#### 情况 3：回到本文档记录的基线

```bash
# 切回 main 并重置到基线 commit
git checkout main
git reset --hard 2756ad2c995e08e77c17b0af0143c30131106409
git push origin main --force-with-lease
```

### 8.4 推荐的 Git 工作流

每次启动一个新的拆分任务前，建议：

```bash
# 1. 确保 main 是最新
git checkout main
git pull origin main

# 2. 用 git worktree 创建隔离的试验分支
git worktree add ../ShuLi-Reader-refactor-<module> -b refactor/split-<module> main

# 3. 进入 worktree 开始拆分
cd ../ShuLi-Reader-refactor-<module>

# 4. 每个模块拆完后独立 commit
git add -A && git commit -m "refactor(<module>): 按 SRP 拆分 <旧文件> 为 <N> 个职责单一的子模块"

# 5. 拆分完成后推送
git push -u origin refactor/split-<module>

# 6. 创建 PR review，合并后回到 main 清理 worktree
cd ../ShuLi-Reader
git merge refactor/split-<module>
git worktree remove ../ShuLi-Reader-refactor-<module>
git branch -d refactor/split-<module>
```

### 8.5 凭证配置（首次推送须知）

若 `git push` 报 `fatal: could not read Username`，可用以下任一方式解决：

| 方式 | 命令 | 适用场景 |
|---|---|---|
| **临时**（单次推送） | `git -c credential.helper='!"C:/Program Files/GitHub CLI/gh" auth git-credential' push origin <branch>` | 一次性使用 |
| **持久**（推荐） | 安装 [Git Credential Manager](https://github.com/git-ecosystem/git-credential-manager) | Windows 原生，一次认证永久缓存 |
| **SSH** | 配置 SSH key 后改 remote URL：`git remote set-url origin git@github.com:jinghu-moon/ShuLi-Reader.git` | 长期开发者 |

### 8.6 验证基线

每次拆分前，可用以下命令确认当前处于基线状态：

```bash
# 应输出 2756ad2c995e08e77c17b0af0143c30131106409（或更新的基线 commit）
git rev-parse main

# 应只看到本文档
git ls-files docs/ | grep 23-large-file-split-refactor

# 应无未提交变更
git status --short
```
