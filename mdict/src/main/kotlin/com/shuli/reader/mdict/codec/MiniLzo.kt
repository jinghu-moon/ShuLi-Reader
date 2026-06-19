package com.shuli.reader.mdict.codec

import com.shuli.reader.mdict.CorruptDictException

/**
 * 纯 Kotlin LZO1X 解压。对应 docs/38 §7。
 *
 * 算法移植自 minilzo 的 lzo1x_decompress（参考 lzo1x.ts / minilzo-decompress.js，
 * 二者均为 GPL-2.0 的 minilzo 移植；此处按算法独立用 Kotlin 重写）。
 *
 * 与 JS 参考的差异（docs/38 §7.2）：用已知 decompSize 预分配精确输出，
 * 无翻倍扩容、无 32 位对齐拷贝技巧，逐字节拷贝（单块 64KB 量级足够快）。
 */
object MiniLzo {

    /**
     * @param outLen 已知解压后字节数（来自 MDX 索引），用于精确预分配。
     * @throws CorruptDictException 数据损坏或越界
     */
    fun decompress(input: ByteArray, inOff: Int, inLen: Int, outLen: Int): ByteArray {
        val w = Worker(input, inOff, inOff + inLen, ByteArray(outLen))
        w.run()
        if (w.op != outLen) {
            throw CorruptDictException("LZO output size mismatch: got ${w.op}, expected $outLen")
        }
        return w.out
    }

    /** 与 minilzo lzo1x_decompress 同构的可变状态机。字段命名沿用 minilzo。 */
    private class Worker(
        private val input: ByteArray,
        startIp: Int,
        private val ipEnd: Int,
        val out: ByteArray,
    ) {
        private var ip = startIp
        var op = 0; private set
        private var t = 0
        private var mPos = 0

        private fun ru8(): Int {
            if (ip >= ipEnd) throw CorruptDictException("LZO input overrun")
            return input[ip++].toInt() and 0xFF
        }
        private fun peek(): Int = input[ip].toInt() and 0xFF
        private fun put(b: Int) {
            if (op >= out.size) throw CorruptDictException("LZO output overrun")
            out[op++] = b.toByte()
        }
        private fun copyBack() {
            if (mPos < 0 || mPos >= op) throw CorruptDictException("LZO lookbehind overrun")
            put(out[mPos++].toInt() and 0xFF)
        }
        /** 0 字节扩展长度：连续 0x00 各 +255，末字节 +余数。 */
        private fun extend(base: Int): Int {
            var len = base
            while (peek() == 0) { len += 255; ip++ }
            return len + ru8()
        }
        /** copy_match：拷 t+2 字节回看数据。 */
        private fun copyMatch() {
            t += 2
            do { copyBack() } while (--t > 0)
        }
        /** match_next：拷 1..3 字节 literal，再读新 token 入 t。 */
        private fun matchNext() {
            put(ru8())
            if (t > 1) { put(ru8()); if (t > 2) put(ru8()) }
            t = ru8()
        }
        /** match_done：t = 上个距离字节低 2 位（=紧随的短 literal 长度）。 */
        private fun matchDone(): Int { t = (input[ip - 2].toInt() and 0xFF) and 3; return t }

        fun run() {
            var skipToFirstLiteral = false
            if (peek() > 17) {
                t = ru8() - 17
                if (t < 4) {
                    matchNext()
                    if (match()) return
                } else {
                    repeat(t) { put(ru8()) }
                    skipToFirstLiteral = true
                }
            }
            while (true) {
                if (!skipToFirstLiteral) {
                    t = ru8()
                    if (t >= 16) {
                        if (match()) return
                        continue
                    }
                    if (t == 0) t = extend(15)
                    t += 3
                    repeat(t) { put(ru8()) }
                } else {
                    skipToFirstLiteral = false
                }
                // 读 match token；<16 为短距离 match
                t = ru8()
                if (t < 16) {
                    mPos = op - (1 + 0x0800) - (t ushr 2) - (ru8() shl 2)
                    put(out[mPos++].toInt() and 0xFF)
                    put(out[mPos++].toInt() and 0xFF)
                    put(out[mPos].toInt() and 0xFF)
                    if (matchDone() == 0) continue else matchNext()
                }
                if (match()) return
            }
        }

        /** 对应 TS 的 match()：返回 true 表示遇到 EOF（解压结束）。 */
        private fun match(): Boolean {
            while (true) {
                when {
                    t >= 64 -> {
                        mPos = op - 1 - ((t ushr 2) and 7) - (ru8() shl 3)
                        t = (t ushr 5) - 1
                        copyMatch()
                    }
                    t >= 32 -> {
                        t = t and 31
                        if (t == 0) t = extend(31)
                        mPos = op - 1
                        mPos -= (input[ip].toInt() and 0xFF) ushr 2
                        mPos -= (input[ip + 1].toInt() and 0xFF) shl 6
                        ip += 2
                        copyMatch()
                    }
                    t >= 16 -> {
                        mPos = op - ((t and 8) shl 11)
                        t = t and 7
                        if (t == 0) t = extend(7)
                        mPos -= (input[ip].toInt() and 0xFF) ushr 2
                        mPos -= (input[ip + 1].toInt() and 0xFF) shl 6
                        ip += 2
                        if (mPos == op) return true // EOF
                        mPos -= 0x4000
                        copyMatch()
                    }
                    else -> {
                        mPos = op - 1 - (t ushr 2) - (ru8() shl 2)
                        put(out[mPos++].toInt() and 0xFF)
                        put(out[mPos].toInt() and 0xFF)
                    }
                }
                if (matchDone() == 0) return false
                matchNext()
            }
        }
    }
}
