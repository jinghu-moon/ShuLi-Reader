package com.shuli.reader.core.i18n

import androidx.compose.runtime.staticCompositionLocalOf

sealed interface AppStrings {
    // 基础导航与通用
    val appName: String
    val bookshelf: String
    val settings: String
    val search: String
    val searchPlaceholder: String
    val todayReading: String
    val noBooksFound: String
    val emptyBookshelf: String
    val searchIconDesc: String
    val previousSearchResult: String
    val nextSearchResult: String
    val sortIconDesc: String
    val viewModeIconDesc: String
    val viewModeGrid: String
    val viewModeList: String
    val viewModeCompact: String
    val moreIconDesc: String
    val backIconDesc: String
    val clearIconDesc: String
    val deleteIconDesc: String
    val infoIconDesc: String
    val favoriteIconDesc: String
    val coverImageDesc: String
    val bookmarkIconDesc: String
    val loading: String
    val selected: String

    // 外观与通用配置
    val appearance: String
    val themeModeLabel: String
    val themeSystem: String
    val themeLight: String
    val themeDark: String
    val themePaper: String
    val readerThemeLabel: String
    val appFontLabel: String
    val appFontSystem: String
    val appFontHarmony: String
    val appFontLxgw: String
    val languageLabel: String
    val languageCn: String
    val languageTw: String
    val languageEn: String

    // 一、阅读器显示偏好
    val readerPreferences: String
    val defaultFontSize: String
    val defaultLineSpacing: String
    val lineSpacingCompact: String
    val lineSpacingMedium: String
    val lineSpacingWide: String
    val defaultPageAnim: String
    val pageAnimOverlay: String
    val pageAnimSlide: String
    val pageAnimSimulation: String
    val pageAnimFade: String
    val pageAnimNone: String
    val pageTurnDirection: String
    val pageTurnHorizontal: String
    val pageTurnVertical: String
    val paragraphSpacing: String
    val paragraphSpacingCompact: String
    val paragraphSpacingNormal: String
    val paragraphSpacingWide: String
    val firstLineIndent: String
    val indentNone: String
    val indentTwoChars: String
    val fullScreenMode: String
    val keepScreenOn: String
    val brightness: String
    val brightnessFollowSystem: String
    val brightnessFollowSystemLabel: String
    val brightnessResetToSystem: String
    val readingFont: String
    val readingFontSystem: String
    val readingFontHarmony: String
    val readingFontLxgw: String
    val importFont: String
    val fontTestText: String
    val marginTopBottom: String
    val marginLeftRight: String
    val editValue: String
    val confirm: String

    // 二、书库与导入设置
    val libraryImportSettings: String
    val duplicateCheck: String
    val duplicateCheckDesc: String
    val importCopy: String
    val importCopyDesc: String
    val clearTempCache: String
    val clearTempCacheDesc: String
    val clearCacheSuccess: String

    // 三、阅读统计
    val readingStats: String
    val statsEnable: String
    val statsEnableDesc: String
    val statsDailyTarget: String
    val resetStats: String
    val resetStatsDesc: String
    val viewStatsReport: String
    val statsTitle: String
    val totalBooksCount: String
    val totalReadingTime: String
    val todayReadingProgress: String
    val readingTargetMinutes: (Int) -> String

    // 四、同步设置
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

    // 四-A、加密管理
    val encryptionManagement: String
    val encryptionEnabled: String
    val encryptionDisabled: String
    val e2eeProtectsSyncData: String
    val dataSyncedInPlaintext: String
    val e2eeNotEnabled: String
    val e2eeNotEnabledDesc: String
    val enableEncryption: String
    val rememberEncryptionPassword: String
    val rememberEncryptionPasswordDesc: String
    val verifyPassword: String
    val changePassword: String
    val setEncryptionPassword: String
    val inputPasswordToVerify: String
    val encryptionPassword: String
    val verifySuccess: String
    val passwordWrong: String
    val encryptionNotEnabled: String
    val verifyError: String
    val verify: String
    val oldPassword: String
    val newPassword: String
    val confirmNewPassword: String
    val passwordMismatch: String
    val confirmChange: String
    val confirmSet: String
    val algorithmDetails: String
    val encryptionAlgorithm: String
    val kdfIterations: String
    val keyVersion: String
    val createdAt: String

    // 四-B、同步状态与导航
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
    val save: String
    val syncContent: String
    val wifiOnly: String
    val autoSync: String
    val connectionSuccess: String
    val authFailedCheckUserPassword: String
    val networkErrorCheckAddress: String
    val unknownErrorRetryLater: String

    // 四-C、同步中状态
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

    // 四-D、本地同步路径
    val internalStorage: String
    val externalStorage: String
    val rootDirectory: String

    // 四-E、冲突解决
    val otherDevice: String
    val readingProgressConflict: String
    val conflictDetectedMessage: (String) -> String
    val thisDevice: String
    val keepLocal: String
    val useRemote: String
    val progressPosition: (Int, Long) -> String
    val updatedAtLabel: String

    // 四-F、设备管理
    val noSyncedDevices: String
    val unknownDevice: String
    val lastSyncLabel: String
    val appVersionLabel: String
    val removeDevice: String
    val removeDeviceConfirm: (String) -> String
    val remove: String
    val deviceFallbackName: (String) -> String

    // 四-G、导出与备份
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

    // 四-H、同步日志
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

    // 杂项
    val favorite: String
    val deleteFont: String

    // 四-I、时长格式化
    val durationSeconds: (Long) -> String
    val durationMinutesSeconds: (Long, Long) -> String
    val durationHoursMinutes: (Long, Long) -> String

    // 四-J、错误消息
    val invalidEncryptedFileFormat: String
    val zipMissingManifest: String
    val backupMissingManifest: String
    val decryptFailedPasswordWrong: String
    val cannotReadFontFile: (String) -> String
    val fontWriteFailedOrEmpty: (String) -> String

    // 五、朗读设置 (TTS)
    val ttsSettings: String
    val ttsSpeed: String
    val ttsPitch: String
    val ttsAutoPage: String
    val ttsHighlightSentence: String
    val ttsSkipTitle: String
    val ttsSleepTimer: String
    val ttsSleepTimerOff: String
    val ttsSleepTimerRemaining: (Int) -> String
    val ttsStart: String
    val ttsPause: String
    val ttsStop: String

    // 六、快捷设置面板 (QuickSettingsSheet)
    val layoutTab: String
    val styleTab: String
    val settingsTab: String
    val letterSpacingLabel: String
    val fontWeightLabel: String
    val fontWeightLight: String
    val fontWeightNormal: String
    val fontWeightMedium: String
    val fontWeightBold: String
    val textAlignLabel: String
    val textAlignLeft: String
    val textAlignJustify: String
    val chineseConvertLabel: String
    val chineseConvertNone: String
    val chineseConvertSimplified: String
    val chineseConvertTraditional: String
    val useZhLayoutLabel: String
    val usePanguSpacingLabel: String
    val slotNone: String
    val slotChapterTitle: String
    val slotBookTitle: String
    val slotPageNumber: String
    val slotProgress: String
    val slotTime: String
    val slotBattery: String
    val slotDate: String
    val headerLabel: String
    val footerLabel: String
    val progressBarLabel: String
    val opacityLabel: String
    val headerFooterCustom: String
    val headerHidden: String
    val footerHidden: String
    val displayLabel: String
    val displayFollowStatusBar: String
    val displayAlwaysShow: String
    val displayAlwaysHide: String
    val positionLeft: String
    val positionCenter: String
    val positionRight: String
    val headerLeft: String
    val headerCenter: String
    val headerRight: String
    val footerLeft: String
    val footerCenter: String
    val footerRight: String
    val titleStyleLabel: String
    val titleAlignLeft: String
    val titleAlignCenter: String
    val titleAlignHidden: String
    val titleSizeOffset: String
    val titleMarginTop: String
    val titleMarginBottom: String
    val headerMarginTop: String
    val footerMarginBottom: String
    val keepScreenOnLabel: String
    val keepScreenOnDesc: String
    val volumeKeyLabel: String
    val volumeKeyDesc: String
    val edgeTurnPageLabel: String
    val edgeTurnPageDesc: String
    val edgeWidthLabel: String
    val headerLineLabel: String
    val footerLineLabel: String
    val headerFontSizeLabel: String
    val footerFontSizeLabel: String
    val bottomJustifyLabel: String
    val readingPresets: String
    val savePresetAction: String
    val resetToDefault: String
    val resetToDefaultConfirm: String
    val savePresetTitle: String
    val presetNameLabel: String
    val deletePresetTitle: String
    val deletePresetConfirm: String
    val confirmAction: String
    val cancelAction: String
    val saveAction: String
    val deleteAction: String
    val deleteBookmarkTitle: String
    val deleteBookmarkConfirm: String
    val deleteNoteTitle: String
    val deleteNoteConfirm: String

    // 七、高级设置
    val advancedSettings: String
    val gpuAcceleration: String
    val loggingEnabled: String
    val resetAllSettings: String
    val resetAllSettingsDesc: String
    val settingsResetSuccess: String

    // 七、关于与版权
    val chapterFullText: String
    val aboutLabel: String
    val versionLabel: String
    val developerLabel: String
    val feedbackLabel: String
    val licenseLabel: String
    val checkUpdate: String

    // 交互响应提示
    val saveSuccess: String
    val saveFailed: String

    val selectAll: String
    val deselectAll: String
    val importSelected: (Int) -> String
    val alreadyLatestVersion: String
    val alreadyFirstPage: String
    val alreadyLastPage: String
    val folderImportDesc: String

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

