# 阅读统计界面 TDD 任务清单

> 对应设计文档：`docs/27-stats-screen-design.md`
> 测试框架：JUnit 4 + MockK + Coroutines Test + Room In-Memory DB
> 测试基类：`CoroutineTestBase`（StandardTestDispatcher）/ `MainDispatcherRule`（UnconfinedTestDispatcher）
> 测试目录约定：
> - 单元测试：`app/src/test/java/com/shuli/reader/...`
> - 集成测试（Room DAO）：`app/src/androidTest/java/com/shuli/reader/database/...`
> - Compose UI 测试：`app/src/androidTest/java/com/shuli/reader/ui/...`

**TDD 节奏**：每个任务严格遵循 Red → Green → Refactor。先写失败的测试（Red），再写最小实现使测试通过（Green），最后在后续任务中统一重构（Refactor）。

---

## Phase 1：数据层 — Entity / DAO / 迁移

### 1.1 ReadingSessionEntity 定义

**测试文件**：`app/src/test/java/com/shuli/reader/core/database/entity/ReadingSessionEntityTest.kt`

- [x] **T-1.1.1** `ReadingSessionEntity` 默认值验证
  - 测试：创建实例时 `id=0`, `isDirty=true`, `version=1`, `syncedVersion=0`, `deleted=false`, `updatedAt=0L`, `mergeSource=null`
  - 实现：创建 `ReadingSessionEntity` data class

- [x] **T-1.1.2** `ReadingSessionEntity` 字段完整性
  - 测试：所有 14 个字段可正确赋值和读取（`id`, `bookId`, `chapterIndex`, `startedAt`, `endedAt`, `durationSeconds`, `dateKey`, `hour`, 同步字段）
  - 实现：确认 data class 字段定义

- [x] **T-1.1.3** `copy()` 语义验证
  - 测试：`copy(durationSeconds = 999)` 仅修改目标字段，其余不变
  - 实现：data class 自带

### 1.2 ReadingSessionDao — 写入

**测试文件**：`app/src/androidTest/java/com/shuli/reader/database/ReadingSessionDaoTest.kt`

- [x] **T-1.2.1** `insert()` 返回自增 ID
  - 测试：插入两条记录，ID 分别为 1 和 2
  - 实现：`@Insert` 方法

- [x] **T-1.2.2** `insert()` 并发安全（无竞态）
  - 测试：两个协程并发插入，各自成功，聚合 SUM 不丢失
  - 实现：主键自增保证

### 1.3 ReadingSessionDao — 热力图查询

- [x] **T-1.3.1** `getDailyTotals()` 基础聚合
  - 测试：插入 3 条不同 `dateKey` 的记录，查询返回正确 `dateKey → SUM(duration_seconds)` 映射
  - 实现：`@Query` SQL GROUP BY date_key

- [x] **T-1.3.2** `getDailyTotals()` 范围过滤
  - 测试：插入 5 条记录（dateKey 20260101~20260105），查询 `start=20260102, end=20260104`，仅返回 3 条
  - 实现：`BETWEEN :start AND :end`

- [x] **T-1.3.3** `getDailyTotals()` 同日多条记录合并
  - 测试：同日插入 3 条（100s + 200s + 300s），查询返回 `total=600`
  - 实现：`SUM(duration_seconds)`

- [x] **T-1.3.4** `getDailyTotals()` 空数据返回空列表
  - 测试：无数据时查询返回 `emptyList()`
  - 实现：SQL 自然返回

- [x] **T-1.3.5** `getDailyTotals()` 排序验证
  - 测试：插入乱序 dateKey 数据，查询结果按 `date_key ASC` 排列
  - 实现：`ORDER BY date_key ASC`

### 1.4 ReadingSessionDao — 24h 热力条查询

- [x] **T-1.4.1** `getHourlyTotals()` 基础聚合
  - 测试：同日插入 hour=9/10/11 各一条，查询返回 3 行
  - 实现：`@Query` SQL GROUP BY hour

- [x] **T-1.4.2** `getHourlyTotals()` 同小时合并
  - 测试：同日 hour=10 插入 2 条（100s + 200s），查询 hour=10 的 total=300
  - 实现：`SUM(duration_seconds)`

- [x] **T-1.4.3** `getHourlyTotals()` 指定日期过滤
  - 测试：不同 dateKey 各插 hour=10，查询仅返回目标日期的聚合
  - 实现：`WHERE date_key = :dateKey`

### 1.5 ReadingSessionDao — 连续活跃天数

- [x] **T-1.5.1** `getActiveDateKeys()` 返回去重有序日期
  - 测试：插入 dateKey=20260103, 20260101, 20260103（重复），查询返回 `[20260101, 20260103]`
  - 实现：`SELECT DISTINCT date_key ... ORDER BY date_key ASC`

- [x] **T-1.5.2** `getActiveDateKeys()` 空数据
  - 测试：无数据时返回空列表
  - 实现：SQL 自然返回

### 1.6 ReadingSessionDao — 今日总时长

- [x] **T-1.6.1** `getTodayTotal()` 基础查询
  - 测试：插入今日 2 条（300s + 600s），查询返回 900
  - 实现：`@Query SELECT SUM(duration_seconds) FROM reading_session WHERE date_key = :todayKey`

- [x] **T-1.6.2** `getTodayTotal()` 无数据返回 null
  - 测试：无今日数据时返回 null
  - 实现：`SUM()` 对空集返回 NULL

- [x] **T-1.6.3** `getTodayTotal()` Flow 响应式更新
  - 测试：collect Flow → 插入新行 → 收到更新值
  - 实现：Flow 返回类型

### 1.7 ReadingSessionDao — 书籍总时长

- [x] **T-1.7.1** `getBookTotals()` 多书聚合
  - 测试：book_id=1 插入 2 条，book_id=2 插入 1 条，查询返回 2 行各自 SUM
  - 实现：`@Query GROUP BY book_id`

- [x] **T-1.7.2** `getBookTotals()` Flow 响应式
  - 测试：collect → 插入 → 收到新值
  - 实现：Flow 返回类型

### 1.8 ReadingSessionDao — 章节总时长

- [x] **T-1.8.1** `getChapterTotals()` 基础聚合
  - 测试：bookId=1, chapterIndex=0 插入 2 条，chapterIndex=1 插入 1 条，查询返回 2 行
  - 实现：`@Query GROUP BY chapter_index`

- [x] **T-1.8.2** `getChapterTotals()` firstVisitedAt / lastVisitedAt
  - 测试：插入 startedAt=100/200/300，验证 MIN(started_at)=100, MAX(ended_at)=最后一条的 endedAt
  - 实现：`MIN(started_at)`, `MAX(ended_at)`

- [x] **T-1.8.3** `getChapterTotals()` 按章节排序
  - 测试：乱序插入 chapter 0/2/1，查询结果按 `chapter_index ASC` 排列
  - 实现：`ORDER BY chapter_index ASC`

### 1.9 ReadingSessionDao — 阅读时间轴

- [x] **T-1.9.1** `getSessionsInRange()` 返回完整实体
  - 测试：插入 2 条，查询返回 `ReadingSessionEntity` 列表，所有字段可访问
  - 实现：`@Query SELECT * ... ORDER BY started_at DESC`

