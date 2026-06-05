package com.shuli.reader.core.i18n

/**
 * 端到端加密管理字符串。
 */
interface EncryptionStrings {
    val encryptionManagement: String
    val encryptionEnabled: String
    val encryptionDisabled: String
    val e2eeProtectsSyncData: String
    val dataSyncedInPlaintext: String
    val e2eeNotEnabled: String
    val e2eeNotEnabledDesc: String
    val enableEncryption: String
    val rememberEncryptionPassword: String
    val rememberEncryptionPasswordDesc: String
    val verifyPassword: String
    val changePassword: String
    val setEncryptionPassword: String
    val inputPasswordToVerify: String
    val encryptionPassword: String
    val verifySuccess: String
    val passwordWrong: String
    val encryptionNotEnabled: String
    val verifyError: String
    val verify: String
    val oldPassword: String
    val newPassword: String
    val confirmNewPassword: String
    val passwordMismatch: String
    val confirmChange: String
    val confirmSet: String
    val algorithmDetails: String
    val encryptionAlgorithm: String
    val kdfIterations: String
    val keyVersion: String
    val createdAt: String
}
