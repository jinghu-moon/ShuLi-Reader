package com.shuli.reader.core.i18n

/** 简体中文 — 书架、导入、书籍操作、编辑模式字符串 */
internal data object ZhHansBookshelf : BookshelfStrings {
    override val libraryImportSettings = "书库与导入设置"
    override val duplicateCheck = "自动查重开关"
    override val duplicateCheckDesc = "导入重复书籍时自动高亮定位"
    override val importCopy = "导入时复制文件"
    override val importCopyDesc = "复制书籍到应用私有目录"
    override val clearTempCache = "清除临时缓存"
    override val clearTempCacheDesc = "清理书籍导入解压过程中产生的临时缓存"
    override val clearCacheSuccess = "缓存清理成功"
    override val bookDeleted = "已删除"
    override val importSuccess = "导入成功"
    override val bookAlreadyInShelf = "书籍已在书架中"
    override val folderCreated = "分组创建成功"
    override val removedFromFolder = "已移出分组"
    override val addedToFolder = "已放入分组"
    override val newFolder = "新建文件夹"
    override val importSuccessCount = { count: Int -> "成功导入 $count 本书" }
    override val importSuccessWithSkipped = { success: Int, skipped: Int -> "成功导入 $success 本，有 $skipped 本已存在跳过" }
    override val importSuccessWithFailed = { success: Int, failed: Int -> "成功导入 $success 本，失败 $failed 本" }
    override val importSuccessWithBoth = { success: Int, skipped: Int, failed: Int -> "成功导入 $success 本，已存在 $skipped 本，失败 $failed 本" }
    override val importFailed = { error: String -> "导入失败: $error" }
    override val favoriteToggled = "收藏状态已更新"
    override val addFavorite = "收藏"
    override val removeFavorite = "取消收藏"
    override val bookInfo = "书籍信息"
    override val deleteBook = "删除"
    override val deleteBookTitle = "删除书籍"
    override val deleteBookConfirm = { title: String -> "确定要删除《$title》吗？此操作不可撤销。" }
    override val filterAll = "全部"
    override val filterFinished = "已读完"
    override val filterFavorite = "收藏"
    override val sortTitle = "排序方式"
    override val sortDescending = "降序"
    override val sortAscending = "升序"
    override val sortLastRead = "最近阅读"
    override val sortAddTime = "添加时间"
    override val sortBookTitle = "书名"
    override val sortReadingTime = "阅读时长"
    override val sortReadingProgress = "阅读进度"
    override val bookTitleLabel = "标题"
    override val bookAuthorLabel = "作者"
    override val unknownAuthor = "未知"
    override val bookFormatLabel = "格式"
    override val bookSizeLabel = "大小"
    override val bookProgressLabel = "进度"
    override val readingDurationLabel = "阅读时长"
    override val notReadYet = "未阅读"
    override val unreadLabel = "未读"
    override val notStartedLabel = "未开始"
    override val readProgress = { percent: Int -> "已读 $percent%" }
    override val filePathLabel = "文件路径"
    override val favoritedDesc = "已收藏"
    override val selectAll = "全选"
    override val deselectAll = "取消全选"
    override val importSelected = { count: Int -> "导入所选 ($count)" }
    override val folderImportDesc = "自动扫描文件夹内所有可导入的书籍"
    override val groupCreatedAndMoved = "已创建分组并移动"
    override val selectedItemCount = { count: Int -> "已选择 $count 项" }
    override val selectItems = "请选择项目"
    override val folderLabel = "分组"
    override val moreLabel = "更多"
    override val confirmDeleteTitle = "确认删除"
    override val confirmDeleteSelected = { count: Int -> "确定要删除选中的 $count 项吗？此操作不可撤销。" }
    override val moveToExistingGroup = "移动到现有分组："
    override val newGroupName = "新分组名称"
    override val createAndMove = "创建并移动"
    override val createNewGroup = "创建新分组"
    override val removeFromGroup = "从分组中移出"
    override val folderEmpty = "分组内暂无书籍"
    override val unableToReadFile = "无法读取文件"
    override val invalidFolderPath = "无效的文件夹路径"
    override val invalidFolder = "不是有效的文件夹"
    override val noImportableFiles = "未找到可导入的文件"

