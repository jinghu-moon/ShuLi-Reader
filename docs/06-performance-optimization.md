# 06 - 性能优化策略

## 目标指标

| 指标 | 目标值 |
|------|--------|
| 首屏渲染 | < 500ms |
| 翻页帧率 | 60fps |
| 内存占用 | < 150MB |
| 文件大小 | 支持 100MB+ |

## 1. 文件读取优化

### mmap 内存映射

```kotlin
class MmapFileReader(private val file: File) {
    private val mappedByteBuffer: MappedByteBuffer
    
    init {
        val fileChannel = FileInputStream(file).channel
        mappedByteBuffer = fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            0,
            file.length()
        )
    }
    
    // 零拷贝读取
    fun read(offset: Long, length: Int): ByteArray {
        val buffer = ByteArray(length)
        mappedByteBuffer.position(offset.toInt())
        mappedByteBuffer.get(buffer)
        return buffer
    }
}
```

### 流式读取

```kotlin
class StreamingReader(private val file: File) {
    // 按需读取，避免一次性加载整个文件
    suspend fun readChunk(offset: Long, size: Int): String = withContext(Dispatchers.IO) {
        file.inputStream().use { stream ->
            stream.skip(offset)
            val buffer = ByteArray(size)
            val bytesRead = stream.read(buffer)
            String(buffer, 0, bytesRead, charset)
        }
    }
}
```

## 2. 分页优化

### 虚拟化分页

```kotlin
class VirtualPaginator(
    private val text: String,
    private val pageSize: PageSize
) {
    // 仅计算当前页及前后各一页
    private val cache = LruCache<Int, Page>(maxSize = 3)
    
    fun getPage(index: Int): Page {
        return cache.get(index) ?: run {
            val page = calculatePage(index)
            cache.put(index, page)
            page
        }
    }
    
    // 预加载下一页
    fun preloadNext(currentIndex: Int) {
        CoroutineScope(Dispatchers.Default).launch {
            getPage(currentIndex + 1)
        }
    }
}
```

### 增量分页计算

```kotlin
class IncrementalPaginator {
    // 记录已计算的断点
    private val breakpoints = mutableListOf<Int>()
    
    // 增量计算，避免重复计算
    fun calculateBreakpoints(text: String, fromIndex: Int = 0): List<Int> {
        val start = breakpoints.lastOrNull() ?: 0
        // 从上次断点继续计算
        // ...
        return breakpoints
    }
}
```

## 3. 渲染优化

### Canvas 绘制优化

```kotlin
class OptimizedTextRenderer {
    // 复用 Paint 对象
    private val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = 18f * density
    }
    
    // 批量绘制，减少 draw call
    fun renderLines(canvas: Canvas, lines: List<TextLine>) {
        canvas.save()
        for (line in lines) {
            canvas.drawText(line.text, line.x, line.y, textPaint)
        }
        canvas.restore()
    }
}
```

### 离屏缓冲

```kotlin
class OffscreenBuffer(
    private val width: Int,
    private val height: Int
) {
    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null
    
    // 预渲染下一页到离屏缓冲
    fun prerenderPage(page: Page) {
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        canvas = Canvas(bitmap!!)
        // 渲染到离屏 canvas
    }
    
    // 翻页时直接绘制缓冲的 bitmap
    fun drawToScreen(targetCanvas: Canvas) {
        bitmap?.let { targetCanvas.drawBitmap(it, 0f, 0f, null) }
    }
}
```

## 4. 动画优化

### 翻页动画性能

```kotlin
class PageFlipAnimation {
    // 使用 Animatior 而非协程，帧率更稳定
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 300
        interpolator = DecelerateInterpolator()
        
        addUpdateListener { animation ->
            val progress = animation.animatedValue as Float
            renderFrame(progress)
        }
    }
    
    // 仅更新变化区域
    private fun renderFrame(progress: Float) {
        // 计算脏区域
        val dirtyRect = calculateDirtyRect(progress)
        // 仅重绘脏区域
        invalidate(dirtyRect)
    }
}
```

