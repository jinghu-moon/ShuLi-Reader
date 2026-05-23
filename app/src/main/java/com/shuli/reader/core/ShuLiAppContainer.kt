package com.shuli.reader.core

import android.content.Context
import androidx.room.Room
import com.shuli.reader.core.database.ShuLiDatabase
import com.shuli.reader.core.parser.EpubParser
import com.shuli.reader.core.parser.TxtParser
import com.shuli.reader.core.repository.BookRepository
import com.shuli.reader.core.theme.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ShuLiAppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: ShuLiDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            ShuLiDatabase::class.java,
            ShuLiDatabase.DATABASE_NAME,
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    val themeManager: ThemeManager by lazy {
        ThemeManager(appContext)
    }

    val bookRepository: BookRepository by lazy {
        BookRepository(
            bookDao = database.bookDao(),
            readingProgressDao = database.readingProgressDao(),
            txtParser = TxtParser(),
            epubParser = EpubParser(),
            booksDir = java.io.File(appContext.filesDir, "books"),
        )
    }
}
