// Part of 备份导入策略
package com.shuli.reader.sync.backup

/**
 * 备份导入合并策略。
 */
enum class ImportStrategy {
    /** 增量合并：按 updatedAt 时间戳，保留较新的条目（主策略） */
    MERGE,

    /** 覆盖：先清空本地数据，再导入 ZIP 中的数据 */
    OVERWRITE,

    /** 仅导入本地不存在的数据，已存在的条目不覆盖 */
    IMPORT_ONLY_NEW,
}
