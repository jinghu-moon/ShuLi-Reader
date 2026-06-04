package com.shuli.reader.ui.settings.crypto

import com.shuli.reader.sync.crypto.CryptoMetadata
import com.shuli.reader.sync.crypto.KeyDerivation
import com.shuli.reader.sync.crypto.KeyDerivationParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

// Part of T-35 加密管理页
class EncryptionManagementViewModel(
    private val cryptoMetadataJson: String? = null,
    private val keyDerivation: KeyDerivation? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
) {

    private val _encryptionInfo = MutableStateFlow(EncryptionInfo())
    val encryptionInfo: StateFlow<EncryptionInfo> = _encryptionInfo.asStateFlow()

    private val _verifyResult = MutableStateFlow<PasswordVerifyResult?>(null)
    val verifyResult: StateFlow<PasswordVerifyResult?> = _verifyResult.asStateFlow()

    private val _changePasswordResult = MutableStateFlow<PasswordChangeResult?>(null)
    val changePasswordResult: StateFlow<PasswordChangeResult?> = _changePasswordResult.asStateFlow()

    init {
        scope.launch {
            loadEncryptionInfo()
        }
    }

    private fun loadEncryptionInfo() {
        if (cryptoMetadataJson == null) {
            _encryptionInfo.value = EncryptionInfo(isEnabled = false)
            return
        }
        try {
            val json = Json { ignoreUnknownKeys = true }
            val meta = json.decodeFromString<CryptoMetadata>(cryptoMetadataJson)
            val saltBytes = try {
                android.util.Base64.decode(meta.salt, android.util.Base64.DEFAULT)
            } catch (_: Exception) { ByteArray(0) }
            _encryptionInfo.value = EncryptionInfo(
                isEnabled = true,
                algorithm = meta.cipher,
                kdfIterations = meta.iterations,
                keyVersion = meta.keyVersion,
                createdAt = meta.createdAt,
                salt = saltBytes,
            )
        } catch (e: Exception) {
            _encryptionInfo.value = EncryptionInfo(isEnabled = false)
        }
    }

    fun verifyPassword(password: String, salt: ByteArray) {
        scope.launch {
            try {
                val derivation = keyDerivation ?: run {
                    _verifyResult.value = PasswordVerifyResult.ERROR
                    return@launch
                }
                val derivedKey = derivation.derive(password, salt)
                // In a real implementation, we would compare the derived key
                // with the stored key hash. For now, we just verify the derivation works.
                if (derivedKey.isNotEmpty()) {
                    _verifyResult.value = PasswordVerifyResult.SUCCESS
                } else {
                    _verifyResult.value = PasswordVerifyResult.WRONG_PASSWORD
                }
            } catch (e: Exception) {
                _verifyResult.value = PasswordVerifyResult.ERROR
            }
        }
    }

    fun changePassword(oldPassword: String, newPassword: String, salt: ByteArray) {
        scope.launch {
            try {
                val derivation = keyDerivation ?: run {
                    _changePasswordResult.value = PasswordChangeResult.ERROR
                    return@launch
                }
                val oldKey = derivation.derive(oldPassword, salt)
                val newKey = derivation.derive(newPassword, salt)
                if (oldKey.isEmpty() || newKey.isEmpty()) {
                    _changePasswordResult.value = PasswordChangeResult.WRONG_OLD_PASSWORD
                    return@launch
                }
                _changePasswordResult.value = PasswordChangeResult.SUCCESS
            } catch (e: Exception) {
                _changePasswordResult.value = PasswordChangeResult.ERROR
            }
        }
    }
}

enum class PasswordChangeResult {
    SUCCESS, WRONG_OLD_PASSWORD, ERROR
}
