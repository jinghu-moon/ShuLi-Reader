package com.shuli.reader.core.reader.animation

import com.shuli.reader.core.data.PageAnimSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageAnimSpeedTest {

    // T-1.7.1: PageAnimSpeed 枚举含三个值，durationMs 正确
    @Test
    fun pageAnimSpeed_hasThreeValues() {
        assertEquals(100, PageAnimSpeed.FAST.durationMs)
        assertEquals(250, PageAnimSpeed.NORMAL.durationMs)
        assertEquals(400, PageAnimSpeed.SLOW.durationMs)
    }

    @Test
    fun pageAnimSpeed_fromDurationMs_knownValues() {
        assertEquals(PageAnimSpeed.FAST, PageAnimSpeed.fromDurationMs(100))
        assertEquals(PageAnimSpeed.NORMAL, PageAnimSpeed.fromDurationMs(250))
        assertEquals(PageAnimSpeed.SLOW, PageAnimSpeed.fromDurationMs(400))
    }

    @Test
    fun pageAnimSpeed_fromDurationMs_unknownFallsBackToNormal() {
        assertEquals(PageAnimSpeed.NORMAL, PageAnimSpeed.fromDurationMs(999))
    }

    // T-1.7.2: PageDelegateFactory 使用 speed 参数
    @Test
    fun factory_create_withSpeedParam_succeeds() {
        val delegate = PageDelegateFactory.create(
            PageDelegateFactory.PageAnimType.HORIZONTAL,
            PageAnimSpeed.FAST,
        )
        assertTrue(delegate is HorizontalPageDelegate)
    }

    @Test
    fun factory_create_noAnimType_ignoresSpeed() {
        val delegate = PageDelegateFactory.create(
            PageDelegateFactory.PageAnimType.NONE,
            PageAnimSpeed.SLOW,
        )
        assertTrue(delegate is NoAnimPageDelegate)
    }

    // T-1.7.3: AnimSpec 缓存验证
    @Test
    fun createAnimSpec_fast_returnsCorrectDuration() {
        val spec = AnimSpecCache.create(PageAnimSpeed.FAST)
        assertEquals(100L, spec.durationMs)
    }

    @Test
    fun createAnimSpec_normal_returnsCorrectDuration() {
        val spec = AnimSpecCache.create(PageAnimSpeed.NORMAL)
        assertEquals(250L, spec.durationMs)
    }

    @Test
    fun createAnimSpec_slow_returnsCorrectDuration() {
        val spec = AnimSpecCache.create(PageAnimSpeed.SLOW)
        assertEquals(400L, spec.durationMs)
    }

    // T-1.7.4: Registry 注册 page_anim_speed 为 PAGE_DELEGATE scope
    @Test
    fun registry_pageAnimSpeed_hasPageDelegateScope() {
        val def = com.shuli.reader.feature.reader.settings.ReaderSettingRegistry.all
            .first { it.key == "page_anim_speed" }
        assertEquals(
            com.shuli.reader.feature.reader.render.InvalidationScope.PAGE_DELEGATE,
            def.scope,
        )
    }

    @Test
    fun registry_pageAnimSpeed_defaultIs250() {
        val default = com.shuli.reader.feature.reader.settings.ReaderSettingRegistry
            .getDefault<Int>("page_anim_speed")
        assertEquals(250, default)
    }

    @Test
    fun registry_pageAnimSpeed_notInPreset() {
        val def = com.shuli.reader.feature.reader.settings.ReaderSettingRegistry.all
            .first { it.key == "page_anim_speed" }
        assertEquals(false, def.includeInPreset)
    }
}
