# 阅读设置弹窗重构布局文档

## 范围

本文统计阅读设置弹窗展开区的主体配置项，并按新的一级、二级、三级分类重排。

- 不包含顶部快捷区：全局/本书、日夜、护眼快捷、横屏快捷、主题色块。
- 底部“恢复默认/清除本书设置”是全局动作，不归入四个一级分类。
- 当前来源文件：
  - `ReaderSettingsPanel.kt`
  - `ReaderSettingsModal.kt`
  - `TypeAndFontTab.kt`
  - `AppearanceTab.kt`
  - `BehaviorTab.kt`

## 控件层级

| 层级 | 分类角色 | 控件 | 使用规则 |
|---|---|---|---|
| 一级 | 全局分类 | `PrimaryTabRow` + `Tab` | 四个入口：排版、布局、翻页、辅助 |
| 二级 | 局部模块 | `SettingsCard` | 一个卡片对应一个对象或任务，不嵌套卡片 |
| 三级 | 卡片内分组 | `SettingsSectionDivider` | 文本在左，分隔线在右，不带 note，不表达点击状态 |
| 对象切换 | 同卡片内目标切换 | `SegmentedControl` | 例如页眉/页脚；它不是分类层级 |
| 配置项 | 单项设置 | `SettingRow` 系列 | `SwitchRow`、`SegmentedRow`、`SelectRow`、`InkStepperSlider` 等 |

三级分类组件已独立为：

```text
app/src/main/java/com/shuli/reader/feature/reader/settings/panel/SettingsSectionDivider.kt
```

## 分类原则

1. 同一个配置对象只归入一个一级分类。例如页眉页脚的内容、样式、边距都归入”布局”。
2. 二级分类优先复用当前设置卡片的局部语义，但需要合并被拆散的对象。
3. 三级分类只负责卡片内部的轻量分组，默认使用 `SettingsSectionDivider`。
4. 翻页动画归入”翻页”，不再归入”布局/显示”。
5. 护眼色温与护眼提醒归入同一二级分类“护眼”。
6. 顶部快捷区可以保留重复入口，但主体配置区必须只有一个归属位置。

## 目标一级分类

| 一级分类 | 二级分类 | 归类逻辑 |
|---|---|---|
| 排版 | 字体、正文排版、文本处理 | 文字内容的呈现和处理 |
| 布局 | 正文区域、标题、页眉页脚、边距方案 | 页面元素的布局 |
| 翻页 | 翻页方式、触控区域、翻页动效 | 翻页的输入方式和反馈 |
| 辅助 | 护眼、屏幕状态、阅读形态 | 系统级辅助和阅读体验 |

命名说明：
- **排版**：字体选择、字号行距、文本处理，聚焦"文字怎么显示"
- **布局**：边距、标题、页眉页脚、背景，聚焦"页面怎么排"
- **翻页**：翻页方式、触控区域、动效，聚焦"怎么翻页"
- **辅助**：护眼、屏幕状态，聚焦"系统辅助功能"

## 配置项清单

主体配置区共统计 58 项，其中包含持久化设置、字体导入/删除动作、边距批量动作和局部同步状态。

**配置项分布**：排版 22 项 / 布局 27 项 / 翻页 10 项 / 辅助 7 项（共 66 项业务配置 + 1 项局部状态）

