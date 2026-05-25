package com.shuli.reader.core

import android.content.Context
import androidx.room.Room
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.shuli.reader.core.data.UserPreferences
import com.shuli.reader.core.database.ShuLiDatabase
import com.shuli.reader.core.parser.EpubParser
import com.shuli.reader.core.parser.TxtParser
import com.shuli.reader.core.repository.BookRepository
import kotlinx.coroutines.flow.first

val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

class ShuLiAppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext

    val database: ShuLiDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            ShuLiDatabase::class.java,
            ShuLiDatabase.DATABASE_NAME,
        )
            .addMigrations(
                ShuLiDatabase.MIGRATION_1_2,
                ShuLiDatabase.MIGRATION_2_3,
                ShuLiDatabase.MIGRATION_3_4,
                ShuLiDatabase.MIGRATION_4_5,
                ShuLiDatabase.MIGRATION_5_6,
                ShuLiDatabase.MIGRATION_6_7,
                ShuLiDatabase.MIGRATION_7_8,
            )
            .build()
    }

    val userPreferences: UserPreferences by lazy {
        UserPreferences(appContext.userPreferencesDataStore)
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

