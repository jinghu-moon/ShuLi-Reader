# 22 - 功能增强与阅读 UI 优化 TDD 任务清单（续）

> 本文档为 [22-feature-enhancement-tasks.md](./22-feature-enhancement-tasks.md) 的 P1（续）+ P2 + P3 部分。

---

### F09 - 阅读器新增配置项

> 来源：reader_ui_analysis.md §3.1

#### F09.1 蓝光过滤

- [ ] Red：无蓝光过滤功能。
- [ ] Green：新增 `blueLightFilter: Boolean` + `blueLightIntensity: Float (0~100%)` 到 ReaderPreferences。
- [ ] Green：在 ReaderScreen 叠加暖色半透明 Overlay 层（`Color(0xFFFF9329).copy(alpha = intensity * 0.5f)`）。
- [ ] Green：在 QuickSettingsSheet 显示区域新增蓝光过滤开关和强度滑杆。
- [ ] 验收：蓝光过滤层正确叠加，强度可调。

#### F09.2 页码格式

- [ ] Red：页码格式固定为 `1/20`。
- [ ] Green：新增 `pageNumberFormat: PageNumberFormat` 枚举（`FRACTION` = "1/20" / `CURRENT_ONLY` = "1" / `CHINESE` = "第1页"）。
- [ ] Green：页脚 Slot 渲染时按格式输出。
- [ ] Green：在 QuickSettingsSheet 显示面板新增页码格式选项。
- [ ] 验收：三种格式均正确渲染。

#### F09.3 标题独立字重

- [ ] Red：章节标题字重跟随正文。
- [ ] Green：`TitleStyleConfig` 新增 `fontWeight: ReaderFontWeight` 字段。
- [ ] Green：Canvas 渲染标题时使用独立字重。
- [ ] Green：QuickSettingsSheet 标题样式区域新增字重选项。
- [ ] 验收：标题可独立设置为粗体而正文保持常规。

#### F09.4 页眉页脚独立字号

- [ ] Red：页眉页脚字号固定为正文 × 0.75。
- [ ] Green：新增 `headerFooterFontSize: Float` 到 ReaderPreferences（默认 12sp）。
- [ ] Green：ReaderCanvasView 的 headerPaint/footerPaint 使用独立字号。
- [ ] Green：QuickSettingsSheet 显示面板新增页眉页脚字号滑杆。
- [ ] 验收：页眉页脚字号可独立调节。

#### F09.5 长按动作配置

- [ ] Red：长按动作固定为选文。
- [ ] Green：新增 `longPressAction: LongPressAction` 枚举（SELECT_TEXT / DICTIONARY / NONE）。
- [ ] Green：ReaderTouchController 根据配置分发长按事件。
- [ ] Green：QuickSettingsSheet 行为面板新增长按动作选项。
- [ ] 验收：切换为"查词"后长按直接触发词典查询。

#### F09.6 段首空行数量

- [ ] Red：段落分隔只有段距倍率，无法设置空行数。
- [ ] Green：新增 `paragraphBlankLines: Int (0/1/2)` 到 ReaderPreferences。
- [ ] Green：Paginator 在段落间插入额外空行。
- [ ] Green：QuickSettingsSheet 排版面板新增段首空行选项。
- [ ] 验收：设置 1 空行后段落间有明显分隔。

#### F09.7 自动翻页

- [ ] Red：无自动翻页功能。
- [ ] Green：新增 `autoPageTurn: Boolean` + `autoPageIntervalSeconds: Int (3~60)` 到 ReaderPreferences。
- [ ] Green：开启后用 `delay(interval)` 循环触发 `nextPage()`。
- [ ] Green：手动翻页/触摸屏幕时暂停自动翻页 5 秒。
- [ ] Green：QuickSettingsSheet 行为面板新增自动翻页开关和间隔滑杆。
- [ ] 验收：自动翻页按间隔稳定运行。

---

### F10 - 底部工具栏与进度条改进

> 来源：reader_ui_analysis.md §2.2、§2.8

#### F10.1 进度条拖拽气泡

- [ ] Red：拖动 CanvasSlider 时无目标页码预览。
- [ ] Green：拖拽时在滑块上方显示浮动气泡 Composable。
- [ ] Green：气泡内显示目标页码和章节标题。
- [ ] Green：松手后气泡消失。
- [ ] 验收：气泡位置跟随滑块，内容正确。

#### F10.2 全书百分比显示

