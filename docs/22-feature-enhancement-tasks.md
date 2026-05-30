# 22 - 功能增强与阅读 UI 优化 TDD 任务清单

> 基于 `analysis_results.md`（功能完善与新功能建议）和 `reader_ui_analysis.md`（阅读界面 UI/功能/配置项建议）编制。
> 所有建议均已纳入，按优先级分为 P0（核心补全）、P1（功能完善）、P2（差异化）、P3（格式扩展）四个阶段。
> WebDAV 同步（T-01~T-41）已在 CLAUDE.md 中独立规划，本文档不重复，仅在依赖处标注。

## 一、设计原则

1. **纯离线**：除 WebDAV 同步外不做任何网络请求。
2. **纯统计**：阅读统计仅呈现客观数据，不做打卡/目标/成就/游戏化。
3. **TDD 流程**：Red → Green → Refactor → Measure → Review。
4. 保持单模块，按包组织边界。
5. 新增 UI 文案必须同步 `AppStrings`（简体/繁体/英文三语）。

## 二、任务总览

| 阶段 | 编号 | 目标 | 优先级 |
|------|------|------|--------|
| P0 | F01 | 架构重构（大文件拆分） | P0 |
| P0 | F02 | 文本选择增强（手势+选区渲染+气泡手柄） | P0 |
| P0 | F03 | TTS 朗读系统增强 | P0 |
| P0 | F04 | 阅读 UI 快速优化（全沉浸/按钮标签/选区跟随） | P0 |
| P1 | F05 | 书签与笔记系统完善 | P1 |
| P1 | F06 | 阅读统计系统 | P1 |
| P1 | F07 | EPUB 解析增强 | P1 |
| P1 | F08 | 阅读主题预设库 | P1 |
| P1 | F09 | 阅读器新增配置项 | P1 |
| P1 | F10 | 底部工具栏与进度条改进 | P1 |
| P1 | F11 | QuickSettingsSheet 4-Tab 重构 | P1 |
| P1 | F12 | 目录面板改进 | P1 |
| P2 | F13 | 触控热区自定义 | P2 |
| P2 | F14 | 离线词典查询 | P2 |
| P2 | F15 | 文本替换/净化规则 | P2 |
| P2 | F16 | 智能目录（章节正则编辑器） | P2 |
| P2 | F17 | 沉浸式阅读增强 | P2 |
| P2 | F18 | 滑杆交互优化 | P2 |
| P2 | F19 | 垂直无尽滚动模式 | P2 |
| P3 | F20 | PDF 基础支持 | P3 |

## 三、详细任务清单

---

### F01 - 架构重构（大文件拆分）

> 来源：analysis_results.md §5 架构健康度、reader_ui_analysis.md §4 源码级优化

#### F01.1 ReaderViewModel 拆分

- [ ] Red：ReaderViewModel 超过 2000 行，新增职责覆盖测试证明耦合导致测试困难。
- [ ] Green：提取 `ReaderContentUseCase`（章节加载/分页/页面管理）。
- [ ] Green：提取 `ReaderNavigationUseCase`（翻页/跳章/跳页/进度恢复）。
- [ ] Green：提取 `ReaderSettingsUseCase`（排版参数/主题/字体/预设管理）。
- [ ] Green：ReaderViewModel 仅保留 UiState 组装和事件分发（~500 行）。
- [ ] Refactor：UseCase 构造注入 Repository/DAO，ViewModel 不直接访问底层。
- [ ] 验收：所有现有 ReaderViewModel 单元测试仍通过，新 UseCase 各自独立可测。

#### F01.2 ReaderScreen 拆分

- [ ] Red：ReaderScreen 840 行，新增组件时定位困难。
- [ ] Green：提取 `ReaderToolbar.kt`（顶部+底部工具栏）。
- [ ] Green：提取 `ReaderSearchBar.kt`（搜索输入栏，已是 private fun）。
- [ ] Green：提取 `ReaderSelectionBar.kt`（选区操作栏）。
- [ ] Green：提取 `ReaderLoadingError.kt`（加载/错误状态）。
- [ ] Green：ReaderScreen.kt 仅保留骨架（~200 行）。
- [ ] 验收：编译通过，UI 行为无变化。

