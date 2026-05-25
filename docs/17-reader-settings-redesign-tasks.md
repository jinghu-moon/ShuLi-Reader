# 17 · 阅读器设置面板重构 · 实施任务清单

> 文档版本：v1.0  
> 编制时间：2026-05-24  
> 范围：阅读界面 `QuickSettings` 浮层重构 + 关联数据/渲染层扩展  
> 关联资料：
> - `@d:/100_Projects/110_Daily/ShuLi-Reader/scratch/reader-settings-mockup.html`（可交互预览）
> - `@d:/100_Projects/110_Daily/ShuLi-Reader/docs/reader-architecture-notes.md`（架构基线）

---

## 0. 总览

### 0.1 目标

把当前扁平 stepper 风格的快捷设置浮层（`@d:/100_Projects/110_Daily/ShuLi-Reader/app/src/main/java/com/shuli/reader/feature/reader/ReaderScreen.kt:419-574`）重构为：

- **容器**：`ModalBottomSheet`（M3）+ 固定 62% 屏高 + 内部滚动
- **结构**：常驻亮度栏 + 6-Tab 分类（字号 / 字体 / 边距 / 显示 / 页眉脚 / 更多）
- **新增能力**：字距、对齐方式、字重、简繁、预设、页眉脚三槽位、屏幕常亮、音量键翻页、恢复默认
- **整合**：合并底部工具栏中的"亮度"独立浮层入口

### 0.2 范围

**包含**：

- 设置浮层 UI 重构（`ReaderScreen.kt` 内 `showQuickSettings` / `showBrightness` 分支）
- 公共控件抽取（`ReaderSliderRow` / `ReaderSegmentedRow` 等）
- `ReaderPreferences` 字段扩展 + DataStore 持久化
- `Paginator` / `ReaderPageRenderer` 配合改造（仅当数据项需要影响排版/绘制时）
- 数据库新增 `PresetEntity`（预设功能）

**不包含**：

- 翻页动画引擎重写（已在 R4 完成）
- 选区端点调整（R8，单独立项）
- ChapterProvider/PageBuffer 接入（R7，单独立项）
- 多窗口/分屏适配（独立工作）

### 0.3 阶段划分

| 阶段 | 内容 | 风险 | 预估工时 |
|---|---|---|---|
| **阶段一** | 容器骨架（ModalBottomSheet + Tab + 公共控件 + 亮度整合） | 低 | 1 天 |
| **阶段二** | 现有数据项迁移到新 UI（无数据/渲染改动） | 低 | 1 天 |
| **阶段三** | 新增字距/对齐/字重/简繁（涉及分页+渲染） | **中-高** | 3-4 天 |
| **阶段四** | 预设管理（数据库 + UI） | 中 | 1.5 天 |
| **阶段五** | 页眉脚（数据 + 分页 + 渲染 + UI） | **高** | 3-4 天 |
| **阶段六** | 杂项与打磨（屏幕常亮/音量键/恢复默认） | 低 | 0.5 天 |
| **阶段七** | 测试与回归 | — | 1 天 |

> **建议节奏**：阶段一 → 阶段二 合并验收一轮；阶段三 ~ 五 各自独立提交并验收；阶段六 ~ 七 收尾。

### 0.4 风险与回滚策略

| 风险 | 影响 | 缓解 |
|---|---|---|
| `ReaderPreferences` 字段新增后旧用户数据缺字段 | 闪退 / 数据丢失 | DataStore 默认值兜底 + Migration 测试 |
| `Paginator` 改动影响所有页面布局 | 末行裁切 / 翻页错位 | 单测 + 视觉回归（参考 `15-visual-regression-plan.md`） |
| 字距/字重单位与 Canvas Paint 不一致 | 重现 H1（已修复过的字号问题） | 数据层一律用 px，分页与渲染共用同一份 `ReaderLayoutConfig` |
| 页眉脚影响 `headerHeight/footerHeight` 常量 | 现有分页结果失效 | 把 height 计算从硬编码改为按 prefs 计算 |

**回滚**：每阶段独立提交，可按阶段回滚。`ReaderPreferences` 新字段全部带默认值，回滚 UI 不影响数据兼容性。

---

## 1. 阶段一 · 容器骨架与脚手架

### 1.1 引入 ModalBottomSheet

**文件**：`@d:/100_Projects/110_Daily/ShuLi-Reader/app/src/main/java/com/shuli/reader/feature/reader/ReaderScreen.kt`

**步骤**：

1. 删除 `AnimatedVisibility(showQuickSettings) + Surface(...)` 块（line 421-574）。
2. 删除 `AnimatedVisibility(showBrightness) + Surface(...)` 块（line 380-418）。
3. 新增组件文件 `feature/reader/component/QuickSettingsSheet.kt`，导出 `@Composable QuickSettingsSheet(...)`。
4. 在 `ReaderScreen` 主体中按以下骨架挂载：

