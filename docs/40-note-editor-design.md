# 40 - TXT 书籍文本编辑方案（V2）

> 编写时间：2026-06-20
> 范围：书籍原文纠错 · 查找替换 · 编辑保存
> 原则：高性能 · 轻量 · 常用编辑功能 · 速度快
> 场景：阅读时发现错字/错词 → 快速改正 → 保存回原文件

---

## 1. 场景与需求

### 1.1 用户场景

```
阅读 → 发现错字"踟躇"应为"踟蹰" → 点击编辑 → 改正 → 保存 → 继续阅读
阅读 → 发现作者名错误贯穿全书 → 查找替换 → 全部替换 → 保存
阅读 → 发现当前页有个别错字 → 内联编辑当前文字 → 回车确认
```

### 1.2 功能清单

| 功能 | 说明 | 优先级 |
|---|---|---|
| **查找** | 输入关键词，高亮匹配，上下跳转 | P0 |
| **替换** | 逐个确认或全部替换 | P0 |
| **撤销/重做** | 连续撤销多次编辑 | P0 |
| **当前文字编辑** | 选中文字后直接内联编辑 | P0 |
| **保存到文件** | 流式写入 + 原子替换 + 自动备份 | P0 |
| **正则表达式** | 查找/替换支持正则（`.*`开关） | P1 |
| **大小写敏感** | 查找时提供开关 | P1 |
| **编辑记录** | 列出所有修改，diff 显示，可逐条撤销 | P1 |
| **全书查找替换** | 流式跨章节扫描 | P1 |
| **查找历史** | 记录最近查找词 | P2 |

---

## 2. 核心架构

### 2.1 章节内字符索引模型

**核心决策：EditDelta 使用章节内字符偏移，而非全局字节偏移。**

项目已有的数据链路：`BookChapterEntity` 存储 `byteStart/byteEnd`（字节偏移），`BookContentRepository.getChapterText()` 读取字节后用 `StreamDecoder` 解码为章节文本字符串。阅读器以**章节**为单位工作。

因此 EditDelta 只需记录「第 N 章的第 X 到 Y 个字符替换为新文本」，彻底解耦文件编码（UTF-8/GBK）：

```kotlin
/**
 * 一条编辑补丁。
 *
 * 定位方式：chapterIndex + 章节内字符偏移（非字节偏移）。
 * 优势：
 * - 与文件编码无关（UTF-8/GBK 均适用）
 * - 不需要 byte↔char 转换
 * - 同一章节内多个 Delta 的偏移互不影响（应用时按降序处理）
 */
data class EditDelta(
    val chapterIndex: Int,
    val charStart: Int,           // 章节内字符起始
    val charEnd: Int,             // 章节内字符结束（exclusive）
    val newText: String,          // 替换后的文本
    val timestamp: Long = System.currentTimeMillis(),
) {
    /** 字符长度变化量（用于保存时计算字节偏移增量） */
    fun charLengthDiff(): Int = newText.length - (charEnd - charStart)
}
```

**`originalText` 不存储在 Delta 中**——原文随时可通过 `byteStart/byteEnd` 从原文件按需读取，避免内存冗余。仅在 UI 显示 diff 时按需加载。

### 2.2 BatchEditDelta（批量替换优化）

全部替换 1000 处不应生成 1000 个 EditDelta。引入批量补丁：

```kotlin
/**
 * 批量编辑补丁：一次"全部替换"操作的所有匹配。
 *
 * 只记录一条 {findText, replaceText, List<CharRange>}，
 * 而非 N 个独立的 EditDelta。撤销时一次性撤销整批。
 */
data class BatchEditDelta(
    val chapterIndex: Int,
    val findText: String,
    val replaceText: String,
    val ranges: List<IntRange>,   // 每个匹配的 charStart..charEnd
    val isRegex: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
) {
    /** 展开为单个 EditDelta 列表（按降序排列，供应用时使用） */
    fun expand(): List<EditDelta> = ranges.sortedByDescending { it.first }.map { range ->
        EditDelta(chapterIndex, range.first, range.last + 1, replaceText, timestamp)
    }

    /** 总字符长度变化量 */
    fun totalCharLengthDiff(): Int {
        val originalLen = ranges.sumOf { it.last - it.first + 1 }
        val newLen = replaceText.length * ranges.size
        return newLen - originalLen
    }
}
```