| 一级 | 二级 Card | 三级分隔线 | 配置项 | 当前 key / 来源 | 归并说明 |
|---|---|---|---|---|---|
| 排版 | 字体 | 字体 | 阅读字体 | `reading_font` | 保留在字体卡片 |
| 排版 | 字体 | 字体 | 导入字体 | `onImportFont` | 动作项，不是持久化 key |
| 排版 | 字体 | 字体 | 删除字体 | `onDeleteFont` | 动作项，不是持久化 key |
| 排版 | 字体 | 字重 | 字重 | `font_weight` | 保留在字体卡片 |
| 排版 | 正文排版 | 字号 | 正文字号 | `font_size` | 从当前正文卡片迁入 |
| 排版 | 正文排版 | 行段 | 行距 | `line_spacing` | 从当前正文卡片迁入 |
| 排版 | 正文排版 | 行段 | 段距 | `paragraph_spacing` | 从当前正文卡片迁入 |
| 排版 | 正文排版 | 行段 | 首行缩进 | `indent` | 与保留原文缩进存在语义关联 |
| 排版 | 正文排版 | 行段 | 缩进单位 | `indent_unit` | 首行缩进的单位选择 |
| 排版 | 正文排版 | 行段 | 字距 | `letter_spacing` | 从当前正文卡片迁入 |
| 排版 | 正文排版 | 方向 | 竖排文本 | `vertical_text` | 文本排列方向 |
| 排版 | 正文排版 | 对齐 | 正文对齐 | `text_align` | 从当前字体卡片迁入正文排版 |
| 排版 | 正文排版 | 对齐 | 底部对齐 | `bottom_justify` | 从高级排版迁入正文排版 |
| 排版 | 文本处理 | 转换 | 繁简转换 | `chinese_convert` | 保留高级文本处理语义 |
| 排版 | 文本处理 | 转换 | 中文排版 | `use_zh_layout` | 中文排版优化 |
| 排版 | 文本处理 | 转换 | 盘古空格 | `use_pangu_spacing` | 与文本转换同组 |
| 排版 | 文本处理 | 清理 | 移除空行 | `remove_empty_lines` | 文本清理 |
| 排版 | 文本处理 | 清理 | 清理章节标题 | `clean_chapter_title` | 文本清理 |
| 排版 | 文本处理 | 清理 | 广告过滤 | `ad_filtering` | 过滤广告内容 |
| 排版 | 文本处理 | 增强 | 段落分隔线 | `paragraph_divider` | 阅读增强 |
| 排版 | 文本处理 | 增强 | Bionic Reading | `bionic_reading` | 阅读增强 |
| 排版 | 文本处理 | 兼容 | 保留原文缩进 | `preserve_original_indent` | 文本排版基础 |
| 排版 | 文本处理 | 兼容 | EPUB 样式覆盖 | `epub_override_style` | 格式兼容 |
| 布局 | 正文区域 | 边距 | 正文边距 | `body_box` | 从当前正文卡片拆出，归入布局 |
| 布局 | 标题 | 样式 | 标题字号 | `title_font_size` | 标题对象内聚 |
| 布局 | 标题 | 样式 | 标题字号偏移 | `title_size_offset` | 标题字号相对于正文的偏移 |
| 布局 | 标题 | 样式 | 标题对齐 | `title_align` | 标题对象内聚 |
| 布局 | 标题 | 边距 | 标题上边距 | `title_margin_top` | 标题对象内聚 |
| 布局 | 标题 | 边距 | 标题下边距 | `title_margin_bottom` | 标题对象内聚 |
| 布局 | 标题 | 边距 | 标题边距 | `title_box` | 标题对象内聚 |
| 布局 | 页眉页脚 | 显示 | 页眉可见性 | `header_visibility` | 与页眉页脚内容合并 |
| 布局 | 页眉页脚 | 显示 | 页脚可见性 | `footer_visibility` | 与页眉页脚内容合并 |
| 布局 | 页眉页脚 | 显示 | 显示进度 | `show_progress` | 是否显示阅读进度 |
| 布局 | 页眉页脚 | 内容 | 页眉左侧内容 | `header_left` | 当前在外观显示，归入页眉页脚 |
| 布局 | 页眉页脚 | 内容 | 页眉中间内容 | `header_center` | 当前在外观显示，归入页眉页脚 |
| 布局 | 页眉页脚 | 内容 | 页眉右侧内容 | `header_right` | 当前在外观显示，归入页眉页脚 |
| 布局 | 页眉页脚 | 内容 | 页脚左侧内容 | `footer_left` | 当前在外观显示，归入页眉页脚 |
| 布局 | 页眉页脚 | 内容 | 页脚中间内容 | `footer_center` | 当前在外观显示，归入页眉页脚 |
| 布局 | 页眉页脚 | 内容 | 页脚右侧内容 | `footer_right` | 当前在外观显示，归入页眉页脚 |
| 布局 | 页眉页脚 | 样式 | 页眉字号比例 | `header_font_size_ratio` | 当前在字体排版，归入页眉页脚 |
| 布局 | 页眉页脚 | 样式 | 页脚字号比例 | `footer_font_size_ratio` | 当前在字体排版，归入页眉页脚 |
| 布局 | 页眉页脚 | 样式 | 页眉页脚透明度 | `header_footer_alpha` | 共用样式项 |
| 布局 | 页眉页脚 | 样式 | 进度样式 | `progress_style` | 进度显示样式 |
| 布局 | 页眉页脚 | 样式 | 页眉分隔线 | `show_header_line` | 页眉样式 |
| 布局 | 页眉页脚 | 样式 | 页脚分隔线 | `show_footer_line` | 页脚样式 |
| 布局 | 页眉页脚 | 边距 | 页眉边距 | `header_box` | 不再与显示内容拆 Tab |
| 布局 | 页眉页脚 | 边距 | 页脚边距 | `footer_box` | 不再与显示内容拆 Tab |
| 布局 | 边距方案 | 预设 | 紧凑/标准/宽松预设 | 批量写入 `body_box/header_box/footer_box/title_box` | 跨对象批量动作 |
| 布局 | 边距方案 | 同步 | 统一左右边距 | `unifiedSync` 局部状态 | 跨对象同步，不是持久化 key |
| 布局 | 边距方案 | 重置 | 重置边距 | 批量写入四个 box | 跨对象批量动作 |
| 翻页 | 翻页方式 | 按键 | 音量键翻页 | `volume_key_turn_page` | 翻页输入方式 |
| 翻页 | 翻页方式 | 边缘 | 边缘翻页 | `edge_turn_page` | 翻页输入方式 |
| 翻页 | 翻页方式 | 边缘 | 边缘宽度 | `edge_width_percent` | 依赖边缘翻页开启 |
| 翻页 | 翻页方式 | 自动 | 自动翻页 | `auto_page_turn` | 翻页输入方式 |
| 翻页 | 翻页方式 | 自动 | 自动翻页间隔 | `auto_page_turn_interval` | 依赖自动翻页开启 |
| 翻页 | 触控区域 | 区域 | 手势区域编辑 | `gesture_config` | 通过编辑器写入 |
| 翻页 | 触控区域 | 区域 | 左侧区域比例 | `left_zone_ratio` | 与手势区域同组 |
| 翻页 | 触控区域 | 反馈 | 触觉反馈 | `haptic_feedback` | 触控反馈 |
| 翻页 | 翻页动效 | 类型 | 翻页动画类型 | `page_anim_type` | 从显示模式迁入翻页 |
| 翻页 | 翻页动效 | 速度 | 翻页动画速度 | `page_anim_speed` | 从显示模式迁入翻页 |
| 辅助 | 护眼 | 色温 | 色温 | `color_temperature` | 与顶部护眼快捷重复，但主体归入辅助 |
| 辅助 | 护眼 | 提醒 | 护眼提醒间隔 | `eye_care_reminder_interval` | 与色温合并为护眼 |
| 辅助 | 屏幕状态 | 显示 | 沉浸模式 | `immersive_mode` | 系统显示状态 |
| 辅助 | 屏幕状态 | 常亮 | 屏幕常亮 | `keep_screen_on` | 系统显示状态 |
| 辅助 | 屏幕状态 | 方向 | 方向锁定 | `orientation_lock` | 与顶部横屏快捷重复，但主体归入辅助 |
| 辅助 | 阅读形态 | 纸张 | 背景纹理 | `background_texture` | 从布局迁入，纸张视觉体验 |
| 辅助 | 阅读形态 | 分页 | 双页模式 | `dual_page_mode` | 从布局迁入，阅读形态切换 |

