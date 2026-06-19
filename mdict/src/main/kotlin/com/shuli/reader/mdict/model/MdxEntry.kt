package com.shuli.reader.mdict.model

/**
 * 一条词条的定位信息（查词结果）。对应 docs/38 §5.4。
 *
 * keyword 命中后产生，携带在「所有 record block 解压后拼接流」中的字节区间，
 * 供后续 [com.shuli.reader.mdict.MdictParser.readDefinition] 取出释义。
 *
 * @property keyword      词头（原始大小写）
 * @property recordStart  释义在解压 record 流中的起始字节偏移
 * @property recordEnd    释义结束偏移（下一条词条的 recordStart；末条为流总长）
 */
data class MdxEntry(
    val keyword: String,
    val recordStart: Long,
    val recordEnd: Long,
)