    // 书架操作菜单
    val addFavorite: String
    val removeFavorite: String
    val bookInfo: String
    val deleteBook: String
    val deleteBookTitle: String
    val deleteBookConfirm: (String) -> String
    val cancel: String

    // 书架筛选、排序与信息
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

    // 阅读目录、书签与笔记
    val directoryTab: String
    val bookmarksTab: String
    val notesTab: String
    val currentChapterLabel: String
    val noBookmarks: String
    val noNotes: String
    val notePosition: (Int, Int, String) -> String
    val copySelection: String
    val addBookmarkAction: String
    val addNoteAction: String
    val previousChapter: String
    val nextChapter: String
    val customizeCover: String
    val resetCoverColor: String
    val unifiedCoverColor: String
    val unifiedCoverColorAuto: String
    val unifiedCoverColorActive: (Int) -> String

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

    // 字数统计
    val wordCountTenThousand: (Float) -> String
    val wordCountUnit: (Int) -> String

    // EPUB 图片占位
    val imagePlaceholder: String

    // 书架操作补充
    val groupCreatedAndMoved: String

    // 编辑模式与分组
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

    // 领域错误提示
    val unableToReadFile: String
    val invalidFolderPath: String
    val invalidFolder: String
    val noImportableFiles: String

    // 简体中文实现
    data object ZhHans : AppStrings {
        override val appName = "书里阅读器"
        override val bookshelf = "书架"
        override val settings = "设置"
        override val search = "搜索"
        override val searchPlaceholder = "输入书名进行搜索..."
        override val todayReading = "今日"
        override val noBooksFound = "该文件夹下未找到 TXT 或 EPUB 文件"
        override val emptyBookshelf = "书架空空如也，点击右下角按钮导入书籍"
        override val searchIconDesc = "搜索"
        override val previousSearchResult = "上一个搜索结果"
        override val nextSearchResult = "下一个搜索结果"
        override val sortIconDesc = "排序"
        override val viewModeIconDesc = "切换视图"
        override val viewModeGrid = "网格"
        override val viewModeList = "列表"
        override val viewModeCompact = "紧凑"
        override val moreIconDesc = "更多"
        override val backIconDesc = "返回"
        override val clearIconDesc = "清除"
        override val deleteIconDesc = "删除"
        override val infoIconDesc = "详情"
        override val favoriteIconDesc = "收藏"
        override val coverImageDesc = "封面图片"
        override val bookmarkIconDesc = "书签"
        override val loading = "加载中..."
        override val selected = "已选中"

        override val appearance = "外观"
        override val themeModeLabel = "深浅主题"
        override val themeSystem = "跟随系统"
        override val themeLight = "浅色模式"
        override val themeDark = "深色模式"
        override val themePaper = "纸质护眼"
        override val readerThemeLabel = "阅读主题"
        override val appFontLabel = "界面字体"
        override val appFontSystem = "系统默认"
        override val appFontHarmony = "鸿蒙字体"
        override val appFontLxgw = "霞鹜文楷"
        override val languageLabel = "界面语言"
        override val languageCn = "简体中文"
        override val languageTw = "繁體中文"
        override val languageEn = "English"

        override val readerPreferences = "阅读器显示偏好"
        override val defaultFontSize = "字号"
        override val defaultLineSpacing = "行距"
        override val lineSpacingCompact = "紧凑 (1.2)"
        override val lineSpacingMedium = "适中 (1.5)"
        override val lineSpacingWide = "宽敞 (1.8)"
        override val defaultPageAnim = "默认翻页动画"
        override val pageAnimOverlay = "覆盖"
        override val pageAnimSlide = "滑动"
        override val pageAnimSimulation = "仿真"
        override val pageAnimFade = "淡入淡出"
        override val pageAnimNone = "无动画"
        override val pageTurnDirection = "翻页方向"
        override val pageTurnHorizontal = "左右滑动"
        override val pageTurnVertical = "上下滚动"
        override val paragraphSpacing = "段距"
        override val paragraphSpacingCompact = "紧凑"
        override val paragraphSpacingNormal = "标准"
        override val paragraphSpacingWide = "宽敞"
        override val firstLineIndent = "缩进"
        override val indentNone = "无"
        override val indentTwoChars = "2字"
        override val fullScreenMode = "全屏模式"
        override val keepScreenOn = "屏幕常亮"
        override val brightness = "亮度调节"
        override val brightnessFollowSystem = "跟随系统"
        override val brightnessFollowSystemLabel = "跟随系统亮度"
        override val brightnessResetToSystem = "长按重置为系统亮度"
        override val readingFont = "阅读字体"
        override val readingFontSystem = "系统默认"
        override val readingFontHarmony = "鸿蒙字体"
        override val readingFontLxgw = "霞鹜文楷"
        override val importFont = "导入字体"
        override val fontTestText = "一只敏捷的棕色狐狸跳过一只懒惰的狗"
        override val marginTopBottom = "上下边距"
        override val marginLeftRight = "左右边距"
        override val editValue = "修改数值"
        override val confirm = "确定"

        override val libraryImportSettings = "书库与导入设置"
        override val duplicateCheck = "自动查重开关"
        override val duplicateCheckDesc = "导入重复书籍时自动高亮定位"
        override val importCopy = "导入时复制文件"
        override val importCopyDesc = "复制书籍到应用私有目录"
        override val clearTempCache = "清除临时缓存"
        override val clearTempCacheDesc = "清理书籍导入解压过程中产生的临时缓存"
        override val clearCacheSuccess = "缓存清理成功"

        override val readingStats = "阅读统计"
        override val statsEnable = "阅读时长统计"
        override val statsEnableDesc = "开启记录每日/每周/每月阅读时长"
        override val statsDailyTarget = "每日阅读目标"
        override val resetStats = "重置统计数据"
        override val resetStatsDesc = "清除本地所有阅读记录"
        override val viewStatsReport = "查看详细阅读报告"
        override val statsTitle = "阅读统计看板"
        override val totalBooksCount = "藏书总数"
        override val totalReadingTime = "累计阅读时长"
        override val todayReadingProgress = "今日阅读进度"
        override val readingTargetMinutes = { minutes: Int -> "$minutes 分钟" }

        override val syncSettings = "同步设置"
        override val syncMethod = "同步方式"
        override val syncMethodLocal = "本地备份"
        override val syncMethodWebdav = "WebDAV 同步"
        override val webdavUrl = "WebDAV 服务器地址"
        override val webdavUser = "用户名"
        override val webdavPassword = "密码"
        override val testConnection = "测试连接"
        override val syncNow = "立即同步"
        override val syncAndBackup = "同步与备份"
        override val syncAndBackupDesc = "云端同步配置、加密、设备管理"
        override val cloudSyncConfig = "云端同步配置"
        override val localBackupDesc = "导出与导入数据备份"

        override val encryptionManagement = "加密管理"
        override val encryptionEnabled = "加密已启用"
        override val encryptionDisabled = "加密未启用"
        override val e2eeProtectsSyncData = "端到端加密保护您的同步数据"
        override val dataSyncedInPlaintext = "数据以明文方式同步"
        override val e2eeNotEnabled = "端到端加密未启用"
        override val e2eeNotEnabledDesc = "启用后，同步数据将在设备端加密后上传，服务器无法读取内容。"
        override val enableEncryption = "启用加密"
        override val rememberEncryptionPassword = "请牢记加密密码"
        override val rememberEncryptionPasswordDesc = "加密密码无法找回。如果忘记密码，加密的同步数据将无法恢复。"
        override val verifyPassword = "验证密码"
        override val changePassword = "更换密码"
        override val setEncryptionPassword = "设置加密密码"
        override val inputPasswordToVerify = "输入加密密码以验证正确性"
        override val encryptionPassword = "加密密码"
        override val verifySuccess = "验证成功"
        override val passwordWrong = "密码错误"
        override val encryptionNotEnabled = "未启用加密"
        override val verifyError = "验证出错"
        override val verify = "验证"
        override val oldPassword = "旧密码"
        override val newPassword = "新密码"
        override val confirmNewPassword = "确认新密码"
        override val passwordMismatch = "密码不一致"
        override val confirmChange = "确认更换"
        override val confirmSet = "确认设置"
        override val algorithmDetails = "算法详情"
        override val encryptionAlgorithm = "加密算法"
        override val kdfIterations = "KDF 迭代次数"
        override val keyVersion = "密钥版本"
        override val createdAt = "创建时间"

        override val webdavServer = "WebDAV 服务器"
        override val webdavServerSettings = "WebDAV 服务器设置"
        override val e2eeStatus = "端到端加密状态"
        override val e2eeSettings = "端到端加密设置"
        override val syncedDevices = "已同步设备"
        override val manageRegisteredDevices = "管理已注册设备"
        override val syncLog = "同步日志"
        override val viewSyncHistory = "查看历史同步记录"
        override val exportData = "导出数据"
        override val exportBookmarksNotesProgress = "导出书签、笔记、进度"
        override val syncStatus = "同步状态"
        override val ready = "就绪"
        override val authFailedCheckCredentials = "认证失败，请检查账号密码"
        override val networkConnectionFailed = "网络连接失败"
        override val rateLimitedRetryLater = "请求过于频繁，稍后重试"
        override val cryptoLockedVerifyPassword = "加密锁未解锁，请先验证密码"
        override val unknownError = "发生未知错误"
        override val serverAddress = "服务器地址"
        override val save = "保存"
        override val syncContent = "同步内容"
        override val wifiOnly = "仅 Wi-Fi 同步"
        override val autoSync = "自动同步"
        override val connectionSuccess = "连接成功"
        override val authFailedCheckUserPassword = "认证失败，请检查用户名和密码"
        override val networkErrorCheckAddress = "网络错误，请检查地址和网络连接"
        override val unknownErrorRetryLater = "未知错误，请稍后重试"

