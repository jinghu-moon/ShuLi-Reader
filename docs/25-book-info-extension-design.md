# 书籍信息扩展设计方案

> 编写时间：2026-06-05
> 范围：`BookEntity` 信息扩展 · 用户可主动标注的元数据
> 原则：**三重价值检验**（查看 · 引用 · 编辑）+ 纯离线 + 用户主控 + 性能优先

---

## 1. 背景与设计原则

### 1.1 问题陈述

当前 `BookEntity` 的信息维度**严重不足**：

| 已有字段 | 缺失维度 |
|---|---|
| 书名 / 作者 / 字数 / 章节数 | ❌ 用户对书的组织（标签） |
| 文件格式 / 文件大小 | ❌ 用户定义的阅读状态（显式） |
| 导入时间 / 最后阅读时间 | ❌ 重读次数（二刷/三刷） |
| 阅读进度 / 阅读时长 | |
| 书架分组 / 固定槽位 | |

**核心矛盾**：现有字段全部是"系统产生"或"内容固有"，**没有任何"用户主动标注"的信息**。这导致：

1. 用户在多本书之间**无法快速识别偏好**（无法按标签筛选）
2. 用户的阅读状态**边界模糊**（进度 50% 是"在读"还是"暂停"？）
3. 用户无法表达"二刷"（重读次数没有记录）

### 1.2 设计原则

#### 三重价值检验法

任何新增信息字段必须同时通过以下三重检验。未通过者不得混入本需求；如确认为必要，必须单独补设计、排期和验收标准，不允许以笼统承诺替代。

| 维度 | 问题 | 失败后果 |
|---|---|---|
| **查看** | 用户会在哪里看到它？ | 数据无人看 = 装饰字段 |
| **引用** | 它能驱动什么功能？| 不能筛选/排序/搜索/统计 = 数据孤岛 |
| **编辑** | 用户如何修改它？ | 无编辑入口 = 数据迅速腐烂 |

#### 其他原则

- **纯离线**：所有数据仅存本地，不依赖任何服务器
- **用户主控**：所有信息都由用户**主动标注或确认**，系统不得自动写入分类/标签；智能建议只能作为用户确认后的输入辅助
- **轻量优先**：每个信息的 UI 控件尽量简洁，避免"为功能而功能"
- **可逆**：所有标注都可以清空/修改，不强制
- **历史保留**：任何状态变化不丢失历史数据（如"读完过"是重要信息）

#### 性能优先的重构决策

本项目处于 pre-release 阶段，允许破坏性重构，但不以"破坏性"本身为目标。所有增量更新与破坏性重构的选择按以下规则决策：

| 对比维度 | 决策规则 |
|---|---|
| **运行时性能** | 谁能减少组合分支、空值判断、重复查询、全量刷新或不必要的数据加载，就选谁 |
| **数据链路性能** | 保留现有高性能分页投影 `BookShelfRow`，不为了重构把书架列表退回全量 `BookEntity` 查询 |
| **编译期安全** | 运行时性能相同或差异可忽略时，选择 API 更严格、调用路径更短的方案 |
| **迁移成本** | 破坏性重构只用于 UI/API 边界；数据库、同步、备份协议采用可迁移的增量升级，避免丢失用户数据 |

**本方案结论**：

- 详情页组件：采用**破坏性重构**，删除兼容旧只读详情页的分支，统一为严格 API 的 `BookDetailsSheet`。
- 数据库和书架数据链路：采用**增量扩展**，继续复用 `ShuLiDatabase`、`BookShelfRow`、分页查询和现有 Repository。
- 标签系统：按 P1/P2/P3 分阶段交付。P1 做最小可用闭环；P2 必须补齐标签管理、组合筛选、标签统计；P3 补齐智能建议、预设标签包和导出增强。

### 1.3 方案总览

通过三重检验的信息字段共 **2 个**，但标签能力必须按阶段完整落地：

| 能力 | 查看 | 引用 | 编辑 | 优先级 | 工作量 |
|---|---|---|---|:---:|--:|
| **阅读状态**（5 种）+ **重读次数** | 书架卡片 · 详情页 · 统计 | 筛选 · 排序 · 统计 | 详情页 · 长按菜单 | P0 | 2-3 天 |
| **标签基础能力**（自由多标签）| 详情页 · 书架 | 单标签筛选 · 搜索 | 标签输入器 | P1 | 3-5 天 |
| **标签管理与组合能力** | 标签管理页 · 标签统计 | AND/OR 组合筛选 · 标签云 · Top N | 重命名 · 删除 · 合并 | P2 | 4-6 天 |
| **标签智能与导出增强** | 建议面板 · 导出报表 | 智能建议 · Markdown/CSV 导出 | 用户确认建议 · 预设标签包导入 | P3 | 3-5 天 |

**本需求外部约束**：

以下条目不属于本次书籍信息扩展路线；若用户重新启用，必须新建独立设计文档，不能混入本方案造成漏项。

| 条目 | 处理方式 |
|---|---|
| 个人评分（5 星）| 用户已否决；需重新确认后单独设计 |
| 个人短评 | 用户已否决；需重新确认后单独设计 |
| 系列关联 | 用户已否决；需重新确认后单独设计 |
| 书籍类型（硬编码）| 由自由标签替代；如要固定分类，需新增分类体系设计 |
| AI 自动写入分类/摘要 | 违反用户主控；P3 仅允许"建议 + 用户确认" |
| 人物表/关系图 | 需 NLP 与复杂实体关系模型；需独立规格 |
| 社交共享/推荐系统 | 违背纯离线边界；需独立产品决策 |

---

## 2. 阅读状态（5 种显式状态 + 重读次数）

### 2.1 为什么必要

当前 `BookEntity` 的"阅读状态"是**隐式**的（靠 `progress` 字段推断）：

| 进度 | 推断状态 | 问题 |
|---|---|---|
| 0% | 未读 | 无法区分"想读"和"还没开始" |
| 1-99% | 在读 | 无法区分"在读"和"暂停"/"弃读" |
| 100% | 已读完 | 无法表达"中途放弃"或"二刷" |

**核心矛盾**：用户心理上的"阅读状态"是**显式的、主观的**，但系统只能靠进度推断。

### 2.2 5 种状态定义

| 状态 | 英文 | 颜色 | 默认触发 | 语义 |
|---|---|---|---|---|
| **想读** | `WANT_TO_READ` | 灰色 `#9C9082` | 手动标记 | "加入待读清单" |
| **在读** | `READING` | 深棕 `#8B5E3C` | 打开阅读时自动 | "正在读" |
| **暂停** | `PAUSED` | 黄色 `#9A6500` | 手动标记 | "暂时放下，稍后继续" |
| **已读完** | `FINISHED` | 绿色 `#2D7A52` | 进度 100% 自动 | "完整读过" |
| **弃读** | `ABANDONED` | 红色 `#9B3525` | 手动标记 | "不再继续" |

### 2.3 状态迁移规则

```
       ┌────────────────────────────────────┐
       │                                    │
       ▼                                    │
  [想读] ──打开──▶ [在读] ──100%──▶ [已读完] │
       ▲            │  │                    │
       │            │  └──手动──▶ [暂停] ───┘
       │            │              │
       │            └──手动──▶ [弃读]
       │                          │
       └──── 手动 ────────────────┘
```

**关键规则**：
1. **任意状态 → 任意状态**：用户可以手动切换（不强制流程）
2. **自动触发**：
   - 导入新书：默认 `WANT_TO_READ`
   - 首次打开阅读：自动切到 `READING`
   - 进度达到 100%：自动切到 `FINISHED`（可撤销）
3. **进度回退**：用户可以从 `FINISHED` 重新打开阅读，状态自动切回 `READING`

### 2.4 关键决策：状态改回 READING 时的 progress 处理

**场景**：用户把一本 `readingProgress >= 0.99f`（已读完）的书，手动改回 `READING`（打算二刷），如何处理 `readingProgress` 字段？

#### 三种方案对比

| 方案 | 处理 | 优点 | 缺点 |
|---|---|---|---|
| **A. 不联动** | progress 保持 100% | 保留历史进度 | 状态与进度语义不一致（"在读 100%"很奇怪）|
| **B. 自动重置为 0** | progress 重置为 0% | 状态与进度一致 | **丢失"读完过"的历史信息** |
| **C. 不联动 + readCount 计数器（采用）** | progress 保持 100% + readCount++ | 既保留历史，又记录"第几次读" | 新增一个字段 |

#### 采用方案 C 的设计

**新增字段**：
```kotlin
@ColumnInfo(name = "read_count", defaultValue = "1")
val readCount: Int = 1  // 阅读次数（默认 1）
```

**触发规则**：
| 状态迁移 | readCount 变化 | 说明 |
|---|---|---|
| 任意 → `FINISHED` | 不变 | 首次完成或当前次数完成 |
| `FINISHED` → `READING` | `readCount++` | 触发二刷 |
| `FINISHED` → `WANT_TO_READ` | `readCount++` | 准备重读 |
| 其他状态迁移 | 不变 | 普通切换 |

**UI 展示**：
| 位置 | 展示方式 |
|---|---|
| **详情页** | `● 在读 · 第 2 次阅读`（`readCount > 1` 时显示）|
| **书架卡片** | `● 在读 🔄2`（小图标 + 次数，`readCount > 1` 时显示）|
| **统计页** | P2 必做："二刷书籍"榜单 |

**理由**：
1. **不丢历史**：用户"读完过"是重要信息，不应被重置
2. **二刷是常见场景**：网络小说读者常会重温神作
3. **统计价值**：`readCount > 1` 的书是用户的"真爱"，可做"最爱重读书"榜单

### 2.5 查看（UI 展示）

| 位置 | 展示方式 |
|---|---|
| **书架卡片** | 左上角 7px 状态点（颜色区分）+ 重读次数（🔄N，readCount > 1 时显示）|
| **书架列表** | 行首状态徽章（文字 + 颜色）|
| **详情页顶部** | 状态徽章（可点击切换）+ "第 N 次阅读"标签（readCount > 1 时显示）|
| **统计页** | "阅读状态分布"图（P0）+ "二刷书籍"榜单（P2）|
| **阅读页** | 不展示（避免干扰沉浸阅读）|

### 2.6 引用（驱动功能）

| 功能 | 用法 |
|---|---|
| **书架筛选** | 默认显示"想读/在读/暂停/已读完"，隐藏"弃读" |
| **书架排序** | 优先级：在读 > 暂停 > 想读 > 已读完 > 弃读 |
| **统计分布** | 5 种状态的数量/占比分布图 |
| **快捷操作** | 长按书籍 → 状态快捷菜单（5 个选项） |
| **二刷榜单**（P2）| 统计页显示"二刷书籍 Top N" |

### 2.7 编辑（UI 入口）

| 入口 | 交互 |
|---|---|
| **详情页顶部** | 点击状态徽章 → 下拉菜单（5 选项）|
| **书架卡片长按** | 弹出快捷菜单 → 状态（5 选项）|
| **批量操作** | 多选 → 顶部"批量改状态"按钮 |
| **100% 自动** | 进度达 100% → Toast "标记为已读完？[确认][撤销]" |

### 2.8 数据模型

```kotlin
// BookEntity 增量字段：数据库存 String，业务层用 ReadingStatus
@ColumnInfo(name = "reading_status", defaultValue = "'WANT_TO_READ'")
val readingStatus: String = ReadingStatus.WANT_TO_READ.name

@ColumnInfo(name = "read_count", defaultValue = "1")
val readCount: Int = 1

enum class ReadingStatus {
    WANT_TO_READ,    // 想读
    READING,         // 在读
    PAUSED,          // 暂停
    FINISHED,        // 已读完
    ABANDONED;       // 弃读

    companion object {
        fun fromDb(value: String?): ReadingStatus =
            entries.firstOrNull { it.name == value } ?: WANT_TO_READ
    }
}

fun BookEntity.transitionTo(
    newStatus: ReadingStatus,
    now: Long = System.currentTimeMillis(),
): BookEntity {
    val currentStatus = ReadingStatus.fromDb(readingStatus)
    val shouldIncrementReadCount = 
        currentStatus == ReadingStatus.FINISHED && 
        (newStatus == ReadingStatus.READING || newStatus == ReadingStatus.WANT_TO_READ)
    
    return this.copy(
        readingStatus = newStatus.name,
        readCount = if (shouldIncrementReadCount) readCount + 1 else readCount,
        isDirty = true,
        version = version + 1,
        updatedAt = now,
    )
}

// 当前工程数据库为 ShuLiDatabase version = 16，P0 从 16 升到 17
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            ALTER TABLE books 
            ADD COLUMN reading_status TEXT NOT NULL DEFAULT 'WANT_TO_READ'
        """)
        database.execSQL("""
            ALTER TABLE books 
            ADD COLUMN read_count INTEGER NOT NULL DEFAULT 1
        """)

        // readingProgress 是 0f..1f，不是 0..100
        database.execSQL("""
            UPDATE books SET reading_status = 'READING' 
            WHERE readingProgress > 0 AND readingProgress < 0.99
        """)
        database.execSQL("""
            UPDATE books SET reading_status = 'FINISHED' 
            WHERE readingProgress >= 0.99
        """)
    }
}
```

