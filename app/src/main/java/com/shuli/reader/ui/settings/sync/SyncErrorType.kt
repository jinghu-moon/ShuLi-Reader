package com.shuli.reader.ui.settings.sync

// Part of T-40 错误状态 UI
enum class SyncErrorType {
    NONE,
    AUTH_FAILED,
    NETWORK_ERROR,
    RATE_LIMITED,
    CRYPTO_LOCKED,
    UNKNOWN,
}