    // ── 阅读状态（P0）──
    override val statusWantToRead = "想读"
    override val statusReading = "在读"
    override val statusPaused = "暂停"
    override val statusFinished = "已读完"
    override val statusAbandoned = "弃读"
    override val rereadCountLabel = { count: Int -> "第 $count 次阅读" }
    override val continueReading = "继续阅读"
    override val startReading = "开始阅读"
    override val rereadBook = "重新阅读"
    override val restartBook = "重新开始"
    override val exportNotes = "导出笔记"
    override val statusChanged = "阅读状态已更新"
    override val batchStatusChange = "批量改状态"

    // ── 标签（P1）──
    override val addTag = "添加标签"
    override val removeTag = "移除标签"
    override val tags = "标签"
    override val tagCountLabel = { count: Int -> "$count 本书使用" }
    override val searchTagHint = "搜索或输入标签名"
    override val noTags = "暂无标签"
    override val clearFilter = "清除筛选"
    override val sortReadingStatus = "阅读状态"
    override val sortReadCount = "阅读次数"

    // ── 标签管理（P2）──
    override val tagManagement = "标签管理"
    override val tagTotalCount = { count: Int -> "共 $count 个" }
    override val renameTag = "重命名标签"
    override val deleteTag = "删除标签"
    override val mergeTag = "合并标签"
    override val confirmDeleteTag = { name: String, count: Int -> "确定要删除标签「$name」吗？该标签已被 $count 本书使用。" }
    override val confirmMergeTag = { source: String, target: String -> "确定要将标签「$source」合并到「$target」吗？合并后「$source」将被删除。" }

    // ── 阅读数据（P2）──
    override val readingData = "阅读数据"
    override val totalDuration = "总时长"
    override val readingDays = "已读天数"
    override val daysUnit = "天"

    // ── 书签与笔记（P2）──
    override val bookmarksAndNotes = "书签与笔记"
    override val viewAll = "查看全部 ›"
    override val bookmarkNoteCount = { bookmarks: Int, notes: Int -> "$bookmarks 个书签 · $notes 条笔记" }

    // ── 标签建议（P3）──
    override val tagSuggestions = "标签建议"
    override val acceptAllSuggestions = "全部接受"
    override val noSuggestions = "暂无建议"
    override val acceptSuggestion = "接受"
    override val rejectSuggestion = "拒绝"

    // ── 预设标签包（P3）──
    override val presetTagPacks = "预设标签包"
    override val importPresetTags = "导入"
}

