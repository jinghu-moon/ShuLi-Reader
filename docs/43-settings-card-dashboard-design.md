# 设置弹窗卡片仪表盘设计文档

## 设计理念

设置弹窗采用**卡片仪表盘 + 全屏详情**的两层架构：

- **第一层**：底部弹窗，展示卡片网格，每张卡片用最适合自身内容的排版展示当前状态
- **第二层**：点击卡片后进入全屏设置页，展示该卡片的完整配置项

核心原则：
- 每张卡片的内部排版**按内容特性独立设计**，不套用统一模板
- 信息密度高但不拥挤，好看优先于规整
- 让用户一眼看到"现在是什么状态"，而不只是"这里有个设置入口"

## 整体布局结构

```
┌─────────────────────────────────┐
│          Drag Handle            │
├─────────────────────────────────┤
│  Peek 区                        │
│  [全局 | 本书]  ○○○○○  ☀ 🔒    │
├─────────────────────────────────┤
│  [ 排版 | 布局 | 翻页 | 辅助 ]  │ ← PrimaryTabRow
├─────────────────────────────────┤
│                                 │
│   卡片网格区（每 Tab 独立设计）   │
│                                 │
└─────────────────────────────────┘
```

## 卡片基础规范

| 属性 | 值 |
|------|------|
| 圆角 | 16dp |
| 内边距 | 14dp |
| 卡片间距 | 10dp |
| 列数 | 2（等宽），部分卡片跨列 |
| 背景 | surfaceContainer（比底板略深一层） |
| 点击涟漪 | 有，暗示整卡片可点击 |
| 导航指示 | 右上角 chevron_right，12dp，textTertiary |

## 排版 Tab — 3 张卡片

### ① 字体（半宽·左列）

展示当前字体的视觉样本，让用户"看到"字体而非只是读名字。

```
┌──────────────────────┐
│ 永   鸿蒙黑体     ▸  │
│      Light           │
└──────────────────────┘
```

**排版策略**：
- 左侧：一个大号汉字「永」作为字体预览，使用当前阅读字体渲染，32sp，textPrimary
- 右侧上：字体名称，bodyMedium，textPrimary
- 右侧下：字重英文名（Light/Regular/Medium/Bold），labelSmall，textTertiary
- 右上角：chevron 箭头

**为什么这样设计**：汉字「永」包含横竖撇捺点钩，一个字即可感知字体风格。比纯文本名称直观得多。

---

### ② 正文排版（半宽·右列）

用**微缩行文预览**展示字号和间距的实际效果。

```
┌──────────────────────┐
│ 正文排版           ▸  │
│ ┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄    │
│ ┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄    │
│ ┄┄┄┄┄┄┄┄┄┄          │
│                      │
│ 28sp · 行1.5 · 段12  │
└──────────────────────┘
```

**排版策略**：
- 顶部：标题「正文排版」，labelSmall，textTertiary
- 中部：3-4 条细灰色横线模拟文本行，行间距和段间距按比例缩放真实值。使用 Canvas 绘制，颜色 textTertiary，alpha 0.3
- 底部：参数标注，labelSmall，textSecondary，格式 `{字号}sp · 行{行距} · 段{段距}`

**为什么这样设计**：抽象的行线比数字更直觉地传达"间距感"。用户能看出当前是紧凑还是宽松。

---

### ③ 文本处理（跨列·满宽）

用**标签芯片组**展示已开启的文本处理功能。

```
┌──────────────────────────────────────────────┐
│ ⚙ 文本处理                                ▸  │
│                                              │
│  ┌──────┐ ┌──────┐ ┌────────┐ ┌──────┐      │
│  │繁→简 │ │ 盘古 │ │去空行  │ │Bionic│      │
│  └──────┘ └──────┘ └────────┘ └──────┘      │
└──────────────────────────────────────────────┘
```

