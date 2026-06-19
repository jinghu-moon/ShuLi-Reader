# 设置界面重构设计文档

> 目标：重构 ShuLi Reader 全局设置页，使其与项目当前阅读器能力、同步能力、统计能力和 MoTuInk 视觉体系匹配。  
> 原型：`docs/prototypes/settings-v3-proposal.html`  
> 相关现状：`SettingsScreen.kt`、`SettingsViewModel.kt`、`UserPreferences.kt`、`ReaderPreferences.kt`、`ReaderSettingRegistry.kt`、阅读器设置面板组件。

---

## 1. 背景与结论

当前全局设置页已经明显落后于项目本身的能力。

代码层面，`SettingsScreen` 仍是 `TopAppBar + LazyColumn + Material ListItem/Card` 的旧式设置页；`SettingsViewModel.SettingsUiState` 只组合了语言、主题、基础排版、导入、统计、同步、GPU、日志、备份等少量字段。与此同时，项目已经在阅读器设置体系中建立了更完整的能力：

- `UserPreferences` 已有大量阅读、排版、显示、手势、TTS、护眼、主题、同步、备份、书架字段。
- `ReaderPreferences` 已经包含字体、字号、行距、段距、缩进、字距、字重、对齐、简繁转换、页眉页脚、背景主题、色温、手势、TTS、自动翻页、竖排、双页、Bionic Reading 等配置。
- `ReaderSettingRegistry` 已经为阅读设置建立了元数据：默认值、存储层级、失效范围、UI 分组、是否进入预设、预览策略。
- 阅读器快捷设置面板已经有 MoTuInk 风格的 `SettingsCard`、`InkStepperSlider`、`InkSelect`、`InkToggle`、`ThemeSwatchRow` 等组件。
- 同步/备份/加密/设备/日志二级页已经存在，但视觉体系仍偏 Material 默认列表。

因此本次重构不应只是“把旧设置页换皮”，而应完成三件事：

1. 把全局设置页从“少量旧设置项”升级为“应用默认值和数据能力的总入口”。
2. 把全局设置页 UI 与阅读器设置面板 UI 统一到一套可复用 Settings UI 组件。
3. 明确全局设置页、阅读器内快捷设置面板、我的/工具入口之间的边界。

---

## 2. 现状研读

### 2.1 当前全局设置页结构

当前 `SettingsScreen` 由以下 section 构成：

| Section | 当前内容 | 主要问题 |
|---|---|---|
| `AppearanceSection` | 语言、界面字体、应用主题 | 只有界面级设置，没有阅读背景/阅读字体入口；大量弹窗选择 |
| `ReaderPrefsSection` | 字号、行距、段距、缩进、翻页动画/方向、全屏、常亮、亮度 | 混合排版/阅读行为/屏幕显示；无实时预览；亮度“跟随系统”与最暗状态混淆 |
| `LibrarySection` | 查重、导入复制、封面颜色、清缓存 | 只暴露导入偏好，没有书架视图、标签、分类、存储摘要 |
| `StatsSection` | 时长统计、每日目标、查看报告 | 缺少目标提醒、数据保留/重置策略 |
| `SyncSection` | 同步方式、同步页、本地备份 | WebDAV 配置跳转较深，主设置页没有状态摘要和就地配置入口 |
| `AdvancedSection` | GPU、日志、重置 | 过少，缺少缓存、实验功能、调试导出等层次 |
| `AboutSection` | 版本、开发者、反馈、许可 | 链接行点击反馈弱，未统一 NavigationRow |

### 2.2 当前设置项落后的具体证据

`SettingsUiState` 当前暴露的阅读默认值主要是：

- `defaultFontSize`
- `defaultLineSpacing`
- `defaultParagraphSpacing`
- `defaultIndent`
- `defaultPageAnim`
- `pageTurnDir`
- `fullScreen`
- `keepScreenOn`
- `brightness`

但 `UserPreferences` 和 `ReaderPreferences` 已经存在但全局设置页未暴露或未体系化的设置包括：