#### F01.3 QuickSettingsSheet 拆分

- [ ] Red：QuickSettingsSheet 1122 行，Tab 面板混杂在同一文件。
- [ ] Green：提取 `LayoutPanel.kt`。
- [ ] Green：提取 `StylePanel.kt`。
- [ ] Green：提取 `DisplayPanel.kt`（从原 SettingsPanel 拆出视觉相关）。
- [ ] Green：提取 `BehaviorPanel.kt`（从原 SettingsPanel 拆出行为相关）。
- [ ] Green：提取 `ThemeColorRow.kt`、`BrightnessBar.kt` 为独立组件。
- [ ] 验收：编译通过，UI 行为无变化。

#### F01.4 BookRepository 拆分

- [ ] Red：BookRepository 30KB，导入/章节/元数据职责混杂。
- [ ] Green：提取 `ImportRepository`（文件导入/去重/编码检测）。
- [ ] Green：提取 `ChapterRepository`（章节加载/正文读取/分块索引）。
- [ ] Green：提取 `MetadataRepository`（书名/作者/封面/FTS）。
- [ ] Green：BookRepository 保留为 Facade，委托给三个子 Repository。
- [ ] 验收：所有现有 Repository 测试通过。

#### F01.5 AppStrings 拆分

- [ ] Red：AppStrings.kt 55KB 单文件，新增文案时定位困难。
- [ ] Green：按模块拆分为 `BookshelfStrings` / `ReaderStrings` / `SettingsStrings` / `CommonStrings` 接口。
- [ ] Green：`AppStrings` 组合继承各子接口。
- [ ] Green：三语实现（ZhHans/ZhHant/En）同步拆分。
- [ ] 验收：编译通过，现有 LocalAppStrings 用法无需修改。

#### F01.6 ReaderCanvasView 触摸逻辑提取

- [ ] Red：onTouchEvent 分支复杂度高，新增热区自定义会进一步恶化。
- [ ] Green：提取 `ReaderTouchController`（手势识别、热区路由、选区拖拽）。
- [ ] Green：ReaderCanvasView 仅保留渲染职责。
- [ ] Green：TouchController 通过接口回调通知 View。
- [ ] 验收：现有手势行为（边缘翻页、中心工具栏、长按选区）无退化。

#### F01.7 SettingsScreen 拆分

- [ ] Red：SettingsScreen 916 行，设置分区混杂。
- [ ] Green：按 Section 拆分为 `AppearanceSection.kt` / `ReaderSection.kt` / `SyncSection.kt` / `AboutSection.kt`。
- [ ] 验收：编译通过，设置页 UI 无变化。

#### F01.8 ReaderPreferences 语义分组

- [ ] Red：ReaderPreferences 21+ 平铺字段，新增配置项时命名冲突风险高。
- [ ] Green：重构为嵌套分组：`LayoutConfig` / `StyleConfig` / `DisplayConfig` / `BehaviorConfig` / `TtsConfig`。
- [ ] Green：保持 `@Serializable` 向后兼容（DataStore 迁移）。
- [ ] Green：更新所有读写点（ViewModel、QuickSettingsSheet、UserPreferences）。
- [ ] 验收：现有配置持久化/恢复无丢失。

---

### F02 - 文本选择增强

> 来源：analysis_results.md §2.6 文本选择、§2.4 书签笔记的前置依赖

#### F02.1 长按选区手势

- [ ] Red：长按后只能选中整行，无法精确到词/字。
- [ ] Green：实现 Canvas 层 char-level 命中检测（x,y → charIndex）。
- [ ] Green：长按选中当前词（中文按字，英文按单词边界）。
- [ ] Green：选中后显示前后气泡手柄（拖拽可扩展选区）。
- [ ] Refactor：命中检测为纯函数，输入 TextPage + 坐标，输出 charIndex。
- [ ] 验收：中英文混排的精确选区测试通过。

#### F02.2 选区渲染

- [ ] Red：选区背景色未渲染。
- [ ] Green：Canvas 层绘制选区半透明背景色（蓝色，alpha 0.3）。
- [ ] Green：跨行选区正确渲染（首行从起点到行尾，末行从行首到终点）。
- [ ] Green：选区随翻页清除。
- [ ] 验收：多行选区渲染截图正确。

