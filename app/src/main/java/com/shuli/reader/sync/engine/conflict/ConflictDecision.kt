package com.shuli.reader.sync.engine.conflict

/**
 * 冲突决策枚举（T-18）
 */
enum class ConflictDecision {
    /** 自动合并，无需用户干预 */
    AUTO_MERGE,
    /** 需要用户输入来解决冲突 */
    REQUIRE_USER_INPUT,
}
