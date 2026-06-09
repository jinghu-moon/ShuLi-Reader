package com.shuli.reader.feature.reader.render

import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.data.ThemeColors
import com.shuli.reader.core.reader.SlotResolution
import com.shuli.reader.core.reader.TitleStyleConfig
import com.shuli.reader.core.reader.animation.PageDelegateFactory
import com.shuli.reader.core.reader.layout.ReaderLayoutInput
import com.shuli.reader.core.reader.layout.createDefaultLayoutInput
import com.shuli.reader.core.reader.model.PageRenderMode
import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.reader.model.TextPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReaderRenderSnapshotTest {

    @Test
    fun pageSnapshot_equals_sameFields_returnsTrue() {
        val a = createDefaultPageSnapshot(pageIndex = 5)
        val b = createDefaultPageSnapshot(pageIndex = 5)
        assertEquals(a, b)
    }

    @Test
    fun pageSnapshot_equals_differentPageIndex_returnsFalse() {
        val a = createDefaultPageSnapshot(pageIndex = 5)
        val b = createDefaultPageSnapshot(pageIndex = 6)
        assertNotEquals(a, b)
    }

    @Test
    fun pageSnapshot_contentVersion_isIntNotCharSequence() {
        val snapshot = createDefaultPageSnapshot(contentVersion = 42)
        val version: Int = snapshot.contentVersion
        assertEquals(42, version)
    }

    @Test
    fun layoutSnapshot_equals_sameLayoutKey_returnsTrue() {
        val key = LayoutKey(layoutVersion = 1, inputHash = "abc")
        val a = LayoutSnapshot(input = createDefaultLayoutInput(), layoutKey = key)
        val b = LayoutSnapshot(input = createDefaultLayoutInput(), layoutKey = key)
        assertEquals(a, b)
    }

    @Test
    fun visualSnapshot_equals_sameTheme_returnsTrue() {
        val a = createDefaultVisualSnapshot()
        val b = createDefaultVisualSnapshot()
        assertEquals(a, b)
    }

    @Test
    fun shellSnapshot_equals_sameBattery_returnsTrue() {
        val a = createDefaultShellSnapshot(batteryLevel = 80)
        val b = createDefaultShellSnapshot(batteryLevel = 80)
        assertEquals(a, b)
    }

    @Test
    fun overlaySnapshot_equals_sameTtsRange_returnsTrue() {
        val range = SelectionRange(0, 10, 20, "test")
        val a = OverlaySnapshot(null, range, emptyList(), OverlayKey(null, range, 0))
        val b = OverlaySnapshot(null, range, emptyList(), OverlayKey(null, range, 0))
        assertEquals(a, b)
    }

    @Test
    fun overlaySnapshot_differentTtsRange_returnsFalse() {
        val a = OverlaySnapshot(null, SelectionRange(0, 10, 20, "a"), emptyList(), OverlayKey(null, null, 0))
        val b = OverlaySnapshot(null, SelectionRange(0, 30, 40, "b"), emptyList(), OverlayKey(null, null, 0))
        assertNotEquals(a, b)
    }

    @Test
    fun readerRenderSnapshot_generation_isAccessible() {
        val snapshot = createDefaultSnapshot(generation = 7)
        assertEquals(7L, snapshot.generation)
    }
}

// ── Test helpers ──

fun createDefaultPageSnapshot(
    bookId: Long = 1L,
    chapterIndex: Int = 0,
    pageIndex: Int = 0,
    anchorByteOffset: Long = 0L,
    currentPage: TextPage? = TextPage.EMPTY,
    nextPage: TextPage? = null,
    prevPage: TextPage? = null,
    contentVersion: Int = 1,
    pageRenderMode: PageRenderMode = PageRenderMode.SEQUENTIAL,
    animType: PageDelegateFactory.PageAnimType = PageDelegateFactory.PageAnimType.HORIZONTAL,
    canTurnPrev: Boolean = false,
    canTurnNext: Boolean = true,
) = PageSnapshot(
    bookId = bookId,
    chapterIndex = chapterIndex,
    pageIndex = pageIndex,
    anchorByteOffset = anchorByteOffset,
    currentPage = currentPage,
    nextPage = nextPage,
    prevPage = prevPage,
    contentVersion = contentVersion,
    pageRenderMode = pageRenderMode,
    pageAnimType = animType,
    canTurnPrev = canTurnPrev,
    canTurnNext = canTurnNext,
)

