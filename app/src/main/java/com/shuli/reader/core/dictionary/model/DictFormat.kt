package com.shuli.reader.core.dictionary.model

/**
 * 词典格式枚举
 */
enum class DictFormat(val extension: String, val displayName: String) {
    /** Stardict 格式（.ifo + .idx + .dict/.dict.dz） */
    STAR_DICT("ifo", "Stardict"),
    /** MDX 格式（Mdict 词典） */
    MDX("mdx", "MDX"),
    ;

    companion object {
        fun fromExtension(ext: String): DictFormat? = entries.find {
            it.extension.equals(ext, ignoreCase = true)
        }

        fun fromFileName(fileName: String): DictFormat? {
            val ext = fileName.substringAfterLast('.', "")
            return fromExtension(ext)
        }
    }
}
