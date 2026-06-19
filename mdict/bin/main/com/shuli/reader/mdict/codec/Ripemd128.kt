package com.shuli.reader.mdict.codec

/**
 * RIPEMD-128 哈希。对应 docs/38 §6.2。
 *
 * 纯 Kotlin 移植，仅供 key-index 解密派生密钥用。小端读 32bit 字 + 小端 64bit 长度填充。
 * 用 Int 配合无符号运算（`and`/`ushr`/`shl`，比较用 toLong and 0xFFFFFFFF）。
 */
object Ripemd128 {

    private val R1 = intArrayOf(
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
        7, 4, 13, 1, 10, 6, 15, 3, 12, 0, 9, 5, 2, 14, 11, 8,
        3, 10, 14, 4, 9, 15, 8, 1, 2, 7, 0, 6, 13, 11, 5, 12,
        1, 9, 11, 10, 0, 8, 12, 4, 13, 3, 7, 15, 14, 5, 6, 2,
    )
    private val R2 = intArrayOf(
        5, 14, 7, 0, 9, 2, 11, 4, 13, 6, 15, 8, 1, 10, 3, 12,
        6, 11, 3, 7, 0, 13, 5, 10, 14, 15, 8, 12, 4, 9, 1, 2,
        15, 5, 1, 3, 7, 14, 6, 9, 11, 8, 12, 2, 10, 0, 4, 13,
        8, 6, 4, 1, 3, 11, 15, 0, 5, 12, 2, 13, 9, 7, 10, 14,
    )
    private val S1 = intArrayOf(
        11, 14, 15, 12, 5, 8, 7, 9, 11, 13, 14, 15, 6, 7, 9, 8,
        7, 6, 8, 13, 11, 9, 7, 15, 7, 12, 15, 9, 11, 7, 13, 12,
        11, 13, 6, 7, 14, 9, 13, 15, 14, 8, 13, 6, 5, 12, 7, 5,
        11, 12, 14, 15, 14, 15, 9, 8, 9, 14, 5, 6, 8, 6, 5, 12,
    )
    private val S2 = intArrayOf(
        8, 9, 9, 11, 13, 15, 15, 5, 7, 7, 8, 11, 14, 14, 12, 6,
        9, 13, 15, 7, 12, 8, 9, 11, 7, 7, 12, 7, 6, 15, 13, 11,
        9, 7, 15, 11, 8, 6, 6, 14, 12, 13, 5, 14, 13, 13, 7, 5,
        15, 5, 8, 11, 14, 14, 6, 14, 6, 9, 12, 9, 12, 5, 15, 8,
    )

    fun hash(message: ByteArray): ByteArray {
        var h0 = 0x67452301
        var h1 = 0xefcdab89.toInt()
        var h2 = 0x98badcfe.toInt()
        var h3 = 0x10325476

        val padded = pad(message)
        val x = IntArray(16)
        var off = 0
        while (off < padded.size) {
            for (i in 0 until 16) {
                x[i] = (padded[off + i * 4].toInt() and 0xFF) or
                    ((padded[off + i * 4 + 1].toInt() and 0xFF) shl 8) or
                    ((padded[off + i * 4 + 2].toInt() and 0xFF) shl 16) or
                    ((padded[off + i * 4 + 3].toInt() and 0xFF) shl 24)
            }
            var al = h0; var bl = h1; var cl = h2; var dl = h3
            var ar = h0; var br = h1; var cr = h2; var dr = h3
            for (j in 0 until 64) {
                val round = j / 16
                var t = al + f(round, bl, cl, dl) + x[R1[j]] + kl(round)
                t = rol(t, S1[j])
                al = dl; dl = cl; cl = bl; bl = t
                t = ar + f(3 - round, br, cr, dr) + x[R2[j]] + kr(round)
                t = rol(t, S2[j])
                ar = dr; dr = cr; cr = br; br = t
            }
            val t = h1 + cl + dr
            h1 = h2 + dl + ar
            h2 = h3 + al + br
            h3 = h0 + bl + cr
            h0 = t
            off += 64
        }
        return intToLE(h0) + intToLE(h1) + intToLE(h2) + intToLE(h3)
    }

    private fun f(round: Int, x: Int, y: Int, z: Int): Int = when (round) {
        0 -> x xor y xor z
        1 -> (x and y) or (x.inv() and z)
        2 -> (x or y.inv()) xor z
        else -> (x and z) or (y and z.inv())
    }

    private fun kl(round: Int): Int = when (round) {
        0 -> 0x00000000
        1 -> 0x5a827999
        2 -> 0x6ed9eba1
        else -> 0x8f1bbcdc.toInt()
    }

    private fun kr(round: Int): Int = when (round) {
        0 -> 0x50a28be6
        1 -> 0x5c4dd124
        2 -> 0x6d703ef3
        else -> 0x00000000
    }

    private fun rol(x: Int, n: Int): Int = (x shl n) or (x ushr (32 - n))

    private fun intToLE(v: Int): ByteArray = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v ushr 8) and 0xFF).toByte(),
        ((v ushr 16) and 0xFF).toByte(),
        ((v ushr 24) and 0xFF).toByte(),
    )

    private fun pad(message: ByteArray): ByteArray {
        val msgLenBits = message.size.toLong() * 8
        // 追加 0x80，补 0 到 56 mod 64，再补 8 字节小端长度
        var padLen = 56 - (message.size + 1) % 64
        if (padLen < 0) padLen += 64
        val out = ByteArray(message.size + 1 + padLen + 8)
        System.arraycopy(message, 0, out, 0, message.size)
        out[message.size] = 0x80.toByte()
        var lenOff = out.size - 8
        var len = msgLenBits
        for (i in 0 until 8) {
            out[lenOff++] = (len and 0xFF).toByte()
            len = len ushr 8
        }
        return out
    }
}