/** 繁体中文 — 书架、导入、书籍操作、编辑模式字符串 */
internal data object ZhHantBookshelf : BookshelfStrings {
    override val libraryImportSettings = "書庫與匯入設定"
    override val duplicateCheck = "自動查重開關"
    override val duplicateCheckDesc = "匯入重複書籍時自動高亮定位"
    override val importCopy = "匯入時複製檔案"
    override val importCopyDesc = "複製書籍到應用程式私有目錄"
    override val clearTempCache = "清除暫存快取"
    override val clearTempCacheDesc = "清理書籍匯入解壓過程中產生的暫存快取"
    override val clearCacheSuccess = "快取清理成功"
    override val bookDeleted = "已刪除"
    override val importSuccess = "匯入成功"
    override val bookAlreadyInShelf = "書籍已在書架中"
    override val folderCreated = "分組建立成功"
    override val removedFromFolder = "已移出分組"
    override val addedToFolder = "已放入分組"
    override val newFolder = "新建資料夾"
    override val importSuccessCount = { count: Int -> "成功匯入 $count 本書" }
    override val importSuccessWithSkipped = { success: Int, skipped: Int -> "成功匯入 $success 本，有 $skipped 本已存在跳過" }
    override val importSuccessWithFailed = { success: Int, failed: Int -> "成功匯入 $success 本，失敗 $failed 本" }
    override val importSuccessWithBoth = { success: Int, skipped: Int, failed: Int -> "成功匯入 $success 本，已存在 $skipped 本，失敗 $failed 本" }
    override val importFailed = { error: String -> "匯入失敗: $error" }
    override val favoriteToggled = "收藏狀態已更新"
    override val addFavorite = "收藏"
    override val removeFavorite = "取消收藏"
    override val bookInfo = "書籍資訊"
    override val deleteBook = "刪除"
    override val deleteBookTitle = "刪除書籍"
    override val deleteBookConfirm = { title: String -> "確定要刪除《$title》嗎？此操作不可撤銷。" }
    override val filterAll = "全部"
    override val filterFinished = "已讀完"
    override val filterFavorite = "收藏"
    override val sortTitle = "排序方式"
    override val sortDescending = "降序"
    override val sortAscending = "升序"
    override val sortLastRead = "最近閱讀"
    override val sortAddTime = "加入時間"
    override val sortBookTitle = "書名"
    override val sortReadingTime = "閱讀時長"
    override val sortReadingProgress = "閱讀進度"
    override val bookTitleLabel = "標題"
    override val bookAuthorLabel = "作者"
    override val unknownAuthor = "未知"
    override val bookFormatLabel = "格式"
    override val bookSizeLabel = "大小"
    override val bookProgressLabel = "進度"
    override val readingDurationLabel = "閱讀時長"
    override val notReadYet = "未閱讀"
    override val unreadLabel = "未讀"
    override val notStartedLabel = "未開始"
    override val readProgress = { percent: Int -> "已讀 $percent%" }
    override val filePathLabel = "檔案路徑"
    override val favoritedDesc = "已收藏"
    override val selectAll = "全選"
    override val deselectAll = "取消全選"
    override val importSelected = { count: Int -> "匯入所選 ($count)" }
    override val folderImportDesc = "自動掃描資料夾內所有可匯入的書籍"
    override val groupCreatedAndMoved = "已建立分組並移動"
    override val selectedItemCount = { count: Int -> "已選擇 $count 項" }
    override val selectItems = "請選擇項目"
    override val folderLabel = "分組"
    override val moreLabel = "更多"
    override val confirmDeleteTitle = "確認刪除"
    override val confirmDeleteSelected = { count: Int -> "確定要刪除選中的 $count 項嗎？此操作不可撤銷。" }
    override val moveToExistingGroup = "移動到現有分組："
    override val newGroupName = "新分組名稱"
    override val createAndMove = "建立並移動"
    override val createNewGroup = "建立新分組"
    override val removeFromGroup = "從分組中移出"
    override val folderEmpty = "分組內暫無書籍"
    override val unableToReadFile = "無法讀取檔案"
    override val invalidFolderPath = "無效的資料夾路徑"
    override val invalidFolder = "不是有效的資料夾"
    override val noImportableFiles = "未找到可匯入的檔案"

    // ── 閱讀狀態（P0）──
    override val statusWantToRead = "想讀"
    override val statusReading = "在讀"
    override val statusPaused = "暫停"
    override val statusFinished = "已讀完"
    override val statusAbandoned = "棄讀"
    override val rereadCountLabel = { count: Int -> "第 $count 次閱讀" }
    override val continueReading = "繼續閱讀"
    override val startReading = "開始閱讀"
    override val rereadBook = "重新閱讀"
    override val restartBook = "重新開始"
    override val exportNotes = "匯出筆記"
    override val statusChanged = "閱讀狀態已更新"
    override val batchStatusChange = "批次改狀態"

    // ── 標籤（P1）──
    override val addTag = "新增標籤"
    override val removeTag = "移除標籤"
    override val tags = "標籤"
    override val tagCountLabel = { count: Int -> "$count 本書使用" }
    override val searchTagHint = "搜尋或輸入標籤名"
    override val noTags = "暫無標籤"
    override val clearFilter = "清除篩選"
    override val sortReadingStatus = "閱讀狀態"
    override val sortReadCount = "閱讀次數"

