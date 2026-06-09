# 27 - 阅读统计界面重构设计方案

> 编写时间：2026-06-09
> 原型参考：`docs/prototypes/stats-screen.html`
> 评审参考：`refer/stats_screen_review_overview.html`
> 范围：新增独立统计页面 · 数据层扩展 · 导航集成
> 原则：纯离线 · 纯统计（不做打卡/成就/游戏化）· 性能优先

---

## 1. 现状分析

### 1.1 已有数据基础设施

| 数据源 | 表/实体 | 已有字段 | 统计价值 |
|---|---|---|---|
| 阅读进度 | `reading_progress` | `readTime`(秒) · `updatedTime` | 每本书累计时长，可派生今日/本周/本月 |
| 章节统计 | `chapter_reading_stats` | `read_time_seconds` · `visited` · `first_visited_at` · `last_visited_at` | 章节粒度时长，可聚合为每日热力图 |
| 阅读历史 | `reading_history` | `read_count` · `finished_at` · `reading_duration_minutes` | 读完记录、重读次数 |
| 书籍元数据 | `books` | `readingStatus` · `readCount` · `lastReadTime` · `addedTime` · `fileType` · `totalChapterNum` | 阅读状态分布、格式分布 |
| 书签/笔记 | `bookmarks` / `notes` | 数量 | 榜单排序维度 |
| 书架 | `books` + `tags` + `book_tag_cross_ref` | 分组、标签 | 按分组/标签分布 |

### 1.2 已有统计能力

- `ReadingProgressRepository.getReadingDurations()` — 全量 `Map<bookId, totalDuration>`
- `ReadingProgressRepository.getTodayReadingTime()` — 今日总时长
- `StatsSection` — 设置页内的开关 + 每日目标滑块（极简）
- `UserPreferences.readingDailyTarget` — 每日目标分钟数

### 1.3 缺口

| 原型需要 | 现状 |
|---|---|
| 日/周/月/年粒度切换 | ❌ 无 |
| 热力图（GitHub 风格日历） | ❌ 无，缺少每日时长聚合查询 |
| 24 小时热力条 | ❌ 无，缺少小时粒度数据 |
| 维度分布环形图（作者/分组/格式/字数） | ❌ 无 |
| Top N 榜单（时长/书签/笔记/速度） | ❌ 无，缺少书签数/笔记数聚合查询 |
| 阅读状态分布 | 部分可用（`readingStatus` 字段存在） |
| 阅读时间轴 | ❌ 无 |
| 独立统计页面 | ❌ 无，仅有设置页内嵌 Section |

---

## 2. 数据层设计

### 2.0 单一数据源架构（破坏性重构）

#### 现状：4 份冗余数据

| 表 | 时长字段 | 粒度 | 累加方式 |
|---|---|---|---|
| `reading_progress` | `readTime`（秒） | 每本书 | `persistReadingTime()` 独立累加 |
| `chapter_reading_stats` | `read_time_seconds`（秒） | 每本书×每章 | `flushChapterTime()` 独立累加 |
| `reading_history` | `reading_duration_minutes`（分钟） | 每次读完事件 | `ReadingProgressRepository` 独立写入 |
| ~~`daily_reading_stats`~~ | ~~`total_seconds`（秒）~~ | ~~每天~~ | ~~原方案：再独立累加一次~~ |

4 个表各自累加阅读时长，写入路径不同（`persistReadingTime` vs `flushChapterTime` vs `recordReadingHistory`），任一失败都会导致数据分叉，且无法仲裁谁是对的。

**双重计时问题**：当前 `BookSessionManager.releaseResources()` 中，`flushChapterTime()` 和 `persistReadingTime()` 记录的是**同一段物理时间**，只是粒度不同（章节级 vs 书籍级）。`ReadingStateManager.endSession()` 返回的 `sessionElapsedMs` 包含了整个会话的累计时间（扣除暂停），而 `flushChapterTime()` 已经把最后一章的时间写入了 `chapter_reading_stats`。两条路径各自累加，容易分叉。

`reading_session` 方案通过只保留一条写入路径（`flushChapterTime` → INSERT），自然消除这个问题。**必须同步清理**：
- `releaseResources()` 中删除 `persistReadingTime()` 调用（`readTime` 字段已移除）
- `ReadingStateManager.endSession()` 的返回值 `sessionElapsedMs` 不再被消费，如果无其他模块使用，可简化 `ReadingStateManager` 为仅维护 `isSessionActive` / `isSessionPaused` 状态标记

改造后 `releaseResources()` 完整代码：

```kotlin
fun releaseResources() {
    saveReadingProgress(immediate = true)
    flushChapterTime()                    // ✅ 保留：写入 reading_session
    readingStateManager().endSession()    // 调用但不使用返回值
    readingStateManager().cancel()
    chapterJob?.cancel()
    // ❌ 已删除: persistReadingTime(uiState.value.bookId, sessionElapsed)
}
```

**现有 bug**：`BookshelfViewModel.kt:230` 调用 `todayTime.toReadableDuration()`，其中 `todayTime` 来自 `SUM(readTime)`（单位：秒），但 `toReadableDuration()` 扩展函数按**分钟**处理（`this / 60`）。3600 秒会显示为 "60h" 而非 "1h"。本次重构中 `reading_progress.readTime` 字段被删除，此 bug 随消费者迁移到 `ReadingSessionDao.getTodayTotal()` 时一并修复（`getTodayTotal()` 返回秒，`StatsFormatter` 按秒处理）。

#### 方案：`reading_session` 单一数据源

新增 `reading_session` 表，每次阅读会话（从开读到切换/暂停/退出的一段连续时间）存一行。**所有时长数据均从此表派生**：

| 需求 | 派生 SQL |
|---|---|
| 热力图（每日时长） | `GROUP BY date_key` |
| 24 小时热力条 | `GROUP BY hour` |
| 章节列表（每章时长） | `GROUP BY book_id, chapter_index` |
| 每本书总时长 | `GROUP BY book_id` |
| 今日总时长 | `WHERE date_key = today` |
| 阅读时间轴 | `ORDER BY started_at DESC` |
| 连续阅读天数 | `DISTINCT date_key` 应用层计算 |
| 读完总时长 | `GROUP BY book_id WHERE book_id = ?` |

#### 全项目数据统一：所有消费者必须读 `reading_session`

如果只让统计页读 `reading_session`，而书架/章节列表/书籍详情仍读旧表，就不是真正的单一数据源。以下是当前所有读取时长的消费者及迁移方案：

| 消费者 | 文件 | 当前数据源 | 迁移到 |
|---|---|---|---|
| 书架每本书时长 | `BookshelfViewModel.kt:149` | `reading_progress.readTime` | `ReadingSessionDao.getBookTotals()` |
| 书架今日总时长 | `BookshelfViewModel.kt:150` | `reading_progress.readTime` | `ReadingSessionDao.getTodayTotal()` |
| 章节列表每章时长 | `ChapterList.kt:107` | `chapter_reading_stats.read_time_seconds` | `ReadingSessionDao.getChapterTotals()` |
| 书籍详情总时长 | `ReadingProgressRepository.kt:73` | `reading_progress.readTime` | `ReadingSessionDao.getBookTotals()` 单次查询 |
| 写入路径读取累计值 | `BookSessionManager.kt:339` | `reading_progress.readTime` | 不再需要读取旧值，直接 INSERT 新行 |

**旧字段处理（项目未发布，直接删除）**：

- `reading_progress.readTime` — **删除字段**。`ReadingProgressEntity` 移除 `readTime` 属性，`ReadingProgressDao.updateProgress()` 移除 `readTime` 参数。`persistReadingTime()` 不再累加 `readTime`，仅更新 `pageIndex` / `position` / `updatedTime` / `chapterIndex` / `themeBackgroundColor`。同步协议改为同步 `reading_session` 表（已有 `is_dirty` / `version` / `synced_version` 字段）
- `chapter_reading_stats.read_time_seconds` — **删除字段**。`ChapterReadingStatsEntity` 移除 `readTimeSeconds` 属性。`ChapterReadingStatsDao` 移除 `addReadTime()` / `addReadTimeOrCreate()` 方法。`flushChapterTime()` 不再调用这些方法。保留 `visited` / `firstVisitedAt` / `lastVisitedAt`（章节已读状态）
- `reading_history.reading_duration_minutes` — **删除字段**。`ReadingHistoryEntity` 移除 `readingDurationMinutes` 属性。读完事件仍插入 `reading_history`（记录 `finished_at` 和 `read_count`），时长从 `ReadingSessionDao` 按需查询

### 2.1 新增实体：`ReadingSessionEntity`