**必须同步扩展书架轻量投影**：

当前书架列表使用 `BookShelfRow` 分页投影，而不是直接加载完整 `BookEntity`。因此 P0 必须同时修改：

| 文件 | 必改点 |
|---|---|
| `core/database/entity/BookShelfRow.kt` | 新增 `readingStatus: String`、`readCount: Int` |
| `core/database/dao/BookDao.kt` | `getBookRowsPage()` 和 `searchBookRowsFtsPage()` 的 SELECT 增加 `readingStatus`、`readCount` |
| `feature/bookshelf/model/BookItem.kt` | 新增 `readingStatus: ReadingStatus`、`readCount: Int` |
| `BookEntity.toBookItem()` / `BookShelfRow.toBookItem()` | 统一把 DB 字符串映射为 `ReadingStatus.fromDb(...)` |

这条链路不能省略。否则数据库字段已存在，但书架卡片、详情页、筛选和排序都拿不到新数据。

### 2.9 状态迁移的完整逻辑

```kotlin
// BookDao：补同步查询，避免在 Repository 里 first() 长期订阅 Flow
@Query("SELECT * FROM books WHERE id = :id LIMIT 1")
suspend fun getBookByIdSync(id: Long): BookEntity?

@Update
suspend fun updateBook(book: BookEntity)

data class ReadingStatusUpdateResult(
    val previousStatus: ReadingStatus,
    val updatedBook: BookEntity,
)

// ReadingProgressRepository 或拆出的 BookMetadataRepository：集中处理状态迁移
suspend fun updateReadingStatus(
    bookId: Long,
    newStatus: ReadingStatus,
): ReadingStatusUpdateResult? {
    val book = bookDao.getBookByIdSync(bookId) ?: return null
    val previousStatus = ReadingStatus.fromDb(book.readingStatus)
    val updatedBook = book.transitionTo(newStatus)
    bookDao.updateBook(updatedBook)
    return ReadingStatusUpdateResult(
        previousStatus = previousStatus,
        updatedBook = updatedBook,
    )
}

// 首次打开：只把 WANT_TO_READ 自动切到 READING，尊重 PAUSED/ABANDONED 等用户显式状态
suspend fun markOpenedForReading(bookId: Long) {
    val book = bookDao.getBookByIdSync(bookId) ?: return
    if (ReadingStatus.fromDb(book.readingStatus) == ReadingStatus.WANT_TO_READ) {
        bookDao.updateBook(book.transitionTo(ReadingStatus.READING))
    }
}

// 保存进度时联动完成状态，避免 ViewModel 和 BookSessionManager 分散判断
suspend fun updateReadingPositionAndMaybeFinish(
    bookId: Long,
    byteOffset: Long,
    chapterTitle: String?,
    progress: Float,
) {
    bookDao.updateReadingPosition(
        bookId = bookId,
        byteOffset = byteOffset,
        chapterTitle = chapterTitle,
        progress = progress,
    )

    if (progress >= 0.99f) {
        val book = bookDao.getBookByIdSync(bookId) ?: return
        if (ReadingStatus.fromDb(book.readingStatus) != ReadingStatus.FINISHED) {
            bookDao.updateBook(book.transitionTo(ReadingStatus.FINISHED))
        }
    }
}
```

**接入点**：

| 现有位置 | 修改方式 |
|---|---|
| `BookshelfViewModel.onBookClick()` | 调用 `markOpenedForReading(bookId)` 后再导航 |
| `BookSessionManager.openBook()` | 打开阅读时调用 `markOpenedForReading(bookId)`，保证阅读页入口也生效 |
| `BookSessionManager.saveReadingProgress()` | 把 `updateReadingPosition()` 替换为 `updateReadingPositionAndMaybeFinish()` |
| `BookshelfViewModel.updateStatus()` / `ReaderViewModel.updateStatus()` | 只调用 Repository，不直接复制状态迁移规则 |

**性能理由**：状态迁移是一次单书更新，集中在 Repository 不增加额外列表查询；书架仍由 Room Flow 增量刷新对应投影行。相比在多个 UI 层做判断，集中逻辑减少重复查询和状态分支。

---

## 3. 标签系统（自由多标签）

### 3.1 为什么必要

- **最灵活的组织方式**：一本小说可以有多个标签（`#玄幻` `#蒸汽朋克` `#完结` `#神作`），这是任何固定分类都做不到的
- **多维筛选**：用户可组合多个标签筛选（"#玄幻 AND #完结"）
- **个人化**：标签由用户定义，反映用户**自己的分类逻辑**（而不是系统强制的分类）
- **统计基础**：标签 Top N、标签云属于 P2 必做能力；P1 先交付标签数据模型和单标签筛选，P2 在同一数据模型上补齐统计展示

### 3.2 设计规格

| 元素 | 规格 |
|---|---|
| 每本书标签上限 | 20 个 |
| 标签长度上限 | 20 字符 |
| 标签格式 | 纯文本（字母/数字/汉字/下划线，不含空格/特殊符号）|
| 标签颜色 | 自动分配（基于标签名哈希，6 种墨土色阶循环）|
| 全局标签上限 | 无（但 UI 上展示 Top 100 常用标签）|

### 3.3 数据模型

#### 3.3.1 新增两张表

```kotlin
// 标签表（全局唯一标签名）
@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "name", index = true)
    val name: String,  // 标签名（唯一）
    
    @ColumnInfo(name = "color_index")
    val colorIndex: Int,  // 颜色索引（0-5，自动分配）
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)

// 书籍-标签 关联表（多对多）
@Entity(
    tableName = "book_tag_cross_ref",
    primaryKeys = ["book_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE
        ),
    ],
    indices = [
        Index("book_id"),
        Index("tag_id"),
    ]
)
data class BookTagCrossRef(
    @ColumnInfo(name = "book_id") val bookId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis(),
)
```

#### 3.3.2 DAO 查询

```kotlin
@Dao
interface TagDao {
    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN book_tag_cross_ref r ON t.id = r.tag_id
        WHERE r.book_id = :bookId
        ORDER BY r.added_at ASC
    """)
    fun getTagsForBook(bookId: Long): Flow<List<TagEntity>>

    @Query("""
        SELECT t.*, COUNT(r.book_id) as usage_count 
        FROM tags t
        LEFT JOIN book_tag_cross_ref r ON t.id = r.tag_id
        GROUP BY t.id
        ORDER BY usage_count DESC
        LIMIT 100
    """)
    fun getAllTagsWithCount(): Flow<List<TagWithCount>>

    @Query("""
        SELECT t.*, COUNT(r.book_id) as usage_count
        FROM tags t
        LEFT JOIN book_tag_cross_ref r ON t.id = r.tag_id
        WHERE t.name LIKE :prefix || '%'
        GROUP BY t.id
        ORDER BY usage_count DESC, t.name ASC
        LIMIT 10
    """)
    suspend fun searchTagsByPrefix(prefix: String): List<TagWithCount>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTagToBook(crossRef: BookTagCrossRef)

    @Query("DELETE FROM book_tag_cross_ref WHERE book_id = :bookId AND tag_id = :tagId")
    suspend fun removeTagFromBook(bookId: Long, tagId: Long)
}
```

P1 仅提供单书标签增删。P2 必须补齐全局重命名、删除、合并标签接口，并同步设计批量更新的 `isDirty/version/updatedAt` 规则，避免 WebDAV 冲突时丢失远端变更。

### 3.4 查看（UI 展示）

| 位置 | 展示方式 |
|---|---|
| **详情页** | 作者下方标签流（横向滚动 chip）|
| **书架卡片** | P1 默认不显示标签；P2 支持紧凑显示前 1-2 个标签，并通过稳定高度避免卡片重排 |
| **标签云视图** | P2 必做，用于展示 Top 100 常用标签和使用频次 |
| **长按预览** | P2 必做，长按标签时展示该标签下的书籍数量与快捷筛选入口 |

### 3.5 引用（驱动功能）

| 功能 | 用法 |
|---|---|
| **书架筛选** | P1 支持单标签筛选；P2 必须支持 AND/OR 组合筛选 |
| **书架搜索** | 支持 `#标签名` 或 `tag:标签名` 精确匹配 |
| **统计分布** | P2 必做，显示标签 Top 10 与总标签数 |
| **标签云** | P2 必做，支持按使用次数排序和点击筛选 |
| **搜索语法** | P1 支持 `tag:玄幻` 单标签过滤；P2 支持 `tag:玄幻 AND tag:完结`、`tag:玄幻 OR tag:武侠` |

### 3.6 编辑（UI 入口）

#### 3.6.1 详情页标签输入器

```
┌──────────────────────────────────────┐
│ [玄幻] [蒸汽朋克] [神作] [+]         │
└──────────────────────────────────────┘

输入时自动补全：
┌──────────────────────────────────────┐
│ 玄█                                   │
├──────────────────────────────────────┤
│ · 玄幻 （15 本书使用）                │
│ · 玄幻小说 （8 本书使用）             │
│ · 玄学 （2 本书使用）                 │
└──────────────────────────────────────┘
```

**交互规则**：
1. **输入时自动补全**：基于已有标签前缀匹配（按使用次数排序）
2. **回车/逗号创建**：输入 `新标签` + 回车 → 创建新标签
3. **点击 × 删除**：从本书移除该标签（不删除全局标签）
4. **点击标签**：跳转到该标签的筛选视图

#### 3.6.2 标签管理页（P2 必做）

```
┌──────────────────────────────────────┐
│ ‹ 标签管理               共 47 个    │
├──────────────────────────────────────┤
│ 🔍 搜索标签...                       │
├──────────────────────────────────────┤
│ 玄幻                     15本  ✏️ 🗑 │
│ 完本小说                 12本  ✏️ 🗑 │
│ 蒸汽朋克                  8本  ✏️ 🗑 │
│ 神作                      6本  ✏️ 🗑 │
│ ...                                   │
└──────────────────────────────────────┘
```

**阶段决策**：标签管理页安排在 P2 必做；原因仅是控制首轮数据迁移和同步风险，不能因此漏做。

- 需要全局标签重命名、删除、合并的冲突处理，复杂度高于详情页标签编辑。
- 合并标签会触发跨书籍批量更新，需要同步 `isDirty/version/updatedAt`，否则 WebDAV 合并会丢失远端变更。
- 从性能角度看，P1 先完成单书标签编辑和单标签筛选；P2 标签管理页必须采用分页/搜索加载，不允许常驻全量复杂查询。

### 3.7 关键细节

#### 3.7.1 自动补全算法

```kotlin
// P1 只做数据库前缀匹配；P3 再做用户确认式智能建议，不在输入路径引入 Levenshtein 全量计算
@Query("""
    SELECT t.*, COUNT(r.book_id) AS usage_count
    FROM tags t
    LEFT JOIN book_tag_cross_ref r ON t.id = r.tag_id
    WHERE t.name LIKE :prefix || '%'
    GROUP BY t.id
    ORDER BY usage_count DESC, t.name ASC
    LIMIT 10
""")
suspend fun searchTagsByPrefix(prefix: String): List<TagWithCount>
```

**性能理由**：模糊匹配需要把标签集合拉到内存再计算编辑距离。P1 采用 SQLite 前缀匹配，数据量和响应时间更可控；P3 若做模糊/智能建议，必须走后台计算、结果缓存和用户确认，不得拖慢输入框。

#### 3.7.2 标签颜色自动分配

```kotlin
// 6 种墨土色阶
val TAG_COLORS = listOf(
    "#8B5E3C",  // 深棕
    "#5E5346",  // 暗灰棕
    "#B89568",  // 浅棕
    "#9C9082",  // 中灰
    "#D4CCC0",  // 浅灰米
    "#7D7162",  // 深灰
)

// 基于标签名哈希分配颜色（同一标签永远同一颜色）
fun getTagColor(tagName: String): String {
    val hash = tagName.hashCode()
    return TAG_COLORS[Math.abs(hash) % TAG_COLORS.size]
}
```

#### 3.7.3 预设标签包（P3 必做）

P3 必须提供几套官方模板供用户一键导入；导入前展示预览，用户确认后写入：

| 模板 | 标签示例 |
|---|---|
| 按题材 | 玄幻、武侠、都市、历史、科幻、悬疑 |
| 按阅读状态 | 想读、在读、读完、弃读 |
| 按长度 | 短篇、中篇、长篇、超长篇 |

### 3.8 数据库迁移

