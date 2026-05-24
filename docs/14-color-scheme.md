# 14 - 墨土配色方案

> 基于 `refer/motu_color_scheme.html` 的“墨土 · Mo Tu”色阶体系，并结合书里阅读器“阅读界面偏棕、非阅读界面偏白”的产品定位制定。本文档是后续主题代码、阅读器 Canvas 绘制、截图测试和设计验收的颜色依据。

## 一、设计结论

书里的配色不采用“一套色值覆盖所有界面”，而采用同源双体系：

| 界面域 | 主倾向 | 目标 | 策略 |
|------|------|------|------|
| 非阅读界面 | 偏白 | 干净、现代、信息层级清晰 | 暖白背景 + 白色卡片 + 灰棕文字 |
| 阅读界面 | 偏棕 | 沉浸、护眼、长时间舒适 | 纸感棕底 + 深棕正文 + 低刺激辅助色 |

二者保持一致的是色彩语言，而不是完全一致的色值：

- 同一套暖灰棕色阶。
- 同一个深棕主强调色。
- 同一组低饱和功能色。
- 同样避免纯黑、纯白、强蓝、强紫作为默认视觉基调。

核心原则：

- 黑白灰负责现代感和结构。
- 棕色负责阅读温度和品牌气质。
- 非阅读界面做“白纸上的管理界面”。
- 阅读界面做“纸书上的正文界面”。

## 二、基础色阶

基础色阶来自“墨土”方案：暖灰棕底色，含轻微棕色偏移，从近白到近黑。日常界面不直接使用纯黑白轴，纯黑白只用于高对比校验、打印或特殊 OLED 主题。

### 2.1 墨土 Primitive 色阶

| Token | 色值 | 用途 |
|------|------|------|
| `MoTuInk50` | `#F6F4F0` | 非阅读界面默认背景、浅色底层画布 |
| `MoTuInk100` | `#EAE5DC` | 次级背景、悬浮层、阅读浅纸底候选 |
| `MoTuInk200` | `#D4CCC0` | 分割线、弱描边、暗色主色 |
| `MoTuInk300` | `#B9AFA0` | 亮色描边、中弱提示 |
| `MoTuInk400` | `#9C9082` | 暗色次级文字、阅读页页眉页脚 |
| `MoTuInk500` | `#7D7162` | 阅读次级文字、弱图标 |
| `MoTuInk600` | `#5E5346` | 非阅读界面次级文字 |
| `MoTuInk700` | `#453B2E` | 亮色主色、主要按钮、阅读强调 |
| `MoTuInk800` | `#2C231A` | 阅读正文、暗色容器 |
| `MoTuInk900` | `#1A130B` | 亮色主文字、夜间阅读背景 |
| `MoTuInk950` | `#0C0804` | OLED 背景、极深反转层 |

### 2.2 纯黑白轴

| 色值 | 用途 |
|------|------|
| `#FFFFFF` | 仅用于非阅读界面卡片、主按钮文字、极少量高亮容器 |
| `#000000` | 不作为默认背景；仅用于 OLED 主题或对比校验 |

约束：

- 默认阅读背景不得使用 `#FFFFFF`。
- 默认夜间阅读背景不得使用 `#000000`。
- 默认正文不得使用纯黑或纯白。

## 三、非阅读界面配色

非阅读界面包括书架、设置、导入、搜索、统计、弹窗、底部面板、关于页等。它们承担信息管理职责，应偏白、清爽、层级清晰。

### 3.1 亮色模式

| 语义 Token | 色值 | 用途 |
|------|------|------|
| `AppBackground` | `#F6F4F0` | 页面背景 |
| `AppSurface` | `#FFFFFF` | 卡片、列表项、主要容器 |
| `AppSurfaceVariant` | `#EAE5DC` | 次级容器、筛选条、输入框背景 |
| `AppSurfaceContainer` | `#FFFFFF` | 弹窗、底部面板 |
| `AppTextPrimary` | `#1A130B` | 标题、正文主文字 |
| `AppTextSecondary` | `#5E5346` | 副标题、说明、图标 |
| `AppTextTertiary` | `#9C9082` | 时间、占位、弱提示 |
| `AppPrimary` | `#453B2E` | 主按钮、选中态、关键操作 |
| `AppOnPrimary` | `#FFFFFF` | 主按钮文字 |
| `AppOutline` | `#B9AFA0` | 输入框、卡片描边 |
| `AppDivider` | `#D4CCC0` | 分割线 |

### 3.2 暗色模式

