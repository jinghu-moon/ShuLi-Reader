# 05 - 核心模块设计

## 1. 文件解析模块

### TXT 解析器

```kotlin
class TxtParser {
    // 编码检测
    suspend fun detectEncoding(file: File): Charset
    
    // 文件解析
    suspend fun parse(file: File, charset: Charset): BookContent
    
    // 分章检测
    fun detectChapters(content: String): List<Chapter>
}

data class BookContent(
    val title: String,
    val chapters: List<Chapter>,
    val totalLength: Long
)

data class Chapter(
    val title: String,
    val startIndex: Int,
    val endIndex: Int
)
```

### EPUB 解析器

```kotlin
class EpubParser {
    // 解压 EPUB
    suspend fun unzip(file: File, targetDir: File)
    
    // 解析 container.xml
    fun parseContainer(file: File): String
    
    // 解析 content.opf
    fun parseContent(file: File): EpubMetadata
    
    // 解析目录
    fun parseToc(file: File): List<Chapter>
    
    // 渲染 HTML
    fun renderHtml(html: String): String
}

data class EpubMetadata(
    val title: String,
    val author: String,
    val coverPath: String?,
    val chapters: List<Chapter>
)
```

### 编码检测封装

```kotlin
object CharsetDetector {
    fun detect(bytes: ByteArray): Charset {
        val detector = UniversalDetector(null)
        detector.handleData(bytes, 0, bytes.size)
        detector.dataEnd()
        return Charset.forName(detector.detectedCharset ?: "UTF-8")
    }
}
```

## 2. 阅读引擎模块

### 分页器

```kotlin
class Paginator(
    private val textLayout: TextLayout,
    private val pageSize: PageSize
) {
    // 计算分页
    fun paginate(text: String): List<Page>
    
    // 虚拟化加载
    fun loadPage(index: Int): Page
    
    // 缓存管理
    private val cache = LruCache<Int, Page>(maxSize = 5)
}

data class Page(
    val index: Int,
    val lines: List<TextLine>,
    val startIndex: Int,
    val endIndex: Int
)

data class TextLine(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float
)
```

### 渲染器

```kotlin
class TextRenderer(
    private val canvas: Canvas,
    private val paint: Paint
) {
    // 渲染单页
    fun renderPage(page: Page)
    
    // 渲染文本行
    fun renderLine(line: TextLine)
    
    // 渲染高亮
    fun renderHighlight(range: IntRange, color: Color)
}

class PageRenderer(
    private val textRenderer: TextRenderer,
    private val animationController: AnimationController
) {
    // 渲染当前页
    fun renderCurrentPage(page: Page)
    
    // 渲染翻页动画
    fun renderPageFlip(from: Page, to: Page, progress: Float)
}
```

### 翻页动画控制器

```kotlin
class AnimationController {
    // 翻页状态
    enum class FlipState { IDLE, FLIPPING, SETTLING }
    
    // 翻页方式
    enum class FlipStyle { SLIDE, SCROLL, COVER, SIMULATION }
    
    // 开始翻页
    fun startFlip(direction: Direction)
    
    // 更新翻页进度
    fun updateFlip(progress: Float)
    
    // 结束翻页
    fun endFlip()
}

class SimulationFlipRenderer {
    // 贝塞尔曲线控制点
    private val controlPoints = arrayOf(PointF(), PointF(), PointF())
    
    // 渲染仿真翻页
    fun render(canvas: Canvas, progress: Float)
    
    // 计算阴影
    private fun calculateShadow(progress: Float): Shadow
}
```

## 3. 数据存储模块

### 数据库表设计

```sql
-- 书籍信息表
CREATE TABLE books (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    author TEXT,
    file_path TEXT NOT NULL,
    file_type TEXT NOT NULL,  -- TXT, EPUB
    file_size INTEGER,
    cover_path TEXT,
    last_read_time INTEGER,
    added_time INTEGER,
    reading_progress REAL DEFAULT 0
);

-- 书签表
CREATE TABLE bookmarks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    book_id INTEGER NOT NULL,
    page_index INTEGER NOT NULL,
    position INTEGER NOT NULL,
    title TEXT,
    created_time INTEGER,
    FOREIGN KEY (book_id) REFERENCES books(id)
);

-- 笔记表
CREATE TABLE notes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    book_id INTEGER NOT NULL,
    start_position INTEGER NOT NULL,
    end_position INTEGER NOT NULL,
    content TEXT,
    color TEXT,
    created_time INTEGER,
    FOREIGN KEY (book_id) REFERENCES books(id)
);

-- 阅读进度表
CREATE TABLE reading_progress (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    book_id INTEGER NOT NULL,
    page_index INTEGER NOT NULL,
    position INTEGER NOT NULL,
    read_time INTEGER,  -- 阅读时长（秒）
    updated_time INTEGER,
    FOREIGN KEY (book_id) REFERENCES books(id)
);
```