**排版策略**：
- 顶行：图标（auto_fix_high）+ 标题，bodyMedium，textPrimary
- 下方：FlowRow 排列的 AssistChip，每个 chip 代表一个已开启的处理功能
- Chip 样式：FilledTonalChip，labelSmall，圆角 8dp，背景 primaryContainer
- 全部关闭时：显示一行文字「所有处理已关闭」，textTertiary

**为什么这样设计**：文本处理是一组独立的开关功能，数量可变（0-9 个）。Chip 标签比纯文本罗列更容易扫视，且能直观看出"开了几个"。

---

## 布局 Tab — 4 张卡片

### ① 正文区域（半宽·左列）

用**微缩页面示意图**可视化边距。

```
┌──────────────────────┐
│ 正文区域           ▸  │
│  ┌─────────────┐     │
│  │ ┌─────────┐ │     │
│  │ │░░░░░░░░░│ │     │
│  │ │░░░░░░░░░│ │     │
│  │ └─────────┘ │     │
│  └─────────────┘     │
│     24 · 24 · 20     │
└──────────────────────┘
```

**排版策略**：
- 顶部：标题，labelSmall，textTertiary
- 中部：Canvas 绘制一个矩形嵌套图，外框代表屏幕，内框代表正文区域，四边距离按比例映射真实 bodyBox。内框填充 primaryContainer alpha 0.2
- 底部：上下·左右 数值，labelSmall，textSecondary

**为什么这样设计**：边距是空间关系，示意图比四个数字有效 10 倍。用户一眼看出"留白是否均匀"。

---

### ② 标题（半宽·右列）

展示标题相对正文的字号关系和对齐方式。

```
┌──────────────────────┐
│ 标题样式           ▸  │
│                      │
│       标题文本        │  ← 模拟标题（居中/左对齐）
│  ┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄   │  ← 模拟正文行
│  ┄┄┄┄┄┄┄┄┄┄┄        │
│                      │
│    +4sp · 居中        │
└──────────────────────┘
```

**排版策略**：
- 顶部：标题，labelSmall，textTertiary
- 中部：一行较大较粗的文字「标题文本」+ 下方两条细线模拟正文。「标题文本」的对齐方式随 `titleAlign` 变化（左/中/右）
- 底部：参数标注 `+{offset}sp · {对齐}`

**为什么这样设计**：标题的核心属性是"比正文大多少"和"放在哪"，微缩预览直接呈现这个关系。

---

### ③ 页眉页脚（跨列·满宽）

用**双行槽位映射**直观显示内容分配。

```
┌──────────────────────────────────────────────┐
│ 页眉页脚                                   ▸  │
│                                              │
│  页眉  [书名]     [章节]     [进度]          │
│  ─────────────────────────────────────────   │
│  页脚  [页码]     [电量]     [时间]          │
│                                              │
└──────────────────────────────────────────────┘
```

**排版策略**：
- 顶行：标题，bodyMedium，textPrimary
- 中部：两行三列布局，模拟真实的页眉/页脚槽位分配
  - 每个槽位用圆角矩形背景 + 文字标签表示，像迷你 Chip
  - 如果该槽位为「无」则显示为虚线框
  - 如果页眉/页脚整体隐藏，该行显示为灰色删除线样式
- 中间一条细分隔线代表正文区域

**为什么这样设计**：页眉页脚的核心是"左中右各放了什么"，三列布局直接对应真实结构，比文字描述清晰。

---

### ④ 边距方案（半宽·左列第二行）

用**图标化预设标签**表示当前方案。

```
┌──────────────────────┐
│ 边距方案           ▸  │
│                      │
│    ╔══════════╗      │
│    ║  标 准   ║      │
│    ╚══════════╝      │
└──────────────────────┘
```

**排版策略**：
- 顶部：标题，labelSmall，textTertiary
- 中部：一个大号 Chip 或徽章，显示当前预设名称（紧凑/标准/宽松/自定义）
- Chip 背景：primaryContainer，文字 onPrimaryContainer，bodyLarge
- 如果是「自定义」状态：chip 使用 outlinedBorder 样式 + textSecondary

