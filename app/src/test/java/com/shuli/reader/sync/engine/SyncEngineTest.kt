package com.shuli.reader.sync.engine

import com.shuli.reader.sync.engine.conflict.BookmarkDto
import com.shuli.reader.sync.engine.conflict.DeviceInfo
import com.shuli.reader.sync.engine.conflict.UserPreferences
import com.shuli.reader.sync.engine.manifest.ManifestManager
import com.shuli.reader.sync.engine.manifest.SyncManifest
import com.shuli.reader.sync.network.webdav.WebDavRateLimitException
import com.shuli.reader.sync.engine.state.SyncState
import com.shuli.reader.sync.engine.state.SyncStateMachine
import com.shuli.reader.sync.network.throttle.RateLimitHandler
import com.shuli.reader.sync.network.transport.SyncTransport
import com.shuli.reader.sync.network.transport.TransportResourceInfo
import com.shuli.reader.sync.network.transport.TransportResourceMetadata
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Part of T-22 SyncEngine 核心编排
class SyncEngineTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * 创建一个配置好默认行为的 mock transport
     */
    private fun createDefaultTransport(): SyncTransport {
        val transport = mockk<SyncTransport>(relaxed = true)
        coEvery { transport.read("manifest.json") } returns """{"schemaVersion":2,"version":0}""".toByteArray()
        coEvery { transport.getMetadata("manifest.json") } returns null
        coEvery { transport.list("state") } returns emptyList()
        coEvery { transport.read("config/preferences.json") } returns null
        return transport
    }

    // ── 基础状态转换测试 ──────────────────────────────────────────────

    @Test
    fun `sync transitions state machine to SUCCESS`() = runTest {
        val transport = createDefaultTransport()
        val manifestManager = ManifestManager(transport)
        val stateMachine = SyncStateMachine()
        val engine = SyncEngine(manifestManager, stateMachine)

        engine.sync(transport)

        assertEquals(SyncState.SUCCESS, stateMachine.state.value)
    }

    @Test
    fun `sync reads remote manifest`() = runTest {
        val transport = createDefaultTransport()
        coEvery { transport.read("manifest.json") } returns """{"schemaVersion":2,"version":42,"bookCount":10}""".toByteArray()

        val manifestManager = ManifestManager(transport)
        val stateMachine = SyncStateMachine()
        val engine = SyncEngine(manifestManager, stateMachine)

        engine.sync(transport)

        coVerify(atLeast = 1) { transport.read("manifest.json") }
    }

    // ── 数据流测试：上传本地脏数据 ──────────────────────────────────────

    @Test
    fun `sync uploads dirty local book states`() = runTest {
        val transport = createDefaultTransport()

        val dataProvider = mockk<SyncDataProvider>(relaxed = true)
        coEvery { dataProvider.getDirtyBookKeys() } returns listOf("book-1", "book-2")
        coEvery { dataProvider.getLocalState("book-1") } returns SyncBookState(
            bookKey = "book-1", byteOffset = 100, chapterIndex = 2, version = 3, updatedAt = 1000L,
        )
        coEvery { dataProvider.getLocalState("book-2") } returns SyncBookState(
            bookKey = "book-2", byteOffset = 500, chapterIndex = 5, version = 1, updatedAt = 2000L,
        )
        coEvery { dataProvider.getLocalBookmarks(any()) } returns emptyList()
        coEvery { dataProvider.getLocalNotes(any()) } returns emptyList()
        coEvery { dataProvider.getDirtyPreferenceKeys() } returns emptySet()

        val manifestManager = ManifestManager(transport)
        val stateMachine = SyncStateMachine()
        val engine = SyncEngine(manifestManager, stateMachine, dataProvider, "device-1")

        engine.sync(transport)

        // 验证上传了两个 book 的状态（通过检查 write 调用次数）
        coVerify(exactly = 1) { transport.write("state/book-1.json", any(), any()) }
        coVerify(exactly = 1) { transport.write("state/book-2.json", any(), any()) }
        coVerify { dataProvider.clearDirtyFlags(listOf("book-1", "book-2")) }
    }

    @Test
    fun `sync uploads dirty bookmarks and notes`() = runTest {
        val transport = createDefaultTransport()

        val bookmarks = listOf(
            BookmarkDto(id = "bm-1", byteOffset = 100, updatedAt = 1000L),
            BookmarkDto(id = "bm-2", byteOffset = 200, updatedAt = 2000L),
        )
        val notes = listOf(
            BookmarkDto(id = "note-1", byteOffset = 150, updatedAt = 1500L),
        )

        val dataProvider = mockk<SyncDataProvider>(relaxed = true)
        coEvery { dataProvider.getDirtyBookKeys() } returns listOf("book-1")
        coEvery { dataProvider.getLocalState("book-1") } returns SyncBookState(bookKey = "book-1")
        coEvery { dataProvider.getLocalBookmarks("book-1") } returns bookmarks
        coEvery { dataProvider.getLocalNotes("book-1") } returns notes
        coEvery { dataProvider.getDirtyPreferenceKeys() } returns emptySet()

        val manifestManager = ManifestManager(transport)
        val stateMachine = SyncStateMachine()
        val engine = SyncEngine(manifestManager, stateMachine, dataProvider, "device-1")

        engine.sync(transport)

        coVerify(exactly = 1) { transport.write("bookmarks/book-1.json", any(), any()) }
        coVerify(exactly = 1) { transport.write("notes/book-1.json", any(), any()) }
    }

    // ── 数据流测试：下载远端数据并合并 ──────────────────────────────────────

    @Test
    fun `sync downloads remote states and merges with local`() = runTest {
        val transport = createDefaultTransport()

        val remoteState = SyncBookState(
            bookKey = "book-1", byteOffset = 300, chapterIndex = 3, version = 5, updatedAt = 5000L,
        )
        coEvery { transport.list("state") } returns listOf(
            TransportResourceInfo(path = "state/book-1.json", contentLength = 100),
        )
        coEvery { transport.read("state/book-1.json") } returns json.encodeToString(
            SyncBookState.serializer(), remoteState,
        ).toByteArray()
        coEvery { transport.read("bookmarks/book-1.json") } returns null
        coEvery { transport.read("notes/book-1.json") } returns null

        val localState = SyncBookState(
            bookKey = "book-1", byteOffset = 100, chapterIndex = 1, version = 3, updatedAt = 2000L,
        )
        val dataProvider = mockk<SyncDataProvider>(relaxed = true)
        coEvery { dataProvider.getDirtyBookKeys() } returns emptyList()
        coEvery { dataProvider.getLocalState("book-1") } returns localState
        coEvery { dataProvider.getBookFileSize("book-1") } returns 10000L
        coEvery { dataProvider.getDirtyPreferenceKeys() } returns emptySet()

        val manifestManager = ManifestManager(transport)
        val stateMachine = SyncStateMachine()
        val engine = SyncEngine(manifestManager, stateMachine, dataProvider, "device-1")

        engine.sync(transport)

        // 远端版本更高，应该保存远端的状态到本地
        coVerify {
            dataProvider.saveLocalState("book-1", withArg { saved ->
                assertEquals("远端版本更高应采用远端", 5, saved.version)
                assertEquals(300L, saved.byteOffset)
            })
        }
    }

    @Test
    fun `sync merges bookmarks from local and remote`() = runTest {
        val remoteBookmarksJson = """{"items":[
            {"id":"bm-shared","byteOffset":100,"updatedAt":5000},
            {"id":"bm-remote","byteOffset":300,"updatedAt":3000}
        ]}"""

        // 不使用 relaxed mock，显式 mock 所有方法以避免 ByteArray 类型转换问题
        val transport = mockk<SyncTransport>()
        coEvery { transport.ensureDirectories() } returns Unit
        coEvery { transport.read("manifest.json") } returns """{"schemaVersion":2,"version":0}""".toByteArray()
        coEvery { transport.getMetadata("manifest.json") } returns null
        coEvery { transport.write(any(), any(), any()) } returns Unit
        coEvery { transport.list("state") } returns listOf(
            TransportResourceInfo(path = "state/book-1.json", contentLength = 100),
        )
        coEvery { transport.read("state/book-1.json") } returns null
        coEvery { transport.read("bookmarks/book-1.json") } answers { remoteBookmarksJson.toByteArray() }
        coEvery { transport.read("notes/book-1.json") } returns null
        coEvery { transport.read("config/preferences.json") } returns null
        coEvery { transport.read("device") } returns null
        coEvery { transport.list("device") } returns emptyList()
        coEvery { transport.list("bookmarks") } returns emptyList()
        coEvery { transport.list("notes") } returns emptyList()

        val localBookmarks = listOf(
            BookmarkDto(id = "bm-shared", byteOffset = 100, updatedAt = 1000L),
            BookmarkDto(id = "bm-local", byteOffset = 200, updatedAt = 2000L),
        )
        val dataProvider = mockk<SyncDataProvider>()
        coEvery { dataProvider.getDirtyBookKeys() } returns listOf("book-1")
        coEvery { dataProvider.getLocalState("book-1") } returns SyncBookState(bookKey = "book-1")
        coEvery { dataProvider.getLocalBookmarks("book-1") } returns localBookmarks
        coEvery { dataProvider.getLocalNotes("book-1") } returns emptyList()
        coEvery { dataProvider.getDirtyPreferenceKeys() } returns emptySet()
        coEvery { dataProvider.getBookFileSize(any()) } returns 0L
        coEvery { dataProvider.saveLocalState(any(), any()) } returns Unit
        coEvery { dataProvider.saveLocalBookmarks(any(), any()) } returns Unit
        coEvery { dataProvider.saveLocalNotes(any(), any()) } returns Unit
        coEvery { dataProvider.clearDirtyFlags(any()) } returns Unit
        coEvery { dataProvider.clearDirtyPreferenceKeys() } returns Unit

        val manifestManager = ManifestManager(transport)
        val stateMachine = SyncStateMachine()
        val engine = SyncEngine(manifestManager, stateMachine, dataProvider, "device-1")

        engine.sync(transport)

        // 验证合并后保存了书签
        val bookmarkSlot = slot<List<BookmarkDto>>()
        coVerify { dataProvider.saveLocalBookmarks(eq("book-1"), capture(bookmarkSlot)) }
        val merged = bookmarkSlot.captured
        assertTrue("合并后应至少有 2 个书签, 实际: ${merged.size}", merged.size >= 2)
        val shared = merged.find { it.id == "bm-shared" }
        assertTrue("共同 ID 应存在", shared != null)
        assertEquals("共同 ID 应采用远端时间戳", 5000L, shared!!.updatedAt)
    }

    // ── 数据流测试：配置合并 ──────────────────────────────────────────────

    @Test
    fun `sync merges preferences with dirty key logic`() = runTest {
        val transport = createDefaultTransport()

        val remotePrefs = UserPreferences(fontSize = 20f, themeMode = "dark", lineSpacing = 1.8f)
        coEvery { transport.read("config/preferences.json") } returns json.encodeToString(
            UserPreferences.serializer(), remotePrefs,
        ).toByteArray()

        val localPrefs = UserPreferences(fontSize = 16f, themeMode = "light", lineSpacing = 1.5f)
        val dataProvider = mockk<SyncDataProvider>(relaxed = true)
        coEvery { dataProvider.getDirtyBookKeys() } returns emptyList()
        coEvery { dataProvider.getDirtyPreferenceKeys() } returns setOf("fontSize") andThen emptySet()
        coEvery { dataProvider.getLocalPreferences() } returns localPrefs
        coEvery { dataProvider.getLocalPreferencesJson() } returns """{"fontSize":16.0,"themeMode":"light","lineSpacing":1.5}""".toByteArray()

        val manifestManager = ManifestManager(transport)
        val stateMachine = SyncStateMachine()
        val engine = SyncEngine(manifestManager, stateMachine, dataProvider, "device-1")

        engine.sync(transport)

        coVerify {
            dataProvider.saveLocalPreferencesMerged(withArg { merged ->
                assertEquals("脏 key fontSize 应用本地值", 16f, merged.fontSize)
                assertEquals("非脏 key themeMode 应用远端值", "dark", merged.themeMode)
                assertEquals("非脏 key lineSpacing 应用远端值", 1.8f, merged.lineSpacing)
            })
        }
    }

    // ── 数据流测试：manifest 更新 ──────────────────────────────────────────────

    @Test
    fun `sync updates manifest version after upload`() = runTest {
        val transport = createDefaultTransport()
        coEvery { transport.read("manifest.json") } returns """{"schemaVersion":2,"version":10,"bookCount":3}""".toByteArray()
        coEvery { transport.getMetadata("manifest.json") } returns TransportResourceMetadata(etag = "\"abc\"")

        val dataProvider = mockk<SyncDataProvider>(relaxed = true)
        coEvery { dataProvider.getDirtyBookKeys() } returns emptyList()
        coEvery { dataProvider.getDirtyPreferenceKeys() } returns emptySet()

        val manifestManager = ManifestManager(transport)
        val stateMachine = SyncStateMachine()
        val engine = SyncEngine(manifestManager, stateMachine, dataProvider, "device-1")

        engine.sync(transport)

        coVerify {
            transport.write("manifest.json", withArg { data ->
                val manifest = json.decodeFromString<SyncManifest>(data.decodeToString())
                assertEquals("version 应递增", 11, manifest.version)
                assertEquals("updatedBy 应为设备 ID", "device-1", manifest.updatedBy)
            }, etag = "\"abc\"")
        }
    }

    // ── Tombstone 清理测试 ──────────────────────────────────────────────

    @Test
    fun `sync compacts tombstones when all devices have synced past deletion`() = runTest {
        val transport = createDefaultTransport()

        // 书签中有 1 个 tombstone（已标记删除）
        val tombstone = BookmarkDto(id = "bm-deleted", byteOffset = 100, updatedAt = 1000L, deleted = true)
        val activeBookmark = BookmarkDto(id = "bm-active", byteOffset = 200, updatedAt = 2000L)

        coEvery { transport.list("state") } returns listOf(
            TransportResourceInfo(path = "state/book-1.json", contentLength = 100),
        )
        coEvery { transport.read("state/book-1.json") } returns null
        coEvery { transport.read("bookmarks/book-1.json") } returns null
        coEvery { transport.read("notes/book-1.json") } returns null

        val dataProvider = mockk<SyncDataProvider>(relaxed = true)
        coEvery { dataProvider.getDirtyBookKeys() } returns listOf("book-1")
        coEvery { dataProvider.getLocalState("book-1") } returns SyncBookState(bookKey = "book-1")
        coEvery { dataProvider.getLocalBookmarks("book-1") } returns listOf(tombstone, activeBookmark)
        coEvery { dataProvider.getLocalNotes("book-1") } returns emptyList()
        coEvery { dataProvider.getDirtyPreferenceKeys() } returns emptySet()
        // 设备都已同步过（lastSyncAt > tombstone.updatedAt=1000）
        coEvery { dataProvider.getDevices() } returns listOf(
            DeviceInfo(deviceId = "device-a", lastSyncAt = 2000L),
            DeviceInfo(deviceId = "device-b", lastSyncAt = 3000L),
        )

        val manifestManager = ManifestManager(transport)
        val stateMachine = SyncStateMachine()
        val engine = SyncEngine(manifestManager, stateMachine, dataProvider, "device-1")

        engine.sync(transport)

        // 验证 tombstone 被清理
        coVerify {
            dataProvider.deleteTombstoneBookmarks("book-1", listOf("bm-deleted"))
        }
    }

    @Test
    fun `sync does NOT compact tombstones when a device has not synced yet`() = runTest {
        val transport = createDefaultTransport()

        val tombstone = BookmarkDto(id = "bm-deleted", byteOffset = 100, updatedAt = 5000L, deleted = true)
        val activeBookmark = BookmarkDto(id = "bm-active", byteOffset = 200, updatedAt = 2000L)

        coEvery { transport.list("state") } returns listOf(
            TransportResourceInfo(path = "state/book-1.json", contentLength = 100),
        )
        coEvery { transport.read("state/book-1.json") } returns null
        coEvery { transport.read("bookmarks/book-1.json") } returns null
        coEvery { transport.read("notes/book-1.json") } returns null

        val dataProvider = mockk<SyncDataProvider>(relaxed = true)
        coEvery { dataProvider.getDirtyBookKeys() } returns listOf("book-1")
        coEvery { dataProvider.getLocalState("book-1") } returns SyncBookState(bookKey = "book-1")
        coEvery { dataProvider.getLocalBookmarks("book-1") } returns listOf(tombstone, activeBookmark)
        coEvery { dataProvider.getLocalNotes("book-1") } returns emptyList()
        coEvery { dataProvider.getDirtyPreferenceKeys() } returns emptySet()
        // 设备 B 的 lastSyncAt < tombstone.updatedAt（未同步过删除）
        coEvery { dataProvider.getDevices() } returns listOf(
            DeviceInfo(deviceId = "device-a", lastSyncAt = 6000L),
            DeviceInfo(deviceId = "device-b", lastSyncAt = 3000L), // < 5000
        )

        val manifestManager = ManifestManager(transport)
        val stateMachine = SyncStateMachine()
        val engine = SyncEngine(manifestManager, stateMachine, dataProvider, "device-1")

        engine.sync(transport)

        // 验证 tombstone 未被清理
        coVerify(exactly = 0) { dataProvider.deleteTombstoneBookmarks(any(), any()) }
    }

    // ── 错误处理测试 ──────────────────────────────────────────────

    @Test
    fun `sync transitions to FAILED on exception`() = runTest {
        val transport = createDefaultTransport()
        // ensureDirectories() 不被 try-catch 包裹，异常会传播
        coEvery { transport.ensureDirectories() } throws RuntimeException("Network error")

        val manifestManager = ManifestManager(transport)
        val stateMachine = SyncStateMachine()
        val engine = SyncEngine(manifestManager, stateMachine)

        try {
            engine.sync(transport)
        } catch (_: Exception) {
            // expected
        }

        assertEquals(SyncState.FAILED, stateMachine.state.value)
    }

    @Test
    fun `sync re-throws exception after FAILED transition`() = runTest {
        val transport = createDefaultTransport()
        coEvery { transport.ensureDirectories() } throws RuntimeException("Network error")

        val manifestManager = ManifestManager(transport)
        val stateMachine = SyncStateMachine()
        val engine = SyncEngine(manifestManager, stateMachine)

        var caught: Exception? = null
        try {
            engine.sync(transport)
        } catch (e: Exception) {
            caught = e
        }

        assertTrue("应重新抛出异常", caught is RuntimeException)
        assertEquals(SyncState.FAILED, stateMachine.state.value)
    }

    // ── 限流重试测试 ──────────────────────────────────────────────

    @Test
    fun `sync retries on rate limit and succeeds`() = runTest {
        // 使用非 relaxed mock 避免 ByteArray 类型转换问题
        val transport = mockk<SyncTransport>()
        coEvery { transport.ensureDirectories() } returns Unit
        coEvery { transport.read("manifest.json") } returns """{"schemaVersion":2,"version":0}""".toByteArray()
        coEvery { transport.getMetadata("manifest.json") } returns null
        coEvery { transport.list("state") } returns emptyList()
        coEvery { transport.read("config/preferences.json") } returns null
        // 第一次上传时抛限流异常，第二次成功
        var callCount = 0
        coEvery { transport.write(any(), any(), any()) } answers {
            callCount++
            if (callCount == 1) throw WebDavRateLimitException("rate limited", retryAfterSeconds = 1)
        }

        val dataProvider = mockk<SyncDataProvider>()
        coEvery { dataProvider.getDirtyBookKeys() } returns listOf("book-1")
        coEvery { dataProvider.getLocalState("book-1") } returns SyncBookState(bookKey = "book-1")
        coEvery { dataProvider.getLocalBookmarks(any()) } returns emptyList()
        coEvery { dataProvider.getLocalNotes(any()) } returns emptyList()
        coEvery { dataProvider.getDirtyPreferenceKeys() } returns emptySet()
        coEvery { dataProvider.getBookFileSize(any()) } returns 0L
        coEvery { dataProvider.saveLocalState(any(), any()) } returns Unit
        coEvery { dataProvider.saveLocalBookmarks(any(), any()) } returns Unit
        coEvery { dataProvider.saveLocalNotes(any(), any()) } returns Unit
        coEvery { dataProvider.clearDirtyFlags(any()) } returns Unit
        coEvery { dataProvider.clearDirtyPreferenceKeys() } returns Unit

        val manifestManager = ManifestManager(transport)
        val stateMachine = SyncStateMachine()
        // 使用极短的等待时间避免测试超时
        val handler = mockk<RateLimitHandler>()
        coEvery { handler.computeWaitMs(any(), any()) } returns 10L
        val engine = SyncEngine(manifestManager, stateMachine, dataProvider, "device-1",
            rateLimitHandler = handler, maxRetries = 3)

        engine.sync(transport)

        assertEquals(SyncState.SUCCESS, stateMachine.state.value)
    }

    @Test
    fun `sync fails after max retries exceeded`() = runTest {
        // 使用非 relaxed mock 避免 ByteArray 类型转换问题
        val transport = mockk<SyncTransport>()
        coEvery { transport.ensureDirectories() } returns Unit
        coEvery { transport.read("manifest.json") } returns """{"schemaVersion":2,"version":0}""".toByteArray()
        coEvery { transport.getMetadata("manifest.json") } returns null
        coEvery { transport.list("state") } returns emptyList()
        coEvery { transport.read("config/preferences.json") } returns null
        // 每次上传都抛限流异常
        coEvery { transport.write(any(), any(), any()) } throws
            WebDavRateLimitException("rate limited", retryAfterSeconds = 1)

        val dataProvider = mockk<SyncDataProvider>()
        coEvery { dataProvider.getDirtyBookKeys() } returns listOf("book-1")
        coEvery { dataProvider.getLocalState("book-1") } returns SyncBookState(bookKey = "book-1")
        coEvery { dataProvider.getLocalBookmarks(any()) } returns emptyList()
        coEvery { dataProvider.getLocalNotes(any()) } returns emptyList()
        coEvery { dataProvider.getDirtyPreferenceKeys() } returns emptySet()
        coEvery { dataProvider.getBookFileSize(any()) } returns 0L
        coEvery { dataProvider.saveLocalState(any(), any()) } returns Unit
        coEvery { dataProvider.saveLocalBookmarks(any(), any()) } returns Unit
        coEvery { dataProvider.saveLocalNotes(any(), any()) } returns Unit
        coEvery { dataProvider.clearDirtyFlags(any()) } returns Unit
        coEvery { dataProvider.clearDirtyPreferenceKeys() } returns Unit

        val manifestManager = ManifestManager(transport)
        val stateMachine = SyncStateMachine()
        val handler = mockk<RateLimitHandler>()
        coEvery { handler.computeWaitMs(any(), any()) } returns 10L
        val engine = SyncEngine(manifestManager, stateMachine, dataProvider, "device-1",
            rateLimitHandler = handler, maxRetries = 3)

        var caught: Exception? = null
        try {
            engine.sync(transport)
        } catch (e: Exception) {
            caught = e
        }

        assertTrue("应抛出 WebDavRateLimitException", caught is WebDavRateLimitException)
        assertEquals(SyncState.FAILED, stateMachine.state.value)
    }
}
