package com.shuli.reader.feature.settings

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.shuli.reader.core.ShuLiAppContainer
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.database.entity.NoteEntity
import com.shuli.reader.core.database.entity.ReadingProgressEntity
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.sync.export.BackupExporter
import com.shuli.reader.sync.export.BackupImporter
import com.shuli.reader.sync.export.ExportDatabase
import com.shuli.reader.sync.export.ImportDatabase
import com.shuli.reader.sync.export.ImportStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SubScreenNavigation(
    currentSubScreen: SettingsSubScreen?,
    onSubScreenChange: (SettingsSubScreen?) -> Unit,
    appContainer: ShuLiAppContainer?,
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    when (currentSubScreen) {
        is SettingsSubScreen.Sync -> {
            val syncSummaryViewModel = remember {
                com.shuli.reader.ui.settings.sync.SyncSummaryViewModel(
                    stateMachine = com.shuli.reader.sync.state.SyncStateMachine()
                )
            }
            com.shuli.reader.ui.settings.sync.SyncSettingsScreen(
                viewModel = syncSummaryViewModel,
                onBackClick = { onSubScreenChange(null) },
                onNavigateToCloudSync = { onSubScreenChange(SettingsSubScreen.CloudSync) },
                onNavigateToEncryption = { onSubScreenChange(SettingsSubScreen.Encryption) },
                onNavigateToDevices = { onSubScreenChange(SettingsSubScreen.Devices) },
                onNavigateToLogs = { onSubScreenChange(SettingsSubScreen.Logs) },
                onNavigateToExport = { onSubScreenChange(SettingsSubScreen.Export) },
            )
        }
        is SettingsSubScreen.CloudSync -> {
            val cloudSyncViewModel = remember {
                com.shuli.reader.ui.settings.sync.CloudSyncSettingsViewModel(
                    userPreferences = appContainer?.userPreferences
                )
            }
            com.shuli.reader.ui.settings.sync.CloudSyncSettingsScreen(
                viewModel = cloudSyncViewModel,
                onBackClick = { onSubScreenChange(SettingsSubScreen.Sync) },
                onNavigateToEncryption = { onSubScreenChange(SettingsSubScreen.Encryption) },
                onNavigateToDevices = { onSubScreenChange(SettingsSubScreen.Devices) },
                onNavigateToLogs = { onSubScreenChange(SettingsSubScreen.Logs) },
            )
        }
        is SettingsSubScreen.Encryption -> {
            val encryptionViewModel = remember {
                com.shuli.reader.ui.settings.crypto.EncryptionManagementViewModel()
            }
            com.shuli.reader.ui.settings.crypto.EncryptionManagementScreen(
                viewModel = encryptionViewModel,
                onBackClick = { onSubScreenChange(SettingsSubScreen.Sync) },
            )
        }
        is SettingsSubScreen.Devices -> {
            val devicesViewModel = remember {
                com.shuli.reader.ui.devices.DeviceManagementViewModel()
            }
            com.shuli.reader.ui.devices.DeviceManagementScreen(
                viewModel = devicesViewModel,
                onBackClick = { onSubScreenChange(SettingsSubScreen.Sync) },
            )
        }
        is SettingsSubScreen.Logs -> {
            val logsViewModel = remember {
                com.shuli.reader.ui.log.SyncLogViewModel()
            }
            com.shuli.reader.ui.log.SyncLogScreen(
                viewModel = logsViewModel,
                onBackClick = { onSubScreenChange(SettingsSubScreen.Sync) },
            )
        }
        is SettingsSubScreen.Export -> {
            var showExportSheet by remember { mutableStateOf(true) }
            if (showExportSheet) {
                com.shuli.reader.ui.export.ExportBottomSheet(
                    onDismiss = {
                        showExportSheet = false
                        onSubScreenChange(SettingsSubScreen.Sync)
                    },
                    onExport = { _ ->
                        showExportSheet = false
                        onSubScreenChange(SettingsSubScreen.Sync)
                    },
                )
            }
        }
        is SettingsSubScreen.LocalBackup -> {
            LocalBackupNavigation(
                appContainer = appContainer,
                uiState = uiState,
                viewModel = viewModel,
                onBackClick = { onSubScreenChange(null) },
            )
        }
        null -> {}
    }
}

