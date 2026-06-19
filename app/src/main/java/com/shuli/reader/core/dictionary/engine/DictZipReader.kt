package com.shuli.reader.core.dictionary.engine

import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.util.zip.Inflater

/**
 * DictZip (.dict.dz) 文件随机访问读取器
 *
 * DictZip 是 Stardict 的压缩格式，支持随机访问：
 * - 文件头部包含块索引（每个块的压缩偏移和原始大小）
 * - 读取时定位到对应块，解压后提取数据
 */
class DictZipReader(
    private val filePath: String,
) : AutoCloseable {

    private val file = RandomAccessFile(filePath, "r")
    private var chunks: List<ChunkInfo> = emptyList()
    private var headerSize: Int = 0

    init {
        parseHeader()
    }

    /**
     * 解析 DictZip 文件头
     *
     * 文件格式：
     * - GZip 头（10 字节）
     * - FEXTRA 标志（如果有）
     * - RA 块索引
     */
    private fun parseHeader() {
        file.seek(0)

        // 读取 GZip 头
        val id1 = file.readByte().toInt() and 0xFF
        val id2 = file.readByte().toInt() and 0xFF
        if (id1 != 0x1F || id2 != 0x8B) {
            throw IllegalArgumentException("Not a valid gzip file: $filePath")
        }

        val method = file.readByte().toInt() and 0xFF
        val flags = file.readByte().toInt() and 0xFF

        // 跳过 mtime, xfl, os
        file.skipBytes(6)

        // 检查 FEXTRA 标志
        if ((flags and 0x04) != 0) {
            val xlen = file.readUnsignedShort()

            // 读取子字段
            val startPos = file.filePointer
            var pos = startPos

            while (pos < startPos + xlen) {
                file.seek(pos)
                val subId1 = file.readByte().toInt() and 0xFF
                val subId2 = file.readByte().toInt() and 0xFF
                val subLen = file.readUnsignedShort()

                if (subId1 == 'R'.code && subId2 == 'A'.code) {
                    // 找到 RA 块索引
                    parseRaHeader(subLen)
                    break
                }

                pos += 4 + subLen
            }

            file.seek(startPos + xlen)
        }

        // 跳过 FNAME, FCOMMENT, FHCRC
        if ((flags and 0x08) != 0) {
            while (file.readByte().toInt() != 0) {}
        }
        if ((flags and 0x10) != 0) {
            while (file.readByte().toInt() != 0) {}
        }
        if ((flags and 0x02) != 0) {
            file.skipBytes(2)
        }

        headerSize = file.filePointer.toInt()
    }

    /**
     * 解析 RA（Random Access）子字段
     */
    private fun parseRaHeader(length: Int) {
        val startPos = file.filePointer

        // 读取版本
        val version = file.readShort().toInt()

        // 读取块大小
        val chunkLength = file.readShort().toInt() and 0xFFFF

        // 读取块数量
        val chunkCount = file.readShort().toInt() and 0xFFFF

        // 读取每个块的压缩大小
        val chunkSizes = mutableListOf<Int>()
        for (i in 0 until chunkCount) {
            chunkSizes.add(file.readShort().toInt() and 0xFFFF)
        }

        // 计算每个块的压缩偏移
        val chunkList = mutableListOf<ChunkInfo>()
        var compressedOffset = headerSize.toLong()

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
     * @param offset 原始文件中的偏移
     * @param size 数据大小
     * @param charset 字符编码
     * @return 解码后的字符串
     */
    fun read(offset: Long, size: Int, charset: Charset): String {
        if (chunks.isEmpty()) {
            throw IllegalStateException("DictZip not initialized")
        }

        val result = ByteArray(size)
        var remaining = size
        var resultPos = 0
        var currentOffset = offset

        while (remaining > 0) {
            // 找到对应的块
            val chunkIndex = (currentOffset / chunks[0].uncompressedSize).toInt()
            if (chunkIndex >= chunks.size) break

            val chunk = chunks[chunkIndex]
            val chunkOffset = (currentOffset % chunk.uncompressedSize).toInt()
            val bytesToRead = minOf(remaining, chunk.uncompressedSize - chunkOffset)

            // 解压块
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
     * 解压指定块
     */
    private fun decompressChunk(chunk: ChunkInfo): ByteArray {
        val compressedData = ByteArray(chunk.compressedSize)
        file.seek(chunk.compressedOffset)
        file.readFully(compressedData)

        val inflater = Inflater(true)
        inflater.setInput(compressedData)

        val output = ByteArray(chunk.uncompressedSize * 2) // 预留足够空间
        val resultBuilder = mutableListOf<Byte>()

        try {
            val buffer = ByteArray(1024)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsDictionary()) {
                        break
                    }
                    if (inflater.finished()) break
                }
                for (i in 0 until count) {
                    resultBuilder.add(buffer[i])
                }
            }
        } finally {
            inflater.end()
        }

        return resultBuilder.toByteArray()
    }

    override fun close() {
        file.close()
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
