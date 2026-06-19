package com.shuli.reader.mdict.io

import java.io.Closeable
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * 词典文件的随机访问读取器。对应 docs/38 §8.1。
 *
 * 用 [FileChannel.read]（position 形式）做无状态定位读，天然支持 Long offset，
 * 规避 MappedByteBuffer.position(Int) 的 2GB 溢出陷阱。不默认 mmap：
 * MDD 可达 1GB+，mmap 整文件在 Android 上回收不可控。
 *
 * 线程安全：FileChannel 的 position-read 不修改通道全局 position，
 * 可在多协程下并发读（JDK 保证 read(buf, position) 的原子性）。
 */
class BlockReader(file: java.io.File) : Closeable {

    private val raf = RandomAccessFile(file, "r")
    private val channel: FileChannel = raf.channel

    /** 文件总字节数。 */
    val size: Long get() = channel.size()

    /**
     * 从 [offset] 读取 [length] 字节。
     *
     * @throws com.shuli.reader.mdict.CorruptDictException 若读到的字节数不足（文件被截断）
     */
    fun read(offset: Long, length: Int): ByteArray {
        require(length >= 0) { "length must be >= 0, was $length" }
        val buf = ByteBuffer.allocate(length)
        var pos = offset
        var total = 0
        while (total < length) {
            val n = channel.read(buf, pos)
            if (n < 0) break // EOF
            total += n
            pos += n
        }
        if (total != length) {
            throw com.shuli.reader.mdict.CorruptDictException(
                "expected $length bytes at offset $offset, but only read $total (file truncated?)"
            )
        }
        return buf.array()
    }

    override fun close() {
        channel.close()
        raf.close()
    }
}