| 能力域 | 已存在字段/能力 | 全局设置页现状 |
|---|---|---|
| 阅读字体 | `readingFont`、`FontImportManager`、`FontManager`、`FontPreviewRow` | 缺少“阅读字体”，只有“界面字体” |
| 阅读主题 | `readerTheme`、自定义背景/正文/标题/页眉脚颜色、`ThemeSwatchRow` | 缺少阅读背景色/主题皮肤 |
| 排版增强 | 字距、字重、对齐、简繁转换、盘古间距、去空行、清理标题、保留原缩进、段落分隔、Bionic Reading | 全局页只暴露字号/行距/段距/缩进 |
| 页面盒模型 | 正文/页眉/页脚/标题的四边距、最大页宽 | 全局页未暴露 |
| 页眉页脚 | 可见性、槽位内容、透明度、分隔线、字号比例、进度样式 | 全局页未暴露 |
| 显示 | 色温、背景纹理、双页模式、方向锁定、沉浸模式 | 全局页未暴露或仅暴露全屏 |
| 翻页和手势 | 音量键翻页、边缘翻页、边缘宽度、左侧区域比例、手势配置、振动反馈、自动翻页 | 全局页未暴露 |
| 护眼 | 护眼提醒间隔、色温 | 全局页未暴露 |
| TTS | 语速、音调、自动翻页、高亮句子、语音 | 全局页未暴露 |
| 同步 | WebDAV、自动备份、加密、设备、日志、导出 | 有二级页，但主设置页缺少状态摘要和统一样式 |
| 书库 | 书架视图、标签管理、封面色盘、导入策略 | 暴露很少 |
| 字典/生词 | `DictionaryManager`、`DictionaryBottomSheet`、`AnkiExporter` | 设置页缺少“词典与划词”管理入口 |

### 2.3 设置页与“我的页”的边界

参考对话中的两张“我的”页面截图，其分类方式适合个人中心/工具入口，但不能直接复制为设置页。

设置页应承载：

- 默认偏好
- 数据、安全、同步、备份配置
- 资源管理
- 实验/高级开关
- 关于与法律信息

我的页/个人中心更适合承载：

- 头像、昵称、会员、账号
- 数据概览
- AI 助手、语音朗读、翻译、生词本等功能入口
- 网盘书库、标签、分类等管理入口

设置页可以给这些功能提供“配置入口”，但不应把功能本身堆成入口列表。

---

## 3. 设计目标

### 3.1 产品目标

1. 常用设置在主设置页可快速扫描、快速修改。
2. 阅读体验相关设置需要有实时预览。
3. 选择类设置尽量减少 Dialog，优先使用行内选择、分段控制、底部 Sheet。
4. 同步、备份、安全需要有状态摘要，减少“点进去才知道状态”的不确定性。
5. 危险操作必须视觉区分，并保持二次确认。

### 3.2 工程目标

1. 设置页 UI 组件统一放置，供全局设置页、同步设置页、阅读器设置面板复用。
2. 复用现有阅读器设置面板中的成熟 Ink 组件，不重新造一套。
3. 设置项映射尽量复用 `ReaderSettingRegistry` 的分组、默认值、预览策略。
4. 保持全局设置和每书设置的边界：全局设置页修改默认值；阅读器面板修改当前书/当前阅读上下文。
5. DataStore 写入避免高频抖动：连续滑动用本地预览状态，结束后提交最终值。

---

## 4. 推荐信息架构

V3 主设置页采用“密集单列 + 分组卡片 + 少量二级页”的结构。

### 4.1 主设置页分组