- [x] **T-1.9.2** `getSessionsInRange()` 按 started_at 降序
  - 测试：插入 startedAt=100/300/200，查询返回顺序为 300/200/100
  - 实现：`ORDER BY started_at DESC`

- [x] **T-1.9.3** `getSessionsInRange()` 日期范围过滤
  - 测试：插入不同 dateKey，查询仅返回范围内数据
  - 实现：`WHERE date_key BETWEEN :start AND :end`

### 1.10 Tuple 数据类

**测试文件**：`app/src/test/java/com/shuli/reader/core/database/dao/TupleDataClassTest.kt`

- [x] **T-1.10.1** `DailyTotalTuple` 字段访问
  - 测试：创建实例，验证 `dateKey` 和 `total` 可读取
  - 实现：`data class DailyTotalTuple(val dateKey: Int, val total: Long)`

- [x] **T-1.10.2** `HourlyTotalTuple` 字段访问
  - 测试：同上，`hour` 和 `total`
  - 实现：`data class HourlyTotalTuple(val hour: Int, val total: Long)`

- [x] **T-1.10.3** `ChapterTotalTuple` 字段访问
  - 测试：验证 `chapterIndex`, `totalSeconds`, `firstVisitedAt`, `lastVisitedAt`
  - 实现：`data class ChapterTotalTuple(...)`

- [x] **T-1.10.4** `BookWithDurationTuple` 字段访问
  - 测试：验证所有 8 个字段（`id`, `title`, `author`, `fileType`, `readingStatus`, `totalChapterNum`, `estimatedTotalChars`, `totalDuration`）
  - 实现：`data class BookWithDurationTuple(...)`

### 1.11 旧 Entity 字段删除

**测试文件**：复用已有 Entity 测试 + 编译验证

- [x] **T-1.11.1** `ReadingProgressEntity` 不再含 `readTime`
  - 测试：编译通过（`ReadingProgressEntity` 无 `readTime` 属性），已有 `BookEntityTest` 不受影响
  - 实现：删除 `readTime: Long` 字段

- [x] **T-1.11.2** `ChapterReadingStatsEntity` 不再含 `readTimeSeconds`
  - 测试：编译通过（无 `readTimeSeconds` 属性）
  - 实现：删除 `readTimeSeconds: Long` 字段

- [x] **T-1.11.3** `ReadingHistoryEntity` 不再含 `readingDurationMinutes`
  - 测试：编译通过（无 `readingDurationMinutes` 属性）
  - 实现：删除 `readingDurationMinutes: Long` 字段

### 1.12 ReadingProgressDao 改造

**测试文件**：已有 DAO 测试（androidTest）

- [x] **T-1.12.1** `updateProgress()` 不含 `readTime` 参数
  - 测试：编译通过，调用 `updateProgress()` 时无 `readTime` 参数
  - 实现：移除 `readTime` 参数

- [x] **T-1.12.2** 移除 `getReadingDurationByBookId()`
  - 测试：编译通过，无此方法引用
  - 实现：删除方法

- [x] **T-1.12.3** 移除 `getAllReadingDurations()`
  - 测试：编译通过
  - 实现：删除方法

- [x] **T-1.12.4** 移除 `getTodayTotalReadingTime()`
  - 测试：编译通过
  - 实现：删除方法

### 1.13 ChapterReadingStatsDao 改造

- [x] **T-1.13.1** 移除 `addReadTime()` / `addReadTimeOrCreate()`
  - 测试：编译通过，无方法引用
  - 实现：删除方法

- [x] **T-1.13.2** `ensureExists()` 不再引用 `readTimeSeconds`
  - 测试：编译通过，`ensureExists()` 调用正常
  - 实现：更新构造函数调用

### 1.14 BookDao 扩展

**测试文件**：`app/src/androidTest/java/com/shuli/reader/database/BookDaoStatsTest.kt`

- [x] **T-1.14.1** `getAllBooksWithDuration()` 基础查询
  - 测试：插入 2 本书 + 对应 reading_session 数据，查询返回含 `totalDuration` 的正确投影
  - 实现：`@Query LEFT JOIN reading_session`

- [x] **T-1.14.2** `getAllBooksWithDuration()` 无阅读记录返回 0
  - 测试：插入书但无 session，`totalDuration` 为 0（`COALESCE`）
  - 实现：`COALESCE(rs.totalDuration, 0)`

- [x] **T-1.14.3** `getAllBooksWithDuration()` Flow 响应式
  - 测试：collect → 插入 session → 收到更新
  - 实现：Flow 返回

### 1.15 ShuLiDatabase 升级

**测试文件**：`app/src/androidTest/java/com/shuli/reader/database/MigrationTest.kt`（已有）

- [x] **T-1.15.1** Database version 24 可创建
  - 测试：`ShuLiDatabase` 可打开，`readingSessionDao()` 不为 null
  - 实现：version 23→24，新增 entity 和 DAO

- [x] **T-1.15.2** `readingSessionDao()` 返回有效 DAO
  - 测试：通过 DAO 插入并查询数据
  - 实现：`abstract fun readingSessionDao(): ReadingSessionDao`

- [x] **T-1.15.3** Destructive Migration 验证
  - 测试：version 23 升级到 24 不崩溃（`fallbackToDestructiveMigration()` 已有）
  - 实现：version 升级

---

## Phase 2：数据写入路径 — BookSessionManager 改造

### 2.1 flushChapterTime() 改造

**测试文件**：`app/src/test/java/com/shuli/reader/core/reader/BookSessionManagerWriteTest.kt`

- [x] **T-2.1.1** `flushChapterTime()` 插入 ReadingSessionEntity
  - 测试：Mock `ReadingSessionDao`，调用 `flushChapterTime()` 后验证 `insert()` 被调用，参数中 `bookId`, `chapterIndex`, `durationSeconds` 正确
  - 实现：`flushChapterTime()` 内追加 `readingSessionDao.insert()`

- [x] **T-2.1.2** `flushChapterTime()` 不再调用 `addReadTimeOrCreate()`
  - 测试：Mock `ChapterReadingStatsDao`，验证 `addReadTimeOrCreate` 未被调用（`verify(exactly = 0)`）
  - 实现：删除旧调用

- [x] **T-2.1.3** `flushChapterTime()` elapsedSeconds < 1 时不写入
  - 测试：模拟快速切章（< 1s），验证 `readingSessionDao.insert()` 未被调用
  - 实现：保持已有的 `< 1L` 守卫

- [x] **T-2.1.4** `flushChapterTime()` 重置 `chapterStartTimestamp`
  - 测试：调用 flush 后，再次 flush 的 `durationSeconds` 从 0 开始计
  - 实现：`chapterStartTimestamp = System.currentTimeMillis()`

- [x] **T-2.1.5** `flushChapterTime()` dateKey 计算
  - 测试：验证插入的 `dateKey` 为当天 yyyyMMdd 格式整数
  - 实现：`todayDateKey()` 计算

- [x] **T-2.1.6** `flushChapterTime()` hour 计算
  - 测试：验证插入的 `hour` 为当前小时（0-23）
  - 实现：`Calendar.HOUR_OF_DAY`

- [x] **T-2.1.7** `flushChapterTime()` startedAt / endedAt 正确性
  - 测试：验证 `startedAt` 约等于 chapterStartTimestamp，`endedAt` 约等于 now
  - 实现：传入时间戳

### 2.2 persistReadingTime() 重命名