### 2.3 EditStore（补丁存储 + 持久化）

```kotlin
/**
 * 编辑补丁存储。
 *
 * 内存 + DB 双层存储：
 * - 内存：快速访问（撤销/重做/应用）
 * - Room edit_delta 表：防崩溃丢失
 *
 * 保存到文件后清空内存和 DB。
 */
class EditStore(
    private val editDeltaDao: EditDeltaDao,
) {
    // 内存中的补丁（单个 + 批量混合存储）
    sealed interface Patch {
        val chapterIndex: Int
        val timestamp: Long
    }
    data class SinglePatch(val delta: EditDelta) : Patch {
        override val chapterIndex get() = delta.chapterIndex
        override val timestamp get() = delta.timestamp
    }
    data class BatchPatch(val batch: BatchEditDelta) : Patch {
        override val chapterIndex get() = batch.chapterIndex
        override val timestamp get() = batch.timestamp
    }

    private val _patches = mutableListOf<Patch>()
    val patches: List<Patch> get() = _patches.toList()

    private val undoStack = ArrayDeque<Patch>()
    private val redoStack = ArrayDeque<Patch>()

    /** 添加单个编辑（同步写入 DB） */
    suspend fun addSingle(delta: EditDelta) {
        val patch = SinglePatch(delta)
        _patches.add(patch)
        undoStack.addLast(patch)
        redoStack.clear()
        editDeltaDao.insert(delta)
    }

    /** 添加批量编辑 */
    suspend fun addBatch(batch: BatchEditDelta) {
        val patch = BatchPatch(batch)
        _patches.add(patch)
        undoStack.addLast(patch)
        redoStack.clear()
        editDeltaDao.insertBatch(batch)
    }

    /** 获取指定章节的所有 Delta（展开批量，按 charStart 降序） */
    fun getDeltasForChapter(chapterIndex: Int): List<EditDelta> =
        _patches
            .filter { it.chapterIndex == chapterIndex }
            .flatMap { patch ->
                when (patch) {
                    is SinglePatch -> listOf(patch.delta)
                    is BatchPatch -> patch.batch.expand()
                }
            }
            .sortedByDescending { it.charStart }

    fun undo(): Patch? { /* ... 同步更新 DB */ }
    fun redo(): Patch? { /* ... */ }
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val isEmpty: Boolean get() = _patches.isEmpty()

    suspend fun clear() {
        _patches.clear()
        undoStack.clear()
        redoStack.clear()
        editDeltaDao.deleteAll()
    }
}
```

### 2.4 数据流

```
                    ┌──────────────┐
                    │  EditStore    │ ← 内存 + DB 双层
                    │  (Patch 列表) │
                    └──────┬───────┘
                           │
         ┌─────────────────┼──────────────────┐
         ▼                 ▼                  ▼
  BookContentRepo    TextEditPanel      TextEditManager
  (读取时叠加 Delta)  (UI 编辑操作)    (流式保存到文件)
```

**读取路径（叠加 Delta）：**

```kotlin
// BookContentRepository.getChapterText() 改造
suspend fun getChapterText(
    file: File, chapterIndex: Int, chapters: List<Chapter>,
    bookId: Long = 0L, editStore: EditStore? = null,
): String {
    val rawText = readFromOriginalFile(file, chapterIndex, chapters, bookId) // 原有逻辑
    if (editStore == null) return rawText

    val deltas = editStore.getDeltasForChapter(chapterIndex)
    if (deltas.isEmpty()) return rawText

    // 按 charStart 降序应用（避免偏移错位）
    val sb = StringBuilder(rawText)
    for (delta in deltas) {  // 已按 charStart 降序排列
        sb.replace(delta.charStart, delta.charEnd, delta.newText)
    }
    return sb.toString()
}
```

