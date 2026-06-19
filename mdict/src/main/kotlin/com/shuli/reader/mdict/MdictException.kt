package com.shuli.reader.mdict

/** MDX/MDD 解析相关异常的基类。 */
sealed class MdictException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * 词典文件结构损坏或不符合预期（如 idx 大小校验失败、字段越界）。
 */
class CorruptDictException(message: String, cause: Throwable? = null) :
    MdictException(message, cause)

/**
 * 词典使用了本库不支持的特性。
 *
 * 最典型：record 区 Salsa20 注册码加密（`Encrypted & 1`）——
 * 无用户专属密钥则任何阅读器都无法解密（见 docs/38 §1.2）。
 */
class UnsupportedDictException(message: String, cause: Throwable? = null) :
    MdictException(message, cause)
