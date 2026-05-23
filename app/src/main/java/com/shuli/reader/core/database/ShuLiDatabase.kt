package com.shuli.reader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.shuli.reader.core.database.dao.BookDao
import com.shuli.reader.core.database.dao.BookmarkDao
import com.shuli.reader.core.database.dao.NoteDao
import com.shuli.reader.core.database.dao.ReadingProgressDao
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.database.entity.NoteEntity
import com.shuli.reader.core.database.entity.ReadingProgressEntity

@Database(
    entities = [
        BookEntity::class,
        BookmarkEntity::class,
        NoteEntity::class,
        ReadingProgressEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ShuLiDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun noteDao(): NoteDao
    abstract fun readingProgressDao(): ReadingProgressDao

    companion object {
        const val DATABASE_NAME = "shuli_database"
    }
}