## 5. 内存优化

### LRU 缓存策略

```kotlin
class CacheManager {
    // 页面缓存
    val pageCache = LruCache<Int, Page>(maxSize = 5)
    
    // 位图缓存
    val bitmapCache = object : LruCache<Int, Bitmap>(maxSize = 3 * 1024 * 1024) { // 3MB
        override fun sizeOf(key: Int, value: Bitmap): Int {
            return value.byteCount
        }
    }
    
    // 清理缓存
    fun clearAll() {
        pageCache.evictAll()
        bitmapCache.evictAll()
    }
}
```

### 内存监控

```kotlin
class MemoryMonitor {
    fun logMemoryUsage() {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        
        Log.d("Memory", "Used: ${usedMemory / 1024 / 1024}MB")
        Log.d("Memory", "Max: ${maxMemory / 1024 / 1024}MB")
        Log.d("Memory", "Usage: ${usedMemory * 100 / maxMemory}%")
    }
    
    // 内存紧张时主动释放
    fun onTrimMemory(level: Int) {
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                // 释放非关键缓存
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                // 释放所有缓存
            }
        }
    }
}
```

## 6. 数据库优化

### Room 查询优化

```kotlin
@Dao
interface BookDao {
    // 使用索引
    @Query("SELECT * FROM books WHERE id = :id")
    fun getBookById(id: Long): Flow<Book?>
    
    // 分页查询
    @Query("SELECT * FROM books ORDER BY last_read_time DESC LIMIT :limit OFFSET :offset")
    suspend fun getBooks(limit: Int, offset: Int): List<Book>
    
    // 使用 FTS4 全文搜索
    @Query("SELECT * FROM books_fts WHERE books_fts MATCH :query")
    fun searchBooks(query: String): Flow<List<Book>>
}

// FTS4 虚拟表
@Fts4
@Entity(tableName = "books_fts")
data class BookFts(
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "author") val author: String
)
```

## 7. 协程优化

### 结构化并发

```kotlin
class ReaderViewModel : ViewModel() {
    // 使用 viewModelScope，自动取消
    fun loadBook(bookId: Long) {
        viewModelScope.launch {
            val book = bookRepository.getBookById(bookId).first()
            val pages = paginator.paginate(book.content)
            _uiState.value = ReaderUiState.Success(pages)
        }
    }
    
    // 并行加载
    fun loadBookDetails(bookId: Long) {
        viewModelScope.launch {
            val bookDeferred = async { bookRepository.getBookById(bookId) }
            val progressDeferred = async { progressRepository.getProgress(bookId) }
            val bookmarksDeferred = async { bookmarkRepository.getBookmarks(bookId) }
            
            val book = bookDeferred.await()
            val progress = progressDeferred.await()
            val bookmarks = bookmarksDeferred.await()
            
            // 更新 UI
        }
    }
}
```

## 8. 启动优化

### 延迟初始化

```kotlin
class ShuLiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 仅初始化必要组件
        initKoin()
        
        // 延迟初始化
        Handler(Looper.getMainLooper()).post {
            initAnalytics()
            initSync()
        }
    }
}
```

### 预加载

```kotlin
class BookshelfViewModel {
    // 预加载封面图
    fun preloadCovers(books: List<Book>) {
        books.forEach { book ->
            imageLoader.load(book.coverUrl)
        }
    }
}
```

## 性能监控

```kotlin
class PerformanceMonitor {
    // 帧率监控
    fun monitorFrameRate() {
        Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                // 计算帧率
                // 记录掉帧
                Choreographer.getInstance().postFrameCallback(this)
            }
        })
    }
    
    // 方法耗时
    inline fun <T> measureTimeMillis(tag: String, block: () -> T): T {
        val start = System.currentTimeMillis()
        val result = block()
        val duration = System.currentTimeMillis() - start
        Log.d("Performance", "$tag: ${duration}ms")
        return result
    }
}
```
