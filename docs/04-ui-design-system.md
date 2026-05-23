# 04 - UI 设计系统

## 设计原则

- **沉浸**：阅读界面无干扰，专注内容
- **克制**：圆角、阴影、动画适度使用
- **舒适**：色彩、字体、间距护眼舒适

## 颜色系统

### 亮色主题

```kotlin
val LightBackground = Color(0xFFFAF8F5)      // 微暖白
val LightSurface = Color(0xFFF5F2EE)          // 卡片背景
val LightTextPrimary = Color(0xFF2C2C2C)      // 主文字（非纯黑）
val LightTextSecondary = Color(0xFF8A8A8A)    // 副文字
val LightAccent = Color(0xFFE8956A)           // 暖橙强调色
val LightDivider = Color(0xFFEDEBE8)          // 极淡分割线
```

### 暗色主题

```kotlin
val DarkBackground = Color(0xFF1A1A1A)        // 深灰（非纯黑）
val DarkSurface = Color(0xFF242424)
val DarkTextPrimary = Color(0xFFE0E0E0)       // 主文字（非纯白）
val DarkTextSecondary = Color(0xFF7A7A7A)
val DarkAccent = Color(0xFFE8956A)
val DarkDivider = Color(0xFF333333)
```

### 纸质主题

```kotlin
val PaperBackground = Color(0xFFF5EDE0)       // 模拟纸张
val PaperText = Color(0xFF3A3028)
```

## 形状系统

```kotlin
val Small = RoundedCornerShape(4.dp)          // 小组件
val Medium = RoundedCornerShape(8.dp)         // 卡片
val Large = RoundedCornerShape(12.dp)         // 弹窗
val Button = RoundedCornerShape(6.dp)         // 按钮
```

## 字体系统

### 阅读字体

```kotlin
val ReadingFont = FontFamily(
    Font(R.font.lxgw_wenkai, FontWeight.Normal),      // 霞鹜文楷
    Font(R.font.lxgw_wenkai_bold, FontWeight.Bold)
)
```

### UI 字体

```kotlin
val UIFont = FontFamily.Default
```

### 字号规范

| 用途 | 字号 | 行距 |
|------|------|------|
| 标题 | 18-20sp | 1.4 |
| 正文（阅读） | 16-18sp | 1.6 |
| 副文字 | 12-14sp | 1.4 |
| 按钮 | 14sp | 1.2 |

## 间距系统

| 间距 | 数值 | 用途 |
|------|------|------|
| 紧凑 | 4dp | 图标与文字 |
| 标准 | 8dp | 组件内部 |
| 中等 | 12dp | 组件之间 |
| 页面 | 16dp | 页面边距 |
| 大间距 | 24dp | 区块分隔 |

## 阴影规则

扁平化为主，仅用极淡阴影：

| 组件 | 阴影 |
|------|------|
| 卡片 | 1dp |
| 弹窗 | 4dp |
| 悬浮按钮 | 2dp |

## 组件库

### 自研组件清单

| 组件 | 代码量 | 说明 |
|------|--------|------|
| ReaderButton | ~60行 | 按钮（3种变体） |
| ReaderCard | ~40行 | 卡片容器 |
| ReaderSlider | ~80行 | 滑块控件 |
| ReaderDialog | ~100行 | 对话框 |
| ReaderBottomSheet | ~120行 | 底部弹窗 |
| ReaderTopBar | ~60行 | 顶部导航栏 |
| ReaderSwitch | ~40行 | 开关 |
| ReaderTextField | ~80行 | 输入框 |
| ReaderDivider | ~10行 | 分割线 |
| ReaderIcon | ~20行 | 图标封装 |
| **合计** | **~610行** | **零外部依赖** |

## 动效规范

```kotlin
// 页面切换
val PageTransition = slideInHorizontally(
    initialOffsetX = { it },
    animationSpec = tween(300, easing = EaseOutCubic)
) + fadeIn(animationSpec = tween(200))

// 列表项出现
val ListItemEnter = fadeIn() + slideInVertically(
    initialOffsetY = { it / 10 },
    animationSpec = tween(300)
)

// 翻页物理效果
val PageFlipSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessLow
)
```

## 设计参考

| App | 借鉴点 |
|-----|--------|
| 微信读书 | 极简 UI、纸质主题、翻页手感 |
| Kindle | 沉浸式阅读、字体排版 |
| 多看阅读 | 主题丰富、设置细致 |
| Apple Books | 动效流畅、卡片设计 |
