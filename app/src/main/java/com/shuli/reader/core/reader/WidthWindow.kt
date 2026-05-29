package com.shuli.reader.core.reader

/**
 * 分块字符宽度缓存，按需测量并淘汰。
 *
 * 替代 Paginator 中整章一次性 [FloatArray] 的方案，将 O(n) 前置测量
 * 拆为按 BLOCK_SIZE 分块的惰性测量。Paginator 通过 [get] 透明访问
 * 任意偏移的字符宽度，首次访问时自动触发测量并缓存。
 *
 * ## 内存模型
 * - 每块 BLOCK_SIZE 个 float（默认 4096 * 4 = 16 KB）
 * - 最多保留 [MAX_CACHED_BLOCKS] 块（默认 4 块 ≈ 64 KB）
 * - 淘汰策略：最久未访问的块优先移除
 *
 * ## 线程安全
 * 本类非线程安全。调用方（Paginator）应在单个协程内使用。
 */
class WidthWindow(
    private val content: String,
    private val textSize: Float,
    private val textMeasurer: TextMeasurer,
) {
    private val blocks = HashMap<Int, FloatArray>()

    /** 访问顺序追踪，用于 LRU 淘汰 */
    private val accessOrder = ArrayDeque<Int>()

    /** 总字符数 */
    val length: Int get() = content.length

    /**
     * 获取指定偏移处的字符宽度。
     * 若对应块尚未测量，立即测量并缓存；若缓存已满，淘汰最久未访问的块。
     */
    operator fun get(charIndex: Int): Float {
        val blockIndex = charIndex / BLOCK_SIZE
        val block = blocks[blockIndex] ?: measureBlock(blockIndex)
        return block[charIndex - blockIndex * BLOCK_SIZE]
    }

    /**
     * 预热指定范围的块。在 emit 页之前调用，确保后续 calculateLine
     * 访问不会触发同步测量。
     */
    fun warmUp(startChar: Int, endChar: Int) {
        val startBlock = startChar / BLOCK_SIZE
        val endBlock = (endChar - 1).coerceAtLeast(0) / BLOCK_SIZE
        for (b in startBlock..endBlock) {
            if (b !in blocks) measureBlock(b)
        }
    }

    private fun measureBlock(blockIndex: Int): FloatArray {
        val start = blockIndex * BLOCK_SIZE
        val end = minOf(start + BLOCK_SIZE, content.length)
        val chunk = content.substring(start, end)
        val widths = textMeasurer.measureTextWidths(chunk, textSize)
        blocks[blockIndex] = widths
        accessOrder.addLast(blockIndex)
        evictIfNeeded()
        return widths
    }

    private fun evictIfNeeded() {
        while (blocks.size > MAX_CACHED_BLOCKS) {
            val oldest = accessOrder.removeFirst()
            blocks.remove(oldest)
        }
    }

    companion object {
        /** 每块字符数。4096 字符 ≈ 16 KB FloatArray */
        const val BLOCK_SIZE = 4096

        /** 最大缓存块数 */
        const val MAX_CACHED_BLOCKS = 4
    }
}