| 分组 | 定位 | 设置项 |
|---|---|---|
| 外观 | 应用 UI 级偏好 | 应用主题、界面语言、界面字体、MoTuInk 色系预览 |
| 阅读主题 | 阅读背景和主题色 | 纸白、米黄、护眼绿、夜间灰、OLED、自定义颜色、背景纹理 |
| 阅读字体与排版 | 阅读默认排版 | 阅读字体、字号、行距、段距、缩进、字距、字重、对齐、实时预览 |
| 阅读内容处理 | 文本处理默认值 | 简繁转换、去空行、清理章节标题、保留原缩进、盘古间距、EPUB 覆盖样式 |
| 阅读模式 | 翻页和阅读行为 | 翻页动画、动画速度、翻页方向、音量键翻页、边缘翻页、自动翻页 |
| 屏幕与显示 | 与设备屏幕相关 | 跟随系统亮度、亮度、色温、全屏、沉浸、常亮、方向锁定、双页模式 |
| 页眉页脚 | 页面 chrome 默认值 | 页眉页脚可见性、槽位、进度样式、透明度、分隔线 |
| 书库与导入 | 书架和导入默认值 | 自动查重、导入复制、封面色盘、书架视图、标签管理、清除导入缓存 |
| 数据与同步 | WebDAV、备份、导出 | 同步状态、同步方式、WebDAV Accordion、本地备份、自动备份、加密、设备、日志 |
| 阅读统计 | 统计记录和目标 | 时长统计、每日目标、目标提醒、统计报告、重置统计 |
| 词典与划词 | 划词/查词/TTS 相关配置入口 | 默认词典、词典管理、Anki 导出、TTS 语速/音调/高亮、长按选择辅助 |
| 隐私与安全 | 权限、隐私和安全 | 数据加密、隐私数据清理、日志导出、崩溃日志开关 |
| 高级与实验 | 低频高风险设置 | GPU、调试日志、实验功能、缓存策略、重置所有设置 |
| 关于 | 应用信息 | 版本、GitHub、许可证、检查更新、反馈 |

### 4.2 主设置页与二级页边界

主设置页应只展开“轻量配置”和“状态摘要”。以下内容保留二级页：

| 二级页 | 原因 |
|---|---|
| 字体管理 | 涉及导入、删除、文件权限、字体预览列表 |
| 主题资源/素材管理 | 可能包含封面图集、底栏图集、启动图集、我的图库 |
| 标签管理/分类管理 | 属于书库数据管理 |
| 同步日志 | 长列表和筛选 |
| 设备管理 | 多设备列表、删除、状态 |
| 加密管理 | 密码验证、启用/更换密码、风险提示 |
| 本地备份 | 文件选择、导出选项、导入策略 |
| 词典管理 | 导入词典、启用顺序、索引状态 |

---

## 5. 新增设置项目建议

### 5.1 P0：必须进入本次重构的设置项

这些项目已经有底层字段、组件或明确业务价值，应优先进入 V3：

| 分组 | 设置项 | 类型 | 依据 |
|---|---|---|---|
| 阅读主题 | 阅读背景 | 色块组 | `ReaderTheme` 已有 LIGHT/DARK/PAPER/GREEN/OLED/CUSTOM |
| 阅读字体与排版 | 阅读字体 | 字体预览行 + 导入入口 | `readingFont`、`FontManager`、`FontPreviewRow` 已存在 |
| 阅读字体与排版 | 排版预览 | `TextPreviewBanner` | 字号/行距/段距/缩进必须即时反馈 |
| 阅读字体与排版 | 缩进 | `DiscreteSlider` | 0/1/2 字属于离散档，不适合连续滑块 |
| 阅读字体与排版 | 字重、对齐 | `SegmentedControl` 或 `InkSelect` | `fontWeight`、`textAlign` 已存在 |
| 阅读内容处理 | 去空行、清理章节标题、保留原缩进 | Toggle | `UserPreferences` 已有字段 |
| 阅读模式 | 翻页动画、方向、动画速度 | `SegmentedControl` | 2-5 项离散选项不应藏在 Dialog |
| 屏幕与显示 | 跟随系统亮度 + 亮度滑块 | Toggle + disabled slider | 修复“0% = 跟随系统”的歧义 |
| 屏幕与显示 | 色温 | StepperSlider | `colorTemperature` 已存在 |
| 书库与导入 | 书架视图、封面色盘、导入复制、查重 | Select/Toggle/Navigation | 书架已有视图模式字段和封面色盘 |
| 数据与同步 | 同步状态 Banner | StatusBanner | 同步状态不应隐藏在二级页 |
| 数据与同步 | WebDAV 就地展开配置 | Accordion + InputRow | 用户期望切到 WebDAV 后立即配置 |
| 高级 | 重置/清除类按钮 | DestructiveButton | 防误触 |
| 关于 | GitHub/许可证 | NavigationRow | 统一可点击反馈 |

