package com.shuli.reader.core.data

import com.shuli.reader.core.reader.FooterConfig
import com.shuli.reader.core.reader.HeaderConfig
import com.shuli.reader.core.reader.HeaderVisibility
import com.shuli.reader.core.reader.SlotContent
import com.shuli.reader.core.reader.TitleAlign
import com.shuli.reader.core.reader.TitleStyleConfig
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PresetSerializerTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun defaultPreferences_roundTrip_noFieldLost() {
        val original = ReaderPreferences()
        val encoded = json.encodeToString(ReaderPreferences.serializer(), original)
        val decoded = json.decodeFromString(ReaderPreferences.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun customPreferences_roundTrip_preservesAllFields() {
        val original = ReaderPreferences(
            fontSize = 22f,
            lineSpacing = 2.0f,
            paragraphSpacing = 1.5f,
            indent = 3f,
            pageAnimType = PageAnimType.SIMULATION,
            backgroundColor = ReaderTheme.DARK,
            marginHorizontal = 32f,
            marginVertical = 60f,
            brightness = 0.5f,
            readingFont = "serif",
            letterSpacing = 0.05f,
            fontWeight = ReaderFontWeight.BOLD,
            textAlign = ReaderTextAlign.JUSTIFY,
            chineseConvert = ChineseConvert.TRADITIONAL,
            titleStyle = TitleStyleConfig(
                align = TitleAlign.LEFT,
                sizeOffsetSp = 6,
                marginTopDp = 12f,
                marginBottomDp = 80f,
            ),
            header = HeaderConfig(
                visibility = HeaderVisibility.ALWAYS_SHOW,
                left = SlotContent.BOOK_TITLE,
                center = SlotContent.CHAPTER_TITLE,
                right = SlotContent.PAGE_NUMBER,
            ),
            footer = FooterConfig(
                visibility = HeaderVisibility.ALWAYS_HIDE,
                left = SlotContent.NONE,
                center = SlotContent.PROGRESS,
                right = SlotContent.TIME,
            ),
            headerFooterAlpha = 0.6f,
            showProgress = false,
            keepScreenOn = true,
            volumeKeyTurnPage = true,
            edgeTurnPage = false,
        )

        val encoded = json.encodeToString(ReaderPreferences.serializer(), original)
        val decoded = json.decodeFromString(ReaderPreferences.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun decodeJson_missingNewFields_usesDefaults() {
        // 模拟旧版本 JSON（不含阶段五/六字段）
        val oldJson = """{
            "fontSize":18.0,
            "lineSpacing":1.5,
            "pageAnimType":"HORIZONTAL",
            "backgroundColor":"PAPER"
        }"""

        val decoded = json.decodeFromString(ReaderPreferences.serializer(), oldJson)

        assertEquals(18f, decoded.fontSize, 0.01f)
        // 新字段应使用默认值
        assertEquals(HeaderVisibility.HIDE_WHEN_STATUS_BAR, decoded.header.visibility)
        assertEquals(HeaderVisibility.ALWAYS_SHOW, decoded.footer.visibility)
        assertEquals(0.4f, decoded.headerFooterAlpha, 0.01f)
        assertEquals(true, decoded.showProgress)
        assertEquals(false, decoded.keepScreenOn)
        assertEquals(false, decoded.volumeKeyTurnPage)
        assertEquals(true, decoded.edgeTurnPage)
        assertEquals(TitleStyleConfig(), decoded.titleStyle)
    }

    @Test
    fun decodeJson_extraUnknownFields_ignored() {
        val jsonWithExtra = """{
            "fontSize":16.0,
            "lineSpacing":1.5,
            "unknownField":"should be ignored",
            "anotherUnknown":42
        }"""

        val decoded = json.decodeFromString(ReaderPreferences.serializer(), jsonWithExtra)
        assertEquals(16f, decoded.fontSize, 0.01f)
    }

    @Test
    fun headerConfig_roundTrip() {
        val original = HeaderConfig(
            visibility = HeaderVisibility.ALWAYS_SHOW,
            left = SlotContent.BOOK_TITLE,
            center = SlotContent.CHAPTER_TITLE,
            right = SlotContent.DATE,
        )
        val encoded = json.encodeToString(HeaderConfig.serializer(), original)
        val decoded = json.decodeFromString(HeaderConfig.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun footerConfig_roundTrip() {
        val original = FooterConfig(
            visibility = HeaderVisibility.ALWAYS_HIDE,
            left = SlotContent.NONE,
            center = SlotContent.PAGE_NUMBER,
            right = SlotContent.BATTERY,
        )
        val encoded = json.encodeToString(FooterConfig.serializer(), original)
        val decoded = json.decodeFromString(FooterConfig.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun titleStyleConfig_roundTrip() {
        val original = TitleStyleConfig(
            align = TitleAlign.CENTER,
            sizeOffsetSp = 4,
            marginTopDp = 9f,
            marginBottomDp = 60f,
        )
        val encoded = json.encodeToString(TitleStyleConfig.serializer(), original)
        val decoded = json.decodeFromString(TitleStyleConfig.serializer(), encoded)
        assertEquals(original, decoded)
    }
}