| 语义 Token | 色值 | 用途 |
|------|------|------|
| `AppDarkBackground` | `#0C0804` | 页面背景 |
| `AppDarkSurface` | `#1A130B` | 卡片、列表项 |
| `AppDarkSurfaceVariant` | `#2C231A` | 次级容器、输入框背景 |
| `AppDarkSurfaceContainer` | `#2C231A` | 弹窗、底部面板 |
| `AppDarkTextPrimary` | `#EAE5DC` | 标题、正文主文字 |
| `AppDarkTextSecondary` | `#9C9082` | 副标题、说明、图标 |
| `AppDarkTextTertiary` | `#5E5346` | 弱提示 |
| `AppDarkPrimary` | `#D4CCC0` | 主按钮、选中态 |
| `AppDarkOnPrimary` | `#0C0804` | 主按钮文字 |
| `AppDarkOutline` | `#5E5346` | 输入框、卡片描边 |
| `AppDarkDivider` | `#453B2E` | 分割线 |

### 3.3 Material 3 映射

Compose 的 `MaterialTheme.colorScheme` 应优先服务非阅读界面：

| Material 字段 | 亮色 | 暗色 |
|------|------|------|
| `background` | `AppBackground` | `AppDarkBackground` |
| `surface` | `AppSurface` | `AppDarkSurface` |
| `surfaceVariant` | `AppSurfaceVariant` | `AppDarkSurfaceVariant` |
| `surfaceContainer` | `AppSurfaceContainer` | `AppDarkSurfaceContainer` |
| `onBackground` | `AppTextPrimary` | `AppDarkTextPrimary` |
| `onSurface` | `AppTextPrimary` | `AppDarkTextPrimary` |
| `onSurfaceVariant` | `AppTextSecondary` | `AppDarkTextSecondary` |
| `primary` | `AppPrimary` | `AppDarkPrimary` |
| `onPrimary` | `AppOnPrimary` | `AppDarkOnPrimary` |
| `outline` | `AppOutline` | `AppDarkOutline` |
| `outlineVariant` | `AppDivider` | `AppDarkDivider` |

## 四、阅读界面配色

阅读界面包括正文 Canvas、页眉页脚、阅读工具栏、目录、快速设置、亮度浮层、进度条、选区、高亮、书签标记。阅读界面不得简单复用 `MaterialTheme.colorScheme.background`，应使用独立 `ReaderColorScheme`。

### 4.1 纸感阅读主题

这是默认阅读主题。

| Reader Token | 色值 | 用途 |
|------|------|------|
| `ReaderPaperBackground` | `#EAE5DC` | 正文底色，偏棕纸感 |
| `ReaderPaperSurface` | `#F6F4F0` | 工具栏、轻浮层、目录背景 |
| `ReaderPaperTextPrimary` | `#2C231A` | 正文文字 |
| `ReaderPaperTextSecondary` | `#7D7162` | 页眉页脚、章节辅助文字 |
| `ReaderPaperTextTertiary` | `#9C9082` | 电量、时间、弱提示 |
| `ReaderPaperAccent` | `#453B2E` | 进度条、当前章节、选中控件 |
| `ReaderPaperDivider` | `#D4CCC0` | 细分割线、页脚弱线 |
| `ReaderPaperOverlay` | `#F6F4F0E6` | 阅读工具栏半透明底 |
| `ReaderPaperSelection` | `#453B2E33` | 文本选择背景 |
| `ReaderPaperHighlight` | `#D4CCC066` | 笔记高亮 |

说明：

- 正文使用 `#2C231A`，比 `#1A130B` 更柔和。
- 背景使用 `#EAE5DC`，比非阅读界面的 `#F6F4F0` 更偏纸感。
- 工具栏用半透明浅色覆盖，不使用纯白。

### 4.2 清爽阅读主题

适合喜欢更亮背景的用户，但仍避免纯白。

| Reader Token | 色值 | 用途 |
|------|------|------|
| `ReaderLightBackground` | `#F6F4F0` | 正文底色 |
| `ReaderLightSurface` | `#FFFFFF` | 浮层、目录 |
| `ReaderLightTextPrimary` | `#2C231A` | 正文文字 |
| `ReaderLightTextSecondary` | `#7D7162` | 页眉页脚 |
| `ReaderLightAccent` | `#453B2E` | 进度、选中 |
| `ReaderLightDivider` | `#D4CCC0` | 分割线 |

### 4.3 夜间阅读主题

默认夜间阅读偏深棕灰，不使用纯黑。

| Reader Token | 色值 | 用途 |
|------|------|------|
| `ReaderDarkBackground` | `#1A130B` | 正文底色 |
| `ReaderDarkSurface` | `#2C231A` | 工具栏、目录、浮层 |
| `ReaderDarkTextPrimary` | `#EAE5DC` | 正文文字 |
| `ReaderDarkTextSecondary` | `#9C9082` | 页眉页脚、辅助文字 |
| `ReaderDarkTextTertiary` | `#5E5346` | 弱提示 |
| `ReaderDarkAccent` | `#D4CCC0` | 进度、选中 |
| `ReaderDarkDivider` | `#453B2E` | 分割线 |
| `ReaderDarkOverlay` | `#2C231AE6` | 阅读工具栏半透明底 |
| `ReaderDarkSelection` | `#D4CCC033` | 文本选择背景 |
| `ReaderDarkHighlight` | `#9C908255` | 笔记高亮 |

