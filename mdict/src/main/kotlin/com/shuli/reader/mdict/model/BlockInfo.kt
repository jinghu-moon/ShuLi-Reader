package com.shuli.reader.mdict.model

/**
 * 单个 key block 的索引信息。对应 docs/38 §5.3。
 *
 * 解析 key_index 时构建，常驻内存。first/last word 用于块级二分定位，
 * 累加偏移用于 O(1) 定位 block 在文件/解压流中的位置。
 *
 * @property entryCount        该 block 内的词条数
 * @property firstWord         该 block 第一个词（字典序）
 * @property lastWord          该 block 最后一个词（字典序）
 * @property compSize          该 block 压缩字节数
 * @property decompSize        该 block 解压字节数
 * @property compAccumulator   该 block 之前所有 block 的压缩字节数之和（前缀和）
 * @property decompAccumulator 该 block 之前所有 block 的解压字节数之和（前缀和）
 */
data class KeyBlockInfo(
    val entryCount: Long,
    val firstWord: String,
    val lastWord: String,
    val compSize: Long,
    val decompSize: Long,
    val compAccumulator: Long,
    val decompAccumulator: Long,
)

/**
 * 单个 record block 的索引信息。对应 docs/38 §5.5。
 *
 * @property compSize          压缩字节数
 * @property decompSize        解压字节数
 * @property compAccumulator   前缀和：之前所有 record block 的压缩字节数之和
 * @property decompAccumulator 前缀和：之前所有 record block 的解压字节数之和
 */
data class RecordBlockInfo(
    val compSize: Long,
    val decompSize: Long,
    val compAccumulator: Long,
    val decompAccumulator: Long,
)
