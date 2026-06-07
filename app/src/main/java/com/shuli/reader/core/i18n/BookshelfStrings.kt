package com.shuli.reader.core.i18n

/**
 * 书架、导入、书籍操作、编辑模式、分组字符串。
 */
interface BookshelfStrings {
    val libraryImportSettings: String
    val duplicateCheck: String
    val duplicateCheckDesc: String
    val importCopy: String
    val importCopyDesc: String
    val clearTempCache: String
    val clearTempCacheDesc: String
    val clearCacheSuccess: String
    val bookDeleted: String
    val importSuccess: String
    val bookAlreadyInShelf: String
    val folderCreated: String
    val removedFromFolder: String
    val addedToFolder: String
    val newFolder: String
    val importSuccessCount: (Int) -> String
    val importSuccessWithSkipped: (Int, Int) -> String
    val importSuccessWithFailed: (Int, Int) -> String
    val importSuccessWithBoth: (Int, Int, Int) -> String
    val importFailed: (String) -> String
    val favoriteToggled: String
    val addFavorite: String
    val removeFavorite: String
    val bookInfo: String
    val deleteBook: String
    val deleteBookTitle: String
    val deleteBookConfirm: (String) -> String
    val filterAll: String
    val filterFinished: String
    val filterFavorite: String
    val sortTitle: String
    val sortDescending: String
    val sortAscending: String
    val sortLastRead: String
    val sortAddTime: String
    val sortBookTitle: String
    val sortReadingTime: String
    val sortReadingProgress: String
    val bookTitleLabel: String
    val bookAuthorLabel: String
    val unknownAuthor: String
    val bookFormatLabel: String
    val bookSizeLabel: String
    val bookProgressLabel: String
    val readingDurationLabel: String
    val notReadYet: String
    val unreadLabel: String
    val notStartedLabel: String
    val readProgress: (Int) -> String
    val filePathLabel: String
    val favoritedDesc: String
    val selectAll: String
    val deselectAll: String
    val importSelected: (Int) -> String
    val folderImportDesc: String
    val groupCreatedAndMoved: String
    val selectedItemCount: (Int) -> String
    val selectItems: String
    val folderLabel: String
    val moreLabel: String
    val confirmDeleteTitle: String
    val confirmDeleteSelected: (Int) -> String
    val moveToExistingGroup: String
    val newGroupName: String
    val createAndMove: String
    val createNewGroup: String
    val removeFromGroup: String
    val folderEmpty: String
    val unableToReadFile: String
    val invalidFolderPath: String
    val invalidFolder: String
    val noImportableFiles: String

    // ── 阅读状态（P0）──
    val statusWantToRead: String
    val statusReading: String
    val statusPaused: String
    val statusFinished: String
    val statusAbandoned: String
    val rereadCountLabel: (Int) -> String
    val continueReading: String
    val startReading: String
    val rereadBook: String
    val restartBook: String
    val exportNotes: String
    val statusChanged: String
    val batchStatusChange: String

    // ── 标签（P1）──
    val addTag: String
    val removeTag: String
    val tags: String
    val tagCountLabel: (Int) -> String
    val searchTagHint: String
    val noTags: String
    val clearFilter: String
    val sortReadingStatus: String
    val sortReadCount: String

    // ── 标签管理（P2）──
    val tagManagement: String
    val tagTotalCount: (Int) -> String
    val renameTag: String
    val deleteTag: String
    val mergeTag: String
    val confirmDeleteTag: (String, Int) -> String
    val confirmMergeTag: (String, String) -> String

    // ── 阅读数据（P2）──
    val readingData: String
    val totalDuration: String
    val readingDays: String
    val daysUnit: String

    // ── 书签与笔记（P2）──
    val bookmarksAndNotes: String
    val viewAll: String
    val bookmarkNoteCount: (Int, Int) -> String

    // ── 标签建议（P3）──
    val tagSuggestions: String
    val acceptAllSuggestions: String
    val noSuggestions: String
    val acceptSuggestion: String
    val rejectSuggestion: String

    // ── 预设标签包（P3）──
    val presetTagPacks: String
    val importPresetTags: String
}
