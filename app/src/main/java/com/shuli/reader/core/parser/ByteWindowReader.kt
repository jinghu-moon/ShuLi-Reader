package com.shuli.reader.core.parser

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/**
 * 一个未解码的字节窗口。仅 IO 层使用，不参与文本布局。
 */
data class ByteWindow(
    /** 窗口对应文件的字节起点（已对齐到段首） */
    val byteStart: Long,
    /** 窗口对应文件的字节终点（exclusive，已对齐到段尾） */
    val byteEnd: Long,
    /** 窗口字节内容（长度 = byteEnd - byteStart） */
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * 字节窗口读取器（v4 IO 层）。
 *
 * 设计要点：
 * - 不使用 mmap（Android 缺少干净的 unmap API，1GB+ 文件 OOM 风险）
 * - 使用 RandomAccessFile.seek + readFully，依赖 OS page cache
 * - 段落对齐用单字节 0x0A 检测，UTF-8/GBK 中 \n 不会出现在多字节字符内
 * - **方向严格区分**：
 *     - [loadWindow]：forward — 向后找第一个 \n 之后作为字符起点
 *     - [alignToParagraphStart]：backward — 向前找最近 \n 的下一字节
 */
class ByteWindowReader(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * 从指定字节读取一个 IO 窗口（默认 128KB）。
     *
     * 边界对齐：
     * - 起点：若 rawStart > 0，前 [HEAD_SCAN_BYTES] 字节内 forward 找第一个 \n，
     *   将其下一字节作为字符起点；找不到则保持 rawStart（罕见，超长行）。
     * - 终点：末尾 [TAIL_SCAN_BYTES] 字节内 backward 找最后一个 \n，
     *   作为字符终点；找不到则保持 rawEnd（罕见，超长行）。
     */
    suspend fun loadWindow(
        file: File,
        byteOffset: Long,
        windowSize: Int = IO_WINDOW_SIZE,
    ): ByteWindow = withContext(ioDispatcher) {
        val fileLength = file.length()
        val rawStart = (byteOffset - HEAD_SCAN_BYTES).coerceAtLeast(0L)
        val rawEnd = (rawStart + windowSize).coerceAtMost(fileLength)
        val rawLen = (rawEnd - rawStart).toInt()
        if (rawLen <= 0) {
            return@withContext ByteWindow(rawStart, rawStart, ByteArray(0))
        }

        val buf = ByteArray(rawLen)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(rawStart)
            raf.readFully(buf)
        }

        // 起点对齐：forward 找第一个 \n
        var startInBuf = 0
        if (rawStart > 0L) {
            val scanEnd = HEAD_SCAN_BYTES.coerceAtMost(buf.size)
            var i = 0
            while (i < scanEnd) {
                if (buf[i] == NL) {
                    startInBuf = i + 1
                    break
                }
                i++
            }
        }

        // 终点对齐：backward 找最后一个 \n（仅当窗口未到达文件尾时）
        var endInBuf = buf.size
        if (rawEnd < fileLength) {
            val scanFrom = (buf.size - TAIL_SCAN_BYTES).coerceAtLeast(startInBuf)
            var i = buf.size - 1
            while (i >= scanFrom) {
                if (buf[i] == NL) {
                    endInBuf = i + 1
                    break
                }
                i--
            }
        }

        if (endInBuf <= startInBuf) {
            // 超长行：找不到换行边界，保留原始窗口（让分页器自己处理）
            return@withContext ByteWindow(rawStart, rawEnd, buf)
        }

        val effective = buf.copyOfRange(startInBuf, endInBuf)
        ByteWindow(
            byteStart = rawStart + startInBuf,
            byteEnd = rawStart + endInBuf,
            bytes = effective,
        )
    }

    /**
     * 加载已知字节范围（章节切换走这条），不做边界对齐——调用方保证 byteStart/byteEnd 已是行边界。
     */
    suspend fun loadRange(
        file: File,
        byteStart: Long,
        byteEnd: Long,
    ): ByteWindow = withContext(ioDispatcher) {
        require(byteEnd >= byteStart) { "byteEnd ($byteEnd) < byteStart ($byteStart)" }
        val len = (byteEnd - byteStart).toInt()
        if (len <= 0) return@withContext ByteWindow(byteStart, byteStart, ByteArray(0))
        val buf = ByteArray(len)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(byteStart)
            raf.readFully(buf)
        }
        ByteWindow(byteStart, byteEnd, buf)
    }


    /**
     * 把任意 byteOffset 对齐到段首（**反向扫描**）：
     * 从 byteOffset 向前找最近的 \n，返回 \n 的下一字节位置；
     * 若找不到（位于文件开头附近），返回 0。
     *
     * 用途：进度条/书签拖动后的输入归一化。
     */
    suspend fun alignToParagraphStart(
        file: File,
        byteOffset: Long,
    ): Long = withContext(ioDispatcher) {
        if (byteOffset <= 0L) return@withContext 0L
        val fileLength = file.length()
        val end = byteOffset.coerceAtMost(fileLength)
        // 从 [end - ALIGN_SCAN_BYTES, end) 区间向前扫描
        val scanLen = ALIGN_SCAN_BYTES.toLong().coerceAtMost(end).toInt()
        if (scanLen <= 0) return@withContext 0L
        val from = end - scanLen
        val buf = ByteArray(scanLen)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(from)
            raf.readFully(buf)
        }
        var i = buf.size - 1
        while (i >= 0) {
            if (buf[i] == NL) return@withContext from + i + 1
            i--
        }
        // 未找到换行：若已扫到文件起点，返回 0；否则保守返回 from
        if (from == 0L) 0L else from
    }


    companion object {
        const val IO_WINDOW_SIZE = 128 * 1024
        /** 起点对齐扫描窗口：在窗口前 8KB 内找首个 \n */
        const val HEAD_SCAN_BYTES = 8 * 1024
        /** 终点对齐扫描窗口：在窗口末 4KB 内找最后一个 \n */
        const val TAIL_SCAN_BYTES = 4 * 1024
        /** alignToParagraphStart 反向扫描窗口 */
        const val ALIGN_SCAN_BYTES = 8 * 1024

        const val NL: Byte = 0x0A
    }
}
