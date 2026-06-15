package com.shuli.reader.sync.engine

import com.shuli.reader.sync.network.webdav.WebDavClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BookProgress(
    val bookId: Long,
    val chapterIndex: Int,
    val chapterPos: Int,
    val chapterTitle: String?,
    val updatedAt: Long,
    val deviceId: String,
)

enum class ConflictPolicy {
    LATEST_WINS,
    LOCAL_WINS,
    REMOTE_WINS,
}

interface ProgressRemote {
    suspend fun upload(path: String, body: String)
    suspend fun download(path: String): String
}

class WebDavProgressRemote(
    private val client: WebDavClient,
) : ProgressRemote {
    override suspend fun upload(path: String, body: String) {
        client.put(path, body)
    }

    override suspend fun download(path: String): String {
        return client.get(path).body
    }
}

class WebDavSyncManager(
    private val remote: ProgressRemote,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val pendingUploads = ArrayDeque<BookProgress>()

    suspend fun uploadProgress(progress: BookProgress) {
        remote.upload(progressPath(progress.bookId), json.encodeToString(progress))
    }

    suspend fun downloadProgress(bookId: Long): BookProgress {
        return json.decodeFromString(remote.download(progressPath(bookId)))
    }

    fun resolveConflict(
        local: BookProgress,
        remote: BookProgress,
        policy: ConflictPolicy = ConflictPolicy.LATEST_WINS,
    ): BookProgress {
        return when (policy) {
            ConflictPolicy.LOCAL_WINS -> local
            ConflictPolicy.REMOTE_WINS -> remote
            ConflictPolicy.LATEST_WINS -> if (remote.updatedAt > local.updatedAt) remote else local
        }
    }

    fun enqueueUpload(progress: BookProgress) {
        pendingUploads += progress
    }

    suspend fun flushPendingUploads(): Int {
        var uploaded = 0
        val remaining = ArrayDeque<BookProgress>()

        while (pendingUploads.isNotEmpty()) {
            val progress = pendingUploads.removeFirst()
            runCatching {
                uploadProgress(progress)
            }.onSuccess {
                uploaded++
            }.onFailure {
                remaining += progress
            }
        }

        pendingUploads += remaining
        return uploaded
    }

    fun pendingCount(): Int = pendingUploads.size

    private fun progressPath(bookId: Long): String = "progress/$bookId.json"
}