---

## 3. 保存流程（流式写入 + 原子替换）

### 3.1 核心原则

- **不全量读入内存**：50MB 文件不能 `readText()` 后 `StringBuilder.replace`
- **流式逐章处理**：利用已有的 `ByteWindowReader` + `StreamDecoder` 按章节读取
- **原子替换**：先写临时文件，完成后 rename 覆盖原文件
- **自动备份**：写前 `.bak` 备份，防 IO 中途崩溃

### 3.2 保存流程

```kotlin
suspend fun TextEditManager.saveToFile(
    file: File,
    charset: Charset,
    bookContent: BookContent,
    bookChapterDao: BookChapterDao,
) = withContext(Dispatchers.IO) {
    val allDeltas = editStore.allDeltasExpanded()  // 所有 Delta，按章节分组
    if (allDeltas.isEmpty()) return@withContext

    // 1. 自动备份
    val bakFile = File(file.parent, "${file.name}.bak")
    file.copyTo(bakFile, overwrite = true)

    // 2. 流式逐章处理
    val tempFile = File(file.parent, "${file.name}.tmp")
    val chapters = bookContent.chapters

    FileOutputStream(tempFile).use { fos ->
        val writer = fos.writer(charset).buffered(64 * 1024)  // 64KB 写缓冲

        for ((index, chapter) in chapters.withIndex()) {
            // 读取原始章节文本（利用已有的 ByteWindowReader）
            val rawText = bookContentRepository.getChapterText(file, index, chapters)

            // 应用本章 Delta
            val deltas = allDeltas[index]  // 已按 charStart 降序
            val modifiedText = if (deltas.isNullOrEmpty()) {
                rawText
            } else {
                val sb = StringBuilder(rawText)
                for (delta in deltas) {
                    sb.replace(delta.charStart, delta.charEnd, delta.newText)
                }
                sb.toString()
            }

            writer.write(modifiedText)
        }
        writer.flush()
    }

    // 3. 原子替换
    tempFile.renameTo(file)

    // 4. 增量更新章节字节偏移（O(1)，不重解析章节）
    updateChapterOffsetsIncremental(bookContent.bookId, allDeltas, charset, bookChapterDao)

    // 5. 清空编辑
    editStore.clear()

    // 6. 删除备份（成功保存后）
    bakFile.delete()
}
```

### 3.3 增量章节偏移更新

**不重新解析章节**（避免 `TxtParser.parse` 全量扫描），而是计算每个 Delta 引起的字节长度变化，批量 UPDATE 后续章节偏移：

```kotlin
/**
 * O(1) 增量更新章节字节偏移。
 *
 * 原理：每个 Delta 的字节长度变化 = newText.getBytes(charset).size - 原文字节长度。
 * 累积变化量逐章传递，只需一条 SQL 批量 UPDATE。
 */
private suspend fun updateChapterOffsetsIncremental(
    bookId: Long,
    deltasByChapter: Map<Int, List<EditDelta>>,
    charset: Charset,
    bookChapterDao: BookChapterDao,
) {
    var cumulativeByteDiff = 0L

    // 按章节顺序遍历
    for (chapterIndex in deltasByChapter.keys.sorted()) {
        val deltas = deltasByChapter[chapterIndex] ?: continue

        // 计算本章的字节长度变化
        for (delta in deltas) {
            val originalByteLen = (delta.charEnd - delta.charStart).toLong() *
                if (charset.name().startsWith("UTF-8")) 3L else 2L  // 中文近似
            val newByteLen = delta.newText.toByteArray(charset).size.toLong()
            cumulativeByteDiff += (newByteLen - originalByteLen)
        }

        // 更新所有后续章节的偏移（批量 SQL）
        if (cumulativeByteDiff != 0L) {
            bookChapterDao.shiftByteOffsets(
                bookId = bookId,
                fromChapterIndex = chapterIndex + 1,
                byteDelta = cumulativeByteDiff,
            )
        }
    }
}
```

**BookChapterDao 新增方法：**

