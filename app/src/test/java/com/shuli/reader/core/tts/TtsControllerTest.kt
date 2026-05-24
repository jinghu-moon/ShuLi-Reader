package com.shuli.reader.core.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsControllerTest {

    @Test
    fun initialize_configuresEngineAndEntersReady() {
        val engine = FakeTtsEngine()
        val controller = TtsController(engine)
        val config = TtsConfig(speed = 1.2f, pitch = 0.9f, autoPage = true)

        controller.initialize(config)

        assertEquals(TtsState.READY, controller.state)
        assertEquals(config, controller.config)
        assertEquals(config, engine.config)
    }

    @Test
    fun play_speaksTextAndEntersPlaying() {
        val engine = FakeTtsEngine()
        val controller = TtsController(engine)

        controller.initialize()
        controller.play("hello")

        assertEquals(TtsState.PLAYING, controller.state)
        assertEquals("hello", controller.activeText)
        assertEquals("hello", engine.spokenText)
    }

    @Test
    fun pauseAndResume_keepCurrentText() {
        val engine = FakeTtsEngine()
        val controller = TtsController(engine)

        controller.initialize()
        controller.play("paragraph")
        controller.pause()
        controller.resume()

        assertEquals(TtsState.PLAYING, controller.state)
        assertEquals("paragraph", engine.spokenText)
        assertEquals(1, engine.stopCount)
    }

    @Test
    fun stop_clearsCurrentText() {
        val engine = FakeTtsEngine()
        val controller = TtsController(engine)

        controller.initialize()
        controller.play("paragraph")
        controller.stop()

        assertEquals(TtsState.STOPPED, controller.state)
        assertEquals("", controller.activeText)
        assertEquals(1, engine.stopCount)
    }

    @Test
    fun play_withBlankText_entersErrorWithoutEngineCall() {
        val engine = FakeTtsEngine()
        val controller = TtsController(engine)

        controller.initialize()
        controller.play("")

        assertEquals(TtsState.ERROR, controller.state)
        assertEquals("", engine.spokenText)
    }

    @Test
    fun completion_withAutoPageEnabled_invokesPageEndCallback() {
        val engine = FakeTtsEngine()
        var pageEndCount = 0
        val controller = TtsController(engine, onPageEnd = { pageEndCount++ })

        controller.initialize(TtsConfig(autoPage = true))
        controller.play("page text")
        engine.completeUtterance()

        assertEquals(1, pageEndCount)
        assertEquals(TtsState.READY, controller.state)
        assertEquals("", controller.activeText)
    }

    @Test
    fun completion_invokesUtteranceCompletedCallback() {
        val engine = FakeTtsEngine()
        var completionCount = 0
        val controller = TtsController(engine, onUtteranceCompleted = { completionCount++ })

        controller.initialize()
        controller.play("page text")
        engine.completeUtterance()

        assertEquals(1, completionCount)
        assertEquals(TtsState.READY, controller.state)
    }

    @Test
    fun completion_withAutoPageDisabled_doesNotInvokePageEndCallback() {
        val engine = FakeTtsEngine()
        var pageEndCount = 0
        val controller = TtsController(engine, onPageEnd = { pageEndCount++ })

        controller.initialize(TtsConfig(autoPage = false))
        controller.play("page text")
        engine.completeUtterance()

        assertEquals(0, pageEndCount)
        assertEquals(TtsState.READY, controller.state)
    }

    @Test
    fun completion_afterStop_doesNotInvokePageEndCallback() {
        val engine = FakeTtsEngine()
        var pageEndCount = 0
        val controller = TtsController(engine, onPageEnd = { pageEndCount++ })

        controller.initialize(TtsConfig(autoPage = true))
        controller.play("page text")
        controller.stop()
        engine.completeUtterance()

        assertEquals(0, pageEndCount)
        assertEquals(TtsState.STOPPED, controller.state)
    }

    private class FakeTtsEngine : TtsEngine {
        var config = TtsConfig()
        var spokenText = ""
        var stopCount = 0
        var shutdownCount = 0
        private var listener: TtsEngine.Listener? = null

        override fun configure(config: TtsConfig) {
            this.config = config
        }

        override fun setListener(listener: TtsEngine.Listener?) {
            this.listener = listener
        }

        override fun speak(text: String) {
            spokenText = text
        }

        override fun stop() {
            stopCount++
        }

        override fun shutdown() {
            shutdownCount++
        }

        fun completeUtterance() {
            listener?.onUtteranceCompleted()
        }
    }
}
