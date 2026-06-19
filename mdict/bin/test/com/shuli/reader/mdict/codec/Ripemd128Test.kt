package com.shuli.reader.mdict.codec

import org.junit.Assert.assertEquals
import org.junit.Test

class Ripemd128Test {

    private fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

    // 已知测试向量（RIPEMD-128 标准）
    @Test
    fun `empty string`() {
        assertEquals("cdf26213a150dc3ecb610f18f6b38b46", hex(Ripemd128.hash(ByteArray(0))))
    }

    @Test
    fun `abc`() {
        assertEquals("c14a12199c66e4ba84636b0f69144c77", hex(Ripemd128.hash("abc".toByteArray())))
    }

    @Test
    fun `message digest`() {
        assertEquals(
            "9e327b3d6e523062afc1132d7df9d1b8",
            hex(Ripemd128.hash("message digest".toByteArray())),
        )
    }

    @Test
    fun `alphabet`() {
        assertEquals(
            "fd2aa607f71dc8f510714922b371834e",
            hex(Ripemd128.hash("abcdefghijklmnopqrstuvwxyz".toByteArray())),
        )
    }
}
