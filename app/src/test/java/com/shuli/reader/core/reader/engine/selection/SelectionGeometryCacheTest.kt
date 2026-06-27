package com.shuli.reader.core.reader.engine.selection

import com.shuli.reader.core.reader.model.BoxBounds
import com.shuli.reader.core.reader.model.PageLayout
import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.reader.model.TextPage
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SelectionGeometryCacheTest {

    @Test
    fun key_includesChapterIndex() {
        val range = SelectionRange(0, 0, 5, "Hello")
        val chapter0 = page(chapterIndex = 0, pageIndex = 1)
        val chapter1 = page(chapterIndex = 1, pageIndex = 1)

        val key0 = SelectionGeometryCacheKey.create(chapter0, range, layoutVersion = 1, styleVersion = 1)
        val key1 = SelectionGeometryCacheKey.create(chapter1, range, layoutVersion = 1, styleVersion = 1)

        assertNotEquals(key0, key1)
    }

    @Test
    fun onPageRecycled_withChapterIndex_onlyRemovesMatchingPage() {
        val cache = SelectionGeometryCache()
        val range = SelectionRange(0, 0, 5, "Hello")
        val key0 = SelectionGeometryCacheKey.create(page(chapterIndex = 0, pageIndex = 1), range, 1, 1)
        val key1 = SelectionGeometryCacheKey.create(page(chapterIndex = 1, pageIndex = 1), range, 1, 1)
        cache.put(key0, SelectionGeometry())
        cache.put(key1, SelectionGeometry())

        cache.onPageRecycled(chapterIndex = 0, pageIndex = 1)

        assertNull(cache.get(key0))
        assertNotNull(cache.get(key1))
    }

    private fun page(chapterIndex: Int, pageIndex: Int): TextPage {
        return TextPage(
            startCharOffset = 0,
            endCharOffset = 10,
            chapterIndex = chapterIndex,
            pageIndex = pageIndex,
            lines = emptyList(),
            layout = PageLayout(
                header = null,
                title = null,
                body = BoxBounds(left = 0f, top = 0f, right = 100f, bottom = 100f),
                footer = null,
                pageWidth = 100f,
                pageHeight = 100f,
            ),
        )
    }
}
