# 09 - 高性能库选型指南

> 基于 Legado 项目实践和 Android 生态现状，为书里阅读器推荐的高性能库选型方案。

## 选型原则

遵循奥卡姆剃刀原则：
- 能用系统 API 就不引入第三方库
- 功能单一、体积小、无传递依赖优先
- 只在确实需要时才增加复杂度

---

## 一、核心渲染层

### 1. 文本测量与绘制

| 库 | 用途 | 说明 |
|---|---|---|
| `androidx.compose.ui:ui-text` | 文本测量 | `TextMeasurer` API，Compose 1.3+ |
| `androidx.compose.ui:ui-graphics` | Canvas 绘制 | `drawWithCache`、`drawText` |
| **原生 Canvas API** | 高性能文本绘制 | `Canvas.drawText()` + `TextPaint`，Legado 采用方案 |

**推荐方案**：核心阅读视图使用传统 `View` + `Canvas` 直接绘制，比 Compose Canvas 性能更优：

```kotlin
// 高性能文本绘制
class ContentTextView(context: Context) : View(context) {
    private val textPaint = TextPaint().apply {
        isAntiAlias = true
        textSize = 18f * density
    }

    override fun onDraw(canvas: Canvas) {
        // 直接绘制，避免 Compose 重组开销
        canvas.drawText(text, x, y, textPaint)
    }
}
```

### 2. 中文排版

| 库 | 用途 | 说明 |
|---|---|---|
| **自研 ZhLayout** | 中文排版优化 | Legado 方案，处理中文标点禁则 |
| `android.text.StaticLayout` | 系统内置排版 | 通用文本排版 |

---

## 二、文件解析层

### 1. 编码检测

| 库 | 体积 | 说明 |
|---|---|---|
| `com.github.albfernandez:juniversalchardet` | ~60KB | Mozilla 引擎，轻量准确 |
| `com.ibm.icu:icu4j` | ~12MB | 功能全面，体积过大 |

**推荐**：`juniversalchardet`，体积小、准确率高。

### 2. EPUB 解析

| 库 | 用途 | 说明 |
|---|---|---|
| `org.jsoup:jsoup` | HTML/XML 解析 | CSS 选择器，DOM 操作 |
| `java.util.zip` | ZIP 解压 | 系统内置，零依赖 |
| `nl.siegmann.epublib:epublib-core` | EPUB 完整解析 | 功能全面，但体积大 |

**推荐**：`Jsoup` + `java.util.zip` 组合，按需解析，避免加载整个 EPUB 到内存。

### 3. TXT 章节检测

```kotlin
// 正则表达式检测章节
val CHAPTER_PATTERNS = listOf(
    Regex("^第[一二三四五六七八九十百千零\\d]+[章节回卷集部篇]"),
    Regex("^Chapter\\s+\\d+", RegexOption.IGNORE_CASE),
    Regex("^卷[一二三四五六七八九十百千零\\d]+"),
)
```

---

## 三、内存管理

### 1. 缓存

| 库 | 用途 | 说明 |
|---|---|---|
| `androidx.collection:LruCache` | LRU 缓存 | 系统内置，章节/页面缓存 |
| `io.coil-kt:coil-compose` | 图片缓存 | Kotlin 优先，Compose 原生支持 |

```kotlin
// 章节缓存
val chapterCache = LruCache<Int, TextChapter>(maxSize = 5)

// 页面缓存
val pageCache = LruCache<Int, TextPage>(maxSize = 10)

// 位图缓存（离屏渲染）
val bitmapCache = object : LruCache<Int, Bitmap>(maxSize = 3 * 1024 * 1024) {
    override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
}
```

### 2. 内存映射（大文件）

```kotlin
// mmap 零拷贝读取
class MmapFileReader(private val file: File) {
    private val mappedByteBuffer: MappedByteBuffer

    init {
        val fileChannel = FileInputStream(file).channel
        mappedByteBuffer = fileChannel.map(
            FileChannel.MapMode.READ_ONLY, 0, file.length()
        )
    }

    fun read(offset: Long, length: Int): ByteArray {
        val buffer = ByteArray(length)
        mappedByteBuffer.position(offset.toInt())
        mappedByteBuffer.get(buffer)
        return buffer
    }
}
```

---

## 四、异步与协程

