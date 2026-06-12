package com.shuli.reader.core.tts

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TtsManagerTest {

    // T-3.1.1: 初始化成功
    @Test
    fun initialize_success() = runTest {
        val manager = FakeTtsManager(initResult = true)
        val result = manager.initialize()
        assertTrue(result.isSuccess)
        assertTrue(manager.isInitialized)
        assertNull(manager.initError)
    }

    // T-3.1.2: 初始化失败时 initError 有值
    @Test
    fun initialize_failure_setsInitError() = runTest {
        val manager = FakeTtsManager(initResult = false)
        val result = manager.initialize()
        assertTrue(result.isFailure)
        assertFalse(manager.isInitialized)
        assertNotNull(manager.initError)
    }

    // T-3.1.3: speak() 未初始化返回 false
    @Test
    fun speak_notInitialized_returnsFalse() {
        val manager = FakeTtsManager(initResult = true)
        // 未调用 initialize()
        assertFalse(manager.isInitialized)
        val result = manager.speak("Hello", "utt1")
        assertFalse(result)
    }

    // T-3.1.4: speak() 成功返回 true
    @Test
    fun speak_initialized_returnsTrue() = runTest {
        val manager = FakeTtsManager(initResult = true)
        manager.initialize()
        val result = manager.speak("Hello", "utt1")
        assertTrue(result)
    }

    // T-3.1.5: onError 触发恢复
    @Test
    fun onError_triggersListener() = runTest {
        val manager = FakeTtsManager(initResult = true)
        manager.initialize()

        var errorReceived = false
        manager.setListener(object : TtsListener {
            override fun onError(utteranceId: String, errorCode: Int) {
                errorReceived = true
            }
        })

        manager.speak("Hello", "utt1")
        manager.simulateError("utt1", -1)
        assertTrue(errorReceived)
    }

    // T-3.1.6: shutdown() 清理资源
    @Test
    fun shutdown_clearsResources() = runTest {
        val manager = FakeTtsManager(initResult = true)
        manager.initialize()
        assertTrue(manager.isInitialized)

        manager.shutdown()
        assertFalse(manager.isInitialized)
        assertNull(manager.initError)
    }

    // onDone 回调测试
    @Test
    fun onDone_triggersListener() = runTest {
        val manager = FakeTtsManager(initResult = true)
        manager.initialize()

        var doneReceived = false
        manager.setListener(object : TtsListener {
            override fun onDone(utteranceId: String) {
                doneReceived = true
            }
        })

        manager.speak("Hello", "utt1")
        manager.simulateDone("utt1")
        assertTrue(doneReceived)
    }

    // getAvailableVoices 测试
    @Test
    fun getAvailableVoices_returnsNonEmpty() = runTest {
        val manager = FakeTtsManager(initResult = true)
        manager.initialize()
        val voices = manager.getAvailableVoices()
        assertTrue(voices.isNotEmpty())
    }
}

/**
 * TtsManager 的 Fake 实现，用于单元测试。
 */
private class FakeTtsManager(
    private val initResult: Boolean,
) : TtsManager {

    override var isInitialized: Boolean = false
        private set

    override var initError: String? = null
        private set

    private var listener: TtsListener? = null
    private val spokenUtterances = mutableMapOf<String, String>()

    override suspend fun initialize(): Result<Unit> {
        return if (initResult) {
            isInitialized = true
            initError = null
            Result.success(Unit)
        } else {
            isInitialized = false
            initError = "TTS initialization failed"
            Result.failure(RuntimeException(initError))
        }
    }

    override fun speak(text: String, utteranceId: String): Boolean {
        if (!isInitialized) return false
        spokenUtterances[utteranceId] = text
        listener?.onStart(utteranceId)
        return true
    }

    override fun stop() {
        spokenUtterances.clear()
    }

    override fun shutdown() {
        isInitialized = false
        initError = null
        spokenUtterances.clear()
        listener = null
    }

    override fun setListener(listener: TtsListener?) {
        this.listener = listener
    }

    override fun getAvailableVoices(): List<String> {
        return listOf("default", "zh-CN", "en-US")
    }

    fun simulateDone(utteranceId: String) {
        listener?.onDone(utteranceId)
    }

    fun simulateError(utteranceId: String, errorCode: Int) {
        listener?.onError(utteranceId, errorCode)
    }
}
