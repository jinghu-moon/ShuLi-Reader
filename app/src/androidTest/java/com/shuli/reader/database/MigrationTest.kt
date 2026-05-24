package com.shuli.reader.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shuli.reader.core.database.ShuLiDatabase
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ShuLiDatabase::class.java,
    )

    private lateinit var db: SupportSQLiteDatabase

    @After
    @Throws(IOException::class)
    fun teardown() {
        if (::db.isInitialized) db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate1To2_shouldAddChapterPositionColumns() {
        // 创建 v1 数据库并插入一条记录
        helper.createDatabase(ShuLiDatabase.DATABASE_NAME, 1).use { db ->
            db.execSQL("""
                INSERT INTO books (title, author, filePath, fileType, fileSize, coverPath, lastReadTime, addedTime, readingProgress)
                VALUES ('测试书', '作者', '/test.txt', 'TXT', 1024, NULL, NULL, ${System.currentTimeMillis()}, 0.5)
            """.trimIndent())
        }

        // 执行迁移并验证
        db = helper.runMigrationsAndValidate(
            ShuLiDatabase.DATABASE_NAME,
            2,
            true,
            ShuLiDatabase.MIGRATION_1_2,
        )

        // 验证新字段默认值
        val cursor = db.query(SimpleSQLiteQuery("SELECT durChapterIndex, durChapterPos, durChapterTitle, durChapterTime, totalChapterNum FROM books WHERE title = '测试书'"))
        cursor.moveToFirst()
        val chapterIndex = cursor.getInt(0)
        val chapterPos = cursor.getInt(1)
        val chapterTitle = cursor.getString(2)
        val chapterTime = cursor.getLong(3)
        val totalChapters = cursor.getInt(4)
        cursor.close()

        assert(chapterIndex == 0) { "durChapterIndex should be 0, got $chapterIndex" }
        assert(chapterPos == 0) { "durChapterPos should be 0, got $chapterPos" }
        assert(chapterTitle == null) { "durChapterTitle should be null, got $chapterTitle" }
        assert(chapterTime == 0L) { "durChapterTime should be 0, got $chapterTime" }
        assert(totalChapters == 0) { "totalChapterNum should be 0, got $totalChapters" }
    }

    @Test
    @Throws(IOException::class)
    fun migrate4To5_shouldCreateFtsTable() {
        // 创建 v4 数据库并插入记录
        helper.createDatabase(ShuLiDatabase.DATABASE_NAME, 4).use { db ->
            db.execSQL("""
                INSERT INTO books (title, author, filePath, fileType, fileSize, addedTime, readingProgress, isFavorite)
                VALUES ('三体', '刘慈欣', '/test.txt', 'TXT', 1024, ${System.currentTimeMillis()}, 0.0, 0)
            """.trimIndent())
        }

        // 执行迁移
        db = helper.runMigrationsAndValidate(
            ShuLiDatabase.DATABASE_NAME,
            5,
            true,
            ShuLiDatabase.MIGRATION_1_2,
            ShuLiDatabase.MIGRATION_2_3,
            ShuLiDatabase.MIGRATION_3_4,
            ShuLiDatabase.MIGRATION_4_5,
        )

        // 验证 FTS 表存在且可查询
        val cursor = db.query(SimpleSQLiteQuery("SELECT title, author FROM books_fts WHERE books_fts MATCH '三*'"))
        assert(cursor.count == 1) { "FTS should find 1 result, got ${cursor.count}" }
        cursor.moveToFirst()
        val title = cursor.getString(0)
        val author = cursor.getString(1)
        cursor.close()

        assert(title == "三体") { "title should be '三体', got $title" }
        assert(author == "刘慈欣") { "author should be '刘慈欣', got $author" }
    }

    @Test
    @Throws(IOException::class)
    fun migrate5To6_shouldCreateContentIndexTable() {
        helper.createDatabase(ShuLiDatabase.DATABASE_NAME, 5).use { db ->
            db.execSQL("""
                INSERT INTO books (title, author, filePath, fileType, fileSize, addedTime, readingProgress, isFavorite)
                VALUES ('索引测试', '作者', '/index.txt', 'TXT', 1024, ${System.currentTimeMillis()}, 0.0, 0)
            """.trimIndent())
        }

        db = helper.runMigrationsAndValidate(
            ShuLiDatabase.DATABASE_NAME,
            6,
            true,
            ShuLiDatabase.MIGRATION_5_6,
        )

        db.execSQL("""
            INSERT INTO book_content_index (bookId, chapterIndex, chapterTitle, chapterStart, content)
            VALUES (1, 0, 'Chapter 1', 0, 'Searchable content')
        """.trimIndent())

        val cursor = db.query(SimpleSQLiteQuery("SELECT content FROM book_content_index WHERE bookId = 1"))
        assert(cursor.count == 1) { "content index should contain 1 row, got ${cursor.count}" }
        cursor.close()
    }
}