```kotlin
// P1 从 ShuLiDatabase 17 升到 18
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 创建标签表
        database.execSQL("""
            CREATE TABLE tags (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                color_index INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )
        """)
        database.execSQL("CREATE UNIQUE INDEX index_tags_name ON tags(name)")
        
        // 创建关联表
        database.execSQL("""
            CREATE TABLE book_tag_cross_ref (
                book_id INTEGER NOT NULL,
                tag_id INTEGER NOT NULL,
                added_at INTEGER NOT NULL,
                PRIMARY KEY (book_id, tag_id),
                FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
                FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
            )
        """)
        database.execSQL("CREATE INDEX index_book_tag_cross_ref_book_id ON book_tag_cross_ref(book_id)")
        database.execSQL("CREATE INDEX index_book_tag_cross_ref_tag_id ON book_tag_cross_ref(tag_id)")
    }
}
```

---

## 4. 数据库升级总览

当前工程事实：

| 项目 | 当前值 |
|---|---|
| 数据库类 | `core/database/ShuLiDatabase.kt` |
| 当前版本 | `version = 16` |
| 数据库名 | `ShuLiDatabase.DATABASE_NAME` |
| 书架查询模型 | `BookShelfRow` 分页投影 |
| Room 构建 | `ShuLiAppContainer.database` |

因此本方案必须按 `ShuLiDatabase 16 -> 17 -> 18 -> 19 -> 20` 设计，不能继续沿用旧文档里的 `AppDatabase`、`5 -> 6 -> 7` 示例。

### 4.1 新增字段

| 表 | 字段 | 类型 | 默认值 | 迁移版本 |
|---|---|---|---|--:|
| `books` | `reading_status` | TEXT | `'WANT_TO_READ'` | 16→17 |
| `books` | `read_count` | INTEGER | `1` | 16→17 |

### 4.2 新增表

| 表 | 用途 | 迁移版本 |
|---|---|--:|
| `tags` | 全局标签表 | 17→18 |
| `book_tag_cross_ref` | 书籍-标签多对多关联 | 17→18 |
| `reading_history` | 每次完成阅读的历程记录 | 18→19 |
| `tag_suggestion_decision` | P3 智能标签建议的接受/拒绝决策，防止重复强推 | 19→20 |

### 4.3 Room Database 版本

```kotlin
@Database(
    entities = [
        BookEntity::class,
        BookFtsEntity::class,
        BookContentIndexEntity::class,
        BookmarkEntity::class,
        NoteEntity::class,
        ReadingProgressEntity::class,
        ReaderPresetEntity::class,
        FolderEntity::class,
        BookChapterEntity::class,
        // P1 新增
        TagEntity::class,
        BookTagCrossRef::class,
        // P2 新增
        ReadingHistoryEntity::class,
        // P3 新增
        TagSuggestionDecisionEntity::class,
    ],
    version = 20,
    exportSchema = true
)
abstract class ShuLiDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookChapterDao(): BookChapterDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun noteDao(): NoteDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun readerPresetDao(): ReaderPresetDao
    abstract fun tagDao(): TagDao
    abstract fun readingHistoryDao(): ReadingHistoryDao
    abstract fun tagSuggestionDecisionDao(): TagSuggestionDecisionDao
}
```

### 4.4 迁移脚本整合

```kotlin
val ALL_MIGRATIONS = arrayOf(
    MIGRATION_16_17, // 阅读状态 + 重读次数
    MIGRATION_17_18, // 标签
    MIGRATION_18_19, // 阅读历程
    MIGRATION_19_20, // 智能建议决策
)

Room.databaseBuilder(
    appContext,
    ShuLiDatabase::class.java,
    ShuLiDatabase.DATABASE_NAME,
)
    .addMigrations(*ALL_MIGRATIONS)
    .build()
```

> 当前 `ShuLiAppContainer` 使用 `fallbackToDestructiveMigration()`。如果要保护已有用户书籍元数据，实施本方案时应注册迁移并移除 destructive fallback，至少在 release 构建中不能依赖破坏性迁移。

### 4.5 备份与同步协议

新增字段不只属于 Room 表，也必须进入备份/同步协议，否则本地可编辑但导出、导入和跨设备同步会丢失用户标注。

| 模块 | 必改点 |
|---|---|
| `sync/export/BackupExporter.kt` | `books.json` 增加 `readingStatus`、`readCount`；P1 增加 `tags/{bookKey}.json` 或在 `books.json` 写标签名数组；P2 增加 `reading_history`；P3 增加建议接受/拒绝决策 |
| `sync/export/BackupImporter.kt` | 解析上述字段；旧备份缺字段时使用默认值 |
| `sync/engine/SyncBookState` | P0 增加 `readingStatus`、`readCount`，并参与 `version/updatedAt` 冲突判断 |
| `sync/conflict/BookState` | 如状态元数据进入 `SyncBookState`，需明确冲突策略 |
| `BookEntity.transitionTo()` | 更新状态时必须设置 `isDirty = true`、`version + 1`、`updatedAt = now` |

**P0 冲突策略**：

- `readingStatus/readCount` 跟随书籍状态版本，使用现有 `version/updatedAt` 规则。
- 如果远端和本地都有状态变更，优先版本号高的一方；版本相同则 `updatedAt` 晚的一方胜出。
- `readCount` 避免简单取最大值，防止本地撤销状态后被远端错误恢复；它跟随获胜状态整体覆盖。

**P1 标签同步策略**：

- 标签属于用户标注元数据，必须同步。
- P1 必须按标签名同步，而不是同步本地自增 `tagId`，避免多设备 ID 不一致。
- `book_tag_cross_ref` 本地仍用 `tagId` 关联；导出/同步时转换为 `List<String>`。

**P2/P3 历史与建议决策策略**：

- P2 `reading_history` 必须进入本地备份；云同步按 `bookKey + readCount + finishedAt` 去重。
- P3 接受的建议最终写入标签关系，按 P1 标签规则同步。
- P3 拒绝的建议必须持久化到 `tag_suggestion_decision`，至少用于本机和本地备份恢复后不重复强推；若进入 WebDAV 同步，只同步 `bookKey/tagName/decision/updatedAt`，不同步未确认候选的正文片段或模型中间结果。

---

## 5. UI 整合设计

### 5.1 书架卡片

```
┌─────────────┐
│●在读  🔄2   │  ← 状态点（左上） + 重读次数（右上，readCount > 1 时显示）
│             │
│   [封面]    │
│             │
│ 诡秘之主    │
│ 爱潜水的乌贼│
│ 57% · 182h  │
│ [玄幻][完结]│  ← P2 显示前 1-2 个标签；P1 预留稳定布局
└─────────────┘
```

**P0 新增元素**：

- 左上角：状态点（7px 圆形，颜色区分），不改变封面尺寸。
- 右上角：重读次数（`N` 或图标 + 数字，仅 `readCount > 1` 时显示）。
- 列表/紧凑列表：使用状态文字徽章，避免仅靠颜色传达语义。

**性能决策**：书架卡片采用增量更新。原因是现有 `BookGrid` / `BookList` / `BookCompactList` 已经按 `BookItem` 渲染，新增状态点不会改变数据加载方式；破坏性重写列表组件没有运行时收益，反而增加重排风险。

### 5.2 详情页

```
┌──────────────────────────────────────┐
│ ‹ 返回                               │
├──────────────────────────────────────┤
│ ┌─────┐ 书名                          │
│ │封面 │ 《诡秘之主》                  │
│ │     │ 爱潜水的乌贼                  │
│ └─────┘ [● 在读 ▼] · 第 2 次阅读      │ ← 状态徽章 + 重读标签
│         [玄幻] [蒸汽朋克] [+]         │ ← 标签（可添加）
├──────────────────────────────────────┤
│ 格式 TXT · 大小 1.2MB · 进度 57%      │
│ 阅读时长 182h                         │
│ 文件路径 /storage/...                 │
└──────────────────────────────────────┘
```

**组件决策**：详情页采用破坏性重构，把旧 `BookInfoBottomSheet` 替换为 `BookDetailsSheet`。

| 方案 | 性能与维护影响 | 结论 |
|---|---|---|
| 增量扩展旧 `BookInfoBottomSheet` | 需要保留可空回调和多分支 UI，调用点可继续遗漏核心能力 | 结论：选择破坏性重构 |
| 破坏性替换为 `BookDetailsSheet` | 非空 `BookItem` + 必填 `BookDetailsActions`，减少空值判断和兼容分支 | 采用 |

详情页按阶段扩展，但所有区块都必须进入路线图：P0 完成状态、重读次数和现有书籍信息；P1 增加标签编辑；P2 增加阅读数据网格、书签/笔记预览和标签管理入口；P3 增加共享元素动画和导出增强。

### 5.3 书架筛选栏（新增维度）

```
┌──────────────────────────────────────┐
│ [全部] [在读] [已读完] [想读] [暂停] │  ← 状态筛选
│ [排序 ▼]                            │
└──────────────────────────────────────┘
```

**筛选维度**：
- **P0 状态**：新增状态筛选，必须替换现有 `FilterType.FINISHED` 的进度推断逻辑。
- **P1 标签**：完成单标签筛选。
- **P2 标签组合**：必须支持 AND/OR 组合筛选，并提供可清除的筛选条件栏。
- **排序**：保留现有排序入口，新增 `READING_STATUS` / `READ_COUNT` 时必须同步更新 `SortOrder`、`SortBottomSheet`、`BookshelfSorting`。

### 5.4 统计页

统计页按阶段落地：P0 必须增加状态分布；P2 必须增加标签 Top N、标签云入口和二刷榜单。

```
[作者] [分组] [格式] [字数] [状态]
                              ▲
                              P0 新增
```

| 维度 | 数据源 | 中心数字 | Legend |
|---|---|---|---|
| 状态 | `BookEntity.readingStatus` | 5 种状态 | 在读 X 本 · 已读完 Y 本 · ... |
| 标签 | `book_tag_cross_ref` | P2 必做 | Top N 标签 |

---

## 6. 实施迭代计划

本路线允许分阶段交付，但每个阶段都是完整闭环。P0/P1/P2/P3 均为本设计的必交付范围；阶段边界只控制先后顺序，不代表功能可遗漏。

### 迭代 1（P0，3-4 天）· 阅读状态 + 重读次数

| 任务 | 工作量 | 优先级 |
|---|--:|:---:|
| `ShuLiDatabase 16 -> 17` 迁移 + `BookEntity` 字段 | 0.5 天 | P0 |
| `BookShelfRow` / `BookDao` 投影查询 / `BookItem` 转换 | 0.5 天 | P0 |
| Repository 状态迁移逻辑（含 `isDirty/version/updatedAt`）| 0.5 天 | P0 |
| `BookDetailsSheet` 破坏性替换旧详情组件 | 0.75 天 | P0 |
| 书架卡片状态点 UI | 0.25 天 | P0 |
| 状态筛选/排序接入 | 0.25 天 | P0 |
| 备份/同步字段接入 | 0.5 天 | P0 |
| 单元测试 + 迁移测试 | 0.5 天 | P0 |

**P0 通过条件**：状态、重读次数、详情页、书架筛选、备份、同步、迁移测试全部通过后，才能进入 P1。

### 迭代 2（P1，3-5 天）· 标签基础闭环

| 任务 | 工作量 | 优先级 |
|---|--:|:---:|
| `ShuLiDatabase 17 -> 18` 迁移 + 标签实体/DAO | 1 天 | P1 |
| 详情页标签添加/删除 | 1 天 | P1 |
| SQLite 前缀自动补全 | 0.5 天 | P1 |
| 单标签筛选/搜索语法 | 0.75 天 | P1 |
| 备份/同步标签名数组 | 0.75 天 | P1 |
| 单元测试 + DAO 测试 | 0.75 天 | P1 |

**P1 通过条件**：单书标签增删、前缀补全、单标签筛选、标签搜索、备份同步全部通过后，才能进入 P2。

### 迭代 3（P2，4-6 天）· 标签管理 + 组合筛选 + 统计

| 任务 | 工作量 | 优先级 |
|---|--:|:---:|
| 标签管理页：搜索、分页、重命名、删除 | 1 天 | P2 |
| 标签合并：跨书籍批量更新 + 冲突规则 | 1 天 | P2 |
| AND/OR 组合筛选与可清除筛选条件栏 | 1 天 | P2 |
| 标签云视图 + 标签 Top N 统计 | 0.75 天 | P2 |
| `ShuLiDatabase 18 -> 19` 迁移 + 阅读历程表 `reading_history` | 0.75 天 | P2 |
| 二刷榜单 + 阅读历程详情展示 | 0.75 天 | P2 |
| P2 同步/备份扩展 + 批量更新测试 | 0.75 天 | P2 |

**P2 通过条件**：全局标签管理、标签合并、组合筛选、标签统计、二刷榜单、批量同步测试全部通过后，才能进入 P3。

### 迭代 4（P3，3-5 天）· 智能建议 + 预设标签包 + 导出增强