**为什么这样设计**：边距方案只有一个关键信息——"当前用的是哪个"。一个醒目的标签比一行小字更好找。

---

## 翻页 Tab — 3 张卡片

### ① 翻页方式（半宽·左列）

用**图标列表**展示已激活的翻页输入方式。

```
┌──────────────────────┐
│ 翻页方式           ▸  │
│                      │
│  🔊 音量键翻页       │
│  ◧  边缘翻页 15%    │
└──────────────────────┘
```

**排版策略**：
- 顶部：标题，labelSmall，textTertiary
- 下方：每个已开启的翻页方式占一行：小图标 + 名称 + 参数值
  - 音量键：`volume_up` 图标
  - 边缘翻页：`side_navigation` 图标 + 百分比
  - 自动翻页：`timer` 图标 + 间隔秒数
- 只显示已开启的，全关时显示「仅手势翻页」

**为什么这样设计**：翻页方式是一组可叠加的输入源，图标+文字列表让"开了哪些"一目了然。

---

### ② 触控区域（半宽·右列）

用**微缩手机屏幕分区图**可视化触控区域划分。

```
┌──────────────────────┐
│ 触控区域           ▸  │
│  ┌──┬──────┬──┐      │
│  │← │  菜单 │→ │      │
│  │  │      │  │      │
│  │30│      │30│      │
│  └──┴──────┴──┘      │
│      触觉反馈 ✓       │
└──────────────────────┘
```

**排版策略**：
- 顶部：标题，labelSmall，textTertiary
- 中部：Canvas 绘制一个竖向矩形（模拟手机屏幕），内部分三列，左右列着色表示翻页区域，中间为菜单区域。左右列标注百分比数字
  - 左区：secondaryContainer 填充
  - 右区：secondaryContainer 填充
  - 中区：无填充（留白）
- 底部：触觉反馈状态，labelSmall + 对勾/叉号

**为什么这样设计**：触控区域本质是空间分割，图形化展示比"左30%"的文字自然太多了。

---

### ③ 翻页动效（跨列·满宽）

用**动画类型名 + 速度指示条**展示。

```
┌──────────────────────────────────────────────┐
│ 翻页动效                                   ▸  │
│                                              │
│  📖 仿真翻页                                 │
│  速度  ▓▓▓▓▓▓▓░░░░  标准                    │
└──────────────────────────────────────────────┘
```

**排版策略**：
- 顶行：标题，bodyMedium，textPrimary
- 第二行：大号动画类型图标 + 动画类型名称（仿真翻页/覆盖/滑动/渐变/无）
  - 每种类型对应不同图标：仿真→`menu_book`，覆盖→`layers`，滑动→`swipe_left`，渐变→`gradient`，无→`block`
- 第三行：一个微型进度条表示速度等级，旁边标注速度名称
  - 进度条用 LinearProgressIndicator 的 determinate 模式
  - 慢/标准/快 → 33%/66%/100%

**为什么这样设计**：动效有两个维度（类型+速度），用类型图标+速度条分开展示比一行文字"仿真翻页·标准"更有层次感。

---

## 辅助 Tab — 3 张卡片

### ① 护眼（半宽·左列）

用**色温圆环**可视化色温强度。

```
┌──────────────────────┐
│ 护眼               ▸  │
│                      │
│      ◐  40%          │  ← 半圆色温环
│                      │
│  提醒  30分钟         │
└──────────────────────┘
```

**排版策略**：
- 顶部：标题，labelSmall，textTertiary
- 中部：Canvas 绘制一个弧形（类似圆环进度），弧度比例 = 色温值/100，渐变色从透明到暖橙色。弧旁标注百分比
- 底部：护眼提醒间隔（如果 > 0），icon `alarm` + 文字
- 色温为 0 且无提醒时：中部显示图标 `visibility` + 文字「未开启」