```kotlin
if (uiState.showQuickSettings) {
    QuickSettingsSheet(
        uiState = uiState,
        onDismiss = viewModel::toggleQuickSettings,
        onBrightnessChange = viewModel::setBrightness,
        onFontSizeChange = viewModel::setFontSize,
        // ... 其他回调
    )
}
```

5. 修改 `ReaderViewModel`：
   - 删除 `OverlayPanel.BRIGHTNESS` 枚举值（如确认无其他入口）。
   - 删除 `toggleBrightness()` 函数。
   - 删除 `BackHandler` 中 `uiState.showBrightness` 分支（`@d:/100_Projects/110_Daily/ShuLi-Reader/app/src/main/java/com/shuli/reader/feature/reader/ReaderScreen.kt:139-148`）。
   - 删除底部工具栏的"亮度按钮"（`@d:/100_Projects/110_Daily/ShuLi-Reader/app/src/main/java/com/shuli/reader/feature/reader/ReaderScreen.kt:368-370`）。

**验收**：底部工具栏只剩"目录 / 设置"两个按钮；点击设置弹出 ModalBottomSheet；下滑关闭、点击外部关闭、返回键关闭三种交互均生效。

### 1.2 SecondaryTabRow + 6-Tab 框架

**在** `QuickSettingsSheet.kt` **内**：

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSettingsSheet(
    uiState: ReaderUiState,
    onDismiss: () -> Unit,
    /* ... callbacks ... */
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.62f),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        BrightnessBar(...)        // 1.4
        HorizontalDivider()
        SecondaryTabRow(selectedTabIndex = selectedTab) { /* 6 个 Tab */ }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            when (selectedTab) {
                0 -> FontSizePanel(...)
                1 -> FontPanel(...)
                2 -> MarginPanel(...)
                3 -> DisplayPanel(...)
                4 -> HeaderFooterPanel(...)
                5 -> MorePanel(...)
            }
        }
    }

    // 切换 Tab 时滚动复位
    LaunchedEffect(selectedTab) { scrollState.scrollTo(0) }
}
```

**Tab 标签**：`字号 / 字体 / 边距 / 显示 / 页眉脚 / 更多`

### 1.3 公共控件抽取

**新文件**：`feature/reader/component/QuickSettingsControls.kt`

需要的可复用控件：

| 控件 | 签名 | 用途 |
|---|---|---|
| `ReaderSliderRow` | `(label, value, range, steps, format, onChange, onDec, onInc)` | 字号/字距/行距/段距/边距/缩进/标题字号/透明度 |
| `ReaderSegmentedRow<T>` | `(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit)` | 翻页动画/字重/对齐/简繁/标题对齐 |
| `ReaderChipRow<T>` | `(options, selected, onSelect, modifier)` | 字体名/预设 |
| `ReaderSwitchRow` | `(label, description?, checked, onCheckedChange)` | 屏幕常亮/音量键/页眉显隐 |
| `ReaderInfoRow` | `(label, value: AnnotatedString, onClick)` | 页眉脚槽位（行末显当前值 + chevron） |
| `ReaderColorRow` | `(label, color: Color?, onClick)` | 颜色选择 |
| `ThemeSwatch` | `(theme, label, isSelected, onClick, onLongClick)` | 主题色块 |

**约束**：
- 所有 IconButton ≥ 36dp
- 数值文本固定宽 + 右对齐 + tabular-nums，避免抖动
- 标签宽统一 `48.dp`（中文 2 字）

### 1.4 亮度常驻栏

```kotlin
@Composable
private fun BrightnessBar(
    brightness: Float,                 // < 0 表示跟随系统
    onBrightnessChange: (Float) -> Unit,
) {
    val isAuto = brightness < 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.BrightnessLow, null, Modifier.size(18.dp))
        Slider(
            value = if (isAuto) 0.5f else brightness,
            onValueChange = onBrightnessChange,
            enabled = !isAuto,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Outlined.BrightnessHigh, null, Modifier.size(20.dp))
        FilterChip(
            selected = isAuto,
            onClick = { onBrightnessChange(if (isAuto) 0.5f else -1f) },
            label = { Text("跟随系统", style = MaterialTheme.typography.labelSmall) },
        )
    }
}
```

**与 ViewModel**：`onBrightnessChange = viewModel::setBrightness` 即可（已有逻辑：`< 0` → `BRIGHTNESS_OVERRIDE_NONE`）。

### 1.5 阶段一验收

- [ ] 点击底部"设置"按钮弹出 ModalBottomSheet，占屏 62%，38% 阅读区可见
- [ ] 6 个 Tab 横向均分，选中下划线显示
- [ ] 切换 Tab 内容平滑替换，滚动位置复位
- [ ] 弹窗下滑可关闭，点击外部可关闭，返回键可关闭
- [ ] 亮度滑块与"跟随系统" chip 互斥；调亮度自动取消跟随系统
- [ ] 旧的"亮度浮层"独立入口已移除，BackHandler 行为正确
- [ ] 旋转屏幕后弹窗保持高度比例，Tab 选中状态保留（用 `rememberSaveable`）

---

## 2. 阶段二 · 现有数据项迁移

### 2.1 字号 Tab（FontSizePanel）

**字段** | **控件** | **范围** | **当前值出处**
---|---|---|---
字号 | `ReaderSliderRow` | 10..32 sp，step 1 | `prefs.fontSize` (`ReaderPreferences.kt`)
字距 | `ReaderSliderRow` | 0..0.2，step 0.01 | **新增**，详见阶段三
行距 | `ReaderSliderRow` | 0.8..3.0，step 0.1 | `prefs.lineSpacing`
段距 | `ReaderSliderRow` | 0..5.0，step 0.1 | `prefs.paragraphSpacing`

> 字距在阶段三接入；本阶段先占位（disabled 或隐藏），避免阻塞布局验收。

### 2.2 字体 Tab（FontPanel）

| 项 | 控件 | 数据 |
|---|---|---|
| 阅读字体 | `ReaderChipRow`（LazyRow） | `prefs.readingFont` |
| 字重 | `ReaderSegmentedRow` | **新增** `prefs.fontWeight`（阶段三） |
| 对齐方式 | `ReaderSegmentedRow` | **新增** `prefs.textAlign`（阶段三） |
| 简繁转换 | `ReaderSegmentedRow` | **新增** `prefs.chineseConvert`（阶段三） |

> 本阶段只迁移"阅读字体"；其余三项 UI 占位 disabled。

### 2.3 边距 Tab（MarginPanel）

| 项 | 控件 | 范围 | 数据 |
|---|---|---|---|
| 上下边距 | `ReaderSliderRow` | 0..96 dp，step 4 | `prefs.marginVertical` |
| 左右边距 | `ReaderSliderRow` | 0..64 dp，step 4 | `prefs.marginHorizontal` |
| 首行缩进 | `ReaderSliderRow` | 0..10，step 0.5 | `prefs.indent` |

### 2.4 显示 Tab（DisplayPanel）

| 区块 | 控件 | 数据 |
|---|---|---|
| 翻页动画 | `ReaderSegmentedRow` 5 选 1 | `prefs.pageAnimType`（已有 `setPageAnimType`） |
| 主题 | `ThemeSwatch` ×4-5 | `prefs.backgroundColor`（已有 `setReaderTheme`） |
| 页眉显示 | `ReaderSwitchRow` | **新增** `prefs.showHeader`（阶段五） |
| 页脚显示 | `ReaderSwitchRow` | **新增** `prefs.showFooter`（阶段五） |
| 进度条显示 | `ReaderSwitchRow` | **新增** `prefs.showProgress`（阶段五） |

> 翻页动画 + 主题 本阶段就接入；3 个 Switch 留待阶段五。

### 2.5 更多 Tab（MorePanel）

| 项 | 控件 | 数据 |
|---|---|---|
| 预设 chip 行 | `ReaderChipRow` + 长按 | **新增**（阶段四） |
| 屏幕常亮 | `ReaderSwitchRow` | **新增** `prefs.keepScreenOn`（阶段六） |
| 音量键翻页 | `ReaderSwitchRow` | **新增** `prefs.volumeKeyTurnPage`（阶段六） |
| 边缘翻页 | `ReaderSwitchRow` | **新增** `prefs.edgeTurnPage`（阶段六） |
| 恢复默认 | `OutlinedButton` | **新增** `viewModel.resetToDefault()`（阶段六） |

### 2.6 阶段二验收

- [ ] 字号 / 行距 / 段距 / 缩进 / 上下边距 / 左右边距 6 个 Slider 调节实时生效
- [ ] 阅读字体切换实时生效（霞鹜文楷 / 系统默认）
- [ ] 翻页动画 5 种一键切换
- [ ] 主题色块 4-5 个，长按入口暂留 TODO
- [ ] 调节参数后调用 `resetToolbarAutoHide()`（已在 ViewModel 内部统一处理，本阶段无需 UI 触发）
- [ ] 关闭弹窗后参数已落 DataStore（杀进程后重启保留）

---

## 3. 阶段三 · 新增字距 / 对齐 / 字重 / 简繁

> **本阶段是整个重构中风险最高的部分**：字距与对齐都直接影响 `Paginator` 字宽计算与 `Canvas` 渲染。**必须保证分页测量与 Canvas 绘制使用同一组单位**，复习 H1 教训。

### 3.1 字距 letterSpacing

**数据层**：

```kotlin
// ReaderPreferences.kt
data class ReaderPreferences(
    ...
    val letterSpacing: Float = 0f,   // 单位：em（字号倍数），范围 0..0.2
)

