package com.shuli.reader.core.reader.engine

import android.text.StaticLayout
import android.text.TextPaint
import com.shuli.reader.core.reader.model.BoxBounds
import com.shuli.reader.core.reader.model.PageLayout
import com.shuli.reader.core.reader.model.TextLine
import com.shuli.reader.core.reader.model.TextPage
import com.shuli.reader.core.reader.model.TitleStyleConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScrollBodyFlowLayoutTest {

    private val body = BoxBounds(left = 40f, top = 100f, right = 500f, bottom = 900f)
    private val layoutWithoutTitle = PageLayout(
        header = null,
        title = null,
        body = body,
        footer = null,
        pageWidth = 540f,
        pageHeight = 960f,
    )

    @Test
    fun finalChapterPage_usesActualContentHeightInsteadOfFullBodyHeight() {
        val page = textPage(
            layout = layoutWithoutTitle,
            lines = listOf(textLine(top = 100f, bottom = 220f)),
            endCharOffset = 100,
            chapterContentLength = 100,
            density = 2f,
        )

        val segment = ScrollBodyFlowLayout.segmentFor(page, TitleStyleConfig())

        assertEquals(160f, segment.height, 0.1f)
        assertTrue(segment.height < body.height)
    }

    @Test
    fun nonFinalPage_usesActualContentHeightInsteadOfFullBodyHeight() {
        val page = textPage(
            layout = layoutWithoutTitle,
            lines = listOf(textLine(top = 100f, bottom = 220f)),
            endCharOffset = 50,
            chapterContentLength = 100,
        )

        val segment = ScrollBodyFlowLayout.segmentFor(page, TitleStyleConfig())

        assertEquals(120f, segment.height, 0.1f)
        assertTrue(segment.height < body.height)
    }

    @Test
    fun chapterFirstPage_startsSegmentAtTitleFlowTop() {
        val titleLayout = titleLayout()
        val titleBox = BoxBounds(left = 40f, top = 100f, right = 500f, bottom = 220f)
        val firstPageBody = BoxBounds(left = 40f, top = 300f, right = 500f, bottom = 900f)
        val firstPageLayout = PageLayout(
            header = null,
            title = titleBox,
            body = firstPageBody,
            footer = null,
            pageWidth = 540f,
            pageHeight = 960f,
        )
        val titleStyle = TitleStyleConfig(marginTopDp = 10f, marginBottomDp = 12f)
        val page = textPage(
            layout = firstPageLayout,
            titleLayout = titleLayout,
            lines = listOf(textLine(top = 300f, bottom = 360f)),
            endCharOffset = 50,
            chapterContentLength = 100,
            density = 2f,
        )

        val segment = ScrollBodyFlowLayout.segmentFor(page, titleStyle)

        assertEquals(100f, segment.sourceTop, 0.1f)
        val firstLineLocalTop = page.lines.first().top - segment.sourceTop
        assertTrue(firstLineLocalTop >= titleStyle.marginTopDp * page.density + titleLayout.height)
    }

    @Test
    fun chapterFirstPage_viewportStartsAtTitleFlowTop() {
        val titleBox = BoxBounds(left = 40f, top = 100f, right = 500f, bottom = 220f)
        val firstPageBody = BoxBounds(left = 40f, top = 300f, right = 500f, bottom = 900f)
        val firstPageLayout = PageLayout(
            header = null,
            title = titleBox,
            body = firstPageBody,
            footer = null,
            pageWidth = 540f,
            pageHeight = 960f,
        )
        val page = textPage(
            layout = firstPageLayout,
            titleLayout = titleLayout(),
            lines = listOf(textLine(top = 300f, bottom = 360f)),
            endCharOffset = 50,
            chapterContentLength = 100,
        )

        val viewport = ScrollBodyFlowLayout.viewportFor(page)

        assertEquals(titleBox.top, viewport.top, 0.1f)
        assertEquals(firstPageBody.bottom, viewport.bottom, 0.1f)
    }

    private fun textPage(
        layout: PageLayout,
        lines: List<TextLine>,
        endCharOffset: Int,
        chapterContentLength: Int,
        density: Float = 1f,
        titleLayout: StaticLayout? = null,
    ): TextPage {
        return TextPage(
            startCharOffset = 0,
            endCharOffset = endCharOffset,
            chapterIndex = 0,
            pageIndex = 0,
            lines = lines,
            layout = layout,
            titleLayout = titleLayout,
            density = density,
            chapterContentLength = chapterContentLength,
        )
    }

    private fun textLine(top: Float, bottom: Float): TextLine {
        return TextLine(
            startCharOffset = 0,
            endCharOffset = 10,
            baseline = top + (bottom - top) * 0.8f,
            top = top,
            bottom = bottom,
            isParagraphEnd = false,
        )
    }

    private fun titleLayout(): StaticLayout {
        val paint = TextPaint().apply { textSize = 24f }
        return StaticLayout.Builder.obtain("Next Chapter", 0, "Next Chapter".length, paint, 460)
            .setIncludePad(false)
            .build()
    }
}