**为什么这样设计**：色温是一个 0-100 的连续值，弧形/圆环比文字"40%"更有氛围感，也暗示它是一个可调节的滑块设置。

---

### ② 屏幕状态（半宽·右列）

用**状态图标矩阵**展示各开关状态。

```
┌──────────────────────┐
│ 屏幕状态           ▸  │
│                      │
│  🖥 沉浸       ✓     │
│  💡 常亮       ✓     │
│  🔒 竖屏锁定   ✗     │
└──────────────────────┘
```

**排版策略**：
- 顶部：标题，labelSmall，textTertiary
- 下方：三行，每行一个状态项：图标 + 名称 + 开关状态指示
  - 开启：textPrimary + 对勾（primary 色）
  - 关闭：textTertiary + 叉号
  - 图标：沉浸→`fullscreen`，常亮→`brightness_high`，方向锁定→`screen_lock_rotation`

**为什么这样设计**：屏幕状态是三个独立开关，没有数值只有开/关。清单式列出最直白，配合颜色区分让已开启的项突出。

---

### ③ 阅读形态（跨列·满宽）

用**微缩双页/单页预览图**展示当前模式。

```
┌──────────────────────────────────────────────┐
│ 阅读形态                                   ▸  │
│                                              │
│  ┌─────┐ ┌─────┐       纹理：牛皮纸         │
│  │░░░░░│ │░░░░░│       模式：双页            │
│  │░░░░░│ │░░░░░│                             │
│  └─────┘ └─────┘                             │
└──────────────────────────────────────────────┘
```

**排版策略**：
- 顶行：标题，bodyMedium，textPrimary
- 左侧：Canvas 绘制单页或双页示意图
  - 单页模式：一个矩形，内部填充纹理色或图案
  - 双页模式：两个并排矩形
  - 矩形填充色 = 当前纹理的代表色（牛皮纸→暖黄，羊皮纸→米白，无纹理→surfaceVariant）
- 右侧：两行参数标注
  - 纹理：{纹理名称}
  - 模式：单页/双页

**为什么这样设计**：阅读形态影响的是视觉"画面感"，一个缩略图比任何文字描述都直观。用户能直接看出"现在看到的是单页还是双页"。

---

## 设计差异化总结

| 卡片 | 内部排版风格 | 核心可视化手段 |
|------|-------------|---------------|
| 字体 | 字体样本预览 | 大号汉字「永」实际渲染 |
| 正文排版 | 微缩行文 | Canvas 行线模拟间距 |
| 文本处理 | 标签芯片组 | FlowRow + AssistChip |
| 正文区域 | 嵌套矩形图 | Canvas 边距比例可视化 |
| 标题 | 标题+正文对比 | 大小字 + 对齐位置 |
| 页眉页脚 | 双行槽位映射 | 三列 Chip 布局 |
| 边距方案 | 大号预设徽章 | 突出 Chip |
| 翻页方式 | 图标状态列表 | 小图标 + 已开启项 |
| 触控区域 | 手机分区示意 | Canvas 三列分割图 |
| 翻页动效 | 类型图标+速度条 | Icon + LinearProgress |
| 护眼 | 色温弧形 | Canvas 圆弧渐变 |
| 屏幕状态 | 开关清单 | 对勾/叉号列表 |
| 阅读形态 | 页面缩略图 | Canvas 单/双页矩形 |

## 图标选型

| 卡片 | Material Symbol | 用途 |
|------|----------------|------|
| 字体 | 不使用独立图标 | 「永」字本身即图标 |
| 正文排版 | 不使用独立图标 | 行线图形即视觉锚点 |
| 文本处理 | `auto_fix_high` | 标题前的功能标识 |
| 正文区域 | 不使用独立图标 | 嵌套矩形即视觉锚点 |
| 标题 | 不使用独立图标 | 预览文字即视觉锚点 |
| 页眉页脚 | 不使用独立图标 | 槽位图即视觉锚点 |
| 边距方案 | 不使用独立图标 | 预设徽章即视觉锚点 |
| 翻页方式 | `volume_up` / `side_navigation` / `timer` | 行内图标 |
| 触控区域 | 不使用独立图标 | 分区图即视觉锚点 |
| 翻页动效 | `menu_book` / `layers` / `swipe_left` / `gradient` | 动画类型图标 |
| 护眼 | `alarm`（提醒行） | 仅提醒行使用 |
| 屏幕状态 | `fullscreen` / `brightness_high` / `screen_lock_rotation` | 行内图标 |
| 阅读形态 | 不使用独立图标 | 页面缩略图即视觉锚点 |

