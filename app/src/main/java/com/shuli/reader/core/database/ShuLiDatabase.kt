package com.shuli.reader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shuli.reader.core.database.dao.BookChapterDao
import com.shuli.reader.core.database.dao.BookDao
import com.shuli.reader.core.database.dao.BookmarkDao
import com.shuli.reader.core.database.dao.NoteDao
import com.shuli.reader.core.database.dao.ReadingProgressDao
import com.shuli.reader.core.database.dao.ReaderPresetDao
import com.shuli.reader.core.database.entity.BookChapterEntity
import com.shuli.reader.core.database.entity.BookContentIndexEntity
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.BookFtsEntity
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.database.entity.NoteEntity
import com.shuli.reader.core.database.entity.ReaderPresetEntity
import com.shuli.reader.core.database.entity.ReadingProgressEntity

@Database(
    entities = [
        BookEntity::class,
        BookFtsEntity::class,
        BookContentIndexEntity::class,
        BookmarkEntity::class,
        NoteEntity::class,
        ReadingProgressEntity::class,
        ReaderPresetEntity::class,
        com.shuli.reader.core.database.entity.FolderEntity::class,
        BookChapterEntity::class,
    ],
    version = 13,
    exportSchema = true,
)
abstract class ShuLiDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookChapterDao(): BookChapterDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun noteDao(): NoteDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun readerPresetDao(): ReaderPresetDao

    companion object {
        const val DATABASE_NAME = "shuli_database"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN durChapterIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN durChapterPos INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN durChapterTitle TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN durChapterTime INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN totalChapterNum INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // BookmarkEntity 新增章节定位字段
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN chapterIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN chapterPos INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN chapterName TEXT")
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN selectedText TEXT")
                // NoteEntity 新增章节定位字段
                db.execSQL("ALTER TABLE notes ADD COLUMN chapterIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN chapterStartPos INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN chapterEndPos INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // BookEntity 新增收藏字段
                db.execSQL("ALTER TABLE books ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 创建 FTS4 虚拟表用于书籍元数据全文搜索
                db.execSQL("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS books_fts
                    USING fts4(title, author, content=`books`)
                """.trimIndent())
                // 填充已有数据
                db.execSQL("""
                    INSERT INTO books_fts(rowid, title, author)
                    SELECT id, title, author FROM books
                """.trimIndent())
                // 创建同步触发器：插入
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS books_ai AFTER INSERT ON books BEGIN
                        INSERT INTO books_fts(rowid, title, author)
                        VALUES (new.id, new.title, new.author);
                    END
                """.trimIndent())
                // 创建同步触发器：删除
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS books_ad AFTER DELETE ON books BEGIN
                        INSERT INTO books_fts(books_fts, rowid, title, author)
                        VALUES ('delete', old.id, old.title, old.author);
                    END
                """.trimIndent())
                // 创建同步触发器：更新
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS books_au AFTER UPDATE ON books BEGIN
                        INSERT INTO books_fts(books_fts, rowid, title, author)
                        VALUES ('delete', old.id, old.title, old.author);
                        INSERT INTO books_fts(rowid, title, author)
                        VALUES (new.id, new.title, new.author);
                    END
                """.trimIndent())
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS book_content_index (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        bookId INTEGER NOT NULL,
                        chapterIndex INTEGER NOT NULL,
                        chapterTitle TEXT NOT NULL,
                        chapterStart INTEGER NOT NULL,
                        content TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_book_content_index_bookId ON book_content_index(bookId)")
                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_book_content_index_bookId_chapterIndex
                    ON book_content_index(bookId, chapterIndex)
                """.trimIndent())
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // BookEntity 新增用户自定义封面色盘索引，null 表示走自动散列
                db.execSQL("ALTER TABLE books ADD COLUMN customCoverPaletteIndex INTEGER")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS reader_preset (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        configJson TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 创建文件夹表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS folders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        orderIndex INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                // books 表增加分组和排序字段
                db.execSQL("ALTER TABLE books ADD COLUMN folderId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE books ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 创建章节目录表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS book_chapters (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        bookId INTEGER NOT NULL,
                        chapterIndex INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        spineIndex INTEGER NOT NULL DEFAULT -1,
                        byteStart INTEGER NOT NULL DEFAULT 0,
                        byteEnd INTEGER NOT NULL DEFAULT 0,
                        charset TEXT NOT NULL DEFAULT 'UTF-8',
                        wordCount INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS index_book_chapters_bookId_chapterIndex
                    ON book_chapters(bookId, chapterIndex)
                """.trimIndent())
                // books 表增加章节索引指纹字段
                db.execSQL("ALTER TABLE books ADD COLUMN chapterIndexFileSize INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN chapterIndexLastModified INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN chapterIndexBuiltAt INTEGER NOT NULL DEFAULT 0")
            }
        }

    }
}
