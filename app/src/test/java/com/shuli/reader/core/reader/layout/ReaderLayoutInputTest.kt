package com.shuli.reader.core.reader.layout

import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.IndentUnit
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.reader.model.TitleStyleConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReaderLayoutInputTest {

    @Test
    fun constructor_allFieldsAccessible() {
        val input = createDefaultLayoutInput()
        assertEquals(1, input.layoutVersion)
        assertEquals(1080, input.viewportWidth)
        assertEquals("harmony", input.fontKey)
        assertEquals(18f, input.fontSizeSp)
        assertEquals(3f, input.density)
    }

    @Test
    fun equals_sameInputs_returnsTrue() {
        val a = createDefaultLayoutInput()
        val b = createDefaultLayoutInput()
        assertEquals(a, b)
    }

    @Test
    fun equals_differentFontSize_returnsFalse() {
        val a = createDefaultLayoutInput(fontSizeSp = 16f)
        val b = createDefaultLayoutInput(fontSizeSp = 18f)
        assertNotEquals(a, b)
    }

    @Test
    fun equals_differentLayoutVersion_returnsFalse() {
        val a = createDefaultLayoutInput(layoutVersion = 1)
        val b = createDefaultLayoutInput(layoutVersion = 2)
        assertNotEquals(a, b)
    }

    @Test
    fun equals_differentFontWeight_returnsFalse() {
        val a = createDefaultLayoutInput(fontWeight = ReaderFontWeight.NORMAL)
        val b = createDefaultLayoutInput(fontWeight = ReaderFontWeight.BOLD)
        assertNotEquals(a, b)
    }

    @Test
    fun equals_differentChineseConvert_returnsFalse() {
        val a = createDefaultLayoutInput(chineseConvert = ChineseConvert.NONE)
        val b = createDefaultLayoutInput(chineseConvert = ChineseConvert.SIMPLIFIED)
        assertNotEquals(a, b)
    }

    @Test
    fun equals_differentViewport_returnsFalse() {
        val a = createDefaultLayoutInput(viewportWidth = 1080)
        val b = createDefaultLayoutInput(viewportWidth = 1440)
        assertNotEquals(a, b)
    }
}

fun createDefaultLayoutInput(
    layoutVersion: Int = 1,
    bookId: Long = 1L,
    chapterIndex: Int = 0,
    anchorByteOffset: Long = 0L,
    viewportWidth: Int = 1080,
    viewportHeight: Int = 1920,
    density: Float = 3f,
    fontSizeSp: Float = 18f,
    fontKey: String = "harmony",
    fontWeight: ReaderFontWeight = ReaderFontWeight.NORMAL,
    lineSpacing: Float = 1.5f,
    paragraphSpacing: Float = 1.0f,
    letterSpacing: Float = 0f,
    marginTopDp: Float = 48f,
    marginBottomDp: Float = 48f,
    marginLeftDp: Float = 24f,
    marginRightDp: Float = 24f,
    indent: Float = 2f,
    indentUnit: IndentUnit = IndentUnit.CHARACTER,
    titleStyle: TitleStyleConfig = TitleStyleConfig(),
    headerVisibleForLayout: Boolean = true,
    footerVisibleForLayout: Boolean = true,
    chineseConvert: ChineseConvert = ChineseConvert.NONE,
    usePanguSpacing: Boolean = false,
    useZhLayout: Boolean = false,
    bottomJustify: Boolean = false,
) = ReaderLayoutInput(
    layoutVersion = layoutVersion,
    bookId = bookId,
    chapterIndex = chapterIndex,
    anchorByteOffset = anchorByteOffset,
    viewportWidth = viewportWidth,
    viewportHeight = viewportHeight,
    density = density,
    fontSizeSp = fontSizeSp,
    fontKey = fontKey,
    fontWeight = fontWeight,
    lineSpacing = lineSpacing,
    paragraphSpacing = paragraphSpacing,
    letterSpacing = letterSpacing,
    marginTopDp = marginTopDp,
    marginBottomDp = marginBottomDp,
    marginLeftDp = marginLeftDp,
    marginRightDp = marginRightDp,
    indent = indent,
    indentUnit = indentUnit,
    titleStyle = titleStyle,
    headerVisibleForLayout = headerVisibleForLayout,
    footerVisibleForLayout = footerVisibleForLayout,
    chineseConvert = chineseConvert,
    usePanguSpacing = usePanguSpacing,
    useZhLayout = useZhLayout,
    bottomJustify = bottomJustify,
)
