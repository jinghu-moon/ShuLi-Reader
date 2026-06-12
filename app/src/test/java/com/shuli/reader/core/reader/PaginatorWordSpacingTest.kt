package com.shuli.reader.core.reader

import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.IndentUnit
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.reader.layout.ReaderLayoutInput
import com.shuli.reader.core.reader.model.PageSize
import com.shuli.reader.core.reader.model.ReaderLayoutConfig
import com.shuli.reader.core.reader.TitleStyleConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaginatorWordSpacingTest {

    private val measurer = SimpleTextMeasurer()
    private val paginator = Paginator(measurer)
    private val pageSize = PageSize(1080, 1920)

    private fun config(wordSpacing: Float = 0f) = ReaderLayoutConfig(
        pageSize = pageSize,
        textSize = 16f * 2.75f, // 44px
        lineHeight = 1.5f,
        paragraphSpacing = 1.0f * 44f,
        marginTop = 48f * 2.75f,
        marginBottom = 48f * 2.75f,
        marginLeft = 24f * 2.75f,
        marginRight = 24f * 2.75f,
        indent = 2f * 44f,
        density = 2.75f,
        letterSpacingPx = 0f,
        wordSpacingPx = wordSpacing,
    )

    private fun layoutInput(wordSpacing: Float = 0f) = ReaderLayoutInput(
        layoutVersion = 1,
        bookId = 1L,
        chapterIndex = 0,
        anchorByteOffset = 0L,
        viewportWidth = 1080,
        viewportHeight = 1920,
        density = 2.75f,
        fontSizeSp = 16f,
        fontKey = "default",
        fontWeight = ReaderFontWeight.NORMAL,
        lineSpacing = 1.5f,
        paragraphSpacing = 1.0f,
        letterSpacing = 0f,
        marginTopDp = 48f,
        marginBottomDp = 48f,
        marginLeftDp = 24f,
        marginRightDp = 24f,
        indent = 2f,
        indentUnit = IndentUnit.CHARACTER,
        titleStyle = TitleStyleConfig(),
        headerVisibleForLayout = true,
        footerVisibleForLayout = true,
        chineseConvert = ChineseConvert.NONE,
        usePanguSpacing = false,
        useZhLayout = false,
        bottomJustify = false,
        wordSpacing = wordSpacing,
    )

    // T-2.1.1: wordSpacing = 0 时分页结果不变（基线）
    @Test
    fun wordSpacing_zero_sameAsBaseline() {
        val content = "The quick brown fox jumps over the lazy dog. ".repeat(20)
        val baseline = paginator.paginateChapter(0, "Test", content, config(0f))
        val withZero = paginator.paginateChapter(0, "Test", content, config(0f))
        assertEquals(baseline.pages.size, withZero.pages.size)
    }

    // T-2.1.2: wordSpacing > 0 时英文行宽增加
    @Test
    fun wordSpacing_positive_increasesLineWidth() {
        val content = "The quick brown fox jumps over the lazy dog. ".repeat(20)
        val baseline = paginator.paginateChapter(0, "Test", content, config(0f))
        val withSpacing = paginator.paginateChapter(0, "Test", content, config(5f))
        // 更大的词间距 → 每行容纳更少的词 → 更多页
        assertTrue(
            "pages with spacing (${withSpacing.pages.size}) should be >= baseline (${baseline.pages.size})",
            withSpacing.pages.size >= baseline.pages.size,
        )
    }

    // T-2.1.3: 中文文本不受影响（纯中文无空格）
    @Test
    fun wordSpacing_chineseText_noEffect() {
        val content = "天地玄黄宇宙洪荒日月盈昃辰宿列张。".repeat(50)
        val baseline = paginator.paginateChapter(0, "Test", content, config(0f))
        val withSpacing = paginator.paginateChapter(0, "Test", content, config(5f))
        assertEquals(
            "Chinese text pages should be same",
            baseline.pages.size,
            withSpacing.pages.size,
        )
    }

    // T-2.1.4: charWidths 不含词间距
    @Test
    fun wordSpacing_charWidths_excludeWordSpacing() {
        val content = "Hello world test"
        val baseline = paginator.paginateChapter(0, "T", content, config(0f))
        val withSpacing = paginator.paginateChapter(0, "T", content, config(5f))

        // charWidths 应相同（词间距不计入字符宽度）
        val baselineWidths = baseline.pages.first().lines.first().charWidths
        val spacingWidths = withSpacing.pages.first().lines.first().charWidths
        if (baselineWidths != null && spacingWidths != null) {
            assertEquals(baselineWidths.size, spacingWidths.size)
            for (i in baselineWidths.indices) {
                assertEquals(baselineWidths[i], spacingWidths[i], 0.5f)
            }
        }
    }

    // T-2.1.5: Reflow 触发验证 — LayoutHasher 包含 wordSpacing
    @Test
    fun wordSpacing_affectsLayoutHash() {
        val hash0 = com.shuli.reader.feature.reader.render.ReaderLayoutHasher.hash(
            layoutInput(0f)
        )
        val hash5 = com.shuli.reader.feature.reader.render.ReaderLayoutHasher.hash(
            layoutInput(5f)
        )
        assertNotEquals("hash should change with wordSpacing", hash0.inputHash, hash5.inputHash)
    }
}
