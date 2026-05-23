# 11 - 阅读进度与位置管理设计

> 基于 Legado 项目的成熟方案，为书里阅读器设计的阅读进度追踪、位置保存与恢复机制。

## 设计目标

- **精确性**：字体大小、屏幕尺寸变化不影响位置精度
- **轻量性**：最小化数据库写入频率，避免性能损耗
- **可靠性**：异常退出不丢失进度
- **可同步性**：支持多端进度同步

---

## 一、核心数据模型

### 1.1 阅读位置（存储到 BookEntity）

```kotlin
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String?,
    val filePath: String,
    val fileType: String,
    val fileSize: Long,
    val coverPath: String?,
    val addedTime: Long,

    // 阅读进度字段
    var durChapterIndex: Int = 0,       // 当前章节索引
    var durChapterPos: Int = 0,         // 章节内字符偏移量
    var durChapterTitle: String? = null, // 当前章节标题
    var durChapterTime: Long = 0,       // 最近阅读时间戳
    var totalChapterNum: Int = 0,       // 章节总数（缓存）
    var readingProgress: Float = 0f,    // 进度百分比（0.0 ~ 1.0）
)
```

**关键设计**：
- `durChapterPos` 是**字符偏移量**，不是页码
- 字体/屏幕变化时，通过重新排版即可正确定位
- `readingProgress` 是冗余字段，用于书架快速展示，避免实时计算

### 1.2 书签模型

```kotlin
@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val chapterIndex: Int,      // 章节索引
    val chapterPos: Int,        // 字符偏移量
    val chapterName: String,    // 章节名称
    val selectedText: String,   // 选中文本（书签上下文）
    val content: String,        // 用户备注
    val createdAt: Long,
)
```

### 1.3 进度传输对象（同步用）

```kotlin
data class BookProgress(
    val bookId: Long,
    val title: String,
    val author: String?,
    val durChapterIndex: Int,
    val durChapterPos: Int,
    val durChapterTitle: String?,
    val durChapterTime: Long,
)
```

---

## 二、进度计算公式

### 2.1 百分比计算

```kotlin
// TextPage 扩展属性
val TextPage.readProgress: Float
    get() {
        if (chapterSize == 0) return 0f
        if (pageSize == 0 && chapterIndex == 0) return 0f

        // 章节级进度 + 页码级贡献
        val chapterProgress = chapterIndex.toFloat() / chapterSize
        val pageProgress = (1f / chapterSize) * (index + 1) / pageSize

        return (chapterProgress + pageProgress).coerceAtMost(0.999f)
    }
```

### 2.2 计算层级

```
┌─────────────────────────────────────────────────────────┐
│                    进度计算层级                          │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  整体进度 = 章节级进度 + 页码级贡献                      │
│                                                         │
│  章节级进度 = chapterIndex / chapterSize                 │
│     例：第 5 章 / 共 20 章 = 0.25                       │
│                                                         │
│  页码级贡献 = (1/chapterSize) × (pageIndex+1)/pageSize  │
│     例：(1/20) × (3/10) = 0.015                        │
│                                                         │
│  整体进度 = 0.25 + 0.015 = 0.265 = 26.5%               │
│                                                         │
│  兜底策略：非最后一页，上限 99.9%                        │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 2.3 阅读状态判断

```kotlin
// 未读
fun isUnread(): Boolean = durChapterIndex == 0 && durChapterPos == 0

// 在读
fun isReading(): Boolean = totalChapterNum > 0 
    && durChapterIndex > 0 
    && durChapterIndex < totalChapterNum - 1

// 已读完
fun isFinished(): Boolean = totalChapterNum > 0 
    && durChapterIndex >= totalChapterNum - 1