### 5.2 P1：第二阶段进入设置页

| 分组 | 设置项 | 类型 | 说明 |
|---|---|---|---|
| 页眉页脚 | 页眉页脚可见性、进度样式、槽位 | 二级页或折叠卡片 | 项目已有完整模型，但主设置页不宜过重 |
| 阅读模式 | 触控区域、左侧区域比例、振动反馈 | NavigationRow + Slider | 可进入手势编辑器 |
| TTS | 语速、音调、自动翻页、高亮句子 | StepperSlider/Toggle | `UserPreferences` 已有 TTS 字段 |
| 护眼 | 护眼提醒间隔 | SegmentedControl | `eyeCareReminderInterval` 已存在 |
| 备份 | 自动备份、启动/退出备份、间隔、位置 | Toggle/Input/Navigation | `SettingsUiState` 已有自动备份字段 |
| 隐私与安全 | 加密管理状态、日志导出 | StatusRow/NavigationRow | 相关二级页存在 |

### 5.3 P2：后续增强，不阻塞 V3 首版

| 分组 | 设置项 | 说明 |
|---|---|---|
| 设置搜索 | 搜索设置项并高亮结果 | 设置项更多后再做 |
| QuickNav | 顶部横向分组锚点 | 设置项很多时有价值，首版可保留原型但实现后置 |
| 自定义主题编辑器 | 背景/正文/标题/页眉脚颜色 | 需要颜色选择器和预览 |
| 主题资源管理 | 封面图集/底栏图集/启动图集/我的图库 | 更像资源管理二级页 |
| AI/翻译 | 引擎配置 | 当前项目未见完整 AI/翻译实现，先不进入主设置 |

---

## 6. 可复用的现有组件

### 6.1 应直接抽取复用的组件

以下组件已接近原型和 MoTuInk 风格，建议从 `feature/reader/settings/panel` 抽到统一 Settings UI 包：

| 现有组件 | 当前路径 | 用途 |
|---|---|---|
| `SettingsCard` | `feature/reader/settings/panel/ReaderSettingsCard.kt` | 描边设置卡片、折叠、LabelWidthState |
| `LabelWidthState` / `LocalLabelWidthState` | 同上 | 多个 slider 标签/数值列对齐 |
| `InkStepperSlider` | `feature/reader/settings/panel/controls/InkStepperSlider.kt` | 数值型设置 |
| `InkSlider` | `feature/reader/settings/panel/controls/InkSlider.kt` | 自绘滑块 |
| `InkCircleButton` | `feature/reader/settings/panel/controls/InkCircleButton.kt` | Stepper 加减按钮 |
| `InkSelect` | `feature/reader/settings/panel/controls/InkSelect.kt` | 行内下拉选择 |
| `InkDropdownMenu` | `feature/reader/settings/panel/controls/InkDropdownMenu.kt` | 下拉菜单 |
| `InkToggle` | `feature/reader/settings/panel/controls/InkToggle.kt` | MoTuInk 开关 |
| `ThemeSwatchRow` | `feature/reader/settings/panel/ThemeSwatchRow.kt` | 阅读背景/主题色块 |
| `MarginPresetRow` | `feature/reader/settings/panel/controls/MarginPresetRow.kt` | 边距预设 |
| `FontPreviewRow` | `feature/reader/settings/panel/controls/FontPreviewRow.kt` | 阅读字体预览 |
| `BoxMarginSection` | `feature/reader/settings/panel/controls/BoxMarginSection.kt` | 盒模型边距配置 |
| `SlotMatrix` | `feature/reader/settings/panel/SlotMatrix.kt` | 页眉页脚槽位配置 |

