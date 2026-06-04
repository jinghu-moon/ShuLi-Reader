package com.shuli.reader.sync.conflict

import kotlinx.serialization.Serializable

/**
 * 用户偏好设置（T-20）
 *
 * 用于配置同步的 key-level merge。
 */
@Serializable
data class UserPreferences(
    val fontSize: Float = 16f,
    val themeMode: String = "system",
    val lineSpacing: Float = 1.5f,
)