```kotlin
@Entity(
    tableName = "reading_session",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["date_key"]),
        Index(value = ["book_id"]),
        Index(value = ["book_id", "chapter_index"]),
        Index(value = ["started_at"]),
    ],
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "book_id")
    val bookId: Long,

    @ColumnInfo(name = "chapter_index")
    val chapterIndex: Int,

    /** 会话开始时间戳（毫秒） */
    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    /** 会话结束时间戳（毫秒） */
    @ColumnInfo(name = "ended_at")
    val endedAt: Long,

    /** 会话时长（秒），已扣除暂停时段（见 §7 暂停扣除机制） */
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Long,

    /** 日期键：yyyyMMdd，用于热力图 GROUP BY，避免运行时日期计算 */
    @ColumnInfo(name = "date_key")
    val dateKey: Int,

    /** 小时（0-23），用于 24h 热力条 GROUP BY */
    @ColumnInfo(name = "hour")
    val hour: Int,

    // === 同步字段（与 ReadingProgressEntity 保持一致的 T-06 协议） ===
    @ColumnInfo(name = "is_dirty")
    val isDirty: Boolean = true,

    @ColumnInfo(name = "version")
    val version: Int = 1,

    @ColumnInfo(name = "synced_version")
    val syncedVersion: Int = 0,

    @ColumnInfo(name = "deleted")
    val deleted: Boolean = false,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0L,

    @ColumnInfo(name = "merge_source")
    val mergeSource: String? = null,
)
```

**设计决策**：

- `date_key` 和 `hour` 冗余存储，避免热力图查询时对每行做 `strftime` 日期/小时解析
- `duration_seconds` 冗余存储 `endedAt - startedAt`，避免 `SUM(ended_at - started_at)` 计算
- 同步字段（`is_dirty` / `version` / `synced_version`）与 `reading_progress` 保持一致的同步协议
- 不存 `total_words`（估算值），在 `StatsRepository` 中按需计算 `durationSeconds × avgReadingSpeed`

### 2.2 新增 DAO：`ReadingSessionDao`

```kotlin
@Dao
interface ReadingSessionDao {

    // ── 写入 ──

    @Insert
    suspend fun insert(session: ReadingSessionEntity): Long

    // ── 热力图 + 汇总（一次查询，应用层聚合） ──

    @Query("SELECT date_key, SUM(duration_seconds) as total FROM reading_session WHERE date_key BETWEEN :start AND :end GROUP BY date_key ORDER BY date_key ASC")
    fun getDailyTotals(start: Int, end: Int): Flow<List<DailyTotalTuple>>

    // ── 24h 热力条 ──

    @Query("SELECT hour, SUM(duration_seconds) as total FROM reading_session WHERE date_key = :dateKey GROUP BY hour")
    suspend fun getHourlyTotals(dateKey: Int): List<HourlyTotalTuple>

    // ── 连续活跃天数 ──

    @Query("SELECT DISTINCT date_key FROM reading_session WHERE date_key BETWEEN :start AND :end ORDER BY date_key ASC")
    suspend fun getActiveDateKeys(start: Int, end: Int): List<Int>

    // ── 今日总时长（书架顶栏用） ──

    @Query("SELECT SUM(duration_seconds) FROM reading_session WHERE date_key = :todayKey")
    fun getTodayTotal(todayKey: Int): Flow<Long?>

    // ── 每本书总时长（替代 ReadingProgressDao.getAllReadingDurations） ──

    @Query("SELECT book_id as bookId, SUM(duration_seconds) as totalDuration FROM reading_session GROUP BY book_id")
    fun getBookTotals(): Flow<List<BookDurationTuple>>

    // ── 每章时长（替代 ChapterReadingStatsDao.getStatsByBookId 的时长部分） ──

    @Query("SELECT chapter_index as chapterIndex, SUM(duration_seconds) as totalSeconds, MIN(started_at) as firstVisitedAt, MAX(ended_at) as lastVisitedAt FROM reading_session WHERE book_id = :bookId GROUP BY chapter_index ORDER BY chapter_index ASC")
    fun getChapterTotals(bookId: Long): Flow<List<ChapterTotalTuple>>

    // ── 阅读时间轴 ──

    @Query("SELECT * FROM reading_session WHERE date_key BETWEEN :start AND :end ORDER BY started_at DESC")
    fun getSessionsInRange(start: Int, end: Int): Flow<List<ReadingSessionEntity>>
}

data class DailyTotalTuple(val dateKey: Int, val total: Long)
data class HourlyTotalTuple(val hour: Int, val total: Long)
data class ChapterTotalTuple(val chapterIndex: Int, val totalSeconds: Long, val firstVisitedAt: Long, val lastVisitedAt: Long)
```

**精简决策**：所有聚合通过 SQL `GROUP BY` 在数据库层完成，应用层只做遍历和 streak 计算。无冗余的 `countActiveDays()` / `sumSeconds()` 独立查询。

### 2.3 扩展已有 DAO 查询

**ReadingProgressDao**：`getReadingDurationByBookId()` / `getAllReadingDurations()` / `getTodayTotalReadingTime()` **直接删除**（项目未发布）。调用方迁移到 `ReadingSessionDao`。`updateProgress()` 移除 `readTime` 参数。

**BookDao**：新增 `getAllBooksWithDuration()` 查询，LEFT JOIN `reading_session`（而非现有的 `reading_progress`，后者无此查询）：

```kotlin
/** 新增：所有书的阅读时长 + 元数据（统计页 Top N / 分布用） */
@Query("""
    SELECT b.id, b.title, b.author, b.fileType, b.readingStatus,
           b.totalChapterNum, b.estimatedTotalChars,
           COALESCE(rs.totalDuration, 0) as totalDuration
    FROM books b
    LEFT JOIN (
        SELECT book_id, SUM(duration_seconds) as totalDuration
        FROM reading_session GROUP BY book_id
    ) rs ON b.id = rs.book_id
""")
fun getAllBooksWithDuration(): Flow<List<BookWithDurationTuple>>
```

`getBookmarkCounts()` / `getNoteCounts()` 保持不变（与时长无关）。

### 2.4 数据写入时机

写入路径简化为**单一操作**：`flushChapterTime()` 将累计时长作为一条 `ReadingSessionEntity` 插入。

```
BookSessionManager.flushChapterTime() (已有，章节切换/退出/暂停时调用):
  1. 计算 elapsedSeconds = (now - chapterStartTimestamp) / 1000
  2. ReadingSessionDao.insert()                       // 唯一时长写入（权威源）
  3. chapterStartTimestamp = now                       // 重置计时起点
```

**写入时机**（复用已有调用点 + 新增暂停 flush）：

| 调用点 | 触发场景 | 新增/已有 |
|---|---|---|
| `flushChapterTime()` ← `openChapter()` | 章节切换 | 已有，追加 insert |
| `flushChapterTime()` ← `releaseResources()` | 退出阅读器 | 已有，追加 insert |
| `flushChapterTime()` ← `pauseReadingSession()` | App 切后台 | **新增**（§14.2 修复） |

**写入频率**：自然与章节切换/暂停频率一致（1-5 分钟/次）。每次写入 = 1 行 INSERT，O(1)。

---

## 3. 页面架构

### 3.1 导航入口

当前 `MainActivity` 使用 `ActiveScreen` sealed class 管理三个页面：

```kotlin
sealed class ActiveScreen {
    data object Bookshelf : ActiveScreen()
    data object Settings : ActiveScreen()
    data class Reader(val bookId: Long) : ActiveScreen()
}
```

新增统计页面：

```kotlin
sealed class ActiveScreen {
    data object Bookshelf : ActiveScreen()
    data object Stats : ActiveScreen()        // ← 新增
    data object Settings : ActiveScreen()
    data class Reader(val bookId: Long) : ActiveScreen()
}
```

**入口位置**：书架顶栏新增统计图标按钮（`Icons.Outlined.BarChart`），点击切换到 `ActiveScreen.Stats`。

### 3.2 文件组织

```
feature/stats/
├── StatsScreen.kt              // 顶层 Composable（Scaffold + 滚动容器）
├── StatsViewModel.kt           // 状态管理 + 数据查询
├── StatsUiState.kt             // UiState data class
├── StatsGranularity.kt         // 枚举：Day / Week / Month / Year
├── StatsDateNavigator.kt       // 日期导航逻辑（前后翻页）
├── component/
│   ├── GranularitySelector.kt  // 粒度切换 Pill
│   ├── DateNavigator.kt        // 日期导航栏
│   ├── HeroSection.kt          // 核心指标区（大数字 + 副指标 + 目标环）
│   ├── HourlyHeatmap.kt        // 24 小时热力条（日视图）
│   ├── WeeklyBarChart.kt       // 周柱状图（周视图）
│   ├── CalendarHeatmap.kt      // 日历热力图（月/年视图）
│   ├── DistributionChart.kt    // 维度分布（环形图 + Legend + SegControl）
│   ├── TopNList.kt             // Top N 榜单
│   ├── ReadingStatusChart.kt   // 阅读状态分布
│   ├── ReadingTimeline.kt      // 阅读时间轴
│   └── EmptyStatsState.kt      // 空数据友好提示
└── StatsRepository.kt          // 统计专用 Repository（聚合查询）

> **注意**：`StatsFormatter` 放在 `core/util/StatsFormatter.kt`，而非 `feature/stats/`。因为 `BookshelfViewModel` 等非统计模块也需调用时长格式化，放 `feature/` 会违反包边界。
```

### 3.3 UiState 设计