        override val syncing = "正在同步"
        override val cancelSync = "取消同步"
        override val cancelSyncExplanation = "取消不会丢失已完成的部分，下次同步时继续"
        override val syncAgain = "再次同步"
        override val scanningLocalChanges = "正在扫描本地变更..."
        override val downloadingRemoteData = "正在下载远端数据..."
        override val mergingData = "正在合并数据..."
        override val uploadingBookmarksNotes = "正在上传书签与笔记..."
        override val syncComplete = "同步完成"
        override val syncFailed = "同步失败"
        override val rateLimitedWaitRetry = "请求过于频繁，等待重试..."
        override val waitingRetry = "等待重试..."
        override val cryptoLocked = "加密锁未解锁"

        override val internalStorage = "内部存储"
        override val externalStorage = "外部存储"
        override val rootDirectory = "根目录"

        // 四-E、冲突解决
        override val otherDevice = "其他设备"
        override val readingProgressConflict = "阅读进度冲突"
        override val conflictDetectedMessage = { name: String -> "检测到 \"$name\" 与本机的阅读进度不一致。请选择要保留的进度：" }
        override val thisDevice = "本机"
        override val keepLocal = "保留本地"
        override val useRemote = "使用远端"
        override val progressPosition = { chapter: Int, offset: Long -> "位置: 第 $chapter 章，偏移 $offset" }
        override val updatedAtLabel = "更新时间"

        // 四-F、设备管理
        override val noSyncedDevices = "暂无已同步设备"
        override val unknownDevice = "未知设备"
        override val lastSyncLabel = "最后同步"
        override val appVersionLabel = "版本"
        override val removeDevice = "移除设备"
        override val removeDeviceConfirm = { name: String -> "确定要移除 \"$name\" 吗？移除后该设备需要重新同步。" }
        override val remove = "移除"
        override val deviceFallbackName = { id: String -> "设备 $id" }

        // 四-G、导出与备份
        override val selectExportContent = "选择要导出的内容："
        override val bookFiles = "书籍文件"
        override val bookmarks = "书签"
        override val notes = "笔记"
        override val readingProgressExport = "阅读进度"
        override val readerConfig = "阅读器配置"
        override val estimatingSize = "正在估算导出大小..."
        override val estimatedSize = { size: String -> "预估大小: $size" }
        override val encryptedExport = "加密导出"
        override val rememberExportPassword = "请牢记加密密码。密码丢失后，导出的数据将无法恢复。"
        override val password = "密码"
        override val passwordConfirm = "确认密码"
        override val export = "导出"
        override val backupDescription = "将书签、笔记、阅读进度等数据备份到本地文件，或从备份文件恢复数据。"
        override val backupLocation = "备份存储位置"
        override val defaultAppDirectory = "应用默认目录"
        override val select = "选择"
        override val customBackupDirSelected = "已选择自定义备份目录"
        override val backupInPrivateDirWarning = "备份将保存在应用私有目录，卸载应用时数据会被清除"
        override val autoBackup = "自动备份"
        override val autoBackupDesc = "按计划自动备份数据"
        override val backupFrequency = "备份频率"
        override val backupEveryNHours = { hours: Int ->
            when {
                hours < 24 -> "每 $hours 小时"
                hours == 24 -> "每天"
                else -> "每 ${hours / 24} 天"
            }
        }
        override val backupOnStart = "启动时备份"
        override val backupOnStartDesc = "每次打开应用时自动备份"
        override val backupOnExit = "关闭时备份"
        override val backupOnExitDesc = "每次关闭应用时自动备份"
        override val autoBackupNote = "自动备份不包含书籍文件，仅备份书签、笔记和进度数据。最多保留 5 个备份。"
        override val manualBackup = "手动备份"
        override val exportBackup = "导出备份"
        override val exportBackupDesc = "将数据导出为 ZIP 文件"
        override val importBackup = "导入备份"
        override val importBackupDesc = "从 ZIP 文件恢复数据"
        override val selectFile = "选择文件"
        override val backupNotes = "注意事项"
        override val backupNotesContent = "• 导入会合并现有数据，不会覆盖较新的本地数据\n• 加密备份需要输入正确的密码才能导入\n• 建议定期备份以防止数据丢失\n• 自定义目录需要授予应用读写权限"
        override val backupResultSuccess = "成功"
        override val backupResultComplete = "完成"

        // 四-H、同步日志
        override val filterCloud = "云端"
        override val filterLocal = "本地"
        override val filterFailed = "失败"
        override val noSyncLogs = "暂无同步日志"
        override val syncTypeCloud = "云端"
        override val syncTypeLocal = "本地"
        override val syncTypeUnknown = "未知"
        override val requestCount = { count: Int -> "$count 次请求" }
        override val today = "今天"
        override val yesterday = "昨天"

        // 杂项
        override val favorite = "收藏"
        override val deleteFont = "删除字体"

        // 四-I、时长格式化
        override val durationSeconds = { seconds: Long -> "${seconds}秒" }
        override val durationMinutesSeconds = { minutes: Long, seconds: Long -> "${minutes}分${seconds}秒" }
        override val durationHoursMinutes = { hours: Long, minutes: Long -> "${hours}时${minutes}分" }

        // 四-J、错误消息
        override val invalidEncryptedFileFormat = "加密文件格式无效"
        override val zipMissingManifest = "ZIP 文件缺少 manifest.json"
        override val backupMissingManifest = "备份文件缺少 manifest.json，格式无效"
        override val decryptFailedPasswordWrong = "解密失败：密码错误或文件已损坏"
        override val cannotReadFontFile = { uri: String -> "无法读取字体文件: $uri" }
        override val fontWriteFailedOrEmpty = { path: String -> "字体文件写入失败或文件为空: $path" }

        override val ttsSettings = "朗读设置 (TTS)"
        override val ttsSpeed = "语速调节"
        override val ttsPitch = "音调调节"
        override val ttsAutoPage = "自动翻页"
        override val ttsHighlightSentence = "高亮当前句子"
        override val ttsSkipTitle = "跳过章节标题"
        override val ttsSleepTimer = "定时停止"
        override val ttsSleepTimerOff = "关闭"
        override val ttsSleepTimerRemaining = { seconds: Int -> "${seconds / 60}分${seconds % 60}秒后停止" }
        override val ttsStart = "开始朗读"
        override val ttsPause = "暂停朗读"
        override val ttsStop = "停止朗读"

        override val layoutTab = "排版"
        override val styleTab = "样式"
        override val settingsTab = "设置"
        override val letterSpacingLabel = "字距"
        override val fontWeightLabel = "字重"
        override val fontWeightLight = "细"
        override val fontWeightNormal = "常规"
        override val fontWeightMedium = "中"
        override val fontWeightBold = "粗"
        override val textAlignLabel = "对齐"
        override val textAlignLeft = "左对齐"
        override val textAlignJustify = "两端"
        override val chineseConvertLabel = "简繁"
        override val chineseConvertNone = "原始"
        override val chineseConvertSimplified = "简体"
        override val chineseConvertTraditional = "繁体"
        override val useZhLayoutLabel = "自定义中文分行"
        override val usePanguSpacingLabel = "中英文间增加空格"
        override val slotNone = "无"
        override val slotChapterTitle = "章节名"
        override val slotBookTitle = "书名"
        override val slotPageNumber = "页码"
        override val slotProgress = "进度"
        override val slotTime = "时间"
        override val slotBattery = "电量"
        override val slotDate = "日期"
        override val headerLabel = "页眉"
        override val footerLabel = "页脚"
        override val progressBarLabel = "进度条"
        override val opacityLabel = "透明度"
        override val headerFooterCustom = "页眉脚自定义"
        override val headerHidden = "页眉已隐藏"
        override val footerHidden = "页脚已隐藏"
        override val displayLabel = "显示"
        override val displayFollowStatusBar = "跟随状态栏"
        override val displayAlwaysShow = "常显"
        override val displayAlwaysHide = "隐藏"
        override val positionLeft = "左"
        override val positionCenter = "中"
        override val positionRight = "右"
        override val headerLeft = "页眉左"
        override val headerCenter = "页眉中"
        override val headerRight = "页眉右"
        override val footerLeft = "页脚左"
        override val footerCenter = "页脚中"
        override val footerRight = "页脚右"
        override val titleStyleLabel = "标题样式"
        override val titleAlignLeft = "靠左"
        override val titleAlignCenter = "居中"
        override val titleAlignHidden = "隐藏"
        override val titleSizeOffset = "字号偏移"
        override val titleMarginTop = "上距"
        override val titleMarginBottom = "下距"
        override val headerMarginTop = "上边距"
        override val footerMarginBottom = "下边距"
        override val keepScreenOnLabel = "屏幕常亮"
        override val keepScreenOnDesc = "阅读时保持屏幕常亮"
        override val volumeKeyLabel = "音量键"
        override val volumeKeyDesc = "音量 +/- 键翻页"
        override val edgeTurnPageLabel = "边缘翻页"
        override val edgeTurnPageDesc = "点击屏幕左右边缘翻页"
        override val edgeWidthLabel = "边缘宽度"
        override val headerLineLabel = "页眉分割线"
        override val footerLineLabel = "页脚分割线"
        override val headerFontSizeLabel = "页眉字号"
        override val footerFontSizeLabel = "页脚字号"
        override val bottomJustifyLabel = "底部对齐"
        override val readingPresets = "阅读预设"
        override val savePresetAction = "＋ 保存当前"
        override val resetToDefault = "恢复默认设置"
        override val resetToDefaultConfirm = "确定要将所有阅读设置恢复为默认值吗？此操作不可撤销。"
        override val savePresetTitle = "保存预设"
        override val presetNameLabel = "预设名称"
        override val deletePresetTitle = "删除预设"
        override val deletePresetConfirm = "确定要删除这个预设吗？"
        override val confirmAction = "确定"
        override val cancelAction = "取消"
        override val saveAction = "保存"
        override val deleteAction = "删除"
        override val deleteBookmarkTitle = "删除书签"
        override val deleteBookmarkConfirm = "确定要删除这个书签吗？"
        override val deleteNoteTitle = "删除笔记"
        override val deleteNoteConfirm = "确定要删除这个笔记吗？"

