# 20. 阅读设置弹窗重新设计方案

> 日期：2025-05-25

---

## 一、现状问题

### 1. 分类依据不合理，常用项分散

当前 6 个 Tab 按"控件类型"而非"用户场景"分类：

| Tab | 内容 | 问题 |
|-----|------|------|
| 字号 | 字号、字距、行距、段距、缩进 | ✅ 尚可 |
| 字体 | 字体、字重、对齐、简繁、中文分行 | ❌ "对齐"和"简繁"不属于字体概念 |
| 边距 | 上下边距、左右边距 | ❌ 仅 2 项，Tab 存在感太低 |
| 翻页 | 翻页动画、主题色、页眉开关、页脚开关、进度条 | ❌ "主题色"与翻页无关；页眉脚开关同时出现在两个 Tab |
| 页眉脚 | 标题样式（对齐/字号/间距）+ 页眉脚 3 槽位 + 透明度 + 进度条 | ❌ 内容最多最长，选项过于专业 |
| 更多 | 预设、屏幕常亮、音量键、边缘翻页、恢复默认 | ❌ "垃圾抽屉"型，剩余项堆积 |

**核心矛盾**：用户最常用的 3 件事——**字号 + 主题色 + 翻页动画**——分散在 3 个不同 Tab 里。

### 2. 布局问题

- **Tab 过多**（6 个），横向可滚动 Tab 在小屏上需要滑动才能看到后面的 Tab
- **Tab 图标+文字**占用过多垂直空间（约 72dp），挤压内容区
- **边距 Tab 仅 2 项**，点进去几乎是空页面
- **页眉脚 Tab 需长滚动**，标题样式、页眉 3 槽、页脚 3 槽、透明度全挤在一起

### 3. 无过渡动画

- Tab 切换时内容直接替换（`when (selectedTab)`），无 crossfade/slide 动画
- 滑块调整时，背后页面直接 reflow 重绘，视觉上感觉"闪烁"而非"流动"

---

## 二、竞品参考

### Apple Books (iOS 18)

**结构**：`Themes & Settings` → 单层面板，不分 Tab

| 区域 | 内容 |
|------|------|
| 顶部 | 字号 A-/A+ 两个按钮 + 翻页方式图标 |
| 主题 | 6 个预设主题色块（Quiet/Focus/Bold 等）横向排列 |
| 亮暗 | Light / Dark / Match Device / Match Surroundings |
| 自定义 | 展开二级：字体选择、Accessibility（行距/字距/词距/边距/对齐） |

**设计哲学**：
- **首屏放最常用**（字号 + 翻页 + 主题），一眼可见
- **高级选项折叠**在 "Customize" 按钮后，不干扰基础用户
- **无 Tab**，单页纵向滚动，层级清晰

### Kindle (2024)

**结构**：`Aa` 按钮 → 3 个 Tab

| Tab | 内容 |
|-----|------|
| Themes | Compact / Standard / Large 预设 + 自定义保存 |
| Font | 字体选择 + 字号滑块 + 粗细 |
| Layout | 行距 / 边距 / 对齐 / 分栏 |

还有一个 `More` 入口通向完整设置页。

**设计哲学**：
- **Tab ≤ 3 个**，不需要滚动
- **预设优先**（Theme tab 第一个），用户一键切换
- **高级项少**，排版微调只有 4 项

### 共性原则

1. **常用优先**：字号、主题/配色放在最显眼位置
2. **Tab 数量 ≤ 4**：避免横向滚动，减少认知负担
3. **分层展示**：基础选项首屏可见，高级选项折叠或放二级页
4. **即时预览**：调整后背后页面平滑过渡，而非突然重排

---

## 三、改进方案

### 3.1 重新分类：3 Tab + 常驻区

```
┌─────────────────────────────────────────┐
│  亮度  [─────●────────────] 50%         │  ← 常驻亮度栏（保留）
├─────────────────────────────────────────┤
│  ○ ○ ○ ● ○   主题色块（4-5个，常驻）     │  ← 新增：主题色提升到常驻区
├─────────────────────────────────────────┤
│  [ 排版 ]  [ 样式 ]  [ 设置 ]            │  ← 3 个文字 Tab（无图标）
├─────────────────────────────────────────┤
│                                         │
│  Tab 内容区（带 Crossfade 动画）          │
│                                         │
└─────────────────────────────────────────┘
```

### 3.2 Tab 内容重组

#### Tab 1：排版（最常用，打开即首屏）

按使用频率从高到低排列：