```kotlin
/** 日期键范围（yyyyMMdd 整数，非连续，不可用 ClosedRange<Int>） */
data class DateKeyRange(val start: Int, val end: Int) {
    operator fun contains(dateKey: Int) = dateKey in start..end
}

data class StatsUiState(
    val navigation: StatsNavigationState = StatsNavigationState(),
    val hero: StatsHeroState = StatsHeroState(),
    val heatmap: StatsHeatmapState = StatsHeatmapState(),
    val distribution: StatsDistributionState = StatsDistributionState(),
    val topN: StatsTopNState = StatsTopNState(),
    val status: StatsStatusState = StatsStatusState(),
    val hasAnyData: Boolean = false,
)

data class StatsNavigationState(
    val granularity: StatsGranularity = StatsGranularity.YEAR,
    val currentDate: LocalDate = LocalDate.now(),
    val canGoNext: Boolean = false,
)

data class StatsHeroState(
    val totalMinutes: Long = 0,
    val totalWords: Long = 0,
    val totalChapters: Int = 0,
    val avgWordsPerMinute: Int = 0,
    val speedTrend: SpeedTrend = SpeedTrend.FLAT,
    val activeDays: Int = 0,
    val totalDaysInPeriod: Int = 0,
    val previousPeriodMinutes: Long = 0,
    val deltaPercent: Float = 0f,
    val deltaIsUp: Boolean = true,
    val currentStreak: Int = 0,
    val goalMinutes: Long = 0,
    val goalPercent: Int = 0,
    val dailyNeededMinutes: Long = 0,
)

data class StatsHeatmapState(
    val heatmapData: List<DailyHeatCell> = emptyList(),
    val hourlyMinutes: List<Int> = List(24) { 0 },
    val weekData: WeekChartData? = null,
)

data class StatsDistributionState(
    val dimension: DistributionDim = DistributionDim.AUTHOR,
    val items: List<DistributionItem> = emptyList(),
)

data class StatsTopNState(
    val sort: TopNSort = TopNSort.DURATION,
    val books: List<TopNBookItem> = emptyList(),
)

data class StatsStatusState(
    val items: List<StatusItem> = emptyList(),
)

enum class SpeedTrend { UP, DOWN, FLAT }
```

**拆分理由**：25+ 字段的扁平 data class 违反 ISP（接口隔离原则）。拆分为子状态后，每个 Compose 组件只观察自己依赖的子状态，减少不必要的重组。例如切换 Top N 排序时只有 `StatsTopNState` 变化，热力图组件不会重组。

**DateKeyRange**：`yyyyMMdd` 格式的整数不是连续的（20260131 到 20260201 之间有 70 个整数差距），使用 `ClosedRange<Int>` 做 `contains()` 或遍历会出错。自定义 `DateKeyRange` 仅用于 SQL `BETWEEN` 参数传递，不提供遍历语义。

---

## 4. UI 组件设计

### 4.1 页面布局

```
StatsScreen
├── TopBar（标题 + 返回）
├── GranularitySelector（日/周/月/年 Pill）
├── DateNavigator（日期 + 前后箭头）
└── LazyColumn
    ├── HeroSection
    ├── [按粒度切换]
    │   ├── Day:   HourlyHeatmap + ReadingTimeline
    │   ├── Week:  WeeklyBarChart + ReadingTimeline
    │   ├── Month: CalendarHeatmap
    │   └── Year:  CalendarHeatmap + DistributionChart + TopNList + ReadingStatusChart
    └── Footer
```

### 4.2 组件与原型对照

| 原型区域 | Compose 组件 | 关键技术点 |
|---|---|---|
| 粒度切换 Pill + 滑动指示器 | `GranularitySelector` | `animateDpAsState` 驱动指示器位移，`SubcomposeLayout` 等宽分配 |
| 日期导航 | `DateNavigator` | `Row` + 箭头 `IconButton`，根据粒度格式化文本 |
| Hero 大数字 | `HeroSection` | `AnimatedContent` 数字滚动，`Canvas` 绘制环形进度 |
| 热力图 | `CalendarHeatmap` | `Canvas` 绘制格子，`pointerInput` 触摸反馈；**月视图 cellSize=18-20dp**（见 §6.3） |
| 24 小时热力条 | `HourlyHeatmap` | 3 行 × 8 列 `Grid`，色阶映射；**左侧行标签加小时轴（0/8/16），峰值格标注具体小时** |
| 周柱状图 | `WeeklyBarChart` | `Canvas` 绘制双柱（本周/上周），`LaunchedEffect` 首次可见触发一次动画 |
| 维度分布 | `DistributionChart` | `Canvas` 绘制环形图（`drawArc`），SegControl 切换维度；TXT/EPUB 用书架语义色 |
| Top N 榜单 | `TopNList` | `LazyColumn` + SegControl 切换排序；**行可点击跳转对应书籍**（回调 `onBookClick(bookId)`） |
| 阅读状态 | `ReadingStatusChart` | 水平进度条 + 圆点颜色 |
| 时间轴 | `ReadingTimeline` | `LazyColumn` + 竖线装饰；**按 `book_id` 分组后，同书相邻会话间隔 < 30 分钟合并为单次会话**（跨书不合并）。合并仅用于 UI 展示，不修改数据库。合并后：`durationSeconds` 取 SUM，章节显示为范围（如"第3-5章"），`startedAt` 取首条、`endedAt` 取末条。30 分钟内切书又切回不合并（`book_id` 不同） |

### 4.3 色阶系统

#### sqrt 变换（非线性映射）

原型的线性等比分割在存在极端高值时会让大多数天聚集在 L1，整图趋近单色。改用 `sqrt` 变换压缩高值区间、放大低值区分度：

```kotlin
import kotlin.math.sqrt

fun heatLevel(minutes: Long, maxMinutes: Long): HeatLevel {
    if (minutes == 0L) return HeatLevel.L0
    if (maxMinutes <= 0L) return HeatLevel.L1  // 守卫：避免除零产生 NaN
    val ratio = sqrt(minutes.toFloat() / maxMinutes.toFloat())
    return when {
        ratio < 0.17f -> HeatLevel.L1
        ratio < 0.33f -> HeatLevel.L2
        ratio < 0.50f -> HeatLevel.L3
        ratio < 0.67f -> HeatLevel.L4
        else -> HeatLevel.L5
    }
}
```

#### Nord Aurora 色系

原型的墨土棕色系（`#E8DCC8 → #8B5E3C`）与项目 Nord 主题不协调。热力图改用 Nord Frost 蓝轴，与 Polar Night 背景自然融合：

| 级别 | 亮色模式 | 暗色模式 | 语义 |
|---|---|---|---|
| L0 | `#ECEFF4`（Snow Storm） | `#3B4252`（Polar Night 中段） | 未阅读 |
| L1 | `#D8DEE9` | `#434C5E` | 微量 |
| L2 | `#88C0D0`（Frost 浅） | `#5E81AC`（Frost Deep） | 轻度 |
| L3 | `#81A1C1`（Frost） | `#81A1C1`（Frost） | 中度 |
| L4 | `#5E81AC`（Frost Deep） | `#88C0D0`（Frost 浅） | 重度 |
| L5 | `#4C7FBF` | `#8FBCBB`（Frost 最浅） | 极高 |

暗色模式下色阶反转亮度方向（深底→亮蓝），呈现从背景渐变到亮蓝的冰霜感。

**按格式维度语义色**：分布环形图中 TXT 用暖沙色（`#D08770`，Nord Aurora Orange）、EPUB 用冷蓝灰（`#5E81AC`，Nord Frost Deep），与书架封面色保持一致，形成跨页面视觉联系。

---

## 5. 核心模块设计

### 5.1 StatsRepository

```kotlin
class StatsRepository(
    private val readingSessionDao: ReadingSessionDao,
    private val bookDao: BookDao,
    private val bookmarkDao: BookmarkDao,
    private val noteDao: NoteDao,
    private val userPreferences: UserPreferences,
) {
    // ── 聚合查询（按粒度映射日期范围）──

    fun getHeatmapData(start: Int, end: Int): Flow<List<DailyHeatCell>>
    fun getHeroMetrics(start: Int, end: Int): Flow<HeroMetrics>
    fun getWeeklyChartData(weekStart: Int, weekEnd: Int, prevWeekStart: Int, prevWeekEnd: Int): Flow<WeekChartData>
    fun getHourlyData(dateKey: Int): Flow<List<Int>>

    // ── 维度分布 ──
    fun getDistribution(dim: DistributionDim): Flow<List<DistributionItem>>

    // ── Top N ──
    fun getTopN(sort: TopNSort, limit: Int = 5): Flow<List<TopNBookItem>>

    // ── 阅读状态 ──
    fun getReadingStatusDistribution(): Flow<List<StatusItem>>

    // ── 连续活跃天数 ──
    /** 历史最长连续天数（热力图 summary 展示） */
    suspend fun getLongestStreak(start: Int, end: Int): Int
    /** 当前连续阅读天数（从今天/本期末日反向遍历连续有记录的天数），日/周视图 Hero 区醒目展示 */
    suspend fun getCurrentStreak(asOfDateKey: Int): Int

    // ── 目标派生（纯算术，无需 suspend）──
    /** 每天还需 X 分钟：(goalMinutes - totalMinutes) / remainingDays，剩余天数 <= 0 时返回 0 */
    fun getDailyNeededMinutes(goalMinutes: Long, totalMinutes: Long, remainingDays: Int): Long

    // ── 阅读速度趋势（周视图用）──
    /** 本周均速 vs 上周均速对比，返回 ↑/↓/→ */
    suspend fun getSpeedTrend(thisWeekWpm: Int, lastWeekWpm: Int): SpeedTrend
}
```

