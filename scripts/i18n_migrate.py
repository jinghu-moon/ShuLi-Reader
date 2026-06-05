import re
import os

# Property -> Sub-interface mapping
MAPPING = {
    # CommonStrings
    'appName': 'common', 'bookshelf': 'common', 'settings': 'common', 'search': 'common',
    'searchPlaceholder': 'common', 'todayReading': 'common', 'noBooksFound': 'common',
    'emptyBookshelf': 'common', 'searchIconDesc': 'common', 'previousSearchResult': 'common',
    'nextSearchResult': 'common', 'sortIconDesc': 'common', 'viewModeIconDesc': 'common',
    'viewModeGrid': 'common', 'viewModeList': 'common', 'viewModeCompact': 'common',
    'moreIconDesc': 'common', 'backIconDesc': 'common', 'clearIconDesc': 'common',
    'deleteIconDesc': 'common', 'infoIconDesc': 'common', 'favoriteIconDesc': 'common',
    'coverImageDesc': 'common', 'bookmarkIconDesc': 'common', 'loading': 'common',
    'selected': 'common', 'appearance': 'common', 'themeModeLabel': 'common',
    'themeSystem': 'common', 'themeLight': 'common', 'themeDark': 'common',
    'themePaper': 'common', 'readerThemeLabel': 'common', 'appFontLabel': 'common',
    'appFontSystem': 'common', 'appFontHarmony': 'common', 'appFontLxgw': 'common',
    'languageLabel': 'common', 'languageCn': 'common', 'languageTw': 'common',
    'languageEn': 'common', 'favorite': 'common', 'cancel': 'common', 'save': 'common',
    'deleteFont': 'common',

    # BookshelfStrings
    'libraryImportSettings': 'bookshelf', 'duplicateCheck': 'bookshelf', 'duplicateCheckDesc': 'bookshelf',
    'importCopy': 'bookshelf', 'importCopyDesc': 'bookshelf', 'clearTempCache': 'bookshelf',
    'clearTempCacheDesc': 'bookshelf', 'clearCacheSuccess': 'bookshelf', 'bookDeleted': 'bookshelf',
    'importSuccess': 'bookshelf', 'bookAlreadyInShelf': 'bookshelf', 'folderCreated': 'bookshelf',
    'removedFromFolder': 'bookshelf', 'addedToFolder': 'bookshelf', 'newFolder': 'bookshelf',
    'importSuccessCount': 'bookshelf', 'importSuccessWithSkipped': 'bookshelf',
    'importSuccessWithFailed': 'bookshelf', 'importSuccessWithBoth': 'bookshelf',
    'importFailed': 'bookshelf', 'favoriteToggled': 'bookshelf', 'addFavorite': 'bookshelf',
    'removeFavorite': 'bookshelf', 'bookInfo': 'bookshelf', 'deleteBook': 'bookshelf',
    'deleteBookTitle': 'bookshelf', 'deleteBookConfirm': 'bookshelf', 'filterAll': 'bookshelf',
    'filterFinished': 'bookshelf', 'filterFavorite': 'bookshelf', 'sortTitle': 'bookshelf',
    'sortDescending': 'bookshelf', 'sortAscending': 'bookshelf', 'sortLastRead': 'bookshelf',
    'sortAddTime': 'bookshelf', 'sortBookTitle': 'bookshelf', 'sortReadingTime': 'bookshelf',
    'sortReadingProgress': 'bookshelf', 'bookTitleLabel': 'bookshelf', 'bookAuthorLabel': 'bookshelf',
    'unknownAuthor': 'bookshelf', 'bookFormatLabel': 'bookshelf', 'bookSizeLabel': 'bookshelf',
    'bookProgressLabel': 'bookshelf', 'readingDurationLabel': 'bookshelf', 'notReadYet': 'bookshelf',
    'unreadLabel': 'bookshelf', 'notStartedLabel': 'bookshelf', 'readProgress': 'bookshelf',
    'filePathLabel': 'bookshelf', 'favoritedDesc': 'bookshelf', 'selectAll': 'bookshelf',
    'deselectAll': 'bookshelf', 'importSelected': 'bookshelf', 'folderImportDesc': 'bookshelf',
    'groupCreatedAndMoved': 'bookshelf', 'selectedItemCount': 'bookshelf', 'selectItems': 'bookshelf',
    'folderLabel': 'bookshelf', 'moreLabel': 'bookshelf', 'confirmDeleteTitle': 'bookshelf',
    'confirmDeleteSelected': 'bookshelf', 'moveToExistingGroup': 'bookshelf', 'newGroupName': 'bookshelf',
    'createAndMove': 'bookshelf', 'createNewGroup': 'bookshelf', 'removeFromGroup': 'bookshelf',
    'folderEmpty': 'bookshelf', 'unableToReadFile': 'bookshelf', 'invalidFolderPath': 'bookshelf',
    'invalidFolder': 'bookshelf', 'noImportableFiles': 'bookshelf',

    # ReaderStrings
    'readerPreferences': 'reader', 'defaultFontSize': 'reader', 'defaultLineSpacing': 'reader',
    'lineSpacingCompact': 'reader', 'lineSpacingMedium': 'reader', 'lineSpacingWide': 'reader',
    'defaultPageAnim': 'reader', 'pageAnimOverlay': 'reader', 'pageAnimSlide': 'reader',
    'pageAnimSimulation': 'reader', 'pageAnimFade': 'reader', 'pageAnimNone': 'reader',
    'pageTurnDirection': 'reader', 'pageTurnHorizontal': 'reader', 'pageTurnVertical': 'reader',
    'paragraphSpacing': 'reader', 'paragraphSpacingCompact': 'reader', 'paragraphSpacingNormal': 'reader',
    'paragraphSpacingWide': 'reader', 'firstLineIndent': 'reader', 'indentNone': 'reader',
    'indentTwoChars': 'reader', 'fullScreenMode': 'reader', 'keepScreenOn': 'reader',
    'brightness': 'reader', 'brightnessFollowSystem': 'reader', 'brightnessFollowSystemLabel': 'reader',
    'brightnessResetToSystem': 'reader', 'readingFont': 'reader', 'readingFontSystem': 'reader',
    'readingFontHarmony': 'reader', 'readingFontLxgw': 'reader', 'importFont': 'reader',
    'fontTestText': 'reader', 'marginTopBottom': 'reader', 'marginLeftRight': 'reader',
    'editValue': 'reader', 'confirm': 'reader', 'layoutTab': 'reader', 'styleTab': 'reader',
    'settingsTab': 'reader', 'letterSpacingLabel': 'reader', 'fontWeightLabel': 'reader',
    'fontWeightLight': 'reader', 'fontWeightNormal': 'reader', 'fontWeightMedium': 'reader',
    'fontWeightBold': 'reader', 'textAlignLabel': 'reader', 'textAlignLeft': 'reader',
    'textAlignJustify': 'reader', 'chineseConvertLabel': 'reader', 'chineseConvertNone': 'reader',
    'chineseConvertSimplified': 'reader', 'chineseConvertTraditional': 'reader',
    'useZhLayoutLabel': 'reader', 'usePanguSpacingLabel': 'reader', 'slotNone': 'reader',
    'slotChapterTitle': 'reader', 'slotBookTitle': 'reader', 'slotChapterProgressFraction': 'reader',
    'slotChapterProgressPercent': 'reader', 'slotBookProgressFraction': 'reader',
    'slotBookProgressPercent': 'reader', 'slotTime': 'reader', 'slotBattery': 'reader',
    'slotDate': 'reader', 'headerLabel': 'reader', 'footerLabel': 'reader',
    'progressBarLabel': 'reader', 'opacityLabel': 'reader', 'headerFooterCustom': 'reader',
    'headerHidden': 'reader', 'footerHidden': 'reader', 'displayLabel': 'reader',
    'displayFollowStatusBar': 'reader', 'displayAlwaysShow': 'reader', 'displayAlwaysHide': 'reader',
    'positionLeft': 'reader', 'positionCenter': 'reader', 'positionRight': 'reader',
    'headerLeft': 'reader', 'headerCenter': 'reader', 'headerRight': 'reader',
    'footerLeft': 'reader', 'footerCenter': 'reader', 'footerRight': 'reader',
    'titleStyleLabel': 'reader', 'titleAlignLeft': 'reader', 'titleAlignCenter': 'reader',
    'titleAlignHidden': 'reader', 'titleSizeOffset': 'reader', 'titleMarginTop': 'reader',
    'titleMarginBottom': 'reader', 'headerMarginTop': 'reader', 'footerMarginBottom': 'reader',
    'keepScreenOnLabel': 'reader', 'keepScreenOnDesc': 'reader', 'volumeKeyLabel': 'reader',
    'volumeKeyDesc': 'reader', 'edgeTurnPageLabel': 'reader', 'edgeTurnPageDesc': 'reader',
    'edgeWidthLabel': 'reader', 'headerLineLabel': 'reader', 'footerLineLabel': 'reader',
    'headerFontSizeLabel': 'reader', 'footerFontSizeLabel': 'reader', 'bottomJustifyLabel': 'reader',
    'readingPresets': 'reader', 'savePresetAction': 'reader', 'resetToDefault': 'reader',
    'resetToDefaultConfirm': 'reader', 'savePresetTitle': 'reader', 'presetNameLabel': 'reader',
    'deletePresetTitle': 'reader', 'deletePresetConfirm': 'reader', 'confirmAction': 'reader',
    'cancelAction': 'reader', 'saveAction': 'reader', 'deleteAction': 'reader',
    'deleteBookmarkTitle': 'reader', 'deleteBookmarkConfirm': 'reader', 'deleteNoteTitle': 'reader',
    'deleteNoteConfirm': 'reader', 'directoryTab': 'reader', 'bookmarksTab': 'reader',
    'notesTab': 'reader', 'currentChapterLabel': 'reader', 'noBookmarks': 'reader',
    'noNotes': 'reader', 'notePosition': 'reader', 'copySelection': 'reader',
    'addBookmarkAction': 'reader', 'addNoteAction': 'reader', 'previousChapter': 'reader',
    'nextChapter': 'reader', 'customizeCover': 'reader', 'resetCoverColor': 'reader',
    'unifiedCoverColor': 'reader', 'unifiedCoverColorAuto': 'reader', 'unifiedCoverColorActive': 'reader',
    'saveSuccess': 'reader', 'saveFailed': 'reader', 'alreadyLatestVersion': 'reader',
    'alreadyFirstPage': 'reader', 'alreadyLastPage': 'reader', 'imagePlaceholder': 'reader',
    'wordCountTenThousand': 'reader', 'wordCountUnit': 'reader',

    # SettingsStrings
    'advancedSettings': 'settings', 'gpuAcceleration': 'settings', 'loggingEnabled': 'settings',
    'resetAllSettings': 'settings', 'resetAllSettingsDesc': 'settings', 'settingsResetSuccess': 'settings',
    'chapterFullText': 'settings', 'aboutLabel': 'settings', 'versionLabel': 'settings',
    'developerLabel': 'settings', 'feedbackLabel': 'settings', 'licenseLabel': 'settings',
    'checkUpdate': 'settings', 'readingStats': 'settings', 'statsEnable': 'settings',
    'statsEnableDesc': 'settings', 'statsDailyTarget': 'settings', 'resetStats': 'settings',
    'resetStatsDesc': 'settings', 'viewStatsReport': 'settings', 'statsTitle': 'settings',
    'totalBooksCount': 'settings', 'totalReadingTime': 'settings', 'todayReadingProgress': 'settings',
    'readingTargetMinutes': 'settings',

    # SyncStrings
    'syncSettings': 'sync', 'syncMethod': 'sync', 'syncMethodLocal': 'sync', 'syncMethodWebdav': 'sync',
    'webdavUrl': 'sync', 'webdavUser': 'sync', 'webdavPassword': 'sync', 'testConnection': 'sync',
    'syncNow': 'sync', 'syncAndBackup': 'sync', 'syncAndBackupDesc': 'sync', 'cloudSyncConfig': 'sync',
    'localBackupDesc': 'sync', 'webdavServer': 'sync', 'webdavServerSettings': 'sync',
    'e2eeStatus': 'sync', 'e2eeSettings': 'sync', 'syncedDevices': 'sync',
    'manageRegisteredDevices': 'sync', 'syncLog': 'sync', 'viewSyncHistory': 'sync',
    'exportData': 'sync', 'exportBookmarksNotesProgress': 'sync', 'syncStatus': 'sync',
    'ready': 'sync', 'authFailedCheckCredentials': 'sync', 'networkConnectionFailed': 'sync',
    'rateLimitedRetryLater': 'sync', 'cryptoLockedVerifyPassword': 'sync', 'unknownError': 'sync',
    'serverAddress': 'sync', 'syncContent': 'sync', 'wifiOnly': 'sync', 'autoSync': 'sync',
    'connectionSuccess': 'sync', 'authFailedCheckUserPassword': 'sync', 'networkErrorCheckAddress': 'sync',
    'unknownErrorRetryLater': 'sync', 'syncing': 'sync', 'cancelSync': 'sync',
    'cancelSyncExplanation': 'sync', 'syncAgain': 'sync', 'scanningLocalChanges': 'sync',
    'downloadingRemoteData': 'sync', 'mergingData': 'sync', 'uploadingBookmarksNotes': 'sync',
    'syncComplete': 'sync', 'syncFailed': 'sync', 'rateLimitedWaitRetry': 'sync',
    'waitingRetry': 'sync', 'cryptoLocked': 'sync', 'internalStorage': 'sync',
    'externalStorage': 'sync', 'rootDirectory': 'sync', 'otherDevice': 'sync',
    'readingProgressConflict': 'sync', 'conflictDetectedMessage': 'sync', 'thisDevice': 'sync',
    'keepLocal': 'sync', 'useRemote': 'sync', 'progressPosition': 'sync', 'updatedAtLabel': 'sync',
    'noSyncedDevices': 'sync', 'unknownDevice': 'sync', 'lastSyncLabel': 'sync',
    'appVersionLabel': 'sync', 'removeDevice': 'sync', 'removeDeviceConfirm': 'sync',
    'remove': 'sync', 'deviceFallbackName': 'sync', 'selectExportContent': 'sync',
    'bookFiles': 'sync', 'bookmarks': 'sync', 'notes': 'sync', 'readingProgressExport': 'sync',
    'readerConfig': 'sync', 'estimatingSize': 'sync', 'estimatedSize': 'sync',
    'encryptedExport': 'sync', 'rememberExportPassword': 'sync', 'password': 'sync',
    'passwordConfirm': 'sync', 'export': 'sync', 'backupDescription': 'sync',
    'backupLocation': 'sync', 'defaultAppDirectory': 'sync', 'select': 'sync',
    'customBackupDirSelected': 'sync', 'backupInPrivateDirWarning': 'sync', 'autoBackup': 'sync',
    'autoBackupDesc': 'sync', 'backupFrequency': 'sync', 'backupEveryNHours': 'sync',
    'backupOnStart': 'sync', 'backupOnStartDesc': 'sync', 'backupOnExit': 'sync',
    'backupOnExitDesc': 'sync', 'autoBackupNote': 'sync', 'manualBackup': 'sync',
    'exportBackup': 'sync', 'exportBackupDesc': 'sync', 'importBackup': 'sync',
    'importBackupDesc': 'sync', 'selectFile': 'sync', 'backupNotes': 'sync',
    'backupNotesContent': 'sync', 'backupResultSuccess': 'sync', 'backupResultComplete': 'sync',
    'filterCloud': 'sync', 'filterLocal': 'sync', 'filterFailed': 'sync', 'noSyncLogs': 'sync',
    'syncTypeCloud': 'sync', 'syncTypeLocal': 'sync', 'syncTypeUnknown': 'sync',
    'requestCount': 'sync', 'today': 'sync', 'yesterday': 'sync', 'durationSeconds': 'sync',
    'durationMinutesSeconds': 'sync', 'durationHoursMinutes': 'sync',
    'invalidEncryptedFileFormat': 'sync', 'zipMissingManifest': 'sync', 'backupMissingManifest': 'sync',
    'decryptFailedPasswordWrong': 'sync', 'cannotReadFontFile': 'sync', 'fontWriteFailedOrEmpty': 'sync',
    'backupExportFailedWriteDir': 'sync', 'backupExportSuccessCustom': 'sync',
    'backupExportFailedCreateFile': 'sync', 'backupExportFailedPermission': 'sync',
    'backupExportSuccess': 'sync', 'backupExportFailed': 'sync', 'backupImportFailedRead': 'sync',
    'backupImportSuccess': 'sync', 'backupImportFailed': 'sync', 'fontImportSuccess': 'sync',
    'fontImportFailed': 'sync',

    # TtsStrings
    'ttsSettings': 'tts', 'ttsSpeed': 'tts', 'ttsPitch': 'tts', 'ttsAutoPage': 'tts',
    'ttsHighlightSentence': 'tts', 'ttsSkipTitle': 'tts', 'ttsSleepTimer': 'tts',
    'ttsSleepTimerOff': 'tts', 'ttsSleepTimerRemaining': 'tts', 'ttsStart': 'tts',
    'ttsPause': 'tts', 'ttsStop': 'tts',

    # EncryptionStrings
    'encryptionManagement': 'encryption', 'encryptionEnabled': 'encryption',
    'encryptionDisabled': 'encryption', 'e2eeProtectsSyncData': 'encryption',
    'dataSyncedInPlaintext': 'encryption', 'e2eeNotEnabled': 'encryption',
    'e2eeNotEnabledDesc': 'encryption', 'enableEncryption': 'encryption',
    'rememberEncryptionPassword': 'encryption', 'rememberEncryptionPasswordDesc': 'encryption',
    'verifyPassword': 'encryption', 'changePassword': 'encryption',
    'setEncryptionPassword': 'encryption', 'inputPasswordToVerify': 'encryption',
    'encryptionPassword': 'encryption', 'verifySuccess': 'encryption', 'passwordWrong': 'encryption',
    'encryptionNotEnabled': 'encryption', 'verifyError': 'encryption', 'verify': 'encryption',
    'oldPassword': 'encryption', 'newPassword': 'encryption', 'confirmNewPassword': 'encryption',
    'passwordMismatch': 'encryption', 'confirmChange': 'encryption', 'confirmSet': 'encryption',
    'algorithmDetails': 'encryption', 'encryptionAlgorithm': 'encryption', 'kdfIterations': 'encryption',
    'keyVersion': 'encryption', 'createdAt': 'encryption',
}

