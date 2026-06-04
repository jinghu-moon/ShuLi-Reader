package com.shuli.reader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
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
    version = 16,
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
    }
}