### Repository 接口

```kotlin
interface BookRepository {
    fun getAllBooks(): Flow<List<Book>>
    fun getBookById(id: Long): Flow<Book?>
    suspend fun insertBook(book: Book): Long
    suspend fun updateBook(book: Book)
    suspend fun deleteBook(id: Long)
    fun searchBooks(query: String): Flow<List<Book>>
}

interface BookmarkRepository {
    fun getBookmarks(bookId: Long): Flow<List<Bookmark>>
    suspend fun addBookmark(bookmark: Bookmark)
    suspend fun deleteBookmark(id: Long)
}

interface ReadingProgressRepository {
    fun getProgress(bookId: Long): Flow<ReadingProgress?>
    suspend fun updateProgress(progress: ReadingProgress)
}
```

## 4. 主题系统模块

### 主题数据模型

```kotlin
data class ReaderTheme(
    val id: String,
    val name: String,
    val background: Color,
    val textColor: Color,
    val accentColor: Color,
    val fontFamily: String,
    val fontSize: Sp,
    val lineHeight: Float,
    val pageMargin: Dp
)
```

### 主题管理器

```kotlin
class ThemeManager(
    private val context: Context
) {
    // 获取当前主题
    fun getCurrentTheme(): ReaderTheme
    
    // 设置主题
    fun setTheme(theme: ReaderTheme)
    
    // 导入主题
    fun importTheme(uri: Uri): ReaderTheme
    
    // 导出主题
    fun exportTheme(theme: ReaderTheme, uri: Uri)
    
    // 跟随系统
    fun followSystemTheme(enable: Boolean)
}
```

## 5. 同步模块

### WebDAV 客户端

```kotlin
class WebDavClient(
    private val httpClient: OkHttpClient
) {
    // 连接测试
    suspend fun testConnection(url: String, credentials: Credentials): Boolean
    
    // 列出文件
    suspend fun listFiles(url: String): List<WebDavFile>
    
    // 下载文件
    suspend fun download(remoteUrl: String, localFile: File)
    
    // 上传文件
    suspend fun upload(localFile: File, remoteUrl: String)
    
    // 删除文件
    suspend fun delete(remoteUrl: String)
}
```

### 同步管理器

```kotlin
class SyncManager(
    private val webDavClient: WebDavClient,
    private val progressRepository: ReadingProgressRepository
) {
    // 同步状态
    enum class SyncState { IDLE, SYNCING, SUCCESS, ERROR }
    
    // 执行同步
    suspend fun sync(): SyncState
    
    // 解决冲突
    private suspend fun resolveConflict(local: ReadingProgress, remote: ReadingProgress): ReadingProgress
}
```

## 6. 搜索模块

### 全文搜索

```kotlin
class SearchEngine(
    private val bookRepository: BookRepository
) {
    // 搜索书籍
    suspend fun searchBooks(query: String): List<Book>
    
    // 搜索内容
    suspend fun searchContent(bookId: Long, query: String): List<SearchResult>
    
    // 高亮关键词
    fun highlightText(text: String, query: String): AnnotatedString
}

data class SearchResult(
    val pageIndex: Int,
    val position: Int,
    val context: String,  // 上下文
    val matchStart: Int,
    val matchEnd: Int
)
```

## 7. TTS 模块

### TTS 管理器

```kotlin
class TtsManager(
    private val context: Context
) {
    // 初始化 TTS
    suspend fun init()
    
    // 朗读文本
    fun speak(text: String)
    
    // 暂停
    fun pause()
    
    // 继续
    fun resume()
    
    // 停止
    fun stop()
    
    // 设置语速
    fun setSpeed(speed: Float)
    
    // 设置音调
    fun setPitch(pitch: Float)
}
```