        override val advancedSettings = "高级设置"
        override val gpuAcceleration = "GPU 硬件加速"
        override val loggingEnabled = "调试运行日志"
        override val resetAllSettings = "重置所有设置"
        override val resetAllSettingsDesc = "将应用设置项全部恢复为默认值"
        override val settingsResetSuccess = "设置重置成功"

        override val chapterFullText = "全文"
        override val aboutLabel = "关于与版权"
        override val versionLabel = "软件版本"
        override val developerLabel = "开发者"
        override val feedbackLabel = "反馈建议"
        override val licenseLabel = "开源许可证 (AGPL-3.0)"
        override val checkUpdate = "检查更新"

        override val saveSuccess = "设置保存成功"
        override val saveFailed = "设置保存失败"

        override val selectAll = "全选"
        override val deselectAll = "取消全选"
        override val importSelected = { count: Int -> "导入所选 ($count)" }
        override val alreadyLatestVersion = "已是最新版本"
        override val alreadyFirstPage = "已经是第一页了"
        override val alreadyLastPage = "已经是最后一页了"
        override val folderImportDesc = "自动扫描文件夹内所有可导入的书籍"

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
        override val cancel = "取消"
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
        override val directoryTab = "目录"
        override val bookmarksTab = "书签"
        override val notesTab = "笔记"
        override val currentChapterLabel = "当前"
        override val noBookmarks = "暂无书签"
        override val noNotes = "暂无笔记"
        override val notePosition = { start: Int, end: Int, date: String -> "位置: $start-$end  $date" }
        override val copySelection = "复制"
        override val addBookmarkAction = "添加书签"
        override val addNoteAction = "添加笔记"
        override val previousChapter = "上一章"
        override val nextChapter = "下一章"
        override val customizeCover = "自定义封面颜色"
        override val resetCoverColor = "恢复自动配色"
        override val unifiedCoverColor = "统一封面颜色"
        override val unifiedCoverColorAuto = "自动（按书名散列）"
        override val unifiedCoverColorActive = { idx: Int -> "已统一为色盘 #$idx" }
        override val backupExportFailedWriteDir = "无法写入目标目录"
        override val backupExportSuccessCustom = "导出成功：自定义目录"
        override val backupExportFailedCreateFile = "导出失败：无法创建文件"
        override val backupExportFailedPermission = "导出失败：目录无写入权限，请重新选择"
        override val backupExportSuccess = { dir: String -> "导出成功：$dir" }
        override val backupExportFailed = { msg: String -> "导出失败：$msg" }
        override val backupImportFailedRead = "无法读取备份文件"
        override val backupImportSuccess = "导入成功"
        override val backupImportFailed = { msg: String -> "导入失败：$msg" }
        override val fontImportSuccess = { name: String, count: Int -> "字体导入成功: $name（共 $count 个自定义字体）" }
        override val fontImportFailed = { msg: String -> "字体导入失败: $msg" }
        override val wordCountTenThousand = { v: Float -> String.format("%.2f万字", v) }
        override val wordCountUnit = { count: Int -> "${count}字" }
        override val imagePlaceholder = "图片"
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
    }

    // 繁体中文实现（继承并覆盖差异项）
    data object ZhHant : AppStrings {
        override val appName = "書裡閱讀器"
        override val bookshelf = "書架"
        override val settings = "設定"
        override val search = "搜尋"
        override val searchPlaceholder = "輸入書名進行搜尋..."
        override val todayReading = "今日"
        override val noBooksFound = "該資料夾下未找到 TXT 或 EPUB 檔案"
        override val emptyBookshelf = "書架空空如也，點擊右下角按鈕匯入書籍"
        override val searchIconDesc = "搜尋"
        override val previousSearchResult = "上一個搜尋結果"
        override val nextSearchResult = "下一個搜尋結果"
        override val sortIconDesc = "排序"
        override val viewModeIconDesc = "切換檢視"
        override val viewModeGrid = "網格"
        override val viewModeList = "列表"
        override val viewModeCompact = "緊湊"
        override val moreIconDesc = "更多"
        override val backIconDesc = "返回"
        override val clearIconDesc = "清除"
        override val deleteIconDesc = "刪除"
        override val infoIconDesc = "詳情"
        override val favoriteIconDesc = "收藏"
        override val coverImageDesc = "封面圖片"
        override val bookmarkIconDesc = "書籤"
        override val loading = "載入中..."
        override val selected = "已選中"

        override val appearance = "外觀"
        override val themeModeLabel = "深淺主題"
        override val themeSystem = "跟隨系統"
        override val themeLight = "淺色模式"
        override val themeDark = "深色模式"
        override val themePaper = "紙質護眼"
        override val readerThemeLabel = "閱讀主題"
        override val appFontLabel = "介面字體"
        override val appFontSystem = "系統預設"
        override val appFontHarmony = "鴻蒙字體"
        override val appFontLxgw = "霞鶩文楷"
        override val languageLabel = "介面語言"
        override val languageCn = "簡體中文"
        override val languageTw = "繁體中文"
        override val languageEn = "English"

        override val readerPreferences = "閱讀器顯示偏好"
        override val defaultFontSize = "字號"
        override val defaultLineSpacing = "行距"
        override val lineSpacingCompact = "緊湊 (1.2)"
        override val lineSpacingMedium = "適中 (1.5)"
        override val lineSpacingWide = "寬敞 (1.8)"
        override val defaultPageAnim = "預設翻頁動畫"
        override val pageAnimOverlay = "覆蓋"
        override val pageAnimSlide = "滑動"
        override val pageAnimSimulation = "模擬"
        override val pageAnimFade = "淡入淡出"
        override val pageAnimNone = "無動畫"
        override val pageTurnDirection = "翻頁方向"
        override val pageTurnHorizontal = "左右滑動"
        override val pageTurnVertical = "上下滾動"
        override val paragraphSpacing = "段距"
        override val paragraphSpacingCompact = "緊湊"
        override val paragraphSpacingNormal = "標準"
        override val paragraphSpacingWide = "寬敞"
        override val firstLineIndent = "縮排"
        override val indentNone = "無"
        override val indentTwoChars = "2字"
        override val fullScreenMode = "全螢幕模式"
        override val keepScreenOn = "螢幕常亮"
        override val brightness = "亮度調節"
        override val brightnessFollowSystem = "跟隨系統"
        override val brightnessFollowSystemLabel = "跟隨系統亮度"
        override val brightnessResetToSystem = "長按重置為系統亮度"
        override val readingFont = "閱讀字體"
        override val readingFontSystem = "系統預設"
        override val readingFontHarmony = "鴻蒙字體"
        override val readingFontLxgw = "霞鶩文楷"
        override val importFont = "匯入字型"
        override val fontTestText = "一隻敏捷的棕色狐狸跳過一隻懶惰的狗"
        override val marginTopBottom = "上下邊距"
        override val marginLeftRight = "左右邊距"
        override val editValue = "修改數值"
        override val confirm = "確定"

        override val libraryImportSettings = "書庫與匯入設定"
        override val duplicateCheck = "自動查重開關"
        override val duplicateCheckDesc = "匯入重複書籍時自動高亮定位"
        override val importCopy = "匯入時複製檔案"
        override val importCopyDesc = "複製書籍到應用程式私有目錄"
        override val clearTempCache = "清除暫存快取"
        override val clearTempCacheDesc = "清理書籍匯入解壓過程中產生的暫存快取"
        override val clearCacheSuccess = "快取清理成功"

        override val readingStats = "閱讀統計"
        override val statsEnable = "閱讀時長統計"
        override val statsEnableDesc = "開啟記錄每日/每週/每月閱讀時長"
        override val statsDailyTarget = "每日閱讀目標"
        override val resetStats = "重置統計數據"
        override val resetStatsDesc = "清除本地所有閱讀記錄"
        override val viewStatsReport = "查看詳細閱讀報告"
        override val statsTitle = "閱讀統計看板"
        override val totalBooksCount = "藏書總數"
        override val totalReadingTime = "累計閱讀時長"
        override val todayReadingProgress = "今日閱讀進度"
        override val readingTargetMinutes = { minutes: Int -> "$minutes 分鐘" }

        override val syncSettings = "同步設定"
        override val syncMethod = "同步方式"
        override val syncMethodLocal = "本地備份"
        override val syncMethodWebdav = "WebDAV 同步"
        override val webdavUrl = "WebDAV 伺服器位址"
        override val webdavUser = "使用者名稱"
        override val webdavPassword = "密碼"
        override val testConnection = "測試連線"
        override val syncNow = "立即同步"
        override val syncAndBackup = "同步與備份"
        override val syncAndBackupDesc = "雲端同步配置、加密、裝置管理"
        override val cloudSyncConfig = "雲端同步配置"
        override val localBackupDesc = "匯出與匯入資料備份"