// UserPreferences DataStore
val LETTER_SPACING = floatPreferencesKey("letter_spacing")
val letterSpacing: Flow<Float> = ...
suspend fun setLetterSpacing(value: Float) = ...
```

**分页层** `Paginator.kt`：

`SimpleTextMeasurer.measureCharWidth` 需要在每个字符宽度上额外加 `textSize * letterSpacing`。建议把 `letterSpacing` 作为 `ReaderLayoutConfig` 字段：

```kotlin
data class ReaderLayoutConfig(
    ...
    val letterSpacingPx: Float = 0f,   // 已经 × textSize 的绝对像素
)

// Paginator.calculateLine
val charWidth = textMeasurer.measureCharWidth(remaining[i], textSize) + config.letterSpacingPx
```

**渲染层** `ReaderCanvasView.kt` / `ReaderPageRenderer.kt`：

```kotlin
textPaint.letterSpacing = prefs.letterSpacing   // Paint.letterSpacing 单位也是 em，与 Paginator 一致
```

> **关键**：`Paint.letterSpacing` 在测量时会自动加到 `measureText` 的结果中，但我们的分页用的是 `SimpleTextMeasurer` 不是 Paint。**必须保证 `config.letterSpacingPx == textSize * prefs.letterSpacing`**，且分页和渲染都用同一份 prefs。

**UI 接入**：放在「字号 Tab」第 2 行。

**验收**：
- 字距 0 → 0.1 滑动后正文字符间距明显增加
- 末行不溢出、不裁切
- 与字号同时调节无错位

### 3.2 对齐方式 textAlign

```kotlin
enum class ReaderTextAlign { LEFT, JUSTIFY }
data class ReaderPreferences(... val textAlign: ReaderTextAlign = LEFT)
```

**分页层**：左对齐无变化。两端对齐需要：

1. 计算每行剩余宽度 `remaining = availableWidth - usedWidth`
2. 计算可分配字数 `gaps = text.length - 1`（不含最后一行）
3. 每两字间额外加 `extra = remaining / gaps`
4. **段末行/换行行不参与两端对齐**（保持自然结尾）

```kotlin
// TextLine 新增字段
data class TextLine(
    ...
    val justifyExtra: Float = 0f,   // 每字符额外间距
)