| 任务 | 工作量 | 优先级 |
|---|--:|:---:|
| 标签智能建议面板：基于书名/作者/关键词生成候选 | 1 天 | P3 |
| `ShuLiDatabase 19 -> 20` 迁移 + 建议接受/拒绝决策表 | 0.5 天 | P3 |
| 用户确认式建议写入：接受/拒绝/批量接受 | 0.75 天 | P3 |
| 预设标签包导入：预览、选择、确认写入 | 0.75 天 | P3 |
| Markdown / CSV 导出用户标注与标签统计 | 1 天 | P3 |
| 共享元素动画与详情页动效收尾 | 0.75 天 | P3 |
| P3 UI 测试 + 导出测试 + 性能回归 | 0.75 天 | P3 |

**P3 通过条件**：智能建议不自动写入、预设标签包可控导入、导出数据完整、动画无布局抖动、性能回归达标。

### 总工期

| 阶段 | 预计工期 | 完成交付 |
|---|--:|---|
| P0 | 3-4 天 | 阅读状态、重读次数、详情页破坏性替换 |
| P1 | 3-5 天 | 标签基础闭环 |
| P2 | 4-6 天 | 标签管理、组合筛选、统计、二刷榜单 |
| P3 | 3-5 天 | 智能建议、预设标签包、导出增强、动效 |

**完整路线**：约 3-4 周。可以阶段发布，但不能跳过阶段通过条件。

---

## 7. 验收标准

### 7.1 功能验收

| 能力 | 验收点 |
|---|---|
| **阅读状态 P0** | 5 种状态可切换；打开待读书籍自动切为 `READING`；`readingProgress >= 0.99f` 自动标记 `FINISHED`；书架筛选生效 |
| **重读次数 P0** | `FINISHED -> READING/WANT_TO_READ` 时 `readCount++`；详情页显示"第 N 次阅读"；书架卡片显示重读次数 |
| **标签基础 P1** | 添加/删除标签；前缀自动补全生效；单标签筛选生效；`tag:标签名` 或 `#标签名` 搜索生效 |
| **标签管理 P2** | 标签管理页可搜索、分页、重命名、删除；合并标签后所有关联书籍正确迁移 |
| **组合筛选 P2** | 支持 `AND` / `OR` 多标签组合；筛选条件可见、可单项移除、可一键清空 |
| **统计 P2** | 标签 Top N、标签云、二刷榜单、阅读状态分布均可查看 |
| **智能建议 P3** | 标签建议只生成候选；用户确认后才写入；拒绝后不重复强推同一候选 |
| **导出 P3** | Markdown / CSV 导出包含阅读状态、重读次数、标签、统计摘要 |

### 7.2 数据验收

| 验收点 | 检查方法 |
|---|---|
| `16 -> 17` 迁移无损 | 升级后验证历史书籍数据完整 |
| `17 -> 18` 迁移无损 | 升级后验证标签表、关联表、索引存在且历史书籍不丢失 |
| `18 -> 19` 迁移无损 | 升级后验证 `reading_history` 表、索引存在，历史书籍不丢失 |
| `19 -> 20` 迁移无损 | 升级后验证 `tag_suggestion_decision` 表存在，接受/拒绝决策可持久化 |
| 隐式状态正确转换 | `readingProgress > 0 AND < 0.99` -> `READING`；`readingProgress >= 0.99` -> `FINISHED` |
| 书架投影完整 | `BookShelfRow`、`BookItem` 均含 `readingStatus/readCount`，书架查询不退回全量实体 |
| 备份/导入不丢字段 | 导出后再导入，状态、重读次数、标签保持一致 |
| WebDAV 同步不丢字段 | 双设备状态冲突按 `version/updatedAt` 生效 |
| 标签级联删除生效 | 删除书籍时关联标签自动删除 |
| 标签重命名/合并同步正确 | P2 批量更新必须写入 `isDirty/version/updatedAt` |
| readCount 正确递增 | 多次 `FINISHED -> READING` 后 `readCount` 正确 |
| reading_history 正确记录 | P2 读完一本书时追加历史记录，不覆盖旧记录 |
| 智能建议决策正确持久化 | P3 拒绝候选后重启应用不再重复强推同一候选；接受候选后写入标签关系 |

### 7.3 性能验收

| 验收点 | 目标 |
|---|---|
| 详情页加载时间 | <= 200ms |
| 标签自动补全响应 | <= 100ms |
| 书架筛选响应 | <= 300ms |
| 状态切换响应 | <= 100ms |
| 标签管理页首屏 | <= 300ms，必须分页或限制 Top N |
| 标签云渲染 | <= 300ms，最多渲染 Top 100 |
| 组合筛选查询 | <= 500ms，必须使用索引，不允许内存全量过滤 |
| 智能建议 | 后台计算；主线程输入和滚动不得卡顿 |
| 书架首屏查询 | 继续使用 `BookShelfRow` 分页投影，不回退到全量 `BookEntity` |

### 7.4 UI 验收

| 验收点 | 检查方法 |
|---|---|
| 亮/暗主题适配 | 所有新组件两种主题下视觉正确 |
| 平板适配 | 详情页在宽屏下双栏布局 |
| 无障碍 | 所有控件有 `contentDescription` 或文本标签 |
| 触控反馈 | 所有可点击元素有 ripple 效果 |
| 书架卡片稳定 | 标签、状态点、重读徽章不会改变封面尺寸或造成列表跳动 |
| 标签管理页可用 | 搜索框、操作按钮、删除/合并确认弹窗完整 |
| 筛选条件清晰 | AND/OR 组合状态必须在 UI 中可见，不让用户猜当前筛选条件 |

### 7.5 阶段完整性验收

| 阶段 | 不允许进入下一阶段的阻断项 |
|---|---|
| P0 -> P1 | 迁移、备份、同步、书架投影任一未接入 |
| P1 -> P2 | 标签基础 CRUD、单标签筛选、标签同步任一未接入 |
| P2 -> P3 | 标签管理、合并、组合筛选、标签统计、`reading_history` 迁移任一未接入 |
| P3 完成 | 智能建议、建议决策持久化、预设标签包、导出增强、动效任一未接入 |

---

## 8. 外部需求边界

本章只记录不属于当前 P0-P3 路线的外部需求。它们不是本路线的遗漏项；若重新启用，必须新建独立设计、任务和验收标准。

| 条目 | 边界说明 |
|---|---|
| 个人评分（5 星）| 用户已否决；重新启用需单独确认 |
| 个人短评 / 完整书评 | 用户已否决；重新启用需单独确认 |
| 系列关联 | 用户已否决；重新启用需单独确认 |
| 书籍类型硬编码 | 当前由自由标签替代；如要固定分类需单独设计 |
| AI 自动写入分类/摘要 | 违反用户主控；P3 只允许建议候选，用户确认后写入 |
| 人物表/关系图 | 需 NLP 与实体关系模型；独立于本路线 |
| 社交共享 / 推荐系统 | 违背纯离线边界；需独立产品决策 |
| 阅读进度分享图 | 涉及图片生成与分享流程；独立于本路线 |

---

## 9. P2/P3 必做路线图

### 9.1 阅读历程表（P2）

- 新增 `reading_history` 表，记录每次读完的日期、阅读次数、完成时进度。
- 详情页显示"第 1 次读完：2024-06-30；第 2 次读完：2025-01-15"。
- 统计页新增"二刷书籍"榜单。

### 9.2 标签智能建议（P3）

- 基于书名/作者/内容关键词生成候选标签。
- 用户可一键接受、拒绝、批量接受。
- 不自动写入，保持"用户主控"原则。

### 9.3 跨设备同步增强（P2/P3）

- P0/P1 已要求把状态、重读次数、标签纳入备份/同步。
- P2 增强标签集合冲突合并：按标签名求并集，删除/合并操作按 `version/updatedAt` 判定。
- P3 增强智能建议状态同步：只同步用户确认后的标签，不同步未确认候选。

### 9.4 数据导出（P3）

- P0/P1 已要求备份 JSON 保留用户标注。
- P3 增加 Markdown 导出：个人读书笔记、标签、阅读状态、重读次数。
- P3 增加 CSV 导出：书籍基础信息、标签列表、阅读状态、统计字段。

### 9.5 标签增强（P2）

- 标签云视图。
- 标签管理页：重命名、删除、合并。
- AND/OR 多标签组合筛选。
- 标签 Top N 统计图。

---

## 10. 文档维护

| 时间 | 变更 |
|---|---|
| 2026-06-05 | 初版，含 4 个信息字段（阅读状态/评分/短评/标签）|
| 2026-06-05 | v2：移除评分和短评，仅保留阅读状态 + 标签；新增 readCount 字段处理二刷场景 |
| 2026-06-06 | v3：按性能优先统一增量/破坏性决策；对齐 `ShuLiDatabase 16 -> 17 -> 18`、`BookShelfRow` 投影、备份/同步协议；收敛 P1 标签范围 |
| 2026-06-06 | v4：按"允许分阶段但禁止漏做"重构 P0-P3 路线、阻断项、详情页示例和 UI/数据/国际化阶段边界 |
| 2026-06-06 | v5：补齐 P2/P3 数据库迁移路线 `18 -> 19 -> 20`，明确阅读历程与智能建议决策持久化验收 |

---

## 附录 A：关键 UI 组件清单

| 组件 | 阶段 | 用途 | 复用 |
|---|---|---|---|
| `ReadingStatusBadge` | P0 | 状态徽章（5 种颜色）| 详情页 · 书架卡片 |
| `ReadCountBadge` | P0 | 重读次数徽章（次数仅 `readCount > 1` 显示）| 详情页 · 书架卡片 |
| `ReadingStatusFilter` | P0 | 状态筛选 chip 组 | 书架筛选栏 |
| `BookDetailsSheet` | P0-P3 | P0 状态/基础信息；P1 标签；P2 阅读数据/预览；P3 导出增强/动效 | 书架 · 阅读页 |
| `TagChip` | P1 | 标签 chip（带颜色）| 详情页 · 书架筛选栏 |
| `TagInputField` | P1 | 标签输入器（带自动补全）| 详情页 |
| `TagFilterDropdown` | P1/P2 | P1 单标签筛选；P2 AND/OR 组合筛选入口 | 书架筛选栏 |
| `TagManagementScreen` | P2 | 标签搜索、分页、重命名、删除、合并 | 标签管理页 |
| `ReadingDataGrid` | P2 | 阅读数据网格 | 详情页 · 统计页 |
| `BookmarkNotesPreview` | P2 | 书签/笔记预览卡 | 详情页 |
| `TagSuggestionPanel` | P3 | 用户确认式标签智能建议 | 详情页 · 标签管理页 |

## 附录 B：数据查询示例

### B.1 获取"在读"的书籍

```kotlin
@Query("""
    SELECT * FROM books 
    WHERE reading_status = 'READING'
    ORDER BY COALESCE(lastReadTime, addedTime) DESC
""")
fun getReadingBooks(): Flow<List<BookEntity>>
```

### B.2 获取某标签下的所有书籍

```kotlin
@Query("""
    SELECT b.* FROM books b
    INNER JOIN book_tag_cross_ref r ON b.id = r.book_id
    INNER JOIN tags t ON r.tag_id = t.id
    WHERE t.name = :tagName
    ORDER BY COALESCE(b.lastReadTime, b.addedTime) DESC
""")
fun getBooksByTag(tagName: String): Flow<List<BookEntity>>
```

### B.3 统计标签 Top N

```kotlin
@Query("""
    SELECT t.name, COUNT(r.book_id) as book_count
    FROM tags t
    LEFT JOIN book_tag_cross_ref r ON t.id = r.tag_id
    GROUP BY t.id
    ORDER BY book_count DESC
    LIMIT :limit
""")
fun getTopTags(limit: Int = 10): Flow<List<TagWithCount>>
```

### B.4 获取"二刷书籍"（readCount > 1）

```kotlin
@Query("""
    SELECT * FROM books 
    WHERE read_count > 1
    ORDER BY read_count DESC, COALESCE(lastReadTime, addedTime) DESC
""")
fun getRereadBooks(): Flow<List<BookEntity>>
```

---

## 11. 详情页详细设计

### 11.1 设计目标

详情页是书籍的**信息中心 + 操作中心**，必须满足：

1. **多入口复用**：书架长按、阅读界面工具栏、阅读界面标题点击，三个入口共用同一组件
2. **渐进展开**：半屏（peek）→ 全屏（expanded），不打断当前阅读
3. **编辑能力**：状态、标签可在详情页直接编辑，无需跳转
4. **上下文感知**：不同入口下的按钮行为略有差异（如"继续阅读"按钮在阅读界面入口不显示）

### 11.1.3 Pre-release UI/API 破坏性重构原则

**项目背景**：
- 快速开发期（pre-release）
- 尚未发布，无外部用户
- 破坏性改动 ✅ 允许
- 重构 ✅ 鼓励

基于此背景，API 设计**不必向后兼容**，应追求**更激进、更清晰、更严格**：

#### 1. 拒绝"宽容型 API"