        override val encryptionManagement = "加密管理"
        override val encryptionEnabled = "加密已啟用"
        override val encryptionDisabled = "加密未啟用"
        override val e2eeProtectsSyncData = "端到端加密保護您的同步資料"
        override val dataSyncedInPlaintext = "資料以明文方式同步"
        override val e2eeNotEnabled = "端到端加密未啟用"
        override val e2eeNotEnabledDesc = "啟用後，同步資料將在裝置端加密後上傳，伺服器無法讀取內容。"
        override val enableEncryption = "啟用加密"
        override val rememberEncryptionPassword = "請牢記加密密碼"
        override val rememberEncryptionPasswordDesc = "加密密碼無法找回。如果忘記密碼，加密的同步資料將無法恢復。"
        override val verifyPassword = "驗證密碼"
        override val changePassword = "更換密碼"
        override val setEncryptionPassword = "設定加密密碼"
        override val inputPasswordToVerify = "輸入加密密碼以驗證正確性"
        override val encryptionPassword = "加密密碼"
        override val verifySuccess = "驗證成功"
        override val passwordWrong = "密碼錯誤"
        override val encryptionNotEnabled = "未啟用加密"
        override val verifyError = "驗證出錯"
        override val verify = "驗證"
        override val oldPassword = "舊密碼"
        override val newPassword = "新密碼"
        override val confirmNewPassword = "確認新密碼"
        override val passwordMismatch = "密碼不一致"
        override val confirmChange = "確認更換"
        override val confirmSet = "確認設定"
        override val algorithmDetails = "演算法詳情"
        override val encryptionAlgorithm = "加密演算法"
        override val kdfIterations = "KDF 迭代次數"
        override val keyVersion = "金鑰版本"
        override val createdAt = "建立時間"

        override val webdavServer = "WebDAV 伺服器"
        override val webdavServerSettings = "WebDAV 伺服器設定"
        override val e2eeStatus = "端到端加密狀態"
        override val e2eeSettings = "端到端加密設定"
        override val syncedDevices = "已同步裝置"
        override val manageRegisteredDevices = "管理已註冊裝置"
        override val syncLog = "同步日誌"
        override val viewSyncHistory = "查看歷史同步記錄"
        override val exportData = "匯出資料"
        override val exportBookmarksNotesProgress = "匯出書籤、筆記、進度"
        override val syncStatus = "同步狀態"
        override val ready = "就緒"
        override val authFailedCheckCredentials = "認證失敗，請檢查帳號密碼"
        override val networkConnectionFailed = "網路連線失敗"
        override val rateLimitedRetryLater = "請求過於頻繁，稍後重試"
        override val cryptoLockedVerifyPassword = "加密鎖未解鎖，請先驗證密碼"
        override val unknownError = "發生未知錯誤"
        override val serverAddress = "伺服器位址"
        override val save = "儲存"
        override val syncContent = "同步內容"
        override val wifiOnly = "僅 Wi-Fi 同步"
        override val autoSync = "自動同步"
        override val connectionSuccess = "連線成功"
        override val authFailedCheckUserPassword = "認證失敗，請檢查使用者名稱和密碼"
        override val networkErrorCheckAddress = "網路錯誤，請檢查位址和網路連線"
        override val unknownErrorRetryLater = "未知錯誤，請稍後重試"

        override val syncing = "正在同步"
        override val cancelSync = "取消同步"
        override val cancelSyncExplanation = "取消不會丟失已完成的部分，下次同步時繼續"
        override val syncAgain = "再次同步"
        override val scanningLocalChanges = "正在掃描本機變更..."
        override val downloadingRemoteData = "正在下載遠端資料..."
        override val mergingData = "正在合併資料..."
        override val uploadingBookmarksNotes = "正在上傳書籤與筆記..."
        override val syncComplete = "同步完成"
        override val syncFailed = "同步失敗"
        override val rateLimitedWaitRetry = "請求過於頻繁，等待重試..."
        override val waitingRetry = "等待重試..."
        override val cryptoLocked = "加密鎖未解鎖"

        override val internalStorage = "內部儲存"
        override val externalStorage = "外部儲存"
        override val rootDirectory = "根目錄"

        // 四-E、衝突解決
        override val otherDevice = "其他裝置"
        override val readingProgressConflict = "閱讀進度衝突"
        override val conflictDetectedMessage = { name: String -> "偵測到 \"$name\" 與本機的閱讀進度不一致。請選擇要保留的進度：" }
        override val thisDevice = "本機"
        override val keepLocal = "保留本機"
        override val useRemote = "使用遠端"
        override val progressPosition = { chapter: Int, offset: Long -> "位置: 第 $chapter 章，偏移 $offset" }
        override val updatedAtLabel = "更新時間"

        // 四-F、裝置管理
        override val noSyncedDevices = "暫無已同步裝置"
        override val unknownDevice = "未知裝置"
        override val lastSyncLabel = "最後同步"
        override val appVersionLabel = "版本"
        override val removeDevice = "移除裝置"
        override val removeDeviceConfirm = { name: String -> "確定要移除 \"$name\" 嗎？移除後該裝置需要重新同步。" }
        override val remove = "移除"
        override val deviceFallbackName = { id: String -> "裝置 $id" }

        // 四-G、匯出與備份
        override val selectExportContent = "選擇要匯出的內容："
        override val bookFiles = "書籍檔案"
        override val bookmarks = "書籤"
        override val notes = "筆記"
        override val readingProgressExport = "閱讀進度"
        override val readerConfig = "閱讀器配置"
        override val estimatingSize = "正在估算匯出大小..."
        override val estimatedSize = { size: String -> "預估大小: $size" }
        override val encryptedExport = "加密匯出"
        override val rememberExportPassword = "請牢記加密密碼。密碼遺失後，匯出的資料將無法恢復。"
        override val password = "密碼"
        override val passwordConfirm = "確認密碼"
        override val export = "匯出"
        override val backupDescription = "將書籤、筆記、閱讀進度等資料備份到本機檔案，或從備份檔案恢復資料。"
        override val backupLocation = "備份儲存位置"
        override val defaultAppDirectory = "應用預設目錄"
        override val select = "選擇"
        override val customBackupDirSelected = "已選擇自訂備份目錄"
        override val backupInPrivateDirWarning = "備份將保存在應用私有目錄，解除安裝應用時資料會被清除"
        override val autoBackup = "自動備份"
        override val autoBackupDesc = "按計劃自動備份資料"
        override val backupFrequency = "備份頻率"
        override val backupEveryNHours = { hours: Int ->
            when {
                hours < 24 -> "每 $hours 小時"
                hours == 24 -> "每天"
                else -> "每 ${hours / 24} 天"
            }
        }
        override val backupOnStart = "啟動時備份"
        override val backupOnStartDesc = "每次開啟應用時自動備份"
        override val backupOnExit = "關閉時備份"
        override val backupOnExitDesc = "每次關閉應用時自動備份"
        override val autoBackupNote = "自動備份不包含書籍檔案，僅備份書籤、筆記和進度資料。最多保留 5 個備份。"
        override val manualBackup = "手動備份"
        override val exportBackup = "匯出備份"
        override val exportBackupDesc = "將資料匯出為 ZIP 檔案"
        override val importBackup = "匯入備份"
        override val importBackupDesc = "從 ZIP 檔案恢復資料"
        override val selectFile = "選擇檔案"
        override val backupNotes = "注意事項"
        override val backupNotesContent = "• 匯入會合併現有資料，不會覆蓋較新的本機資料\n• 加密備份需要輸入正確的密碼才能匯入\n• 建議定期備份以防止資料遺失\n• 自訂目錄需要授予應用讀寫權限"
        override val backupResultSuccess = "成功"
        override val backupResultComplete = "完成"

        // 四-H、同步日誌
        override val filterCloud = "雲端"
        override val filterLocal = "本機"
        override val filterFailed = "失敗"
        override val noSyncLogs = "暫無同步日誌"
        override val syncTypeCloud = "雲端"
        override val syncTypeLocal = "本機"
        override val syncTypeUnknown = "未知"
        override val requestCount = { count: Int -> "$count 次請求" }
        override val today = "今天"
        override val yesterday = "昨天"

        // 雜項
        override val favorite = "收藏"
        override val deleteFont = "刪除字型"

        // 四-I、時長格式化
        override val durationSeconds = { seconds: Long -> "${seconds}秒" }
        override val durationMinutesSeconds = { minutes: Long, seconds: Long -> "${minutes}分${seconds}秒" }
        override val durationHoursMinutes = { hours: Long, minutes: Long -> "${hours}時${minutes}分" }

        // 四-J、錯誤訊息
        override val invalidEncryptedFileFormat = "加密檔案格式無效"
        override val zipMissingManifest = "ZIP 檔案缺少 manifest.json"
        override val backupMissingManifest = "備份檔案缺少 manifest.json，格式無效"
        override val decryptFailedPasswordWrong = "解密失敗：密碼錯誤或檔案已損壞"
        override val cannotReadFontFile = { uri: String -> "無法讀取字型檔案: $uri" }
        override val fontWriteFailedOrEmpty = { path: String -> "字型檔案寫入失敗或檔案為空: $path" }

        override val ttsSettings = "朗讀設定 (TTS)"
        override val ttsSpeed = "語速調節"
        override val ttsPitch = "音調調節"
        override val ttsAutoPage = "自動翻頁"
        override val ttsHighlightSentence = "高亮當前句子"
        override val ttsSkipTitle = "跳過章節標題"
        override val ttsSleepTimer = "定時停止"
        override val ttsSleepTimerOff = "關閉"
        override val ttsSleepTimerRemaining = { seconds: Int -> "${seconds / 60}分${seconds % 60}秒後停止" }
        override val ttsStart = "開始朗讀"
        override val ttsPause = "暫停朗讀"
        override val ttsStop = "停止朗讀"