## 补充：未暴露/隐藏配置项

为了保证配置项的 100% 覆盖率，以下是存在于 `ReaderSettingRegistry.kt` 和 `ReaderPreferences.kt` 中，但当前可能未直接在 UI 中暴露，或者设计为高级设置的项目：

| 推荐归属一级 | 推荐归属二级 | 推荐三级 | 配置项 | 当前 key | 归并说明 |
|---|---|---|---|---|---|
| 布局 | 标题 | 样式 | 标题专属字体 | `title_font` | 独立于正文字体之外的标题字体 |
| 辅助 | 屏幕状态 / 高级 | 性能 | 渲染优化 | `optimize_render` | 底层渲染优化开关，目前未暴露 UI |
| 布局 | 颜色/主题 | 自定义 | 自定义背景色 | `custom_background_color` | 自定义主题色的高级配置 |
| 布局 | 颜色/主题 | 自定义 | 自定义正文颜色 | `custom_text_color` | 自定义主题色的高级配置 |
| 布局 | 颜色/主题 | 自定义 | 自定义标题颜色 | `custom_title_color` | 自定义主题色的高级配置 |
| 布局 | 颜色/主题 | 自定义 | 自定义页眉页脚色 | `custom_header_footer_color` | 自定义主题色的高级配置 |

