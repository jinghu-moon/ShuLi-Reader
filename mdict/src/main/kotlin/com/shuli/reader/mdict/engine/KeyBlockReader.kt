package com.shuli.reader.mdict.engine

import com.shuli.reader.mdict.cache.BlockCache
import com.shuli.reader.mdict.codec.Decompressor
import com.shuli.reader.mdict.io.BlockReader
import com.shuli.reader.mdict.io.ByteCursor
import com.shuli.reader.mdict.model.KeyBlockInfo
import com.shuli.reader.mdict.model.MdxEntry
import com.shuli.reader.mdict.model.MdxHeader

/**
 * Key block 读取与块内拆词、两级查词。对应 docs/38 §5.4。
 *
 * 第一级：用 keyBlockInfos 的 first/last word 二分定位目标 block；
 * 第二级：解压该 block，块内拆词后查找。
 * 解压后的拆词结果走 LRU 缓存（docs/38 §8.2），避免连续查邻近词重复解压。
 */
class KeyBlockReader(
    private val reader: BlockReader,
    private val header: MdxHeader,
    private val keyIndex: KeyIndexParser.KeyIndex,
    cacheCapacity: Int = 4,
) {

    private val cache = BlockCache<List<RawKey>>(cacheCapacity)

    /** 解压后的单个 key block 内拆出的词条（带在 record 流中的偏移）。 */
    data class RawKey(val recordStart: Long, val keyword: String)

    /**
     * 解压第 [blockIdx] 个 key block，拆出全部词条（命中缓存则直接返回）。
     * recordEnd 由调用方按相邻补齐（这里只给 recordStart）。
     */
    fun readBlock(blockIdx: Int): List<RawKey> = cache.getOrPut(blockIdx) {
        val info = keyIndex.keyBlockInfos[blockIdx]
        val comp = reader.read(
            keyIndex.keyBlocksStart + info.compAccumulator,
            info.compSize.toInt(),
        )
        val data = Decompressor.decompress(comp, info.decompSize.toInt())
        splitKeys(data)
    }

    /**
     * 块内拆词：每条 = [recordOffset: numberWidth][keyword: 以 unitWidth 个 0x00 结尾]。
     */
    private fun splitKeys(block: ByteArray): List<RawKey> {
        val out = ArrayList<RawKey>()
        val nw = header.numberWidth
        val width = header.unitWidth
        val c = ByteCursor(block)
        while (c.hasRemaining()) {
            val recordOffset = c.number(nw)
            val keyStart = c.pos
            // 找连续 width 个 0x00，且需对齐到编码单元边界
            var end = keyStart
            while (true) {
                if (isNullAt(block, end, width) && (end - keyStart) % width == 0) break
                end += 1
                if (end + width > block.size + 1) break
            }
            val keyword = String(block, keyStart, end - keyStart, header.charset)
            out.add(RawKey(recordOffset, keyword))
            c.skip((end - keyStart) + width) // 跳过 keyword + null 终止符
        }
        return out
    }

    private fun isNullAt(b: ByteArray, off: Int, width: Int): Boolean {
        if (off + width > b.size) return false
        for (i in 0 until width) if (b[off + i].toInt() != 0) return false
        return true
    }

    /**
     * 精确查词。先块级二分定位，命中后块内线性扫描；
     * 边界模糊时回退到相邻 ±1 block（docs/38 §5.4）。
     * 未命中返回 null。
     */
    fun lookup(word: String): MdxEntry? {
        val infos = keyIndex.keyBlockInfos
        if (infos.isEmpty()) return null
        val target = normalize(word)

        val blockIdx = findBlock(target)
        // 尝试候选块及其相邻块，去重
        for (idx in setOf(blockIdx, blockIdx - 1, blockIdx + 1)) {
            if (idx < 0 || idx >= infos.size) continue
            val hit = lookupInBlock(idx, word, target)
            if (hit != null) return hit
        }
        return null
    }

    /** 块级二分：找首个 lastWord >= target 的块。 */
    private fun findBlock(target: String): Int {
        val infos = keyIndex.keyBlockInfos
        var lo = 0
        var hi = infos.size - 1
        var ans = infos.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (compare(normalize(infos[mid].lastWord), target) >= 0) {
                ans = mid
                hi = mid - 1
            } else {
                lo = mid + 1
            }
        }
        return ans
    }

    /**
     * 前缀查询：返回词头以 [prefix] 开头的最多 [limit] 个词条（按字典序）。
     * 供上层「联想 / 你是不是要找」（docs/38 §9.1）。
     *
     * 定位首个可能含前缀的块（lastWord >= prefix），向后逐块扫描，
     * 收集 normalize 后以 prefix 开头的词条，直到超出前缀范围或达上限。
     */
    fun prefixRange(prefix: String, limit: Int): List<MdxEntry> {
        if (limit <= 0) return emptyList()
        val infos = keyIndex.keyBlockInfos
        if (infos.isEmpty()) return emptyList()
        val target = normalize(prefix)
        if (target.isEmpty()) return emptyList()

        val out = ArrayList<MdxEntry>(limit)
        var blockIdx = (findBlock(target) - 1).coerceAtLeast(0) // 前移一块防边界遗漏
        while (blockIdx < infos.size && out.size < limit) {
            // 整块都在 prefix 之后（firstWord 已超过且不以 prefix 开头）则停止
            val first = normalize(infos[blockIdx].firstWord)
            if (first > target && !first.startsWith(target)) break

            val keys = readBlock(blockIdx)
            for (i in keys.indices) {
                val norm = normalize(keys[i].keyword)
                if (norm.startsWith(target)) {
                    out.add(MdxEntry(keys[i].keyword, keys[i].recordStart, recordEndOf(blockIdx, keys, i)))
                    if (out.size >= limit) break
                }
            }
            blockIdx++
        }
        return out
    }

    private fun lookupInBlock(blockIdx: Int, word: String, target: String): MdxEntry? {
        val keys = readBlock(blockIdx)
        for (i in keys.indices) {
            if (normalize(keys[i].keyword) == target) {
                val end = recordEndOf(blockIdx, keys, i)
                return MdxEntry(keys[i].keyword, keys[i].recordStart, end)
            }
        }
        return null
    }

    /**
     * 某词条的 recordEnd = 同块下一条的 recordStart；
     * 若是块内最后一条，则取下一个非空块第一条的 recordStart；
     * 若全局最后一条，调用方需用 record 流总长收尾（这里返回 Long.MAX_VALUE 作哨兵）。
     */
    private fun recordEndOf(blockIdx: Int, keys: List<RawKey>, i: Int): Long {
        if (i + 1 < keys.size) return keys[i + 1].recordStart
        // 跨块找下一条
        var nb = blockIdx + 1
        while (nb < keyIndex.keyBlockInfos.size) {
            val next = readBlock(nb)
            if (next.isNotEmpty()) return next.first().recordStart
            nb++
        }
        return Long.MAX_VALUE // 全局最后一条，由 RecordReader 用流总长替换
    }

    // 归一化用于比较/二分。KeyCaseSensitive=No（绝大多数中文词典）时小写化，
    // 与 MDX 生成排序一致。大小写敏感支持留待后续。
    private fun normalize(s: String): String = s.lowercase()

    private fun compare(a: String, b: String): Int = a.compareTo(b)
}
