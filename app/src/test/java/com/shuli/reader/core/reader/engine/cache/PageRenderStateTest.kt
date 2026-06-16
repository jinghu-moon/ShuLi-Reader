package com.shuli.reader.core.reader.engine.cache

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageRenderStateTest {

    @Test
    fun pageRenderState_hasAllRecorders() {
        val state = PageRenderState()
        assertNotNull(state.content)
        assertNotNull(state.shell)
        assertNotNull(state.overlay)
        assertNotNull(state.composite)
    }

    @Test
    fun pageRenderState_invalidateOverlay_onlyInvalidatesOverlayAndComposite() {
        val state = PageRenderState()
        state.markAllClean()

        state.invalidateOverlay()

        assertTrue(state.overlay.needRecord())
        assertTrue(state.composite.needRecord())
        assertFalse(state.content.needRecord())
        assertFalse(state.shell.needRecord())
    }

    @Test
    fun pageRenderState_invalidateContent_doesNotInvalidateOverlay() {
        val state = PageRenderState()
        state.markAllClean()

        state.invalidateContent()

        assertTrue(state.content.needRecord())
        assertTrue(state.composite.needRecord())
        assertFalse(state.overlay.needRecord())
    }

    @Test
    fun pageRenderState_invalidateAll_invalidatesEverything() {
        val state = PageRenderState()
        state.markAllClean()

        state.invalidateAll()

        assertTrue(state.content.needRecord())
        assertTrue(state.shell.needRecord())
        assertTrue(state.overlay.needRecord())
        assertTrue(state.composite.needRecord())
    }

    @Test
    fun pageRenderState_invalidateShell_doesNotInvalidateContent() {
        val state = PageRenderState()
        state.markAllClean()

        state.invalidateShell()

        assertTrue(state.shell.needRecord())
        assertTrue(state.composite.needRecord())
        assertFalse(state.content.needRecord())
        assertFalse(state.overlay.needRecord())
    }
}

private fun PageRenderState.markAllClean() {
    listOf(content, shell, overlay, composite).forEach { it.markClean() }
}

private fun com.shuli.reader.core.recorder.CanvasRecorder.markClean() {
    var target: Any = this
    runCatching {
        val delegateField = target.javaClass.getDeclaredField("delegate")
        delegateField.isAccessible = true
        val unwrapped = delegateField.get(target)
        if (unwrapped != null) target = unwrapped
    }
    val dirtyField = findFieldInHierarchy(target.javaClass, "isDirty")
    dirtyField.isAccessible = true
    dirtyField.setBoolean(target, false)
}

private fun findFieldInHierarchy(clazz: Class<*>, name: String): java.lang.reflect.Field {
    var current: Class<*>? = clazz
    while (current != null) {
        try {
            return current.getDeclaredField(name)
        } catch (_: NoSuchFieldException) {
            current = current.superclass
        }
    }
    throw NoSuchFieldException("$name not found in hierarchy of ${clazz.name}")
}