#### F02.3 选区操作栏位置跟随

- [ ] Red：操作栏固定底部，与工具栏重叠。
- [ ] Green：操作栏出现在选区上方（若空间不足则下方）。
- [ ] Green：操作栏位置随选区拖拽实时更新。
- [ ] 验收：选区在页面顶部/中部/底部时操作栏均不被遮挡。

#### F02.4 选区操作栏新增动作

- [ ] Red：仅有复制/书签/笔记三个按钮。
- [ ] Green：新增「分享」按钮（`Intent.ACTION_SEND`，纯离线系统分享面板）。
- [ ] Green：新增「查词」按钮（调用系统 `ACTION_DEFINE`，预留离线词典接口）。
- [ ] Green：三语文案同步更新。
- [ ] 验收：分享/查词功能可用，新按钮有 testTag。

---

### F03 - TTS 朗读系统增强

> 来源：analysis_results.md §2.1

#### F03.1 分句高亮

- [ ] Red：TTS 播放时无句级高亮。
- [ ] Green：TtsController 分句后回调当前句的 charStart/charEnd。
- [ ] Green：ReaderCanvasView 渲染 TTS 高亮区域（接入已有 `ttsHighlightPaint`）。
- [ ] Green：当前句不在可视区时自动滚动/翻页到目标位置。
- [ ] 验收：TTS 播放时可见高亮跟随。

#### F03.2 连续章节朗读

- [ ] Red：章节结束后 TTS 停止。
- [ ] Green：当前章节最后一句结束 → 自动加载下一章 → 继续朗读。
- [ ] Green：到达全书最后一章最后一句时停止。
- [ ] 验收：跨章节朗读流畅无中断。

#### F03.3 后台朗读（Foreground Service + MediaSession）

- [ ] Red：退出阅读页后 TTS 停止。
- [ ] Green：创建 `TtsReadingService : Service()`，绑定 `MediaSession`。
- [ ] Green：实现 Foreground Service + MediaStyle Notification。
- [ ] Green：通知栏提供 播放/暂停/上一章/下一章 控制。
- [ ] Green：锁屏界面显示 MediaSession 控制。
- [ ] Refactor：Service 生命周期与 TtsController 状态同步。
- [ ] 验收：后台/锁屏状态下 TTS 持续朗读，通知栏控制可用。

#### F03.4 语音引擎选择

- [ ] Red：无法选择系统中安装的不同 TTS 引擎。
- [ ] Green：查询 `TextToSpeech.getEngines()` 获取本地已安装引擎列表。
- [ ] Green：在 TTS 设置区域提供引擎选择下拉。
- [ ] Green：切换引擎后重新初始化 `TextToSpeech`。
- [ ] 验收：可切换引擎并正常朗读。

#### F03.5 定时停止

- [ ] Red：无睡前定时器功能。
- [ ] Green：提供定时选项：15分钟 / 30分钟 / 60分钟 / 本章结束后停止。
- [ ] Green：倒计时结束后 `TtsController.stop()`。
- [ ] Green：在 TTS 面板显示剩余时间。
- [ ] 验收：定时停止精度 ±1 秒。

---

### F04 - 阅读 UI 快速优化

> 来源：reader_ui_analysis.md §2（投入产出比最高的改进）

#### F04.1 全沉浸模式

- [ ] Red：阅读页系统状态栏和导航栏始终可见。
- [ ] Green：新增 `fullScreenMode: Boolean` 到 ReaderPreferences。
- [ ] Green：开启时 `WindowInsetsController.hide(statusBars | navigationBars)`。
- [ ] Green：点击中心区域唤出工具栏时临时显示系统栏。
- [ ] Green：在 QuickSettingsSheet 显示区域新增全屏开关。
- [ ] 验收：全屏模式下阅读区域最大化。

#### F04.2 底部按钮增加文字标签

- [ ] Red：底部工具栏 5 个按钮仅有图标，无文字标签。
- [ ] Green：每个 IconButton 下方增加微型 Text 标签（目录/主题/书签/朗读/设置）。
- [ ] Green：标签使用 `labelSmall` 字体，颜色 `textSecondary`。
- [ ] Green：三语文案同步更新。
- [ ] 验收：标签可见且不影响按钮点击区域。

