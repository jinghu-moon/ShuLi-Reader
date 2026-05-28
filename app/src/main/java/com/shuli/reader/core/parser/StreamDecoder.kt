package com.shuli.reader.core.parser

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 一段已解码的文本，携带 UTF-16 unit index → 字节偏移的映射表。
 *
 * 重要：
 * - [text] 是 Java/Kotlin UTF-16 String，索引为 UTF-16 code unit（不是 codepoint）。
 * - [utf16IndexToByte] 长度 = text.length + 1，存储的是**相对 [byteStart] 的偏移**（4 字节够用，
 *   即使整个文件 > 2GB 也安全，因为单段 ≤ 128KB）。
 * - map[text.length] 是段内终点字节相对偏移（exclusive，等于 byteEnd - byteStart）。
 * - 精度：单步 buffer = 64 char，同段多个字符共享起点字节偏移（粗近似，分页/书签足够；
 *   若需 char-perfect，把 [StreamDecoder.DEFAULT_OUT_CAPACITY] 调到 2 即可）。
 */
data class DecodedSegment(
    val text: String,
    val byteStart: Long,
    val byteEnd: Long,
    val utf16IndexToByte: IntArray,
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)

    /** 在 segment 内把 UTF-16 unit index 转为绝对文件字节偏移。 */
    fun byteAt(utf16Index: Int): Long {
        val i = utf16Index.coerceIn(0, utf16IndexToByte.size - 1)
        return byteStart + utf16IndexToByte[i].toLong()
    }
}

/**
 * 字节窗口解码器（v4 Decoder 层）。
 *
 * 严格遵循 JDK [java.nio.charset.CharsetDecoder] 契约：
 * 1. 循环中 endOfInput=false
 * 2. 输入结束时 endOfInput=true 调用一次
 * 3. 最后调用 flush() 清空 decoder 内部状态
 *
 * 错误恢复：默认 REPLACE 模式，遇到非法字节用 '?' 占位，不抛异常。
 */
class StreamDecoder {

    fun decode(window: ByteWindow, charset: Charset): DecodedSegment {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)

        val bb = ByteBuffer.wrap(window.bytes)
        val sb = StringBuilder(window.bytes.size) // 上界估计
        // 存储的是相对 window.byteStart 的偏移，4 字节够用（单段 ≤ 128KB）
        val mapList = IntArray(window.bytes.size + 1) // 上界：最坏每字节 1 char
        var mapLen = 0
        val out = CharBuffer.allocate(DEFAULT_OUT_CAPACITY)

        // 阶段 1：循环 decode，endOfInput=false
        while (bb.hasRemaining()) {
            val relStart = bb.position()
            out.clear()
            val cr = decoder.decode(bb, out, false)
            out.flip()
            while (out.hasRemaining()) {
                ensureCapacity(mapList, mapLen)
                mapList[mapLen++] = relStart
                sb.append(out.get())
            }
            if (cr.isError) {
                // REPLACE 模式下应该不会到这里，作为防御
                cr.throwException()
            }
            if (cr.isOverflow) {
                // 输出缓冲满，下一轮继续；正常路径
                continue
            }
            if (cr.isUnderflow && !bb.hasRemaining()) break
        }

        // 阶段 2：endOfInput=true 调用一次（让 decoder 处理残留字节）
        val tailRelStart = bb.position()
        out.clear()
        decoder.decode(bb, out, true)
        out.flip()
        while (out.hasRemaining()) {
            ensureCapacity(mapList, mapLen)
            mapList[mapLen++] = tailRelStart
            sb.append(out.get())
        }

        // 阶段 3：flush 清空 decoder
        out.clear()
        decoder.flush(out)
        out.flip()
        while (out.hasRemaining()) {
            ensureCapacity(mapList, mapLen)
            mapList[mapLen++] = window.bytes.size
            sb.append(out.get())
        }

        // 终点哨兵：text[text.length] → 窗口字节终点（相对）
        ensureCapacity(mapList, mapLen)
        mapList[mapLen++] = window.bytes.size

        val finalMap = mapList.copyOf(mapLen)
        return DecodedSegment(
            text = sb.toString(),
            byteStart = window.byteStart,
            byteEnd = window.byteEnd,
            utf16IndexToByte = finalMap,
        )
    }

    private fun ensureCapacity(arr: IntArray, idx: Int) {
        // mapList 已按上界预分配，正常情况下不会越界
        if (idx >= arr.size) {
            throw IllegalStateException("utf16IndexToByte overflow at $idx (size=${arr.size})")
        }
    }

    companion object {
        /**
         * decode 单步输出 buffer 容量（UTF-16 unit）。
         * 64 char = 性能优先；若需代理对单字符精度，调到 2 即可。
         */
        const val DEFAULT_OUT_CAPACITY = 64
    }
}

/**
 * utf16IndexToByte 与 BLOB 互转工具。
 *
 * 格式：定长 4 字节大端 Int 序列。
 * size = (utf16IndexToByte.size) * 4 字节。
 */
object Utf16ToByteCodec {

    fun encode(map: IntArray): ByteArray {
        val out = ByteArray(map.size * 4)
        var p = 0
        for (v in map) {
            out[p++] = (v ushr 24).toByte()
            out[p++] = (v ushr 16).toByte()
            out[p++] = (v ushr 8).toByte()
            out[p++] = v.toByte()
        }
        return out
    }

    fun decode(blob: ByteArray): IntArray {
        require(blob.size % 4 == 0) { "Blob size ${blob.size} not multiple of 4" }
        val out = IntArray(blob.size / 4)
        var p = 0
        for (i in out.indices) {
            val b0 = blob[p++].toInt() and 0xFF
            val b1 = blob[p++].toInt() and 0xFF
            val b2 = blob[p++].toInt() and 0xFF
            val b3 = blob[p++].toInt() and 0xFF
            out[i] = (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
        }
        return out
    }
}