> 备注：`brightness`（亮度）和 `background_color`（基础主题色）明确属于顶部快捷操作区，故不列入此面板的卡片区配置项。

## 布局草图

```text
Tab：排版
  Card：字体
    Divider：字体
    Divider：字重
  Card：正文排版
    Divider：字号
    Divider：行段
    Divider：方向
    Divider：对齐
  Card：文本处理
    Divider：转换
    Divider：清理
    Divider：增强
    Divider：兼容

Tab：布局
  Card：正文区域
    Divider：边距
  Card：标题
    Divider：样式
    Divider：边距
  Card：页眉页脚
    SegmentedControl：页眉 | 页脚
    Divider：显示
    Divider：内容
    Divider：样式
    Divider：边距
  Card：边距方案
    Divider：预设
    Divider：同步
    Divider：重置

Tab：翻页
  Card：翻页方式
    Divider：按键
    Divider：边缘
    Divider：自动
  Card：触控区域
    Divider：区域
    Divider：反馈
  Card：翻页动效
    Divider：类型
    Divider：速度

Tab：辅助
  Card：护眼
    Divider：色温
    Divider：提醒
  Card：屏幕状态
    Divider：显示
    Divider：常亮
    Divider：方向
  Card：阅读形态
    Divider：纸张
    Divider：分页
```

## 三级分隔线规范

`SettingsSectionDivider` 只接收 `title`，不接收 note。

视觉规则：

1. 文本位于最左侧。
2. 分隔线位于文本右侧，并与文本垂直居中。
3. 使用弱文本色，不使用强调色，避免被误解为可点击控件。
4. 标题控制在 2-4 个中文字符。
5. 第一组三分隔线可保留，以保持卡片内部结构一致。

示例：

```kotlin
SettingsSectionDivider("内容")
SlotMatrix(...)

SettingsSectionDivider("样式")
InkStepperSlider(...)
SwitchRow(...)
```

## 代码文件拆分方案

### 当前文件结构

```
app/src/main/java/com/shuli/reader/feature/reader/settings/panel/tabs/
├── TypeAndFontTab.kt      # 字体排版（正文/页眉页脚/标题/字体/高级排版）
├── AppearanceTab.kt       # 外观显示（页眉页脚/色温/显示模式）
└── BehaviorTab.kt         # 行为交互（翻页方式/触控区域/护眼/通用）
```

### 目标文件结构

```
app/src/main/java/com/shuli/reader/feature/reader/settings/panel/tabs/
├── Typesetting.kt      # 排版（字体/正文排版/文本处理）22项
├── Layout.kt           # 布局（正文区域/标题/页眉页脚/边距方案）27项
├── PageTurn.kt         # 翻页（翻页方式/触控区域/翻页动效）10项
└── Auxiliary.kt        # 辅助（护眼/屏幕状态/阅读形态）7项
```

