package com.shuli.reader.mdict.codec

/**
 * MDX key-index 加密的解密。对应 docs/38 §6.1。
 *
 * 仅处理 `Encrypted & 2`（key-index 加密）。
 * 算法：密钥 = ripemd128( 密文[4,8) ++ {0x95,0x36,0x00,0x00} )，
 *      对 密文[8:] 做 swap-nibble + XOR 链解密；密文[0,8) 原样保留。
 */
object MdxCrypto {

    fun decryptKeyIndex(compBlock: ByteArray): ByteArray {
        require(compBlock.size >= 8) { "encrypted key-index block too short" }
        val seed = ByteArray(8)
        System.arraycopy(compBlock, 4, seed, 0, 4)
        seed[4] = 0x95.toByte()
        seed[5] = 0x36.toByte()
        seed[6] = 0x00
        seed[7] = 0x00
        val key = Ripemd128.hash(seed)

        val out = compBlock.copyOf()
        fastDecrypt(out, 8, key)
        return out
    }

    /**
     * swap-nibble XOR 链。previous 用「解密前的原始字节」（docs/38 §6.1 警示）。
     */
    private fun fastDecrypt(buf: ByteArray, from: Int, key: ByteArray) {
        var previous = 0x36
        for (i in from until buf.size) {
            val orig = buf[i].toInt() and 0xFF
            val swapped = ((orig ushr 4) or (orig shl 4)) and 0xFF
            val idx = i - from
            buf[i] = (swapped xor previous xor (idx and 0xFF) xor (key[idx % key.size].toInt() and 0xFF)).toByte()
            previous = orig
        }
    }
}
