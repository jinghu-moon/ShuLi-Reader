package com.shuli.reader.sync.engine.hash

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * 三点采样快速哈希（T-03）
 *
 * 采样策略（§6.2）：
 * - 段 1：文件头 4KB
 * - 段 2：offset = fileSize / 3 处 4KB（当 fileSize > 8192）
 * - 段 3：文件末尾 4KB（当 fileSize > 4096）
 * - 追加 8 字节 fileSize（Big-Endian Long）
 * - 返回 SHA-256 hex 字符串
 */
object FastHasher {

    private const val CHUNK_SIZE = 4096

    /**
     * 计算文件的三点采样哈希
     */
    fun compute(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val fileSize = file.length()

        RandomAccessFile(file, "r").use { raf ->
            // 段 1：文件头 4KB
            val head = readChunk(raf, 0, CHUNK_SIZE)
            digest.update(head)

            // 段 2：offset = fileSize / 3 处 4KB（当 fileSize > 8192）
            if (fileSize > CHUNK_SIZE * 2) {
                val midOffset = fileSize / 3
                val mid = readChunk(raf, midOffset, CHUNK_SIZE)
                digest.update(mid)
            }

            // 段 3：文件末尾 4KB（当 fileSize > 4096）
            if (fileSize > CHUNK_SIZE) {
                val tailOffset = maxOf(0L, fileSize - CHUNK_SIZE)
                val tail = readChunk(raf, tailOffset, CHUNK_SIZE)
                digest.update(tail)
            }

            // 追加 8 字节 fileSize（Big-Endian Long）
            val sizeBytes = ByteArray(8)
            for (i in 7 downTo 0) {
                sizeBytes[7 - i] = ((fileSize shr (i * 8)) and 0xFF).toByte()
            }
            digest.update(sizeBytes)
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readChunk(raf: RandomAccessFile, offset: Long, maxBytes: Int): ByteArray {
        raf.seek(offset)
        val buffer = ByteArray(maxBytes)
        val bytesRead = raf.read(buffer)
        return if (bytesRead <= 0) {
            ByteArray(0)
        } else if (bytesRead < maxBytes) {
            buffer.copyOf(bytesRead)
        } else {
            buffer
        }
    }
}
