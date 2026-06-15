package com.shuli.reader.core.reader.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TextPageTest {

    @Test
    fun textPage_hasOverlayRecorder() {
        val page = TextPage.EMPTY
        assertNotNull(page.overlayRecorder)
    }

    @Test
    fun textPage_hasContentRecorder() {
        val page = TextPage.EMPTY
        assertNotNull(page.contentRecorder)
    }

    @Test
    fun textPage_contentRecorder_isSameInstanceAsCanvasRecorder() {
        val page = TextPage.EMPTY
        assertSame(page.canvasRecorder, page.contentRecorder)
    }

    @Test
    fun textPage_invalidateOverlay_onlyInvalidatesOverlay() {
        val page = createTestPage()
        page.markAllRecordersClean()

        page.invalidateOverlay()

        assertTrue(page.overlayRecorder.needRecord())
        assertFalse(page.contentRecorder.needRecord())
        assertFalse(page.shellRecorder.needRecord())
    }

    @Test
    fun textPage_invalidateContent_doesNotInvalidateOverlay() {
        val page = createTestPage()
        page.markAllRecordersClean()

        page.invalidateContent()

        assertTrue(page.contentRecorder.needRecord())
        assertFalse(page.overlayRecorder.needRecord())
    }

    @Test
    fun textPage_invalidateAll_invalidatesEverything() {
        val page = createTestPage()
        page.markAllRecordersClean()

        page.invalidateAll()

        assertTrue(page.contentRecorder.needRecord())
        assertTrue(page.shellRecorder.needRecord())
        assertTrue(page.overlayRecorder.needRecord())
    }
}

private fun createTestPage(): TextPage = TextPage(
    startCharOffset = 0,
    endCharOffset = 100,
    chapterIndex = 0,
    pageIndex = 0,
    pageSize = PageSize(1080, 1920),
    marginHorizontal = 24f,
    lines = emptyList(),
)

/**
 * 通过反射将 BaseCanvasRecorder.isDirty 置 false，模拟"已录制"状态。
 *
 * endRecording() 在纯 JVM 测试下会因 canvas=null 触发 NullPointerException
 * （CanvasPool.obtain() 在 returnDefaultValues 模式返回 null）。
 */
private fun TextPage.markAllRecordersClean() {
    listOf(canvasRecorder, shellRecorder, overlayRecorder, compositeRecorder)
        .forEach { it.markClean() }
}

private fun com.shuli.reader.core.recorder.CanvasRecorder.markClean() {
    // CanvasRecorderLocked 通过 by delegate 持有私有 delegate 字段，
    // isDirty 在委托目标（BaseCanvasRecorder 及其子类）上。
    var target: Any = this
    runCatching {
        val delegateField = target.javaClass.getDeclaredField("delegate")
        delegateField.isAccessible = true
        val unwrapped = delegateField.get(target)
        if (unwrapped != null) target = unwrapped
    }
    // @JvmField protected 字段在 BaseCanvasRecorder 上，需沿 class 层级向上查找
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