        override val layoutTab = "排版"
        override val styleTab = "樣式"
        override val settingsTab = "設定"
        override val letterSpacingLabel = "字距"
        override val fontWeightLabel = "字重"
        override val fontWeightLight = "細"
        override val fontWeightNormal = "常規"
        override val fontWeightMedium = "中"
        override val fontWeightBold = "粗"
        override val textAlignLabel = "對齊"
        override val textAlignLeft = "左對齊"
        override val textAlignJustify = "兩端"
        override val chineseConvertLabel = "簡繁"
        override val chineseConvertNone = "原始"
        override val chineseConvertSimplified = "簡體"
        override val chineseConvertTraditional = "繁體"
        override val useZhLayoutLabel = "自定義中文分行"
        override val usePanguSpacingLabel = "中英文間增加空格"
        override val slotNone = "無"
        override val slotChapterTitle = "章節名"
        override val slotBookTitle = "書名"
        override val slotPageNumber = "頁碼"
        override val slotProgress = "進度"
        override val slotTime = "時間"
        override val slotBattery = "電量"
        override val slotDate = "日期"
        override val headerLabel = "頁眉"
        override val footerLabel = "頁腳"
        override val progressBarLabel = "進度條"
        override val opacityLabel = "透明度"
        override val headerFooterCustom = "頁眉腳自定義"
        override val headerHidden = "頁眉已隱藏"
        override val footerHidden = "頁腳已隱藏"
        override val displayLabel = "顯示"
        override val displayFollowStatusBar = "跟隨狀態欄"
        override val displayAlwaysShow = "常顯"
        override val displayAlwaysHide = "隱藏"
        override val positionLeft = "左"
        override val positionCenter = "中"
        override val positionRight = "右"
        override val headerLeft = "頁眉左"
        override val headerCenter = "頁眉中"
        override val headerRight = "頁眉右"
        override val footerLeft = "頁腳左"
        override val footerCenter = "頁腳中"
        override val footerRight = "頁腳右"
        override val titleStyleLabel = "標題樣式"
        override val titleAlignLeft = "靠左"
        override val titleAlignCenter = "居中"
        override val titleAlignHidden = "隱藏"
        override val titleSizeOffset = "字號偏移"
        override val titleMarginTop = "上距"
        override val titleMarginBottom = "下距"
        override val headerMarginTop = "上邊距"
        override val footerMarginBottom = "下邊距"
        override val keepScreenOnLabel = "螢幕常亮"
        override val keepScreenOnDesc = "閱讀時保持螢幕常亮"
        override val volumeKeyLabel = "音量鍵"
        override val volumeKeyDesc = "音量 +/- 鍵翻頁"
        override val edgeTurnPageLabel = "邊緣翻頁"
        override val edgeTurnPageDesc = "點擊螢幕左右邊緣翻頁"
        override val edgeWidthLabel = "邊緣寬度"
        override val headerLineLabel = "頁眉分割線"
        override val footerLineLabel = "頁腳分割線"
        override val headerFontSizeLabel = "頁眉字號"
        override val footerFontSizeLabel = "頁腳字號"
        override val bottomJustifyLabel = "底部對齊"
        override val readingPresets = "閱讀預設"
        override val savePresetAction = "＋ 儲存當前"
        override val resetToDefault = "恢復預設設定"
        override val resetToDefaultConfirm = "確定要將所有閱讀設定恢復為預設值嗎？此操作不可撤銷。"
        override val savePresetTitle = "儲存預設"
        override val presetNameLabel = "預設名稱"
        override val deletePresetTitle = "刪除預設"
        override val deletePresetConfirm = "確定要刪除這個預設嗎？"
        override val confirmAction = "確定"
        override val cancelAction = "取消"
        override val saveAction = "儲存"
        override val deleteAction = "刪除"
        override val deleteBookmarkTitle = "刪除書籤"
        override val deleteBookmarkConfirm = "確定要刪除這個書籤嗎？"
        override val deleteNoteTitle = "刪除筆記"
        override val deleteNoteConfirm = "確定要刪除這個筆記嗎？"

        override val advancedSettings = "高級設定"
        override val gpuAcceleration = "GPU 硬體加速"
        override val loggingEnabled = "調試運行日誌"
        override val resetAllSettings = "重置所有設定"
        override val resetAllSettingsDesc = "將應用程式設定全部恢復為預設值"
        override val settingsResetSuccess = "設定重置成功"

        override val chapterFullText = "全文"
        override val aboutLabel = "關於與版權"
        override val versionLabel = "軟體版本"
        override val developerLabel = "開發者"
        override val feedbackLabel = "反饋建議"
        override val licenseLabel = "開源許可證 (AGPL-3.0)"
        override val checkUpdate = "檢查更新"

        override val saveSuccess = "設定儲存成功"
        override val saveFailed = "設定儲存失敗"

        override val selectAll = "全選"
        override val deselectAll = "取消全選"
        override val importSelected = { count: Int -> "匯入所選 ($count)" }
        override val alreadyLatestVersion = "已是最新版本"
        override val alreadyFirstPage = "已經是第一頁了"
        override val alreadyLastPage = "已經是最後一頁了"
        override val folderImportDesc = "自動掃描資料夾內所有可匯入的書籍"

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
        override val cancel = "取消"
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
        override val directoryTab = "目錄"
        override val bookmarksTab = "書籤"
        override val notesTab = "筆記"
        override val currentChapterLabel = "目前"
        override val noBookmarks = "暫無書籤"
        override val noNotes = "暫無筆記"
        override val notePosition = { start: Int, end: Int, date: String -> "位置: $start-$end  $date" }
        override val copySelection = "複製"
        override val addBookmarkAction = "新增書籤"
        override val addNoteAction = "新增筆記"
        override val previousChapter = "上一章"
        override val nextChapter = "下一章"
        override val customizeCover = "自訂封面顏色"
        override val resetCoverColor = "恢復自動配色"
        override val unifiedCoverColor = "統一封面顏色"
        override val unifiedCoverColorAuto = "自動（按書名散列）"
        override val unifiedCoverColorActive = { idx: Int -> "已統一為色盤 #$idx" }
        override val backupExportFailedWriteDir = "無法寫入目標目錄"
        override val backupExportSuccessCustom = "匯出成功：自訂目錄"
        override val backupExportFailedCreateFile = "匯出失敗：無法建立檔案"
        override val backupExportFailedPermission = "匯出失敗：目錄無寫入權限，請重新選擇"
        override val backupExportSuccess = { dir: String -> "匯出成功：$dir" }
        override val backupExportFailed = { msg: String -> "匯出失敗：$msg" }
        override val backupImportFailedRead = "無法讀取備份檔案"
        override val backupImportSuccess = "匯入成功"
        override val backupImportFailed = { msg: String -> "匯入失敗：$msg" }
        override val fontImportSuccess = { name: String, count: Int -> "字型匯入成功: $name（共 $count 個自訂字型）" }
        override val fontImportFailed = { msg: String -> "字型匯入失敗: $msg" }
        override val wordCountTenThousand = { v: Float -> String.format("%.2f萬字", v) }
        override val wordCountUnit = { count: Int -> "${count}字" }
        override val imagePlaceholder = "圖片"
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
    }

    // 英文实现
    data object En : AppStrings {
        override val appName = "ShuLi Reader"
        override val bookshelf = "Bookshelf"
        override val settings = "Settings"
        override val search = "Search"
        override val searchPlaceholder = "Search books..."
        override val todayReading = "Today"
        override val noBooksFound = "No TXT or EPUB files found in this folder"
        override val emptyBookshelf = "The bookshelf is empty. Click the button at the bottom-right to import books"
        override val searchIconDesc = "Search"
        override val previousSearchResult = "Previous search result"
        override val nextSearchResult = "Next search result"
        override val sortIconDesc = "Sort"
        override val viewModeIconDesc = "Toggle View"
        override val viewModeGrid = "Grid"
        override val viewModeList = "List"
        override val viewModeCompact = "Compact"
        override val moreIconDesc = "More"
        override val backIconDesc = "Back"
        override val clearIconDesc = "Clear"
        override val deleteIconDesc = "Delete"
        override val infoIconDesc = "Info"
        override val favoriteIconDesc = "Favorite"
        override val coverImageDesc = "Cover Image"
        override val bookmarkIconDesc = "Bookmark"
        override val loading = "Loading..."
        override val selected = "Selected"

        override val appearance = "Appearance"
        override val themeModeLabel = "Theme Mode"
        override val themeSystem = "Follow System"
        override val themeLight = "Light Theme"
        override val themeDark = "Dark Theme"
        override val themePaper = "Paper Eye-care"
        override val readerThemeLabel = "Reading Theme"
        override val appFontLabel = "App Font"
        override val appFontSystem = "System Default"
        override val appFontHarmony = "HarmonyOS Sans"
        override val appFontLxgw = "LXGW Wenkai"
        override val languageLabel = "App Language"
        override val languageCn = "简体中文"
        override val languageTw = "繁體中文"
        override val languageEn = "English"