### 文件拆分详情

#### 1. Typesetting.kt（排版）

**来源**：TypeAndFontTab.kt 部分 + BehaviorTab.kt 部分

| 二级 Card | 三级分组 | 来源文件 | 迁移说明 |
|-----------|----------|----------|----------|
| 字体 | 字体、字重 | TypeAndFontTab.kt | 从字体卡片迁入 |
| 正文排版 | 字号、行段、对齐、边距 | TypeAndFontTab.kt | 从正文卡片拆分 |
| 文本处理 | 转换、清理、增强、兼容 | TypeAndFontTab.kt | 从高级排版卡片迁入 |

**包含配置项**（22项）：
- 阅读字体、导入字体、删除字体、字重
- 正文字号、行距、段距、首行缩进、缩进单位、字距、竖排文本
- 正文对齐、底部对齐
- 繁简转换、中文排版、盘古空格、移除空行、清理章节标题、广告过滤
- 段落分隔线、Bionic Reading、保留原文缩进、EPUB样式覆盖

#### 2. Layout.kt（布局）

**来源**：TypeAndFontTab.kt 部分 + AppearanceTab.kt 部分

| 二级 Card | 三级分组 | 来源文件 | 迁移说明 |
|-----------|----------|----------|----------|
| 正文区域 | 边距 | TypeAndFontTab.kt | 从正文卡片拆出 |
| 标题 | 样式、边距 | TypeAndFontTab.kt | 从标题卡片迁入 |
| 页眉页脚 | 显示、内容、样式、边距 | TypeAndFontTab.kt + AppearanceTab.kt | 合并两个文件的页眉页脚 |
| 边距方案 | 预设、同步、重置 | TypeAndFontTab.kt | 从边距预设卡片迁入 |

**包含配置项**（27项）：
- 正文边距
- 标题字号、标题字号偏移、标题对齐、标题上边距、标题下边距、标题边距
- 页眉/页脚可见性、显示进度、页眉/页脚内容（6项）、页眉/页脚字号比例、页眉页脚透明度、进度样式、页眉/页脚分隔线、页眉/页脚边距
- 紧凑/标准/宽松预设、统一左右边距、重置边距

#### 3. PageTurn.kt（翻页）

**来源**：BehaviorTab.kt 部分 + AppearanceTab.kt 部分

| 二级 Card | 三级分组 | 来源文件 | 迁移说明 |
|-----------|----------|----------|----------|
| 翻页方式 | 按键、边缘、自动 | BehaviorTab.kt | 从翻页方式卡片迁入 |
| 触控区域 | 区域、反馈 | BehaviorTab.kt | 从触控区域卡片迁入 |
| 翻页动效 | 类型、速度 | AppearanceTab.kt | 从显示模式卡片拆出 |

**包含配置项**（10项）：
- 音量键翻页、边缘翻页、边缘宽度
- 自动翻页、自动翻页间隔
- 手势区域编辑、左侧区域比例、触觉反馈
- 翻页动画类型、翻页动画速度

#### 4. Auxiliary.kt（辅助）

**来源**：BehaviorTab.kt 部分 + AppearanceTab.kt 部分

| 二级 Card | 三级分组 | 来源文件 | 迁移说明 |
|-----------|----------|----------|----------|
| 护眼 | 色温、提醒 | BehaviorTab.kt + AppearanceTab.kt | 合并色温和护眼提醒 |
| 屏幕状态 | 显示、常亮、方向 | BehaviorTab.kt | 从通用卡片迁入 |
| 阅读形态 | 纸张、分页 | AppearanceTab.kt | 从布局迁入，阅读体验相关 |

**包含配置项**（7项）：
- 色温、护眼提醒间隔
- 沉浸模式、屏幕常亮、方向锁定
- 背景纹理、双页模式

### 文件操作清单