- [x] **T-2.2.1** `persistReadingPosition()` 不写 `readTime`
  - 测试：调用 `persistReadingPosition()` 后，验证 `ReadingProgressDao.updateProgress()` 被调用但无 `readTime` 参数
  - 实现：重命名 + 移除 readTime 累加

- [x] **T-2.2.2** `persistReadingPosition()` 仍更新 pageIndex / position / updatedTime
  - 测试：验证 `updateProgress()` 被调用，`pageIndex` 等参数正确
  - 实现：保留位置更新逻辑

### 2.3 BookSessionManager 构造注入

- [x] **T-2.3.1** `BookSessionManager` 接收 `ReadingSessionDao`
  - 测试：构造时传入 mock `ReadingSessionDao`，不抛异常
  - 实现：构造函数新增参数

### 2.4 flushChapterTime() 可见性

- [x] **T-2.4.1** `flushChapterTime()` 为 `internal` 可见性
  - 测试：同模块代码可直接调用（编译验证）
  - 实现：`private` → `internal`

### 2.5 resetChapterStartTimestamp()

- [x] **T-2.5.1** `resetChapterStartTimestamp()` 重置计时起点
  - 测试：调用后立即 `flushChapterTime()`，`durationSeconds` 接近 0
  - 实现：`internal fun resetChapterStartTimestamp()`

### 2.6 releaseResources() 清理

- [x] **T-2.6.1** `releaseResources()` 不再调用 `persistReadingTime()`
  - 测试：Mock 验证 `persistReadingPosition()` 被调用（非 `persistReadingTime()`）
  - 实现：删除旧调用，保留 `saveReadingProgress` + `flushChapterTime`

- [x] **T-2.6.2** `releaseResources()` 调用链完整性
  - 测试：验证 `saveReadingProgress` → `flushChapterTime` → `endSession` → `cancel` 顺序
  - 实现：保持调用顺序

---

## Phase 3：生命周期暂停/恢复 — ReaderViewModel

### 3.1 pauseReadingSession()

**测试文件**：`app/src/test/java/com/shuli/reader/feature/reader/ReaderViewModelSessionTest.kt`

- [x] **T-3.1.1** `pauseReadingSession()` 调用 `flushChapterTime()`
  - 测试：Mock `BookSessionManager`，调用 `pauseReadingSession()` 后验证 `flushChapterTime()` 被调用
  - 实现：追加 `bookSessionManager.flushChapterTime()`

- [x] **T-3.1.2** `pauseReadingSession()` 调用 `readingStateManager.pauseSession()`
  - 测试：验证 `pauseSession()` 被调用
  - 实现：保持已有调用

### 3.2 resumeReadingSession()

- [x] **T-3.2.1** `resumeReadingSession()` 调用 `resetChapterStartTimestamp()`
  - 测试：Mock `BookSessionManager`，调用 `resumeReadingSession()` 后验证 `resetChapterStartTimestamp()` 被调用
  - 实现：追加 `bookSessionManager.resetChapterStartTimestamp()`

- [x] **T-3.2.2** `resumeReadingSession()` 调用 `readingStateManager.resumeSession()`
  - 测试：验证 `resumeSession()` 被调用
  - 实现：保持已有调用

---

## Phase 4：消费者迁移

### 4.1 BookshelfViewModel 迁移

**测试文件**：`app/src/test/java/com/shuli/reader/feature/bookshelf/BookshelfViewModelTest.kt`（已有，扩展）

- [x] **T-4.1.1** 书架每本书时长读 `ReadingSessionDao.getBookTotals()`
  - 测试：Mock `ReadingSessionDao` 返回 `BookDurationTuple(bookId=1, total=3600)`，验证 UI 显示 "1h"
  - 实现：注入 `ReadingSessionDao`，替换 `readingProgressRepository.getReadingDurations()`

- [x] **T-4.1.2** 书架今日总时长读 `ReadingSessionDao.getTodayTotal()`
  - 测试：Mock 返回 1800L（秒），验证 UI 显示 "30m"
  - 实现：替换 `getTodayReadingTime()`

- [x] **T-4.1.3** `BookshelfViewModel` 不再依赖 `ReadingProgressRepository.getReadingDurations()`
  - 测试：`verify(exactly = 0) { readingProgressRepository.getReadingDurations() }`
  - 实现：移除旧依赖

- [x] **T-4.1.4** 今日时长格式化使用 `StatsFormatter`
  - 测试：传入 3600（秒），验证显示 "1h"（非旧 bug 的 "60h"）
  - 实现：`toReadableDuration()` → `StatsFormatter.formatDuration()`

### 4.2 ChapterList 迁移

**测试文件**：集成验证

- [x] **T-4.2.1** 章节列表时长读 `ReadingSessionDao.getChapterTotals()`
  - 测试：Mock 返回章节时长数据，验证列表正确显示
  - 实现：替换 `ChapterReadingStatsDao.getStatsByBookId()`

### 4.3 ReadingProgressRepository 迁移

- [x] **T-4.3.1** `getReadingDuration()` 委托 `ReadingSessionDao.getBookTotals()`
  - 测试：Mock DAO 返回数据，验证 Repository 正确透传
  - 实现：委托查询

### 4.4 BookItem 格式化迁移

- [x] **T-4.4.1** `BookItem.kt` 使用 `StatsFormatter.formatDuration()`
  - 测试：传入秒值，验证格式化输出正确
  - 实现：替换 `toReadableDuration()`

### 4.5 BackupExporter / BackupImporter 扩展

**测试文件**：`app/src/test/java/com/shuli/reader/sync/export/BackupExporterTest.kt`（已有，扩展）

- [x] **T-4.5.1** `BackupExporter` 序列化 `reading_session` 表
  - 测试：插入 session 数据后导出，验证导出的 ZIP 中包含 `reading_session` 数据
  - 实现：新增表序列化

- [x] **T-4.5.2** `BackupImporter` 反序列化 `reading_session` 表
  - 测试：导入含 session 数据的备份，验证数据库中存在正确记录
  - 实现：新增表反序列化

---

## Phase 5：工具类 — StatsFormatter / StatsGranularity / StatsDateNavigator

### 5.1 StatsFormatter

**测试文件**：`app/src/test/java/com/shuli/reader/core/util/StatsFormatterTest.kt`

- [x] **T-5.1.1** `formatDuration()` — 0 秒
  - 测试：`formatDuration(0)` → `"0m"`
  - 实现：`< 60s → "0m"`

- [x] **T-5.1.2** `formatDuration()` — 纯分钟
  - 测试：`formatDuration(1500)` → `"25m"`
  - 实现：`< 3600s → "Xm"`

- [x] **T-5.1.3** `formatDuration()` — 小时 + 分钟
  - 测试：`formatDuration(5400)` → `"1h30m"`
  - 实现：`< 86400s → "XhYm"`

- [x] **T-5.1.4** `formatDuration()` — 整小时省略分钟
  - 测试：`formatDuration(7200)` → `"2h"`
  - 实现：Y=0 时省略

- [x] **T-5.1.5** `formatDuration()` — 天 + 小时
  - 测试：`formatDuration(90000)` → `"1d1h"`
  - 实现：`>= 86400s → "XdYh"`

- [x] **T-5.1.6** `formatDuration()` — 整天省略小时
  - 测试：`formatDuration(86400)` → `"1d"`
  - 实现：Y=0 时省略