// 渲染：drawText 时按 justifyExtra 偏移每字
// 用 Canvas.drawText(char, x, y, paint) 逐字绘制，或用 Paint.letterSpacing 累加
```

**简化方案**：渲染时用 `canvas.drawTextRun` 配合 `glyphAdvance` 数组，但实现复杂。**推荐**：把 `textPaint.letterSpacing = baseLetterSpacing + justifyExtra/textSize` 临时改一下绘制每行（牺牲略微性能，逻辑简单）。

**UI 接入**：「字体 Tab」第 3 行 `SegmentedButton`。

**验收**：
- 切换两端对齐后正文每行末尾对齐到右边距
- 段末行自然结束（不被强行拉伸）
- 短行（< 80% 宽度）不强制对齐（避免视觉空洞，可加阈值）

### 3.3 字重 fontWeight

```kotlin
enum class ReaderFontWeight(val weight: Int) {
    LIGHT(300), NORMAL(400), MEDIUM(500), BOLD(700)
}
data class ReaderPreferences(... val fontWeight: ReaderFontWeight = NORMAL)
```

**渲染**：

```kotlin
// 仅简单实现（FakeBold）
textPaint.isFakeBoldText = (prefs.fontWeight == BOLD)

// 真正字重需要加载字体的多种 variant：
// val typeface = Typeface.create(baseTypeface, prefs.fontWeight.weight, false)
// textPaint.typeface = typeface
```

> 当前项目用霞鹜文楷单字重字体，建议先实现 `BOLD` via `isFakeBoldText`；细字重待引入字体 variant 时再做。

**字宽影响**：FakeBold 不改变字宽。变体字体改变字宽 → 必须同步分页。先做 FakeBold 路线。

### 3.4 简繁转换 chineseConvert

```kotlin
enum class ChineseConvert { NONE, SIMPLIFIED, TRADITIONAL }
data class ReaderPreferences(... val chineseConvert: ChineseConvert = NONE)
```

**实现层级**：在 `paginateChapter` 内对 `chapterText` 做转换，**不改原始 `BookContent.content`**：

```kotlin
val converted = when (prefs.chineseConvert) {
    NONE -> chapterText
    SIMPLIFIED -> ChineseConverter.toSimplified(chapterText)
    TRADITIONAL -> ChineseConverter.toTraditional(chapterText)
}
paginator.paginateChapter(..., content = converted, ...)
```

**依赖**：可选库 `com.github.houbb:opencc4j` 或 `me.zhanghai.android.opencc:library`。建议引入 `opencc4j`（无网络）。

> **影响**：转换会改变 `content.length` 与 charOffset 含义。`durChapterPos` 仍按原文偏移保存（避免切换设置后位置错乱），但分页对比转换后内容。需在 `ReaderViewModel.openBook` 与 `reflowCurrentChapter` 内统一应用。

### 3.5 阶段三验收

- [ ] 字距滑动实时生效，无末行裁切、无溢出
- [ ] 切换"两端对齐"后正文右边整齐
- [ ] 字重切换粗体生效
- [ ] 简繁切换实时反映正文（首次切换可能 reflow）
- [ ] 上述任一改动后翻页位置稳定（charOffset 锚定不丢失）

---

## 4. 阶段四 · 预设管理

### 4.1 数据库

```kotlin
@Entity(tableName = "reader_preset")
data class ReaderPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    // 序列化的 ReaderPreferences JSON
    val configJson: String,
)