### 6.2 可保留但需要改造的组件

| 现有组件 | 问题 | 改造方向 |
|---|---|---|
| `SettingsClickItem` | Material `ListItem` 风格，与原型不一致 | 替换为 `NavigationRow` / `ValueRow` |
| `SettingsSwitchItem` | 使用 Material `Switch` | 替换为 `InkToggleRow` |
| `SettingsButtonItem` | 普通按钮与危险按钮无区分 | 拆成 `ActionRow` 和 `DestructiveActionRow` |
| `ThemePreviewDots` | 只能预览三点 | 保留为 `ThemePreviewDots`，但放入新包 |
| 同步二级页 Card/ListItem | 视觉体系与设置页不一致 | 逐步替换为统一 Settings UI 组件 |

---

## 7. 新增组件设计

### 7.1 统一放置位置

建议所有设置类 UI 组件统一放置在：

```text
app/src/main/java/com/shuli/reader/ui/component/settings/
```

建议包结构：

```text
ui/component/settings/
├── SettingsCard.kt
├── SettingsRows.kt
├── SettingsTextPreview.kt
├── SettingsStatus.kt
├── SettingsInputs.kt
├── SettingsButtons.kt
├── SettingsControls.kt
├── SettingsThemePickers.kt
└── SettingsLayout.kt
```

迁移原则：

1. 新包只依赖 `ui.theme`、Material3 基础类型和 Compose 基础组件，不依赖 `feature.reader`、`feature.settings` 的 ViewModel。
2. 组件只接收值和回调，不直接读写 DataStore。
3. 组件文案由调用方传入，组件不直接依赖具体业务字符串。
4. 阅读器面板和全局设置页都从新包导入组件。
5. 旧路径可以先保留转发包装，避免一次性大规模改动。

### 7.2 新组件清单

| 组件 | 用途 | 关键 API |
|---|---|---|
| `SettingRow` | 基础行容器 | `label`、`sublabel`、`enabled`、`trailing` |
| `ValueRow` | 左标题右当前值 | `valueText`、`onClick` |
| `NavigationRow` | 二级页入口 | `label`、`sublabel`、`badge`、`onClick` |
| `InkToggleRow` | 开关行 | `checked`、`onCheckedChange` |
| `SegmentedControl` | 2-5 个选项的离散选择 | `options`、`selected`、`onSelect` |
| `DiscreteSlider` | 固定刻度滑块 | `steps: List<T>`、`selected`、`formatValue` |
| `TextPreviewBanner` | 排版实时预览 | `fontSize`、`lineHeight`、`paragraphSpacing`、`indentEm`、`fontFamily` |
| `InputRow` | 单行输入 | `label`、`value`、`placeholder`、`visualTransformation` |
| `PasswordInputRow` | 密码输入 | `visible`、`onVisibleChange` |
| `AccordionSection` | 条件展开区域 | `expanded`、`content` |
| `StatusBanner` | 同步/备份/安全状态 | `status`、`message`、`actionLabel` |
| `ActionRow` | 普通操作 | `buttonText`、`onClick` |
| `DestructiveButton` | 危险操作按钮 | 红色描边、二次确认 |
| `DestructiveActionRow` | 危险操作行 | `label`、`sublabel`、`confirmDialog` |
| `ThemeChipGroup` | 应用主题浅/深/跟随系统 | `selectedMode` |
| `ReaderThemeSwatchRow` | 阅读背景色 | `ReaderTheme` / custom color |
| `SettingsSearchBar` | 设置搜索 | P2 实现 |
| `QuickNavBar` | 设置分组锚点 | P2 实现 |

### 7.3 组件复用边界

`ui/component/settings` 是视觉和交互组件，不包含业务状态。业务层组件仍放在各 feature：