- [ ] Red：底部工具栏仅显示章节内页码。
- [ ] Green：在页码旁增加全书百分比显示（如 "32.5%"）。
- [ ] Green：页脚 SlotContent 新增 `GLOBAL_PROGRESS` 选项。
- [ ] 验收：百分比与实际阅读进度一致。

#### F10.3 预计剩余时间

- [ ] Red：无预计剩余阅读时间。
- [ ] Green：基于历史阅读速度（字/分钟），计算本章剩余时间。
- [ ] Green：页脚 SlotContent 新增 `REMAINING_TIME` 选项。
- [ ] Green：显示格式：「约 X 分钟」。
- [ ] 验收：预估时间合理。

---

### F11 - QuickSettingsSheet 4-Tab 重构

> 来源：reader_ui_analysis.md §3.3

#### F11.1 Tab 重组

- [ ] Red：当前 3-Tab（排版/样式/设置）中「设置」Tab 是混合桶。
- [ ] Green：拆分为 4-Tab：排版 / 样式 / 显示 / 行为。
- [ ] Green：「显示」Tab 包含：页眉页脚自定义/标题样式/进度条/页码格式/蓝光过滤/全屏模式。
- [ ] Green：「行为」Tab 包含：屏幕常亮/音量翻页/边缘翻页/自动翻页/长按动作/振动反馈/TTS/预设管理/恢复默认。
- [ ] Green：三语 Tab 标签同步更新。
- [ ] 验收：4-Tab 布局清晰，所有配置项可访问。

---

### F12 - 目录面板改进

> 来源：reader_ui_analysis.md §2.4

#### F12.1 目录改为侧栏

- [ ] Red：ModalBottomSheet 对长章节列表不够友好，高度限制 400dp。
- [ ] Green：改为从左侧滑入的全高 ModalDrawerSheet。
- [ ] Green：宽度 80% 屏幕宽或最大 360dp。
- [ ] Green：保留目录/书签/笔记三 Tab。
- [ ] 验收：长章节列表可完整滚动浏览。

#### F12.2 章节搜索过滤

- [ ] Red：章节列表无搜索功能。
- [ ] Green：在目录 Tab 顶部增加 OutlinedTextField 搜索框。
- [ ] Green：输入时实时过滤匹配的章节标题。
- [ ] 验收：搜索"第三"能过滤出"第三章"。

#### F12.3 章节百分比与已读标记

- [ ] Red：章节条目无全书进度标记。
- [ ] Green：每个章节旁显示全书百分比（如 "(12%)"）。
- [ ] Green：已读章节（index < currentChapterIndex）文字颜色使用 `textSecondary`。
- [ ] 验收：已读/未读视觉分区明确。

---

### F13 - 触控热区自定义

> 来源：reader_ui_analysis.md §2.1

#### F13.1 热区配置数据模型

- [ ] Red：TouchZone → TouchAction 映射硬编码。
- [ ] Green：新增 `touchZoneConfig: Map<TouchZone, TouchAction>` 到 ReaderPreferences。
- [ ] Green：提供默认映射（左列=上页/中列=工具栏/右列=下页）。
- [ ] Green：`TouchZoneCalculator.getActionForZone()` 改为读取配置。
- [ ] 验收：配置存储和读取正确。

#### F13.2 热区配置 UI

- [ ] Red：用户无法自定义热区。
- [ ] Green：在 QuickSettingsSheet 行为面板新增「触控热区」入口。
- [ ] Green：展示 3×3 九宫格可视化网格。
- [ ] Green：点击每个格子弹出动作选择（上一页/下一页/工具栏/无操作）。
- [ ] Green：提供「恢复默认」按钮。
- [ ] 验收：自定义热区后翻页行为符合配置。

---

### F14 - 离线词典查询

> 来源：analysis_results.md §3.1（完全离线）

#### F14.1 系统词典 Intent

- [ ] Red：选中单词后无查词入口。
- [ ] Green：选区操作栏「查词」按钮调用 `ACTION_DEFINE` Intent。
- [ ] Green：无匹配 App 时显示 Toast 提示。
- [ ] 验收：安装离线词典 App 后可查词。

#### F14.2 StarDict 离线词典引擎

- [ ] Red：无内嵌离线词典。
- [ ] Green：实现 StarDict `.dict`/`.idx` 格式解析器。
- [ ] Green：用户通过 SAF 导入词库文件，存储在 App 私有目录。
- [ ] Green：查询结果在弹窗中展示释义。
- [ ] Refactor：词典引擎为独立模块 `core/dictionary`。
- [ ] 验收：导入词库后可离线查词。

---