| 项 | 控件类型 | 说明 |
|----|---------|------|
| 字号 | 滑块 | 10-32sp |
| 行距 | 滑块 | 0.8-3.0 |
| 边距 | 上下 + 左右 两个滑块 | 合并原"边距"Tab |
| 段距 | 滑块 | 0-5.0 |
| 缩进 | 滑块 | 0-10 |
| 字距 | 滑块 | 0-0.2em |

> 将原"字号"Tab + 原"边距"Tab 合并。边距与字号/行距本质上都是"排版参数"，用户调节字号后通常会顺手调边距。

#### Tab 2：样式

| 项 | 控件类型 | 说明 |
|----|---------|------|
| 翻页动画 | 分段按钮 | 无/覆盖/平移/仿真 |
| 字体 | 选择器 | 系统/鸿蒙/LXGW |
| 字重 | 选择器 | 常规/粗体 |
| 对齐 | 分段按钮 | 左对齐/两端对齐 |
| 简繁转换 | 选择器 | 原始/简→繁/繁→简 |
| 中文分行 | 开关 | 标点避头尾 |

> 将翻页动画提到这个 Tab 最顶部（使用频率高），字体/字重/对齐等"外观样式"归在一起。

#### Tab 3：设置（低频）

| 分组 | 项 | 说明 |
|------|----|------|
| **页面元素** | 页眉 显示/隐藏 | 开关 |
| | 页脚 显示/隐藏 | 开关 |
| | 进度条 | 开关 |
| | 页眉脚透明度 | 滑块 |
| **页眉脚详细** | ▶ 页眉脚自定义 | 可展开/折叠 或 跳转子面板 |
| | （展开后）页眉左/中/右 槽位 | 选择器 |
| | 页脚左/中/右 槽位 | 选择器 |
| **标题样式** | ▶ 标题样式 | 可展开/折叠 |
| | （展开后）对齐、字号偏移、上距、下距 | |
| **行为** | 屏幕常亮 | 开关 |
| | 音量键翻页 | 开关 |
| | 边缘翻页 | 开关 |
| **预设** | 保存/加载/删除预设 | |
| | 恢复默认 | 按钮 |

> 低频项全部收拢。"页眉脚自定义"和"标题样式"使用**可折叠区块**（`ExpandableSection`），默认收起，避免长滚动。

### 3.3 常驻区设计

**主题色块**从"翻页"Tab 提升到亮度栏下方，成为常驻区域：

```kotlin
// 常驻：亮度
BrightnessBar(...)

// 常驻：主题色块
ThemeColorRow(
    currentTheme = prefs.backgroundColor,
    onThemeChange = actions.onThemeChange,
)

HorizontalDivider(...)

// Tab 区域
TabRow(...)
```

**理由**：主题色是仅次于亮度的高频操作（夜间/白天切换），不应藏在 Tab 里。

### 3.4 Tab 切换动画

**当前**：`when (selectedTab)` 硬切换，无动画。

**改进**：使用 `AnimatedContent` + `fadeIn/fadeOut`：

```kotlin
AnimatedContent(
    targetState = selectedTab,
    transitionSpec = {
        fadeIn(animationSpec = tween(200)) togetherWith
        fadeOut(animationSpec = tween(150))
    },
    label = "tab-content",
) { tab ->
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        when (tab) {
            TAB_LAYOUT -> LayoutPanel(...)
            TAB_STYLE -> StylePanel(...)
            TAB_SETTINGS -> SettingsPanel(...)
        }
    }
}
```

也可以结合 `slideInHorizontally` 做方向感知滑动：

```kotlin
transitionSpec = {
    if (targetState > initialState) {
        // 向右切换：新内容从右侧滑入
        slideInHorizontally { it / 4 } + fadeIn() togetherWith
        slideOutHorizontally { -it / 4 } + fadeOut()
    } else {
        // 向左切换：新内容从左侧滑入
        slideInHorizontally { -it / 4 } + fadeIn() togetherWith
        slideOutHorizontally { it / 4 } + fadeOut()
    }
}
```

### 3.5 Tab 样式简化

**当前**：`SecondaryScrollableTabRow` + 图标 + 文字（≈72dp 高）

**改进**：改用 `PrimaryTabRow`（固定宽度，不滚动）+ 纯文字（≈48dp 高）：

```kotlin
PrimaryTabRow(
    selectedTabIndex = selectedTab,
    containerColor = readerColors.surface,
    contentColor = readerColors.textPrimary,
) {
    listOf("排版", "样式", "设置").forEachIndexed { index, title ->
        Tab(
            selected = selectedTab == index,
            onClick = { selectedTab = index },
            text = {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                )
            },
        )
    }
}
```