- [x] **T-5.1.7** `formatWords()` — zh-CN 小于万
  - 测试：`formatWords(5000, zhLocale)` → `"5000字"`
  - 实现：`< 10000 → "X字"`

- [x] **T-5.1.8** `formatWords()` — zh-CN 大于等于万
  - 测试：`formatWords(11840000, zhLocale)` → `"≈1184万"`
  - 实现：`>= 10000 → "≈X.X万"`

- [x] **T-5.1.9** `formatWords()` — en 小于千
  - 测试：`formatWords(500, enLocale)` → `"500"`
  - 实现：`< 1000 → "X"`

- [x] **T-5.1.10** `formatWords()` — en 大于等于千
  - 测试：`formatWords(1500, enLocale)` → `"≈1.5K"`
  - 实现：`>= 1000 → "≈X.XK"`

- [x] **T-5.1.11** `formatPercent()` — 整数
  - 测试：`formatPercent(75.0f)` → `"75%"`
  - 实现：整数不带小数

- [x] **T-5.1.12** `formatPercent()` — 非整数
  - 测试：`formatPercent(75.3f)` → `"75.3%"`
  - 实现：保留一位小数

- [x] **T-5.1.13** `zeroOrNull()` — 零值
  - 测试：`zeroOrNull(0)` → `"--"`
  - 实现：0 → `"--"`

- [x] **T-5.1.14** `zeroOrNull()` — 非零值
  - 测试：`zeroOrNull(3600)` → 正常格式化的时长字符串
  - 实现：非 0 → 正常格式化

### 5.2 StatsGranularity

**测试文件**：`app/src/test/java/com/shuli/reader/feature/stats/StatsGranularityTest.kt`

- [x] **T-5.2.1** `DAY.dateRange()` 返回单日范围
  - 测试：`DAY.dateRange(2026-06-09)` → `DateKeyRange(20260609, 20260609)`
  - 实现：`dateRange()` 方法

- [x] **T-5.2.2** `WEEK.dateRange()` 返回周一到周日
  - 测试：`WEEK.dateRange(2026-06-10 周三)` → `DateKeyRange(20260608, 20260614)`
  - 实现：计算周起止

- [x] **T-5.2.3** `MONTH.dateRange()` 返回当月范围
  - 测试：`MONTH.dateRange(2026-02-15)` → `DateKeyRange(20260201, 20260228)`
  - 实现：月份起止

- [x] **T-5.2.4** `YEAR.dateRange()` 返回当年范围
  - 测试：`YEAR.dateRange(2026-06-09)` → `DateKeyRange(20260101, 20261231)`
  - 实现：年份起止

- [x] **T-5.2.5** `previousRange()` 各粒度正确
  - 测试：DAY 返回前一天，WEEK 返回前一周，MONTH 返回前一月，YEAR 返回前一年
  - 实现：`previousRange()` 方法

- [x] **T-5.2.6** `dateText()` 格式化正确
  - 测试：各粒度 + zh-CN locale 返回正确的日期文本
  - 实现：`dateText()` 方法

- [x] **T-5.2.7** `canGoNext()` 不超过当前日期
  - 测试：当前日期为 today，`canGoNext(today)` → false；`canGoNext(yesterday)` → true
  - 实现：`canGoNext()` 方法

### 5.3 StatsDateNavigator

**测试文件**：`app/src/test/java/com/shuli/reader/feature/stats/StatsDateNavigatorTest.kt`

- [x] **T-5.3.1** `next(DAY, ...)` 返回下一天
  - 测试：2026-06-09 → 2026-06-10
  - 实现：`next()` 方法

- [x] **T-5.3.2** `prev(DAY, ...)` 返回前一天
  - 测试：2026-06-09 → 2026-06-08
  - 实现：`prev()` 方法

- [x] **T-5.3.3** `next(WEEK, ...)` 返回下一周
  - 测试：验证加 7 天
  - 实现：`next()` 方法

- [x] **T-5.3.4** `prev(WEEK, ...)` 返回上一周
  - 测试：验证减 7 天
  - 实现：`prev()` 方法

- [x] **T-5.3.5** `next(MONTH, ...)` 返回下一月
  - 测试：2026-01-15 → 2026-02-15
  - 实现：`next()` 方法

- [x] **T-5.3.6** `prev(MONTH, ...)` 返回上一月
  - 测试：2026-03-15 → 2026-02-15
  - 实现：`prev()` 方法

- [x] **T-5.3.7** `next(YEAR, ...)` 返回下一年
  - 测试：2026-06-09 → 2027-06-09
  - 实现：`next()` 方法

- [x] **T-5.3.8** `prev(YEAR, ...)` 返回上一年
  - 测试：2026-06-09 → 2025-06-09
  - 实现：`prev()` 方法

### 5.4 DateKeyRange

**测试文件**：`app/src/test/java/com/shuli/reader/feature/stats/DateKeyRangeTest.kt`

- [x] **T-5.4.1** `contains()` 范围内
  - 测试：`DateKeyRange(20260101, 20260131).contains(20260115)` → true
  - 实现：`operator fun contains()`

- [x] **T-5.4.2** `contains()` 范围外
  - 测试：`DateKeyRange(20260101, 20260131).contains(20260201)` → false
  - 实现：同上

- [x] **T-5.4.3** `contains()` 边界值
  - 测试：start 和 end 值本身在范围内
  - 实现：同上

---

## Phase 6：StatsRepository

### 6.1 热力图数据

**测试文件**：`app/src/test/java/com/shuli/reader/feature/stats/StatsRepositoryTest.kt`

- [x] **T-6.1.1** `getHeatmapData()` 基础映射
  - 测试：Mock DAO 返回 DailyTotalTuple 列表，验证输出 `DailyHeatCell` 列表的 `dateKey` 和 `minutes` 正确
  - 实现：`getHeatmapData()` 方法

- [x] **T-6.1.2** `getHeatmapData()` 空数据
  - 测试：Mock DAO 返回空列表，验证输出空列表
  - 实现：空数据处理

- [x] **T-6.1.3** `getHeatmapData()` 缺失日期填充 L0
  - 测试：DAO 仅返回 3 天数据，但日期范围 30 天，验证缺失日期的 heatLevel 为 L0
  - 实现：补全日期范围

### 6.2 Hero 指标

- [x] **T-6.2.1** `getHeroMetrics()` totalMinutes 计算
  - 测试：Mock 总时长 7200s，验证 `totalMinutes = 120`
  - 实现：`getHeroMetrics()` 方法

- [x] **T-6.2.2** `getHeroMetrics()` activeDays 计算
  - 测试：Mock 5 个不同 dateKey，验证 `activeDays = 5`
  - 实现：从 `getActiveDateKeys()` 派生

- [x] **T-6.2.3** `getHeroMetrics()` deltaPercent 环比计算
  - 测试：本期 100 分钟，上期 80 分钟，验证 `deltaPercent = 25.0f`
  - 实现：环比公式

- [x] **T-6.2.4** `getHeroMetrics()` deltaPercent 上期为 0
  - 测试：本期 100 分钟，上期 0 分钟，验证 `deltaPercent` 不除零（返回 100 或特殊值）
  - 实现：守卫除零