### F15 - 文本替换/净化规则

> 来源：analysis_results.md §3.4

#### F15.1 净化规则数据模型

- [ ] Red：无文本净化规则存储。
- [ ] Green：新增 `TextReplaceRuleEntity`（id, name, pattern, replacement, isRegex, isEnabled, sortOrder）。
- [ ] Green：Room Migration 新增 `text_replace_rules` 表。
- [ ] Green：`TextReplaceRuleDao` 基础 CRUD。
- [ ] 验收：规则存储和查询正确。

#### F15.2 净化管线

- [ ] Red：章节文本加载后无净化步骤。
- [ ] Green：在 `loadChapterContent → paginateChapter` 之间插入净化管线。
- [ ] Green：按 `sortOrder` 顺序依次执行启用的规则。
- [ ] Green：正则规则使用 `Regex` 替换，纯文本规则使用 `String.replace`。
- [ ] Refactor：净化器为纯函数 `TextCleaner.apply(text, rules): String`。
- [ ] 验收：净化后的文本在阅读页正确显示。

#### F15.3 净化规则管理 UI

- [ ] Red：无规则管理界面。
- [ ] Green：新增规则管理 Screen（从设置页进入）。
- [ ] Green：支持添加/编辑/删除/启用禁用/排序。
- [ ] Green：内置常见净化模板（去除广告文字、多余空行等）。
- [ ] Green：规则导入/导出 JSON（通过 SAF）。
- [ ] 验收：规则管理流程完整。

---

### F16 - 智能目录（章节正则编辑器）

> 来源：analysis_results.md §3.3

#### F16.1 章节正则存储

- [ ] Red：章节正则固定在 TxtParser 中。
- [ ] Green：`BookEntity` 新增 `customChapterRegex: String?` 字段。
- [ ] Green：TxtParser 优先使用书籍自定义正则，无自定义时使用默认。
- [ ] 验收：单书自定义正则生效。

#### F16.2 正则模板库

- [ ] Red：无内置章节正则模板。
- [ ] Green：内置 20+ 常见中文/英文章节正则模板。
- [ ] Green：模板列表可选择，选中后预览匹配的章节。
- [ ] 验收：模板覆盖常见小说格式。

#### F16.3 正则编辑器 UI

- [ ] Red：用户无法自定义章节正则。
- [ ] Green：在书籍信息面板新增「章节规则」入口。
- [ ] Green：编辑器：正则输入框 + 实时预览匹配章节列表。
- [ ] Green：「应用并重新解析」按钮触发章节重建。
- [ ] 验收：自定义正则后章节列表正确更新。

---

### F17 - 沉浸式阅读增强

> 来源：analysis_results.md §3.6

#### F17.1 自动亮度

- [ ] Red：无环境光自动亮度。
- [ ] Green：读取 `Sensor.TYPE_LIGHT` 传感器数据。
- [ ] Green：映射环境光到亮度值（对数映射），实时调节 Window 亮度。
- [ ] Green：仅在 `brightness` 设为"自动"时启用。
- [ ] 验收：环境光变化时亮度平滑调节。

#### F17.2 焦点行高亮

- [ ] Red：无焦点行阅读模式。
- [ ] Green：新增 `focusLineMode: Boolean` 到 ReaderPreferences。
- [ ] Green：开启时仅高亮当前 1/3 区域，其余行 alpha 降至 0.3。
- [ ] Green：翻页后重置焦点到页面顶部。
- [ ] 验收：焦点行模式视觉效果自然。

---

### F18 - 滑杆交互优化

> 来源：reader_ui_analysis.md §2.3

#### F18.1 步进按钮

- [ ] Red：滑杆精确调节困难。
- [ ] Green：ReaderValueSlider 两端增加 `−` / `+` 按钮。
- [ ] Green：点击按钮按 1 step 步进（字号±1, 行距±0.1 等）。
- [ ] 验收：步进按钮功能正确。

#### F18.2 双击重置

- [ ] Red：无法快速恢复单项默认值。
- [ ] Green：双击滑杆标签恢复该项为默认值。
- [ ] Green：重置时触发短 Toast 提示。
- [ ] 验收：双击重置行为正确。

---

### F19 - 垂直无尽滚动模式

> 来源：reader_ui_analysis.md §2.6

#### F19.1 滚动容器架构

- [ ] Red：当前 SCROLL 模式是上下推动画，非真正连续滚动。
- [ ] Green：新增 `VerticalScrollDelegate`，拼接多个 TextPage 为连续长视图。
- [ ] Green：Canvas 层引入类 RecyclerView 的页面回收机制。
- [ ] Green：滚动位置映射到 charOffset 并可保存。
- [ ] 验收：连续滚动阅读流畅无卡顿。