```

---

## 三、位置保存机制

### 3.1 保存触发时机

| 触发场景 | 调用方法 | 说明 |
|----------|----------|------|
| 翻页 | `moveToNextPage()` / `moveToPrevPage()` | 防抖 500ms 后保存 |
| 翻章 | `moveToNextChapter()` / `moveToPrevChapter()` | 立即保存 |
| 跳页 | `skipToPage()` | 立即保存 |
| 打开章节 | `openChapter()` | 立即保存 |
| Activity onPause | `onPause()` | 确保不丢失 |
| 应用进入后台 | `onStop()` | 最后防线 |

### 3.2 保存流程

```kotlin
// ReadingStateManager.kt
class ReadingStateManager(
    private val bookRepository: BookRepository,
    private val scope: CoroutineScope,
) {
    private var saveJob: Job? = null

    // 防抖保存（翻页场景）
    fun saveReadDebounced(bookId: Long, chapterIndex: Int, charPos: Int) {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(500) // 500ms 防抖
            saveRead(bookId, chapterIndex, charPos)
        }
    }

    // 立即保存（翻章/跳转/退出场景）
    suspend fun saveRead(bookId: Long, chapterIndex: Int, charPos: Int) {
        bookRepository.updateReadingPosition(
            bookId = bookId,
            chapterIndex = chapterIndex,
            charPos = charPos,
            timestamp = System.currentTimeMillis(),
        )
    }

    // 计算并更新进度百分比
    suspend fun updateProgress(bookId: Long, chapterIndex: Int, pageIndex: Int, pageSize: Int, chapterSize: Int) {
        val progress = if (chapterSize > 0 && pageSize > 0) {
            val chapterProgress = chapterIndex.toFloat() / chapterSize
            val pageProgress = (1f / chapterSize) * (pageIndex + 1) / pageSize
            (chapterProgress + pageProgress).coerceAtMost(0.999f)
        } else {
            0f
        }
        bookRepository.updateReadingProgress(bookId, progress)
    }
}
```

### 3.3 保存数据流

```
用户翻页
    │
    ▼
ReadingStateManager.saveReadDebounced()
    │
    ├─ 500ms 防抖
    │
    ▼
BookRepository.updateReadingPosition()
    │
    ▼
BookDao.updatePosition(bookId, chapterIndex, charPos, timestamp)
    │
    ▼
Room DB (books 表)
    │
    ▼
Flow 触发 → BookshelfScreen 自动刷新进度条
```

---

## 四、位置恢复机制

### 4.1 恢复流程

```
用户点击书籍
    │
    ▼
ReaderViewModel.openBook(bookId)
    │
    ▼
BookRepository.getBookById(bookId)
    │
    ▼
获取 durChapterIndex + durChapterPos
    │
    ▼
ChapterProvider.loadChapter(durChapterIndex)
    │
    ▼
分页完成，得到 pages: List<TextPage>
    │
    ▼
TextChapter.getPageIndexByCharIndex(durChapterPos)
    │
    ▼
定位到目标页面 → 渲染显示
```

### 4.2 字符偏移到页码的映射

```kotlin
// TextChapter.kt
fun getPageIndexByCharIndex(charIndex: Int): Int {
    for ((index, page) in pages.withIndex()) {
        if (page.startCharOffset <= charIndex && charIndex < page.endCharOffset) {
            return index
        }
    }
    return pages.size - 1 // 超出范围则定位到最后一页
}
```

### 4.3 TextPage 扩展字段

```kotlin
data class TextPage(
    val index: Int,
    val text: String,
    val textLines: ArrayList<TextLine>,
    val startCharOffset: Long,  // 本页起始字符偏移（全书）
    val endCharOffset: Long,    // 本页结束字符偏移（全书）
    val chapterIndex: Int,      // 所属章节索引
    val chapterSize: Int,       // 章节总数
    val pageSize: Int,          // 本章总页数
    // ...
)
```

---

## 五、数据库设计

### 5.1 BookDao 关键方法

```kotlin
@Dao
interface BookDao {
    // 更新阅读位置
    @Query("UPDATE books SET durChapterIndex = :chapterIndex, durChapterPos = :charPos, durChapterTime = :timestamp WHERE id = :bookId")
    suspend fun updateReadingPosition(bookId: Long, chapterIndex: Int, charPos: Int, timestamp: Long)