- [x] **T-6.2.5** `getHeroMetrics()` goalPercent 计算
  - 测试：已读 150 分钟，目标 300 分钟，验证 `goalPercent = 50`
  - 实现：公式

- [x] **T-6.2.6** `getHeroMetrics()` goalPercent 超过 100
  - 测试：已读 500 分钟，目标 300 分钟，验证 `goalPercent = 100`（coerceIn）
  - 实现：`coerceIn(0, 100)`

### 6.3 周柱状图数据

- [x] **T-6.3.1** `getWeeklyChartData()` 本周/上周对比
  - 测试：Mock 两周数据，验证 `WeekChartData` 包含 7 天的双柱数据
  - 实现：`getWeeklyChartData()` 方法

### 6.4 24 小时数据

- [x] **T-6.4.1** `getHourlyData()` 返回 24 元素列表
  - 测试：Mock DAO 返回 hour=9/10/11 数据，验证输出 24 元素列表，其余为 0
  - 实现：`getHourlyData()` 方法

- [x] **T-6.4.2** `getHourlyData()` 空数据
  - 测试：Mock DAO 返回空，验证输出全 0 列表
  - 实现：默认值填充

### 6.5 维度分布

- [x] **T-6.5.1** `getDistribution(AUTHOR)` 按作者聚合
  - 测试：Mock `getAllBooksWithDuration()` 返回不同作者的书籍，验证按作者分组的 `DistributionItem` 列表
  - 实现：`getDistribution()` 方法

- [x] **T-6.5.2** `getDistribution(FORMAT)` 按格式聚合
  - 测试：TXT 3 本 + EPUB 2 本，验证分组正确
  - 实现：格式维度

- [x] **T-6.5.3** `getDistribution(GROUP)` 按分组聚合
  - 测试：验证分组维度正确
  - 实现：分组维度

- [x] **T-6.5.4** `getDistribution(WORDS)` 按字数区间聚合
  - 测试：验证字数区间分桶正确
  - 实现：字数维度

### 6.6 Top N 榜单

- [x] **T-6.6.1** `getTopN(DURATION)` 按时长排序
  - 测试：Mock 5 本书不同时长，验证返回按时长降序排列的 Top 5
  - 实现：`getTopN()` 方法

- [x] **T-6.6.2** `getTopN(BOOKMARKS)` 按书签数排序
  - 测试：Mock 书签数数据，验证排序正确
  - 实现：书签维度

- [x] **T-6.6.3** `getTopN(NOTES)` 按笔记数排序
  - 测试：Mock 笔记数数据，验证排序正确
  - 实现：笔记维度

- [x] **T-6.6.4** `getTopN(SPEED)` 按阅读速度排序
  - 测试：Mock 速度数据，验证排序正确
  - 实现：速度维度

- [x] **T-6.6.5** `getTopN()` limit 参数
  - 测试：`limit=3` 时仅返回 3 条
  - 实现：LIMIT 约束

### 6.7 阅读状态分布

- [x] **T-6.7.1** `getReadingStatusDistribution()` 各状态计数
  - 测试：Mock 3 在读 + 2 已读完 + 1 暂停，验证 `StatusItem` 列表正确
  - 实现：`getReadingStatusDistribution()` 方法

### 6.8 连续活跃天数

- [x] **T-6.8.1** `getLongestStreak()` 连续天数
  - 测试：Mock dateKeys=[20260101, 20260102, 20260103, 20260105, 20260106]，验证最长连续 = 3
  - 实现：应用层 streak 算法

- [x] **T-6.8.2** `getLongestStreak()` 空数据
  - 测试：空 dateKeys，验证返回 0
  - 实现：守卫

- [x] **T-6.8.3** `getLongestStreak()` 全部连续
  - 测试：7 天连续，验证返回 7
  - 实现：边界

- [x] **T-6.8.4** `getCurrentStreak()` 从今天反向遍历
  - 测试：今天有记录 + 昨天有 + 前天无，验证返回 2
  - 实现：`getCurrentStreak()` 方法

- [x] **T-6.8.5** `getCurrentStreak()` 今天无记录
  - 测试：今天无记录，验证返回 0
  - 实现：守卫

### 6.9 目标派生

- [x] **T-6.9.1** `getDailyNeededMinutes()` 正常计算
  - 测试：目标 300 分钟，已读 100 分钟，剩余 10 天，验证返回 20
  - 实现：公式

- [x] **T-6.9.2** `getDailyNeededMinutes()` 已达标
  - 测试：已读 350 分钟 > 目标 300 分钟，验证返回 0
  - 实现：`coerceAtLeast(0)`

- [x] **T-6.9.3** `getDailyNeededMinutes()` 剩余天数 <= 0
  - 测试：`remainingDays = 0`，验证返回 0
  - 实现：守卫

### 6.10 阅读速度趋势

- [x] **T-6.10.1** `getSpeedTrend()` 本周更快
  - 测试：本周 200 WPM，上周 150 WPM，验证返回 `UP`
  - 实现：比较逻辑

- [x] **T-6.10.2** `getSpeedTrend()` 本周更慢
  - 测试：本周 100 WPM，上周 150 WPM，验证返回 `DOWN`
  - 实现：比较逻辑

- [x] **T-6.10.3** `getSpeedTrend()` 相同速度
  - 测试：两周相同，验证返回 `FLAT`
  - 实现：比较逻辑

---

## Phase 7：StatsViewModel

**测试文件**：`app/src/test/java/com/shuli/reader/feature/stats/StatsViewModelTest.kt`

### 7.1 初始状态

- [x] **T-7.1.1** 初始状态正确
  - 测试：初始 `granularity = YEAR`，`currentDate = today`，`hasAnyData = false`
  - 实现：`StatsViewModel` 构造函数

### 7.2 粒度切换

- [x] **T-7.2.1** 切换到 DAY 粒度
  - 测试：`setGranularity(DAY)` → `uiState.navigation.granularity == DAY`
  - 实现：`setGranularity()` 方法

- [x] **T-7.2.2** 切换粒度触发数据重新加载
  - 测试：切换粒度后，`dateKeyRange` 更新，热力图数据重新查询
  - 实现：`flatMapLatest` 重新订阅

- [x] **T-7.2.3** 切换到 WEEK / MONTH / YEAR
  - 测试：各粒度切换后状态正确
  - 实现：同上

### 7.3 日期导航

- [x] **T-7.3.1** `goNext()` 日期前进
  - 测试：DAY 粒度下 goNext，`currentDate` 加 1 天
  - 实现：`goNext()` 方法

- [x] **T-7.3.2** `goPrev()` 日期后退
  - 测试：DAY 粒度下 goPrev，`currentDate` 减 1 天
  - 实现：`goPrev()` 方法

- [x] **T-7.3.3** `canGoNext` 边界
  - 测试：当前日期为 today 时 `canGoNext = false`
  - 实现：状态计算

### 7.4 数据流组合

- [x] **T-7.4.1** 粒度变化 → `dateKeyRange` 变化
  - 测试：YEAR → DAY，验证 `dateKeyRange` 从全年变为单日
  - 实现：`combine(granularity, currentDate)`

- [x] **T-7.4.2** `dateKeyRange.flatMapLatest` 取消旧订阅
  - 测试：快速切换粒度 3 次，验证仅最后一次的数据被收集
  - 实现：`flatMapLatest` 语义