### 5.2 StatsGranularity

```kotlin
enum class StatsGranularity {
    DAY,    // 单日
    WEEK,   // 周一~周日
    MONTH,  // 当月
    YEAR;   // 当年

    fun dateRange(date: LocalDate): DateKeyRange  // 返回 dateKey 范围
    fun previousRange(date: LocalDate): DateKeyRange
    fun dateText(date: LocalDate, locale: Locale): String
    fun canGoNext(date: LocalDate): Boolean
}
```

### 5.3 StatsDateNavigator

```kotlin
class StatsDateNavigator {
    fun next(granularity: StatsGranularity, current: LocalDate): LocalDate
    fun prev(granularity: StatsGranularity, current: LocalDate): LocalDate
}
```

### 5.4 StatsViewModel 数据流设计

粒度/日期变化时需重新订阅 Repository Flow，避免泄漏旧范围：

```kotlin
class StatsViewModel(
    private val statsRepository: StatsRepository,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val granularity = MutableStateFlow(StatsGranularity.YEAR)
    private val currentDate = MutableStateFlow(LocalDate.now())

    /** 粒度/日期变化 → 重新计算 DateKeyRange → 重新订阅热力图 Flow */
    private val dateKeyRange = combine(granularity, currentDate) { gran, date ->
        gran.dateRange(date)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StatsGranularity.YEAR.dateRange(LocalDate.now()))

    val uiState: StateFlow<StatsUiState> = combine(
        dateKeyRange.flatMapLatest { range ->
            statsRepository.getDailyTotals(range.start, range.end)
        },
        granularity,
        currentDate,
        userPreferences.readingDailyTarget,
        // ... 其他 Flow
    ) { values ->
        // 组装 StatsUiState
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState())
}
```

**关键**：`dateKeyRange.flatMapLatest {}` 确保粒度/日期变化时自动取消旧范围的 Flow 订阅，不会泄漏。

---

## 6. 热力图实现

### 6.1 年视图（~53 列 × 7 行）+ 横向滚动

53 列 × (11+3)dp ≈ 742dp，超出 360-420dp 屏幕宽度。Canvas 本身不支持滚动，需包裹在 `horizontalScroll` 容器中：

```kotlin
@Composable
fun YearCalendarHeatmap(
    cells: List<DailyHeatCell>,
    modifier: Modifier = Modifier,
    cellSize: Dp = 11.dp,
    cellGap: Dp = 3.dp,
) {
    val scrollState = rememberScrollState()
    val step = cellSize + cellGap
    val canvasWidth = 53 * step  // 53 周列

    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
    ) {
        Canvas(
            modifier = Modifier
                .width(canvasWidth)
                .height(7 * step + 16.dp)  // 7 行 + 周标签空间
        ) {
            drawHeatmapCells(cells, cellSize.toPx(), cellGap.toPx())
        }
    }

    // 初始化时自动滚动到当前周附近（让用户看到"最近的活动"而非 1 月初）
    LaunchedEffect(Unit) {
        val currentWeekCol = LocalDate.now().weekOfWeekBasedYear()
        scrollState.scrollTo((currentWeekCol * step.toPx().toInt()).coerceAtMost(scrollState.maxValue))
    }
}
```

**性能**：全年 365 个格子用 Canvas 一次绘制，无需 LazyGrid，无 Composable 树开销。

### 6.2 today 格高亮实现

原型用 `scale(1.1) + box-shadow` 实现 today 格高亮。Canvas 中改为：先绘制 accent 色描边矩形（略大），再在其上绘制正常格子，模拟光晕效果：

```kotlin
// 在 drawHeatmapCells 内部
if (cell.isToday) {
    // 1. 绘制 accent 描边光晕（比格子大 2px）
    drawRoundRect(
        color = accentColor.copy(alpha = 0.6f),
        topLeft = Offset(x - 2f, y - 2f),
        size = Size(size + 4f, size + 4f),
        cornerRadius = CornerRadius(3f),
        style = Stroke(width = 2f),
    )
}
// 2. 正常绘制格子
drawRoundRect(
    color = cell.heatLevel.color(isDark),
    topLeft = Offset(x, y),
    size = Size(size, size),
    cornerRadius = CornerRadius(2f),
)
```

### 6.3 月视图（7 列 × 5 行，大格子）

月视图只有 28-31 格，若沿用年视图的 11dp 格子，内容区高度仅 ~80dp，屏幕利用率极低。月视图改用 **18-20dp** 格子，7 列（周一~周日）排列，充分填充水平空间，同时每个格子有足够面积承载 tap 交互：

```kotlin
@Composable
fun MonthCalendarHeatmap(
    cells: List<DailyHeatCell>,
    modifier: Modifier = Modifier,
    cellSize: Dp = 20.dp,   // 月视图大格子
    cellGap: Dp = 4.dp,
) {
    // 7 列固定，无需横向滚动
    Canvas(modifier = modifier.fillMaxWidth()) {
        val size = cellSize.toPx()
        val gap = cellGap.toPx()
        val step = size + gap

        cells.forEachIndexed { index, cell ->
            val dayOfMonth = cell.date.dayOfMonth
            val firstDayWeekday = (cell.date.withDayOfMonth(1).dayOfWeek.value % 7) // 0=Mon
            val col = (firstDayWeekday + dayOfMonth - 1) % 7
            val row = (firstDayWeekday + dayOfMonth - 1) / 7

            drawHeatCell(cell, col * step, row * step, size)
        }
    }
}
```

### 6.4 触摸交互

使用 `pointerInput` + `detectTapGestures`，根据触摸坐标反算格子，弹出 Tooltip：

```kotlin
.pointerInput(cells) {
    detectTapGestures { offset ->
        val col = (offset.x / step).toInt()
        val row = (offset.y / step).toInt()
        val cell = resolveCell(col, row, cells)
        if (cell != null) onCellTap(cell, offset)
    }
}
```

**月视图汇总行**：月视图热力图下方补充与年视图相同的 4 格汇总（时长/活跃天/连读/日均），数据范围缩至当月。

---

## 7. 数据写入链路

```
BookSessionManager
  ├─ flushChapterTime()  ← 章节切换 / 释放资源 / 切后台时调用
  │   ├─ elapsedSeconds = (now - chapterStartTimestamp) / 1000
  │   ├─ ReadingSessionDao.insert(                       // 唯一时长写入
  │   │       ReadingSessionEntity(
  │   │         bookId, chapterIndex,
  │   │         startedAt = chapterStartTimestamp,
  │   │         endedAt = now,
  │   │         durationSeconds = elapsedSeconds,
  │   │         dateKey = todayDateKey(),
  │   │         hour = Calendar.HOUR_OF_DAY,
  │   │       ))
  │   └─ chapterStartTimestamp = now                     // 重置计时起点
  │
  └─ persistReadingPosition()  ← 切换书籍 / 释放资源时调用（原 persistReadingTime 重命名）
      └─ ReadingProgressDao.updateProgress(pageIndex, position, updatedTime, ...)  // 不再写 readTime
```

### 7.1 暂停扣除机制

`durationSeconds` 必须扣除暂停时段（App 切后台、锁屏）。实现方式：**暂停时 flush 当前片段，恢复时重置计时起点**。

```
用户行为时间线：
  10:00  开始读第3章        → chapterStartTimestamp = 10:00
  10:10  App切后台(ON_PAUSE) → flushChapterTime(): INSERT session(10:00→10:10, 600s)
                               chapterStartTimestamp = 10:10
         pauseReadingSession()
  10:15  App回前台(ON_RESUME) → resumeReadingSession()
                                chapterStartTimestamp = 10:15  ← 重置，跳过暂停时段
  10:25  切换到第4章         → flushChapterTime(): INSERT session(10:15→10:25, 600s)

总计: 20 分钟阅读 (600s + 600s)，5 分钟暂停被自然排除
```

**关键调用**：

`flushChapterTime()` 可见性从 `private` 改为 `internal`，允许同模块的 `ReaderViewModel` 调用。`flushChapterTime()` 末尾已有 `chapterStartTimestamp = System.currentTimeMillis()` 重置逻辑，无需额外包装方法。

```kotlin
// BookSessionManager — flushChapterTime() 可见性改为 internal
internal fun flushChapterTime() {
    val dao = chapterReadingStatsDao ?: return
    // ... 已有逻辑 ...
    chapterStartTimestamp = System.currentTimeMillis()  // 末尾重置
}

// BookSessionManager — 新增 internal 方法供 resume 使用
internal fun resetChapterStartTimestamp() {
    chapterStartTimestamp = System.currentTimeMillis()
}

// ReaderViewModel
fun pauseReadingSession() {
    bookSessionManager.flushChapterTime()    // internal，可调用；末尾自动重置计时起点
    readingStateManager.pauseSession()
}

fun resumeReadingSession() {
    readingStateManager.resumeSession()
    bookSessionManager.resetChapterStartTimestamp()  // 显式重置，跳过暂停时段
}
```

### 7.2 边界条件：快速切章（< 1 秒）

`flushChapterTime()` 中 `elapsedSeconds < 1L` 时直接 return 不写入（`BookSessionManager.kt:513`），此时 `chapterStartTimestamp` 也不会重置。这意味着快速连续切章（< 1 秒）不会产生垃圾数据，下次 flush 时会累加更长的时间。这是现有行为，`reading_session` 方案保持一致。

