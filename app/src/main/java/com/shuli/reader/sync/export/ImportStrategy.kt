// Part of T-31 ZIP 导入
package com.shuli.reader.sync.export

/**
 * ZIP 导入合并策略。
 */
enum class ImportStrategy {
    /** 覆盖：先清空本地数据，再导入 ZIP 中的数据 */
    OVERWRITE,

    /** 智能合并：按 updatedAt 时间戳，保留较新的条目 */
    SMART_MERGE,

    /** 仅导入新条目：跳过已存在的条目（按 ID 判断） */
    IMPORT_ONLY_NEW,
}