**反模式**（之前方案）：所有新参数都带 `null` 默认值
```kotlin
// ❌ 过于宽容：调用者可无视状态管理
fun BookInfoBottomSheet(
    book: BookItem?,
    onDismiss: () -> Unit,
    onStatusChange: ((ReadingStatus) -> Unit)? = null,  // 默认 null，调用者可能忘记传
    onTagAdd: ((String) -> Unit)? = null,
    // ... 更多可空回调
)
```
**问题**：
- 编译时不强制调用者处理状态/标签，**错误推迟到运行时发现**
- 默认 `null` 让 API 看起来"状态/标签可以跳过"，但实际是**核心功能**
- 过度参数化（10+ 参数）让签名难以阅读

#### 2. 采用"严格型 API"

**推荐模式**（破坏性重构）：
```kotlin
// ✅ 严格：回调打包为 data class，必填项显式标注
data class BookDetailsActions(
    val onStatusChange: (ReadingStatus) -> Unit,  // 必填
    val onContinueReading: (() -> Unit)? = null,   // 阅读界面传 null
    val onExportNotes: () -> Unit,                 // 必填
    val onDeleteBook: () -> Unit,                  // 必填
)

// P1 标签能力上线时新增，不能用 null 占位长期跳过
data class BookDetailsTagActions(
    val onTagAdd: (String) -> Unit,
    val onTagRemove: (Long) -> Unit,
    val onTagClick: (String) -> Unit,
)

@Composable
fun BookDetailsSheet(
    book: BookItem,                               // 非空，调用前先判空
    actions: BookDetailsActions,                  // 打包回调，结构清晰
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
)
```
**优势**：
- P0 编译时强制调用者提供状态回调；P1 标签上线后强制调用者提供标签回调，**错误在编译期发现**
- 回调打包为 `data class`，签名从 10+ 参数降到 4 参数
- 组件**重命名**为 `BookDetailsSheet`（更准确反映"详情 + 编辑"语义）

#### 3. 破坏性重构清单

| 项目 | 之前（保守）| 现在（破坏性）|
|---|---|---|
| 组件命名 | `BookInfoBottomSheet`（只读语义）| **`BookDetailsSheet`**（详情+编辑语义）|
| `book` 参数 | `BookItem?`（可空）| **`BookItem`**（非空，调用前先判空）|
| 回调组织 | 10+ 独立参数，多数可空 | **`BookDetailsActions` data class 打包** |
| 状态回调 | `onStatusChange: (() -> Unit)? = null` | **`onStatusChange: (ReadingStatus) -> Unit`**（必填）|
| 标签回调 | `onTagAdd/onTagRemove = null` | **P1 必填**（打包进 `BookDetailsTagActions`，P0 不提前依赖标签类型）|
| 调用点改动 | 零修改即可编译 | **所有调用点必须更新**（pre-release 允许）|

#### 4. 重构收益

| 维度 | 收益 |
|---|---|
| **编译期安全** | 必填回调强制调用者处理状态管理，错误提前发现 |
| **API 清晰度** | 签名从 10+ 参数降到 4 参数，结构一目了然 |
| **语义准确** | `BookDetailsSheet` 比 `BookInfoBottomSheet` 更反映真实功能 |
| **可维护性** | P0 回调集中在 `BookDetailsActions`；P1 标签回调集中在 `BookDetailsTagActions` |
| **文档自描述** | `BookDetailsActions` / `BookDetailsTagActions` 的字段名即文档，阶段职责清晰 |

### 11.1.5 代码现状与复用清单

经深度研读代码，以下组件/模式**已经存在**，详情页设计**必须复用而非重写**：

#### 已存在的详情页组件

| 文件 | 现状 | 演进方向 |
|---|---|---|
| `feature/bookshelf/component/BookInfoBottomSheet.kt` | **已存在**，但只读：标题/作者/格式/大小/进度/时长/路径 | **删除旧组件**，新建 `BookDetailsSheet` 破坏性替换；避免在旧 API 上累积可空回调和分支 |
| `feature/bookshelf/component/BookActionMenu.kt` | **已存在**，`DropdownMenu` 模式：收藏/信息/自定义封面/删除 | 作为详情页内"更多操作"菜单复用 |
| `feature/reader/overlays/ReaderTopBar.kt` | **已抽出**为独立组件（含 `AnimatedVisibility` 淡入淡出动画）| 在 `actions` 区块新增 `ⓘ` 按钮 |

#### 已存在的书架触发链路

```
书架网格/列表 长按
  ↓ BookGrid.kt / BookList.kt / BookCompactList.kt 的 onLongClick
  ↓ onShowInfo(bookId)
  ↓ BookshelfScreen.kt: overlaysState.selectedInfoBookId = bookId
  ↓ BookshelfOverlays.kt: state.selectedInfoBookId
  ↓ BookDetailsSheet(book, actions, onDismiss)  // 替换旧 BookInfoBottomSheet
```

**结论**：书架入口的触发链路**已经完整**，无需新增入口代码；但浮层渲染点在 `BookshelfOverlays.kt`，应破坏性替换旧 `BookInfoBottomSheet`，不在旧组件上增量扩展。

#### 已存在的 BottomSheet 模式（可直接参考）

| 组件 | 文件 | 可复用点 |
|---|---|---|
| `SortBottomSheet` | `feature/bookshelf/component/SortBottomSheet.kt` | `ModalBottomSheet` + `rememberModalBottomSheetState` 的标准骨架 |
| `ImportOptionBottomSheet` | `feature/bookshelf/ImportDialogs.kt` | 列表项 + 图标的菜单式底部弹窗 |
| `StatisticsBottomSheet` | `feature/bookshelf/BookshelfDialogs.kt` | 数据展示型底部弹窗 |
| `QuickSettingsSheet` | `feature/reader/component/QuickSettingsSheet.kt` | 多 Tab 底部弹窗（可用于详情页的"数据/书签/信息"分 Tab）|
| `PickerSheet` | `feature/reader/component/PickerSheet.kt` | 选项选择器底部弹窗（可用于状态选择）|

#### 已存在的编辑控件模式

| 控件 | 文件 | 用途 |
|---|---|---|
| `DropdownMenu` + `DropdownMenuItem` | `BookActionMenu.kt` | 状态下拉菜单 |
| `AlertDialog` + `TextButton` | `BookActionMenu.kt`（删除确认）| 危险操作确认 |
| `SuggestionChip` | Material 3 内置 | 标签 chip |
| `FlowRow` | Material 3 内置 | 标签流布局 |
| `CoverColorPickerDialog` | `feature/bookshelf/component/` | 选项选择器（可借鉴）|

#### 已存在的数据模型（需扩展而非重建）

| 实体 | 文件 | 当前字段 | 需新增 |
|---|---|---|---|
| `BookEntity` | `core/database/entity/BookEntity.kt` | id/title/author/readingProgress/isFavorite/folderId/... | `readingStatus: String`、`readCount: Int` |
| `BookItem` | `feature/bookshelf/model/BookItem.kt` | id/title/author/readingProgress/readingDuration/... | P0：`readingStatus: ReadingStatus`、`readCount: Int`；P1：`tags: List<TagEntity>` |
| `BookshelfNode` | `feature/bookshelf/model/BookItem.kt` | `sealed interface`（`BookItem` + `FolderItem`）| 无需改 |
| `ReadingStatus` 枚举 | **不存在**，需新建 | — | 5 种状态 |

#### 已存在的国际化结构（按模块分离）

```
core/i18n/
├── AppStrings.kt                 （主入口，7 个子接口组合）
├── BookshelfStrings.kt           （接口） ← 已有 bookInfo/bookTitleLabel 等
├── BookshelfStringsImpl.kt       （ZhHans/ZhHant/En 实现）
├── ReaderStrings.kt              （接口） ← 新增详情页相关字段
├── ReaderStringsImpl.kt          （实现）
├── CommonStrings.kt              （接口） ← 已有 infoIconDesc/cancel 等
└── ...
```

**已有字段**（无需新增）：`strings.bookshelf.bookInfo`、`strings.bookshelf.bookTitleLabel`、`strings.bookshelf.bookAuthorLabel`、`strings.bookshelf.bookFormatLabel`、`strings.bookshelf.bookSizeLabel`、`strings.bookshelf.bookProgressLabel`、`strings.bookshelf.readingDurationLabel`、`strings.bookshelf.filePathLabel`、`strings.bookshelf.unknownAuthor`、`strings.bookshelf.notReadYet`、`strings.common.infoIconDesc`、`strings.common.cancel`

**需新增字段**：
- P0：`readingStatus`、`statusWantToRead`/`statusReading`/`statusPaused`/`statusFinished`/`statusAbandoned`、`rereadCount`、`statusReread`、`continueReading`、`startReading`、`rereadBook`、`restartBook`、`exportNotes`、`deleteBook`、`confirmDeleteBook`
- P1：`addTag`、`removeTag`、`tags`
- P2：`bookmarksAndNotes`、`viewAll`、`latestAnnotation`、`readingData`、`totalDuration`、`readingDays`、`startDate`
- P3：导出增强与动效相关文案按实现入口补齐，必须覆盖三语种实现

#### 已存在的工具函数（可直接复用）

| 函数 | 文件 | 用途 |
|---|---|---|
| `Long.toReadableDuration()` | `feature/bookshelf/model/BookItem.kt` | 时长格式化（182 → "3h2m"）|
| `Long.toFormattedFileSize()` | `feature/bookshelf/model/BookItem.kt` | 文件大小格式化 |
| `BookEntity.toBookItem()` | `feature/bookshelf/model/BookItem.kt` | 实体 → UI 模型转换（需扩展新字段）|

### 11.2 三个入口

| 入口 | 触发方式 | 场景 | 现状 |
|---|---|---|---|
| **书架长按** | 长按封面 300ms | 书籍管理（改状态/加标签）| ✅ **入口已完整**：`BookGrid/BookList/BookCompactList` 的 `onLongClick` → `onShowInfo(bookId)` → `BookshelfOverlays`；浮层组件替换为 `BookDetailsSheet` |
| **阅读界面工具栏** | 点击 `ReaderTopBar` 的 `ⓘ` 按钮 | 阅读中查看/修改书籍信息 | ⚠️ 需新增：在 `ReaderTopBar.kt` 的 `actions` 区块加 `IconButton(Icons.Outlined.Info)` + `onShowBookInfo` 回调 |
| **阅读界面标题点击** | 点击 TopAppBar 书名 | 同上，更顺手 | ⚠️ P3 必做：在 `ReaderTopBar.kt` 的 `title` 加 `Modifier.clickable` |

**关键复用**：三个入口都调用**同一个** `BookDetailsSheet` 组件，仅通过 `actions.onContinueReading: (() -> Unit)?` 区分上下文（阅读界面传 `null` 隐藏"继续阅读"按钮）。

### 11.3 详情页形态

采用 **Material 3 ModalBottomSheet**，支持两段式展开：

| 状态 | 高度 | 内容可见 |
|---|---|---|
| **peek（半屏）** | 50% 屏幕高度 | 顶部信息卡 + 继续阅读按钮 + "上滑展开"提示 |
| **expanded（全屏）** | 95% 屏幕高度 | 完整内容（信息卡 + 数据 + 书签预览 + 书籍信息 + 操作）|

### 11.4 完整布局（P3 最终态）

以下布局是 P0-P3 全部完成后的最终形态。P0 只显示状态、重读次数、继续阅读和现有书籍信息；P1 增加标签；P2 增加阅读数据和书签/笔记预览；P3 增加标题点击入口、导出增强和共享元素动画。

#### 11.4.1 peek 状态（半屏）

```
┌──────────────────────────────────────┐
│ 状态栏                               │
├──────────────────────────────────────┤
│ ‹ 返回    诡秘之主    🔍  ⓘ(激活)   │ ← 顶部工具栏（阅读界面入口可见）
├──────────────────────────────────────┤
│                                      │
│          [阅读内容区域]              │ ← 保持可见，不中断阅读
│                                      │
├──────────────────────────────────────┤
│ ═══════  （拖拽把手）                │
│ ┌─────┐ 诡秘之主                    │
│ │封面 │ 爱潜水的乌贼                │ ← 基本信息
│ │     │ [● 在读 ▼] · 第 2 次阅读   │ ← 状态（可编辑）
│ └─────┘ [玄幻] [蒸汽朋克] [+]       │ ← 标签（可编辑）
│                                      │
│ [▶ 继续阅读]                       │ ← 主操作（仅书架入口显示；章节标题可用时再追加）
│                                      │
│ ── 上滑展开完整详情 ──              │ ← 提示
└──────────────────────────────────────┘
```

#### 11.4.2 expanded 状态（全屏）

