package com.shuli.reader.mdict.io

import com.shuli.reader.mdict.CorruptDictException

/**
 * 顺序读游标，统一处理 MDX 的大端整数与变宽字段。
 *
 * MDX 多字节整数均为大端序（big-endian）。JVM 无无符号类型，
 * 8 字节计数一律用 Long 承载；按字节取值时 `and 0xFF` 防符号扩展。
 */
class ByteCursor(private val data: ByteArray, start: Int = 0) {

    var pos: Int = start
        private set

    val remaining: Int get() = data.size - pos

    fun hasRemaining(): Boolean = pos < data.size

    fun skip(n: Int) {
        ensure(n)
        pos += n
    }

    /** 大端 uint8。 */
    fun u8(): Int {
        ensure(1)
        return data[pos++].toInt() and 0xFF
    }

    /** 大端 uint16 → Int。 */
    fun u16(): Int {
        ensure(2)
        val v = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
        pos += 2
        return v
    }

    /** 大端 uint32 → Long（避免符号位）。 */
    fun u32(): Long {
        ensure(4)
        var v = 0L
        for (i in 0 until 4) v = (v shl 8) or (data[pos + i].toLong() and 0xFF)
        pos += 4
        return v
    }

    /** 大端 uint64 → Long（高位若置位则为负，MDX 实际不会超出 Long 正范围）。 */
    fun u64(): Long {
        ensure(8)
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (data[pos + i].toLong() and 0xFF)
        pos += 8
        return v
    }

    /**
     * 按版本宽度读计数字段：numberWidth=8 读 u64，=4 读 u32。
     */
    fun number(numberWidth: Int): Long = when (numberWidth) {
        8 -> u64()
        4 -> u32()
        else -> throw CorruptDictException("invalid numberWidth=$numberWidth")
    }

    /** 读 [len] 字节并按 [charset] 解码为字符串。 */
    fun string(len: Int, charset: java.nio.charset.Charset): String {
        ensure(len)
        val s = String(data, pos, len, charset)
        pos += len
        return s
    }

    private fun ensure(n: Int) {
        if (pos + n > data.size) {
            throw CorruptDictException("read past end: need $n at $pos, size ${data.size}")
        }
    }
}
