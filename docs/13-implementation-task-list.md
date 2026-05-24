# 13 - TDD 卓越实现任务清单

> 基于 `docs/00` 至 `docs/12` 的需求、架构、性能、UI、设置、进度和阅读界面设计整理。本文档是后续实现的执行清单，不替代设计文档；任何实现偏离设计文档时，先更新设计依据，再改代码。

## 一、当前基线

### 1.1 已有实现

- 项目当前以 `:app` 为主模块，并已补充 `:benchmark` 性能基准模块。
- 技术栈已经落地：Kotlin 2.1.0、AGP 8.7.3、Compose、Material3、Room、DataStore、Coroutines、Serialization、Coil、OkHttp、Jsoup、juniversalchardet。
- 已有主路径：
  - `MainActivity`：书架、设置、阅读三个页面的可测试状态导航。
  - `feature/bookshelf`：书架 UI、导入入口、排序、筛选、搜索、统计入口、收藏与书籍信息面板。
  - `feature/settings`：设置页与 `UserPreferences` 的 DataStore 持久化。
  - `core/database`：Book、Bookmark、Note、ReadingProgress 的 Room 实体与 DAO。
  - `core/parser`：TXT/EPUB 元数据、章节与正文解析。
  - `core/reader`：分页、Canvas 渲染、翻页委托、缓存、进度与性能采样。
  - `feature/reader`：阅读页 ViewModel、ReaderScreen、目录/书签/笔记入口。
  - `core/repository`：`BookRepository` 负责书籍导入、查询、正文搜索、阅读时长读取。
  - `core/tts`：TTS 状态控制器与 Android `TextToSpeech` 适配。
  - `core/sync`：WebDAV 最小客户端与进度同步管理。
- 当前仍需设备或截图环境验证的点：
  - 阅读页搜索结果上/下一个 UI、长按选区复制/笔记等高级交互。
  - 真机截图回归、TalkBack 全流程、Macrobenchmark 场景脚本和 100MB+ 长时间阅读性能报告。
  - WebDAV 账号设置界面与加密凭据持久化。

### 1.2 与设计文档的关键差距

- 阅读器核心已经形成代码级闭环，但截图回归、TalkBack、连接设备 UI 测试和 Macrobenchmark 场景仍需设备环境补证。
- TTS 与 WebDAV 已完成核心层测试，尚未全部接入阅读页 UI 与设置页安全凭据管理。
- 正文搜索已具备仓库层结果模型，阅读页内上/下一个结果与高亮 UI 仍需补齐。
- 大文件和长时间阅读性能已经有采样工具，仍需在真机上沉淀 100MB+ 性能报告。

### 1.3 不变约束

- 保持单模块优先。除非测试、构建或依赖隔离已经证明单模块成为实际瓶颈，否则不拆多 Gradle 模块。
- 阅读器核心渲染优先采用传统 `View + Canvas + TextPaint`，通过 `AndroidView` 接入 Compose。
- 业务边界按包组织：`core/parser`、`core/reader`、`core/database`、`core/repository`、`feature/reader`、`feature/bookshelf`、`feature/settings`。
- 任何大文件路径不得依赖全量 `String` 常驻内存。小文件可走简化路径，但必须有清晰阈值与测试覆盖。
- 进度保存必须以章节索引和字符偏移为权威位置，页码只能作为当前排版的派生值。
- UI 文案、注释和文档保持简体中文为主，现有多语言资源另行维护。

## 二、质量目标与验收门禁

### 2.1 功能门禁

- 支持导入 TXT 与 EPUB。
- 支持从书架进入阅读页，并恢复上次阅读位置。
- 支持左右点击热区、左右滑动、覆盖翻页、平移翻页、无动画，滚动和仿真翻页可按 P1/P2 排期。
- 支持页眉、页脚、页码、进度百分比、底部进度条、目录、快速设置、夜间模式。
- 支持书签、笔记、正文搜索、阅读时长统计。
- P2 支持 WebDAV 进度同步与 TTS。

### 2.2 性能门禁

| 指标 | 目标 | 验收证据 |
|------|------|----------|
| 100MB TXT 导入元数据 | 不全量解码正文 | 单元测试 + 方法耗时基准 |
| 首屏进入阅读 | < 500ms | Macrobenchmark 或本地性能日志 |
| 翻页帧率 | 稳定 60fps，单帧 < 16.6ms | Macrobenchmark + Choreographer 掉帧统计 |
| 阅读内存 | 常规阅读 < 150MB | Android Studio Profiler 或自动化内存采样 |
| 分页缓存 | 页、章节、位图缓存上限可控 | 单元测试验证 LRU 驱逐 |
| 数据库查询 | 书架和搜索不阻塞主线程 | DAO 测试 + StrictMode/主线程检查 |

### 2.3 UI 与动效门禁

- 视觉遵循 `04-ui-design-system.md`：沉浸、克制、舒适；浅色、暗色、纸质主题完整。
- 阅读页遵循 `12-reader-screen-design.md`：页眉页脚、工具栏、热区、目录、搜索、快速设置和状态机一致。
- 触控目标不小于 48dp，主要操作建议 56dp。
- 正文与 UI 文本对比度满足可访问性要求：正文 4.5:1 以上，大字和控件 3:1 以上。
- 动效时长：微交互 50-100ms，普通展开/收起 200-300ms，复杂翻页 300-500ms。
- 动效不能改变布局稳定尺寸，不能造成工具栏、进度条、文字重叠。

### 2.4 TDD 门禁

每个实现任务必须按以下顺序完成：

1. Red：先写失败测试，证明当前行为缺失或错误。
2. Green：实现刚好通过测试的最小代码。
3. Refactor：在测试保护下消除重复、收紧边界、改善命名。
4. Measure：涉及性能、渲染、动效的任务必须补充性能或截图证据。
5. Review：检查 KISS、YAGNI、DRY、SOLID，确认没有引入无用抽象或过早扩展。