### 7.3 跨整点精度损失

`hour` 字段取 `startedAt` 的小时值。对于跨越整点的会话（如 10:50 → 11:20），整个 30 分钟被归入 `hour=10`。对于 24 小时热力条的统计目的，这种近似是可接受的（单会话误差 ≤ 59 分钟，日均误差趋近于零）。

**已删除的写入**：
- `ChapterReadingStatsDao.addReadTimeOrCreate()` — 方法已删除（§2.0）
- `ReadingProgressDao.updateProgress(readTime=...)` — `readTime` 参数已删除，`persistReadingTime()` 重命名为 `persistReadingPosition()`

**写入时机**（复用已有调用点 + 新增暂停/恢复）：

| 调用点 | 触发场景 | 新增/已有 |
|---|---|---|
| `flushChapterTime()` ← `openChapter()` | 章节切换 | 已有，改为 INSERT |
| `flushChapterTime()` ← `releaseResources()` | 退出阅读器 | 已有，改为 INSERT |
| `flushChapterTime()` ← `pauseReadingSession()` | App 切后台 | **新增**（§14.2 修复） |
| `resetChapterStartTimestamp()` ← `resumeReadingSession()` | App 回前台 | **新增**（暂停扣除） |

**性能约束**：每次写入 = 1 行 INSERT（`reading_session` 主键自增），O(1)。`date_key` 索引确保热力图查询走索引扫描。

---

## 8. 数据库迁移

项目未发布，使用 `fallbackToDestructiveMigration()`（`ShuLiAppContainer.kt:64` 已有），无需编写 Migration SQL。Database version 从 23 升至 24，Room 自动 DROP 所有表并重建。

```kotlin
@Database(
    entities = [
        // ... 已有 entities ...
        ReadingSessionEntity::class,    // ← 新增
    ],
    version = 24,
    exportSchema = true,
)
abstract class ShuLiDatabase : RoomDatabase() {
    // ... 已有 DAOs ...
    abstract fun readingSessionDao(): ReadingSessionDao  // ← 新增
}
```

**同步修改的 Entity**（删除时长字段）：

| Entity | 删除字段 | 影响 |
|---|---|---|
| `ReadingProgressEntity` | `readTime: Long` | `ReadingProgressDao.updateProgress()` 移除参数 |
| `ChapterReadingStatsEntity` | `readTimeSeconds: Long` | `ChapterReadingStatsDao` 移除 `addReadTime()` / `addReadTimeOrCreate()` |
| `ReadingHistoryEntity` | `readingDurationMinutes: Long` | `recordReadingHistory()` 不再传入时长 |

---

## 9. i18n

新增 `StatsStrings` 接口 + 三语实现，注册到 `AppStrings`：

```kotlin
interface StatsStrings {
    val statsTitle: String                    // "阅读统计"
    val granularityDay: String                // "日"
    val granularityWeek: String               // "周"
    val granularityMonth: String              // "月"
    val granularityYear: String               // "年"
    val today: String                         // "今天"
    val thisWeek: String                      // "本周"
    val thisMonth: String                     // "本月"
    val thisYear: String                      // "今年"
    val cumulativeLabel: String               // "累计"
    val vsPrevious: String                    // "vs 上期"
    val words: String                         // "字数"
    val chapters: String                      // "章节"
    val wordsPerMinute: String                // "字/分"
    val activeDays: String                    // "活跃天"
    /** 目标标题：(年份, 目标小时数) → "2024 年度目标 · 500 小时" */
    val goalTitle: (String, Int) -> String
    val goalProgress: (String) -> String      // "已读 {done} · 剩余 {remaining}"
    /** 派生行动指导："每天约需 {minutes} 分钟" */
    val dailyNeededHint: (Long) -> String
    val hourlyPeak: (Int, Int) -> String      // "阅读高峰 · {hour}:00 - {hour+1}:00"
    val heatmapTitle: (String) -> String      // "{year} 年 · 阅读热力图"
    val heatmapSummary: (Int, Long) -> String  // "{days} 天活跃 · 累计 {hours}h"
    val longestStreak: String                 // "最长连读"
    /** 当前连续阅读天数（日/周视图 Hero 区） */
    val currentStreak: (Int) -> String        // "已连续 {days} 天"
    val dailyAvg: String                      // "日均"
    val distributionTitle: String             // "阅读分布"
    val dimAuthor: String                     // "按作者"
    val dimGroup: String                      // "按分组"
    val dimFormat: String                     // "按格式"
    val dimWords: String                      // "按字数"
    val topNTitle: String                     // "榜单"
    val sortByDuration: String                // "时长"
    val sortByBookmarks: String               // "书签"
    val sortByNotes: String                   // "笔记"
    val sortBySpeed: String                   // "速度"
    val readingStatus: String                 // "阅读状态"
    val statusReading: String                 // "在读"
    val statusFinished: String                // "已读完"
    val statusPaused: String                  // "暂停"
    val statusWantToRead: String              // "未读"
    val less: String                          // "少"
    val more: String                          // "多"
    val notRead: String                       // "未阅读"
    /** 空状态提示（当前粒度无阅读记录时） */
    val emptyStateHint: String                // "暂无阅读记录，打开一本书开始阅读吧"
    /** 字数估算标记（≈ 前缀，提醒用户这是估算值） */
    val estimatedPrefix: String               // "≈"
}
```

> **实现注意**：`goalTitle` 签名是 `(String, Int) -> String`（年份 + 小时数），非 `(String) -> String`。
> 字数类指标在 UI 上统一使用 `estimatedPrefix`（如 `≈1184万`），提醒用户这是 `seconds × avgSpeed` 估算值。

### 9.1 StatsFormatter 格式化规格

```kotlin
object StatsFormatter {
    /** 时长格式化（输入：秒） */
    fun formatDuration(seconds: Long, locale: Locale): String
    // 规则：
    //   < 60s      → "0m"
    //   < 3600s    → "Xm"            (例: 1500s → "25m")
    //   < 86400s   → "XhYm"          (例: 5400s → "1h30m"，Y=0 时省略 → "2h")
    //   >= 86400s  → "XdYh"          (例: 90000s → "1d1h")

    /** 字数格式化（输入：Long，估算值） */
    fun formatWords(words: Long, locale: Locale): String
    // 规则：
    //   zh-CN/zh-TW: < 10000 → "X字"  | >= 10000 → "≈X.X万"  (例: 11840000 → "≈1184万")
    //   en:          < 1000  → "X"     | >= 1000  → "≈X.XK"   (例: 1500 → "≈1.5K")

    /** 百分比格式化 */
    fun formatPercent(value: Float): String
    // 规则：整数不带小数 (75.0 → "75%")，非整数保留一位 (75.3 → "75.3%")

    /** 零值显示 */
    fun zeroOrNull(value: Long): String
    // 规则：0 → "--"，非 0 → 正常格式化
}
```

**修复现有 bug**：`BookshelfViewModel.kt:230` 的 `toReadableDuration()` 将秒误当分钟处理。迁移到 `StatsFormatter.formatDuration()` 后，输入统一为秒，输出正确。

---

## 10. 实施任务清单

### Phase 1：数据层 + 全项目消费者迁移（预计 6-8 天）

**1a. Entity 修改 + 新建**
- [ ] 新增 `ReadingSessionEntity` + `ReadingSessionDao`（8 个方法）
- [ ] 新增 `DailyTotalTuple` / `HourlyTotalTuple` / `ChapterTotalTuple` / `BookWithDurationTuple`
- [ ] `ReadingProgressEntity` 删除 `readTime` 字段
- [ ] `ReadingProgressDao.updateProgress()` 移除 `readTime` 参数
- [ ] `ReadingProgressDao` 移除 `getReadingDurationByBookId()` / `getAllReadingDurations()` / `getTodayTotalReadingTime()`
- [ ] `ChapterReadingStatsEntity` 删除 `readTimeSeconds` 字段
- [ ] `ChapterReadingStatsDao` 移除 `addReadTime()` / `addReadTimeOrCreate()`，更新 `ensureExists()` 中的构造函数调用（移除 `readTimeSeconds` 默认值引用）
- [ ] `ReadingHistoryEntity` 删除 `readingDurationMinutes` 字段
- [ ] `ShuLiDatabase` version 23→24，新增 `readingSessionDao()`
- [ ] `BookDao` 新增 `getAllBooksWithDuration()` / `getBookmarkCounts()` / `getNoteCounts()`
- [ ] 单元测试：DAO 聚合查询正确性、INSERT 并发安全