```
┌──────────────────────────────────────┐
│ ✕ 关闭    书籍详情    ⋮ 更多        │
├──────────────────────────────────────┤
│ ┌─────┐ 诡秘之主                    │
│ │     │ 爱潜水的乌贼                │
│ │封面 │                             │
│ │     │ [● 在读 ▼] · 第 2 次阅读   │
│ └─────┘ [玄幻] [蒸汽朋克] [+]       │
├──────────────────────────────────────┤
│ [▶ 继续阅读]                       │
├──────────────────────────────────────┤
│ 阅读数据                              │
│ ┌────────┬────────┬────────┐         │
│ │ 182h   │ 214天  │ 57%    │         │
│ │ 总时长 │ 已读天数│ 阅读进度│         │
│ └────────┴────────┴────────┘         │
├──────────────────────────────────────┤
│ 书签与笔记                     查看全部›│
│ 🔖 47 个书签  ·  📝 23 条笔记         │
│ 最新：第738章 "命运终将回归..."       │
├──────────────────────────────────────┤
│ 书籍信息                              │
│ 格式：EPUB · 3.2 MB                 │
│ 导入：2024-01-15                    │
│ 最后阅读：2024-08-04 22:15         │
│ 文件：/storage/emulated/0/Books/... │
├──────────────────────────────────────┤
│ [📤 导出笔记]                         │
│ [🗑 删除书籍]（红色）                 │
└──────────────────────────────────────┘
```

### 11.5 三个入口的交互差异

| 差异点 | 书架入口 | 阅读界面入口 |
|---|---|---|
| **关闭行为** | 关闭后回到书架 | 关闭后继续阅读 |
| **"继续阅读"按钮** | 显示 · 点击跳转阅读页 | **不显示**（已在阅读页）|
| **背景内容** | 书架网格（模糊处理）| 阅读内容（保持可见）|
| **打开动画** | P3 从封面位置展开（共享元素动画）| 从底部滑入 |
| **关闭按钮** | 拖拽关闭 + 点击背景 | 拖拽关闭 + `✕` 按钮 |

### 11.6 组件 API 定义（按阶段严格扩展）

#### 11.6.1 `BookDetailsSheet`（P0 破坏性替换旧组件）

**现有签名**（将被**破坏性替换**）：
```kotlin
// feature/bookshelf/component/BookInfoBottomSheet.kt（现状，将被删除）
@Composable
fun BookInfoBottomSheet(
    book: BookItem?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
)
```

**P0 签名**（状态 + 重读次数，可独立编译）：
```kotlin
// feature/bookshelf/component/BookDetailsSheet.kt（新文件）
data class BookDetailsActions(
    val onStatusChange: (ReadingStatus) -> Unit,
    val onExportNotes: () -> Unit,
    val onDeleteBook: () -> Unit,
    val onContinueReading: (() -> Unit)? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailsSheet(
    book: BookItem,
    actions: BookDetailsActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
)
```

**P1 标签上线时的破坏性扩展签名**（必须更新所有调用点）：
```kotlin
data class BookDetailsTagState(
    val tags: List<TagEntity>,
    val allTags: List<TagWithCount>,
)

data class BookDetailsTagActions(
    val onTagAdd: (String) -> Unit,
    val onTagRemove: (Long) -> Unit,
    val onTagClick: (String) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailsSheet(
    book: BookItem,
    actions: BookDetailsActions,
    tagState: BookDetailsTagState,
    tagActions: BookDetailsTagActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
)
```

**阶段原则**：
- P0 不提前依赖 `TagEntity` / `TagWithCount`，保证阅读状态阶段可独立编译和验收。
- P1 标签上线时不使用 nullable 回调占位，必须破坏性扩展 API，并强制所有调用点传入标签状态和标签回调。
- P2/P3 继续按同样方式扩展统计、书签预览、导出和动效状态；阶段通过条件见 §7.5。
- `BookInfoBottomSheet` 必须删除，不保留兼容壳。

**破坏性改动说明**：
- **重命名**：`BookInfoBottomSheet` -> `BookDetailsSheet`（语义更准确）
- **参数非空**：`book: BookItem?` -> `book: BookItem`（调用前先判空）
- **回调打包**：10+ 独立参数 -> 按职责拆分的 `BookDetailsActions` / `BookDetailsTagActions`
- **调用点破坏性更新**：所有调用点必须更新为阶段对应的新 API（pre-release 允许）

#### 11.6.2 `ReaderTopBar`（P0 破坏性新增信息入口）

**现有签名**（`feature/reader/overlays/ReaderTopBar.kt`）：
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderTopBar(
    uiState: ReaderUiState,
    bookId: Long,
    onBackClick: () -> Unit,
    onToggleSearch: () -> Unit,
    onPreviousSearchResult: () -> Unit,
    onNextSearchResult: () -> Unit,
    modifier: Modifier = Modifier,
)
```

**P0 演进后签名**（破坏性新增必填 `onShowBookInfo`）：
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderTopBar(
    uiState: ReaderUiState,
    bookId: Long,
    onBackClick: () -> Unit,
    onToggleSearch: () -> Unit,
    onPreviousSearchResult: () -> Unit,
    onNextSearchResult: () -> Unit,
    onShowBookInfo: () -> Unit,  // ← 破坏性新增（无默认值，强制调用者传）
    modifier: Modifier = Modifier,
)
```

**actions 区块改动**：
```kotlin
actions = {
    ReaderSearchControls(...)
    
    // 新增：书籍详情按钮
    IconButton(onClick = onShowBookInfo) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = strings.bookshelf.bookInfo,
            tint = readerColors.textPrimary,
        )
    }
    
    IconButton(onClick = onToggleSearch) { ... }
}
```

**破坏性改动说明**：
- ✅ **强制传入**：`onShowBookInfo: () -> Unit` 无默认值，所有调用方必须提供
- ✅ 唯一的调用方 `ReaderScreen` 必须更新（pre-release 允许）

#### 11.6.3 `ReaderScreen` 调用方（破坏性更新）

```kotlin
// feature/reader/ReaderScreen.kt
var showBookDetailsSheet by remember { mutableStateOf(false) }
val currentBookItem by viewModel.currentBookItem.collectAsState()

ReaderTopBar(
    uiState = uiState,
    bookId = bookId,
    onBackClick = onBackClick,
    onToggleSearch = viewModel.navigationCoordinator::toggleSearch,
    onPreviousSearchResult = viewModel.readerSearchManager::goToPreviousSearchResult,
    onNextSearchResult = viewModel.readerSearchManager::goToNextSearchResult,
    onShowBookInfo = { showBookDetailsSheet = true },  // 破坏性新增（必填）
)

// 弹窗
if (showBookDetailsSheet) {
    currentBookItem?.let { book ->
        BookDetailsSheet(
            book = book,
            actions = BookDetailsActions(
                onStatusChange = viewModel::updateStatus,
                onExportNotes = { viewModel.exportNotes(book.id) },
                onDeleteBook = { showDeleteConfirmDialog = true },
                onContinueReading = null,  // 阅读界面入口：隐藏该按钮
            ),
            onDismiss = { showBookDetailsSheet = false },
        )
    }
}
```

P1 标签上线后，`ReaderScreen` 必须额外收集 `currentBookTags` / `allTags` 并传入 `BookDetailsTagState` 与 `BookDetailsTagActions`，否则不得通过 P1 阶段验收。

#### 11.6.4 `BookshelfOverlays` 调用方（破坏性更新）

```kotlin
// feature/bookshelf/BookshelfScreen.kt：入口状态仍由 overlaysState 承接
val overlaysState = rememberBookshelfOverlaysState()

BookContent(
    books = uiState.nodes,
    onShowInfo = { overlaysState.selectedInfoBookId = it },  // 长按触发（已有，无需改）
    ...
)

BookshelfOverlays(viewModel = viewModel, state = overlaysState)
```

```kotlin
// feature/bookshelf/BookshelfOverlays.kt：替换旧 BookInfoBottomSheet 渲染点
state.selectedInfoBookId?.let { id ->
    val book = uiState.nodes.firstOrNull { it.id == id } as? BookItem
    book?.let {
        BookDetailsSheet(
            book = it,
            actions = BookDetailsActions(
                onStatusChange = { newStatus -> viewModel.updateStatus(it.id, newStatus) },
                onExportNotes = { viewModel.exportNotes(it.id) },
                onDeleteBook = { showDeleteConfirmDialog = true },
                onContinueReading = {
                    state.selectedInfoBookId = null
                    viewModel.onBookClick(it.id)
                },
            ),
            onDismiss = { state.selectedInfoBookId = null },
        )
    }
}
```

P1 标签上线后，`BookshelfOverlays` 必须额外传入当前书籍标签、全局标签自动补全数据源和 `BookDetailsTagActions`。P2 标签管理入口也从这里接入。

### 11.7 区块详细设计

#### 11.7.1 顶部信息卡（P0，peek 状态可见）

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    // 封面：使用 DefaultBookCover 组件（墨土 Morandi 渐变 + 宋体首字 + 腰封）
    DefaultBookCover(
        title = book.title,
        fileType = book.fileType,
        modifier = Modifier.size(width = 80.dp, height = 120.dp),
        isFavorite = book.isFavorite,
        readingProgress = book.readingProgress,
        paletteIndexOverride = book.customCoverPaletteIndex,
    )
    
    Column(modifier = Modifier.weight(1f)) {
        // 书名（18px 衬线粗体）
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Serif,
        )
        
        // 作者（12px 次色）
        Text(
            text = book.author ?: "未知作者",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        
        Spacer(Modifier.height(8.dp))
        
        // 状态徽章（可点击切换）
        ReadingStatusBadge(
            status = book.readingStatus,
            readCount = book.readCount,
            onClick = { showStatusMenu = true },
        )
    }
}
```

P1 标签能力上线后，必须在状态徽章下方插入 §11.7.3 的 `TagFlow`，并通过 `BookDetailsTagActions` 处理新增、删除和筛选跳转；不得把标签回调塞回 P0 `BookDetailsActions`。

#### 11.7.2 状态切换下拉菜单（P0）

```kotlin
DropdownMenu(
    expanded = showStatusMenu,
    onDismissRequest = { showStatusMenu = false },
) {
    ReadingStatus.values().forEach { status ->
        DropdownMenuItem(
            text = {
                val statusColor = when (status) {
                    ReadingStatus.WANT_TO_READ -> MaterialTheme.colorScheme.outline
                    ReadingStatus.READING -> MaterialTheme.colorScheme.primary
                    ReadingStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
                    ReadingStatus.FINISHED -> MaterialTheme.colorScheme.secondary
                    ReadingStatus.ABANDONED -> MaterialTheme.colorScheme.error
                }
                val statusLabel = when (status) {
                    ReadingStatus.WANT_TO_READ -> "想读"
                    ReadingStatus.READING -> "在读"
                    ReadingStatus.PAUSED -> "暂停"
                    ReadingStatus.FINISHED -> "已读完"
                    ReadingStatus.ABANDONED -> "弃读"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(statusColor, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(statusLabel)
                    if (status == book.readingStatus) {
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Filled.Check, contentDescription = null)
                    }
                }
            },
            onClick = {
                onStatusChange(status)
                showStatusMenu = false
            },
        )
    }
}
```

#### 11.7.3 标签流（P1 必做）

```kotlin
@Composable
fun TagFlow(
    tags: List<TagEntity>,
    onTagClick: (TagEntity) -> Unit,
    onAddClick: () -> Unit,
    onRemoveClick: (Long) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.forEach { tag ->
            TagChip(
                tag = tag,
                onClick = { onTagClick(tag) },
                onRemove = { onRemoveClick(tag.id) },
            )
        }
        
        // "+ 添加" chip（虚线边框）
        AddTagChip(onClick = onAddClick)
    }
}

