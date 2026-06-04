package com.shuli.reader.ui.settings.crypto

// Part of T-35 加密管理页
data class EncryptionInfo(
    val isEnabled: Boolean = false,
    val algorithm: String = "",
    val kdfIterations: Int = 0,
    val keyVersion: Int = 0,
    val createdAt: Long = 0L,
    val salt: ByteArray = ByteArray(0),
)