**1b. 写入路径改造**
- [ ] `BookSessionManager.flushChapterTime()` 移除 `ChapterReadingStatsDao.addReadTimeOrCreate()`，改为 `ReadingSessionDao.insert()`
- [ ] `BookSessionManager.persistReadingTime()` 重命名为 `persistReadingPosition()`，移除 `readTime` 累加逻辑
- [ ] `BookSessionManager` 构造函数新增 `readingSessionDao` 参数
- [ ] `flushChapterTime()` 可见性从 `private` 改为 `internal`（§7.1）
- [ ] `BookSessionManager` 新增 `resetChapterStartTimestamp()` 方法（`internal` 可见性）
- [ ] `ReaderViewModel.pauseReadingSession()` 追加 `flushChapterTime()` 调用（§14.2 修复）
- [ ] `ReaderViewModel.resumeReadingSession()` 追加 `resetChapterStartTimestamp()` 调用（§7.1 暂停扣除）
- [ ] **ReadingStateManager 审核**：确认 `sessionElapsedMs` 与 `reading_session` 的 `SUM(duration_seconds)` 语义一致（均扣除暂停）。如果 `ReadingStateManager` 的会话级计时不再被其他模块消费，可简化为仅维护 `isSessionActive` / `isSessionPaused` 状态标记
- [ ] 回归测试：开书 → 阅读 → 切章 → 切后台 → 回前台 → 切章 → 退出，验证 `reading_session` 行的 `startedAt` / `endedAt` / `durationSeconds` 正确

**1c. 消费者迁移（全部读 `reading_session`）**
- [ ] `BookshelfViewModel` 注入 `ReadingSessionDao`，`getReadingDurations()` → `getBookTotals()`
  - **架构决策**：选择直接注入 DAO 而非通过 `ReadingProgressRepository` 委托。理由：`BookshelfViewModel` 只读不写，且 `ReadingSessionDao.getBookTotals()` 返回的 `Flow<List<BookDurationTuple>>` 类型与当前消费代码兼容。在项目当前规模下，直接注入 DAO 减少一层间接调用的开销，可接受。如果未来 `BookshelfViewModel` 需要对时长数据做写操作，应改为通过 Repository
- [ ] `BookshelfViewModel` `getTodayReadingTime()` → `getTodayTotal(todayDateKey())`
- [ ] `BookItem.kt:158` 的 `readingDurationMinutes.toReadableDuration()` 迁移到 `StatsFormatter.formatDuration()`（同一 toReadableDuration 单位 bug，§2.0）
- [ ] `ChapterList.kt` 消费 `ReadingSessionDao.getChapterTotals()` 替代 `ChapterReadingStatsDao.getStatsByBookId()` 的时长部分
- [ ] `ReadingProgressRepository.getReadingDuration()` → 委托 `ReadingSessionDao.getBookTotals()` 单次查询
- [ ] `BackupExporter` / `BackupImporter` 新增 `reading_session` 表的序列化/反序列化（确保备份恢复后统计数据不丢失）

### Phase 2：统计 Repository + ViewModel（预计 2 天）

- [ ] 新增 `StatsRepository`（聚合查询）
- [ ] 新增 `StatsUiState` / `StatsGranularity` / `StatsDateNavigator`
- [ ] 新增 `StatsViewModel`（状态管理、粒度切换、日期导航）
- [ ] 单元测试：粒度日期范围、环比计算、热力图数据映射

### Phase 3：UI 组件（预计 10-12 天）

- [ ] `StatsScreen` 骨架 + `LazyColumn` 布局
- [ ] `StatsFormatter` 工具类（统一时长 `XhYm`/`Xh`、字数 `≈X.X万`、百分比格式化）
- [ ] `GranularitySelector`（Pill + 滑动指示器）
- [ ] `DateNavigator`
- [ ] `HeroSection`（大数字 + 副指标网格 + 目标环 + `dailyNeededHint` + `currentStreak`）
- [ ] `YearCalendarHeatmap`（Canvas 绘制 + `horizontalScroll` + 自动定位当前周 + today 光晕）
- [ ] `MonthCalendarHeatmap`（大格子 18-20dp + 4 格汇总行）
- [ ] `HourlyHeatmap`（24 小时热力条 + 小时轴标注 + 峰值格标注）
- [ ] `WeeklyBarChart`（双柱 + `LaunchedEffect` 首次可见触发动画）
- [ ] `DistributionChart`（Canvas 环形图 + SegControl + TXT/EPUB 语义色）
- [ ] `TopNList`（SegControl + LazyColumn + `onBookClick(bookId)` 导航回调）
- [ ] `ReadingStatusChart`
- [ ] `ReadingTimeline`（会话合并阈值 30 分钟）
- [ ] `EmptyStatsState`（空数据友好提示组件）
- [ ] `StatsStrings` i18n 三语

### Phase 4：导航集成 + DI 接线（预计 1 天）

- [ ] `ActiveScreen` 新增 `Stats`
- [ ] `BookshelfScreen` 签名新增 `onNavigateToStats: () -> Unit` 参数，改造统计页导航链路（§13.5）
- [ ] `ShuLiAppContainer` 新增 `statsRepository` lazy 属性（注入 `ReadingSessionDao`）
- [ ] `ShuLiDatabase` 新增 `abstract fun readingSessionDao()`
- [ ] `ReadingProgressRepository` 调用方迁移到 `ReadingSessionDao.getBookTotals()` / `getTodayTotal()`
- [ ] `MainActivity` 新增 `ActiveScreen.Stats` 分支 + `StatsViewModel` 创建
- [ ] 验证 `BookshelfTopBar.onStatisticsClick` → `BookshelfScreen.onNavigateToStats` → `ActiveScreen.Stats` 完整链路

### Phase 5：联调 + 打磨（预计 2 天）

- [ ] 亮色/暗色主题全量测试（Nord Frost 色阶双模式验证）
- [ ] 空数据状态验证（Hero 大数字显示 `--`，热力图全灰 + 提示语，时间轴空 + 引导文案）
- [ ] 大量数据性能验证（365 天热力图横向滚动流畅性、100+ 本书 Top N 查询耗时）
- [ ] 月视图大格子触摸区域验证
- [ ] 横屏适配
- [ ] 无障碍（contentDescription、TalkBack 焦点、热力图格子语义标签）

---

## 11. 性能约束

| 约束 | 措施 |
|---|---|
| 热力图 365 格 | Canvas 绘制，非 Composable 节点 |
| 年视图横向滚动 | `Box(horizontalScroll)` + `Canvas(width=内容宽度)`，`LaunchedEffect` 自动定位当前周 |
| Top N 排序 | SQL 聚合 + LIMIT，不加载全量 |
| 每日统计写入 | 单行 INSERT 到 `reading_session`，O(1)，无 read-then-write |
| ViewModel 查询 | 全部走 `Flow`，SQL `GROUP BY` 在数据库层聚合 |
| 环比/同比计算 | `getDailyTotals()` 一次查询两个日期范围，不在应用层做差 |
| 字数估算 | `charsPerSecond × seconds`，UI 加 `≈` 前缀标注 |
| 周柱状图动画 | `LaunchedEffect` 首次可见触发一次，不在每次重组时重播 |

---

## 12. 与原型差异说明

> P0 = MVP 必须实现 · P1 = 重要改进 · P2 = 可延后增强

| 优先级 | 原型特性 | 实现决策 | 原因 |
|---|---|---|---|
| P0 | 数字滚动动画 | 简化为 `AnimatedContent` 切换 | 原型 JS 逐位动画在 Compose 中成本高，收益低 |
| P0 | 月视图热力图（周列排布） | 月视图改用 7 列日历网格，cellSize=18-20dp | 月视图 28-31 格用 11dp 太小，大格子填充空间且触摸友好 |
| P0 | 年视图热力图（固定宽度） | 包裹 `horizontalScroll` + 自动定位当前周 | 53 列超出屏幕宽度，Canvas 不支持原生滚动 |
| P0 | 线性色阶 | 改用 `sqrt` 非线性变换 | 极端高值会导致大多数天聚集在 L1，sqrt 压缩高值放大低值区分度 |
| P0 | 墨土棕色系 | 改用 Nord Frost 蓝轴色系 | 与项目 Nord 主题协调，暗色模式呈现冰霜渐变感 |
| P0 | 零数据无处理 | 新增 `EmptyStatsState` 组件 | Hero 大数字显示 `--`，热力图全灰 + 提示语，时间轴空 + 引导文案 |
| P0 | 目标环仅年视图 | 各粒度均有目标环（见下方公式） | 设计文档未覆盖此分支，各粒度都应有目标环 |
| P1 | 时间轴仅日粒度显示 | 日 + 周均显示 | 周视图数据量适中，时间轴有展示价值 |
| P1 | 混用数字格式 | 统一 `StatsFormatter` 工具类 | 时长 `XhYm`/`Xh`、字数 `≈X.X万`、百分比等格式需全局一致 |
| P1 | 字数为精确值 | 加 `≈` 前缀标注 | `seconds × avgSpeed` 是估算值，需视觉区分避免误导 |
| P1 | Top N 行无点击行为 | 行可点击跳转对应书籍 | 原型 row 有 hover 效果暗示可点击 |
| P1 | today 格 scale+shadow | Canvas 绘制 accent 描边光晕 | Canvas 不支持 CSS transform/box-shadow |
| P1 | 时间轴无合并规则 | 按 book_id + 时间间隔 < 30 分钟合并 | 避免同书相邻章节碎片化（见 §4.2） |
| P1 | 24h 热力条无小时轴 | 行标签加小时数（06-13h/14-21h/22-05h） | 仅文字时段标签不够精确 |
| P2 | WebDAV 同步提示 | 不展示 | 统计页面不暴露同步状态 |
| P2 | 峰值 callout | 保留但简化 | 仅文字标注，不做独立卡片 |
| P2 | 历史最长连读 | 日/周 Hero 区展示当前连续天数 | 当前连读更有激励价值 |
| P2 | 目标仅显示剩余时长+天数 | 新增「每天还需 X 分钟」 | 将数字转化为行动指导 |
| P2 | 每次重组重播动画 | `LaunchedEffect` 首次可见触发一次 | 快速切换粒度时避免视觉抖动 |
| P2 | 分布图按格式无语义色 | TXT=暖沙色，EPUB=冷蓝灰 | 与书架封面色保持一致 |
| P2 | 无阅读速度趋势 | 周视图 Hero 区 WPM 旁加趋势箭头 | 本周均速 vs 上周均速对比 |

