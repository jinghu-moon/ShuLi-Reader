# Agent 执行合约 — 光屿同步与备份 TDD 任务清单

> 规范：本文件为 Agent 执行指令。每个步骤（S-x）独立可验证，执行前必须确认上游依赖已完成。  
> 所有 verify 命令均在项目根目录执行。测试路径前缀：`com.shuli.reader`。

---

## 全局约束

```
技术栈       Kotlin · Jetpack Compose · Room 2.8.4 · DataStore · OkHttp · kotlinx.serialization
测试框架     JUnit 5 · MockK · Kotest · Turbine（Flow 测试）· Robolectric（可选）
最小 SDK     Android API 26（minSdk = 26）
构建命令     ./gradlew :app:testDebugUnitTest --tests "<fully.qualified.TestClass>"
集成命令     ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<class>
覆盖率检查   ./gradlew :app:koverReport
```

TDD 三色循环规范：
- **🔴 RED**：先写断言式测试，运行应失败（编译失败或断言失败均可）
- **🟢 GREEN**：写最小实现使测试通过，不追求代码质量
- **🔵 REFACTOR**：在测试绿色下重构，不得修改测试逻辑

每完成一个步骤立即运行 verify 命令；失败则停止，不推进下一步。

Wrap-up（全部任务完成后）：
```bash
mv tasks/sync-backup-tdd-20260529-0941 tasks/sync-backup-tdd-20260529-0941-done
git tag task/sync-backup-tdd-20260529-0941
```

---

## 阶段总览

| 阶段 | 名称 | 任务数 | 依赖 |
|------|------|--------|------|
| P1 | 数据基础层 | T-01 ~ T-04 | — |
| P2 | 脏标记系统 | T-05 ~ T-06 | P1 |
| P3 | WebDAV 传输层 | T-07 ~ T-13 | P1 |
| P4 | 传输抽象层 | T-14 ~ T-16 | P3 |
| P5 | 同步引擎核心 | T-17 ~ T-23 | P2, P4 |
| P6 | 后台调度 | T-24 ~ T-25 | P5 |
| P7 | 端到端加密 | T-26 ~ T-29 | P5 |
| P8 | 导出与导入 | T-30 ~ T-32 | P5, P7 |
| P9 | 界面层 | T-33 ~ T-41 | P5, P7 |

---

# P1 — 数据基础层

---

## T-01 · BookEntity 同步字段扩展

**Covers**: R-01, R-04  
**依赖**: —  
**预计耗时**: 1.5h

### 🔴 S1-1 · 写失败测试：BookEntity 包含同步字段

```kotlin
// test: data/BookEntityTest.kt
@Test
fun `BookEntity has bookKey, fastHash, isDirty, version, syncedVersion fields`() {
    val entity = BookEntity(
        id = 0L,
        title = "测试书",
        filePath = "/path/to/book.txt",
        fileType = "TXT",
        fileSize = 1000L,
        bookKey = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
        fastHash = "abc123",
        fullHash = null,
        isDirty = false,
        version = 1,
        syncedVersion = 0,
        updatedAt = 1710000000000L,
        remoteBookKey = null
    )
    assertThat(entity.bookKey).isEqualTo("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    assertThat(entity.isDirty).isFalse()
    assertThat(entity.version).isEqualTo(1)
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.BookEntityTest.BookEntity has bookKey*"
# 期望：FAILED（字段不存在 → 编译失败）
```

### 🟢 S1-2 · 实现：扩展 BookEntity

在 `data/local/entity/BookEntity.kt` 中添加字段：
- `bookKey: String` — 非空，远端唯一标识
- `fastHash: String` — 文件三点采样 hash
- `fullHash: String?` — 后台计算，可为空
- `isDirty: Boolean = false` — 本地修改标记
- `version: Int = 1` — 逻辑版本号，每次本地修改递增
- `syncedVersion: Int = 0` — 上次同步成功时的版本号
- `updatedAt: Long` — 本地修改时间戳
- `remoteBookKey: String?` — 用于旧格式兼容映射

提供 Room Migration：`Migration(N, N+1)` 添加上述列（均有默认值）。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.BookEntityTest"
# 期望：PASSED
```

### 🟢 S1-3 · 实现：Room Migration 测试

```kotlin
// test: data/migration/BookEntityMigrationTest.kt
@Test
fun `migration adds sync fields with defaults`() {
    // 使用 MigrationTestHelper 验证旧表行在迁移后有默认值
    // bookKey 默认 "" · isDirty 默认 0 · version 默认 1
}
```

**Verify**:
```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=*.BookEntityMigrationTest
# 期望：PASSED
```

### 🔵 S1-4 · Refactor

确认字段命名、注解、索引符合项目规范（`@ColumnInfo(name = "book_key")` 等）。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.BookEntityTest"
# 期望：PASSED（重构后保持绿色）
```

---

## T-02 · bookKey 在 importBook() 事务内生成

**Covers**: R-01  
**依赖**: T-01  
**预计耗时**: 1h

### 🔴 S2-1 · 写失败测试：importBook 时 bookKey 非空

```kotlin
// test: data/repository/BookRepositoryTest.kt
@Test
fun `importBook assigns non-empty bookKey immediately`() = runTest {
    val fakeFile = File(tempDir, "book.txt").also { it.writeText("content") }
    val result = bookRepository.importBook(fakeFile)
    assertThat(result.bookKey).isNotEmpty()
    assertThat(result.bookKey).matches("[0-9a-f-]{36}") // UUID v4 格式
}

@Test
fun `two independent importBook calls produce different bookKeys`() = runTest {
    val f1 = File(tempDir, "a.txt").also { it.writeText("aaa") }
    val f2 = File(tempDir, "b.txt").also { it.writeText("bbb") }
    val r1 = bookRepository.importBook(f1)
    val r2 = bookRepository.importBook(f2)
    assertThat(r1.bookKey).isNotEqualTo(r2.bookKey)
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.BookRepositoryTest.importBook assigns*"
# 期望：FAILED（importBook 未生成 bookKey）
```

### 🟢 S2-2 · 实现

在 `BookRepository.importBook()` 的数据库事务内：
```kotlin
val bookKey = UUID.randomUUID().toString()
val entity = BookEntity(..., bookKey = bookKey, ...)
bookDao.insert(entity)
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.BookRepositoryTest"
# 期望：PASSED
```

### 🔵 S2-3 · Refactor

确保 `importBook()` 是单个事务（`@Transaction`），bookKey 与 bookId 在同一次写入中落盘。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.BookRepositoryTest"
```

---

## T-03 · fastHash 三点采样

**Covers**: R-02  
**依赖**: T-01  
**预计耗时**: 1.5h

### 🔴 S3-1 · 写失败测试

```kotlin
// test: sync/hash/FastHasherTest.kt
@Test
fun `fastHash reads head, mid-third, tail of file`() {
    // 构造 30KB 文件：头部 "HEAD"×1024、中段 "MIDD"×1024、尾部 "TAIL"×1024
    // 验证 hash 随中段内容变化
    val file30kb = buildFile(headContent = "AAAA", midContent = "BBBB", tailContent = "CCCC", size = 30_000)
    val file30kbAlt = buildFile(headContent = "AAAA", midContent = "DDDD", tailContent = "CCCC", size = 30_000)
    val h1 = FastHasher.compute(file30kb)
    val h2 = FastHasher.compute(file30kbAlt)
    assertThat(h1).isNotEqualTo(h2)
}

@Test
fun `fastHash includes file size in digest`() {
    val f1 = createFileWithContent("same content", size = 1000)
    val f2 = createFileWithContent("same content", size = 2000)
    assertThat(FastHasher.compute(f1)).isNotEqualTo(FastHasher.compute(f2))
}

@Test
fun `fastHash is deterministic for same file`() {
    val file = createTempFile("book.txt")
    assertThat(FastHasher.compute(file)).isEqualTo(FastHasher.compute(file))
}

