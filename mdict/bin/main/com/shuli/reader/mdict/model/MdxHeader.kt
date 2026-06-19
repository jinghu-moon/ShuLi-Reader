package com.shuli.reader.mdict.model

import java.nio.charset.Charset

/**
 * MDX/MDD 文件头解析出的元信息。对应 docs/38 §5.1。
 *
 * @property isMdd            true = MDD（资源库，`<Library_Data>`），false = MDX（词典，`<Dictionary>`）
 * @property version          格式版本（1.2 或 2.0）
 * @property numberWidth      计数字段字节宽度：version >= 2.0 为 8，否则 4
 * @property encodingName     头中的原始 Encoding 值（如 "UTF-8"、"GBK"）
 * @property charset          归一化后的实际解码字符集（GBK→GB18030，UTF-16→UTF-16LE，MDD 固定 UTF-16LE）
 * @property enco.unitWidth   一个编码单元的字节数：UTF-16 为 2，其余为 1（用于 key block 拆词时识别 null 终止符）
 * @property encrypted        Encrypted 标志位：bit0=record 加密，bit1=key-index 加密
 * @property format           Format 属性（"Html"/"Text"），影响上层渲染，不影响解析
 * @property styleSheet       StyleSheet 替换表：编号 → (前缀, 后缀)，供上层释义 `` `n` `` 标记替换
 * @property keySectionStart  Keyword Section 在文件中的起始字节偏移
 */
data class MdxHeader(
    val isMdd: Boolean,
    val version: Float,
    val numberWidth: Int,
    val encodingName: String,
    val charset: Charset,
    val unitWidth: Int,
    val encrypted: Int,
    val format: String,
    val styleSheet: Map<Int, Pair<String, String>>,
    val keySectionStart: Long,
) {
    /** record 区是否 Salsa20 加密（本库不支持）。 */
    val isRecordEncrypted: Boolean get() = (encrypted and 1) != 0

    /** key-index 是否加密（本库支持，仅 v2.0）。 */
    val isKeyIndexEncrypted: Boolean get() = (encrypted and 2) != 0

    val isV2: Boolean get() = version >= 2.0f
}
