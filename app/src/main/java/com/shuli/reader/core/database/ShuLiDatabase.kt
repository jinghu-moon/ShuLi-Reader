package com.shuli.reader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shuli.reader.core.database.dao.BookChapterDao
import com.shuli.reader.core.database.dao.BookDao
import com.shuli.reader.core.database.dao.BookmarkDao
import com.shuli.reader.core.database.dao.BookReaderPrefsDao
import com.shuli.reader.core.database.dao.ChapterReadingStatsDao
import com.shuli.reader.core.database.dao.DictHistoryDao
import com.shuli.reader.core.database.dao.DictMetaDao
import com.shuli.reader.core.database.dao.EditDeltaDao
import com.shuli.reader.core.database.dao.NoteDao
import com.shuli.reader.core.database.dao.ReadingHistoryDao
import com.shuli.reader.core.database.dao.ReadingProgressDao
import com.shuli.reader.core.database.dao.ReadingSessionDao
import com.shuli.reader.core.database.dao.ReaderPresetDao
import com.shuli.reader.core.database.dao.TagDao
import com.shuli.reader.core.database.dao.TagSuggestionDecisionDao
import com.shuli.reader.core.database.dao.WordBookDao
import com.shuli.reader.core.database.entity.BookChapterEntity
import com.shuli.reader.core.database.entity.BookContentIndexEntity
import com.shuli.reader.core.database.entity.ChapterReadingStatsEntity
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.BookFtsEntity
import com.shuli.reader.core.database.entity.BookReaderPrefsEntity
import com.shuli.reader.core.database.entity.BookTagCrossRef
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.database.entity.DictHistoryEntity
import com.shuli.reader.core.database.entity.DictMetaEntity
import com.shuli.reader.core.database.entity.EditDeltaEntity
import com.shuli.reader.core.database.entity.NoteEntity
import com.shuli.reader.core.database.entity.ReaderPresetEntity
import com.shuli.reader.core.database.entity.ReadingHistoryEntity
import com.shuli.reader.core.database.entity.ReadingProgressEntity
import com.shuli.reader.core.database.entity.ReadingSessionEntity
import com.shuli.reader.core.database.entity.TagEntity
import com.shuli.reader.core.database.entity.TagSuggestionDecisionEntity
import com.shuli.reader.core.database.entity.WordBookEntity

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
        BookReaderPrefsEntity::class,
        ChapterReadingStatsEntity::class,
        ReadingSessionEntity::class,
        DictMetaEntity::class,
        DictHistoryEntity::class,
        WordBookEntity::class,
        EditDeltaEntity::class,
    ],
    version = 27,
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
    abstract fun bookReaderPrefsDao(): BookReaderPrefsDao
    abstract fun chapterReadingStatsDao(): ChapterReadingStatsDao
    abstract fun readingSessionDao(): ReadingSessionDao
    abstract fun dictMetaDao(): DictMetaDao
    abstract fun dictHistoryDao(): DictHistoryDao
    abstract fun wordBookDao(): WordBookDao
    abstract fun editDeltaDao(): EditDeltaDao

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

        /** §11.1.1.1: SnapshotDigest — 新增 chapterIndex / themeBackgroundColor */
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE reading_progress ADD COLUMN chapterIndex INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE reading_progress ADD COLUMN themeBackgroundColor INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /** 本书级偏好覆盖表 */
        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE book_reader_prefs (
                        book_id INTEGER NOT NULL PRIMARY KEY,
                        config_json TEXT NOT NULL,
                        updated_at INTEGER NOT NULL DEFAULT 0
                    )
                """)
            }
        }

        /** 章节阅读统计表 */
        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE chapter_reading_stats (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        book_id INTEGER NOT NULL,
                        chapter_index INTEGER NOT NULL,
                        visited INTEGER NOT NULL DEFAULT 0,
                        read_time_seconds INTEGER NOT NULL DEFAULT 0,
                        first_visited_at INTEGER NOT NULL DEFAULT 0,
                        last_visited_at INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE UNIQUE INDEX index_chapter_reading_stats_book_chapter ON chapter_reading_stats(book_id, chapter_index)")
                database.execSQL("CREATE INDEX index_chapter_reading_stats_book_id ON chapter_reading_stats(book_id)")
            }
        }

        /** v24: reading_session 表 + 旧表列清理 */
        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. 创建 reading_session 表
                database.execSQL("""
                    CREATE TABLE reading_session (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        book_id INTEGER NOT NULL,
                        chapter_index INTEGER NOT NULL,
                        started_at INTEGER NOT NULL,
                        ended_at INTEGER NOT NULL,
                        duration_seconds INTEGER NOT NULL,
                        date_key INTEGER NOT NULL,
                        hour INTEGER NOT NULL,
                        is_dirty INTEGER NOT NULL DEFAULT 1,
                        version INTEGER NOT NULL DEFAULT 1,
                        synced_version INTEGER NOT NULL DEFAULT 0,
                        deleted INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL DEFAULT 0,
                        merge_source TEXT,
                        FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX index_reading_session_date_key ON reading_session(date_key)")
                database.execSQL("CREATE INDEX index_reading_session_book_id ON reading_session(book_id)")
                database.execSQL("CREATE INDEX index_reading_session_book_chapter ON reading_session(book_id, chapter_index)")
                database.execSQL("CREATE INDEX index_reading_session_started_at ON reading_session(started_at)")

                // 2. chapter_reading_stats 移除 read_time_seconds 列
                database.execSQL("""
                    CREATE TABLE chapter_reading_stats_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        book_id INTEGER NOT NULL,
                        chapter_index INTEGER NOT NULL,
                        visited INTEGER NOT NULL DEFAULT 0,
                        first_visited_at INTEGER NOT NULL DEFAULT 0,
                        last_visited_at INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("""
                    INSERT INTO chapter_reading_stats_new (id, book_id, chapter_index, visited, first_visited_at, last_visited_at)
                    SELECT id, book_id, chapter_index, visited, first_visited_at, last_visited_at FROM chapter_reading_stats
                """)
                database.execSQL("DROP TABLE chapter_reading_stats")
                database.execSQL("ALTER TABLE chapter_reading_stats_new RENAME TO chapter_reading_stats")
                database.execSQL("CREATE UNIQUE INDEX index_chapter_reading_stats_book_chapter ON chapter_reading_stats(book_id, chapter_index)")
                database.execSQL("CREATE INDEX index_chapter_reading_stats_book_id ON chapter_reading_stats(book_id)")

                // 3. reading_history 移除 reading_duration_minutes 列
                database.execSQL("""
                    CREATE TABLE reading_history_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        book_id INTEGER NOT NULL,
                        read_count INTEGER NOT NULL,
                        finished_at INTEGER NOT NULL,
                        reading_progress REAL NOT NULL DEFAULT 1.0,
                        FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("""
                    INSERT INTO reading_history_new (id, book_id, read_count, finished_at, reading_progress)
                    SELECT id, book_id, read_count, finished_at, reading_progress FROM reading_history
                """)
                database.execSQL("DROP TABLE reading_history")
                database.execSQL("ALTER TABLE reading_history_new RENAME TO reading_history")
                database.execSQL("CREATE INDEX index_reading_history_book_id ON reading_history(book_id)")
            }
        }

        /** v25: 词典相关表（dict_meta, dict_history, word_book） */
        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. 词库元数据表
                database.execSQL("""
                    CREATE TABLE dict_meta (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        dict_key TEXT NOT NULL,
                        display_name TEXT NOT NULL,
                        format TEXT NOT NULL,
                        lang_pair TEXT NOT NULL DEFAULT '',
                        file_path TEXT NOT NULL,
                        index_path TEXT,
                        data_path TEXT,
                        entry_count INTEGER NOT NULL DEFAULT 0,
                        is_enabled INTEGER NOT NULL DEFAULT 1,
                        priority INTEGER NOT NULL DEFAULT 0,
                        imported_at INTEGER NOT NULL DEFAULT 0,
                        last_used_at INTEGER NOT NULL DEFAULT 0
                    )
                """)
                database.execSQL("CREATE UNIQUE INDEX index_dict_meta_dict_key ON dict_meta(dict_key)")

                // 2. 查词历史表
                database.execSQL("""
                    CREATE TABLE dict_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        word TEXT NOT NULL,
                        context_sentence TEXT NOT NULL DEFAULT '',
                        book_id INTEGER NOT NULL DEFAULT 0,
                        chapter_index INTEGER NOT NULL DEFAULT 0,
                        char_offset INTEGER NOT NULL DEFAULT 0,
                        queried_at INTEGER NOT NULL DEFAULT 0
                    )
                """)
                database.execSQL("CREATE INDEX index_dict_history_queried_at ON dict_history(queried_at)")
                database.execSQL("CREATE INDEX index_dict_history_word ON dict_history(word)")

                // 3. 生词本表
                database.execSQL("""
                    CREATE TABLE word_book (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        word TEXT NOT NULL,
                        definition TEXT NOT NULL DEFAULT '',
                        context_sentence TEXT NOT NULL DEFAULT '',
                        book_id INTEGER NOT NULL DEFAULT 0,
                        chapter_index INTEGER NOT NULL DEFAULT 0,
                        char_offset INTEGER NOT NULL DEFAULT 0,
                        added_at INTEGER NOT NULL DEFAULT 0,
                        last_review_at INTEGER NOT NULL DEFAULT 0,
                        review_count INTEGER NOT NULL DEFAULT 0,
                        mastery_level INTEGER NOT NULL DEFAULT 0,
                        exported_to_anki INTEGER NOT NULL DEFAULT 0
                    )
                """)
                database.execSQL("CREATE UNIQUE INDEX index_word_book_word ON word_book(word)")
                database.execSQL("CREATE INDEX index_word_book_added_at ON word_book(added_at)")
            }
        }

        /** v26: 编辑补丁表（用于崩溃恢复） */
        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE edit_delta (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        book_id INTEGER NOT NULL,
                        chapter_index INTEGER NOT NULL,
                        char_start INTEGER NOT NULL,
                        char_end INTEGER NOT NULL,
                        new_text TEXT NOT NULL,
                        original_text TEXT NOT NULL DEFAULT '',
                        timestamp INTEGER NOT NULL DEFAULT 0
                    )
                """)
                database.execSQL("CREATE INDEX index_edit_delta_book_id ON edit_delta(book_id)")
            }
        }

        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE edit_delta ADD COLUMN batch_id INTEGER NOT NULL DEFAULT 0")
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_16_17,
            MIGRATION_17_18,
            MIGRATION_18_19,
            MIGRATION_19_20,
            MIGRATION_20_21,
            MIGRATION_21_22,
            MIGRATION_22_23,
            MIGRATION_23_24,
            MIGRATION_24_25,
            MIGRATION_25_26,
            MIGRATION_26_27,
        )
    }
}