### 12.1 目标环计算公式

| 粒度 | 目标值 | 来源 |
|---|---|---|
| 日 | `readingDailyTarget` 分钟 | `UserPreferences` 直接读取 |
| 周 | `readingDailyTarget × 7` 分钟 | 派生 |
| 月 | `readingDailyTarget × 当月天数` 分钟 | 派生 |
| 年 | `readingDailyTarget × 365` 分钟 | 派生 |

`goalPercent = (totalMinutes / goalMinutes × 100).coerceIn(0, 100)`

`dailyNeededMinutes = if (remainingDays > 0) ((goalMinutes - totalMinutes) / remainingDays).coerceAtLeast(0) else 0`

---

## 13. 增量更新 vs 破坏性重构决策

> 基于项目源码深度研读后的逐项决策，每项标注具体文件路径和代码依据。

### 13.0 数据架构：`reading_session` 单一数据源（破坏性重构）

**决策**：废弃原方案的 `daily_reading_stats` 聚合表，改为 `reading_session` 会话表作为唯一时长数据源。同时**删除** 3 个旧表中的时长字段（项目未发布，无需向后兼容）。

**代码依据**：

当前 4 个表各自累加时长：
- `reading_progress.readTime` ← `persistReadingTime()`
- `chapter_reading_stats.read_time_seconds` ← `flushChapterTime()`
- `reading_history.reading_duration_minutes` ← `recordReadingHistory()`
- ~~`daily_reading_stats.total_seconds`~~ ← 原方案再叠一层

`reading_session` 每次 flush 插入一行（`startedAt` → `endedAt`），所有聚合查询（热力图/章节/书单/时间轴）均从此表 `GROUP BY` 派生。旧字段直接删除：`reading_progress.readTime`、`chapter_reading_stats.read_time_seconds`、`reading_history.reading_duration_minutes`。

### 13.1 数据写入链路：复用已有 flush 点（增量）

**决策**：不新增独立定时器，在 `BookSessionManager` 已有的 `flushChapterTime()` 中追加 `ReadingSessionDao.insert()`。

**代码依据**（`BookSessionManager.kt:505-517`）：

```kotlin
// 已有：章节切换/释放时调用（可见性将从 private 改为 internal，见 §7.1）
internal fun flushChapterTime() {
    val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000L
    scope.launch(Dispatchers.IO) {
        dao.addReadTimeOrCreate(bookId, chapterIndex, elapsedSeconds)
    }
}
```

`flushChapterTime()` 已在以下时机被调用：
- `openChapter()` — 章节切换时（line 245）
- `releaseResources()` — 退出阅读器时（line 371）

**新增**（§14.2 修复）：
- `pauseReadingSession()` — App 切后台时

**方案**：在 `flushChapterTime()` 内部追加一行 `readingSessionDao.insert(ReadingSessionEntity(...))`。写入频率自然与章节切换频率一致（通常 1-5 分钟/次），无需额外防抖。

**性能收益**：不引入新定时器，不增加 I/O 次数（合并到已有的 `Dispatchers.IO` 协程中），写入为单行 INSERT（O(1)）。

### 13.2 BookDao 查询扩展：增量添加 3 个聚合查询

**决策**：在现有 `BookDao`（251 行）中追加 `getAllBooksWithDuration()` / `getBookmarkCounts()` / `getNoteCounts()`，不创建独立 `StatsBookDao`。

**代码依据**（`BookDao.kt`）：

已有 `BookShelfRow` 投影查询模式（line 24-52），使用 SELECT 指定列避免加载完整 `BookEntity`。新增的统计查询遵循同一模式：

```kotlin
// 遵循 BookShelfRow 投影模式，定义 BookWithDurationTuple
data class BookWithDurationTuple(
    val id: Long,
    val title: String,
    val author: String?,
    val fileType: String,
    val readingStatus: String,
    val totalChapterNum: Int,
    val estimatedTotalChars: Long,
    val totalDuration: Long,
)
```

**理由**：3 个查询添加到 251 行文件中是微量增量，且遵循已有的投影查询模式。独立 DAO 会增加 DI 接线成本（需修改 `ShuLiDatabase` abstract 方法、`ShuLiAppContainer` 构造）而不带来性能收益。

### 13.3 StatsRepository：新建（独立于 ReadingProgressRepository）

**决策**：新建 `StatsRepository`，不扩展 `ReadingProgressRepository`。

**代码依据**（`ReadingProgressRepository.kt`）：

当前 `ReadingProgressRepository` 职责明确定义为"阅读进度 + 时长统计 + 收藏 + 元数据 + 阅读状态迁移"（line 16）。其方法全部围绕**单本书**的进度操作（`updateReadingProgress`、`updateReadingPosition`、`toggleFavorite`、`updateReadingStatus`）。

统计页的 `StatsRepository` 职责是**跨书聚合**（热力图、Top N、维度分布、连续天数），数据源为 `ReadingSessionDao`，与 `ReadingProgressRepository` 的单本进度操作是完全不同的查询轴。合并会违反 SRP。

**性能考量**：独立 Repository 允许 `StatsRepository` 的查询全部走 `Flow` + `Dispatchers.Default` 做聚合计算，不影响 `ReadingProgressRepository` 的进度写入路径。

### 13.4 ReadingSessionDao：SQL 聚合替代应用层累加

**决策**：所有聚合通过 SQL `GROUP BY` 在数据库层完成，不在应用层做 `list.sumOf {}`。

**理由**：`reading_session` 表在 1 年使用后会积累 ~1000-3000 行（每天 3-8 次 flush）。`GROUP BY date_key` 在 SQLite 中走索引扫描（`date_key` 有索引），比取出全部行再在 Kotlin 中遍历聚合更高效：

| 查询 | SQL 层 | 应用层 |
|---|---|---|
| 热力图 365 天 | `GROUP BY date_key` → 返回 ≤365 行 | 取 3000 行 → `groupBy` → `sumOf` |
| 24h 热力条 | `GROUP BY hour` → 返回 ≤24 行 | 取当天所有行 → `groupBy` → `sumOf` |
| 章节时长 | `GROUP BY chapter_index` → 返回 ≤N 行 | 取该书所有行 → `groupBy` → `sumOf` |

SQL 层聚合返回的行数远小于原始行数，减少 Flow 发射的数据量和 Compose 重组开销。

### 13.5 导航入口：需改造 BookshelfScreen 回调链路

**决策**：修改 `BookshelfScreen` + `MainActivity`，将统计页从底部弹窗改为全屏页面。

**代码依据**（`BookshelfScreen.kt:113`）：

当前 `onStatisticsClick` 回调直接设置本地布尔值 `overlaysState.showStatisticsSheet = true`，控制底部弹窗显示，不经过 `MainActivity` 的导航系统。改为全屏页面需要完整链路：

1. `BookshelfScreen` 签名新增 `onNavigateToStats: () -> Unit` 参数
2. `BookshelfScreen.kt:113` 改为 `onStatisticsClick = { onNavigateToStats() }`
3. `MainActivity.kt` 的 `BookshelfScreen()` 调用处传入 `onNavigateToStats = { currentScreen = ActiveScreen.Stats }`
4. `MainActivity.kt` 的 `when (screen)` 新增 `ActiveScreen.Stats` 分支

### 13.6 ShuLiAppContainer：增量添加 StatsRepository

**决策**：在 `ShuLiAppContainer` 中追加 `statsRepository` lazy 属性。

**代码依据**（`ShuLiAppContainer.kt:84-90`）：

已有的 Repository 注册模式是 `by lazy` + 构造注入 DAO：

```kotlin
val statsRepository: StatsRepository by lazy {
    StatsRepository(
        readingSessionDao = database.readingSessionDao(),
        bookDao = database.bookDao(),
        bookmarkDao = database.bookmarkDao(),
        noteDao = database.noteDao(),
        userPreferences = userPreferences,
    )
}
```

不需要改变现有 DI 架构，不需要引入 Hilt/Dagger。

### 13.7 SettingsViewModel 统计字段：保留不动

**决策**：`readingTimeEnabled` 和 `readingDailyTarget` 保留在 `SettingsViewModel` 中供设置页使用。`StatsViewModel` 直接从 `UserPreferences` 读取 `readingDailyTarget`。

**代码依据**（`SettingsViewModel.kt:78-79`）：

```kotlin
userPreferences.readingTimeEnabled,     // arr[14]
userPreferences.readingDailyTarget,     // arr[15]
```

`SettingsViewModel` 使用 `combine` 聚合 26 个 Flow。移除 2 个统计字段需要重新编号所有 `arr[index]`（line 92-119），风险高且无收益。统计页的目标进度直接读 `userPreferences.readingDailyTarget` 即可。

### 13.8 数据库迁移：破坏性 Destructive Migration

