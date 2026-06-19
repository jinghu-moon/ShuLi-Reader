package com.shuli.reader.mdict.codec

import com.shuli.reader.mdict.CorruptDictException
import java.util.zip.Inflater

/**
 * 压缩块解压器。对应 docs/38 §5、§7。
 *
 * 每个压缩块的布局：`[comp_type:4][adler32:4][compressed_data...]`。
 * comp_type（小端，实际只看首字节）：
 *   0 = 无压缩，1 = LZO，2 = zlib。
 *
 * Step 1 实现 raw + zlib；LZO 留待 Step 2（MiniLzo）。
 */
object Decompressor {

    private const val HEADER_LEN = 8 // comp_type(4) + adler32(4)

    /**
     * 解压一个完整的压缩块（含 8 字节头）。
     *
     * @param block        原始块字节（从 comp_type 开始）
     * @param decompSize   已知的解压后大小（用于预分配，来自索引）。<0 表示未知。
     * @return 解压后的数据
     */
    fun decompress(block: ByteArray, decompSize: Int = -1): ByteArray {
        if (block.size < HEADER_LEN) {
            throw CorruptDictException("compressed block too short: ${block.size} bytes")
        }
        return when (val type = block[0].toInt() and 0xFF) {
            0 -> block.copyOfRange(HEADER_LEN, block.size)
            1 -> {
                if (decompSize < 0) {
                    throw CorruptDictException("LZO requires known decompSize")
                }
                MiniLzo.decompress(block, HEADER_LEN, block.size - HEADER_LEN, decompSize)
            }
            2 -> inflate(block, decompSize)
            else -> throw CorruptDictException("unknown comp_type=$type")
        }
    }

    /** zlib 解压：跳过 8 字节头喂给 Inflater。decompSize 已知时零扩容。 */
    private fun inflate(block: ByteArray, decompSize: Int): ByteArray {
        val inflater = Inflater()
        inflater.setInput(block, HEADER_LEN, block.size - HEADER_LEN)
        try {
            if (decompSize >= 0) {
                val out = ByteArray(decompSize)
                var off = 0
                while (off < decompSize && !inflater.finished()) {
                    val n = inflater.inflate(out, off, decompSize - off)
                    if (n == 0 && inflater.needsInput()) {
                        throw CorruptDictException("zlib stream truncated at $off/$decompSize")
                    }
                    off += n
                }
                return out
            }
            // 大小未知：动态缓冲
            val buf = ByteArray(8192)
            val sink = java.io.ByteArrayOutputStream()
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && inflater.needsInput()) break
                sink.write(buf, 0, n)
            }
            return sink.toByteArray()
        } catch (e: java.util.zip.DataFormatException) {
            throw CorruptDictException("zlib decompression failed", e)
        } finally {
            inflater.end()
        }
    }
}