#### F04.3 屏幕方向锁定

- [ ] Red：阅读页方向始终跟随系统。
- [ ] Green：新增 `screenOrientation: ScreenOrientation` 枚举（FOLLOW_SYSTEM / PORTRAIT / LANDSCAPE）。
- [ ] Green：在 ReaderScreen 的 LaunchedEffect 中设置 `activity.requestedOrientation`。
- [ ] Green：在 QuickSettingsSheet 行为面板新增方向选择。
- [ ] 验收：锁定横屏/竖屏后旋转设备不切换。

#### F04.4 工具栏自动隐藏激活

- [ ] Red：`TOOLBAR_AUTO_HIDE_DELAY_MS` 已定义但未实际使用。
- [ ] Green：工具栏显示后启动 5 秒倒计时 Job，超时自动隐藏。
- [ ] Green：用户与工具栏交互时重置倒计时。
- [ ] Green：新增 `toolbarAutoHide: Boolean` 配置项（默认 true）。
- [ ] 验收：5 秒无操作后工具栏自动消失。

#### F04.5 翻页点击触觉反馈

- [ ] Red：边缘点击翻页无触觉反馈。
- [ ] Green：新增 `clickFeedbackVibration: Boolean` 配置项（默认 false）。
- [ ] Green：开启时边缘翻页触发 `HapticFeedbackConstants.CONTEXT_CLICK`。
- [ ] 验收：开启振动后翻页有轻微触觉反馈。

---

### F05 - 书签与笔记系统完善

> 来源：analysis_results.md §2.4

#### F05.1 高亮渲染

- [ ] Red：已保存的高亮笔记在阅读页不可见。
- [ ] Green：加载当前章节的 NoteEntity 列表，计算 byte→char→行坐标映射。
- [ ] Green：Canvas 层使用 `NoteEntity.color` 绘制半透明背景。
- [ ] Green：翻页后正确渲染新页面的高亮。
- [ ] 验收：保存笔记后立即可见高亮。

#### F05.2 多色高亮

- [ ] Red：高亮颜色固定单一。
- [ ] Green：预设 5 种高亮颜色（黄/绿/蓝/粉/紫），定义为 `HighlightColor` 枚举。
- [ ] Green：选区操作栏的「笔记」按钮改为展开色盘快速选色。
- [ ] Green：NoteEntity 的 `color` 字段存储颜色枚举。
- [ ] 验收：不同颜色高亮在页面上正确区分渲染。

#### F05.3 笔记导出

- [ ] Red：无法导出笔记到文件。
- [ ] Green：实现「导出笔记」功能，输出为 Markdown 格式。
- [ ] Green：按章节分组，每条笔记包含选中文本 + 批注 + 时间。
- [ ] Green：通过 SAF `Intent.ACTION_CREATE_DOCUMENT` 选择保存路径。
- [ ] 验收：导出的 Markdown 文件可被其他编辑器正常打开。

---

### F06 - 阅读统计系统

> 来源：analysis_results.md §2.3（纯数据，不做游戏化）

#### F06.1 Session 持久化

- [ ] Red：ReadingStateManager 的 session 时长未写入数据库。
- [ ] Green：新增 `ReadingSessionEntity`（id, bookId, startTime, endTime, durationMs）。
- [ ] Green：Room Migration 新增 `reading_sessions` 表。
- [ ] Green：`ReadingStateManager.endSession()` 时写入 DB。
- [ ] 验收：阅读 session 数据可查询。

#### F06.2 统计聚合查询

- [ ] Red：无按日/周/月聚合阅读时长的 DAO 方法。
- [ ] Green：`ReadingSessionDao.getTodayDuration()` — 今日总时长。
- [ ] Green：`getWeekDuration()` / `getMonthDuration()` / `getTotalDuration()`。
- [ ] Green：`getDurationByBook(bookId)` — 单书累计时长。
- [ ] Green：`getDailyDurations(startDate, endDate)` — 日历热力图数据。
- [ ] Green：`getHourlyDistribution()` — 24 小时时段分布数据。
- [ ] 验收：聚合查询结果正确，边界（跨天/跨周/时区）测试通过。

