package com.shuli.reader.core.dictionary.engine

import java.io.ByteArrayOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.zip.Inflater

/**
 * DictZip (.dict.dz) 文件随机访问读取器
 *
 * DictZip 是 Stardict 的压缩格式，支持随机访问：
 * - 文件头部包含块索引（每个块的压缩偏移和原始大小）
 * - 读取时定位到对应块，解压后提取数据
 *
 * 参考规范：man dictzip / RFC1952 (GZIP)
 */
class DictZipReader(
    private val filePath: String,
) : AutoCloseable {

    private val file = RandomAccessFile(filePath, "r")
    private var chunks: List<ChunkInfo> = emptyList()
    /** 压缩数据开始的文件偏移（GZip 头结束后） */
    private var dataStartOffset: Long = 0

    /** 最近解压的块缓存（volatile 保证线程可见性） */
    @Volatile
    private var cachedChunkIndex: Int = -1
    @Volatile
    private var cachedChunkData: ByteArray? = null

    /** 同步锁，保护 Inflater 和文件读取 */
    private val lock = Any()

    init {
        parseHeader()
    }

    /**
     * 解析 DictZip 文件头
     *
     * 文件格式（RFC1952）：
     * - GZip 头（10 字节固定）
     * - FEXTRA 标志（FLG bit 2）
     * - XLEN (2 bytes, little-endian)
     * - 子字段：SI1='R', SI2='A', SLEN, RA data...
     * - FNAME, FCOMMENT, FHCRC（可选）
     * - 压缩数据开始
     */
    private fun parseHeader() {
        file.seek(0)

        // 读取 GZip 固定头（10 字节）
        val header = ByteArray(10)
        file.readFully(header)

        val id1 = header[0].toInt() and 0xFF
        val id2 = header[1].toInt() and 0xFF
        if (id1 != 0x1F || id2 != 0x8B) {
            throw IllegalArgumentException("Not a valid gzip file: $filePath")
        }

        val flags = header[3].toInt() and 0xFF

        // 第一步：预扫描所有可选头字段，计算 dataStartOffset
        val savedPos = file.filePointer
        skipOptionalHeaders(flags)
        dataStartOffset = file.filePointer
        file.seek(savedPos)

        // 第二步：处理 FEXTRA（如果有）
        if ((flags and 0x04) != 0) {
            // 读取 XLEN（小端序）
            val xlenBuf = ByteArray(2)
            file.readFully(xlenBuf)
            val xlen = (xlenBuf[0].toInt() and 0xFF) or ((xlenBuf[1].toInt() and 0xFF) shl 8)

            // 读取子字段
            val startPos = file.filePointer
            var pos = startPos

            while (pos < startPos + xlen) {
                file.seek(pos)
                val si1 = file.readByte().toInt() and 0xFF
                val si2 = file.readByte().toInt() and 0xFF

                // 读取子字段长度（小端序）
                val slenBuf = ByteArray(2)
                file.readFully(slenBuf)
                val slen = (slenBuf[0].toInt() and 0xFF) or ((slenBuf[1].toInt() and 0xFF) shl 8)

                if (si1 == 'R'.code && si2 == 'A'.code) {
                    // 找到 RA 块索引
                    parseRaHeader(slen)
                    break
                }

                pos += 4 + slen
            }
        }
    }

    /**
     * 跳过所有可选头字段（FNAME, FCOMMENT, FHCRC）
     * 用于预计算 dataStartOffset
     */
    private fun skipOptionalHeaders(flags: Int) {
        // 跳过 FEXTRA
        if ((flags and 0x04) != 0) {
            val xlenBuf = ByteArray(2)
            file.readFully(xlenBuf)
            val xlen = (xlenBuf[0].toInt() and 0xFF) or ((xlenBuf[1].toInt() and 0xFF) shl 8)
            file.skipBytes(xlen)
        }

        // 跳过 FNAME（FLG bit 3）
        if ((flags and 0x08) != 0) {
            while (file.readByte().toInt() != 0) {}
        }
        // 跳过 FCOMMENT（FLG bit 4）
        if ((flags and 0x10) != 0) {
            while (file.readByte().toInt() != 0) {}
        }
        // 跳过 FHCRC（FLG bit 1）
        if ((flags and 0x02) != 0) {
            file.skipBytes(2)
        }
    }

    /**
     * 解析 RA（Random Access）子字段
     *
     * RA 格式（小端序）：
     * - version: 2 bytes
     * - chunk_length: 2 bytes（每个块解压后的大小）
     * - chunk_count: 2 bytes
     * - chunks: chunk_count 个 2 bytes（每个块的压缩大小）
     */
    private fun parseRaHeader(length: Int) {
        // 读取整个 RA 数据到缓冲区（小端序处理）
        val raData = ByteArray(length)
        file.readFully(raData)
        val buf = ByteBuffer.wrap(raData).order(ByteOrder.LITTLE_ENDIAN)

        // 读取版本
        val version = buf.short.toInt() and 0xFFFF

        // 读取块大小
        val chunkLength = buf.short.toInt() and 0xFFFF

        // 读取块数量
        val chunkCount = buf.short.toInt() and 0xFFFF

        // 读取每个块的压缩大小
        val chunkSizes = IntArray(chunkCount)
        for (i in 0 until chunkCount) {
            chunkSizes[i] = buf.short.toInt() and 0xFFFF
        }

        // 计算每个块的压缩偏移（从 dataStartOffset 开始）
        val chunkList = mutableListOf<ChunkInfo>()
        var compressedOffset = dataStartOffset

        for (i in 0 until chunkCount) {
            val compressedSize = chunkSizes[i]
            chunkList.add(ChunkInfo(
                index = i,
                compressedOffset = compressedOffset,
                compressedSize = compressedSize,
                uncompressedSize = chunkLength,
            ))
            compressedOffset += compressedSize
        }

        chunks = chunkList
    }

    /**
     * 读取指定偏移和大小的数据
     *
     * @param offset 解压后数据中的偏移
     * @param size 数据大小（字节）
     * @param charset 字符编码
     * @return 解码后的字符串
     */
    fun read(offset: Long, size: Int, charset: Charset): String {
        if (chunks.isEmpty()) {
            throw IllegalStateException("DictZip not initialized: no chunks loaded")
        }

        val chunkSize = chunks[0].uncompressedSize
        val result = ByteArray(size)
        var remaining = size
        var resultPos = 0
        var currentOffset = offset

        while (remaining > 0) {
            // 找到对应的块
            val chunkIndex = (currentOffset / chunkSize).toInt()
            if (chunkIndex >= chunks.size) break

            val chunk = chunks[chunkIndex]
            val chunkOffset = (currentOffset % chunkSize).toInt()
            val bytesToRead = minOf(remaining, chunkSize - chunkOffset)

            // 解压块（内部已同步）
            val decompressed = decompressChunk(chunk)

            // 复制数据
            System.arraycopy(decompressed, chunkOffset, result, resultPos, bytesToRead)

            resultPos += bytesToRead
            remaining -= bytesToRead
            currentOffset += bytesToRead
        }

        return String(result, charset)
    }

    /**
     * 读取指定偏移和大小的数据（返回 ByteArray）
     *
     * @param offset 解压后数据中的偏移
     * @param size 数据大小（字节）
     * @return 原始字节数据
     */
    fun readBytes(offset: Long, size: Int): ByteArray {
        if (chunks.isEmpty()) {
            throw IllegalStateException("DictZip not initialized: no chunks loaded")
        }

        val chunkSize = chunks[0].uncompressedSize
        val result = ByteArray(size)
        var remaining = size
        var resultPos = 0
        var currentOffset = offset

        while (remaining > 0) {
            val chunkIndex = (currentOffset / chunkSize).toInt()
            if (chunkIndex >= chunks.size) break

            val chunk = chunks[chunkIndex]
            val chunkOffset = (currentOffset % chunkSize).toInt()
            val bytesToRead = minOf(remaining, chunkSize - chunkOffset)

            val decompressed = decompressChunk(chunk)
            System.arraycopy(decompressed, chunkOffset, result, resultPos, bytesToRead)

            resultPos += bytesToRead
            remaining -= bytesToRead
            currentOffset += bytesToRead
        }

        return result
    }

    /**
     * 解压指定块
     *
     * 使用块缓存和 synchronized 保证线程安全
     */
    private fun decompressChunk(chunk: ChunkInfo): ByteArray {
        // 检查缓存（volatile 读取）
        if (chunk.index == cachedChunkIndex) {
            cachedChunkData?.let { return it }
        }

        // 同步块：保护文件读取和 Inflater
        synchronized(lock) {
            // 双重检查缓存
            if (chunk.index == cachedChunkIndex) {
                cachedChunkData?.let { return it }
            }

            val compressedData = ByteArray(chunk.compressedSize)
            file.seek(chunk.compressedOffset)
            file.readFully(compressedData)

            // 每次创建新的 Inflater（线程安全，避免共享状态）
            val inflater = Inflater(true)
            inflater.setInput(compressedData)

            val outputStream = ByteArrayOutputStream(chunk.uncompressedSize)
            val buffer = ByteArray(4096)

            try {
                while (!inflater.finished()) {
                    val count = inflater.inflate(buffer)
                    if (count == 0) {
                        if (inflater.needsDictionary()) {
                            throw IllegalStateException("Inflater needs dictionary")
                        }
                        if (inflater.finished()) break
                        if (inflater.needsInput()) {
                            throw IllegalStateException("Inflater needs more input")
                        }
                    }
                    outputStream.write(buffer, 0, count)
                }
            } finally {
                inflater.end()
            }

            val result = outputStream.toByteArray()

            // 更新缓存（volatile 写入）
            cachedChunkIndex = chunk.index
            cachedChunkData = result

            return result
        }
    }

    override fun close() {
        synchronized(lock) {
            file.close()
            cachedChunkData = null
            cachedChunkIndex = -1
        }
    }

    /**
     * 块信息
     */
    private data class ChunkInfo(
        val index: Int,
        val compressedOffset: Long,
        val compressedSize: Int,
        val uncompressedSize: Int,
    )
}