    // ── 標籤管理（P2）──
    override val tagManagement = "標籤管理"
    override val tagTotalCount = { count: Int -> "共 $count 個" }
    override val renameTag = "重新命名標籤"
    override val deleteTag = "刪除標籤"
    override val mergeTag = "合併標籤"
    override val confirmDeleteTag = { name: String, count: Int -> "確定要刪除標籤「$name」嗎？該標籤已被 $count 本書使用。" }
    override val confirmMergeTag = { source: String, target: String -> "確定要將標籤「$source」合併到「$target」嗎？合併後「$source」將被刪除。" }

    // ── 閱讀數據（P2）──
    override val readingData = "閱讀數據"
    override val totalDuration = "總時長"
    override val readingDays = "已讀天數"
    override val daysUnit = "天"

    // ── 書簽與筆記（P2）──
    override val bookmarksAndNotes = "書籤與筆記"
    override val viewAll = "查看全部 ›"
    override val bookmarkNoteCount = { bookmarks: Int, notes: Int -> "$bookmarks 個書籤 · $notes 條筆記" }

    // ── 標籤建議（P3）──
    override val tagSuggestions = "標籤建議"
    override val acceptAllSuggestions = "全部接受"
    override val noSuggestions = "暫無建議"
    override val acceptSuggestion = "接受"
    override val rejectSuggestion = "拒絕"

    // ── 預設標籤包（P3）──
    override val presetTagPacks = "預設標籤包"
    override val importPresetTags = "匯入"
}

/** English — Bookshelf, import, book operations, edit mode strings */
internal data object EnBookshelf : BookshelfStrings {
    override val libraryImportSettings = "Library & Import"
    override val duplicateCheck = "Check Duplicates"
    override val duplicateCheckDesc = "Auto scroll and highlight existing books when importing duplicates"
    override val importCopy = "Copy File on Import"
    override val importCopyDesc = "Copy imported book files to app private storage"
    override val clearTempCache = "Clear Temp Cache"
    override val clearTempCacheDesc = "Clear temporary cache files generated during extraction"
    override val clearCacheSuccess = "Cache cleared successfully"
    override val bookDeleted = "Deleted"
    override val importSuccess = "Imported successfully"
    override val bookAlreadyInShelf = "Book already in library"
    override val folderCreated = "Folder created"
    override val removedFromFolder = "Removed from folder"
    override val addedToFolder = "Added to folder"
    override val newFolder = "New Folder"
    override val importSuccessCount = { count: Int -> "Successfully imported $count books" }
    override val importSuccessWithSkipped = { success: Int, skipped: Int -> "Successfully imported $success, skipped $skipped duplicates" }
    override val importSuccessWithFailed = { success: Int, failed: Int -> "Successfully imported $success, failed to import $failed" }
    override val importSuccessWithBoth = { success: Int, skipped: Int, failed: Int -> "Successfully imported $success, skipped $skipped, failed $failed" }
    override val importFailed = { error: String -> "Failed to import: $error" }
    override val favoriteToggled = "Favorite status updated"
    override val addFavorite = "Add to favorites"
    override val removeFavorite = "Remove from favorites"
    override val bookInfo = "Book info"
    override val deleteBook = "Delete"
    override val deleteBookTitle = "Delete book"
    override val deleteBookConfirm = { title: String -> "Are you sure you want to delete \"$title\"? This action cannot be undone." }
    override val filterAll = "All"
    override val filterFinished = "Finished"
    override val filterFavorite = "Favorites"
    override val sortTitle = "Sort"
    override val sortDescending = "Descending"
    override val sortAscending = "Ascending"
    override val sortLastRead = "Last read"
    override val sortAddTime = "Added time"
    override val sortBookTitle = "Title"
    override val sortReadingTime = "Reading time"
    override val sortReadingProgress = "Reading progress"
    override val bookTitleLabel = "Title"
    override val bookAuthorLabel = "Author"
    override val unknownAuthor = "Unknown"
    override val bookFormatLabel = "Format"
    override val bookSizeLabel = "Size"
    override val bookProgressLabel = "Progress"
    override val readingDurationLabel = "Reading time"
    override val notReadYet = "Not read yet"
    override val unreadLabel = "Unread"
    override val notStartedLabel = "Not started"
    override val readProgress = { percent: Int -> "Read $percent%" }
    override val filePathLabel = "File path"
    override val favoritedDesc = "Favorited"
    override val selectAll = "Select All"
    override val deselectAll = "Deselect All"
    override val importSelected = { count: Int -> "Import Selected ($count)" }
    override val folderImportDesc = "Auto-scan all importable books in the folder"
    override val groupCreatedAndMoved = "Folder created and books moved"
    override val selectedItemCount = { count: Int -> "$count items selected" }
    override val selectItems = "Select items"
    override val folderLabel = "Folder"
    override val moreLabel = "More"
    override val confirmDeleteTitle = "Confirm Delete"
    override val confirmDeleteSelected = { count: Int -> "Are you sure you want to delete $count items? This action cannot be undone." }
    override val moveToExistingGroup = "Move to existing folder:"
    override val newGroupName = "New folder name"
    override val createAndMove = "Create & Move"
    override val createNewGroup = "Create new folder"
    override val removeFromGroup = "Remove from folder"
    override val folderEmpty = "No books in this folder"
    override val unableToReadFile = "Unable to read file"
    override val invalidFolderPath = "Invalid folder path"
    override val invalidFolder = "Invalid folder"
    override val noImportableFiles = "No importable files found"