- [x] **T-7.4.3** `hasAnyData` 标志
  - 测试：有 session 数据时为 true，无数据时为 false
  - 实现：状态派生

### 7.5 热力图状态

- [x] **T-7.5.1** 热力图数据正确映射到 `StatsHeatmapState`
  - 测试：Mock Repository 返回数据，验证 `uiState.heatmap.heatmapData` 非空
  - 实现：状态组装

### 7.6 Hero 状态

- [x] **T-7.6.1** Hero 指标正确映射到 `StatsHeroState`
  - 测试：Mock Repository 返回 HeroMetrics，验证 `uiState.hero` 各字段
  - 实现：状态组装

### 7.7 Top N 状态

- [x] **T-7.7.1** 切换排序维度
  - 测试：`setTopNSort(BOOKMARKS)` → `uiState.topN.sort == BOOKMARKS`
  - 实现：排序切换

---

## Phase 8：UI 组件

### 8.1 StatsScreen 骨架

**测试文件**：`app/src/androidTest/java/com/shuli/reader/ui/stats/StatsScreenTest.kt`

- [x] **T-8.1.1** 页面渲染不崩溃
  - 测试：传入空 `StatsUiState`，`StatsScreen` Composable 渲染成功
  - 实现：`StatsScreen.kt` 骨架

- [x] **T-8.1.2** 空数据状态显示 EmptyStatsState
  - 测试：`hasAnyData = false` 时，显示空状态提示文案
  - 实现：`EmptyStatsState` 组件

### 8.2 GranularitySelector

**测试文件**：`app/src/androidTest/java/com/shuli/reader/ui/stats/GranularitySelectorTest.kt`

- [x] **T-8.2.1** 渲染 4 个 Pill
  - 测试：显示 "日"/"周"/"月"/"年" 四个选项
  - 实现：`GranularitySelector.kt`

- [x] **T-8.2.2** 点击切换回调
  - 测试：点击 "周" Pill，`onGranularityChange(WEEK)` 被调用
  - 实现：点击事件

- [x] **T-8.2.3** 选中状态高亮
  - 测试：当前 `YEAR` 时，"年" Pill 显示选中样式
  - 实现：选中态

### 8.3 DateNavigator

**测试文件**：`app/src/androidTest/java/com/shuli/reader/ui/stats/DateNavigatorTest.kt`

- [x] **T-8.3.1** 显示日期文本
  - 测试：传入 `YEAR + 2026-06-09`，显示 "2026年"
  - 实现：`DateNavigator.kt`

- [x] **T-8.3.2** 前进箭头回调
  - 测试：点击右箭头，`onNext()` 被调用
  - 实现：箭头点击

- [x] **T-8.3.3** 后退箭头回调
  - 测试：点击左箭头，`onPrev()` 被调用
  - 实现：箭头点击

- [x] **T-8.3.4** `canGoNext = false` 时前进箭头禁用
  - 测试：前进箭头不可点击
  - 实现：`enabled` 状态

### 8.4 HeroSection

**测试文件**：`app/src/androidTest/java/com/shuli/reader/ui/stats/HeroSectionTest.kt`

- [x] **T-8.4.1** 显示大数字（总时长）
  - 测试：`totalMinutes = 120`，显示 "2h"
  - 实现：`HeroSection.kt`

- [x] **T-8.4.2** 显示副指标（活跃天/连读/日均）
  - 测试：验证 `activeDays`, `currentStreak`, `dailyAvg` 等文本可见
  - 实现：副指标网格

- [x] **T-8.4.3** 目标环渲染
  - 测试：`goalPercent = 50` 时，Canvas 绘制半圆环
  - 实现：`Canvas` 环形进度

- [x] **T-8.4.4** `dailyNeededHint` 显示
  - 测试：`dailyNeededMinutes = 20` 时，显示 "每天约需 20 分钟"
  - 实现：提示文本

- [x] **T-8.4.5** 零值显示 "--"
  - 测试：`totalMinutes = 0` 时，大数字显示 "--"
  - 实现：`StatsFormatter.zeroOrNull()`

### 8.5 CalendarHeatmap — 年视图

**测试文件**：`app/src/androidTest/java/com/shuli/reader/ui/stats/CalendarHeatmapTest.kt`

- [x] **T-8.5.1** 年视图渲染 365 格
  - 测试：传入全年数据，Canvas 绘制 ~53 列 × 7 行
  - 实现：`YearCalendarHeatmap.kt`

- [x] **T-8.5.2** 年视图自动滚动到当前周
  - 测试：渲染后 `scrollState.value` 接近当前周位置
  - 实现：`LaunchedEffect` + `scrollTo()`

- [x] **T-8.5.3** today 格高亮（accent 描边）
  - 测试：传入含 today 的数据，Canvas 绘制中包含 accent 色描边
  - 实现：today 格特殊绘制

### 8.6 CalendarHeatmap — 月视图

- [x] **T-8.6.1** 月视图渲染 28-31 格
  - 测试：传入 6 月数据（30 天），Canvas 绘制 30 格
  - 实现：`MonthCalendarHeatmap.kt`

- [x] **T-8.6.2** 月视图格子大小 18-20dp
  - 测试：验证 `cellSize` 参数默认为 20.dp
  - 实现：参数定义

- [x] **T-8.6.3** 月视图 4 格汇总行
  - 测试：验证热力图下方显示时长/活跃天/连读/日均
  - 实现：汇总行

### 8.7 HourlyHeatmap

**测试文件**：`app/src/androidTest/java/com/shuli/reader/ui/stats/HourlyHeatmapTest.kt`

- [x] **T-8.7.1** 渲染 3 行 × 8 列网格
  - 测试：传入 24 元素列表，渲染 24 格
  - 实现：`HourlyHeatmap.kt`

- [x] **T-8.7.2** 小时轴标注（0/8/16）
  - 测试：验证左侧行标签显示 "0"/"8"/"16"
  - 实现：行标签

- [x] **T-8.7.3** 峰值格标注
  - 测试：hour=20 为峰值时，该格显示 "20"
  - 实现：峰值标注

### 8.8 WeeklyBarChart

**测试文件**：`app/src/androidTest/java/com/shuli/reader/ui/stats/WeeklyBarChartTest.kt`

- [x] **T-8.8.1** 渲染双柱（本周/上周）
  - 测试：传入 WeekChartData，Canvas 绘制 7 组双柱
  - 实现：`WeeklyBarChart.kt`

- [x] **T-8.8.2** 首次可见触发动画
  - 测试：首次组合时动画触发，后续重组不重播
  - 实现：`LaunchedEffect` 首次可见

### 8.9 DistributionChart

**测试文件**：`app/src/androidTest/java/com/shuli/reader/ui/stats/DistributionChartTest.kt`

- [x] **T-8.9.1** 渲染环形图
  - 测试：传入分布数据，Canvas 绘制 `drawArc` 扇区
  - 实现：`DistributionChart.kt`

- [x] **T-8.9.2** SegControl 切换维度
  - 测试：点击 "按格式"，回调 `onDimensionChange(FORMAT)` 触发
  - 实现：维度切换

- [x] **T-8.9.3** TXT/EPUB 语义色
  - 测试：格式维度下 TXT 使用 `#D08770`，EPUB 使用 `#5E81AC`
  - 实现：语义色映射

### 8.10 TopNList

