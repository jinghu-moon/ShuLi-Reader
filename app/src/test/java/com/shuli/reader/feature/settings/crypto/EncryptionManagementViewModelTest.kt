package com.shuli.reader.feature.settings.crypto

import com.shuli.reader.sync.crypto.KeyDerivation
import com.shuli.reader.sync.crypto.KeyDerivationParams
import com.shuli.reader.feature.settings.crypto.EncryptionManagementViewModel
import com.shuli.reader.feature.settings.crypto.PasswordVerifyResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Part of T-35 加密管理页
@OptIn(ExperimentalCoroutinesApi::class)
class EncryptionManagementViewModelTest {

    private fun defaultParams() = KeyDerivationParams(
        algorithm = "PBKDF2WithHmacSHA256",
        iterations = 600_000,
        keyLengthBits = 256,
    )

    @Test
    fun `encryption info shows disabled when no metadata`() = runTest(UnconfinedTestDispatcher()) {
        val vm = EncryptionManagementViewModel(
            cryptoMetadataJson = null,
            keyDerivation = KeyDerivation(defaultParams()),
            scope = backgroundScope,
        )
        val info = vm.encryptionInfo.first()
        assertEquals(false, info.isEnabled)
    }

    @Test
    fun `encryption info shows AES-256-GCM and 600K iterations`() = runTest(UnconfinedTestDispatcher()) {
        val metaJson = """{"kdf":"PBKDF2WithHmacSHA256","iterations":600000,"salt":"dGVzdA==","cipher":"AES-256-GCM","keyVersion":1,"createdAt":1234567890,"integrity":"abc"}"""
        val vm = EncryptionManagementViewModel(
            cryptoMetadataJson = metaJson,
            keyDerivation = KeyDerivation(defaultParams()),
            scope = backgroundScope,
        )
        val info = vm.encryptionInfo.first()
        assertEquals("AES-256-GCM", info.algorithm)
        assertEquals(600_000, info.kdfIterations)
    }

    @Test
    fun `verifyPassword succeeds with valid password`() = runTest(UnconfinedTestDispatcher()) {
        val vm = EncryptionManagementViewModel(
            cryptoMetadataJson = null,
            keyDerivation = KeyDerivation(defaultParams()),
            scope = backgroundScope,
        )
        vm.verifyPassword("test-password", ByteArray(16) { it.toByte() })
        val result = vm.verifyResult.first()
        assertEquals(PasswordVerifyResult.SUCCESS, result)
    }
}
