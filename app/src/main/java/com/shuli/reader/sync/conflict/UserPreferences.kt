package com.shuli.reader.sync.conflict

/**
 * 用户偏好设置（T-20）
 *
 * 用于配置同步的 key-level merge。
 */
data class UserPreferences(
    val fontSize: Float = 16f,
    val themeMode: String = "system",
    val lineSpacing: Float = 1.5f,
)
