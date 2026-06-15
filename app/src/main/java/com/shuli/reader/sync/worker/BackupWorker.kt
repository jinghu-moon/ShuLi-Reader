// Part of 自动备份 Worker
package com.shuli.reader.sync.worker

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shuli.reader.ShuLiApplication
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.database.entity.NoteEntity
import com.shuli.reader.core.database.entity.ReadingProgressEntity
import com.shuli.reader.sync.backup.BackupExporter
import com.shuli.reader.sync.backup.ExportDatabase
import com.shuli.reader.sync.backup.ExportOptions
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 自动备份 Worker
 *
 * 由 WorkManager 调度执行定时备份任务。
 * 支持自定义备份目录（SAF）和默认应用私有目录。
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as ShuLiApplication
            val database = app.appContainer.database
            val userPreferences = app.appContainer.userPreferences

            val exportDb = object : ExportDatabase {
                override suspend fun getAllBooks(): List<BookEntity> {
                    return database.bookDao().getAllBooksSync()
                }
                override suspend fun getAllBookmarks(): List<BookmarkEntity> {
                    return database.bookmarkDao().queryAllActive()
                }
                override suspend fun getAllNotes(): List<NoteEntity> {
                    return database.noteDao().queryAllActive()
                }
                override suspend fun getAllProgress(): List<ReadingProgressEntity> {
                    return database.readingProgressDao().queryAllActive()
                }
                override suspend fun getAllTags(): List<com.shuli.reader.core.database.entity.TagEntity> {
                    return database.tagDao().getAllTagsSync()
                }
                override suspend fun getAllBookTagCrossRefs(): List<com.shuli.reader.core.database.entity.BookTagCrossRef> {
                    return database.tagDao().getAllBookTagCrossRefs()
                }
                override suspend fun getAllReadingSessions(): List<com.shuli.reader.core.database.entity.ReadingSessionEntity> {
                    return database.readingSessionDao().getAllSessions()
                }
            }

            val exporter = BackupExporter(exportDb, applicationContext)

            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "shuli_backup_${dateFormat.format(Date())}.zip"

            val options = ExportOptions(
                includeBookFiles = false,
                includeBookmarks = true,
                includeNotes = true,
                includeProgress = true,
                includeConfig = true,
                encryptionPassword = null,
            )

            // 导出到临时文件
            val tempFile = File.createTempFile("shuli_backup_", ".zip", applicationContext.cacheDir)
            try {
                exporter.export(tempFile, options)

                // 根据配置决定备份目标目录
                val backupLocation = userPreferences.backupLocation.first()
                if (backupLocation.isNotEmpty()) {
                    // 自定义目录：使用 DocumentFile 拷贝到 SAF 目录
                    val treeUri = Uri.parse(backupLocation)
                    val docDir = DocumentFile.fromTreeUri(applicationContext, treeUri)
                    if (docDir != null && docDir.canWrite()) {
                        val targetFile = docDir.createFile("application/zip", fileName)
                        if (targetFile != null) {
                            applicationContext.contentResolver.openOutputStream(targetFile.uri)?.use { out ->
                                tempFile.inputStream().use { inp ->
                                    inp.copyTo(out)
                                }
                            }
                        }
                    }
                } else {
                    // 默认目录：应用私有目录
                    val backupDir = File(applicationContext.getExternalFilesDir(null), "backups")
                    if (!backupDir.exists()) {
                        backupDir.mkdirs()
                    }
                    val outputFile = File(backupDir, fileName)
                    tempFile.copyTo(outputFile, overwrite = true)
                }

                // 清理旧备份
                val backupDir = File(applicationContext.getExternalFilesDir(null), "backups")
                cleanOldBackups(backupDir, maxKeep = 5)

                Result.success()
            } finally {
                tempFile.delete()
            }
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private fun cleanOldBackups(backupDir: File, maxKeep: Int) {
        val backups = backupDir.listFiles { file ->
            file.name.startsWith("shuli_backup_") && file.name.endsWith(".zip")
        }?.sortedByDescending { it.lastModified() } ?: return

        if (backups.size > maxKeep) {
            backups.drop(maxKeep).forEach { it.delete() }
        }
    }

    companion object {
        const val MAX_RETRIES = 3
    }
}
