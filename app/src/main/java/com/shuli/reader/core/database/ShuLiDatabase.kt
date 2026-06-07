package com.shuli.reader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shuli.reader.core.database.dao.BookChapterDao
import com.shuli.reader.core.database.dao.BookDao
import com.shuli.reader.core.database.dao.BookmarkDao
import com.shuli.reader.core.database.dao.NoteDao
import com.shuli.reader.core.database.dao.ReadingHistoryDao
import com.shuli.reader.core.database.dao.ReadingProgressDao
import com.shuli.reader.core.database.dao.ReaderPresetDao
import com.shuli.reader.core.database.dao.TagDao
import com.shuli.reader.core.database.dao.TagSuggestionDecisionDao
import com.shuli.reader.core.database.entity.BookChapterEntity
import com.shuli.reader.core.database.entity.BookContentIndexEntity
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.BookFtsEntity
import com.shuli.reader.core.database.entity.BookTagCrossRef
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.database.entity.NoteEntity
import com.shuli.reader.core.database.entity.ReaderPresetEntity
import com.shuli.reader.core.database.entity.ReadingHistoryEntity
import com.shuli.reader.core.database.entity.ReadingProgressEntity
import com.shuli.reader.core.database.entity.TagEntity
import com.shuli.reader.core.database.entity.TagSuggestionDecisionEntity

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
        TagEntity::class,
        BookTagCrossRef::class,
        ReadingHistoryEntity::class,
        TagSuggestionDecisionEntity::class,
    ],
    version = 20,
    exportSchema = true,
)
abstract class ShuLiDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookChapterDao(): BookChapterDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun noteDao(): NoteDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun readerPresetDao(): ReaderPresetDao
    abstract fun tagDao(): TagDao
    abstract fun readingHistoryDao(): ReadingHistoryDao
    abstract fun tagSuggestionDecisionDao(): TagSuggestionDecisionDao

    companion object {
        const val DATABASE_NAME = "shuli_database"

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE books ADD COLUMN readingStatus TEXT NOT NULL DEFAULT 'WANT_TO_READ'"
                )
                database.execSQL(
                    "ALTER TABLE books ADD COLUMN readCount INTEGER NOT NULL DEFAULT 1"
                )
                database.execSQL(
                    "UPDATE books SET readingStatus = 'READING' WHERE readingProgress > 0 AND readingProgress < 0.99"
                )
                database.execSQL(
                    "UPDATE books SET readingStatus = 'FINISHED' WHERE readingProgress >= 0.99"
                )
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE tags (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        color_index INTEGER NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE UNIQUE INDEX index_tags_name ON tags(name)")

                database.execSQL("""
                    CREATE TABLE book_tag_cross_ref (
                        book_id INTEGER NOT NULL,
                        tag_id INTEGER NOT NULL,
                        added_at INTEGER NOT NULL,
                        PRIMARY KEY (book_id, tag_id),
                        FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
                        FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX index_book_tag_cross_ref_book_id ON book_tag_cross_ref(book_id)")
                database.execSQL("CREATE INDEX index_book_tag_cross_ref_tag_id ON book_tag_cross_ref(tag_id)")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE reading_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        book_id INTEGER NOT NULL,
                        read_count INTEGER NOT NULL,
                        finished_at INTEGER NOT NULL,
                        reading_progress REAL NOT NULL DEFAULT 1.0,
                        reading_duration_minutes INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX index_reading_history_book_id ON reading_history(book_id)")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE tag_suggestion_decision (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        book_id INTEGER NOT NULL,
                        tag_name TEXT NOT NULL,
                        accepted INTEGER NOT NULL,
                        decided_at INTEGER NOT NULL,
                        FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX index_tag_suggestion_decision_book_id ON tag_suggestion_decision(book_id)")
                database.execSQL("CREATE UNIQUE INDEX index_tag_suggestion_decision_book_tag ON tag_suggestion_decision(book_id, tag_name)")
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_16_17,
            MIGRATION_17_18,
            MIGRATION_18_19,
            MIGRATION_19_20,
        )
    }
}