@Test
fun `fastHash on small file under 8KB does not throw`() {
    val tinyFile = createFileWithContent("tiny", size = 100)
    assertDoesNotThrow { FastHasher.compute(tinyFile) }
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.FastHasherTest"
# 期望：FAILED（FastHasher 不存在）
```

### 🟢 S3-2 · 实现 FastHasher

新建 `sync/hash/FastHasher.kt`，按 §6.2 三点采样算法：
- 段 1：文件头 4KB
- 段 2：offset = fileSize / 3 处 4KB（当 fileSize > 8192）
- 段 3：文件末尾 4KB（当 fileSize > 4096）
- 追加 8 字节 fileSize（Big-Endian Long）
- 返回 SHA-256 hex 字符串

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.FastHasherTest"
# 期望：PASSED（全部 4 个测试）
```

### 🔵 S3-3 · Refactor

提取 `readChunk(file, offset, maxBytes)` 私有函数；确保 RandomAccessFile 在 finally 中关闭（或使用 `use {}`）。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.FastHasherTest"
```

---

## T-04 · SyncStateMachine

**Covers**: R-03  
**依赖**: —  
**预计耗时**: 2h

### 🔴 S4-1 · 写失败测试：状态转换

```kotlin
// test: sync/state/SyncStateMachineTest.kt
@Test
fun `initial state is IDLE`() = runTest {
    val sm = SyncStateMachine()
    assertThat(sm.state.value).isEqualTo(SyncState.IDLE)
}

@Test
fun `IDLE to SCANNING is valid`() = runTest {
    val sm = SyncStateMachine()
    sm.transition(SyncState.SCANNING)
    assertThat(sm.state.value).isEqualTo(SyncState.SCANNING)
}

@Test
fun `SCANNING to DOWNLOADING is valid`() = runTest {
    val sm = SyncStateMachine()
    sm.transition(SyncState.SCANNING)
    sm.transition(SyncState.DOWNLOADING)
    assertThat(sm.state.value).isEqualTo(SyncState.DOWNLOADING)
}

@Test
fun `IDLE to UPLOADING is INVALID and throws`() = runTest {
    val sm = SyncStateMachine()
    assertThrows<IllegalArgumentException> {
        sm.transition(SyncState.UPLOADING)
    }
}

@Test
fun `canStartSync returns true only for IDLE, SUCCESS, FAILED`() = runTest {
    val sm = SyncStateMachine()
    assertThat(sm.canStartSync()).isTrue()
    sm.transition(SyncState.SCANNING)
    assertThat(sm.canStartSync()).isFalse()
}

@Test
fun `state emits via StateFlow`() = runTest {
    val sm = SyncStateMachine()
    sm.state.test {
        assertThat(awaitItem()).isEqualTo(SyncState.IDLE)
        sm.transition(SyncState.SCANNING)
        assertThat(awaitItem()).isEqualTo(SyncState.SCANNING)
        cancelAndIgnoreRemainingEvents()
    }
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.SyncStateMachineTest"
# 期望：FAILED（SyncStateMachine 不存在）
```

### 🟢 S4-2 · 实现

新建 `sync/state/SyncState.kt`（enum）和 `sync/state/SyncStateMachine.kt`：
- 使用 `MutableStateFlow<SyncState>` 持有状态
- `isValidTransition(from, to)` 实现完整状态转换图（见 §6.1）
- `canStartSync()` 返回当前状态是否允许开始新同步
- `retryAfter: Instant?` 字段用于限流计时

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.SyncStateMachineTest"
# 期望：PASSED（全部 6 个测试）
```

### 🔵 S4-3 · Refactor

提取合法转换表为 `companion object` 常量 map；使用 `require()` 替代手写 `if`。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.SyncStateMachineTest"
```

---

# P2 — 脏标记系统

---

## T-05 · PreferencesDirtyTracker

**Covers**: R-05  
**依赖**: —  
**预计耗时**: 1h

### 🔴 S5-1 · 写失败测试

```kotlin
// test: sync/dirty/PreferencesDirtyTrackerTest.kt
@Test
fun `initially has no dirty keys`() = runTest {
    val tracker = PreferencesDirtyTracker()
    assertThat(tracker.hasDirty()).isFalse()
    assertThat(tracker.dirtyKeys.value).isEmpty()
}

@Test
fun `markDirty adds key`() = runTest {
    val tracker = PreferencesDirtyTracker()
    tracker.markDirty("fontSize")
    assertThat(tracker.dirtyKeys.value).containsExactly("fontSize")
}

@Test
fun `markDirty multiple keys accumulates`() = runTest {
    val tracker = PreferencesDirtyTracker()
    tracker.markDirty("fontSize")
    tracker.markDirty("themeMode")
    assertThat(tracker.dirtyKeys.value).containsExactlyInAnyOrder("fontSize", "themeMode")
}

@Test
fun `clearDirty removes all keys`() = runTest {
    val tracker = PreferencesDirtyTracker()
    tracker.markDirty("fontSize")
    tracker.clearDirty()
    assertThat(tracker.hasDirty()).isFalse()
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.PreferencesDirtyTrackerTest"
# 期望：FAILED
```

### 🟢 S5-2 · 实现

新建 `sync/dirty/PreferencesDirtyTracker.kt`，按 §6.2 第一层实现。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.PreferencesDirtyTrackerTest"
# 期望：PASSED
```

---

## T-06 · Room 实体脏标记（书签、笔记、进度）

**Covers**: R-04  
**依赖**: T-01  
**预计耗时**: 2h

### 🔴 S6-1 · 写失败测试：BookmarkEntity 脏标记

```kotlin
// test: data/BookmarkEntityTest.kt
@Test
fun `BookmarkEntity default isDirty is true for new items`() {
    val bm = BookmarkEntity(
        id = "uuid-1",
        bookId = 1L,
        byteOffset = 1000L,
        isDirty = true,
        version = 1,
        syncedVersion = 0,
        deleted = false,
        updatedAt = System.currentTimeMillis(),
        mergeSource = null
    )
    assertThat(bm.isDirty).isTrue()
    assertThat(bm.deleted).isFalse()
}

@Test
fun `NoteEntity has isDirty and deleted fields`() {
    val note = NoteEntity(
        id = "note-uuid",
        bookId = 1L,
        byteStart = 100L,
        byteEnd = 200L,
        content = "批注",
        isDirty = true,
        version = 1,
        syncedVersion = 0,
        deleted = false,
        updatedAt = System.currentTimeMillis(),
        mergeSource = null
    )
    assertThat(note.isDirty).isTrue()
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.BookmarkEntityTest"
# 期望：FAILED（字段不存在）
```

### 🟢 S6-2 · 实现

扩展 `BookmarkEntity`、`NoteEntity`、`ReadingProgressEntity`：
- `isDirty: Boolean = true`
- `version: Int = 1`
- `syncedVersion: Int = 0`
- `deleted: Boolean = false`（tombstone 标记）
- `updatedAt: Long`
- `mergeSource: String?`（"local" | "remote" | "merged"，仅用于排错）

提供对应 Room Migration。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.BookmarkEntityTest"
./gradlew :app:testDebugUnitTest --tests "*.NoteEntityTest"
# 期望：PASSED
```

### 🟢 S6-3 · DAO 查询：按 isDirty 筛选

```kotlin
// test: data/dao/BookmarkDaoTest.kt
@Test
fun `queryDirtyBookmarks returns only isDirty=true`() = runTest {
    // 插入 2 条 dirty + 1 条 clean
    // 断言 dao.queryDirty() 返回 2 条
}
```

**Verify**:
```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=*.BookmarkDaoTest
# 期望：PASSED
```

---

# P3 — WebDAV 传输层

---

## T-07 · WebDavClient 认证拦截器

**Covers**: R-12  
**依赖**: —  
**预计耗时**: 2h

### 🔴 S7-1 · 写失败测试：Basic Auth header

```kotlin
// test: network/webdav/WebDavClientAuthTest.kt
@Test
fun `BasicAuthInterceptor adds Authorization header`() {
    val interceptor = BasicAuthInterceptor("user", "pass")
    val mockChain = mockChain(url = "https://dav.example.com/")
    interceptor.intercept(mockChain)
    val request = mockChain.capturedRequest
    assertThat(request.header("Authorization")).startsWith("Basic ")
}

@Test
fun `AdaptiveAuthInterceptor retries with Digest on 401 WWW-Authenticate Digest`() {
    // MockWebServer: 第一次返回 401 + WWW-Authenticate: Digest ..., 第二次返回 200
    // 断言客户端自动切换到 Digest
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.WebDavClientAuthTest"
# 期望：FAILED
```

### 🟢 S7-2 · 实现

新建：
- `network/webdav/auth/BasicAuthInterceptor.kt`
- `network/webdav/auth/DigestAuthInterceptor.kt`
- `network/webdav/auth/AdaptiveAuthInterceptor.kt`
- `network/webdav/auth/AuthType.kt`（enum: BASIC, DIGEST, BASIC_OR_DIGEST）

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.WebDavClientAuthTest"
# 期望：PASSED
```

---

## T-08 · WebDavClient PROPFIND

**Covers**: R-08  
**依赖**: T-07  
**预计耗时**: 2.5h

### 🔴 S8-1 · 写失败测试

```kotlin
// test: network/webdav/WebDavClientPropfindTest.kt
@Test
fun `propfind depth 1 returns list of resources`() = runTest {
    // MockWebServer 返回标准 207 Multi-Status XML
    val server = MockWebServer()
    server.enqueue(MockResponse().setResponseCode(207).setBody(PROPFIND_RESPONSE_XML))
    val client = buildTestClient(server)
    val resources = client.propfind("/ShuLiReader/", depth = 1)
    assertThat(resources).hasSize(3)
    assertThat(resources[0].path).isEqualTo("/ShuLiReader/manifest.json")
    assertThat(resources[0].etag).isEqualTo("\"abc123\"")
    assertThat(resources[0].contentLength).isEqualTo(256L)
}

@Test
fun `propfind 401 throws WebDavAuthException`() = runTest {
    server.enqueue(MockResponse().setResponseCode(401))
    assertThrows<WebDavAuthException> { client.propfind("/", depth = 0) }
}

@Test
fun `propfind 404 throws WebDavNotFoundException`() = runTest {
    server.enqueue(MockResponse().setResponseCode(404))
    assertThrows<WebDavNotFoundException> { client.propfind("/notexist/", depth = 0) }
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.WebDavClientPropfindTest"
# 期望：FAILED
```

### 🟢 S8-2 · 实现

新建 `network/webdav/WebDavClient.kt`：
- `propfind(path, depth)` → `List<ResourceInfo>`
- 内置 `WebDavXmlParser` 解析 207 Multi-Status XML（不引入 Jsoup，使用 Android 内置 XmlPullParser）
- `ResourceInfo(path, etag, contentLength, lastModified, isDirectory)`

新建异常层级：
- `WebDavException` (基类)
- `WebDavAuthException` (401/403)
- `WebDavNotFoundException` (404)
- `WebDavConflictException` (409/412)
- `WebDavRateLimitException` (429/503)

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.WebDavClientPropfindTest"
# 期望：PASSED
```

---

## T-09 · WebDavClient MKCOL / HEAD / DELETE

**Covers**: R-08  
**依赖**: T-08  
**预计耗时**: 1.5h

### 🔴 S9-1 · 写失败测试

```kotlin
// test: network/webdav/WebDavClientMkcolTest.kt
@Test
fun `mkcol sends MKCOL method`() = runTest {
    server.enqueue(MockResponse().setResponseCode(201))
    client.mkcol("/ShuLiReader/books/")
    val recorded = server.takeRequest()
    assertThat(recorded.method).isEqualTo("MKCOL")
    assertThat(recorded.path).isEqualTo("/ShuLiReader/books/")
}

@Test
fun `head returns etag and content length`() = runTest {
    server.enqueue(MockResponse().setResponseCode(200)
        .addHeader("ETag", "\"abc123\"")
        .addHeader("Content-Length", "1024"))
    val meta = client.head("/ShuLiReader/manifest.json")
    assertThat(meta?.etag).isEqualTo("\"abc123\"")
    assertThat(meta?.contentLength).isEqualTo(1024L)
}

@Test
fun `head on missing file returns null`() = runTest {
    server.enqueue(MockResponse().setResponseCode(404))
    val meta = client.head("/notexist.json")
    assertThat(meta).isNull()
}

@Test
fun `delete sends DELETE method`() = runTest {
    server.enqueue(MockResponse().setResponseCode(204))
    client.delete("/ShuLiReader/state/bookkey123.json")
    assertThat(server.takeRequest().method).isEqualTo("DELETE")
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.WebDavClientMkcolTest"
# 期望：FAILED
```

### 🟢 S9-2 · 实现

在 `WebDavClient.kt` 追加：`mkcol(path)`、`head(path): ResourceMetadata?`、`delete(path)`。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.WebDavClientMkcolTest"
# 期望：PASSED
```

---

## T-10 · WebDavClient GET / PUT 流式读写

**Covers**: R-08  
**依赖**: T-08  
**预计耗时**: 2h

### 🔴 S10-1 · 写失败测试

```kotlin
// test: network/webdav/WebDavClientGetPutTest.kt
@Test
fun `get returns byte content`() = runTest {
    val body = """{"schemaVersion":2}""".toByteArray()
    server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(body)))
    val result = client.get("/ShuLiReader/manifest.json")
    assertThat(result).isEqualTo(body)
}

@Test
fun `get returns null for 404`() = runTest {
    server.enqueue(MockResponse().setResponseCode(404))
    assertThat(client.get("/notexist.json")).isNull()
}

@Test
fun `put sends content with correct content-type`() = runTest {
    server.enqueue(MockResponse().setResponseCode(201))
    client.put("/ShuLiReader/manifest.json", """{"v":1}""".toByteArray())
    val request = server.takeRequest()
    assertThat(request.method).isEqualTo("PUT")
    assertThat(request.getHeader("Content-Type")).contains("application/json")
}

@Test
fun `put with ifMatch sends If-Match header`() = runTest {
    server.enqueue(MockResponse().setResponseCode(204))
    client.put("/path.json", data = ByteArray(0), ifMatch = "\"etag123\"")
    assertThat(server.takeRequest().getHeader("If-Match")).isEqualTo("\"etag123\"")
}

@Test
fun `put returns 412 throws WebDavConflictException`() = runTest {
    server.enqueue(MockResponse().setResponseCode(412))
    assertThrows<WebDavConflictException> {
        client.put("/path.json", ByteArray(0), ifMatch = "\"stale\"")
    }
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.WebDavClientGetPutTest"
# 期望：FAILED
```

### 🟢 S10-2 · 实现

在 `WebDavClient.kt` 追加：`get(path): ByteArray?`、`put(path, data, ifMatch?, contentType?)`。  
PUT 使用 OkHttp `RequestBody.create()`；GET 使用 `ResponseBody.bytes()`。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.WebDavClientGetPutTest"
# 期望：PASSED
```

---

## T-11 · ETag 与 If-Match 乐观锁

**Covers**: R-09  
**依赖**: T-10  
**预计耗时**: 1h

### 🔴 S11-1 · 写失败测试

```kotlin
// test: network/webdav/WebDavETagTest.kt
@Test
fun `put without ifMatch does not send If-Match header`() = runTest {
    server.enqueue(MockResponse().setResponseCode(201))
    client.put("/path.json", ByteArray(0))
    assertThat(server.takeRequest().getHeader("If-Match")).isNull()
}

@Test
fun `put with ifNoneMatch sends If-None-Match header`() = runTest {
    server.enqueue(MockResponse().setResponseCode(201))
    client.put("/path.json", ByteArray(0), ifNoneMatch = "*")
    assertThat(server.takeRequest().getHeader("If-None-Match")).isEqualTo("*")
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.WebDavETagTest"
# 期望：FAILED（ifNoneMatch 参数不存在）
```

### 🟢 S11-2 · 实现

扩展 `put()` 签名加入 `ifNoneMatch: String?` 参数。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.WebDavETagTest"
# 期望：PASSED
```

---

## T-12 · HTTP 错误分类

**Covers**: R-10  
**依赖**: T-08  
**预计耗时**: 1.5h

### 🔴 S12-1 · 写失败测试

```kotlin
// test: network/webdav/WebDavErrorHandlingTest.kt
@ParameterizedTest
@ValueSource(ints = [401, 403, 404, 409, 412, 423, 429, 503])
fun `each HTTP error maps to correct exception type`(code: Int) = runTest {
    server.enqueue(MockResponse().setResponseCode(code))
    val exception = assertThrows<WebDavException> { client.get("/any.json") }
    when (code) {
        401, 403 -> assertThat(exception).isInstanceOf(WebDavAuthException::class.java)
        404      -> assertThat(exception).isInstanceOf(WebDavNotFoundException::class.java)
        409, 412 -> assertThat(exception).isInstanceOf(WebDavConflictException::class.java)
        423      -> assertThat(exception).isInstanceOf(WebDavLockedException::class.java)
        429, 503 -> assertThat(exception).isInstanceOf(WebDavRateLimitException::class.java)
    }
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.WebDavErrorHandlingTest"
# 期望：FAILED（423/WebDavLockedException 等不存在）
```

### 🟢 S12-2 · 实现

完善异常层级，在 `WebDavClient` 内部统一 `handleHttpError(code)` 函数。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.WebDavErrorHandlingTest"
# 期望：PASSED（8 个参数化测试全绿）
```

---

## T-13 · 限流处理与指数退避

**Covers**: R-11  
**依赖**: T-12  
**预计耗时**: 2h

### 🔴 S13-1 · 写失败测试

```kotlin
// test: sync/throttle/RateLimitHandlerTest.kt
@Test
fun `RateLimitHandler extracts Retry-After from 429 response`() = runTest {
    val fakeException = WebDavRateLimitException(retryAfterSeconds = 300)
    val handler = RateLimitHandler()
    val delay = handler.computeWaitMs(fakeException, attempt = 1)
    assertThat(delay).isEqualTo(300_000L)
}

@Test
fun `without Retry-After header uses exponential backoff`() = runTest {
    val handler = RateLimitHandler()
    val d1 = handler.computeWaitMs(WebDavRateLimitException(null), attempt = 1)
    val d2 = handler.computeWaitMs(WebDavRateLimitException(null), attempt = 2)
    val d3 = handler.computeWaitMs(WebDavRateLimitException(null), attempt = 3)
    assertThat(d2).isGreaterThan(d1)
    assertThat(d3).isGreaterThan(d2)
    assertThat(d3).isLessThan(3_600_000L) // cap at 1h
}

@Test
fun `RequestThrottler enforces 600 requests per 30 min limit`() = runTest {
    val throttler = RequestThrottler(maxRequests = 600, windowMs = 30 * 60 * 1000)
    repeat(600) { throttler.recordRequest() }
    assertThat(throttler.isThrottled()).isTrue()
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.RateLimitHandlerTest"
# 期望：FAILED
```

### 🟢 S13-2 · 实现

新建 `sync/throttle/RateLimitHandler.kt` 和 `sync/throttle/RequestThrottler.kt`。  
`WebDavRateLimitException` 增加 `retryAfterSeconds: Long?` 字段。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.RateLimitHandlerTest"
# 期望：PASSED
```

---

# P4 — 传输抽象层

---

## T-14 · SyncTransport 接口与数据模型

**Covers**: R-13  
**依赖**: —  
**预计耗时**: 1h

### 🔴 S14-1 · 写失败测试

```kotlin
// test: sync/transport/SyncTransportContractTest.kt
// 验证接口方法签名完整
@Test
fun `SyncTransport interface has all required methods`() {
    // 通过反射验证接口定义（编译级验证）
    val methods = SyncTransport::class.java.declaredMethods.map { it.name }
    assertThat(methods).containsAll(listOf("read","write","delete","list","exists","getMetadata"))
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.SyncTransportContractTest"
# 期望：FAILED（接口不存在）
```

### 🟢 S14-2 · 实现

新建 `sync/transport/SyncTransport.kt` (interface)、`ResourceInfo.kt`、`ResourceMetadata.kt`（含 etag, lastModified, contentLength）。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.SyncTransportContractTest"
# 期望：PASSED
```

---

## T-15 · WebDavTransport 实现

**Covers**: R-14  
**依赖**: T-08 ~ T-13, T-14  
**预计耗时**: 2h

### 🔴 S15-1 · 写失败测试

```kotlin
// test: sync/transport/WebDavTransportTest.kt
@Test
fun `read delegates to WebDavClient.get`() = runTest {
    val mockClient = mockk<WebDavClient> {
        coEvery { get("/ShuLiReader/manifest.json") } returns """{"v":1}""".toByteArray()
    }
    val transport = WebDavTransport(mockClient, rootPath = "/ShuLiReader/", crypto = null)
    val result = transport.read("manifest.json")
    assertThat(result).isEqualTo("""{"v":1}""".toByteArray())
}

@Test
fun `write delegates to WebDavClient.put`() = runTest {
    val mockClient = mockk<WebDavClient>(relaxed = true)
    val transport = WebDavTransport(mockClient, rootPath = "/ShuLiReader/", crypto = null)
    transport.write("state/book1.json", """{"progress":0.5}""".toByteArray())
    coVerify { mockClient.put("/ShuLiReader/state/book1.json", any(), any(), any()) }
}

@Test
fun `ensureDirectories creates required dirs`() = runTest {
    val mockClient = mockk<WebDavClient>(relaxed = true)
    val transport = WebDavTransport(mockClient, rootPath = "/ShuLiReader/", crypto = null)
    transport.ensureDirectories()
    coVerify { mockClient.mkcol("/ShuLiReader/books/") }
    coVerify { mockClient.mkcol("/ShuLiReader/state/") }
    coVerify { mockClient.mkcol("/ShuLiReader/bookmarks/") }
    coVerify { mockClient.mkcol("/ShuLiReader/notes/") }
    coVerify { mockClient.mkcol("/ShuLiReader/config/") }
    coVerify { mockClient.mkcol("/ShuLiReader/device/") }
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.WebDavTransportTest"
# 期望：FAILED
```

### 🟢 S15-2 · 实现

新建 `sync/transport/WebDavTransport.kt` 实现 `SyncTransport`，在路径前拼接 `rootPath`，调用对应的 `WebDavClient` 方法。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.WebDavTransportTest"
# 期望：PASSED
```

---

## T-16 · LocalFileTransport 与原子写入

**Covers**: R-15  
**依赖**: T-14  
**预计耗时**: 2h

### 🔴 S16-1 · 写失败测试

```kotlin
// test: sync/transport/LocalFileTransportTest.kt
@Test
fun `write creates file at correct path`() = runTest {
    val rootDir = tempDir.resolve("ShuLiReader").also { it.mkdirs() }
    val transport = LocalFileTransport(rootDir)
    transport.write("manifest.json", """{"v":2}""".toByteArray())
    val file = rootDir.resolve("manifest.json")
    assertThat(file.exists()).isTrue()
    assertThat(file.readText()).isEqualTo("""{"v":2}""")
}

@Test
fun `write is atomic - no temp file left on success`() = runTest {
    val transport = LocalFileTransport(tempDir)
    transport.write("test.json", "data".toByteArray())
    val tmpFiles = tempDir.listFiles { f -> f.name.startsWith(".tmp-") }
    assertThat(tmpFiles).isEmpty()
}

@Test
fun `read returns null for non-existent file`() = runTest {
    val transport = LocalFileTransport(tempDir)
    assertThat(transport.read("doesnotexist.json")).isNull()
}

@Test
fun `list returns files in directory`() = runTest {
    tempDir.resolve("sub").mkdirs()
    tempDir.resolve("sub/a.json").writeText("{}")
    tempDir.resolve("sub/b.json").writeText("{}")
    val transport = LocalFileTransport(tempDir)
    val resources = transport.list("sub")
    assertThat(resources.map { it.path }).containsExactlyInAnyOrder("sub/a.json", "sub/b.json")
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.LocalFileTransportTest"
# 期望：FAILED
```

### 🟢 S16-2 · 实现

新建 `sync/transport/LocalFileTransport.kt` 和 `sync/transport/AtomicFileWriter.kt`（写入临时文件 + fsync + rename）。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.LocalFileTransportTest"
# 期望：PASSED
```

---

# P5 — 同步引擎核心

---

## T-17 · ManifestManager（含 Mutex 串行化）

**Covers**: R-06, R-07, R-16  
**依赖**: T-14  
**预计耗时**: 2.5h

### 🔴 S17-1 · 写失败测试

```kotlin
// test: sync/manifest/ManifestManagerTest.kt
@Test
fun `readManifest returns null if remote file absent`() = runTest {
    val transport = mockk<SyncTransport> { coEvery { read("manifest.json") } returns null }
    val manager = ManifestManager(transport)
    assertThat(manager.readManifest()).isNull()
}

@Test
fun `readManifest parses schemaVersion and version`() = runTest {
    val json = """{"schemaVersion":2,"updatedAt":1710000000000,"updatedBy":"dev1","version":42,"bookCount":156}"""
    val transport = mockk<SyncTransport> { coEvery { read("manifest.json") } returns json.toByteArray() }
    val manager = ManifestManager(transport)
    val manifest = manager.readManifest()
    assertThat(manifest?.schemaVersion).isEqualTo(2)
    assertThat(manifest?.version).isEqualTo(42)
    assertThat(manifest?.bookCount).isEqualTo(156)
}

@Test
fun `writeManifest is serialized - concurrent writes do not interleave`() = runTest {
    val writes = mutableListOf<Int>()
    val transport = mockk<SyncTransport>(relaxed = true)
    val manager = ManifestManager(transport)
    val jobs = (1..10).map { i ->
        launch { manager.updateManifest { it.copy(version = i).also { writes += i } } }
    }
    jobs.joinAll()
    // 所有写入都完成，无异常
    assertThat(writes).hasSize(10)
    coVerify(exactly = 10) { transport.write("manifest.json", any()) }
}

@Test
fun `manifest must not contain books array`() = runTest {
    // 模拟旧格式含 books 字段的 JSON 被拒绝解析
    val oldFormat = """{"schemaVersion":1,"books":[{"bookKey":"abc"}]}"""
    val transport = mockk<SyncTransport> { coEvery { read("manifest.json") } returns oldFormat.toByteArray() }
    val manager = ManifestManager(transport)
    // V4 manifest 解析应忽略 books 字段（不出现在 SyncManifest 模型中）
    val manifest = manager.readManifest()
    assertThat(manifest).isNotNull()
    // SyncManifest 数据类不含 books 字段
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.ManifestManagerTest"
# 期望：FAILED
```

### 🟢 S17-2 · 实现

新建 `sync/manifest/SyncManifest.kt`（数据类，无 books 字段）和 `sync/manifest/ManifestManager.kt`：
- `readManifest()`: 调用 transport.read("manifest.json")，解析 JSON
- `updateManifest(update: (SyncManifest) -> SyncManifest)`: 用 `Mutex.withLock` 保护完整的读-改-写流程

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.ManifestManagerTest"
# 期望：PASSED
```

---

## T-18 · ConflictResolver — 进度冲突

**Covers**: R-17  
**依赖**: T-06  
**预计耗时**: 2h

### 🔴 S18-1 · 写失败测试

```kotlin
// test: sync/conflict/ProgressConflictResolverTest.kt
@Test
fun `higher logicalVersion wins regardless of timestamp`() {
    val local  = BookState(version = 5, updatedAt = 1000L, byteOffset = 200L, fileType = "TXT")
    val remote = BookState(version = 3, updatedAt = 9999L, byteOffset = 500L, fileType = "TXT")
    val result = ConflictResolver.resolveProgress(local, remote)
    assertThat(result.byteOffset).isEqualTo(200L) // local wins (higher version)
}

@Test
fun `same version falls back to timestamp`() {
    val local  = BookState(version = 2, updatedAt = 1000L, byteOffset = 200L, fileType = "TXT")
    val remote = BookState(version = 2, updatedAt = 2000L, byteOffset = 500L, fileType = "TXT")
    val result = ConflictResolver.resolveProgress(local, remote)
    assertThat(result.byteOffset).isEqualTo(500L) // remote wins (later timestamp)
}

@Test
fun `same version same timestamp TXT uses larger byteOffset`() {
    val local  = BookState(version = 1, updatedAt = 1000L, byteOffset = 200L, fileType = "TXT")
    val remote = BookState(version = 1, updatedAt = 1000L, byteOffset = 500L, fileType = "TXT")
    val result = ConflictResolver.resolveProgress(local, remote)
    assertThat(result.byteOffset).isEqualTo(500L)
}

@Test
fun `gap under 5 percent does NOT trigger user conflict dialog`() {
    // total = 1000, local = 900, remote = 950 → diff = 5%
    val local  = BookState(version = 1, updatedAt = 1000L, byteOffset = 900L, fileType = "TXT", totalSize = 1000L)
    val remote = BookState(version = 1, updatedAt = 2000L, byteOffset = 950L, fileType = "TXT", totalSize = 1000L)
    val decision = ConflictResolver.classifyProgressConflict(local, remote)
    assertThat(decision).isEqualTo(ConflictDecision.AUTO_MERGE)
}

@Test
fun `gap over 5 percent triggers REQUIRE_USER_INPUT`() {
    val local  = BookState(version = 1, updatedAt = 1000L, byteOffset = 200L, fileType = "TXT", totalSize = 1000L)
    val remote = BookState(version = 1, updatedAt = 2000L, byteOffset = 700L, fileType = "TXT", totalSize = 1000L)
    val decision = ConflictResolver.classifyProgressConflict(local, remote)
    assertThat(decision).isEqualTo(ConflictDecision.REQUIRE_USER_INPUT)
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.ProgressConflictResolverTest"
# 期望：FAILED
```

### 🟢 S18-2 · 实现

新建 `sync/conflict/ConflictResolver.kt`：
- `resolveProgress(local, remote): BookState`
- `classifyProgressConflict(local, remote): ConflictDecision`（AUTO_MERGE | REQUIRE_USER_INPUT）
- `ConflictDecision` enum
- `BookState` 数据类

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.ProgressConflictResolverTest"
# 期望：PASSED（全部 5 个测试）
```

---

## T-19 · ConflictResolver — 书签/笔记 UUID Merge + Tombstone

**Covers**: R-18, R-20  
**依赖**: T-06, T-18  
**预计耗时**: 2.5h

### 🔴 S19-1 · 写失败测试

```kotlin
// test: sync/conflict/BookmarkMergeTest.kt
@Test
fun `merge keeps items from both local and remote by UUID`() {
    val local  = listOf(BookmarkDto(id = "A", byteOffset = 100, updatedAt = 1000, deleted = false))
    val remote = listOf(BookmarkDto(id = "B", byteOffset = 200, updatedAt = 2000, deleted = false))
    val merged = ConflictResolver.mergeBookmarks(local, remote)
    assertThat(merged.map { it.id }).containsExactlyInAnyOrder("A", "B")
}

@Test
fun `same UUID remote timestamp wins`() {
    val local  = listOf(BookmarkDto(id = "A", byteOffset = 100, updatedAt = 1000, deleted = false))
    val remote = listOf(BookmarkDto(id = "A", byteOffset = 200, updatedAt = 2000, deleted = false))
    val merged = ConflictResolver.mergeBookmarks(local, remote)
    assertThat(merged.single().byteOffset).isEqualTo(200)
}

@Test
fun `deleted tombstone from remote propagates to merged result`() {
    val local  = listOf(BookmarkDto(id = "A", byteOffset = 100, updatedAt = 1000, deleted = false))
    val remote = listOf(BookmarkDto(id = "A", byteOffset = 100, updatedAt = 2000, deleted = true))
    val merged = ConflictResolver.mergeBookmarks(local, remote)
    assertThat(merged.single().deleted).isTrue()
}

@Test
fun `canCompactTombstone returns false if any device has not synced after deletedAt`() {
    val tombstone = BookmarkDto(id = "A", deleted = true, updatedAt = 5000L)
    val devices = listOf(
        DeviceInfo(deviceId = "d1", lastSyncAt = 6000L),
        DeviceInfo(deviceId = "d2", lastSyncAt = 4000L) // 未同步
    )
    assertThat(ConflictResolver.canCompactTombstone(tombstone, devices)).isFalse()
}

@Test
fun `canCompactTombstone returns true if all devices synced after deletedAt`() {
    val tombstone = BookmarkDto(id = "A", deleted = true, updatedAt = 5000L)
    val devices = listOf(
        DeviceInfo(deviceId = "d1", lastSyncAt = 6000L),
        DeviceInfo(deviceId = "d2", lastSyncAt = 7000L)
    )
    assertThat(ConflictResolver.canCompactTombstone(tombstone, devices)).isTrue()
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.BookmarkMergeTest"
# 期望：FAILED
```

### 🟢 S19-2 · 实现

在 `ConflictResolver.kt` 追加 `mergeBookmarks()`、`mergeNotes()`、`canCompactTombstone()`。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.BookmarkMergeTest"
# 期望：PASSED
```

---

## T-20 · Config key-level merge

**Covers**: R-19  
**依赖**: T-05, T-18  
**预计耗时**: 1.5h

### 🔴 S20-1 · 写失败测试

```kotlin
// test: sync/conflict/ConfigMergeTest.kt
@Test
fun `dirty key uses local value, non-dirty key uses remote value`() {
    val local  = UserPreferences(fontSize = 18f, themeMode = "dark", lineSpacing = 1.5f)
    val remote = UserPreferences(fontSize = 14f, themeMode = "light", lineSpacing = 2.0f)
    val dirtyKeys = setOf("fontSize") // 只改了字号
    val merged = ConflictResolver.mergePreferences(local, remote, dirtyKeys)
    assertThat(merged.fontSize).isEqualTo(18f)       // local 胜出（dirty）
    assertThat(merged.themeMode).isEqualTo("light")   // remote 胜出（not dirty）
    assertThat(merged.lineSpacing).isEqualTo(2.0f)    // remote 胜出
}

@Test
fun `empty dirtyKeys uses all remote values`() {
    val local  = UserPreferences(fontSize = 18f, themeMode = "dark", lineSpacing = 1.5f)
    val remote = UserPreferences(fontSize = 14f, themeMode = "light", lineSpacing = 2.0f)
    val merged = ConflictResolver.mergePreferences(local, remote, emptySet())
    assertThat(merged.fontSize).isEqualTo(14f)
    assertThat(merged.themeMode).isEqualTo("light")
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.ConfigMergeTest"
# 期望：FAILED
```

### 🟢 S20-2 · 实现

在 `ConflictResolver.kt` 追加 `mergePreferences(local, remote, localDirtyKeys)` 逐字段比较。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.ConfigMergeTest"
# 期望：PASSED
```

---

## T-21 · Device 信息同步

**Covers**: R-21  
**依赖**: T-14  
**预计耗时**: 1h

### 🔴 S21-1 · 写失败测试

```kotlin
// test: sync/device/DeviceSyncManagerTest.kt
@Test
fun `uploadDeviceInfo writes to device/{deviceId}.json`() = runTest {
    val transport = mockk<SyncTransport>(relaxed = true)
    val manager = DeviceSyncManager(transport, deviceId = "dev-123", model = "Pixel 7", appVersion = "1.2.3")
    manager.uploadDeviceInfo(lastSyncAt = 1710000000000L)
    coVerify {
        transport.write(
            eq("device/dev-123.json"),
            match { String(it).contains("\"model\":\"Pixel 7\"") }
        )
    }
}

@Test
fun `listDevices returns all device infos from remote`() = runTest {
    val transport = mockk<SyncTransport> {
        coEvery { list("device") } returns listOf(
            ResourceInfo(path = "device/dev-1.json"),
            ResourceInfo(path = "device/dev-2.json")
        )
        coEvery { read("device/dev-1.json") } returns """{"deviceId":"dev-1","model":"Pixel 7","lastSyncAt":1000}""".toByteArray()
        coEvery { read("device/dev-2.json") } returns """{"deviceId":"dev-2","model":"小米14","lastSyncAt":2000}""".toByteArray()
    }
    val manager = DeviceSyncManager(transport, "dev-1", "Pixel 7", "1.0")
    val devices = manager.listDevices()
    assertThat(devices).hasSize(2)
    assertThat(devices.map { it.model }).containsExactlyInAnyOrder("Pixel 7", "小米14")
}

@Test
fun `removeDevice deletes remote file`() = runTest {
    val transport = mockk<SyncTransport>(relaxed = true)
    val manager = DeviceSyncManager(transport, "dev-1", "Pixel 7", "1.0")
    manager.removeDevice("dev-2")
    coVerify { transport.delete("device/dev-2.json") }
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.DeviceSyncManagerTest"
# 期望：FAILED
```

### 🟢 S21-2 · 实现

新建 `sync/device/DeviceSyncManager.kt` 和 `sync/device/DeviceInfo.kt`（含 deviceId, model, manufacturer, appVersion, lastSyncAt）。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.DeviceSyncManagerTest"
# 期望：PASSED
```

---

## T-22 · SyncEngine 核心编排

**Covers**: R-22  
**依赖**: T-17 ~ T-21  
**预计耗时**: 3h

### 🔴 S22-1 · 写失败测试

```kotlin
// test: sync/engine/SyncEngineTest.kt
@Test
fun `sync uploads dirty bookmarks and clears isDirty flag`() = runTest {
    val fakeTransport = FakeTransport()
    val engine = buildTestSyncEngine(fakeTransport)
    // 准备 1 本书有脏书签
    insertDirtyBookmark(bookKey = "bk1", id = "uuid-A")
    engine.sync(fakeTransport, SyncOptions())
    // 验证 bookmarks/bk1.json 已被写入
    assertThat(fakeTransport.written["bookmarks/bk1.json"]).isNotNull()
    // 验证 isDirty 已清除
    assertThat(bookmarkDao.get("uuid-A").isDirty).isFalse()
}

@Test
fun `sync downloads remote-only bookmarks`() = runTest {
    // FakeTransport 有 bookmarks/bk2.json，本地无 bk2 数据
    val fakeTransport = FakeTransport()
    fakeTransport.put("manifest.json", buildManifest(version = 10).toJson())
    fakeTransport.put("bookmarks/bk2.json", buildBookmarks(bookKey = "bk2").toJson())
    val engine = buildTestSyncEngine(fakeTransport)
    engine.sync(fakeTransport, SyncOptions())
    // 验证本地 DB 已有 bk2 的书签
    assertThat(bookmarkDao.findByBookKey("bk2")).isNotEmpty()
}

@Test
fun `sync transitions state machine correctly`() = runTest {
    val sm = SyncStateMachine()
    val states = mutableListOf<SyncState>()
    sm.state.onEach { states += it }.launchIn(this)
    val engine = buildTestSyncEngine(FakeTransport(), stateMachine = sm)
    engine.sync(FakeTransport(), SyncOptions())
    assertThat(states).containsSequence(
        SyncState.IDLE, SyncState.SCANNING, SyncState.DOWNLOADING,
        SyncState.MERGING, SyncState.UPLOADING, SyncState.SUCCESS
    )
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.SyncEngineTest"
# 期望：FAILED（SyncEngine 不存在）
```

### 🟢 S22-2 · 实现

新建 `sync/engine/SyncEngine.kt`，实现 §6.3 完整变更检测流程（9 个步骤），并驱动 `SyncStateMachine`。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.SyncEngineTest"
# 期望：PASSED
```

---

## T-23 · SyncOrchestrator — BOTH 模式独立失败语义

**Covers**: R-23  
**依赖**: T-22  
**预计耗时**: 1.5h

### 🔴 S23-1 · 写失败测试

```kotlin
// test: sync/engine/SyncOrchestratorTest.kt
@Test
fun `BOTH mode: local fails, cloud still executes`() = runTest {
    val failingLocal = mockk<LocalFileTransport> { coEvery { read(any()) } throws IOException("disk full") }
    val successCloud = mockk<WebDavTransport>(relaxed = true)
    val engine = mockk<SyncEngine>(relaxed = true)
    val orchestrator = SyncOrchestrator(successCloud, failingLocal, engine)
    val result = orchestrator.sync(SyncTarget.BOTH)
    assertThat(result.localResult?.isFailure).isTrue()
    assertThat(result.cloudResult?.isSuccess).isTrue()
}

@Test
fun `BOTH mode: cloud fails, local result still returned`() = runTest {
    val successLocal = mockk<LocalFileTransport>(relaxed = true)
    val failingCloud = mockk<WebDavTransport> { coEvery { read(any()) } throws WebDavAuthException() }
    val engine = mockk<SyncEngine>(relaxed = true)
    val orchestrator = SyncOrchestrator(failingCloud, successLocal, engine)
    val result = orchestrator.sync(SyncTarget.BOTH)
    assertThat(result.cloudResult?.isFailure).isTrue()
    assertThat(result.localResult?.isSuccess).isTrue()
}

@Test
fun `CLOUD mode: null local result`() = runTest {
    val cloud = mockk<WebDavTransport>(relaxed = true)
    val engine = mockk<SyncEngine>(relaxed = true)
    val orchestrator = SyncOrchestrator(cloud, localTransport = null, engine)
    val result = orchestrator.sync(SyncTarget.CLOUD)
    assertThat(result.localResult).isNull()
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.SyncOrchestratorTest"
# 期望：FAILED
```

### 🟢 S23-2 · 实现

新建 `sync/engine/SyncOrchestrator.kt` 和 `SyncOrchestratorResult.kt`，按 §9.8 实现 CLOUD/LOCAL/BOTH 三种目标的独立 runCatching 语义。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.SyncOrchestratorTest"
# 期望：PASSED
```

---

# P6 — 后台调度

---

## T-24 · WorkManager 周期同步

**Covers**: R-24  
**依赖**: T-23  
**预计耗时**: 2h

### 🔴 S24-1 · 写失败测试

```kotlin
// test: sync/worker/PeriodicSyncWorkerTest.kt
@Test
fun `SyncWorker calls SyncOrchestrator on doWork`() = runTest {
    val mockOrchestrator = mockk<SyncOrchestrator>(relaxed = true)
    val worker = buildTestWorker(mockOrchestrator)
    val result = worker.doWork()
    assertThat(result).isEqualTo(ListenableWorker.Result.success())
    coVerify(exactly = 1) { mockOrchestrator.sync(any()) }
}

@Test
fun `scheduleCloudSync creates unique periodic task with CONNECTED constraint`() {
    val scheduler = SyncScheduler(context = ApplicationProvider.getApplicationContext())
    scheduler.scheduleCloudSync(intervalHours = 6)
    val wm = WorkManager.getInstance(ApplicationProvider.getApplicationContext())
    val tasks = wm.getWorkInfosForUniqueWork("webdav_periodic_sync").get()
    assertThat(tasks).isNotEmpty()
    assertThat(tasks[0].state).isEqualTo(WorkInfo.State.ENQUEUED)
}
```

**Verify**:
```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=*.PeriodicSyncWorkerTest
# 期望：FAILED
```

### 🟢 S24-2 · 实现

新建 `sync/worker/SyncWorker.kt`（继承 `CoroutineWorker`）和 `sync/worker/SyncScheduler.kt`：
- 使用 `PeriodicWorkRequestBuilder`
- 约束：`NetworkType.CONNECTED`
- 任务名：`webdav_periodic_sync` / `local_periodic_sync`
- 最小间隔：15 分钟（但默认 6h）

**Verify**:
```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=*.PeriodicSyncWorkerTest
# 期望：PASSED
```

---

## T-25 · 通知状态更新（状态机切换级别）

**Covers**: R-40  
**依赖**: T-04, T-24  
**预计耗时**: 1.5h

### 🔴 S25-1 · 写失败测试

```kotlin
// test: sync/notification/SyncNotifierTest.kt
@Test
fun `notification only updates on state machine transitions`() = runTest {
    val notifier = FakeSyncNotifier()
    val sm = SyncStateMachine()
    SyncNotificationCoordinator(sm, notifier).start(this)

    sm.transition(SyncState.SCANNING)
    sm.transition(SyncState.DOWNLOADING)
    sm.transition(SyncState.UPLOADING)
    sm.transition(SyncState.SUCCESS)

    // 每次状态转换 1 次通知更新（共 4 次，不含初始 IDLE）
    assertThat(notifier.updateCount).isEqualTo(4)
}

@Test
fun `notification text matches SyncState`() = runTest {
    val notifier = FakeSyncNotifier()
    val sm = SyncStateMachine()
    SyncNotificationCoordinator(sm, notifier).start(this)
    sm.transition(SyncState.UPLOADING)
    assertThat(notifier.lastText).isEqualTo("正在上传书签与笔记...")
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.SyncNotifierTest"
# 期望：FAILED
```

### 🟢 S25-2 · 实现

新建 `sync/notification/SyncNotificationCoordinator.kt`，订阅 `SyncStateMachine.state` Flow，仅在状态变化时调用 `SyncNotifier.update(text)`。`SyncStateTextMapper` 提供状态→文案映射。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.SyncNotifierTest"
# 期望：PASSED
```

---

# P7 — 端到端加密

---

## T-26 · PBKDF2 密钥派生

**Covers**: R-25  
**依赖**: —  
**预计耗时**: 1.5h

### 🔴 S26-1 · 写失败测试

```kotlin
// test: sync/crypto/KeyDerivationTest.kt
@Test
fun `deriveKey uses PBKDF2WithHmacSHA256 with 600000 iterations`() {
    val params = KeyDerivationParams(
        algorithm = "PBKDF2WithHmacSHA256",
        iterations = 600_000,
        keyLengthBits = 256
    )
    val kdf = KeyDerivation(params)
    val key = kdf.derive(password = "test-password", salt = ByteArray(16) { it.toByte() })
    assertThat(key.size).isEqualTo(32) // 256 bits = 32 bytes
    assertThat(key).isNotEqualTo(ByteArray(32)) // 非全零
}

@Test
fun `same password and salt produce same key deterministically`() {
    val kdf = KeyDerivation(defaultParams())
    val salt = SecureRandom().generateSeed(16)
    val k1 = kdf.derive("password", salt)
    val k2 = kdf.derive("password", salt)
    assertThat(k1).isEqualTo(k2)
}

@Test
fun `different passwords produce different keys`() {
    val kdf = KeyDerivation(defaultParams())
    val salt = SecureRandom().generateSeed(16)
    assertThat(kdf.derive("password1", salt)).isNotEqualTo(kdf.derive("password2", salt))
}

@Test
fun `iterations below 600000 is rejected`() {
    assertThrows<IllegalArgumentException> {
        KeyDerivationParams(algorithm = "PBKDF2WithHmacSHA256", iterations = 310_000, keyLengthBits = 256)
    }
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.KeyDerivationTest"
# 期望：FAILED
```

### 🟢 S26-2 · 实现

新建 `sync/crypto/KeyDerivation.kt`，使用 `javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")`；`KeyDerivationParams` 的构造器在 iterations < 600_000 时抛出 `IllegalArgumentException`。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.KeyDerivationTest"
# 期望：PASSED
```

---

## T-27 · AES-256-GCM 加密/解密

**Covers**: R-26  
**依赖**: T-26  
**预计耗时**: 2h

### 🔴 S27-1 · 写失败测试

```kotlin
// test: sync/crypto/AesGcmCipherTest.kt
@Test
fun `encrypt then decrypt returns original plaintext`() {
    val cipher = AesGcmCipher()
    val key = ByteArray(32) { it.toByte() }
    val plain = "Hello, 光屿!".toByteArray(Charsets.UTF_8)
    val ciphertext = cipher.encrypt(plain, key)
    val decrypted = cipher.decrypt(ciphertext, key)
    assertThat(decrypted).isEqualTo(plain)
}

@Test
fun `each encrypt call produces different ciphertext (unique nonce)`() {
    val cipher = AesGcmCipher()
    val key = ByteArray(32) { it.toByte() }
    val plain = "test".toByteArray()
    val c1 = cipher.encrypt(plain, key)
    val c2 = cipher.encrypt(plain, key)
    assertThat(c1).isNotEqualTo(c2)
}

@Test
fun `tampered ciphertext fails authentication`() {
    val cipher = AesGcmCipher()
    val key = ByteArray(32) { it.toByte() }
    val ciphertext = cipher.encrypt("data".toByteArray(), key).also { it[it.size / 2] = it[it.size / 2].xor(0xFF.toByte()) }
    assertThrows<AEADBadTagException> { cipher.decrypt(ciphertext, key) }
}

@Test
fun `ciphertext format is nonce(12B) + ciphertext + tag(16B)`() {
    val cipher = AesGcmCipher()
    val key = ByteArray(32)
    val plain = ByteArray(100)
    val result = cipher.encrypt(plain, key)
    // nonce(12) + payload(100) + GCM tag(16) = 128
    assertThat(result.size).isEqualTo(128)
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.AesGcmCipherTest"
# 期望：FAILED
```

### 🟢 S27-2 · 实现

新建 `sync/crypto/AesGcmCipher.kt`：
- 随机 12 字节 nonce（SecureRandom）
- `Cipher.getInstance("AES/GCM/NoPadding")`，tag 长度 128 bit
- 输出格式：`nonce(12) || ciphertext+tag`

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.AesGcmCipherTest"
# 期望：PASSED
```

---

## T-28 · crypto.json 完整性保护

**Covers**: R-27  
**依赖**: T-26, T-27  
**预计耗时**: 2.5h

### 🔴 S28-1 · 写失败测试

```kotlin
// test: sync/crypto/CryptoMetadataManagerTest.kt
@Test
fun `writeCryptoJson includes HMAC integrity field`() = runTest {
    val transport = mockk<SyncTransport>(relaxed = true)
    val manager = CryptoMetadataManager(transport)
    val masterKey = ByteArray(32) { it.toByte() }
    manager.writeCryptoJson(masterKey, salt = ByteArray(16), iterations = 600_000)
    val writtenJson = slot<ByteArray>()
    coVerify { transport.write("crypto.json", capture(writtenJson)) }
    val parsed = Json.decodeFromString<CryptoMetadata>(String(writtenJson.captured))
    assertThat(parsed.integrity).isNotEmpty()
    assertThat(parsed.iterations).isEqualTo(600_000)
}

@Test
fun `verifyCryptoIntegrity returns true for untampered data`() {
    val masterKey = ByteArray(32) { it.toByte() }
    val meta = CryptoMetadata(kdf = "PBKDF2WithHmacSHA256", iterations = 600_000, salt = "aGVsbG8=", cipher = "AES-256-GCM", keyVersion = 1, createdAt = 0L, integrity = "")
    val metaWithIntegrity = CryptoMetadataManager.computeIntegrity(meta, masterKey)
    assertThat(CryptoMetadataManager.verifyIntegrity(metaWithIntegrity, masterKey)).isTrue()
}

@Test
fun `verifyCryptoIntegrity returns false for tampered iterations`() {
    val masterKey = ByteArray(32) { it.toByte() }
    val meta = CryptoMetadata(kdf = "PBKDF2WithHmacSHA256", iterations = 600_000, salt = "aGVsbG8=", cipher = "AES-256-GCM", keyVersion = 1, createdAt = 0L, integrity = "")
    val signed = CryptoMetadataManager.computeIntegrity(meta, masterKey)
    val tampered = signed.copy(iterations = 1)
    assertThat(CryptoMetadataManager.verifyIntegrity(tampered, masterKey)).isFalse()
}

@Test
fun `local hash cache detects remote crypto json modification`() = runTest {
    val fakeDataStore = FakeDataStore()
    val validator = CryptoJsonCacheValidator(fakeDataStore)
    val original = CryptoMetadata(kdf = "PBKDF2WithHmacSHA256", iterations = 600_000, salt = "abc", cipher = "AES-256-GCM", keyVersion = 1, createdAt = 0L, integrity = "sig")
    validator.saveHash(original)
    val tampered = original.copy(iterations = 1)
    assertThrows<CryptoConfigTamperedException> { validator.verify(tampered) }
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.CryptoMetadataManagerTest"
# 期望：FAILED
```

### 🟢 S28-2 · 实现

新建：
- `sync/crypto/CryptoMetadata.kt`（含 integrity 字段）
- `sync/crypto/CryptoMetadataManager.kt`（computeIntegrity 用 HKDF 派生子密钥 + HMAC-SHA256）
- `sync/crypto/CryptoJsonCacheValidator.kt`（本地 DataStore 缓存 crypto.json SHA-256 hash）
- `sync/crypto/CryptoConfigTamperedException.kt`

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.CryptoMetadataManagerTest"
# 期望：PASSED
```

---

## T-29 · SyncCryptoManager — 完整 E2EE 管道

**Covers**: R-28  
**依赖**: T-26 ~ T-28  
**预计耗时**: 2h

### 🔴 S29-1 · 写失败测试

```kotlin
// test: sync/crypto/SyncCryptoManagerTest.kt
@Test
fun `encrypt then decrypt roundtrip for manifest`() {
    val manager = SyncCryptoManager(AesGcmCipher(), masterKey = ByteArray(32) { it.toByte() })
    val json = """{"schemaVersion":2,"version":1}"""
    val encrypted = manager.encrypt(json.toByteArray())
    val decrypted = manager.decrypt(encrypted)
    assertThat(String(decrypted)).isEqualTo(json)
}

@Test
fun `encrypted output is .enc format`() {
    val manager = SyncCryptoManager(AesGcmCipher(), masterKey = ByteArray(32))
    val result = manager.encrypt("test".toByteArray())
    assertThat(result.size).isGreaterThan(12 + 4 + 16) // nonce + data + tag
}

@Test
fun `decrypt with wrong key throws`() {
    val m1 = SyncCryptoManager(AesGcmCipher(), masterKey = ByteArray(32) { 1 })
    val m2 = SyncCryptoManager(AesGcmCipher(), masterKey = ByteArray(32) { 2 })
    val enc = m1.encrypt("secret".toByteArray())
    assertThrows<Exception> { m2.decrypt(enc) }
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.SyncCryptoManagerTest"
# 期望：FAILED
```

### 🟢 S29-2 · 实现

新建 `sync/crypto/SyncCryptoManager.kt`，封装 `AesGcmCipher`，提供 `encrypt(plaintext): ByteArray` 和 `decrypt(ciphertext): ByteArray`。确保 Transport 层在 E2EE 模式下透明注入加密管道。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.SyncCryptoManagerTest"
# 期望：PASSED
```

---

# P8 — 导出与导入

---

## T-30 · ZIP 明文导出

**Covers**: R-29  
**依赖**: T-22  
**预计耗时**: 2h

### 🔴 S30-1 · 写失败测试

```kotlin
// test: sync/export/ZipExporterTest.kt
@Test
fun `export includes progress, bookmarks, notes, config by default`() = runTest {
    val exporter = buildTestExporter(db = fakeDb)
    val outputFile = tempDir.resolve("export.zip")
    exporter.export(outputFile, options = ExportOptions())
    val zip = ZipFile(outputFile)
    val entries = zip.entries().toList().map { it.name }
    assertThat(entries).contains("manifest.json", "books.json")
    assertThat(entries.any { it.startsWith("states/") }).isTrue()
    assertThat(entries.any { it.startsWith("bookmarks/") }).isTrue()
    assertThat(entries.any { it.startsWith("notes/") }).isTrue()
    assertThat(entries.any { it.startsWith("config/") }).isTrue()
}

@Test
fun `export excludes book files when option is false`() = runTest {
    val exporter = buildTestExporter(db = fakeDb)
    val outputFile = tempDir.resolve("export.zip")
    exporter.export(outputFile, ExportOptions(includeBookFiles = false))
    val zip = ZipFile(outputFile)
    val entries = zip.entries().toList().map { it.name }
    assertThat(entries.none { it.startsWith("books/") || it.endsWith(".txt") || it.endsWith(".epub") }).isTrue()
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.ZipExporterTest"
# 期望：FAILED
```

### 🟢 S30-2 · 实现

新建 `sync/export/ZipExporter.kt` 和 `sync/export/ExportOptions.kt`（includeBookFiles, includeConfig, …）。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.ZipExporterTest"
# 期望：PASSED
```

---

## T-31 · ZIP 导入（含三种合并策略）

**Covers**: R-30  
**依赖**: T-22, T-30  
**预计耗时**: 2h

### 🔴 S31-1 · 写失败测试

```kotlin
// test: sync/export/ZipImporterTest.kt
@Test
fun `import OVERWRITE strategy clears local data first`() = runTest {
    insertLocalBookmark(id = "local-only")
    val zipFile = buildTestZip(bookmarks = emptyList())
    val importer = buildTestImporter(db = fakeDb)
    importer.import(zipFile, strategy = ImportStrategy.OVERWRITE)
    assertThat(bookmarkDao.getAll()).isEmpty()
}

@Test
fun `import MERGE strategy keeps newer local items`() = runTest {
    insertLocalBookmark(id = "local-bm", updatedAt = 9999L)
    val zipBm = BookmarkDto(id = "local-bm", updatedAt = 1000L) // 旧
    val zipFile = buildTestZip(bookmarks = listOf(zipBm))
    val importer = buildTestImporter(db = fakeDb)
    importer.import(zipFile, strategy = ImportStrategy.SMART_MERGE)
    assertThat(bookmarkDao.get("local-bm").updatedAt).isEqualTo(9999L) // 本地胜出
}

@Test
fun `import IMPORT_ONLY_NEW ignores already-existing items`() = runTest {
    insertLocalBookmark(id = "existing-bm")
    val zipBm = BookmarkDto(id = "existing-bm", byteOffset = 999)
    val zipFile = buildTestZip(bookmarks = listOf(zipBm))
    val importer = buildTestImporter(db = fakeDb)
    importer.import(zipFile, strategy = ImportStrategy.IMPORT_ONLY_NEW)
    assertThat(bookmarkDao.get("existing-bm").byteOffset).isNotEqualTo(999)
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.ZipImporterTest"
# 期望：FAILED
```

### 🟢 S31-2 · 实现

新建 `sync/export/ZipImporter.kt` 和 `sync/export/ImportStrategy.kt`（OVERWRITE, SMART_MERGE, IMPORT_ONLY_NEW）。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.ZipImporterTest"
# 期望：PASSED
```

---

## T-32 · 加密 ZIP 导出/导入

**Covers**: R-31  
**依赖**: T-27, T-30, T-31  
**预计耗时**: 2h

### 🔴 S32-1 · 写失败测试

```kotlin
// test: sync/export/EncryptedZipTest.kt
@Test
fun `encrypted export produces .zip.enc file`() = runTest {
    val exporter = buildTestExporter()
    val outFile = tempDir.resolve("export.zip.enc")
    exporter.export(outFile, ExportOptions(encryptionPassword = "secret-pass"))
    assertThat(outFile.exists()).isTrue()
    assertThat(outFile.length()).isGreaterThan(0)
    // 确认不是明文 ZIP（PK 魔术字节）
    assertThat(outFile.readBytes().take(2)).isNotEqualTo(listOf(0x50.toByte(), 0x4B.toByte()))
}

@Test
fun `decrypt with correct password restores original zip`() = runTest {
    val exporter = buildTestExporter()
    val outFile = tempDir.resolve("export.zip.enc")
    exporter.export(outFile, ExportOptions(encryptionPassword = "correct-pass"))
    val importer = buildTestImporter()
    // 正确密码 → 成功
    assertDoesNotThrow { importer.import(outFile, ImportStrategy.SMART_MERGE, password = "correct-pass") }
}

@Test
fun `decrypt with wrong password throws`() = runTest {
    val exporter = buildTestExporter()
    val outFile = tempDir.resolve("export.zip.enc")
    exporter.export(outFile, ExportOptions(encryptionPassword = "correct-pass"))
    val importer = buildTestImporter()
    assertThrows<Exception> { importer.import(outFile, ImportStrategy.SMART_MERGE, password = "wrong-pass") }
}

@Test
fun `export password is independent of sync password`() {
    // 验证 ExportOptions 中的密码不读取 DataStore 中的同步密码
    val exportOpts = ExportOptions(encryptionPassword = "export-pass")
    assertThat(exportOpts.encryptionPassword).isEqualTo("export-pass")
    // 无法访问同步密码（不同模块隔离）
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.EncryptedZipTest"
# 期望：FAILED
```

### 🟢 S32-2 · 实现

在 `ZipExporter`/`ZipImporter` 中增加加密路径：导出时用 PBKDF2 派生临时密钥 + AES-GCM 加密整个 ZIP bytes；导入时反向解密后再解压。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.EncryptedZipTest"
# 期望：PASSED
```

---

# P9 — 界面层

---

## T-33 · 设置主页 — 同步摘要卡

**Covers**: R-32  
**依赖**: T-22, T-23  
**预计耗时**: 2h

### 🔴 S33-1 · 写失败测试（ViewModel）

```kotlin
// test: ui/settings/SyncSummaryViewModelTest.kt
@Test
fun `cloudSyncUiState reflects SyncState SUCCESS`() = runTest {
    val fakeStateMachine = FakeSyncStateMachine(initialState = SyncState.SUCCESS)
    val vm = SyncSummaryViewModel(fakeStateMachine, fakeRepository)
    vm.cloudSyncUiState.test {
        val state = awaitItem()
        assertThat(state.lastSyncText).isNotEmpty()
        assertThat(state.isLoading).isFalse()
    }
}

@Test
fun `triggerManualSync calls orchestrator with CLOUD target`() = runTest {
    val mockOrchestrator = mockk<SyncOrchestrator>(relaxed = true)
    val vm = SyncSummaryViewModel(FakeSyncStateMachine(), fakeRepository, orchestrator = mockOrchestrator)
    vm.triggerManualSync(SyncTarget.CLOUD)
    coVerify { mockOrchestrator.sync(SyncTarget.CLOUD) }
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.SyncSummaryViewModelTest"
# 期望：FAILED
```

### 🟢 S33-2 · 实现 ViewModel

新建 `ui/settings/sync/SyncSummaryViewModel.kt`，暴露 `cloudSyncUiState: StateFlow<CloudSyncCardUiState>` 和 `localSyncUiState: StateFlow<LocalSyncCardUiState>`。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.SyncSummaryViewModelTest"
# 期望：PASSED
```

### 🟢 S33-3 · 实现 Compose UI

新建 `ui/settings/sync/SyncSettingsScreen.kt`，按 §14.1 ~ §14.3 设计实现摘要卡：
- 云端摘要卡：状态徽章 + 上次同步 + [立即同步] + [设置 ›]
- 本地摘要卡：友好路径显示 + [立即同步] + [设置 ›]
- 导出/恢复入口

新建对应 Compose 测试 `SyncSettingsScreenTest.kt`：

```kotlin
// test: ui/settings/SyncSettingsScreenTest.kt
@Test
fun `sync summary card shows last sync time`() {
    composeTestRule.setContent {
        SyncSettingsScreen(
            cloudState = CloudSyncCardUiState(lastSyncText = "2 分钟前", isLoading = false),
            localState = LocalSyncCardUiState(displayPath = "Documents / ShuLiReader", storageLabel = "内部存储")
        )
    }
    composeTestRule.onNodeWithText("2 分钟前").assertIsDisplayed()
    composeTestRule.onNodeWithText("Documents / ShuLiReader").assertIsDisplayed()
    composeTestRule.onNodeWithText("内部存储").assertIsDisplayed()
}
```

**Verify**:
```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=*.SyncSettingsScreenTest
# 期望：PASSED（各元素可见性断言）
```

---

## T-34 · 云端同步设置子页

**Covers**: R-33  
**依赖**: T-33  
**预计耗时**: 2h

### 🔴 S34-1 · 写失败测试（ViewModel）

```kotlin
// test: ui/settings/CloudSyncSettingsViewModelTest.kt
@Test
fun `testConnection emits SUCCESS when WebDavClient returns 200`() = runTest {
    val mockClient = mockk<WebDavClient> { coEvery { propfind("/", 0) } returns emptyList() }
    val vm = CloudSyncSettingsViewModel(mockClient, fakeDataStore)
    vm.testConnection()
    vm.connectionTestResult.test {
        assertThat(awaitItem()).isEqualTo(ConnectionTestResult.SUCCESS)
    }
}

@Test
fun `testConnection emits AUTH_FAILED on 401`() = runTest {
    val mockClient = mockk<WebDavClient> { coEvery { propfind(any(), any()) } throws WebDavAuthException() }
    val vm = CloudSyncSettingsViewModel(mockClient, fakeDataStore)
    vm.testConnection()
    vm.connectionTestResult.test {
        assertThat(awaitItem()).isEqualTo(ConnectionTestResult.AUTH_FAILED)
    }
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.CloudSyncSettingsViewModelTest"
# 期望：FAILED
```

### 🟢 S34-2 · 实现 ViewModel + UI

新建 `ui/settings/sync/CloudSyncSettingsViewModel.kt` 和对应 Compose 页面，包含：服务商选择、账号输入、测试连接、同步内容复选、Wi-Fi 限制开关、自动同步开关、加密管理入口、查看日志、已同步设备入口。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.CloudSyncSettingsViewModelTest"
# 期望：PASSED
```

---

## T-35 · 加密管理页

**Covers**: R-34  
**依赖**: T-28, T-29  
**预计耗时**: 2h

### 🔴 S35-1 · 写失败测试

```kotlin
// test: ui/settings/EncryptionManagementViewModelTest.kt
@Test
fun `verifyPassword calls KeyDerivation and validates against stored salt`() = runTest {
    val vm = EncryptionManagementViewModel(fakeDataStore, KeyDerivation(defaultParams()), fakeTransport)
    val result = vm.verifyPassword("correct-password")
    // 期望根据存储的 salt 和 iterations 验证成功
    assertThat(result).isEqualTo(PasswordVerifyResult.SUCCESS)
}

@Test
fun `encryption info shows AES-256-GCM and 600K iterations`() = runTest {
    val vm = EncryptionManagementViewModel(fakeDataStore, KeyDerivation(defaultParams()), fakeTransport)
    val info = vm.encryptionInfo.first()
    assertThat(info.algorithm).isEqualTo("AES-256-GCM")
    assertThat(info.kdfIterations).isEqualTo(600_000)
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.EncryptionManagementViewModelTest"
# 期望：FAILED
```

### 🟢 S35-2 · 实现 ViewModel + UI

新建 `ui/settings/crypto/EncryptionManagementViewModel.kt` 和 Compose 页面，包含：E2EE 状态 Hero 区、算法详情行、操作入口（验证/更换密码）、**同步密码与导出密码区隔说明**（warn-card）、危险区域（重置）。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.EncryptionManagementViewModelTest"
# 期望：PASSED
```

---

## T-36 · 冲突解决弹窗（含设备名 fallback）

**Covers**: R-35  
**依赖**: T-18, T-21  
**预计耗时**: 1.5h

### 🔴 S36-1 · 写失败测试

```kotlin
// test: ui/conflict/ConflictDialogViewModelTest.kt
@Test
fun `getDeviceDisplayName returns model when available`() {
    val info = DeviceInfo(deviceId = "d1", model = "Pixel 7", lastSyncAt = 0)
    assertThat(ConflictDialogViewModel.getDeviceDisplayName(info)).isEqualTo("Pixel 7")
}

@Test
fun `getDeviceDisplayName returns fallback when model is blank`() {
    val info = DeviceInfo(deviceId = "f47ac10b-58cc", model = "", lastSyncAt = 0)
    assertThat(ConflictDialogViewModel.getDeviceDisplayName(info)).isEqualTo("其他设备（f47ac1）")
}

@Test
fun `getDeviceDisplayName returns fallback when deviceInfo is null`() {
    assertThat(ConflictDialogViewModel.getDeviceDisplayName(null)).isEqualTo("其他设备")
}

@Test
fun `conflict requiring user input emits ShowConflictDialog event`() = runTest {
    val vm = ConflictDialogViewModel(fakeSyncEngine)
    vm.events.test {
        vm.onProgressConflictDetected(localState, remoteState, remoteDeviceInfo = null)
        val event = awaitItem()
        assertThat(event).isInstanceOf(SyncUiEvent.ShowConflictDialog::class.java)
    }
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.ConflictDialogViewModelTest"
# 期望：FAILED
```

### 🟢 S36-2 · 实现

新建 `ui/conflict/ConflictDialogViewModel.kt` 和 Compose `ConflictResolutionDialog`，使用设备名 fallback 逻辑，提供"保留本地/跳转云端/暂不处理"三个选项。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.ConflictDialogViewModelTest"
# 期望：PASSED
```

---

## T-37 · 设备管理页

**Covers**: R-36  
**依赖**: T-21  
**预计耗时**: 1.5h

### 🔴 S37-1 · 写失败测试

```kotlin
// test: ui/devices/DeviceManagementViewModelTest.kt
@Test
fun `loadDevices shows list sorted by lastSyncAt descending`() = runTest {
    val vm = DeviceManagementViewModel(fakeDeviceSyncManager)
    vm.devices.test {
        val list = awaitItem()
        assertThat(list.first().lastSyncAt).isGreaterThan(list.last().lastSyncAt)
    }
}

@Test
fun `removeDevice calls DeviceSyncManager and refreshes list`() = runTest {
    val mockManager = mockk<DeviceSyncManager>(relaxed = true)
    val vm = DeviceManagementViewModel(mockManager)
    vm.removeDevice("dev-2")
    coVerify { mockManager.removeDevice("dev-2") }
}

@Test
fun `current device is marked with isSelf=true`() = runTest {
    val vm = DeviceManagementViewModel(fakeDeviceSyncManager, localDeviceId = "dev-1")
    vm.devices.test {
        val self = awaitItem().first { it.deviceId == "dev-1" }
        assertThat(self.isSelf).isTrue()
    }
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.DeviceManagementViewModelTest"
# 期望：FAILED
```

### 🟢 S37-2 · 实现

新建 `ui/devices/DeviceManagementViewModel.kt` 和 Compose `DeviceManagementScreen`，按 §14.12 实现设备列表 + 本机标记 + 移除确认弹窗 + 说明文字。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.DeviceManagementViewModelTest"
# 期望：PASSED
```

---

## T-38 · 同步日志页

**Covers**: R-37  
**依赖**: T-22  
**预计耗时**: 1.5h

### 🔴 S38-1 · 写失败测试

```kotlin
// test: ui/log/SyncLogViewModelTest.kt
@Test
fun `logs are grouped by date`() = runTest {
    val vm = SyncLogViewModel(fakeSyncLogRepository)
    vm.groupedLogs.test {
        val groups = awaitItem()
        assertThat(groups.keys.first()).isEqualTo("今天")
    }
}

@Test
fun `filter FAILED shows only failed logs`() = runTest {
    val vm = SyncLogViewModel(fakeSyncLogRepository)
    vm.applyFilter(SyncLogFilter.FAILED)
    vm.groupedLogs.test {
        val groups = awaitItem()
        groups.values.flatten().forEach { assertThat(it.result).isEqualTo(SyncResult.FAILED) }
    }
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.SyncLogViewModelTest"
# 期望：FAILED
```

### 🟢 S38-2 · 实现

新建 `ui/log/SyncLogViewModel.kt` 和 Compose `SyncLogScreen`，包含：日期分组、筛选（全部/云端/本地/失败）、日志行详情（时间/耗时/请求数/传输量）。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.SyncLogViewModelTest"
# 期望：PASSED
```

---

## T-39 · 导出对话框（异步大小预估 + 密码视觉区隔）

**Covers**: R-38  
**依赖**: T-30, T-32  
**预计耗时**: 2h

### 🔴 S39-1 · 写失败测试

```kotlin
// test: ui/export/ExportDialogViewModelTest.kt
@Test
fun `estimatedSize starts as Calculating then updates`() = runTest {
    val vm = ExportDialogViewModel(fakeExporter)
    vm.estimatedSize.test {
        assertThat(awaitItem()).isInstanceOf(SizeEstimate.Calculating::class.java)
        assertThat(awaitItem()).isInstanceOf(SizeEstimate.Calculated::class.java)
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun `toggleBookFiles triggers re-estimation`() = runTest {
    val vm = ExportDialogViewModel(fakeExporter)
    val sizeStates = vm.estimatedSize.take(4).toList() // 初始 Calc + Calced + Calc + Calced
    vm.toggleIncludeBookFiles(true)
    assertThat(sizeStates.filterIsInstance<SizeEstimate.Calculating>()).hasSize(2)
}

@Test
fun `export password is stored in ExportOptions not in DataStore`() = runTest {
    val vm = ExportDialogViewModel(fakeExporter)
    vm.setExportPassword("my-password")
    // 确认密码在 ViewModel 内存中，不持久化
    val opts = vm.buildExportOptions()
    assertThat(opts.encryptionPassword).isEqualTo("my-password")
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.ExportDialogViewModelTest"
# 期望：FAILED
```

### 🟢 S39-2 · 实现

新建 `ui/export/ExportDialogViewModel.kt` 和 Compose `ExportBottomSheet`，包含：异步大小预估（`SizeEstimate` sealed class）、导出密码区块（视觉上与同步密码区隔，使用 warn-card 样式）、内容复选。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.ExportDialogViewModelTest"
# 期望：PASSED
```

---

## T-40 · 错误状态 UI（连接失败 / 限流 / 密钥未解锁）

**Covers**: R-39  
**依赖**: T-04, T-13  
**预计耗时**: 1.5h

### 🔴 S40-1 · 写失败测试

```kotlin
// test: ui/settings/SyncErrorStateTest.kt
@Test
fun `CloudSyncCardUiState.ERROR_AUTH shows auth error card`() = runTest {
    val vm = SyncSummaryViewModel(FakeSyncStateMachine(SyncState.FAILED), fakeRepo)
    vm.cloudSyncUiState.test {
        val state = awaitItem()
        assertThat(state.errorType).isEqualTo(SyncErrorType.AUTH_FAILED)
    }
}

@Test
fun `RATE_LIMITED state shows retry timer and links`() = runTest {
    val vm = SyncSummaryViewModel(FakeSyncStateMachine(SyncState.RATE_LIMITED), fakeRepo)
    vm.cloudSyncUiState.test {
        val state = awaitItem()
        assertThat(state.rateLimitRetryAfterMs).isGreaterThan(0L)
        assertThat(state.showRateLimitLinks).isTrue()
    }
}

@Test
fun `CRYPTO_LOCKED state shows password input`() = runTest {
    val vm = SyncSummaryViewModel(FakeSyncStateMachine(SyncState.CRYPTO_LOCKED), fakeRepo)
    vm.cloudSyncUiState.test {
        val state = awaitItem()
        assertThat(state.requiresPasswordInput).isTrue()
    }
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.SyncErrorStateTest"
# 期望：FAILED
```

### 🟢 S40-2 · 实现

扩展 `CloudSyncCardUiState` 加入 `errorType`、`rateLimitRetryAfterMs`、`showRateLimitLinks`、`requiresPasswordInput`。Compose UI 中三种错误状态分别渲染：error-card / warn-card（含倒计时 + 链接）/ info-card（含密码输入框）。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.SyncErrorStateTest"
# 期望：PASSED
```

---

## T-41 · 本地路径友好显示 + 取消同步说明文案

**Covers**: R-41, R-42, R-43  
**依赖**: T-33  
**预计耗时**: 1h

### 🔴 S41-1 · 写失败测试

```kotlin
// test: ui/settings/LocalSyncPathFormatterTest.kt
@Test
fun `primary storage URI is formatted as Documents slash ShuLiReader`() {
    val uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FShuLiReader")
    val formatted = LocalSyncPathFormatter.format(context, uri)
    assertThat(formatted.displayPath).isEqualTo("Documents / ShuLiReader")
    assertThat(formatted.storageLabel).isEqualTo("内部存储")
}

@Test
fun `cancel sync button shows explanation text`() {
    // Compose 测试：确认取消按钮下方有说明文字
    composeTestRule.setContent { SyncInProgressCard(onCancel = {}) }
    composeTestRule.onNodeWithText("取消不会丢失已完成的部分，下次同步时继续").assertExists()
}
```

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.LocalSyncPathFormatterTest"
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=*.SyncInProgressCardTest
# 期望：FAILED
```

### 🟢 S41-2 · 实现

新建 `ui/settings/sync/LocalSyncPathFormatter.kt`，用 `DocumentFile.fromTreeUri` + lastPathSegment 转换友好路径。`SyncInProgressCard` Compose 组件在取消按钮下渲染说明文字。

**Verify**:
```bash
./gradlew :app:testDebugUnitTest --tests "*.LocalSyncPathFormatterTest"
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=*.SyncInProgressCardTest
# 期望：PASSED
```

---

## 全局质量门

所有任务完成后，运行完整测试套件并检查覆盖率：

```bash
# 单元测试全量
./gradlew :app:testDebugUnitTest

# 仪器测试全量
./gradlew :app:connectedDebugAndroidTest

# 覆盖率报告
./gradlew :app:koverReport
# 核心模块目标覆盖率：sync/** ≥ 85%，crypto/** ≥ 90%
```

**Wrap-up**:
```bash
mv tasks/sync-backup-tdd-20260529-0941 tasks/sync-backup-tdd-20260529-0941-done
git tag task/sync-backup-tdd-20260529-0941
```
