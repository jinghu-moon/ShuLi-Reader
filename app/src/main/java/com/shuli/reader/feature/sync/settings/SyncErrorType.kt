package com.shuli.reader.feature.sync.settings

// Part of T-40 错误状态 UI
enum class SyncErrorType {
    NONE,
    AUTH_FAILED,
    NETWORK_ERROR,
    RATE_LIMITED,
    CRYPTO_LOCKED,
    UNKNOWN,
}