    // ── Reading status (P0) ──
    override val statusWantToRead = "Want to read"
    override val statusReading = "Reading"
    override val statusPaused = "Paused"
    override val statusFinished = "Finished"
    override val statusAbandoned = "Abandoned"
    override val rereadCountLabel = { count: Int -> "Read $count times" }
    override val continueReading = "Continue reading"
    override val startReading = "Start reading"
    override val rereadBook = "Read again"
    override val restartBook = "Restart"
    override val exportNotes = "Export notes"
    override val statusChanged = "Reading status updated"
    override val batchStatusChange = "Batch change status"

    // ── Tags (P1) ──
    override val addTag = "Add tag"
    override val removeTag = "Remove tag"
    override val tags = "Tags"
    override val tagCountLabel = { count: Int -> "Used by $count books" }
    override val searchTagHint = "Search or enter tag name"
    override val noTags = "No tags"
    override val clearFilter = "Clear filter"
    override val sortReadingStatus = "Reading status"
    override val sortReadCount = "Read count"

    // ── Tag management (P2) ──
    override val tagManagement = "Tag management"
    override val tagTotalCount = { count: Int -> "$count total" }
    override val renameTag = "Rename tag"
    override val deleteTag = "Delete tag"
    override val mergeTag = "Merge tag"
    override val confirmDeleteTag = { name: String, count: Int -> "Delete tag \"$name\"? It is used by $count books." }
    override val confirmMergeTag = { source: String, target: String -> "Merge tag \"$source\" into \"$target\"? \"$source\" will be deleted." }

    // ── Reading data (P2) ──
    override val readingData = "Reading data"
    override val totalDuration = "Total time"
    override val readingDays = "Reading days"
    override val daysUnit = "d"

    // ── Bookmarks & notes (P2) ──
    override val bookmarksAndNotes = "Bookmarks & notes"
    override val viewAll = "View all ›"
    override val bookmarkNoteCount = { bookmarks: Int, notes: Int -> "$bookmarks bookmarks · $notes notes" }

    // ── Tag suggestions (P3) ──
    override val tagSuggestions = "Tag suggestions"
    override val acceptAllSuggestions = "Accept all"
    override val noSuggestions = "No suggestions"
    override val acceptSuggestion = "Accept"
    override val rejectSuggestion = "Reject"

    // ── Preset tag packs (P3) ──
    override val presetTagPacks = "Preset tag packs"
    override val importPresetTags = "Import"
}
