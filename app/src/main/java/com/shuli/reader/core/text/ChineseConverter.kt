package com.shuli.reader.core.text

import com.github.houbb.opencc4j.util.ZhConverterUtil

/**
 * 简繁转换工具类
 *
 * 基于 OpenCC4j（工业级简繁转换），支持词汇级转换（如 "网络" → "網路"）
 */
object ChineseConverter {

    /**
     * 转换为简体
     */
    fun toSimplified(text: String): String {
        return ZhConverterUtil.toSimple(text)
    }

    /**
     * 转换为繁体
     */
    fun toTraditional(text: String): String {
        return ZhConverterUtil.toTraditional(text)
    }
}