### 4.4 OLED 阅读主题

OLED 是可选主题，不作为默认夜间模式。

| Reader Token | 色值 | 用途 |
|------|------|------|
| `ReaderOledBackground` | `#0C0804` | 正文底色 |
| `ReaderOledSurface` | `#1A130B` | 工具栏、目录 |
| `ReaderOledTextPrimary` | `#EAE5DC` | 正文文字 |
| `ReaderOledTextSecondary` | `#9C9082` | 页眉页脚 |
| `ReaderOledAccent` | `#D4CCC0` | 进度、选中 |
| `ReaderOledDivider` | `#453B2E` | 分割线 |

## 五、功能色

功能色独立于主色阶，但保持低饱和，避免破坏整体暖灰棕气质。

### 5.1 亮色功能色

| 状态 | 主色 | 背景 | 文字 | 用途 |
------|------|------|------|------|
| 成功 | `#2D7A52` | `#E8F4EE` | `#1A5035` | 导入成功、同步成功 |
| 警告 | `#9A6500` | `#F5ECD8` | `#6B4400` | 重复导入、缓存过大 |
| 错误 | `#9B3525` | `#F5E5E2` | `#6A1E12` | 导入失败、解析失败 |
| 信息 | `#3A607A` | `#E2EDF4` | `#1E3F55` | 提示、说明、帮助 |

### 5.2 暗色功能色

| 状态 | 主色 | 背景 | 文字 | 用途 |
------|------|------|------|------|
| 成功 | `#5DBE8A` | `#0E2A1E` | `#9ADDB5` | 导入成功、同步成功 |
| 警告 | `#E0A020` | `#2A1E00` | `#F5C860` | 重复导入、缓存过大 |
| 错误 | `#D46050` | `#2A0E0A` | `#ED9C8C` | 导入失败、解析失败 |
| 信息 | `#6AA0BE` | `#0A1E2A` | `#A0C8E0` | 提示、说明、帮助 |

使用约束：

- 功能色只用于状态表达，不作为品牌主色。
- 错误色不得用于普通删除按钮背景；删除默认使用文字按钮或弱强调，确认弹窗中再使用错误色。
- 信息色可以少量保留蓝灰，但不得替代主强调色。

## 六、关键对比度

以下组合已满足常规可访问性门槛：

| 组合 | 背景 | 前景 | 对比度 |
|------|------|------|------|
| 非阅读亮色主文字 | `#F6F4F0` | `#1A130B` | 16.74 |
| 非阅读亮色次级文字 | `#F6F4F0` | `#5E5346` | 6.82 |
| 亮色主按钮 | `#453B2E` | `#FFFFFF` | 10.95 |
| OLED 主文字 | `#0C0804` | `#EAE5DC` | 15.91 |
| OLED 次级文字 | `#0C0804` | `#9C9082` | 6.39 |
| 暗色主按钮 | `#D4CCC0` | `#0C0804` | 12.54 |

验收标准：

- 正文阅读文字对比度不低于 7:1。
- 普通 UI 正文对比度不低于 4.5:1。
- 大字号、图标、控件边界不低于 3:1。
- 次级文字可以弱化，但不得低于 4.5:1。

## 七、组件使用规则

### 7.1 书架

- 页面背景：`AppBackground`。
- 书籍卡片：`AppSurface`。
- 封面占位：可从 `MoTuInk100`、`MoTuInk200`、`MoTuInk300` 派生，避免花哨渐变。
- 当前阅读进度：`AppPrimary`。
- 筛选 Tab 选中态：`AppSurface` + `AppPrimary` 文字。
- 空态插画或图标：`AppTextTertiary`，不使用蓝紫色。

### 7.2 设置页

- 页面背景：`AppBackground`。
- 分组标题：`AppTextSecondary`，字号和字重控制层级，不靠强色。
- 开关选中：`AppPrimary`。
- Slider 激活轨道：`AppPrimary`。
- 危险操作：默认弱化，确认态使用 `state-error`。

### 7.3 阅读页

- 正文 Canvas 背景：使用 `ReaderColorScheme.background`。
- 正文文字：使用 `ReaderColorScheme.textPrimary`。
- 页眉页脚：使用 `ReaderColorScheme.textSecondary` 或 `textTertiary`，透明度不低于可读范围。
- 进度条：轨道用 `divider`，进度用 `accent`。
- 工具栏：使用 `overlay`，不要使用纯白或纯黑。
- 目录和快速设置：使用 `surface`，但整体色温跟随阅读主题。

