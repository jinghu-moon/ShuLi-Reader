package com.shuli.reader.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shuli.reader.core.database.ShuLiDatabase
import com.shuli.reader.core.database.entity.BookEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ReadingPositionTest {

    private lateinit var database: ShuLiDatabase

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = TestDatabaseFactory.create(context)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun bookEntityShouldHaveChapterPositionFields() = runTest {
        val bookDao = database.bookDao()
        val book = BookEntity(
            title = "位置测试",
            author = "作者",
            filePath = "/test/pos.txt",
            fileType = "TXT",
            fileSize = 1024L,
            coverPath = null,
            lastReadTime = null,
            addedTime = System.currentTimeMillis(),
            readingProgress = 0.5f,
            durByteOffset = 1500,
            durChapterTitle = "第四章 学艺",
            totalChapterNum = 10,
        )
        val id = bookDao.insertBook(book)
        val retrieved = bookDao.getBookById(id).first()!!

        assertEquals(1500L, retrieved.durByteOffset)
        assertEquals("第四章 学艺", retrieved.durChapterTitle)
        assertEquals(10, retrieved.totalChapterNum)
    }

    @Test
    fun defaultChapterPositionShouldBeZero() = runTest {
        val bookDao = database.bookDao()
        val book = BookEntity(
            title = "默认值测试",
            author = null,
            filePath = "/test/default.txt",
            fileType = "TXT",
            fileSize = 512L,
            coverPath = null,
            lastReadTime = null,
            addedTime = System.currentTimeMillis(),
        )
        val id = bookDao.insertBook(book)
        val retrieved = bookDao.getBookById(id).first()!!

        assertEquals(0L, retrieved.durByteOffset)
        assertEquals(null, retrieved.durChapterTitle)
        assertEquals(0, retrieved.totalChapterNum)
    }
}