设计原则：**当卡片已经有图形化预览时，不再额外加标题图标**，避免视觉噪音。图标只在需要标识列表行或动画类型时使用。

## 交互规范

### 点击行为

| 操作 | 行为 |
|------|------|
| 点击卡片任意区域 | 导航到全屏设置详情页 |
| 长按卡片 | 无操作 |
| Tab 切换 | 水平滑动动画 |

### 全屏详情页

进入方式：点击卡片 → Compose Navigation destination

详情页结构复用 42 号文档定义的 `SettingsSectionDivider` + `SettingRow` 组件组合，从卡片容器移入全屏 Scaffold：

```
┌─────────────────────────────────┐
│  ← {卡片标题}             重置   │  TopAppBar
├─────────────────────────────────┤
│                                 │
│  ─── 三级分组 ──────────────    │  SettingsSectionDivider
│  [配置项控件]                   │
│  [配置项控件]                   │
│                                 │
│  ─── 三级分组 ──────────────    │
│  [配置项控件]                   │
│                                 │
└─────────────────────────────────┘
```

### 状态同步

摘要及可视化元素由 `ReaderPreferences` State 驱动，Compose 自动 recompose。用户在详情页修改参数后返回时卡片即时刷新。

## 与现有架构的关系

- **42 号文档**：定义配置项归属、代码文件拆分方案、详情页内部结构
- **43 号文档（本文）**：定义卡片仪表盘层的差异化视觉设计

两层对应：每个 42 号文档中的「二级 Card」= 本文中的一张仪表盘卡片 = 一个全屏详情页入口。

## 工程补充设计

### 组件边界

当前代码中已有 `SettingsCard`，它的职责是承载详情页内部的配置项：标题、折叠、`SettingsSectionDivider`、`SettingRow`、滑杆列宽对齐等。仪表盘卡片不应复用或改造这个组件，否则会把「详情配置容器」和「入口摘要卡片」两种语义混在一起。

新增独立组件：

```text
app/src/main/java/com/shuli/reader/feature/reader/settings/panel/dashboard/SettingsDashboardCard.kt
```

职责划分：

| 组件 | 职责 | 不负责 |
|------|------|--------|
| `SettingsDashboardCard` | 仪表盘卡片壳：形状、背景、内边距、点击、涟漪、chevron、无障碍语义、测试标签、跨列标记 | 具体摘要内容、业务状态计算、详情页配置项 |
| 各卡片 Composable | 卡片内部可视化：字体样本、行线、槽位、进度条、状态列表等 | 外层点击样式、导航、全局网格规则 |
| `SettingsCard` | 详情页内的设置分组容器 | 仪表盘摘要入口 |
| `SettingsSectionDivider` / `SettingRow` | 详情页内三级分组和配置项 | 仪表盘卡片布局 |

### 卡片壳 API 草案

```kotlin
enum class DashboardCardSpan {
    Half,
    Full,
}

@Composable
fun SettingsDashboardCard(
    title: String,
    destination: SettingsDetailDestination,
    modifier: Modifier = Modifier,
    span: DashboardCardSpan = DashboardCardSpan.Half,
    contentDescription: String,
    showChevron: Boolean = true,
    header: (@Composable RowScope.() -> Unit)? = null,
    onClick: (SettingsDetailDestination) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
)
```

壳组件规则：