| 操作 | 文件 | 说明 |
|------|------|------|
| **新建** | `tabs/Typesetting.kt` | 排版 Tab |
| **新建** | `tabs/Layout.kt` | 布局 Tab |
| **新建** | `tabs/PageTurn.kt` | 翻页 Tab |
| **新建** | `tabs/Auxiliary.kt` | 辅助 Tab |
| **删除** | `tabs/TypeAndFontTab.kt` | 拆分到排版 + 布局 |
| **删除** | `tabs/AppearanceTab.kt` | 拆分到布局 + 翻页 + 辅助 |
| **删除** | `tabs/BehaviorTab.kt` | 拆分到翻页 + 辅助 |

### 迁移映射表

| 原文件 | 原卡片 | 目标文件 | 目标卡片 |
|--------|--------|----------|----------|
| TypeAndFontTab.kt | 字体卡片 | Typesetting.kt | 字体 |
| TypeAndFontTab.kt | 正文卡片（排版部分） | Typesetting.kt | 正文排版 |
| TypeAndFontTab.kt | 正文卡片（边距部分） | Layout.kt | 正文区域 |
| TypeAndFontTab.kt | 标题卡片 | Layout.kt | 标题 |
| TypeAndFontTab.kt | 页眉页脚卡片 | Layout.kt | 页眉页脚 |
| TypeAndFontTab.kt | 边距预设卡片 | Layout.kt | 边距方案 |
| TypeAndFontTab.kt | 高级排版卡片 | Typesetting.kt | 文本处理 |
| AppearanceTab.kt | 页眉页脚卡片 | Layout.kt | 页眉页脚（合并） |
| AppearanceTab.kt | 色温卡片 | Auxiliary.kt | 护眼 |
| AppearanceTab.kt | 显示模式卡片（背景/双页） | Auxiliary.kt | 阅读形态 |
| AppearanceTab.kt | 显示模式卡片（翻页动效） | PageTurn.kt | 翻页动效 |
| BehaviorTab.kt | 翻页方式卡片 | PageTurn.kt | 翻页方式 |
| BehaviorTab.kt | 触控区域卡片 | PageTurn.kt | 触控区域 |
| BehaviorTab.kt | 护眼卡片 | Auxiliary.kt | 护眼（合并） |
| BehaviorTab.kt | 通用卡片 | Auxiliary.kt | 屏幕状态 |

### 注意事项

1. **页眉页脚合并**：TypeAndFontTab.kt 和 AppearanceTab.kt 都有页眉页脚卡片，需要合并到 LayoutTab.kt
2. **护眼合并**：BehaviorTab.kt 的护眼卡片和 AppearanceTab.kt 的色温卡片需要合并到 AuxiliaryTab.kt
3. **显示模式拆分**：AppearanceTab.kt 的显示模式卡片需要拆分到翻页（动效）和辅助（背景/双页）
4. **桥接映射**：ReaderSettingsModal.kt 的 bridgeSettingChange 不需要修改，只改 Tab 文件
5. **i18n 字符串**：可能需要新增 Tab 名称的 i18n 字符串（排版/布局/翻页/辅助）

## 实现注意

1. `SettingsTab` 当前只有 3 个 Tab：字体排版、外观显示、行为交互。实现重构时需要改为 4 个 Tab：排版、布局、翻页、辅助。
2. `preserve_original_indent` 已在 `ReaderSettingsModal.bridgeSettingChange` 中补齐分发桥接。
3. 页眉页脚当前被拆在 `TypeAndFontTab` 和 `AppearanceTab`，重构时应合并到同一个布局卡片内。
4. `page_anim_type` 和 `page_anim_speed` 当前位于显示模式卡片，重构时应迁入翻页动效。
5. `color_temperature` 当前既有主体滑杆，也有顶部护眼快捷。顶部快捷可保留，但主体归属只保留在 `辅助 > 护眼`。
6. `orientation_lock` 当前既有主体设置，也有顶部横屏快捷。顶部快捷可保留，但主体归属只保留在 `辅助 > 屏幕状态`。
7. 底部“恢复默认/清除本书设置”保持为全局动作，不放进任何 `SettingsCard`。