**测试文件**：`app/src/androidTest/java/com/shuli/reader/ui/stats/TopNListTest.kt`

- [x] **T-8.10.1** 渲染 Top 5 列表
  - 测试：传入 5 本书数据，LazyColumn 显示 5 行
  - 实现：`TopNList.kt`

- [x] **T-8.10.2** SegControl 切换排序
  - 测试：点击 "书签"，回调 `onSortChange(BOOKMARKS)` 触发
  - 实现：排序切换

- [x] **T-8.10.3** 行可点击
  - 测试：点击某行，`onBookClick(bookId)` 被调用
  - 实现：行点击回调

### 8.11 ReadingStatusChart

**测试文件**：`app/src/androidTest/java/com/shuli/reader/ui/stats/ReadingStatusChartTest.kt`

- [x] **T-8.11.1** 渲染各状态条目
  - 测试：传入 4 种状态数据，显示 "在读"/"已读完"/"暂停"/"未读"
  - 实现：`ReadingStatusChart.kt`

- [x] **T-8.11.2** 水平进度条宽度比例
  - 测试：50% 的状态条宽度为一半
  - 实现：进度条

### 8.12 ReadingTimeline

**测试文件**：`app/src/androidTest/java/com/shuli/reader/ui/stats/ReadingTimelineTest.kt`

- [x] **T-8.12.1** 渲染会话列表
  - 测试：传入 3 条 session，显示 3 个条目
  - 实现：`ReadingTimeline.kt`

- [x] **T-8.12.2** 同书相邻会话合并（< 30 分钟）
  - 测试：bookId=1, 两条间隔 10 分钟，合并为 1 条，章节显示 "第3-5章"
  - 实现：合并逻辑（应用层）

- [x] **T-8.12.3** 跨书不合并
  - 测试：bookId=1 和 bookId=2 间隔 5 分钟，不合并
  - 实现：`book_id` 分组

- [x] **T-8.12.4** 30 分钟阈值不合并
  - 测试：bookId=1, 两条间隔 35 分钟，不合并
  - 实现：阈值判断

- [x] **T-8.12.5** 合并后 durationSeconds 为 SUM
  - 测试：300s + 600s 合并后为 900s
  - 实现：SUM 聚合

- [x] **T-8.12.6** 竖线装饰
  - 测试：验证竖线装饰渲染正确
  - 实现：竖线 UI

### 8.13 EmptyStatsState

**测试文件**：`app/src/androidTest/java/com/shuli/reader/ui/stats/EmptyStatsStateTest.kt`

- [x] **T-8.13.1** 显示引导文案
  - 测试：显示 "暂无阅读记录，打开一本书开始阅读吧"
  - 实现：`EmptyStatsState.kt`

### 8.14 heatLevel 色阶算法

**测试文件**：`app/src/test/java/com/shuli/reader/feature/stats/HeatLevelTest.kt`

- [x] **T-8.14.1** 0 分钟返回 L0
  - 测试：`heatLevel(0, 100)` → `L0`
  - 实现：`heatLevel()` 函数

- [x] **T-8.14.2** sqrt 变换低值区分
  - 测试：`heatLevel(1, 100)` → `L1`（sqrt(0.01) = 0.1 < 0.17）
  - 实现：sqrt 映射

- [x] **T-8.14.3** 中等值返回 L3
  - 测试：`heatLevel(25, 100)` → `L3`（sqrt(0.25) = 0.5）
  - 实现：区间映射

- [x] **T-8.14.4** 最大值返回 L5
  - 测试：`heatLevel(100, 100)` → `L5`（sqrt(1.0) = 1.0 >= 0.67）
  - 实现：最高级

- [x] **T-8.14.5** maxMinutes 为 0 返回 L1
  - 测试：`heatLevel(10, 0)` → `L1`（守卫，避免除零）
  - 实现：守卫

- [x] **T-8.14.6** Nord 色系双模式
  - 测试：`L2.color(isDark=false)` = `#88C0D0`，`L2.color(isDark=true)` = `#5E81AC`
  - 实现：色阶映射表

---

## Phase 9：i18n

### 9.1 StatsStrings 接口

**测试文件**：`app/src/test/java/com/shuli/reader/core/i18n/StatsStringsTest.kt`

- [x] **T-9.1.1** `StatsStrings` 接口字段完整
  - 测试：编译通过，所有 ~40 个字段可访问
  - 实现：`StatsStrings` 接口定义

### 9.2 三语实现

- [x] **T-9.2.1** `ZhHansStats` 简体中文
  - 测试：`statsTitle == "阅读统计"`, `granularityDay == "日"`
  - 实现：`ZhHansStats` object

- [x] **T-9.2.2** `ZhHantStats` 繁体中文
  - 测试：`statsTitle == "閱讀統計"`, `granularityDay == "日"`
  - 实现：`ZhHantStats` object

- [x] **T-9.2.3** `EnStats` 英文
  - 测试：`statsTitle == "Reading Stats"`, `granularityDay == "Day"`
  - 实现：`EnStats` object

### 9.3 AppStrings 注册

- [x] **T-9.3.1** `AppStrings` 新增 `stats` 字段
  - 测试：`AppStrings.ZhHans.stats` 可访问且类型为 `StatsStrings`
  - 实现：`AppStrings` sealed interface 新增 `stats` 属性

- [x] **T-9.3.2** 三个语言变体均注册
  - 测试：`ZhHans.stats`, `ZhHant.stats`, `En.stats` 均可访问
  - 实现：三个 data object 新增 override

### 9.4 函数式字符串

- [x] **T-9.4.1** `goalTitle` 函数签名
  - 测试：`goalTitle("2026", 500)` 返回含 "2026" 和 "500" 的字符串
  - 实现：`(String, Int) -> String`

- [x] **T-9.4.2** `currentStreak` 函数
  - 测试：`currentStreak(5)` 返回含 "5" 的字符串
  - 实现：`(Int) -> String`

- [x] **T-9.4.3** `dailyNeededHint` 函数
  - 测试：`dailyNeededHint(20)` 返回含 "20" 的字符串
  - 实现：`(Long) -> String`

---

## Phase 10：导航集成 + DI 接线

### 10.1 ActiveScreen 扩展

**测试文件**：`app/src/test/java/com/shuli/reader/NavigationTest.kt`

- [x] **T-10.1.1** `ActiveScreen.Stats` 存在
  - 测试：`ActiveScreen.Stats` 可实例化，类型为 `ActiveScreen`
  - 实现：`data object Stats : ActiveScreen()`

### 10.2 BookshelfScreen 签名改造

- [x] **T-10.2.1** `BookshelfScreen` 接收 `onNavigateToStats` 参数
  - 测试：编译通过，参数类型为 `() -> Unit`
  - 实现：新增参数

- [x] **T-10.2.2** 统计图标按钮点击调用 `onNavigateToStats`
  - 测试：点击统计按钮，`onNavigateToStats()` 被调用
  - 实现：按钮回调

### 10.3 MainActivity 导航

- [x] **T-10.3.1** `ActiveScreen.Stats` 分支渲染 `StatsScreen`
  - 测试：`currentScreen = Stats` 时显示 `StatsScreen`
  - 实现：`when` 新增分支

- [x] **T-10.3.2** 书架 → 统计页完整链路
  - 测试：书架顶栏统计按钮 → `ActiveScreen.Stats` → `StatsScreen` 渲染
  - 实现：链路接线