    // 更新进度百分比
    @Query("UPDATE books SET readingProgress = :progress WHERE id = :bookId")
    suspend fun updateReadingProgress(bookId: Long, progress: Float)

    // 更新章节总数
    @Query("UPDATE books SET totalChapterNum = :totalChapters WHERE id = :bookId")
    suspend fun updateTotalChapters(bookId: Long, totalChapters: Int)

    // 获取最近阅读的书籍
    @Query("SELECT * FROM books WHERE durChapterTime > 0 ORDER BY durChapterTime DESC LIMIT 1")
    fun getLastReadBook(): Flow<BookEntity?>

    // 获取未读书籍
    @Query("SELECT * FROM books WHERE durChapterIndex = 0 AND durChapterPos = 0")
    fun getUnreadBooks(): Flow<List<BookEntity>>

    // 获取在读书籍
    @Query("SELECT * FROM books WHERE totalChapterNum > 0 AND durChapterIndex > 0 AND durChapterIndex < totalChapterNum - 1")
    fun getReadingBooks(): Flow<List<BookEntity>>

    // 获取已读完书籍
    @Query("SELECT * FROM books WHERE totalChapterNum > 0 AND durChapterIndex >= totalChapterNum - 1")
    fun getFinishedBooks(): Flow<List<BookEntity>>
}
```

### 5.2 BookmarkDao

```kotlin
@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun getBookmarksByBookId(bookId: Long): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteAllBookmarksForBook(bookId: Long)
}
```

---

## 六、与分页引擎集成

### 6.1 分页时记录字符偏移

```kotlin
// ChapterProvider.kt
fun paginate(chapterIndex: Int, text: String, ...): List<TextPage> {
    val pages = mutableListOf<TextPage>()
    var currentOffset = 0L

    // ... 分页逻辑 ...

    for (pageIndex in 0 until pageCount) {
        val pageText = extractPageText(pageIndex)
        val page = TextPage(
            index = pageIndex,
            text = pageText,
            startCharOffset = currentOffset,
            endCharOffset = currentOffset + pageText.length,
            chapterIndex = chapterIndex,
            // ...
        )
        pages.add(page)
        currentOffset += pageText.length
    }

    return pages
}
```

### 6.2 翻页时更新进度

```kotlin
// ReaderViewModel.kt
fun onPageChanged(newPageIndex: Int) {
    val currentPage = currentPages[newPageIndex]

    // 保存位置（防抖）
    readingStateManager.saveReadDebounced(
        bookId = currentBookId,
        chapterIndex = currentPage.chapterIndex,
        charPos = currentPage.startCharOffset.toInt(),
    )

    // 更新进度百分比
    readingStateManager.updateProgress(
        bookId = currentBookId,
        chapterIndex = currentPage.chapterIndex,
        pageIndex = currentPage.index,
        pageSize = currentPage.pageSize,
        chapterSize = currentPage.chapterSize,
    )
}
```

---

## 七、书架进度展示

### 7.1 BookItem 进度字段

```kotlin
data class BookItem(
    val id: Long,
    val title: String,
    val readingProgress: Float,      // 0.0 ~ 1.0
    val unreadChapters: Int,         // 未读章节数
    val lastReadTime: Long?,
    // ...
)