```kotlin
@Query("UPDATE book_chapters SET byteStart = byteStart + :byteDelta, byteEnd = byteEnd + :byteDelta WHERE bookId = :bookId AND chapterIndex >= :fromChapterIndex")
suspend fun shiftByteOffsets(bookId: Long, fromChapterIndex: Int, byteDelta: Long)
```

---

## 4. UI 设计

### 4.1 查找栏：Chrome 式轻量悬浮条

**放弃 BottomSheet 形式**。参考 Chrome 浏览器页内查找，在阅读界面顶部显示一个极窄悬浮条（~48dp 高），不遮挡阅读内容：

```
┌─────────────────────────────────────────┐
│ 🔍 [踟躇          ] 3/12 [◀][▶][✕]  │  ← 查找悬浮条（48dp）
├─────────────────────────────────────────┤
│                                         │
│         （阅读内容区域）                 │  ← 不被遮挡
│                                         │
│    ...踟蹰于石径之上...                 │  ← 匹配项高亮可见
│                                         │
└─────────────────────────────────────────┘
```

### 4.2 替换按需展开

默认只显示查找栏。点击 `▼` 图标展开替换输入：

```
┌─────────────────────────────────────────┐
│ 🔍 [踟躇          ] 3/12 [◀][▶][▼][✕]│
│ ✏️ [踟蹰          ]  [替换] [全部替换] │  ← 展开后（+40dp）
├─────────────────────────────────────────┤
│         （阅读内容区域）                 │
└─────────────────────────────────────────┘
```

### 4.3 正则开关

查找栏输入框右侧增加 `[.*]` 按钮（toggle 态），开启后使用 `Regex.findAll()` 替代 `String.indexOf`。

### 4.4 内联编辑模式

选区菜单点击「编辑」时，如果是针对选中文字的快速修改（无需查找替换），直接在阅读器画布上覆盖一个 `TextField`：

```
┌─────────────────────────────────────────┐
│                                         │
│         （阅读内容区域）                 │
│                                         │
│    ...[踟躇▏]于石径之上...             │  ← 内联 TextField 覆盖
│                                         │
│                              [↶ 撤销]   │
└─────────────────────────────────────────┘
```

- TextField 预填选中文字，自动获取焦点弹出键盘
- 用户修改后按回车 → 生成 EditDelta → 文本更新 → 重分页当前页
- 按 Esc/点击空白 → 取消编辑

### 4.5 查找高亮

在 `ReaderCanvasView` 渲染层绘制：

| 匹配类型 | 视觉 | 绘制方式 |
|---|---|---|
| 当前匹配 | 橙色边框 + 浅黄背景 | `drawRoundRect` stroke + fill |
| 其他匹配 | 半透明黄色背景 | `drawRoundRect` fill alpha=0.3 |

导航到不同匹配时，阅读器翻页并将匹配行定位到屏幕**上 30% 区域**（避免被底部查找栏遮挡）。

### 4.6 编辑记录面板

从查找栏的 `📝` 图标打开，显示 diff 视图：

```
┌─ 编辑记录 (3) ─────────────────────────┐
│                                         │
│  第5章                                  │
│  ~~踟躇~~ → **踟蹰**           [↶]    │
│  ~~的的~~ → **的**             [↶]    │
│                                         │
│  第3章                                  │
│  ~~他她~~ → **他**             [↶]    │
│                                         │
│                    [全部撤销] [保存]     │
└─────────────────────────────────────────┘
```

原文用删除线 + 红色，替换后用粗体 + 主题色。每条可单独撤销。

---

## 5. 文件结构

```
feature/reader/editor/
├── TextEditManager.kt        // 流式保存 + 增量偏移更新 + 备份恢复
├── TextEditViewModel.kt      // 查找/替换/撤销状态 + 正则支持
├── TextEditPanel.kt          // Chrome 式查找悬浮条 + 展开替换
├── InlineEditOverlay.kt      // 内联编辑覆盖层
└── EditHistoryPanel.kt       // 编辑记录 diff 面板

core/database/
├── entity/EditDeltaEntity.kt // Delta 持久化实体
├── dao/EditDeltaDao.kt       // Delta 持久化 DAO
```

