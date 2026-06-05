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
}
