package com.shuli.reader.core.i18n

/**
 * 同步、备份、导出、冲突解决、设备管理、同步日志字符串。
 */
interface SyncStrings {
    // 同步设置
    val syncSettings: String
    val syncMethod: String
    val syncMethodLocal: String
    val syncMethodWebdav: String
    val webdavUrl: String
    val webdavUser: String
    val webdavPassword: String
    val testConnection: String
    val syncNow: String
    val syncAndBackup: String
    val syncAndBackupDesc: String
    val cloudSyncConfig: String
    val localBackupDesc: String

    // 同步状态与导航
    val webdavServer: String
    val webdavServerSettings: String
    val e2eeStatus: String
    val e2eeSettings: String
    val syncedDevices: String
    val manageRegisteredDevices: String
    val syncLog: String
    val viewSyncHistory: String
    val exportData: String
    val exportBookmarksNotesProgress: String
    val syncStatus: String
    val ready: String
    val authFailedCheckCredentials: String
    val networkConnectionFailed: String
    val rateLimitedRetryLater: String
    val cryptoLockedVerifyPassword: String
    val unknownError: String
    val serverAddress: String
    val syncContent: String
    val wifiOnly: String
    val autoSync: String
    val connectionSuccess: String
    val authFailedCheckUserPassword: String
    val networkErrorCheckAddress: String
    val unknownErrorRetryLater: String

    // 同步中状态
    val syncing: String
    val cancelSync: String
    val cancelSyncExplanation: String
    val syncAgain: String
    val scanningLocalChanges: String
    val downloadingRemoteData: String
    val mergingData: String
    val uploadingBookmarksNotes: String
    val syncComplete: String
    val syncFailed: String
    val rateLimitedWaitRetry: String
    val waitingRetry: String
    val cryptoLocked: String

    // 本地同步路径
    val internalStorage: String
    val externalStorage: String
    val rootDirectory: String

    // 冲突解决
    val otherDevice: String
    val readingProgressConflict: String
    val conflictDetectedMessage: (String) -> String
    val thisDevice: String
    val keepLocal: String
    val useRemote: String
    val progressPosition: (Int, Long) -> String
    val updatedAtLabel: String

    // 设备管理
    val noSyncedDevices: String
    val unknownDevice: String
    val lastSyncLabel: String
    val appVersionLabel: String
    val removeDevice: String
    val removeDeviceConfirm: (String) -> String
    val remove: String
    val deviceFallbackName: (String) -> String

    // 导出与备份
    val selectExportContent: String
    val bookFiles: String
    val bookmarks: String
    val notes: String
    val readingProgressExport: String
    val readerConfig: String
    val estimatingSize: String
    val estimatedSize: (String) -> String
    val encryptedExport: String
    val rememberExportPassword: String
    val password: String
    val passwordConfirm: String
    val export: String
    val backupDescription: String
    val backupLocation: String
    val defaultAppDirectory: String
    val select: String
    val customBackupDirSelected: String
    val backupInPrivateDirWarning: String
    val autoBackup: String
    val autoBackupDesc: String
    val backupFrequency: String
    val backupEveryNHours: (Int) -> String
    val backupOnStart: String
    val backupOnStartDesc: String
    val backupOnExit: String
    val backupOnExitDesc: String
    val autoBackupNote: String
    val manualBackup: String
    val exportBackup: String
    val exportBackupDesc: String
    val importBackup: String
    val importBackupDesc: String
    val selectFile: String
    val backupNotes: String
    val backupNotesContent: String
    val backupResultSuccess: String
    val backupResultComplete: String

    // 同步日志
    val filterCloud: String
    val filterLocal: String
    val filterFailed: String
    val noSyncLogs: String
    val syncTypeCloud: String
    val syncTypeLocal: String
    val syncTypeUnknown: String
    val requestCount: (Int) -> String
    val today: String
    val yesterday: String

    // 时长格式化
    val durationSeconds: (Long) -> String
    val durationMinutesSeconds: (Long, Long) -> String
    val durationHoursMinutes: (Long, Long) -> String

    // 错误消息
    val invalidEncryptedFileFormat: String
    val zipMissingManifest: String
    val backupMissingManifest: String
    val decryptFailedPasswordWrong: String
    val cannotReadFontFile: (String) -> String
    val fontWriteFailedOrEmpty: (String) -> String

    // 本地备份
    val backupExportFailedWriteDir: String
    val backupExportSuccessCustom: String
    val backupExportFailedCreateFile: String
    val backupExportFailedPermission: String
    val backupExportSuccess: (String) -> String
    val backupExportFailed: (String) -> String
    val backupImportFailedRead: String
    val backupImportSuccess: String
    val backupImportFailed: (String) -> String

    // 字体导入
    val fontImportSuccess: (String, Int) -> String
    val fontImportFailed: (String) -> String
}