---

## 6. 性能指标

| 操作 | 目标 | 措施 |
|---|---|---|
| 本章查找 | < 5ms | `String.indexOf` 循环（JVM 优化，O(n×m) 对 < 50KB 章节足够） |
| 正则查找 | < 20ms | `Regex.findAll()`，编译一次复用 |
| 全书查找（500 章） | < 500ms | 流式逐章 `indexOf`，`Dispatchers.IO` |
| 单次替换 | < 1ms | 内存 Delta 追加 + DB insert |
| 全部替换（1000 处） | < 10ms | 单个 `BatchEditDelta` + 批量 DB insert |
| 保存（50MB 文件） | < 200ms | 流式逐章读写 + 64KB 缓冲 |
| 章节偏移更新 | < 5ms | 单条 SQL 批量 UPDATE |
| 内联编辑确认 | < 50ms | 生成 Delta + 重分页当前页 |

---

## 7. 与现有系统集成

### 7.1 ReaderIntent 扩展

```kotlin
sealed interface ReaderIntent {
    // ... 已有 ...
    data object OpenTextEdit : ReaderIntent               // 打开查找悬浮条
    data class InlineEdit(val text: String) : ReaderIntent // 内联编辑当前选区
    data class FindNext(val forward: Boolean) : ReaderIntent
    data class ReplaceCurrent(val replacement: String) : ReaderIntent
    data class ReplaceAll(val find: String, val replace: String, val isRegex: Boolean) : ReaderIntent
    data object UndoEdit : ReaderIntent
    data object SaveEdits : ReaderIntent
}
```

### 7.2 退出保护

用户退出阅读器时，如果 `EditStore` 非空：

```
┌──────────────────────────────────────┐
│  有 3 处未保存的修改                 │
│                                      │
│  [放弃修改]          [保存并退出]    │
└──────────────────────────────────────┘
```

### 7.3 崩溃恢复

`EditStore` 的 Delta 持久化到 Room `edit_delta` 表。App 重启后：
1. 检查 `edit_delta` 表是否有记录
2. 有 → 恢复 EditStore，提示用户"有未保存的编辑"
3. 检查 `.bak` 文件是否存在 → 提示用户"上次保存可能中断，是否恢复备份"

### 7.4 BookContentRepository 改造

```kotlin
suspend fun getChapterText(
    file: File, chapterIndex: Int, chapters: List<Chapter>,
    bookId: Long = 0L, editStore: EditStore? = null,
): String  // §2.4 已描述
```

---

## 8. 实施步骤

### Phase 1：查找替换 + 保存（P0）✅ 已完成

- [x] `EditDelta` / `BatchEditDelta` 数据模型
- [x] `EditDeltaEntity` + `EditDeltaDao`（Room 持久化）
- [x] `EditStore` — 内存 + DB 双层存储 + 撤销/重做
- [x] `TextEditViewModel` — 查找/替换/正则/导航
- [x] `TextEditPanel` — Chrome 式查找悬浮条 + 展开替换
- [x] `InlineEditOverlay` — 内联编辑覆盖层
- [x] `TextEditManager.saveToFile()` — 流式保存 + 原子替换 + .bak 备份
- [x] `TextEditManager.updateChapterOffsetsIncremental()` — 增量偏移更新
- [x] `BookContentRepository.getChapterText()` — 叠加 Delta
- [x] 阅读器画布查找匹配高亮（当前/其他）
- [x] 选区菜单新增「编辑」按钮
- [x] 退出时未保存修改确认对话框

### Phase 2：全书查找 + 编辑记录（P1）✅ 已完成

- [x] 全书查找（流式跨章节扫描 + 进度显示）
- [x] `EditHistoryPanel` — diff 视图 + 逐条撤销
- [x] 查找历史（最近 20 条）
- [x] 大小写敏感开关
- [x] 崩溃恢复（DB + .bak 检测）