1. 统一使用 16dp 圆角、14dp 内边距、10dp 网格间距。
2. 默认 `Role.Button`，整卡片可点击，使用 `clickable` / `combinedClickable` 的 Material ripple。
3. 右上角默认显示 `chevron_right`，颜色 `textTertiary`，尺寸 12dp。
4. `contentDescription` 必填，不能只依赖 Canvas 或图形预览。
5. 通过 `Modifier.testTag("SettingsDashboardCard_$destination")` 暴露稳定测试标签。
6. 只接收 `destination` 和点击回调，不直接依赖 `NavController`。
7. 不读取 `ReaderPreferences`，不计算业务摘要，保持壳组件纯展示。

推荐调用方式：

```kotlin
SettingsDashboardCard(
    title = strings.fontCard,
    destination = SettingsDetailDestination.Font,
    contentDescription = summary.fontContentDescription,
    onClick = onOpenDetail,
) {
    FontDashboardCardContent(summary)
}
```

### 摘要状态模型

仪表盘卡片展示的是「当前状态摘要」，不应在 UI 内部临时拼接大量业务逻辑。建议为每张卡片建立轻量 summary，集中从 `ReaderPreferences`、字体列表、手势配置等状态派生。

```kotlin
@Immutable
data class FontCardSummary(
    val fontName: String,
    val weightName: String,
    val sampleText: String = "永",
    val contentDescription: String,
)

@Immutable
data class TextProcessingSummary(
    val enabledChips: List<String>,
    val contentDescription: String,
)
```

推荐文件：

```text
panel/dashboard/SettingsDashboardSummaries.kt
```

设计原则：

1. Summary 只读，不写设置。
2. Summary mapper 集中处理空状态、缺失字体、关闭状态、长文本截断等规则。
3. 卡片 Composable 只消费 summary，不直接读取完整 `ReaderPreferences`。
4. `contentDescription` 与视觉摘要在同一 mapper 中生成，避免无障碍语义与界面状态不一致。
5. 对数量可变的摘要使用上限策略，例如文本处理 chip 最多展示 5 个，超过显示 `+N`。

### 详情页导航策略

卡片点击不建议由卡片内部直接调用 `NavController`。推荐先采用 sheet 内部状态机：

```text
Dashboard(tab) -> Detail(destination) -> Dashboard(tab)
```

实现方式：

1. 在 `ReaderSettingsSheetContent` 内维护 `detailDestination: SettingsDetailDestination?`。
2. `detailDestination == null` 时显示 Peek 区、TabRow、仪表盘网格。
3. `detailDestination != null` 时显示详情页 Scaffold / TopAppBar，复用 42 号文档中的 `SettingsCard` + `SettingsSectionDivider` + `SettingRow`。
4. 使用 `AnimatedContent` 做横向切换，系统返回或顶部返回按钮将 `detailDestination` 置空。
5. 如果后续确实要接 Compose Navigation，应由上层注入 `onOpenSettingsDetail(destination)`，卡片壳仍不直接依赖导航组件。

这样做的好处是：保留当前 `ReaderSettingsModal` 的 modal 语义，避免 bottom sheet 尚未关闭时又 push 全屏 destination 导致返回栈复杂化。

### 网格容器

优先使用 `LazyVerticalGrid(GridCells.Fixed(2))`，暂不使用 `LazyVerticalStaggeredGrid`。

理由：

1. 当前卡片只有半宽和满宽两种跨度，普通 grid 足够表达。
2. `GridItemSpan(maxLineSpan)` 可以处理跨列卡片。
3. 普通 grid 行为更稳定，测试和无障碍遍历顺序更可控。
4. 只有当后续出现大量不等高瀑布流卡片时，再考虑 staggered grid。

网格规则：

```kotlin
LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
)
```

满宽卡片：

```kotlin
item(span = { GridItemSpan(maxLineSpan) }) {
    SettingsDashboardCard(span = DashboardCardSpan.Full, ...)
}
```

### 边界状态