| 库 | 用途 | 说明 |
|---|---|---|
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 协程 | Kotlin 标准，IO/Default/Main 调度 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-flow` | 响应式流 | 状态管理、数据流 |

```kotlin
// 结构化并发 - 并行加载
fun loadBookDetails(bookId: Long) {
    viewModelScope.launch {
        val bookDeferred = async(Dispatchers.IO) { bookRepository.getBookById(bookId) }
        val progressDeferred = async(Dispatchers.IO) { progressRepository.getProgress(bookId) }
        val bookmarksDeferred = async(Dispatchers.IO) { bookmarkRepository.getBookmarks(bookId) }

        val book = bookDeferred.await()
        val progress = progressDeferred.await()
        val bookmarks = bookmarksDeferred.await()

        // 更新 UI
    }
}
```

---

## 五、数据持久化

| 库 | 用途 | 说明 |
|---|---|---|
| `androidx.room:room-ktx` | 数据库 | FTS4 全文搜索，Flow 支持 |
| `androidx.datastore:datastore-preferences` | 配置存储 | 替代 SharedPreferences |

```kotlin
// FTS4 全文搜索
@Fts4
@Entity(tableName = "books_fts")
data class BookFts(
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "author") val author: String
)
```

---

## 六、序列化

| 库 | 体积 | 说明 |
|---|---|---|
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | ~300KB | Kotlin 原生，编译时生成 |
| `com.google.code.gson:gson` | ~200KB | 反射，运行时解析 |

**推荐**：`kotlinx-serialization`，类型安全，性能更优。

---

## 七、依赖注入

| 库 | 体积 | 说明 |
|---|---|---|
| `io.insert-koin:koin-androidx-compose` | ~300KB | 无需注解处理器，编译快 |
| `com.google.dagger:hilt-android` | ~1MB | 编译时 DI，功能强大 |

**推荐**：`Koin`，轻量、API 简洁，适合中小型项目。

---

## 八、网络同步（可选）

| 库 | 用途 | 说明 |
|---|---|---|
| `com.squareup.okhttp3:okhttp` | HTTP 客户端 | WebDAV 需要底层控制 |
| `io.ktor:ktor-client` | HTTP 客户端 | Kotlin 原生，协程支持 |

---

## 九、性能监控

```kotlin
// 帧率监控
class PerformanceMonitor {
    fun monitorFrameRate() {
        Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                // 计算帧率，记录掉帧
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

---

## 十、最终依赖清单（精简版）

```kotlin
dependencies {
    // 架构
    implementation("io.insert-koin:koin-androidx-compose:3.5.6")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // 文件解析
    implementation("com.github.albfernandez:juniversalchardet:2.4.0")
    implementation("org.jsoup:jsoup:1.17.2")

    // 网络同步（可选）
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // 图片
    implementation("io.coil-kt:coil-compose:2.6.0")

    // 测试
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("junit:junit:4.13.2")
}
```

### 依赖统计

| 指标 | 数值 |
|------|------|
| 第三方依赖数 | 8 个 |
| 依赖总大小 | ~3.1MB |
| 注解处理器 | 0 个 |

---

## 关键原则

### 1. Canvas 直接绘制

核心阅读视图用传统 View，避免 Compose 重组开销：

```kotlin
// 性能对比
// Compose Canvas: ~16ms/帧（包含重组开销）
// 传统 View Canvas: ~8ms/帧（直接绘制）
```

### 2. mmap 内存映射

大文件（100MB+）使用零拷贝读取：

```kotlin
// 性能对比
// 传统 IO: 读取 100MB 文件需要 ~500ms
// mmap: 首次访问 ~10ms，后续访问 ~0ms（按需加载）
```

### 3. LRU 缓存

章节/页面/位图三级缓存：

```
┌─────────────────────────────────────────────────────────┐
│                    缓存层级                              │
├─────────────────────────────────────────────────────────┤
│  L1: 页面缓存 (TextPage)      │ maxSize = 10           │
│  L2: 章节缓存 (TextChapter)   │ maxSize = 5            │
│  L3: 位图缓存 (Bitmap)        │ maxSize = 3MB          │
└─────────────────────────────────────────────────────────┘
```

### 4. 异步分页

协程 IO 线程分页，不阻塞主线程：

```kotlin
fun paginateAsync(text: String) = scope.launch(Dispatchers.IO) {
    val pages = ChapterProvider.paginate(text)
    withContext(Dispatchers.Main) {
        updatePages(pages)
    }
}
```

### 5. 最小依赖

能用系统 API 就不引入第三方库：

| 功能 | 系统 API | 第三方库 | 选择 |
|------|----------|----------|------|
| ZIP 解压 | `java.util.zip` | `commons-compress` | 系统 API |
| XML 解析 | `XmlPullParser` | `DOM` | 系统 API |
| KV 存储 | `SharedPreferences` | `DataStore` | 系统 API |
| 异步 | `Coroutines` | `RxJava` | Coroutines |

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
| **09** | **高性能库选型指南** | **高性能库推荐方案** |