# All prefixes that resolve to an AppStrings instance
# Each entry: (prefix_pattern, replacement_prefix)
# prefix_pattern is a regex that matches the prefix before the property name
PREFIXES = [
    # strings.X → strings.sub.X
    (r'(strings\.)', r'\1'),
    # currentStrings.X → currentStrings.sub.X
    (r'(currentStrings\.)', r'\1'),
    # LocalAppStrings.current.X → LocalAppStrings.current.sub.X
    (r'(LocalAppStrings\.current\.)', r'\1'),
    # stringResolver().X → stringResolver().sub.X
    (r'(stringResolver\(\)\.)', r'\1'),
    # it.X in lambdas — only when followed by a mapped property
    # Use word-boundary-like lookbehind: "it." not preceded by alphanumeric
    (r'(?<![a-zA-Z0-9_])(it\.)', r'\1'),
]

def replace_in_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    original = content

    # Sort by length descending to avoid partial matches
    sorted_props = sorted(MAPPING.keys(), key=len, reverse=True)

    for prop in sorted_props:
        sub = MAPPING[prop]
        for prefix_pattern, replacement_prefix in PREFIXES:
            # Full pattern: prefix + property, not followed by alphanumeric/underscore/dot
            pattern = prefix_pattern + re.escape(prop) + r'(?=[^a-zA-Z0-9_.]|$)'
            replacement = replacement_prefix + sub + '.' + prop
            content = re.sub(pattern, replacement, content)

    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False

# Find all Kotlin files in the project
root = r'D:\100_Projects\110_Daily\ShuLi-Reader\app\src\main\java\com\shuli\reader'
count = 0
for dirpath, dirnames, filenames in os.walk(root):
    for filename in filenames:
        if filename.endswith('.kt'):
            filepath = os.path.join(dirpath, filename)
            if replace_in_file(filepath):
                count += 1
                print(f"Updated: {filepath}")

print(f"\nTotal files updated: {count}")