// 计算未读章节数
fun BookEntity.toBookItem(): BookItem {
    return BookItem(
        id = id,
        title = title,
        readingProgress = readingProgress,
        unreadChapters = maxOf(totalChapterNum - durChapterIndex - 1, 0),
        lastReadTime = durChapterTime.takeIf { it > 0 },
        // ...
    )
}
```

### 7.2 UI 展示

```kotlin
// BookCard.kt
@Composable
fun BookCard(book: BookItem, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column {
            // 封面
            BookCover(coverUrl = book.coverUrl)

            // 书名
            Text(text = book.title, maxLines = 1, overflow = TextOverflow.Ellipsis)

            // 进度条
            LinearProgressIndicator(
                progress = book.readingProgress,
                modifier = Modifier.fillMaxWidth(),
            )

            // 进度文字
            Text(
                text = when {
                    book.readingProgress == 0f -> "未读"
                    book.readingProgress >= 0.999f -> "已读完"
                    else -> "${(book.readingProgress * 100).toInt()}%"
                },
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
```

---

## 八、WebDAV 进度同步（P2 阶段）

### 8.1 同步策略

```kotlin
// SyncManager.kt
suspend fun syncProgress(book: BookEntity) {
    val remote = webDavClient.downloadProgress(book.title, book.author)
    if (remote != null) {
        val localTime = book.durChapterTime
        val remoteTime = remote.durChapterTime

        if (remoteTime > localTime) {
            // 远程更新，提示用户同步
            emitSyncEvent(SyncEvent.RemoteNewer(book.id, remote))
        } else if (localTime > remoteTime) {
            // 本地更新，上传
            webDavClient.uploadProgress(BookProgress(book))
        }
    } else {
        // 远程无记录，上传
        webDavClient.uploadProgress(BookProgress(book))
    }
}
```

### 8.2 存储路径

```
WebDAV 根目录/
└── bookProgress/
    ├── 书名1_作者1.json
    ├── 书名2_作者2.json
    └── ...
```

---

## 九、性能优化

### 9.1 防抖写入

| 场景 | 策略 | 原因 |
|------|------|------|
| 翻页 | 500ms 防抖 | 用户快速翻页时避免频繁写入 |
| 翻章 | 立即保存 | 章节切换是关键节点 |
| 退出 | 立即保存 | 确保不丢失 |

### 9.2 进度百分比缓存

```kotlin
// 书架展示使用缓存的 readingProgress 字段
// 阅读器内实时计算，不写入数据库
// 仅在退出阅读器时批量更新 readingProgress
```

### 9.3 索引优化

```sql
-- BookDao 查询优化
CREATE INDEX idx_books_dur_chapter_time ON books(durChapterTime DESC);
CREATE INDEX idx_books_reading_progress ON books(readingProgress);
```

---

## 十、实现优先级

### P0 - 核心功能

| 功能 | 说明 |
|------|------|
| 位置保存 | `durChapterIndex` + `durChapterPos` |
| 位置恢复 | 字符偏移到页码映射 |
| 进度计算 | 章节 + 页码二级公式 |
| 书架进度条 | `readingProgress` 展示 |

### P1 - 增强功能

| 功能 | 说明 |
|------|------|
| 书签系统 | 手动标记 + 列表管理 |
| 阅读状态分类 | 未读/在读/已读完筛选 |
| 阅读时长统计 | 每日/每周/总计 |

### P2 - 同步功能

| 功能 | 说明 |
|------|------|
| WebDAV 同步 | 进度上传/下载 |
| 冲突处理 | 远程/本地时间戳比较 |

---

## 文档索引更新

| 序号 | 文档 | 内容 |
|------|------|------|
| 00 | 项目概述 | 项目基本信息 |
| 01 | 需求分析 | 功能需求与优先级 |
| 02 | 技术架构 | 整体架构设计 |
| 03 | 组件选型 | 第三方库选型 |
| 04 | UI设计系统 | 视觉规范与组件库 |
| 05 | 核心模块设计 | 关键模块详细设计 |
| 06 | 性能优化策略 | 性能优化方案 |
| 07 | 项目结构 | 代码目录结构 |
| 08 | Legado 阅读器分析 | 参考项目架构分析 |
| 09 | 高性能库选型指南 | 高性能库推荐方案 |
| 10 | 设置页面设计 | 完整配置项与优先级 |
| **11** | **阅读进度设计** | **进度追踪与位置管理** |