### 7.4 弹窗与底部面板

- 非阅读入口弹窗：使用 App Token。
- 阅读页内弹窗：使用 Reader Token。
- 弹窗阴影应弱，优先用色块和描边建立层级。

## 八、代码落地建议

### 8.1 文件结构

建议在现有主题包中逐步形成以下结构：

```text
app/src/main/java/com/shuli/reader/ui/theme/
├── Color.kt              // Primitive + App semantic tokens
├── ReaderColor.kt        // ReaderColorScheme 与阅读主题 tokens
├── Theme.kt              // MaterialTheme 映射
└── Shape.kt / Type.kt
```

### 8.2 Kotlin Token 命名

```kotlin
val MoTuInk50 = Color(0xFFF6F4F0)
val MoTuInk100 = Color(0xFFEAE5DC)
val MoTuInk200 = Color(0xFFD4CCC0)
val MoTuInk300 = Color(0xFFB9AFA0)
val MoTuInk400 = Color(0xFF9C9082)
val MoTuInk500 = Color(0xFF7D7162)
val MoTuInk600 = Color(0xFF5E5346)
val MoTuInk700 = Color(0xFF453B2E)
val MoTuInk800 = Color(0xFF2C231A)
val MoTuInk900 = Color(0xFF1A130B)
val MoTuInk950 = Color(0xFF0C0804)
```

### 8.3 ReaderColorScheme

```kotlin
data class ReaderColorScheme(
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val divider: Color,
    val overlay: Color,
    val selection: Color,
    val highlight: Color,
)
```

阅读器 Canvas、ReaderScreen、ReaderToolbar、DirectoryPanel、QuickSettingsPanel 都应依赖 `ReaderColorScheme`，不要直接读取 `MaterialTheme.colorScheme`。

### 8.4 迁移当前代码

当前 `Color.kt` 中 Geist 蓝色体系应逐步替换：

| 当前 | 建议 |
|------|------|
| `GeistBackground #080808` | `MoTuInk950` 或 `AppDarkBackground` |
| `GeistAccent #4F8CFF` | `MoTuInk700` / `AppPrimary` |
| `LightAccent #3B82F6` | `MoTuInk700` |
| `PaperAccent #D97706` | `MoTuInk700`，减少橙色倾向 |
| `PaperText #3A3028` | `MoTuInk800` |

迁移顺序：

1. 先新增 MoTu Primitive tokens，不删除旧变量。
2. 新增 App light/dark Material 映射。
3. 新增 ReaderColorScheme。
4. 阅读界面切换到 ReaderColorScheme。
5. 书架、设置等非阅读界面统一收敛到 MoTu 命名，不再新增旧式蓝色变量。
6. 截图测试稳定后，只保留 MoTu / Reader token，不回流旧 Geist 变量。

## 九、测试与验收

### 9.1 单元测试

- Token 对比度测试：正文、次级文字、按钮、功能色均满足阈值。
- ReaderColorScheme 映射测试：每个阅读主题都有完整字段。
- 暗色模式映射测试：非阅读界面和阅读界面不混用背景。

### 9.2 截图测试

至少覆盖以下截图：

- 书架亮色。
- 书架暗色。
- 设置亮色。
- 设置暗色。
- 阅读纸感主题。
- 阅读清爽主题。
- 阅读夜间主题。
- 阅读 OLED 主题。
- 阅读页工具栏展开。
- 阅读页目录和快速设置。

### 9.3 人工验收

- 连续阅读 30 分钟，默认纸感主题不刺眼。
- 夜间阅读不出现纯黑纯白高反差疲劳。
- 非阅读界面看起来干净，不显得满屏发黄。
- 阅读界面进入后能明显感知“纸书模式”。
- 功能色状态清晰，但不破坏整体暖灰棕气质。

## 十、禁止事项

- 禁止默认阅读背景使用纯白。
- 禁止默认夜间阅读背景使用纯黑。
- 禁止将蓝色作为主强调色。
- 禁止正文使用高饱和棕、橙、黄。
- 禁止为了“纸感”给所有非阅读页面套米黄色背景。
- 禁止用透明度过低的文字承担关键信息。
- 禁止阅读页直接依赖 App Material 背景色。

## 十一、最终决策

书里采用“墨土”作为主色体系：

- 非阅读界面：暖白主导，白色容器，灰棕文字，深棕强调。
- 阅读界面：纸感棕主导，深棕正文，低刺激辅助色。
- 暗色界面：暖黑棕灰，不默认纯黑。
- OLED：作为独立阅读主题提供。

这套方案能同时满足现代 App 的清晰管理体验和小说阅读器的长时间沉浸体验。
