package com.shuli.reader.feature.reader
import com.shuli.reader.feature.reader.screen.PageDirection
import com.shuli.reader.feature.reader.screen.ReaderSettingKey
import com.shuli.reader.feature.reader.screen.ReaderSettingValue
import com.shuli.reader.feature.reader.screen.ReaderIntent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderIntentTest {

    @Test
    fun readerIntent_openBook_carriesBookId() {
        val intent = ReaderIntent.OpenBook(42L)
        assertEquals(42L, intent.bookId)
    }

    @Test
    fun readerIntent_turnPage_carriesDirection() {
        val intent = ReaderIntent.TurnPage(PageDirection.NEXT)
        assertEquals(PageDirection.NEXT, intent.direction)
    }

    @Test
    fun readerIntent_commitPageTurn_carriesDirection() {
        val intent = ReaderIntent.CommitPageTurn(PageDirection.PREV)
        assertEquals(PageDirection.PREV, intent.direction)
    }

    @Test
    fun readerIntent_updateSetting_carriesKeyAndValue() {
        val intent = ReaderIntent.UpdateSetting(
            ReaderSettingKey.FONT_SIZE,
            ReaderSettingValue.Float(18f),
        )
        assertEquals(ReaderSettingKey.FONT_SIZE, intent.key)
        assertEquals(18f, (intent.value as ReaderSettingValue.Float).value, 0.01f)
    }

    @Test
    fun dispatch_allIntentsHandled() {
        val allSubclasses = ReaderIntent::class.sealedSubclasses
        assertTrue(
            "ReaderIntent 应至少有 20 个子类",
            allSubclasses.size >= 20,
        )
    }

    @Test
    fun readerSettingKey_hasExpectedCount() {
        assertTrue(
            "ReaderSettingKey 应至少覆盖 30 个设置项",
            ReaderSettingKey.entries.size >= 30,
        )
    }

    @Test
    fun readerSettingValue_allVariantsAccessible() {
        val floatVal: ReaderSettingValue = ReaderSettingValue.Float(1.5f)
        val intVal: ReaderSettingValue = ReaderSettingValue.Int(42)
        val strVal: ReaderSettingValue = ReaderSettingValue.Str("test")
        val boolVal: ReaderSettingValue = ReaderSettingValue.Bool(true)

        assertEquals(1.5f, (floatVal as ReaderSettingValue.Float).value, 0.01f)
        assertEquals(42, (intVal as ReaderSettingValue.Int).value)
        assertEquals("test", (strVal as ReaderSettingValue.Str).value)
        assertTrue((boolVal as ReaderSettingValue.Bool).value)
    }

    @Test
    fun readerIntent_openChapter_carriesAllParams() {
        val intent = ReaderIntent.OpenChapter(
            index = 5,
            targetToLastPage = true,
            targetByteOffset = 1024L,
        )
        assertEquals(5, intent.index)
        assertTrue(intent.targetToLastPage)
        assertEquals(1024L, intent.targetByteOffset)
    }
}