#### F19.2 滚动/分页模式切换

- [ ] Red：切换模式后阅读位置丢失。
- [ ] Green：切换时基于 charOffset 保持位置。
- [ ] Green：滚动模式下隐藏页码进度条，改为连续进度指示器。
- [ ] 验收：模式切换后位置稳定。

---

### F20 - PDF 基础支持

> 来源：analysis_results.md §3.5

#### F20.1 PdfRenderer 只读浏览

- [ ] Red：无法打开 PDF 文件。
- [ ] Green：新增 `PdfReaderScreen`（独立于文本阅读器）。
- [ ] Green：使用 Android `PdfRenderer` (API 21+) 逐页渲染 Bitmap。
- [ ] Green：支持缩放和翻页。
- [ ] Green：书架识别并导入 PDF 文件。
- [ ] Refactor：PDF 阅读器不与文本分页引擎耦合。
- [ ] 验收：PDF 文件可正常浏览。

#### F20.2 PDF 进度保存

- [ ] Red：退出 PDF 后无法恢复页码。
- [ ] Green：保存当前页码到 BookEntity（`durChapterPos` 复用为页码）。
- [ ] Green：重新打开时恢复到上次页码。
- [ ] 验收：PDF 进度保存恢复正确。

---

## 四、推荐执行顺序

### 第四阶段：架构与核心补全（P0）

1. F01 架构重构（大文件拆分）
2. F04 阅读 UI 快速优化（全沉浸/按钮标签/方向/自动隐藏/振动）
3. F02 文本选择增强（手势+渲染+位置跟随+新动作）
4. F03 TTS 朗读系统增强（分句高亮→连续章节→后台→引擎选择→定时）

阶段验收：大文件完成拆分，文本选择精确到字/词，TTS 可后台播放。

### 第五阶段：功能完善（P1）

1. F05 书签笔记完善（高亮渲染/多色/导出）
2. F06 阅读统计系统（session→聚合→总览→热力图→书籍→时段）
3. F07 EPUB 增强（图片→CSS→多级目录→注脚）
4. F08 阅读主题预设库（内置主题/导入导出）
5. F09 新增配置项（蓝光/页码格式/标题字重/页眉字号/长按/空行/自动翻页）
6. F10 底部工具栏改进（气泡/百分比/剩余时间）
7. F11 QuickSettingsSheet 4-Tab 重构
8. F12 目录面板改进（侧栏/搜索/百分比标记）

阶段验收：阅读统计可视化完成，EPUB 图片可渲染，设置面板 4-Tab 布局。

### 第六阶段：差异化（P2）

1. F13 触控热区自定义
2. F14 离线词典查询
3. F15 文本净化规则
4. F16 智能目录正则编辑器
5. F17 沉浸式增强（自动亮度/焦点行）
6. F18 滑杆交互优化（步进/双击重置）
7. F19 垂直无尽滚动模式

阶段验收：热区可自定义，词典可查，净化规则可用，垂直滚动模式可选。

### 第七阶段：格式扩展（P3）

1. F20 PDF 基础支持

阶段验收：PDF 文件可浏览，进度可保存。

## 五、质量门禁

沿用 [13-implementation-task-list.md](./13-implementation-task-list.md) 第二章的质量目标与验收门禁，补充：

| 指标 | 目标 |
|------|------|
| 统计页 Canvas 渲染 | 热力图/柱状图首帧 < 100ms |
| PDF 首页渲染 | < 300ms（PdfRenderer 方案） |
| 新增配置项 | 每项均有持久化/恢复单元测试 |
| 新增 UI 组件 | 每个均有 testTag，Compose Test 可定位 |
| 三语覆盖 | 每次新增文案同步更新 ZhHans/ZhHant/En |

## 六、风险清单

1. **ReaderViewModel 拆分风险**：2482 行拆分影响面大，建议先补全现有测试覆盖再动手。
2. **ReaderPreferences 重构风险**：DataStore 序列化格式变更可能导致用户设置丢失，必须提供迁移。
3. **垂直滚动模式风险**：Canvas 层连续滚动的回收机制是全新架构，工作量可能超预期。
4. **PDF 渲染风险**：PdfRenderer 不支持文本选择和 reflow，用户预期管理重要。
5. **StarDict 词典风险**：词库文件可能很大（数十 MB），需要增量加载和索引优化。
