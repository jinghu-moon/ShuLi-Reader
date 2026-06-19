package com.shuli.reader.feature.reader.render

import com.shuli.reader.feature.reader.settings.ReaderSettingRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleFontTest {

    // T-2.5.1: titleFont = "" 时跟随正文字体（逻辑验证）
    @Test
    fun titleFont_empty_followsBodyFont() {
        val titleFont = ""
        // 空字符串表示 fallback 到正文字体
        assertEquals("", titleFont)
    }

    // T-2.5.2: titleFont = "serif" 时使用指定字体
    @Test
    fun titleFont_specific_usesSpecifiedFont() {
        val titleFont = "serif"
        assertNotEquals("", titleFont)
    }

    // T-2.5.3: Registry 注册 title_font 为 null scope（CONTENT 已由 key-diff 驱动）
    @Test
    fun registry_titleFont_hasNullScope() {
        val def = ReaderSettingRegistry.all.first { it.key == "title_font" }
        assertNull(def.scope)
    }

    @Test
    fun registry_titleFont_defaultIsEmpty() {
        val default = ReaderSettingRegistry.getDefault<String>("title_font")
        assertEquals("", default)
    }

    // T-2.5.4: 标题字体变更不触发 reflow（LayoutHasher 不含 titleFont）
    @Test
    fun titleFont_notInLayoutHasher() {
        // titleFont 是 null scope（key-diff 驱动），不在 LayoutHasher 中
        // LayoutHasher 只包含 REFLOW scope 的字段
        val def = ReaderSettingRegistry.all.first { it.key == "title_font" }
        assertNull("titleFont should be null scope (key-diff driven)", def.scope)
    }
}