@Composable
fun TagChip(
    tag: TagEntity,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    SuggestionChip(
        onClick = onClick,
        label = { Text(tag.name, style = MaterialTheme.typography.labelSmall) },
        trailingIcon = {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(14.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "移除", modifier = Modifier.size(10.dp))
            }
        },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = Color(tag.colorHex),
            labelColor = Color.White,
        ),
    )
}
```

#### 11.7.4 主操作按钮（P0，继续阅读）

```kotlin
onContinueReading?.let {
    Button(
        onClick = it,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(
            text = when (book.readingStatus) {
                ReadingStatus.WANT_TO_READ -> "开始阅读"
                ReadingStatus.FINISHED -> "重新阅读"
                ReadingStatus.ABANDONED -> "重新开始"
                else -> "继续阅读"
            },
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
```

#### 11.7.5 阅读数据（P2 必做，3 列网格）

```kotlin
@Composable
fun ReadingDataGrid(book: BookItem) {
    Text(
        "阅读数据",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(16.dp, 8.dp),
    )
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { DataCard("总时长", book.totalDuration) }
        item { DataCard("已读天数", "${book.readingDays}天") }
        item { DataCard("阅读进度", "${(book.readingProgress * 100).toInt()}%") }
    }
}

@Composable
fun DataCard(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

#### 11.7.6 书签与笔记预览卡（P2 必做）

```kotlin
@Composable
fun BookmarkNotesPreview(
    bookmarkCount: Int,
    noteCount: Int,
    latestAnnotation: AnnotationItem?,
    onViewAll: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp, 8.dp),
        onClick = onViewAll,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("书签与笔记", style = MaterialTheme.typography.titleSmall)
                Text(
                    "查看全部 ›",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                "🔖 $bookmarkCount 个书签 · 📝 $noteCount 条笔记",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            
            latestAnnotation?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    "最新：${it.chapterName} \"${it.preview}...\"",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
```

### 11.8 演进路径（性能优先版）

**核心原则**：UI/API 边界采用破坏性替换以减少兼容分支；数据库、同步、备份链路采用增量迁移以保护数据。P0/P1/P2/P3 均为必做阶段，不能因阶段拆分而漏掉详情页能力。

#### P0 · 详情页破坏性替换 + 阅读状态

**目标**：删除旧 `BookInfoBottomSheet`，替换为 P0 可独立编译的 `BookDetailsSheet`；接入阅读状态和重读次数。

| 改动文件 | 改动类型 | 内容 |
|---|---|---|
| `feature/bookshelf/component/BookInfoBottomSheet.kt` | **删除** | 旧的只读详情页 |
| `feature/bookshelf/component/BookDetailsSheet.kt` | **新建** | P0 详情页，含 `BookDetailsActions`、状态徽章、继续阅读、导出/删除入口 |
| `feature/reader/overlays/ReaderTopBar.kt` | **破坏性修改** | 新增必填 `onShowBookInfo: () -> Unit` + `actions` 加 `Info` 按钮 |
| `feature/reader/ReaderScreen.kt` | **破坏性修改** | 更新 `ReaderTopBar` 调用 + 新增 `BookDetailsSheet` 弹窗 |
| `feature/reader/ReaderViewModel.kt` | 增量 | 新增 `currentBookItem: StateFlow<BookItem?>`；新增 `updateStatus(newStatus)` |
| `feature/bookshelf/BookshelfOverlays.kt` | **破坏性修改** | 替换旧渲染点，统一调用 P0 `BookDetailsSheet` |
| `core/database/entity/BookEntity.kt` | 增量 | 新增 `readingStatus/readCount` |
| `core/database/entity/BookShelfRow.kt` | 增量 | 同步新增投影字段，保持书架分页性能 |
| `core/database/dao/BookDao.kt` | 增量 | `getBookRowsPage()` / `searchBookRowsFtsPage()` 补齐新字段 |
| `core/database/ShuLiDatabase.kt` | 增量 | 新增 `MIGRATION_16_17` |

**P0 阻断项**：`BookDetailsSheet` 不得依赖 `TagEntity` / `TagWithCount`；否则 P0 无法独立验收。

#### P1 · 标签基础能力进入详情页

**目标**：详情页可添加/删除标签，书架可单标签筛选，搜索语法可按标签匹配。

| 改动文件 | 改动类型 | 内容 |
|---|---|---|
| `core/database/entity/TagEntity.kt` | **新建** | 标签实体 |
| `core/database/entity/BookTagCrossRef.kt` | **新建** | 书籍-标签关联表 |
| `core/database/dao/TagDao.kt` | **新建** | 标签 DAO（参考 §3.3.2） |
| `core/database/ShuLiDatabase.kt` | 增量 | 新增 `MIGRATION_17_18` + 注册新实体与 `tagDao()` |
| `feature/bookshelf/component/TagFlow.kt` | **新建** | 标签流组件 |
| `feature/bookshelf/component/TagInputField.kt` | **新建** | 标签输入器（SQLite 前缀自动补全） |
| `feature/bookshelf/component/BookDetailsSheet.kt` | **破坏性扩展** | 新增必填 `BookDetailsTagState` / `BookDetailsTagActions` |
| `feature/bookshelf/BookshelfViewModel.kt` | 增量 | 新增 `addTag` / `removeTag` / 单标签筛选 |
| `feature/reader/ReaderViewModel.kt` | 增量 | 新增当前书籍标签流与标签编辑回调 |

**P1 阻断项**：不能使用可空标签回调绕过调用点更新；所有入口都必须传入标签状态和标签动作。

#### P2 · 标签管理 + 统计 + 阅读数据

**目标**：补齐全局标签管理、组合筛选、标签统计、二刷榜单、阅读数据和书签/笔记预览。

| 改动文件 | 改动类型 | 内容 |
|---|---|---|
| `feature/bookshelf/component/BookDetailsSheet.kt` | 增量 | 增加阅读数据网格、书签/笔记预览、标签管理入口 |
| `feature/bookshelf/component/ReadingDataGrid.kt` | **新建** | 阅读数据网格 |
| `feature/bookshelf/component/BookmarkNotesPreview.kt` | **新建** | 书签/笔记预览卡 |
| `feature/bookshelf/tag/TagManagementScreen.kt` 或等价模块 | **新建** | 标签搜索、分页、重命名、删除、合并 |
| `core/database/entity/ReadingHistoryEntity.kt` | **新建** | 阅读历程表 |
| `core/database/dao/TagDao.kt` | 增量 | 组合筛选、Top N、合并、批量更新 |
| `core/database/ShuLiDatabase.kt` | 增量 | P2 迁移：阅读历程表与必要索引 |

**P2 阻断项**：标签管理、合并、AND/OR、标签统计、二刷榜单、`reading_history` 迁移任一缺失，都不能进入 P3。

#### P3 · 智能建议 + 导出增强 + 动效

**目标**：补齐用户确认式智能标签建议、预设标签包、Markdown/CSV 导出、共享元素动画。

| 改动文件 | 改动类型 | 内容 |
|---|---|---|
| `feature/bookshelf/component/BookDetailsSheet.kt` | 增量 | 智能建议入口、导出增强入口、共享元素动画状态 |
| `feature/bookshelf/tag/TagSuggestionPanel.kt` 或等价模块 | **新建** | 只生成候选，用户确认后写入 |
| `feature/bookshelf/tag/PresetTagImportSheet.kt` 或等价模块 | **新建** | 预设标签包预览与导入 |
| `sync/export/BackupExporter.kt` / 导出模块 | 增量 | Markdown / CSV 导出 |
| `feature/bookshelf/BookshelfScreen.kt` / `BookshelfOverlays.kt` | 增量 | 共享元素动画来源与目标状态 |

**P3 阻断项**：智能建议不得自动写入；建议接受/拒绝决策必须持久化；导出必须包含状态、重读次数、标签和统计字段。

### 11.9 与现有代码的整合点

#### 11.9.1 文件修改清单（P0-P3 累计）

**已存在，需增量修改**：

| 文件路径 | 修改类型 | 涉及阶段 |
|---|---|---|
| `app/src/main/java/com/shuli/reader/feature/reader/overlays/ReaderTopBar.kt` | 破坏性修改 + 增量 | P0, P3 |
| `app/src/main/java/com/shuli/reader/feature/reader/ReaderScreen.kt` | 破坏性修改 | P0, P1 |
| `app/src/main/java/com/shuli/reader/feature/reader/ReaderViewModel.kt` | 增量 | P0, P1 |
| `app/src/main/java/com/shuli/reader/feature/bookshelf/BookshelfOverlays.kt` | 破坏性修改 + 增量 | P0, P1, P3 |
| `app/src/main/java/com/shuli/reader/feature/bookshelf/BookshelfScreen.kt` | 增量 | P2, P3 |
| `app/src/main/java/com/shuli/reader/feature/bookshelf/BookshelfViewModel.kt` | 增量 | P0, P1, P2 |
| `app/src/main/java/com/shuli/reader/feature/bookshelf/model/BookItem.kt` | 增量 | P0, P1 |
| `app/src/main/java/com/shuli/reader/feature/bookshelf/component/BookGrid.kt` | 增量 | P0, P2 |
| `app/src/main/java/com/shuli/reader/feature/bookshelf/component/BookList.kt` | 增量 | P0, P2 |
| `app/src/main/java/com/shuli/reader/feature/bookshelf/component/BookCompactList.kt` | 增量 | P0, P2 |
| `app/src/main/java/com/shuli/reader/core/database/entity/BookEntity.kt` | 增量 | P0 |
| `app/src/main/java/com/shuli/reader/core/database/entity/BookShelfRow.kt` | 增量 | P0 |
| `app/src/main/java/com/shuli/reader/core/database/dao/BookDao.kt` | 增量 | P0 |
| `app/src/main/java/com/shuli/reader/core/database/ShuLiDatabase.kt` | 增量 | P0, P1, P2, P3 |
| `app/src/main/java/com/shuli/reader/core/i18n/BookshelfStrings.kt` | 增量 | P0, P1, P2, P3 |
| `app/src/main/java/com/shuli/reader/core/i18n/BookshelfStringsImpl.kt` | 增量 | P0, P1, P2, P3 |

**需新建**：

| 文件路径 | 用途 | 涉及阶段 |
|---|---|---|
| `app/src/main/java/com/shuli/reader/feature/bookshelf/component/BookDetailsSheet.kt` | 新详情页组件，破坏性替换旧 `BookInfoBottomSheet` | P0 |
| `app/src/main/java/com/shuli/reader/core/reading/ReadingStatus.kt` | 5 种阅读状态枚举 + `transitionTo()` 状态迁移 | P0 |
| `app/src/main/java/com/shuli/reader/core/database/entity/TagEntity.kt` | 标签实体 | P1 |
| `app/src/main/java/com/shuli/reader/core/database/entity/BookTagCrossRef.kt` | 书籍-标签多对多关联 | P1 |
| `app/src/main/java/com/shuli/reader/core/database/dao/TagDao.kt` | 标签 DAO | P1, P2 |
| `app/src/main/java/com/shuli/reader/feature/bookshelf/component/TagFlow.kt` | 标签流组件 | P1 |
| `app/src/main/java/com/shuli/reader/feature/bookshelf/component/TagInputField.kt` | 标签输入器（带自动补全）| P1 |
| `app/src/main/java/com/shuli/reader/feature/bookshelf/component/ReadingDataGrid.kt` | 阅读数据网格 | P2 |
| `app/src/main/java/com/shuli/reader/feature/bookshelf/component/BookmarkNotesPreview.kt` | 书签/笔记预览卡 | P2 |
| `app/src/main/java/com/shuli/reader/core/database/entity/ReadingHistoryEntity.kt` | 阅读历程表 | P2 |
| `app/src/main/java/com/shuli/reader/core/database/entity/TagSuggestionDecisionEntity.kt` | 智能建议接受/拒绝决策 | P3 |
| `app/src/main/java/com/shuli/reader/core/database/dao/ReadingHistoryDao.kt` | 阅读历程 DAO | P2 |
| `app/src/main/java/com/shuli/reader/core/database/dao/TagSuggestionDecisionDao.kt` | 智能建议决策 DAO | P3 |
| `app/src/main/java/com/shuli/reader/feature/bookshelf/tag/TagManagementScreen.kt` | 标签管理页 | P2 |
| `app/src/main/java/com/shuli/reader/feature/bookshelf/tag/TagSuggestionPanel.kt` | 标签智能建议 | P3 |

**零改动（直接复用）**：

| 文件路径 | 复用原因 |
|---|---|
| `app/src/main/java/com/shuli/reader/feature/bookshelf/component/BookActionMenu.kt` | 作为"更多操作"菜单的模板参考，但无需修改 |
| `app/src/main/java/com/shuli/reader/feature/bookshelf/component/BookCover.kt` | 详情页顶部信息卡直接复用 |
| `app/src/main/java/com/shuli/reader/feature/bookshelf/component/SortBottomSheet.kt` | 作为 BottomSheet 骨架参考 |
| `app/src/main/java/com/shuli/reader/feature/bookshelf/component/CoverColorPickerDialog.kt` | 作为选项选择器参考 |
| `app/src/main/java/com/shuli/reader/core/i18n/CommonStrings.kt` | 已有 `infoIconDesc`、`cancel` 等字段，无需新增 |

#### 11.9.2 `BookshelfOverlays.kt` 整合示例（破坏性重构版）

**现有渲染点**（将被破坏性替换）：
```kotlin
// feature/bookshelf/BookshelfOverlays.kt
if (state.selectedInfoBookId != null) {
    BookInfoBottomSheet(
        book = uiState.nodes.firstOrNull { it.id == state.selectedInfoBookId } as? BookItem,
        onDismiss = { state.selectedInfoBookId = null },
    )
}
```

**P0 破坏性重构后**（使用新 `BookDetailsSheet` + `BookDetailsActions`）：
```kotlin
// feature/bookshelf/BookshelfOverlays.kt
state.selectedInfoBookId?.let { id ->
    val book = uiState.nodes.firstOrNull { it.id == id } as? BookItem
    book?.let { item ->
        BookDetailsSheet(
            book = item,
            actions = BookDetailsActions(
                onStatusChange = { newStatus -> viewModel.updateStatus(item.id, newStatus) },
                onExportNotes = { viewModel.exportNotes(item.id) },
                onDeleteBook = { state.showDeleteConfirmDialog = true },
                onContinueReading = {
                    state.selectedInfoBookId = null
                    viewModel.onBookClick(item.id)
                },
            ),
            onDismiss = { state.selectedInfoBookId = null },
        )
    }
}
```

**破坏性改动说明**：
- **组件替换**：删除 `BookInfoBottomSheet`，新增 `BookDetailsSheet`
- **回调打包**：10+ 独立参数收敛为 `BookDetailsActions`
- **P0 必填回调**：`onStatusChange` / `onExportNotes` / `onDeleteBook` 强制非空
- **入口复用**：`BookshelfScreen.kt` 继续只负责把 `onShowInfo` 写入 `overlaysState.selectedInfoBookId`

**P1 必做扩展**：标签上线时，`BookshelfOverlays` 必须在同一渲染点补齐标签状态与标签动作，并同步更新 `BookDetailsSheet` 调用。
```kotlin
val currentBookTags by viewModel.observeBookTags(item.id).collectAsState(initial = emptyList())
val allTags by viewModel.allTags.collectAsState()

BookDetailsSheet(
    book = item,
    actions = bookDetailsActions,
    tagState = BookDetailsTagState(
        tags = currentBookTags,
        allTags = allTags,
    ),
    tagActions = BookDetailsTagActions(
        onTagAdd = { tagName -> viewModel.addTag(item.id, tagName) },
        onTagRemove = { tagId -> viewModel.removeTag(item.id, tagId) },
        onTagClick = { tagName -> viewModel.applyTagFilter(tagName) },
    ),
    onDismiss = { state.selectedInfoBookId = null },
)
```

P1 验收时必须确认两个入口都完成标签接入：书架入口和阅读页入口。任一入口仍停留在 P0 签名，均视为 P1 未完成。

#### 11.9.3 `ReaderTopBar.kt` + `ReaderScreen.kt` 整合示例

**现有 `ReaderTopBar.kt`**（已抽出独立组件，含 `AnimatedVisibility` 动画）：
```kotlin
// feature/reader/overlays/ReaderTopBar.kt（现状）
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderTopBar(
    uiState: ReaderUiState,
    bookId: Long,
    onBackClick: () -> Unit,
    onToggleSearch: () -> Unit,
    onPreviousSearchResult: () -> Unit,
    onNextSearchResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = uiState.showToolbar && !uiState.showSearch,
        enter = slideInVertically(...) + fadeIn(...),
        exit = slideOutVertically(...) + fadeOut(...),
    ) {
        Box(...) {
            TopAppBar(
                title = { Text(uiState.bookTitle, ...) },
                navigationIcon = { IconButton(onClick = onBackClick) { ... } },
                actions = {
                    ReaderSearchControls(...)
                    IconButton(onClick = onToggleSearch) {
                        Icon(Icons.Outlined.Search, ...)
                    }
                },
            )
        }
    }
}
```

**P0 破坏性改动**（新增必填 `onShowBookInfo` 参数 + 一个 `IconButton`）：
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderTopBar(
    uiState: ReaderUiState,
    bookId: Long,
    onBackClick: () -> Unit,
    onToggleSearch: () -> Unit,
    onPreviousSearchResult: () -> Unit,
    onNextSearchResult: () -> Unit,
    onShowBookInfo: () -> Unit,  // P0 新增：必填
    modifier: Modifier = Modifier,
) {
    // ... AnimatedVisibility 完全保留
    AnimatedVisibility(...) {
        Box(...) {
            TopAppBar(
                title = { Text(uiState.bookTitle, ...) },
                navigationIcon = { ... },
                actions = {
                    ReaderSearchControls(...)
                    
                    IconButton(onClick = onShowBookInfo) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = strings.bookshelf.bookInfo,
                            tint = readerColors.textPrimary,
                        )
                    }
                    
                    IconButton(onClick = onToggleSearch) { ... }
                },
            )
        }
    }
}
```

**`ReaderScreen.kt` P0 改动**（调用方）：
```kotlin
// feature/reader/ReaderScreen.kt
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBackClick: () -> Unit,
    ...
) {
    val uiState by viewModel.uiState.collectAsState()
    val bookId = ...
    val currentBookItem by viewModel.currentBookItem.collectAsState()
    
    // P0 新增：弹窗状态
    var showBookDetailsSheet by remember { mutableStateOf(false) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // ... 阅读器主体
        
        // 已有：TopBar
        ReaderTopBar(
            uiState = uiState,
            bookId = bookId,
            onBackClick = onBackClick,
            onToggleSearch = viewModel.navigationCoordinator::toggleSearch,
            onPreviousSearchResult = viewModel.readerSearchManager::goToPreviousSearchResult,
            onNextSearchResult = viewModel.readerSearchManager::goToNextSearchResult,
            onShowBookInfo = { showBookDetailsSheet = true },
        )
        
        // ... 其他 overlays
    }
    
    // P0 新增：书籍详情弹窗
    if (showBookDetailsSheet) {
        currentBookItem?.let { book ->
            BookDetailsSheet(
                book = book,
                actions = BookDetailsActions(
                    onStatusChange = viewModel::updateStatus,
                    onExportNotes = { viewModel.exportNotes(book.id) },
                    onDeleteBook = { showDeleteConfirmDialog = true },
                    onContinueReading = null,
                ),
                onDismiss = { showBookDetailsSheet = false },
            )
        }
    }
}
```

**`ReaderViewModel.kt` P0 增量**：
```kotlin
// 新增派生 StateFlow：当前阅读书籍的 BookItem（供详情页使用）
// 性能要求：按当前 bookId 精确查询，不从 getAllBooks()/booksFlow 派生全量列表。
@OptIn(ExperimentalCoroutinesApi::class)
val currentBookItem: StateFlow<BookItem?> = uiState
    .map { it.bookId }
    .distinctUntilChanged()
    .flatMapLatest { bookId ->
        bookQueryRepository?.getBookById(bookId) ?: flowOf(null)
    }
    .map { entity -> entity?.toBookItem() }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

