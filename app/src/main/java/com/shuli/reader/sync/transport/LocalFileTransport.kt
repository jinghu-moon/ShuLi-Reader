package com.shuli.reader.sync.transport

import java.io.File

/**
 * 本地文件传输实现（T-16）
 *
 * 实现 SyncTransport 接口，使用本地文件系统。
 * 写入操作使用原子写入（临时文件 + rename）。
 */
class LocalFileTransport(
    private val rootDir: File,
) : SyncTransport {

    init {
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
    }

    override suspend fun read(path: String): ByteArray? {
        val file = resolveFile(path)
        return if (file.exists()) file.readBytes() else null
    }

    override suspend fun write(path: String, data: ByteArray, etag: String?) {
        val file = resolveFile(path)
        file.parentFile?.mkdirs()
        atomicWrite(file, data)
    }

    override suspend fun delete(path: String) {
        val file = resolveFile(path)
        if (file.exists()) {
            file.delete()
        }
    }

    override suspend fun list(path: String): List<TransportResourceInfo> {
        val dir = resolveFile(path)
        if (!dir.isDirectory) return emptyList()

        return dir.listFiles()?.map { file ->
            TransportResourceInfo(
                path = "$path/${file.name}",
                isDirectory = file.isDirectory,
                contentLength = if (file.isFile) file.length() else 0L,
            )
        } ?: emptyList()
    }

    override suspend fun exists(path: String): Boolean {
        return resolveFile(path).exists()
    }

    override suspend fun getMetadata(path: String): TransportResourceMetadata? {
        val file = resolveFile(path)
        if (!file.exists()) return null
        return TransportResourceMetadata(
            contentLength = file.length(),
            lastModified = file.lastModified().toString(),
        )
    }

    private fun resolveFile(path: String): File {
        return File(rootDir, path)
    }

    /**
     * 原子写入：写入临时文件 + rename
     */
    private fun atomicWrite(target: File, data: ByteArray) {
        val tmpFile = File(target.parentFile, ".tmp-${System.nanoTime()}-${target.name}")
        try {
            tmpFile.writeBytes(data)
            tmpFile.renameTo(target)
        } finally {
            if (tmpFile.exists()) {
                tmpFile.delete()
            }
        }
    }
}