        override val readerPreferences = "Reader Preferences"
        override val defaultFontSize = "Size"
        override val defaultLineSpacing = "Spacing"
        override val lineSpacingCompact = "Compact (1.2)"
        override val lineSpacingMedium = "Medium (1.5)"
        override val lineSpacingWide = "Wide (1.8)"
        override val defaultPageAnim = "Default Page Animation"
        override val pageAnimOverlay = "Overlay"
        override val pageAnimSlide = "Slide"
        override val pageAnimSimulation = "Simulation"
        override val pageAnimFade = "Fade"
        override val pageAnimNone = "None"
        override val pageTurnDirection = "Page Turn Direction"
        override val pageTurnHorizontal = "Horizontal"
        override val pageTurnVertical = "Vertical"
        override val paragraphSpacing = "Para Space"
        override val paragraphSpacingCompact = "Compact"
        override val paragraphSpacingNormal = "Normal"
        override val paragraphSpacingWide = "Wide"
        override val firstLineIndent = "Indent"
        override val indentNone = "None"
        override val indentTwoChars = "2 Chars"
        override val fullScreenMode = "Full Screen Mode"
        override val keepScreenOn = "Keep Screen On"
        override val brightness = "Brightness"
        override val brightnessFollowSystem = "Follow System"
        override val brightnessFollowSystemLabel = "Follow System Brightness"
        override val brightnessResetToSystem = "Long press to reset to system brightness"
        override val readingFont = "Reading Font"
        override val readingFontSystem = "System Default"
        override val readingFontHarmony = "HarmonyOS Sans"
        override val readingFontLxgw = "LXGW Wenkai"
        override val importFont = "Import Font"
        override val fontTestText = "The quick brown fox jumps over the lazy dog"
        override val marginTopBottom = "Top/Bottom Margin"
        override val marginLeftRight = "Left/Right Margin"
        override val editValue = "Edit Value"
        override val confirm = "OK"

        override val libraryImportSettings = "Library & Import"
        override val duplicateCheck = "Check Duplicates"
        override val duplicateCheckDesc = "Auto scroll and highlight existing books when importing duplicates"
        override val importCopy = "Copy File on Import"
        override val importCopyDesc = "Copy imported book files to app private storage"
        override val clearTempCache = "Clear Temp Cache"
        override val clearTempCacheDesc = "Clear temporary cache files generated during extraction"
        override val clearCacheSuccess = "Cache cleared successfully"

        override val readingStats = "Reading Stats"
        override val statsEnable = "Track Reading Time"
        override val statsEnableDesc = "Record and analyze daily/weekly/monthly reading durations"
        override val statsDailyTarget = "Daily Reading Goal"
        override val resetStats = "Reset Statistics"
        override val resetStatsDesc = "Clear all reading duration logs from this device"
        override val viewStatsReport = "View Details"
        override val statsTitle = "Reading Statistics"
        override val totalBooksCount = "Total Books"
        override val totalReadingTime = "Accumulated Time"
        override val todayReadingProgress = "Today's Progress"
        override val readingTargetMinutes = { minutes: Int -> "$minutes min" }

        override val syncSettings = "Synchronization"
        override val syncMethod = "Sync Method"
        override val syncMethodLocal = "Local Backup"
        override val syncMethodWebdav = "WebDAV Sync"
        override val webdavUrl = "WebDAV Server URL"
        override val webdavUser = "Username"
        override val webdavPassword = "Password"
        override val testConnection = "Test Connection"
        override val syncNow = "Sync Now"
        override val syncAndBackup = "Sync & Backup"
        override val syncAndBackupDesc = "Cloud sync settings, encryption, device management"
        override val cloudSyncConfig = "Cloud Sync Settings"
        override val localBackupDesc = "Export and import data backup"

        override val encryptionManagement = "Encryption Management"
        override val encryptionEnabled = "Encryption Enabled"
        override val encryptionDisabled = "Encryption Disabled"
        override val e2eeProtectsSyncData = "End-to-end encryption protects your sync data"
        override val dataSyncedInPlaintext = "Data is synced in plaintext"
        override val e2eeNotEnabled = "End-to-end encryption is not enabled"
        override val e2eeNotEnabledDesc = "Once enabled, sync data will be encrypted on device before upload. The server cannot read the content."
        override val enableEncryption = "Enable Encryption"
        override val rememberEncryptionPassword = "Remember Your Encryption Password"
        override val rememberEncryptionPasswordDesc = "The encryption password cannot be recovered. If forgotten, encrypted sync data will be unrecoverable."
        override val verifyPassword = "Verify Password"
        override val changePassword = "Change Password"
        override val setEncryptionPassword = "Set Encryption Password"
        override val inputPasswordToVerify = "Enter encryption password to verify"
        override val encryptionPassword = "Encryption Password"
        override val verifySuccess = "Verification successful"
        override val passwordWrong = "Incorrect password"
        override val encryptionNotEnabled = "Encryption not enabled"
        override val verifyError = "Verification error"
        override val verify = "Verify"
        override val oldPassword = "Old Password"
        override val newPassword = "New Password"
        override val confirmNewPassword = "Confirm New Password"
        override val passwordMismatch = "Passwords do not match"
        override val confirmChange = "Confirm Change"
        override val confirmSet = "Confirm Set"
        override val algorithmDetails = "Algorithm Details"
        override val encryptionAlgorithm = "Encryption Algorithm"
        override val kdfIterations = "KDF Iterations"
        override val keyVersion = "Key Version"
        override val createdAt = "Created At"

        override val webdavServer = "WebDAV Server"
        override val webdavServerSettings = "WebDAV Server Settings"
        override val e2eeStatus = "E2EE Status"
        override val e2eeSettings = "E2EE Settings"
        override val syncedDevices = "Synced Devices"
        override val manageRegisteredDevices = "Manage registered devices"
        override val syncLog = "Sync Log"
        override val viewSyncHistory = "View sync history"
        override val exportData = "Export Data"
        override val exportBookmarksNotesProgress = "Export bookmarks, notes, progress"
        override val syncStatus = "Sync Status"
        override val ready = "Ready"
        override val authFailedCheckCredentials = "Authentication failed, please check your credentials"
        override val networkConnectionFailed = "Network connection failed"
        override val rateLimitedRetryLater = "Too many requests, please retry later"
        override val cryptoLockedVerifyPassword = "Encryption locked, please verify password first"
        override val unknownError = "An unknown error occurred"
        override val serverAddress = "Server Address"
        override val save = "Save"
        override val syncContent = "Sync Content"
        override val wifiOnly = "Wi-Fi Only"
        override val autoSync = "Auto Sync"
        override val connectionSuccess = "Connection successful"
        override val authFailedCheckUserPassword = "Authentication failed, please check username and password"
        override val networkErrorCheckAddress = "Network error, please check address and connection"
        override val unknownErrorRetryLater = "Unknown error, please retry later"

        override val syncing = "Syncing"
        override val cancelSync = "Cancel Sync"
        override val cancelSyncExplanation = "Cancelling won't lose completed progress. Next sync will continue."
        override val syncAgain = "Sync Again"
        override val scanningLocalChanges = "Scanning local changes..."
        override val downloadingRemoteData = "Downloading remote data..."
        override val mergingData = "Merging data..."
        override val uploadingBookmarksNotes = "Uploading bookmarks and notes..."
        override val syncComplete = "Sync complete"
        override val syncFailed = "Sync failed"
        override val rateLimitedWaitRetry = "Too many requests, waiting to retry..."
        override val waitingRetry = "Waiting to retry..."
        override val cryptoLocked = "Encryption locked"

        override val internalStorage = "Internal Storage"
        override val externalStorage = "External Storage"
        override val rootDirectory = "Root Directory"

        // Conflict Resolution
        override val otherDevice = "Other device"
        override val readingProgressConflict = "Reading Progress Conflict"
        override val conflictDetectedMessage = { name: String -> "Detected that \"$name\" has a different reading progress from this device. Please choose which progress to keep:" }
        override val thisDevice = "This Device"
        override val keepLocal = "Keep Local"
        override val useRemote = "Use Remote"
        override val progressPosition = { chapter: Int, offset: Long -> "Position: Chapter $chapter, offset $offset" }
        override val updatedAtLabel = "Updated at"

        // Device Management
        override val noSyncedDevices = "No synced devices"
        override val unknownDevice = "Unknown device"
        override val lastSyncLabel = "Last sync"
        override val appVersionLabel = "Version"
        override val removeDevice = "Remove Device"
        override val removeDeviceConfirm = { name: String -> "Remove \"$name\"? This device will need to re-sync." }
        override val remove = "Remove"
        override val deviceFallbackName = { id: String -> "Device $id" }

        // Export & Backup
        override val selectExportContent = "Select content to export:"
        override val bookFiles = "Book files"
        override val bookmarks = "Bookmarks"
        override val notes = "Notes"
        override val readingProgressExport = "Reading progress"
        override val readerConfig = "Reader settings"
        override val estimatingSize = "Estimating export size..."
        override val estimatedSize = { size: String -> "Estimated size: $size" }
        override val encryptedExport = "Encrypted export"
        override val rememberExportPassword = "Remember your encryption password. If lost, exported data cannot be recovered."
        override val password = "Password"
        override val passwordConfirm = "Confirm password"
        override val export = "Export"
        override val backupDescription = "Back up bookmarks, notes, reading progress and other data to a local file, or restore from a backup."
        override val backupLocation = "Backup location"
        override val defaultAppDirectory = "Default app directory"
        override val select = "Select"
        override val customBackupDirSelected = "Custom backup directory selected"
        override val backupInPrivateDirWarning = "Backups are saved in app private storage. Uninstalling the app will delete them."
        override val autoBackup = "Auto Backup"
        override val autoBackupDesc = "Back up data on a schedule"
        override val backupFrequency = "Backup frequency"
        override val backupEveryNHours = { hours: Int ->
            when {
                hours < 24 -> "Every $hours hours"
                hours == 24 -> "Daily"
                else -> "Every ${hours / 24} days"
            }
        }
        override val backupOnStart = "Backup on start"
        override val backupOnStartDesc = "Auto back up every time the app opens"
        override val backupOnExit = "Backup on exit"
        override val backupOnExitDesc = "Auto back up every time the app closes"
        override val autoBackupNote = "Auto backup excludes book files. Only bookmarks, notes and progress are backed up. Keeps up to 5 backups."
        override val manualBackup = "Manual Backup"
        override val exportBackup = "Export Backup"
        override val exportBackupDesc = "Export data as a ZIP file"
        override val importBackup = "Import Backup"
        override val importBackupDesc = "Restore data from a ZIP file"
        override val selectFile = "Select File"
        override val backupNotes = "Notes"
        override val backupNotesContent = "• Importing merges with existing data; newer local data is not overwritten\n• Encrypted backups require the correct password to import\n• Regular backups are recommended to prevent data loss\n• Custom directories require granting read/write permissions"
        override val backupResultSuccess = "Success"
        override val backupResultComplete = "Complete"