@Dao
interface ReaderPresetDao {
    @Query("SELECT * FROM reader_preset ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ReaderPresetEntity>>

    @Insert suspend fun insert(entity: ReaderPresetEntity): Long
    @Delete suspend fun delete(entity: ReaderPresetEntity)
    @Update suspend fun update(entity: ReaderPresetEntity)
}
```

**Migration**：版本号 +1，加表 SQL。

### 4.2 ViewModel

```kotlin
fun loadPresets()                                  // 启动时收集
fun saveCurrentAsPreset(name: String)              // 取当前 prefs 序列化
fun applyPreset(presetId: Long)                    // 反序列化并依次调用 setXxx
fun renamePreset(presetId: Long, newName: String)
fun deletePreset(presetId: Long)
```

序列化用 `kotlinx.serialization.json.Json`（已是项目依赖）。

### 4.3 UI

```kotlin
LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    items(presets) { preset ->
        AssistChip(
            onClick = { onApply(preset.id) },
            label = { Text(preset.name) },
            modifier = Modifier.combinedClickable(
                onClick = { onApply(preset.id) },
                onLongClick = { showMenuFor = preset.id },
            ),
        )
    }
    item {
        AssistChip(
            onClick = { showSaveDialog = true },
            label = { Text("＋ 保存当前") },
            border = AssistChipDefaults.assistChipBorder(/* dashed */),
        )
    }
}
```

**长按菜单**：`DropdownMenu` 显示 重命名 / 删除。

**保存对话框**：`AlertDialog` + `OutlinedTextField` 输入预设名。

### 4.4 默认预设种子（可选）

数据库迁移时插入 3 个默认预设：日间常用 / 夜间 / 大字号。

### 4.5 阶段四验收

- [ ] 修改任意参数后点击"＋ 保存当前"，输入名称，预设出现在列表
- [ ] 点击预设 chip，正文实时切换为该预设的所有参数
- [ ] 长按预设可重命名/删除
- [ ] 杀进程重启后预设保留

---

## 5. 阶段五 · 页眉脚

### 5.1 数据模型

```kotlin
enum class HeaderVisibility { ALWAYS_SHOW, ALWAYS_HIDE, HIDE_WHEN_STATUS_BAR }

enum class SlotContent {
    NONE,
    CHAPTER_TITLE,    // 当前章节名
    BOOK_TITLE,       // 书名
    PAGE_NUMBER,      // x / y
    PROGRESS,         // 章节进度 xx.x%
    TIME,             // HH:mm
    BATTERY,          // xx%
    DATE,             // MM-dd
}

data class HeaderConfig(
    val visibility: HeaderVisibility = HIDE_WHEN_STATUS_BAR,
    val left: SlotContent = CHAPTER_TITLE,
    val center: SlotContent = NONE,
    val right: SlotContent = NONE,
)

data class FooterConfig(
    val visibility: HeaderVisibility = ALWAYS_SHOW,
    val left: SlotContent = PROGRESS,
    val center: SlotContent = PAGE_NUMBER,
    val right: SlotContent = TIME,   // 时间 + 电池可分两个槽
)

data class TitleStyleConfig(
    val align: TitleAlign = CENTER,            // LEFT / CENTER / HIDDEN
    val sizeOffsetSp: Int = 4,                 // 相对正文字号的偏移
    val marginTopDp: Float = 9f,
    val marginBottomDp: Float = 60f,
)

data class ReaderPreferences(
    ...
    val titleStyle: TitleStyleConfig = TitleStyleConfig(),
    val header: HeaderConfig = HeaderConfig(),
    val footer: FooterConfig = FooterConfig(),
    val headerFooterAlpha: Float = 0.4f,        // 0..1
    val headerFooterColor: Int? = null,         // null = 跟随正文
    val dividerColor: Int? = null,              // null = 默认
)
```

**DataStore**：因结构复杂，建议把 `header/footer/titleStyle` 序列化为 JSON 单 key 存。

### 5.2 ReaderPageRenderer 重构

替换现有单字符串页眉/页脚绘制为多槽位：

```kotlin
fun render(
    ...
    headerSlots: SlotResolution,    // 已解析的左/中/右文本
    footerSlots: SlotResolution,
    ...
)