#### F06.3 统计总览页

- [ ] Red：无阅读统计展示页面。
- [ ] Green：新增 `StatisticsScreen`，展示今日/本周/本月/总计阅读时长。
- [ ] Green：从书架导航入口（已有统计入口按钮）进入。
- [ ] Green：数字使用大字展示，单位（分钟/小时）自动切换。
- [ ] 验收：统计页数据与 session 记录一致。

#### F06.4 日历热力图

- [ ] Red：无阅读热力图。
- [ ] Green：使用 Canvas 自绘 GitHub 贡献图风格的 90 天热力图。
- [ ] Green：颜色深浅映射阅读时长（0 = 空白，>0 按分位数分 4 级）。
- [ ] Green：点击某天显示该天具体时长。
- [ ] 验收：热力图渲染正确，性能无卡顿。

#### F06.5 书籍级统计

- [ ] Red：无单书阅读统计。
- [ ] Green：在书籍信息面板增加：累计阅读时长、阅读速度（字/分钟）、完成进度。
- [ ] Green：阅读速度 = 已读字数 / 累计时长。
- [ ] 验收：数据与 session 记录吻合。

#### F06.6 时段分布图

- [ ] Red：无 24 小时阅读时段分布。
- [ ] Green：Canvas 自绘柱状图，X 轴 0~23 时，Y 轴累计分钟。
- [ ] Green：放置在统计页中。
- [ ] 验收：柱状图数据与 session 记录一致。

---

### F07 - EPUB 解析增强

> 来源：analysis_results.md §2.5

#### F07.1 内嵌图片渲染

- [ ] Red：EPUB 中 `<img>` 标签被忽略。
- [ ] Green：解析 `<img src="...">` 和 `<image>` 标签，记录图片路径到 TextLine。
- [ ] Green：从 ZIP 中提取图片为 Bitmap，Canvas 内联绘制。
- [ ] Green：图片按页面宽度等比缩放，超高图片分页。
- [ ] Refactor：图片缓存纳入 LRU，避免内存溢出。
- [ ] 验收：含图 EPUB 可正确显示图片。

#### F07.2 CSS 基础解析

- [ ] Red：EPUB 的 `text-align`/`font-weight`/`font-style` 被忽略。
- [ ] Green：解析内联 style 属性和外部 CSS 文件的基础属性。
- [ ] Green：映射到 TextLine 的渲染属性（对齐/加粗/斜体）。
- [ ] 验收：加粗/居中段落正确渲染。

#### F07.3 多级目录

- [ ] Red：NCX/nav 的嵌套目录显示为平铺。
- [ ] Green：解析目录层级关系，ChapterInfo 增加 `level: Int` 字段。
- [ ] Green：目录列表按层级缩进渲染。
- [ ] 验收：三级嵌套目录正确显示。

#### F07.4 注脚弹窗

- [ ] Red：点击脚注引用无反应。
- [ ] Green：识别 EPUB 脚注链接（`<a epub:type="noteref">`）。
- [ ] Green：点击时弹出浮层展示脚注内容。
- [ ] 验收：脚注弹窗可显示并关闭。

---

### F08 - 阅读主题预设库

> 来源：analysis_results.md §3.2

#### F08.1 内置主题预设

- [ ] Red：无内置精心调配的主题预设。
- [ ] Green：设计 10~15 套内置预设（仿古纸/护眼绿/OLED 纯黑/暖光/冷光/牛皮纸/水墨/薄荷/樱花粉/深海蓝 等）。
- [ ] Green：每套包含：背景色/文字色/行距/字号/字间距组合。
- [ ] Green：首次启动时写入 `ReaderPresetEntity`，标记 `isBuiltin = true`。
- [ ] 验收：预设列表展示内置主题，可一键应用。

#### F08.2 预设导入/导出

- [ ] Red：预设无法在设备间迁移。
- [ ] Green：导出为 JSON 文件（通过 SAF）。
- [ ] Green：导入 JSON 文件恢复预设。
- [ ] Green：可通过 WebDAV 同步预设（预留接口）。
- [ ] 验收：导出→导入后预设内容一致。

---

本文档续接 → [22-feature-enhancement-tasks-p2.md](./22-feature-enhancement-tasks-p2.md)
