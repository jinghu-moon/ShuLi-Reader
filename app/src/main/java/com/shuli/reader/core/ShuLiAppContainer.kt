package com.shuli.reader.core

import android.content.Context
import androidx.room.Room
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.shuli.reader.core.data.UserPreferences
import com.shuli.reader.core.database.ShuLiDatabase
import com.shuli.reader.core.parser.ByteWindowReader
import com.shuli.reader.core.parser.EpubParser
import com.shuli.reader.core.parser.StreamDecoder
import com.shuli.reader.core.parser.TxtParser
import com.shuli.reader.core.repository.BookRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import java.util.concurrent.Executors

val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

class ShuLiAppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext

    /** 阅读器专用调度器：2 线程池，避免和 Room/UI 抢资源（v4） */
    val readerDispatcher: CoroutineDispatcher by lazy {
        Executors.newFixedThreadPool(2) { r ->
            Thread(r, "reader-io").apply { isDaemon = true }
        }.asCoroutineDispatcher()
    }

    /** 应用级协程作用域：用于后台建索引等不应随 ViewModel 取消的任务（v4） */
    val applicationScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + readerDispatcher)
    }

    val byteWindowReader: ByteWindowReader by lazy {
        ByteWindowReader(readerDispatcher)
    }

    val streamDecoder: StreamDecoder by lazy {
        StreamDecoder()
    }

    val database: ShuLiDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            ShuLiDatabase::class.java,
            ShuLiDatabase.DATABASE_NAME,
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    val userPreferences: UserPreferences by lazy {
        UserPreferences(appContext.userPreferencesDataStore)
    }

    val epubParser: EpubParser by lazy {
        EpubParser()
    }

    val bookRepository: BookRepository by lazy {
        BookRepository(
            bookDao = database.bookDao(),
            bookChapterDao = database.bookChapterDao(),
            readingProgressDao = database.readingProgressDao(),
            txtParser = TxtParser(),
            epubParser = epubParser,
            byteWindowReader = byteWindowReader,
            booksDir = java.io.File(appContext.filesDir, "books"),
            applicationScope = applicationScope,
        )
    }

    val coverPrewarmer: com.shuli.reader.core.cover.CoverPrewarmer by lazy {
        com.shuli.reader.core.cover.CoverPrewarmer(bookRepository, appContext)
    }

    val syncManager: com.shuli.reader.core.sync.WebDavSyncManager by lazy {
        val remote = object : com.shuli.reader.core.sync.ProgressRemote {
            @Volatile
            private var currentClient: Pair<com.shuli.reader.core.sync.WebDavConfig, com.shuli.reader.core.sync.WebDavClient>? = null

            private suspend fun getClient(): com.shuli.reader.core.sync.WebDavClient {
                val config = com.shuli.reader.core.sync.WebDavConfig(
                    userPreferences.webdavUrl.first(),
                    userPreferences.webdavUser.first(),
                    userPreferences.webdavPassword.first()
                )
                val cached = currentClient
                if (cached != null && cached.first == config) {
                    return cached.second
                }
                val client = com.shuli.reader.core.sync.WebDavClient(config)
                currentClient = config to client
                return client
            }

            override suspend fun upload(path: String, body: String) {
                getClient().put(path, body)
            }

            override suspend fun download(path: String): String {
                return getClient().get(path).body
            }
        }
        com.shuli.reader.core.sync.WebDavSyncManager(remote)
    }
}

