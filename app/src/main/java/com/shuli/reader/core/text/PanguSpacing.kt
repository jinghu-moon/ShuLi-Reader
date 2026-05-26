package com.shuli.reader.core.text

/**
 * 盘古之白：在中文与英文/数字之间自动插入空格
 */
object PanguSpacing {

    private val CJK_TO_LATIN = Regex("([\\u4e00-\\u9fa5\\u3400-\\u4dbf])([a-zA-Z0-9@#%\\-\\[\\{<\\(])")
    private val LATIN_TO_CJK = Regex("([a-zA-Z0-9!~%&\\-\\]\\}>\\)])([\\u4e00-\\u9fa5\\u3400-\\u4dbf])")

    fun insert(text: String): String {
        return text
            .replace(CJK_TO_LATIN, "$1 $2")
            .replace(LATIN_TO_CJK, "$1 $2")
    }
}