```text
feature/settings/sections/
feature/sync/settings/
feature/reader/settings/panel/
```

例如：

- `WebDavSettingsCard` 属于 `feature/settings` 或 `feature/sync/settings`。
- `InputRow` 属于 `ui/component/settings`。
- `TextPreviewBanner` 属于 `ui/component/settings`，但预览文案由 `feature/settings` 传入。

---

## 8. 布局方案

### 8.1 主布局

采用：

```kotlin
Scaffold(
    topBar = { LargeTopAppBar(...) },
) { innerPadding ->
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { AppearanceCard(...) }
        item { ReaderThemeCard(...) }
        item { TypographyCard(...) }
        ...
    }
}
```

LargeTopAppBar 建议使用：

```kotlin
TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
```

V3 原型保留搜索和 QuickNav，但 Compose 首版可后置：

- P0：LargeTopAppBar + LazyColumn + SettingsCard。
- P1：折叠高级卡片状态持久化。
- P2：SearchBar + QuickNavBar + 滚动锚点。

### 8.2 卡片布局

卡片采用 MoTuInk 描边样式：

- 1dp outline
- 10-12dp 圆角
- 无阴影
- 背景透明或接近页面背景
- 内部 12-14dp padding
- 标题 11-12sp、半粗、弱化色
- 标题左侧可用 2dp accent 竖线强化识别

行布局：

- 普通行最小高度 44-52dp。
- 左侧 `label + optional sublabel`。
- 右侧控件固定尺寸，避免拉伸。
- Slider 行使用 `LabelWidthState` 统一标签列和数值列。
- 二级入口统一 `NavigationRow`，不要混用裸 `›`。

### 8.3 排版卡片

排版卡片必须包含 `TextPreviewBanner`，建议放在卡片顶部：

1. 阅读字体选择
2. `TextPreviewBanner`
3. 字号
4. 行距
5. 段距
6. 缩进 `DiscreteSlider`
7. 字距、字重、对齐
8. 高级排版折叠区

预览文本：

```text
永和九年，岁在癸丑，暮春之初，会于会稽山阴之兰亭，修禊事也。

群贤毕至，少长咸集。此地有崇山峻岭，茂林修竹，又有清流激湍，映带左右。
```

预览必须实时应用：

- 字号
- 行距
- 段距
- 首行缩进
- 阅读字体
- 字重
- 对齐

### 8.4 阅读行为与屏幕显示必须拆分

阅读模式卡片：

- 翻页动画
- 翻页方向
- 动画速度
- 音量键翻页
- 边缘翻页
- 自动翻页

屏幕与显示卡片：

- 跟随系统亮度
- 亮度滑块
- 色温
- 全屏模式
- 沉浸模式
- 屏幕常亮
- 方向锁定
- 横屏双页

亮度逻辑：

- `brightness < 0` 表示跟随系统。
- UI 上必须是独立 Toggle。
- Toggle 开启时亮度 slider disabled。
- Toggle 关闭时 slider 使用 0.05-1.0 或 0-1.0 范围。

### 8.5 同步卡片

主设置页的数据与同步卡片应包含：

1. `StatusBanner`：显示当前同步状态、上次同步时间、错误状态、立即同步/重试按钮。
2. 同步方式：本地 / WebDAV。
3. WebDAV Accordion：当同步方式为 WebDAV 时展开 URL、账号、密码、测试连接、保存。
4. 本地备份入口。
5. 自动备份折叠项。
6. 加密管理、设备管理、同步日志 NavigationRow。

WebDAV 密码输入必须使用 `PasswordInputRow`，支持显示/隐藏。

---

## 9. 设置项落地映射

### 9.1 Global Settings UiState 扩展

`SettingsUiState` 应扩展并按领域拆分。不要继续把所有字段平铺在一个巨大 data class 里。

建议：

```kotlin
data class SettingsUiState(
    val appAppearance: AppAppearanceSettings,
    val readerDefaults: ReaderDefaultSettings,
    val library: LibrarySettings,
    val sync: SyncSettings,
    val stats: StatsSettings,
    val privacy: PrivacySettings,
    val advanced: AdvancedSettings,
)
```