// P0 新增：更新阅读状态
fun updateStatus(newStatus: ReadingStatus) {
    val bookId = _uiState.value.bookId
    viewModelScope.launch {
        val repository = readingProgressRepository ?: return@launch
        val result = repository.updateReadingStatus(bookId, newStatus) ?: return@launch
        val updatedBook = result.updatedBook
        
        // 状态变化反馈
        val message = when (newStatus) {
            ReadingStatus.FINISHED -> strings.reader.statusFinished
            ReadingStatus.READING -> if (result.previousStatus == ReadingStatus.FINISHED) {
                strings.reader.statusReread(updatedBook.readCount)
            } else null
            else -> null
        }
        message?.let { _uiState.update { s -> s.copy(toastMessage = it) } }
    }
}
```

**P1 必做扩展**：阅读页入口必须与书架入口一致，收集当前书籍标签和全局标签自动补全数据，并传入 `BookDetailsTagState` / `BookDetailsTagActions`。阅读页内 `onContinueReading = null` 保持不变。

#### 11.9.4 `BookDetailsSheet.kt` P0 实现骨架（新文件）

```kotlin
// feature/bookshelf/component/BookDetailsSheet.kt（新文件，破坏性替换 BookInfoBottomSheet）

package com.shuli.reader.feature.bookshelf.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.core.reading.ReadingStatus
import com.shuli.reader.feature.bookshelf.model.BookItem
import com.shuli.reader.feature.bookshelf.model.FileType

// 回调打包为 data class
data class BookDetailsActions(
    val onStatusChange: (ReadingStatus) -> Unit,
    val onExportNotes: () -> Unit,
    val onDeleteBook: () -> Unit,
    val onContinueReading: (() -> Unit)? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailsSheet(
    book: BookItem,
    actions: BookDetailsActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val sheetState = rememberModalBottomSheetState()
    var showStatusMenu by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            // 顶部信息卡（封面 + 书名 + 作者 + 状态）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 封面：使用 DefaultBookCover 组件（墨土 Morandi 渐变 + 宋体首字 + 腰封）
                DefaultBookCover(
                    title = book.title,
                    fileType = book.fileType,
                    modifier = Modifier.size(width = 80.dp, height = 120.dp),
                    isFavorite = book.isFavorite,
                    readingProgress = book.readingProgress,
                    paletteIndexOverride = book.customCoverPaletteIndex,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Serif,
                    )
                    Text(
                        text = book.author ?: strings.bookshelf.unknownAuthor,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    
                    // 状态徽章（可点击）
                    ReadingStatusBadge(
                        status = book.readingStatus,
                        readCount = book.readCount,
                        onClick = { showStatusMenu = true },
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            
            // 继续阅读按钮（仅 actions.onContinueReading 非 null 时显示）
            actions.onContinueReading?.let { onContinue ->
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        text = when (book.readingStatus) {
                            ReadingStatus.WANT_TO_READ -> strings.reader.startReading
                            ReadingStatus.FINISHED -> strings.reader.rereadBook
                            ReadingStatus.ABANDONED -> strings.reader.restartBook
                            else -> strings.reader.continueReading
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            
            // 信息列表（保留原有信息）
            InfoItem(label = strings.bookshelf.bookFormatLabel, value = if (book.fileType == FileType.TXT) "TXT" else "EPUB")
            InfoItem(label = strings.bookshelf.bookSizeLabel, value = book.fileSize)
            InfoItem(label = strings.bookshelf.bookProgressLabel, value = "${(book.readingProgress * 100).toInt()}%")
            InfoItem(label = strings.bookshelf.readingDurationLabel, value = book.readingDuration.ifEmpty { strings.bookshelf.notReadYet })
            InfoItem(label = strings.bookshelf.filePathLabel, value = book.filePath)
        }
    }
    
    // 状态下拉菜单（参考 BookActionMenu.kt）
    DropdownMenu(
        expanded = showStatusMenu,
        onDismissRequest = { showStatusMenu = false },
    ) {
        ReadingStatus.entries.forEach { status ->
            DropdownMenuItem(
                text = {
                    val statusColor = when (status) {
                        ReadingStatus.WANT_TO_READ -> MaterialTheme.colorScheme.outline
                        ReadingStatus.READING -> MaterialTheme.colorScheme.primary
                        ReadingStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
                        ReadingStatus.FINISHED -> MaterialTheme.colorScheme.secondary
                        ReadingStatus.ABANDONED -> MaterialTheme.colorScheme.error
                    }
                    val statusLabel = when (status) {
                        ReadingStatus.WANT_TO_READ -> strings.reader.statusWantToRead
                        ReadingStatus.READING -> strings.reader.statusReading
                        ReadingStatus.PAUSED -> strings.reader.statusPaused
                        ReadingStatus.FINISHED -> strings.reader.statusFinished
                        ReadingStatus.ABANDONED -> strings.reader.statusAbandoned
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(statusColor, CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(statusLabel)
                        if (status == book.readingStatus) {
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    }
                },
                onClick = {
                    actions.onStatusChange(status)
                    showStatusMenu = false
                },
            )
        }
    }
}
```

**P1 骨架扩展**：标签上线时，`BookDetailsSheet` 在上述 P0 签名基础上破坏性增加 `tagState` 与 `tagActions`，并把 `TagFlow` 插入状态徽章下方。该扩展属于 P1 阻断项，不得用 P0 骨架长期替代。

### 11.10 设计要点总结

| 要点 | 设计 |
|---|---|
| **单组件多入口** | 同一 `BookDetailsSheet`，三个入口复用（仅通过 `actions.onContinueReading: (() -> Unit)?` 区分上下文）|
| **渐进展开** | 半屏（peek）→ 全屏（expanded），不打断阅读（沿用 `ModalBottomSheet` 默认行为）|
| **上下文感知** | 阅读界面入口 `onContinueReading = null`（按钮自动隐藏）|
| **编辑能力** | P0 状态可直接编辑；P1 标签可直接编辑；P2 管理/统计入口补齐；P3 导出增强和动效补齐 |
| **主操作突出** | "继续阅读"按钮强调色填充，占满宽度，48dp 高 |
| **视觉连续** | 阅读界面打开时，背景内容保持可见（`TopAppBar` 半透明 0.95f）|
| **性能分层演进** | UI/API 边界用破坏性替换减少分支；数据库、同步、备份链路用增量迁移保护数据 |
| **详情页破坏性重构** | 直接删除旧 `BookInfoBottomSheet`，替换为新 `BookDetailsSheet`；所有调用点同步更新（pre-release 允许）|
| **严格型 API** | P0 `BookDetailsActions`、P1 `BookDetailsTagActions` 均为必填结构；调用点错误在编译期暴露 |
| **代码复用优先** | P0 复用 `DropdownMenu`、`BookCover`、`Long.toReadableDuration()`；P1 复用 `SuggestionChip` / `FlowRow`；P2 复用现有书签/笔记数据源 |

### 11.11 与第 5 章的关系

- **第 5 章 UI 整合设计**：详情页在整体 UI 中的位置（书架卡片、详情页区块划分、筛选栏）
- **本章（第 11 章）**：详情页的**详细实现设计**（组件 API、区块代码、演进路径、整合示例）

两章互为补充，第 5 章提供宏观视图，本章提供微观实现细节。