        // Sync Log
        override val filterCloud = "Cloud"
        override val filterLocal = "Local"
        override val filterFailed = "Failed"
        override val noSyncLogs = "No sync logs"
        override val syncTypeCloud = "Cloud"
        override val syncTypeLocal = "Local"
        override val syncTypeUnknown = "Unknown"
        override val requestCount = { count: Int -> "$count requests" }
        override val today = "Today"
        override val yesterday = "Yesterday"

        // Misc
        override val favorite = "Favorite"
        override val deleteFont = "Delete Font"

        // Duration formatting
        override val durationSeconds = { seconds: Long -> "${seconds}s" }
        override val durationMinutesSeconds = { minutes: Long, seconds: Long -> "${minutes}m${seconds}s" }
        override val durationHoursMinutes = { hours: Long, minutes: Long -> "${hours}h${minutes}m" }

        // Error messages
        override val invalidEncryptedFileFormat = "Invalid encrypted file format"
        override val zipMissingManifest = "ZIP file missing manifest.json"
        override val backupMissingManifest = "Backup file missing manifest.json, invalid format"
        override val decryptFailedPasswordWrong = "Decryption failed: wrong password or file corrupted"
        override val cannotReadFontFile = { uri: String -> "Cannot read font file: $uri" }
        override val fontWriteFailedOrEmpty = { path: String -> "Font file write failed or file is empty: $path" }

        override val ttsSettings = "TTS Settings"
        override val ttsSpeed = "Speech Speed"
        override val ttsPitch = "Speech Pitch"
        override val ttsAutoPage = "Auto Page Turn"
        override val ttsHighlightSentence = "Highlight Active Sentence"
        override val ttsSkipTitle = "Skip Chapter Title"
        override val ttsSleepTimer = "Sleep Timer"
        override val ttsSleepTimerOff = "Off"
        override val ttsSleepTimerRemaining = { seconds: Int -> "Stop in ${seconds / 60}m${seconds % 60}s" }
        override val ttsStart = "Start Reading"
        override val ttsPause = "Pause Reading"
        override val ttsStop = "Stop Reading"

        override val layoutTab = "Layout"
        override val styleTab = "Style"
        override val settingsTab = "Settings"
        override val letterSpacingLabel = "Letter Spacing"
        override val fontWeightLabel = "Font Weight"
        override val fontWeightLight = "Light"
        override val fontWeightNormal = "Normal"
        override val fontWeightMedium = "Medium"
        override val fontWeightBold = "Bold"
        override val textAlignLabel = "Alignment"
        override val textAlignLeft = "Left"
        override val textAlignJustify = "Justify"
        override val chineseConvertLabel = "Chinese Convert"
        override val chineseConvertNone = "Original"
        override val chineseConvertSimplified = "Simplified"
        override val chineseConvertTraditional = "Traditional"
        override val useZhLayoutLabel = "Custom Chinese Layout"
        override val usePanguSpacingLabel = "Space Between CJK and Latin"
        override val slotNone = "None"
        override val slotChapterTitle = "Chapter Title"
        override val slotBookTitle = "Book Title"
        override val slotPageNumber = "Page Number"
        override val slotProgress = "Progress"
        override val slotTime = "Time"
        override val slotBattery = "Battery"
        override val slotDate = "Date"
        override val headerLabel = "Header"
        override val footerLabel = "Footer"
        override val progressBarLabel = "Progress Bar"
        override val opacityLabel = "Opacity"
        override val headerFooterCustom = "Header & Footer Customization"
        override val headerHidden = "Header Hidden"
        override val footerHidden = "Footer Hidden"
        override val displayLabel = "Display"
        override val displayFollowStatusBar = "Follow Status Bar"
        override val displayAlwaysShow = "Always Show"
        override val displayAlwaysHide = "Always Hide"
        override val positionLeft = "Left"
        override val positionCenter = "Center"
        override val positionRight = "Right"
        override val headerLeft = "Header Left"
        override val headerCenter = "Header Center"
        override val headerRight = "Header Right"
        override val footerLeft = "Footer Left"
        override val footerCenter = "Footer Center"
        override val footerRight = "Footer Right"
        override val titleStyleLabel = "Title Style"
        override val titleAlignLeft = "Left"
        override val titleAlignCenter = "Center"
        override val titleAlignHidden = "Hidden"
        override val titleSizeOffset = "Size Offset"
        override val titleMarginTop = "Margin Top"
        override val titleMarginBottom = "Margin Bottom"
        override val headerMarginTop = "Margin Top"
        override val footerMarginBottom = "Margin Bottom"
        override val keepScreenOnLabel = "Keep Screen On"
        override val keepScreenOnDesc = "Keep screen on while reading"
        override val volumeKeyLabel = "Volume Keys"
        override val volumeKeyDesc = "Volume +/- to turn pages"
        override val edgeTurnPageLabel = "Edge Turn Page"
        override val edgeTurnPageDesc = "Tap screen edges to turn pages"
        override val edgeWidthLabel = "Edge Width"
        override val headerLineLabel = "Header Divider"
        override val footerLineLabel = "Footer Divider"
        override val headerFontSizeLabel = "Header Font Size"
        override val footerFontSizeLabel = "Footer Font Size"
        override val bottomJustifyLabel = "Bottom Justify"
        override val readingPresets = "Reading Presets"
        override val savePresetAction = "+ Save Current"
        override val resetToDefault = "Reset to Default"
        override val resetToDefaultConfirm = "Are you sure you want to reset all reading settings to default? This action cannot be undone."
        override val savePresetTitle = "Save Preset"
        override val presetNameLabel = "Preset Name"
        override val deletePresetTitle = "Delete Preset"
        override val deletePresetConfirm = "Are you sure you want to delete this preset?"
        override val confirmAction = "Confirm"
        override val cancelAction = "Cancel"
        override val saveAction = "Save"
        override val deleteAction = "Delete"
        override val deleteBookmarkTitle = "Delete Bookmark"
        override val deleteBookmarkConfirm = "Are you sure you want to delete this bookmark?"
        override val deleteNoteTitle = "Delete Note"
        override val deleteNoteConfirm = "Are you sure you want to delete this note?"

        override val advancedSettings = "Advanced"
        override val gpuAcceleration = "GPU Hardware Acceleration"
        override val loggingEnabled = "Debug Log Recording"
        override val resetAllSettings = "Reset All Settings"
        override val resetAllSettingsDesc = "Restore all settings to system default values"
        override val settingsResetSuccess = "Settings reset successfully"

        override val chapterFullText = "Full Text"
        override val aboutLabel = "About & Copyright"
        override val versionLabel = "Software Version"
        override val developerLabel = "Developer"
        override val feedbackLabel = "Feedback"
        override val licenseLabel = "License (AGPL-3.0)"
        override val checkUpdate = "Check for Update"

        override val saveSuccess = "Settings saved successfully"
        override val saveFailed = "Failed to save settings"

        override val selectAll = "Select All"
        override val deselectAll = "Deselect All"
        override val importSelected = { count: Int -> "Import Selected ($count)" }
        override val alreadyLatestVersion = "Already up to date"
        override val alreadyFirstPage = "Already the first page"
        override val alreadyLastPage = "Already the last page"
        override val folderImportDesc = "Auto-scan all importable books in the folder"

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
        override val cancel = "Cancel"
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
        override val directoryTab = "Contents"
        override val bookmarksTab = "Bookmarks"
        override val notesTab = "Notes"
        override val currentChapterLabel = "Current"
        override val noBookmarks = "No bookmarks"
        override val noNotes = "No notes"
        override val notePosition = { start: Int, end: Int, date: String -> "Position: $start-$end  $date" }
        override val copySelection = "Copy"
        override val addBookmarkAction = "Bookmark"
        override val addNoteAction = "Note"
        override val previousChapter = "Previous chapter"
        override val nextChapter = "Next chapter"
        override val customizeCover = "Customize cover color"
        override val resetCoverColor = "Reset to auto"
        override val unifiedCoverColor = "Unified cover color"
        override val unifiedCoverColorAuto = "Auto (hash by title)"
        override val unifiedCoverColorActive = { idx: Int -> "Unified to palette #$idx" }
        override val backupExportFailedWriteDir = "Cannot write to target directory"
        override val backupExportSuccessCustom = "Exported: custom directory"
        override val backupExportFailedCreateFile = "Export failed: cannot create file"
        override val backupExportFailedPermission = "Export failed: no write permission, please choose another directory"
        override val backupExportSuccess = { dir: String -> "Exported: $dir" }
        override val backupExportFailed = { msg: String -> "Export failed: $msg" }
        override val backupImportFailedRead = "Cannot read backup file"
        override val backupImportSuccess = "Import successful"
        override val backupImportFailed = { msg: String -> "Import failed: $msg" }
        override val fontImportSuccess = { name: String, count: Int -> "Font imported: $name ($count custom fonts)" }
        override val fontImportFailed = { msg: String -> "Font import failed: $msg" }
        override val wordCountTenThousand = { v: Float -> String.format("%.1fK words", v * 10) }
        override val wordCountUnit = { count: Int -> "$count words" }
        override val imagePlaceholder = "Image"
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
    }
}

val LocalAppStrings = staticCompositionLocalOf<AppStrings> { AppStrings.ZhHans }