其中 `readerDefaults` 可由 `UserPreferences` + `ReaderSettingRegistry` 映射。

### 9.2 DataStore 写入策略

| 控件 | 写入策略 |
|---|---|
| Toggle | 立即写入 |
| SegmentedControl | 立即写入 |
| InkSelect | 选择后写入 |
| Slider/Stepper | 拖动时只更新本地预览，`onValueChangeFinished` 写入 |
| TextField | 本地状态编辑，失焦或保存按钮写入 |
| Accordion 展开状态 | `rememberSaveable`；需要持久化时单独 DataStore |

### 9.3 全局默认值与每书覆盖

全局设置页修改的是默认值；阅读器内设置面板修改当前书或当前会话覆盖。UI 上应明确：

- 全局设置页标题使用“阅读默认值”。
- 阅读器设置面板标题使用“当前阅读设置”或保留当前 ScopeHeader。
- 如果将来支持“应用到所有书籍”，必须是明确操作，不应自动覆盖每书配置。

---

## 10. 与现有页面的迁移计划

### Phase 0：组件抽取

1. 新建 `ui/component/settings`。
2. 迁移 `SettingsCard`、`LabelWidthState`、`InkStepperSlider`、`InkSlider`、`InkCircleButton`、`InkSelect`、`InkDropdownMenu`、`InkToggle`。
3. 阅读器设置面板改为导入新包。
4. 保留旧文件作为过渡包装或一次性迁移后删除旧引用。

### Phase 1：全局设置页 P0 重构

1. 替换 `SettingsItems.kt`。
2. 重写 `AppearanceSection`。
3. 拆分 `ReaderPrefsSection` 为：
   - `ReaderThemeSection`
   - `ReaderTypographySection`
   - `ReaderContentSection`
   - `ReaderModeSection`
   - `ScreenDisplaySection`
4. 增加 `TextPreviewBanner`。
5. WebDAV 配置改成卡内 Accordion。

### Phase 2：同步/备份/安全统一样式

1. `SyncSettingsScreen` 使用统一 `SettingsCard` 和 `NavigationRow`。
2. `CloudSyncSettingsScreen` 使用 `InputRow`、`StatusBanner`。
3. `EncryptionManagementScreen` 使用 `StatusBanner`、`DestructiveButton`、`WarningCard`。

### Phase 3：扩展设置项

1. 页眉页脚入口。
2. TTS 设置入口。
3. 词典与划词管理入口。
4. 搜索和 QuickNav。
5. 主题资源/素材管理二级页。

---

## 11. 原则应用

### KISS

首版不做复杂搜索和全量动态设置生成，先用静态 section + 统一组件完成主要体验升级。

### YAGNI

AI、翻译、会员、资源商店等未在当前项目形成完整能力的入口不进入设置页主结构。

### DRY

不在 `feature/settings` 重写 Ink 控件；抽取阅读器面板已有组件形成统一 Settings UI。

### SOLID

- UI 组件只负责展示和交互，不直接读写偏好。
- Section 组件负责业务映射。
- ViewModel 负责状态组合和写入。
- DataStore/Registry 负责默认值和持久化。

---

## 12. 验收标准

1. 全局设置页不再使用 Material 默认 `ListItem` 作为主要视觉单元。
2. 排版卡片能实时预览字号、行距、段距、缩进。
3. 阅读行为与屏幕显示拆成独立卡片。
4. 亮度跟随系统逻辑无歧义。
5. WebDAV 能在主设置页就地展开配置。
6. 危险操作有红色视觉区分和确认。
7. 所有可进入二级页的行统一使用 `NavigationRow`。
8. 阅读器设置面板和全局设置页共享同一套 Settings UI 组件。
9. Slider 类写入不会在拖动中高频写 DataStore。
10. 深浅色模式下对比度和触控目标符合 Android 基础可访问性要求。

