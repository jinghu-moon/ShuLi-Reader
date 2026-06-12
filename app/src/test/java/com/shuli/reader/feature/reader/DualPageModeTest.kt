package com.shuli.reader.feature.reader

import com.shuli.reader.core.data.DualPageMode
import com.shuli.reader.feature.reader.settings.ReaderSettingRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DualPageModeTest {

    // T-3.7.1: DualPageMode 枚举含三个值
    @Test
    fun dualPageMode_hasThreeValues() {
        assertEquals(3, DualPageMode.entries.size)
        assertTrue(DualPageMode.entries.contains(DualPageMode.AUTO))
        assertTrue(DualPageMode.entries.contains(DualPageMode.SINGLE))
        assertTrue(DualPageMode.entries.contains(DualPageMode.DUAL))
    }

    // T-3.7.2: AUTO 模式横屏 + 宽屏时启用双页
    @Test
    fun autoMode_landscape_wideScreen_isDual() {
        val isDual = DualPageResolver.resolve(
            mode = DualPageMode.AUTO,
            isLandscape = true,
            widthPx = 2400,
            density = 2.75f,
        )
        assertTrue("AUTO + landscape + wide should be dual", isDual)
    }

    @Test
    fun autoMode_landscape_narrowScreen_isSingle() {
        val isDual = DualPageResolver.resolve(
            mode = DualPageMode.AUTO,
            isLandscape = true,
            widthPx = 1000,
            density = 2.75f,
        )
        assertFalse("AUTO + landscape + narrow should be single", isDual)
    }

    @Test
    fun autoMode_portrait_isSingle() {
        val isDual = DualPageResolver.resolve(
            mode = DualPageMode.AUTO,
            isLandscape = false,
            widthPx = 2400,
            density = 2.75f,
        )
        assertFalse("AUTO + portrait should be single", isDual)
    }

    // T-3.7.3: SINGLE 模式始终单页
    @Test
    fun singleMode_alwaysSingle() {
        val isDual = DualPageResolver.resolve(
            mode = DualPageMode.SINGLE,
            isLandscape = true,
            widthPx = 2400,
            density = 2.75f,
        )
        assertFalse("SINGLE should always be single", isDual)
    }

    // T-3.7.4: DUAL 模式始终双页
    @Test
    fun dualMode_alwaysDual() {
        val isDual = DualPageResolver.resolve(
            mode = DualPageMode.DUAL,
            isLandscape = false,
            widthPx = 1000,
            density = 2.75f,
        )
        assertTrue("DUAL should always be dual", isDual)
    }

    // T-3.7.5: 双页模式翻两页
    @Test
    fun dualMode_nextPage_skipsTwo() {
        var pageIndex = 0
        val resolver = DualPageNavigation(
            isDual = true,
            onPageChange = { pageIndex = it },
        )
        resolver.nextPage(10) // 当前 0，总 10 页
        assertEquals(2, pageIndex)
    }

    @Test
    fun singleMode_nextPage_skipsOne() {
        var pageIndex = 0
        val resolver = DualPageNavigation(
            isDual = false,
            onPageChange = { pageIndex = it },
        )
        resolver.nextPage(10)
        assertEquals(1, pageIndex)
    }

    // Registry 注册
    @Test
    fun registry_dualPageMode_defaultIsAuto() {
        val default = ReaderSettingRegistry.getDefault<DualPageMode>("dual_page_mode")
        assertEquals(DualPageMode.AUTO, default)
    }
}

/**
 * 双页模式解析器。
 */
object DualPageResolver {
    /** 宽屏阈值：800dp 转为像素 */
    private const val WIDE_SCREEN_THRESHOLD_DP = 800

    fun resolve(
        mode: DualPageMode,
        isLandscape: Boolean,
        widthPx: Int,
        density: Float,
    ): Boolean = when (mode) {
        DualPageMode.SINGLE -> false
        DualPageMode.DUAL -> true
        DualPageMode.AUTO -> isLandscape && widthPx > WIDE_SCREEN_THRESHOLD_DP * density
    }
}

/**
 * 双页模式翻页导航。
 */
class DualPageNavigation(
    private val isDual: Boolean,
    private val onPageChange: (Int) -> Unit,
) {
    fun nextPage(totalPages: Int) {
        val step = if (isDual) 2 else 1
        onPageChange(step.coerceAtMost(totalPages))
    }

    fun prevPage() {
        val step = if (isDual) 2 else 1
        onPageChange(-step)
    }
}