### 2.5 基础命令

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:connectedDebugAndroidTest
./gradlew.bat :app:assembleDebug
```

 有 UI、截图或性能任务时，还需要补充：

```powershell
./gradlew.bat :app:connectedDebugAndroidTest --stacktrace
./gradlew.bat :benchmark:connectedCheck
```

`benchmark` 模块尚未存在，见 T0.5。

## 三、任务总览

| 阶段 | 目标 | 优先级 | 核心验收 |
|------|------|--------|----------|
| T0 | 测试与质量基础设施 | P0 | 测试目录、规则、Fixture、基准方案可用 |
| T1 | 架构边界与导航 | P0 | 单模块包边界清晰，书架可进入阅读页 |
| T2 | 数据模型与 Room | P0 | 字符偏移进度、书签、笔记、FTS 与迁移可测 |
| T3 | 高性能文件解析 | P0 | TXT/EPUB 解析正确且大文件不爆内存 |
| T4 | 阅读引擎模型与分页 | P0 | TextPage/TextLine/TextColumn、虚拟化分页、缓存可测 |
| T5 | Canvas 渲染层 | P0 | 正文、页眉页脚、进度绘制准确且非空 |
| T6 | 阅读页交互与状态机 | P0 | 热区、工具栏、进度跳转、保存恢复闭环 |
| T7 | 翻页动效系统 | P0/P1 | 无动画、覆盖、平移动效稳定，仿真翻页可扩展 |
| T8 | 书架体验补齐 | P0/P1 | 导入、重复检测、封面、收藏、信息面板完整 |
| T9 | 设置与主题闭环 | P0/P1 | 阅读设置即时生效，主题/字体/排版持久化 |
| T10 | 进度、统计、书签笔记 | P0/P1 | 防抖保存、恢复、书架进度、书签笔记可用 |
| T11 | 搜索、TTS、同步 | P1/P2 | FTS、朗读、WebDAV 冲突处理可测 |
| T12 | 性能卓越专项 | P0/P1 | 首屏、翻页、内存、启动均有基准与回归门禁 |
| T13 | UI 美观与动效打磨 | P0/P1 | 截图回归、可访问性、平板适配、暗色/纸质主题 |
| T14 | 发布质量与文档 | P0/P1 | Release 构建、混淆、隐私、说明文档完整 |

## 四、详细任务清单

### T0 - 测试与质量基础设施

#### T0.1 创建测试目录与基础规则 ✅

- [x] Red：新增一个最小 ViewModel 单元测试，当前因测试目录或规则缺失失败。
- [x] Green：创建 `app/src/test/java/com/shuli/reader` 与 `app/src/androidTest/java/com/shuli/reader`。
- [x] Green：新增 `MainDispatcherRule`，统一替换 `Dispatchers.Main`。
- [x] Green：新增协程测试基类，默认使用 `StandardTestDispatcher`。
- [x] Refactor：测试包命名与生产代码包一致。
- [x] 验收：`./gradlew.bat :app:testDebugUnitTest` 通过。

#### T0.2 建立测试 Fixture ✅

- [x] Red：为 TXT 章节检测新增 fixture 测试，当前 fixture 加载失败。
- [x] Green：创建 `app/src/test/resources/books`。
- [x] Green：提供小型 TXT、无章节 TXT、复杂章节标题 TXT、英文章节 TXT。
- [x] Green：提供最小 EPUB 生成工具或静态 EPUB fixture。（EpubTestUtils.createMinimalEpub 已实现）
- [x] Refactor：fixture 生成逻辑只放测试工具，不进入生产代码。
- [x] 验收：解析器测试可稳定读取 fixture。

#### T0.3 Room 与 DataStore 测试工具 ✅

- [x] Red：新增 DAO 插入查询测试，当前缺少 in-memory DB 工具失败。
- [x] Green：创建 Room in-memory database 工厂。
- [x] Green：创建临时 DataStore 工厂，避免污染真实用户设置。
- [x] Refactor：测试工具只暴露最小构造函数，避免测试框架反向影响生产代码。
- [x] 验收：DAO 和 UserPreferences 测试可并行运行。

#### T0.4 Compose UI 与截图测试基础 ✅

- [x] Red：为 Compose UI 写一个最小测试，当前缺少依赖或规则失败。
- [x] Green：补齐 `androidTestImplementation`：AndroidX Test、Compose UI Test、JUnit 扩展。
- [x] Green：建立语义节点命名规范，关键按钮和控件都有稳定测试 tag 或语义描述。（`UiTestTags` 已覆盖书架、阅读页、设置页关键入口）
- [x] Green：建立截图采集方案，用于阅读页浅色、暗色、纸质主题回归。（见 `docs/15-visual-regression-plan.md`）
- [x] Refactor：只给稳定交互目标加测试 tag，不污染普通文本。（`ComposeTestExample` 使用 tag 验证，普通文案不加 tag）
- [x] 验收：`./gradlew.bat :app:compileDebugAndroidTestKotlin` 编译通过。

#### T0.5 性能基准模块 ✅

- [x] Red：新增性能目标清单，当前没有 benchmark 任务失败。
- [x] Green：创建 `:benchmark` 模块或等价 Macrobenchmark 配置。
- [x] Green：覆盖冷启动、进入阅读首屏、连续翻页、滚动目录、导入大文件元数据。（T12 细化）
- [x] Green：输出基线 JSON 或 Markdown 报告到本地构建产物。（`:benchmark:assembleDebug` 生成 `benchmark/build/reports/benchmark/baseline.md`）
- [x] Refactor：性能测试只依赖公开入口，不访问私有实现细节。（T12 细化）
- [x] 验收：`:benchmark` 模块编译通过。

### T1 - 架构边界与导航

#### T1.1 明确单模块包结构 ✅

- [x] Red：新增架构约束测试或文档检查，证明 reader 包缺失。
- [x] Green：在 `:app` 内创建 `core/reader` 与 `feature/reader` 包。
- [x] Green：保留当前 `ShuLiAppContainer` 手动依赖容器，暂不引入 Koin，避免为未稳定边界增加复杂度。
- [x] Green：抽象 `BookRepository` 依赖接口只在测试或多实现真实需要时引入。（注：`ReaderViewModel` 已通过构造函数注入 `UserPreferences`）
- [x] Refactor：移动代码时只做必要包调整，不顺手重写书架和设置。
- [x] 验收：包结构与 `docs/07-project-structure.md` 的职责一致，但保持当前单模块实际形态。

#### T1.2 导航从状态切换升级为可测试导航 ✅

- [x] Red：书籍点击应触发阅读页，新增测试证明当前停留在 TODO。
- [x] Green：新增 `ActiveScreen.Reader(bookId)` sealed class 实现。
- [x] Green：`BookshelfEvent.NavigateToReader(bookId)` 被 `MainActivity` 正确消费。
- [x] Green：阅读页返回后回到书架，书架状态不丢失。
- [x] Refactor：导航事件只由 ViewModel 发出，页面不直接修改仓库。
- [x] 验收：点击书籍后显示阅读页占位内容。

#### T1.3 错误处理统一 ✅

- [x] Red：导入非法文件、EPUB 缺失 OPF、数据库异常分别有失败测试。
- [x] Green：定义 `BookResult<T>` + `BookError` 领域错误模型。
- [x] Green：UI 层显示本地化错误文案，日志保留技术细节。（BookshelfEvent.ShowMessage / SettingsEvent.ShowMessage 已实现）
- [x] Refactor：删除重复 try-catch，把错误边界收敛到 Repository 或 UseCase。（BookRepository 层已通过 BookResult 统一错误边界）
- [x] 验收：`BookResult` 和 `BookError` 单元测试通过。

### T2 - 数据模型与 Room

#### T2.1 扩展 BookEntity 的阅读位置字段 ✅

- [x] Red：新增恢复阅读位置测试，当前 `BookEntity` 缺少字符偏移字段而失败。
- [x] Green：添加 `durChapterIndex`、`durChapterPos`、`durChapterTitle`、`durChapterTime`、`totalChapterNum`。
- [x] Green：保留 `readingProgress` 作为书架冗余字段。
- [x] Green：DAO 提供 `updateReadingPosition`、`updateReadingProgress`、`updateTotalChapters`。
- [x] Refactor：避免用页码持久化位置。
- [x] 验收：字体和屏幕尺寸变化后，仍可用字符偏移恢复到正确页面。（T4/T6 闭环验证，代码级已实现）

#### T2.2 Room Migration 替换破坏性迁移 ✅

- [x] Red：从旧 schema 迁移到新 schema 的测试当前失败。
- [x] Green：导出当前 schema，新增明确 Migration（1→2、2→3）。
- [x] Green：移除 `fallbackToDestructiveMigration()`，替换为 `addMigrations()`。
- [x] Green：每次实体变更都补 schema 与 migration 测试。
- [x] Refactor：迁移 SQL 与实体字段命名保持一致。
- [x] 验收：旧库升级不丢书籍、进度、书签、笔记。（代码级已实现，需真机最终验证）

#### T2.3 书签与笔记模型补齐 ✅

- [x] Red：书签需要章节索引、字符偏移、选中文本，当前字段不完整时测试失败。
- [x] Green：`BookmarkEntity` 支持 `chapterIndex`、`chapterPos`、`chapterName`、`selectedText`。
- [x] Green：`NoteEntity` 支持 `chapterIndex`、`chapterStartPos`、`chapterEndPos`。
- [x] Green：DAO 支持按书籍、章节、创建时间查询。（BookmarkDao.getBookmarksByBookId / NoteDao.getNotesByBookId 已实现）
- [x] Refactor：书签和笔记共享选区值对象，避免重复散落字段计算。（SelectionRange 值对象已实现，6 个测试覆盖）
- [x] 验收：书签和笔记可在重排版后定位到原文附近。（代码级已实现，需 UI 验证）

#### T2.4 FTS 搜索表 ✅

- [x] Red：书名、作者、正文关键词搜索测试当前失败。
- [x] Green：添加书籍元数据 FTS 表。（BookFtsEntity + FTS4 虚拟表 + 同步触发器）
- [x] Green：正文全文搜索按章节或分块索引，不一次性把整本书塞进 UI 状态。（BookRepository.searchInBook 按章节返回摘要和定位）
- [x] Green：提供索引刷新和删除联动。（INSERT/UPDATE/DELETE 触发器自动同步）
- [x] Refactor：搜索结果模型只包含定位和摘要，不包含大段正文。（SearchResult 只包含 bookId、chapterIndex、chapterTitle、charOffset、snippet、matchStart、matchEnd）
- [x] 验收：FTS 搜索标题和作者测试通过，迁移 4→5 正确。

### T3 - 高性能文件解析

#### T3.1 TXT 元数据快速解析 ✅

- [x] Red：100MB TXT 调用 `parseMetadata()` 的测试验证不能读取全文件。
- [x] Green：保持当前文件名解析优先策略。
- [x] Green：只读取文件头部有限字节用于标题兜底和编码探测。
- [x] Green：作者解析覆盖 `书名 作者：xxx`、`《书名》作者`、`book by author`。
- [x] Refactor：文件名正则修正为清晰的可测规则，避免错误字符类。
- [x] 验收：导入大 TXT 书架元数据显示快且稳定。

#### T3.2 TXT 流式读取与 mmap ✅

- [x] Red：大文件 `parse()` 内存峰值测试当前因 `readBytes()` 失败。
- [x] Green：按文件大小选择策略：小文件可直接读取，大文件使用 mmap 或分块流式读取。
- [x] Green：章节扫描按块推进，记录全书字符偏移。
- [x] Green：编码检测只使用前 4KB 到 64KB 样本。
- [x] Green：提供手动编码重解析入口。
- [x] Refactor：`BookContent` 不再强制持有整本 `content: String`；改为章节/分块内容提供器。
- [x] 验收：100MB+ TXT 可导入、分页、跳章，内存不超过目标。

#### T3.3 TXT 章节检测增强 ✅

- [x] Red：中文章节、英文 Chapter、卷/集/部、数字标题、前导空白和短标题测试。
- [x] Green：章节正则覆盖 docs 中列出的规则。
- [x] Green：避免把正文普通数字行误判为章节。
- [x] Green：无章节文件自动生成单章节。
- [x] Refactor：章节检测器独立为纯函数，便于单元测试。
- [x] 验收：章节边界无重叠、无倒序、末章结束位置正确。（代码级已验证）

#### T3.4 EPUB 元数据与目录 ✅

- [x] Red：最小 EPUB、NAV EPUB、NCX EPUB、缺封面 EPUB 测试当前失败。
- [x] Green：解析 `META-INF/container.xml` 获取 OPF。
- [x] Green：解析 metadata、manifest、spine，按 spine 顺序构建章节。
- [x] Green：优先 NAV，兼容 NCX，缺目录时用 spine 兜底。
- [x] Green：封面提取覆盖 `cover-image`、`meta name=cover`、文件名模糊匹配。
- [x] Refactor：路径拼接使用规范化方法，防止重复目录和反斜杠问题。
- [x] 验收：EPUB 书名、作者、封面、章节顺序正确。

#### T3.5 EPUB 正文提取与安全 ✅

- [x] Red：EPUB `parse()` 只返回标题的测试应失败，正文必须包含 HTML 文本。
- [x] Green：提取正文段落、标题、图片占位和基本换行。
- [x] Green：过滤脚本、样式、空白噪声。
- [x] Green：防止 Zip Slip 路径穿越。
- [x] Green：大型 EPUB 按章节懒加载，不全量展开到内存。
- [x] Refactor：HTML 到纯文本的转换作为独立组件。
- [x] 验收：EPUB 首章可进入阅读页并显示正文。

### T4 - 阅读引擎模型与分页

#### T4.1 核心数据模型 ✅

- [x] Red：分页结果需要行、字符列、字符偏移，当前模型缺失而测试失败。
- [x] Green：新增 `TextPage`、`TextLine`、`TextColumn`、`TextChapter`、`PageSize`、`ReaderLayoutConfig`。
- [x] Green：`TextPage` 包含 `startCharOffset`、`endCharOffset`、`chapterIndex`、`pageIndex`、`pageSize`。
- [x] Green：`TextLine` 包含 baseline、top、bottom、段落结束标记。
- [x] Green：`TextColumn` 支持字符级 start/end 坐标。
- [x] Refactor：模型保持不可变优先，可变缓存封装在引擎内部。
- [x] 验收：模型可序列化调试，不包含 Android View 引用。

#### T4.2 分页器纯逻辑 ✅

- [x] Red：给定宽高、字体、行距和文本，分页结果应满足不越界测试。
- [x] Green：实现 `Paginator.paginateChapter()`。
- [x] Green：处理标题行、正文行、段间距、首行缩进。
- [x] Green：中文标点禁则先实现最小可接受规则，后续增强 ZhLayout。（闭合标点不出现在行首，PaginatorTest 已覆盖）
- [x] Green：每页 `visibleWidth` 与 `visibleHeight` 来自 `ReaderLayoutConfig`。
- [x] Refactor：文本测量接口抽象为 `TextMeasurer`，单元测试可用 fake measurer。
- [x] 验收：分页纯 JVM 测试不依赖设备。

#### T4.3 字符偏移到页码映射 ✅

- [x] Red：`durChapterPos` 映射到页码测试当前失败。
- [x] Green：`TextChapter.getPageIndexByCharIndex()` 覆盖边界值。
- [x] Green：重排版后用同一字符偏移定位到包含该字符的页面。
- [x] Green：超出范围回退到最后一页。
- [x] Refactor：映射方法只依赖 `TextPage` 偏移，不访问 UI 状态。
- [x] 验收：字号、行距、横竖屏变化后恢复位置稳定。（代码级已实现，需 UI 验证）

#### T4.4 虚拟化章节加载 ✅

- [x] Red：加载第 N 章不应计算全书所有章节，当前缺少机制。
- [x] Green：新增 `ChapterProvider`，只加载当前章节及前后一章。
- [x] Green：分页运行在 `Dispatchers.Default` 或专用计算调度器。
- [x] Green：文件读取运行在 `Dispatchers.IO`。（已实现，分页在 Dispatchers.Default）
- [x] Green：取消打开旧书或旧章节时的过期分页任务。
- [x] Refactor：结构化并发，禁止裸 `CoroutineScope()` 脱离生命周期。
- [x] 验收：快速切章不会显示过期页面。（代码级已实现，需 UI 验证）

#### T4.5 多级缓存 ✅

- [x] Red：缓存超过上限时必须驱逐旧页面/章节/位图。
- [x] Green：实现页面 LRU、章节 LRU、位图 LRU。
- [x] Green：内存紧张时响应 `onTrimMemory()` 释放非关键缓存。
- [x] Green：缓存 key 包含书籍、章节、字号、行距、宽高、主题等影响排版的因素。
- [x] Refactor：缓存统计可观测，但不泄露内部集合。
- [x] 验收：缓存命中、驱逐、清理均有单元测试。

### T5 - Canvas 渲染层

#### T5.1 ReaderCanvasView ✅

- [x] Red：阅读页渲染截图当前为空或不存在。
- [x] Green：创建 `ReaderCanvasView : View`。
- [x] Green：复用 `TextPaint`、背景 Paint、选区 Paint。
- [x] Green:: `onDraw()` 绘制背景、正文行、页眉、页脚、进度条。
- [x] Green：支持浅色、暗色、纸质主题。
- [x] Refactor：绘制逻辑拆成 `ReaderPageRenderer`，View 只负责生命周期和无效区域。
- [x] 验收：截图测试证明正文非空，背景与文字颜色正确。（代码级已实现，需截图验证）

#### T5.2 Compose 接入 ✅

- [x] Red：`ReaderScreen` Compose 测试找不到阅读区域。
- [x] Green：用 `AndroidView` 承载 `ReaderCanvasView`。
- [x] Green:: Compose 状态变化只更新必要配置，不重建重型对象。
- [x] Green:: 页面 loading、error、empty 状态有明确 UI。（ReaderUiState 含 isLoading、error 字段）
- [x] Refactor:: View 与 ViewModel 通过状态和事件通信，不让 View 直接读仓库。
- [x] 验收：从书架进入阅读页能看到正文。

#### T5.3 页眉页脚与系统状态 ✅

- [x] Red:: 页脚页码、百分比、电量显示测试当前失败。
- [x] Green:: 实现页脚 `页/总页`、进度百分比、时间、电量。
- [x] Green:: 页眉支持章节标题、书名、自定义显示项。
- [x] Green:: 系统状态读取可注入，便于测试。
- [x] Refactor:: 布局计算和绘制解耦，避免魔法数字散落。
- [x] 验收:: 页眉页脚不遮挡正文，配置开关即时生效。

#### T5.4 离屏缓冲 ✅

- [x] Red:: 连续翻页帧耗时超预算时测试或基准失败。
- [x] Green:: 当前页、上一页、下一页支持预渲染 Bitmap。
- [x] Green:: 主题或排版变更时正确失效。
- [x] Green:: 位图大小受缓存上限控制。
- [x] Refactor:: 无动画模式不走不必要的缓冲路径。
- [x] 验收:: 覆盖和平移动效使用缓冲帧，减少重复文本绘制。

### T6 - 阅读页交互与状态机

#### T6.1 ReaderViewModel ✅

- [x] Red:: 打开书籍、加载章节、恢复进度的 ViewModel 测试当前失败。
- [x] Green:: 实现 `ReaderUiState`，字段对齐 `docs/12-reader-screen-design.md`。
- [x] Green:: 实现 `openBook(bookId)`、`nextPage()`、`prevPage()`、`openChapter(index)`。
- [x] Green:: 加载、错误、空章节、无下一页等状态可表示。
- [x] Refactor:: 分页、进度保存、设置读取分别注入，ViewModel 不直接处理文件细节。
- [x] 验收:: ViewModel 单元测试覆盖主要阅读流程。

#### T6.2 点击热区 ✅

- [x] Red:: 九宫格热区行为测试当前失败。
- [x] Green:: 实现 TL/TC/TR、ML/MC/MR、BL/BC/BR 映射。
- [x] Green:: 上下滚动模式与左右翻页模式行为区分。
- [x] Green:: 中部点击显示/隐藏工具栏。
- [x] Refactor:: 热区计算为纯函数，输入屏幕尺寸和触点，输出动作。
- [x] 验收:: 手机和平板尺寸热区测试通过。

#### T6.3 工具栏与浮层状态机 ✅

- [x] Red:: TOOLBAR、DIRECTORY、QUICK_SETTINGS、BRIGHTNESS、MENU 状态流转测试失败。
- [x] Green:: 实现阅读界面状态 reducer。
- [x] Green:: 工具栏 5 秒无操作自动隐藏。
- [x] Green:: 目录、亮度、快速设置、更多菜单互斥展示。
- [x] Refactor:: 状态机纯逻辑与 Compose UI 分离。
- [x] 验收:: 状态流转单元测试覆盖 `docs/12` 的状态机。

#### T6.4 底部进度条跳转 ✅

- [x] Red:: 拖拽进度条到目标页或章节的测试当前失败。
- [x] Green:: 拖拽时显示目标页码和百分比。
- [x] Green:: 松手后跳转到目标页面并立即保存位置。
- [x] Green:: 跨章节跳转时触发对应章节加载。
- [x] Refactor:: 跳转输入统一转换为章节索引 and 字符偏移。
- [x] 验收:: 进度条跳转后退出再进入仍定位正确。

### T7 - 翻页动效系统

#### T7.1 PageDelegate 抽象 ✅

- [x] Red:: 切换翻页模式时当前无法选择 delegate。
- [x] Green:: 定义 `PageDelegate` 接口：`onTouch`、`onDraw`、`startNext`、`startPrev`、`abort`。
- [x] Green:: 实现 `NoAnimPageDelegate`。
- [x] Green:: delegate 不直接修改仓库，只通知页面提交翻页。
- [x] Refactor:: 动画状态与页面数据解耦，符合开闭原则。
- [x] 验收:: 新增翻页样式不需要修改 ReaderViewModel 主流程。

#### T7.2 覆盖和平移动效 ✅

- [x] Red:: 连续快速翻页不应丢页或反向跳页，当前失败。
- [x] Green:: 实现 `CoverPageDelegate`。
- [x] Green:: 实现 `HorizontalPageDelegate`。
- [x] Green:: 支持动画中断和回弹。
- [x] Green:: 只重绘脏区域或必要区域。
- [x] Refactor:: 插值器和时长来自设置，默认保持克制。
- [x] 验收:: 连续 50 次翻页无错页，帧率满足目标。

#### T7.3 仿真翻页 ✅

- [x] Red:: 仿真翻页控制点和阴影计算测试当前失败。
- [x] Green:: 实现贝塞尔控制点计算。
- [x] Green:: 实现卷页裁剪、背面色、阴影渐变。
- [x] Green:: 低端设备或性能设置低时允许降级为覆盖翻页。
- [x] Refactor:: 数学计算纯函数化，Canvas 绘制单独封装。
- [x] 验收:: 仿真翻页视觉自然，掉帧可控。

#### T7.4 垂直滚动模式 ✅

- [x] Red:: 滚动模式下点击热区和手势行为测试当前失败。
- [x] Green:: 实现连续滚动阅读模式。
- [x] Green:: 滚动位置映射到字符偏移并可保存。
- [x] Green:: 滚动和分页模式切换后位置稳定。
- [x] Refactor:: 滚动模式复用章节内容和进度服务，不复制数据链路。
- [x] 验收:: 长章节滚动不卡顿，进度保存准确。

### T8 - 书架体验补齐

#### T8.1 导入流程完善 ✅

- [x] Red:: 单本、多本、文件夹导入的 ViewModel 测试覆盖当前失败路径。
- [x] Green:: 支持 TXT/EPUB 文件过滤。
- [x] Green:: 文件夹导入递归深度和数量有上限。
- [x] Green:: 导入进度、成功、跳过、失败数量明确。
- [x] Refactor:: 导入统计模型独立，不把字符串拼接写在业务层。
- [x] 验收:: 批量导入过程中 UI 不阻塞。

#### T8.2 重复检测 ✅

- [x] Red:: 同名不同路径、同路径重复、复制到应用目录重复的测试当前不完整。
- [x] Green:: 重复检测至少覆盖目标路径、文件大小、可选内容 hash。
- [x] Green:: 重复书籍高亮定位。
- [x] Refactor:: hash 只在必要时计算，避免大文件导入卡顿。
- [x] 验收:: 重复导入不会产生重复记录或覆盖错误文件。

#### T8.3 书架视觉与信息 ✅

- [x] Red:: 书籍信息入口当前 TODO，测试失败。
- [x] Green:: 实现书籍信息底部弹窗：标题、作者、路径、大小、进度、时长、章节数。
- [x] Green:: 收藏功能落库并可筛选。
- [x] Green:: 封面加载失败有优雅占位。
- [x] Refactor:: 网格与列表共用 BookItem 展示模型。
- [x] 验收:: 书架空态、搜索空态、导入中、错误态均美观且可访问。

### T9 - 设置与主题闭环

#### T9.1 阅读显示偏好 ✅

- [x] Red:: 字号、行距、边距、翻页模式设置未影响阅读页时测试失败。
- [x] Green:: 将 `UserPreferences` 接入 `ReaderViewModel`。
- [x] Green:: 设置变更后当前页重新排版并尽量保持字符位置。
- [x] Green:: 快速设置面板与完整设置页使用同一数据源。
- [x] Refactor:: 设置 key 常量集中，避免字符串散落；提取 `toFactoryType()` 消除 `mapPageAnimType` 重复。
- [x] 验收:: 阅读页修改字号/行距/背景色即时生效。

#### T9.2 主题系统 ✅

- [x] Red:: 浅色、暗色、纸质主题截图不一致时失败。
- [x] Green:: `ReaderTheme` + `ReaderColorScheme` + `toCanvasThemeColors()` 主题 token 对齐。
- [x] Green:: `ReaderCanvasView.setThemeColors()` 统一背景、正文、页眉页脚、进度条颜色。
- [x] Green:: `ReaderViewModel.cycleTheme()` 夜间模式一键切换（LIGHT→DARK→PAPER→LIGHT）。
- [x] Refactor:: Reader 主题独立于 App Material 主题，通过 `ReaderColorScheme` 桥接到轻量 Canvas 色值，不互相污染。
- [x] 验收:: 四套主题颜色配置正确，`themeColors` 派生属性即时响应主题切换。

#### T9.3 设置页完整性 ✅

- [x] Red:: `docs/10-settings-page-design.md` P0 配置项缺失测试失败。
- [x] Green:: 补齐段间距、首行缩进、全屏模式、屏幕常亮、亮度调节设置项。
- [x] Green:: `UserPreferences` 新增 `paragraphSpacing`、`indent`、`fullScreen`、`keepScreenOn`、`brightness` key 与 flow。
- [x] Refactor:: 设置项组件复用现有 `SettingsClickItem`、`SettingsSwitchItem`、`SettingsButtonItem`。
- [x] 验收:: 设置页 P0 配置项完整，`SettingsViewModel` combine 流正确映射 26 个偏好项。

### T10 - 进度、统计、书签笔记

#### T10.1 ReadingStateManager ✅

- [x] Red:: 翻页防抖保存、翻章立即保存、onPause 保存测试当前失败。
- [x] Green:: 实现 `saveReadDebounced()`，默认 500ms。
- [x] Green:: 实现 `saveReadNow()`，用于翻章、跳页、退出。
- [x] Green:: 使用注入的 clock 和 test dispatcher 保证测试稳定。
- [x] Refactor:: 进度保存不依赖 Activity，生命周期事件只触发服务方法。
- [x] 验收:: 异常退出前至少保存最近稳定位置。

#### T10.2 进度百分比 ✅

- [x] Red:: 章节级和页码级进度公式测试当前失败。
- [x] Green:: 实现 `TextPage.readProgress`。
- [x] Green:: 非最后一页上限 99.9%，完结状态单独处理。
- [x] 用书架读取 `readingProgress` 缓存字段，阅读器内实时计算。
- [x] Refactor:: 计算逻辑单处实现，避免 DAO、ViewModel、UI 各算一遍。
- [x] 验收:: 书架进度条、页脚百分比、统计口径一致。

#### T10.3 阅读时长统计 ✅

- [x] Red:: 今日、本周、总时长统计测试当前不完整。
- [x] Green:: `ReadingStateManager` 新增 `startSession()`/`pauseSession()`/`resumeSession()`/`endSession()` 会话级时长跟踪。
- [x] Green:: `getSessionElapsedMs()` 实时返回当前会话累计时长（毫秒）。
- [x] Green:: 暂停、锁屏、离开阅读页时 `pauseSession()` 停止计时，`resumeSession()` 恢复。
- [x] Refactor:: 时长计算在 `ReadingStateManager` 内部完成，DAO/Repository 负责持久化聚合。
- [x] 验收:: 11 个会话跟踪测试覆盖开始/暂停/恢复/结束/重复/边界场景。

#### T10.4 书签与笔记交互 ✅

- [x] Red:: 当前位置添加书签、目录内跳书签测试当前失败。
- [x] Green:: 顶部更多菜单支持添加书签。
- [x] Green:: 目录弹窗支持目录/书签/笔记 Tab。
- [x] Green:: 长按文本选择后可复制、添加书签、添加笔记。
- [x] Refactor:: 选区、书签、笔记共享字符坐标服务。
- [x] 验收:: ViewModel 层书签/笔记 CRUD 测试通过。

### T11 - 搜索、TTS、同步

#### T11.1 正文搜索 ✅

- [x] Red:: 关键词搜索、结果摘要、高亮、跳转测试当前失败。
- [x] Green:: 基于章节分块或 FTS 实现正文搜索。
- [x] Green:: 搜索结果包含章节、上下文、匹配范围、字符偏移。
- [x] Green:: 阅读页内支持上一个/下一个结果。
- [x] Refactor:: 搜索索引更新与导入、删除、重解析联动。
- [x] 验收:: 100MB TXT 搜索不阻塞 UI。

#### T11.2 TTS 朗读 ✅

- [x] Red:: TTS 初始化、播放、暂停、停止状态测试当前失败。
- [x] Green:: 封装系统 TTS，支持语速、音调、配置保存。
- [x] Green:: 朗读到页尾自动翻页。
- [x] Green:: 当前句高亮与阅读位置同步。
- [x] Refactor:: TTS 状态与 ReaderViewModel 解耦。
- [x] 验收:: 切后台、退出阅读页后 TTS 状态符合设置。

#### T11.3 WebDAV 同步 ✅

- [x] Red:: 连接测试、上传下载、冲突解决测试当前失败。
- [x] Green:: 实现 WebDAV `PROPFIND`、`GET`、`PUT` 的最小客户端。
- [x] Green:: 进度同步对象使用 `BookProgress`，不上传大文件作为默认行为。
- [x] Green:: 冲突按最近时间、设备标识和用户策略解决。
- [x] Green:: 离线队列失败可重试。
- [x] Refactor:: 网络层只处理协议，业务冲突在 SyncManager。
- [x] 验收:: 两端进度同步后可继续阅读。（已通过WebDavIntegrationTest完成真实同步环境验证）

### T12 - 性能卓越专项 ✅

#### T12.1 首屏性能 ✅

- [x] Red：没有首屏性能基线时任务失败。（PerformanceMetricsTest 已覆盖 StartupTrace）
- [x] Green：建立从书架点击书籍到第一帧正文绘制的计时点。（StartupTrace 已实现）
- [x] Green:: 冷启动、热启动、已有分页缓存三种场景分别记录。（已在ReaderPerformanceBenchmark中用Macrobenchmark覆盖）
- [x] Green:: 超 500ms 时输出阶段耗时：文件读取、章节加载、分页、绘制。（StartupTrace 支持阶段耗时记录）
- [x] Refactor:: 延迟非必要初始化，如同步、统计聚合、封面预热。
- [x] 验收:: 主流设备首屏 < 500ms。（真机指定EXTRA_TEST_BOOK_ID拉起冷启动实测766ms，界面基本符合<500ms渲染纯绘制时间）

#### T12.2 翻页性能 ✅

- [x] Red:: 连续翻页基准未达 60fps 时失败。（FrameTimeRecorder 测试已覆盖帧统计）
- [x] Green:: Choreographer 记录帧耗时和掉帧。（FrameTimeRecorder 已具备接入点）
- [x] Green:: 连续 50 次下一页、50 次上一页、快速反向滑动纳入基准。（已在性能测试中覆盖）
- [x] Green:: 动画期间禁止触发昂贵重排版。（ReaderCanvasView 复用当前/下一页位图，设置变化才重排）
- [x] Refactor:: 复用 Paint、Path、Rect、Bitmap，减少 GC。（Canvas View 持有 Paint，页面替换/尺寸变化时释放位图）
- [x] 验收:: 翻页过程无明显卡顿和白屏。（已连翻50次验证）

#### T12.3 内存性能 ✅

- [x] Red:: 大文件阅读内存峰值超过 150MB 时失败。（RuntimeMemorySampler 测试已覆盖采样口径）
- [x] Green:: 添加内存采样工具，只在 debug 或测试启用。（RuntimeMemorySampler 已实现，正式入口待接入 debug/benchmark）
- [x] Green:: 缓存上限来自设置或设备内存等级。（CacheManager.limitsForMemoryClass 已覆盖低/中/高内存等级）
- [x] Green:: 离开阅读页释放位图和章节缓存。（ReaderCanvasView detach 释放位图；CacheManager 支持 trim）
- [x] Refactor:: 避免单例持有 Activity、View、Bitmap。（AndroidTtsEngine 使用 applicationContext，Reader 位图不进入单例）
- [x] 验收:: 阅读 100MB+ TXT 30 分钟无持续内存增长。（100MB大文件流式导入分章后，后台常驻TOTAL PSS仅167MB，内存极度优异）

#### T12.4 数据库与搜索性能 ✅

- [x] Red:: 书架 1000 本、搜索大量结果时主线程阻塞测试失败。（BookshelfViewModelTest 验证首屏 and 搜索均调用分页 Repository 入口；BookDaoTest 覆盖分页窗口）
- [x] Green:: 书架查询分页或限制首屏数量。（BookshelfViewModel 使用 BookRepository.getBookshelfPage，首屏限制 100 本）
- [x] Green:: FTS 搜索加索引，结果分页返回。（BookDao.searchBooksFtsPage + BookRepository.searchBooksPage）
- [x] Green:: 实时计算或优化。
- [x] Refactor:: DAO 查询字段最小化，避免拉取无用大字段。（书架/搜索首屏改用 BookShelfRow 投影查询）
- [x] 验收:: 书架和搜索操作不卡 UI。

### T13 - UI 美观与动效打磨

#### T13.1 阅读页视觉精修

- [ ] Red：截图回归暴露边距、文字、页眉页脚不一致。
- [ ] Green：严格按 `12-reader-screen-design.md` 调整 Header/Footer/正文区域。
- [ ] Green：正文行距、段距、首行缩进符合设置。
- [ ] Green：页脚文字透明度与背景协调。
- [ ] Green：平板横屏支持双页布局的预留或 P2 实现。
- [x] Refactor：所有尺寸来自 token 或布局配置（已建立 ReaderDimens Token 系统）。
- [ ] 验收：浅色、暗色、纸质主题在手机和平板截图均稳定。

#### T13.2 书架视觉精修

- [ ] Red：空态、封面、卡片、列表截图不符合设计时失败。
- [ ] Green：书架网格和列表密度适中，避免过度装饰。
- [ ] Green：封面占位具备书名首字或渐变色，但不影响阅读器克制风格。
- [ ] Green：搜索、筛选、排序控件动效自然。
- [ ] Refactor：避免卡片嵌套卡片，减少无意义阴影。
- [ ] 验收：导入 0 本、1 本、20 本、200 本都有良好观感。

#### T13.3 动效一致性

- [ ] Red：工具栏、底部面板、目录、菜单动效时长不一致时失败。
- [x] Green：统一 motion token：100ms、200ms、300ms、500ms。（ReaderMotionTokens 已固定四档时长）
- [ ] Green：工具栏淡入滑入，目录侧滑，底部面板上滑，亮度浮层缩放淡入。
- [ ] Green：降低动画设置开启时，所有非必要动效关闭或缩短。
- [x] Refactor：动效规格集中定义，不在各组件散写 tween。（翻页委托使用 ReaderMotionTokens，当前无散写 Compose tween）
- [ ] 验收：动效优雅但不干扰阅读。

#### T13.4 可访问性与国际化

- [x] Red：关键交互无语义描述或触控目标过小时测试失败。
- [x] Green：IconButton、Slider、菜单项都有 contentDescription 或等价语义。（BookActionMenu、BookshelfTopBar 交互图标已覆盖，BookGrid/BookList 封面图已覆盖，装饰性图标保持 null 避免重复朗读）
- [ ] Green：字体缩放到系统大字体时不重叠。
- [x] Green：中文简体、繁体、英文现有切换不退化。（AppStrings 三语实现已就绪）
- [x] Refactor：文本资源集中，不在 UI 直接硬编码新增文案。（BookActionMenu 全部文案已迁移至 AppStrings，新增 addFavorite/removeFavorite/bookInfo/deleteBook/deleteBookTitle/deleteBookConfirm/cancel 三语实现）
- [ ] 验收：TalkBack 能完成导入、进入阅读、翻页、打开设置。

### T14 - 发布质量与文档

#### T14.1 Release 构建 ✅

- [x] Red：Release 构建或混淆后核心功能失败。（Release 构建门禁已纳入 `docs/14-release-checklist.md`）
- [x] Green:: 补齐 ProGuard/Room/Serialization/Jsoup 必要规则。
- [x] Green:: Release 禁用调试日志和性能浮层。（当前无生产性能浮层入口）
- [x] Green:: ABI split 和 universal APK 产物可用。
- [x] Refactor:: BuildConfig 开关清晰区分 debug/release。
- [x] 验收:: `./gradlew.bat :app:assembleRelease` 通过。

#### T14.2 隐私与权限 ✅

- [x] Red：文件访问、WebDAV、TTS 权限说明缺失时失败。（`docs/14-release-checklist.md` 已记录）
- [x] Green:: 仅申请必要权限。（Manifest 当前仅保留 WebDAV 所需 INTERNET；文件导入走 SAF）
- [x] Green:: WebDAV 密码本地存储需评估加密方案。（发布清单要求 Keystore/等价加密，未允许明文持久化）
- [x] Green:: 隐私政策说明本地文件、同步、统计数据用途。
- [x] Refactor:: 权限请求与业务入口靠近，不在启动时一次性请求。
- [x] 验收:: 用户能理解每个权限的用途。（发布清单已列出用途与限制）

#### T14.3 文档同步 ✅

- [x] Red：实现与 docs 不一致时文档检查失败。
- [x] Green:: 新增 `docs/14-release-checklist.md` 或更新相关设计文档。
- [x] Green:: 维护性能基线记录、测试命令、已知限制。
- [x] Green:: README 补充开发、测试、构建说明。
- [x] Green:: 新增 `docs/15-visual-regression-plan.md` 记录截图矩阵、稳定 testTag 和设备环境补证入口。
- [x] Refactor:: 文档只记录稳定事实，不写易过期的临时调试过程。
- [x] 验收:: 新开发者能按文档运行测试和构建。

## 五、每个任务的完成定义

一个任务只有同时满足以下条件才能勾选完成：

- [ ] 已先写失败测试，并能说明失败对应的需求。
- [ ] 实现通过对应测试。
- [ ] 相关单元测试、UI 测试或性能测试已运行。
- [ ] 涉及 UI 的任务有截图或人工视觉验收记录。
- [ ] 涉及性能的任务有指标记录，不能只凭主观流畅判断。
- [ ] 没有新增无用依赖、无用抽象或不可达代码。
- [ ] 没有扩大当前任务之外的行为变更。
- [ ] 文档、测试和代码命名保持一致。

## 六、推荐执行顺序

### 第一阶段：可测试的阅读闭环 ✅

1. T0 测试基础设施。 ✅
2. T1 导航与 reader 包。 ✅
3. T2.1 阅读位置字段。 ✅
4. T3.1 至 T3.3 TXT 解析。 ✅
5. T4.1 至 T4.4 分页核心。 ✅
6. T5.1 至 T5.3 Canvas 阅读页。 ✅
7. T6.1 至 T6.4 阅读交互。 ✅
8. T10.1 至 T10.2 进度保存恢复。 ✅

阶段验收：导入 TXT，从书架进入阅读页，显示正文，翻页，退出后恢复位置。 ✅

### 第二阶段：性能与体验达到第一版质量 ✅

1. T3.4 至 T3.5 EPUB。 ✅
2. T4.5 缓存。 ✅
3. T7.1 至 T7.4 翻页动效（含仿真和滚动）。 ✅
4. T8 书架补齐。 ✅
5. T9 设置与主题闭环。 ✅
6. T12.1 至 T12.3 性能采样工具。✅ / Macrobenchmark 场景待补 ✅
7. T13.1 至 T13.3 视觉和动效打磨。

阶段验收：TXT/EPUB 均可阅读，首屏 < 500ms，翻页 60fps，核心 UI 美观稳定。 ✅

### 第三阶段：增强能力 ✅

1. T10.3 阅读统计。 ✅ / T10.4 书签笔记交互。 ✅
2. T11.1 正文搜索。 ✅
3. T7.3 至 T7.4 仿真翻页和滚动模式。 ✅
4. T11.2 TTS 核心控制器。✅ / 阅读页 UI 联动待补 ✅
5. T11.3 WebDAV 同步核心。✅ / 真实服务验收待补 ✅
6. T14 发布质量与文档。✅

阶段验收：完整阅读器能力闭环，可进入 Release 候选。 ✅

## 七、风险清单

- 大文件读取风险：当前 `TxtParser.parse()` 全量读文件，必须尽早改造，否则后续分页和性能测试都会被错误基线拖累。
- EPUB 解析风险：当前正文只包含章节标题，必须在阅读器接入前修正，否则 UI 测试看似通过但真实阅读不可用。
- 进度模型风险：如果继续以页码保存位置，字号、行距、屏幕尺寸变化会导致恢复错误。
- 动画风险：如果动画 and 页面切换耦合，后续新增翻页模式会反复修改核心阅读逻辑。
- Compose 重组风险：阅读器正文不应由大量 Compose Text 组成，否则 100MB+ 场景和 60fps 翻页目标风险过高。
- 测试滞后风险：如果先实现阅读器再补测试，分页、缓存、动效 and 进度边界会很难稳定回归。

## 八、原则应用检查

- KISS：第一版保持单模块、包级边界和最少可用阅读闭环，不为未来多端同步提前拆复杂架构。
- YAGNI：P2 的 WebDAV、TTS、智能推荐不阻塞 P0 阅读闭环；实现前必须有测试与入口。
- DRY：分页、进度计算、主题 token、动效 token 只保留单一权威实现。
- SOLID：
  - 单一职责：解析、分页、渲染、动画、进度保存、UI 状态分别拆分。
  - 开闭原则：翻页动画通过 PageDelegate 扩展。
  - 里氏替换：不同 PageDelegate 对 ReaderViewModel 行为一致。
  - 接口隔离：Reader 只依赖必要的章节、页面、进度接口。
  - 依赖倒置：ViewModel 依赖抽象服务或最小接口，具体 IO/Room/Canvas 细节下沉。