**收益**：
- 3 个 Tab 不需要滚动，一目了然
- 去掉图标省 24dp 垂直空间
- Tab 栏高度从 ~72dp 降到 ~48dp，内容区多出 24dp

### 3.6 低频项折叠化（ExpandableSection）

页眉脚详细配置和标题样式使用可折叠区块：

```kotlin
@Composable
fun ExpandableSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column { content() }
        }
    }
}
```

---

## 四、页面变化过渡动画

### 问题

当前调整字号/行距/边距等排版参数时，页面直接 reflow 重绘。Canvas 内容突然变化，没有过渡。

### 方案：CanvasRecorder crossfade

在 `ReaderCanvasView` 中，当排版参数变化导致页面重建时，不直接替换渲染内容，而是：

1. **保存旧页面快照**：在 `setPage()` 被调用时，如果检测到是排版变化（而非翻页），将当前 `canvasRecorder` 的内容暂存为 `oldSnapshot`
2. **使用 alpha 动画过渡**：在 `onDraw` 中，绘制 `oldSnapshot`（alpha 从 1→0）和新页面（alpha 从 0→1），持续约 200ms

```kotlin
// ReaderCanvasView 中
private var crossfadeAnimator: ValueAnimator? = null
private var oldPageBitmap: Bitmap? = null

fun setPage(page: TextPage?, ..., isLayoutChange: Boolean = false) {
    if (isLayoutChange && currentPage != null) {
        // 捕获旧页面为 Bitmap
        oldPageBitmap = captureCurrentPageBitmap()
        // 启动 crossfade
        crossfadeAnimator?.cancel()
        crossfadeAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener { invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    oldPageBitmap?.recycle()
                    oldPageBitmap = null
                }
            })
            start()
        }
    }
    // 正常更新页面引用...
}

// onDraw 中
override fun onDraw(canvas: Canvas) {
    // 绘制新页面
    drawCurrentPage(canvas)
    
    // 如果有 crossfade，叠加旧页面
    oldPageBitmap?.let { old ->
        val alpha = (crossfadeAnimator?.animatedValue as? Float) ?: 0f
        if (alpha > 0f) {
            val paint = Paint().apply { this.alpha = (alpha * 255).toInt() }
            canvas.drawBitmap(old, 0f, 0f, paint)
        }
    }
}
```

### 轻量替代方案：Compose 层 crossfade

如果不想改 Canvas 层，可以在 Compose 侧给 `AndroidView` 加 `Modifier.animateContentSize()` 或在 `ReaderScreen` 中检测排版变化时触发一个短暂的 alpha 动画：

```kotlin
val layoutVersion = remember { mutableIntStateOf(0) }
LaunchedEffect(prefs.fontSize, prefs.lineSpacing, prefs.marginHorizontal, ...) {
    layoutVersion.intValue++
}

val alpha by animateFloatAsState(
    targetValue = 1f,
    animationSpec = tween(200),
    label = "page-crossfade",
)

AndroidView(
    modifier = Modifier.alpha(alpha),
    ...
)
```

不过这种方式是整个 View 的 alpha 动画，不如 Canvas 层的 crossfade 精细。

---

## 五、总结对比

| 维度 | 当前 | 改进后 |
|------|------|--------|
| Tab 数量 | 6 个（需滚动） | 3 个（固定，不滚动） |
| 主题色位置 | 藏在"翻页"Tab 里 | 常驻区，亮度栏下方 |
| 翻页动画位置 | "翻页"Tab 首项 | "样式"Tab 首项 |
| 边距 | 独立 Tab（仅 2 项） | 合并到"排版"Tab |
| 页眉脚详细 | 整个 Tab 长滚动 | "设置"Tab 内折叠区块 |
| Tab 切换动画 | 无 | crossfade + 方向感知 slide |
| 排版变化过渡 | 无 | Canvas crossfade (200ms) |
| Tab 栏高度 | ~72dp（图标+文字） | ~48dp（纯文字） |

### 实施步骤

1. **Step 1**：重组 Tab（3 Tab + 常驻主题色），重新分配选项到各 Panel
2. **Step 2**：Tab 切换加 `AnimatedContent` 过渡
3. **Step 3**：实现 `ExpandableSection` 折叠组件
4. **Step 4**：Canvas 层 crossfade 过渡动画
5. **Step 5**：清理旧代码，更新文档