### 10.4 DI 容器

**测试文件**：`app/src/test/java/com/shuli/reader/DiContainerTest.kt`

- [x] **T-10.4.1** `ShuLiAppContainer.statsRepository` 可访问
  - 测试：`container.statsRepository` 不为 null，类型正确
  - 实现：lazy 属性

- [x] **T-10.4.2** `ShuLiDatabase.readingSessionDao()` 可访问
  - 测试：`database.readingSessionDao()` 返回有效 DAO
  - 实现：abstract 方法

---

## Phase 11：联调 + 打磨

### 11.1 主题适配

- [x] **T-11.1.1** 亮色模式全页面渲染
  - 测试：`isSystemInDarkTheme = false`，所有组件正确渲染
  - 实现：亮色色值

- [x] **T-11.1.2** 暗色模式全页面渲染
  - 测试：`isSystemInDarkTheme = true`，所有组件正确渲染，Nord Frost 色阶冰霜感
  - 实现：暗色色值

### 11.2 空数据状态

- [x] **T-11.2.1** Hero 区零值显示 "--"
  - 测试：无数据时大数字显示 "--"
  - 实现：`zeroOrNull()`

- [x] **T-11.2.2** 热力图全灰
  - 测试：无数据时所有格子为 L0 灰色
  - 实现：默认 L0

- [x] **T-11.2.3** 时间轴空状态
  - 测试：显示引导文案
  - 实现：空状态组件

### 11.3 性能验证

- [x] **T-11.3.1** 365 天热力图 Canvas 绘制 < 2ms
  - 测试：Benchmark 365 格 `drawRoundRect` 耗时
  - 实现：Canvas 优化

- [x] **T-11.3.2** 全年 `getDailyTotals()` 查询 < 1ms
  - 测试：3000 行数据，GROUP BY 查询耗时
  - 实现：索引优化

- [x] **T-11.3.3** 年视图横向滚动流畅
  - 测试：滚动 FPS >= 55
  - 实现：`horizontalScroll` 优化

### 11.4 无障碍

- [x] **T-11.4.1** 热力图 contentDescription
  - 测试：TalkBack 朗读 "2026年6月9日，阅读45分钟"
  - 实现：`contentDescription`

- [x] **T-11.4.2** Top N 列表项 contentDescription
  - 测试：TalkBack 朗读 "书名，阅读时长2小时"
  - 实现：`contentDescription`

### 11.5 横屏适配

- [x] **T-11.5.1** 横屏布局不溢出
  - 测试：横屏配置下所有组件正确布局
  - 实现：响应式布局

---

## 附录 A：测试工具与辅助类

### A.1 测试数据工厂

**文件**：`app/src/test/java/com/shuli/reader/feature/stats/StatsTestFixtures.kt`

- [x] **T-A.1.1** `createReadingSession()` 工厂方法
  - 提供默认值的 `ReadingSessionEntity` 创建方法，支持参数覆盖

- [x] **T-A.1.2** `createDailyTotalTuple()` 工厂方法
  - 快速创建 `DailyTotalTuple` 测试数据

- [x] **T-A.1.3** `createBookWithDuration()` 工厂方法
  - 快速创建 `BookWithDurationTuple` 测试数据

- [x] **T-A.1.4** `MockReadingSessionDao` 辅助
  - 预设常用 mock 行为的 DAO mock

### A.2 androidTest 数据库辅助

- [x] **T-A.2.1** 扩展 `TestDatabaseFactory` 支持 `readingSessionDao()`
  - 确保内存数据库包含新表

---

## 附录 B：任务依赖关系

```
Phase 1（数据层）
  ├─ 1.1~1.3 Entity + DAO     → 无前置依赖
  ├─ 1.4~1.9 DAO 查询         → 依赖 1.1（Entity 定义）
  ├─ 1.10 Tuple               → 与 1.4~1.9 并行
  ├─ 1.11~1.13 旧 Entity 改造 → 依赖 1.1（新 Entity 就位后再删旧字段）
  ├─ 1.14 BookDao 扩展        → 依赖 1.1（reading_session 表存在）
  └─ 1.15 Database 升级       → 依赖 1.1~1.14 全部完成

Phase 2（写入路径）→ 依赖 Phase 1 完成
  ├─ 2.1~2.3 flushChapterTime → 依赖 1.15（Database 含新 DAO）
  ├─ 2.4~2.5 可见性/方法      → 依赖 2.1
  └─ 2.6 releaseResources     → 依赖 2.1~2.2

Phase 3（生命周期）→ 依赖 Phase 2 完成
  └─ 3.1~3.2 pause/resume    → 依赖 2.4~2.5（internal 可见性）

Phase 4（消费者迁移）→ 依赖 Phase 1 + Phase 2 完成
  ├─ 4.1 BookshelfViewModel   → 依赖 1.7（getBookTotals）
  ├─ 4.2 ChapterList          → 依赖 1.8（getChapterTotals）
  ├─ 4.3 Repository           → 依赖 1.7
  └─ 4.5 Backup               → 依赖 1.15（新表存在）

Phase 5（工具类）→ 无前置依赖，可与 Phase 2~4 并行
  ├─ 5.1 StatsFormatter       → 独立
  ├─ 5.2 StatsGranularity     → 独立
  ├─ 5.3 StatsDateNavigator   → 独立
  └─ 5.4 DateKeyRange         → 独立

Phase 6（StatsRepository）→ 依赖 Phase 1（DAO）+ Phase 5（工具类）
  └─ 6.1~6.10 全部方法        → 依赖 DAO + Formatter

Phase 7（StatsViewModel）→ 依赖 Phase 6（Repository）+ Phase 5（Granularity）
  └─ 7.1~7.7 全部状态         → 依赖 Repository

Phase 8（UI 组件）→ 依赖 Phase 7（ViewModel）+ Phase 5（Formatter）
  ├─ 8.1~8.13 组件            → 依赖 ViewModel + Formatter
  └─ 8.14 heatLevel           → 独立，可与 Phase 5 并行

Phase 9（i18n）→ 无前置依赖，最早可并行
  └─ 9.1~9.4                  → 独立

Phase 10（导航 + DI）→ 依赖 Phase 7 + Phase 8 + Phase 9
  └─ 10.1~10.4                → 依赖 StatsScreen + ViewModel + i18n

Phase 11（联调）→ 依赖 Phase 10 全部完成
  └─ 11.1~11.5                → 全链路集成
```

---

## 附录 C：任务统计

| Phase | 任务数 | 预估工期 |
|---|---|---|
| Phase 1：数据层 | 32 | 6-8 天 |
| Phase 2：写入路径 | 12 | 2-3 天 |
| Phase 3：生命周期 | 4 | 0.5 天 |
| Phase 4：消费者迁移 | 9 | 2-3 天 |
| Phase 5：工具类 | 26 | 2 天 |
| Phase 6：StatsRepository | 27 | 2 天 |
| Phase 7：StatsViewModel | 13 | 1-2 天 |
| Phase 8：UI 组件 | 35 | 10-12 天 |
| Phase 9：i18n | 9 | 1 天 |
| Phase 10：导航 + DI | 7 | 1 天 |
| Phase 11：联调 | 10 | 2 天 |
| **总计** | **184** | **~30-35 天** |