@Composable
private fun LocalBackupNavigation(
    appContainer: ShuLiAppContainer?,
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var exportResult by remember { mutableStateOf<String?>(null) }
    var importResult by remember { mutableStateOf<String?>(null) }

    com.shuli.reader.ui.export.LocalBackupScreen(
        onBackClick = onBackClick,
        onExport = { options ->
            if (!isExporting && appContainer != null) {
                coroutineScope.launch {
                    isExporting = true
                    exportResult = null
                    try {
                        val database = appContainer.database
                        val exportDb = object : ExportDatabase {
                            override suspend fun getAllBooks(): List<BookEntity> =
                                database.bookDao().getAllBooksSync()
                            override suspend fun getAllBookmarks(): List<BookmarkEntity> =
                                database.bookmarkDao().queryAllActive()
                            override suspend fun getAllNotes(): List<NoteEntity> =
                                database.noteDao().queryAllActive()
                            override suspend fun getAllProgress(): List<ReadingProgressEntity> =
                                database.readingProgressDao().queryAllActive()
                            override suspend fun getAllTags(): List<com.shuli.reader.core.database.entity.TagEntity> =
                                database.tagDao().getAllTagsSync()
                            override suspend fun getAllBookTagCrossRefs(): List<com.shuli.reader.core.database.entity.BookTagCrossRef> =
                                database.tagDao().getAllBookTagCrossRefs()
                        }
                        val exporter = BackupExporter(exportDb, context)
                        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                        val fileName = "shuli_backup_${dateFormat.format(Date())}.zip"

                        val tempFile = withContext(Dispatchers.IO) {
                            File.createTempFile("shuli_export_", ".zip", context.cacheDir).also {
                                exporter.export(it, options)
                            }
                        }

                        val customDir = uiState.backupLocation
                        if (customDir.isNotEmpty()) {
                            val treeUri = Uri.parse(customDir)
                            val docDir = DocumentFile.fromTreeUri(context, treeUri)
                            if (docDir != null && docDir.canWrite()) {
                                val targetFile = docDir.createFile("application/zip", fileName)
                                if (targetFile != null) {
                                    withContext(Dispatchers.IO) {
                                        context.contentResolver.openOutputStream(targetFile.uri)?.use { out ->
                                            tempFile.inputStream().use { inp -> inp.copyTo(out) }
                                        } ?: throw IllegalStateException(strings.sync.backupExportFailedWriteDir)
                                    }
                                    exportResult = strings.sync.backupExportSuccessCustom
                                } else {
                                    exportResult = strings.sync.backupExportFailedCreateFile
                                }
                            } else {
                                exportResult = strings.sync.backupExportFailedPermission
                            }
                        } else {
                            val backupDir = File(context.getExternalFilesDir(null), "backups").apply { mkdirs() }
                            val outputFile = File(backupDir, fileName)
                            withContext(Dispatchers.IO) {
                                tempFile.copyTo(outputFile, overwrite = true)
                            }
                            exportResult = strings.sync.backupExportSuccess(outputFile.parent ?: "")
                        }

                        withContext(Dispatchers.IO) { tempFile.delete() }
                    } catch (e: Exception) {
                        exportResult = strings.sync.backupExportFailed(e.message ?: "")
                    } finally {
                        isExporting = false
                    }
                }
            }
        },
        onImport = { uri ->
            if (!isImporting && appContainer != null) {
                coroutineScope.launch {
                    isImporting = true
                    importResult = null
                    try {
                        val database = appContainer.database
                        val importDb = object : ImportDatabase {
                            override suspend fun getAllBooks(): List<BookEntity> =
                                database.bookDao().getAllBooksSync()
                            override suspend fun getAllBookmarks(): List<BookmarkEntity> =
                                database.bookmarkDao().queryAllActive()
                            override suspend fun getAllNotes(): List<NoteEntity> =
                                database.noteDao().queryAllActive()
                            override suspend fun getAllProgress(): List<ReadingProgressEntity> =
                                database.readingProgressDao().queryAllActive()
                            override suspend fun getAllTags(): List<com.shuli.reader.core.database.entity.TagEntity> =
                                database.tagDao().getAllTagsSync()
                            override suspend fun getAllBookTagCrossRefs(): List<com.shuli.reader.core.database.entity.BookTagCrossRef> =
                                database.tagDao().getAllBookTagCrossRefs()
                            override suspend fun upsertBook(book: BookEntity) =
                                database.bookDao().upsertBook(book)
                            override suspend fun clearBooks() = database.bookDao().deleteAllBooks()
                            override suspend fun upsertBookmark(bookmark: BookmarkEntity) =
                                database.bookmarkDao().upsertBookmark(bookmark)
                            override suspend fun clearBookmarks() = database.bookmarkDao().deleteAllBookmarks()
                            override suspend fun upsertNote(note: NoteEntity) =
                                database.noteDao().upsertNote(note)
                            override suspend fun clearNotes() = database.noteDao().deleteAllNotes()
                            override suspend fun upsertProgress(progress: ReadingProgressEntity) =
                                database.readingProgressDao().upsertProgress(progress)
                            override suspend fun clearProgress() = database.readingProgressDao().deleteAllProgress()
                            override suspend fun getExistingBookIds(): Set<Long> =
                                database.bookDao().getAllBooksSync().map { it.id }.toSet()
                            override suspend fun getExistingBookmarkIds(): Set<Long> =
                                database.bookmarkDao().queryAllActive().map { it.id }.toSet()
                            override suspend fun getExistingNoteIds(): Set<Long> =
                                database.noteDao().queryAllActive().map { it.id }.toSet()
                            override suspend fun getExistingProgressBookIds(): Set<Long> =
                                database.readingProgressDao().queryAllActive().map { it.bookId }.toSet()
                            override suspend fun insertTag(tag: com.shuli.reader.core.database.entity.TagEntity): Long =
                                database.tagDao().insertTagSync(tag)
                            override suspend fun addTagToBook(crossRef: com.shuli.reader.core.database.entity.BookTagCrossRef) {
                                database.tagDao().addTagToBookSync(crossRef)
                            }
                            override suspend fun runInTransaction(block: suspend () -> Unit) {
                                database.withTransaction { block() }
                            }
                        }
                        val importer = BackupImporter(db = importDb, strings = strings)

                        val tempFile = withContext(Dispatchers.IO) {
                            File.createTempFile("shuli_import_", ".zip", context.cacheDir).also { file ->
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    file.outputStream().use { output -> input.copyTo(output) }
                                } ?: throw IllegalStateException(strings.sync.backupImportFailedRead)
                            }
                        }

                        try {
                            importer.import(tempFile, strategy = ImportStrategy.MERGE)
                            importResult = strings.sync.backupImportSuccess
                        } finally {
                            withContext(Dispatchers.IO) { tempFile.delete() }
                        }
                    } catch (e: Exception) {
                        importResult = strings.sync.backupImportFailed(e.message ?: "")
                    } finally {
                        isImporting = false
                    }
                }
            }
        },
        isExporting = isExporting,
        isImporting = isImporting,
        exportResult = exportResult,
        importResult = importResult,
        autoBackupEnabled = uiState.autoBackupEnabled,
        backupOnAppStart = uiState.backupOnAppStart,
        backupOnAppExit = uiState.backupOnAppExit,
        backupIntervalHours = uiState.backupIntervalHours,
        backupLocation = uiState.backupLocation,
        onAutoBackupEnabledChange = { viewModel.updateAutoBackupEnabled(it) },
        onBackupOnAppStartChange = { viewModel.updateBackupOnAppStart(it) },
        onBackupOnAppExitChange = { viewModel.updateBackupOnAppExit(it) },
        onBackupIntervalChange = { viewModel.updateBackupIntervalHours(it) },
        onBackupLocationChange = { viewModel.updateBackupLocation(it) },
    )
}