fun createDefaultVisualSnapshot(
    themeColors: ThemeColors = ThemeColors(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF666666.toInt(), 0xFF666666.toInt(), 0xFF333333.toInt(), 0xFF1976D2.toInt()),
    textAlign: ReaderTextAlign = ReaderTextAlign.LEFT,
    titleStyle: TitleStyleConfig = TitleStyleConfig(),
) = VisualSnapshot(
    themeColors = themeColors,
    textAlign = textAlign,
    titleStyle = titleStyle,
    contentKey = RenderKey(textAlign, themeColors, titleStyle, true, 0.4f),
)

fun createDefaultShellSnapshot(
    batteryLevel: Int = 100,
    headerText: String = "",
    footerText: String = "",
) = ShellSnapshot(
    headerSlots = SlotResolution(left = headerText),
    footerSlots = SlotResolution(left = footerText),
    batteryLevel = batteryLevel,
    showProgress = true,
    headerFooterAlpha = 0.4f,
    shellKey = RenderKey(ReaderTextAlign.LEFT, ThemeColors(0, 0, 0, 0, 0, 0), TitleStyleConfig(), true, 0.4f),
)

fun createDefaultOverlaySnapshot(
    ttsRange: SelectionRange? = null,
    selection: SelectionRange? = null,
) = OverlaySnapshot(
    selectedRange = selection,
    ttsActiveRange = ttsRange,
    noteRanges = emptyList(),
    overlayKey = OverlayKey(selection, ttsRange, 0),
)

fun createDefaultSnapshot(
    generation: Long = 1,
    page: PageSnapshot = createDefaultPageSnapshot(),
    layout: LayoutSnapshot = LayoutSnapshot(createDefaultLayoutInput(), LayoutKey(1, "test")),
    visual: VisualSnapshot = createDefaultVisualSnapshot(),
    shell: ShellSnapshot = createDefaultShellSnapshot(),
    overlay: OverlaySnapshot = createDefaultOverlaySnapshot(),
) = ReaderRenderSnapshot(
    generation = generation,
    page = page,
    layout = layout,
    visual = visual,
    shell = shell,
    overlay = overlay,
)

fun createLayoutSnapshot(
    fontSizeSp: Float = 18f,
    fontKey: String = "harmony",
    fontWeight: ReaderFontWeight = ReaderFontWeight.NORMAL,
    lineSpacing: Float = 1.5f,
    marginHorizontalDp: Float = 24f,
) = LayoutSnapshot(
    input = createDefaultLayoutInput(
        fontSizeSp = fontSizeSp,
        fontKey = fontKey,
        fontWeight = fontWeight,
        lineSpacing = lineSpacing,
        marginHorizontalDp = marginHorizontalDp,
    ),
    layoutKey = ReaderLayoutHasher.hash(
        createDefaultLayoutInput(
            fontSizeSp = fontSizeSp,
            fontKey = fontKey,
            fontWeight = fontWeight,
            lineSpacing = lineSpacing,
            marginHorizontalDp = marginHorizontalDp,
        )
    ),
)

fun createVisualSnapshot(
    textAlign: ReaderTextAlign = ReaderTextAlign.LEFT,
    themeColors: ThemeColors = ThemeColors(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF666666.toInt(), 0xFF666666.toInt(), 0xFF333333.toInt(), 0xFF1976D2.toInt()),
) = createDefaultVisualSnapshot(themeColors = themeColors, textAlign = textAlign)

fun createShellSnapshot(
    batteryLevel: Int = 100,
    headerText: String = "",
) = createDefaultShellSnapshot(batteryLevel = batteryLevel, headerText = headerText)

fun createOverlaySnapshot(
    ttsRange: SelectionRange? = null,
    selection: SelectionRange? = null,
) = createDefaultOverlaySnapshot(ttsRange = ttsRange, selection = selection)