private fun drawHeader(canvas, slots, paint, alpha, ...) {
    paint.alpha = (alpha * 255).toInt()
    val y = ... // 基线
    val w = canvas.width.toFloat()
    paint.textAlign = Paint.Align.LEFT;   canvas.drawText(slots.left, marginH, y, paint)
    paint.textAlign = Paint.Align.CENTER; canvas.drawText(slots.center, w / 2f, y, paint)
    paint.textAlign = Paint.Align.RIGHT;  canvas.drawText(slots.right, w - marginH, y, paint)
}
```

**电池图标**：仅在 `BATTERY` 槽出现时绘制（替代或附在文本旁）。

**章节标题绘制**：

```kotlin
private fun drawChapterTitle(canvas, page, prefs) {
    if (prefs.titleStyle.align == HIDDEN) return
    if (page.pageIndex != 0) return    // 仅章首页绘制
    
    val titleSize = prefs.fontSize + prefs.titleStyle.sizeOffsetSp
    val titleY = prefs.titleStyle.marginTopDp * density + titleSize
    val xByAlign = when (prefs.titleStyle.align) {
        LEFT -> page.marginHorizontal
        CENTER -> canvas.width / 2f
    }
    titlePaint.textSize = titleSize * density
    titlePaint.textAlign = if (CENTER) Align.CENTER else Align.LEFT
    canvas.drawText(page.chapterTitle, xByAlign, titleY, titlePaint)
}
```

### 5.3 Paginator 变更

**关键改动**：动态计算 header/footer 占用高度。

```kotlin
fun paginatePage(... prefs: ReaderPreferences ...) {
    val headerHeight = if (prefs.header.visibility == ALWAYS_HIDE) 0f
                       else 24f * density
    val footerHeight = if (prefs.footer.visibility == ALWAYS_HIDE) 0f
                       else 24f * density

    // 章节首页额外预留标题区（如 align != HIDDEN）
    val titleAreaHeight = if (pageIndex == 0 && prefs.titleStyle.align != HIDDEN) {
        prefs.titleStyle.marginTopDp * density +
        (prefs.fontSize + prefs.titleStyle.sizeOffsetSp) * density +
        prefs.titleStyle.marginBottomDp * density
    } else 0f

    val maxAvailableY = pageSize.height - marginVertical - footerHeight
    var currentY = marginVertical + headerHeight + titleAreaHeight
    ...
}
```

> **回归测试关键**：旧版章首和非章首高度相同。新版章首页可用高度变小，必然导致章首页字数变少。**这是正确行为**，但视觉回归基线需要更新。

### 5.4 UI 实现（HeaderFooterPanel）

参考预览 HTML 中 Tab 4 的结构：

1. **正文标题** 区
   - `ReaderSegmentedRow` 对齐（靠左 / 居中 / 隐藏）
   - 3 个 `ReaderSliderRow`（字号偏移 / 上距 / 下距）

2. **页眉** 区（`section-title` + 4 个 `ReaderInfoRow`）
   - 显示/隐藏 → 点击弹 `AlertDialog` 单选 3 态
   - 左/中/右 → 点击弹 `AlertDialog` 单选 SlotContent

3. **页脚** 区（同上）

4. **共同配置** 区
   - 文字颜色 → `ReaderColorRow` → `ColorPickerDialog`（默认"跟随正文"）
   - 分隔线颜色 → 同上
   - 透明度 → `ReaderSliderRow` 0..100%

**ColorPickerDialog**：可用现有库 `com.github.skydoves:colorpicker-compose` 或自绘 HSV picker。建议引入库。

### 5.5 数据迁移

```kotlin
// DataStore Migration
val migrations = listOf(
    object : DataMigration<Preferences> {
        override suspend fun shouldMigrate(currentData: Preferences) =
            !currentData.contains(HEADER_CONFIG_JSON)
        override suspend fun migrate(currentData: Preferences): Preferences {
            return currentData.toMutablePreferences().apply {
                set(HEADER_CONFIG_JSON, Json.encodeToString(HeaderConfig()))
                set(FOOTER_CONFIG_JSON, Json.encodeToString(FooterConfig()))
                set(TITLE_STYLE_JSON, Json.encodeToString(TitleStyleConfig()))
            }
        }
        override suspend fun cleanUp() {}
    }
)
```

### 5.6 阶段五验收

- [ ] 章首页显示居中标题，正文从标题下方开始
- [ ] 切换"靠左/居中/隐藏"立刻生效
- [ ] 页眉左/中/右槽位可独立配置内容
- [ ] 页脚同上
- [ ] 透明度滑块改变页眉脚透明度
- [ ] 颜色选择器可设置页眉脚文字颜色与分隔线颜色
- [ ] "状态栏显示时隐藏"配合系统状态栏可见时页眉自动隐藏（需读 `WindowInsetsCompat`）
- [ ] 隐藏页眉/页脚时正文可用区扩大（验证 Paginator 改动）

---

## 6. 阶段六 · 杂项与打磨

### 6.1 屏幕常亮

```kotlin
// ReaderScreen.kt
LaunchedEffect(uiState.readerPreferences.keepScreenOn) {
    activity?.window?.let { window ->
        if (uiState.readerPreferences.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
```

`onDispose` 清理 flag。

### 6.2 音量键翻页

在 `MainActivity` 或 Reader Activity 重写 `dispatchKeyEvent`：

```kotlin
override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (uiState.readerPreferences.volumeKeyTurnPage && currentScreen == Reader) {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> if (event.action == ACTION_DOWN) {
                viewModel.prevPage(); return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> if (event.action == ACTION_DOWN) {
                viewModel.nextPage(); return true
            }
        }
    }
    return super.dispatchKeyEvent(event)
}
```

**注意**：开启时音量按钮被劫持，长按需有提示告知用户。

### 6.3 边缘翻页开关

当前手势在 `ReaderCanvasView.onTouchEvent` 内已实现"左/中/右 1/3"分区。加开关后：

```kotlin
if (!prefs.edgeTurnPage) {
    // 中心点击仍唤起工具栏；左右触摸不响应翻页，全部交 PageDelegate（或忽略）
}
```

### 6.4 恢复默认

```kotlin
fun resetToDefault() {
    val defaults = ReaderPreferences()  // 数据类默认值
    setFontSize(defaults.fontSize)
    setLineSpacing(defaults.lineSpacing)
    /* ... 全部 setter ... */
}
```

UI 触发前弹 `AlertDialog` 二次确认。

### 6.5 阶段六验收

- [ ] 屏幕常亮开启后阅读 5 分钟不息屏；关闭后正常息屏
- [ ] 音量键翻页开启后按键生效；关闭后正常调音量
- [ ] 边缘翻页关闭后只能用滑动手势翻页
- [ ] 恢复默认弹确认对话框，确认后所有参数回归

---

## 7. 阶段七 · 测试与回归

### 7.1 单元测试

| 模块 | 测试内容 |
|---|---|
| `Paginator` | 字距/对齐/标题区域改动后页码数与现有基线一致（除已知预期变化） |
| `SlotResolver` | 各 `SlotContent` 类型解析为正确文本（mock 时间/电池等） |
| `ChineseConverter` | 简繁转换前后 `content.length` 仍与 `chapterIndex` 兼容 |
| `PresetSerializer` | JSON 往返序列化 `ReaderPreferences` 不丢字段 |

### 7.2 UI 测试（Compose）

| 流程 | 断言 |
|---|---|
| 打开设置 → 切换 6 个 Tab | 各 Tab 内容正确显示，滚动复位 |
| 拖动字号滑块 | `view.setTextSizePx` 被调用 |
| 切换主题 | 阅读区背景色变化 |
| 保存预设 → 应用 | 当前参数变更与预设一致 |
| 关闭弹窗 | sheetState.isVisible == false |

### 7.3 视觉回归

更新 `@d:/100_Projects/110_Daily/ShuLi-Reader/docs/15-visual-regression-plan.md` 中的基线截图：

- 章首页（带标题）
- 章中页（不带标题）
- 两端对齐 vs 左对齐
- 页眉脚显示 vs 隐藏
- 简繁转换前后
- 5 种翻页动画静帧

### 7.4 性能基准

参考 `@d:/100_Projects/110_Daily/ShuLi-Reader/docs/16-benchmark-execution-guide.md`，跑：

- `paginateChapter` 单次耗时（字距/对齐改动后不应增加 > 20%）
- 滚动 60s 帧率
- 翻页动画帧率（90/120Hz 设备）

---

## 8. 文件改动清单（速查）

### 新建文件

| 路径 | 用途 |
|---|---|
| `feature/reader/component/QuickSettingsSheet.kt` | 主面板入口 |
| `feature/reader/component/QuickSettingsControls.kt` | 公共控件 |
| `feature/reader/component/panels/FontSizePanel.kt` | Tab 0 |
| `feature/reader/component/panels/FontPanel.kt` | Tab 1 |
| `feature/reader/component/panels/MarginPanel.kt` | Tab 2 |
| `feature/reader/component/panels/DisplayPanel.kt` | Tab 3 |
| `feature/reader/component/panels/HeaderFooterPanel.kt` | Tab 4 |
| `feature/reader/component/panels/MorePanel.kt` | Tab 5 |
| `feature/reader/component/dialogs/SlotPickerDialog.kt` | 槽位单选 |
| `feature/reader/component/dialogs/PresetSaveDialog.kt` | 预设命名 |
| `feature/reader/component/dialogs/ColorPickerDialog.kt` | 颜色选择（或引入库） |
| `core/database/entity/ReaderPresetEntity.kt` | 预设实体 |
| `core/database/dao/ReaderPresetDao.kt` | 预设 DAO |
| `core/reader/HeaderFooterModels.kt` | HeaderConfig / FooterConfig / SlotContent |
| `core/reader/SlotResolver.kt` | 槽位文本解析 |
| `core/text/ChineseConverter.kt` | 简繁转换封装 |

### 修改文件

| 路径 | 改动 |
|---|---|
| `feature/reader/ReaderScreen.kt` | 移除独立亮度浮层；接入 QuickSettingsSheet；屏幕常亮/音量键 |
| `feature/reader/ReaderViewModel.kt` | 删除 `OverlayPanel.BRIGHTNESS`；新增 `setLetterSpacing/setTextAlign/setFontWeight/setChineseConvert/setHeaderConfig/...`；预设方法；`resetToDefault` |
| `core/data/ReaderPreferences.kt` | 新增 11+ 字段 |
| `core/data/UserPreferences.kt` | 新增对应 DataStore key 与 Flow/setter |
| `core/reader/Paginator.kt` | 字距 + 两端对齐 + 标题预留 + header/footer 高度按 prefs 算 |
| `core/reader/SimpleTextMeasurer.kt` | 加入 letterSpacing |
| `core/reader/ReaderPageRenderer.kt` | 多槽位绘制 + 标题绘制 + 透明度按 prefs |
| `core/reader/ReaderCanvasView.kt` | `setLetterSpacing` / `setFontWeight` 等新 setter |
| `core/reader/model/TextModels.kt` | `TextLine.justifyExtra` |
| `core/reader/model/ReaderLayoutConfig.kt` | `letterSpacingPx` 等 |
| `core/database/AppDatabase.kt` | 注册 `ReaderPresetEntity` 与 Migration |
| `core/database/Migrations.kt` | 新建表 SQL |
| `MainActivity.kt` | `dispatchKeyEvent` 处理音量键 |

### 资源/文案

| 路径 | 改动 |
|---|---|
| `core/i18n/AppStrings.kt` | 新增所有面板文案 key（中英双语） |
| `core/i18n/AppStringsZh.kt` | 中文翻译 |
| `core/i18n/AppStringsEn.kt` | 英文翻译 |

---

## 9. 验收清单（用户视角 · 端到端）

完整重构落地后，从**用户角度**应能体验到：

### 设置面板
- [ ] 阅读时点击底部"设置"按钮，从底部弹出占屏 ~62% 的面板
- [ ] 顶部 38% 仍可见正文，所有调整可即时预览
- [ ] 顶部有亮度条（含跟随系统），整个面板期间始终可见
- [ ] 6 个 Tab 横向均分，切换内容平滑
- [ ] 内容超长时面板内部滚动，Tab 顶部钉住

### 字号
- [ ] 字号、字距、行距、段距 4 个 Slider，配 ± 微调
- [ ] 调整实时反映到正文，无末行裁切

### 字体
- [ ] 多种字体 chip 横滑切换
- [ ] 字重切换粗体
- [ ] 左对齐 / 两端对齐切换
- [ ] 简繁切换实时转换正文

### 边距
- [ ] 上下/左右边距、首行缩进 3 个 Slider

### 显示
- [ ] 5 种翻页动画 SegmentedButton 一键切换
- [ ] 4-5 个主题色块，长按可自定义
- [ ] 页眉/页脚/进度条 3 个独立显隐开关

### 页眉脚
- [ ] 章节标题靠左/居中/隐藏 + 字号偏移 + 上下距
- [ ] 页眉左/中/右 3 槽位独立配置 + 显隐 3 态
- [ ] 页脚左/中/右 3 槽位独立配置 + 显隐 3 态
- [ ] 文字颜色、分隔线颜色、透明度

### 更多
- [ ] 预设 chip 行（保存/应用/重命名/删除）
- [ ] 屏幕常亮、音量键翻页、边缘翻页 3 个开关
- [ ] 恢复默认按钮（带二次确认）

### 数据持久化
- [ ] 杀进程重启后所有设置保留
- [ ] 旧版用户升级后，老字段保留、新字段使用默认值
- [ ] 跨章节翻页后，全局设置不重置；阅读位置正确

---

## 10. 实施建议节奏

```
第 1 周（建议）
  Day 1: 阶段一（容器骨架）
  Day 2: 阶段二（迁移现有项）→ 提交 PR · 验收
  Day 3-4: 阶段三（字距 + 对齐）→ 提交 PR · 验收
  Day 5: 阶段三剩余（字重 + 简繁）+ 阶段四开始

第 2 周
  Day 1: 阶段四完成（预设）→ 提交 PR
  Day 2-4: 阶段五（页眉脚）→ 拆 2 个 PR：数据/分页/渲染 + UI
  Day 5: 阶段六（杂项）+ 阶段七（测试）→ 收官 PR
```

每阶段提交后，在 `docs/` 内追加 `17-phaseN-completion-report.md` 记录：

- 实际工时
- 偏离原计划的地方
- 测试结果
- 已知遗留

---

## 11. 附录 · 参考文件

- 预览：`@d:/100_Projects/110_Daily/ShuLi-Reader/scratch/reader-settings-mockup.html`
- 架构基线：`@d:/100_Projects/110_Daily/ShuLi-Reader/docs/reader-architecture-notes.md`
- 已修复问题（R1~R12）：见架构基线第 11 节
- 颜色方案：`@d:/100_Projects/110_Daily/ShuLi-Reader/docs/14-color-scheme.md`
- 视觉回归：`@d:/100_Projects/110_Daily/ShuLi-Reader/docs/15-visual-regression-plan.md`
- 性能基准：`@d:/100_Projects/110_Daily/ShuLi-Reader/docs/16-benchmark-execution-guide.md`
