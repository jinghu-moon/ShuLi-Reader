package com.shuli.reader.sync.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class WebDavSyncManagerTest {

    @Test
    fun uploadProgress_writesFixedProgressPath() = runBlocking {
        val remote = FakeProgressRemote()
        val manager = WebDavSyncManager(remote)
        val progress = progress(bookId = 7L)

        manager.uploadProgress(progress)

        assertEquals("progress/7.json", remote.uploadedPath)
        assertEquals(true, remote.uploadedBody.contains("\"bookId\":7"))
    }

    @Test
    fun downloadProgress_parsesRemoteProgress() = runBlocking {
        val remote = FakeProgressRemote(
            downloadBody = """{"bookId":9,"chapterIndex":2,"chapterPos":45,"chapterTitle":"c","updatedAt":100,"deviceId":"remote"}""",
        )
        val manager = WebDavSyncManager(remote)

        val progress = manager.downloadProgress(9L)

        assertEquals(9L, progress.bookId)
        assertEquals(2, progress.chapterIndex)
        assertEquals(45, progress.chapterPos)
    }

    @Test
    fun latestWins_selectsNewerProgress() {
        val manager = WebDavSyncManager(FakeProgressRemote())
        val local = progress(updatedAt = 100L, deviceId = "local")
        val remote = progress(updatedAt = 200L, deviceId = "remote")

        val resolved = manager.resolveConflict(local, remote)

        assertEquals("remote", resolved.deviceId)
    }

    @Test
    fun localWins_prefersLocalProgress() {
        val manager = WebDavSyncManager(FakeProgressRemote())
        val local = progress(updatedAt = 100L, deviceId = "local")
        val remote = progress(updatedAt = 200L, deviceId = "remote")

        val resolved = manager.resolveConflict(local, remote, ConflictPolicy.LOCAL_WINS)

        assertEquals("local", resolved.deviceId)
    }

    @Test
    fun offlineQueue_keepsPendingItemAfterFailure() = runBlocking {
        val remote = FakeProgressRemote(shouldFail = true)
        val manager = WebDavSyncManager(remote)

        manager.enqueueUpload(progress(bookId = 1L))
        val uploaded = manager.flushPendingUploads()

        assertEquals(0, uploaded)
        assertEquals(1, manager.pendingCount())
    }

    private fun progress(
        bookId: Long = 1L,
        updatedAt: Long = 100L,
        deviceId: String = "device",
    ): BookProgress {
        return BookProgress(
            bookId = bookId,
            chapterIndex = 1,
            chapterPos = 12,
            chapterTitle = "Chapter",
            updatedAt = updatedAt,
            deviceId = deviceId,
        )
    }

    private class FakeProgressRemote(
        private val shouldFail: Boolean = false,
        private val downloadBody: String = "",
    ) : ProgressRemote {
        var uploadedPath = ""
        var uploadedBody = ""

        override suspend fun upload(path: String, body: String) {
            if (shouldFail) error("offline")
            uploadedPath = path
            uploadedBody = body
        }

        override suspend fun download(path: String): String {
            return downloadBody
        }
    }
}