**决策**：利用已有的 `fallbackToDestructiveMigration()`（`ShuLiAppContainer.kt:64`），Database version 23→24 时 Room 自动 DROP + 重建所有表。无需编写 Migration SQL，无需回填。

同步修改 3 个已有 Entity（删除时长字段），新增 1 个 Entity（`ReadingSessionEntity`）。现有 11 个 DAO 中 2 个需修改（`ReadingProgressDao` 移除 `readTime` 参数、`ChapterReadingStatsDao` 移除时长方法）。

### 决策总览

| 变更点 | 方案 | 理由 |
|---|---|---|
| 数据架构 | **破坏性重构** — `reading_session` 单一数据源 + 删除旧字段 | 消除 4 表冗余，项目未发布可直接清理 |
| 数据写入 | **增量** — 复用 `flushChapterTime()` | 不引入新定时器，合并 I/O |
| BookDao 查询 | **增量** — 追加 3 个聚合方法 | 遵循 `BookShelfRow` 投影模式，微量增量 |
| StatsRepository | **新建** — 独立 Repository | 跨书聚合 vs 单本进度是不同查询轴 |
| ReadingSessionDao | **新建** — SQL GROUP BY 聚合 | 数据库层聚合比应用层遍历高效 |
| 消费者迁移 | **破坏性** — 5 个消费者全部改读 `reading_session` | 真正实现全项目单一数据源 |
| Entity 字段 | **破坏性** — 删除 3 个旧表的时长字段 | 项目未发布，无兼容包袱 |
| 导航入口 | **破坏性** — 改造 `BookshelfScreen` 回调链路 | 当前回调打开底部弹窗，需改为全屏页面导航（§13.5） |
| DI 容器 | **增量** — 追加 lazy 属性 | 遵循现有 `by lazy` 模式 |
| SettingsViewModel | **不动** — 保留统计字段 | 避免 `combine` 数组重编号风险 |
| 数据库 | **破坏性** — Destructive Migration | `fallbackToDestructiveMigration()` 已有，无需 Migration SQL |

**总结**：11 个变更点中 5 个为破坏性（数据架构、消费者迁移、字段删除、Entity 修改、Destructive Migration），6 个为增量扩展。破坏性重构的收益是彻底消除 4 份冗余时长数据，全项目统一读 `reading_session`。

---

## 14. 性能与数据准确性分析

> 基于 `BookSessionManager`、`ReadingStateManager`、`ReaderCanvasEffects` 的源码级追踪。

### 14.1 写入路径并发安全

**原问题**：`daily_reading_stats` 的 read-then-write（`@Transaction` upsert）在快速切章时可能竞态。

**`reading_session` 架构下已消除**：写入改为 `INSERT`（追加新行），不存在 read-then-write。两次并发 `flushChapterTime()` 各自插入独立行，`SUM(duration_seconds)` 聚合时两条记录都参与计算，不丢失数据。

```
协程 A: INSERT session(duration=30)  → 行 1
协程 B: INSERT session(duration=20)  → 行 2
聚合 SUM: 30 + 20 = 50  ✅ 无丢失
```

### 14.2 生命周期暂停时未 flush

**问题**：App 切后台时 `ReaderCanvasEffects` 仅调用 `pauseReadingSession()`（line 85），不触发 `flushChapterTime()`。

```kotlin
// ReaderCanvasEffects.kt:82-92
Lifecycle.Event.ON_PAUSE -> {
    viewModel.pauseReadingSession()   // 仅暂停 ReadingStateManager 计时器
    // ❌ flushChapterTime() 未被调用
}
```

**后果**：如果用户在同一章节内阅读 30 分钟后切后台，这 30 分钟不会写入 `reading_session`，直到下次切换章节或退出阅读器。如果进程在此期间被杀，30 分钟丢失。

**修复**：在 `ReaderViewModel.pauseReadingSession()` 中追加 `flushChapterTime()` 调用：

```kotlin
fun pauseReadingSession() {
    bookSessionManager.flushChapterTime()   // ← 新增
    readingStateManager.pauseSession()
}
```

同理，`resumeReadingSession()` 中重置 `chapterStartTimestamp`：

```kotlin
fun resumeReadingSession() {
    readingStateManager.resumeSession()
    bookSessionManager.resetChapterStartTimestamp()   // ← 新增，避免恢复后重复计算暂停时段
}
```

**注意**：这是已有的准确性缺陷（依赖 `flushChapterTime` 的表均受影响），本次修复确保 `reading_session` 在切后台时准确 flush。

### 14.3 跨午夜归属

**问题**：`flushChapterTime()` 在 flush 时刻调用 `todayDateKey()` 获取当前日期。如果用户从 23:30 读到 01:00 不切换章节，最终 flush 时全部 90 分钟归属于 01:00 所在日期。

```
23:30 开始阅读 → chapterStartTimestamp = 23:30
01:00 切换章节 → flushChapterTime() → todayDateKey() = 次日 → 90 分钟全归次日
```

**影响评估**：这是一个**低频边缘场景**（需要用户在午夜前后持续阅读同一章节且不切换），且总时长不丢失（只是日期归属有偏）。属于已知限制，不做修复。

**缓解**：如果未来需要高精度，可在 `ReadingStateManager.pauseSession()` 时按 `chapterStartTimestamp` 的日期归属写入，而非 flush 时的日期。但这会增加写入复杂度，当前不做。

**迁移后行为差异**：当前 `getTodayTotalReadingTime()` 使用 `updatedTime >= todayStart`（午夜时间戳），按最后更新时间归属。`reading_session` 的 `getTodayTotal(dateKey)` 使用 `date_key = startedAt 的日期`，按阅读开始时间归属。跨午夜场景下两者结果不同（23:50 开始、00:10 结束的 20 分钟：旧方案归入"明天"，新方案归入"今天"）。新方案语义更准确。

### 14.4 Flow 查询开销

**已不适用**：原设计中 `getRange().first()` 用 Flow 查询做 read-then-write。`reading_session` 架构下写入为纯 `INSERT`，不需要查询现有行。热力图渲染用的 `getDailyTotals()` 是 Flow（需要响应式更新），但这是正确的用法——统计页需要观察数据变化。

### 14.5 性能数据估算

| 操作 | 估算耗时 | 依据 |
|---|---|---|
| `INSERT` 单行会话 | < 0.1ms | 主键自增 + 4 个索引维护 |
| `getDailyTotals()` 全年 GROUP BY | < 1ms | `date_key` 索引扫描，3000 行 → ≤365 行 |
| `getHourlyTotals()` 单日 GROUP BY | < 0.1ms | `date_key` 索引定位 + `hour` 聚合，≤8 行 |
| `getActiveDateKeys()` 全年 DISTINCT | < 0.5ms | `date_key` 索引扫描，去重后 ≤365 个整数 |
| `getAllBooksWithDuration()` 100 本书 | < 2ms | LEFT JOIN + GROUP BY，`book_id` 索引。`StatsRepository` 层用 `stateIn(SharingStarted.WhileSubscribed(5000))` 缓存，避免 `books` 表变化时重复触发 JOIN |
| `getBookmarkCounts()` 100 本书 × 500 书签 | < 1ms | COUNT + GROUP BY，`book_id` 索引 |
| Top N 排序（SQL LIMIT 5） | < 0.5ms | ORDER BY + LIMIT，不需要全量排序 |
| 热力图 Canvas 绘制 365 格 | < 2ms | 365 × `drawRoundRect`，无 Composable 树 |
| `StatsRepository` 应用层 streak 计算 | < 0.5ms | 365 次整数比较 + 计数器 |

**总计**：统计页首次加载（全年视图）50-200ms（SQL 查询 + Flow 冷启动 + ViewModel combine 首次发射 + Compose LazyColumn 首帧布局 + Canvas 首次绘制）。在可接受范围内，不会造成明显卡顿。

**Flow 防抖**：`getDailyTotals()` 返回 `Flow<List<DailyTotalTuple>>`，每次 `reading_session` 表有新行插入都会触发重发射。为避免 UI 闪烁，`StatsViewModel` 层对热力图数据加 `debounce(300)` + `distinctUntilChanged()`。

### 14.6 准确性总览

| 场景 | 总时长 | 日期归属 | 小时归属 | 状态 |
|---|---|---|---|---|
| 正常阅读 + 切换章节 | ✅ 准确 | ✅ 准确 | ✅ 准确 | 每次 flush 插入 session 行 |
| App 切后台 | ✅ 准确（修复后） | ✅ 准确 | ✅ 准确 | §14.2 修复 |
| App 进程被杀（onDispose 触发） | ✅ 准确 | ✅ 准确 | ✅ 准确 | 已有 `releaseResources` |
| App 进程被杀（onDispose 未触发） | ⚠️ 丢失未 flush 部分 | — | — | 已有缺陷，非本次引入 |
| 跨午夜阅读同一章节 | ✅ 准确 | ⚠️ 归属到 flush 日 | ✅ 准确 | 已知限制，低频场景 |
| 快速连续切换章节 | ✅ 准确 | ✅ 准确 | ✅ 准确 | INSERT-only 无竞态 |
| 历史数据（迁移前） | — | — | — | Destructive Migration 清空所有数据，无历史残留 |
| 多表数据不一致 | — | — | — | `reading_session` 为唯一仲裁者 |