落地前需要明确以下状态的卡片表现：

| 卡片 | 边界状态 | 展示规则 |
|------|----------|----------|
| 字体 | 当前字体已删除或不可用 | 显示「系统默认」，样本使用 fallback 字体 |
| 正文排版 | 开启竖排文本 | 行线预览改为竖向行列，摘要增加「竖排」 |
| 文本处理 | 全部关闭 | 显示「所有处理已关闭」 |
| 文本处理 | 开启项过多 | 最多展示 5 个 chip，其余显示 `+N` |
| 正文区域 | 自定义边距极端值 | 预览图保持最小正文区域，避免内框消失 |
| 页眉页脚 | 页眉或页脚隐藏 | 对应行置灰，并显示删除线或「隐藏」 |
| 边距方案 | 非预设组合 | 显示「自定义」，使用 outlined 样式 |
| 翻页方式 | 全部关闭 | 显示「仅手势翻页」 |
| 触控区域 | 左右比例极端 | 图形保持最小可见宽度，数字仍显示真实值 |
| 翻页动效 | 无动画 | 图标使用 `block`，速度条隐藏 |
| 护眼 | 色温 0 且无提醒 | 显示 `visibility` +「未开启」 |
| 屏幕状态 | 全部关闭 | 三项均使用 textTertiary，状态为叉号 |
| 阅读形态 | 无纹理 | 页面预览使用 `surfaceVariant` |

### 无障碍与响应式

1. 每张卡片最小点击高度不低于 72dp，详情入口卡片建议不低于 96dp。
2. 支持系统字体缩放，至少验证 `fontScale = 1.3` 和 `fontScale = 1.5`。
3. Canvas 内容必须有等价文本语义，不能只画图。
4. 图标仅作辅助，状态必须同时通过文字或语义表达。
5. 动画遵守系统「减少动态效果」设置；关闭时改用淡入淡出或无动画。
6. 深色主题下所有图形线条和 chip 背景需要使用 `LocalReaderColorScheme` 派生色，不写死浅色。
7. TalkBack 顺序按视觉顺序遍历：Peek 区 -> Tab -> 当前 Tab 卡片 -> 全局动作。

### 测试要求

1. Summary mapper 单元测试：覆盖空状态、极端值、长文本、缺失字体、全关/全开。
2. Compose UI 测试：点击每张卡片进入正确详情页，返回后保留当前 Tab。
3. 截图预览：四个 Tab 至少各一张默认状态预览。
4. 响应式预览：普通手机、窄屏、横屏或平板至少覆盖一种宽屏状态。
5. 无障碍检查：所有 `SettingsDashboardCard_*` 都有非空 contentDescription。

## 实现路径

1. 新建 `panel/dashboard/SettingsDashboardCard.kt`，实现仪表盘卡片壳。
2. 新建 `SettingsDetailDestination`，枚举 13 个详情入口。
3. 新建 `SettingsDashboardSummaries.kt`，集中派生 13 张卡片的摘要状态和无障碍文案。
4. 新建 `SettingsDashboardGrid`，基于 `LazyVerticalGrid(GridCells.Fixed(2))` 实现半宽/满宽卡片布局。
5. 按 Tab 实现 13 张卡片内容 Composable，优先放在四个 Tab 文件或 `dashboard/cards/` 下，不让卡片壳承载业务逻辑。
6. 修改 `ReaderSettingsSheetContent`：Tab 内容从详情配置项列表切换为仪表盘网格。
7. 新增 sheet 内部详情页状态 `detailDestination`，点击卡片后进入详情视图，返回时回到原 Tab。
8. 将现有 `Typesetting.kt`、`Layout.kt`、`PageTurn.kt`、`Auxiliary.kt` 中的配置项内容迁入对应详情页，继续复用 `SettingsCard`、`SettingsSectionDivider`、`SettingRow` 系列组件。
9. 补齐边界状态、无障碍语义、summary mapper 单元测试和 Compose UI 点击测试。
