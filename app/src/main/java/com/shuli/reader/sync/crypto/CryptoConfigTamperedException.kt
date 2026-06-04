package com.shuli.reader.sync.crypto

/**
 * crypto.json 被篡改时抛出的异常（T-28）
 *
 * 当远端 crypto.json 的本地缓存 hash 与实际内容不一致时触发，
 * 可能表示降级攻击或数据损坏。
 */
class CryptoConfigTamperedException(
    message: String = "crypto.json has been tampered with — possible KDF downgrade attack",
) : SecurityException(message)
