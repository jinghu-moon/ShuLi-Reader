package com.shuli.reader.mdict

import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 不支持特性的拒绝路径。对应 docs/38 §1.2、§3。
 * 注册码加密（Encrypted&1，Salsa20）无用户密钥不可解，open 时应抛 UnsupportedDictException。
 */
class UnsupportedDictTest {

    @Test
    fun `record-level salsa20 encryption is rejected on open`() {
        assertThrows(UnsupportedDictException::class.java) {
            MdictParser.open(Fixtures.extract("v2_utf8_salsa.mdx"))
        }
    }
}
